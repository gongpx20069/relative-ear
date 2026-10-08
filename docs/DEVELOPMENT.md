# 开发指南

本文件描述当前工程和本地开发，不替代 [产品设计](DESIGN.md) 或 [用户使用说明](../README.md)。发布签名见 [RELEASE.md](RELEASE.md)，验证范围见 [TESTING.md](TESTING.md)。

## 当前实现

应用版本 `0.0.10`，Kotlin + Jetpack Compose，minSdk 26、compileSdk/targetSdk 35。默认用户指南为英文 [README](../README.md)，中文指南独立为 [README.zh-CN](../README.zh-CN.md)；应用支持英文/简体中文，默认跟随系统。

- 练耳：默认入口，固定 C4=Do；三音、五音、八音与自选 C4–C5 自然音范围，唱名/音名选项，10 题一轮及首次回答计分。
- 快速记忆：独立钢琴页提供横向 C4–C5 八白键（窄屏滑动，最小 48 dp 键宽）和快速切换单音试听；展开为沉浸式横屏，退出按钮/系统返回恢复原方向设置。其他页不嵌入钢琴。训练保留实时谱位示范和答后听答案；八分音符 60/80/100/120 BPM 播放，不做节奏评分。
- 唱唱名：按指定音符回唱，先播放 C4，8 秒采集窗口，首个稳定片段评分；未保存八度设置时默认区分八度，已有手动设置保留。
- UI：奶白/松绿主题、圆角分区、练习主卡、真实底栏图标、进度与答题网格、音准指示条、配置快照历史；两种 Compose 预览支持初始与八音答题布局。
- 图标：耳朵、听觉波纹与三点，adaptive icon 及 Android 13+ 单色主题图标，不再使用播放器式音符图标。
- 识音：YIN 单音检测，音名/Hz/cents、最近 60 秒白板、音符分段、最近 30 音文本列表；停止后按实际音高/时长/停顿合成回放，AudioTrack 播放头驱动红线，不再显示钢琴。
- 本地记录：SQLite 成绩及配置快照，SharedPreferences 设置；按整轮显示最近 100 次练习，点进详情才加载该练习的全部音符，支持部分练习、页面/系统返回与确认删除。
- 权限与生命周期：练耳不请求录音权限；离开页面、后台和播放焦点丢失时中断训练。
- GitHub Actions：测试、lint、构建；完整仪器测试只运行一次，同次收集截图、JUnit XML 和日志并严格核对结果；标签触发固定签名的四种 ABI APK 与通用 APK、完整校验和 REST Release 上传。
- 设置更新：手动查询公开 Releases（包含 prerelease），数值比较 0.0.x 版本、ABI 选包、下载提示与发布说明；不后台检查或自动安装，网络与资产异常有显式提示。

尚未实现：弱项推荐、独立考试模式、C4–C5 自然音以外的训练音库、完整旋律模唱与节奏评分、调内级数识音显示、主动噪声校准、带伴奏主旋律模型和歌曲转谱。

## 界面语言

语言实现使用 AppCompatActivity 与 AppCompat 1.7.0 的 setApplicationLocales；空 LocaleList 表示跟随系统，手动支持 en / zh-Hans。Android 13+ 接入平台应用语言，较旧版本使用 autoStoreLocales 的本机持久化；manifest 声明 locales_config，默认 values 是完整英文，values-zh 为完整简体中文。不自行改全局 Locale 或覆盖系统资源。Compose 文案及无障碍描述从资源读取；ViewModel 的提示/更新错误保存 UiMessage 资源 ID 和参数，在 UI 按当前语言解析，嵌套音频设备错误也本地化，避免重建后残留旧语言。切换引起 Activity 配置更新，仍遵守 onStop 中断音频，不清空历史或设置。

## 环境

- JDK 21。
- Android SDK platform 35、build-tools 35.0.0、platform-tools。
- Python 3.10+，用于 REST 发布及其测试，仅用标准库。
- Gradle Wrapper 8.11.1（固定分发 SHA-256），无需全局安装 Gradle。

首次构建需要联网下载依赖；训练与识音不需要联网，手动检查更新需要联网。

Windows PowerShell 示例：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

也可以用 Android Studio 打开仓库根目录，设置 Gradle JDK 为 21，安装缺失的 SDK 包。

Debug APK：`app\build\outputs\apk\debug\app-universal-debug.apk`，同目录包含 `arm64-v8a`、`armeabi-v7a`、`x86_64` 和 `x86` 独立 APK。Debug 和 release 都启用 ABI splits；每个包可独立安装，所有架构共享相同 versionCode。

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-universal-debug.apk
```

没有连接手机时可以构建，但无法验证真实麦克风与设备音频路由。

## 实际模块

| 位置 | 职责 |
|---|---|
| `core` / FixedTraining、ToneSynthesis | 固定音符、范围/标签配置、八分音符时值、问题身份、带静音间隔的 PCM 合成 |
| `core` / Music、Questions、Solfege | 音高转换；保留旧首调与音程生成器的兼容测试 |
| `core` / PitchDetector | 可复用缓冲区的纯 Kotlin YIN，静音门限和置信过滤 |
| `core` / SingingScorer | 时间窗口、有效覆盖、稳定性、目标偏差和首个有效答案 |
| `core` / NoteSegmenter | 换音滞回、最短片段、静音分段及结束刷新 |
| `core` / MelodyClip | 最近 60 秒的音符裁剪、合法时间线、停顿与当前音符定位 |
| `app` / AudioEngine | 16 kHz 单声道 PCM 采集/合成播放、播放焦点与资源释放 |
| `app` / EarViewModel | 训练状态、协程任务、权限失败、持久化、UI 状态 |
| `app` / HistoryStore | 参数化 SQLite 写入、统计和设置 |
| `app` / PracticeHistory | 按练习汇总的卡片、完整逐音详情、部分练习与返回导航 |
| `app` / PianoKeyboard、ListeningStudio | 独立/全屏横向八键试听、识音白板、红线进度和互斥回放控件 |
| `core` / AppUpdates | 数值版本比较、已发布候选、完整资产/官方地址校验与系统 ABI 优先选包 |
| `app` / ReleaseUpdateClient、UpdateSettings | 无凭据 HTTPS 查询、分页与错误处理、设置卡片与下载提示 |
| `app` / MainActivity、EarTheme、MusicArtwork | Compose 页面、统一主题、音符谱位与导航图标、预览、权限请求、后台中断 |
| `scripts` | GitHub REST 仓库/Release、AGP 输出元数据读取、ABI/签名验证及标准库测试 |

设计的多层 `:core:*` 模块暂合并为一个 JVM `:core`，Android 集成集中在 `:app`。初版使用系统 SQLite 和 SharedPreferences，而非 Room/DataStore，避免在基础功能验证前引入生成器；后续更换必须迁移既有数据，不删除数据库。

## 音频实现参数

当前 UI 使用 `NoteQuestion`，答案身份是 MIDI 音高，C4=60、C5=72，不因唱名相同而合并。`TrainingSetup` 校验范围有序、非空、无重复且只包含 C4–C5 自然音，速度属于四档。范围/显示/速度保存到 SharedPreferences，配置只在开练前或轮次完成后修改。

新模式写为 `fixed_note`、`sing_fixed`。SQLite 版本 1→2 仅添加 nullable `training_notes`、`notation`、`bpm` 三列，保留旧行与 schema；新行保存配置快照，旧行不猜测补齐。示范不请求麦克风、不计分；中断答后示范返回原反馈状态。`demoNote` 由 AudioTrack 播放头位置更新，而非提前按写入队列标记。

记录按已有 `session` 与 `mode` 分组，不改数据库版本、不重写旧成绩；mode 防止旧数据中同名会话的不同题型混成一条。汇总先按全量会话聚合，再按最新写入 ID 选最近 100 次练习，不能先截取 100 个音符。累计次数与正确率包含全部保存数据；详情使用参数化会话/模式查询、按 ID 顺序加载且不限制为 100 音。每题仍立即持久化，以保留未做完练习；新轮次使用新会话 ID，无答案的轮次不生成历史卡片。固定唱名已保存 10 题标为完成，少于 10 题标为部分练习；旧题型不推测完成状态。选择详情后离开记录页或删除全部成绩会清除详情状态，异步加载结果不会重新打开已关闭的详情。

`ToneSynthesis` 在 16 kHz PCM 中为每音保留完整半拍槽，包括 85% 发声与尾部静音；播放一次连续写入并在末尾等待输出，不累积逐音协程定时误差。音频观察任务在释放 AudioTrack 前完成取消，避免访问已释放设备。

音符回放以 AudioRecord 的累计样本时间为基准，裁剪到最近 60 秒；跨窗口的长音截取交集，首尾空白与音间停顿保持静音，不按题目 BPM 重新排列音符。`ToneSynthesis.render(MelodyClip)` 复用合成包络，最长分配 960,000 个 short（约 1.92 MB）；监听帧窗口最多 2,000 帧，已结束音符只保留与窗口相交的事件，文本只显示最后 30 项。持续监听不自动结束。回唱的原 10 秒曲线不变。

所有播放共用 AudioEngine 的焦点、路由、排队、实际播放头观察与取消清理。REPLAYING 与 LISTENING 互斥；播放器位置是已输出样本数，不用独立动画计时器猜进度。停止/后台/切页先 cancelAndJoin，观察器取消完成后才释放 AudioTrack。钢琴试听可替换上一枚试听，但不能中断题目、回唱或示范；不写入历史。`AudioSession` 接口允许测试注入合成 PitchFrame，生产仍使用真实 AudioRecord/AudioTrack。

钢琴试听仅允许在 PIANO 页面。`pianoExpanded` 保存在 ViewModel，MainActivity 在 STARTED 生命周期同步系统栏与 SENSOR_LANDSCAPE；原 requestedOrientation 随实例状态保留，退出时恢复。全屏不渲染 AppShell，支持退出按钮和系统返回；配置变化或后台仍停止音频，不自动恢复试听。

快速试听取消上一任务，但用跨任务 Mutex 串行保护完整音频操作及资源清理，不能只 join 紧邻的取消任务：第二个任务可能在等待第一个清理时被第三个取消。被取消的等待者不播放；已取消任务的设备错误不覆盖新请求状态，当前任务的真实错误仍记录并提示。`ToneVoice` 提供钢琴、笛子和纯音；仅钢琴页向 AudioSession 传选定音色，训练/回放默认纯音保持不变。钢琴采用衰减泛音，笛子使用柔和起音与较弱泛音，均为本地合成而非采样。独立 `pianoVoice` preference 保存选择，不改 SQLite schema 或训练设置；保存失败明确提示，选择应用于后续按键。

- 采样率 16 kHz，分析窗 2048 样本（128 ms），步长通常 512 样本（32 ms）。
- 初始 RMS 门限 0.008，YIN 阈值 0.15，有效置信度至少 0.85。
- 支持约 65–1000 Hz，边界留 10 cents 的数值检测余量，不强制吸附音符。
- 回唱：至少 400 ms，时间覆盖至少 80%；MAD ≤20 cents，且至少 80% 的有效样本离中位音高不超过 20 cents。
- 音符分段：约 96 ms 的确认时间与 120 ms 静音间隔。没有起音检测器，同音连奏可能合并，用户可用短暂停顿分开。
- 播放排队样本全部输出后再进入间隔和录音等待，不在播放阶段评分。

这些参数是初始实现，不代表真机验收达标。与设计中 48 kHz/4096 的初始方案不同，16 kHz 降低纯 Kotlin YIN 的计算量；音域、精度和延迟需按 [TESTING.md](TESTING.md) 复核。

录音源使用 `VOICE_RECOGNITION`，设备仍可能存在信号处理或路由差异。首版没有后台录音服务，也不尝试跨设备统一蓝牙体验。

## 维护约定

更新查询读取 `/repos/gongpx20069/relative-ear/releases` 而非 `/releases/latest`，因为现有发行版均为 prerelease。每页 30 条，最多 20 页，每页至多 2 MiB，连接/读取超时均为 10 秒；超过边界不报告“暂无更新”，而是失败并提供发布页面入口。新版本必须包含完整五个 APK 和 SHA256SUMS，状态为 uploaded、大小非零、地址精确属于相应官方标签；手机按 `Build.SUPPORTED_ABIS` 的顺序选包，未知架构退回 universal。检查不下载或在手机验证 APK 的 SHA-256，只验证发布元数据；真实资产签名/校验门禁由发布工作流负责。

`BuildConfig.VERSION_CODE/NAME` 由唯一版本文件生成。`EarViewModel` 保留 Application 单参数 JVM 构造器给 Android 工厂，并支持测试注入无网络 client；更新状态独立于训练阶段，不触发麦克风或写入历史。

- 新功能的领域计算先加入 `core` 测试，再接 UI。
- 错误必须有 UI 提示；保存失败不能说成绩已成功保存。
- 不将低置信结果变成零频率，不依赖目标答案“修正”检测结果。
- 英文文案集中在 `values/strings.xml`，中文对应 `values-zh/strings.xml`，资源与格式参数必须完全匹配；唱名名称使用资源数组，旧音程资源仅用于兼容。
- 不提交 SDK 路径、构建输出、签名材料、token 或个人录音。
- 应用版本只在 `version.properties` 修改，发布要求见 [RELEASE.md](RELEASE.md)。
