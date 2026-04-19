## 工程结构
- `app`：Compose 单 Activity 壳层、`AppContainer`、系统权限桥接与应用入口。
- `feature/stream-control`：发送控制台 UI、ViewModel、用户动作与界面状态映射。
- `core/common`：线程抽象与可复用基础能力，不依赖具体平台实现。
- `core/model`：配置、能力快照、状态、错误模型与信令配置校验。
- `core/session`：发送会话编排层，定义授权、推流、信令消息与 RTC 事件边界接口。
- `platform/android-capture`：MediaProjection 授权桥接与 `AudioPlaybackCapture` 系统音频采集实现。
- `platform/media-codec`：编码能力探测、默认 CodecPolicy 与 codec fallback 策略。
- `platform/webrtc`：`ScreenCapturerAndroid + PeerConnection + WebSocket` 推流实现，负责 sender 侧 JSON 信令协议、WebRTC codec 能力探测、系统音频注入与按编码偏好排序的 SDP 协商。
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
- sender 扫码连接接收端时固定解析 `receiver-connect v2`，从二维码恢复 `scheme`、`host`、`port`、`path` 与 `sessionId`；因此扫码链路必须同时兼容 `ws://` 和 `wss://`。
- sender 既然固定依赖 WebSocket 信令、WebRTC 网络状态监测与 `AudioPlaybackCapture`，`app` manifest 必须声明 `android.permission.INTERNET`、`android.permission.ACCESS_NETWORK_STATE` 与 `android.permission.RECORD_AUDIO`；录音权限必须在 app 层预检，若未授权或系统音频初始化失败，只允许降级到 video-only，不得阻断整场投屏。
- 发送控制台固定开放 `signalingEndpoint`、`sessionId`、编码选择、分辨率、帧率与码率；这些配置都只能在开播前修改。规格候选当前固定为 `1280x720 / 1600x900 / 1920x1080`、`24 / 30 / 45 / 60 FPS`、`2000 / 4000 / 6000 / 8000 kbps`，默认值为 `1280x720 / 30 FPS / 4000 kbps`。编码默认优先 H.264，并且只展示设备 MediaCodec 与 libwebrtc 交集后的可用编码。
- sender 视频能力现在必须同时维护两层概念：开播前用户选择的 requested profile，以及会话中真实生效的 active profile。设备能力探测需要为预设规格生成 `codec + resolution + fps + bitrate` 组合矩阵，开始推流前据此做二次校验；如果会话内检测到硬件编码器持续过载，则允许在不重连的前提下按预设梯度自动降档，并通过独立状态把 active profile 与降档原因同步给 UI / 通知层。
- sender 视频过载治理必须优先保护实时性与内存安全：运行期视频健康采样固定按 `250ms` 周期执行，降档顺序固定为 `bitrate -> fps -> resolution`，且最低档位持续过载时必须尽快进入保护分支，不能容忍 native 视频 backlog 长时间增长。
- sender 系统音频能力同样必须维护“用户请求”和“会话真实状态”两层语义：发送控制台里的 `audioEnabled` 表示用户希望投屏时附带系统音频；会话快照中的 `audioState/audioDetail` 表示当前是否真的成功采集、是否已降级为仅视频或静音。权限拒绝、永久拒绝、`AudioPlaybackCapture` 初始化失败与运行期读取失败都必须收敛到常规状态机，不允许散落成额外补丁。
- sender 的系统音频桥接必须保持低延时小缓冲：`AudioPlaybackCapture` 读取线程使用独立单线程调度与音频优先级，WebRTC 输入侧 ring buffer 只保留极小窗口并以“丢旧帧、绝不无界积压”为原则；任何新的音频实现都不得把完整性优先于实时性与内存安全。
- 发送控制台的可编辑配置必须统一通过 `feature/stream-control` 暴露的 `StreamControlConfigStore` 接缝加载和保存；`feature` 只依赖抽象，具体持久化固定由 `app` 层 `DataStore` 实现，禁止在 `feature` 直接访问 `DataStore`、`SharedPreferences` 等 Android 存储 API。
- 录音权限桥接必须继续由 `app` 层统一持有：`MainActivity` 负责同步 `RECORD_AUDIO` 实时状态、触发系统权限框与应用设置页，`feature` 只通过抽象的权限控制器消费 `Granted / Requestable / PermanentlyDenied` 三态，禁止在 `feature` 直接调用 Android 权限 API。
- Quest 主界面必须同时提供自由窗口默认尺寸提示和 Compose 响应式布局兜底：`MainActivity` 通过 manifest `layout` 指定平板级默认宽度与最小宽度，发送控制台在 `<600dp`、`600-839dp`、`>=840dp` 三档宽度下分别切换为紧凑单列、居中单列和宽屏双列，避免 VR 设备上出现手机式窄栏布局。
- 所有会实例化 `DefaultVideoEncoderFactory`、`PeerConnectionFactory` 或其他直接进入 `org.webrtc` native 方法的实现，都必须先复用共享 `WebRtcLibraryInitializer` 执行一次性初始化；禁止把 `PeerConnectionFactory.initialize(...)` 藏在单个调用点的私有细节里并假设其他路径不会提前访问 JNI。
- WebRTC codec 能力探测同样必须使用共享 EGL 上下文，不能再用 `DefaultVideoEncoderFactory(null, ...)` 这类无 EGL 的探测路径，否则容易混入非 texture mode 警告并误导性能判断。
- 当前发送控制台和扫码链路都必须接受 `ws://` 与 `wss://` 两类 signaling 地址：Android receiver 继续以 `ws://` 为主，部署到 HTTPS 的 browser-preview 可回填 `wss://`；如后续要强制全站只保留 `wss://`，必须同步收紧 manifest 策略并更新回归测试。
- sender app 现已固定支持 `English` 与 `简体中文` 两种界面语言；首次启动跟随系统，用户手动切换后由 `AppCompat` locale 持久化恢复。
- 只要 `MainActivity` 继续继承 `AppCompatActivity` 且语言切换仍依赖 `AppCompat` locale，`app` 启动主题就必须保持 `AppCompat` 兼容父主题，不能回退到 `@android:style/Theme.DeviceDefault.*` 等非 `AppCompat` 主题，否则会在 `setContent` 前直接启动崩溃。
- 所有用户可见文案、错误原因与会话状态细节都必须通过 `UiText` 或等价语义类型表达，`ViewModel`、会话快照与错误模型不得缓存最终展示字符串，避免语言切换后残留旧 locale 文案。
- `demo/browser-preview` 只支持单个 `sessionId` 下的一发一收；sender 可先于 receiver 启动，服务端负责缓存最新 `offer` 与 sender 侧 ICE candidate，供后加入的浏览器补齐建链；当前 sender 会在权限与系统能力允许时附带系统音频，否则自动回退为仅视频。

## 测试基线
- 单元测试至少覆盖 codec 选择策略、发送会话状态机与 ViewModel 行为。
- 集成测试至少覆盖开始推流、失败回滚、停止释放。
- Compose UI 测试至少覆盖发送控制台的开始/停止交互与错误展示入口。
- 根目录验收命令固定为 `./gradlew spotlessCheck lintDebug testDebugUnitTest`。
- `demo/browser-preview` 变更必须额外覆盖 Node 单元测试、Node 集成测试，并执行 `npm run format:check`、`npm run lint`、`npm run test`。
