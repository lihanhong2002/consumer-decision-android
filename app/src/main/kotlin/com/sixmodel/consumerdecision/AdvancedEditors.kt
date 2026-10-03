package com.sixmodel.consumerdecision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.sixmodel.core.*
import com.sixmodel.consumerdecision.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun Field(label: String,value: String,onChange: (String)->Unit) { OutlinedTextField(value,onValueChange=onChange,label={Text(plainLabel(label))},modifier=Modifier.fillMaxWidth(),singleLine=true) }
@Composable fun NumberField(label: String,value: Double,onChange: (Double)->Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column {OutlinedTextField(text,{ text=it; it.toDoubleOrNull()?.takeIf { v -> v.isFinite() }?.let(onChange) },label={Text(plainLabel(label))},singleLine=true,isError=text.toDoubleOrNull()?.isFinite()!=true,modifier=Modifier.fillMaxWidth());fieldHelp(label)?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))}}
}
@Composable fun AmountField(label: String,value: Money,onChange: (Money)->Unit) {
    var text by remember(value) { mutableStateOf(value.amount) }
    OutlinedTextField(text,{ text=it; if(it.toBigDecimalOrNull()!=null) onChange(value.copy(amount=it)) },label={Text("$label (${value.currency})")},singleLine=true,isError=text.toBigDecimalOrNull()==null,modifier=Modifier.fillMaxWidth())
}
@Composable fun Check(label: String,value: Boolean,onChange: (Boolean)->Unit) { Row(Modifier.fillMaxWidth().clickable {onChange(!value)}) { Checkbox(value,onCheckedChange=onChange); Text(plainLabel(label),modifier=Modifier.weight(1f).padding(top=12.dp,bottom=12.dp)) } }
@Composable fun Panel(title: String,content: @Composable ColumnScope.()->Unit) { Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(plainLabel(title),style=MaterialTheme.typography.titleMedium); content() } } }
fun DecisionInputSnapshot.replaceStrategy(s: Strategy)=copy(strategies=strategies.map { if(it.id==s.id) s else it })
fun Strategy.replaceStage(s: Stage)=copy(stages=stages.map { if(it.id==s.id) s else it })

@Composable fun BasicEditor(d: DecisionInputSnapshot,update: (DecisionInputSnapshot)->Unit) {
    Field("标题",d.title) { update(d.copy(title=it)) }; Field("情境 / 当前任务",d.context) { update(d.copy(context=it)) }
    Field("品类",d.category.name) { update(d.copy(category=d.category.copy(name=it))) }
    NumberField("规划期 H（年）",d.horizon) { h -> if(h>0) update(d.copy(horizon=h,strategies=d.strategies.map { s -> s.copy(stages=s.stages.mapIndexed { index,stage -> if(index==s.stages.lastIndex) stage.copy(end=h) else stage }) })) }
    EnumSelect("费用统计方式",if(kotlin.math.abs(d.cashStep-1.0/12)<1e-9)"按月" else if(d.cashStep==1.0)"按年" else "自定义",listOf("按月","按年","自定义")) {if(it!="自定义")update(d.copy(cashStep=if(it=="按月")1.0/12 else 1.0))}
    Details("自定义费用时间间隔") {NumberField("每个统计间隔是多少年",d.cashStep) {update(d.copy(cashStep=it))}}
    Field("币种（如 CNY，不自动换算金额）",d.currency) { value -> if(value.matches(Regex("[A-Z]{3}"))) update(d.copy(currency=value,budget=d.budget?.copy(currency=value),totalBudget=d.totalBudget?.copy(currency=value),strategies=d.strategies.map { s -> s.copy(flows=s.flows.map { it.copy(money=it.money.copy(currency=value),hourlyRate=it.hourlyRate?.copy(currency=value)) },stages=s.stages.map { st -> st.copy(emotion=st.emotion.copy(functional=st.emotion.functional.copy(currency=value),actual=st.emotion.actual.copy(currency=value)),upgrade=st.upgrade?.let { it.copy(oldPrice=it.oldPrice.copy(currency=value),newPrice=it.newPrice.copy(currency=value)) }) }) })) }
    Check("设置初始支出预算",d.budget!=null) { update(d.copy(budget=if(it) Money("0",d.currency) else null)) }; d.budget?.let { AmountField("初始预算",it) { value -> update(d.copy(budget=value)) } }
    Check("另设全期累计支出上限",d.totalBudget!=null) { update(d.copy(totalBudget=if(it) Money("0",d.currency) else null)) }; d.totalBudget?.let { AmountField("全期上限（不抵扣收入）",it) { value -> update(d.copy(totalBudget=value)) } }
    Text("品类维度与固定参考尺度",style=MaterialTheme.typography.titleMedium)
    d.category.dimensions.forEach { dimension -> Panel(dimension.name) {
        fun replace(v: Dimension) { update(d.copy(category=d.category.copy(dimensions=d.category.dimensions.map { if(it.id==v.id) v else it }))) }
        Field("维度名称",dimension.name) { replace(dimension.copy(name=it)) }; Field("单位",dimension.unit) { replace(dimension.copy(unit=it)) }
        NumberField("固定参考尺度 R",dimension.reference) { replace(dimension.copy(reference=it)) }; Check("数值越小越好",dimension.lowerBetter) { replace(dimension.copy(lowerBetter=it)) }
        Field("尺度/阈值参数来源",dimension.source) { replace(dimension.copy(source=it)) }; EnumSelect("置信度",dimension.confidence.name,Confidence.entries.map { it.name }) { replace(dimension.copy(confidence=Confidence.valueOf(it))) }
        Check("关键任务维度",dimension.critical) { replace(dimension.copy(critical=it)) }; NumberField("关键阈值 τ",dimension.threshold) { replace(dimension.copy(threshold=it)) }
        if(d.category.dimensions.size>1) TextButton(onClick={ val id=dimension.id; update(d.copy(category=d.category.copy(dimensions=d.category.dimensions.filter { it.id!=id }),needs=d.needs.map { it.copy(inputs=it.inputs.filter { it.dimensionId!=id }) },strategies=d.strategies.map { s -> s.copy(stages=s.stages.map { it.copy(utilities=it.utilities.filter { it.dimensionId!=id },upgrade=null,repairs=emptyList(),allowedRecoveries=emptyList()) },primaryUpgradeId=null) })) }) { Text("移除此维度（清除旧升级/维修配置）") }
    } }
    OutlinedButton(onClick={ val dim=Dimension(UUID.randomUUID().toString(),"新维度"); update(d.copy(category=d.category.copy(dimensions=d.category.dimensions+dim),needs=d.needs.map { it.copy(inputs=it.inputs+NeedInput(dim.id)) },strategies=d.strategies.map { s -> s.copy(stages=s.stages.map { it.copy(utilities=it.utilities+Utility(dim.id,lambda=0.0),upgrade=null) },primaryUpgradeId=null) })) }) { Text("添加维度") }
}

@Composable fun NeedEditor(d: DecisionInputSnapshot,update: (DecisionInputSnapshot)->Unit) {
    Text("先描述你怎样使用、有什么问题，以及哪些事情不能出错，再决定各项需求的重要程度。")
    d.needs.forEachIndexed { index,point -> Panel("需求节点 ${index+1}（${point.time} 年）") {
        fun replace(p: NeedPoint) { update(d.copy(needs=d.needs.mapIndexed { i,v -> if(i==index) p else v })) }
        if(index>0) NumberField("生效时间（年）",point.time) { replace(point.copy(time=it)) }
        EnumSelect("怎么设置重要程度",point.method.name,NeedMethod.entries.map {it.name}) {replace(point.copy(method=NeedMethod.valueOf(it)))}
        point.inputs.forEach { i ->
            fun change(v: NeedInput) { replace(point.copy(inputs=point.inputs.map { if(it.dimensionId==v.dimensionId) v else it })) }
            Text(d.category.dimensions.first { it.id==i.dimensionId }.name)
            if(point.method==NeedMethod.MANUAL_USER_SIDE) NumberField("原始权重（自动归一化）",i.weight) { change(i.copy(weight=it)) } else {
                NumberField("频率 F [0,1]",i.frequency) { change(i.copy(frequency=it)) }; NumberField("痛点 P [0,1]",i.pain) { change(i.copy(pain=it)) }; NumberField("重要度 M [0,1]",i.importance) { change(i.copy(importance=it)) }; NumberField("可靠性 R [0,1]",i.reliability) { change(i.copy(reliability=it)) }
            }
        }
        if(point.method!=NeedMethod.MANUAL_USER_SIDE) {
            val coefficients=if(point.coefficients.size==4) point.coefficients else List(4) { 0.0 }
            listOf("F","P","M","R").forEachIndexed { i,label -> NumberField("个人校准系数 $label",coefficients[i]) { value -> replace(point.copy(coefficients=coefficients.mapIndexed { j,v -> if(i==j) value else v },coefficientsConfirmed=false)) } }
            Check("确认这些系数来自我的设定/校准",point.coefficientsConfirmed) { replace(point.copy(coefficientsConfirmed=it)) }
        }
        val normalized=runCatching { NeedGenerator.generate(point,d.category.dimensions) }; Text(normalized.fold({ "换算后的重要程度："+it.weights.entries.joinToString { (id,w) -> "${d.category.dimensions.first { it.id==id }.name} ${"%.1f".format(w*100)}%" } },{FriendlyFields.error(it)}))
        if(index>0) TextButton(onClick={update(d.copy(needs=d.needs.filterIndexed { i,_ -> i!=index }))}) { Text("删除节点") }
    } }
    OutlinedButton(onClick={update(d.copy(needs=d.needs+d.needs.last().copy(time=(d.needs.last().time+d.horizon)/2)))}) { Text("添加未来需求节点") }
}

@Composable fun PathEditor(d: DecisionInputSnapshot,update: (DecisionInputSnapshot)->Unit) {
    Text("每段使用时间要接得上。维修仍是同一件物品，换新是另一件；选定主要评估哪件物品，费用仍计算整个过程。")
    d.strategies.forEach { s -> Panel(s.name) {
        fun replace(v: Strategy) { update(d.replaceStrategy(v)) }
        Field("策略名称",s.name) { replace(s.copy(name=it)) }
        TextButton(onClick={replace(s.copy(stages=s.stages + s.stages.last().let { st -> val midpoint=(st.start+st.end)/2; st.copy(id=UUID.randomUUID().toString(),assetId=UUID.randomUUID().toString(),name="新资产阶段",start=midpoint,utilities=d.category.dimensions.map { Utility(it.id,lambda=0.0) },upgrade=null,repairs=emptyList(),allowedRecoveries=emptyList()) }, primaryUpgradeId=s.primaryUpgradeId).let { next -> val added=next.stages.last(); next.copy(stages=next.stages.mapIndexed { i,st -> if(i==next.stages.lastIndex-1) st.copy(end=added.start) else st }) })}) { Text("拆分末段并添加新资产") }
        s.stages.forEach { st -> Panel(st.name) {
            fun stage(v: Stage) { replace(s.replaceStage(v)) }
            Field("阶段名称",st.name) { stage(st.copy(name=it)) }; Field("资产身份 ID",st.assetId) { stage(st.copy(assetId=it)) }
            Row { RadioButton(s.primaryStageId==st.id,{replace(s.copy(primaryStageId=st.id))}); Text("设为主资产 / 情感对照",modifier=Modifier.padding(top=12.dp)) }
            NumberField("开始时间（年）",st.start) { stage(st.copy(start=it)) }; NumberField("结束时间（年）",st.end) { stage(st.copy(end=it)) }
            EnumSelect("动作类型",st.action.name,Action.entries.map { it.name }) { stage(st.copy(action=Action.valueOf(it))) }
            st.utilities.forEach { u ->
                fun utility(v: Utility) { stage(st.copy(utilities=st.utilities.map { if(it.dimensionId==v.dimensionId) v else it })) }
                Text(d.category.dimensions.first { it.id==u.dimensionId }.name)
                NumberField("开始时满足需求的程度（0~1）",u.initial) { utility(u.copy(initial=it)) }
                NumberField("一年后相对减少多少（百分比）",(1-kotlin.math.exp(-u.lambda))*100) {if(it>=0&&it<100)utility(u.copy(lambda=-kotlin.math.ln(1-it/100),source="MANUAL"))}
                NumberField("每年固定减少的程度（0~1）",u.mu) {utility(u.copy(mu=it,source="MANUAL"))}
                EnumSelect("衰减模式",u.decay.name,Decay.entries.map { it.name }) { utility(u.copy(decay=Decay.valueOf(it))) }
                Field("效用/衰减参数来源",u.source) { utility(u.copy(source=it)) }; EnumSelect("置信度",u.confidence.name,Confidence.entries.map { it.name }) { utility(u.copy(confidence=Confidence.valueOf(it))) }
            }
            st.repairs.forEachIndexed { ri,repair -> Panel("维修动作 ${ri+1}") {
                fun change(r: RepairEvent) { stage(st.copy(repairs=st.repairs.mapIndexed { i,v -> if(i==ri) r else v })) }
                NumberField("相对阶段开始时间（年）",repair.time) { change(repair.copy(time=it)) }
                Field("动作名称",repair.recovery.name) { change(repair.copy(recovery=repair.recovery.copy(name=it))) }
                Field("恢复参数来源",repair.recovery.source) { change(repair.copy(recovery=repair.recovery.copy(source=it))) }
                EnumSelect("置信度",repair.recovery.confidence.name,Confidence.entries.map { it.name }) { change(repair.copy(recovery=repair.recovery.copy(confidence=Confidence.valueOf(it)))) }
                Field("关联现金流 ID（费用仅在现金流录入一次）",repair.cashFlowId ?: "") { change(repair.copy(cashFlowId=it.takeIf { it.isNotBlank() })) }
                d.category.dimensions.forEach { dim -> NumberField("${dim.name} 恢复比例 q",repair.recovery.ratios[dim.id] ?: 0.0) { change(repair.copy(recovery=repair.recovery.copy(ratios=repair.recovery.ratios+(dim.id to it)))) } }
                Check("改变核心身份（将被拒绝为维修）",repair.recovery.changesCoreIdentity) { change(repair.copy(recovery=repair.recovery.copy(changesCoreIdentity=it))) }
                TextButton(onClick={stage(st.copy(repairs=st.repairs.filterIndexed { i,_ -> i!=ri }))}) { Text("删除维修") }
                TextButton(onClick={stage(st.copy(allowedRecoveries=st.allowedRecoveries.filter { it.id!=repair.recovery.id }+repair.recovery))}) { Text("将此动作加入 M1 可用恢复方案") }
            } }
            Text("M1可用恢复方案：${st.allowedRecoveries.joinToString { it.name }}")
            if(st.allowedRecoveries.isNotEmpty()) TextButton(onClick={stage(st.copy(allowedRecoveries=emptyList()))}) { Text("清除可用恢复方案") }
            TextButton(onClick={ val id=UUID.randomUUID().toString(); val recovery=Recovery(id,"维修",d.category.dimensions.associate { it.id to 0.0 }); stage(st.copy(repairs=st.repairs+RepairEvent(0.0,recovery))) }) { Text("添加维修动作") }
            if(s.stages.size>1) TextButton(onClick={ val remaining=s.stages.filter { it.id!=st.id }; replace(s.copy(stages=remaining,primaryStageId=if(s.primaryStageId==st.id) remaining.first().id else s.primaryStageId,primaryUpgradeId=null)) }) { Text("删除阶段（需手动修复时间覆盖）") }
        } }
        s.constraints.forEachIndexed { ci,c ->
            Field("${c.type} 要求",c.description) { text -> replace(s.copy(constraints=s.constraints.mapIndexed { i,v -> if(i==ci) v.copy(description=text) else v })) }
            EnumSelect("检查结果",c.passed?.toString() ?: "未确认",listOf("未确认","true","false")) { value -> replace(s.copy(constraints=s.constraints.mapIndexed { i,v -> if(i==ci) v.copy(passed=value.toBooleanStrictOrNull()) else v })) }
        }
        listOf("CRITICAL_TASK","SAFETY","LEGAL","CUSTOM_BOOLEAN").forEach { type -> TextButton(onClick={replace(s.copy(constraints=s.constraints+BooleanConstraint(UUID.randomUUID().toString(),type,"请填写要求")))}) { Text("添加${translated(type)}") } }
        if(d.strategies.size>2 && s.id!=d.baselineId) TextButton(onClick={update(d.copy(strategies=d.strategies.filter { it.id!=s.id }))}) { Text("删除策略") }
    } }
    OutlinedButton(onClick={val st=Stage(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"资产阶段",start=0.0,end=d.horizon,utilities=d.category.dimensions.map { Utility(it.id,lambda=0.0) },emotion=Emotion(Money(currency=d.currency),Money(currency=d.currency))); update(d.copy(strategies=d.strategies+Strategy(UUID.randomUUID().toString(),"新策略",listOf(st),st.id)))}) { Text("添加策略") }
}
fun legacyTranslated(value: String): String = mapOf("CONTINUE_USE" to "继续使用","REPAIR" to "维修","PURCHASE" to "购买","RENT" to "租赁","SUBSTITUTE" to "替代","WAIT_THEN_PURCHASE" to "等待后购买","MULTI_STAGE" to "多阶段组合","LOW" to "低","MEDIUM" to "中","MEDIUM_HIGH" to "中高","HIGH" to "高","true" to "满足","false" to "不满足","NOT_APPLICABLE" to "不适用","NO_POSITIVE_RELEVANT_UPGRADE" to "没有正向且对需求有用的升级")[value] ?: value
@Composable fun EnumSelect(label: String,value: String,options: List<String>,change: (String)->Unit) { var open by remember { mutableStateOf(false) }; Box { OutlinedButton(onClick={open=true}) { Text("${plainLabel(label)}：${translated(value)}") }; DropdownMenu(open,{open=false}) { options.forEach { option -> DropdownMenuItem(text={Text(translated(option))},onClick={change(option); open=false}) } } } }

@Composable fun TechnicalEditor(d: DecisionInputSnapshot,update: (DecisionInputSnapshot)->Unit) {
    Text("用固定单位衡量改善幅度。例如续航增加几小时，再考虑你能否感受到，以及现在是否已经够用。比较单位不随备选商品变化。")
    d.category.dimensions.forEach { dim -> Panel("${dim.name} 感知参数") {
        fun change(v: Dimension) { update(d.copy(category=d.category.copy(dimensions=d.category.dimensions.map { if(it.id==dim.id) v else it }))) }
        NumberField("正向阈值 θ+",dim.thetaPlus) { change(dim.copy(thetaPlus=it)) }; NumberField("负向阈值 θ−",dim.thetaMinus) { change(dim.copy(thetaMinus=it)) }; NumberField("正向斜率 k+",dim.kPlus) { change(dim.copy(kPlus=it)) }; NumberField("负向斜率 k−",dim.kMinus) { change(dim.copy(kMinus=it)) }
    } }
    d.strategies.forEach { s -> s.stages.forEach { st -> Panel("${s.name} / ${st.name}") {
        fun change(v: Stage) { update(d.replaceStrategy(s.replaceStage(v))) }
        Check("存在商品升级对照",st.upgrade!=null) { enabled -> change(st.copy(upgrade=if(enabled) Upgrade(UUID.randomUUID().toString(),d.category.dimensions.associate { it.id to 0.0 },d.category.dimensions.associate { it.id to 0.0 },d.category.dimensions.associate { it.id to 0.5 },oldPrice=Money(currency=d.currency),newPrice=Money(currency=d.currency),units=d.category.dimensions.associate { it.id to it.unit }) else null)); if(!enabled && s.primaryUpgradeId==st.upgrade?.id) update(d.replaceStrategy(s.replaceStage(st.copy(upgrade=null)).copy(primaryUpgradeId=null))) }
        st.upgrade?.let { u ->
            fun upgrade(v: Upgrade) { change(st.copy(upgrade=v)) }
            Row { RadioButton(s.primaryUpgradeId==u.id,{update(d.replaceStrategy(s.copy(primaryUpgradeId=u.id)))}); Text("设为策略主升级对照",modifier=Modifier.padding(top=12.dp)) }
            NumberField("技术间隔 Δt（年）",u.years) { upgrade(u.copy(years=it)) }; AmountField("基准商品价格",u.oldPrice) { upgrade(u.copy(oldPrice=it)) }; AmountField("新商品价格",u.newPrice) { upgrade(u.copy(newPrice=it)) }
            Field("商品数据来源",u.source) { upgrade(u.copy(source=it)) }; EnumSelect("置信度",u.confidence.name,Confidence.entries.map { it.name }) { upgrade(u.copy(confidence=Confidence.valueOf(it))) }
            EnumSelect("激活模式",u.activation.name,Activation.entries.map { it.name }) { upgrade(u.copy(activation=Activation.valueOf(it))) }
            d.category.dimensions.forEach { dim -> Text("${dim.name} (${dim.unit})"); NumberField("旧指标",u.old[dim.id] ?: 0.0) { upgrade(u.copy(old=u.old+(dim.id to it),units=u.units+(dim.id to dim.unit))) }; NumberField("新指标",u.new[dim.id] ?: 0.0) { upgrade(u.copy(new=u.new+(dim.id to it),units=u.units+(dim.id to dim.unit))) }; NumberField("满意度 S [0,1]",u.saturation[dim.id] ?: 0.5) { upgrade(u.copy(saturation=u.saturation+(dim.id to it))) } }
            Check("使用真实比例尺度 Q",u.ratioOldQuality!=null) { upgrade(u.copy(ratioOldQuality=if(it) 1.0 else null,ratioNewQuality=if(it) 1.0 else null)) }
            u.ratioOldQuality?.let { q -> NumberField("Q_old",q) { upgrade(u.copy(ratioOldQuality=it)) }; NumberField("Q_new",u.ratioNewQuality ?: 1.0) { upgrade(u.copy(ratioNewQuality=it)) } }
        }
    } } }
}

@Composable fun EmotionEditor(d: DecisionInputSnapshot,update: (DecisionInputSnapshot)->Unit) {
    Text("WTP 驱动模式：情感价值=max(0,实际愿付−功能愿付)，不加入现金流。")
    d.strategies.forEach { s -> s.stages.forEach { st -> Panel("${s.name} / ${st.name}") {
        fun change(v: Emotion) { update(d.replaceStrategy(s.replaceStage(st.copy(emotion=v)))) }
        AmountField("同功能普通版本愿付",st.emotion.functional) { change(st.emotion.copy(functional=it)) }; AmountField("实际版本愿付",st.emotion.actual) { change(st.emotion.copy(actual=it)) }
        NumberField("情感衰减 γ（/年）",st.emotion.gamma) { change(st.emotion.copy(gamma=it)) }; NumberField("冷静期剩余比例 q (0,1)",st.emotion.q) { change(st.emotion.copy(q=it)) }
        Field("情感参数来源",st.emotion.source) { change(st.emotion.copy(source=it)) }; EnumSelect("置信度",st.emotion.confidence.name,Confidence.entries.map { it.name }) { change(st.emotion.copy(confidence=Confidence.valueOf(it))) }
        Text("γ 初值来自规格，仅为可编辑初始值；请结合个人经验确认。")
    } } }
}

@Composable fun CashEditor(d: DecisionInputSnapshot,update: (DecisionInputSnapshot)->Unit) {
    Text("仅记录实际成本/收入。支出为负、收入为正；不得重复记入避免支出、功能或情感价值。时间成本通过负担小时与时薪计算。")
    d.strategies.forEach { s -> Panel(s.name) {
        s.flows.forEachIndexed { index,f -> Panel("现金流 ${index+1}") {
            fun change(v: CashFlow) { update(d.replaceStrategy(s.copy(flows=s.flows.mapIndexed { i,old -> if(i==index) v else old }))) }
            Text("ID：${f.id}",style=MaterialTheme.typography.bodySmall)
            Field("事件说明",f.note) { change(f.copy(note=it)) }; NumberField("发生时间（年）",f.time) { change(f.copy(time=it)) }; AmountField("金额（支出负值）",f.money) { change(f.copy(money=it)) }
            EnumSelect("分类",f.kind.name,CashKind.entries.map { it.name }) { kind -> val type=CashKind.valueOf(kind); change(f.copy(kind=type,time=if(type==CashKind.SALVAGE) d.horizon else f.time,probability=if(type==CashKind.INDUCED) f.probability ?: 1.0 else f.probability,burdenHours=if(type==CashKind.TIME) f.burdenHours ?: 0.0 else f.burdenHours,hourlyRate=if(type==CashKind.TIME) f.hourlyRate ?: Money("0",d.currency) else f.hourlyRate)) }
            if(f.kind==CashKind.INDUCED) NumberField("发生概率",f.probability ?: 1.0) { change(f.copy(probability=it)) }
            Field("现金流来源",f.source) { change(f.copy(source=it)) }
            if(f.kind==CashKind.TIME) {
                NumberField("负担小时",f.burdenHours ?: 0.0) { change(f.copy(burdenHours=it)) }
                val p=f.hourlyRateParameter ?: ParameterRecord("${f.id}:w_time","w_time",value=0.0,unit="${d.currency}/hour")
                AmountField("个人时薪",f.hourlyRate ?: Money("0",d.currency)) { change(f.copy(hourlyRate=it,hourlyRateParameter=p.copy(value=it.decimal().toDouble(),confirmed=false))) }
                Field("时薪参数来源",p.source) { change(f.copy(hourlyRateParameter=p.copy(source=it))) }
                EnumSelect("时薪置信度",p.confidence.name,Confidence.entries.map { it.name }) { change(f.copy(hourlyRateParameter=p.copy(confidence=Confidence.valueOf(it)))) }
                Check("确认个人时薪参数",p.confirmed) { change(f.copy(hourlyRate=f.hourlyRate ?: Money("0",d.currency),hourlyRateParameter=p.copy(confirmed=it))) }
            }
            TextButton(onClick={update(d.replaceStrategy(s.copy(flows=s.flows.filterIndexed { i,_ -> i!=index })))}) { Text("删除现金流") }
        } }
        TextButton(onClick={update(d.replaceStrategy(s.copy(flows=s.flows+CashFlow(UUID.randomUUID().toString(),0.0,Money("0",d.currency)))))}) { Text("添加现金流") }
    } }
}

@Composable fun ValidationEditor(d: DecisionInputSnapshot,update: (DecisionInputSnapshot)->Unit) {
    d.parameters.forEach { p -> Panel(FriendlyFields.parameter(p.key)) {
        fun change(v: ParameterRecord) { update(d.copy(parameters=d.parameters.map { if(it.id==p.id) v else it })) }
        if(p.value!=null) NumberField("标量值",p.value!!) { change(p.copy(value=it,confirmed=false)) } else { Text("区间：${p.lower ?: "−∞"} 至 ${p.upper ?: "+∞"}"); NumberField("本次显式选择计算值",p.selected ?: p.lower ?: 0.0) { change(p.copy(selected=it,confirmed=false)) } }
        Check("保存为区间参数",p.value==null) { change(if(it) p.copy(value=null,lower=p.value,upper=null,selected=null,confirmed=false) else p.copy(value=p.selected ?: p.lower ?: 0.0,lower=null,upper=null,confirmed=false)) }
        if(p.value==null) { OutlinedButton(onClick={change(p.copy(selected=p.selected ?: p.lower ?: 0.0,confirmed=false))}) { Text("显式选择当前显示值用于计算") }; Field("下界（空=无）",p.lower?.toString() ?: "") { change(p.copy(lower=it.toDoubleOrNull(),confirmed=false)) }; Field("上界（空=无）",p.upper?.toString() ?: "") { change(p.copy(upper=it.toDoubleOrNull(),confirmed=false)) }; Check("上界不包含",p.upperExclusive) { change(p.copy(upperExclusive=it,confirmed=false)) } }
        Field("来源",p.source) { change(p.copy(source=it)) }; Field("作用域",p.scope) { change(p.copy(scope=it)) }; EnumSelect("置信度",p.confidence.name,Confidence.entries.map { it.name }) { change(p.copy(confidence=Confidence.valueOf(it))) }
        Check("确认此参数用于本次计算",p.confirmed) { change(p.copy(confirmed=it)) }
    } }
    Text("技术阈值、衰减、恢复和情感参数随输入快照保存，完整来源可通过高级结构化输入补充。")
    PreferenceEditor(d,update)
    val validation=remember(d) { runCatching { DecisionEngine().evaluate(d) } }
    Text(validation.fold({ "校验通过：${it.paretoIds.size} 个方案值得继续比较；${it.strategies.count { s -> s.violations.isNotEmpty() }} 个方案不满足必须条件。" },{ "待修正：${FriendlyFields.error(it)}" }))
}
@Composable fun PreferenceEditor(d: DecisionInputSnapshot,update: (DecisionInputSnapshot)->Unit) {
    Check("配置显式线性偏好排序（可选）",d.preference!=null) { update(d.copy(preference=if(it) Preference(weights=List(4) { 0.0 },scales=List(4) { 1.0 }) else null)) }
    d.preference?.let { p -> Panel("线性 F / 固定尺度") {
        Text("只对值得继续比较的方案排序。需要你确认各项重要程度和固定尺度；超过评估时间的寿命只使用已知下界。")
        listOf("寿命","情感愿付","需求相关增益","现金流差值").forEachIndexed { i,label -> NumberField("$label 权重",p.weights.getOrElse(i) { 0.0 }) { value -> update(d.copy(preference=p.copy(weights=p.weights.mapIndexed { j,v -> if(i==j) value else v },confirmed=false))) }; NumberField("$label 固定参考尺度",p.scales.getOrElse(i) { 1.0 }) { value -> update(d.copy(preference=p.copy(scales=p.scales.mapIndexed { j,v -> if(i==j) value else v },confirmed=false))) } }
        Field("权重来源",p.source) { update(d.copy(preference=p.copy(source=it))) }; Check("确认权重和固定尺度",p.confirmed) { update(d.copy(preference=p.copy(confirmed=it))) }
    } }
}
@Composable fun AdvancedInput(d: DecisionInputSnapshot,update: (DecisionInputSnapshot)->Unit,notify: (String)->Unit) {
    var open by remember { mutableStateOf(false) }; var text by remember(d.id) { mutableStateOf(SnapshotCodec.encode(d)) }
    TextButton(onClick={open=!open; if(open) text=SnapshotCodec.encode(d)}) { Text("高级：结构化输入 JSON") }
    if(open) { OutlinedTextField(text,{text=it},modifier=Modifier.fillMaxWidth().height(260.dp),label={Text("当前决策输入（严格字段校验）")}); Button(onClick={runCatching { SnapshotCodec.decode<DecisionInputSnapshot>(text) }.onSuccess { value -> if(value.id==d.id) update(value) else notify("不能修改决策 ID") }.onFailure { notify(it.message ?: "JSON 不合法") }}) { Text("应用输入") } }
}
