package com.sixmodel.consumerdecision.ai

import com.sixmodel.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*

class AiProtocolTest {
    private val settings=AiSettings(apiKey="personal-test-key",enabled=true,consent=true)
    private fun response(content: String,finish: String="stop") = buildJsonObject {putJsonArray("choices") {add(buildJsonObject {put("finish_reason",finish);putJsonObject("message") {put("content",content)}})}}.toString()
    @Test fun requestUsesJsonProtocolWithoutPuttingKeyInPrompt() {
        val body=AiRequest.body(settings,"json model rules","current input")
        val parsed=SnapshotCodec.json.parseToJsonElement(body).jsonObject
        assertEquals("json_object",parsed["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("disabled",parsed["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertFalse(body.contains(settings.apiKey))
        assertEquals("https://api.deepseek.com/v1/chat/completions",AiRequest.url(settings.copy(endpoint="https://api.deepseek.com/v1/")))
    }
    @Test fun unsafeAddressMissingConsentAndTruncatedResponsesAreRejected() {
        listOf("http://api.deepseek.com","https://someone:key@api.deepseek.com","https://api.deepseek.com?key=secret").forEach {address->try {AiRequest.validateSettings(settings.copy(endpoint=address));fail("unsafe")}catch(_:IllegalArgumentException){}}
        try {AiRequest.validateSettings(settings.copy(consent=false));fail("consent")}catch(_:IllegalArgumentException){}
        listOf("length","content_filter").forEach {finish->try {AiRequest.content(response("{}",finish));fail("unfinished")}catch(_:IllegalArgumentException){}}
        try {AiRequest.content(response(""));fail("empty")}catch(_:IllegalStateException){}
    }
    @Test fun validServiceResponseIsOnlySuggestionAndInvalidResponseLeavesInputIntact() = runBlocking {
        val input=Examples.phone();val hash=SnapshotCodec.hash(input)
        val reply=AssistantReply(question=InterviewQuestion("q","你最在意什么？",options=listOf(InterviewOption("a","续航"),InterviewOption("b","重量"))),changes=listOf(SuggestedChange("/title",JsonPrimitive("换机比较"),"根据你的描述","USER_ANSWER",Confidence.HIGH)))
        val client=AiClient("V4.2.1",AiTransport {_,_->response(SnapshotCodec.encode(reply))})
        assertEquals(reply,client.interview(settings,input,IntakeSession(decisionId=input.id)))
        assertEquals(hash,SnapshotCodec.hash(input))
        val invalid=AiClient("rules",AiTransport {_,_->response("{\"schemaVersion\":\"1\",\"unexpected\":true}")})
        try {invalid.interview(settings,input,IntakeSession(decisionId=input.id));fail("unknown protocol fields")}catch(_:Exception){}
        assertEquals(hash,SnapshotCodec.hash(input));Unit
    }
}
