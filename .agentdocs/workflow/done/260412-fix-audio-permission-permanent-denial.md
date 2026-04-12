## 背景
- sender 当前只会在录音权限未授权时重复触发 `RequestPermission()`，没有识别 Android 的“拒绝且不再提醒”分支。
- 一旦用户把 `RECORD_AUDIO` 选成“拒绝且不再提醒”，系统后续会静默返回拒绝，发送控制台没有任何恢复入口，音频能力会被永久锁死。
- 本轮目标是在保持视频-only 可开播的前提下，识别永久拒绝状态，并把音频恢复路径切换为应用内联“打开系统设置”引导。

## 实施结果
- `feature/stream-control` 已引入 `Granted / Requestable / PermanentlyDenied` 录音权限三态，ViewModel 会在权限状态变更时同步更新音频开关、状态文案和“打开系统设置”入口可见性。
- `app` 已复用现有 DataStore 持久化“录音权限是否请求过”标记，`MainActivity` 会结合授权结果、`shouldShowRequestPermissionRationale()` 和该标记区分首次未请求、普通拒绝和永久拒绝。
- 当权限处于永久拒绝时，系统授权框不再被重复触发；发送控制台音频区域会展示永久拒绝说明和系统设置入口，开始按钮仍保持可用，继续走仅视频开播。
- 已补充权限状态判定测试、DataStore 标记测试、ViewModel 测试与 Compose UI 测试，覆盖永久拒绝后的关键状态与交互。

## TODO
- [x] 建立本轮任务文档并登记到索引。
- [x] 为录音权限补充 `Granted / Requestable / PermanentlyDenied` 三态模型。
- [x] 在 `app` 层持久化“是否已经发起过录音权限请求”，用于区分首次未请求和永久拒绝。
- [x] 调整 `MainActivity` 的权限桥接，在永久拒绝时不再发起系统授权框，而是同步设置引导状态。
- [x] 改造发送控制台音频区域，展示永久拒绝说明与“打开系统设置”入口。
- [x] 补充单元测试 / UI 测试并执行必需校验。
- [x] 回顾长期文档并在完成后归档任务文档。

## 关键约束
- 不改变 sender 音频默认开启、缺权限时自动降级为仅视频的主流程。
- `feature` 不能直接依赖 Android 权限 API；平台细节仍由 `app` 层桥接。
- 永久拒绝后的恢复入口固定为系统应用详情设置页，不额外引入自定义弹窗流程。

## 验证记录
- 2026-04-12：`./gradlew :feature:stream-control:testDebugUnitTest` 通过。
- 2026-04-12：`./gradlew :app:testDebugUnitTest` 通过。
- 2026-04-12：`./gradlew spotlessCheck lintDebug testDebugUnitTest` 通过。
- 2026-04-12：本轮新增 Compose UI 测试已写入 `feature/stream-control`，但当前环境没有已连接 Android 设备，未执行 `connectedDebugAndroidTest`。
