# receiver-connect v2 + WSS 兼容

## 背景
- sender 手动输入 `signalingEndpoint` 已支持 `ws://` 与 `wss://`，但扫码协议仍只会恢复 `ws://host:port`。
- `browser-preview` 未来会部署到 HTTPS 站点，因此 sender 需要能从二维码直接恢复 `wss://` 与非根 path。
- 当前功能尚未上线，本次允许直接做二维码协议断点升级，不保留旧版兼容。

## 分阶段计划
1. 升级 sender 侧二维码模型与编解码协议。
2. 调整扫码回填、最近扫码提示和手动输入文案。
3. 更新仓库记忆并完成本地校验。

## TODO
- [x] 升级 sender 侧 `receiver-connect` 模型与编解码。
- [x] 支持从二维码精确恢复 `ws/wss + path`，并更新 UI 提示。
- [x] 完成文档回写与本地校验。

## 当前决策
- sender 本次只识别 `receiver-connect v2`，不再保留旧版 `v1` 兼容分支。
- 扫码后直接回填二维码里的完整 signaling 地址，不再默认拼成 `ws://host:port`。
- Android receiver 继续使用 `ws://`，HTTPS 部署的 browser-preview 可通过二维码回填 `wss://`。

## 已落地结果
- sender `ReceiverConnectionInfo` / `ReceiverConnectPayloadCodec` 已升级到 `v2`，新增 `scheme` 与 `path`。
- 扫码成功后，sender 会回填完整 `webSocketUrl`，最近扫码提示也改为展示完整 `ws://` 或 `wss://` 地址。
- 发送控制台手动输入文案已明确区分局域网 `ws://` 与 HTTPS browser-preview `wss://` 两种场景。

## 验证结果
- `cd /home/ubuntu/pj/relavr && ./gradlew spotlessCheck`
- `cd /home/ubuntu/pj/relavr && ./gradlew lintDebug`
- `cd /home/ubuntu/pj/relavr && ./gradlew testDebugUnitTest`
