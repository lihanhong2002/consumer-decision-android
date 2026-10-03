# V0.2 验收记录

验收日期：2026-10-03（Asia/Shanghai）。应用版本 0.2.0，versionCode 2；理论 4.2.1，规格 1.0.0。

## 交付内容

新增个人 AI 配置、一次一题问答、选项与自由补充、未知信息缺口、逐项建议确认、共享草稿、手动切换、修改撤销、自动保存、物品和通用参数复用、独立情景草稿及绑定历史结果的 AI 解读。首页、编辑、确认、比较、历史、物品与设置使用统一浅色卡片界面。常见术语使用日常解释，专业公式和完整追踪仍可展开查看。

AI 参考应用内完整模型规格及工程约定，不训练新模型。字段建议由应用校验并经用户确认；正式数值继续由确定性的六模型核心产生。默认没有综合分数和偏好排序。

## 自动化与设备结果

| 检查 | 结果 |
|---|---|
| 纯 Kotlin 核心 | 30 项通过（原有 22 项、新增 8 项） |
| AI 请求与响应 JVM 测试 | 3 项通过 |
| Android Lint | 0 错误、0 警告 |
| 调试 APK、Android 测试包 | 构建成功 |
| API 26 / Android 8.0 | 数据库 4 项、应用 5 项通过 |
| API 36 / Android 16 | 数据库 4 项、应用 5 项通过 |
| 安装包 | com.sixmodel.consumerdecision，minSdk 26，targetSdk 36 |

共 30 项核心、3 项 JVM、18 次 Android 测试通过。两台设备实际安装运行，设备验收没有以构建通过替代。

## 已覆盖的行为

核心保留原来的单模型、负向升级、需求零向量、单位、rho 状态、右删失、维修及资产身份、非整步长现金流、重复记账、约束、保守 Pareto、候选集独立和显式偏好配置测试。

新增核心测试覆盖严格建议协议、非法字段与重叠修改拒绝、身份保留、确认标记防绕过、无效参数及单位、未知信息保留、问答状态往返、离线时间同步、区间参数不隐式选择标量及核心计算不受引导影响。

JVM 测试覆盖 JSON 请求格式、HTTPS 地址、发送授权、空回答及截断响应拒绝、合法回答仅形成建议、非法回答保持原输入完整。

每台模拟器的数据库测试覆盖不可变历史、选择绑定、备份往返、重复导入跳过、冲突及非法文件整次拒绝、关闭数据库后恢复；新增问答、未提交文字、物品、复用参数、输入来源、正式快照及 AI 解读往返，过期确认和未知必要字段阻止计算，真实版本 1 数据库迁移到版本 2 及旧备份导入。

每台模拟器的应用测试包含三项 AI 状态测试和两项完整界面流程：个人密钥加密并排除备份；建议确认后应用；手动改动后的迟到 AI 响应丢弃；网络失败保留草稿；离线非法金额可修改重试；问答与手动共用预算和未提交文字；手机示例走完向导、确认、计算、记录选择、查看历史、通过系统文件选择器导出并重新导入。

API 26 另实际强制停止并重启应用，重新打开保存的问答，核实题目、已回答数量及未提交文字仍在；证据位于 `api26/restart*.xml`、`reopened-interview.xml` 和 `saved-answer.xml`。

API 36 也实际强制停止并冷启动应用，首页仍显示保存的案例；日志和 UI 结构保留在 `api36/restart.txt`、`restart.xml`。

## 构建与复现

Windows、Microsoft JDK 17.0.16、Gradle 9.3.1、AGP 9.1.1、Kotlin 2.2.10、KSP 2.3.9、Room 2.8.5、Compose BOM 2026.04.01。

```powershell
./build.ps1 -Tasks ':core:test',':app:testDebugUnitTest',':app:assembleDebug',':app:lintDebug',':app:assembleDebugAndroidTest',':data:assembleDebugAndroidTest'
```

使用 SixModel26Test 与 SixModel36Test Google APIs x86_64 模拟器、AEHD 加速、软件图形渲染；单台顺序运行。设备测试前停止 Gradle，避免 8GB 主机的内存争用。安装主 APK 和对应测试 APK 后运行：

```powershell
adb -s emulator-5554 shell am instrument -w com.sixmodel.consumerdecision.data.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w com.sixmodel.consumerdecision.test/androidx.test.runner.AndroidJUnitRunner
```

API 26 使用 emulator-5556。Android instrumentation 的 shell 退出码不足以判断成功，应检查日志中的 `OK (N tests)`。

## 验收中修复

- 底部导航使用实际文字定位，避免合并语义导致测试找不到按钮。
- 小屏幕上的建议确认项可能尚未在 LazyColumn 中加载；先滚动加载对应项，再操作复选框，避免误报金额问答失败。另实际点击验证金额建议正常生成。
- 补充明确的窗口调整、IME 避让及题目切换时收起键盘，改善 Android 16 的输入体验。
- API 36 测试模拟器曾出现 System UI 及 Google 后台应用无响应弹窗，拦截应用点击。仅在该测试模拟器停用无关搜索、消息、Google Play services 和原先的 Digital Wellbeing 后复测；保留文件选择器与系统界面。正式 APK 不修改设备的其他应用。
- 离线金额解析失败保留原草稿和可修改的回答，不将失败答案计入已完成题目。
- 导出再导入时保持未改动草稿的原更新时间，避免自身内容形成假冲突。
- 复用资料保存前校验，避免生成无法再次导入的备份。

## 证据与边界

本地原始测试 XML、Lint、设备日志、截图和安装包信息位于 `artifacts/validation-v0.2/`；V0.1 原交付和记录保留。公开仓库保留本验收记录及演示截图，不上传原始模拟器日志、设备数据库或个人配置。

安装包：`artifacts/ConsumerDecision-v0.2-debug.apk`。SHA-256：`aa47f3821990f1342d380feb890c5d3775acdda39264eae919830b2709b6ba2c`。apksigner 验证通过，使用 APK Signature Scheme v2。

本次没有用户的 DeepSeek API 密钥，真实付费服务连通性、模型返回质量及个人实际问题效果尚未验证。已通过可注入的模拟传输验证协议和应用状态；安装后在设置页填入自己的服务地址、模型与密钥，使用“保存并测试连接”。HTTPS 传输实现处理超时、鉴权、额度、重定向和响应长度；这些真实网络故障没有逐一进行现场注入。

离线基础引导只覆盖问题、预算、比较时间和需求重要程度，其余细节通过 AI 或完整手动编辑补充。预设、复用和估计均需核实，必要信息缺口阻止正式计算。AI 解读单独保存，不参与核心计算；用户仍需判断其文字表述。

交付为调试签名 APK。源码包含构建脚本、测试、Room 两版 schema 和模型文档，不包含本机工具链、个人密钥或发布签名。本记录对应首次本地验收；后续 GitHub 开源发布记录见 `OPEN_SOURCE_RELEASE.md`。未发布应用商店。
