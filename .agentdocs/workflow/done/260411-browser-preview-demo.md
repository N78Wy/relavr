## 背景
- 当前仓库已经完成 Quest 3 sender 的 MediaProjection -> WebRTC 视频推流闭环，但仍缺少可直接验证的浏览器接收端与 demo 级信令服务。
- 用户希望能在浏览器中直接看到 Quest 3 推出来的视频流，当前使用场景限定为本地或同一局域网内预览。
- 本轮选择新增轻量 Node demo 子工程，而不是把浏览器接收能力塞进 Android 工程或额外引入完整前端框架。

## 分阶段计划
1. 新建任务文档并更新索引，明确 demo 范围、协议复用策略与校验要求。
2. 新增 `demo/browser-preview` Node 工程，实现静态浏览器接收页与 WebSocket 信令服务。
3. 补充 Node 单元测试、集成测试，以及 Android sender 侧接入提示文案。
4. 更新技术文档与执行约束，补齐 Node 工程的验证要求。
5. 执行 Node 与 Android 校验，记录结果并完成归档。

## TODO
- [x] 记录本轮任务背景、阶段与核心约束。
- [x] 实现浏览器接收页和 demo 信令服务。
- [x] 补充 Node 单元测试与集成测试。
- [x] 更新 Android sender 文案和相关文档记忆。
- [x] 执行 `npm` 与 `Gradle` 校验并记录结果。

## 关键约束
- 继续复用 sender 现有 `WebSocket + JSON Offer/Answer` 协议，不改动 Android 已实现的消息结构。
- demo 仅支持单个 `sessionId` 下的一发一收，不实现多观看者、鉴权、TURN、录制或公网部署能力。
- 浏览器页只处理视频轨，音频继续沿用 sender 当前未接入的限制。
- 浏览器预览入口需兼容本机与局域网访问，sender 的 `signalingEndpoint` 必须允许填开发机局域网地址。

## 实现结果
- 新增 `demo/browser-preview` Node 子工程，提供静态浏览器 receiver 页面、同源静态资源服务与 `/ws` WebSocket 信令入口。
- demo 信令服务按 `sessionId` 管理一发一收会话，拒绝重复角色加入，并在 receiver 晚于 sender 加入时补发缓存的 `offer` 与 sender ICE candidate。
- 浏览器页使用原生 WebRTC API，负责 `join(receiver)`、应用远端 `offer`、回传 `answer`、双向交换 ICE、展示连接状态与日志。
- Android 发送控制台补充了 Quest 3 实机的局域网地址提示，明确 `10.0.2.2` 仅适用于模拟器。
- 为了让本机 lint 能稳定执行，`gradle.properties` 将 Gradle JVM 堆上限从 `4g` 调整为 `1536m`。

## 验证记录
- `cd demo/browser-preview && npm run format:check`：通过。
- `cd demo/browser-preview && npm run lint`：通过。
- `cd demo/browser-preview && npm run test`：通过；其中集成测试在沙箱外执行，因为需要监听本机 HTTP / WebSocket 端口。
- `./gradlew spotlessCheck`：通过。
- `./gradlew lintDebug`：通过；在降低 Gradle 堆上限后稳定完成。
- `./gradlew testDebugUnitTest`：通过。
