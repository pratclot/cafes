package com.pratclot.cafes.ui

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun formatEpochMillisJs(epochMillis: Double): String =
  js("new Date(epochMillis).toLocaleString()")

actual fun formatTimestamp(epochMillis: Long): String =
  formatEpochMillisJs(epochMillis.toDouble())
