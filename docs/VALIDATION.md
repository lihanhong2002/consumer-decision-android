# V0.1 验收记录

验收日期：2026-10-02（Asia/Shanghai）。工程与 APK 版本 0.1.0；理论 4.2.1；规格 1.0.0。

## 结果

| 检查 | 实际结果 |
|---|---|
| 纯 Kotlin 核心测试 | 22 项通过，0 失败、0 跳过 |
| Android Lint | 0 错误、0 警告 |
| 调试 APK / 两个 Android 测试包 | 构建成功 |
| API 36 / Android 16 | 数据库 2 项 + 完整界面 1 项通过 |
| API 26 / Android 8.0 | 数据库 2 项 + 完整界面 1 项通过 |
| APK 签名 | apksigner 验证通过，APK Signature Scheme v2 |
| 安装包信息 | 包名 com.sixmodel.consumerdecision，minSdk 26，targetSdk 36 |

APK：`artifacts/ConsumerDecision-v0.1-debug.apk`。

SHA-256：`5e4bcd80afce80e2ae14c645b4cbb4779dd79b7a16662b099c12ca1bf0fd0815`。

## 已验证的行为

核心测试验证：有符号增益及方向统一、饱和只削弱正向、需求零向量和个人系数确认、负向/零向量对齐、情感 WTP 与冷静期、rho状态及比例尺度、恢复动作和核心身份排除、右删失、年度/月度和非整步长现金流、基线及预算、需求变更边界、单位冲突、确定性序列化、区间参数显式选择、候选集独立、保守 Pareto、显式固定尺度偏好排序、时间/诱导成本来源，以及使用→维修→等待→购买保留全部事件。

每个模拟器上的数据库测试验证：计算快照追加而不覆盖、选择绑定历史、JSON往返、相同 ID 跳过、冲突事务回滚、未知字段/版本拒绝、关闭并重开数据库后快照完整。

每个模拟器上的界面测试实际执行：载入手机示例→走完九步向导→正式计算→查看无默认偏好排序→记录真实选择→打开历史→通过 Android 系统文件选择器保存 JSON→再次选取该文件导入→确认相同记录跳过。测试结束保存截图。

API 26 另执行应用进程 force-stop 与重新启动，保留重启日志、UI结构和截图以验证首页继续显示已保存案例。

## 测试环境与复现

Windows；Microsoft JDK 17.0.16；Gradle 9.3.1；AGP 9.1.1；Kotlin 2.2.10；KSP 2.3.9；Room 2.8.5；Compose BOM 2026.04.01。

使用本项目创建的 SixModel36Test 和 SixModel26Test Google APIs x86_64 模拟器，AEHD硬件加速，单台顺序运行，隐藏窗口，软件图形渲染。

```powershell
./build.ps1
./build.ps1 -Tasks @(':data:assembleDebugAndroidTest', ':app:assembleDebugAndroidTest')
```

本机物理内存约 8GB。最终设备测试在停止 Gradle 后通过 adb 直接运行已构建测试包，以免构建进程与模拟器争用内存：

```powershell
adb -s emulator-5554 shell am instrument -w com.sixmodel.consumerdecision.data.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w com.sixmodel.consumerdecision.test/androidx.test.runner.AndroidJUnitRunner
```

API 26 使用 emulator-5556。测试包需先安装；脚本中的 adb 可替换为 `.tools/sdk/platform-tools/adb.exe`。设备充足时也可直接使用 `./build.ps1 -Connected`。

## 验收中解决的问题

- Windows中文路径导致 JVM 测试类加载失败，使用指向原工程的英文目录联接构建。
- 更新 KSP 以支持 AGP 9 内置 Kotlin。
- 修正数据库测试方法的返回类型，使其符合 JUnit4要求。
- API 36首次启动系统 Digital Wellbeing / DocumentsUI出现无响应。仅在测试模拟器停用 Digital Wellbeing，停止构建进程释放内存，重新执行原文件流程并通过。
- 文件选择器返回应用存在生命周期过渡期；测试等待 Compose界面重新出现后再断言，避免将临时无界面误判为失败。

## 原始证据

`artifacts/validation/core/EngineTest.xml`；`artifacts/validation/lint/lint.xml`；两个 `api*/repository-tests.txt` 与 `api*/app-tests.txt`；`api*/acceptance.png`；API 26重启记录。

此交付为可安装调试版本。正式发布签名、商店发布和真实个人参数校准不在本次验收范围内。
