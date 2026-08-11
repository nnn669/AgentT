package com.agentt.app.ui.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SandboxVariable(
    val name: String,
    val value: String,
    val description: String
)

object SandboxVariableRules {
    private val validName = Regex("^[A-Z_][A-Z0-9_]*$")

    fun normalizeName(value: String): String = value.trim().uppercase()

    fun isValidName(value: String): Boolean = validName.matches(value)

    fun mask(value: String): String {
        if (value.length < 8) return "*".repeat(value.length)
        return value.take(4) + "*".repeat((value.length - 8).coerceAtLeast(1)) + value.takeLast(4)
    }
}

class SandboxEnvironmentStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var privacyMode: Boolean
        get() = preferences.getBoolean(PRIVACY_MODE, true)
        set(value) { preferences.edit().putBoolean(PRIVACY_MODE, value).apply() }

    fun variables(): List<SandboxVariable> = names().mapNotNull { name ->
        val encrypted = preferences.getString(secretKey(name), null) ?: return@mapNotNull null
        val value = decrypt(encrypted) ?: return@mapNotNull null
        SandboxVariable(name, value, preferences.getString(descriptionKey(name), "").orEmpty())
    }.sortedBy(SandboxVariable::name)

    fun put(name: String, value: String, description: String = "") {
        val normalized = SandboxVariableRules.normalizeName(name)
        require(SandboxVariableRules.isValidName(normalized)) { "变量名必须符合 POSIX shell 规则" }
        require(value.isNotEmpty()) { "变量值不能为空" }
        val updatedNames = names().toMutableSet().apply { add(normalized) }
        preferences.edit()
            .putStringSet(NAMES, updatedNames)
            .putString(secretKey(normalized), encrypt(value))
            .putString(descriptionKey(normalized), description.trim())
            .apply()
    }

    fun delete(name: String) {
        val normalized = SandboxVariableRules.normalizeName(name)
        val updatedNames = names().toMutableSet().apply { remove(normalized) }
        preferences.edit()
            .putStringSet(NAMES, updatedNames)
            .remove(secretKey(normalized))
            .remove(descriptionKey(normalized))
            .apply()
    }

    fun environment(): Map<String, String> = variables().associate { it.name to it.value }

    fun redactForModel(text: String): String {
        if (!privacyMode || text.isEmpty()) return text
        return variables()
            .filter { it.value.isNotEmpty() }
            .sortedByDescending { it.value.length }
            .fold(text) { result, variable ->
                result.replace(variable.value, SandboxVariableRules.mask(variable.value))
            }
    }

    private fun names(): Set<String> = preferences.getStringSet(NAMES, emptySet()).orEmpty().toSet()

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_SIZE)
        val encrypted = bytes.copyOfRange(IV_SIZE, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private fun secretKey(name: String) = "secret_$name"
    private fun descriptionKey(name: String) = "description_$name"

    companion object {
        private const val PREFERENCES = "sandbox_environment"
        private const val PRIVACY_MODE = "privacy_mode"
        private const val NAMES = "variable_names"
        private const val KEY_ALIAS = "agentt_sandbox_environment"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12

        @Volatile private var instance: SandboxEnvironmentStore? = null

        fun from(context: Context): SandboxEnvironmentStore = instance ?: synchronized(this) {
            instance ?: SandboxEnvironmentStore(context).also { instance = it }
        }
    }
}