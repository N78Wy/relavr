## 背景
- Android sender 默认信令地址原先是 `ws://10.0.2.2:8080/ws`，但应用清单没有显式放开明文流量，导致 `WebSocketSignaling` 在连接本地 demo 时被网络安全策略拦截。
- 当前仓库的浏览器预览 demo 仍以本机或局域网 `ws://` 联调为主，不具备直接切换 `wss://` 的必要条件。
- Quest 3 实机联调时继续默认填入 `10.0.2.2` 也会误导用户，因此需要同步调整默认配置与界面提示。

## 分阶段计划
1. 调整 Android 应用网络安全策略，允许 sender 使用明文 WebSocket 连接本机与局域网信令服务。
2. 移除默认 `10.0.2.2` 信令地址，改为要求用户手动填写有效 `ws://` 或 `wss://` 地址。
3. 补充模型校验、ViewModel / UI / instrumentation 回归测试，并更新技术文档记忆。

## TODO
- [x] 放开应用级明文流量策略，修复 `CLEARTEXT communication ... not permitted`。
- [x] 将默认 `signalingEndpoint` 改为空值，并补充明确的空地址校验。
- [x] 更新发送控制台输入提示与默认交互状态。
- [x] 补充单元测试、Compose UI 测试与 Android instrumentation 测试。
- [x] 更新架构文档与索引。
- [x] 执行 `spotlessCheck`、`lintDebug`、`testDebugUnitTest`、`connectedDebugAndroidTest` 并记录结果。

## 关键决策
- 按当前阶段联调需求，在所有构建变体的应用层统一允许 `ws://` 明文流量，不拆分 `debug` / `release` 差异配置。
- 默认信令地址改为空字符串，而不是继续保留 `10.0.2.2` 或写入伪示例地址，避免 Quest 3 实机首次启动时直接落到错误配置。
- 回归测试以 `NetworkSecurityPolicy` 对模拟器地址和局域网地址的断言为准，防止后续 manifest 调整导致明文能力回退。

## 实现结果
- `app` 清单已显式开启 `usesCleartextTraffic`，sender 现在可以连接 `ws://10.0.2.2:8080/ws` 与 `ws://<局域网IP>:8080/ws`。
- `StreamConfig` 默认 `signalingEndpoint` 为空，并在校验时返回“WebSocket 地址不能为空”，`Session ID` 默认值继续保留。
- 发送控制台的 WebSocket 输入框新增示例占位文本；初始状态下开始按钮保持禁用，用户填写合法地址后才能启动推流。
- 相关单元测试与集成测试已改为显式提供合法信令地址；新增 instrumentation 测试覆盖明文策略断言。

## 验证记录
- `./gradlew spotlessCheck`：通过。
- `./gradlew lintDebug`：通过。
- `./gradlew testDebugUnitTest`：通过。
- `./gradlew connectedDebugAndroidTest`：执行到设备阶段后失败，原因是当前环境没有已连接 Android 设备，Gradle 报错 `No connected devices!`。
