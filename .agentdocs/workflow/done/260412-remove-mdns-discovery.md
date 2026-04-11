# 移除 sender 侧 mDNS discovery

## 背景
- 当前 sender 侧 mDNS / NSD 发现长期不可用。
- 用户明确要求彻底移除 mDNS 相关功能，不保留 UI、代码或文档入口。

## 范围
- 删除 sender `:platform:discovery` 模块。
- 删除 sender `core:model` / `core:session` 中所有 discovery 模型、协议解析与协调代码。
- 删除发送控制台中的“局域网接收端”区块、刷新按钮、确认弹窗与相关 ViewModel 状态。
- 删除对应单元测试与 Compose UI 测试。
- 更新 `.agentdocs` 架构记忆与索引，清理已失效的 mDNS 说明。

## TODO
- [x] 建立任务文档并确认删除范围
- [x] 删除 sender 侧 discovery 模块、依赖与接线
- [x] 删除 sender 控制台 discovery UI 与测试
- [x] 更新 sender 文档与索引并归档
