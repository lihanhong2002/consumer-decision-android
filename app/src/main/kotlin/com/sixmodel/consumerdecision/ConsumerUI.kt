package com.sixmodel.consumerdecision

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmodel.core.*
import com.sixmodel.consumerdecision.ai.*
import com.sixmodel.consumerdecision.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ln

@Composable fun ConsumerTheme(content: @Composable ()->Unit) {
    MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF176B58),onPrimary=Color.White,primaryContainer=Color(0xFFE8F3EE),onPrimaryContainer=Color(0xFF176B58),secondary=Color(0xFF466B87),background=Color(0xFFF5F7F5),surface=Color.White,onSurface=Color(0xFF182C25),onSurfaceVariant=Color(0xFF5E7068),outlineVariant=Color(0xFFDDE6DF)),shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(20.dp),extraLarge=RoundedCornerShape(28.dp)),content=content)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ConsumerApp(model: AppModel) {
    val context=LocalContext.current
    val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(model.page,model.session?.question?.id) {keyboard?.hide()}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null)model.task { val text=withContext(Dispatchers.IO) {model.repository.export()};withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri)?.use {it.write(text.toByteArray(Charsets.UTF_8))}?:error("无法写入文件")};model.notify("JSON 已导出") } }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null)model.task {val text=withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {it.readText()}?:error("无法读取文件")};val result=withContext(Dispatchers.IO) {model.repository.importBackup(text)};model.notify(result)} }
    BackHandler(enabled=model.page!="home") {if(model.page=="editor"&&model.step>0)model.step-- else model.page="home"}
    Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Surface(color=MaterialTheme.colorScheme.primary,shape=RoundedCornerShape(11.dp)) {Icon(Icons.Outlined.Eco,null,Modifier.padding(7.dp).size(20.dp),tint=Color.White)};Text("理性消费",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Medium)}},navigationIcon={if(model.page!="home")IconButton(onClick={model.page="home"}) {Icon(Icons.Outlined.ArrowBack,"首页")}},actions={IconButton(onClick={model.page="settings"}) {Icon(Icons.Outlined.Tune,"AI 设置")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))},bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
        listOf(Triple("home","决策",Icons.Outlined.Home),Triple("history","记录",Icons.Outlined.History),Triple("assets","我的物品",Icons.Outlined.Smartphone)).forEach {(page,label,icon)->NavigationBarItem(selected=model.page==page||page=="home"&&model.page !in listOf("history","assets"),onClick={model.save();model.page=page},icon={Icon(icon,label)},label={Text(label)})}
    }}) {padding->Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal=20.dp)) {
        if(model.busy||model.aiBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(model.message.isNotBlank())Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().padding(bottom=8.dp)) {Row(Modifier.padding(start=12.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {Text(model.message,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);IconButton(onClick={model.notify("")},Modifier.size(36.dp)) {Icon(Icons.Outlined.Close,"关闭提示",Modifier.size(16.dp))}}}
        when(model.page) {
            "home"->DecisionHome(model)
            "interview"->InterviewScreen(model)
            "review"->SuggestionScreen(model)
            "summary"->InputSummary(model)
            "editor"->ManualScreen(model)
            "result"->model.current?.let {ComparisonScreen(model,it)}
            "history"->HistoryScreen(model)
            "assets"->AssetsScreen(model)
            "settings"->SettingsScreen(model,{export.launch("consumer-decision-backup.json")},{import.launch(arrayOf("application/json","text/plain"))})
        }
    }}
}
@Composable private fun ScreenTitle(title: String,subtitle: String) {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Medium);Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun Note(text: String,warning: Boolean=false) {Surface(color=if(warning)Color(0xFFFFF3DF) else MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) {Text(text,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,color=if(warning)Color(0xFF89540F) else MaterialTheme.colorScheme.onPrimaryContainer)}}
@Composable fun Details(title: String,initiallyOpen: Boolean=false,content: @Composable ColumnScope.()->Unit) {var open by remember(title) {mutableStateOf(initiallyOpen)};Column {TextButton(onClick={open=!open}) {Icon(if(open)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null);Text(title)};if(open)Column(verticalArrangement=Arrangement.spacedBy(10.dp),content=content)}}
@Composable private fun Fact(label: String,value: String) {Row(Modifier.fillMaxWidth().padding(vertical=7.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {Text(label,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium);Text(value,Modifier.weight(1.2f),style=MaterialTheme.typography.bodyMedium)}}
@Composable private fun WideButton(text: String,onClick: ()->Unit,enabled: Boolean=true) {Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(14.dp)) {Text(text)}}

@Composable fun DecisionHome(model: AppModel) {
    val cases by model.decisions.collectAsStateWithLifecycle()
    LazyColumn(contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {Text("把每次选择想清楚",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge);Spacer(Modifier.height(10.dp));ScreenTitle("这次，你在纠结什么？","从你的情况出发，一步步比较选择。")}
        item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {Icon(Icons.Outlined.AutoAwesome,null,tint=MaterialTheme.colorScheme.primary);Column {Text("AI 带我填写",style=MaterialTheme.typography.titleMedium);Text("一次一题，选一选就能开始",style=MaterialTheme.typography.bodySmall)}};WideButton("开始一次决策 →",{model.new(interview=true)});TextButton(onClick={model.new()}) {Text("我想自己填写 ↗")}}}}
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Text("继续上次",style=MaterialTheme.typography.titleMedium);TextButton(onClick={model.new(example=true)}) {Text("手机示例")}}}
        if(cases.isEmpty())item {Note("还没有草稿。开始一次决策，或用手机示例看看怎么算。")}
        items(cases,key={it.id}) {row->Card(onClick={model.open(row.id,interview=true)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(if(row.example)"示例案例" else "个人草稿",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(row.title,style=MaterialTheme.typography.titleMedium);Text("继续填写 · AI 和手动共用同一份输入 →",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
        item {Panel("少填一点") {Text("物品、预算和已确认的参数可以复用。",style=MaterialTheme.typography.bodyMedium);TextButton(onClick={model.page="assets"}) {Text("查看我的物品 →")}}}
    }
}
@Composable fun InterviewScreen(model: AppModel) {
    val d=model.draft?:return;val s=model.session?:return;val q=s.question
    val editing=s.draftAnswer?.takeIf {it.question.id==q?.id}
    var selected by remember(q?.id) {mutableStateOf(editing?.optionId)}
    var text by remember(q?.id) {mutableStateOf(editing?.text.orEmpty())}
    var unknown by remember(q?.id) {mutableStateOf(editing?.unknown?:false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {TextButton(onClick={model.save();model.page="home"}) {Text("稍后继续")};TextButton(onClick={model.switchManual()}) {Icon(Icons.Outlined.Edit,null,Modifier.size(16.dp));Text("手动编辑")}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(if(model.aiMode)"AI 引导" else "离线引导",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge);Text("已回答 ${s.answers.size} 题",style=MaterialTheme.typography.labelMedium)}
        if(!model.aiMode)TextButton(onClick={model.askAi()},enabled=!model.aiBusy) {Text("切换 AI，让它继续问")}
        if(s.pending!=null) {Note("有整理好的建议等待确认，原草稿尚未修改。");WideButton("查看待确认建议",{model.page="review"})}
        if(q==null) {
            ScreenTitle("先说说你的情况","问答会使用当前决策信息，你可以查看即将发送的内容。")
            if(model.aiBusy)Note("正在整理下一道问题，当前输入已保存。")
            WideButton(if(model.settings.enabled)"让 AI 开始提问" else "配置我的 DeepSeek",{if(model.settings.enabled)model.askAi() else model.page="settings"},!model.aiBusy)
            OutlinedButton(onClick={model.offline()},modifier=Modifier.fillMaxWidth()) {Text("先用离线引导")}
            TextButton(onClick={model.page="summary"}) {Text("查看当前填写摘要")}
        } else {
            ScreenTitle(q.title,q.help)
            q.options.forEach {option->OutlinedCard(onClick={selected=option.id;unknown=false;model.saveAnswerDraft(q,selected,text,unknown)},enabled=!model.aiBusy,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.outlinedCardColors(containerColor=if(selected==option.id&&!unknown)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,if(selected==option.id&&!unknown)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text(option.label,style=MaterialTheme.typography.bodyLarge);if(option.detail.isNotBlank())Text(option.detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};RadioButton(selected=selected==option.id&&!unknown,onClick={selected=option.id;unknown=false;model.saveAnswerDraft(q,selected,text,unknown)},enabled=!model.aiBusy)}}}
            OutlinedCard(onClick={unknown=true;selected=null;text="";model.saveAnswerDraft(q,selected,text,unknown)},enabled=!model.aiBusy,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.outlinedCardColors(containerColor=if(unknown)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {Text("我还不确定",Modifier.weight(1f));RadioButton(selected=unknown,onClick={unknown=true;selected=null;text="";model.saveAnswerDraft(q,selected,text,unknown)},enabled=!model.aiBusy)}}
            Details("为什么问这个？") {Text(q.why,style=MaterialTheme.typography.bodyMedium)}
            OutlinedTextField(value=text,onValueChange={text=it.take(4000);if(it.isNotBlank())unknown=false;model.saveAnswerDraft(q,selected,text,unknown)},label={Text("也可以自己补充")},placeholder={Text("用自己的话说就好…")},modifier=Modifier.fillMaxWidth().heightIn(min=110.dp),enabled=!model.aiBusy)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {TextButton(onClick={model.previousQuestion()},enabled=s.answers.isNotEmpty()&&!model.aiBusy) {Text("← 上一题")};Button(onClick={model.answer(selected,text,unknown)},enabled=!model.aiBusy&&(selected!=null||text.isNotBlank()||unknown)) {Text("下一题 →")}}
            if(model.aiMode)TextButton(onClick={model.askAi()},enabled=!model.aiBusy) {Text("重试生成下一题")}
        }
        Details("查看将发送给 AI 的信息") {Note("当前草稿和最近回答会发送到你配置的服务；密钥、其他决策和真实选择历史不会作为提问内容发送。");SelectionContainer {Text(SnapshotCodec.encode(d),style=MaterialTheme.typography.bodySmall)};s.answers.takeLast(24).forEach {Text("${it.question.title}：${if(it.unknown)"不确定" else it.text.ifBlank {it.question.options.firstOrNull {o->o.id==it.optionId}?.label.orEmpty()}}",style=MaterialTheme.typography.bodySmall)}}
        if(s.changes.isNotEmpty())Details("已修改哪些内容") {s.changes.takeLast(12).reversed().forEach {Text("${it.description} · ${it.source}",style=MaterialTheme.typography.bodySmall)}}
        if(s.undo!=null)TextButton(onClick={model.undo()}) {Text("撤销最近一次修改")}
        Spacer(Modifier.height(20.dp))
    }
}
@Composable fun SuggestionScreen(model: AppModel) {
    val d=model.draft?:return;val reply=model.session?.pending?:return
    var selected by remember(reply) {mutableStateOf(reply.changes.map {it.path}.toSet())}
    var understood by remember(reply) {mutableStateOf(false)}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {ScreenTitle("先确认，理解对了吗？","勾选你认可的内容，再应用到共同草稿。");if(reply.summary.isNotBlank())Note(reply.summary)}
        items(reply.changes,key={it.path}) {change->Panel(FriendlyFields.path(d,change.path)) {Check("应用这项修改",change.path in selected) {selected=if(it)selected+change.path else selected-change.path};Text(suggestionValue(change.value),style=MaterialTheme.typography.titleMedium);Text(change.explanation);Text("来源：${when(change.source){"USER_ANSWER"->"根据你的回答";"PRESET"->"待确认的预设";else->"AI 估计，需要核实"}} · 确定程度：${translated(change.confidence.name)}",style=MaterialTheme.typography.bodySmall);Details("修改前后和完整字段") {Text("原值：${AssistantProtocol.read(d,change.path)}",style=MaterialTheme.typography.bodySmall);SelectionContainer {Text("建议值：${change.value}",style=MaterialTheme.typography.bodySmall)};Text(change.path,style=MaterialTheme.typography.bodySmall)}}}
        if(reply.unknownPaths.isNotEmpty())item {Note("仍需补充："+reply.unknownPaths.joinToString {FriendlyFields.path(d,it)},true)}
        item {Check("我已查看选择的改动，知道估计和预设不等于已核实事实",understood) {understood=it};WideButton("确认并应用 →",{model.acceptPending(selected)},understood&&!model.busy);TextButton(onClick={model.rejectPending()}) {Text("暂不应用，回到问答")}}
    }
}
private val manualSteps=listOf("我的情况","参照方案","需求重点","使用过程","性能变化","喜欢与愿付","每笔费用","参数检查","确认比较")
@Composable fun ManualScreen(model: AppModel) {
    val d=model.draft?:return
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {EnumSelect("编辑",manualSteps[model.step],manualSteps) {model.step=manualSteps.indexOf(it)};TextButton(onClick={model.page="interview"}) {Text("AI 引导")}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {TextButton(onClick={if(model.step>0)model.step--},enabled=model.step>0) {Text("上一步")};Text("${model.step+1}/9",Modifier.padding(top=12.dp),style=MaterialTheme.typography.labelMedium);TextButton(onClick={if(model.step<8)model.step++}) {Text("下一步")}}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(d.example)Note("这是示例案例，参数和报价用于演示。") else Note("请核实当前状态、性能变化和费用。默认预设会在计算前一起展示确认。")
            when(model.step) {
                0->SimpleBasicEditor(model,d)
                1->{ScreenTitle("和哪个方案比较？","选一个参照，其他方案的未来收支都与它比较。");d.strategies.forEach {s->Row(verticalAlignment=Alignment.CenterVertically) {RadioButton(selected=d.baselineId==s.id,onClick={model.update(d.copy(baselineId=s.id))});Text(s.name)}}}
                2->NeedEditor(d,model::update)
                3->{ScreenTitle("每个方案，接下来怎么用？","阶段要覆盖整个比较时间，维修与换新可以组合。");d.strategies.forEach {s->Panel(s.name) {s.stages.sortedBy {it.start}.forEach {st->Text("${st.start}～${st.end} 年 · ${st.name}（${translated(st.action.name)}）")};Text("主要评估：${s.stages.firstOrNull {it.id==s.primaryStageId}?.name}",style=MaterialTheme.typography.bodySmall)}};Details("编辑方案、阶段、维修与必须条件") {PathEditor(d,model::update)}}
                4->{Note("改善是否对你有用，取决于需求、实际差异，以及你是否能感受到。已有参数只作本次比较的固定参照。");TechnicalEditor(d,model::update)}
                5->SimpleEmotionEditor(d,model::update)
                6->SimpleCashEditor(d,model::update)
                7->{ValidationEditor(d,model::update);TextButton(onClick={model.saveDefaults()}) {Text("记住预算和通用参数")};TextButton(onClick={model.reuseDefaults()}) {Text("复用以前的预算和参数")}}
                8->{ScreenTitle("准备好比较了吗？","摘要会检查必要信息、展示参数，再保存一次独立计算。");WideButton("查看输入摘要",{model.page="summary"});AdvancedInput(d,model::update,model::notify)}
            }
            if(model.session?.undo!=null)TextButton(onClick={model.undo()}) {Text("撤销最近一次修改")}
            Spacer(Modifier.height(20.dp))
        }
    }
}
@Composable private fun SimpleBasicEditor(model: AppModel,d: DecisionInputSnapshot) {
    Field("这次想解决什么问题",d.title) {model.update(d.copy(title=it))};Field("你的使用情况",d.context) {model.update(d.copy(context=it))};Field("物品类别",d.category.name) {model.update(d.copy(category=d.category.copy(name=it)))}
    NumberField("比较未来多少年",d.horizon) {h->if(h>0&&d.strategies.all {it.stages.size==1})model.update(d.copy(horizon=h,strategies=d.strategies.map {s->s.copy(stages=s.stages.map {it.copy(end=h)},flows=s.flows.map {if(it.kind==CashKind.SALVAGE)it.copy(time=h) else it})})) else model.notify("多阶段方案请在使用过程中调整时间")}
    Check("限制现在最多花多少钱",d.budget!=null) {model.update(d.copy(budget=if(it)Money("0",d.currency) else null))};d.budget?.let {AmountField("初始支出预算",it) {value->model.update(d.copy(budget=value))}}
    TextButton(onClick={model.reuseDefaults()}) {Text("复用以前的预算和通用参数")}
    Details("编辑比较项目、单位和更多设置") {BasicEditor(d,model::update)}
}
@Composable private fun SimpleEmotionEditor(d: DecisionInputSnapshot,update: (DecisionInputSnapshot)->Unit) {
    Note("先分别想：只看功能最多愿付多少？把喜欢也算进去，最多愿付多少？两者的正差额用于表示喜欢带来的额外价值。")
    d.strategies.forEach {s->s.stages.forEach {st->Panel("${s.name} · ${st.name}") {
        fun change(e: Emotion) {update(d.replaceStrategy(s.replaceStage(st.copy(emotion=e))))}
        AmountField("只考虑功能的愿付金额",st.emotion.functional) {change(st.emotion.copy(functional=it,source="MANUAL"))};AmountField("考虑喜欢后的愿付金额",st.emotion.actual) {change(st.emotion.copy(actual=it,source="MANUAL"))}
        NumberField("额外愿付金额大约多少天减半",ln(2.0)/st.emotion.gamma*365.25) {days->if(days>0)change(st.emotion.copy(gamma=ln(2.0)*365.25/days,source="MANUAL"))}
        NumberField("降到初始额外愿付的多少比例时再考虑（0~1）",st.emotion.q) {change(st.emotion.copy(q=it,source="MANUAL"))}
        Details("来源和专业参数") {Field("信息来源",st.emotion.source) {change(st.emotion.copy(source=it))};Text("γ=${st.emotion.gamma} / 年；q=${st.emotion.q}",style=MaterialTheme.typography.bodySmall)}
    }}}
}
@Composable private fun SimpleCashEditor(d: DecisionInputSnapshot,update: (DecisionInputSnapshot)->Unit) {
    Note("每笔钱只记一次。这里按日常习惯填写正金额，应用区分花出去的钱和收到的钱。")
    d.strategies.forEach {s->Panel(s.name) {
        s.flows.forEachIndexed {index,f->
            fun change(v: CashFlow) {update(d.replaceStrategy(s.copy(flows=s.flows.mapIndexed {i,old->if(i==index)v else old})))}
            val income=f.kind in listOf(CashKind.INCOME,CashKind.SALVAGE) || f.kind==CashKind.OTHER&&f.money.decimal().signum()>0
            Field("费用或收入说明",f.note.ifBlank {translated(f.kind.name)}) {change(f.copy(note=it,source="MANUAL"))}
            if(f.kind!=CashKind.TIME)AmountField(if(income)"收到的钱" else "花出去的钱",Money(f.money.decimal().abs().toPlainString(),d.currency)) {m->if(m.decimal().signum()>=0)change(f.copy(money=Money((if(income)m.decimal() else m.decimal().negate()).toPlainString(),d.currency),source="MANUAL"))}
            if(f.kind==CashKind.SALVAGE)Text("评估结束时卖出，时间：${d.horizon} 年",style=MaterialTheme.typography.bodySmall) else NumberField("从现在起第几个月发生（现在=0）",f.time*12) {change(f.copy(time=it/12,source="MANUAL"))}
            TextButton(onClick={update(d.replaceStrategy(s.copy(flows=s.flows.filterIndexed {i,_->i!=index})))}) {Text("删除这笔记录")};HorizontalDivider()
        }
        Row {TextButton(onClick={val flow=CashFlow(java.util.UUID.randomUUID().toString(),0.0,Money("0",d.currency),CashKind.INITIAL,source="MANUAL");update(d.replaceStrategy(s.copy(flows=s.flows+flow)))}) {Text("添加支出")};TextButton(onClick={val flow=CashFlow(java.util.UUID.randomUUID().toString(),d.horizon,Money("0",d.currency),CashKind.SALVAGE,source="MANUAL");update(d.replaceStrategy(s.copy(flows=s.flows+flow)))}) {Text("添加期末卖出收入")}}
    }}
    Details("更多费用类型、概率和时间成本") {CashEditor(d,update)}
}
@Composable fun InputSummary(model: AppModel) {
    val d=model.draft?:return;val s=model.session?:return
    var checked by remember(d,s.pending,s.unknownPaths) {mutableStateOf(false)}
    val preview=remember(d) {runCatching {DecisionEngine().evaluate(d.copy(parameters=d.parameters.map {it.copy(confirmed=true)},needs=d.needs.map {it.copy(coefficientsConfirmed=true)}))}}
    LazyColumn(modifier=Modifier.testTag("input-summary-list"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {ScreenTitle("先确认，再比较","请核实真实状态、估计、报价，以及必须满足的条件。")}
        item {Panel("我的情况") {Fact("这次的问题",d.title);Fact("使用情况",d.context.ifBlank {"未补充"});Fact("物品类别",d.category.name);Fact("比较时间","${d.horizon} 年");Fact("初始预算",d.budget?.let {"${it.amount} ${it.currency}"}?:"不设预算");Fact("需求重点",d.needs.first().inputs.joinToString {"${d.category.dimensions.firstOrNull {dim->dim.id==it.dimensionId}?.name}：${it.weight} 份"});TextButton(onClick={model.switchManual()}) {Text("修改这些信息")}}}
        if(s.pending!=null)item {Note("还有建议等待确认，暂时不能正式计算。",true);WideButton("查看待确认建议",{model.page="review"})}
        if(s.unknownPaths.isNotEmpty())item {Panel("仍需补充的信息") {s.unknownPaths.forEach {path->Text(FriendlyFields.path(d,path));TextButton(onClick={model.switchManual();model.step=stepForPath(path)}) {Text("手动补齐这一项")}};TextButton(onClick={model.page="interview";model.askAi()}) {Text("让 AI 帮我补齐")}}}
        items(d.strategies,key={it.id}) {strategy->Panel(strategy.name) {
            Fact("主要评估物品",strategy.stages.firstOrNull {it.id==strategy.primaryStageId}?.name.orEmpty());Fact("主要升级对照",strategy.primaryUpgradeId?:"没有升级，相关指标不适用")
            strategy.stages.forEach {st->Text("${st.start}～${st.end} 年 · ${st.name}",style=MaterialTheme.typography.bodyMedium);Details("${st.name}：状态、变化、维修和喜欢") {st.utilities.forEach {u->Text("${d.category.dimensions.firstOrNull {it.id==u.dimensionId}?.name}：满足程度 ${u.initial}；逐渐变差 ${u.lambda}/年；来源 ${u.source}",style=MaterialTheme.typography.bodySmall)};Text("功能愿付 ${st.emotion.functional.amount}；实际愿付 ${st.emotion.actual.amount}；约 ${"%.1f".format(ln(2.0)/st.emotion.gamma*365.25)} 天减半；冷静比例 ${st.emotion.q}；来源 ${st.emotion.source}",style=MaterialTheme.typography.bodySmall);st.repairs.forEach {Text("${it.recovery.name} · 恢复比例 ${it.recovery.ratios} · 来源 ${it.recovery.source}",style=MaterialTheme.typography.bodySmall)};st.upgrade?.let {Text("当前性能 ${it.old} → 备选性能 ${it.new}；价格 ${it.oldPrice.amount} → ${it.newPrice.amount}；来源 ${it.source}",style=MaterialTheme.typography.bodySmall)}}}
            Details("费用、未来收入与必须条件") {if(strategy.flows.isEmpty())Text("未记录额外收支，请确认是否确实没有。");strategy.flows.forEach {f->Text("第 ${f.time*12} 个月 · ${f.note.ifBlank {translated(f.kind.name)}} · ${f.money.amount} ${f.money.currency} · 来源 ${f.source}",style=MaterialTheme.typography.bodySmall)};if(strategy.constraints.isEmpty())Text("未添加额外必须条件，请确认是否需要安全、法律或关键任务要求。");strategy.constraints.forEach {Text("${it.description}：${it.passed?.let {v->if(v)"满足" else "不满足"}?:"未确认"}")}}
        }}
        item {Panel("本次使用的通用参数") {d.parameters.forEach {p->Text("${FriendlyFields.parameter(p.key)}：${p.value?:p.selected?:"未选择标量"}");Text("来源 ${p.source} · 确定程度 ${translated(p.confidence.name)}",style=MaterialTheme.typography.bodySmall)};Details("比较尺度和技术感受的预设") {d.category.dimensions.forEach {dim->Text("${dim.name}：单位 ${dim.unit}；固定尺度 ${dim.reference}；最低要求 ${dim.threshold}；感知起点 +${dim.thetaPlus}/−${dim.thetaMinus}；敏感程度 ${dim.kPlus}/${dim.kMinus}；来源 ${dim.source}",style=MaterialTheme.typography.bodySmall)}};Details("需求系数与偏好设置") {d.needs.forEach {Text("第 ${it.time} 年：${it.method}；系数 ${it.coefficients}",style=MaterialTheme.typography.bodySmall)};Text(if(d.preference==null)"没有偏好配置，不生成综合分数" else "偏好权重 ${d.preference!!.weights}；固定尺度 ${d.preference!!.scales}；确认状态 ${d.preference!!.confirmed}",style=MaterialTheme.typography.bodySmall)}}}
        item {Note(preview.fold({"结构校验通过：${it.paretoIds.size} 个方案值得继续比较，${it.strategies.count {e->e.violations.isNotEmpty()}} 个方案存在必须条件问题。"},{"待修正：${FriendlyFields.error(it)}"}),preview.isFailure)}
        item {Check("我已核实以上状态、费用及当前显示的参数（包括估计、预设和需求系数）",checked) {checked=it};OutlinedButton(onClick={model.confirmInput()},enabled=checked&&s.pending==null&&s.unknownPaths.isEmpty()&&!model.busy,modifier=Modifier.fillMaxWidth()) {Text("确认这些输入和参数")};WideButton("计算并比较策略",{model.calculate()},s.reviewedHash==SnapshotCodec.hash(d)&&!model.busy);Text("修改后需要重新确认，旧计算结果不会被覆盖。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item {Details("填写记录和完整输入") {s.changes.takeLast(20).forEach {Text("${it.description} · ${it.source}",style=MaterialTheme.typography.bodySmall)};SelectionContainer {Text(SnapshotCodec.encode(d),style=MaterialTheme.typography.bodySmall)}}}
    }
}
private fun stepForPath(path: String) = when {path.startsWith("/needs")->2;path.contains("emotion")->5;path.contains("flows")->6;path.startsWith("/parameters")->7;path.startsWith("/strategies")->3;else->0}
fun lifeText(life: EffectiveLifeValue) = if(life.value!=null)"${"%.2f".format(life.value)} 年" else "至少 ${"%.2f".format(life.lowerBound)} 年（仅下界）"
@Composable fun ComparisonScreen(model: AppModel,row: EvaluationRow) {
    LaunchedEffect(row.id) {model.loadExplanation(row)}
    val result=remember(row.id) {SnapshotCodec.decode<DecisionEvaluation>(row.resultJson)}
    val ordered=remember(result) {if(result.rankedIds.isEmpty())result.strategies else result.strategies.sortedBy {result.rankedIds.indexOf(it.id).takeIf {i->i>=0}?:Int.MAX_VALUE}}
    val input=remember(row.id) {SnapshotCodec.decode<DecisionInputSnapshot>(row.inputJson)}
    var chosen by remember(row.id) {mutableStateOf<String?>(null)}
    var reason by remember(row.id) {mutableStateOf("")}
    var scenario by remember {mutableStateOf(false)}
    LazyColumn(modifier=Modifier.testTag("result-list"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {ScreenTitle(input.title,"先看必须条件，再看各个方案的优势和代价。");Text("${result.strategies.count {it.violations.isEmpty()}} 个可行 · ${result.paretoIds.size} 个值得继续比较",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=12.dp));Note(if(result.rankedIds.isEmpty())"未启用偏好排序，不生成自动综合分数。" else "已按你确认的偏好，对值得比较的方案排序。")}
        if(result.rankedIds.isNotEmpty())item {Note("超过评估时间的寿命在排序中采用下界，不能把分数解读为精确寿命排名。",true)}
        items(ordered,key={it.id}) {s->val strategy=input.strategies.first {it.id==s.id};Panel(s.name) {
            result.rankedIds.indexOf(s.id).takeIf {it>=0}?.let {Text("你的偏好排序 · 第 ${it+1} 位",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}
            Text(when {s.violations.isNotEmpty()->"必须条件未通过";s.dominatedBy.isNotEmpty()->"在共同指标下，有其他方案明确优于它";else->"各有优势，值得继续比较"},color=if(s.violations.isNotEmpty())MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            val initial=strategy.flows.filter {it.time==0.0}.sumOf {Model6.amount(it).min(BigDecimal.ZERO).negate()}
            Fact("现在实际支出","${initial.toPlainString()} ${input.currency}")
            Fact("还能满足需求多久",lifeText(s.z.life))
            Fact("喜欢的额外愿付","${s.z.emotion.amount} ${input.currency}")
            Fact("对需求有用的改善","%.4f".format(s.meta.gRel))
            Fact("相对参照的未来收支","${"%.2f".format(s.z.deltaNpv.decimal())} ${input.currency}")
            Text("金额按共同时间折算到现在；寿命基于当前输入估计。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            s.violations.forEach {Text(translatedViolation(it),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
            s.score?.let {Text("已确认偏好分数 ${"%.4f".format(it)}；贡献 ${s.contributions.joinToString {v->"%.4f".format(v)}}")}
            Details("使用过程与六模型明细") {
                Text("主要评估：${strategy.stages.first {it.id==s.primaryStageId}.name}；主升级 ${strategy.primaryUpgradeId?:"不适用"}")
                strategy.stages.sortedBy {it.start}.forEach {Text("${it.start}～${it.end} 年 → ${it.name}")}
                Text("技术变化（保留变差）：${s.z.iteration}；需求匹配：${s.z.alignment}；溢价与收益关系：${s.z.rho.value?:translated(s.z.rho.status)}；全路径未来净收支：${s.m6.npv.amount} ${input.currency}",style=MaterialTheme.typography.bodySmall)
                s.stages.forEach {st->Text("${strategy.stages.first {it.id==st.stageId}.name}：${lifeText(st.m1.life)}；额外愿付 ${st.m3.wEmo.amount}；约 ${"%.1f".format(st.m3.halfLife*365.25)} 天减半，${"%.1f".format(st.m3.coolDown*365.25)} 天达到冷静比例。",style=MaterialTheme.typography.bodySmall)}
                Note("主物品与主升级用于寿命、情感及技术比较；费用计算整条路径，服务要求检查所有阶段。多阶段扩展 ${result.config.projectionVersion}。")
                Details("公式、来源和完整计算过程") {s.meta.traces.forEach {trace->Text("${trace.model} · ${trace.formula}",style=MaterialTheme.typography.titleSmall);SelectionContainer {Text(trace.values.entries.joinToString("\n") {"${it.key}: ${it.value}"},style=MaterialTheme.typography.bodySmall)}};s.meta.warnings.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}}
            }
            WideButton("记录我的真实选择",{chosen=s.id},!model.busy)
        }}
        item {Panel("用日常语言理解结果") {if(model.explanation.isNotBlank())Text(model.explanation);Text("AI 仅辅助阅读，实际计算数值和必须条件以本页明细为准。",style=MaterialTheme.typography.bodySmall);OutlinedButton(onClick={model.explainResult()},enabled=!model.busy,modifier=Modifier.fillMaxWidth()) {Text("让 AI 解读这次结果")}}}
        item {TextButton(onClick={scenario=true}) {Text("看看估计变化会不会影响结果")};TextButton(onClick={model.open(row.caseId)}) {Text("修改输入，创建新计算")};Details("版本与输入标识") {Text("理论 ${result.config.theoryVersion} · 引擎 ${result.config.engineVersion} · 应用 ${result.config.appVersion}");SelectionContainer {Text(row.inputHash,style=MaterialTheme.typography.bodySmall)}}}
    }
    if(chosen!=null)AlertDialog(onDismissRequest={chosen=null},title={Text("记录这次选择")},text={OutlinedTextField(reason,{reason=it},label={Text("原因或感受（可选）")},modifier=Modifier.fillMaxWidth())},confirmButton={TextButton(onClick={model.choose(chosen!!,reason);chosen=null}) {Text("保存选择")}},dismissButton={TextButton(onClick={chosen=null}) {Text("取消")}})
    if(scenario)AlertDialog(onDismissRequest={scenario=false},title={Text("建立两个估计情景")},text={Text("以当前输入为中心，分别把性能和情感衰减速度减慢或加快 20%。这是假设，不是自动选取区间值。将新建草稿供你确认，费用和旧结果保留。")},confirmButton={TextButton(onClick={scenario=false;model.scenario(1.2)}) {Text("变差快 20%")}},dismissButton={TextButton(onClick={scenario=false;model.scenario(0.8)}) {Text("变差慢 20%")}})
}
private fun translatedViolation(value: String) = value.replace("BUDGET:","预算：").replace("SERVICE_FEASIBILITY:","使用要求：").replace("CRITICAL_TASK:","关键任务：").replace("SAFETY:","安全：").replace("LEGAL:","法律：").replace("CUSTOM_BOOLEAN:","必须条件：")
private fun suggestionValue(value: JsonElement): String = when(value) {JsonNull->"不设置 / 不适用（请确认）";is JsonPrimitive->value.content;is JsonArray->"${value.size} 项内容，请展开核实完整字段";is JsonObject->if("amount" in value)"${value["amount"]?.jsonPrimitive?.content} ${value["currency"]?.jsonPrimitive?.content.orEmpty()}" else "${value.size} 个字段，请展开核实完整内容"}
@Composable fun HistoryScreen(model: AppModel) {
    val rows by model.evaluations.collectAsStateWithLifecycle();val choices by model.choices.collectAsStateWithLifecycle()
    val format=remember {DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {ScreenTitle("我的决策记录","保留当时的输入，也保留之后的选择。")}
        if(rows.isEmpty())item {Note("还没有正式计算。首页的草稿可以随时继续。")}
        items(rows,key={it.id}) {row->val input=remember(row.id) {SnapshotCodec.decode<DecisionInputSnapshot>(row.inputJson)};Card(onClick={model.show(row)},colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(input.title,style=MaterialTheme.typography.titleMedium);Text("${format.format(Instant.ofEpochMilli(row.createdAt))} · ${if(input.example)"示例" else "个人决策"}",style=MaterialTheme.typography.bodySmall);choices.filter {it.evaluationId==row.id}.forEach {choice->Text("真实选择：${input.strategies.firstOrNull {it.id==choice.strategyId}?.name}；${choice.reason}",style=MaterialTheme.typography.bodySmall)}}}}
    }
}
@Composable fun AssetsScreen(model: AppModel) {
    val rows by model.assets.collectAsStateWithLifecycle()
    var reuse by remember {mutableStateOf<AssetRow?>(null)}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {ScreenTitle("我的物品","记录一次，下次复用前确认状态是否变化。")}
        model.draft?.let {d->item {Panel("保存当前草稿里的物品") {d.strategies.firstOrNull {it.id==d.baselineId}?.stages?.forEach {st->TextButton(onClick={model.saveAsset(st.name,st)}) {Text("保存 ${st.name} 与需求习惯")}}}}}
        if(rows.isEmpty())item {Note("还没有保存的物品。填写决策后，可以保存当前物品和使用习惯。")}
        items(rows,key={it.id}) {row->Panel(row.name) {val asset=remember(row.json) {SnapshotCodec.decode<AssetProfile>(row.json)};Text("${asset.category.name} · ${asset.stage.utilities.size} 个比较项目");Text("状态与使用习惯来自之前的确认，需要重新核实。",style=MaterialTheme.typography.bodySmall);WideButton("用这件物品开始决策",{reuse=row})}}
    }
    reuse?.let {row->AlertDialog(onDismissRequest={reuse=null},title={Text("复用前请确认")},text={Text("将复制物品状态、需求习惯和通用参数到新草稿。请重新检查当前状态，并补充备选方案；参数不会自动确认。")},confirmButton={TextButton(onClick={reuse=null;model.reuseAsset(row)}) {Text("复用并检查")}},dismissButton={TextButton(onClick={reuse=null}) {Text("取消")}})}
}
@Composable fun SettingsScreen(model: AppModel,export: ()->Unit,import: ()->Unit) {
    var config by remember(model.settings) {mutableStateOf(model.settings)}
    var visible by remember {mutableStateOf(false)}
    var delete by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        ScreenTitle("配置我的 AI","每个人使用自己的服务和密钥，优先适配 DeepSeek。")
        Panel("DeepSeek / 兼容接口") {Field("服务地址（HTTPS）",config.endpoint) {config=config.copy(endpoint=it)};Field("模型名称",config.model) {config=config.copy(model=it)};OutlinedTextField(config.apiKey,{config=config.copy(apiKey=it)},label={Text("个人 API 密钥")},visualTransformation=if(visible)androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),trailingIcon={IconButton(onClick={visible=!visible}) {Icon(if(visible)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(visible)"隐藏密钥" else "显示密钥")}},modifier=Modifier.fillMaxWidth(),singleLine=true);Check("启用 AI 引导",config.enabled) {config=config.copy(enabled=it)};Check("使用 JSON 输出（兼容接口可关闭）",config.jsonMode) {config=config.copy(jsonMode=it)}}
        Note("启用后，当前决策草稿和最近回答会发送到你指定的服务，由该服务处理并可能产生 API 费用。密钥只加密保存在本机，不进入源码或决策备份。未配置或断网时仍可手动填写与本地计算。")
        Check("我理解并同意将当前决策信息发送到所配置的 AI 服务",config.consent) {config=config.copy(consent=it)}
        WideButton("保存 AI 配置",{model.saveSettings(config)},!model.busy)
        OutlinedButton(onClick={model.saveSettings(config,test=true)},enabled=!model.busy,modifier=Modifier.fillMaxWidth()) {Text("保存并测试连接")}
        if(model.draft!=null)TextButton(onClick={model.page="interview"}) {Text("返回问答 →")}
        TextButton(onClick={delete=true}) {Text("删除密钥和配置")}
        Panel("备份与数据") {Text("导出包含正式快照、选择、问答、物品和复用参数，不包含 AI 密钥。旧版备份也可导入。",style=MaterialTheme.typography.bodySmall);OutlinedButton(onClick=export,enabled=!model.busy,modifier=Modifier.fillMaxWidth()) {Text("导出 JSON")};OutlinedButton(onClick=import,enabled=!model.busy,modifier=Modifier.fillMaxWidth()) {Text("导入 JSON")}}
        Text("应用 V0.2 · 理论 V4.2.1 · 计算仍在本机完成",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(20.dp))
    }
    if(delete)AlertDialog(onDismissRequest={delete=false},title={Text("删除本机 AI 配置？")},text={Text("决策和计算历史会保留。之后可重新填写自己的密钥。")},confirmButton={TextButton(onClick={model.clearSettings();delete=false}) {Text("删除")}},dismissButton={TextButton(onClick={delete=false}) {Text("取消")}})
}
