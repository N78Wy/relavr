## 背景
- 当前 sender 已经接入 `AudioPlaybackCapture + WebRTC audio track + RECORD_AUDIO` 权限链路，但实测目标已调整为先只保留视频推流，不再处理声音。
- 音频能力并不是孤立模块，已经串进 `StreamConfig`、会话状态、前台服务参数、Compose UI、权限流程、DataStore 与测试，需要一次性收敛，避免留下半删状态。
- 本轮产品决策固定为：彻底收敛到视频-only，不保留音频开关、录音权限、音频状态或音频平台接缝。

## TODO
- [x] 建立任务文档并登记索引。
- [x] 删除 core/model、core/session、platform 层全部音频契约与实现。
- [x] 删除 app、feature 层音频配置、权限、UI 与相关测试。
- [x] 更新产品/架构文档中的长期记忆，明确当前版本仅支持视频推流。
- [x] 执行 `./gradlew spotlessCheck`、`./gradlew lintDebug`、`./gradlew testDebugUnitTest` 并记录结果。

## 关键删除边界
- `StreamConfig` 不再包含 `audioEnabled`；`StreamingSessionSnapshot` 不再包含音频状态字段；`CapabilitySnapshot` 不再暴露音频采集支持位。
- `StreamingSessionCoordinator`、`RtcPublishSession`、`WebRtcPublisherFactory` 收敛为纯视频推流，不再创建音频源、音轨或降级事件。
- `app` 删除录音权限请求、权限状态解析、前台服务音频 extra、权限偏好存储与 `RECORD_AUDIO` manifest 声明。
- `feature/stream-control` 删除音频开关、音频状态文案、设置入口与对应 UI/test tags。

## 验收标准
- sender 能正常请求 `MediaProjection` 授权并发起纯视频推流。
- 发送控制台不再出现音频配置或录音权限交互。
- 编译、单元测试、lint、spotless 均通过。

## 验证结果
- `./gradlew spotlessCheck`：通过。
- `./gradlew lintDebug`：通过。
- `./gradlew testDebugUnitTest`：通过。
