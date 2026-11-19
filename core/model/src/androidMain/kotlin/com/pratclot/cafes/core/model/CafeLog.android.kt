package com.pratclot.cafes.core.model

import android.util.Log

internal actual fun defaultPlatformLog(tag: String, message: String, throwable: Throwable?) {
  try {
    if (throwable != null) {
      Log.w(tag, message, throwable)
    } else {
      Log.w(tag, message)
    }
  } catch (_: RuntimeException) {
    val detail = throwable?.let { ": ${it.message ?: it::class.simpleName}" }.orEmpty()
    println("[$tag] $message$detail")
  }
}
