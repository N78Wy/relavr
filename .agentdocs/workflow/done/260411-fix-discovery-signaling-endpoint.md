# 修复局域网发现后的 signaling 连接地址不一致

## 背景
- sender 已能通过 mDNS 发现 receiver，但实机连接日志出现 `ECONNREFUSED`，目标地址为发现结果回填的 `ws://host:port`。
- receiver discovery 协议文档把 `port` 定义为 TXT 字段，用于承载实际 signaling 监听端口。
- sender 当前 discovery 实现只使用 NSD resolve 返回的 `host/port`，没有解析 TXT 里的 `port`。
- receiver 当前广播使用 `ReceiverConfig.toAdvertiseInfo()`，没有复用 signaling host 真正启动后的 `connectionInfo`。

## 目标
- sender 发现到 receiver 后，优先使用 discovery TXT 中的 `port` 生成连接地址，NSD resolve 的端口只作为回退值。
- receiver 注册 mDNS 广播时，固定复用 signaling host 已启动后的 `connectionInfo`，避免配置端口与实际监听端口漂移。
- 补齐双仓测试与文档，确认不会再因为 discovery 端口错位导致连接被拒绝。

## TODO
- [x] 梳理 sender discovery 与 receiver signaling / advertiser 的端口来源。
- [x] 修复 sender discovery 端口解析与 receiver 广播端口来源。
- [x] 补充 sender / receiver 单元测试。
- [x] 更新相关文档并完成双仓验证。
