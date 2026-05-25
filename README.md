# OpenClaw P2P Chat

[English](#english) | [中文](#中文)

---

<a name="english"></a>

## English

### Overview

OpenClaw P2P Chat is a lightweight real-time AI chat system built on the [OpenClaw](https://github.com/anthropics/openclaw) gateway platform. It enables you to chat with AI models from your Android phone through a self-hosted WebSocket bridge.

| Component | Description |
|-----------|-------------|
| **openclaw-p2p** | WebSocket plugin for OpenClaw gateway. Bridges AI model responses to clients over persistent WebSocket connections, with built-in HTTP media server. |
| **imrchat-android** | Native Android chat app built with Jetpack Compose + Material 3. Connects to the P2P plugin for a WeChat-like AI chat experience. |

### Download

| Version | Download | Release Date |
|---------|----------|-------------|
| **v0.1.4** (latest) | [IMRChat-v0.1.4-debug.apk](https://github.com/imrckj1-ctrl/openclaw-p2p-project/releases/download/v0.1.4/IMRChat-v0.1.4-debug.apk) | 2026-05-25 |

Or build from source: `cd imrchat-android && ./gradlew assembleDebug`

### Features

#### P2P Plugin (Server)

| Category | Feature |
|----------|---------|
| **Connection** | WebSocket server on configurable port (default 18790), token-based authentication, heartbeat keep-alive |
| **Streaming** | Real-time AI response streaming with optimized chunk delivery (pre-serialized JSON, 8ms interval) |
| **Thinking** | `<think>` tag parsing and streaming — AI reasoning content sent as separate `thinking_chunk` events |
| **Media** | Built-in HTTP media server for serving uploaded images/files; supports PNG/GIF/WebP/JPEG |
| **File Transfer** | 64KB chunked upload for large files (max 50MB per file, 10MB per image) |
| **Offline** | Message caching for disconnected clients with configurable limit (default 100); SQLite persistent storage survives gateway restarts |
| **Security** | TLS/WSS encryption with self-signed cert support, ACL tool filtering, token masked in logs, targeted message routing by client ID |
| **Store & Forward** | SQLite-backed message persistence — undelivered messages survive gateway restarts; ACK mechanism for confirmed delivery |
| **ACL** | Tool-level access control — deny-list specific tools per channel; prevents unauthorized agent operations |
| **Recovery** | Lazy channelRuntime recovery, graceful WebSocket close, media buffer TTL cleanup (5min) |

#### Android App (Client)

| Category | Feature |
|----------|---------|
| **Chat** | Real-time streaming replies, markdown rendering (headers, bold, italic, code blocks, tables) |
| **Tables** | Markdown pipe tables with horizontal scroll support |
| **Thinking** | Collapsible thinking/reasoning display |
| **Media** | Image/file sending via content picker; received images loaded with Coil |
| **Copy** | Long-press any message bubble to copy; text selection on all message types |
| **TLS** | WSS/HTTPS support — trusts self-signed certificates automatically; toggle in settings |
| **Storage** | Room SQLite local storage with proper database migrations |
| **Offline** | Outgoing message queue — messages queued when disconnected, auto-flushed on reconnect |
| **Voice** | Voice input via Android SpeechRecognizer (Chinese) |
| **Commands** | Slash-command support with autocomplete suggestions |
| **Settings** | Multiple server configs, dark mode toggle, clear chat history |
| **Connectivity** | Coroutine-based reconnection with exponential backoff (3s → 5s → 10s → 30s) |
| **Typing** | Typing indicator (send/receive) for real-time conversation feedback |

### Architecture

```
┌──────────────────┐                    ┌───────────────────┐
│  Android App     │   WSS/WS           │  OpenClaw Gateway │
│  (Jetpack        │◄══════════════════►│  (port 18789)     │
│   Compose)       │   port 18790        │       │           │
└──────────────────┘                    │  ┌────┴────────┐  │
        │                               │  │ P2P Plugin  │  │
        │ HTTPS/HTTP (media)            │  │ (port 18790) │  │
        ▼                               │  └────┬────────┘  │
┌──────────────────┐                    │       │           │
│  Media Files     │                    │  OpenClaw API     │
│  ~/.openclaw/    │                    │       │           │
│  p2p-media/      │                    └───────┼───────────┘
└──────────────────┘                            │
                                                ▼
                                       ┌────────────────┐
                                       │   AI Models    │
                                       │  (LLM APIs)    │
                                       └────────────────┘
```

### Directory Structure

```
openclaw-p2p-project/
├── openclaw-p2p/                     # OpenClaw WebSocket plugin
│   ├── index.js                      #   Plugin entry — WebSocket server, message routing, protocol
│   ├── openclaw.plugin.json          #   Plugin manifest & config schema
│   ├── package.json                  #   Node.js dependencies (ws)
│   └── src/                          #   Plugin source modules
├── imrchat-android/                  # Android app
│   ├── app/src/main/java/com/imr/chat/
│   │   ├── network/
│   │   │   ├── WebSocketClient.kt    #   OkHttp WebSocket with auto-reconnect & offline queue
│   │   │   ├── MediaSender.kt       #   Image/file upload with chunking & format preservation
│   │   │   └── protocol/
│   │   │       └── Messages.kt       #   Full message protocol (25+ message types)
│   │   ├── ui/
│   │   │   ├── chat/
│   │   │   │   ├── ChatScreen.kt     #   Main chat UI with streaming cards
│   │   │   │   └── ChatViewModel.kt  #   State management & offline queue
│   │   │   ├── settings/
│   │   │   │   ├── SettingsScreen.kt #   Server config, dark mode, clear history
│   │   │   │   └── SettingsViewModel.kt
│   │   │   └── components/
│   │   │       └── MarkdownRenderer.kt  # Rich markdown: tables, code blocks, bold/italic
│   │   ├── data/
│   │   │   ├── db/
│   │   │   │   ├── Entities.kt       #   Room entities (MessageEntity, CommandEntity)
│   │   │   │   ├── Daos.kt           #   Room DAOs with Flow support
│   │   │   │   └── AppDatabase.kt    #   Room database with version migrations
│   │   │   └── SettingsStore.kt      #   DataStore-based settings persistence
│   │   ├── service/
│   │   │   └── ChatService.kt        #   Foreground service for persistent connection
│   │   └── IMRChatApp.kt             #   Application class
│   ├── app/build.gradle.kts
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── IMRChat-v0.1.4-debug.apk          # Latest prebuilt APK
└── README.md
```

### Prerequisites

| Requirement | Version |
|-------------|---------|
| OpenClaw Gateway | v2026.5.12+ |
| Node.js | 18+ |
| JDK | 17 |
| Android SDK | API 35 |

### Installation

#### 1. Install the Plugin

```bash
cp -r openclaw-p2p ~/.openclaw/extensions/openclaw-p2p
cd ~/.openclaw/extensions/openclaw-p2p && npm install
```

#### 2. Configure the Gateway

Add to `~/.openclaw/openclaw.json`:

```json
{
  "plugins": {
    "entries": {
      "openclaw-p2p": {
        "enabled": true,
        "config": {
          "port": 18790,
          "token": "your-secret-token-here"
        }
      }
    },
    "allow": ["openclaw-p2p"]
  },
  "channels": {
    "p2p": {
      "token": "your-secret-token-here"
    }
  }
}
```

Then restart the gateway. Check `openclaw logs` or the gateway control UI to confirm the plugin is running and listening.

##### Enabling TLS/WSS Encryption (Recommended)

Generate a self-signed certificate for encrypted WSS/HTTPS connections:

```bash
mkdir -p ~/.openclaw/p2p-cert
openssl req -x509 -newkey rsa:4096 -keyout ~/.openclaw/p2p-cert/key.pem \
  -out ~/.openclaw/p2p-cert/cert.pem -days 3650 -nodes \
  -subj "/CN=192.168.31.168"   # Replace with your server's LAN IP or domain
```

Then add `certPath` and `keyPath` to the plugin config:

```json
"config": {
  "port": 18790,
  "token": "your-secret-token-here",
  "certPath": "/home/<user>/.openclaw/p2p-cert/cert.pem",
  "keyPath": "/home/<user>/.openclaw/p2p-cert/key.pem"
}
```

When both files exist and are readable, the plugin automatically enables TLS — the gateway log will show "TLS enabled — serving WSS/HTTPS". If the files are missing, it falls back to unencrypted HTTP/WS mode.

#### 3. Install the Android App

- **Prebuilt APK**: Download from [Releases](https://github.com/imrckj1-ctrl/openclaw-p2p-project/releases) and install directly.
- **Build from source**: Open `imrchat-android/` in Android Studio, sync Gradle, build & run.

In the app, go to **Settings** and add a server:

| Field | Value |
|-------|-------|
| Host | Your server's LAN IP (e.g. `192.168.x.x`) |
| Port | `18790` |
| Token | Same token from gateway config |
| SSL | **On** (if TLS is configured on server — app auto-trusts self-signed certs) |

### Plugin Configuration Reference

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `port` | number | `18790` | WebSocket server port |
| `token` | string | `""` | Auth token (empty = no auth) |
| `maxImageSize` | number | `10485760` | Max image bytes (10 MB) |
| `maxFileSize` | number | `52428800` | Max file bytes (50 MB) |
| `chunkSize` | number | `65536` | File transfer chunk size (64 KB) |
| `offlineCacheLimit` | number | `100` | Max cached offline messages per client |
| `certPath` | string | `""` | Path to TLS certificate PEM file (enables WSS/HTTPS) |
| `keyPath` | string | `""` | Path to TLS private key PEM file (enables WSS/HTTPS) |
| `acl.denyTools` | string[] | `[]` | Tool names to deny (ACL filtering) |

### WebSocket Protocol

All messages are JSON with a `type` field. The connection lifecycle:

```
Client ──auth──► Server
Client ◄──auth_result── Server
Client ──text──► Server      (or image, file)
Client ◄──reply_start── Server
Client ◄──reply_chunk── Server  (streaming, repeated)
Client ◄──reply_end── Server
```

#### Client → Server

| Type | Fields | Description |
|------|--------|-------------|
| `auth` | `token`, `clientId` | Authenticate with persistent device ID |
| `text` | `msgId`, `content`, `timestamp`, `source?` | Send text message |
| `image` | `msgId`, `fileName`, `mimeType`, `data`, `timestamp` | Send image (base64, small) |
| `image_start` | `msgId`, `fileName`, `mimeType`, `totalSize`, `totalChunks` | Start chunked image transfer |
| `image_chunk` | `msgId`, `chunkIndex`, `data` | Image chunk |
| `image_end` | `msgId` | End image transfer |
| `file` | `msgId`, `fileName`, `mimeType`, `fileSize`, `data`, `timestamp` | Send file (base64, small) |
| `file_start` | `msgId`, `fileName`, `mimeType`, `totalSize`, `totalChunks` | Start chunked file transfer |
| `file_chunk` | `msgId`, `chunkIndex`, `data` | File chunk |
| `file_end` | `msgId` | End file transfer |
| `get_commands` | — | Request available slash commands |
| `typing` | `clientId`, `typing` | Typing indicator |

#### Server → Client

| Type | Fields | Description |
|------|--------|-------------|
| `auth_result` | `ok`, `serverVersion`, `reason?` | Auth success/failure |
| `reply_start` | `msgId`, `replyTo`, `model?`, `startedAt?` | AI reply starting |
| `reply_chunk` | `msgId`, `content` | Reply text chunk (streaming) |
| `reply_end` | `msgId`, `fullContent`, `elapsedMs?`, `model?`, `charCount?` | Reply complete |
| `thinking_start` | `msgId`, `replyTo` | AI reasoning starting |
| `thinking_chunk` | `msgId`, `content` | Reasoning text chunk |
| `thinking_end` | `msgId`, `fullContent` | Reasoning complete |
| `media` | `msgId`, `replyTo?`, `url`, `text?` | AI-generated image/file |
| `commands` | `commands[]` (`name`, `description`, `hint`) | Available commands |
| `system` | `msgId`, `content`, `level`, `timestamp?` | System notification |
| `error` | `msgId`, `code`, `message`, `timestamp?` | Error message |
| `offline_messages` | `count` | Offline messages being delivered |
| `offline_done` | — | All offline messages delivered |
| `typing` | `clientId`, `typing` | Peer typing indicator |

### Troubleshooting

| Problem | Check |
|---------|-------|
| App won't connect | Verify server IP & port are reachable from phone (same LAN) |
| Auth fails | Token in app must match gateway `channels.p2p.token` AND `plugins.entries.openclaw-p2p.config.token` |
| Plugin not running | `openclaw logs` — check for "P2P plugin started" and port binding errors |
| Images not loading | Ensure media server is reachable. Check `~/.openclaw/p2p-media/` for saved files |
| Slow responses | First message after cold start includes model warmup (5-8s). Subsequent replies should be fast. |
| TLS not working | Verify `cert.pem` and `key.pem` exist at configured paths. Check gateway logs for "TLS enabled". Ensure Android SSL toggle matches server mode. |

### Development

```bash
# Plugin: edit then restart gateway
vim ~/.openclaw/extensions/openclaw-p2p/index.js
openclaw restart

# Android: edit then rebuild
cd imrchat-android && ./gradlew assembleDebug
```

### License

MIT

---

<a name="中文"></a>

## 中文

### 概述

OpenClaw P2P Chat 是一个基于 [OpenClaw](https://github.com/anthropics/openclaw) 网关平台的轻量级实时 AI 聊天系统。通过自建的 WebSocket 桥梁，在 Android 手机上即可与 AI 模型对话。

| 组件 | 说明 |
|------|------|
| **openclaw-p2p** | OpenClaw 网关的 WebSocket 插件。通过持久 WebSocket 连接桥接 AI 响应到客户端，内置 HTTP 媒体服务器。 |
| **imrchat-android** | Jetpack Compose + Material 3 构建的原生 Android 聊天应用，连接 P2P 插件，体验类似微信的 AI 聊天。 |

### 下载

| 版本 | 下载 | 发布日期 |
|------|------|----------|
| **v0.1.4**（最新） | [IMRChat-v0.1.4-debug.apk](https://github.com/imrckj1-ctrl/openclaw-p2p-project/releases/download/v0.1.4/IMRChat-v0.1.4-debug.apk) | 2026-05-25 |

或从源码构建：`cd imrchat-android && ./gradlew assembleDebug`

### 功能特性

#### P2P 插件（服务端）

| 类别 | 功能 |
|------|------|
| **连接** | WebSocket 服务器（可配置端口，默认 18790），Token 认证，心跳保活 |
| **流式传输** | AI 回复实时流式推送，预序列化 JSON 优化性能 |
| **思考过程** | `<think>` 标签解析，AI 推理过程以独立 `thinking_chunk` 事件流式发送 |
| **媒体** | 内置 HTTP 媒体服务器，支持 PNG/GIF/WebP/JPEG 图片和文件外网访问 |
| **文件传输** | 64KB 分片上传（文件最大 50MB，图片最大 10MB） |
| **离线消息** | 断线消息缓存（SQLite 持久化），重连后自动投递，网关重启不丢失 |
| **安全** | TLS/WSS 加密传输（支持自签证书），ACL 工具权限控制，日志 Token 脱敏、按 clientId 定向路由 |
| **存储转发** | SQLite 持久化消息——离线消息在网关重启后依然保留；ACK 机制确保可靠投递 |
| **ACL** | 工具级访问控制——可按通道禁止特定工具调用，防止越权操作 |
| **容错** | channelRuntime 懒恢复、WebSocket 优雅关闭、媒体缓冲区 5 分钟 TTL 自动清理 |

#### Android 应用（客户端）

| 类别 | 功能 |
|------|------|
| **聊天** | 实时流式回复、Markdown 渲染（标题、粗体、斜体、代码块、表格） |
| **表格** | Markdown 管道表格支持横向滑动 |
| **思考** | 可折叠的 AI 思考过程展示 |
| **媒体** | 图片/文件发送，收到的图片用 Coil 加载 |
| **复制** | 长按任意消息气泡弹出复制菜单，所有文字消息支持选中复制 |
| **TLS** | WSS/HTTPS 加密——自动信任自签证书，设置页一键开关 |
| **存储** | Room SQLite 本地存储，带数据库版本迁移 |
| **离线** | 消息发送队列——断线时自动暂存，重连后自动发送 |
| **语音** | 语音输入（调用 Android SpeechRecognizer，中文） |
| **命令** | 斜杠命令 `/` 自动补全提示 |
| **设置** | 多服务器配置、深色模式、清除聊天记录 |
| **连接** | 协程式自动重连（3s → 5s → 10s → 30s 指数退避） |
| **输入状态** | 输入中指示器（发送/接收），实时对话反馈 |

### 架构

```
┌──────────────────┐                    ┌───────────────────┐
│  Android 应用    │   WSS/WS           │  OpenClaw 网关    │
│  (Jetpack        │◄══════════════════►│  (端口 18789)     │
│   Compose)       │    端口 18790      │       │           │
└──────────────────┘                    │  ┌────┴────────┐  │
        │                               │  │ P2P 插件    │  │
        │ HTTPS/HTTP（获取媒体文件）       │  │ (端口 18790)│  │
        ▼                               │  └────┬────────┘  │
┌──────────────────┐                    │       │           │
│  媒体文件         │                    │  OpenClaw API     │
│  ~/.openclaw/    │                    │       │           │
│  p2p-media/      │                    └───────┼───────────┘
└──────────────────┘                            │
                                                ▼
                                       ┌────────────────┐
                                       │   AI 大模型     │
                                       │  (LLM APIs)    │
                                       └────────────────┘
```

### 目录结构

```
openclaw-p2p-project/
├── openclaw-p2p/                     # OpenClaw WebSocket 插件
│   ├── index.js                      #   插件入口 — WebSocket 服务器、消息路由、协议处理
│   ├── openclaw.plugin.json          #   插件清单与配置定义
│   ├── package.json                  #   Node.js 依赖
│   └── src/                          #   插件模块
├── imrchat-android/                  # Android 应用
│   ├── app/src/main/java/com/imr/chat/
│   │   ├── network/
│   │   │   ├── WebSocketClient.kt    #   OkHttp WebSocket + 自动重连 + 离线队列
│   │   │   ├── MediaSender.kt       #   图片/文件上传（分片 + 保留原格式）
│   │   │   └── protocol/
│   │   │       └── Messages.kt       #   完整消息协议（25+ 消息类型）
│   │   ├── ui/
│   │   │   ├── chat/
│   │   │   │   ├── ChatScreen.kt     #   聊天主界面 + 流式卡片
│   │   │   │   └── ChatViewModel.kt  #   状态管理 + 离线队列
│   │   │   ├── settings/
│   │   │   │   ├── SettingsScreen.kt #   服务器配置、深色模式、清除记录
│   │   │   │   └── SettingsViewModel.kt
│   │   │   └── components/
│   │   │       └── MarkdownRenderer.kt  # 富文本：表格、代码块、粗/斜体
│   │   ├── data/
│   │   │   ├── db/
│   │   │   │   ├── Entities.kt       #   Room 数据实体
│   │   │   │   ├── Daos.kt           #   Room DAO（Flow 响应式查询）
│   │   │   │   └── AppDatabase.kt    #   Room 数据库（含版本迁移）
│   │   │   └── SettingsStore.kt      #   DataStore 持久化配置
│   │   ├── service/
│   │   │   └── ChatService.kt        #   前台服务（保持连接常驻）
│   │   └── IMRChatApp.kt             #   Application 初始化
│   ├── app/build.gradle.kts
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── IMRChat-v0.1.4-debug.apk          # 最新预编译安装包
└── README.md
```

### 环境要求

| 软件 | 版本 |
|------|------|
| OpenClaw Gateway | v2026.5.12+ |
| Node.js | 18+ |
| JDK | 17 |
| Android SDK | API 35 |

### 安装部署

#### 1. 安装插件

```bash
cp -r openclaw-p2p ~/.openclaw/extensions/openclaw-p2p
cd ~/.openclaw/extensions/openclaw-p2p && npm install
```

#### 2. 配置网关

在 `~/.openclaw/openclaw.json` 中添加：

```json
{
  "plugins": {
    "entries": {
      "openclaw-p2p": {
        "enabled": true,
        "config": {
          "port": 18790,
          "token": "你的密钥"
        }
      }
    },
    "allow": ["openclaw-p2p"]
  },
  "channels": {
    "p2p": {
      "token": "你的密钥"
    }
  }
}
```

重启网关。用 `openclaw logs` 或网关控制面板确认插件已启动并监听端口。

##### 启用 TLS/WSS 加密（推荐）

生成自签名证书用于 WSS/HTTPS 加密传输：

```bash
mkdir -p ~/.openclaw/p2p-cert
openssl req -x509 -newkey rsa:4096 -keyout ~/.openclaw/p2p-cert/key.pem \
  -out ~/.openclaw/p2p-cert/cert.pem -days 3650 -nodes \
  -subj "/CN=192.168.31.168"   # 替换为你的服务器局域网 IP 或域名
```

然后在插件配置中添加 `certPath` 和 `keyPath`：

```json
"config": {
  "port": 18790,
  "token": "你的密钥",
  "certPath": "/home/<用户名>/.openclaw/p2p-cert/cert.pem",
  "keyPath": "/home/<用户名>/.openclaw/p2p-cert/key.pem"
}
```

证书和私钥均存在且可读时，插件自动启用 TLS——网关日志将显示 "TLS enabled — serving WSS/HTTPS"。文件缺失则自动回退到非加密模式。

#### 3. 安装 Android 应用

- **预编译 APK**：从 [Releases](https://github.com/imrckj1-ctrl/openclaw-p2p-project/releases) 下载直接安装。
- **源码构建**：Android Studio 打开 `imrchat-android/`，同步 Gradle，构建运行。

在 APP 中进入**设置**添加服务器：

| 字段 | 值 |
|------|-----|
| 主机 | 你服务器的局域网 IP（如 `192.168.x.x`） |
| 端口 | `18790` |
| Token | 与网关配置中相同的密钥 |
| SSL | **开启**（服务器已配置 TLS 时——APP 自动信任自签证书） |

### 插件参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `port` | number | `18790` | WebSocket 服务器端口 |
| `token` | string | `""` | 认证密钥（空 = 无认证） |
| `maxImageSize` | number | `10485760` | 图片最大字节数（10 MB） |
| `maxFileSize` | number | `52428800` | 文件最大字节数（50 MB） |
| `chunkSize` | number | `65536` | 文件传输分片大小（64 KB） |
| `offlineCacheLimit` | number | `100` | 每客户端离线消息缓存上限 |
| `certPath` | string | `""` | TLS 证书 PEM 文件路径（启用 WSS/HTTPS） |
| `keyPath` | string | `""` | TLS 私钥 PEM 文件路径（启用 WSS/HTTPS） |
| `acl.denyTools` | string[] | `[]` | 禁止使用的工具名称（ACL 过滤） |

### WebSocket 通信协议

所有消息为 JSON 格式，通过 `type` 字段区分。连接流程：

```
客户端 ──auth──► 服务端        认证
客户端 ◄──auth_result── 服务端  认证结果
客户端 ──text──► 服务端        发送消息
客户端 ◄──reply_start── 服务端  开始回复
客户端 ◄──reply_chunk── 服务端  流式内容（多次）
客户端 ◄──reply_end── 服务端    回复完成
```

#### 客户端 → 服务端

| 类型 | 字段 | 说明 |
|------|------|------|
| `auth` | `token`, `clientId` | 认证（带持久设备ID） |
| `text` | `msgId`, `content`, `timestamp`, `source?` | 发送文字消息 |
| `image` | `msgId`, `fileName`, `mimeType`, `data`, `timestamp` | 发送图片（base64，小文件） |
| `image_start` | `msgId`, `fileName`, `mimeType`, `totalSize`, `totalChunks` | 开始分片图片传输 |
| `image_chunk` | `msgId`, `chunkIndex`, `data` | 图片分片 |
| `image_end` | `msgId` | 图片传输结束 |
| `file` | `msgId`, `fileName`, `mimeType`, `fileSize`, `data`, `timestamp` | 发送文件（base64，小文件） |
| `file_start` | `msgId`, `fileName`, `mimeType`, `totalSize`, `totalChunks` | 开始分片文件传输 |
| `file_chunk` | `msgId`, `chunkIndex`, `data` | 文件分片 |
| `file_end` | `msgId` | 文件传输结束 |
| `get_commands` | — | 请求可用命令列表 |
| `typing` | `clientId`, `typing` | 输入状态通知 |

#### 服务端 → 客户端

| 类型 | 字段 | 说明 |
|------|------|------|
| `auth_result` | `ok`, `serverVersion`, `reason?` | 认证结果 |
| `reply_start` | `msgId`, `replyTo`, `model?`, `startedAt?` | AI 开始回复 |
| `reply_chunk` | `msgId`, `content` | 回复文字块（流式） |
| `reply_end` | `msgId`, `fullContent`, `elapsedMs?`, `model?`, `charCount?` | 回复完成 |
| `thinking_start` | `msgId`, `replyTo` | AI 开始推理 |
| `thinking_chunk` | `msgId`, `content` | 推理文字块 |
| `thinking_end` | `msgId`, `fullContent` | 推理完成 |
| `media` | `msgId`, `replyTo?`, `url`, `text?` | AI 生成的图片/文件 |
| `commands` | `commands[]` (`name`, `description`, `hint`) | 可用命令列表 |
| `system` | `msgId`, `content`, `level`, `timestamp?` | 系统通知 |
| `error` | `msgId`, `code`, `message`, `timestamp?` | 错误消息 |
| `offline_messages` | `count` | 离线消息开始投递 |
| `offline_done` | — | 离线消息投递完毕 |
| `typing` | `clientId`, `typing` | 对方输入状态 |

### 常见问题

| 问题 | 排查 |
|------|------|
| 无法连接 | 确认手机和服务器在同一局域网，检查 IP 和端口是否可达 |
| 认证失败 | APP 中 Token 需与网关 `channels.p2p.token` 和 `plugins.entries.openclaw-p2p.config.token` 一致 |
| 插件未启动 | 执行 `openclaw logs` 查看是否有 "P2P plugin started" 和端口绑定错误 |
| 图片加载失败 | 检查媒体服务器是否可达，查看 `~/.openclaw/p2p-media/` 是否有文件 |
| 首次回复慢 | 冷启动首次调用包含模型预热（5-8秒），后续回复应该很快 |
| TLS 不生效 | 确认 `cert.pem` 和 `key.pem` 在配置的路径下存在，查看网关日志是否有 "TLS enabled"，确保 APP 中 SSL 开关与服务器模式一致 |

### 开发

```bash
# 插件：编辑后重启网关
vim ~/.openclaw/extensions/openclaw-p2p/index.js
openclaw restart

# Android：编辑后重新构建
cd imrchat-android && ./gradlew assembleDebug
```

### 开源协议

MIT
