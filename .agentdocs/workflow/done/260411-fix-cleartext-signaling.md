## 背景
- Quest 3 真机在 2026-04-11 连接 `ws://192.168.123.182` 时触发 `CLEARTEXT communication not permitted by network security policy`，导致 WebSocket 信令会话启动失败。
- 当前 `app/src/main/res/xml/network_security_config.xml` 试图通过 `10.0.0.0`、`192.168.0.0` 这类条目放开私网明文流量，但 Android 的 `domain-config` 不会按网段解释这些值，因此配置实际无效。
- 现有产品与联调约束明确允许首阶段使用 `WebSocket + JSON Offer/Answer`，仓库内置联调工具也默认使用明文 `ws://`，因此需要把应用侧网络安全策略与当前联调方式对齐。

## 分阶段计划
1. 记录任务背景、实现边界与验收要求。
2. 修正应用网络安全配置，并收敛默认信令地址与 UI 提示策略。
3. 补充 `core:model`、`feature/stream-control` 与 `app` 的回归测试。
4. 执行 Gradle 校验并回填文档、索引与长期记忆。

## TODO
- [x] 记录任务背景、阶段与关键约束。
- [x] 放开应用级明文 WebSocket，并移除无效的私网域名白名单配置。
- [x] 将默认信令地址调整为留空，并在 UI 中补充 Quest 真机填写局域网地址的提示。
- [x] 补充模型、UI 与网络安全配置回归测试。
- [x] 执行 `spotlessCheck`、`lintDebug`、`testDebugUnitTest`、`connectedDebugAndroidTest` 并记录结果。

## 关键约束
- 本轮决策固定为所有构建均允许明文 `ws://`，不区分 `debug` / `release`。
- Quest 真机默认不预填宿主机地址，避免继续使用只适用于模拟器或本机的错误默认值。
- 不修改现有信令协议、消息类型或 WebRTC 会话编排，只修复连通性策略与默认输入体验。

## 实现结果
- `app` 模块将 `network_security_config.xml` 收敛为应用级 `base-config cleartextTrafficPermitted="true"`，移除了此前无效的 `10.0.0.0`、`192.168.0.0` 伪域名白名单。
- `core:model` 将默认 `signalingEndpoint` 改为空字符串，并新增“WebSocket 地址不能为空”的校验分支，避免继续透出只适用于模拟器的假默认值。
- `feature/stream-control` 的 WebSocket 输入框现在默认留空，新增局域网地址示例与 Quest 真机提示；未填写合法地址前，开始按钮保持禁用，并在状态区与操作区展示明确原因。
- 新增 `StreamConfigTest` 与 `NetworkSecurityConfigTest`，并调整会话编排、ViewModel 与 Compose UI 测试，使所有启动路径都显式提供合法 `ws://` 地址。
- 更新 Android 架构文档、索引记忆与 `tools/signaling/README.md`，明确当前阶段所有构建允许明文 `ws://`，且 Quest 真机必须填写宿主机局域网地址而非 `localhost`。

## 验证记录
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk ./gradlew -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon spotlessCheck`：通过。
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk ./gradlew -Dorg.gradle.jvmargs='-Xmx2g -Dfile.encoding=UTF-8' -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon lintDebug`：通过。首次未限制堆大小时 Gradle daemon 在 lint 分析阶段意外退出，重试后通过。
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk ./gradlew -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon testDebugUnitTest`：通过。
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk ./gradlew -Dorg.gradle.jvmargs='-Xmx2g -Dfile.encoding=UTF-8' -g /home/ubuntu/pj/relavr/.gradle-home --no-daemon connectedDebugAndroidTest`：执行失败，原因是当前环境没有已连接 Android 设备，报错 `No connected devices!`。
