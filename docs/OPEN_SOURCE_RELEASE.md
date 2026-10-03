# V0.2.0 开源发布记录

日期：2026-10-03（Asia/Shanghai）。仓库：`lihanhong2002/consumer-decision-android`；默认分支 `main`；发布标签 `v0.2.0`。

## 发布内容

项目自有代码与模型规范文档采用 MIT，版权署名为 `2026 lihanhong2002`。第三方组件保留原始许可证，Gradle Wrapper 的许可证及分发声明随源码提供。

公开源码包括三模块、测试、Room 两版 schema、模型规范、文档、演示截图、可移植构建脚本与 GitHub Actions。工作流仅授予读取源码权限；执行核心测试、接口协议测试、Lint 和 APK 构建，不使用个人 AI 密钥。

Release 提供已完成 API 26/36 验收的调试 APK、从发布提交生成的源码 ZIP 和 `SHA256SUMS.txt`。既有 APK 保留原调试签名，未修改应用功能或公式。自行构建和 CI 产物可能使用其他调试签名，换包前应导出数据备份。

## 独立检出验收

从本地 Git 仓库检出到全新的英文目录，未复制工程的构建输出、`.tools` 或 `local.properties`。外部配置 JDK 17 与 Android SDK 36 后运行：

```text
:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

结果：95 项 Gradle 任务实际执行，构建成功；核心 30 项、接口 JVM 3 项通过；Lint 0 错误、0 警告。构建后检出目录的 Git 工作区干净。

API 26/36 各 9 项设备测试的既有结果与边界见 [V0.2 验收记录](VALIDATION_V0_2.md)。本次只整理开源发布文件，没有重新执行设备流程。真实 DeepSeek 服务尚未使用个人密钥联调。

## 公开文件检查

提交范围不包含工具链、缓存、生成目录、原始模拟器日志、个人数据库、用户 JSON 备份、API 密钥、签名私钥或私人邮箱。测试中的密钥字符串均为显式虚构值；截图和示例为演示数据。Git 提交使用 GitHub noreply 邮箱。

源码 ZIP 由 Git 的发布提交生成，包含 MIT、第三方声明和完整构建文件；APK 仅作为 Release 附件分发，不进入 Git 历史。

APK SHA-256：`aa47f3821990f1342d380feb890c5d3775acdda39264eae919830b2709b6ba2c`。源码 ZIP 的校验值保存在同一 Release 的 `SHA256SUMS.txt`，避免在源码中记录自身哈希。
