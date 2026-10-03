# 六模型理性消费决策软件规范 V1.0.0

> **文档类型**：Software Requirements & Architecture Specification（SRS + 核心架构规范）  
> **理论依赖**：六模型统一框架：理性消费决策理论 V4.2.1  
> **理论版本状态**：Canonical / Frozen Core  
> **软件规范版本**：1.0.0  
> **目标**：将 V4.2.1 理论稳定映射为可实现、可测试、可追溯、可扩展的软件系统。  
> **适用对象**：Codex / 人类开发者 / 后续维护者 / 测试人员。  

---

# 0. 文档约定

## 0.1 规范等级

本文使用以下关键词：

- **MUST**：必须实现，不满足即视为违反理论或软件规范。
- **MUST NOT**：严禁实现。
- **SHOULD**：强烈建议，除非有明确工程理由。
- **MAY**：可选实现。

## 0.2 理论约束与工程约束的边界

本规范分为两类内容：

1. **理论硬约束**：直接来源于 V4.2.1，不得由实现层修改。
2. **工程实现约束**：为了让理论可计算、可持久化、可测试而补充的工程规则。

若工程实现与理论发生冲突：

> **理论 V4.2.1 优先。**

## 0.3 核心设计原则

软件 MUST 遵守以下原则：

1. 比较对象是**完整策略路径**，不是孤立商品。
2. 六模型负责生成稳定描述，决策层负责偏好组合。
3. `Z` 是稳定理论接口；`Y`、`ψ`、`F` 是可迭代决策接口。
4. 动态需求 `d(u,c,x,t)` MUST 只使用用户侧信息生成。
5. 技术变化 MUST 保留负向变化，禁止将退步截断为 0。
6. `ρ` 仅在其适用域内有定义；不适用时必须表示为 `NA` 语义，而不是数字 0。
7. 生命周期模型只记录真实现金流，禁止功能、情感、技术价值再次货币化后重复计入。
8. 六模型输出不得被解释为“六票等权投票”。
9. 所有结果 MUST 可追溯到输入、参数、模型版本和计算过程。
10. 参数优先校准；不得因为少量个案频繁重写理论模型。

---

# 1. 产品定义

## 1.1 产品目标

软件用于回答：

> 在给定用户、品类、情境、规划期限和候选策略的前提下，各策略在有效寿命、技术变化、情感价值、需求对齐、升级性价比和生命周期现金流上分别表现如何；经过硬约束和非支配筛选后，当前偏好函数如何进行最终排序。

软件的输出不是“绝对正确答案”，而是：

- 可解释的六模型结果；
- 可追溯的参数来源；
- 策略之间的反事实比较；
- 硬约束过滤结果；
- Pareto 非支配结果；
- 在存在合法偏好函数时的最终排序；
- 后续偏好学习所需历史数据。

## 1.2 V1 产品边界

V1 SHOULD 采用**本地优先、单用户、单机可运行**架构。

V1 MUST 包含：

- 品类与功能维度管理；
- 用户参数与参数置信度管理；
- 决策案例管理；
- 策略路径编辑；
- 动态需求生成；
- 六模型计算；
- `Z / Meta / Y` 输出；
- 硬约束过滤；
- Pareto 筛选；
- 可解释线性决策函数接口；
- 结果解释和计算追踪；
- JSON 导入/导出；
- 历史选择记录接口。

V1 MAY 暂不包含：

- 自动联网抓商品参数；
- 自动价格历史抓取；
- 云同步；
- 多用户协作；
- 非线性机器学习排序；
- Monte Carlo GUI；
- 自动推荐商品。

---

# 2. 总体架构

## 2.1 分层架构

系统 SHOULD 采用以下逻辑分层：

```text
Presentation / UI
        ↓
Application / Use Cases
        ↓
Decision Pipeline
        ↓
Six-Model Calculation Engine
        ↓
Domain Model
        ↓
Persistence / Import-Export
```

其中：

### Domain Model

保存理论对象、值对象、枚举和不变量。

### Six-Model Calculation Engine

实现 `M1 ~ M6`，必须尽量使用纯函数。

### Decision Pipeline

实现：

```text
事实输入
→ 动态需求
→ M1~M6
→ (Z, Meta)
→ ψ
→ Y
→ 硬约束
→ Pareto
→ F(Y)
```

### Application / Use Cases

负责创建决策、运行计算、保存快照、导入导出等。

### Presentation

只负责交互与展示，不得在 UI 层复制核心公式。

## 2.2 核心模块建议

```text
/core/domain
/core/validation
/core/need
/core/model/m1_lifetime
/core/model/m2_iteration
/core/model/m3_emotion
/core/model/m4_alignment
/core/model/m5_quality_price
/core/model/m6_cashflow
/core/decision/feature_mapping
/core/decision/constraints
/core/decision/pareto
/core/decision/preference
/core/trace
/data/persistence
/data/import_export
/app/usecase
/ui
/tests
```

## 2.3 平台独立性

核心计算引擎 MUST NOT 依赖具体 UI 框架或数据库。

建议将核心引擎实现为：

```text
Input DTO / Domain Object
        ↓
Pure Calculation Service
        ↓
Immutable Result Object
```

这样 Android、Desktop、Web 或命令行前端都可复用同一计算核心。

---

# 3. 版本与可复现性

每一次正式计算运行 MUST 保存：

```text
theoryVersion = "4.2.1"
specVersion
engineVersion
schemaVersion
featureMapVersion
decisionFunctionVersion
createdAt
inputSnapshotHash
```

建议结果对象包含：

```json
{
  "theoryVersion": "4.2.1",
  "specVersion": "1.0.0",
  "engineVersion": "1.0.0",
  "featureMapVersion": "psi-1",
  "decisionFunctionVersion": "linear-1"
}
```

同一输入快照 + 同一版本配置 MUST 得到确定性结果。

---

# 4. 基础领域对象

## 4.1 UserProfile

```text
UserProfile
- id
- displayName
- createdAt
- updatedAt
```

V1 可只支持一个本地用户，但数据模型 SHOULD 保留 `userId`。

## 4.2 Category

```text
Category
- id
- name
- description
- defaultHorizonYears
- defaultCashStepYears
- theoryConfigVersion
```

示例：

- 手机
- PC
- 相机
- 兴趣消费
- 耐用品

## 4.3 DimensionDefinition

每个品类拥有若干功能维度：

```text
DimensionDefinition
- id
- categoryId
- key
- name
- unit
- benefitDirection
- referenceScaleR
- thresholdTau
- positivePerceptionTheta
- negativePerceptionTheta
- positiveSlopeK
- negativeSlopeK
- saturationAnchorOptional
- isEnabled
```

`benefitDirection`：

```text
HIGHER_IS_BETTER
LOWER_IS_BETTER
```

规范：

- `referenceScaleR > 0`。
- `thresholdTau` 若存在，应位于 `[0,1]`。
- 感知阈值和斜率必须有来源和置信度。
- 不得使用当前候选集临时 min-max 作为 `R_i^(c)`。

## 4.4 ParameterRecord

参数 MUST 保存值、作用域、来源和置信度。

```text
ParameterRecord
- id
- key
- scopeType
- userId?
- categoryId?
- contextId?
- valueType
- valuePayload
- confidence
- sourceType
- sourceRef?
- validFrom?
- validTo?
- note?
- createdAt
- updatedAt
```

`scopeType`：

```text
USER_GLOBAL
USER_CATEGORY
CONTEXT
PRODUCT_MARKET
```

`valueType`：

```text
SCALAR
INTERVAL
FUNCTION_REF
DISTRIBUTION_REF
```

V1 MUST 至少完整支持 `SCALAR` 和 `INTERVAL`。

`confidence` SHOULD 使用明确枚举：

```text
LOW
MEDIUM
MEDIUM_HIGH
HIGH
```

避免存储伪精确的 0.734 之类置信度，除非未来有明确统计定义。

`sourceType`：

```text
QUESTIONNAIRE
REAL_PURCHASE
LONG_TERM_USAGE
HISTORICAL_PRICE
MARKET_DATA
EXPERT_PRIOR
MANUAL
DERIVED
```

---

# 5. 决策对象

## 5.1 DecisionCase

一次决策：

```text
DecisionCase
- id
- userId
- categoryId
- title
- contextId
- decisionTime
- horizonYears H
- cashStepYears Δt_cash
- currency
- budgetMax?
- baselineStrategyId
- status
- createdAt
- updatedAt
```

`status`：

```text
DRAFT
READY
CALCULATED
ARCHIVED
```

约束：

- `H > 0`
- `Δt_cash > 0`
- 所有参与模型六比较的策略 MUST 使用同一 `H`、`Δt_cash`、币种和现金流基线。

## 5.2 ContextSnapshot

```text
ContextSnapshot
- id
- userId
- categoryId
- timestamp
- budget
- stageLabel?
- painPoints
- availableSubstitutes
- taskRequirements
- notes
```

Context MUST 是一次决策的快照；历史决策不得自动被当前 Context 覆盖。

## 5.3 Strategy

```text
Strategy
- id
- decisionCaseId
- name
- description
- strategyType
- isBaseline
- isEnabled
- steps[]
```

`strategyType`：

```text
CONTINUE_USE
MAINTAIN
REPAIR
SUBSTITUTE
RENT
PURCHASE
WAIT_THEN_PURCHASE
MULTI_STAGE
CUSTOM
```

## 5.4 StrategyStep

```text
StrategyStep
- id
- strategyId
- sequence
- actionType
- startTimeYears
- endTimeYears?
- assetId?
- productId?
- costRef?
- notes?
```

策略比较 MUST 基于完整路径。

例如：

```text
0~1 年：继续旧机
1 年：换电池
1~3 年：继续使用
3 年：换新机
```

不得将上述路径偷偷简化为“旧机 vs 新机”而丢失中间现金流与服务状态。

---

# 6. 资产、商品与技术数据

## 6.1 AssetSnapshot

```text
AssetSnapshot
- id
- categoryId
- ownerUserId
- name
- acquiredAt?
- currentAgeYears?
- coreIdentityPolicyId
- dimensionStates[]
```

## 6.2 ProductSnapshot

```text
ProductSnapshot
- id
- categoryId
- name
- capturedAt
- price
- currency
- dimensionRawValues[]
- marketMetadata?
```

商品参数必须是**快照**，避免未来商品页变化导致历史结果不可复现。

## 6.3 DimensionRawValue

```text
DimensionRawValue
- dimensionId
- rawValue
- unit
- sourceType
- sourceRef?
- capturedAt
```

## 6.4 方向统一

模型二内部 MUST 将原始指标转换为“数值越大越有利”的方向。

建议：

```text
benefitValue(raw) =
    raw        if HIGHER_IS_BETTER
    -raw       if LOWER_IS_BETTER
```

注意：

- 此转换只改变方向，不改变尺度。
- `R_i^(c)` 仍使用与转换后差值一致的单位尺度。

---

# 7. 动态需求状态

## 7.1 NeedState

需求向量是状态变量。

```text
NeedState
- id
- decisionCaseId
- timestamp
- generationMethod
- dimensionInputs[]
- normalizedVector[]
- sourceAudit
```

`generationMethod`：

```text
MULTIPLICATIVE
WEIGHTED_SUM
MANUAL_USER_SIDE
```

## 7.2 NeedDimensionInput

```text
NeedDimensionInput
- dimensionId
- frequency F
- pain P
- importance M
- reliability R
- source
```

这些字段 MUST 来自用户侧信息。

### 严禁输入来源

需求生成模块 MUST NOT 读取：

- 新品比旧品强多少；
- `g_i`；
- `ĝ_i`；
- 新品宣传卖点；
- 候选商品技术参数相对差异。

建议在代码层直接做模块依赖隔离：

```text
NeedGenerator
```

只接收 `User / Category / Context / Time / NeedInputs`，不接收 `ProductComparison`。

## 7.3 乘法生成

若使用乘法形式：

```text
d_raw_i = F_i^αF × P_i^αP × M_i^αM × R_i^αR
```

然后：

```text
d_i = d_raw_i / Σ d_raw_k
```

所有 `α` 必须来自个人校准。

## 7.4 加权和生成

```text
d_raw_i = βF F_i + βP P_i + βM M_i + βR R_i
```

所有 `β` MUST 来自个人校准，不得由开发者拍脑袋提供“科学默认值”。

## 7.5 零向量异常

若：

```text
Σ d_raw_i = 0
```

则 MUST 返回：

```text
NEED_VECTOR_UNDEFINED
```

不得自动改为均匀权重。

用户可：

- 修正需求输入；或
- 使用 `MANUAL_USER_SIDE` 手工提供需求权重。

手工权重仍必须满足：

```text
d_i >= 0
Σ d_i = 1
```

---

# 8. 模型一：预期有效寿命 M1

## 8.1 输入

```text
M1Input
- dimensions
- currentDimensionUtilities u_i(0)
- decayModels
- needState d(t)
- globalThreshold τ_bar
- criticalDimensionPolicy K(t)
- dimensionThresholds τ_i
- nonReplaceActionPlans
- evaluationHorizon
- lifeTimeStep
```

## 8.2 功能效用

对每个维度：

```text
u_i(t) ∈ [0,1]
```

综合服务效用：

```text
U(t) = Σ d_i(u,c,x,t) × u_i(t)
```

## 8.3 衰减模型

必须支持：

### 技术相对落后型

```text
u_i(t) = exp(-λ_i t)
```

### 物理老化型

```text
u_i(t) = max(0, 1 - μ_i t)
```

### 混合型

```text
u_i(t) = exp(-λ_i t) × max(0, 1 - μ_i t)
```

若现实资产当前效用不从 1 开始，工程实现 MAY 在理论函数外增加初始状态系数，但必须：

- 明确标记为工程扩展；
- 保存原始 V4.2.1 公式结果或说明；
- 不得静默改变理论定义。

V1 最稳妥实现：将 `u_i(t)` 直接视为已归一化的当前至未来效用函数，由配置层提供函数参数。

## 8.4 可行性

```text
Feasible(t) = true
```

当且仅当：

```text
U(t) >= τ_bar
```

且对所有 `i ∈ K(t)`：

```text
u_i(t) >= τ_i
```

关键短板只能通过约束表达，MUST NOT 再从 `U(t)` 中重复扣分。

## 8.5 非换新动作

```text
NonReplaceActionPlan
- id
- name
- affectedComponents[]
- recoveryByDimension q_i(t)
- changesCoreIdentity
- identityReason?
```

动作只有在不改变核心资产身份时才可进入 `A_nonreplace`。

## 8.6 核心身份规则

```text
CoreIdentityPolicy
- categoryId
- coreComponentIds[]
- manualRules[]
```

若动作替换核心身份部件：

```text
a ∉ A_nonreplace
```

工程实现 SHOULD 提供人工 override，但必须要求填写理由并写入审计日志。

## 8.7 恢复比例

```text
q_i,a(t) ∈ [0,1]
```

恢复后：

```text
u_i^(a)(t) = u_i(t) + q_i,a(t) × [1 - u_i(t)]
```

## 8.8 T_eff

理论：

```text
T_eff = inf { t >= 0 : 对所有 a ∈ A_nonreplace, Feasible^(a)(t) = false }
```

### 数值实现

连续时间需要数值离散。

V1 MUST 使用可配置：

```text
lifeTimeStep > 0
```

例如：

```text
1/12 年 = 1 个月
```

算法：

```text
for t from 0 to evaluationHorizon step lifeTimeStep:
    evaluate base state
    evaluate every explicit allowed non-replacement action plan
    if no plan can restore feasibility:
        return t
return RIGHT_CENSORED_AT_HORIZON
```

若在评估窗口内未失效，MUST NOT 返回 `∞`；应返回：

```text
T_eff_status = RIGHT_CENSORED
T_eff_lower_bound = evaluationHorizon
```

这样避免把有限观察窗口误写成无限寿命。

## 8.9 M1 输出

```text
M1Result
- tEffValue?
- tEffStatus
- serviceUtilityTimeline
- criticalFailures[]
- bestRecoveryActionAtEachStep?
- trace
```

---

# 9. 模型二：迭代速度 M2

## 9.1 输入

```text
M2Input
- oldProductSnapshot
- newProductSnapshot
- dimensions
- ΔtYears
- currentSaturationStates S_i
- perceptionConfig
```

要求：

```text
ΔtYears > 0
R_i^(c) > 0
```

## 9.2 有符号技术增益

```text
g_i = clip((x_new_i - x_old_i) / R_i^(c), -1, 1)
```

其中 `x` 已统一为“越大越好”。

实现 MUST 保留：

```text
g_i < 0
```

不得使用：

```text
max(g_i, 0)
```

替代完整增益。

## 9.3 正负感知激活

正向：

```text
A+_i(g_i) = 1 / (1 + exp[-k+_i(g_i - θ+_i)])
```

负向：

```text
A-_i(|g_i|) = 1 / (1 + exp[-k-_i(|g_i| - θ-_i)])
```

V1 MUST 支持两种激活模式：

```text
LOGISTIC
INDICATOR
```

数据不足时可选 `INDICATOR`，但必须保存所用模式。

## 9.4 性能饱和

默认个人锚点可初始化：

```text
S_sat ≈ 0.80
```

但必须作为参数，而不是代码常量。

```text
h_sat(S_i) =
  1                              if S_i <= S_sat
  (1 - S_i) / (1 - S_sat)        if S_i > S_sat
```

饱和函数 MUST 仅作用于正向升级。

## 9.5 感知技术变化

```text
ĝ_i =
  g_i × A+_i(g_i) × h_sat(S_i)    if g_i >= 0
  g_i × A-_i(|g_i|)               if g_i < 0
```

## 9.6 汇总指标

```text
I_net  = Σ ĝ_i / (n × Δt)
I_abs  = Σ |ĝ_i| / (n × Δt)
I_gain = Σ max(ĝ_i, 0) / (n × Δt)
```

理论描述量：

```text
I_personal = I_net
```

## 9.7 M2 输出

```text
M2Result
- signedGainByDimension g[]
- perceivedGainByDimension ĝ[]
- iNet
- iAbs
- iGain
- iPersonal
- trace
```

---

# 10. 模型三：情感价值 M3

## 10.1 输入

```text
M3Input
- price P
- wtpFunctional
- wtpActual
- gammaEmo
- discountRate r
- exposureFrequencyModel n(t)?
- evaluationHorizon T?
- emotionalPriceFunctionParams a,b?
```

## 10.2 情感支付意愿

```text
W_emo = max(0, WTP_actual - WTP_func)
```

单位必须与决策币种一致。

## 10.3 情感价格函数

若存在已校准 `a,b`：

```text
W_emo(P) = a × P^b
```

约束：

```text
a > 0
0 < b < 1
```

未校准时 MUST NOT 自动拟合伪参数。

## 10.4 情感衰减

```text
e(t) = e0 × exp(-gammaEmo × t)
```

个人初始化参数可来自参数库，例如：

```text
gammaEmo >= 8.32 / year
```

但 UI 必须显示其来源和置信度。

## 10.5 WTP 与 e0 闭合

若 `n(t)=n`：

```text
W_emo = n e0 / (gammaEmo + r) × [1 - exp(-(gammaEmo+r)T)]
```

因此：

```text
e0 = W_emo(gammaEmo+r) /
     { n[1-exp(-(gammaEmo+r)T)] }
```

软件 MUST NOT 允许 `W_emo` 与 `e0` 在同一计算中作为两个互不相关的自由输入。

应采用：

```text
WTP_DRIVEN
```

或：

```text
E0_DRIVEN
```

二选一模式。

## 10.6 冷静期

```text
T_cool(q) = -ln(q) / gammaEmo
```

要求：

```text
0 < q < 1
gammaEmo > 0
```

## 10.7 M3 输出

```text
M3Result
- wEmo
- e0?
- halfLife?
- coolDownByQ?
- modelMode
- trace
```

---

# 11. 模型四：需求—技术对齐 M4

## 11.1 输入

```text
M4Input
- normalizedNeedVector d[]
- perceivedTechnicalVector ĝ[]
```

两个向量 MUST 使用相同维度顺序或相同 dimensionId 映射。

## 11.2 Gamma

```text
Gamma = (d · ĝ) / (||d||_2 × ||ĝ||_2)
```

若：

```text
||ĝ||_2 = 0
```

规定：

```text
Gamma = 0
```

数值结果应裁剪到：

```text
[-1, 1]
```

以处理浮点误差。

## 11.3 G_rel

```text
G_rel = d · ĝ
```

允许：

```text
G_rel < 0
```

不得对其取绝对值或截断为 0 后再作为主结果。

## 11.4 M4 输出

```text
M4Result
- gamma
- gRel
- contributionByDimension[]
- trace
```

`contributionByDimension` SHOULD 保存：

```text
d_i × ĝ_i
```

用于解释“哪个需求维度贡献了正/负结果”。

---

# 12. 模型五：质量—价格权衡 M5

## 12.1 结果类型禁止使用裸 nullable number

`ρ` 必须建模为显式联合状态：

```text
RhoResult =
  VALUE(value, mode)
  | NOT_APPLICABLE(reason)
  | NO_POSITIVE_RELEVANT_UPGRADE
  | INVALID_INPUT(reason)
```

MUST NOT 使用：

```text
rho = 0
```

表达不适用。

## 12.2 适用性判断

进入 `J_rho` 至少必须满足：

- 存在明确基准商品/方案；
- 存在可解释的增量价格；
- 存在正向功能升级。

V1 工程默认策略 MAY 进一步要求：

```text
ΔP > 0
```

如果采用该规则，必须标注为“实现策略”，不能伪装成新增理论定理。

## 12.3 比例尺度模式

当存在真实比例尺度 `Q`：

```text
rho = (ΔP / P_old) / (ΔQ / Q_old)
```

必须验证：

```text
P_old > 0
Q_old > 0
ΔQ > 0
```

## 12.4 多维相关升级模式

```text
G_rel_plus = max(G_rel, 0)
```

若：

```text
G_rel <= 0
```

返回：

```text
NO_POSITIVE_RELEVANT_UPGRADE
```

不得计算一个看似正常的正升级性价比数字。

否则：

```text
rho_rel = (ΔP / P_old) / max(G_rel_plus, epsilon)
```

`epsilon` 必须是配置项并记录在计算 trace 中。

## 12.5 M5 输出

```text
M5Result
- rhoResult
- eligibility
- mode
- deltaPrice
- deltaPerformance?
- gRel?
- trace
```

---

# 13. 模型六：生命周期现金流 M6

## 13.1 货币类型

持久化层 MUST 使用：

- 十进制定点；或
- 最小货币单位整数；

不得使用二进制浮点数直接保存货币金额。

每个金额必须绑定币种。

## 13.2 输入

```text
M6Input
- horizonYears H
- cashStepYears Δt_cash
- annualDiscountRate r
- currency
- strategyCashFlows[]
- salvageValueAtH
- baselineStrategyCashFlows
```

要求：

```text
H > 0
Δt_cash > 0
r > -1
```

## 13.3 时间步

```text
N = ceil(H / Δt_cash)
```

步贴现率：

```text
r_step = (1+r)^(Δt_cash) - 1
```

例如月度：

```text
Δt_cash = 1/12
```

V1 MUST 严格按理论公式计算，不自动把最后不足一个步长的区间改成其他折现规则。

## 13.4 策略现金流

```text
NPV_cash(j;H)
= -C0_j
+ Σ[k=1..N] CF_j,k / (1+r_step)^k
+ S_j,H / (1+r_step)^N
```

其中：

```text
CF_j,k = R_j,k - C_j,k
```

## 13.5 成本分类

```text
C_j,k =
  C_run
+ C_maint
+ C_induced
+ C_time
+ C_other
```

## 13.6 诱导消费

```text
E[C_induced] = Σ p_m(u,c,x,t) × c_m
```

概率允许随情境变化。

## 13.7 时间成本

```text
C_time = H_burden × w_time
```

`w_time` 必须来自参数记录。

## 13.8 共同基线

一次 DecisionCase MUST 指定唯一：

```text
baselineStrategyId
```

所有候选计算：

```text
DeltaNPV_cf(j|b)
= NPV_cash(j;H) - NPV_cash(b;H)
```

不得让不同候选分别选择最有利的现金流基线。

## 13.9 禁止双重记账

现金流编辑器 MUST 提示：

- 只记录该策略真实发生的现金流；
- 不得一边在购买方案中加入“避免支出”，另一边又在替代方案中记录对应支出；
- 不得用模糊收入归因系数猜测“买了设备带来多少收入”。

## 13.10 M6 输出

```text
M6Result
- npvCash
- deltaNpvCf
- discountedCashFlowTimeline[]
- salvagePresentValue
- baselineStrategyId
- trace
```

---

# 14. 理论描述向量 Z

每个候选策略必须生成：

```text
Z_j = [
  T_eff,
  I_personal,
  W_emo,
  Gamma,
  rho,
  DeltaNPV_cf
]
```

数据结构建议：

```text
DescriptionVectorZ
- tEff: EffectiveLifeValue
- iPersonal: Decimal
- wEmo: Money
- gamma: Decimal
- rho: RhoResult
- deltaNpvCf: Money
```

注意：

- `rho` 不是普通数字字段。
- `T_eff` 也建议保留 `RIGHT_CENSORED` 状态。

---

# 15. Meta 元数据

至少保存：

```text
StrategyMeta
- iAbs
- iGain
- gRel
- needVector
- criticalPainPoints[]
- parameterConfidenceSummary
- parameterSources[]
- rhoEligibility
- uncertaintyIntervals[]
- calculationWarnings[]
- modelTraces[]
```

Meta SHOULD 支持未来加入：

- Monte Carlo 分布；
- 敏感性分析；
- 维度贡献解释；
- 机器学习解释值。

---

# 16. 决策特征 Y 与 ψ

## 16.1 核心规则

```text
Y_j = psi(Z_j, Meta_j, x)
```

`psi` MUST 有独立版本号。

`psi` 变化不得修改历史 `Z`。

## 16.2 V1 默认特征映射

V1 可提供一个最小可解释特征映射：

```text
Y = [
  T_eff,
  W_emo,
  G_rel,
  DeltaNPV_cf
]
```

对属于 `J_rho` 的升级型比较，可附加：

```text
rho
```

但注意：

- `rho = NA` 的策略不得被强制填 0；
- 加入 `rho` 后应使用“升级型子集决策配置”。

## 16.3 标准化

若 `F` 需要标准化特征，MUST 使用：

```text
固定品类参考尺度
```

MUST NOT 使用：

```text
当前候选集 min-max
```

原因：候选集变化不应导致同一策略特征值定义漂移。

---

# 17. 硬约束层

## 17.1 Constraint

```text
Constraint
- id
- type
- target
- operator
- threshold
- severity
- source
- description
```

V1 至少支持：

```text
BUDGET
SERVICE_FEASIBILITY
CRITICAL_TASK
SAFETY
LEGAL
CUSTOM_BOOLEAN
```

## 17.2 约束输出

```text
ConstraintResult
- strategyId
- passed
- violations[]
```

只有：

```text
passed = true
```

的策略才能进入后续 Pareto。

安全、法律和不可补偿任务要求不得通过高分“抵消”。

---

# 18. Pareto 非支配筛选

## 18.1 共同可比核心指标

默认：

```text
K_common = {
  T_eff,
  I_personal,
  W_emo,
  Gamma,
  DeltaNPV_cf
}
```

`rho` 不进入全体策略共同 Pareto。

## 18.2 指标方向

默认解释：

```text
T_eff          higher is better
I_personal     higher is better
W_emo          higher is better
Gamma          higher is better
DeltaNPV_cf    higher is better
```

升级型子集中的 `rho`：

```text
lower is better
```

## 18.3 支配定义

A 支配 B 当且仅当：

1. A 在所有共同指标上不差于 B；
2. 至少一项严格优于 B。

浮点比较 MUST 使用统一容差：

```text
paretoEpsilon
```

并写入计算版本。

## 18.4 输出

```text
ParetoResult
- feasibleStrategies[]
- paretoStrategies[]
- dominatedPairs[]
- metricsUsed[]
```

Pareto 只做筛选，MUST NOT 被 UI 描述为“最终推荐算法”。

---

# 19. 最终偏好函数 F

## 19.1 无偏好参数时

若系统没有经过用户确认或校准的 `omega`：

> 软件 MUST 停止在 Pareto 结果，并显示“尚未配置最终偏好函数”。

MUST NOT 自动使用“六模型等权”或“所有特征等权”。

## 19.2 V1 线性可解释版本

```text
F(Y;omega) = omega^T × Y_tilde
```

要求：

- `omega` 必须持久化；
- 保存来源和版本；
- `Y_tilde` 的标准化规则固定且可追溯；
- UI 必须允许查看每个特征对总分的贡献。

## 19.3 结果

```text
PreferenceResult
- scoreByStrategy[]
- rankedStrategyIds[]
- featureContributions[]
- preferenceConfigVersion
```

## 19.4 禁止事项

MUST NOT：

- 把 `Z` 六个值直接默认加权；
- 把相关变量假设为独立效用来源；
- 因某个 `rho` 不适用而填 0；
- 用当前候选集动态 min-max 后宣称跨决策分数可比较。

---

# 20. 历史选择与机器学习接口

## 20.1 ChoiceEvent

系统 SHOULD 从 V1 开始记录真实选择：

```text
ChoiceEvent
- id
- decisionCaseId
- chosenStrategyId
- rejectedStrategyIds[]
- choiceTime
- userConfidence?
- reasonTags[]
- freeTextReason?
- zSnapshots
- metaSnapshots
- ySnapshots
- preferenceFunctionVersion
```

## 20.2 PairwiseChoice

为未来偏好学习派生：

```text
PairwiseChoice
- strategyAId
- strategyBId
- chosen
- featureSnapshotA
- featureSnapshotB
```

未来可学习：

```text
P(A > B)
= sigmoid(F(Y_A) - F(Y_B))
```

## 20.3 升级到非线性模型的条件

软件规范不规定具体阈值，但产品层 MUST 遵循：

> 只有真实历史数据证明线性模型存在稳定系统误差时，才启用非线性 `F(Y;theta)`。

V1 SHOULD 先积累数据，不急于训练复杂模型。

---

# 21. 计算流水线

## 21.1 Pipeline API

```text
DecisionEngine.evaluate(caseId) -> DecisionEvaluation
```

内部顺序 MUST 为：

```text
1. 加载 DecisionCase 快照
2. 校验基础输入
3. 构造/加载动态需求 d(u,c,x,t)
4. 对每个策略运行 M1
5. 对需要技术比较的策略运行 M2
6. 运行 M3
7. 使用 d 与 M2 结果运行 M4
8. 仅对 J_rho 策略运行 M5
9. 运行 M6
10. 生成 Z_j
11. 生成 Meta_j
12. 通过 psi 生成 Y_j
13. 运行硬约束
14. 对可行策略运行 Pareto
15. 若存在合法 F 和 omega，则排序
16. 保存不可变 EvaluationSnapshot
```

## 21.2 输出

```text
DecisionEvaluation
- decisionCaseSnapshot
- needStateSnapshot
- strategyEvaluations[]
- constraintResult
- paretoResult
- preferenceResult?
- warnings[]
- versionInfo
- createdAt
```

---

# 22. 计算 Trace 与解释系统

每个模型 SHOULD 输出结构化 Trace。

```text
CalculationTrace
- modelId
- modelVersion
- inputSummary
- formulaId
- intermediateValues
- outputSummary
- warnings
```

示例 M2：

```json
{
  "dimension": "battery",
  "old": 5000,
  "new": 6000,
  "direction": "HIGHER_IS_BETTER",
  "referenceScale": 2000,
  "g": 0.5,
  "activation": 0.88,
  "saturation": 0.6,
  "gHat": 0.264
}
```

结果页必须能够回答：

- 这个数从哪里来的？
- 用了哪些参数？
- 哪些参数是用户输入？
- 哪些参数是市场数据？
- 哪些参数置信度低？
- 哪个公式产生了这个结果？

---

# 23. 不确定性接口

V1 数据结构 MUST 为未来不确定性保留接口。

参数可表示：

```text
SCALAR
INTERVAL
DISTRIBUTION_REF
```

未来：

```text
theta ~ P(theta)
```

可生成：

```text
Z_j^(1)...Z_j^(N)
```

并估计：

```text
P(A dominates B)
P(j is selected)
```

注意：概率型结果是工程统计层，不得改变六模型基础定义。

---

# 24. 数据库建议结构

建议表：

```text
users
categories
dimensions
parameter_records
contexts
assets
products
product_dimension_values
decision_cases
strategies
strategy_steps
need_states
need_dimension_inputs
non_replace_actions
cash_flow_items
constraints
choice_events
evaluation_snapshots
```

## 24.1 EvaluationSnapshot

历史计算结果 SHOULD 以不可变快照保存。

```text
EvaluationSnapshot
- id
- decisionCaseId
- createdAt
- theoryVersion
- engineVersion
- inputJson
- resultJson
- inputHash
```

不要只保存“最终分数”；必须能重建当时决策环境。

---

# 25. JSON 导入/导出规范

## 25.1 顶层结构

```json
{
  "schemaVersion": "1.0.0",
  "theoryVersion": "4.2.1",
  "exportedAt": "...",
  "userProfile": {},
  "categories": [],
  "parameters": [],
  "decisionCases": [],
  "choiceEvents": []
}
```

## 25.2 兼容性

导入时 MUST：

1. 检查 `schemaVersion`；
2. 检查 `theoryVersion`；
3. 禁止静默丢弃未知重要字段；
4. 迁移失败时保留原文件不变；
5. 输出清晰迁移日志。

---

# 26. UI / UX 信息架构

以下为工程建议，不改变理论。

## 26.1 首页

模块：

```text
新建决策
进行中的决策
历史决策
品类参数
个人参数
校准记录
数据导入/导出
```

## 26.2 新建决策向导

建议按以下步骤：

### Step 1：决策基本信息

- 决策标题
- 品类
- 当前情境
- 规划期限 H
- 现金流步长
- 币种
- 预算硬约束

### Step 2：当前资产与基线

- 当前资产
- 当前功能状态
- 基线策略
- 关键身份部件

### Step 3：需求状态

按功能维度填写：

- 使用频率 F
- 当前痛点 P
- 任务重要度 M
- 可靠性要求 R

先计算需求向量，再进入商品比较页面。

这样在交互层也降低“看到新品参数后反向修改需求”的诱导。

### Step 4：候选策略

每个策略按时间轴编辑：

```text
继续使用 / 维修 / 替代 / 租赁 / 购买 / 等待 / 多阶段组合
```

### Step 5：技术比较数据

- 基准商品
- 候选商品
- 各功能维度原始指标
- 固定品类尺度
- 感知阈值
- 当前饱和状态

### Step 6：情感价值

- 同功能基准愿付价
- 实际愿付价
- 情感衰减参数
- 可选冷静期计算

### Step 7：现金流

时间轴方式录入：

- 初始支出
- 运行成本
- 维修成本
- 诱导消费
- 时间成本
- 其他成本
- 收入
- 期末残值

### Step 8：约束检查

显示：

- 缺失参数
- 单位冲突
- 不合法的 `rho` 适用域
- 需求向量异常
- 现金流基线异常
- 核心身份边界冲突

### Step 9：结果

结果页分四层：

```text
A. 硬约束：哪些方案直接淘汰
B. 六模型描述：每个模型分别说了什么
C. Pareto：哪些方案明显被支配
D. 偏好排序：仅在已配置 F 时显示
```

## 26.3 六模型结果卡片

每张卡片必须展示：

- 主结果；
- 单位；
- 状态；
- 关键贡献维度；
- 参数置信度；
- “查看计算过程”。

不得只显示一个神秘总分。

---

# 27. 验证规则

## 27.1 通用数值规则

```text
H > 0
Δt_cash > 0
Δt_tech > 0
R_i^(c) > 0
0 <= u_i <= 1
0 <= q_i,a <= 1
0 <= d_i <= 1
sum(d_i) ≈ 1
0 <= S_i <= 1
0 < q_cool < 1
r > -1
```

## 27.2 单位规则

同一维度比较 MUST 单位一致。

若单位可转换，必须先显式转换并记录：

```text
sourceUnit
convertedUnit
conversionRule
```

## 27.3 缺失值规则

缺失值必须区分：

```text
UNKNOWN
NOT_APPLICABLE
NOT_COLLECTED
UNDEFINED_BY_THEORY
```

不得统一使用 0。

---

# 28. 错误与警告码

建议：

```text
ERR_NEED_VECTOR_ZERO
ERR_REFERENCE_SCALE_NONPOSITIVE
ERR_TECH_TIME_NONPOSITIVE
ERR_CASH_STEP_NONPOSITIVE
ERR_HORIZON_NONPOSITIVE
ERR_CURRENCY_MISMATCH
ERR_BASELINE_MISSING
ERR_DIMENSION_MISMATCH
ERR_RHO_INVALID_BASELINE
ERR_RHO_NO_POSITIVE_UPGRADE
ERR_CORE_IDENTITY_VIOLATION
ERR_WTP_E0_OVERDETERMINED
ERR_PARAMETER_MISSING
WARN_LOW_CONFIDENCE_PARAMETER
WARN_T_EFF_RIGHT_CENSORED
WARN_UNCALIBRATED_PREFERENCE_FUNCTION
WARN_ENGINEERING_EXTENSION_ACTIVE
```

错误阻止计算；警告允许计算但必须展示。

---

# 29. 禁止重复计权静态规则

测试和代码审查 MUST 检查：

1. `h_sat` 只存在于 M2 正向升级路径。
2. `W_emo` 不进入 NeedGenerator。
3. 关键短板只通过 M1 可行性约束处理。
4. M6 只读取真实现金流数据结构。
5. NeedGenerator 不得依赖 M2 输出。
6. `rho NA` 不得被转换为 0。
7. `Z` 六个输出不得默认等权进入最终分数。

建议通过依赖方向直接限制：

```text
NeedGenerator  ─X→ M2Result
M6Engine       ─X→ W_emo
M6Engine       ─X→ Gamma
M6Engine       ─X→ I_personal
```

`─X→` 表示禁止依赖。

---

# 30. 测试规范

## 30.1 单元测试：M1

必须覆盖：

- 正常衰减后到达阈值；
- 关键维度先失败；
- 换电池恢复可行；
- 核心部件替换不得进入 `A_nonreplace`；
- 在评估窗口内未失败返回 `RIGHT_CENSORED`；
- 需求向量随时间变化时结果更新。

## 30.2 单元测试：M2

必须覆盖：

- `g_i > 0`；
- `g_i = 0`；
- `g_i < 0` 保持负值；
- clip 上下限；
- 饱和仅作用于正向；
- 负向升级不受 `h_sat` 削弱；
- `I_net / I_abs / I_gain` 正确；
- `I_personal == I_net`。

## 30.3 单元测试：M3

必须覆盖：

- `WTP_actual < WTP_func` 时 `W_emo = 0`；
- 半衰期；
- 冷静期；
- WTP 驱动与 e0 驱动不能同时自由输入；
- 非法 `q` 拒绝计算。

## 30.4 单元测试：M4

必须覆盖：

- 完全正向对齐；
- 正交；
- 负向对齐；
- `||ĝ||=0 => Gamma=0`；
- `G_rel < 0` 合法；
- 维度顺序错位必须被 dimensionId 映射纠正或报错。

## 30.5 单元测试：M5

必须覆盖：

- 非升级策略返回 `NOT_APPLICABLE`；
- `G_rel <= 0` 返回 `NO_POSITIVE_RELEVANT_UPGRADE`；
- 绝不返回 0 代表 NA；
- 比例尺度公式；
- 多维 rho_rel；
- epsilon 被记录。

## 30.6 单元测试：M6

必须覆盖：

- 年度步长；
- 月度步长；
- `N = ceil(H/dt)`；
- 折现率换算；
- 初始成本；
- 期末残值；
- 同一基线；
- 币种不一致报错；
- 现金流双计提示逻辑。

## 30.7 Pipeline 集成测试

至少构造 3 类场景：

### 场景 A：继续用明显占优

验证：

- `rho = NA`；
- 现金流优势；
- 可进入 Pareto；
- 不因缺 rho 被惩罚。

### 场景 B：新品某些维度升级、某些退步

验证：

- 负向 `g` 被保留；
- `I_abs` 高但 `I_net` 可能低；
- `Gamma` 可为负。

### 场景 C：高情感价值但短半衰期

验证：

- M3 独立计算；
- M6 不重复计入情感价值；
- 最终是否选择取决于显式 `F`，而不是模型自行下结论。

---

# 31. 数值精度建议

## 31.1 浮点

归一化、向量、指数函数可使用 IEEE 754 Double。

比较时统一使用：

```text
numericEpsilon
```

例如 `1e-9`，但应集中配置。

## 31.2 货币

货币必须使用 Decimal / BigDecimal / minor units。

## 31.3 展示精度

存储精度与展示精度分离。

例如：

```text
内部 Gamma = 0.6738421
UI 显示 = 0.674
```

不得把 UI 舍入值重新写回计算输入。

---

# 32. 参数初始化

可将理论文档中的当前个人参数作为**初始数据**导入，但不得硬编码为永恒常量。

例如：

```text
tau_phone ≈ 0.55
tau_PC ≈ 0.55
tau_hobby <= 0.45
tau_durable <= 0.45
S_sat ≈ 0.80
gamma_emo >= 8.32/year
rho* in [1.0, 1.2)
w_time ≈ 30 CNY/h
r ≈ 0.05/year
```

要求：

- 保存参数数学形式：点估计 / 上界 / 下界 / 区间；
- 保存置信度；
- 保存来源；
- 允许后续更新；
- 历史决策保留当时参数快照。

原历史需求权重：

```text
0.40 / 0.20 / 0.15 / 0.15 / 0.10 / 0
```

只能标记为历史初始化信息，MUST NOT 作为永久固定需求向量。

---

# 33. 软件状态与不可变快照

## 33.1 Draft vs Snapshot

编辑中的 DecisionCase 是可变的。

执行正式计算时 MUST 生成：

```text
DecisionInputSnapshot
```

计算结果绑定快照，而不是绑定可继续编辑的数据引用。

## 33.2 Recalculate

用户修改任何关键输入后：

- 原 EvaluationSnapshot 保留；
- 新建一个 EvaluationSnapshot；
- 不覆盖历史结果。

这样可以比较“我为什么后来改变了决策”。

---

# 34. 隐私与本地优先建议

以下为工程建议：

- 用户偏好、购买历史、预算和选择记录默认只存本地；
- 核心模型不依赖网络；
- 导出需要用户主动触发；
- 若未来接入商品数据 API，网络层只能提供 `ProductSnapshot`，不得直接修改理论结果。

---

# 35. API 级接口建议

以下为语言无关伪接口。

```text
interface NeedGenerator {
    NeedState generate(UserState user, Category category, ContextSnapshot context, Time t)
}

interface Model1EffectiveLife {
    M1Result evaluate(M1Input input)
}

interface Model2Iteration {
    M2Result evaluate(M2Input input)
}

interface Model3Emotion {
    M3Result evaluate(M3Input input)
}

interface Model4Alignment {
    M4Result evaluate(M4Input input)
}

interface Model5QualityPrice {
    M5Result evaluate(M5Input input)
}

interface Model6CashFlow {
    M6Result evaluate(M6Input input)
}

interface FeatureMapper {
    DecisionFeaturesY map(DescriptionVectorZ z, StrategyMeta meta, ContextSnapshot context)
}

interface ConstraintEngine {
    ConstraintResult evaluate(StrategyEvaluation strategy)
}

interface ParetoEngine {
    ParetoResult filter(List<StrategyEvaluation> strategies, ParetoConfig config)
}

interface PreferenceFunction {
    PreferenceResult rank(List<DecisionFeaturesY> candidates, PreferenceConfig config)
}

interface DecisionEngine {
    DecisionEvaluation evaluate(DecisionCaseId id)
}
```

---

# 36. 推荐的领域值对象

避免 Primitive Obsession。

建议定义：

```text
NormalizedValue [0,1]
SignedNormalizedValue [-1,1]
Probability [0,1]
Years > 0
AnnualRate > -1
Money(amount, currency)
ConfidenceLevel
DimensionId
CategoryId
StrategyId
ParameterSource
```

理论状态也应使用值对象：

```text
EffectiveLifeValue
RhoResult
NeedVector
DescriptionVectorZ
DecisionFeaturesY
```

---

# 37. 开发阶段划分

## Phase 0：项目骨架

完成：

- 模块目录；
- Domain 基础类型；
- 版本系统；
- 测试框架；
- JSON schema 基础。

验收：项目可编译，核心模块无 UI 依赖。

## Phase 1：品类、维度、参数系统

完成：

- Category；
- DimensionDefinition；
- ParameterRecord；
- 参数来源和置信度；
- 固定参考尺度。

验收：能建立“手机”品类及多个功能维度。

## Phase 2：决策案例与策略路径

完成：

- DecisionCase；
- ContextSnapshot；
- Strategy；
- StrategyStep；
- baseline。

验收：能表达多阶段策略。

## Phase 3：动态需求模块

完成：

- F/P/M/R 输入；
- 乘法 / 加权和 / 手工用户侧向量；
- 零向量错误；
- 产品信息依赖隔离。

验收：需求向量可独立于商品数据计算。

## Phase 4：M1 ~ M4

优先实现：

- 有效寿命；
- 有符号技术变化；
- 情感价值；
- 需求—技术对齐。

验收：单元测试全部通过。

## Phase 5：M5 ~ M6

完成：

- `RhoResult` 联合状态；
- 现金流时间轴；
- NPV；
- 统一基线。

验收：月度、年度现金流测试通过。

## Phase 6：Z / Meta / Y

完成：

- 稳定描述接口；
- feature map version；
- 决策特征映射；
- trace。

验收：改变 `psi` 不改变历史 `Z`。

## Phase 7：硬约束 + Pareto

完成：

- 预算等硬约束；
- 非支配筛选；
- 升级型子集 rho 诊断。

验收：`rho = NA` 的策略不会被错误淘汰。

## Phase 8：线性 F 与历史选择

完成：

- 显式偏好权重；
- 固定尺度标准化；
- 贡献解释；
- ChoiceEvent。

验收：无权重时不自动给“最终冠军”。

## Phase 9：UI 与结果解释

完成：

- 新建决策向导；
- 六模型卡片；
- 计算过程；
- Pareto 页；
- 历史结果。

## Phase 10：导入导出与迁移

完成：

- JSON 全量备份；
- schema 版本；
- migration；
- import validation。

---

# 38. MVP 验收标准

V1.0 MVP 必须满足：

- [ ] 可创建至少一个用户和多个品类。
- [ ] 可为品类定义功能维度与固定参考尺度。
- [ ] 可记录参数来源、作用域、置信度。
- [ ] 可创建一个 DecisionCase。
- [ ] 可创建至少 2 个完整策略路径。
- [ ] 可独立生成动态需求向量。
- [ ] M1 可输出有效寿命或右删失状态。
- [ ] M2 可正确保留负向升级。
- [ ] M3 可计算情感 WTP 与冷静期。
- [ ] M4 可输出 `Gamma ∈ [-1,1]` 与 `G_rel`。
- [ ] M5 正确区分 VALUE / NA / NO_POSITIVE_UPGRADE。
- [ ] M6 可按统一步长计算 NPV 与共同基线差值。
- [ ] 每个策略生成 `Z + Meta`。
- [ ] 可通过版本化 `psi` 生成 `Y`。
- [ ] 硬约束可以淘汰策略。
- [ ] Pareto 不把 `rho NA` 当 0。
- [ ] 无合法 `F` 时系统不伪造最终排序。
- [ ] 有合法线性 `F` 时可展示特征贡献。
- [ ] 所有模型有 trace。
- [ ] 历史 EvaluationSnapshot 不可变。
- [ ] JSON 可导出并重新导入。
- [ ] 理论关键单元测试通过。

---

# 39. Codex 开发规则

将本文交给 Codex 时，建议附加以下开发约束：

```text
1. 不得修改六模型公式语义。
2. 不得自行引入第七模型。
3. 不得用 0 替代 rho 的 NA。
4. 不得让 NeedGenerator 读取商品升级结果。
5. 不得将 M6 扩展成综合效用货币化模型。
6. 不得默认六模型等权。
7. 所有计算逻辑写在 core，不写进 UI。
8. 每个模型先写测试，再接 UI。
9. 任何工程扩展必须加 feature flag / version / trace。
10. 遇到理论未定义的情况，优先返回显式状态或错误，不要静默猜默认值。
```

---

# 40. 理论不可变合同（Canonical Contracts）

以下合同视为整个软件最重要的回归测试。

## Contract A：策略路径

```text
Decision = user + category + context + time + horizon + strategy set
```

比较对象是策略路径。

## Contract B：需求独立

```text
d = d(user, category, context, time)
```

商品升级信息不得参与 `d` 的生成。

## Contract C：有符号升级

```text
g_i ∈ [-1,1]
```

退步必须保留。

## Contract D：稳定理论接口

```text
M(D, Theta, j) -> (Z_j, Meta_j)
```

`Z` 不随机器学习算法变化。

## Contract E：可迭代决策接口

```text
Y_j = psi(Z_j, Meta_j, x)
```

以及：

```text
j* = argmax F(Y_j; omega)
```

仅在合法候选和合法偏好函数上成立。

## Contract F：rho 适用域

```text
j ∉ J_rho => rho = NA semantic state
```

不是 0。

## Contract G：现金流纯粹性

```text
M6 = real monetary cash flow only
```

不重复计算功能、情感和技术价值。

## Contract H：禁止六票等权

```text
Z components are descriptive outputs,
not six independent utility votes.
```

---

# 41. 后续版本路线

## V1.1 参数校准

- 参数编辑历史；
- 校准实验；
- 基于真实选择更新 `omega`；
- 敏感性分析。

## V1.2 不确定性

- 区间传播；
- Monte Carlo；
- 策略选择概率；
- 结果稳定性。

## V1.3 商品数据层

- 商品快照导入；
- 市场价格历史；
- 数据来源评级；
- 自动单位映射。

## V2.0 偏好学习

在积累足够 ChoiceEvent 后：

- pairwise logistic preference；
- 正则化线性模型；
- 交叉验证；
- 误差诊断；
- 仅在证据充分时考虑非线性模型。

机器学习层无权重新定义六模型。

---

# 42. 最终开发口径

本软件应被理解为：

```text
事实与参数管理器
+ 六模型计算引擎
+ 策略反事实比较器
+ 约束与 Pareto 筛选器
+ 可版本化偏好决策层
+ 历史选择数据采集器
```

开发优先级：

```text
先保证模型语义正确
> 再保证结果可追溯
> 再保证数据可积累
> 最后优化 UI 和机器学习
```

理论主干在 V4.2.1 冻结后，后续产品迭代首先应发生在：

- 参数校准；
- 数据质量；
- `psi`；
- `F`；
- 解释系统；
- 不确定性分析；

而不是反复修改 `M1 ~ M6` 的理论定义。

---

# 附录 A：V1 推荐初始参数导入结构

```json
{
  "theoryVersion": "4.2.1",
  "parameters": [
    {
      "key": "tau_phone",
      "scope": "USER_CATEGORY",
      "valueType": "SCALAR",
      "value": 0.55,
      "confidence": "MEDIUM"
    },
    {
      "key": "S_sat",
      "scope": "USER_GLOBAL",
      "valueType": "SCALAR",
      "value": 0.80,
      "confidence": "HIGH"
    },
    {
      "key": "gamma_emo",
      "scope": "USER_GLOBAL",
      "valueType": "INTERVAL",
      "lower": 8.32,
      "upper": null,
      "unit": "1/year",
      "boundType": "LOWER_BOUND",
      "confidence": "HIGH"
    },
    {
      "key": "rho_star",
      "scope": "USER_GLOBAL",
      "valueType": "INTERVAL",
      "lower": 1.0,
      "upper": 1.2,
      "upperExclusive": true,
      "confidence": "MEDIUM_HIGH"
    },
    {
      "key": "w_time",
      "scope": "USER_GLOBAL",
      "valueType": "SCALAR",
      "value": 30,
      "unit": "CNY/hour",
      "confidence": "MEDIUM"
    },
    {
      "key": "annual_discount_rate",
      "scope": "USER_GLOBAL",
      "valueType": "SCALAR",
      "value": 0.05,
      "unit": "1/year",
      "confidence": "MEDIUM"
    }
  ]
}
```

---

# 附录 B：最小决策案例 JSON 示例

```json
{
  "decisionCase": {
    "title": "是否更换手机",
    "category": "phone",
    "horizonYears": 3,
    "cashStepYears": 0.08333333333333333,
    "currency": "CNY",
    "budgetMax": 5000,
    "baselineStrategy": "continue_old_phone"
  },
  "needState": {
    "method": "MULTIPLICATIVE",
    "dimensions": [
      {
        "id": "battery",
        "F": 0.8,
        "P": 0.9,
        "M": 0.9,
        "R": 0.8
      },
      {
        "id": "camera",
        "F": 0.3,
        "P": 0.2,
        "M": 0.3,
        "R": 0.3
      }
    ]
  },
  "strategies": [
    {
      "id": "continue_old_phone",
      "type": "CONTINUE_USE"
    },
    {
      "id": "replace_battery_then_wait",
      "type": "MULTI_STAGE"
    },
    {
      "id": "buy_new_phone",
      "type": "PURCHASE"
    }
  ]
}
```

该示例只用于说明数据结构，不代表任何具体消费建议。

---

# 附录 C：首轮开发最短路径

如果希望立即开始编码，推荐按以下顺序提交给 Codex：

```text
Commit 1  Domain types + versioning
Commit 2  Category / Dimension / Parameter
Commit 3  DecisionCase / Strategy / StrategyStep
Commit 4  NeedGenerator + tests
Commit 5  M2 + M4 + tests
Commit 6  M3 + tests
Commit 7  M1 + tests
Commit 8  M5 + RhoResult + tests
Commit 9  M6 + cash flow + tests
Commit 10 Z / Meta / Y / trace
Commit 11 Constraint + Pareto
Commit 12 Preference F + ChoiceEvent
Commit 13 Persistence
Commit 14 Minimal UI
Commit 15 JSON backup / migration
```

理由：

- M2/M4 的输入输出边界清晰，适合先验证“技术变化—需求对齐”的核心接口；
- M1 涉及时间离散和非换新动作，复杂度更高；
- M6 涉及现金流、币种和时间尺度，应在领域类型稳定后实现；
- UI 最后接入，避免界面先行导致公式逻辑散落。

---

**End of Specification — Six Model Rational Consumption Decision Software V1.0.0**
