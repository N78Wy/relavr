## 背景
- Quest 3 真机在 2026-04-11 触发 `SecurityException`：`Media projections require a foreground service of type ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`。
- 当前实现直接在应用侧会话链路中调用 `MediaProjectionManager.getMediaProjection()`，但 `app` 清单未声明 `mediaProjection` 类型前台服务，也没有通知与服务生命周期托管。

## 分阶段计划
1. 补齐任务文档、索引与 Android 架构约束，明确前台服务职责与授权约束。
2. 在 `app` 模块落地 MediaProjection 前台服务、服务命令分发与会话控制改造。
3. 修正授权网关行为，避免跨会话复用 MediaProjection 授权结果。
4. 补充单元测试与集成测试，并执行必要的 Gradle 校验。

## TODO
- [x] 记录任务背景、阶段与约束。
- [x] 新增 `mediaProjection` 前台服务与通知通道。
- [x] 将 `StreamingSessionController` 改为服务命令驱动，同时保持状态观察与能力刷新接口稳定。
- [x] 移除跨会话缓存授权结果的实现。
- [x] 补充 app 模块单元测试与集成测试。
- [x] 执行 `spotlessCheck`、`lintDebug`、`testDebugUnitTest`，并记录 `connectedDebugAndroidTest` 结果。

## 关键约束
- `StreamingSessionCoordinator` 继续作为会话引擎，但开始/停止必须由 `app` 层前台服务触发。
- `feature/stream-control` 与现有 UI 交互接口保持不变，只能通过 `StreamingSessionController` 发命令与读状态。
- MediaProjection 授权结果只在当前开始流程内使用，不允许缓存用于下一次推流。

## 实现结果
- `app` 模块新增 `MediaProjectionForegroundService`，在进入 `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` 前台状态后才启动会话，并用同一条前台通知刷新状态。
- `AppContainer` 现在同时持有底层 `StreamingSessionCoordinator` 会话引擎和服务驱动型 `StreamingSessionController`；UI 继续使用原接口，但开始/停止已经改为服务命令分发。
- `AndroidProjectionPermissionGateway` 不再缓存上一次授权结果，避免跨会话复用 MediaProjection token。
- 新增 app 模块单元测试与集成测试，覆盖服务命令分发、状态镜像和与真实会话引擎的联动。

## 验证记录
- `ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon spotlessCheck`：通过。
- `ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon lintDebug`：通过。
- `ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon testDebugUnitTest`：通过。
- `ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew -Dorg.gradle.jvmargs=-Xmx2g -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon connectedDebugAndroidTest`：执行到安装/连接阶段后失败，原因是当前环境没有已连接 Android 设备，报错 `No connected devices!`。
