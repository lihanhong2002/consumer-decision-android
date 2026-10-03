package com.sixmodel.consumerdecision.ai

import android.content.Context
import android.annotation.SuppressLint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

data class AiSettings(val endpoint: String = "https://api.deepseek.com", val model: String = "deepseek-flash", val apiKey: String = "", val enabled: Boolean = false, val consent: Boolean = false, val jsonMode: Boolean = true)

/** All provider settings, including the personal key, are encrypted and excluded from backup. */
// Writes run on Dispatchers.IO; commit's checked result confirms durable key storage before use.
@SuppressLint("ApplySharedPref","UseKtx")
class AiSettingsStore(context: Context) {
    private val prefs=context.getSharedPreferences("ai-private",Context.MODE_PRIVATE)
    private val alias="consumer-decision-ai-v1"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun load(): AiSettings {
        val encoded=prefs.getString("cipher",null) ?: return AiSettings()
        return try {
            val bytes=Base64.decode(encoded,Base64.NO_WRAP);require(bytes.size>12)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),javax.crypto.spec.GCMParameterSpec(128,bytes.copyOfRange(0,12))) }
            val json=org.json.JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
            AiSettings(json.getString("endpoint"),json.getString("model"),json.getString("key"),json.getBoolean("enabled"),json.getBoolean("consent"),json.optBoolean("jsonMode",true))
        } catch(_: Exception) { AiSettings() }
    }
    @Synchronized fun save(settings: AiSettings) {
        AiRequest.validateSettings(settings,requireKey=false)
        val json=org.json.JSONObject().put("endpoint",settings.endpoint).put("model",settings.model).put("key",settings.apiKey).put("enabled",settings.enabled).put("consent",settings.consent).put("jsonMode",settings.jsonMode).toString()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val bytes=cipher.iv+cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("cipher",Base64.encodeToString(bytes,Base64.NO_WRAP)).commit()) { "AI 配置保存失败" }
    }
    @Synchronized fun delete() { prefs.edit().clear().commit() }
}
