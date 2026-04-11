## 工程结构
- `app`：Compose 单 Activity 壳层、`AppContainer`、系统权限桥接与应用入口。
- `feature/stream-control`：发送控制台 UI、ViewModel、用户动作与界面状态映射。
- `core/common`：线程抽象与可复用基础能力，不依赖具体平台实现。
- `core/model`：配置、能力快照、状态与错误模型。
- `core/session`：发送会话编排层，定义采集、编码能力、推流、信令等边界接口。
- `platform/android-capture`：MediaProjection 授权桥接、视频采集资源封装、音频接缝实现。
- `platform/media-codec`：编码能力探测与默认 CodecPolicy。
- `platform/webrtc`：推流会话与信令占位实现，当前以可编译的占位适配为主。
- `testing/fakes`：供单元测试和集成测试复用的 fake 实现。

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

## 测试基线
- 单元测试至少覆盖 codec 选择策略、发送会话状态机与 ViewModel 行为。
- 集成测试至少覆盖开始推流、失败回滚、停止释放。
- Compose UI 测试至少覆盖发送控制台的开始/停止交互与错误展示入口。
- 根目录验收命令固定为 `./gradlew spotlessCheck lintDebug testDebugUnitTest connectedDebugAndroidTest`。
