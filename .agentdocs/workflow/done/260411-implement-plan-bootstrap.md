## 背景
- 仓库当前只有需求文档与 `plan.md`，尚未初始化 Android 工程。
- 目标是把 `plan.md` 中的 Quest 3 发送端框架规划落成可继续演进的多模块工程骨架。

## 分阶段计划
1. 建立 `.agentdocs` 索引、基础治理文档与当前任务文档。
2. 初始化 Gradle Kotlin DSL、多模块设置、`build-logic` 与根目录校验基线。
3. 实现核心模型、发送会话编排接口、平台占位实现与 Compose 发送控制台。
4. 补充单元测试、集成测试与 Compose UI 测试。
5. 执行本地校验并根据结果修正工程配置。

## TODO
- [x] 初始化 `.agentdocs` 索引与基础架构文档。
- [x] 初始化 Android 多模块工程、版本目录与 convention plugins。
- [x] 实现 `StreamingSessionCoordinator`、codec 能力策略、权限桥接与发送控制台。
- [x] 补充单元测试、集成测试与 UI 测试。
- [x] 运行 `spotlessCheck`、`lintDebug`、`testDebugUnitTest`、`connectedDebugAndroidTest`。

## 实现结果
- 已建立 `app`、`feature`、`core`、`platform`、`testing` 五层多模块骨架，并统一接入 `build-logic` convention plugins。
- 已实现发送会话状态机、MediaProjection 权限桥接、MediaCodec 能力探测、音频接缝与 `platform/webrtc` 占位推流实现。
- 已落下 Compose 发送控制台，以及覆盖 codec 策略、会话编排、ViewModel 和 UI 交互的测试。

## 验证记录
- `./gradlew -g /home/ubuntu/pj/relavr/.gradle-home spotlessCheck`：通过。
- `./gradlew -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon lintDebug`：通过。
- `./gradlew -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon testDebugUnitTest`：通过。
- `./gradlew -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon connectedDebugAndroidTest`：任务已执行到安装/连接阶段，因当前环境无已连接 Android 设备而失败。

## 关键决策
- 先把平台能力拆成稳定接口，真实 WebRTC 生产实现后续再在 `platform/webrtc` 内替换，不把复杂度提前压进首版骨架。
- 使用 `AndroidProjectionPermissionGateway` 作为 UI 与系统授权的桥接点，让发送会话状态机仍然保持统一入口。
