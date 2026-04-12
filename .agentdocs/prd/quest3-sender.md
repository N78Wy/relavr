## 目标
- 构建运行于 Meta Quest 3 的发送端 Android 应用，负责采集画面，并将视频推流能力组织为可扩展的实时链路。

## 当前范围
- 当前仓库只实现发送端。
- 首轮交付重点是多模块工程骨架、发送控制台、采集/编码/推流边界与状态机。
- 真实浏览器接收端、Android 接收端、生产级信令服务均不在本阶段实现。
- 为了缩短联调路径，仓库允许包含一个仅用于本地/局域网验证的浏览器预览 demo，但该 demo 不视为正式接收端能力。

## 默认实现策略
- 使用 Kotlin + Gradle Kotlin DSL + Jetpack Compose + Coroutines/Flow。
- 采用中度扩展的 Android 多模块结构，保留 `app`、`feature`、`core`、`platform`、`testing` 分层。
- 默认编码偏好为 H.264 优先；发送控制台支持在开播前切换到 HEVC、VP8、VP9，实际可选集合由设备 MediaCodec 与 libwebrtc 能力交集决定。
- 发送控制台支持在开播前切换分辨率、帧率和码率，当前使用内置安全档位而非自由输入。
- 当前版本固定为视频-only：不申请 `RECORD_AUDIO`，不实现系统音频采集，也不暴露音频开关。

## 非目标
- 不实现房间、多路流、鉴权、录制、转码。
- 不实现 Meta 官方投屏兼容逻辑。
- 不引入 Hilt、Koin、native/NDK、CMake。
