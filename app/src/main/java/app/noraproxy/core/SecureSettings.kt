package app.noraproxy.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Subscription URLs contain bearer-like credentials; never persist them as plaintext. */
class SecureSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("noraproxy_settings", Context.MODE_PRIVATE)
    private val alias = "noraproxy.subscription.aes.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun save(url: String, seller: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(url.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("cipher", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("seller", seller.take(80))
            .apply()
    }

    fun load(): Pair<String, String> {
        val seller = prefs.getString("seller", "").orEmpty()
        val ciphertext = prefs.getString("cipher", null) ?: return "" to seller
        val iv = prefs.getString("iv", null) ?: return "" to seller
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
                Charsets.UTF_8
            ) to seller
        } catch (_: Exception) {
            // Key may have been invalidated by an OS restore. Do not crash or expose the URL.
            prefs.edit().remove("cipher").remove("iv").apply()
            "" to seller
        }
    }
}
