package com.example.sceencap.core.engine

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AiRateLimiter — giới hạn số lần sử dụng AI edit ảnh mỗi ngày.
 *
 * Dùng SharedPreferences để lưu trữ. Reset tự động khi sang ngày mới.
 */
class AiRateLimiter(context: Context) {

    companion object {
        private const val PREF_NAME = "ai_rate_limiter"
        private const val KEY_DATE = "last_date"
        private const val KEY_COUNT = "usage_count"
        private const val MAX_DAILY_USES = 15
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Kiểm tra user còn được dùng AI edit không.
     */
    fun canUse(): Boolean {
        resetIfNewDay()
        return prefs.getInt(KEY_COUNT, 0) < MAX_DAILY_USES
    }

    /**
     * Ghi nhận 1 lần sử dụng.
     */
    fun recordUse() {
        resetIfNewDay()
        val current = prefs.getInt(KEY_COUNT, 0)
        prefs.edit().putInt(KEY_COUNT, current + 1).apply()
    }

    /**
     * Số lần còn lại hôm nay.
     */
    fun getRemainingUses(): Int {
        resetIfNewDay()
        return MAX_DAILY_USES - prefs.getInt(KEY_COUNT, 0)
    }

    private fun resetIfNewDay() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val savedDate = prefs.getString(KEY_DATE, "")
        if (savedDate != today) {
            prefs.edit()
                .putString(KEY_DATE, today)
                .putInt(KEY_COUNT, 0)
                .apply()
        }
    }
}
