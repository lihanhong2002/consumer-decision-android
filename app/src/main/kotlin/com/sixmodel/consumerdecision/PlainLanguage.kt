package com.sixmodel.consumerdecision

fun translated(value: String): String = mapOf("EXPONENTIAL" to "逐年按比例变差","LINEAR" to "逐年减少固定份额","MIXED" to "比例与固定减少一起作用","LOGISTIC" to "超过起点后逐渐感受到变化","INDICATOR" to "达到起点才感受到变化","INITIAL" to "现在的一次性支出","RUN" to "日常使用费用","MAINTENANCE" to "维修和维护费用","INDUCED" to "可能新增的消费","TIME" to "花费时间的代价","OTHER" to "其他收支","INCOME" to "收到的钱","SALVAGE" to "评估结束时卖出的收入","MANUAL_USER_SIDE" to "我直接设置各项的重要程度","MULTIPLICATIVE" to "多项条件共同起作用（乘法）","WEIGHTED_SUM" to "分别考虑后合在一起（加权和）","CRITICAL_TASK" to "关键任务要求","SAFETY" to "安全要求","LEGAL" to "法律要求","CUSTOM_BOOLEAN" to "自定义必须条件","USER_GLOBAL" to "这个用户通用","INVALID_INPUT" to "参数不合法，无法判断")[value] ?: legacyTranslated(value)

fun plainLabel(label: String): String {
    val exact=mapOf("规划期 H（年）" to "比较未来多少年","固定参考尺度 R" to "衡量性能变化的固定单位","关键阈值 τ" to "这个关键项目最低能接受多少（0~1）","原始权重（自动归一化）" to "相对重要程度（份数，自动换算）","频率 F [0,1]" to "使用频率（0~1）","痛点 P [0,1]" to "问题对你的影响（0~1）","重要度 M [0,1]" to "任务重要程度（0~1）","可靠性 R [0,1]" to "不能出问题的程度（0~1）","资产身份 ID" to "物品记录编号（同物品保持一致）","正向阈值 θ+" to "改善多少后你能感受到（0~1）","负向阈值 θ−" to "变差多少后你能感受到（0~1）","正向斜率 k+" to "对改善的敏感程度","负向斜率 k−" to "对变差的敏感程度","技术间隔 Δt（年）" to "两代产品相隔多少年","标量值" to "本次采用的数值","作用域" to "在哪些情况下使用这个参数","旧指标" to "当前物品实际参数","新指标" to "备选物品实际参数","满意度 S [0,1]" to "这项能力现在够用到什么程度（0~1）","Q_old" to "旧物品可作比例比较的质量值","Q_new" to "新物品同一尺度的质量值")
    return exact[label] ?: label.replace("配置显式线性偏好排序（可选）","按我的偏好排序（可选）").replace("线性 F / 固定尺度","我的偏好与比较尺度").replace("置信度","对信息有多确定").replace("维度","比较项目").replace("硬约束","必须条件").replace("恢复比例 q","补足不足部分的比例（0~1）").replace("效用/衰减参数来源","当前状态和变化估计的来源").replace("CRITICAL_TASK","关键任务").replace("SAFETY","安全").replace("LEGAL","法律").replace("CUSTOM_BOOLEAN","自定义必须条件")
}
fun fieldHelp(label: String): String? = when {
    label=="原始权重（自动归一化）" -> "例如 1、3、5 份表示相对重要程度，至少一项大于零。"
    label.contains("满足需求的程度") -> "0 表示完全不能满足，1 表示完全满足；根据当前实际体验估计。"
    label.contains("恢复比例") -> "修复的是当前不足的部分。例如当前 0.5、恢复比例 0.8，修复后为 0.5+(1−0.5)×0.8=0.9。"
    label.contains("固定参考尺度") -> "用它把不同单位的变化换成可比较幅度。固定后不能因增加备选商品而改变。"
    label.contains("θ") -> "按固定尺度换算后的变化起点，0.1 表示变化达到尺度的 10%。"
    label.contains("k+") || label.contains("k−") -> "越大，超过感知起点后感觉变化越明显；这是待确认的技术预设。"
    label.contains("满意度") -> "越接近 1，进一步正向升级越不容易带来有用改善；变差仍会保留。"
    label.contains("个人校准系数") -> "决定使用频率、问题、任务和可靠性各起多大作用。需要你明确选择并确认。"
    label.contains("一年后相对减少") -> "例如 5 表示每年相对上一年的水平减少 5%，应用会换算成指数衰减参数。"
    label.contains("固定减少") -> "每年减少的固定份额；例如 0.05 表示每年减少 0.05。在线性或混合变化模式下使用。"
    else -> null
}
