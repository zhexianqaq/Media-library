package com.zhexian.douyingallery.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {

    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        if (gb < 1024) return String.format(Locale.US, "%.2f GB", gb)
        return String.format(Locale.US, "%.2f TB", gb / 1024.0)
    }

    fun duration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    private val fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun dateTime(ms: Long): String = fullFormat.format(Date(ms))

    fun day(ms: Long): String = dayFormat.format(Date(ms))

    /** 「全部」页的日期分组标题：今天 / 昨天 / 2026-11-01 */
    fun dayGroup(ms: Long): String {
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = ms }
        val sameYear = today.get(Calendar.YEAR) == target.get(Calendar.YEAR)
        val dayOfYear = today.get(Calendar.DAY_OF_YEAR)
        val targetDay = target.get(Calendar.DAY_OF_YEAR)
        return when {
            sameYear && dayOfYear == targetDay -> "今天"
            sameYear && dayOfYear - targetDay == 1 -> "昨天"
            else -> dayFormat.format(Date(ms))
        }
    }

    fun relativeDay(ms: Long): String {
        val diff = System.currentTimeMillis() - ms
        val day = 24 * 60 * 60 * 1000L
        return when {
            diff < day -> "今天"
            diff < 2 * day -> "昨天"
            diff < 7 * day -> "${diff / day} 天前"
            else -> dayFormat.format(Date(ms))
        }
    }

    /** 简介目录名 + 省略号；用于列表标题 */
    fun ellipsize(text: String, max: Int = 24): String =
        if (text.length <= max) text else text.take(max) + "…"
}
