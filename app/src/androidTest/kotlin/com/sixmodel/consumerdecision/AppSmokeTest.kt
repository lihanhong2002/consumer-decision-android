package com.sixmodel.consumerdecision

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class) class AppSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun sampleComparisonChoiceAndSafBackup() {
        compose.onNodeWithText("手机示例").performClick()
        repeat(8) {compose.onNodeWithText("下一步").performClick()}
        compose.onNodeWithText("查看输入摘要").performClick()
        val acknowledgement="我已核实以上状态、费用及当前显示的参数（包括估计、预设和需求系数）"
        compose.onNodeWithTag("input-summary-list").performScrollToNode(hasText(acknowledgement))
        compose.onNodeWithText(acknowledgement).performClick()
        compose.onNodeWithText("确认这些输入和参数").performScrollTo().performClick()
        compose.onNodeWithText("计算并比较策略").performScrollTo().performClick()
        compose.waitUntil(30000) {compose.onAllNodesWithText("计算已保存为独立快照").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("未启用偏好排序，不生成自动综合分数。").assertExists()
        compose.onNodeWithTag("result-list").performScrollToNode(hasText("记录我的真实选择"))
        compose.onAllNodesWithText("记录我的真实选择").onFirst().performClick()
        compose.onNodeWithText("保存选择").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("真实选择已记录").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("记录").performClick()
        compose.onNodeWithText("我的决策记录").assertExists()
        compose.onNodeWithContentDescription("AI 设置").performClick()
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val backupName="consumer-decision-v02-${System.currentTimeMillis()}.json"
        compose.onNodeWithText("导出 JSON").performScrollTo().performClick()
        val save=device.wait(Until.findObject(By.text(Pattern.compile("SAVE|Save|保存"))),60000)?:error("保存选择器未出现")
        device.wait(Until.findObject(By.clazz("android.widget.EditText")),15000)!!.text=backupName
        save.click()
        compose.waitUntil(30000) {runCatching {compose.onAllNodesWithText("JSON 已导出").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
        compose.onNodeWithText("导入 JSON").performScrollTo().performClick()
        device.wait(Until.findObject(By.text(backupName)),15000)!!.click()
        compose.waitUntil(30000) {runCatching {compose.onAllNodesWithText("导入完成",substring=true).fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
        compose.onNodeWithText("决策").performClick()
        screenshot("home")
    }
    @Test fun offlineInterviewManualSwitchKeepsAnswersAndUnsubmittedText() {
        compose.onNodeWithText("开始一次决策 →").performClick()
        compose.onNodeWithText("先用离线引导").performScrollTo().performClick()
        compose.onNodeWithText("这次你在纠结什么？").assertExists()
        compose.onNode(hasSetTextAction() and hasText("也可以自己补充")).performTextInput("手机续航决策")
        compose.onNodeWithText("下一题 →").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("我已查看选择的改动，知道估计和预设不等于已核实事实"))
        compose.onNodeWithText("我已查看选择的改动，知道估计和预设不等于已核实事实").performClick()
        compose.onNodeWithText("确认并应用 →").performScrollTo().performClick()
        compose.onNodeWithText("¥3,000").performClick()
        compose.onNodeWithText("下一题 →").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("我已查看选择的改动，知道估计和预设不等于已核实事实"))
        compose.onNodeWithText("我已查看选择的改动，知道估计和预设不等于已核实事实").performClick()
        compose.onNodeWithText("确认并应用 →").performScrollTo().performClick()
        compose.onNodeWithText("手动编辑").performClick()
        compose.onNode(hasSetTextAction() and hasText("初始支出预算 (CNY)")).performScrollTo().performTextReplacement("3500")
        compose.onNodeWithText("AI 引导").performClick()
        compose.onNode(hasSetTextAction() and hasText("也可以自己补充")).performScrollTo().performTextInput("暂未提交的使用计划")
        compose.onNodeWithText("手动编辑").performScrollTo().performClick()
        compose.onNodeWithText("AI 引导").performClick()
        compose.onNode(hasSetTextAction() and hasText("也可以自己补充")).assertTextContains("暂未提交的使用计划")
        compose.runOnIdle {val model=ViewModelProvider(compose.activity)[AppModel::class.java];assertEquals("3500",model.draft!!.budget!!.amount);assertEquals("手机续航决策",model.draft!!.title);assertEquals("OFFLINE",model.session!!.mode)}
        screenshot("interview")
    }
    private fun screenshot(name: String) {compose.waitForIdle();val context=InstrumentationRegistry.getInstrumentation().targetContext;UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(context.getExternalFilesDir(null),"v02-$name-${android.os.Build.VERSION.SDK_INT}.png"))}
}
