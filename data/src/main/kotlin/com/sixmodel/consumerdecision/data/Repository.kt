package com.sixmodel.consumerdecision.data

import androidx.room.withTransaction
import com.sixmodel.core.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable data class UserBackup(val id: String, val name: String)
@Serializable data class CategoryBackup(val id: String, val name: String, val json: String)
@Serializable data class DimensionBackup(val id: String, val categoryId: String, val json: String)
@Serializable data class ParameterBackup(val id: String, val key: String, val json: String)
@Serializable data class DecisionBackup(val id: String, val title: String, val userId: String, val categoryId: String, val updatedAt: Long, val example: Boolean, val json: String)
@Serializable data class StrategyBackup(val id: String, val caseId: String, val name: String, val json: String)
@Serializable data class EvaluationBackup(val id: String, val caseId: String, val createdAt: Long, val inputHash: String, val inputJson: String, val resultJson: String)
@Serializable data class ChoiceBackup(val id: String, val caseId: String, val evaluationId: String, val strategyId: String, val createdAt: Long, val reason: String)
@Serializable data class IntakeBackup(val decisionId: String, val json: String)
@Serializable data class AssistanceBackup(val evaluationId: String, val json: String)
@Serializable data class AssetBackup(val id: String, val name: String, val updatedAt: Long, val json: String)
@Serializable data class ProfileBackup(val id: String, val json: String)
@Serializable data class ExplanationBackup(val id: String, val evaluationId: String, val json: String)
@Serializable data class Backup(val schemaVersion: String = "2.0.0", val theoryVersion: String = "4.2.1", val exportedAt: Long, val users: List<UserBackup>, val categories: List<CategoryBackup>, val dimensions: List<DimensionBackup>, val parameters: List<ParameterBackup>, val decisions: List<DecisionBackup>, val strategies: List<StrategyBackup>, val evaluations: List<EvaluationBackup>, val choices: List<ChoiceBackup>, val intakes: List<IntakeBackup> = emptyList(), val assistance: List<AssistanceBackup> = emptyList(), val assets: List<AssetBackup> = emptyList(), val profiles: List<ProfileBackup> = emptyList(), val explanations: List<ExplanationBackup> = emptyList())

class DecisionRepository(private val db: AppDatabase) {
    private val dao = db.dao()
    val decisions = dao.observeDecisions(); val evaluations = dao.observeEvaluations(); val choices = dao.observeChoices()
    val assets = dao.observeAssets()
    suspend fun load(id: String): DecisionInputSnapshot = SnapshotCodec.decode(dao.decision(id)?.json ?: error("决策不存在"))
    suspend fun intake(id: String): IntakeSession? = dao.intake(id)?.let { SnapshotCodec.decode<IntakeSession>(it.json) }
    suspend fun saveWorkspace(input: DecisionInputSnapshot,session: IntakeSession) = db.withTransaction {
        require(session.decisionId==input.id);session.validate();saveDraft(input)
        dao.saveIntake(IntakeRow(input.id,SnapshotCodec.encode(session)))
    }
    suspend fun saveAsset(asset: AssetProfile) { require(asset.schemaVersion=="1" && asset.name.isNotBlank() && asset.category.dimensions.map {it.id}.toSet()==asset.stage.utilities.map {it.dimensionId}.toSet());NeedGenerator.generate(asset.need,asset.category.dimensions);asset.parameters.forEach {if(it.confirmed)it.scalar()};dao.saveAsset(AssetRow(asset.id,asset.name,asset.updatedAt,SnapshotCodec.encode(asset))) }
    suspend fun saveDefaults(defaults: ReusableDefaults) { require(defaults.schemaVersion=="1" && defaults.horizon.isFinite() && defaults.horizon>0 && defaults.cashStep.isFinite() && defaults.cashStep>0 && defaults.currency.matches(Regex("[A-Z]{3}")));listOfNotNull(defaults.budget,defaults.totalBudget).forEach {require(it.currency==defaults.currency && it.decimal().signum()>=0)};defaults.parameters.forEach {if(it.confirmed)it.scalar()};dao.saveProfile(ProfileRow("local-defaults",SnapshotCodec.encode(defaults))) }
    suspend fun defaults(): ReusableDefaults? = dao.profile("local-defaults")?.let { SnapshotCodec.decode<ReusableDefaults>(it.json) }
    suspend fun explanation(id: String): AiExplanation? = dao.explanation(id)?.let {SnapshotCodec.decode<AiExplanation>(it.json)}
    suspend fun saveExplanation(value: AiExplanation) {require(dao.evaluation(value.evaluationId)!=null && value.schemaVersion=="1" && value.text.length in 1..5000);dao.insertExplanations(listOf(ExplanationRow(value.id,value.evaluationId,SnapshotCodec.encode(value))))}
    suspend fun saveDraft(input: DecisionInputSnapshot) = db.withTransaction {
        val json=SnapshotCodec.encode(input)
        if(dao.decision(input.id)?.json==json) return@withTransaction
        dao.insertUsers(listOf(UserRow(input.userId,"本地用户")))
        dao.saveCategory(CategoryRow(input.category.id,input.category.name,SnapshotCodec.encode(input.category)))
        input.category.dimensions.forEach { dao.saveDimension(DimensionRow("${input.category.id}:${it.id}",input.category.id,SnapshotCodec.encode(it))) }
        input.parameters.forEach { dao.saveParameter(ParameterRow("${input.id}:${it.id}",it.key,SnapshotCodec.encode(it))) }
        dao.saveDecision(DecisionRow(input.id,input.title,input.userId,input.category.id,System.currentTimeMillis(),input.example,json))
        dao.clearDraftStrategies(input.id)
        input.strategies.forEach { dao.saveStrategy(StrategyRow("${input.id}:${it.id}",input.id,it.name,SnapshotCodec.encode(it))) }
    }
    suspend fun calculate(input: DecisionInputSnapshot,session: IntakeSession? = null): EvaluationRow {
        session?.let { require(it.decisionId==input.id && it.pending==null && it.unknownPaths.isEmpty() && it.reviewedHash==SnapshotCodec.hash(input)) { "请先确认输入摘要并补齐信息缺口" };it.validate() }
        val result = DecisionEngine().evaluate(input,EngineConfig(appVersion="0.2.0"))
        val row = EvaluationRow(UUID.randomUUID().toString(),input.id,System.currentTimeMillis(),result.inputHash,SnapshotCodec.encode(input),SnapshotCodec.encode(result))
        db.withTransaction { if(session==null) saveDraft(input) else saveWorkspace(input,session); dao.insertEvaluations(listOf(row));if(session!=null) dao.insertAssistance(listOf(AssistanceRow(row.id,SnapshotCodec.encode(session)))) }
        return row
    }
    suspend fun choose(evaluationId: String, strategyId: String, reason: String) = db.withTransaction {
        val e = dao.evaluation(evaluationId) ?: error("计算记录不存在")
        val result: DecisionEvaluation = SnapshotCodec.decode(e.resultJson)
        require(result.strategies.any { it.id == strategyId })
        dao.insertChoices(listOf(ChoiceRow(UUID.randomUUID().toString(),e.caseId,e.id,strategyId,System.currentTimeMillis(),reason)))
    }
    suspend fun export(): String = db.withTransaction { SnapshotCodec.encode(backup().copy(explanations=dao.explanations().map {ExplanationBackup(it.id,it.evaluationId,it.json)})) }
    private suspend fun backup() = Backup(exportedAt=System.currentTimeMillis(),users=dao.users().map { UserBackup(it.id,it.name) },categories=dao.categories().map { CategoryBackup(it.id,it.name,it.json) },dimensions=dao.dimensions().map { DimensionBackup(it.id,it.categoryId,it.json) },parameters=dao.parameters().map { ParameterBackup(it.id,it.key,it.json) },decisions=dao.decisions().map { DecisionBackup(it.id,it.title,it.userId,it.categoryId,it.updatedAt,it.example,it.json) },strategies=dao.strategies().map { StrategyBackup(it.id,it.caseId,it.name,it.json) },evaluations=dao.evaluations().map { EvaluationBackup(it.id,it.caseId,it.createdAt,it.inputHash,it.inputJson,it.resultJson) },choices=dao.choices().map { ChoiceBackup(it.id,it.caseId,it.evaluationId,it.strategyId,it.createdAt,it.reason) },intakes=dao.intakes().map { IntakeBackup(it.decisionId,it.json) },assistance=dao.assistanceSnapshots().map { AssistanceBackup(it.evaluationId,it.json) },assets=dao.assets().map { AssetBackup(it.id,it.name,it.updatedAt,it.json) },profiles=dao.profiles().map { ProfileBackup(it.id,it.json) })
    suspend fun importBackup(text: String): String {
        val b: Backup = SnapshotCodec.decode(text)
        require(b.schemaVersion in setOf("1.0.0","2.0.0") && b.theoryVersion == "4.2.1") { "不支持的 schema/theory 版本；未写入任何数据" }
        if(b.schemaVersion=="1.0.0") require(b.intakes.isEmpty() && b.assistance.isEmpty() && b.assets.isEmpty() && b.profiles.isEmpty() && b.explanations.isEmpty()) { "旧版本备份包含未知扩展数据" }
        fun unique(ids: List<String>) { require(ids.size == ids.distinct().size) { "备份含重复 ID" } }
        listOf(b.users.map { it.id },b.categories.map { it.id },b.dimensions.map { it.id },b.parameters.map { it.id },b.decisions.map { it.id },b.strategies.map { it.id },b.evaluations.map { it.id },b.choices.map { it.id }).forEach(::unique)
        listOf(b.intakes.map { it.decisionId },b.assistance.map { it.evaluationId },b.assets.map { it.id },b.profiles.map { it.id }).forEach(::unique)
        unique(b.explanations.map {it.id})
        b.categories.forEach { val c: Category = SnapshotCodec.decode(it.json); require(c.id == it.id && c.name == it.name) }
        b.dimensions.forEach { val d: Dimension = SnapshotCodec.decode(it.json); require(b.categories.any { c -> c.id == it.categoryId } && it.id == "${it.categoryId}:${d.id}") }
        b.parameters.forEach { val p: ParameterRecord = SnapshotCodec.decode(it.json); require(p.key == it.key) }
        b.decisions.forEach { val d: DecisionInputSnapshot = SnapshotCodec.decode(it.json); require(d.id == it.id && d.userId == it.userId && d.category.id == it.categoryId && b.users.any { u -> u.id == d.userId } && b.categories.any { c -> c.id == d.category.id }); require(d.title == it.title && d.example == it.example) }
        b.strategies.forEach { val s: Strategy = SnapshotCodec.decode(it.json); val d: DecisionInputSnapshot = SnapshotCodec.decode(b.decisions.first { d -> d.id == it.caseId }.json); require(it.id == "${it.caseId}:${s.id}" && s.name == it.name && d.strategies.any { ds -> ds == s }) }
        b.evaluations.forEach { e ->
            require(b.decisions.any { it.id == e.caseId }); val input: DecisionInputSnapshot = SnapshotCodec.decode(e.inputJson); val result: DecisionEvaluation = SnapshotCodec.decode(e.resultJson)
            require(input.id == e.caseId && SnapshotCodec.hash(input) == e.inputHash && result.inputHash == e.inputHash) { "快照哈希校验失败" }
            require(result.config.appVersion in setOf("0.1.0","0.2.0") && result.config.copy(appVersion="0.1.0") == EngineConfig()) { "不支持的引擎/映射版本" }
            require(DecisionEngine().evaluate(input,result.config) == result) { "计算快照内容校验失败" }
        }
        b.choices.forEach { choice -> val evaluation = b.evaluations.first { it.id == choice.evaluationId }; val result: DecisionEvaluation = SnapshotCodec.decode(evaluation.resultJson); require(evaluation.caseId == choice.caseId && result.strategies.any { it.id == choice.strategyId }) }
        b.intakes.forEach { row -> val session=SnapshotCodec.decode<IntakeSession>(row.json);session.validate();require(session.decisionId==row.decisionId); val input=SnapshotCodec.decode<DecisionInputSnapshot>(b.decisions.first { it.id==row.decisionId }.json);session.pending?.let { AssistantProtocol.validate(it,input) };session.unknownPaths.forEach { require(AssistantProtocol.read(input,it)!=null) };require(session.reviewedHash==null || session.reviewedHash==SnapshotCodec.hash(input)) }
        b.assistance.forEach { row -> val session=SnapshotCodec.decode<IntakeSession>(row.json);session.validate();val evaluation=b.evaluations.first { it.id==row.evaluationId };require(session.decisionId==evaluation.caseId && session.reviewedHash==evaluation.inputHash && session.pending==null && session.unknownPaths.isEmpty()) }
        b.assets.forEach { row -> val a=SnapshotCodec.decode<AssetProfile>(row.json);require(a.schemaVersion=="1" && a.id==row.id && a.name==row.name && a.updatedAt==row.updatedAt && a.name.isNotBlank() && a.category.dimensions.map { it.id }.toSet()==a.stage.utilities.map { it.dimensionId }.toSet());a.parameters.forEach { if(it.confirmed) it.scalar() };NeedGenerator.generate(a.need,a.category.dimensions) }
        b.profiles.forEach { row -> val p=SnapshotCodec.decode<ReusableDefaults>(row.json);require(row.id=="local-defaults" && p.schemaVersion=="1" && p.horizon.isFinite() && p.horizon>0 && p.cashStep.isFinite() && p.cashStep>0 && p.currency.matches(Regex("[A-Z]{3}")));listOfNotNull(p.budget,p.totalBudget).forEach { require(it.currency==p.currency && it.decimal().signum()>=0) };p.parameters.forEach { if(it.confirmed) it.scalar() } }
        b.explanations.forEach {row->val e=SnapshotCodec.decode<AiExplanation>(row.json);require(e.schemaVersion=="1" && e.promptVersion=="explain-1" && e.id==row.id && e.evaluationId==row.evaluationId && b.evaluations.any {it.id==e.evaluationId} && e.text.length in 1..5000)}
        return db.withTransaction {
            val current = backup()
            fun <T> additions(incoming: List<T>, existing: List<T>, id: (T)->String): List<T> {
                val index = existing.associateBy(id)
                return incoming.filter { item -> val previous = index[id(item)]; require(previous == null || previous == item) { "ID 内容冲突：${id(item)}；整次导入已取消" }; previous == null }
            }
            // Compute every conflict before the first write. Transaction also protects all inserts.
            val users = additions(b.users,current.users) { it.id }; val categories = additions(b.categories,current.categories) { it.id }; val dimensions = additions(b.dimensions,current.dimensions) { it.id }; val params = additions(b.parameters,current.parameters) { it.id }; val decisions = additions(b.decisions,current.decisions) { it.id }; val strategies = additions(b.strategies,current.strategies) { it.id }; val evaluations = additions(b.evaluations,current.evaluations) { it.id }; val choices = additions(b.choices,current.choices) { it.id }
            val intakes=additions(b.intakes,current.intakes) { it.decisionId };val assistance=additions(b.assistance,current.assistance) { it.evaluationId };val assets=additions(b.assets,current.assets) { it.id };val profiles=additions(b.profiles,current.profiles) { it.id }
            val explanations=additions(b.explanations,dao.explanations().map {ExplanationBackup(it.id,it.evaluationId,it.json)}) {it.id}
            dao.insertUsers(users.map { UserRow(it.id,it.name) }); dao.insertCategories(categories.map { CategoryRow(it.id,it.name,it.json) }); dao.insertDimensions(dimensions.map { DimensionRow(it.id,it.categoryId,it.json) }); dao.insertParameters(params.map { ParameterRow(it.id,it.key,it.json) }); dao.insertDecisions(decisions.map { DecisionRow(it.id,it.title,it.userId,it.categoryId,it.updatedAt,it.example,it.json) }); dao.insertStrategies(strategies.map { StrategyRow(it.id,it.caseId,it.name,it.json) }); dao.insertEvaluations(evaluations.map { EvaluationRow(it.id,it.caseId,it.createdAt,it.inputHash,it.inputJson,it.resultJson) }); dao.insertChoices(choices.map { ChoiceRow(it.id,it.caseId,it.evaluationId,it.strategyId,it.createdAt,it.reason) })
            dao.insertIntakes(intakes.map { IntakeRow(it.decisionId,it.json) });dao.insertAssistance(assistance.map { AssistanceRow(it.evaluationId,it.json) });dao.insertAssets(assets.map { AssetRow(it.id,it.name,it.updatedAt,it.json) });dao.insertProfiles(profiles.map { ProfileRow(it.id,it.json) })
            dao.insertExplanations(explanations.map {ExplanationRow(it.id,it.evaluationId,it.json)})
            "导入完成：${decisions.size} 个决策、${evaluations.size} 个历史计算；相同内容已跳过"
        }
    }
}
