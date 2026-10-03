package com.sixmodel.core

import java.math.BigDecimal
import kotlin.math.*

object ParetoEngine {
    private fun lifeAtLeast(a: EffectiveLifeValue, b: EffectiveLifeValue, eps: Double): Boolean {
        val aMin = a.value ?: a.lowerBound!!
        val bMax = b.value ?: return false
        return aMin + eps >= bMax
    }
    fun dominates(a: StrategyEvaluation, b: StrategyEvaluation, eps: Double): Boolean {
        if(!lifeAtLeast(a.z.life,b.z.life,eps)) return false
        val av = listOf(a.z.iteration,a.z.alignment)
        val bv = listOf(b.z.iteration,b.z.alignment)
        val moneyDiff = listOf(a.z.emotion.decimal()-b.z.emotion.decimal(),a.z.deltaNpv.decimal()-b.z.deltaNpv.decimal())
        val moneyEpsilon = BigDecimal.valueOf(eps)
        val lifeStrict = (a.z.life.value ?: a.z.life.lowerBound!!) > (b.z.life.value ?: Double.POSITIVE_INFINITY)+eps
        return av.zip(bv).all { (x,y) -> x+eps >= y } && moneyDiff.all { it >= -moneyEpsilon } && (lifeStrict || av.zip(bv).any { (x,y) -> x > y+eps } || moneyDiff.any { it > moneyEpsilon })
    }
    fun filter(evaluations: List<StrategyEvaluation>, epsilon: Double): List<StrategyEvaluation> = evaluations.map { b -> b.copy(dominatedBy=if(b.violations.isNotEmpty()) emptyList() else evaluations.filter { a -> a.id != b.id && a.violations.isEmpty() && dominates(a,b,epsilon) }.map { it.id }) }
}

class DecisionEngine {
    fun validate(input: DecisionInputSnapshot, config: EngineConfig = EngineConfig()) {
        require(config.theoryVersion=="4.2.1" && config.specVersion=="1.0.0" && config.engineVersion=="0.1.0" && config.schemaVersion=="1.0.0" && config.featureMapVersion=="psi-1" && config.decisionFunctionVersion=="linear-1" && config.projectionVersion=="path-projection-1") { "ERR_UNSUPPORTED_VERSION" }
        require(input.horizon.isFinite() && input.horizon > 0) { "ERR_HORIZON_NONPOSITIVE" }
        require(input.cashStep.isFinite() && input.cashStep > 0) { "ERR_CASH_STEP_NONPOSITIVE" }
        require(config.lifeStep.isFinite() && config.lifeStep > 0 && listOf(config.numericEpsilon,config.paretoEpsilon,config.rhoEpsilon).all { it.isFinite() && it > 0 && it < 1 })
        require(input.horizon/config.lifeStep <= 100000 && input.horizon/input.cashStep <= 100000) { "ERR_SIMULATION_TOO_LARGE" }
        require(input.strategies.size >= 2 && input.strategies.map { it.id }.distinct().size == input.strategies.size) { "ERR_STRATEGY_SET" }
        require(input.strategies.any { it.id == input.baselineId }) { "ERR_BASELINE_MISSING" }
        val dims = input.category.dimensions; require(dims.isNotEmpty() && dims.map { it.id }.distinct().size == dims.size)
        dims.forEach { require(it.reference.isFinite() && it.reference > 0 && it.threshold in 0.0..1.0 && it.thetaPlus in 0.0..1.0 && it.thetaMinus in 0.0..1.0 && it.kPlus.isFinite() && it.kMinus.isFinite() && it.kPlus > 0 && it.kMinus > 0) }
        require(input.needs.isNotEmpty() && input.needs.first().time == 0.0 && input.needs.zipWithNext().all { it.first.time < it.second.time } && input.needs.all { it.time.isFinite() && it.time in 0.0..input.horizon })
        input.needs.forEach { NeedGenerator.generate(it,dims) }
        require(input.parameters.map { it.key }.distinct().size == input.parameters.size) { "ERR_AMBIGUOUS_PARAMETER" }
        val params = input.parameters.associateBy { it.key }; params.getValue("serviceThreshold").scalar().also { require(it in 0.0..1.0) }; params.getValue("saturationAnchor").scalar().also { require(it >= 0 && it < 1) }; params.getValue("annualRate").scalar().also { require(it > -1) }
        listOfNotNull(input.budget,input.totalBudget).forEach { require(it.currency == input.currency && it.decimal().signum() >= 0) }
        for(s in input.strategies) {
            require(s.stages.isNotEmpty() && s.stages.map { it.id }.distinct().size == s.stages.size)
            val sorted = s.stages.sortedBy { it.start }; require(abs(sorted.first().start) <= config.numericEpsilon && abs(sorted.last().end-input.horizon) <= config.numericEpsilon && sorted.zipWithNext().all { abs(it.first.end-it.second.start) <= config.numericEpsilon }) { "ERR_STAGE_COVERAGE" }
            require(s.stages.any { it.id == s.primaryStageId }) { "ERR_PRIMARY_STAGE_MISSING" }
            require(s.primaryUpgradeId == null || s.stages.any { it.upgrade?.id == s.primaryUpgradeId }) { "ERR_PRIMARY_UPGRADE_MISSING" }
            require(s.stages.none { it.upgrade != null } || s.primaryUpgradeId != null) { "ERR_PRIMARY_UPGRADE_MISSING: 请显式选择主升级对照" }
            require(s.stages.mapNotNull { it.upgrade?.id }.distinct().size == s.stages.mapNotNull { it.upgrade?.id }.size) { "ERR_DUPLICATE_UPGRADE" }
            sorted.zipWithNext().forEach { (previous,next) -> if(previous.assetId==next.assetId) { val endState=Model1.utilityAt(previous,previous.end-previous.start); require(next.utilities.all { abs(it.initial-endState.getValue(it.dimensionId)) <= config.numericEpsilon }) { "ERR_ASSET_CONTINUITY: 同资产不能静默重置效用" } } }
            for(stage in s.stages) {
                require(stage.start.isFinite() && stage.end.isFinite() && stage.start >= 0 && stage.end <= input.horizon && stage.start < stage.end)
                require(stage.utilities.map { it.dimensionId }.toSet() == dims.map { it.id }.toSet() && stage.utilities.size == dims.size) { "ERR_DIMENSION_MISMATCH" }
                stage.utilities.forEach { require(it.initial.isFinite() && it.initial in 0.0..1.0 && it.lambda.isFinite() && it.lambda >= 0 && it.mu.isFinite() && it.mu >= 0) }
                (stage.allowedRecoveries + stage.repairs.map { it.recovery }).forEach { r -> require(r.ratios.keys.all { id -> dims.any { it.id == id } }); require(r.ratios.values.all { it.isFinite() && it in 0.0..1.0 }) }
                stage.repairs.forEach { r -> require(r.time.isFinite() && r.time in 0.0..(stage.end-stage.start)); require(!r.recovery.changesCoreIdentity) { "ERR_CORE_IDENTITY_VIOLATION" }; if(r.cashFlowId != null) require(s.flows.any { it.id == r.cashFlowId && abs(it.time-(stage.start+r.time)) <= config.numericEpsilon }) { "ERR_REPAIR_CASH_LINK" } }
                require(stage.repairs.mapNotNull { it.cashFlowId }.distinct().size == stage.repairs.mapNotNull { it.cashFlowId }.size) { "ERR_DUPLICATE_CASH_LINK" }
                stage.upgrade?.let { require(it.oldPrice.currency == input.currency && it.newPrice.currency == input.currency) { "ERR_CURRENCY_MISMATCH" } }
            }
            require(s.constraints.map { it.id }.distinct().size == s.constraints.size)
        }
        input.preference?.let { p -> if(p.confirmed) require(p.weights.size == 4 && p.scales.size == 4 && p.weights.all { it.isFinite() && it >= 0 } && p.weights.sum() > 0 && p.scales.all { it.isFinite() && it > 0 }) { "ERR_PREFERENCE_CONFIG" } }
    }

    fun evaluate(input: DecisionInputSnapshot, config: EngineConfig = EngineConfig()): DecisionEvaluation {
        validate(input,config)
        val dims = input.category.dimensions; val needs = input.needs.map { it.time to NeedGenerator.generate(it,dims) }
        val parameters = input.parameters.associateBy { it.key }; val threshold = parameters.getValue("serviceThreshold").scalar(); val saturation = parameters.getValue("saturationAnchor").scalar(); val rate = parameters.getValue("annualRate").scalar()
        val cash = input.strategies.associate { it.id to Model6.evaluate(it.flows,input.horizon,input.cashStep,rate,input.currency) }
        val baseline = cash.getValue(input.baselineId).npv.decimal()
        val results = input.strategies.map { strategy ->
            val stages = strategy.stages.map { stage ->
                val need = NeedGenerator.at(needs,stage.start); val m2 = Model2.evaluate(stage.upgrade,dims,saturation); val m4 = Model4.evaluate(need,m2.perceived)
                val rho = Model5.evaluate(stage.upgrade,m4.relevant,config.rhoEpsilon); require(rho.status != "INVALID_INPUT") { rho.reason ?: "ERR_RHO_INVALID" }
                StageResult(stage.id,Model1.evaluate(stage,dims,needs,threshold,input.horizon,config),m2,Model3.evaluate(stage.emotion,input.currency),m4,rho)
            }
            val primary = stages.first { it.stageId == strategy.primaryStageId }
            val technical = strategy.primaryUpgradeId?.let { id -> stages.first { r -> strategy.stages.first { it.id == r.stageId }.upgrade?.id == id } }
            val m6raw = cash.getValue(strategy.id); val m6 = m6raw.copy(delta=Money((m6raw.npv.decimal()-baseline).toPlainString(),input.currency),trace=m6raw.trace.copy(values=m6raw.trace.values + mapOf("baselineId" to input.baselineId,"baselineNpv" to baseline.toPlainString())))
            val violations = mutableListOf<String>()
            val initial = strategy.flows.filter { it.time == 0.0 }.sumOf { Model6.amount(it).min(BigDecimal.ZERO).negate() }
            val total = strategy.flows.sumOf { Model6.amount(it).min(BigDecimal.ZERO).negate() }
            if(input.budget != null && initial > input.budget.decimal()) violations += "BUDGET: 初始支出超预算"
            if(input.totalBudget != null && total > input.totalBudget.decimal()) violations += "BUDGET: 全期支出超预算"
            strategy.constraints.filter { it.passed != true }.forEach { violations += "${it.type}: ${it.description} (${if(it.passed == null) "未确认" else "不满足"})" }
            strategy.stages.forEach { stage ->
                val duration = stage.end-stage.start
                val times = (0..floor(duration/config.lifeStep).toInt()).map { it*config.lifeStep }.plus(stage.repairs.map { it.time }).plus(needs.map { it.first-stage.start }.filter { it in 0.0..duration }).plus(duration).distinct().sorted()
                val failed = times.firstOrNull { t -> !Model1.feasible(Model1.utilityAt(stage,t),NeedGenerator.at(needs,stage.start+t),dims,threshold) }
                if(failed != null) violations += "SERVICE_FEASIBILITY: ${stage.name} 在 ${stage.start+failed} 年服务不满足要求"
            }
            val m2 = technical?.m2 ?: Model2.evaluate(null,dims,saturation); val m4 = technical?.m4 ?: Model4.evaluate(NeedGenerator.at(needs,strategy.stages.first { it.id == primary.stageId }.start),m2.perceived)
            val z = ZVector(primary.m1.life,m2.net,primary.m3.wEmo,m4.gamma,technical?.rho ?: RhoResult("NOT_APPLICABLE",reason="未指定升级对照"),m6.delta)
            val warnings = listOf("WARN_ENGINEERING_EXTENSION_ACTIVE: ${config.projectionVersion}; 主资产/升级对照不代表全路径效用") + stages.flatMap { it.m1.trace.warnings + it.m4.trace.warnings } + input.parameters.filter { it.confidence == Confidence.LOW }.map { "WARN_LOW_CONFIDENCE_PARAMETER: ${it.key}" }
            val traces = stages.flatMap { listOf(it.m1.trace,it.m2.trace,it.m3.trace,it.m4.trace,CalculationTrace("M5","V4.2.1.M5",mapOf("status" to it.rho.status,"value" to it.rho.value.toString(),"mode" to it.rho.mode.toString(),"epsilon" to config.rhoEpsilon.toString(),"policy" to "positive-delta-price-only"))) } + m6.trace
            val y = listOf(z.life.value ?: z.life.lowerBound!!,z.emotion.decimal().toDouble(),m4.relevant,z.deltaNpv.decimal().toDouble())
            StrategyEvaluation(strategy.id,strategy.name,strategy.primaryStageId,z,StrategyMeta(m2.abs,m2.gain,m4.relevant,NeedGenerator.at(needs,strategy.stages.first { it.id == primary.stageId }.start),input.parameters,traces,warnings),y,stages,m6,violations)
        }
        val filtered = ParetoEngine.filter(results,config.paretoEpsilon); val front = filtered.filter { it.violations.isEmpty() && it.dominatedBy.isEmpty() }.map { it.id }
        val preference = input.preference?.takeIf { it.confirmed }
        val scored = filtered.map { e -> if(preference != null && e.id in front) { val c = e.y.indices.map { e.y[it]/preference.scales[it]*preference.weights[it] }; e.copy(score=c.sum(),contributions=c) } else e }
        val ranked = if(preference == null) emptyList() else scored.filter { it.score != null }.sortedWith(compareByDescending<StrategyEvaluation> { it.score }.thenBy { it.id }).map { it.id }
        return DecisionEvaluation(SnapshotCodec.hash(input),config,scored,front,ranked,if(preference == null) listOf("尚未配置最终偏好函数") else listOf("线性排序使用右删失寿命下界；不可解读为精确寿命排名"))
    }
}
