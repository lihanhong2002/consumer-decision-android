# 理性消费决策 APP V0.1 工程实施方案

> 理论基线：V4.2.1  
> 软件定位：Local-first、个人使用、可长期调参的消费决策支持工具  
> V0.1 目标：将六模型从理论文档正式落地为可运行的 Android APP 核心计算引擎

---

# 1. 第一版究竟做什么

V0.1 定位为：

> **Local-first 的个人消费决策计算器。**

用户建立一个消费问题，例如：

> 我要不要把当前手机换成某款新手机？

然后建立多个策略：

- A：继续使用
- B：换电池继续使用
- C：购买新手机
- D：等一年后再换

系统根据六模型计算每个策略：

\[
Z_j=
[
T_{\rm eff},
I_{\rm personal},
W_{\rm emo},
\Gamma,
\rho,
\Delta NPV_{\rm cf}
]
\]

完整流程：

```text
输入决策
    ↓
输入需求和参数
    ↓
建立多个策略
    ↓
M1~M6 计算
    ↓
生成 Z + Meta
    ↓
硬约束检查
    ↓
Pareto 非支配筛选
    ↓
展示策略差异
    ↓
用户自己选择
    ↓
保存真实选择
```

V0.1 不做强制性的“AI 推荐你买哪个”。

尤其不要直接输出：

```text
综合得分：87.4
推荐购买
```

因为六模型不是六个彼此独立的效用票，也不应该直接无脑加权成一个总分。

第一版应回答：

- 哪些方案不可行；
- 哪些方案被严格支配；
- 各方案分别好在哪里、差在哪里；
- 当前参数下各模型输出是什么；
- 用户最终真实选择是什么。

---

# 2. 技术路线

## 2.1 Android 技术栈

第一版直接采用：

```text
Kotlin
+
Jetpack Compose
+
Room
+
Coroutines / Flow
+
MVVM
```

核心数学部分写成：

```text
纯 Kotlin Core Engine
```

Core Engine 内禁止直接依赖：

```kotlin
android.*
Room
Compose
ViewModel
Context
```

这样以后即使扩展到：

- Windows
- Web
- Kotlin Multiplatform

六模型核心仍然可以复用。

---

# 3. 总体架构

```text
UI
 ↓
ViewModel
 ↓
Repository
 ↓
DecisionEngine
 ↓
M1 M2 M3 M4 M5 M6
 ↓
Z + Meta
 ↓
Constraint
 ↓
Pareto
 ↓
Result
```

核心原则：

> UI、数据库、AI、未来机器学习都不能静默改变六模型理论变量定义。

---

# 4. V0.1 项目目录

```text
ConsumerDecision/
│
├─ app/
│  ├─ MainActivity.kt
│  ├─ ConsumerDecisionApp.kt
│  └─ navigation/
│     └─ AppNavGraph.kt
│
├─ core/
│  ├─ model/
│  │  ├─ CommonTypes.kt
│  │  ├─ DecisionCase.kt
│  │  ├─ Strategy.kt
│  │  ├─ ZVector.kt
│  │  ├─ StrategyMeta.kt
│  │  ├─ NeedState.kt
│  │  ├─ Parameter.kt
│  │  └─ CashFlow.kt
│  │
│  ├─ engine/
│  │  ├─ Model1EffectiveLife.kt
│  │  ├─ Model2Iteration.kt
│  │  ├─ Model3Emotion.kt
│  │  ├─ Model4Alignment.kt
│  │  ├─ Model5PriceQuality.kt
│  │  ├─ Model6Lifecycle.kt
│  │  ├─ ParetoEngine.kt
│  │  ├─ ConstraintEngine.kt
│  │  └─ DecisionEngine.kt
│  │
│  ├─ validation/
│  │  ├─ InputValidator.kt
│  │  └─ ModelWarnings.kt
│  │
│  └─ version/
│     └─ TheoryVersion.kt
│
├─ data/
│  ├─ local/
│  │  ├─ AppDatabase.kt
│  │  ├─ DecisionCaseEntity.kt
│  │  ├─ StrategyEntity.kt
│  │  ├─ ParameterEntity.kt
│  │  └─ DecisionDao.kt
│  │
│  ├─ repository/
│  │  └─ DecisionRepository.kt
│  │
│  └─ backup/
│     ├─ JsonExporter.kt
│     └─ JsonImporter.kt
│
├─ feature/
│  ├─ home/
│  │  ├─ HomeScreen.kt
│  │  └─ HomeViewModel.kt
│  │
│  ├─ decision/
│  │  ├─ DecisionEditorScreen.kt
│  │  ├─ NeedEditorScreen.kt
│  │  ├─ StrategyEditorScreen.kt
│  │  └─ DecisionViewModel.kt
│  │
│  ├─ result/
│  │  ├─ ResultScreen.kt
│  │  └─ ResultViewModel.kt
│  │
│  ├─ history/
│  │  └─ HistoryScreen.kt
│  │
│  └─ settings/
│     └─ ParameterScreen.kt
│
└─ test/
   ├─ Model1Test.kt
   ├─ Model2Test.kt
   ├─ Model3Test.kt
   ├─ Model4Test.kt
   ├─ Model5Test.kt
   ├─ Model6Test.kt
   └─ ParetoEngineTest.kt
```

整个项目最核心的目录是：

```text
core/engine/
```

以后 UI 即使重构多次，六模型也不应该因此被改动。

---

# 5. 核心数据结构

## 5.1 DecisionCase

一次真实消费决策。

```kotlin
data class DecisionCase(
    val id: String,
    val title: String,
    val categoryId: String,

    val horizonYears: Double,
    val cashStepYears: Double,

    val budget: Double?,

    val baselineStrategyId: String,

    val strategies: List<Strategy>,

    val theoryVersion: String = "4.2.1",

    val createdAt: Long,
    val updatedAt: Long
)
```

示例：

```text
标题：
是否更换手机

品类：
PHONE

规划期限：
3 年

现金流步长：
1/12 年

预算：
4000 元
```

---

# 6. Strategy

系统比较的不是单独商品，而是完整策略路径。

```kotlin
enum class StrategyKind {
    KEEP,
    REPAIR,
    BUY,
    RENT,
    SUBSTITUTE,
    WAIT,
    COMPOSITE
}
```

示例：

```text
Strategy A
继续使用三年

Strategy B
换电池 → 再使用两年

Strategy C
现在购买新机

Strategy D
继续使用一年 → 明年换机
```

数据结构：

```kotlin
data class Strategy(
    val id: String,
    val name: String,
    val kind: StrategyKind,

    val input: StrategyInput,

    val result: StrategyResult? = null
)
```

---

# 7. ZVector

V4.2.1 的稳定理论接口直接代码化：

```kotlin
data class ZVector(
    val effectiveLifeYears: Double?,
    val personalIteration: Double?,
    val emotionalWtp: Double?,
    val alignment: Double?,
    val rho: Double?,
    val deltaNpvCf: Double?
)
```

其中：

```kotlin
rho = null
```

表示：

\[
\rho = NA
\]

禁止使用：

```kotlin
rho = 0.0
```

代替 NA。

---

# 8. StrategyMeta

Z 只保存理论主变量，其余信息进入 Meta。

```kotlin
data class StrategyMeta(
    val iterationAbs: Double?,
    val iterationGain: Double?,
    val relevantGain: Double?,

    val needVector: List<Double>,
    val signedTechVector: List<Double>,

    val rhoApplicable: Boolean,

    val warnings: List<String>,

    val parameterConfidence: Map<String, Double>
)
```

策略最终结果：

```kotlin
data class StrategyResult(
    val z: ZVector,
    val meta: StrategyMeta,

    val feasible: Boolean,
    val dominated: Boolean,

    val explanation: List<String>
)
```

---

# 9. M1：有效寿命模型

## 9.1 工程实现策略

V0.1 不追求复杂解析解，直接使用离散时间模拟。

例如：

```text
t = 0
t = 1个月
t = 2个月
...
t = H
```

默认：

\[
\Delta t = \frac{1}{12}
\]

即 1 个月。

## 9.2 功能衰减

支持三种模式：

```text
EXPONENTIAL
LINEAR
MIXED
```

分别对应：

\[
u_i(t)=e^{-\lambda_i t}
\]

\[
u_i(t)=\max(0,1-\mu_i t)
\]

\[
u_i(t)=e^{-\lambda_i t}\max(0,1-\mu_i t)
\]

综合服务效用：

\[
U(t)=\sum_i d_i(t)u_i(t)
\]

同时检查关键维度：

\[
u_i(t)\ge\tau_i
\]

## 9.3 非换新动作

```kotlin
data class RecoveryAction(
    val name: String,
    val recoveryRatio: Map<String, Double>,
    val changesCoreIdentity: Boolean
)
```

例如更换电池：

```text
battery:
q = 0.8

changesCoreIdentity = false
```

恢复后：

\[
u_i^{(a)}(t)
=
u_i(t)+q_{i,a}(t)[1-u_i(t)]
\]

当当前状态不可行，并且所有合法非换新动作都无法恢复可接受服务状态时：

```kotlin
T_eff = t
```

---

# 10. M2：技术迭代模型

输入：

- 旧产品参数；
- 新产品参数；
- 品类固定参考尺度 \(R_i^{(c)}\)；
- 当前满意度 \(S_i\)；
- 正负感知阈值。

计算：

\[
g_i=
\operatorname{clip}
\left(
\frac{x_{i,new}-x_{i,old}}
{R_i^{(c)}},
-1,1
\right)
\]

必须保留负值。

例如：

```text
续航提升：
+0.20

重量退步：
-0.15
```

再计算感知后的：

\[
\hat g_i
\]

输出：

```text
signedVector
I_net
I_abs
I_gain
```

其中：

```kotlin
personalIteration = I_net
```

V0.1 的重要测试规则：

> 新产品某项指标退步时，绝不能被错误截断为 0。

---

# 11. M3：情感价值模型

V0.1 暂不拟合：

\[
W_{\rm emo}=aP^b
\]

第一版直接使用：

\[
W_{\rm emo}
=
\max
(
0,
WTP_{\rm actual}-WTP_{\rm func}
)
\]

用户输入：

```text
如果没有外观 / IP / 联名因素，我最多愿意出多少钱？

实际上这个版本，我最多愿意出多少钱？
```

例如：

```text
普通版本愿付：
250 元

联名版本愿付：
320 元
```

则：

\[
W_{\rm emo}=70
\]

同时计算冷静期：

\[
T_{\rm cool}
=
-\frac{\ln q}{\gamma_{\rm emo}}
\]

V0.1 页面展示：

```text
情感价值：
约 70 元

情绪半衰期：
约 / 小于 X 天
```

---

# 12. M4：需求—技术对齐模型

## 12.1 需求输入

第一版不自动推断需求。

用户自定义需求维度，例如：

```text
性能
续航
屏幕
重量
存储
相机
```

然后输入当前需求状态。

V0.1 可以直接输入归一化前权重，APP 自动归一化：

\[
\sum_i d_i=1
\]

示例：

```text
性能       0.30
存储       0.25
续航       0.20
屏幕       0.15
相机       0.05
重量       0.05
```

## 12.2 对齐计算

M2 输出：

\[
\hat{\mathbf g}
\]

M4 计算：

\[
\Gamma
=
\frac{
\mathbf d\cdot\hat{\mathbf g}
}{
\|\mathbf d\|_2
\|\hat{\mathbf g}\|_2
}
\]

以及：

\[
G_{\rm rel}
=
\mathbf d\cdot\hat{\mathbf g}
\]

如果：

\[
\|\hat{\mathbf g}\|_2=0
\]

规定：

\[
\Gamma=0
\]

解释页面可显示：

```text
需求—技术对齐度：
0.76

技术变化主要集中在你当前较重视的方向。
```

如果：

\[
G_{\rm rel}<0
\]

则显示警告：

```text
该产品在当前真正重要的维度上，
总体退步大于提升。
```

---

# 13. M5：质量—价格权衡模型

程序首先判断：

```kotlin
rhoApplicable
```

只有同时满足：

- 存在明确基准；
- 有明确增量价格；
- 有正向相关功能升级；

才计算：

\[
\rho
\]

否则：

```text
rho = NA
```

多维场景使用：

\[
\rho_{\rm rel}
=
\frac{
\Delta P/P_{\rm old}
}{
\max(G_{\rm rel}^{+},\varepsilon)
}
\]

如果：

\[
G_{\rm rel}\le0
\]

直接标记：

```text
No positive relevant upgrade
```

不要人为计算“正升级性价比”。

---

# 14. M6：生命周期现金流模型

V0.1 做成一个简单现金流表。

例如：

| 时间 | 事件 | 现金流 |
|---|---|---:|
| 现在 | 买手机 | -2999 |
| 第12个月 | 换膜 | -30 |
| 第24个月 | 换电池 | -150 |
| 第36个月 | 二手卖出 | +600 |

同一个 DecisionCase 统一使用：

\[
H
\]

和：

\[
\Delta t_{\rm cash}
\]

每步贴现率：

\[
r_{\Delta t}
=
(1+r)^{\Delta t_{\rm cash}}-1
\]

计算：

\[
NPV_{\rm cash}(j;H)
\]

然后与统一 baseline 比较：

\[
\Delta NPV_{\rm cf}
=
NPV_{\rm cash}(j;H)
-
NPV_{\rm cash}(b;H)
\]

页面可以显示：

```text
相比继续使用旧手机：

方案 C
3 年现值成本增加：
¥1846
```

---

# 15. DecisionEngine

六模型由统一总入口管理。

```kotlin
class DecisionEngine(
    private val model1: Model1EffectiveLife,
    private val model2: Model2Iteration,
    private val model3: Model3Emotion,
    private val model4: Model4Alignment,
    private val model5: Model5PriceQuality,
    private val model6: Model6Lifecycle,
    private val constraintEngine: ConstraintEngine,
    private val paretoEngine: ParetoEngine
) {

    fun evaluate(
        decisionCase: DecisionCase,
        parameters: ParameterSnapshot
    ): DecisionEvaluation
}
```

内部流程：

```text
validate
 ↓
NeedState
 ↓
M1
 ↓
M2
 ↓
M3
 ↓
M4
 ↓
M5
 ↓
M6
 ↓
Z + Meta
 ↓
Constraints
 ↓
Pareto
 ↓
Result
```

---

# 16. ConstraintEngine

第一版至少支持：

```text
预算约束
安全约束
关键任务约束
必需功能约束
```

例如：

```text
Budget(strategy) <= BudgetMax
```

任何违反不可补偿硬约束的策略：

```kotlin
feasible = false
```

这些方案不再进入后续 Pareto 主比较。

---

# 17. ParetoEngine

V0.1 的核心决策筛选器。

共同指标可先使用：

```text
T_eff            MAX
I_personal       MAX
W_emo            MAX
Gamma            MAX
DeltaNPV_cf      MAX
```

注意：

```text
rho
```

不进入跨类型策略的共同 Pareto。

它只用于升级型策略子集内部诊断。

如果一个方案在所有共同指标上都不优于另一个方案，并至少一个指标严格更差，则：

```text
dominated = true
```

UI 显示：

> 该方案在当前输入参数下被另一方案严格支配。

不要直接显示：

> 不建议购买。

---

# 18. V0.1 页面结构

第一版只需要六个主要页面。

---

## 18.1 首页

```text
理性消费

[ + 新建决策 ]

最近决策

手机换新
相机购买
游戏周边
……

理论版本
V4.2.1
```

---

## 18.2 新建决策页面

输入：

```text
标题
品类
预算
规划期限 H
现金流步长
基准策略
```

---

## 18.3 需求页面

例如：

```text
你的当前需求

性能      ███████  30%
存储      ██████   25%
续航      █████    20%
屏幕      ████     15%
相机      ██        5%
重量      ██        5%

[ + 添加维度 ]
```

---

## 18.4 策略页面

```text
方案 A
继续使用

方案 B
换电池

方案 C
购买新手机

[ + 添加策略 ]
```

---

## 18.5 结果比较页面

顶部：

```text
可行策略：3
Pareto 前沿：2
被支配：1
```

核心比较表：

| 指标 | 继续用 | 换电池 | 买新机 |
|---|---:|---:|---:|
| T_eff | 0.8 | 1.8 | 3.0 |
| I_personal | 0 | 0 | 0.21 |
| W_emo | 0 | 0 | ¥200 |
| Gamma | 0 | 0 | 0.72 |
| rho | NA | NA | 1.10 |
| ΔNPV | 0 | -¥150 | -¥2100 |

下方：

```text
Pareto 前沿

● 换电池
● 买新机

被支配

○ 继续使用
```

然后显示：

```text
为什么？
```

提供规则化自然语言解释。

---

## 18.6 历史页面

```text
2026-10-02

是否购买 XXX

最终选择：
继续使用

Theory:
4.2.1
```

---

# 19. Room 数据库设计

V0.1 不要设计过多数据库表。

采用：

> 结构化主字段 + JSON Snapshot

第一版四张表足够：

```text
decision_case

strategy

parameter_profile

actual_choice
```

Strategy 保存：

```text
inputJson
resultJson
```

DecisionCase 保存：

```text
needStateJson
parameterSnapshotJson
```

这样后续数学模型变化时，不需要频繁修改大量数据库列。

---

# 20. 历史版本冻结

每条历史决策必须保存：

```text
theoryVersion
appVersion
schemaVersion
parameterSnapshot
```

旧决策不允许被新公式静默重新计算。

---

# 21. TheoryVersion

项目第一天就建立：

```kotlin
object TheoryVersion {

    const val THEORY_VERSION = "4.2.1"

    const val SCHEMA_VERSION = 1
}
```

每次计算写入：

```text
theoryVersion = 4.2.1
```

以后即使升级：

```text
4.2.1
→
4.3
→
5.0
```

旧结果仍然可复现。

---

# 22. 参数系统

统一结构：

```kotlin
data class Parameter<T>(
    val value: T,
    val confidence: Double,
    val source: ParameterSource,
    val updatedAt: Long,
    val sampleCount: Int? = null
)
```

来源：

```kotlin
enum class ParameterSource {
    QUESTIONNAIRE,
    REAL_CHOICE,
    POST_USE_FEEDBACK,
    MARKET_DATA,
    MANUAL
}
```

例如：

```text
gamma_emo

value:
8.32 / year

confidence:
0.82

source:
QUESTIONNAIRE
```

未来学习系统修改的首先应该是：

```text
Parameter
```

而不是六模型理论公式。

---

# 23. V0.1 明确不做

第一版砍掉：

```text
AI 自动推荐

商品自动联网

淘宝 / 京东爬虫

历史价格 API

账号系统

服务器

云同步

机器学习

Monte Carlo

自动参数学习

复杂图表

消费社区

自动下单
```

第一阶段只验证：

```text
数学模型
+
真实消费案例
+
决策记录
```

---

# 24. 第一批测试案例

Core Engine 完成后，先写固定测试案例，不要直接依赖 UI 测试。

建议：

```text
phone-upgrade

battery-repair

camera-upgrade

emotional-merchandise
```

重点测试：

```text
g < 0

Gamma < 0

G_rel < 0

rho = NA

techVector = 0

budget exceeded

NPV cost increase

gamma_emo increase

nonreplace recovery
```

典型测试：

```kotlin
@Test
fun rhoShouldBeNullForKeepStrategy() {
    ...
}
```

同时加入性质测试：

- 其他条件相同，成本增加时 NPV 不应改善；
- \(\gamma_{\rm emo}\) 增大时冷静期应缩短；
- 未饱和区域正向 \(g_i\) 增加时感知增益不应下降；
- 非升级策略 \(\rho\) 必须为 NA；
- 负向升级不能被截断为 0。

---

# 25. 推荐开发顺序

严格按照：

```text
STEP 1
创建 Android 项目
Compose + Room

        ↓

STEP 2
只写 core/model
建立所有数学数据结构

        ↓

STEP 3
实现 M1～M6
暂时没有 UI

        ↓

STEP 4
写单元测试
验证公式

        ↓

STEP 5
实现 DecisionEngine

        ↓

STEP 6
实现 ConstraintEngine

        ↓

STEP 7
实现 ParetoEngine

        ↓

STEP 8
Room 本地存储

        ↓

STEP 9
新建决策 UI

        ↓

STEP 10
策略输入 UI

        ↓

STEP 11
结果比较 UI

        ↓

STEP 12
历史决策

        ↓

STEP 13
JSON 导入导出
```

不要先做漂亮 UI 再塞数学模型。

这个项目真正的长期资产是：

```text
Core Engine
```

而不是首页。

---

# 26. V0.1 验收标准

第一版完成以下功能即可冻结：

```text
✓ 可以建立一个 DecisionCase

✓ 可以建立 ≥ 2 个 Strategy

✓ 可以自定义需求维度

✓ 可以输入需求状态

✓ M1 可以计算 T_eff

✓ M2 支持正向和负向技术变化

✓ M3 可以计算 W_emo

✓ M4 可以计算 Gamma / G_rel

✓ M5 支持 rho / NA

✓ M6 可以计算 NPV 和 DeltaNPV

✓ 支持预算等硬约束

✓ 可以计算 Pareto 前沿

✓ 可以显示被支配策略

✓ 可以记录最终真实选择

✓ Room 本地保存

✓ APP 重启数据仍然存在

✓ 可以 JSON 导出

✓ 保存 theoryVersion = 4.2.1

✓ 保存 parameterSnapshot

✓ 不生成未经设定的神秘综合得分
```

---

# 27. V0.2 之后的升级路线

```text
V0.2
敏感性分析

↓

V0.3
7 / 30 / 90 天复盘

↓

V0.4
参数更新建议

↓

V0.5
商品模板

↓

V0.6
AI 辅助参数录入

↓

V0.7
真实选择数据集

↓

V0.8
Preference Learning

↓

V1.0
稳定个人消费决策系统
```

---

# 28. AI 的后续正确位置

AI 不应该替代 Core Engine。

正确架构：

```text
用户自然语言
↓
AI 提取事实
↓
转换成结构化输入
↓
Core Engine 计算
↓
AI 解释结果
```

例如用户输入：

> 我现在这个手机性能够用，但是 256G 快满了，续航也稍微有点差。我在看一台 3500 元的新机。

AI 可以提取：

```text
storage pain = high
battery pain = medium
performance pain = low

candidate price = 3500
```

但 AI 不允许偷偷修改：

```text
T_eff
I_personal
W_emo
Gamma
rho
DeltaNPV_cf
```

这些必须由 Core Engine 按理论版本计算。

---

# 29. 最终架构

```text
                    ┌────────────────┐
                    │   用户输入 / AI │
                    └───────┬────────┘
                            ↓
                     Structured Data
                            ↓
                  ┌───────────────────┐
                  │   Core Engine     │
                  │                   │
                  │ M1 Effective Life │
                  │ M2 Iteration      │
                  │ M3 Emotion        │
                  │ M4 Alignment      │
                  │ M5 Price Quality  │
                  │ M6 Lifecycle      │
                  └────────┬──────────┘
                           ↓
                       Z + Meta
                           ↓
                       ψ(Z,Meta)
                           ↓
                           Y
                           ↓
                   Hard Constraints
                           ↓
                        Pareto
                           ↓
                  Explanation / UI
                           ↓
                    Actual Choice
                           ↓
                       Feedback
                           ↓
                 Parameter Calibration
```

核心原则：

\[
Z=\text{稳定理论接口}
\]

而：

\[
Y,F=\text{可迭代决策接口}
\]

---

# 30. V0.1 正式冻结定义

项目暂定：

```text
Consumer Decision
```

内部工程名：

```text
consumer-decision
```

V0.1 技术定义：

```text
Platform:
Android

Language:
Kotlin

UI:
Jetpack Compose

Database:
Room

Architecture:
MVVM + Repository + Pure Kotlin Core

Theory:
V4.2.1

Core:
M1 ～ M6
ConstraintEngine
ParetoEngine
DecisionEngine

Data:
Local-first

Decision:
Human-in-the-loop

AI:
Not implemented

ML:
Not implemented
```

---

# 31. V0.1 最终目标

第一版完成后，这个 APP 应该已经能够真正处理一个现实消费案例：

```text
我要不要换手机？
```

系统能够：

1. 建立当前需求状态；
2. 建立继续使用、维修、购买、等待等策略；
3. 计算六模型；
4. 生成 Z 与 Meta；
5. 检查硬约束；
6. 删除明显被支配策略；
7. 展示策略差异；
8. 保存用户最终真实选择；
9. 为未来参数学习积累真实数据。

因此 V0.1 的本质是：

> **一个参数化、可解释、可版本化的个人消费决策引擎。**

未来 AI、机器学习、价格数据库、商品数据库都围绕这个核心扩展，而不需要推翻第一版架构。
