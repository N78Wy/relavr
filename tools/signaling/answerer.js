import WebSocket from "ws";
import { RTCPeerConnection, RTCSessionDescription, RTCIceCandidate, MediaStreamTrack } from "werift";

const SIGNALING_URL = process.env.SIGNALING_URL || "ws://localhost:8765";
const SESSION_ID = process.env.SESSION_ID || "test-session";

function log(direction, type, details = "") {
  const timestamp = new Date().toISOString().split("T")[1].slice(0, 12);
  console.log(`[${timestamp}] ${direction} ${type}${details ? " " + details : ""}`);
}

function escapeJson(str) {
  return str
    .replace(/\\/g, "\\\\")
    .replace(/"/g, '\\"')
    .replace(/\b/g, "\\b")
    .replace(/\f/g, "\\f")
    .replace(/\n/g, "\\n")
    .replace(/\r/g, "\\r")
    .replace(/\t/g, "\\t");
}

function encodeMessage(msg) {
  const parts = [`"type":"${msg.type}"`, `"sessionId":"${msg.sessionId}"`];
  if (msg.role) parts.push(`"role":"${msg.role}"`);
  if (msg.sdp) parts.push(`"sdp":"${escapeJson(msg.sdp)}"`);
  if (msg.candidate !== undefined) parts.push(`"candidate":"${escapeJson(msg.candidate)}"`);
  if (msg.sdpMid) parts.push(`"sdpMid":"${msg.sdpMid}"`);
  if (msg.sdpMLineIndex !== undefined) parts.push(`"sdpMLineIndex":${msg.sdpMLineIndex}`);
  if (msg.message) parts.push(`"message":"${escapeJson(msg.message)}"`);
  return `{${parts.join(",")}}`;
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

async function main() {
  console.log(`连接信令服务器: ${SIGNALING_URL}`);
  console.log(`会话 ID: ${SESSION_ID}\n`);

  const ws = new WebSocket(SIGNALING_URL);
  const pc = new RTCPeerConnection({
    iceServers: [{ urls: "stun:stun.l.google.com:19302" }],
  });

  let hasVideo = false;

  pc.onTrack.subscribe((track) => {
    if (track.kind === "video" && !hasVideo) {
      hasVideo = true;
      log("✓", "video track received");
      console.log("\n视频轨道已接收，正在接收视频流...\n");
    }
  });

  pc.connectionStateChange.subscribe((state) => {
    log("◆", `connection state: ${state}`);
  });

  pc.iceConnectionStateChange.subscribe((state) => {
    log("◆", `ICE state: ${state}`);
  });

  pc.onIceCandidate.subscribe((candidate) => {
    if (candidate) {
      const msg = {
        type: "ice-candidate",
        sessionId: SESSION_ID,
        candidate: candidate.candidate,
        sdpMid: candidate.sdpMid || "",
        sdpMLineIndex: candidate.sdpMLineIndex ?? 0,
      };
      log("→", "ice-candidate");
      ws.send(encodeMessage(msg));
    }
  });

  ws.on("open", () => {
    log("✓", "connected to signaling server");
    const joinMsg = { type: "join", sessionId: SESSION_ID, role: "receiver" };
    log("→", "join(receiver)");
    ws.send(encodeMessage(joinMsg));
  });

  ws.on("message", async (data) => {
    const msg = parseMessage(data);
    log("←", msg.type);

    if (msg.type === "offer") {
      log("◆", "setting remote description (offer)");
      await pc.setRemoteDescription(new RTCSessionDescription({ type: "offer", sdp: msg.sdp }));

      pc.addTransceiver("video", { direction: "recvonly" });

      log("◆", "creating answer");
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);

      const answerMsg = { type: "answer", sessionId: SESSION_ID, sdp: answer.sdp };
      log("→", "answer");
      ws.send(encodeMessage(answerMsg));
    } else if (msg.type === "ice-candidate") {
      log("◆", "adding ICE candidate");
      await pc.addIceCandidate(
        new RTCIceCandidate({
          candidate: msg.candidate,
          sdpMid: msg.sdpMid,
          sdpMLineIndex: parseInt(msg.sdpMLineIndex),
        })
      );
    } else if (msg.type === "leave") {
      log("✕", "sender left");
      pc.close();
      ws.close();
      process.exit(0);
    } else if (msg.type === "error") {
      log("✗", `error: ${msg.message}`);
    }
  });

  ws.on("close", () => {
    log("✕", "disconnected from signaling server");
    pc.close();
    process.exit(0);
  });

  ws.on("error", (err) => {
    log("✗", `WebSocket error: ${err.message}`);
    process.exit(1);
  });

  process.on("SIGINT", () => {
    log("◆", "shutting down");
    const leaveMsg = { type: "leave", sessionId: SESSION_ID };
    ws.send(encodeMessage(leaveMsg));
    setTimeout(() => {
      pc.close();
      ws.close();
      process.exit(0);
    }, 100);
  });
}

main().catch((err) => {
  console.error("启动失败:", err);
  process.exit(1);
});