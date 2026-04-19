## 背景
- 新一轮真机日志显示，sender 开启系统音频后虽然视频编码 fps 基本稳定，但 `audioBufferedMs=0`、`audioDroppedMs=0`，同时 `audioUnderruns` 每个窗口都是几千到上万，远端只剩噪声/吱声。
- 同期 `nativeHeapMb` 持续上涨到 2GB+，说明根因不在视频编码过载，而在 sender 用错误节奏向 libwebrtc 注入系统音频。
- 结合 `io.github.webrtc-sdk:android` 的 Java/native 结构，本轮决定不引入 NDK，而是改为复用 libwebrtc 自身 `WebRtcAudioRecord` 的原生录制节奏，只替换其底层 `AudioRecord` 为 `AudioPlaybackCapture` 的 `REMOTE_SUBMIX` 实例。

## TODO
- [x] 复核 `AudioPlaybackCapture`、`JavaAudioDeviceModule`、`WebRtcAudioRecord` 的现有实现边界。
- [x] 将 sender 音频抽象改为提供原始 `AudioRecord` 注入，而不是自建 read loop / ring buffer。
- [x] 在 `platform/webrtc` 中实现可注入 `REMOTE_SUBMIX AudioRecord` 的 sender 音频模块，并保留轻量性能观测。
- [x] 移除旧的 `AudioBufferCallback + setAudioRecordEnabled(false)` 主链路与相关测试。
- [x] 执行 `./gradlew spotlessCheck`、`./gradlew lintDebug`、`./gradlew testDebugUnitTest`。
- [x] 更新长期文档/记忆并归档任务文档。

## 实施结果
- `core/session` 的系统音频抽象已改为暴露原始 `AudioRecord` 与格式信息，供 sender 在 WebRTC 音频输入层直接复用。
- `platform/android-capture` 仍负责创建 `AudioPlaybackCapture` 对应的 `REMOTE_SUBMIX AudioRecord`，但不再自行读 PCM 或启动读线程。
- `platform/webrtc` 已新增 `PlaybackCaptureAudioDeviceModule`，通过注入 `REMOTE_SUBMIX AudioRecord` 到 libwebrtc `WebRtcAudioRecord` 的内部字段，复用其原生录制节奏与线程模型；并新增轻量音频性能快照与结构化 `perf` 日志。
- 旧的 `BoundedPcmRingBuffer`、`WebRtcPlaybackAudioBridge` 与 `setAudioRecordEnabled(false)` 主链路已从正式路径移除，避免再次出现高频欠载、噪声和 sender 侧 native 内存持续增长。
- 已补充 sender 侧新的性能日志单测，覆盖音频性能快照压力判定和日志字段输出。

## 验证记录
- 2026-04-19：`./gradlew testDebugUnitTest` 通过。
- 2026-04-19：`./gradlew spotlessCheck` 通过。
- 2026-04-19：`./gradlew lintDebug` 通过。
