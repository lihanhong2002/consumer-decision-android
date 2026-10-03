package com.sixmodel.consumerdecision.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName="users") data class UserRow(@PrimaryKey val id: String, val name: String)
@Entity(tableName="categories") data class CategoryRow(@PrimaryKey val id: String, val name: String, val json: String)
@Entity(tableName="dimensions") data class DimensionRow(@PrimaryKey val id: String, val categoryId: String, val json: String)
@Entity(tableName="parameters") data class ParameterRow(@PrimaryKey val id: String, val key: String, val json: String)
@Entity(tableName="decisions") data class DecisionRow(@PrimaryKey val id: String, val title: String, val userId: String, val categoryId: String, val updatedAt: Long, val example: Boolean, val json: String)
@Entity(tableName="strategies") data class StrategyRow(@PrimaryKey val id: String, val caseId: String, val name: String, val json: String)
@Entity(tableName="evaluations") data class EvaluationRow(@PrimaryKey val id: String, val caseId: String, val createdAt: Long, val inputHash: String, val inputJson: String, val resultJson: String)
@Entity(tableName="choices") data class ChoiceRow(@PrimaryKey val id: String, val caseId: String, val evaluationId: String, val strategyId: String, val createdAt: Long, val reason: String)
@Entity(tableName="intake_sessions") data class IntakeRow(@PrimaryKey val decisionId: String, val json: String)
@Entity(tableName="assistance_snapshots") data class AssistanceRow(@PrimaryKey val evaluationId: String, val json: String)
@Entity(tableName="assets") data class AssetRow(@PrimaryKey val id: String, val name: String, val updatedAt: Long, val json: String)
@Entity(tableName="profiles") data class ProfileRow(@PrimaryKey val id: String, val json: String)
@Entity(tableName="ai_explanations") data class ExplanationRow(@PrimaryKey val id: String, val evaluationId: String, val json: String)

@Dao interface DecisionDao {
    @Query("SELECT * FROM decisions ORDER BY updatedAt DESC") fun observeDecisions(): Flow<List<DecisionRow>>
    @Query("SELECT * FROM evaluations ORDER BY createdAt DESC") fun observeEvaluations(): Flow<List<EvaluationRow>>
    @Query("SELECT * FROM choices ORDER BY createdAt DESC") fun observeChoices(): Flow<List<ChoiceRow>>
    @Query("SELECT * FROM decisions WHERE id=:id") suspend fun decision(id: String): DecisionRow?
    @Query("SELECT * FROM evaluations WHERE id=:id") suspend fun evaluation(id: String): EvaluationRow?
    @Query("SELECT * FROM users") suspend fun users(): List<UserRow>
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryRow>
    @Query("SELECT * FROM dimensions") suspend fun dimensions(): List<DimensionRow>
    @Query("SELECT * FROM parameters") suspend fun parameters(): List<ParameterRow>
    @Query("SELECT * FROM decisions") suspend fun decisions(): List<DecisionRow>
    @Query("SELECT * FROM strategies") suspend fun strategies(): List<StrategyRow>
    @Query("SELECT * FROM evaluations") suspend fun evaluations(): List<EvaluationRow>
    @Query("SELECT * FROM choices") suspend fun choices(): List<ChoiceRow>
    @Query("SELECT * FROM intake_sessions WHERE decisionId=:id") suspend fun intake(id: String): IntakeRow?
    @Query("SELECT * FROM intake_sessions") suspend fun intakes(): List<IntakeRow>
    @Query("SELECT * FROM assistance_snapshots") suspend fun assistanceSnapshots(): List<AssistanceRow>
    @Query("SELECT * FROM assets ORDER BY updatedAt DESC") fun observeAssets(): Flow<List<AssetRow>>
    @Query("SELECT * FROM assets") suspend fun assets(): List<AssetRow>
    @Query("SELECT * FROM profiles") suspend fun profiles(): List<ProfileRow>
    @Query("SELECT * FROM profiles WHERE id=:id") suspend fun profile(id: String): ProfileRow?
    @Query("SELECT * FROM ai_explanations") suspend fun explanations(): List<ExplanationRow>
    @Query("SELECT * FROM ai_explanations WHERE evaluationId=:id ORDER BY rowid DESC LIMIT 1") suspend fun explanation(id: String): ExplanationRow?
    @Upsert suspend fun saveIntake(value: IntakeRow)
    @Upsert suspend fun saveAsset(value: AssetRow)
    @Upsert suspend fun saveProfile(value: ProfileRow)
    @Upsert suspend fun saveDecision(value: DecisionRow)
    @Upsert suspend fun saveCategory(value: CategoryRow)
    @Upsert suspend fun saveDimension(value: DimensionRow)
    @Upsert suspend fun saveParameter(value: ParameterRow)
    @Upsert suspend fun saveStrategy(value: StrategyRow)
    @Query("DELETE FROM strategies WHERE caseId=:caseId") suspend fun clearDraftStrategies(caseId: String)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertUsers(values: List<UserRow>)
    @Insert suspend fun insertCategories(values: List<CategoryRow>)
    @Insert suspend fun insertDimensions(values: List<DimensionRow>)
    @Insert suspend fun insertParameters(values: List<ParameterRow>)
    @Insert suspend fun insertDecisions(values: List<DecisionRow>)
    @Insert suspend fun insertStrategies(values: List<StrategyRow>)
    @Insert suspend fun insertEvaluations(values: List<EvaluationRow>)
    @Insert suspend fun insertChoices(values: List<ChoiceRow>)
    @Insert suspend fun insertIntakes(values: List<IntakeRow>)
    @Insert suspend fun insertAssistance(values: List<AssistanceRow>)
    @Insert suspend fun insertAssets(values: List<AssetRow>)
    @Insert suspend fun insertProfiles(values: List<ProfileRow>)
    @Insert suspend fun insertExplanations(values: List<ExplanationRow>)
}
@Database(entities=[UserRow::class,CategoryRow::class,DimensionRow::class,ParameterRow::class,DecisionRow::class,StrategyRow::class,EvaluationRow::class,ChoiceRow::class,IntakeRow::class,AssistanceRow::class,AssetRow::class,ProfileRow::class,ExplanationRow::class],version=2,exportSchema=true)
abstract class AppDatabase: RoomDatabase() {
    abstract fun dao(): DecisionDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `intake_sessions` (`decisionId` TEXT NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`decisionId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `assistance_snapshots` (`evaluationId` TEXT NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`evaluationId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `assets` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `profiles` (`id` TEXT NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `ai_explanations` (`id` TEXT NOT NULL, `evaluationId` TEXT NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }
    }
}
