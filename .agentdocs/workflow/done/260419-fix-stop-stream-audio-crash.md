## 背景
- 真机回归显示，sender 在推流过程中点击“停止推流”会直接闪退。
- 崩溃线程为 `AudioRecordJavaThread`，异常是 `java.lang.NullPointerException: Attempt to invoke virtual method 'int java.nio.ByteBuffer.capacity()' on a null object reference`。
- 当前 sender 的系统音频实现通过反射把 `REMOTE_SUBMIX AudioRecord` 注入到 libwebrtc `WebRtcAudioRecord`。日志表明 stop 阶段在 WebRTC 自己停掉录音线程前，sender 先清空了 `byteBuffer/audioRecord`，导致录音线程仍在运行时读到空引用而崩溃。

## TODO
- [x] 复核 sender stop 阶段的资源释放顺序，确认最小修复点。
- [x] 调整发送端系统音频 stop/失败清理顺序，避免在录音线程退出前清空内部缓冲字段。
- [x] 补充回归测试，覆盖本次 stop 资源释放顺序。
- [x] 执行 `./gradlew spotlessCheck`、`./gradlew lintDebug`、`./gradlew testDebugUnitTest`。
- [x] 更新相关文档/记忆，并在完成后归档任务文档。

## 实施结果
- `platform/webrtc` 新增了 `PlaybackCaptureReleaseCoordinator`，把 sender 系统音频清理拆成“请求清理”和“线程停止后真正释放”两步，避免 `AudioRecordJavaThread` 在 stop 阶段继续访问已置空的 `byteBuffer/audioRecord`。
- `PlaybackCaptureAudioDeviceModule.clearPlaybackCapture()` 现在会在 WebRTC 录音线程仍活跃时延后关闭 capture session，并在 `onWebRtcAudioRecordStop()` 回调后统一清理反射字段。
- `WebRtcPublishSession.close()` 不再在会话关闭最前面主动调用 `clearPlaybackCapture()`；音频启动失败回滚路径也改成先 dispose WebRTC 音轨/音源，再执行 capture 清理，进一步降低 stop 竞态。
- 已补充 `PlaybackCaptureReleaseCoordinatorTest`，覆盖空闲清理、延后清理、release 兜底和运行中禁止替换 session 四类 stop 生命周期行为。

## 验证记录
- 2026-04-19：`./gradlew spotlessCheck lintDebug testDebugUnitTest` 通过。
