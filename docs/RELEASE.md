# 仓库与 APK 发布

本文件仅描述仓库认证、版本、签名和发布。用户安装见 [README](../README.md)，本地环境见 [DEVELOPMENT](DEVELOPMENT.md)，验证见 [TESTING](TESTING.md)。

## 1. 创建公开仓库

目标：`gongpx20069/relative-ear`。使用 Python 标准库调用 GitHub REST API，不用 `gh repo create`。

认证优先读取本机 `GH_TOKEN`/`GITHUB_TOKEN`，其次读取 `gh auth token`，最后通过 `git credential fill` 查询 Git Credential Manager 已保存的 `gongpx20069` GitHub 凭据。查询必须带 `username=gongpx20069`：默认无用户名查询可能找不到多账号凭据。不要在聊天、提交或日志中粘贴 token。

Token 必须具有创建公开仓库与推送代码所需权限；包含工作流的推送也需要相应权限。fine-grained PAT 还需核实是否支持所需的用户仓库创建操作，不能只授予现有仓库的只读权限。

```powershell
python scripts\github_api.py create-repo
git remote add origin https://github.com/gongpx20069/relative-ear.git
git config credential.https://github.com.username gongpx20069
git push -u origin main
```

创建操作检查登录用户必须是 `gongpx20069`，已有公开仓库则不重复创建。发现同名私有仓库会停止，不擅自公开已有内容。REST 身份认证与 Git 推送认证分别生效；使用环境 token 调 REST 不会自动配置 Git 凭据。

公开仓库已通过 REST API 创建。凭据只在进程内使用，不写入源码或远端 URL；本地 Git 配置仅保存用于选择凭据的用户名。

## 2. 固定签名密钥

在可信本机生成一次 release keystore，离线备份。**所有版本重复使用这把密钥**，否则无法覆盖升级。不要使用每次生成的临时密钥，也不要把 debug key 当正式发布 key。

使用 JDK 的 `keytool` 交互输入密码，避免密码进入命令历史：

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore C:\secure\relative-ear-release.jks -alias relative-ear -keyalg RSA -keysize 3072 -validity 10000
```

将以下配置保存为仓库 Actions Secrets：

| Secret | 内容 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | keystore 完整文件的 Base64 编码 |
| `RELEASE_STORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | 密钥别名，如 `relative-ear` |
| `RELEASE_KEY_PASSWORD` | 密钥密码 |

Base64 是编码，不是加密，同样属于秘密。将其复制到 GitHub Secrets 后清除临时文本；不要写入公开仓库。`GITHUB_TOKEN` 由 Actions 自动提供，不需要保存个人 token。

首次发布已经创建固定的 3072 位 RSA 签名密钥，并配置上述四项 Secrets。本机材料位于 `%LOCALAPPDATA%\RelativeEar\signing`：

- `relative-ear-release.jks`：正式密钥，后续发布必须复用，不能覆盖或重新生成。
- `release-signing.credential.xml`：通过 Windows DPAPI 保护的密码及密钥别名，只能由对应 Windows 用户在原环境恢复。

目录 ACL 限制为当前用户访问。密钥及密码未进入仓库；配置 Secrets 使用标准输入传输并由 GitHub CLI 通过 REST API 加密上传，没有将秘密作为命令行参数。

**管理员必须离线备份 keystore，并将密码另存到安全密码库。** DPAPI 文件不是可跨机器恢复的密码备份，GitHub Secrets 也不能回读明文。更换设备前需完成备份，不要把签名目录推送到 GitHub。

## 3. 版本与触发

`version.properties` 是唯一版本来源：

```properties
versionName=0.0.4
versionCode=4
```

下次发布改为 `0.0.5` / `5`，依次递增。不使用 `0.1.0`，不重用公开发布版本。`v0.0.1` 因 SDK action 请求已移除的旧 `tools` 包而失败，保留该标签，修复后使用 `v0.0.2`，不移动旧标签。

先将代码提交推送到 main，再推送相同版本标签：

```powershell
git tag v0.0.4
git push origin v0.0.4
```

标签必须指向已进入 main 的提交，并与工程版本一致。工作流只响应 `v0.0.*`，Python 校验进一步拒绝前导零和不匹配的版本。

## 4. CI 与发布行为

- `ci.yml`：main 推送和 PR 触发，运行 Python 测试、领域与 App JVM 测试、lint、debug APK 与仪器测试 APK 编译；独立 API 29 模拟器任务执行数据库/迁移与 Compose UI 测试并上传真实截图。不创建 Release。
- `release.yml`：版本标签触发，校验版本与 main 祖先关系，读取 Secrets，执行测试/lint，构建同一签名的四种 ABI release APK 和通用 APK。
- 读取 AGP 的 `output-metadata.json`，要求五个架构完整、版本一致、属于 release，拒绝遗漏、重复、空文件和残留 APK。
- 使用 `apksigner` 校验签名、`aapt` 校验应用 ID/版本/minSdk，并拒绝 debuggable APK；同时检查包内原生库 ABI 和五个 APK 的签名证书一致性。
- REST API 创建 draft Release，上传五个 APK 和合并 SHA-256 文件，确认六项资产名称/大小/状态均正确后公开为 prerelease。
- 同标签并发发布串行化；任何失败都会退出非零，签名临时文件在任务结束时删除。

同一个 `v0.0.x` Release 包含：

| 资产 | 内容 |
|---|---|
| `relative-ear-0.0.x-arm64-v8a.apk` | 64 位 ARM |
| `relative-ear-0.0.x-armeabi-v7a.apk` | 32 位 ARM |
| `relative-ear-0.0.x-x86_64.apk` | 64 位 x86 |
| `relative-ear-0.0.x-x86.apk` | 32 位 x86 |
| `relative-ear-0.0.x-universal.apk` | 包含上述全部原生库 |
| `SHA256SUMS.txt` | 五个 APK 的 SHA-256 值和对应资产名 |

所有 APK 都可独立安装，版本统一从 `version.properties` 读取（当前 `0.0.4` / `4`），不为不同架构制造不同版本号。发布脚本在上传时命名为上述名称，本地 Gradle 输出仍使用 `app-<architecture>-release.apk`。

本地正式构建后，先验证全部 APK，再通过 REST 发布已存在的标签：

```powershell
python scripts\verify_apk.py --apk-dir app\build\outputs\apk\release --tools "$env:ANDROID_HOME\build-tools\35.0.0"
python scripts\github_api.py release --tag v0.0.4 --apk-dir app\build\outputs\apk\release
```

正常发布仍优先使用 GitHub Actions。切换到 ABI splits 后，本地第一次构建应先执行 `.\gradlew.bat :app:clean :app:assembleRelease`，防止旧版单包输出残留；新 Actions runner 不依赖旧输出。

## 5. 失败处理

- 缺少 Secrets：补齐固定签名材料后重跑原标签的任务。
- 上传失败：保留 draft，重试只替换该 draft 的预期五个 APK/校验文件；任何一个架构失败都不发布部分 Release。
- draft 包含未知资产或指向另一提交：停止并人工检查。
- 版本已经公开：拒绝覆盖，增加版本并发布新标签。
- 标签版本写错或尚未合并 main：修正工程与发布提交，使用新的有效版本；不要自动移动已发布标签。

本地未配置签名时 `assembleRelease` 可以生成 unsigned APK，不能安装或发布为正式包。对正式 release 构建设置 `RELEASE_KEYSTORE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`，与 Actions 使用相同配置。

公开仓库及固定签名 Secrets 已配置；后续推送有效 `v0.0.x` 标签会触发 Actions 发布。实际运行结果与完整 APK 资产以 Actions 和 Releases 页面为准。

首次成功发布为 [v0.0.2](https://github.com/gongpx20069/relative-ear/releases/tag/v0.0.2)，由 GitHub Actions 构建并通过 REST API 发布五个 APK 和 `SHA256SUMS.txt`。公开下载的文件已完成校验与固定签名核对；`v0.0.1` 保留为失败标签，没有移动或覆盖。

历史发布 [v0.0.3](https://github.com/gongpx20069/relative-ear/releases/tag/v0.0.3) 增加首调唱名练习与耳朵图标，仍使用同一签名。Actions 已成功发布全部架构与校验文件，公开下载的五个 APK 已核对 SHA-256、实际 ABI、版本与固定证书；运行证据见 [TESTING](TESTING.md)。

当前发布 [v0.0.4](https://github.com/gongpx20069/relative-ear/releases/tag/v0.0.4) 为固定 C4、自选音符范围、两种答案格式、八分音符播放与新版练习台 UI。Actions 已发布五个同签名 APK 和校验文件；完整公开下载校验及真实模拟器 UI/迁移运行证据见 [TESTING](TESTING.md)。
