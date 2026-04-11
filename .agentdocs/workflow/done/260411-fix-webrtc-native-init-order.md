# 修复 WebRTC Native 初始化时序崩溃

## 背景
- 发送控制台启动时会立即刷新编码能力，`CombinedCodecCapabilityRepository` 因此会在真正开始推流前调用 `DefaultWebRtcCodecSupportProvider`。
- 当前 `platform/webrtc` 只有 `WebRtcPublisherFactory` 在创建 `PeerConnectionFactory` 前执行 `PeerConnectionFactory.initialize(...)`，codec 探测路径没有复用这段初始化。
- `DefaultVideoEncoderFactory` 构造时会立刻实例化 `SoftwareVideoEncoderFactory` 并触发 JNI；若此时 native 库尚未加载，应用会在首屏阶段因 `UnsatisfiedLinkError` 崩溃。

## 分阶段计划
1. 补任务文档与索引，记录“共享 WebRTC 初始化器”约束。
2. 在 `platform/webrtc` 提取一次性共享初始化器，并让 codec 探测与推流工厂共用。
3. 补单元测试与启动烟雾测试，覆盖首屏刷新能力不再崩溃的回归场景。
4. 执行根目录校验命令并回写结果。

## TODO
- [x] 创建当前任务文档并更新索引。
- [x] 提取共享 WebRTC 初始化器并接入 codec 探测 / 推流工厂。
- [x] 补充单元测试与 Android 启动烟雾测试。
- [x] 执行 `spotlessCheck`、`lintDebug`、`testDebugUnitTest`、`connectedDebugAndroidTest` 并记录结果。

## 实现结果
- `platform/webrtc` 新增共享 `WebRtcLibraryInitializer`，统一封装 `PeerConnectionFactory.initialize(...)` 的一次性初始化，并由 `AppContainer` 以单例形式注入到所有 WebRTC 调用路径。
- `DefaultWebRtcCodecSupportProvider` 改为在读取 `DefaultVideoEncoderFactory.supportedCodecs` 前先调用共享初始化器，修复“首屏刷新 codec 能力时 JNI 尚未加载”导致的 `UnsatisfiedLinkError`。
- `WebRtcPublisherFactory` 与其内部 `WebRtcRuntime` 已切换为复用同一个共享初始化器，不再在推流工厂私有伴生对象里单独维护初始化状态。
- `platform/webrtc` 新增两个单元测试，分别覆盖初始化器幂等性与 codec 探测前置初始化顺序；`app` 新增 `MainActivity` 启动烟雾测试，覆盖发送控制台首屏渲染回归。

## 验证记录
- 2026-04-11：`./gradlew spotlessCheck` 通过。
- 2026-04-11：`./gradlew lintDebug` 通过。
- 2026-04-11：`./gradlew testDebugUnitTest` 通过。
- 2026-04-11：`./gradlew connectedDebugAndroidTest` 未通过，当前环境没有已连接 Android 设备，Gradle 报错为 `No connected devices!`。在进入设备阶段前，新增 `MainActivitySmokeTest` 的编译问题已修复并通过本次任务内回归验证。
