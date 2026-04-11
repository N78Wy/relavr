## 背景
- 当前仓库已经打通 Quest 3 sender 的 MediaProjection + WebRTC 视频推流，但音频仍停留在占位接口，UI 也明确标注“第二阶段接入”。
- 需求文档要求 sender 通过 `AudioPlaybackCapture` 采集系统播放音频，并通过标准 WebRTC 音视频流输出，供浏览器/Android 接收端接入。
- 本轮已确定的产品决策为：sender 接入真实音频轨；浏览器预览 demo 同步改到可播放远端音频；音频开关默认开启；音频失败时降级为仅视频推流，不中断整个会话。

## 实施结果
- `core/model` 与 `core/session` 已补齐真实音频状态模型、`AudioCaptureSource` PCM 读取接口、`PublishStartResult` 与 `RtcSessionEvent.AudioDegraded`，使音频降级进入常规会话状态机而不是额外补丁逻辑。
- `platform/android-capture` 已改为基于 `AudioPlaybackCapture` 的真实系统播放音频采集实现，复用同一 `MediaProjection` 会话，并在缺少 `RECORD_AUDIO`、系统不支持或 `AudioRecord` 初始化失败时统一抛出可降级错误。
- `platform/webrtc` 已通过 `JavaAudioDeviceModule.AudioBufferCallback` 接入自定义 PCM 输入桥接，在推流启动阶段添加真实 `AudioTrack`，运行时读取失败后只上报一次降级事件并持续发送静音，不中断视频链路。
- `app` 已新增录音权限预检网关，`feature/stream-control` 已改为默认开启音频并展示真实音频状态，`demo/browser-preview` 已支持接收并播放远端音视频。
- 单元测试、UI 测试、集成测试与 Node demo 测试均已补充对应断言，覆盖音频默认开启、启动期降级、运行期降级、浏览器页面文案与音频播放行为。

## TODO
- [x] 记录本轮任务背景、阶段与关键决策。
- [x] 改造音频采集接口、状态模型与降级路径。
- [x] 接入 WebRTC 音轨与自定义音频输入桥接。
- [x] 更新权限流程、发送控制台与浏览器 demo。
- [x] 补充测试覆盖并执行必要校验。

## 关键约束
- MediaProjection 会话仍只能由 `app` 模块内前台服务启动与持有，音频采集只能复用同一会话内的 `MediaProjection`。
- 不引入 native/NDK，也不更改现有 `WebSocket + JSON Offer/Answer` 信令协议。
- 音频仅覆盖系统播放音频，不新增麦克风采集或音视频混音。
- 运行时音频异常只允许降级到静音/仅视频，不做中途 renegotiation。

## 验证记录
- 2026-04-11：`./gradlew spotlessCheck` 通过。
- 2026-04-11：`./gradlew lintDebug` 通过。
- 2026-04-11：`./gradlew testDebugUnitTest` 通过。
- 2026-04-11：`./gradlew connectedDebugAndroidTest` 未通过，当前环境没有已连接 Android 设备，Gradle 报错为 `No connected devices!`。
- 2026-04-11：`cd demo/browser-preview && npm run format:check` 通过。
- 2026-04-11：`cd demo/browser-preview && npm run lint` 通过。
- 2026-04-11：`cd demo/browser-preview && npm run test` 通过。
