# Relavr Sender

Relavr Sender 是运行在 Meta Quest 3 上的 Android 发送端应用，用于采集头显画面，并通过 WebRTC 将视频和可选系统音频推送到接收端。

当前仓库只包含发送端。生产级接收端、房间服务、鉴权、录制、转码和 Meta 官方投屏兼容逻辑不在当前范围内。

## 功能概览

- 通过 `MediaProjection` 采集 Quest 3 画面。
- 通过 WebRTC 发布实时视频流。
- 支持可选系统音频推流，基于 Android `AudioPlaybackCapture` 采集允许被系统捕获的其他应用媒体声或游戏声。
- 支持 H.264、HEVC、VP8、VP9 编码偏好选择，实际可用项由设备 MediaCodec 与 libwebrtc 能力交集决定。
- 支持开播前选择分辨率、帧率和码率，并在运行期编码过载时自动降档保护实时性。
- 支持扫码连接 receiver-connect v2 二维码，也支持手动输入 `IP/域名 + 端口`。
- 支持 `ws://` 与 `wss://` WebSocket 信令地址。
- 支持 English 与简体中文界面语言。

## 技术栈

- Kotlin
- Gradle Kotlin DSL
- Jetpack Compose / Material 3
- Coroutines / Flow
- AndroidX CameraX
- Android DataStore
- WebRTC Android SDK
- OkHttp WebSocket
- Spotless + ktlint

主要版本统一维护在 [gradle/libs.versions.toml](gradle/libs.versions.toml)。

## 工程结构

```text
app/                       Android 应用入口、Compose 单 Activity、权限桥接、前台服务与依赖组装
feature/stream-control/    发送控制台 UI、ViewModel、配置编辑与界面状态映射
core/common/               通用线程与基础能力
core/model/                配置、能力快照、会话状态、错误模型与信令配置校验
core/session/              发送会话编排层，定义授权、推流、信令和 RTC 事件边界
platform/android-capture/  MediaProjection 授权桥接与 AudioPlaybackCapture 系统音频采集实现
platform/media-codec/      编码能力探测、默认 Codec 策略与 fallback
platform/webrtc/           WebRTC 推流、WebSocket 信令、SDP 编码偏好与系统音频注入
testing/fakes/             单元测试和集成测试复用的 fake 实现
build-logic/               Android convention plugin 与统一构建配置
```

依赖方向保持为：`app` 组装依赖，`feature` 依赖 `core`，`platform` 只依赖 `core`，`testing/fakes` 为测试提供替身实现。

## 环境要求

- JDK 17
- Android SDK，可用 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` 指向 SDK 目录
- Android Gradle Plugin 8.5.2 对应的构建环境
- 可调试的 Meta Quest 3 或 Android 设备

项目包含 Gradle Wrapper，通常不需要本机预装 Gradle。

## 构建与安装

在仓库根目录执行：

```bash
./gradlew assembleDebug
```

安装到已连接设备：

```bash
./gradlew installDebug
```

如需直接通过 Android Studio 运行，打开仓库根目录并选择 `app` 模块的 debug 变体。

## 使用方式

1. 启动接收端或信令服务，并准备好 WebSocket signaling 地址。
2. 在 Quest 3 上打开 Relavr Sender。
3. 选择扫码连接 receiver 二维码，或在首页输入接收端 `IP/域名 + 端口`。
4. 如需调整协议路径、`sessionId`、语言、系统音频、编码或视频规格，进入更多设置页并在开播前修改。
5. 点击开始后，按系统提示授予屏幕采集权限；如启用系统音频，还需要授予录音权限。

系统音频不可用、权限拒绝或运行期采集失败时，应用会自动降级为 video-only 或静音，不应中断视频推流。

## 信令约定

发送端建链固定使用 `WebSocket + JSON Offer/Answer`。当前消息类型包括：

- `join`
- `offer`
- `answer`
- `ice-candidate`
- `leave`
- `error`

扫码连接固定解析 receiver-connect v2 载荷，并从二维码恢复 `scheme`、`host`、`port`、`path` 与 `sessionId`。因此接收端二维码需要提供完整且可访问的 `ws://` 或 `wss://` signaling 地址。

## 本地验证

提交或交付变更前至少执行：

```bash
./gradlew spotlessCheck
./gradlew lintDebug
./gradlew testDebugUnitTest
```

也可以合并执行：

```bash
./gradlew spotlessCheck lintDebug testDebugUnitTest
```

涉及设备能力、权限弹窗、MediaProjection 或真实 WebRTC 链路的改动，建议额外在 Quest 3 真机上完成手动回归。

## 开发约束

- 代码风格由 Spotless 和 ktlint 统一维护。
- 依赖版本统一维护在 `gradle/libs.versions.toml`。
- 不引入 Hilt、Koin、native/NDK 或 CMake。
- 触发 `org.webrtc` JNI 的路径必须先复用共享 `WebRtcLibraryInitializer` 完成一次性初始化。
- MediaProjection 会话必须由 `app` 模块内的 `mediaProjection` 类型前台服务启动与持有。
- 用户可见文案、错误原因与会话状态细节应通过资源或语义类型表达，避免在状态层缓存最终展示字符串。

## 许可证

本项目基于 MIT License 授权，详见 [LICENSE](LICENSE)。
