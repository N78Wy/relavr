## 产品文档
`prd/quest3-sender.md` - Quest 3 发送端的目标、范围、默认实现策略与非目标；首次进入项目时必读。

## Android 文档
`android/architecture.md` - 多模块边界、依赖方向、构建约束与测试基线；修改模块结构或公共接口时必读。

## 工具文档
`tools/signaling/README.md` - WebSocket 信令服务器与应答端，用于真机端到端联调

## 已完成任务文档
`workflow/done/260411-implement-plan-bootstrap.md` - 已完成 Quest 3 发送端 Android 多模块骨架初始化，记录实现边界与验证结果。
`workflow/done/260411-fix-media-projection-foreground-service.md` - 已修复 MediaProjection 缺少 `mediaProjection` 前台服务导致的启动失败，并记录服务化与授权约束。
`workflow/done/260411-implement-webrtc-video-streaming.md` - 已完成 WebRTC 视频推流闭环、sender 侧 JSON 信令协议与控制台配置入口，并记录验证结果。
`workflow/done/260411-fix-cleartext-signaling.md` - 已修复 Quest 3 真机连接局域网明文 WebSocket 信令被 Android 网络安全策略拦截的问题，并收敛默认地址策略。

## 全局重要记忆
- 本仓库只承载 Quest 3 发送端，不在同仓库实现接收端与真实信令服务。
- 技术栈固定为 Kotlin、Gradle Kotlin DSL、Jetpack Compose、Coroutines/Flow。
- 首版依赖注入方式固定为 AppContainer + 构造注入，不引入 Hilt 或 Koin。
- 默认推流策略固定为 H.264 优先，HEVC、VP8、VP9 仅作为能力展示与后续扩展入口。
- 首阶段 sender 建链协议固定为 `WebSocket + JSON Offer/Answer`，发送控制台必须提供 `signalingEndpoint` 与 `sessionId` 输入。
- 当前阶段所有构建默认允许明文 `ws://` 信令，发送控制台默认不预填宿主机地址，Quest 真机需手动填写局域网 IP。
- 提交前必须至少执行 `./gradlew spotlessCheck`、`./gradlew lintDebug`、`./gradlew testDebugUnitTest`。
