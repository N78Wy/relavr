## 目标
- 构建运行于 Meta Quest 3 的发送端 Android 应用，负责采集画面与音频，并将音视频能力组织为可扩展的实时推流链路。

## 当前范围
- 当前仓库只实现发送端。
- 首轮交付重点是多模块工程骨架、发送控制台、采集/编码/推流边界与状态机。
- 真实浏览器接收端、Android 接收端、生产级信令服务均不在本阶段实现。
- 为了缩短联调路径，仓库允许包含一个仅用于本地/局域网验证的浏览器预览 demo，但该 demo 不视为正式接收端能力。

## 默认实现策略
- 使用 Kotlin + Gradle Kotlin DSL + Jetpack Compose + Coroutines/Flow。
- 采用中度扩展的 Android 多模块结构，保留 `app`、`feature`、`core`、`platform`、`testing` 分层。
- 默认编码偏好固定为 H.264；HEVC、VP8、VP9 通过能力探测决定是否展示。
- 音频按独立模块接缝设计，首版先预留 AudioPlaybackCapture 能力与错误模型，不引入 native WebRTC 音频扩展。

## 非目标
- 不实现房间、多路流、鉴权、录制、转码。
- 不实现 Meta 官方投屏兼容逻辑。
- 不引入 Hilt、Koin、native/NDK、CMake。
