import { createServer } from "http";
import { readFile } from "fs/promises";
import { WebSocketServer } from "ws";

const PORT = process.env.PORT ? parseInt(process.env.PORT) : 8765;
const server = createServer(async (req, res) => {
  if (req.url === "/" || req.url === "/viewer.html") {
    try {
      const html = await readFile(new URL("./viewer.html", import.meta.url));
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
      res.end(html);
    } catch (err) {
      res.writeHead(404);
      res.end("viewer.html not found");
    }
  } else {
    res.writeHead(404);
    res.end("Not found");
  }
});

const wss = new WebSocketServer({ server });

const sessions = new Map();

function log(direction, sessionId, type, details = "") {
  const timestamp = new Date().toISOString().split("T")[1].slice(0, 12);
  console.log(`[${timestamp}] ${direction} [${sessionId}] ${type}${details ? " " + details : ""}`);
}

function parseMessage(data) {
  const text = data.toString();
  const obj = {};
  let i = 1;
  while (i < text.length) {
    if (text[i] === "}") break;
    let key = "";
    i++;
    while (text[i] !== '"' && i < text.length) key += text[i++];
    i += 3;
    let value = "";
    if (text[i] === '"') {
      i++;
      while (text[i] !== '"' || text[i - 1] === "\\") {
        value += text[i];
        i++;
      }
      i++;
    } else {
      while (text[i] !== "," && text[i] !== "}" && i < text.length) {
        value += text[i];
        i++;
      }
    }
    obj[key] = value;
    if (text[i] === ",") i++;
    while (text[i] === " " || text[i] === "\n" || text[i] === "\r" || text[i] === "\t") i++;
  }
  return obj;
}

wss.on("connection", (ws) => {
  let currentSession = null;
  let currentRole = null;

  ws.on("message", (data) => {
    const msg = parseMessage(data);
    const { type, sessionId, role, sdp, candidate, sdpMid, sdpMLineIndex, message } = msg;

    if (!sessionId) {
      console.warn("收到无 sessionId 的消息，忽略");
      return;
    }

    currentSession = sessionId;
    currentRole = role || currentRole;

    if (!sessions.has(sessionId)) {
      sessions.set(sessionId, { sender: null, receiver: null });
    }
    const session = sessions.get(sessionId);

    if (type === "join") {
      log("←", sessionId, `join(${role || "unknown"})`);
      if (role === "sender") {
        session.sender = ws;
      } else if (role === "receiver") {
        session.receiver = ws;
      }
      return;
    }

    log("←", sessionId, type);

    if (type === "offer" && session.receiver && session.receiver.readyState === 1) {
      log("→", sessionId, "offer", "(forward to receiver)");
      session.receiver.send(data.toString());
    } else if (type === "answer" && session.sender && session.sender.readyState === 1) {
      log("→", sessionId, "answer", "(forward to sender)");
      session.sender.send(data.toString());
    } else if (type === "ice-candidate") {
      if (currentRole === "sender" && session.receiver && session.receiver.readyState === 1) {
        log("→", sessionId, "ice-candidate", "(forward to receiver)");
        session.receiver.send(data.toString());
      } else if (session.sender && session.sender.readyState === 1) {
        log("→", sessionId, "ice-candidate", "(forward to sender)");
        session.sender.send(data.toString());
      }
    } else if (type === "leave") {
      const peer = currentRole === "sender" ? session.receiver : session.sender;
      if (peer && peer.readyState === 1) {
        log("→", sessionId, "leave", "(forward to peer)");
        peer.send(data.toString());
      }
    }
  });

  ws.on("close", () => {
    if (currentSession) {
      log("✕", currentSession, `disconnect(${currentRole || "unknown"})`);
      const session = sessions.get(currentSession);
      if (session) {
        if (currentRole === "sender") session.sender = null;
        if (currentRole === "receiver") session.receiver = null;
        if (!session.sender && !session.receiver) {
          sessions.delete(currentSession);
        }
      }
    }
  });

  ws.on("error", (err) => {
    console.error("WebSocket 错误:", err.message);
  });
});

server.listen(PORT, () => {
  console.log(`信令服务器已启动: ws://0.0.0.0:${PORT}`);
  console.log(`浏览器测试页面: http://localhost:${PORT}/`);
  console.log("等待连接...\n");
});