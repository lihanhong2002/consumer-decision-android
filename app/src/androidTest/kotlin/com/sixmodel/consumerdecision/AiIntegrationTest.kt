package com.sixmodel.consumerdecision

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixmodel.core.*
import com.sixmodel.consumerdecision.ai.*
import com.sixmodel.consumerdecision.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class) class AiIntegrationTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val config=AiSettings(apiKey="fake-key-for-automated-test",enabled=true,consent=true)
    private fun response(reply: AssistantReply) = buildJsonObject {putJsonArray("choices") {add(buildJsonObject {put("finish_reason","stop");putJsonObject("message") {put("content",SnapshotCodec.encode(reply))}})}}.toString()
    @Test fun personalKeyEncryptedAndExcludedFromBackup() = runBlocking {
        val store=AiSettingsStore(context);val before=store.load()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {store.save(config);assertEquals(config,store.load());assertFalse(context.getSharedPreferences("ai-private",0).getString("cipher","")!!.contains(config.apiKey));val repo=DecisionRepository(db);repo.calculate(Examples.phone());assertFalse(repo.export().contains(config.apiKey));store.delete();assertTrue(store.load().apiKey.isEmpty())} finally {store.save(before);db.close()};Unit
    }
    @Test fun validReplyRequiresConfirmationAndManualEditDiscardsLateReply() {
        val settings=AiSettingsStore(context);val before=settings.load();settings.save(config)
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build();val owner=ViewModelStore()
        val started=CountDownLatch(1);val release=CountDownLatch(1)
        var calls=0
        val reply=AssistantReply(changes=listOf(SuggestedChange("/budget",SnapshotCodec.json.encodeToJsonElement(Money("3000")),"用户选择的预算","USER_ANSWER",Confidence.HIGH)),readyForReview=true)
        val transport=AiTransport {_,_->calls++;if(calls==2){started.countDown();release.await(10,TimeUnit.SECONDS)};response(reply)}
        lateinit var model:AppModel
        try {
            instrumentation.runOnMainSync {model=AppModel(DecisionRepository(db),settings,AiClient("model rules",transport));owner.put("model",model);model.new(example=true);model.askAi()}
            await {model.session?.pending!=null}
            instrumentation.runOnMainSync {assertEquals("4000",model.draft!!.budget!!.amount);model.acceptPending(setOf("/budget"));assertEquals("3000",model.draft!!.budget!!.amount);assertNull(model.session!!.pending);model.askAi()}
            assertTrue(started.await(5,TimeUnit.SECONDS))
            instrumentation.runOnMainSync {model.update(model.draft!!.copy(title="手动修改优先"));model.switchManual()}
            release.countDown();Thread.sleep(400)
            instrumentation.runOnMainSync {assertEquals("手动修改优先",model.draft!!.title);assertNull(model.session!!.pending);assertEquals("editor",model.page);assertFalse(model.aiBusy);model.undo();assertFalse(model.draft!!.title=="手动修改优先")}
        } finally {release.countDown();instrumentation.runOnMainSync {owner.clear()};Thread.sleep(100);db.close();settings.save(before)}
    }
    @Test fun failedAiKeepsDraftAndCanSwitchToOffline() {
        val settings=AiSettingsStore(context);val before=settings.load();settings.save(config)
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build();val owner=ViewModelStore();lateinit var model:AppModel
        try {
            instrumentation.runOnMainSync {model=AppModel(DecisionRepository(db),settings,AiClient("rules",AiTransport {_,_->error("模拟网络失败")}));owner.put("model",model);model.new(example=true);model.askAi()}
            await {!model.aiBusy}
            instrumentation.runOnMainSync {assertEquals("4000",model.draft!!.budget!!.amount);assertNull(model.session!!.pending);model.offline();assertEquals("OFFLINE",model.session!!.mode);assertNotNull(model.session!!.question);model.answer(null,"手机使用问题");model.acceptPending(setOf("/title"));val count=model.session!!.answers.size;model.answer(null,"大概几千");assertEquals(count,model.session!!.answers.size);assertNull(model.session!!.pending);assertEquals("4000",model.draft!!.budget!!.amount)}
        } finally {instrumentation.runOnMainSync {owner.clear()};Thread.sleep(100);db.close();settings.save(before)}
    }
    private fun await(condition: ()->Boolean) {val end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){var done=false;instrumentation.runOnMainSync {done=condition()};if(done)return;Thread.sleep(50)};fail("等待异步状态超时")}
}
