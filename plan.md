
  # Quest 3 发送端项目框架规划

  ## Summary

  - 仓库定位为“仅 Quest 3 发送端”，当前不把接收端和信令服务放进同一仓库。
  - 技术栈固定为 Kotlin + Gradle Kotlin DSL + Jetpack Compose + Coroutines/Flow，采用“中度扩展”的 Android 多模块架构，不上 Hilt/
    Koin，不在首轮引入 NDK。
  - 视频链路的架构边界按“采集 / 编码能力 / RTC 推流”拆开，但 v1 实现先复用 WebRTC Android 已有的 MediaCodec 硬件编码能力打通主链，
    不先做“外部预编码帧注入 PeerConnection”这条高复杂度路线。
  - 音频按独立模块设计，只先预留 AudioPlaybackCapture 接缝和错误模型；是否需要后续引入 native WebRTC 音频扩展，放到视频闭环后再决
    定。
  - 首个脚手架任务先补治理基础：初始化 .agentdocs/、建立任务文档，并把现有 AGENTS.md 中面向 NestJS 的校验要求改成 Android/Kotlin
    版本。

  ## 项目骨架

  app/
  feature/stream-control/
  core/common/
  core/model/
  core/session/
  platform/android-capture/
  platform/media-codec/
  platform/webrtc/
  testing/fakes/
  build-logic/
  gradle/libs.versions.toml
  .agentdocs/

  - app：Compose 单 Activity 壳层、AppContainer、权限入口、Quest 生命周期适配；只保留一个“发送控制台”页面。
  - feature/stream-control：ViewModel、UI state、用户动作，负责申请授权、开始/停止推流、切换编码偏好、音频开关。
  - core/common：日志、结果封装、线程/时间抽象、错误基类，不放 Android UI 依赖。
  - core/model：StreamConfig、CodecPreference、CapabilitySnapshot、CaptureState、PublishState、SenderError。
  - core/session：纯 Kotlin 编排层，提供 StreamingSessionCoordinator 状态机和模块接口。
  - platform/android-capture：MediaProjection、VirtualDisplay、projection callback、AudioPlaybackCapture 抽象与资源释放。
  - platform/media-codec：编码能力探测、默认 H.264 策略、HEVC/VP8/VP9 扩展位；先不承担 RTP/SDP 逻辑。
  - platform/webrtc：PeerConnectionFactory、视频编码工厂、RtcPublisher、统计信息、SignalingClient 接口和一个占位
    NoOpSignalingClient。
  - testing/fakes：fake capturer / fake rtc publisher / fake capability repo，支撑单测和无设备集成测试。
  - build-logic：统一 Android app/library、Compose、lint/format/test 的 convention plugins，版本只放 libs.versions.toml。

  ## 核心接口与公开类型

  - StreamingSessionCoordinator.start(config) / stop() / observeState()：统一编排权限、采集、RTC 建链、异常收敛、资源释放。
  - ProjectionPermissionGateway.requestPermission() / restoreIfAvailable()：UI 与系统授权边界。
  - VideoCaptureSource.create(projection, config)：视频采集边界；v1 内部可落到 MediaProjection + WebRTC 可用实现。
  - AudioCaptureSource.create(projection, config)：音频采集边界；首轮先给 contract、状态和错误定义。
  - CodecCapabilityRepository.getCapabilities() + CodecPolicy.select(preference, capabilities)：默认策略固定为 H.264 优先，HEVC/
    VP8/VP9 仅在设备确认支持后暴露。
  - RtcPublisher.createSession(config) / publish(videoSource, audioSource?) / close()：RTC 推流边界。
  - SignalingClient 只定义接口；当前仓库不实现真实服务端，只保留占位实现以保证脚手架可编译。
  - StreamConfig 固定包含 videoEnabled、audioEnabled、codecPreference、resolution、fps、bitrateKbps、signalingEndpoint、
    iceServers；默认值定为 H.264 + 720p + 30fps + 中等码率。

     任务文档；同步修正 AGENTS.md 的校验基线。
  2. 视频主链阶段：打通 MediaProjection 授权、开始/停止推流状态机、H.264 默认策略、WebRTC 视频发布。
  3. 音频接缝阶段：补 AudioCaptureSource、音频开关、错误处理和降级路径，但暂不把 native 扩展引入主干。
  4. 能力扩展阶段：补 codec capability 展示与选择逻辑，允许显示 HEVC/VP8/VP9 能力，但默认自动选择仍锁定 H.264。

  ## 测试与验收

  - 单元测试覆盖 CodecPolicy、StreamingSessionCoordinator、状态 reducer、异常映射、能力回退逻辑。
  - 集成测试使用 fake ProjectionPermissionGateway 与 fake RtcPublisher，验证开始/停止、失败回滚、资源释放。
  - Android UI 测试覆盖发送控制台的权限流程、开始/停止按钮、错误展示、音频开关与编码偏好切换。
  - Quest 3 真机验收至少覆盖：授权弹窗、开始推流、停止推流、再次授权、前后台切换、H.264 视频链路可稳定输出。
  - 根目录校验命令统一为 ./gradlew spotlessCheck lintDebug testDebugUnitTest；Quest 真机链路作为额外手动
    验收，不并入通用 CI。

  ## 假设与默认值

  - 发送端 minSdk 固定为 29，因为 AudioPlaybackCapture 从 Android 10/API 29 起可用；需求里的 “Android 5.0+” 只约束接收端解码兼容目
    标。
  - 首版应用包名暂定为 io.relavr.sender，后续若有正式组织域名再统一替换。
  - 首版不用 Hilt/Koin，采用 AppContainer + 构造注入，避免空仓阶段引入重型依赖注入。
  - 首版不引入 native/、CMake、Prefab；如果后续确认 AudioPlaybackCapture 需要更深的 WebRTC 音频接入，再新增 native 模块。
  - 方案依据官方现状：Meta Quest 支持 Android App 形态
    (https://developers.meta.com/horizon/documentation/android-apps/horizon-os-apps/) 与 MediaProjection
    (https://developers.meta.com/horizon/documentation/native/native-media-projection/)，Android 官方持续推荐 Compose
    (https://developer.android.com/develop/ui/compose) 和 App Architecture (https://developer.android.com/jetpack/arch)，WebRTC A
    ndroid 官方源码已有 MediaCodec 硬件视频编码工厂
    (https://chromium.googlesource.com/external/webrtc/+/master/sdk/android/api/org/webrtc/DefaultVideoEncoderFactory.java) 与 Ja视频先闭环、音频先留接缝”的直接依据。
