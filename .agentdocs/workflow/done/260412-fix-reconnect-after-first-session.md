# 修复首次断开后无法再次连接的问题

## 背景
- 实机表现为：第一次 sender -> receiver 连接正常，关闭后再次连接时 sender 侧 WebSocket 直接 `ECONNREFUSED`。
- 用户同时观察到 receiver 侧报错“远端 ICE 候选添加失败”。
- 当前现象说明 receiver 在第一次会话结束后的回收路径中被打入不可用状态，导致 signaling endpoint 未恢复监听。

## 分析方向
- 梳理 sender 关闭时会发送哪些 `leave / ice-candidate / close` 顺序消息。
- 梳理 receiver 在 `SignalingMessage.Leave` 与 `SignalingMessage.IceCandidate` 到达时的状态转移与资源释放逻辑。
- 重点确认“旧连接尾部的迟到 ICE”是否会把已结束会话误判为错误并关闭 signaling host / advertiser。

## TODO
- [x] 阅读 sender / receiver 重连相关实现，确认错误落点。
- [x] 修复重连路径并补充回归测试。
- [x] 更新相关文档并完成双仓验证。
