# 项目文档导航

本文件是文档目录与交叉引用，不是面向用户的使用手册。默认用户入口是英文 [README.md](README.md)，中文入口是 [README.zh-CN.md](README.zh-CN.md)；App 界面仍为中文。

| 文档 | Scope | 交叉引用 |
|---|---|---|
| [README.md](README.md) / [README.zh-CN.md](README.zh-CN.md) | 英文默认与中文用户指南：安装选包、练习、独立/全屏钢琴、识音回放、记录、更新和常见问题 | 用户数据边界见 PRIVACY；工程文档入口见本文件 |
| [docs/DESIGN.md](docs/DESIGN.md) | 固定 C4 唱名、范围/时值、UI 产品目标、功能边界、评分规则与验收指标；包含未来规划，不能据此声称所有功能已实现 | 当前实现见 DEVELOPMENT；验证方法见 TESTING；发布细节见 RELEASE |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | 已实现功能、实际模块、依赖环境、本地构建及当前与设计的差异 | 产品依据见 DESIGN；测试命令见 TESTING；正式签名见 RELEASE |
| [docs/RELEASE.md](docs/RELEASE.md) | 公开仓库 REST 创建、签名 Secrets、0.0.x 版本、五种架构 APK、CI 与 Release 发布/重试 | 用户选包见 README；工程构建见 DEVELOPMENT；发布前验证见 TESTING |
| [docs/TESTING.md](docs/TESTING.md) | 自动测试范围、真机检查清单、尚未验证的指标与维护方法 | 目标阈值见 DESIGN；APK 发布门禁见 RELEASE |
| [docs/PRIVACY.md](docs/PRIVACY.md) | 麦克风用途、本地数据、手动更新联网/下载、生命周期、备份与删除边界 | 用户权限操作见 README；数据实现见 DEVELOPMENT |

修改时保持交叉引用有效。行为变化同步更新 README、对应实现文档与测试；发布变化同步更新 RELEASE。设计目标与实际完成状态分开记录，不把编译成功写成真机音频验证通过。
