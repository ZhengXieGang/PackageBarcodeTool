package com.example.expresscode.util

import android.content.Context
import android.content.SharedPreferences

object PreferenceUtils {
    private const val PREF_NAME = "suspended_window_prefs"
    private const val KEY_WINDOW_WIDTH = "window_width"
    private const val KEY_WINDOW_HEIGHT = "window_height"
    private const val KEY_WINDOW_X = "window_x"
    private const val KEY_WINDOW_Y = "window_y"

    private const val DEFAULT_WIDTH = 600
    private const val DEFAULT_HEIGHT = 350

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveWindowSize(context: Context, width: Int, height: Int) {
        getPrefs(context).edit()
            .putInt(KEY_WINDOW_WIDTH, width)
            .putInt(KEY_WINDOW_HEIGHT, height)
            .apply()
    }

    fun getWindowSize(context: Context): Pair<Int, Int> {
        val prefs = getPrefs(context)
        val width = prefs.getInt(KEY_WINDOW_WIDTH, DEFAULT_WIDTH)
        val height = prefs.getInt(KEY_WINDOW_HEIGHT, DEFAULT_HEIGHT)
        return Pair(width, height)
    }

    fun saveWindowPosition(context: Context, x: Int, y: Int) {
        getPrefs(context).edit()
            .putInt(KEY_WINDOW_X, x)
            .putInt(KEY_WINDOW_Y, y)
            .apply()
    }

    fun getWindowPosition(context: Context): Pair<Int, Int> {
        val prefs = getPrefs(context)
        val x = prefs.getInt(KEY_WINDOW_X, 0)
        val y = prefs.getInt(KEY_WINDOW_Y, 0)
        return Pair(x, y)
    }

    private const val KEY_SHOW_TEXT = "show_text"

    fun saveShowText(context: Context, show: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_TEXT, show).apply()
    }

    fun getShowText(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_TEXT, true)
    }

    private const val KEY_CAROUSEL_MODE = "carousel_mode"
    const val MODE_IN_APP = 0
    const val MODE_SUSPENDED = 1

    fun saveCarouselMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_CAROUSEL_MODE, mode).apply()
    }

    fun getCarouselMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_CAROUSEL_MODE, MODE_IN_APP)
    }

    private const val KEY_AUTO_JUMP = "auto_jump"
    fun saveAutoJump(context: Context, autoJump: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_JUMP, autoJump).apply()
    }
    fun getAutoJump(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_JUMP, false)
    }

    private const val KEY_URL_SCHEME = "url_scheme"
    fun saveUrlScheme(context: Context, urlScheme: String) {
        getPrefs(context).edit().putString(KEY_URL_SCHEME, urlScheme).apply()
    }
    fun getUrlScheme(context: Context): String {
        return getPrefs(context).getString(KEY_URL_SCHEME, "pinduoduo://com.xunmeng.pinduoduo/mdkd/package") ?: "pinduoduo://com.xunmeng.pinduoduo/mdkd/package"
    }

    private const val KEY_AUTO_START_DISABLED = "auto_start_disabled"
    fun saveAutoStartDisabled(context: Context, disabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_START_DISABLED, disabled).apply()
    }
    fun getAutoStartDisabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_START_DISABLED, false)
    }

    private const val KEY_CAROUSEL_SPEED = "carousel_speed"
    fun saveCarouselSpeed(context: Context, speed: Float) {
        getPrefs(context).edit().putFloat(KEY_CAROUSEL_SPEED, speed).apply()
    }
    fun getCarouselSpeed(context: Context): Float {
        return getPrefs(context).getFloat(KEY_CAROUSEL_SPEED, 1000f)
    }
}
