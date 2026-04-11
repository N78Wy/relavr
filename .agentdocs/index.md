## 产品文档
`prd/quest3-sender.md` - Quest 3 发送端的目标、范围、默认实现策略与非目标；首次进入项目时必读。

## Android 文档
`android/architecture.md` - 多模块边界、依赖方向、构建约束与测试基线；修改模块结构或公共接口时必读。

## 当前任务文档
（暂无）

## 已完成任务文档
`workflow/done/260411-expand-stream-options-and-adaptive-layout.md` - 已扩展发送端规格选择，修复 Quest 默认窄窗、自适应布局与低对比度文字问题，并记录验证结果。
`workflow/done/260411-browser-preview-demo.md` - 已增加本地/局域网浏览器预览 demo，记录 Node 信令服务、浏览器 receiver 页面与验证结果。
`workflow/done/260411-implement-plan-bootstrap.md` - 已完成 Quest 3 发送端 Android 多模块骨架初始化，记录实现边界与验证结果。
`workflow/done/260411-fix-media-projection-foreground-service.md` - 已修复 MediaProjection 缺少 `mediaProjection` 前台服务导致的启动失败，并记录服务化与授权约束。
`workflow/done/260411-implement-webrtc-video-streaming.md` - 已完成 WebRTC 视频推流闭环、sender 侧 JSON 信令协议与控制台配置入口，并记录验证结果。
`workflow/done/260411-fix-cleartext-websocket-policy.md` - 已修复 Android 明文 WebSocket 被网络安全策略拦截的问题，记录 manifest 策略、默认地址调整与回归测试。
`workflow/done/260411-implement-webrtc-audio-streaming.md` - 已完成 Quest 3 sender 音频采集、WebRTC 音轨推流、录音权限预检与浏览器音频预览闭环，并记录降级策略与验证结果。
`workflow/done/260411-implement-codec-switching.md` - 已完成发送端多编码格式切换，记录能力交集、默认回退策略、发送控制台改造与验证结果。
`workflow/done/260411-fix-webrtc-native-init-order.md` - 已修复 WebRTC codec 探测早于 native 初始化导致的启动崩溃，记录共享初始化器方案与回归验证。

## 全局重要记忆
- 本仓库主线仍以 Quest 3 发送端为核心，但包含一个仅用于本地/局域网联调的浏览器预览 demo，不作为正式接收端产品实现。
- 技术栈固定为 Kotlin、Gradle Kotlin DSL、Jetpack Compose、Coroutines/Flow。
- 首版依赖注入方式固定为 AppContainer + 构造注入，不引入 Hilt 或 Koin。
- 所有会触发 `org.webrtc` JNI 的路径都必须先复用共享 `WebRtcLibraryInitializer` 完成一次性初始化，不能假设 native 库已由其他流程预先加载。
- 默认推流策略固定为 H.264 优先；若当前设备或 libwebrtc 不支持 H.264，则按 HEVC、VP8、VP9 顺序回退。发送控制台支持在开播前切换编码偏好，推流中保持锁定。
- 首阶段 sender 建链协议固定为 `WebSocket + JSON Offer/Answer`，发送控制台必须提供 `signalingEndpoint` 与 `sessionId` 输入。
- sender 音频默认开启，固定通过 `AudioPlaybackCapture` 采集系统播放音频；若缺少 `RECORD_AUDIO`、设备不支持或运行时读取失败，必须降级为仅视频/静音而不能打断推流会话。
- 提交前必须至少执行 `./gradlew spotlessCheck`、`./gradlew lintDebug`、`./gradlew testDebugUnitTest`、`./gradlew connectedDebugAndroidTest`。
- 如改动 `demo/browser-preview`，还必须执行 `npm run format:check`、`npm run lint`、`npm run test`。
