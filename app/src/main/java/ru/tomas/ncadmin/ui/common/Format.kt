package ru.tomas.ncadmin.ui.common

import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "∞"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "${bytes} B" else String.format(Locale.getDefault(), "%.1f %s", v, units[i])
}

fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
