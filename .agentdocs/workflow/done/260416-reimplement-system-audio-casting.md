## 背景
- 当前 sender 仅支持视频推流，已移除全部音频链路；这与“投屏时一并发送其他 app 的游戏声、视频声”等目标不符。
- 这次不沿用旧音频方案，改为围绕 `AudioPlaybackCaptureConfiguration` 重建整条链路，并同时解决 Android 14+ 下 MediaProjection token 只能消费一次的约束。
- 产品边界已确定为：只支持系统允许被 playback capture 捕获的其他 app 音频；音频不可用时继续视频-only 推流，不阻断整场投屏。

## TODO
- [x] 梳理现有 sender 视频链路、模型、UI 与测试结构，确认重构接缝。
- [x] 建立任务文档并登记索引。
- [x] 恢复 `StreamConfig.audioEnabled`、音频状态模型与会话结果/事件契约。
- [x] 在 app / feature 层恢复录音权限桥接、音频开关、设置引导与配置持久化。
- [x] 在 `platform/android-capture` 实现 `AudioPlaybackCapture` 采集会话与初始化回退。
- [x] 在 `platform/webrtc` 实现自定义 WebRTC 音频注入、启动期回退与运行期静音降级。
- [x] 补充并更新单元测试、Compose UI 测试与 app 测试。
- [x] 执行 `./gradlew spotlessCheck`、`./gradlew lintDebug`、`./gradlew testDebugUnitTest` 并记录结果。
- [x] 回顾并更新长期文档/记忆，完成后归档任务文档。

## 关键决策
- 视频链路继续使用现有 `ScreenCapturerAndroid`，并在其 `startCapture()` 成功后复用同一份 `MediaProjection` 创建 `AudioPlaybackCaptureConfiguration`，避免再次消费 projection token。
- 音频启动失败不做 renegotiation：若开播前失败，则直接不创建音轨并以视频-only offer 开播；若开播后失败，则保留音轨并切为静音，同时上报会话内降级状态。
- 发送控制台保留“用户想要系统音频”和“当前会话是否真的有音频”两层语义；权限拒绝或永久拒绝时只回退音频，不锁死开始按钮。

## 实施结果
- `core/model` 与 `core/session` 已恢复 `audioEnabled`、`AudioState`、`PublishStartResult`、`RtcSessionEvent.AudioDegraded` 与录音权限控制器契约，让系统音频启动结果和运行期退化都进入常规状态机。
- `app` 已恢复 `RECORD_AUDIO` manifest 声明、录音权限三态桥接、系统设置入口、前台服务音频配置传递与发送控制台持久化；发送控制台默认开启系统音频，但在权限拒绝或永久拒绝时会稳定回退到 video-only。
- `platform/android-capture` 已新增 `AudioPlaybackCapture` 采集会话工厂，固定优先 48 kHz/stereo，失败时回退到 48 kHz/mono；`platform/webrtc` 已通过自定义 `JavaAudioDeviceModule.AudioBufferCallback` 与有界 PCM 环形缓冲把系统音频注入 WebRTC。
- 开播前系统音频初始化失败时会直接以 video-only offer 开播；推流中系统音频失败时只发出 `AudioDegraded` 事件并切为静音，不触发 renegotiation，也不终止视频链路。
- 已补充 ViewModel、前台服务控制器、会话状态机与 Compose UI 测试，覆盖录音权限拒绝后的 video-only 回退、音频成功开播、运行期音频降级与音频设置入口。

## 验证记录
- 2026-04-16：`./gradlew testDebugUnitTest` 通过。
- 2026-04-16：`./gradlew spotlessCheck` 通过。
- 2026-04-16：`./gradlew lintDebug` 通过。
