package com.dlovel.plankton.service

import android.content.Context
import androidx.annotation.RawRes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
private data class EncryptedDocument(
    val version: Int,
    val algorithm: String,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val ciphertext: String,
    val authTag: String
)

object DocumentCrypto {
    private val json = Json { ignoreUnknownKeys = false }

    fun decrypt(context: Context, @RawRes resourceId: Int, password: String): String {
        val encrypted = context.resources.openRawResource(resourceId)
            .bufferedReader()
            .use { it.readText() }
        val payload = json.decodeFromString<EncryptedDocument>(encrypted)
        require(payload.version == 1) { "不支持的文档版本" }
        require(payload.algorithm == "AES-256-GCM") { "不支持的加密算法" }
        require(payload.kdf == "PBKDF2-HMAC-SHA256") { "不支持的密钥派生算法" }

        val salt = android.util.Base64.decode(payload.salt, android.util.Base64.NO_WRAP)
        val iv = android.util.Base64.decode(payload.iv, android.util.Base64.NO_WRAP)
        val ciphertext = android.util.Base64.decode(payload.ciphertext, android.util.Base64.NO_WRAP)
        val authTag = android.util.Base64.decode(payload.authTag, android.util.Base64.NO_WRAP)
        val keySpec: KeySpec = PBEKeySpec(password.toCharArray(), salt, payload.iterations, 256)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec)
            .encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(128, iv)
        )
        return try {
            cipher.doFinal(ciphertext + authTag).toString(Charsets.UTF_8)
        } catch (error: GeneralSecurityException) {
            throw SecurityException("文档密码错误", error)
        }
    }
}
