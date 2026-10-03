package com.sixmodel.core

import kotlinx.serialization.json.*
import kotlin.test.*

class AssistanceTest {
    private val input=Examples.phone()
    @Test fun userAnswerIsOnlyProposedUntilExplicitApply() {
        val reply=AssistantReply(changes=listOf(SuggestedChange("/budget",SnapshotCodec.json.encodeToJsonElement(Money("3000")),"用户选择了 3000 元","USER_ANSWER",Confidence.HIGH)))
        val oldHash=SnapshotCodec.hash(input)
        val parsed=AssistantProtocol.parse(SnapshotCodec.encode(reply),input)
        assertEquals(oldHash,SnapshotCodec.hash(input))
        assertEquals("3000",AssistantProtocol.apply(input,parsed.changes,"USER_CONFIRMED").budget!!.amount)
    }
    @Test fun malformedAndOverlappingSuggestionsNeverApply() {
        assertFails {AssistantProtocol.parse(SnapshotCodec.encode(AssistantReply(question=InterviewQuestion("q","问题"))) .dropLast(1)+",\"danger\":1}",input)}
        assertFails {AssistantProtocol.validate(AssistantReply(changes=listOf(SuggestedChange("/budget",JsonNull,"a"),SuggestedChange("/budget/amount",JsonPrimitive("2"),"b"))),input)}
        assertFails {AssistantProtocol.validate(AssistantReply(changes=listOf(SuggestedChange("/userId",JsonPrimitive("another"),"a"))),input)}
        assertFails {AssistantProtocol.validate(AssistantReply(changes=listOf(SuggestedChange("/horizon",JsonPrimitive(-1),"a"))),input)}
    }
    @Test fun wholeObjectCannotSmuggleConfirmationOrReplaceIdentity() {
        val p=input.parameters.first().copy(value=0.6,confirmed=true)
        val changes=listOf(SuggestedChange("/parameters/0",SnapshotCodec.json.encodeToJsonElement(p),"估计"))
        val next=AssistantProtocol.preview(input,changes)
        assertFalse(next.parameters.first().confirmed)
        val category=input.category.copy(id="hijack")
        assertFails {AssistantProtocol.preview(input,listOf(SuggestedChange("/category",SnapshotCodec.json.encodeToJsonElement(category),"修改")))}
        val replaced=input.strategies.drop(1)
        assertFails {AssistantProtocol.preview(input,listOf(SuggestedChange("/strategies",SnapshotCodec.json.encodeToJsonElement(replaced),"修改")))}
    }
    @Test fun unknownIsSeparateFromZeroAndSessionRestoresMode() {
        val q=OfflineInterview.next(input,IntakeSession(decisionId=input.id))!!
        val answer=InterviewAnswer(q,unknown=true)
        val reply=OfflineInterview.proposal(input,answer)
        assertTrue(reply.changes.isEmpty());assertEquals(listOf("/title"),reply.unknownPaths)
        val s=IntakeSession(decisionId=input.id,answers=listOf(answer),mode="OFFLINE",unknownPaths=reply.unknownPaths,undo=UndoDraft(SnapshotCodec.encode(input),emptyList()))
        s.validate();assertEquals(s,SnapshotCodec.decode<IntakeSession>(SnapshotCodec.encode(s)))
    }
    @Test fun illegalTimeUnitAndRepairIdentityAreRejected() {
        val strategy=input.strategies.first().copy(stages=listOf(input.strategies.first().stages.first().copy(end=1.0)))
        assertFails {AssistantProtocol.preview(input,listOf(SuggestedChange("/strategies/0",SnapshotCodec.json.encodeToJsonElement(strategy),"不完整")))}
        val wrong=input.strategies[2].copy(stages=listOf(input.strategies[2].stages.first().copy(upgrade=input.strategies[2].stages.first().upgrade!!.copy(units=mapOf("battery" to "mAh")))))
        assertFails {AssistantProtocol.preview(input,listOf(SuggestedChange("/strategies/2",SnapshotCodec.json.encodeToJsonElement(wrong),"单位不同")))}
    }
    @Test fun offlineYearsKeepsSalvageAtEndAndRequiresMultistageManualEdit() {
        val single=input.copy(strategies=input.strategies.take(3))
        val q=InterviewQuestion("horizon","多久","",targetPath="/horizon",options=listOf(InterviewOption("2","2 年")))
        val reply=OfflineInterview.proposal(single,InterviewAnswer(q,"2"))
        val next=AssistantProtocol.apply(single,reply.changes,"USER_CONFIRMED_OPTION")
        assertEquals(2.0,next.horizon);assertTrue(next.strategies.all {it.stages.last().end==2.0});assertEquals(2.0,next.strategies[2].flows.first {it.kind==CashKind.SALVAGE}.time)
        assertFails {OfflineInterview.proposal(input,InterviewAnswer(q,"2"))}
    }
    @Test fun anAddedCandidateDoesNotChangeExistingCalculation() {
        val candidate=input.strategies.first().copy(id="another",name="其他方案",stages=input.strategies.first().stages.map {it.copy(id="another-stage",assetId="another-asset")},primaryStageId="another-stage")
        val next=AssistantProtocol.preview(input,listOf(SuggestedChange("/strategies",SnapshotCodec.json.encodeToJsonElement(input.strategies+candidate),"用户要求增加")))
        val before=DecisionEngine().evaluate(input);val after=DecisionEngine().evaluate(next)
        before.strategies.forEach {s->assertEquals(s.z,after.strategies.first {it.id==s.id}.z)}
    }
    @Test fun wrongScalarAndUnknownReplyVersionAreRejected() {
        val bad=input.parameters.first().copy(value=null,selected=null,lower=0.2,upper=0.9)
        val proposed=listOf(SuggestedChange("/parameters/0",SnapshotCodec.json.encodeToJsonElement(bad),"还没选择"))
        assertFails {AssistantProtocol.validate(AssistantReply(changes=proposed),input)}
        AssistantProtocol.validate(AssistantReply(changes=proposed,unknownPaths=listOf("/parameters/0")),input)
        assertNull(AssistantProtocol.preview(input,proposed).parameters.first().selected)
        assertFails {AssistantProtocol.validate(AssistantReply(schemaVersion="99",readyForReview=true),input)}
        assertFails {AssistantProtocol.validate(AssistantReply(unknownPaths=listOf("/does-not-exist"),readyForReview=true),input)}
    }
}
