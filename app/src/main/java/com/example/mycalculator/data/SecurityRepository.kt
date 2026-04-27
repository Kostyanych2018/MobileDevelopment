package com.example.mycalculator.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

class SecurityRepository(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to init EncryptedSharedPreferences, falling back", e)
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    fun hasPin(): Boolean = prefs.contains(KEY_PIN)

    fun savePin(pin: String) {
        prefs.edit { putString(KEY_PIN, pin) }
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN, null) ?: return false
        return stored == pin
    }

    fun clearPin() {
        prefs.edit { remove(KEY_PIN) }
    }

    companion object {
        private const val TAG = "SecurityRepository"
        private const val FILE_NAME = "calc_secure_prefs"
        private const val KEY_PIN = "pass_key_pin"
    }
}
