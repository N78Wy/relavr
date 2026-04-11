# 260411 Sender LAN Discovery Connect

## 背景
- `relavr-view` 接收端已新增基于 Android NSD 的 mDNS 广播，sender 需要在局域网内自动发现接收端并提供选择连接入口。
- sender 当前只有“手动填写 WebSocket 地址”和“扫码解析 receiver 二维码后自动开播”两条连接路径，还没有 discovery 层。

## 目标
- sender 进入发送控制台后自动扫描局域网内可用 receiver，并允许用户手动刷新。
- 发现结果在控制台中展示，用户点击某个 receiver 后先确认，再复用现有开播流程发起连接。
- 发现失败、发现为空或服务瞬时掉线时，不影响扫码和手动输入两条兜底路径。

## 实施阶段
- [x] 阶段 1：补齐 sender 侧 discovery 协议镜像、状态模型与任务文档。
- [x] 阶段 2：实现 Android NSD 发现模块与编排层。
- [x] 阶段 3：接入发送控制台 UI、自动发现生命周期与连接确认弹窗。
- [x] 阶段 4：补齐测试并完成 `spotlessCheck`、`lintDebug`、`testDebugUnitTest`。

## 决策
- 发现到 receiver 后不直接连接，必须先弹确认框，再由用户明确确认。
- sender 不新增 PIN 输入流程；若接收端广播 `auth=pin`，只在界面提示“接收端仍需本地确认”。
- discovery 采用 best-effort 策略，失败只展示内联错误，不阻断扫码与手动连接。

## 验收点
- sender 能自动发现 `_relavr-recv._tcp` 服务，并从 TXT 字段解析展示名、signaling 端口、Session ID 与鉴权模式。
- sender 点击发现结果并确认后，会自动回填 `signalingEndpoint` / `sessionId` 并复用现有开播流程。
- receiver 离线、TXT 非法或发现失败时，不会污染当前配置，也不会影响手动填写和扫码连接。

## 完成记录
- sender 已新增 `platform/discovery` 模块，使用 Android `NsdManager` 发现局域网内 receiver，并通过 `core:session` 的 `ReceiverDiscoveryCoordinator` 统一编排发现状态。
- 发送控制台已新增自动发现列表、刷新按钮和连接确认弹窗；点击某个 receiver 后会先确认，再复用现有推流开播流程。
- sender discovery 镜像了 receiver 的 TXT 协议字段，并补上 IPv6 地址方括号处理；实际连接地址固定取 NSD resolve 到的 host 与 TXT `port`，避免 `ws://host:port` 拼接到错误端口。
- 已通过仓库要求的 `./gradlew spotlessCheck lintDebug testDebugUnitTest`。
