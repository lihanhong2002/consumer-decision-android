package com.sixmodel.core

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.security.MessageDigest

@Serializable data class Money(val amount: String = "0", val currency: String = "CNY") {
    init { require(currency.matches(Regex("[A-Z]{3}"))); BigDecimal(amount) }
    fun decimal() = BigDecimal(amount)
}
@Serializable enum class MissingState { UNKNOWN, NOT_APPLICABLE, NOT_COLLECTED, UNDEFINED_BY_THEORY }
@Serializable enum class Confidence { LOW, MEDIUM, MEDIUM_HIGH, HIGH }
@Serializable data class ParameterRecord(val id: String, val key: String, val scope: String = "USER_GLOBAL", val value: Double? = null, val lower: Double? = null, val upper: Double? = null, val upperExclusive: Boolean = false, val selected: Double? = null, val source: String = "MANUAL", val confidence: Confidence = Confidence.LOW, val confirmed: Boolean = false, val unit: String = "") {
    fun scalar(): Double { require(confirmed) { "ERR_PARAMETER_UNCONFIRMED: $key" }; val v = value ?: selected ?: error("ERR_PARAMETER_MISSING: $key"); require(v.isFinite()); require(lower == null || v >= lower); require(upper == null || if (upperExclusive) v < upper else v <= upper); return v }
}
@Serializable data class Dimension(val id: String, val name: String, val unit: String = "", val lowerBetter: Boolean = false, val reference: Double = 1.0, val critical: Boolean = false, val threshold: Double = 0.3, val thetaPlus: Double = 0.1, val thetaMinus: Double = 0.1, val kPlus: Double = 10.0, val kMinus: Double = 10.0, val source: String = "MANUAL", val confidence: Confidence = Confidence.LOW)
@Serializable data class Category(val id: String, val name: String, val dimensions: List<Dimension>)
@Serializable enum class NeedMethod { MANUAL_USER_SIDE, MULTIPLICATIVE, WEIGHTED_SUM }
@Serializable data class NeedInput(val dimensionId: String, val weight: Double = 1.0, val frequency: Double = 0.5, val pain: Double = 0.5, val importance: Double = 0.5, val reliability: Double = 0.5)
@Serializable data class NeedPoint(val time: Double = 0.0, val method: NeedMethod = NeedMethod.MANUAL_USER_SIDE, val inputs: List<NeedInput>, val coefficients: List<Double> = emptyList(), val coefficientsConfirmed: Boolean = false)
@Serializable data class NeedVector(val weights: Map<String, Double>)
@Serializable enum class Decay { EXPONENTIAL, LINEAR, MIXED }
@Serializable data class Utility(val dimensionId: String, val initial: Double = 1.0, val lambda: Double = 0.05, val mu: Double = 0.0, val decay: Decay = Decay.EXPONENTIAL, val source: String = "MANUAL", val confidence: Confidence = Confidence.LOW)
@Serializable data class Recovery(val id: String, val name: String, val ratios: Map<String, Double>, val changesCoreIdentity: Boolean = false, val overrideReason: String? = null, val source: String = "MANUAL", val confidence: Confidence = Confidence.LOW)
@Serializable data class RepairEvent(val time: Double, val recovery: Recovery, val cashFlowId: String? = null)
@Serializable enum class Action { CONTINUE_USE, REPAIR, PURCHASE, RENT, SUBSTITUTE, WAIT_THEN_PURCHASE, MULTI_STAGE }
@Serializable enum class Activation { LOGISTIC, INDICATOR }
@Serializable data class Upgrade(val id: String, val old: Map<String, Double>, val new: Map<String, Double>, val saturation: Map<String, Double>, val years: Double = 1.0, val oldPrice: Money = Money(), val newPrice: Money = Money(), val activation: Activation = Activation.INDICATOR, val units: Map<String, String> = emptyMap(), val ratioOldQuality: Double? = null, val ratioNewQuality: Double? = null, val source: String = "MANUAL", val confidence: Confidence = Confidence.LOW)
@Serializable data class Emotion(val functional: Money = Money(), val actual: Money = Money(), val gamma: Double = 8.32, val q: Double = 0.5, val source: String = "SPEC_INITIAL", val confidence: Confidence = Confidence.LOW)
@Serializable data class Stage(val id: String, val assetId: String, val name: String, val action: Action = Action.CONTINUE_USE, val start: Double, val end: Double, val utilities: List<Utility>, val allowedRecoveries: List<Recovery> = emptyList(), val repairs: List<RepairEvent> = emptyList(), val upgrade: Upgrade? = null, val emotion: Emotion = Emotion())
@Serializable enum class CashKind { INITIAL, RUN, MAINTENANCE, INDUCED, TIME, OTHER, INCOME, SALVAGE }
@Serializable data class CashFlow(val id: String, val time: Double, val money: Money, val kind: CashKind = CashKind.OTHER, val note: String = "", val probability: Double? = null, val burdenHours: Double? = null, val hourlyRate: Money? = null, val hourlyRateParameter: ParameterRecord? = null, val source: String = "MANUAL", val confidence: Confidence = Confidence.LOW)
@Serializable data class BooleanConstraint(val id: String, val type: String, val description: String, val passed: Boolean? = null)
@Serializable data class Strategy(val id: String, val name: String, val stages: List<Stage>, val primaryStageId: String, val primaryUpgradeId: String? = null, val flows: List<CashFlow> = emptyList(), val constraints: List<BooleanConstraint> = emptyList())
@Serializable data class Preference(val confirmed: Boolean = false, val weights: List<Double> = emptyList(), val scales: List<Double> = emptyList(), val source: String = "MANUAL", val version: String = "linear-1")
@Serializable data class DecisionInputSnapshot(val id: String, val title: String, val category: Category, val horizon: Double = 3.0, val cashStep: Double = 1.0/12, val currency: String = "CNY", val budget: Money? = null, val totalBudget: Money? = null, val baselineId: String, val needs: List<NeedPoint>, val parameters: List<ParameterRecord>, val strategies: List<Strategy>, val preference: Preference? = null, val userId: String = "local-user", val context: String = "", val example: Boolean = false)
@Serializable data class EngineConfig(val theoryVersion: String = "4.2.1", val specVersion: String = "1.0.0", val engineVersion: String = "0.1.0", val appVersion: String = "0.1.0", val schemaVersion: String = "1.0.0", val featureMapVersion: String = "psi-1", val decisionFunctionVersion: String = "linear-1", val projectionVersion: String = "path-projection-1", val numericEpsilon: Double = 1e-9, val paretoEpsilon: Double = 1e-9, val rhoEpsilon: Double = 1e-9, val lifeStep: Double = 1.0/12)
@Serializable data class EffectiveLifeValue(val value: Double? = null, val lowerBound: Double? = null, val status: String = "EXACT") {
    init { require(status in setOf("EXACT","RIGHT_CENSORED")); if(status=="EXACT") require(value!=null && value.isFinite() && value>=0 && lowerBound==null) else require(value==null && lowerBound!=null && lowerBound.isFinite() && lowerBound>=0) }
}
@Serializable data class RhoResult(val status: String, val value: Double? = null, val mode: String? = null, val reason: String? = null) {
    init { require(status in setOf("VALUE","NOT_APPLICABLE","NO_POSITIVE_RELEVANT_UPGRADE","INVALID_INPUT")); if(status=="VALUE") require(value!=null && value.isFinite() && mode in setOf("RATIO","RELEVANT")) else require(value==null && mode==null) }
}
@Serializable data class CalculationTrace(val model: String, val formula: String, val values: Map<String, String>, val warnings: List<String> = emptyList())
@Serializable data class ServicePoint(val time: Double, val utility: Double, val feasible: Boolean, val dimensions: Map<String, Double>)
@Serializable data class M1Result(val life: EffectiveLifeValue, val timeline: List<ServicePoint>, val trace: CalculationTrace)
@Serializable data class M2Result(val g: Map<String, Double>, val perceived: Map<String, Double>, val net: Double, val abs: Double, val gain: Double, val trace: CalculationTrace)
@Serializable data class M3Result(val wEmo: Money, val halfLife: Double, val coolDown: Double, val trace: CalculationTrace)
@Serializable data class M4Result(val gamma: Double, val relevant: Double, val contributions: Map<String, Double>, val trace: CalculationTrace)
@Serializable data class M6Result(val npv: Money, val delta: Money, val discounted: Map<String, Money>, val trace: CalculationTrace)
@Serializable data class StageResult(val stageId: String, val m1: M1Result, val m2: M2Result, val m3: M3Result, val m4: M4Result, val rho: RhoResult)
@Serializable data class ZVector(val life: EffectiveLifeValue, val iteration: Double, val emotion: Money, val alignment: Double, val rho: RhoResult, val deltaNpv: Money)
@Serializable data class StrategyMeta(val iAbs: Double, val iGain: Double, val gRel: Double, val need: NeedVector, val parameters: List<ParameterRecord>, val traces: List<CalculationTrace>, val warnings: List<String>)
@Serializable data class StrategyEvaluation(val id: String, val name: String, val primaryStageId: String, val z: ZVector, val meta: StrategyMeta, val y: List<Double>, val stages: List<StageResult>, val m6: M6Result, val violations: List<String>, val dominatedBy: List<String> = emptyList(), val score: Double? = null, val contributions: List<Double> = emptyList())
@Serializable data class DecisionEvaluation(val inputHash: String, val config: EngineConfig, val strategies: List<StrategyEvaluation>, val paretoIds: List<String>, val rankedIds: List<String>, val warnings: List<String>)
object SnapshotCodec {
    val json = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false; allowSpecialFloatingPointValues = false }
    fun canonical(element: JsonElement): JsonElement = when(element) { is JsonObject -> JsonObject(element.toSortedMap().mapValues { canonical(it.value) }); is JsonArray -> JsonArray(element.map(::canonical)); else -> element }
    inline fun <reified T> encode(value: T) = canonical(json.encodeToJsonElement(value)).toString()
    inline fun <reified T> decode(value: String): T = json.decodeFromString(value)
    fun hash(input: DecisionInputSnapshot) = MessageDigest.getInstance("SHA-256").digest(encode(input).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
