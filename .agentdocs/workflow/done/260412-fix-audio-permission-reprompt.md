## 背景
- sender 当前在开始推流前才做 `RECORD_AUDIO` 预检，发送控制台里的音频开关只修改 `StreamConfig.audioEnabled`，没有驱动独立的运行时授权流程。
- 现状下如果用户首次拒绝录音权限，UI 很容易停留在“音频关闭且再次打开不再触发授权”的状态，和产品预期不一致。
- 本轮目标是把音频权限请求收敛到常规开关状态机里：首次进入默认开启时自动询问；用户拒绝则回退为关闭；用户再次手动打开时继续询问，直到用户同意后不再重复询问。

## 实施结果
- `feature/stream-control` 已为音频开关补充权限感知状态机：ViewModel 会保存录音权限快照，首次进入默认开启且未授权时自动发出一次授权请求；用户拒绝后回退并持久化 `audioEnabled=false`；再次手动打开会重新请求。
- `feature/stream-control` 已新增一次性 `recordAudioPermissionRequests` effect 和 `audioPermissionRequestPending` UI 状态；权限请求进行中时，音频开关与开始按钮都会禁用，音频摘要直接复用系统权限请求中的提示文案。
- `app` 已由 `MainActivity` 统一桥接录音权限：启动和回前台时同步实时权限快照，消费 ViewModel 与前台服务控制器发出的授权请求，并把系统结果同时回传给两侧状态机。
- `ForegroundServiceStreamingSessionController` 已真正消费 `RecordAudioPermissionGateway.requestPermissionIfNeeded()` 的布尔结果；如果用户在开播前最后一次兜底检查里仍拒绝授权，就使用 `audioEnabled=false` 的配置启动前台服务，稳定走视频-only 路径。
- 已补充 ViewModel、app controller、app integration 与 Compose UI 测试；同时修正当前 Android instrumentation 测试方法名中的空格，避免 `connectedDebugAndroidTest` 在 dex 阶段因无效 `SimpleName` 直接失败。

## TODO
- [x] 建立本轮任务文档并登记到索引。
- [x] 为音频开关补充权限感知状态机与一次性授权请求 effect。
- [x] 调整 MainActivity 与 app 层权限网关，保证首次进入与再次打开都能重试授权。
- [x] 补充单元测试、UI 测试、集成测试并执行必要校验。
- [x] 回顾并更新长期文档，完成后归档任务文档。

## 关键约束
- 保持 sender 音频默认开启，不改 `StreamConfig` 默认值。
- MediaProjection 与 WebRTC 音频链路不变，本轮只修改录音权限触发时机、拒绝后的 UI/config 回滚和开播前兜底。
- 用户拒绝录音权限时只能关闭音频并继续允许视频-only 开播，不能把整个发送会话直接置为失败。

## 验证记录
- 2026-04-12：`./gradlew spotlessCheck lintDebug testDebugUnitTest` 通过。
- 2026-04-12：`./gradlew :app:testDebugUnitTest` 通过。
- 2026-04-12：`./gradlew :feature:stream-control:testDebugUnitTest` 通过。
- 2026-04-12：`./gradlew :feature:stream-control:connectedDebugAndroidTest` 已能完成 debugAndroidTest 编译与打包，但当前环境没有已连接 Android 设备，最终失败为 `No connected devices!`。
