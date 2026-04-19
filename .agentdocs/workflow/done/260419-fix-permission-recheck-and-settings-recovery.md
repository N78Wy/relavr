# 260419 Fix Permission Recheck And Settings Recovery

## 背景
- sender 当前录音权限已有“永久拒绝 -> 打开系统设置”恢复入口，但扫码相机权限仍只有普通拒绝提示；用户一旦在系统弹窗里选择“拒绝且不再询问”，首页扫码入口不会告诉用户如何恢复。
- sender 当前对权限恢复后的重检不够严格。用户在 app 内拒绝录音或相机权限后，如果跳到系统设置重新开启权限，再回到 app，现有逻辑仍可能沿用旧快照，导致扫码预览或带声音推流无法恢复。
- 本轮目标是把“每次点击需要权限的按钮都重新检查权限”落实到扫码和录音两条链路，并补齐扫码相机权限的系统设置恢复入口。

## 目标
- 扫码链路识别相机权限永久拒绝，并在扫码弹层内提供“打开系统设置”入口。
- 录音和相机权限在每次触发相关操作前都重新同步系统状态，不依赖旧的内存快照。
- 用户从系统设置返回 app 后，可以重新走扫码预览；录音权限恢复后，再次点击“带声音”或开始推流时可以重新启用带声音推流。

## 实施阶段
- [x] 阶段 1：补齐任务文档与索引，明确权限状态机和验收点。
- [x] 阶段 2：实现 app 层相机权限桥接、扫码设置入口与点击前重检。
- [x] 阶段 3：实现录音权限网关的实时重检，并调整开始推流前的权限同步顺序。
- [x] 阶段 4：补充测试并执行 `./gradlew spotlessCheck lintDebug testDebugUnitTest`。

## 决策
- 录音权限延续现有产品语义：用户拒绝授权后，`audioEnabled` 仍会回退并持久化为 `false`；用户从系统设置恢复授权后，需要再次手动点“带声音”才能恢复音频意图。
- 相机权限的系统设置入口放在扫码弹层内，不额外在首页增加独立设置按钮。
- `feature` 继续只消费抽象状态，不直接调用 Android 权限 API；平台细节仍留在 `app`。

## 验收点
- 相机权限永久拒绝时，扫码弹层能显示恢复说明与“打开系统设置”按钮。
- 从系统设置返回 app 后，再次点击扫码能重新检查并恢复相机预览。
- 从系统设置返回 app 后，再次点击“带声音”或开始推流时会重新检查录音权限；若用户已经重新授权，则能恢复带声音推流。

## 实施结果
- `app` 新增了 `AndroidHeadsetCameraPermissionGateway`，为 Quest 头显相机权限补齐 `Granted / Requestable / PermanentlyDenied` 三态、请求历史持久化、系统设置入口与恢复后的自愈逻辑。
- `MainActivity` 现在会在每次点击扫码、开启系统音频和开始推流前重新同步权限状态；扫码入口在普通拒绝时仍关闭弹层，在永久拒绝时保留弹层并展示系统设置入口。
- `SenderQrScannerOverlay` 已改成权限感知 UI：已授权时显示相机预览，申请中显示等待态，永久拒绝时显示恢复说明和“打开系统设置”按钮；从系统设置返回后如果权限已恢复，弹层会直接回到预览。
- `AndroidRecordAudioPermissionGateway` 现在会在 `requestPermissionIfNeeded()` 前先校验系统真实授权，避免从系统设置恢复后仍沿用陈旧的 `PermanentlyDenied` 快照。
- 已补充 app 层权限网关单测、扫码弹层 instrumentation 测试和 ViewModel 回归测试，覆盖权限恢复后的关键交互。

## 验证记录
- 2026-04-19：`./gradlew spotlessCheck lintDebug testDebugUnitTest` 通过。
- 2026-04-19：`./gradlew :app:compileDebugAndroidTestKotlin` 通过。
- 2026-04-19：`./gradlew :app:connectedDebugAndroidTest` 已完成编译与打包，但当前环境失败于 `No connected devices!`，未能执行真机/模拟器 instrumentation。
