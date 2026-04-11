import test from 'node:test';
import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';
import WebSocket from 'ws';
import { startBrowserPreviewServer } from '../src/browserPreviewServer.js';

test('HTTP 页面和 WebSocket 信令链路可以正常工作', async () => {
    const server = await startBrowserPreviewServer({
        host: '127.0.0.1',
        port: 0,
        logger: silentLogger,
    });

    try {
        const baseUrl = `http://127.0.0.1:${server.address.port}`;
        const html = await fetch(`${baseUrl}/`).then((response) => response.text());
        assert.match(html, /Quest 3 浏览器预览/);

        const senderSocket = await openSocket(`${baseUrl.replace('http', 'ws')}/ws`);
        const sender = createMessageHarness(senderSocket);
        sender.socket.send(JSON.stringify({ type: 'join', sessionId: 'quest3-demo', role: 'sender' }));
        sender.socket.send(JSON.stringify({ type: 'offer', sessionId: 'quest3-demo', sdp: 'offer-sdp' }));
        sender.socket.send(
            JSON.stringify({
                type: 'ice-candidate',
                sessionId: 'quest3-demo',
                candidate: 'candidate-sender',
                sdpMid: '0',
                sdpMLineIndex: 0,
            }),
        );

        const receiverSocket = await openSocket(`${baseUrl.replace('http', 'ws')}/ws`);
        const receiver = createMessageHarness(receiverSocket);
        receiver.socket.send(JSON.stringify({ type: 'join', sessionId: 'quest3-demo', role: 'receiver' }));

        assert.deepEqual(await receiver.nextMessage(), {
            type: 'offer',
            sessionId: 'quest3-demo',
            sdp: 'offer-sdp',
        });
        assert.deepEqual(await receiver.nextMessage(), {
            type: 'ice-candidate',
            sessionId: 'quest3-demo',
            candidate: 'candidate-sender',
            sdpMid: '0',
            sdpMLineIndex: 0,
        });

        receiver.socket.send(JSON.stringify({ type: 'answer', sessionId: 'quest3-demo', sdp: 'answer-sdp' }));
        receiver.socket.send(
            JSON.stringify({
                type: 'ice-candidate',
                sessionId: 'quest3-demo',
                candidate: 'candidate-receiver',
                sdpMid: '0',
                sdpMLineIndex: 0,
            }),
        );

        assert.deepEqual(await sender.nextMessage(), {
            type: 'answer',
            sessionId: 'quest3-demo',
            sdp: 'answer-sdp',
        });
        assert.deepEqual(await sender.nextMessage(), {
            type: 'ice-candidate',
            sessionId: 'quest3-demo',
            candidate: 'candidate-receiver',
            sdpMid: '0',
            sdpMLineIndex: 0,
        });

        receiver.socket.close(1000, 'receiver-close');
        assert.deepEqual(await sender.nextMessage(), {
            type: 'leave',
            sessionId: 'quest3-demo',
        });

        sender.socket.close(1000, 'sender-close');
        await delay(20);
    } finally {
        await server.close();
    }
});

function openSocket(url) {
    return new Promise((resolve, reject) => {
        const socket = new WebSocket(url);
        socket.once('open', () => resolve(socket));
        socket.once('error', reject);
    });
}

function createMessageHarness(socket) {
    const bufferedMessages = [];
    const waitingResolvers = [];

    socket.on('message', (payload) => {
        const message = JSON.parse(payload.toString());
        const resolver = waitingResolvers.shift();
        if (resolver) {
            resolver.resolve(message);
            return;
        }
        bufferedMessages.push(message);
    });

    socket.on('error', (error) => {
        const resolver = waitingResolvers.shift();
        if (resolver) {
            resolver.reject(error);
        }
    });

    return {
        socket,
        nextMessage(timeoutMs = 2000) {
            if (bufferedMessages.length > 0) {
                return Promise.resolve(bufferedMessages.shift());
            }

            return new Promise((resolve, reject) => {
                const timeout = setTimeout(() => {
                    waitingResolvers.splice(waitingResolvers.indexOf(entry), 1);
                    reject(new Error('等待 WebSocket 消息超时'));
                }, timeoutMs);

                const entry = {
                    resolve(message) {
                        clearTimeout(timeout);
                        resolve(message);
                    },
                    reject(error) {
                        clearTimeout(timeout);
                        reject(error);
                    },
                };

                waitingResolvers.push(entry);
            });
        },
    };
}

const silentLogger = {
    info() {},
};
