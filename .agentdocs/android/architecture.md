## 工程结构
- `app`：Compose 单 Activity 壳层、`AppContainer`、系统权限桥接与应用入口。
- `feature/stream-control`：发送控制台 UI、ViewModel、用户动作与界面状态映射。
- `core/common`：线程抽象与可复用基础能力，不依赖具体平台实现。
- `core/model`：配置、能力快照、状态、错误模型与信令配置校验。
- `core/session`：发送会话编排层，定义授权、音频接缝、推流、信令消息与 RTC 事件边界接口。
- `platform/android-capture`：MediaProjection 授权桥接与 AudioPlaybackCapture 接缝实现。
- `platform/media-codec`：编码能力探测与默认 CodecPolicy。
- `platform/webrtc`：`ScreenCapturerAndroid + PeerConnection + WebSocket` 推流实现，负责 sender 侧 JSON 信令协议与 H.264 优先协商。
- `testing/fakes`：供单元测试和集成测试复用的 fake 实现。
- `demo/browser-preview`：Node 局域网联调工具，提供静态浏览器 receiver 页面与 demo 级 WebSocket 信令服务，不参与 Android 正式产物构建。

## 依赖方向
- `app` 只组装依赖，不承载业务状态机。
- `feature` 依赖 `core`，由 `app` 注入 `platform` 实现。
- `platform` 只能依赖 `core`，不反向依赖 `feature` 或 `app`。
- `testing/fakes` 依赖 `core`，为其他模块的测试提供可替换实现。

## 构建约束
- 全仓库使用 Gradle Kotlin DSL。
- 所有 Android 模块统一由 `build-logic` 里的 convention plugin 配置 compileSdk、minSdk、Compose、lint 与测试选项。
- 版本统一维护在 `gradle/libs.versions.toml`。
- 代码风格检查统一由 Spotless 驱动。
- `gradle.properties` 当前将 `org.gradle.jvmargs` 固定为 `-Xmx1536m -Dfile.encoding=UTF-8`，避免在约 6 GiB 内存环境中因默认堆过大导致 `lintDebug` daemon 被系统杀死。

## Android 平台约束
- MediaProjection 会话只能由 `app` 模块内的 `mediaProjection` 类型前台服务启动与持有；`feature`、`core`、`platform` 不得直接启动前台服务或绕过该入口创建投屏会话。
- MediaProjection 系统授权必须逐次请求，不允许跨推流会话缓存并复用上一次授权结果；相关实现只能在单次开始流程内消费授权结果。
- sender 侧 WebRTC 建链固定走 `WebSocket + JSON Offer/Answer` 协议，消息类型只包含 `join`、`offer`、`answer`、`ice-candidate`、`leave`、`error` 六类。
- sender 既然固定依赖 WebSocket 信令、WebRTC 网络状态监测与系统播放音频采集，`app` manifest 必须同时声明 `android.permission.INTERNET`、`android.permission.ACCESS_NETWORK_STATE` 与 `android.permission.RECORD_AUDIO`；缺少前两者会直接导致建链或网络监测失败，缺少后者则必须走视频-only 降级。
- 发送控制台固定开放 `signalingEndpoint`、`sessionId` 与音频开关；视频编码固定为 H.264，音频默认开启。若用户未关闭音频，开始推流前必须先由 `app` 层预检 `RECORD_AUDIO` 运行时权限，但即使用户拒绝，也只能降级为仅视频，不能直接中断整个会话。
- sender 真实音频固定通过同一 `MediaProjection` 会话上的 `AudioPlaybackCapture + JavaAudioDeviceModule.AudioBufferCallback` 接入 WebRTC，不允许回退到麦克风采集或额外实现独立混音链路。
- 当前联调版本在应用层允许 `ws://` 明文信令，以兼容 Android 模拟器 `10.0.2.2` 和开发机局域网地址；如后续切换为 `wss://`，必须同步收紧 manifest 策略并更新对应回归测试。
- 运行时音频异常只允许降级到静音/仅视频，不做中途 renegotiation；会话主状态继续保持推流中，音频细节通过独立的 `audioState` / `audioDetail` 对外暴露。
- `demo/browser-preview` 只支持单个 `sessionId` 下的一发一收；sender 可先于 receiver 启动，服务端负责缓存最新 `offer` 与 sender 侧 ICE candidate，供后加入的浏览器补齐建链；receiver 页面会尝试自动播放远端音视频，并在被浏览器拦截时提示用户手动恢复声音。

## 测试基线
- 单元测试至少覆盖 codec 选择策略、发送会话状态机与 ViewModel 行为。
- 集成测试至少覆盖开始推流、失败回滚、停止释放。
- Compose UI 测试至少覆盖发送控制台的开始/停止交互与错误展示入口。
- 根目录验收命令固定为 `./gradlew spotlessCheck lintDebug testDebugUnitTest connectedDebugAndroidTest`。
- `demo/browser-preview` 变更必须额外覆盖 Node 单元测试、Node 集成测试，并执行 `npm run format:check`、`npm run lint`、`npm run test`。
