package com.pratclot.cafes.core.model

internal actual fun defaultPlatformLog(tag: String, message: String, throwable: Throwable?) {
  val detail = throwable?.let { ": ${it.message ?: it::class.simpleName}" }.orEmpty()
  println("[$tag] $message$detail")
}
