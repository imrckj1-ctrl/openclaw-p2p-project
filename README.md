# OpenClaw P2P Chat

[English](#english) | [中文](#中文)

---

<a name="english"></a>

## English

### Overview

OpenClaw P2P Chat is a lightweight real-time chat system built on the [OpenClaw](https://github.com/anthropics/openclaw) gateway platform. It consists of two components:

- **openclaw-p2p** — A WebSocket plugin for the OpenClaw gateway that bridges AI model responses to connected clients over a persistent WebSocket connection, with built-in HTTP media server for file/image delivery.
- **imrchat-android** — A native Android chat application built with Jetpack Compose, providing a clean mobile interface for interacting with AI models through the P2P plugin.

### Features

**P2P Plugin (Server)**
- WebSocket server with token-based authentication
- Real-time streaming of AI responses with optimized chunk delivery (80-char chunks, 8ms interval)
- Thinking/reasoning content parsing and streaming (`<think>` tag support)
- HTTP media server for serving images and files over the internet
- Offline message caching (configurable limit)
- File and image upload support (up to 50MB files, 10MB images)
- 64KB chunked file transfer

**Android App (Client)**
- Material 3 design with Jetpack Compose
- Real-time message streaming with live typing indicators
- Collapsible thinking/reasoning content display
- Image and file sending/receiving with Coil image loading
- Markdown rendering in messages
- Local SQLite storage via Room
- Configurable server connection settings

### Architecture

```
┌─────────────────┐    WebSocket     ┌──────────────────┐    OpenClaw API    ┌──────────────┐
│  Android App    │◄────────────────►│  P2P Plugin      │◄──────────────────►│  AI Models   │
│  (imrchat)      │    port 18790    │  (openclaw-p2p)  │                    │  (LLM APIs)  │
└─────────────────┘                  └──────────────────┘                    └──────────────┘
                                         │
                                         │ HTTP (media)
                                         ▼
                                    ┌──────────────┐
                                    │  Media Files  │
                                    │  ~/.openclaw/ │
                                    │  p2p-media/   │
                                    └──────────────┘
```

### Directory Structure

```
openclaw-p2p-project/
├── openclaw-p2p/                  # OpenClaw WebSocket plugin
│   ├── index.js                   # Plugin entry point
│   ├── package.json               # Node.js dependencies
│   └── openclaw.plugin.json       # Plugin manifest
├── imrchat-android/               # Android application
│   ├── app/
│   │   ├── build.gradle.kts       # App-level build config
│   │   └── src/main/java/com/imr/chat/
│   │       ├── network/           # WebSocket client & protocol
│   │       ├── ui/                # Compose UI screens
│   │       ├── data/              # Database & settings
│   │       └── service/           # Background service
│   ├── build.gradle.kts           # Project-level build config
│   └── settings.gradle.kts        # Gradle settings
├── 开发任务.md                     # Development task tracking
└── 设计方案与待确认问题.md           # Design decisions & open questions
```

### Prerequisites

- [OpenClaw Gateway](https://github.com/anthropics/openclaw) v2026.5.12+
- Node.js 18+
- Android Studio (for building the Android app)
- JDK 17

### Installation

#### 1. Plugin Setup

```bash
# Copy plugin to OpenClaw extensions directory
cp -r openclaw-p2p ~/.openclaw/extensions/openclaw-p2p

# Install dependencies
cd ~/.openclaw/extensions/openclaw-p2p && npm install
```

#### 2. Gateway Configuration

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

#### 3. Android App

Open `imrchat-android/` in Android Studio, build and run on your device.

In the app settings, configure:
- **Server URL**: `ws://your-server-ip:18790`
- **Token**: The same token configured in the gateway

### Plugin Configuration

| Parameter           | Type   | Default     | Description                        |
|---------------------|--------|-------------|------------------------------------|
| `port`              | number | 18790       | WebSocket server port              |
| `token`             | string | —           | Authentication token               |
| `maxImageSize`      | number | 10485760    | Max image size in bytes (10MB)     |
| `maxFileSize`       | number | 52428800    | Max file size in bytes (50MB)      |
| `chunkSize`         | number | 65536       | File transfer chunk size (64KB)    |
| `offlineCacheLimit` | number | 100         | Max cached offline messages        |

### Protocol

The plugin communicates over WebSocket using JSON messages:

```json
// Client → Server: Send message
{"type": "send", "content": "Hello!", "msgId": "uuid"}

// Server → Client: Streaming reply
{"type": "reply_start", "msgId": "uuid", "replyTo": "original-uuid", "model": "model-name"}
{"type": "reply_chunk", "msgId": "uuid", "content": "partial text"}
{"type": "reply_end", "msgId": "uuid", "fullContent": "complete response"}

// Server → Client: Thinking/reasoning content
{"type": "thinking_start", "msgId": "uuid", "replyTo": "original-uuid"}
{"type": "thinking_chunk", "msgId": "uuid", "content": "thinking text"}
{"type": "thinking_end", "msgId": "uuid", "fullContent": "full thinking"}

// Client → Server: Upload media
{"type": "upload_media", "filename": "photo.jpg", "mimeType": "image/jpeg", "size": 123456, "data": "base64..."}
```

### License

MIT

---

<a name="中文"></a>

## 中文

### 概述

OpenClaw P2P Chat 是一个基于 [OpenClaw](https://github.com/anthropics/openclaw) 网关平台构建的轻量级实时聊天系统。由两个组件组成：

- **openclaw-p2p** — OpenClaw 网关的 WebSocket 插件，通过持久 WebSocket 连接将 AI 模型响应桥接到已连接的客户端，内置 HTTP 媒体服务器用于文件/图片传输。
- **imrchat-android** — 使用 Jetpack Compose 构建的原生 Android 聊天应用，提供简洁的移动端界面，通过 P2P 插件与 AI 模型交互。

### 功能特性

**P2P 插件（服务端）**
- WebSocket 服务器，支持 Token 认证
- AI 响应实时流式传输，优化分块投递（80字符/块，8毫秒间隔）
- 思考/推理内容解析与流式传输（支持 `<think>` 标签）
- HTTP 媒体服务器，支持外网图片和文件访问
- 离线消息缓存（可配置上限）
- 文件和图片上传（文件最大 50MB，图片最大 10MB）
- 64KB 分片文件传输

**Android 应用（客户端）**
- Material 3 设计，Jetpack Compose 构建
- 实时消息流式传输，实时打字指示器
- 可折叠的思考/推理内容展示
- 图片和文件收发，Coil 图片加载
- 消息内 Markdown 渲染
- Room 本地 SQLite 存储
- 可配置的服务器连接设置

### 架构

```
┌─────────────────┐    WebSocket     ┌──────────────────┐    OpenClaw API    ┌──────────────┐
│  Android 应用   │◄────────────────►│  P2P 插件        │◄──────────────────►│  AI 模型     │
│  (imrchat)      │    端口 18790    │  (openclaw-p2p)  │                    │  (大模型API) │
└─────────────────┘                  └──────────────────┘                    └──────────────┘
                                         │
                                         │ HTTP（媒体服务）
                                         ▼
                                    ┌──────────────┐
                                    │  媒体文件     │
                                    │  ~/.openclaw/ │
                                    │  p2p-media/   │
                                    └──────────────┘
```

### 目录结构

```
openclaw-p2p-project/
├── openclaw-p2p/                  # OpenClaw WebSocket 插件
│   ├── index.js                   # 插件入口
│   ├── package.json               # Node.js 依赖
│   └── openclaw.plugin.json       # 插件清单
├── imrchat-android/               # Android 应用
│   ├── app/
│   │   ├── build.gradle.kts       # 应用构建配置
│   │   └── src/main/java/com/imr/chat/
│   │       ├── network/           # WebSocket 客户端与协议
│   │       ├── ui/                # Compose UI 界面
│   │       ├── data/              # 数据库与设置
│   │       └── service/           # 后台服务
│   ├── build.gradle.kts           # 项目构建配置
│   └── settings.gradle.kts        # Gradle 设置
├── 开发任务.md                     # 开发任务追踪
└── 设计方案与待确认问题.md           # 设计方案与待确认问题
```

### 环境要求

- [OpenClaw Gateway](https://github.com/anthropics/openclaw) v2026.5.12+
- Node.js 18+
- Android Studio（构建 Android 应用）
- JDK 17

### 安装部署

#### 1. 插件安装

```bash
# 复制插件到 OpenClaw 扩展目录
cp -r openclaw-p2p ~/.openclaw/extensions/openclaw-p2p

# 安装依赖
cd ~/.openclaw/extensions/openclaw-p2p && npm install
```

#### 2. 网关配置

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

#### 3. Android 应用

在 Android Studio 中打开 `imrchat-android/`，构建并安装到设备。

在应用设置中配置：
- **服务器地址**：`ws://你的服务器IP:18790`
- **Token**：与网关配置中相同的密钥

### 插件参数

| 参数                | 类型   | 默认值      | 说明                             |
|---------------------|--------|-------------|----------------------------------|
| `port`              | number | 18790       | WebSocket 服务器端口             |
| `token`             | string | —           | 认证密钥                         |
| `maxImageSize`      | number | 10485760    | 图片最大字节数（10MB）           |
| `maxFileSize`       | number | 52428800    | 文件最大字节数（50MB）           |
| `chunkSize`         | number | 65536       | 文件传输分片大小（64KB）         |
| `offlineCacheLimit` | number | 100         | 离线消息缓存上限                 |

### 通信协议

插件通过 WebSocket 使用 JSON 消息通信：

```json
// 客户端 → 服务端：发送消息
{"type": "send", "content": "你好！", "msgId": "uuid"}

// 服务端 → 客户端：流式回复
{"type": "reply_start", "msgId": "uuid", "replyTo": "原始uuid", "model": "模型名"}
{"type": "reply_chunk", "msgId": "uuid", "content": "部分内容"}
{"type": "reply_end", "msgId": "uuid", "fullContent": "完整回复"}

// 服务端 → 客户端：思考/推理内容
{"type": "thinking_start", "msgId": "uuid", "replyTo": "原始uuid"}
{"type": "thinking_chunk", "msgId": "uuid", "content": "思考文本"}
{"type": "thinking_end", "msgId": "uuid", "fullContent": "完整思考"}

// 客户端 → 服务端：上传媒体
{"type": "upload_media", "filename": "photo.jpg", "mimeType": "image/jpeg", "size": 123456, "data": "base64..."}
```

### 开源协议

MIT
