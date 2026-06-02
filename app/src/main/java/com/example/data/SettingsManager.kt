package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("geo_cam_settings", Context.MODE_PRIVATE)

    fun getPassword(): String? {
        val pwd = prefs.getString("app_password", null)
        return if (pwd.isNullOrEmpty()) null else pwd
    }

    fun setPassword(password: String?) {
        prefs.edit().putString("app_password", password).apply()
    }
}
