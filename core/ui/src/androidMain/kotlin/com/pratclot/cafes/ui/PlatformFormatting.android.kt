package com.pratclot.cafes.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatTimestamp(epochMillis: Long): String =
  SimpleDateFormat("EEE, d MMM · HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))
