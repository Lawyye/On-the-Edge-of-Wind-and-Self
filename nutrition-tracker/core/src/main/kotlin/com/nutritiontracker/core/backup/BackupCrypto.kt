package com.nutritiontracker.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Необязательное шифрование резервной копии паролем.
 *
 * AES-256-GCM; ключ выводится из пароля PBKDF2-HMAC-SHA256 со случайной
 * солью. Параметры (формат, версия, число итераций) входят в
 * аутентифицированные данные, поэтому их подмена обнаруживается так же,
 * как подмена самих данных. Пароль нигде не сохраняется: если его забыть,
 * копию расшифровать нельзя.
 */
object BackupCrypto {
    const val FORMAT = "nutrition-tracker-backup-encrypted"
    const val VERSION = 1
    const val ITERATIONS = 600_000
    const val MIN_PASSWORD_LENGTH = 8
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    @Serializable
    data class Envelope(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val kdf: String = KDF,
        val iterations: Int = ITERATIONS,
        val salt: String,
        val iv: String,
        val ciphertext: String,
    )

    sealed interface DecryptResult {
        data class Ok(val text: String) : DecryptResult
        data object WrongPassword : DecryptResult
        data class Invalid(val reason: String) : DecryptResult
    }

    private val json = Json { ignoreUnknownKeys = false; prettyPrint = true; encodeDefaults = true }
    private val random = SecureRandom()

    /** Быстрая проверка без расшифровки: похож ли файл на зашифрованную копию. */
    fun isEncrypted(text: String): Boolean {
        val head = text.trimStart().take(400)
        return head.startsWith("{") && head.contains("\"$FORMAT\"")
    }

    private fun aad(version: Int, iterations: Int) = "$FORMAT:$version:$iterations".toByteArray(Charsets.UTF_8)

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plainText: String, password: CharArray, iterations: Int = ITERATIONS): String {
        require(password.size >= MIN_PASSWORD_LENGTH) { "Пароль должен быть не короче $MIN_PASSWORD_LENGTH символов" }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad(VERSION, iterations))
        val ct = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return json.encodeToString(
            Envelope.serializer(),
            Envelope(iterations = iterations, salt = b64.encodeToString(salt), iv = b64.encodeToString(iv), ciphertext = b64.encodeToString(ct)),
        )
    }

    fun decrypt(text: String, password: CharArray): DecryptResult {
        val env = try {
            json.decodeFromString(Envelope.serializer(), text)
        } catch (e: SerializationException) {
            return DecryptResult.Invalid("Повреждён заголовок зашифрованной копии.")
        } catch (e: IllegalArgumentException) {
            return DecryptResult.Invalid("Повреждён заголовок зашифрованной копии.")
        }
        if (env.format != FORMAT) return DecryptResult.Invalid("Неизвестный формат.")
        if (env.version > VERSION) return DecryptResult.Invalid("Копия зашифрована более новой версией приложения.")
        if (env.kdf != KDF || env.iterations !in 100_000..5_000_000) return DecryptResult.Invalid("Неподдерживаемые параметры шифрования.")
        return try {
            val b64 = Base64.getDecoder()
            val salt = b64.decode(env.salt)
            val iv = b64.decode(env.iv)
            val ct = b64.decode(env.ciphertext)
            if (salt.size != SALT_BYTES || iv.size != IV_BYTES) return DecryptResult.Invalid("Повреждены параметры шифрования.")
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, env.iterations), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad(env.version, env.iterations))
            DecryptResult.Ok(String(cipher.doFinal(ct), Charsets.UTF_8))
        } catch (e: javax.crypto.AEADBadTagException) {
            DecryptResult.WrongPassword
        } catch (e: IllegalArgumentException) {
            DecryptResult.Invalid("Повреждены данные зашифрованной копии.")
        } catch (e: GeneralSecurityException) {
            DecryptResult.Invalid("Не удалось расшифровать: ${e.javaClass.simpleName}")
        }
    }
}
