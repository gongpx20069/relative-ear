# 验证与测试

本文件区分自动验证与真机验收。性能和精度的产品目标见 [DESIGN](DESIGN.md)，构建环境见 [DEVELOPMENT](DEVELOPMENT.md)，发布门禁见 [RELEASE](RELEASE.md)。

## 自动验证

Windows PowerShell：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
python -m unittest discover -s scripts -p "test_*.py"
python scripts\github_api.py validate-version --tag v0.0.4
```

最后一条命令发布下一版本时应使用对应标签。

领域测试覆盖：

- 音名、调音基准、音程方向、级数和题目播放一致性。
- 固定 C4 默认三音、自选集合出题约束、唱名/音名切换不改变答案身份、C4/C5 两个 Do 区分、非法配置拒绝。
- 八分音符各速度的实际 PCM 槽长、静音断奏段、合成 C4–C5 音频检测结果；旧首调/音程测试保留。
- App JVM 测试验证默认三音、严格八度默认值与显式开启、配置锁定和答后复听门禁。
- 合成正弦及带泛音单音：65–1000 Hz 范围，含边界，要求无遗漏且误差 ≤10 cents。
- 静音、DC 与固定随机种子的白噪声拒绝。
- 400 ms 实际时间、稀疏帧拒绝、不稳定双峰分布拒绝、容差边界和严格/忽略八度评分。
- 首个稳定答案不被后来更接近目标的声音覆盖。
- 单音换音、静音后重复同音、短音拒绝。

Python 测试使用模拟 REST 响应：版本规则、创建公开仓库、五个架构和校验文件完整上传后发布、上传失败保持 draft、禁止覆盖公开版本、缺失 APK 和 API 主机限制。另验证 AGP 元数据完整性、版本一致、拒绝 debug/重复/残留/目录外文件、原生库 ABI 与签名证书一致。不需要真实 token，也不会创建远端资源。

`HistoryStoreTest` 是 Android 仪器测试，验证 SQLite 记录、nullable cents、配置快照/手动设置保持，以及版本 1→2 的 additive migration 保留旧行。`TrainingUiTest` 使用真实 MainActivity，检查初学者入口可见、答前无真实谱位、八音/音名配置、答题保存与听答案入口，并导出初始页和八音答题页真实截图。编译测试 APK **不等于执行测试**：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

此测试会清空测试 App 的记录，只能在测试设备/模拟器运行，不要在有需要保留数据的日常安装上运行。

CI 的独立 `ui` 任务在 API 29 模拟器执行这些测试，`ui-snapshots` artifact 包含截图与仪器测试报告。模拟器不验证真实手机麦克风或扬声器音准。

## 真机检查清单

至少三款不同厂商 Android 设备，含 API 26 或接近最低版本的设备，以及较新系统：

1. 安装、冷启动、字体放大、小屏和手势导航。
2. 拒绝/永久拒绝权限后听辨正常；授权后回唱与识音正常。
3. 三/五/八音及不连续自选范围完整一轮；固定 C4 不换调、两种答案格式、C4/C5 不合并，重播与听答案不重复计分。
4. 回唱正确、唱错、无声音、颤音、音域外、忽略八度及设置变更。
5. 持续唱 `C4 → E4 → G4`，停止后最后一个音出现；有静音的重复同音分成两个事件。
6. 扬声器播放结束再开启回唱；有线耳机、蓝牙、拔耳机、来电和其他录音 App。
7. 录音中锁屏/切后台/切页面，系统麦克风指示关闭；返回后不自动监听。
8. 飞行模式训练、保存、重启后查看；确认删除记录后无残留成绩。
9. 连续监听 30 分钟，观察温升、耗时、曲线更新、内存和麦克风释放。
10. 固定签名 APK 覆盖升级，设置与历史不丢失。
11. 查看圆形/方形及 Android 13+ 主题图标，耳朵波纹和三点不被遮罩裁切。
12. 中断答后示范仍回到反馈；试听和答题使用同一固定音高，改变播放速度不限制答题时长。
13. 八分音符 60/80/100/120 BPM 的实际输出、谱位随试听同步；答前的装饰图标不泄露音高。
14. 从旧版覆盖升级，旧首调/音程行保持并标注；范围/显示/速度在重启后保留，新记录含配置快照。

复杂音乐可能被单音算法误判，不能通过只演示清晰哼唱宣称歌曲主旋律识别已完成。

## 当前验证边界

首次开发已通过领域测试、REST 模拟测试、Android debug/release APK 构建、Android lint，以及仪器测试 APK 编译。ABI 发布改动已本地构建五个 debug 和五个 release APK，并验证 release 包的架构、版本和共享签名证书。release 构建使用一次性本地验证密钥，该密钥和对应测试签名 APK 随后删除；它们不是正式发布材料，不用于分发或升级。

本机没有连接的物理 Android 设备，真实人声录音准确率、端到端延迟及各机型效果尚未测量。CI 的模拟器仪器测试不替代真机音频验收。

`0.0.4` 固定 C4 与新版 UI 已通过 [Android CI](https://github.com/gongpx20069/relative-ear/actions/runs/37608766607) 的本地逻辑/build 门禁及 API 29 模拟器 5 项仪器测试，包括数据库迁移和真实 MainActivity 操作；单独截图会话再执行两项 UI 用例。初始练习页与八音答题页真实截图已下载检查，不是设计稿合成图。CI 使用 adb-owned 截图会话收集文件，避免 Gradle 自动卸载应用时删除 App 外部目录中的图片。

`v0.0.3` 已通过真实远端 [Android CI](https://github.com/gongpx20069/relative-ear/actions/runs/37604774774) 和 [发布工作流](https://github.com/gongpx20069/relative-ear/actions/runs/37604779847)，包含新增唱名领域测试和 App JVM 默认状态测试。已从公开 [Release](https://github.com/gongpx20069/relative-ear/releases/tag/v0.0.3) 下载五个 APK，验证完整集合、SHA-256、实际 ABI、版本及签名证书与本机固定发布密钥一致。耳朵图标前景已进行几何渲染检查；系统启动器遮罩仍需真机检查。Release 是公开的早期 prerelease，不是 draft；这些验证不替代真实麦克风体验验收。

历史版本 `v0.0.2` 的 [Android CI](https://github.com/gongpx20069/relative-ear/actions/runs/37595040784)、[发布工作流](https://github.com/gongpx20069/relative-ear/actions/runs/37595045600) 及公开 APK 也已完成上述发布校验，不覆盖旧标签或资产。

合成测试不替代真实录音测试集。后续应使用授权音频，保留真值、片段边界和评估脚本，分别统计有效覆盖、音分误差、八度误差、音符遗漏/插入和起止时间偏差。测试素材不含私人录音，也不未经许可上传商业歌曲。
