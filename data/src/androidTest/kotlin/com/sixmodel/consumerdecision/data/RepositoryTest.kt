package com.sixmodel.consumerdecision.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixmodel.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class) class RepositoryTest {
    private lateinit var first: AppDatabase
    private lateinit var second: AppDatabase
    @Before fun setup() { val c=InstrumentationRegistry.getInstrumentation().targetContext; first=Room.inMemoryDatabaseBuilder(c,AppDatabase::class.java).build(); second=Room.inMemoryDatabaseBuilder(c,AppDatabase::class.java).build() }
    @After fun close() { first.close(); second.close() }
    @Test fun snapshotsBackupChoicesAndAtomicConflict() = runBlocking {
        val a=DecisionRepository(first); val b=DecisionRepository(second); val input=Examples.phone()
        val e=a.calculate(input); a.choose(e.id,"keep","节约支出"); a.calculate(input.copy(title="修改后的决策"))
        assertEquals(2,first.dao().evaluations().size)
        val export=a.export(); b.importBackup(export)
        assertEquals(first.dao().evaluations(),second.dao().evaluations()); assertEquals(first.dao().choices(),second.dao().choices())
        b.importBackup(export); assertEquals(2,second.dao().evaluations().size)
        val backup: Backup=SnapshotCodec.decode(export)
        val conflict=backup.copy(users=backup.users+UserBackup("new-user","new"),decisions=backup.decisions.map { it.copy(updatedAt=it.updatedAt+1) })
        try { b.importBackup(SnapshotCodec.encode(conflict)); fail("conflict should fail") } catch(_: IllegalArgumentException) { }
        assertEquals(first.dao().users(),second.dao().users())
        try { b.importBackup(export.replace("\"schemaVersion\":\"2.0.0\"","\"schemaVersion\":\"99.0.0\"")); fail("version should fail") } catch(_: IllegalArgumentException) { }
        try { b.importBackup(export.dropLast(1)+",\"unknownImportant\":1}"); fail("unknown fields should fail") } catch(_: Exception) { }
        assertEquals(2,second.dao().evaluations().size)
    }
    @Test fun reopeningDatabasePreservesSnapshot() = runBlocking {
        val c=InstrumentationRegistry.getInstrumentation().targetContext; c.deleteDatabase("restart-test.db")
        var db=Room.databaseBuilder(c,AppDatabase::class.java,"restart-test.db").build()
        val row=DecisionRepository(db).calculate(Examples.phone()); db.close()
        db=Room.databaseBuilder(c,AppDatabase::class.java,"restart-test.db").build()
        assertEquals(row,db.dao().evaluation(row.id)); db.close(); c.deleteDatabase("restart-test.db"); Unit
    }
    @Test fun workspaceAssetsAndGuidanceRoundTripWithoutKeys() = runBlocking {
        val a=DecisionRepository(first);val b=DecisionRepository(second);val input=Examples.phone()
        val question=InterviewQuestion("goal","这次想解决什么？",targetPath="/title")
        val session=IntakeSession(decisionId=input.id,question=question,answers=listOf(InterviewAnswer(question,text="续航不够用")),reviewedHash=SnapshotCodec.hash(input),mode="OFFLINE",draftAnswer=InterviewAnswer(question,text="未提交的补充"))
        a.saveWorkspace(input,session);a.saveAsset(AssetProfile(id="asset",name="我的手机",category=input.category,stage=input.strategies.first().stages.first(),need=input.needs.first(),parameters=input.parameters,updatedAt=1));a.saveDefaults(ReusableDefaults(horizon=input.horizon,cashStep=input.cashStep,currency=input.currency,budget=input.budget,totalBudget=null,parameters=input.parameters))
        val evaluation=a.calculate(input,session)
        a.saveExplanation(AiExplanation(id="explanation",evaluationId=evaluation.id,model="mock",provider="example.test",text="各方案需要结合需求比较。",createdAt=1))
        val export=a.export();b.importBackup(export);b.importBackup(export)
        assertEquals(first.dao().intakes(),second.dao().intakes());assertEquals(first.dao().assets(),second.dao().assets());assertEquals(first.dao().profiles(),second.dao().profiles());assertEquals(first.dao().assistanceSnapshots(),second.dao().assistanceSnapshots())
        assertEquals(first.dao().explanations(),second.dao().explanations());assertFalse(export.contains("apiKey"));assertEquals("未提交的补充",b.intake(input.id)!!.draftAnswer!!.text)
        try {a.calculate(input.copy(title="已修改"),session);fail("stale review must fail")} catch(_:IllegalArgumentException) {}
        try {a.calculate(input,session.copy(unknownPaths=listOf("/horizon")));fail("unknown must fail")} catch(_:IllegalArgumentException) {}
        assertEquals(1,first.dao().evaluations().size)
    }
    @Test fun oldBackupAndRealV1DatabaseMigrateWithoutLosingHistory() = runBlocking {
        val a=DecisionRepository(first);a.calculate(Examples.phone());val backup:Backup=SnapshotCodec.decode(a.export())
        val old=backup.copy(schemaVersion="1.0.0",evaluations=backup.evaluations.map {e->val r:DecisionEvaluation=SnapshotCodec.decode(e.resultJson);e.copy(resultJson=SnapshotCodec.encode(r.copy(config=r.config.copy(appVersion="0.1.0"))))})
        DecisionRepository(second).importBackup(SnapshotCodec.encode(old));assertEquals(1,second.dao().evaluations().size)
        val instrumentation=InstrumentationRegistry.getInstrumentation();val c=instrumentation.targetContext
        c.deleteDatabase("migration-test.db")
        val schema=org.json.JSONObject(instrumentation.context.assets.open("com.sixmodel.consumerdecision.data.AppDatabase/1.json").bufferedReader().use {it.readText()}).getJSONObject("database")
        c.openOrCreateDatabase("migration-test.db",android.content.Context.MODE_PRIVATE,null).use {legacy->
            val entities=schema.getJSONArray("entities");for(i in 0 until entities.length()) {val entity=entities.getJSONObject(i);legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))}
            legacy.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            legacy.execSQL("INSERT INTO room_master_table VALUES (42,?)",arrayOf(schema.getString("identityHash")))
            val d=first.dao().decisions().single();legacy.execSQL("INSERT INTO decisions VALUES (?,?,?,?,?,?,?)",arrayOf(d.id,d.title,d.userId,d.categoryId,d.updatedAt,if(d.example)1 else 0,d.json))
            val e=old.evaluations.single();legacy.execSQL("INSERT INTO evaluations VALUES (?,?,?,?,?,?)",arrayOf(e.id,e.caseId,e.createdAt,e.inputHash,e.inputJson,e.resultJson));legacy.version=1
        }
        val upgraded=Room.databaseBuilder(c,AppDatabase::class.java,"migration-test.db").addMigrations(AppDatabase.MIGRATION_1_2).build()
        assertEquals(old.evaluations.single().inputHash,upgraded.dao().evaluations().single().inputHash)
        assertTrue(upgraded.dao().intakes().isEmpty());upgraded.close();c.deleteDatabase("migration-test.db");Unit
    }
}
