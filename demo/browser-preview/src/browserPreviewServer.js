import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { WebSocket, WebSocketServer } from 'ws';
import { SessionRegistry } from './sessionRegistry.js';

const DEFAULT_PUBLIC_DIR = fileURLToPath(new URL('../public/', import.meta.url));
const MIME_TYPES = new Map([
    ['.html', 'text/html; charset=utf-8'],
    ['.css', 'text/css; charset=utf-8'],
    ['.js', 'text/javascript; charset=utf-8'],
    ['.json', 'application/json; charset=utf-8'],
]);

export function createBrowserPreviewServer({
    host = '0.0.0.0',
    port = 8080,
    publicDir = DEFAULT_PUBLIC_DIR,
    logger = console,
} = {}) {
    const registry = new SessionRegistry();
    const peerBindings = new Map();
    const resolvedPublicDir = path.resolve(publicDir);
    const webSocketServer = new WebSocketServer({ noServer: true });

    const httpServer = createServer(async (request, response) => {
        if (request.method !== 'GET' && request.method !== 'HEAD') {
            response.writeHead(405, { 'content-type': 'text/plain; charset=utf-8' });
            response.end('Method Not Allowed');
            return;
        }

        try {
            const filePath = resolvePublicFile(resolvedPublicDir, request.url);
            const fileBuffer = await readFile(filePath);
            response.writeHead(200, {
                'cache-control': 'no-store',
                'content-type': MIME_TYPES.get(path.extname(filePath)) ?? 'application/octet-stream',
            });
            if (request.method === 'GET') {
                response.end(fileBuffer);
            } else {
                response.end();
            }
        } catch (error) {
            const statusCode = error.code === 'ENOENT' ? 404 : 500;
            response.writeHead(statusCode, { 'content-type': 'text/plain; charset=utf-8' });
            response.end(statusCode === 404 ? 'Not Found' : 'Internal Server Error');
        }
    });

    httpServer.on('upgrade', (request, socket, head) => {
        const requestUrl = new URL(request.url ?? '/', `http://${request.headers.host ?? 'localhost'}`);
        if (requestUrl.pathname !== '/ws') {
            socket.destroy();
            return;
        }

        webSocketServer.handleUpgrade(request, socket, head, (webSocket) => {
            webSocketServer.emit('connection', webSocket, request);
        });
    });

    webSocketServer.on('connection', (webSocket) => {
        webSocket.on('message', (payload) => {
            void handleIncomingMessage(webSocket, payload.toString());
        });

        webSocket.on('close', () => {
            const binding = peerBindings.get(webSocket);
            if (!binding) {
                return;
            }

            const result = registry.leave(binding);
            applyRegistryResult(result);
            logger.info?.(`[browser-preview] ${binding.role} 已断开: ${binding.sessionId}`);
        });
    });

    async function handleIncomingMessage(webSocket, payload) {
        let message;
        try {
            message = parseMessage(payload);
        } catch (error) {
            sendJson(webSocket, {
                type: 'error',
                sessionId: 'invalid-session',
                message: error.message,
            });
            return;
        }

        if (message.type === 'join') {
            handleJoin(webSocket, message);
            return;
        }

        const binding = peerBindings.get(webSocket);
        if (!binding) {
            sendJson(webSocket, {
                type: 'error',
                sessionId: message.sessionId,
                message: '请先发送 join 消息',
            });
            return;
        }

        if (binding.sessionId !== message.sessionId) {
            sendJson(webSocket, {
                type: 'error',
                sessionId: binding.sessionId,
                message: 'sessionId 与当前连接不一致',
            });
            return;
        }

        try {
            const result = registry.relay({
                sessionId: binding.sessionId,
                role: binding.role,
                message,
            });
            applyRegistryResult(result);
        } catch (error) {
            sendJson(webSocket, {
                type: 'error',
                sessionId: binding.sessionId,
                message: error.message,
            });
        }
    }

    function handleJoin(webSocket, message) {
        if (message.role !== 'sender' && message.role !== 'receiver') {
            sendJson(webSocket, {
                type: 'error',
                sessionId: message.sessionId,
                message: 'role 必须是 sender 或 receiver',
            });
            return;
        }

        if (peerBindings.has(webSocket)) {
            const binding = peerBindings.get(webSocket);
            sendJson(webSocket, {
                type: 'error',
                sessionId: binding.sessionId,
                message: '当前连接已经加入会话，请先断开后重连',
            });
            return;
        }

        try {
            const binding = {
                sessionId: message.sessionId,
                role: message.role,
            };
            const result = registry.join({
                sessionId: binding.sessionId,
                role: binding.role,
                peer: webSocket,
            });
            peerBindings.set(webSocket, binding);
            applyRegistryResult(result);
            logger.info?.(`[browser-preview] ${binding.role} 已加入: ${binding.sessionId}`);
        } catch (error) {
            sendJson(webSocket, {
                type: 'error',
                sessionId: message.sessionId,
                message: error.message,
            });
        }
    }

    function applyRegistryResult(result) {
        for (const delivery of result.deliveries) {
            sendJson(delivery.peer, delivery.message);
        }
        for (const peer of result.unboundPeers) {
            peerBindings.delete(peer);
        }
    }

    return {
        host,
        port,
        httpServer,
        async listen() {
            await new Promise((resolve, reject) => {
                httpServer.once('error', reject);
                httpServer.listen(port, host, () => {
                    httpServer.off('error', reject);
                    resolve();
                });
            });

            return this.address;
        },
        async close() {
            for (const client of webSocketServer.clients) {
                if (client.readyState === WebSocket.OPEN) {
                    client.close(1001, 'server-shutdown');
                }
            }

            await new Promise((resolve, reject) => {
                webSocketServer.close((error) => {
                    if (error) {
                        reject(error);
                        return;
                    }
                    resolve();
                });
            });

            await new Promise((resolve, reject) => {
                httpServer.close((error) => {
                    if (error) {
                        reject(error);
                        return;
                    }
                    resolve();
                });
            });
        },
        get address() {
            const address = httpServer.address();
            if (address === null || typeof address === 'string') {
                return {
                    address: host,
                    family: 'IPv4',
                    port,
                };
            }
            return address;
        },
    };
}

export async function startBrowserPreviewServer(options = {}) {
    const server = createBrowserPreviewServer(options);
    await server.listen();
    return server;
}

function resolvePublicFile(publicDir, requestUrl) {
    const url = new URL(requestUrl ?? '/', 'http://localhost');
    const pathname = url.pathname === '/' ? '/index.html' : url.pathname;
    const decodedPath = decodeURIComponent(pathname);
    const filePath = path.resolve(publicDir, `.${decodedPath}`);

    if (!filePath.startsWith(publicDir)) {
        const error = new Error('非法路径');
        error.code = 'EACCES';
        throw error;
    }

    return filePath;
}

function parseMessage(payload) {
    let message;
    try {
        message = JSON.parse(payload);
    } catch {
        throw new Error('信令消息必须是合法 JSON');
    }

    if (message === null || typeof message !== 'object' || Array.isArray(message)) {
        throw new Error('信令消息必须是 JSON 对象');
    }

    if (typeof message.type !== 'string' || message.type.length === 0) {
        throw new Error('信令消息缺少 type');
    }

    if (typeof message.sessionId !== 'string' || message.sessionId.trim().length === 0) {
        throw new Error('信令消息缺少 sessionId');
    }

    return message;
}

function sendJson(webSocket, message) {
    if (webSocket.readyState !== WebSocket.OPEN) {
        return;
    }
    webSocket.send(JSON.stringify(message));
}
