package com.sixmodel.consumerdecision

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixmodel.core.*
import com.sixmodel.consumerdecision.ai.*
import com.sixmodel.consumerdecision.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class AppModel(val repository: DecisionRepository,private val settingsStore: AiSettingsStore,private val ai: AiClient): ViewModel() {
    val decisions=repository.decisions.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val evaluations=repository.evaluations.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val choices=repository.choices.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val assets=repository.assets.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    var draft by mutableStateOf<DecisionInputSnapshot?>(null);private set
    var session by mutableStateOf<IntakeSession?>(null);private set
    var current by mutableStateOf<EvaluationRow?>(null);private set
    private var pageState by mutableStateOf("home")
    var page: String
        get()=pageState
        set(value) {if(value !in setOf("interview","review","summary") && aiBusy)invalidateRequest();pageState=value}
    var step by mutableIntStateOf(0)
    var message by mutableStateOf("");private set
    var busy by mutableStateOf(false);private set
    var aiBusy by mutableStateOf(false);private set
    var aiMode by mutableStateOf(true);private set
    var settings by mutableStateOf(settingsStore.load());private set
    var explanation by mutableStateOf("");private set
    private var saveJob: Job?=null
    private var aiJob: Job?=null
    private var requestId=0L
    private val saveMutex=Mutex()
    private suspend fun <T> io(block: suspend ()->T): T = withContext(Dispatchers.IO) { block() }
    fun notify(text: String) { message=text }
    fun switchManual() {invalidateRequest();page="editor"}
    fun saveAnswerDraft(question: InterviewQuestion,option: String?,text: String,unknown: Boolean) {val s=session?:return;session=s.copy(draftAnswer=InterviewAnswer(question,option,text.take(4000),unknown));scheduleSave()}
    fun task(block: suspend ()->Unit) { if(busy)return;viewModelScope.launch { busy=true;try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) { message=FriendlyFields.error(e) } finally { busy=false } } }
    private fun scheduleSave() { saveJob?.cancel();val d=draft?:return;val s=session?:return;saveJob=viewModelScope.launch { delay(450);try { saveMutex.withLock { io { repository.saveWorkspace(d,s) } } } catch(e: CancellationException) { throw e } catch(e: Exception) { message="自动保存失败：${FriendlyFields.error(e)}" } } }
    fun save() { saveJob?.cancel();val d=draft?:return;val s=session?:return;saveJob=viewModelScope.launch { try { saveMutex.withLock { io { repository.saveWorkspace(d,s) } };message="草稿已保存" } catch(e: CancellationException) { throw e } catch(e: Exception) { message=FriendlyFields.error(e) } } }
    private suspend fun flush() { saveJob?.cancelAndJoin();val d=draft?:return;val s=session?:return;saveMutex.withLock { io { repository.saveWorkspace(d,s) } } }
    private fun invalidateRequest() { requestId++;aiJob?.cancel();aiBusy=false }
    fun update(value: DecisionInputSnapshot) {
        val old=draft?:return;require(old.id==value.id)
        if(old==value)return
        invalidateRequest();val changed=AssistantProtocol.changedPaths(old,value)
        val s=session?:IntakeSession(decisionId=value.id)
        session=s.copy(revision=s.revision+1,pending=null,reviewedHash=null,unknownPaths=s.unknownPaths.filter { gap -> AssistantProtocol.read(value,gap)!=null && changed.none { it==gap || gap.startsWith("$it/") } },changes=(s.changes+InputChange(s.revision+1,"MANUAL","手动修改",changed)).takeLast(1000),undo=UndoDraft(SnapshotCodec.encode(old),s.unknownPaths))
        draft=value;scheduleSave()
    }
    fun open(id: String,interview: Boolean=false) { invalidateRequest();task { flush();val d=io { repository.load(id) };val s=io { repository.intake(id) } ?: IntakeSession(decisionId=id);draft=d;session=s;aiMode=s.mode=="AI";step=0;page=if(interview)"interview" else "editor";message="" } }
    fun new(example: Boolean=false,interview: Boolean=false) {
        invalidateRequest();saveJob?.cancel()
        val previous=draft;val previousSession=session
        if(previous!=null&&previousSession!=null)viewModelScope.launch {try {saveMutex.withLock {io {repository.saveWorkspace(previous,previousSession)}}} catch(e: Exception) {message=FriendlyFields.error(e)}}
        val id=UUID.randomUUID().toString();val seed=Examples.phone(id)
        val value=if(example) seed.copy(category=seed.category.copy(id="$id:category")) else {
            val dim=Dimension("function","必需功能",reference=1.0,source="PRESET_UNCONFIRMED")
            val st=Stage("current-stage","current-asset","当前物品",start=0.0,end=3.0,utilities=listOf(Utility(dim.id,0.8,0.05,source="PRESET_UNCONFIRMED")))
            seed.copy(title="新的消费决策",category=Category("$id:category","自定义品类",listOf(dim)),needs=listOf(NeedPoint(inputs=listOf(NeedInput(dim.id)))),budget=null,parameters=seed.parameters.map { it.copy(confirmed=false,source="SPEC_INITIAL") },strategies=listOf(Strategy("keep","继续使用",listOf(st),st.id),Strategy("alternative","备选方案",listOf(st.copy(id="alternative-stage",assetId="alternative-asset",name="备选物品")),"alternative-stage")),example=false)
        }
        draft=value;session=IntakeSession(decisionId=id);step=0;page=if(interview)"interview" else "editor";message="";explanation="";aiMode=true;scheduleSave()
    }
    fun offline() { invalidateRequest();aiMode=false;val d=draft?:return;val s=session?:return;session=s.copy(question=OfflineInterview.next(d,s),pending=null,mode="OFFLINE");page="interview";scheduleSave() }
    fun askAi() {
        val d=draft?:return;val s=session?:return
        if(s.pending!=null) {page="review";return}
        if(!settings.enabled||!settings.consent||settings.apiKey.isBlank()) {message="先配置自己的 AI，或使用离线引导。";page="settings";return}
        invalidateRequest();aiMode=true;session=s.copy(mode="AI");val token=requestId;val config=settings;aiBusy=true
        aiJob=viewModelScope.launch {
            try {
                val reply=ai.interview(config,d,s)
                if(token!=requestId||draft?.id!=d.id||session?.revision!=s.revision)return@launch
                val live=session?:return@launch
                session=live.copy(question=reply.question ?: live.question,pending=reply.takeIf { it.changes.isNotEmpty() },unknownPaths=(live.unknownPaths+reply.unknownPaths).distinct(),reviewedHash=null,draftAnswer=null)
                message=reply.summary
                page=when { reply.changes.isNotEmpty() -> "review";reply.readyForReview && reply.question==null -> "summary";else -> "interview" }
                scheduleSave()
            } catch(e: CancellationException) { throw e } catch(e: Exception) { if(token==requestId)message=FriendlyFields.error(e) }
            finally { if(token==requestId)aiBusy=false }
        }
    }
    fun answer(option: String?,text: String,unknown: Boolean=false) {
        val d=draft?:return;val s=session?:return;val q=s.question?:return
        if(text.isBlank()&&option==null&&!unknown) {message="请选一个选项，或用自己的话补充。";return}
        val answer=InterviewAnswer(q,option,text.take(4000),unknown)
        invalidateRequest()
        val paths=if(unknown&&q.targetPath!=null)(s.unknownPaths+listOfNotNull(q.targetPath)).distinct() else s.unknownPaths
        session=s.copy(answers=(s.answers+answer).takeLast(500),unknownPaths=paths,reviewedHash=null,revision=s.revision+1,draftAnswer=null)
        scheduleSave()
        if(aiMode) askAi() else {
            runCatching { OfflineInterview.proposal(d,answer) }.onSuccess { reply ->
                session=session!!.copy(pending=reply,unknownPaths=(session!!.unknownPaths+reply.unknownPaths).distinct());page="review";scheduleSave()
            }.onFailure {session=session!!.copy(answers=s.answers,unknownPaths=s.unknownPaths,revision=s.revision,draftAnswer=answer);scheduleSave();message=FriendlyFields.error(it)}
        }
    }
    fun previousQuestion() { invalidateRequest();val s=session?:return;val previous=s.answers.lastOrNull()?:return;session=s.copy(question=previous.question,answers=s.answers.dropLast(1),pending=null,reviewedHash=null,draftAnswer=previous);page="interview";scheduleSave() }
    fun acceptPending(paths: Set<String>) {
        val d=draft?:return;val s=session?:return;val reply=s.pending?:return
        runCatching {
            val changes=reply.changes.filter { it.path in paths }
            val source=if(aiMode)"AI:${settings.model}:USER_CONFIRMED" else "USER_CONFIRMED_OPTION"
            val next=if(changes.isEmpty())d else AssistantProtocol.apply(d,changes,source,reply.unknownPaths)
            invalidateRequest();draft=next
            session=s.copy(revision=s.revision+1,pending=null,unknownPaths=(s.unknownPaths.filter { gap -> changes.none { it.path==gap||gap.startsWith("${it.path}/") } }+reply.unknownPaths).distinct(),reviewedHash=null,changes=(s.changes+InputChange(s.revision+1,source,reply.summary.ifBlank { "确认输入建议" },changes.map { it.path })).takeLast(1000),undo=UndoDraft(SnapshotCodec.encode(d),s.unknownPaths))
            if(!aiMode) {session=session!!.copy(question=OfflineInterview.next(next,session!!));page=if(session!!.question==null)"summary" else "interview"}
            else page=if(reply.readyForReview&&reply.question==null)"summary" else "interview"
            scheduleSave();message="已按你确认的内容更新草稿"
        }.onFailure { message=FriendlyFields.error(it) }
    }
    fun rejectPending() {val s=session?:return;session=s.copy(pending=null);page="interview";message="建议未应用，原输入保持完整";scheduleSave()}
    fun undo() {val d=draft?:return;val s=session?:return;val undo=s.undo?:return;invalidateRequest();draft=SnapshotCodec.decode(undo.inputJson);session=s.copy(revision=s.revision+1,unknownPaths=undo.unknownPaths,pending=null,reviewedHash=null,undo=null,changes=(s.changes+InputChange(s.revision+1,"USER_UNDO","撤销最近一次修改",AssistantProtocol.changedPaths(d,draft!!))).takeLast(1000));message="已恢复修改前的输入";scheduleSave()}
    fun confirmInput() {
        val d=draft?:return;val s=session?:return
        runCatching {
            require(s.pending==null && s.unknownPaths.isEmpty()) { "请先处理待确认建议和信息缺口" }
            val confirmed=d.copy(parameters=d.parameters.map { it.copy(confirmed=true).also { p -> p.scalar() } },needs=d.needs.map { if(it.method!=NeedMethod.MANUAL_USER_SIDE)it.copy(coefficientsConfirmed=true) else it },strategies=d.strategies.map { st -> st.copy(flows=st.flows.map { f -> f.copy(hourlyRateParameter=f.hourlyRateParameter?.copy(confirmed=true)) }) })
            DecisionEngine().evaluate(confirmed)
            draft=confirmed;session=s.copy(reviewedHash=SnapshotCodec.hash(confirmed),revision=s.revision+1);message="输入和当前显示的参数已确认";scheduleSave()
        }.onFailure {message=FriendlyFields.error(it)}
    }
    fun calculate() {task {flush();val d=draft?:return@task;val s=session?:return@task;val row=io {repository.calculate(d,s)};current=row;page="result";message="计算已保存为独立快照";explanation=""}}
    fun show(row: EvaluationRow) {current=row;page="result";explanation=""}
    fun loadExplanation(row: EvaluationRow) {viewModelScope.launch {val saved=io {repository.explanation(row.id)};if(current?.id==row.id)explanation=saved?.text.orEmpty()}}
    fun explainResult() {val row=current?:return;task {val input=SnapshotCodec.decode<DecisionInputSnapshot>(row.inputJson);val result=SnapshotCodec.decode<DecisionEvaluation>(row.resultJson);val text=ai.explain(settings,input,result);val note=AiExplanation(id=UUID.randomUUID().toString(),evaluationId=row.id,model=settings.model,provider=java.net.URI(settings.endpoint.trim()).host,text=text,createdAt=System.currentTimeMillis());io {repository.saveExplanation(note)};if(current?.id==row.id)explanation=text;message="AI 解读已保存，实际数值和条件仍以计算明细为准"}}
    fun choose(id: String,reason: String) {val row=current?:return;task {io {repository.choose(row.id,id,reason)};message="真实选择已记录"}}
    fun saveSettings(value: AiSettings,test: Boolean=false) {task {io {settingsStore.save(value)};invalidateRequest();settings=value;message=if(test)ai.test(value) else "AI 配置已安全保存"}}
    fun clearSettings() {task {io {settingsStore.delete()};invalidateRequest();settings=AiSettings();message="密钥和配置已删除"}}
    fun saveAsset(name: String,stage: Stage) {val d=draft?:return;task {val a=AssetProfile(id=UUID.randomUUID().toString(),name=name,category=d.category,stage=stage.copy(start=0.0,end=d.horizon,repairs=emptyList(),upgrade=null),need=d.needs.first(),parameters=d.parameters,updatedAt=System.currentTimeMillis());io {repository.saveAsset(a)};message="物品与使用习惯已保存，下次复用前请确认状态"}}
    fun reuseAsset(row: AssetRow) {
        val a=SnapshotCodec.decode<AssetProfile>(row.json);new()
        val d=draft!!;val stage=a.stage.copy(id="current-stage",assetId=a.stage.assetId,start=0.0,end=d.horizon,action=Action.CONTINUE_USE,emotion=Emotion())
        update(d.copy(title="${a.name}的消费决策",category=a.category.copy(id="${d.id}:category"),needs=listOf(a.need.copy(time=0.0)),parameters=a.parameters.map { it.copy(confirmed=false,source="REUSED_RECONFIRM") },strategies=listOf(Strategy("keep","继续使用",listOf(stage),stage.id),Strategy("alternative","备选方案",listOf(stage.copy(id="alternative-stage",assetId=UUID.randomUUID().toString(),name="备选物品")),"alternative-stage"))))
        message="已复用物品与习惯，请核实当前状态及备选方案；不会自动确认。"
    }
    fun saveDefaults() {val d=draft?:return;task {io {repository.saveDefaults(ReusableDefaults(horizon=d.horizon,cashStep=d.cashStep,currency=d.currency,budget=d.budget,totalBudget=d.totalBudget,parameters=d.parameters))};message="已保存预算和通用参数，下次可选择复用"}}
    fun reuseDefaults() {val d=draft?:return;task {val p=io {repository.defaults()}?:error("尚未保存可复用参数");require(p.currency==d.currency) {"币种不同，请先统一币种"};require(d.strategies.all {it.stages.size==1}) {"多阶段方案请手动调整时间"};update(d.copy(horizon=p.horizon,cashStep=p.cashStep,budget=p.budget,totalBudget=p.totalBudget,parameters=p.parameters.map {it.copy(confirmed=false,source="REUSED_RECONFIRM")},strategies=d.strategies.map {s->s.copy(stages=s.stages.map {it.copy(end=p.horizon)},flows=s.flows.map {if(it.kind==CashKind.SALVAGE)it.copy(time=p.horizon) else it})}));message="已复用预算和参数，请确认仍然适用"}}
    fun scenario(multiplier: Double) {
        val row=current?:return;val original=SnapshotCodec.decode<DecisionInputSnapshot>(row.inputJson)
        new();val d=original.copy(id=draft!!.id,title=original.title+if(multiplier<1)" · 变差较慢情景" else " · 变差较快情景",context=original.context+"\n情景假设：性能和情感衰减速度×$multiplier；原快照 ${row.inputHash}",category=original.category.copy(id="${draft!!.id}:category"),strategies=original.strategies.map {s->s.copy(stages=s.stages.map {st->st.copy(utilities=st.utilities.map {it.copy(lambda=it.lambda*multiplier,mu=it.mu*multiplier,source="SCENARIO_ASSUMPTION")},emotion=st.emotion.copy(gamma=st.emotion.gamma*multiplier,source="SCENARIO_ASSUMPTION"))})})
        val strategies=d.strategies.map {strategy->val ordered=mutableListOf<Stage>();strategy.stages.sortedBy {it.start}.forEach {stage->val previous=ordered.lastOrNull();ordered+=if(previous!=null&&previous.assetId==stage.assetId) {val end=Model1.utilityAt(previous,previous.end-previous.start);stage.copy(utilities=stage.utilities.map {it.copy(initial=end.getValue(it.dimensionId))})} else stage};strategy.copy(stages=strategy.stages.map {st->ordered.first {it.id==st.id}})}
        draft=d.copy(strategies=strategies);session=IntakeSession(decisionId=d.id);page="summary";message="新建了独立情景草稿，需确认这些假设后计算；旧结果已保留";scheduleSave()
    }
}
