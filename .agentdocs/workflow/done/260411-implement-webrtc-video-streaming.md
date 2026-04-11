## 背景
- 当前仓库已经具备 Quest 3 发送端的多模块骨架、MediaProjection 前台服务与开始/停止会话状态机，但 `platform/webrtc` 之前仍是日志占位实现，尚未形成真实推流链路。
- 需求文档要求首阶段优先闭环 `MediaProjection -> H.264 -> WebRTC` 视频推流，并让推流模块与采集、信令解耦，为后续音频与多编码扩展保留接口。
- 本轮固定的产品决策是：只做视频推流；WebRTC 建链通过 `WebSocket + JSON Offer/Answer` 完成；发送控制台提供 WebSocket 地址与 Session ID 输入；本轮不做鉴权。

## 分阶段计划
1. 更新文档与索引，明确本轮推流协议、范围与验收要求。
2. 调整 `core/model` 与 `core/session` 契约，补齐信令消息、RTC 事件与错误模型。
3. 在 `platform/webrtc` 落地真实 `ScreenCapturerAndroid + PeerConnection + WebSocket` 推流链路。
4. 更新 `feature/stream-control` 与 `app`，接入新的配置入口、状态展示和前台服务透传。
5. 补齐单元测试、集成测试与 UI 测试，执行 Gradle 校验并记录结果。

## TODO
- [x] 记录本轮任务背景、阶段和关键决策。
- [x] 调整会话与配置模型，支持 WebSocket 信令和 RTC 异步事件。
- [x] 实现 WebRTC 视频推流与 JSON 信令客户端。
- [x] 更新发送控制台和前台服务配置入口。
- [x] 补充多模块测试覆盖。
- [x] 执行 `spotlessCheck`、`lintDebug`、`testDebugUnitTest`、`connectedDebugAndroidTest` 并记录结果。

## 关键约束
- MediaProjection 仍只能由 `app` 模块内的 `mediaProjection` 类型前台服务持有和启动。
- 本轮视频链路固定为 H.264，HEVC / VP8 / VP9 继续保留在能力模型中，但不对用户开放选择入口。
- WebSocket 信令协议由发送端先定义，当前仓库不实现真实信令服务与接收端。
- 音频开关继续保留在 UI，但本轮不接入真实 WebRTC 音轨。

## 实现结果
- `core/model` 新增 `sessionId`、WebSocket 地址校验、细化错误模型与 `statusDetail`，用于驱动 sender 侧建链状态展示。
- `core/session` 改为围绕 `ProjectionAccess + SignalingSession + RtcSessionEvent` 编排，会话在收到 RTC 断连或采集中断事件后会自动回收资源并写入错误状态。
- `platform/webrtc` 新增 sender 侧 JSON 信令协议、`WebSocketSignalingClient`、`ScreenCapturerAndroid + PeerConnection` 推流实现，以及 H.264 优先的 SDP 排序。
- `feature/stream-control` 新增 `signalingEndpoint` 与 `sessionId` 输入，编码固定展示为 H.264，音频开关保留但禁用；状态区会显示当前建链阶段。
- `app` 前台服务现在会透传 `sessionId`，通知文案优先展示实时建链状态，`AppContainer` 已切换到真实 WebRTC / WebSocket 实现。

## 验证记录
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk ./gradlew -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon testDebugUnitTest`：通过。
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk ./gradlew -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon spotlessCheck`：通过。
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk ./gradlew -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon lintDebug`：通过。
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk ./gradlew -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon connectedDebugAndroidTest`：执行到安装/连接阶段后失败，原因是当前环境没有已连接 Android 设备，报错 `No connected devices!`。
