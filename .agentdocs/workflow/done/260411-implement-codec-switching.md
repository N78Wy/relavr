## 背景
- 当前 sender 已具备 `StreamConfig.codecPreference`、MediaCodec 能力探测、前台服务透传与 codec fallback 骨架，但发送控制台仍把编码写死为 H.264，WebRTC SDP 侧也只对 H.264 做优先协商。
- 需求文档要求 sender 支持 `H.264 / HEVC / VP8 / VP9` 多种主流编码格式，并预留编码格式切换能力；结合当前产品决策，本轮实现限定为“开播前切换，推流中锁定”。
- 浏览器预览 demo 继续以 H.264 稳定联调为验收基线；其余编码本轮只要求 sender 侧选择、能力门控和 WebRTC Offer 优先级生效。

## 分阶段计划
1. 补齐文档与索引，替换“视频编码固定为 H.264”的旧约束。
2. 实现编码能力交集，统一 Android MediaCodec 与 libwebrtc 实际可发出的 codec 集合。
3. 更新发送控制台 UI / ViewModel，支持开播前切换编码偏好并展示请求/实际编码。
4. 补充单元测试、集成测试与 Compose UI 测试。
5. 执行根目录校验命令并记录结果。

## TODO
- [x] 创建当前任务文档并更新索引。
- [x] 实现编码能力交集与默认回退策略。
- [x] 改造 WebRTC codec 优先协商逻辑。
- [x] 更新发送控制台编码选择交互与状态展示。
- [x] 补齐测试覆盖。
- [x] 执行 `spotlessCheck`、`lintDebug`、`testDebugUnitTest`、`connectedDebugAndroidTest` 并回写结果。

## 关键决策
- 切换时机固定为开播前；推流中不做 codec renegotiation，也不做自动重启。
- 发送控制台固定展示四种主流编码格式，实际是否可选由能力探测决定，不支持项直接禁用并保留原因提示。
- sender 对外暴露的“支持编码”以 Android `MediaCodec` 编码能力与 libwebrtc `DefaultVideoEncoderFactory` 支持集合的交集为准，避免 UI 暴露不可真正协商的编码。

## 实现结果
- `core/model` 为 `CodecPreference` 增加 WebRTC codec 名称映射，`CapabilitySnapshot` 增加默认 codec 解析逻辑，统一 H.264 优先、HEVC/VP8/VP9 回退顺序。
- `app` 新增 `CombinedCodecCapabilityRepository`，把 Android MediaCodec 编码能力与 libwebrtc `DefaultVideoEncoderFactory` 支持集合取交集，再作为 sender 对外展示与选择的唯一 codec 能力来源。
- `platform/webrtc` 新增 WebRTC codec 能力探测实现，并把 Offer 的 SDP payload 排序从仅 H.264 扩展到 H.265、VP8、VP9，按用户选择的编码偏好统一协商。
- `feature/stream-control` 新增编码卡片组选项、能力门控、请求/实际编码展示与自动归一逻辑；编码选择只允许在空闲态修改，推流中保持锁定。
- `app`、单元测试、集成测试与 Compose UI 测试已同步覆盖非默认 codec 透传、多 codec UI 交互、fallback 展示与 SDP 排序。

## 验证记录
- 2026-04-11：`./gradlew spotlessCheck` 通过。
- 2026-04-11：`./gradlew lintDebug` 通过。
- 2026-04-11：`./gradlew testDebugUnitTest` 通过。
- 2026-04-11：`./gradlew connectedDebugAndroidTest` 未通过，当前环境没有已连接 Android 设备，Gradle 报错为 `No connected devices!`。
