# 双仓代码英文化与界面双语化

## 背景
- 发送端 `relavr` 与接收端 `relavr-view` 代码、注释、测试、运行时错误与界面文案中仍存在大量中文。
- 本任务要求实现层统一改为英文，同时所有用户可见内容支持 English / 简体中文双语。
- `.agentdocs` 保持简体中文，不参与本轮英文化。

## 实施阶段
1. 建立 Android 应用内语言切换基础设施与任务跟踪。
2. sender 仓库：清理实现层中文，改造 UI / 错误 / 状态文案为可本地化结构。
3. receiver 仓库：清理实现层中文，改造 Android UI 与 browser preview 为双语。
4. 补测试并完成双仓验收。

## TODO
- [x] 在 sender / receiver 两仓登记任务文档并补全索引。
- [x] sender Android 接入应用内语言切换能力与手动入口。
- [x] receiver Android 接入应用内语言切换能力与手动入口。
- [x] sender 清理实现层中文并迁移用户文案到双语资源。
- [x] receiver Android 清理实现层中文并迁移用户文案到双语资源。
- [x] browser preview 接入双语词典与语言切换。
- [x] 更新/补充双语相关测试。
- [x] 执行双仓 lint / test / format 验证。
- [x] 回顾并更新长期记忆与任务文档归档。

## 验证
- `cd /home/ubuntu/pj/relavr && ./gradlew spotlessCheck lintDebug testDebugUnitTest`
- `cd /home/ubuntu/pj/relavr-view && ./gradlew spotlessCheck lintDebug testDebugUnitTest`
- `cd /home/ubuntu/pj/relavr-view/browser-preview && npm run format:check`
- `cd /home/ubuntu/pj/relavr-view/browser-preview && npm run lint`
- `cd /home/ubuntu/pj/relavr-view/browser-preview && npm run test`

## 收尾说明
- 两仓实现层、注释、测试名、断言与日志中的中文已清理为英文。
- 用户可见文案统一支持 `English` 与 `简体中文`，剩余中文扫描结果仅存在于 `values-zh-rCN`、浏览器页 `zh-CN` 词典，以及语言选择器中的 `简体中文` 标签，属于预期本地化数据。
