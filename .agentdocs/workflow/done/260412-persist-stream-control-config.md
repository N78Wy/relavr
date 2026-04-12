## 背景
- 当前发送控制台配置只保存在 `StreamControlViewModel` 的内存状态里，应用重启后用户需要重新填写信令地址、会话 ID 和推流规格。
- 发送端已经支持手动填写和扫码自动填充配置，但这些输入都不会落地到本地存储，重复联调成本较高。
- 仓库当前没有现成的发送控制台配置持久化实现，需要在保持 `feature` 层平台无关的前提下补齐本地恢复能力。

## 分阶段计划
1. 创建任务文档并更新索引，明确持久化字段范围和模块边界。
2. 在 `feature/stream-control` 引入配置存储抽象，并改造 ViewModel 的自动加载 / 自动保存流程。
3. 在 `app` 层接入 `DataStore` 实现持久化与依赖装配。
4. 补充 ViewModel 与存储实现测试，覆盖恢复、保存、扫码覆盖和非法值回退。
5. 执行根目录校验命令并完成文档回顾归档。

## TODO
- [x] 创建当前任务文档并更新索引。
- [x] 新增发送控制台配置持久化抽象并接入 ViewModel。
- [x] 实现 `DataStore` 持久化与应用装配。
- [x] 补齐单元测试与集成测试覆盖。
- [x] 执行 `spotlessCheck`、`lintDebug`、`testDebugUnitTest` 并回写结果。

## 关键决策
- 本地持久化范围固定为发送控制台全部可编辑字段：`signalingEndpoint`、`sessionId`、编码偏好、分辨率、帧率、码率、音频开关。
- 持久化抽象放在 `feature/stream-control`，由 `app` 层实现 `DataStore`，保持 `feature` 不依赖 Android 存储 API。
- 自动保存采用“配置变更后异步落盘”的方式，不增加单独的保存按钮，也不改变现有开始推流流程。

## 验收记录
- 2026-04-12：`./gradlew :feature:stream-control:testDebugUnitTest` 通过。
- 2026-04-12：`./gradlew :app:testDebugUnitTest --tests io.relavr.sender.app.PreferencesStreamControlConfigStoreTest` 通过。
- 2026-04-12：`./gradlew spotlessCheck lintDebug testDebugUnitTest` 通过。

## 实现结果
- `feature/stream-control` 新增 `StreamControlConfigStore` 抽象，并将 `StreamControlViewModel` 的配置更新统一收口到“更新内存状态并异步持久化”的路径；初始化时会先恢复本地配置，再刷新设备能力。
- `StreamControlViewModel` 会在手动编辑、扫码自动填充和 codec 能力回退时同步保存配置；若本地配置读取较慢，已发生的用户输入不会被延迟返回的旧配置覆盖。
- `app` 新增 `PreferencesStreamControlConfigStore`，固定用 `DataStore` 持久化发送控制台全部可编辑字段，并对未知 codec、非法分辨率、非法帧率和非法码率回退到默认值。
- 单元测试已覆盖本地恢复、即时保存、扫码覆盖、非法二维码不污染配置、能力回退后重新保存，以及存储 round-trip 与坏数据回退场景。
