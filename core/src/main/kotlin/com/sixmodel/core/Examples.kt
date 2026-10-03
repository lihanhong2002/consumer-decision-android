package com.sixmodel.core

object Examples {
    fun camera(): DecisionInputSnapshot {
        val dimensions=listOf(Dimension("resolution","分辨率","MP",reference=24.0),Dimension("mass","重量","g",lowerBetter=true,reference=200.0))
        val old=Stage("camera-old","camera-owned","继续旧相机",start=0.0,end=3.0,utilities=dimensions.map { Utility(it.id,0.9,0.03) })
        val u=Upgrade("camera-upgrade",mapOf("resolution" to 24.0,"mass" to 600.0),mapOf("resolution" to 33.0,"mass" to 740.0),dimensions.associate { it.id to 0.5 },oldPrice=Money("5000"),newPrice=Money("8000"),units=dimensions.associate { it.id to it.unit })
        val next=old.copy(id="camera-new",assetId="new-camera",name="购买新相机",action=Action.PURCHASE,utilities=dimensions.map { Utility(it.id,1.0,0.03) },upgrade=u)
        return phone("camera-example").copy(title="是否升级相机",category=Category("camera","相机",dimensions),needs=listOf(NeedPoint(inputs=listOf(NeedInput("resolution",0.7),NeedInput("mass",0.3)))),budget=Money("10000"),baselineId="keep",strategies=listOf(Strategy("keep","继续旧相机",listOf(old),old.id,flows=listOf(CashFlow("maintenance",1.0,Money("-300"),CashKind.MAINTENANCE))),Strategy("buy","购买新相机",listOf(next),next.id,u.id,listOf(CashFlow("purchase",0.0,Money("-8000"),CashKind.INITIAL),CashFlow("salvage",3.0,Money("3000"),CashKind.SALVAGE)))))
    }
    fun merchandise(): DecisionInputSnapshot {
        val dim=Dimension("function","基本功能","",reference=1.0)
        val stage=Stage("current","owned","沿用现有物品",start=0.0,end=3.0,utilities=listOf(Utility(dim.id,1.0,0.03)))
        val buy=stage.copy(id="merch-buy",assetId="merch-new",name="联名版本",action=Action.PURCHASE,emotion=Emotion(Money("250"),Money("320"),16.64))
        return phone("merch-example").copy(title="是否购买联名周边",category=Category("hobby","兴趣消费",listOf(dim)),needs=listOf(NeedPoint(inputs=listOf(NeedInput(dim.id)))),budget=Money("400"),baselineId="keep",strategies=listOf(Strategy("keep","不购买，沿用现有物品",listOf(stage),stage.id),Strategy("buy","购买联名版本",listOf(buy),buy.id,flows=listOf(CashFlow("purchase",0.0,Money("-320"),CashKind.INITIAL)))))
    }
    fun phone(id: String = "example-phone"): DecisionInputSnapshot {
        val dimensions = listOf(Dimension("battery","续航","hours",reference=8.0),Dimension("storage","存储","GB",reference=256.0),Dimension("weight","重量","g",lowerBetter=true,reference=100.0))
        val old = listOf(Utility("battery",0.85,0.05),Utility("storage",0.8,0.03),Utility("weight",1.0,0.0))
        val new = dimensions.map { Utility(it.id,1.0,0.03) }
        val upgrade = Upgrade("upgrade-new",mapOf("battery" to 8.0,"storage" to 128.0,"weight" to 180.0),mapOf("battery" to 12.0,"storage" to 256.0,"weight" to 200.0),dimensions.associate { it.id to 0.5 },oldPrice=Money("2000"),newPrice=Money("3500"),units=dimensions.associate { it.id to it.unit })
        val recovery = Recovery("battery-action","换电池",mapOf("battery" to 0.8))
        val keepStage = Stage("keep-stage","old","继续旧机",start=0.0,end=3.0,utilities=old)
        val keep = Strategy("keep","继续使用",listOf(keepStage),keepStage.id)
        val repairStage = keepStage.copy(id="repair-stage",name="换电池后继续",action=Action.REPAIR,repairs=listOf(RepairEvent(0.0,recovery,"repair-cost")),allowedRecoveries=listOf(recovery))
        val repair = Strategy("repair","换电池",listOf(repairStage),repairStage.id,flows=listOf(CashFlow("repair-cost",0.0,Money("-150"),CashKind.MAINTENANCE)))
        val buyStage = Stage("buy-stage","new","购买新机",Action.PURCHASE,0.0,3.0,new,upgrade=upgrade,emotion=Emotion(Money("3500"),Money("3700")))
        val buy = Strategy("buy","立即购买",listOf(buyStage),buyStage.id,upgrade.id,listOf(CashFlow("purchase",0.0,Money("-3500"),CashKind.INITIAL),CashFlow("salvage",3.0,Money("600"),CashKind.SALVAGE)))
        val waitOld = keepStage.copy(id="wait-old",end=1.0)
        val waitNew = buyStage.copy(id="wait-new",start=1.0,upgrade=upgrade.copy(id="wait-upgrade"))
        val wait = Strategy("wait","等一年再换",listOf(waitOld,waitNew),waitNew.id,"wait-upgrade",listOf(CashFlow("wait-purchase",1.0,Money("-3000")),CashFlow("wait-salvage",3.0,Money("600"),CashKind.SALVAGE)))
        return DecisionInputSnapshot(id,"是否更换手机",Category("phone","手机",dimensions),budget=Money("4000"),baselineId="keep",needs=listOf(NeedPoint(inputs=listOf(NeedInput("battery",0.5),NeedInput("storage",0.4),NeedInput("weight",0.1)))),parameters=listOf(ParameterRecord("threshold","serviceThreshold",value=0.55,confidence=Confidence.MEDIUM,source="SPEC_INITIAL",confirmed=true),ParameterRecord("sat","saturationAnchor",value=0.8,confidence=Confidence.HIGH,source="SPEC_INITIAL",confirmed=true),ParameterRecord("rate","annualRate",value=0.05,confidence=Confidence.MEDIUM,source="SPEC_INITIAL",confirmed=true)),strategies=listOf(keep,repair,buy,wait),example=true)
    }
}
