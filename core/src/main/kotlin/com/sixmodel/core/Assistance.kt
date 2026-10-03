package com.sixmodel.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.math.ln

@Serializable data class InterviewOption(val id: String, val label: String, val detail: String = "")
@Serializable data class InterviewQuestion(val id: String, val title: String, val help: String = "", val why: String = "", val targetPath: String? = null, val options: List<InterviewOption> = emptyList())
@Serializable data class InterviewAnswer(val question: InterviewQuestion, val optionId: String? = null, val text: String = "", val unknown: Boolean = false)
@Serializable data class SuggestedChange(val path: String, val value: JsonElement, val explanation: String, val source: String = "AI_ESTIMATE", val confidence: Confidence = Confidence.LOW)
@Serializable data class AssistantReply(val schemaVersion: String = "1", val question: InterviewQuestion? = null, val changes: List<SuggestedChange> = emptyList(), val unknownPaths: List<String> = emptyList(), val summary: String = "", val readyForReview: Boolean = false)
@Serializable data class InputChange(val revision: Long, val source: String, val description: String, val paths: List<String>)
@Serializable data class UndoDraft(val inputJson: String, val unknownPaths: List<String>)
@Serializable data class IntakeSession(val schemaVersion: String = "1", val decisionId: String, val revision: Long = 0, val question: InterviewQuestion? = null, val answers: List<InterviewAnswer> = emptyList(), val pending: AssistantReply? = null, val unknownPaths: List<String> = emptyList(), val reviewedHash: String? = null, val changes: List<InputChange> = emptyList(), val undo: UndoDraft? = null, val mode: String = "AI", val draftAnswer: InterviewAnswer? = null) {
    fun validate() {
        require(schemaVersion == "1" && decisionId.isNotBlank() && revision >= 0)
        require(mode in setOf("AI","OFFLINE"))
        require(answers.size <= 500 && changes.size <= 1000)
        require(unknownPaths.distinct().size == unknownPaths.size)
        question?.let(AssistantProtocol::validateQuestion)
        draftAnswer?.let { AssistantProtocol.validateQuestion(it.question);require(it.text.length<=4000 && (it.optionId==null || it.question.options.any {o->o.id==it.optionId})) }
        answers.forEach { AssistantProtocol.validateQuestion(it.question); require(it.text.length <= 4000 && (it.optionId == null || it.question.options.any { o -> o.id == it.optionId })) }
        undo?.let { require(SnapshotCodec.decode<DecisionInputSnapshot>(it.inputJson).id == decisionId) }
    }
}
@Serializable data class AssetProfile(val schemaVersion: String = "1", val id: String, val name: String, val category: Category, val stage: Stage, val need: NeedPoint, val parameters: List<ParameterRecord>, val updatedAt: Long)
@Serializable data class ReusableDefaults(val schemaVersion: String = "1", val horizon: Double, val cashStep: Double, val currency: String, val budget: Money?, val totalBudget: Money?, val parameters: List<ParameterRecord>)
@Serializable data class AiExplanation(val schemaVersion: String = "1", val id: String, val evaluationId: String, val model: String, val provider: String, val text: String, val createdAt: Long, val promptVersion: String = "explain-1")

/** AI only proposes field updates. Stable IDs and confirmation flags are owned by the app. */
object AssistantProtocol {
    private val roots = setOf("title", "context", "horizon", "cashStep", "currency", "budget", "totalBudget", "baselineId", "category", "needs", "parameters", "strategies", "preference")
    fun segments(path: String): List<String> {
        require(path.startsWith('/') && path.length <= 250) { "建议字段路径不合法" }
        val parts = path.substring(1).split('/').map { it.replace("~1", "/").replace("~0", "~") }
        require(parts.none { it.isBlank() } && parts.first() in roots) { "AI 不能修改这个字段：$path" }
        return parts
    }
    fun read(input: DecisionInputSnapshot, path: String): JsonElement? = read(SnapshotCodec.json.encodeToJsonElement(input), segments(path))
    private fun read(value: JsonElement, parts: List<String>): JsonElement? {
        if(parts.isEmpty()) return value
        val next = when(value) { is JsonObject -> value[parts.first()]; is JsonArray -> parts.first().toIntOrNull()?.let { value.getOrNull(it) }; else -> null } ?: return null
        return read(next, parts.drop(1))
    }
    private fun replace(value: JsonElement, parts: List<String>, newValue: JsonElement): JsonElement {
        if(parts.isEmpty()) return newValue
        val key = parts.first()
        return when(value) {
            is JsonObject -> { require(key in value) { "建议引用了不存在的字段" }; JsonObject(value + (key to replace(value.getValue(key), parts.drop(1), newValue))) }
            is JsonArray -> { val index = key.toIntOrNull() ?: error("数组位置不合法"); require(index in value.indices); JsonArray(value.mapIndexed { i, v -> if(i == index) replace(v, parts.drop(1), newValue) else v }) }
            else -> error("建议字段路径无法访问")
        }
    }
    fun validateQuestion(q: InterviewQuestion) {
        require(q.id.isNotBlank() && q.id.length <= 100 && q.title.isNotBlank() && q.title.length <= 180 && q.help.length <= 600 && q.why.length <= 1000)
        require(q.options.size <= 6 && q.options.map { it.id }.distinct().size == q.options.size)
        q.options.forEach { require(it.id.isNotBlank() && it.id.length <= 100 && it.label.isNotBlank() && it.label.length <= 100 && it.detail.length <= 250) }
        q.targetPath?.let(::segments)
    }
    fun parse(text: String, input: DecisionInputSnapshot): AssistantReply {
        require(text.length <= 150000) { "AI 返回内容过长" }
        val reply = SnapshotCodec.decode<AssistantReply>(text)
        validate(reply, input)
        return reply
    }
    fun validate(reply: AssistantReply, input: DecisionInputSnapshot) {
        require(reply.schemaVersion == "1" && reply.summary.length <= 2000 && reply.changes.size <= 80)
        require(reply.question != null || reply.readyForReview || reply.changes.isNotEmpty()) { "AI 没有给出有效问题或建议" }
        reply.question?.let { validateQuestion(it); it.targetPath?.let { p -> require(read(input, p) != null) { "问题引用了不存在的字段" } } }
        require(reply.unknownPaths.size <= 100 && reply.unknownPaths.distinct().size == reply.unknownPaths.size)
        reply.unknownPaths.forEach { require(read(input, it) != null) { "信息缺口字段不存在" } }
        val paths = reply.changes.map { it.path }
        require(paths.distinct().size == paths.size)
        require(paths.none { a -> paths.any { b -> a != b && a.startsWith("$b/") } }) { "AI 建议包含相互重叠的字段" }
        reply.changes.forEach {
            val parts = segments(it.path)
            require(parts.none { p -> p in setOf("id", "userId", "confirmed", "coefficientsConfirmed", "version", "schemaVersion") }) { "AI 不能修改身份或确认状态" }
            require(read(input, it.path) != null)
            require(it.source in setOf("USER_ANSWER", "AI_ESTIMATE", "PRESET") && it.explanation.isNotBlank() && it.explanation.length <= 1500)
        }
        if(reply.changes.isNotEmpty()) {
            val proposed=preview(input,reply.changes)
            proposed.parameters.forEachIndexed {i,p->if(p.value==null&&p.selected==null)require(reply.unknownPaths.any {overlap(it,"/parameters/$i")}) {"区间参数未选择数值，请明确标注信息缺口"}}
        }
    }
    fun preview(input: DecisionInputSnapshot, changes: List<SuggestedChange>): DecisionInputSnapshot {
        var json = SnapshotCodec.json.encodeToJsonElement(input)
        changes.forEach { json = replace(json, segments(it.path), it.value) }
        var next = SnapshotCodec.json.decodeFromJsonElement<DecisionInputSnapshot>(json)
        require(next.id == input.id && next.userId == input.userId && next.example == input.example && next.category.id == input.category.id) { "AI 不能替换决策身份或示例标记" }
        fun protectedIds(old: List<String>, new: List<String>) { require(new.containsAll(old)) { "AI 不能删除或更换已有身份；请在手动模式操作" } }
        protectedIds(input.strategies.map { it.id }, next.strategies.map { it.id })
        protectedIds(input.category.dimensions.map { it.id }, next.category.dimensions.map { it.id })
        protectedIds(input.parameters.map { it.id }, next.parameters.map { it.id })
        input.strategies.forEach { s ->
            val n = next.strategies.first { it.id == s.id }; protectedIds(s.stages.map { it.id }, n.stages.map { it.id })
            s.stages.forEach { st -> require(n.stages.first { it.id == st.id }.assetId == st.assetId) { "改变物品身份请在手动路径编辑中确认" } }
        }
        // A whole-object patch must not smuggle confirmation of new or changed values.
        next = next.copy(parameters=next.parameters.map { p ->
            val previous=input.parameters.firstOrNull { it.id == p.id }
            if(previous == p) p else p.copy(confirmed=false,source="AI_PENDING",confidence=Confidence.LOW)
        }, needs=next.needs.mapIndexed { i, p -> if(input.needs.getOrNull(i) == p) p else p.copy(coefficientsConfirmed=false) }, preference=next.preference?.let { if(it == input.preference) it else it.copy(confirmed=false) })
        next = next.copy(strategies=next.strategies.map { s -> s.copy(flows=s.flows.map { f ->
            val old=input.strategies.firstOrNull { it.id==s.id }?.flows?.firstOrNull { it.id==f.id }
            if(f.hourlyRateParameter != old?.hourlyRateParameter) f.copy(hourlyRateParameter=f.hourlyRateParameter?.copy(confirmed=false,source="AI_PENDING")) else f
        }) })
        require(next.title.isNotBlank() && next.title.length <= 500 && next.context.length <= 10000)
        // Validate all shape/units/ranges, without granting actual confirmation or calculating a record.
        val check=next.copy(parameters=next.parameters.map { it.copy(confirmed=true) },needs=next.needs.map { it.copy(coefficientsConfirmed=true) },strategies=next.strategies.map { s -> s.copy(flows=s.flows.map { f -> f.copy(hourlyRateParameter=f.hourlyRateParameter?.copy(confirmed=true)) }) })
        next.parameters.forEach {p->require(p.lower==null||p.lower.isFinite());require(p.upper==null||p.upper.isFinite());require(p.lower==null||p.upper==null||p.lower<=p.upper)}
        try {DecisionEngine().evaluate(check)} catch(e: IllegalStateException) {if(!e.message.orEmpty().contains("ERR_PARAMETER_MISSING"))throw e}
        return next
    }
    fun apply(input: DecisionInputSnapshot, changes: List<SuggestedChange>, source: String, unknownPaths: List<String> = emptyList()): DecisionInputSnapshot {
        validate(AssistantReply(changes=changes,unknownPaths=unknownPaths),input)
        val next=preview(input,changes)
        fun affected(path: String) = changes.any { overlap(it.path,path) }
        return next.copy(category=next.category.copy(dimensions=next.category.dimensions.mapIndexed {i,d->if(affected("/category/dimensions/$i"))d.copy(source=source,confidence=Confidence.LOW) else d}),parameters=next.parameters.mapIndexed { i,p -> if(affected("/parameters/$i")) p.copy(source=source) else p },strategies=next.strategies.mapIndexed { si,s -> s.copy(stages=s.stages.mapIndexed { ti,st ->
            val prefix="/strategies/$si/stages/$ti"
            st.copy(utilities=st.utilities.mapIndexed { ui,u -> if(affected("$prefix/utilities/$ui")) u.copy(source=source,confidence=Confidence.LOW) else u },emotion=if(affected("$prefix/emotion")) st.emotion.copy(source=source,confidence=Confidence.LOW) else st.emotion,upgrade=st.upgrade?.let { if(affected("$prefix/upgrade")) it.copy(source=source,confidence=Confidence.LOW) else it },repairs=st.repairs.mapIndexed { ri,r -> if(affected("$prefix/repairs/$ri")) r.copy(recovery=r.recovery.copy(source=source,confidence=Confidence.LOW)) else r })
        },flows=s.flows.mapIndexed { fi,f -> if(affected("/strategies/$si/flows/$fi")) f.copy(source=source,confidence=Confidence.LOW) else f }) })
    }
    fun overlap(a: String,b: String) = a == b || a.startsWith("$b/") || b.startsWith("$a/")
    fun changedPaths(a: DecisionInputSnapshot,b: DecisionInputSnapshot): List<String> {
        val changes=mutableListOf<String>()
        fun walk(x: JsonElement,y: JsonElement,p: String) {
            if(x==y) return
            if(x is JsonObject && y is JsonObject && x.keys == y.keys) x.keys.forEach { walk(x.getValue(it),y.getValue(it),"$p/$it") }
            else if(x is JsonArray && y is JsonArray && x.size==y.size) x.indices.forEach { walk(x[it],y[it],"$p/$it") }
            else changes+=p
        }
        walk(SnapshotCodec.json.encodeToJsonElement(a),SnapshotCodec.json.encodeToJsonElement(b),"")
        return changes
    }
}

/** Deterministic offline questions expose the selected scalar; no implicit low/medium mapping. */
object OfflineInterview {
    fun next(input: DecisionInputSnapshot, session: IntakeSession): InterviewQuestion? {
        val answered=session.answers.map { it.question.id }.toSet()
        val basic=listOf(
            InterviewQuestion("goal","这次你在纠结什么？","例如：手机换电池还是换新，或者相机是否升级。","先确定问题，再比较方案。","/title"),
            InterviewQuestion("budget","现在最多准备花多少钱？","直接填写人民币金额，也可以不设预算。","这是初始实际支出的上限，出售收入单独记录。","/budget",listOf(InterviewOption("1000","¥1,000"),InterviewOption("3000","¥3,000"),InterviewOption("6000","¥6,000"),InterviewOption("none","不设预算"))),
            InterviewQuestion("horizon","想比较未来多久？","所有方案使用同一段时间。","未来费用和可用寿命需要统一比较范围。","/horizon",listOf(InterviewOption("1","1 年"),InterviewOption("2","2 年"),InterviewOption("3","3 年")))
        )
        basic.firstOrNull { it.id !in answered }?.let { return it }
        input.category.dimensions.forEachIndexed { i,dim ->
            val id="need-${dim.id}"
            if(id !in answered) return InterviewQuestion(id,"${dim.name}对你有多重要？","选择的是相对份数，不是客观评分。也可自己填写非负数。","同一时间点会把各项份数换算成总和为 100% 的需求。","/needs/0/inputs/$i/weight",listOf(InterviewOption("1","1 份 · 一般重要"),InterviewOption("3","3 份 · 比较重要"),InterviewOption("5","5 份 · 很重要")))
        }
        return null
    }
    fun proposal(input: DecisionInputSnapshot, answer: InterviewAnswer): AssistantReply {
        val path=answer.question.targetPath ?: return AssistantReply(readyForReview=true)
        if(answer.unknown) return AssistantReply(unknownPaths=listOf(path),summary="已保留为不确定。可以继续其他问题，计算前需要明确必要信息。",readyForReview=true)
        val value=(answer.text.trim().takeIf { it.isNotEmpty() } ?: answer.optionId ?: "").trim()
        val changes=when(answer.question.id) {
            "goal" -> listOf(SuggestedChange(path,JsonPrimitive(value),"你的原话：$value","USER_ANSWER",Confidence.HIGH))
            "budget" -> { val amount=if(answer.optionId=="none" && answer.text.isBlank()) null else value.toBigDecimalOrNull()?.also { require(it.signum() >= 0) { "请填写非负金额，例如 3000" } } ?: if(answer.optionId=="none") null else error("请只填写金额，例如 3000"); listOf(SuggestedChange(path,amount?.let { SnapshotCodec.json.encodeToJsonElement(Money(it.toPlainString(),input.currency)) } ?: JsonNull,"你明确选择的初始支出预算","USER_ANSWER",Confidence.HIGH)) }
            "horizon" -> {
                val h=value.toDoubleOrNull() ?: error("请填写年数，例如 2"); require(h>0 && h.isFinite())
                require(input.strategies.all { it.stages.size==1 }) { "多阶段方案请到手动模式调整各阶段和期末收入时间" }
                listOf(SuggestedChange(path,JsonPrimitive(h),"你选择的统一比较时间","USER_ANSWER",Confidence.HIGH),SuggestedChange("/strategies",SnapshotCodec.json.encodeToJsonElement(input.strategies.map { s -> s.copy(stages=s.stages.map { it.copy(end=h) },flows=s.flows.map { if(it.kind==CashKind.SALVAGE) it.copy(time=h) else it }) }),"同步单阶段结束时间及期末卖出收入","USER_ANSWER",Confidence.HIGH))
            }
            else -> { val w=value.toDoubleOrNull() ?: error("请填写相对份数，例如 3");require(w.isFinite() && w>=0);listOf(SuggestedChange(path,JsonPrimitive(w),"你选择的需求份数：$w","USER_ANSWER",Confidence.HIGH)) }
        }
        return AssistantReply(changes=changes,summary="按你的选择整理，请确认后更新草稿。",readyForReview=true)
    }
}

object FriendlyFields {
    fun path(input: DecisionInputSnapshot,p: String): String {
        val parts=p.trim('/').split('/')
        val dimension=if(parts.firstOrNull()=="needs") parts.getOrNull(3)?.toIntOrNull()?.let { input.needs.getOrNull(0)?.inputs?.getOrNull(it)?.dimensionId }?.let { id -> input.category.dimensions.firstOrNull { it.id==id }?.name } else null
        val suffix=parts.lastOrNull() ?: p
        return when(p) { "/title" -> "这次要解决的问题"; "/context" -> "你的使用情况"; "/budget" -> "初始支出预算"; "/horizon" -> "未来比较时间"; "/strategies" -> "比较方案和使用过程"; "/parameters" -> "本次计算的通用参数"; "/needs" -> "需求和重要程度"; "/category" -> "物品类别和比较项目"; else -> listOfNotNull(dimension,labels[suffix] ?: suffix).joinToString(" · ") }
    }
    val labels=mapOf("weight" to "相对重要程度","frequency" to "使用频率","pain" to "问题影响程度","importance" to "任务重要程度","reliability" to "不能出问题的程度","initial" to "当前满足程度（0~1）","lambda" to "每年逐渐变差的速度","mu" to "每年固定减少的程度","reference" to "比较性能变化的固定单位","threshold" to "最低能接受的程度","thetaPlus" to "能感受到改善的起点","thetaMinus" to "能感受到变差的起点","kPlus" to "对改善的敏感程度","kMinus" to "对变差的敏感程度","functional" to "只考虑功能，最多愿付多少钱","actual" to "考虑喜欢后，最多愿付多少钱","gamma" to "喜欢带来的额外价值衰减速度","q" to "额外愿付金额降到多少时再考虑购买","time" to "发生时间（年）","amount" to "金额","value" to "本次采用的数值","selected" to "从区间明确选择的数值","old" to "当前物品参数","new" to "备选物品参数","saturation" to "继续提升已不太有用的程度","source" to "信息从哪里来","confidence" to "对信息有多确定","passed" to "是否满足必须条件","cashStep" to "按月或按年统计费用","primaryStageId" to "主要评估哪件物品","primaryUpgradeId" to "主要比较哪次升级")
    fun parameter(key: String) = mapOf("serviceThreshold" to "整体至少要满足到什么程度（0~1）","saturationAnchor" to "提升开始变得不太有用的程度（小于 1）","annualRate" to "未来金额的年度折算比例","w_time" to "一小时个人时间值多少钱")[key] ?: key
    fun error(error: Throwable): String {
        val s=error.message.orEmpty()
        return when {
            "ERR_PARAMETER_UNCONFIRMED" in s -> "还有参数没有确认，请到“校验与参数”逐项确认。"
            "ERR_PARAMETER_MISSING" in s -> "有参数只有范围，没有选择本次计算值。"
            "ERR_NEED" in s || "ZERO" in s -> "需求不能全部为零，请至少选一个重要的项目。"
            "ERR_STAGE_COVERAGE" in s -> "使用过程有时间空档或重叠，请检查阶段起止时间。"
            "ERR_CURRENCY" in s -> "金额币种不一致，请统一后再计算。"
            "ERR_PRIMARY" in s -> "请明确主要评估的物品，以及主要比较的升级。"
            "ERR_UNIT" in s -> "性能参数的单位不一致，请检查例如小时、GB、克。"
            "ERR_CORE_IDENTITY" in s -> "更换成另一件物品要新增阶段，不能当作维修。"
            "ERR_REPAIR_CASH_LINK" in s -> "维修需要关联同一时间的费用记录，请检查费用编号和发生时间。"
            "ERR_DUPLICATE_CASH" in s -> "同一笔费用不能重复记录，请检查记录编号及维修关联。"
            "ERR_DUPLICATE_SALVAGE" in s -> "评估结束的卖出收入只能记录一次。"
            "ERR_SALVAGE_TIME" in s -> "期末卖出收入应发生在比较时间结束时。"
            "ERR_CASH_SIGN" in s -> "支出应是负值，收入应是正值；普通费用页会自动处理符号。"
            "ERR_ASSET_CONTINUITY" in s -> "同一件物品的下一阶段，应接着上一阶段结束时的状态继续。"
            "ERR_REFERENCE_SCALE" in s -> "比较性能变化的固定单位必须大于零。"
            "ERR_TIME_RATE_PARAMETER" in s -> "请明确一小时个人时间值多少钱，并确认与时间费用的金额一致。"
            "ERR_EMOTION_PARAMETER" in s -> "请检查愿付金额、多久减半和冷静比例，金额不能为负。"
            "ERR_PREFERENCE_CONFIG" in s -> "偏好需要四项非负权重（不能全为零）和四个正的固定尺度。"
            "ERR_STRATEGY_SET" in s -> "至少需要两个不同方案，方案编号不能重复。"
            "ERR_BASELINE_MISSING" in s -> "请先选择一个仍然存在的参照方案。"
            "ERR_DIMENSION_MISMATCH" in s -> "各个方案的比较项目不一致，请补齐对应的项目。"
            "ERR_TECH_TIME" in s || "ERR_HORIZON" in s || "ERR_CASH_STEP" in s -> "比较时间、产品间隔和费用统计间隔都必须大于零。"
            "ERR_SIMULATION_TOO_LARGE" in s || "ERR_NUMERIC_OVERFLOW" in s -> "比较时间或参数过大，请缩短时间或使用较大的统计间隔。"
            "ERR_RATIO_QUALITY" in s || "ERR_RHO" in s -> "溢价比较的价格或质量基准不合法，请检查两代价格和比例质量值。"
            s.startsWith("ERR_") -> "输入有一项不合法，请在对应页面检查参数、单位和重复记录。"
            else -> s.take(500).ifBlank { "操作没有完成，请检查输入后重试。" }
        }
    }
    fun yearsToDays(gamma: Double): Double = ln(2.0)/gamma*365.25
}
