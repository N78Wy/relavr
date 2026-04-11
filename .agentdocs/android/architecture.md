## 工程结构
- `app`：Compose 单 Activity 壳层、`AppContainer`、系统权限桥接与应用入口。
- `feature/stream-control`：发送控制台 UI、ViewModel、用户动作与界面状态映射。
- `core/common`：线程抽象与可复用基础能力，不依赖具体平台实现。
- `core/model`：配置、能力快照、状态、错误模型与信令配置校验。
- `core/session`：发送会话编排层，定义授权、音频接缝、推流、信令消息与 RTC 事件边界接口。
- `platform/android-capture`：MediaProjection 授权桥接与 AudioPlaybackCapture 接缝实现。
- `platform/discovery`：基于 Android NSD 的局域网 receiver 发现实现，负责 sender 侧 mDNS 解析与发现事件输出。
- `platform/media-codec`：编码能力探测、默认 CodecPolicy 与 codec fallback 策略。
- `platform/webrtc`：`ScreenCapturerAndroid + PeerConnection + WebSocket` 推流实现，负责 sender 侧 JSON 信令协议、WebRTC codec 能力探测与按编码偏好排序的 SDP 协商。
- `testing/fakes`：供单元测试和集成测试复用的 fake 实现。
- `demo/browser-preview`：Node 局域网联调工具，提供静态浏览器 receiver 页面与 demo 级 WebSocket 信令服务，不参与 Android 正式产物构建。

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
- `gradle.properties` 当前将 `org.gradle.jvmargs` 固定为 `-Xmx1536m -Dfile.encoding=UTF-8`，避免在约 6 GiB 内存环境中因默认堆过大导致 `lintDebug` daemon 被系统杀死。

## Android 平台约束
- MediaProjection 会话只能由 `app` 模块内的 `mediaProjection` 类型前台服务启动与持有；`feature`、`core`、`platform` 不得直接启动前台服务或绕过该入口创建投屏会话。
- MediaProjection 系统授权必须逐次请求，不允许跨推流会话缓存并复用上一次授权结果；相关实现只能在单次开始流程内消费授权结果。
- sender 侧 WebRTC 建链固定走 `WebSocket + JSON Offer/Answer` 协议，消息类型只包含 `join`、`offer`、`answer`、`ice-candidate`、`leave`、`error` 六类。
- sender 局域网发现固定镜像 `relavr-view` receiver 的 `_relavr-recv._tcp.local` mDNS 服务，TXT 字段只解析 `name`、`ver`、`sessionId`、`auth`，实际连接地址以 NSD resolve 到的 host/port 为准。
- sender 既然固定依赖 WebSocket 信令、WebRTC 网络状态监测与系统播放音频采集，`app` manifest 必须同时声明 `android.permission.INTERNET`、`android.permission.ACCESS_NETWORK_STATE` 与 `android.permission.RECORD_AUDIO`；缺少前两者会直接导致建链或网络监测失败，缺少后者则必须走视频-only 降级。
- 发送控制台固定开放 `signalingEndpoint`、`sessionId`、编码选择、分辨率、帧率、码率与音频开关；编码和视频规格都只能在开播前修改。规格候选当前固定为 `1280x720 / 1600x900 / 1920x1080`、`24 / 30 / 45 / 60 FPS`、`2000 / 4000 / 6000 / 8000 kbps`，默认值为 `1280x720 / 30 FPS / 4000 kbps`。编码默认优先 H.264，并且只展示设备 MediaCodec 与 libwebrtc 交集后的可用编码。若用户未关闭音频，开始推流前必须先由 `app` 层预检 `RECORD_AUDIO` 运行时权限，但即使用户拒绝，也只能降级为仅视频，不能直接中断整个会话。
- Quest 主界面必须同时提供自由窗口默认尺寸提示和 Compose 响应式布局兜底：`MainActivity` 通过 manifest `layout` 指定平板级默认宽度与最小宽度，发送控制台在 `<600dp`、`600-839dp`、`>=840dp` 三档宽度下分别切换为紧凑单列、居中单列和宽屏双列，避免 VR 设备上出现手机式窄栏布局。
- sender 真实音频固定通过同一 `MediaProjection` 会话上的 `AudioPlaybackCapture + JavaAudioDeviceModule.AudioBufferCallback` 接入 WebRTC，不允许回退到麦克风采集或额外实现独立混音链路。
- 所有会实例化 `DefaultVideoEncoderFactory`、`PeerConnectionFactory` 或其他直接进入 `org.webrtc` native 方法的实现，都必须先复用共享 `WebRtcLibraryInitializer` 执行一次性初始化；禁止把 `PeerConnectionFactory.initialize(...)` 藏在单个调用点的私有细节里并假设其他路径不会提前访问 JNI。
- 当前联调版本在应用层允许 `ws://` 明文信令，以兼容 Android 模拟器 `10.0.2.2` 和开发机局域网地址；如后续切换为 `wss://`，必须同步收紧 manifest 策略并更新对应回归测试。
- sender 控制台进入可编辑空闲态后必须自动启动局域网 discovery，并提供手动刷新入口；当页面离开前台或会话进入准备/推流态时应停止 discovery，发现失败只显示内联提示，不阻断扫码和手动输入兜底。
- sender 发现到 receiver 后必须先经用户确认弹窗，再回填 `signalingEndpoint` / `sessionId` 并走现有开播流程；即使 receiver 广播 `auth=pin`，sender 也只做文案提示，不新增 PIN 输入流程。
- 运行时音频异常只允许降级到静音/仅视频，不做中途 renegotiation；会话主状态继续保持推流中，音频细节通过独立的 `audioState` / `audioDetail` 对外暴露。
- `demo/browser-preview` 只支持单个 `sessionId` 下的一发一收；sender 可先于 receiver 启动，服务端负责缓存最新 `offer` 与 sender 侧 ICE candidate，供后加入的浏览器补齐建链；receiver 页面会尝试自动播放远端音视频，并在被浏览器拦截时提示用户手动恢复声音。

## 测试基线
- 单元测试至少覆盖 codec 选择策略、发送会话状态机与 ViewModel 行为。
- 集成测试至少覆盖开始推流、失败回滚、停止释放。
- Compose UI 测试至少覆盖发送控制台的开始/停止交互与错误展示入口。
- 根目录验收命令固定为 `./gradlew spotlessCheck lintDebug testDebugUnitTest`。
- `demo/browser-preview` 变更必须额外覆盖 Node 单元测试、Node 集成测试，并执行 `npm run format:check`、`npm run lint`、`npm run test`。
