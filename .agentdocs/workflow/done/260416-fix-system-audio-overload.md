## 背景
- 发送端开启系统音频后，Quest 3 上出现明显延时、`HardwareVideoEncoder: Dropped frame, encoder queue full`、主线程 `Skipped frames`，最终被系统报告占用 6G+ 内存并闪退。
- 当前实现已经支持 `AudioPlaybackCapture`，但现象表明开启音频后整条实时流水线被推过了稳定阈值，单纯依赖现有慢速视频降档不足以在 backlog 爆炸前止损。
- 本轮目标不是先拍脑袋降规格，而是先把过载位置观测清楚，再把视频过载治理、音频桥接和 WebRTC 初始化路径改到不会继续把 native 内存顶爆。

## TODO
- [x] 复核当前音视频实现、日志特征与现有过载治理逻辑。
- [x] 建立任务文档并登记索引。
- [x] 补充 sender 侧运行时观测与结构化性能日志，暴露内存、视频编码与音频桥接状态。
- [x] 重构视频过载判定，改为更快采样、bitrate 优先降档与最低档保护。
- [x] 收紧系统音频桥接与 WebRTC 初始化路径，减少调度与拷贝成本。
- [x] 补充/更新单元测试，覆盖 bitrate 优先降档、快速过载保护与音频桥接缓冲策略。
- [x] 执行 `./gradlew spotlessCheck`、`./gradlew lintDebug`、`./gradlew testDebugUnitTest` 并记录结果。
- [x] 回顾并更新长期文档/记忆，完成后归档任务文档。

## 关键判断
- 当前 6G+ 暴涨更像 native 视频编码 backlog 持续堆积，而不是 PCM 音频缓冲本身失控；现有音频 ring buffer 只有几十 KB，不足以解释该量级。
- `HardwareVideoEncoder: Dropped frame, encoder queue full` 是当前最直接的过载信号，说明视频编码器比音轨更早进入排队态；系统音频更像是把整体流水线推过了实时阈值。
- 本轮仍优先保留现有 Kotlin/Java + libwebrtc Java API 结构，不直接引入 NDK/自定义 native ADM；若完成后仍证明 Java 音频注入是主要瓶颈，再单独评估下一轮 native 化。

## 实施结果
- `platform/webrtc` 已新增结构化性能日志，周期性输出 active profile、编码 fps、音频桥接占用、丢帧/欠载计数与 Java/native/PSS 内存，用于真机定位 backlog。
- `AdaptiveVideoProfileController` 已改为 bitrate 优先降档，采样周期收紧到 `250ms`，并去掉秒级 warmup/cooldown 迟滞；最低档持续过载时会更早进入保护分支。
- `WebRtcPlaybackAudioBridge` 已切到独立单线程音频调度、`THREAD_PRIORITY_URGENT_AUDIO` 优先级与紧凑 ring buffer；缓冲只保留极小窗口，超载时优先丢旧帧而不是继续积压。
- `DefaultWebRtcCodecSupportProvider` 已改为基于 EGL 上下文探测 codec，避免继续走 `DefaultVideoEncoderFactory(null, ...)` 并打出误导性的无共享 EGL 警告。
- 已补充 `BoundedPcmRingBuffer`、bitrate 优先降档与 codec 探测路径单测，验证本轮的关键控制逻辑。

## 验证记录
- 2026-04-16：`./gradlew testDebugUnitTest` 通过。
- 2026-04-16：`./gradlew spotlessCheck` 通过。
- 2026-04-16：`./gradlew lintDebug` 通过。
