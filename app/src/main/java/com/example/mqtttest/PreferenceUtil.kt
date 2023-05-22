package com.example.mqtttest

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object PreferenceUtil {

    private val prefs = MyApp.getContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
    private const val KEY = "my_Secret_Key___"

    const val IP_ADDRESS = "ip"
    const val PUBLISH_TOPIC = "publish_topic"
    const val SUBSCRIBE_TOPIC = "subscribe_topic"

    var ipAddress
        get() = getData(IP_ADDRESS) ?: "192.168.11.23:1883"
        set(value) = setData(IP_ADDRESS, value)

    var publishTopic
        get() = getData(PUBLISH_TOPIC) ?: "/android"
        set(value) = setData(PUBLISH_TOPIC, value)

    var subscribeTopic
        get() = getData(SUBSCRIBE_TOPIC) ?: "/unity"
        set(value) = setData(SUBSCRIBE_TOPIC, value)

    private fun setData(key: String, data: String) {
        val editor = prefs.edit()
        editor.putString(key, encrypt(data))
        editor.apply()
    }

    private fun getData(key: String): String? {
        val encryptedData = prefs.getString(key, null)
        return encryptedData?.let { decrypt(it) }
    }

    private fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val key = SecretKeySpec(KEY.toByteArray(), "AES")
        val iv = IvParameterSpec(ByteArray(16))
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encrypted = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val key = SecretKeySpec(KEY.toByteArray(), "AES")
        val iv = IvParameterSpec(ByteArray(16))
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        val decoded = Base64.decode(data, Base64.NO_WRAP)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted)
    }

    fun registerOnSharedPreferenceChangedListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = prefs.registerOnSharedPreferenceChangeListener(listener)


    fun unregisterOnSharedPreferenceChangedListener(
        changeListener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = prefs.unregisterOnSharedPreferenceChangeListener(changeListener)

}