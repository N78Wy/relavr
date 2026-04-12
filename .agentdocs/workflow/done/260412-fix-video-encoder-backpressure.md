## 背景
- sender 当前视频链路直接复用 `ScreenCapturerAndroid + DefaultVideoEncoderFactory + PeerConnection`，但此前只有静态规格配置，没有设备级组合校验，也没有运行时编码过载背压控制。
- Quest 3 实机日志出现连续 `HardwareVideoEncoder: Dropped frame, encoder queue full`，会导致编码输入积压并最终触发内存溢出；同一批日志中的 `BAD_INDEX` 更接近 Codec2 能力查询噪音，不作为本轮根因处理。
- 本轮产品决策固定为：保留开播前规格选择自由，不在 UI 里直接隐藏高风险组合；若运行时检测到编码器持续过载，则在同一会话内自动降档，而不是要求用户重连。

## TODO
- [x] 为 sender 能力模型补齐 codec + 分辨率 + 帧率 + 码率的组合支持矩阵。
- [x] 在开播前基于组合矩阵做二次校验，拦截设备不支持的规格组合。
- [x] 在 WebRTC 推流会话里接入运行时视频编码过载检测、自动降档和最低档失败保护。
- [x] 同步更新会话状态、前台通知与控制台 UI，区分 requested profile 与 active profile。
- [x] 补充单元测试并执行根目录验证命令。

## 关键实现
- `CapabilitySnapshot` 新增 `supportedProfiles`，`StreamConfig.validationError(capabilities)` 新增组合级校验；`AndroidMediaCodecCapabilityRepository` 现基于 `MediaCodecInfo.VideoCapabilities` 为预设规格生成支持矩阵。
- `StreamingSessionSnapshot` 新增 `activeVideoProfile`；`RtcSessionEvent` 新增 `VideoProfileChanged` 与 `VideoEncoderOverloaded`，用于把运行时降档和最低档失败显式传回会话编排层。
- `WebRtcPublishSession` 在建链成功后按 1 秒周期拉取 video sender stats；连续命中 CPU 限制或编码 FPS 过低时，按 `60 -> 45 -> 30 -> 24 FPS` 再 `1080p -> 900p -> 720p` 的顺序自动降档，并把实际生效规格同步到 UI。
- 若最低档 `1280x720 / 24 FPS` 仍持续过载，则直接以 `VideoEncoderOverloaded` 结束会话，优先阻断编码积压导致的 OOM 路径。
- 发送控制台现把“推流规格”展示为 requested / active 双态，开始按钮也会在设备明确不支持当前规格组合时直接禁用并展示错误。

## 验证记录
- `./gradlew testDebugUnitTest`：通过。
- `./gradlew spotlessCheck`：通过。
- `./gradlew lintDebug`：通过。
