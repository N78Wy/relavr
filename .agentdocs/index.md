## 产品文档
`prd/quest3-sender.md` - Quest 3 发送端的目标、范围、默认实现策略与非目标；首次进入项目时必读。

## Android 文档
`android/architecture.md` - 多模块边界、依赖方向、构建约束与测试基线；修改模块结构或公共接口时必读。

## 当前任务文档
- 当前无进行中的任务文档。

## 已完成任务文档
`workflow/done/260419-fix-permission-recheck-and-settings-recovery.md` - 已为扫码相机权限补齐永久拒绝后的系统设置恢复入口，并修复录音/相机权限从系统设置返回后的重检与恢复链路。
`workflow/done/260419-redesign-sender-home-ui.md` - 已将 sender 首页重构为扫码/IP+端口双入口，新增全屏更多设置页承接语言、会话、协议路径、音频与画质配置，并补齐回归测试与验收记录。
`workflow/done/260419-fix-stop-stream-audio-crash.md` - 已修复 sender 在停止推流时提前清空 WebRTC 音频注入字段导致的闪退，补充 stop 清理时序约束与回归单测。
`workflow/done/260419-fix-playback-capture-audio-underrun.md` - 已将 sender 系统音频注入改为复用 WebRtcAudioRecord 的原生节奏，修复 AudioBufferCallback 主链路导致的高频欠载、噪声与 sender 侧内存异常增长。
`workflow/done/260416-fix-system-audio-overload.md` - 已为 sender 增加音视频过载观测、bitrate 优先快速降档、紧凑音频桥接缓冲与 EGL 上下文 codec 探测，缓解系统音频开启后的延时飙升与 native 内存暴涨。
`workflow/done/260416-reimplement-system-audio-casting.md` - 已重新设计 sender 系统音频投屏链路，基于 AudioPlaybackCapture 重建权限、采集、WebRTC 注入、video-only 降级与发送控制台状态。
`workflow/done/260412-remove-audio-streaming.md` - 已移除 sender 音频采集、录音权限、音轨发布、音频 UI 与相关测试，当前版本固定为纯视频推流。
`workflow/done/260412-fix-audio-permission-permanent-denial.md` - 已修复 sender 在录音权限“拒绝且不再提醒”后没有恢复入口的问题，记录权限三态、设置引导与验证结果。
`workflow/done/260412-fix-audio-permission-reprompt.md` - 已修复 sender 音频开关在首次拒绝录音权限后无法再次触发授权的问题，记录权限状态机、app 层兜底与验收结果。
`workflow/done/260412-receiver-connect-v2-wss.md` - 已将 sender 扫码协议升级到 receiver-connect v2，支持从二维码精确恢复 ws / wss 与 signaling path。
`workflow/done/260412-persist-stream-control-config.md` - 已为发送控制台增加本地配置自动保存与恢复，记录持久化边界、DataStore 落地与验收结果。
`workflow/done/260412-bilingualize-code-and-ui.md` - 已完成双仓实现层英文化、Android / 浏览器页双语接入、应用内语言切换与验收。
`workflow/done/260412-remove-mdns-discovery.md` - 已移除 sender 侧全部 mDNS discovery 代码、控制台 UI、模块依赖与当前记忆。
`workflow/done/260412-fix-video-encoder-backpressure.md` - 已为 sender 增加视频规格支持矩阵、运行时编码过载自动降档与最低档失败保护，记录背压根因判断、状态同步与验证结果。
`workflow/done/260412-fix-reconnect-after-first-session.md` - 修复首次推流结束后 receiver 被旧 ICE 打进错误态，导致第二次 sender 连接直接被拒绝的问题。
`workflow/done/260411-fix-discovery-signaling-endpoint.md` - 修复 sender 通过 mDNS 发现 receiver 后使用错误 signaling 端口导致的连接被拒绝问题，并记录双仓对齐策略。
`workflow/done/260411-sender-lan-discovery-connect.md` - 已为 Quest 3 sender 增加局域网 mDNS 发现、接收端选择与确认连接入口，并记录发现协议镜像、UI 交互与验证结果。
`workflow/done/260411-sender-qr-auto-connect.md` - 已为 Quest 3 sender 增加扫码解析 receiver 二维码并自动连接的能力，记录协议镜像、Quest 相机接入与验收结果。
`workflow/done/260411-expand-stream-options-and-adaptive-layout.md` - 已扩展发送端规格选择，修复 Quest 默认窄窗、自适应布局与低对比度文字问题，并记录验证结果。
`workflow/done/260411-browser-preview-demo.md` - 已增加本地/局域网浏览器预览 demo，记录 Node 信令服务、浏览器 receiver 页面与验证结果。
`workflow/done/260411-implement-plan-bootstrap.md` - 已完成 Quest 3 发送端 Android 多模块骨架初始化，记录实现边界与验证结果。
`workflow/done/260411-fix-media-projection-foreground-service.md` - 已修复 MediaProjection 缺少 `mediaProjection` 前台服务导致的启动失败，并记录服务化与授权约束。
`workflow/done/260411-implement-webrtc-video-streaming.md` - 已完成 WebRTC 视频推流闭环、sender 侧 JSON 信令协议与控制台配置入口，并记录验证结果。
`workflow/done/260411-fix-cleartext-websocket-policy.md` - 已修复 Android 明文 WebSocket 被网络安全策略拦截的问题，记录 manifest 策略、默认地址调整与回归测试。
`workflow/done/260411-implement-webrtc-audio-streaming.md` - 已完成 Quest 3 sender 音频采集、WebRTC 音轨推流、录音权限预检与浏览器音频预览闭环，并记录降级策略与验证结果。
`workflow/done/260411-implement-codec-switching.md` - 已完成发送端多编码格式切换，记录能力交集、默认回退策略、发送控制台改造与验证结果。
`workflow/done/260411-fix-webrtc-native-init-order.md` - 已修复 WebRTC codec 探测早于 native 初始化导致的启动崩溃，记录共享初始化器方案与回归验证。

## 全局重要记忆
- 本仓库主线仍以 Quest 3 发送端为核心，但包含一个仅用于本地/局域网联调的浏览器预览 demo，不作为正式接收端产品实现。
- 技术栈固定为 Kotlin、Gradle Kotlin DSL、Jetpack Compose、Coroutines/Flow。
- 首版依赖注入方式固定为 AppContainer + 构造注入，不引入 Hilt 或 Koin。
- 所有会触发 `org.webrtc` JNI 的路径都必须先复用共享 `WebRtcLibraryInitializer` 完成一次性初始化，不能假设 native 库已由其他流程预先加载。
- 默认推流策略固定为 H.264 优先；若当前设备或 libwebrtc 不支持 H.264，则按 HEVC、VP8、VP9 顺序回退。发送控制台支持在开播前切换编码偏好，推流中保持锁定。
- 首阶段 sender 建链协议固定为 `WebSocket + JSON Offer/Answer`，发送控制台必须提供 `signalingEndpoint` 与 `sessionId` 输入。
- sender 扫码自动连接当前固定镜像 `relavr-view` 的 `receiver-connect v2` 协议，载荷包含 `scheme/path`；扫码后必须精确恢复二维码里的完整 `ws/wss` signaling 地址。
- 当前版本 sender 支持视频推流与可选系统音频推流；系统音频固定走 `AudioPlaybackCapture`，仅覆盖允许被系统捕获的其他 app 媒体声或游戏声；若录音权限缺失、初始化失败或运行期读取异常，则自动降级为 video-only 或静音而不中断视频。
- 当前 sender 的系统音频输入固定通过 `AudioPlaybackCapture` 创建 `REMOTE_SUBMIX AudioRecord`，并注入到 libwebrtc 的 `WebRtcAudioRecord` 节奏中；任何替代实现都必须先证明不会重新引入持续欠载、噪声或 native 内存线性增长。
- sender 视频规格继续允许在开播前自由选择，但运行时必须区分用户请求的 requested profile 与实际生效的 active profile；若检测到硬编持续过载，允许在同一会话内按预设梯度自动降档，并在最低档仍无法稳定时主动结束会话，避免编码积压导致 OOM。
- 提交前必须至少执行 `./gradlew spotlessCheck`、`./gradlew lintDebug`、`./gradlew testDebugUnitTest`。
- 如改动 `demo/browser-preview`，还必须执行 `npm run format:check`、`npm run lint`、`npm run test`。
