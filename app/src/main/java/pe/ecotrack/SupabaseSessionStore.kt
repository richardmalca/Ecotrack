package pe.ecotrack

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SupabaseSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("ecotrack_auth", Context.MODE_PRIVATE)

    fun read(endpoint: String): JSONObject? = runCatching {
        val saved = preferences.getString("session", null) ?: return null
        val packed = JSONObject(saved)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.decode(packed.getString("iv"), Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val bytes = cipher.doFinal(Base64.decode(packed.getString("data"), Base64.NO_WRAP))
        JSONObject(bytes.toString(Charsets.UTF_8)).takeIf { it.optString("endpoint") == endpoint }
    }.getOrNull()

    fun save(endpoint: String, data: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(data.put("endpoint", endpoint).toString().toByteArray(Charsets.UTF_8))
        preferences.edit().putString("session", JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).toString()).apply()
    }

    fun clear() {
        preferences.edit().remove("session").apply()
    }

    private fun key(): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    private companion object {
        const val KeyAlias = "ecotrack-supabase-session-v1"
    }
}
