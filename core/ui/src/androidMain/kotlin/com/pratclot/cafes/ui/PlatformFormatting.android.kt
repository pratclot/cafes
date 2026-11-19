package com.pratclot.cafes.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timestampFormat = ThreadLocal.withInitial {
  SimpleDateFormat("EEE, d MMM · HH:mm:ss", Locale.getDefault())
}

actual fun formatTimestamp(epochMillis: Long): String =
  requireNotNull(timestampFormat.get()).format(Date(epochMillis))
