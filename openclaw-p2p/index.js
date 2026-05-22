"use strict";
/**
 * OpenClaw P2P WebSocket channel plugin for IMRChat.
 *
 * Registers a WebSocket server on a configurable port (default 18790).
 * IMRChat Android app connects directly via WebSocket for chat.
 */

const { WebSocketServer, WebSocket } = require("ws");
const http = require("http");
const { randomUUID } = require("crypto");
const fs = require("fs");
const path = require("path");

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------
const DEFAULT_PORT = 18790;
const AUTH_TIMEOUT_MS = 10_000;
const HEARTBEAT_INTERVAL_MS = 30_000;
const MAX_OFFLINE_CACHE = 100;

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------
let wss = null;
let httpServer = null;
let heartbeatTimer = null;
let wssReadyReject = null; // reject for pending listening promise
let stopResolver = null; // resolve when server stops — keeps startAccount alive
const connectedClients = new Map(); // clientId -> { ws, authenticated, lastActivity }
const offlineCache = new Map(); // clientId -> [{ type, msgId, content, ... }]

// Media storage directory
const MEDIA_DIR = path.join(process.env.HOME || "/tmp", ".openclaw", "p2p-media");
if (!fs.existsSync(MEDIA_DIR)) {
  fs.mkdirSync(MEDIA_DIR, { recursive: true });
}
const MIME_TYPES = {
  ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png",
  ".gif": "image/gif", ".webp": "image/webp", ".bmp": "image/bmp",
  ".pdf": "application/pdf", ".doc": "application/msword",
  ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  ".xls": "application/vnd.ms-excel",
  ".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  ".txt": "text/plain", ".json": "application/json", ".csv": "text/csv",
  ".mp3": "audio/mpeg", ".ogg": "audio/ogg", ".wav": "audio/wav", ".m4a": "audio/mp4",
  ".mp4": "video/mp4", ".avi": "video/x-msvideo", ".mov": "video/quicktime",
};
let serverPort = DEFAULT_PORT;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function getConfig(cfg) {
  const pluginConfig = cfg?.plugins?.entries?.["openclaw-p2p"]?.config ?? {};
  const channelConfig = cfg?.channels?.p2p ?? {};
  return { ...pluginConfig, ...channelConfig };
}

function getPort(cfg) {
  return getConfig(cfg).port ?? DEFAULT_PORT;
}

function getToken(cfg) {
  return getConfig(cfg).token ?? "";
}

function getOfflineLimit(cfg) {
  return getConfig(cfg).offlineCacheLimit ?? MAX_OFFLINE_CACHE;
}

function log(level, msg) {
  const ts = new Date().toISOString();
  console[level === "error" ? "error" : "log"](`[openclaw-p2p][${ts}] ${msg}`);
}

// ---------------------------------------------------------------------------
// Message sending helpers
// ---------------------------------------------------------------------------
function sendJson(ws, obj) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(obj));
  }
}

function sendAuthResult(ws, ok, reason) {
  sendJson(ws, {
    type: "auth_result",
    ok,
    serverVersion: "0.1.0",
    ...(reason ? { reason } : {}),
  });
}

function sendSystem(ws, content, level = "info") {
  sendJson(ws, {
    type: "system",
    msgId: randomUUID(),
    content,
    level,
    timestamp: Date.now(),
  });
}

function sendError(ws, code, message) {
  sendJson(ws, {
    type: "error",
    msgId: randomUUID(),
    code,
    message,
    timestamp: Date.now(),
  });
}

function sendReplyStart(ws, msgId, replyTo, meta = {}) {
  sendJson(ws, { type: "reply_start", msgId, replyTo, ...meta });
}

function sendReplyChunk(ws, msgId, content) {
  sendJson(ws, { type: "reply_chunk", msgId, content });
}

function sendReplyEnd(ws, msgId, fullContent, meta = {}) {
  sendJson(ws, { type: "reply_end", msgId, fullContent, ...meta });
}

function sendThinkingStart(ws, msgId, replyTo) {
  sendJson(ws, { type: "thinking_start", msgId, replyTo });
}

function sendThinkingChunk(ws, msgId, content) {
  sendJson(ws, { type: "thinking_chunk", msgId, content });
}

function sendThinkingEnd(ws, msgId, fullContent) {
  sendJson(ws, { type: "thinking_end", msgId, fullContent });
}

// Split text into small chunks for typewriter-style streaming
function splitForStreaming(text) {
  const MAX_CHUNK = 80; // chars per chunk for fast streaming
  const chunks = [];
  let i = 0;
  while (i < text.length) {
    let end = Math.min(i + MAX_CHUNK, text.length);
    if (end < text.length) {
      for (let j = end; j > i + 10; j--) {
        const ch = text[j];
        if (ch === '\n' || ch === '。' || ch === '！' || ch === '？' || ch === '；' || ch === '，' || ch === ' ' || ch === '：') {
          end = j + 1;
          break;
        }
      }
    }
    chunks.push(text.slice(i, end));
    i = end;
  }
  return chunks;
}

// Parse <think> tags from text — returns { thinking, answer }
function splitReasoningText(text) {
  const match = text.match(/<think>([\s\S]*?)<\/think>/);
  if (!match) return { thinking: null, answer: text };
  const thinking = match[1].trim();
  const answer = text.slice(0, match.index) + text.slice(match.index + match[0].length);
  return { thinking, answer: answer.trim() };
}

function sendOfflineMessages(ws) {
  const clientId = ws._clientId;
  const cached = offlineCache.get(clientId);
  if (!cached || cached.length === 0) return;

  const count = cached.length;
  sendJson(ws, { type: "offline_messages", count });
  log("info", `delivering ${count} offline messages to ${clientId}`);

  for (const msg of cached) {
    sendJson(ws, msg);
  }
  sendJson(ws, { type: "offline_done" });
  offlineCache.delete(clientId);
}

function cacheOfflineMessage(clientId, msg) {
  if (!offlineCache.has(clientId)) {
    offlineCache.set(clientId, []);
  }
  const cached = offlineCache.get(clientId);
  if (cached.length >= MAX_OFFLINE_CACHE) {
    cached.shift(); // drop oldest
  }
  cached.push(msg);
  log("info", `cached offline message for ${clientId} (queue size: ${cached.length})`);
}

// ---------------------------------------------------------------------------
// Command list (sourced from OpenClaw agent)
// ---------------------------------------------------------------------------
function getCommandList() {
  return [
    { name: "/clear", description: "清除上下文", hint: "清除 AI 的对话记忆" },
    { name: "/think", description: "切换思考模式", hint: "开启/关闭 AI 深度思考" },
    { name: "/compact", description: "压缩上下文", hint: "压缩历史对话节省 token" },
    { name: "/help", description: "帮助", hint: "显示可用命令" },
  ];
}

// ---------------------------------------------------------------------------
// Message handling (dispatch to OpenClaw agent)
// ---------------------------------------------------------------------------
let pluginApi = null;
let channelRuntime = null; // api.runtime.channel — used for dispatch

async function handleMessage(ws, raw, cfg) {
  let msg;
  try {
    msg = JSON.parse(raw);
  } catch {
    sendError(ws, "INVALID_JSON", "消息格式错误");
    return;
  }

  const client = connectedClients.get(ws._clientId);
  if (!client) return;

  // Require auth first
  if (!client.authenticated) {
    if (msg.type === "auth") {
      const expectedToken = getToken(cfg);
      log("info", `auth attempt: expectedToken="${expectedToken}", receivedToken="${msg.token}", clientId="${msg.clientId}"`);
      if (!expectedToken || msg.token === expectedToken) {
        client.authenticated = true;
        // Use client-provided persistent ID for session continuity
        if (msg.clientId && msg.clientId.length > 0) {
          const oldId = ws._clientId;
          ws._clientId = msg.clientId;
          connectedClients.delete(oldId);
          connectedClients.set(msg.clientId, client);
          log("info", `client id set: ${msg.clientId}`);
        }
        sendAuthResult(ws, true);
        sendSystem(ws, "连接成功");
        sendOfflineMessages(ws);
        log("info", `client authenticated: ${ws._clientId}`);
      } else {
        sendAuthResult(ws, false, "Token 无效");
        log("warn", `auth failed: ${ws._clientId}`);
        setTimeout(() => ws.close(4001, "Auth failed"), 500);
      }
    } else {
      sendError(ws, "NOT_AUTHENTICATED", "请先认证");
    }
    return;
  }

  client.lastActivity = Date.now();

  switch (msg.type) {
    case "text":
      await handleTextMessage(ws, client, msg, cfg);
      break;

    case "get_commands":
      sendJson(ws, { type: "commands", commands: getCommandList() });
      break;

    case "image":
    case "image_start":
    case "image_chunk":
    case "image_end":
    case "file":
    case "file_start":
    case "file_chunk":
    case "file_end":
      await handleMediaMessage(ws, client, msg, cfg);
      break;

    default:
      sendError(ws, "UNKNOWN_TYPE", `未知消息类型: ${msg.type}`);
  }
}

// Build inbound MsgContext from WebSocket text message
function buildInboundContext(ws, msg) {
  return {
    Body: msg.content,
    From: ws._clientId,
    To: ws._clientId,
    AccountId: "default",
    OriginatingChannel: "openclaw-p2p",
    OriginatingTo: ws._clientId,
    MessageSid: randomUUID(),
    Timestamp: msg.timestamp || Date.now(),
    Provider: "openclaw-p2p",
    ChatType: "direct",
  };
}

// Core dispatch: route → record session → dispatch reply via WebSocket streaming
async function dispatchToAgent(ws, msg, cfg, ctxOverrides) {
  const replyMsgId = randomUUID();

  if (!channelRuntime) {
    log("warn", "channelRuntime not available, using echo fallback");
    const echoText = `[echo] ${msg.content || "(empty)"}`;
    const now = Date.now();
    sendReplyStart(ws, replyMsgId, msg.msgId, { model: "echo", startedAt: now });
    sendReplyChunk(ws, replyMsgId, echoText);
    sendReplyEnd(ws, replyMsgId, echoText, { elapsedMs: Date.now() - now, model: "echo", charCount: echoText.length });
    return;
  }

  const accountId = "default";
  const ctx = { ...buildInboundContext(ws, msg), ...ctxOverrides };

  // Resolve agent route
  const route = channelRuntime.routing.resolveAgentRoute({
    cfg,
    channel: "openclaw-p2p",
    accountId,
    peer: { kind: "direct", id: ws._clientId },
  });

  ctx.SessionKey = route.sessionKey;

  log("info", `dispatch: agentId=${route.agentId || "(none)"} sessionKey=${route.sessionKey || "(none)"} bodyLen=${(ctx.Body || "").length}`);

  // Resolve model name for reply metadata
  const dispatchStartTime = Date.now();
  const modelName = cfg.agents?.defaults?.model?.primary || cfg.agents?.defaults?.model || "unknown";

  // Finalize inbound context
  const finalized = channelRuntime.reply.finalizeInboundContext(ctx);

  // Announce reply start IMMEDIATELY — don't wait for session recording
  sendReplyStart(ws, replyMsgId, msg.msgId, {
    model: modelName,
    startedAt: dispatchStartTime,
  });

  // Record inbound session in background (non-blocking)
  const storePath = channelRuntime.session.resolveStorePath(
    cfg.session?.store,
    { agentId: route.agentId },
  );

  channelRuntime.session.recordInboundSession({
    storePath,
    sessionKey: route.sessionKey,
    ctx: finalized,
    updateLastRoute: {
      sessionKey: route.mainSessionKey,
      channel: "openclaw-p2p",
      to: ws._clientId,
      accountId,
    },
    onRecordError: (err) => log("error", `recordInboundSession: ${String(err)}`),
  });

  // No artificial typing delay for p2p
  const humanDelay = undefined;

  // Accumulate full text across streaming chunks
  let accumulatedText = "";
  let accumulatedThinkingText = "";
  let thinkingActive = false;

  // Typing indicators over WebSocket
  const typingCallbacks = {
    start: async () => {
      sendJson(ws, { type: "typing", status: "start" });
    },
    stop: async () => {
      sendJson(ws, { type: "typing", status: "stop" });
    },
    onStartError: (err) => log("error", `typing start: ${String(err)}`),
    onStopError: (err) => log("error", `typing stop: ${String(err)}`),
    keepaliveIntervalMs: 5000,
  };

  // Create reply dispatcher — deliver sends streaming chunks over WebSocket
  const { dispatcher, replyOptions, markDispatchIdle } =
    channelRuntime.reply.createReplyDispatcherWithTyping({
      humanDelay,
      typingCallbacks,
      deliver: async (payload) => {
        // Handle reasoning payloads (framework-level isReasoning flag)
        if (payload.isReasoning) {
          const thinkingText = payload.text ?? "";
          if (thinkingText && ws.readyState === WebSocket.OPEN) {
            if (!thinkingActive) {
              sendThinkingStart(ws, replyMsgId, msg.msgId);
              thinkingActive = true;
            }
            accumulatedThinkingText += thinkingText;
            const chunks = splitForStreaming(thinkingText);
            for (const chunk of chunks) {
              if (ws.readyState !== WebSocket.OPEN) break;
              sendThinkingChunk(ws, replyMsgId, chunk);
            }
          }
          return;
        }

        let text = payload.text ?? "";

        // Parse <think> tags from text (models like Qwen embed thinking in text)
        if (text) {
          const { thinking, answer } = splitReasoningText(text);
          if (thinking && !thinkingActive) {
            sendThinkingStart(ws, replyMsgId, msg.msgId);
            thinkingActive = true;
            accumulatedThinkingText += thinking;
            const chunks = splitForStreaming(thinking);
            for (const chunk of chunks) {
              if (ws.readyState !== WebSocket.OPEN) break;
              sendThinkingChunk(ws, replyMsgId, chunk);
            }
            sendThinkingEnd(ws, replyMsgId, accumulatedThinkingText);
            thinkingActive = false;
          }
          text = answer;
        }

        // Close thinking section when real reply starts
        if (thinkingActive) {
          sendThinkingEnd(ws, replyMsgId, accumulatedThinkingText);
          thinkingActive = false;
        }

        if (text) accumulatedText += text;

        // Process media URLs: convert local paths to HTTP URLs, keep remote URLs as-is
        const rawMediaUrls = payload.mediaUrls || [];
        const allMediaUrls = [];
        for (const u of rawMediaUrls) {
          if (!u) continue;
          if (u.startsWith("http://") || u.startsWith("https://")) {
            allMediaUrls.push(u);
          } else {
            const httpUrl = localFileToUrl(u, ws._reqHost);
            if (httpUrl) allMediaUrls.push(httpUrl);
          }
        }
        if (payload.mediaUrl && !allMediaUrls.includes(payload.mediaUrl)) {
          if (payload.mediaUrl.startsWith("http://") || payload.mediaUrl.startsWith("https://")) {
            allMediaUrls.push(payload.mediaUrl);
          } else {
            const httpUrl = localFileToUrl(payload.mediaUrl, ws._reqHost);
            if (httpUrl) allMediaUrls.push(httpUrl);
          }
        }

        // Send media URLs
        const wsOpen = ws.readyState === WebSocket.OPEN;
        for (const mediaUrl of allMediaUrls) {
          if (wsOpen) {
            sendJson(ws, { type: "media", msgId: randomUUID(), replyTo: msg.msgId, url: mediaUrl, text });
          } else {
            cacheOfflineMessage(ws._clientId, {
              type: "media",
              msgId: randomUUID(),
              replyTo: msg.msgId,
              url: mediaUrl,
              text,
            });
          }
        }

        // Stream text chunks without artificial delay
        if (text && wsOpen) {
          const chunks = splitForStreaming(text);
          for (const chunk of chunks) {
            if (ws.readyState !== WebSocket.OPEN) break;
            sendReplyChunk(ws, replyMsgId, chunk);
          }
        }
      },
      onReasoningStream: async (payload) => {
        // Real-time reasoning stream — fires progressively as model thinks
        if (!payload.text) return;
        if (!thinkingActive) {
          sendThinkingStart(ws, replyMsgId, msg.msgId);
          thinkingActive = true;
          accumulatedThinkingText = "";
        }
        // Calculate delta from what we already sent
        const delta = payload.text.slice(accumulatedThinkingText.length);
        if (delta) {
          accumulatedThinkingText = payload.text;
          const chunks = splitForStreaming(delta);
          for (const chunk of chunks) {
            if (ws.readyState !== WebSocket.OPEN) break;
            sendThinkingChunk(ws, replyMsgId, chunk);
          }
        }
      },
      onError: (err, info) => {
        log("error", `reply ${info.kind}: ${String(err)}`);
        sendError(ws, "PROCESSING_ERROR", err.message || String(err));
      },
    });

  try {
    // Build P2P-optimized config: disable thinking since reasoning stream
    // is not authorized for non-platform channels (canUseReasoningState check)
    const p2pCfg = {
      ...cfg,
      agents: {
        ...cfg.agents,
        defaults: {
          ...cfg.agents?.defaults,
          thinkingDefault: "off",
        },
      },
    };

    await channelRuntime.reply.withReplyDispatcher({
      dispatcher,
      run: () =>
        channelRuntime.reply.dispatchReplyFromConfig({
          ctx: finalized,
          cfg: p2pCfg,
          dispatcher,
          replyOptions,
        }),
    });

    // All chunks delivered — send final reply_end with full text and timing
    const finalText = accumulatedText;
    const elapsedMs = Date.now() - dispatchStartTime;
    if (ws.readyState === WebSocket.OPEN) {
      sendReplyEnd(ws, replyMsgId, finalText, {
        elapsedMs,
        model: modelName,
        charCount: finalText.length,
      });
    } else {
      // Client disconnected — cache for offline delivery
      cacheOfflineMessage(ws._clientId, {
        type: "reply_end",
        msgId: replyMsgId,
        replyTo: msg.msgId,
        fullContent: finalText,
        elapsedMs,
        model: modelName,
        charCount: finalText.length,
      });
    }
    log("info", `deliver complete: textLen=${finalText.length}`);
  } finally {
    markDispatchIdle();
  }
}

// Text message processing
async function handleTextMessage(ws, client, msg, cfg) {
  if (!msg.content) {
    sendError(ws, "EMPTY_CONTENT", "消息内容为空");
    return;
  }

  log("info", `text from ${ws._clientId}: ${msg.content.slice(0, 100)}`);

  try {
    await dispatchToAgent(ws, msg, cfg, {});
  } catch (err) {
    log("error", `dispatch error: ${err.message || String(err)}`);
    sendError(ws, "PROCESSING_ERROR", err.message || String(err));
  }
}

// Media message handling
const mediaBuffers = new Map(); // msgId -> { chunks, fileName, mimeType, ... }

function saveMediaFile(fileName, data, mimeType) {
  const ext = path.extname(fileName) || (mimeType.startsWith("image/") ? ".jpg" : ".bin");
  const baseName = path.basename(fileName, ext);
  const uniqueName = `${baseName}_${Date.now()}${ext}`;
  const filePath = path.join(MEDIA_DIR, uniqueName);
  fs.writeFileSync(filePath, data);
  return filePath;
}

// Convert a local file path to an HTTP URL served by our media server
function localFileToUrl(filePath, reqHost) {
  if (!filePath) return null;
  if (filePath.startsWith("http://") || filePath.startsWith("https://")) return filePath;
  const fileName = path.basename(filePath);
  if (!fs.existsSync(filePath)) return null;
  // Copy file to MEDIA_DIR if it's not already there
  if (!filePath.startsWith(MEDIA_DIR)) {
    const dest = path.join(MEDIA_DIR, fileName);
    if (!fs.existsSync(dest)) {
      try { fs.copyFileSync(filePath, dest); } catch { return null; }
    }
  }
  const host = reqHost || `localhost:${serverPort}`;
  return `http://${host}/media/${encodeURIComponent(fileName)}`;
}

function handleMediaMessage(ws, client, msg, cfg) {
  const getConfig2 = getConfig(cfg);
  const maxSize = msg.type.startsWith("image")
    ? (getConfig2.maxImageSize ?? 10485760)
    : (getConfig2.maxFileSize ?? 52428800);

  if (msg.type === "image" || msg.type === "file") {
    // Direct send (small file)
    const data = Buffer.from(msg.data, "base64");
    if (data.length > maxSize) {
      sendError(ws, "FILE_TOO_LARGE", `文件超过 ${Math.round(maxSize / 1024 / 1024)}MB 限制`);
      return;
    }
    log("info", `${msg.type} from ${ws._clientId}: ${msg.fileName} (${data.length} bytes)`);
    const filePath = saveMediaFile(msg.fileName, data, msg.mimeType);
    log("info", `saved to: ${filePath}`);
    sendSystem(ws, `收到${msg.type === "image" ? "图片" : "文件"}: ${msg.fileName}`);

    const mediaMsg = {
      content: `[${msg.type === "image" ? "图片" : "文件"}] ${msg.fileName}`,
      msgId: msg.msgId,
    };
    dispatchToAgent(ws, mediaMsg, cfg, { MediaPath: filePath, MediaType: msg.mimeType })
      .catch((err) => log("error", `media dispatch error: ${err.message || String(err)}`));
  } else if (msg.type.endsWith("_start")) {
    mediaBuffers.set(msg.msgId, {
      fileName: msg.fileName,
      mimeType: msg.mimeType,
      totalSize: msg.totalSize,
      totalChunks: msg.totalChunks,
      chunks: [],
    });
    log("info", `${msg.type} start: ${msg.fileName} (${msg.totalChunks} chunks)`);
  } else if (msg.type.endsWith("_chunk")) {
    const buffer = mediaBuffers.get(msg.msgId);
    if (buffer) {
      buffer.chunks[msg.chunkIndex] = msg.data;
    }
  } else if (msg.type.endsWith("_end")) {
    const buffer = mediaBuffers.get(msg.msgId);
    if (buffer) {
      const combined = buffer.chunks.join("");
      const data = Buffer.from(combined, "base64");
      log("info", `${msg.type} complete: ${buffer.fileName} (${data.length} bytes)`);
      const filePath = saveMediaFile(buffer.fileName, data, buffer.mimeType);
      log("info", `saved to: ${filePath}`);
      mediaBuffers.delete(msg.msgId);
      sendSystem(ws, `收到${msg.type.startsWith("image") ? "图片" : "文件"}: ${buffer.fileName}`);

      const mediaMsg = {
        content: `[${msg.type.startsWith("image") ? "图片" : "文件"}] ${buffer.fileName}`,
        msgId: msg.msgId,
      };
      dispatchToAgent(ws, mediaMsg, cfg, { MediaPath: filePath, MediaType: buffer.mimeType })
        .catch((err) => log("error", `media dispatch error: ${err.message || String(err)}`));
    }
  }
}

// ---------------------------------------------------------------------------
// Channel plugin (OpenClaw ChannelPlugin interface)
// ---------------------------------------------------------------------------
const p2pChannelPlugin = {
  id: "p2p",

  meta: {
    id: "p2p",
    label: "P2P",
    selectionLabel: "IMRChat P2P",
    blurb: "WebSocket 直连通道，用于 IMRChat APP",
    aliases: ["imrchat"],
    order: 100,
  },

  capabilities: {
    chatTypes: ["direct"],
    media: true,
    reactions: false,
    threads: false,
    nativeCommands: true,
    blockStreaming: false,
  },

  pairing: {
    idLabel: "p2pClientId",
    normalizeAllowEntry: (entry) => entry,
    notifyApproval: async ({ id }) => {
      log("info", `pairing approved: ${id}`);
    },
  },

  messaging: {
    normalizeTarget: (raw) => raw,
    targetResolver: {
      looksLikeId: (s) => true,
      hint: "<clientId>",
    },
  },

  config: {
    listAccountIds: () => ["default"],
    resolveAccount: (cfg, accountId) => ({
      accountId: "default",
      enabled: true,
      configured: !!getPort(cfg),  // server is configured if port is set (token is optional)
      name: "IMRChat P2P",
    }),
    defaultAccountId: () => "default",
    isConfigured: (account) => account.configured,
    describeAccount: (account) => ({
      accountId: account.accountId,
      enabled: account.enabled,
      configured: account.configured,
      name: account.name,
    }),
    resolveAllowFrom: () => [],
    formatAllowFrom: ({ allowFrom }) => allowFrom,
  },

  status: {
    defaultRuntime: {
      accountId: "default",
      running: false,
      lastStartAt: null,
      lastStopAt: null,
      lastError: null,
      port: null,
    },
    buildChannelSummary: ({ snapshot }) => ({
      configured: snapshot.configured ?? false,
      running: snapshot.running ?? false,
      port: snapshot.port ?? null,
      clients: connectedClients.size,
    }),
  },

  gateway: {
    startAccount: async (ctx) => {
      const port = getPort(ctx.cfg);
      const token = getToken(ctx.cfg);

      // Guard against double-start (auto-restart may overlap)
      if (wss) {
        log("info", "WebSocket server already running, stopping old instance first");
        stopServer();
      }

      if (!token) {
        log("warn", "no token configured — connections will be accepted without auth");
      }

      // Create HTTP server for media file serving
      httpServer = http.createServer((req, res) => {
        res.setHeader("Access-Control-Allow-Origin", "*");
        res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        if (req.method === "OPTIONS") { res.writeHead(204); res.end(); return; }

        if (req.method === "GET" && req.url.startsWith("/media/")) {
          const rawName = decodeURIComponent(req.url.slice(7));
          // Prevent path traversal
          const safeName = path.basename(rawName);
          const filePath = path.join(MEDIA_DIR, safeName);
          if (!filePath.startsWith(MEDIA_DIR) || !fs.existsSync(filePath)) {
            res.writeHead(404); res.end("Not found"); return;
          }
          const ext = path.extname(safeName).toLowerCase();
          const contentType = MIME_TYPES[ext] || "application/octet-stream";
          const stat = fs.statSync(filePath);
          res.writeHead(200, {
            "Content-Type": contentType,
            "Content-Length": stat.size,
            "Cache-Control": "public, max-age=86400",
          });
          fs.createReadStream(filePath).pipe(res);
        } else {
          res.writeHead(200, { "Content-Type": "text/plain" });
          res.end("IMRChat P2P Media Server");
        }
      });

      // Create WebSocket server attached to HTTP server
      serverPort = port;
      wss = new WebSocketServer({ server: httpServer });

      // Wait for server to be ready before reporting running status
      await new Promise((resolve, reject) => {
        wssReadyReject = (err) => {
          wssReadyReject = null;
          reject(err);
        };
        httpServer.on("error", (err) => {
          wssReadyReject = null;
          log("error", `server error: ${err.message}`);
          ctx.setStatus({ running: false, lastError: err.message });
          reject(err);
        });
        httpServer.listen(port, () => {
          wssReadyReject = null;
          log("info", `HTTP+WebSocket server listening on port ${port}`);
          ctx.setStatus({ running: true, port, lastStartAt: new Date().toISOString() });
          resolve();
        });
      });

      wss.on("connection", (ws, req) => {
        const clientId = randomUUID();
        ws._clientId = clientId;
        // Store the host from the request for generating media URLs
        const hostHeader = req.headers.host || `localhost:${serverPort}`;
        ws._reqHost = hostHeader;
        connectedClients.set(clientId, {
          ws,
          authenticated: false,
          lastActivity: Date.now(),
          ip: req.socket.remoteAddress,
        });

        log("info", `client connected: ${clientId} from ${req.socket.remoteAddress}`);

        // Auth timeout
        const authTimer = setTimeout(() => {
          const client = connectedClients.get(clientId);
          if (client && !client.authenticated) {
            sendAuthResult(ws, false, "认证超时");
            ws.close(4002, "Auth timeout");
          }
        }, AUTH_TIMEOUT_MS);

        ws.on("message", (data) => {
          const raw = typeof data === "string" ? data : data.toString();
          handleMessage(ws, raw, ctx.cfg).catch((err) => {
            log("error", `unhandled message error: ${err.message || String(err)}`);
          });
        });

        ws.on("close", () => {
          clearTimeout(authTimer);
          connectedClients.delete(clientId);
          log("info", `client disconnected: ${clientId}`);
        });

        ws.on("error", (err) => {
          log("error", `client error ${clientId}: ${err.message}`);
        });

        // Heartbeat
        ws.isAlive = true;
        ws.on("pong", () => {
          ws.isAlive = true;
        });
      });

      // Heartbeat timer
      heartbeatTimer = setInterval(() => {
        wss.clients.forEach((ws) => {
          if (!ws.isAlive) {
            log("info", `heartbeat timeout: ${ws._clientId}`);
            return ws.terminate();
          }
          ws.isAlive = false;
          ws.ping();
        });
      }, HEARTBEAT_INTERVAL_MS);

      // Abort signal
      ctx.abortSignal?.addEventListener("abort", () => {
        log("info", "abort signal received, shutting down");
        stopServer();
      });

      // Keep startAccount pending until stop is requested.
      // The framework treats a resolved startAccount as "stopped".
      await new Promise((resolve) => {
        stopResolver = resolve;
      });
    },

    stopAccount: async (ctx) => {
      stopServer();
      ctx.setStatus({ running: false, lastStopAt: new Date().toISOString() });
      log("info", "p2p channel stopped");
    },
  },

  outbound: {
    sendText: async (params) => {
      const { to, text } = params;
      // Find client by target id, or broadcast to all authenticated clients
      let sent = false;
      for (const [, client] of connectedClients) {
        if (client.authenticated) {
          sendReplyChunk(client.ws, randomUUID(), text);
          sent = true;
        }
      }
      return { success: sent };
    },
  },
};

function stopServer() {
  if (wssReadyReject) {
    wssReadyReject(new Error("Server stopped before listening"));
    wssReadyReject = null;
  }
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
  if (wss) {
    wss.clients.forEach((ws) => ws.close(1001, "Server shutting down"));
    wss.close();
    wss = null;
  }
  if (httpServer) {
    httpServer.close();
    httpServer = null;
  }
  connectedClients.clear();
  offlineCache.clear();
  if (stopResolver) {
    stopResolver();
    stopResolver = null;
  }
}

// ---------------------------------------------------------------------------
// Plugin definition
// ---------------------------------------------------------------------------
const plugin = {
  id: "openclaw-p2p",
  name: "IMRChat P2P",
  description: "WebSocket 直连通道插件，用于 IMRChat 安卓客户端",

  register(api) {
    pluginApi = api;
    channelRuntime = api.runtime?.channel ?? null;
    if (!channelRuntime) {
      log("warn", "api.runtime.channel not available — dispatch will use echo fallback");
    }

    // Register channel
    api.registerChannel({ plugin: p2pChannelPlugin });

    log("info", "openclaw-p2p plugin registered");
  },
};

module.exports = plugin;
module.exports.default = plugin;
