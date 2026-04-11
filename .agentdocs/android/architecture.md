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

## Android 平台约束
- MediaProjection 会话只能由 `app` 模块内的 `mediaProjection` 类型前台服务启动与持有；`feature`、`core`、`platform` 不得直接启动前台服务或绕过该入口创建投屏会话。
- MediaProjection 系统授权必须逐次请求，不允许跨推流会话缓存并复用上一次授权结果；相关实现只能在单次开始流程内消费授权结果。
- sender 侧 WebRTC 建链固定走 `WebSocket + JSON Offer/Answer` 协议，消息类型只包含 `join`、`offer`、`answer`、`ice-candidate`、`leave`、`error` 六类。
- 首阶段 UI 只开放 `signalingEndpoint` 与 `sessionId` 输入；视频编码固定为 H.264，音频开关仅保留展示与后续扩展入口，不接入真实音轨。

## 测试基线
- 单元测试至少覆盖 codec 选择策略、发送会话状态机与 ViewModel 行为。
- 集成测试至少覆盖开始推流、失败回滚、停止释放。
- Compose UI 测试至少覆盖发送控制台的开始/停止交互与错误展示入口。
- 根目录验收命令固定为 `./gradlew spotlessCheck lintDebug testDebugUnitTest connectedDebugAndroidTest`。
