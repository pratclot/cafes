package com.pratclot.cafes.core.model

object CafeLog {
  var handler: (tag: String, message: String, throwable: Throwable?) -> Unit = ::defaultPlatformLog

  fun w(tag: String, message: String, throwable: Throwable? = null) {
    handler(tag, message, throwable)
  }

  fun e(tag: String, message: String, throwable: Throwable? = null) {
    handler(tag, message, throwable)
  }
}

internal expect fun defaultPlatformLog(tag: String, message: String, throwable: Throwable?)
