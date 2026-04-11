const signalingEndpointInput = document.querySelector('#signaling-endpoint');
const sessionIdInput = document.querySelector('#session-id');
const connectButton = document.querySelector('#connect-button');
const disconnectButton = document.querySelector('#disconnect-button');
const connectionForm = document.querySelector('#connection-form');
const remoteVideo = document.querySelector('#remote-video');
const videoFrame = document.querySelector('.video-frame');
const signalingStateText = document.querySelector('#signaling-state');
const iceStateText = document.querySelector('#ice-state');
const peerStateText = document.querySelector('#peer-state');
const logList = document.querySelector('#log-list');

const state = {
    socket: null,
    peerConnection: null,
    currentSessionId: '',
    closingLocally: false,
    pendingRemoteCandidates: [],
};

const defaultEndpoint =
    window.location.protocol === 'https:'
        ? `wss://${window.location.host}/ws`
        : `ws://${window.location.host}/ws`;

signalingEndpointInput.value = defaultEndpoint;
sessionIdInput.value = 'quest3-demo';
setSignalingState('未连接');
setIceState('未开始');
setPeerState('未开始');
syncActionButtons();
appendLog('页面已准备完成，等待连接会话');

connectionForm.addEventListener('submit', (event) => {
    event.preventDefault();
    void connect();
});

disconnectButton.addEventListener('click', () => {
    void disconnect({ notifyServer: true, reason: '浏览器手动断开' });
});

async function connect() {
    const endpoint = signalingEndpointInput.value.trim();
    const sessionId = sessionIdInput.value.trim();
    if (!endpoint || !sessionId) {
        appendLog('WebSocket 地址和 Session ID 不能为空', 'warn');
        return;
    }

    await disconnect({ notifyServer: false });

    state.currentSessionId = sessionId;
    state.closingLocally = false;
    state.pendingRemoteCandidates = [];
    setSignalingState('连接中');
    setIceState('未开始');
    setPeerState('准备中');
    appendLog(`正在连接 ${endpoint}`);

    const socket = new WebSocket(endpoint);
    state.socket = socket;
    syncActionButtons();

    socket.addEventListener('open', () => {
        appendLog(`信令已连接，加入会话 ${sessionId}`);
        setSignalingState('已连接');
        ensurePeerConnection();
        sendMessage({
            type: 'join',
            sessionId,
            role: 'receiver',
        });
        syncActionButtons();
    });

    socket.addEventListener('message', (event) => {
        void handleSignalMessage(event.data);
    });

    socket.addEventListener('close', () => {
        const status = state.closingLocally ? '已断开' : '连接已关闭';
        setSignalingState(status);
        if (!state.closingLocally) {
            appendLog('信令连接被远端关闭', 'warn');
        }
        resetPeerConnection();
        state.socket = null;
        state.closingLocally = false;
        syncActionButtons();
    });

    socket.addEventListener('error', () => {
        appendLog('信令连接出现错误', 'error');
    });
}

async function disconnect({ notifyServer, reason } = {}) {
    const socket = state.socket;
    if (!socket) {
        resetPeerConnection();
        syncActionButtons();
        return;
    }

    if (notifyServer && socket.readyState === WebSocket.OPEN && state.currentSessionId) {
        sendMessage({
            type: 'leave',
            sessionId: state.currentSessionId,
        });
    }

    if (reason) {
        appendLog(reason, notifyServer ? 'warn' : 'info');
    }

    state.closingLocally = true;
    socket.close(1000, 'receiver-close');
    resetPeerConnection();
    state.socket = null;
    syncActionButtons();
}

async function handleSignalMessage(payload) {
    let message;
    try {
        message = JSON.parse(payload);
    } catch {
        appendLog('收到无法解析的信令消息', 'error');
        return;
    }

    switch (message.type) {
        case 'offer':
            appendLog('已收到 Offer，开始创建 Answer');
            await applyOffer(message);
            break;
        case 'ice-candidate':
            await applyRemoteCandidate(message);
            break;
        case 'leave':
            appendLog('sender 已离开当前会话', 'warn');
            await disconnect({ notifyServer: false, reason: '会话已结束，请重新连接' });
            break;
        case 'error':
            appendLog(`服务端错误：${message.message}`, 'error');
            await disconnect({ notifyServer: false, reason: '服务端返回错误，已断开当前连接' });
            break;
        default:
            appendLog(`收到未处理的消息类型：${message.type}`, 'warn');
    }
}

function ensurePeerConnection() {
    if (state.peerConnection) {
        return state.peerConnection;
    }

    const peerConnection = new RTCPeerConnection({
        iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
    });

    peerConnection.addEventListener('icecandidate', (event) => {
        if (!event.candidate) {
            return;
        }

        sendMessage({
            type: 'ice-candidate',
            sessionId: state.currentSessionId,
            candidate: event.candidate.candidate,
            sdpMid: event.candidate.sdpMid ?? '0',
            sdpMLineIndex: event.candidate.sdpMLineIndex,
        });
    });

    peerConnection.addEventListener('track', (event) => {
        const [stream] = event.streams;
        if (stream) {
            remoteVideo.srcObject = stream;
        } else {
            remoteVideo.srcObject = new MediaStream([event.track]);
        }
        videoFrame.classList.add('is-live');
        appendLog(`已收到远端${event.track.kind === 'audio' ? '音频' : '视频'}轨`);
        void tryPlayRemoteMedia();
    });

    peerConnection.addEventListener('iceconnectionstatechange', () => {
        setIceState(peerConnection.iceConnectionState);
    });

    peerConnection.addEventListener('connectionstatechange', () => {
        setPeerState(peerConnection.connectionState);
        if (peerConnection.connectionState === 'failed') {
            appendLog('PeerConnection 已失败', 'error');
        }
    });

    peerConnection.addEventListener('signalingstatechange', () => {
        appendLog(`SignalingState -> ${peerConnection.signalingState}`);
    });

    state.peerConnection = peerConnection;
    return peerConnection;
}

async function applyOffer(message) {
    const peerConnection = ensurePeerConnection();
    await peerConnection.setRemoteDescription({
        type: 'offer',
        sdp: message.sdp,
    });
    await flushPendingRemoteCandidates();

    const answer = await peerConnection.createAnswer();
    await peerConnection.setLocalDescription(answer);
    sendMessage({
        type: 'answer',
        sessionId: state.currentSessionId,
        sdp: answer.sdp,
    });
    setPeerState('已应答');
    appendLog('Answer 已发送，等待 ICE 建立');
}

async function applyRemoteCandidate(message) {
    const candidate = new RTCIceCandidate({
        candidate: message.candidate,
        sdpMid: message.sdpMid,
        sdpMLineIndex: message.sdpMLineIndex,
    });

    const peerConnection = ensurePeerConnection();
    if (!peerConnection.remoteDescription) {
        state.pendingRemoteCandidates.push(candidate);
        appendLog('远端 ICE 已缓存，等待 Offer 落地');
        return;
    }

    await peerConnection.addIceCandidate(candidate);
    appendLog('已应用远端 ICE Candidate');
}

async function flushPendingRemoteCandidates() {
    if (!state.peerConnection) {
        return;
    }

    for (const candidate of state.pendingRemoteCandidates) {
        await state.peerConnection.addIceCandidate(candidate);
    }
    state.pendingRemoteCandidates = [];
}

function sendMessage(message) {
    if (!state.socket || state.socket.readyState !== WebSocket.OPEN) {
        return;
    }
    state.socket.send(JSON.stringify(message));
}

function resetPeerConnection() {
    state.pendingRemoteCandidates = [];
    if (state.peerConnection) {
        state.peerConnection.close();
        state.peerConnection = null;
    }
    remoteVideo.srcObject = null;
    videoFrame.classList.remove('is-live');
    setIceState('未开始');
    setPeerState('未开始');
}

async function tryPlayRemoteMedia() {
    try {
        remoteVideo.muted = false;
        await remoteVideo.play();
    } catch (error) {
        appendLog(`浏览器拦截了自动播放：${error instanceof Error ? error.message : '请手动点击播放'}`, 'warn');
    }
}

function setSignalingState(value) {
    signalingStateText.textContent = value;
}

function setIceState(value) {
    iceStateText.textContent = value;
}

function setPeerState(value) {
    peerStateText.textContent = value;
}

function syncActionButtons() {
    const connected = state.socket && state.socket.readyState <= WebSocket.OPEN;
    connectButton.disabled = Boolean(connected);
    disconnectButton.disabled = !connected;
    signalingEndpointInput.disabled = Boolean(connected);
    sessionIdInput.disabled = Boolean(connected);
}

function appendLog(message, level = 'info') {
    const item = document.createElement('li');
    const timestamp = new Date().toLocaleTimeString('zh-CN', { hour12: false });
    item.className = level;
    item.textContent = `[${timestamp}] ${message}`;
    logList.prepend(item);

    while (logList.children.length > 40) {
        logList.removeChild(logList.lastElementChild);
    }
}
