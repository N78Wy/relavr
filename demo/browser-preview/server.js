import { startBrowserPreviewServer } from './src/browserPreviewServer.js';

const host = process.env.HOST ?? '0.0.0.0';
const rawPort = process.env.PORT ?? '8080';
const port = Number.parseInt(rawPort, 10);

if (!Number.isInteger(port) || port <= 0) {
    throw new Error(`PORT 必须是正整数，当前值为: ${rawPort}`);
}

const server = await startBrowserPreviewServer({ host, port });

console.log(`浏览器预览页已启动: http://${host}:${server.address.port}/`);
console.log(`WebSocket 信令地址: ws://${host}:${server.address.port}/ws`);

let shuttingDown = false;

async function shutdown(signal) {
    if (shuttingDown) {
        return;
    }
    shuttingDown = true;
    console.log(`收到 ${signal}，正在关闭浏览器预览 demo`);
    await server.close();
    process.exit(0);
}

process.on('SIGINT', () => {
    void shutdown('SIGINT');
});

process.on('SIGTERM', () => {
    void shutdown('SIGTERM');
});
