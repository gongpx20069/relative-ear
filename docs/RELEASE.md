# 仓库与 APK 发布

本文件仅描述仓库认证、版本、签名和发布。用户安装见 [README](../README.md)，本地环境见 [DEVELOPMENT](DEVELOPMENT.md)，验证见 [TESTING](TESTING.md)。

## 1. 创建公开仓库

目标：`gongpx20069/relative-ear`。使用 Python 标准库调用 GitHub REST API，不用 `gh repo create`。

认证可采用本机 `gh auth login` 后由脚本读取 `gh auth token`，或在本机安全设置 `GH_TOKEN`。不要在聊天、提交或日志中粘贴 token。

Token 必须具有创建公开仓库与推送代码所需权限；包含工作流的推送也需要相应权限。fine-grained PAT 还需核实是否支持所需的用户仓库创建操作，不能只授予现有仓库的只读权限。

```powershell
python scripts\github_api.py create-repo
git remote add origin https://github.com/gongpx20069/relative-ear.git
git push -u origin main
```

创建操作检查登录用户必须是 `gongpx20069`，已有公开仓库则不重复创建。发现同名私有仓库会停止，不擅自公开已有内容。REST 身份认证与 Git 推送认证分别生效；使用环境 token 调 REST 不会自动配置 Git 凭据。

当前没有可用 GitHub 认证，远端尚未创建或推送；本地实现不等于远端发布。

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

## 3. 版本与触发

`version.properties` 是唯一版本来源：

```properties
versionName=0.0.1
versionCode=1
```

下次发布改为 `0.0.2` / `2`，依次递增。不使用 `0.1.0`，不重用公开发布版本。

先将代码提交推送到 main，再推送相同版本标签：

```powershell
git tag v0.0.1
git push origin v0.0.1
```

标签必须指向已进入 main 的提交，并与工程版本一致。工作流只响应 `v0.0.*`，Python 校验进一步拒绝前导零和不匹配的版本。

## 4. CI 与发布行为

- `ci.yml`：main 推送和 PR 触发，运行 Python 测试、领域测试、lint、debug APK 与仪器测试 APK 编译。上传 debug 构建产物，不创建 Release。
- `release.yml`：版本标签触发，校验版本与 main 祖先关系，读取 Secrets，执行测试/lint，构建固定签名 release APK。
- 使用 `apksigner` 校验签名、`aapt` 校验应用 ID/版本/minSdk，并拒绝 debuggable APK。
- REST API 创建 draft Release，上传 APK 和 SHA-256 文件，确认两项成功后公开为 prerelease。
- 同标签并发发布串行化；任何失败都会退出非零，签名临时文件在任务结束时删除。

资产名称：`relative-ear-0.0.x.apk`、`relative-ear-0.0.x.apk.sha256`。

## 5. 失败处理

- 缺少 Secrets：补齐固定签名材料后重跑原标签的任务。
- 上传失败：保留 draft，重试只替换该 draft 的预期 APK/校验文件。
- draft 包含未知资产或指向另一提交：停止并人工检查。
- 版本已经公开：拒绝覆盖，增加版本并发布新标签。
- 标签版本写错或尚未合并 main：修正工程与发布提交，使用新的有效版本；不要自动移动已发布标签。

本地未配置签名时 `assembleRelease` 可以生成 unsigned APK，不能安装或发布为正式包。对正式 release 构建设置 `RELEASE_KEYSTORE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`，与 Actions 使用相同配置。

目前工作流和 REST 发布逻辑已实现，远端运行和真实 Release 上传尚未验证。首次上线仍需 GitHub 认证、仓库创建、固定签名配置和首轮 Actions 执行。
