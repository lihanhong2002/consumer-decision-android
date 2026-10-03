package com.sixmodel.core

import kotlin.test.*
import java.math.BigDecimal

class EngineTest {
    private val sample = Examples.phone()
    private val dims = sample.category.dimensions
    @Test fun negativeGainAndSaturation() {
        val upgrade = sample.strategies[2].stages[0].upgrade!!
        val result = Model2.evaluate(upgrade.copy(saturation=dims.associate { it.id to 1.0 }),dims,0.8)
        assertEquals(-0.2,result.g.getValue("weight"),1e-9)
        assertEquals(-0.2,result.perceived.getValue("weight"),1e-9)
        assertEquals(0.0,result.perceived.getValue("battery"))
        assertTrue(result.abs > result.net)
    }
    @Test fun rawDirectionAndClip() {
        val u = sample.strategies[2].stages[0].upgrade!!
        val r = Model2.evaluate(u.copy(new=u.new + mapOf("weight" to 10.0,"battery" to 1000.0)),dims,0.8)
        assertEquals(1.0,r.g.getValue("weight")); assertEquals(1.0,r.g.getValue("battery"))
    }
    @Test fun undefinedNeedAndCalibratedCoefficients() {
        assertFails { NeedGenerator.generate(sample.needs[0].copy(inputs=sample.needs[0].inputs.map { it.copy(weight=0.0) }),dims) }
        assertFails { NeedGenerator.generate(sample.needs[0].copy(method=NeedMethod.MULTIPLICATIVE,coefficients=listOf(1.0,1.0,1.0,1.0)),dims) }
        val r = NeedGenerator.generate(sample.needs[0].copy(method=NeedMethod.WEIGHTED_SUM,coefficients=listOf(1.0,1.0,1.0,1.0),coefficientsConfirmed=true),dims)
        assertEquals(1.0,r.weights.values.sum(),1e-9)
    }
    @Test fun alignmentZeroAndNegative() {
        val need = NeedGenerator.generate(sample.needs[0],dims)
        assertEquals(0.0,Model4.evaluate(need,dims.associate { it.id to 0.0 }).gamma)
        assertTrue(Model4.evaluate(need,dims.associate { it.id to -1.0 }).relevant < 0)
        assertFails { Model4.evaluate(need,mapOf("wrong" to 1.0)) }
    }
    @Test fun emotionAndCooling() {
        val a = Model3.evaluate(Emotion(Money("250"),Money("320"),8.32),"CNY")
        assertEquals(BigDecimal("70"),a.wEmo.decimal())
        assertTrue(Model3.evaluate(Emotion(gamma=16.64),"CNY").coolDown < a.coolDown)
        assertEquals(BigDecimal.ZERO,Model3.evaluate(Emotion(Money("320"),Money("250")),"CNY").wEmo.decimal())
        assertFails { Model3.evaluate(Emotion(q=1.0),"CNY") }
    }
    @Test fun rhoStates() {
        val u = sample.strategies[2].stages[0].upgrade!!
        assertEquals("NOT_APPLICABLE",Model5.evaluate(null,1.0,1e-9).status)
        assertNull(Model5.evaluate(null,1.0,1e-9).value)
        assertEquals("NO_POSITIVE_RELEVANT_UPGRADE",Model5.evaluate(u,-0.1,1e-9).status)
        assertEquals("INVALID_INPUT",Model5.evaluate(u.copy(oldPrice=Money("0")),1.0,1e-9).status)
        assertEquals(1.5,Model5.evaluate(u.copy(ratioOldQuality=2.0,ratioNewQuality=3.0),0.5,1e-9).value!!,1e-9)
    }
    @Test fun censoredAndRecovery() {
        val s = sample.strategies[0].stages[0]
        val needs = listOf(0.0 to NeedGenerator.generate(sample.needs[0],dims))
        assertEquals("RIGHT_CENSORED",Model1.evaluate(s,dims,needs,0.55,3.0,EngineConfig()).life.status)
        val weak = s.copy(utilities=s.utilities.map { it.copy(initial=0.2,lambda=0.0) })
        assertEquals(0.0,Model1.evaluate(weak,dims,needs,0.55,3.0,EngineConfig()).life.value)
        val recovery = Recovery("repair","修复",dims.associate { it.id to 1.0 })
        assertEquals("RIGHT_CENSORED",Model1.evaluate(weak.copy(allowedRecoveries=listOf(recovery)),dims,needs,0.55,3.0,EngineConfig()).life.status)
        assertEquals(0.0,Model1.evaluate(weak.copy(allowedRecoveries=listOf(recovery.copy(changesCoreIdentity=true))),dims,needs,0.55,3.0,EngineConfig()).life.value)
    }
    @Test fun cashFlowStepsAndCosts() {
        val flows = listOf(CashFlow("purchase",0.0,Money("-100")),CashFlow("resale",1.1,Money("50"),CashKind.SALVAGE))
        val r = Model6.evaluate(flows,1.1,1.0,0.1,"CNY")
        assertEquals(2,r.trace.values.getValue("N").toInt())
        assertEquals(-100+50/1.21,r.npv.decimal().toDouble(),1e-8)
        assertTrue(Model6.evaluate(flows + CashFlow("cost",1.0,Money("-10")),1.1,1.0,0.1,"CNY").npv.decimal() < r.npv.decimal())
        val monthly = Model6.evaluate(listOf(CashFlow("x",1.0,Money("-100"))),1.0,1.0/12,0.1,"CNY")
        assertEquals(-100/1.1,monthly.npv.decimal().toDouble(),1e-8)
        assertFails { Model6.evaluate(listOf(CashFlow("x",0.0,Money("5","USD"))),1.0,1.0,0.1,"CNY") }
        assertFails { Model6.evaluate(flows + flows.last().copy(id="second-salvage"),1.1,1.0,0.1,"CNY") }
    }
    @Test fun deterministicSnapshotAndNoDefaultRanking() {
        val e = DecisionEngine(); val a = e.evaluate(sample); val b = e.evaluate(SnapshotCodec.decode(SnapshotCodec.encode(sample)))
        assertEquals(a,b); assertTrue(a.rankedIds.isEmpty()); assertEquals(BigDecimal.ZERO,a.strategies.first().z.deltaNpv.decimal())
        assertEquals("NOT_APPLICABLE",a.strategies.first().z.rho.status)
        assertTrue(a.strategies.all { it.dominatedBy.isEmpty() }) // two censored lives cannot prove ordering
    }
    @Test fun budgetAndUnconfirmedConstraint() {
        val a = DecisionEngine().evaluate(sample.copy(budget=Money("10"),strategies=sample.strategies.map { if(it.id=="keep") it.copy(constraints=listOf(BooleanConstraint("safety","SAFETY","安全确认"))) else it }))
        assertTrue(a.strategies.first().violations.isNotEmpty()); assertTrue("buy" !in a.paretoIds)
    }
    @Test fun primaryProjectionDoesNotChangeCash() {
        val wait = sample.strategies.last()
        val modified = sample.copy(strategies=sample.strategies.dropLast(1)+wait.copy(primaryStageId="wait-old"))
        assertEquals(DecisionEngine().evaluate(sample).strategies.last().m6,DecisionEngine().evaluate(modified).strategies.last().m6)
    }
    @Test fun earlierServiceFailureCannotBeHiddenByUpgrade() {
        val wait = sample.strategies.last(); val broken = wait.stages[0].copy(utilities=wait.stages[0].utilities.map { it.copy(initial=0.1) })
        val result = DecisionEngine().evaluate(sample.copy(strategies=sample.strategies.dropLast(1)+wait.copy(stages=listOf(broken,wait.stages[1]))))
        assertTrue(result.strategies.last().violations.any { it.startsWith("SERVICE_FEASIBILITY") })
    }
    @Test fun intervalRequiresSelectionAndBounds() {
        val p = ParameterRecord("g","gamma",lower=8.32,confirmed=true)
        assertFails { p.scalar() }; assertFails { p.copy(selected=8.0).scalar() }; assertEquals(10.0,p.copy(selected=10.0).scalar())
    }
    @Test fun fixedScalePreference() {
        val result = DecisionEngine().evaluate(sample.copy(preference=Preference(true,listOf(1.0,1.0,1.0,1.0),listOf(3.0,1000.0,1.0,4000.0))))
        assertEquals(result.paretoIds.toSet(),result.rankedIds.toSet()); assertTrue(result.strategies.filter { it.score != null }.all { it.contributions.size == 4 })
    }
    @Test fun timeAndInducedCostRequireExplicitInputs() {
        val time=CashFlow("time",0.0,Money("0"),CashKind.TIME,burdenHours=2.0,hourlyRate=Money("30"),hourlyRateParameter=ParameterRecord("time-rate","w_time",value=30.0,confirmed=true))
        assertEquals(BigDecimal("-60.0"),Model6.amount(time))
        assertFails { Model6.amount(time.copy(hourlyRateParameter=null)) }
        assertFails { Model6.amount(time.copy(hourlyRateParameter=time.hourlyRateParameter!!.copy(confirmed=false))) }
        val induced=CashFlow("induced",0.0,Money("-100"),CashKind.INDUCED,probability=0.2)
        assertEquals(BigDecimal("-20.0"),Model6.amount(induced))
    }
    @Test fun useRepairWaitAndPurchaseRetainsEveryEvent() {
        val wait=sample.strategies.last()
        val recovery=Recovery("battery-fix","换电池",mapOf("battery" to 0.8))
        val old=wait.stages[0].copy(repairs=listOf(RepairEvent(0.5,recovery,"battery-cost")))
        val combined=wait.copy(stages=listOf(old,wait.stages[1]),flows=wait.flows+CashFlow("battery-cost",0.5,Money("-150"),CashKind.MAINTENANCE))
        val result=DecisionEngine().evaluate(sample.copy(strategies=sample.strategies.dropLast(1)+combined)).strategies.last()
        assertTrue(result.violations.isEmpty()); assertEquals(2,result.stages.size)
        assertEquals(setOf("battery-cost","wait-purchase","wait-salvage"),result.m6.discounted.keys)
        assertTrue(result.m6.npv.decimal() < DecisionEngine().evaluate(sample).strategies.last().m6.npv.decimal())
        assertFails { RhoResult("NOT_APPLICABLE",0.0) }
    }
    @Test fun rejectsGapsOverlapUnitsAndCoreReplacement() {
        val wait = sample.strategies.last()
        assertFails { DecisionEngine().evaluate(sample.copy(strategies=sample.strategies.dropLast(1)+wait.copy(stages=listOf(wait.stages[0].copy(end=0.8),wait.stages[1])))) }
        val u = sample.strategies[2].stages[0].upgrade!!
        assertFails { Model2.evaluate(u.copy(units=emptyMap()),dims,0.8) }
        val s = sample.strategies[0]; val stage = s.stages[0].copy(repairs=listOf(RepairEvent(0.0,Recovery("core","核心换新",mapOf("battery" to 1.0),true))))
        assertFails { DecisionEngine().evaluate(sample.copy(strategies=listOf(s.copy(stages=listOf(stage)))+sample.strategies.drop(1))) }
    }
    @Test fun candidateSetDoesNotChangeOriginalDescriptions() {
        val original=DecisionEngine().evaluate(sample)
        val extra=sample.strategies.first().copy(id="extra",name="其他备选")
        val expanded=DecisionEngine().evaluate(sample.copy(strategies=sample.strategies+extra))
        original.strategies.forEach { a -> assertEquals(a.z,expanded.strategies.first { it.id==a.id }.z); assertEquals(a.y,expanded.strategies.first { it.id==a.id }.y) }
    }
    @Test fun exactAndCensoredParetoAreConservative() {
        val r=DecisionEngine().evaluate(sample).strategies.first()
        val exact=r.copy(id="exact",z=r.z.copy(life=EffectiveLifeValue(value=2.0)))
        val longer=exact.copy(id="longer",z=exact.z.copy(life=EffectiveLifeValue(value=3.0)))
        val censored=exact.copy(id="censored",z=exact.z.copy(life=EffectiveLifeValue(lowerBound=3.0,status="RIGHT_CENSORED")))
        assertTrue(ParetoEngine.dominates(longer,exact,1e-9)); assertFalse(ParetoEngine.dominates(exact,longer,1e-9))
        assertTrue(ParetoEngine.dominates(censored,exact,1e-9)); assertFalse(ParetoEngine.dominates(longer,censored,1e-9)); assertFalse(ParetoEngine.dominates(censored,censored.copy(id="other"),1e-9))
    }
    @Test fun criticalDimensionAndFutureDemandBoundary() {
        val stage=sample.strategies.first().stages.first().copy(utilities=dims.map { Utility(it.id,if(it.id=="battery") 0.2 else 1.0,0.0) })
        val cameraNeed=NeedVector(mapOf("battery" to 0.0,"storage" to 1.0,"weight" to 0.0))
        val batteryNeed=NeedVector(mapOf("battery" to 1.0,"storage" to 0.0,"weight" to 0.0))
        val critical=dims.map { if(it.id=="battery") it.copy(critical=true,threshold=0.3) else it }
        assertEquals(0.0,Model1.evaluate(stage,critical,listOf(0.0 to cameraNeed),0.55,3.0,EngineConfig()).life.value)
        assertEquals(0.37,Model1.evaluate(stage,dims,listOf(0.0 to cameraNeed,0.37 to batteryNeed),0.55,3.0,EngineConfig()).life.value)
    }
    @Test fun monotonicPerceptionInUnsaturatedRegion() {
        val u=sample.strategies[2].stages.first().upgrade!!
        var previous=Double.NEGATIVE_INFINITY
        for(i in 0..100) { val next=Model2.evaluate(u.copy(new=u.new+("battery" to 8.0+i*0.08),activation=Activation.LOGISTIC),dims,0.8).perceived.getValue("battery"); assertTrue(next >= previous); previous=next }
    }
    @Test fun cameraAndEmotionalMerchandiseFixtures() {
        val camera=DecisionEngine().evaluate(Examples.camera())
        assertEquals(2,camera.strategies.size); assertTrue(camera.strategies[1].stages.first().m2.g.getValue("mass") < 0)
        val input=Examples.merchandise(); val result=DecisionEngine().evaluate(input)
        assertEquals(BigDecimal("70"),result.strategies[1].z.emotion.decimal()); assertEquals("NOT_APPLICABLE",result.strategies[1].z.rho.status)
        val noEmotion=input.copy(strategies=input.strategies.map { s -> s.copy(stages=s.stages.map { it.copy(emotion=Emotion()) }) })
        assertEquals(DecisionEngine().evaluate(noEmotion).strategies[1].m6,result.strategies[1].m6)
    }
}
