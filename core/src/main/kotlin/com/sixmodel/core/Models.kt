package com.sixmodel.core

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.*

object NeedGenerator {
    fun generate(point: NeedPoint, dimensions: List<Dimension>): NeedVector {
        require(point.inputs.map { it.dimensionId }.toSet() == dimensions.map { it.id }.toSet()) { "ERR_DIMENSION_MISMATCH" }
        require(point.inputs.size == dimensions.size)
        if (point.method != NeedMethod.MANUAL_USER_SIDE) { require(point.coefficientsConfirmed && point.coefficients.size == 4) { "ERR_PARAMETER_MISSING: need coefficients" }; require(point.coefficients.all { it.isFinite() && it >= 0 }) }
        val raw = point.inputs.associate { i ->
            val values = listOf(i.frequency, i.pain, i.importance, i.reliability)
            require(values.all { it.isFinite() && it in 0.0..1.0 }); require(i.weight.isFinite() && i.weight >= 0)
            i.dimensionId to when (point.method) {
                NeedMethod.MANUAL_USER_SIDE -> i.weight
                NeedMethod.MULTIPLICATIVE -> values.zip(point.coefficients).fold(1.0) { a, (v, e) -> a * v.pow(e) }
                NeedMethod.WEIGHTED_SUM -> values.zip(point.coefficients).sumOf { (v, w) -> v*w }
            }
        }
        val total = raw.values.sum(); require(total.isFinite() && total > 0) { "NEED_VECTOR_UNDEFINED" }
        return NeedVector(raw.mapValues { it.value/total })
    }
    fun at(needs: List<Pair<Double, NeedVector>>, time: Double) = needs.lastOrNull { it.first <= time + 1e-9 }?.second ?: error("ERR_NEED_STATE_MISSING")
}

object Model1 {
    fun utilityAt(stage: Stage, t: Double): Map<String, Double> {
        fun decay(u: Utility, dt: Double) = when(u.decay) { Decay.EXPONENTIAL -> exp(-u.lambda*dt); Decay.LINEAR -> max(0.0, 1-u.mu*dt); Decay.MIXED -> exp(-u.lambda*dt)*max(0.0,1-u.mu*dt) }
        return stage.utilities.associate { u ->
            var value = u.initial; var previous = 0.0
            for (repair in stage.repairs.sortedBy { it.time }.filter { it.time <= t }) {
                value *= decay(u, repair.time-previous)
                val q = repair.recovery.ratios[u.dimensionId] ?: 0.0
                value += q*(1-value); previous = repair.time
            }
            u.dimensionId to (value*decay(u,t-previous)).coerceIn(0.0,1.0)
        }
    }
    fun feasible(values: Map<String, Double>, need: NeedVector, dimensions: List<Dimension>, threshold: Double): Boolean = need.weights.entries.sumOf { (id,w) -> w*values.getValue(id) } >= threshold && dimensions.filter { it.critical }.all { values.getValue(it.id) >= it.threshold }
    fun evaluate(stage: Stage, dimensions: List<Dimension>, needs: List<Pair<Double,NeedVector>>, threshold: Double, horizon: Double, config: EngineConfig): M1Result {
        val timeline = mutableListOf<ServicePoint>(); var failure: Double? = null
        val times = (0..floor(horizon/config.lifeStep).toInt()).map { it*config.lifeStep }.plus(horizon).plus(needs.map { it.first-stage.start }.filter { it in 0.0..horizon }).plus(stage.repairs.map { it.time }.filter { it in 0.0..horizon }).distinct().sorted()
        require(times.size <= 100000) { "ERR_SIMULATION_TOO_LARGE" }
        for (t in times) {
            val values = utilityAt(stage,t); val need = NeedGenerator.at(needs,stage.start+t)
            val base = feasible(values,need,dimensions,threshold)
            val recovered = stage.allowedRecoveries.filter { !it.changesCoreIdentity }.any { action -> feasible(values.mapValues { (id,v) -> v+(action.ratios[id] ?: 0.0)*(1-v) },need,dimensions,threshold) }
            timeline += ServicePoint(t,need.weights.entries.sumOf { (id,w) -> w*values.getValue(id) },base,values)
            if (!base && !recovered && failure == null) failure = t
        }
        val life = failure?.let { EffectiveLifeValue(value=it) } ?: EffectiveLifeValue(lowerBound=horizon,status="RIGHT_CENSORED")
        return M1Result(life,timeline,CalculationTrace("M1","V4.2.1.M1.discrete-inf",mapOf("step" to config.lifeStep.toString(),"threshold" to threshold.toString(),"horizon" to horizon.toString(),"utilities" to stage.utilities.joinToString { "${it.dimensionId}: u0=${it.initial}, lambda=${it.lambda}, mu=${it.mu}, decay=${it.decay}, source=${it.source}, confidence=${it.confidence}" },"recoveries" to stage.allowedRecoveries.joinToString { "${it.id}: ${it.ratios}, source=${it.source}, confidence=${it.confidence}" },"identityExcluded" to stage.allowedRecoveries.filter { it.changesCoreIdentity }.joinToString { it.id }),listOf("ENGINEERING_EXTENSION: initial normalized utility and repair-state decay") + if(failure == null) listOf("WARN_T_EFF_RIGHT_CENSORED") else emptyList()))
    }
}
object Model2 {
    fun evaluate(upgrade: Upgrade?, dimensions: List<Dimension>, saturationAnchor: Double): M2Result {
        if (upgrade == null) return M2Result(dimensions.associate { it.id to 0.0 },dimensions.associate { it.id to 0.0 },0.0,0.0,0.0,CalculationTrace("M2","no-technical-change",emptyMap()))
        require(upgrade.years.isFinite() && upgrade.years > 0) { "ERR_TECH_TIME_NONPOSITIVE" }; require(saturationAnchor in 0.0..<1.0)
        val ids = dimensions.map { it.id }.toSet(); require(upgrade.old.keys == ids && upgrade.new.keys == ids && upgrade.saturation.keys == ids) { "ERR_DIMENSION_MISMATCH" }
        val g = linkedMapOf<String,Double>(); val perceived = linkedMapOf<String,Double>(); val trace = linkedMapOf<String,String>()
        for(d in dimensions) {
            require(d.reference.isFinite() && d.reference > 0) { "ERR_REFERENCE_SCALE_NONPOSITIVE" }
            require(upgrade.units[d.id] == d.unit) { "ERR_UNIT_MISMATCH: ${d.id}" }
            val old = upgrade.old.getValue(d.id); val new = upgrade.new.getValue(d.id); require(old.isFinite() && new.isFinite())
            val change = ((new-old)*(if(d.lowerBetter) -1 else 1)/d.reference).coerceIn(-1.0,1.0)
            val theta = if(change >= 0) d.thetaPlus else d.thetaMinus; val k = if(change >= 0) d.kPlus else d.kMinus
            val activation = if(upgrade.activation == Activation.INDICATOR) { if(abs(change) >= theta) 1.0 else 0.0 } else 1/(1+exp(-k*(abs(change)-theta)))
            val s = upgrade.saturation.getValue(d.id); require(s.isFinite() && s in 0.0..1.0)
            val sat = if(change >= 0 && s > saturationAnchor) (1-s)/(1-saturationAnchor) else 1.0
            g[d.id] = change; perceived[d.id] = change*activation*sat
            trace[d.id] = "old=$old; new=$new; R=${d.reference}; g=$change; activation=$activation; saturation=$sat; perceived=${perceived[d.id]}; source=${d.source}; confidence=${d.confidence}"
        }
        val denominator = dimensions.size*upgrade.years
        return M2Result(g,perceived,perceived.values.sum()/denominator,perceived.values.sumOf { abs(it) }/denominator,perceived.values.sumOf { max(it,0.0) }/denominator,CalculationTrace("M2","V4.2.1.M2.signed-perception",trace + mapOf("activationMode" to upgrade.activation.name,"deltaYears" to upgrade.years.toString(),"source" to upgrade.source,"confidence" to upgrade.confidence.name)))
    }
}
object Model3 {
    fun evaluate(input: Emotion, currency: String): M3Result {
        require(input.actual.currency == currency && input.functional.currency == currency) { "ERR_CURRENCY_MISMATCH" }
        require(input.actual.decimal().signum() >= 0 && input.functional.decimal().signum() >= 0)
        require(input.gamma.isFinite() && input.gamma > 0 && input.q > 0 && input.q < 1) { "ERR_EMOTION_PARAMETER" }
        val w = (input.actual.decimal()-input.functional.decimal()).max(BigDecimal.ZERO)
        return M3Result(Money(w.toPlainString(),currency),ln(2.0)/input.gamma,-ln(input.q)/input.gamma,CalculationTrace("M3","V4.2.1.M3.WTP_DRIVEN",mapOf("functionalWtp" to input.functional.amount,"actualWtp" to input.actual.amount,"gamma" to input.gamma.toString(),"q" to input.q.toString(),"source" to input.source,"confidence" to input.confidence.name)))
    }
}
object Model4 {
    fun evaluate(need: NeedVector, technical: Map<String,Double>): M4Result {
        require(need.weights.keys == technical.keys) { "ERR_DIMENSION_MISMATCH" }
        val contributions = need.weights.mapValues { (id,w) -> w*technical.getValue(id) }; val relevant = contributions.values.sum()
        val norm = sqrt(need.weights.values.sumOf { it*it })*sqrt(technical.values.sumOf { it*it })
        return M4Result(if(norm == 0.0) 0.0 else (relevant/norm).coerceIn(-1.0,1.0),relevant,contributions,CalculationTrace("M4","V4.2.1.M4.cosine-dot",contributions.mapValues { it.value.toString() },if(relevant < 0) listOf("WARN_NEGATIVE_RELEVANT_GAIN") else emptyList()))
    }
}
object Model5 {
    fun evaluate(upgrade: Upgrade?, relevant: Double, epsilon: Double): RhoResult {
        if(upgrade == null) return RhoResult("NOT_APPLICABLE",reason="非升级策略")
        if(upgrade.oldPrice.currency != upgrade.newPrice.currency) return RhoResult("INVALID_INPUT",reason="ERR_CURRENCY_MISMATCH")
        val old = upgrade.oldPrice.decimal(); val delta = upgrade.newPrice.decimal()-old
        if(old.signum() <= 0) return RhoResult("INVALID_INPUT",reason="ERR_RHO_INVALID_BASELINE")
        if(delta.signum() <= 0) return RhoResult("NOT_APPLICABLE",reason="工程规则：仅比较正增量价格")
        if(upgrade.ratioOldQuality != null || upgrade.ratioNewQuality != null) {
            val q = upgrade.ratioOldQuality; val next = upgrade.ratioNewQuality
            if(q == null || next == null || !q.isFinite() || !next.isFinite() || q <= 0 || next <= q) return RhoResult("INVALID_INPUT",reason="ERR_RATIO_QUALITY")
            return RhoResult("VALUE",delta.divide(old,MathContext.DECIMAL128).toDouble()/((next-q)/q),"RATIO")
        }
        if(relevant <= 0) return RhoResult("NO_POSITIVE_RELEVANT_UPGRADE")
        return RhoResult("VALUE",delta.divide(old,MathContext.DECIMAL128).toDouble()/max(relevant,epsilon),"RELEVANT")
    }
}
object Model6 {
    private val mc = MathContext.DECIMAL128
    fun amount(flow: CashFlow): BigDecimal = when(flow.kind) {
        CashKind.INDUCED -> { val p = flow.probability ?: error("ERR_PARAMETER_MISSING: probability"); require(p.isFinite() && p in 0.0..1.0); flow.money.decimal()*BigDecimal.valueOf(p) }
        CashKind.TIME -> { val h = flow.burdenHours ?: error("ERR_PARAMETER_MISSING: burdenHours"); require(h.isFinite() && h >= 0); val rate = flow.hourlyRate ?: error("ERR_PARAMETER_MISSING: hourlyRate"); val p = flow.hourlyRateParameter ?: error("ERR_PARAMETER_MISSING: w_time"); require(p.key=="w_time" && p.scalar() >= 0 && BigDecimal.valueOf(p.scalar()).compareTo(rate.decimal())==0) { "ERR_TIME_RATE_PARAMETER" }; require(rate.currency == flow.money.currency && rate.decimal().signum() >= 0); -(BigDecimal.valueOf(h)*rate.decimal()) }
        else -> flow.money.decimal()
    }
    fun evaluate(flows: List<CashFlow>, horizon: Double, step: Double, annualRate: Double, currency: String, baseline: BigDecimal = BigDecimal.ZERO): M6Result {
        require(horizon.isFinite() && horizon > 0 && step.isFinite() && step > 0 && annualRate.isFinite() && annualRate > -1)
        val n = ceil(horizon/step).toInt(); require(n in 1..100000) { "ERR_SIMULATION_TOO_LARGE" }
        val stepRate = (1+annualRate).pow(step)-1
        require(stepRate.isFinite() && stepRate > -1) { "ERR_NUMERIC_OVERFLOW: discount rate" }
        val discounted = linkedMapOf<String,Money>(); val flowTrace = linkedMapOf<String,String>(); var total = BigDecimal.ZERO
        require(flows.map { it.id }.distinct().size == flows.size) { "ERR_DUPLICATE_CASHFLOW" }
        require(flows.count { it.kind == CashKind.SALVAGE } <= 1) { "ERR_DUPLICATE_SALVAGE" }
        for(flow in flows) {
            require(flow.money.currency == currency) { "ERR_CURRENCY_MISMATCH" }; require(flow.time.isFinite() && flow.time in 0.0..horizon)
            require(flow.kind != CashKind.SALVAGE || abs(flow.time-horizon) < 1e-9) { "ERR_SALVAGE_TIME" }
            val index = if(flow.kind == CashKind.SALVAGE) n else if(flow.time == 0.0) 0 else ceil(flow.time/step-1e-10).toInt().coerceIn(1,n)
            val factor = (1+stepRate).pow(index); require(factor.isFinite() && factor > 0) { "ERR_NUMERIC_OVERFLOW: discount factor" }
            val cash = amount(flow)
            if(flow.kind in listOf(CashKind.INITIAL,CashKind.RUN,CashKind.MAINTENANCE,CashKind.INDUCED,CashKind.TIME)) require(cash.signum() <= 0) { "ERR_CASH_SIGN: cost must be nonpositive" }
            if(flow.kind in listOf(CashKind.INCOME,CashKind.SALVAGE)) require(cash.signum() >= 0) { "ERR_CASH_SIGN: income must be nonnegative" }
            val pv = cash.divide(BigDecimal.valueOf(factor),mc)
            total += pv; discounted[flow.id] = Money(pv.toPlainString(),currency)
            flowTrace[flow.id] = "time=${flow.time}; kind=${flow.kind}; amount=${cash.toPlainString()}; index=$index; factor=$factor; presentValue=${pv.toPlainString()}; source=${flow.source}; confidence=${flow.confidence}; probability=${flow.probability}; hours=${flow.burdenHours}; timeRateSource=${flow.hourlyRateParameter?.source}"
        }
        return M6Result(Money(total.toPlainString(),currency),Money((total-baseline).toPlainString(),currency),discounted,CalculationTrace("M6","V4.2.1.M6.discrete-NPV",flowTrace + mapOf("N" to n.toString(),"stepRate" to stepRate.toString(),"annualRate" to annualRate.toString(),"baselineNpv" to baseline.toPlainString()),listOf("仅记录实际成本/收入；时间与诱导成本需显式来源；避免重复记账")))
    }
}
