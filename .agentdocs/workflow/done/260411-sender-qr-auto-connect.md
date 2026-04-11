# 260411 Sender QR Auto Connect

## 背景
- 用户已在 `/home/ubuntu/pj/relavr-view` 实现接收端，希望 Quest 3 sender 通过扫描接收端二维码自动连接。
- receiver 当前已经输出二维码，载荷包含 `host`、`port`、`sessionId` 与 `auth`，sender 只需镜像协议并复用现有 `WebSocket + JSON Offer/Answer` 建链能力。
- 目标平台固定为 Quest 3 / Quest 3S，依赖 Meta `Passthrough Camera API` 提供的 Android `CameraX/Camera2` 能力。

## 目标
- 在 sender 控制台提供扫码入口。
- 成功扫描 receiver 二维码后，自动写入 `signalingEndpoint` / `sessionId` 并直接触发现有开播流程。
- 相机权限拒绝、设备不支持、二维码非法时，保留手动输入地址的兜底路径。

## 实施阶段
- [x] 阶段 1：补齐 sender 侧二维码协议镜像与解析测试。
- [x] 阶段 2：补齐发送控制台扫码状态、交互入口与自动开播编排。
- [x] 阶段 3：接入 Quest passthrough 相机扫码覆盖层与权限处理。
- [x] 阶段 4：补齐测试并完成 `spotlessCheck`、`lintDebug`、`testDebugUnitTest`。

## 决策
- 不改 `relavr-view` receiver 仓库，避免引入跨仓耦合修改。
- 不新增 sender 侧 PIN 输入，`auth` 仅用于界面提示；接收端继续本地确认。
- 扫码成功后直接发起开播，沿用现有录音权限与 MediaProjection 权限流程。

## 验收点
- sender 可从合法 receiver 二维码解析出 `ws://host:port` 与 `sessionId`。
- sender 仅在二维码合法时自动开播，非法二维码不会污染当前配置。
- 扫码流程不影响手动输入连接地址与原有开播/停播能力。

## 完成记录
- sender `core:model` 已镜像 receiver 二维码协议，新增 `ReceiverConnectPayloadCodec`、`ReceiverConnectionInfo` 与轻量 JSON 解析器。
- sender 控制台已新增“扫码连接接收端”入口；扫码成功会自动回填地址并直接走现有前台服务开播流程。
- `app` 模块已接入 Quest passthrough 相机扫码覆盖层，优先按 Meta vendor tag 选择 RGB 相机，并保留权限拒绝/设备不支持时的手动输入兜底。
- 已通过仓库要求的 `./gradlew spotlessCheck lintDebug testDebugUnitTest`。
