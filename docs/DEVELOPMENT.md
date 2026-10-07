# 开发指南

本文件描述当前工程和本地开发，不替代 [产品设计](DESIGN.md) 或 [用户使用说明](../README.md)。发布签名见 [RELEASE.md](RELEASE.md)，验证范围见 [TESTING.md](TESTING.md)。

## 当前实现

应用版本 `0.0.1`，Kotlin + Jetpack Compose，minSdk 26、compileSdk/targetSdk 35。

- 唱准：基准音 + 音程提示，上/下行，8 秒采集窗口，首个稳定片段评分，八度模式、容差、A4 设置。
- 练耳：0–12 半音的音程辨认、大调级数辨认，随机基准，10 题一轮，重播、首次回答计分。
- 识音：YIN 单音检测，音名/Hz/cents、音高曲线、音符分段、最近 30 音列表。
- 本地记录：SQLite 成绩及配置快照，SharedPreferences 设置，最近 100 条报告、确认删除。
- 权限与生命周期：练耳不请求录音权限；离开页面、后台和播放焦点丢失时中断训练。
- GitHub Actions：测试、lint、构建；标签触发固定签名 APK、REST Release 上传。

尚未实现：弱项推荐、考试模式、自定义音程集合/音域、完整旋律模唱评分、调内级数识音显示、主动噪声校准、带伴奏主旋律模型和歌曲转谱。

## 环境

- JDK 21。
- Android SDK platform 35、build-tools 35.0.0、platform-tools。
- Python 3.10+，用于 REST 发布及其测试，仅用标准库。
- Gradle Wrapper 8.11.1（固定分发 SHA-256），无需全局安装 Gradle。

首次构建需要联网下载依赖；运行 App 不需要联网。

Windows PowerShell 示例：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
```

也可以用 Android Studio 打开仓库根目录，设置 Gradle JDK 为 21，安装缺失的 SDK 包。

Debug APK：`app\build\outputs\apk\debug\app-debug.apk`。

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

没有连接手机时可以构建，但无法验证真实麦克风与设备音频路由。

## 实际模块

| 位置 | 职责 |
|---|---|
| `core` / Music、Questions | 音高转换、音名、音程/级数题目与播放音符 |
| `core` / PitchDetector | 可复用缓冲区的纯 Kotlin YIN，静音门限和置信过滤 |
| `core` / SingingScorer | 时间窗口、有效覆盖、稳定性、目标偏差和首个有效答案 |
| `core` / NoteSegmenter | 换音滞回、最短片段、静音分段及结束刷新 |
| `app` / AudioEngine | 16 kHz 单声道 PCM 采集/合成播放、播放焦点与资源释放 |
| `app` / EarViewModel | 训练状态、协程任务、权限失败、持久化、UI 状态 |
| `app` / HistoryStore | 参数化 SQLite 写入、统计和设置 |
| `app` / MainActivity | Compose 页面、导航、权限请求、后台中断 |
| `scripts` | GitHub REST 仓库/Release、APK 验证及标准库测试 |

设计的多层 `:core:*` 模块暂合并为一个 JVM `:core`，Android 集成集中在 `:app`。初版使用系统 SQLite 和 SharedPreferences，而非 Room/DataStore，避免在基础功能验证前引入生成器；后续更换必须迁移既有数据，不删除数据库。

## 音频实现参数

- 采样率 16 kHz，分析窗 2048 样本（128 ms），步长通常 512 样本（32 ms）。
- 初始 RMS 门限 0.008，YIN 阈值 0.15，有效置信度至少 0.85。
- 支持约 65–1000 Hz，边界留 10 cents 的数值检测余量，不强制吸附音符。
- 回唱：至少 400 ms，时间覆盖至少 80%；MAD ≤20 cents，且至少 80% 的有效样本离中位音高不超过 20 cents。
- 音符分段：约 96 ms 的确认时间与 120 ms 静音间隔。没有起音检测器，同音连奏可能合并，用户可用短暂停顿分开。
- 播放排队样本全部输出后再进入间隔和录音等待，不在播放阶段评分。

这些参数是初始实现，不代表真机验收达标。与设计中 48 kHz/4096 的初始方案不同，16 kHz 降低纯 Kotlin YIN 的计算量；音域、精度和延迟需按 [TESTING.md](TESTING.md) 复核。

录音源使用 `VOICE_RECOGNITION`，设备仍可能存在信号处理或路由差异。首版没有后台录音服务，也不尝试跨设备统一蓝牙体验。

## 维护约定

- 新功能的领域计算先加入 `core` 测试，再接 UI。
- 错误必须有 UI 提示；保存失败不能说成绩已成功保存。
- 不将低置信结果变成零频率，不依赖目标答案“修正”检测结果。
- 中文文案集中在 `strings.xml`，音程名称使用资源数组。
- 不提交 SDK 路径、构建输出、签名材料、token 或个人录音。
- 应用版本只在 `version.properties` 修改，发布要求见 [RELEASE.md](RELEASE.md)。
