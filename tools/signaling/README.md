# Relavr 信令服务与应答端

用于 Quest 3 发送端真机联调的 WebSocket 信令服务和 WebRTC 应答端。

## 协议格式

所有消息均为 JSON 格式：

| type          | 必需字段                                  | 说明           |
|---------------|------------------------------------------|----------------|
| `join`        | `sessionId`, `role`                      | 加入会话       |
| `offer`       | `sessionId`, `sdp`                       | SDP Offer      |
| `answer`      | `sessionId`, `sdp`                       | SDP Answer     |
| `ice-candidate` | `sessionId`, `candidate`, `sdpMid`, `sdpMLineIndex` | ICE 候选 |
| `leave`       | `sessionId`                              | 离开会话       |
| `error`       | `sessionId`, `message`                   | 错误消息       |

## 快速开始

```bash
# 安装依赖
cd tools/signaling
npm install

# 启动信令服务器（终端 1）
npm start

# 启动应答端（终端 2，先启动信令服务器）
npm run answerer
```

## 环境变量

### server.js

| 变量    | 默认值  | 说明         |
|---------|--------|--------------|
| `PORT`  | `8765` | 监听端口     |

### answerer.js

| 变量            | 默认值              | 说明               |
|-----------------|--------------------|--------------------|
| `SIGNALING_URL` | `ws://localhost:8765` | 信令服务器地址   |
| `SESSION_ID`    | `test-session`     | 会话 ID            |

## 使用流程

1. 启动信令服务器：`npm start`
2. 启动应答端：`npm run answerer`（或指定会话 ID：`SESSION_ID=my-session npm run answerer`）
3. 在 Quest 3 发送端输入相同的 WebSocket 地址和会话 ID
4. 发送端发起推流，应答端接收视频轨道

## 示例输出

```
信令服务器已启动: ws://0.0.0.0:8765
等待连接...

[12:34:56.789] ← [test-session] join(sender)
[12:34:56.790] ← [test-session] join(receiver)
[12:34:57.123] ← [test-session] offer
[12:34:57.124] → [test-session] offer (forward to receiver)
[12:34:57.456] ← [test-session] answer
[12:34:57.457] → [test-session] answer (forward to sender)
[12:34:57.789] ← [test-session] ice-candidate
...
```

## 注意事项

- 应答端仅接收视频轨道，不进行渲染（适合验证连通性）
- 如需查看视频画面，可使用支持 WebRTC 的浏览器客户端
- 会话 ID 必须在发送端和应答端保持一致
