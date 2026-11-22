@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.pratclot.cafes.web

import kotlinx.browser.window
import kotlin.js.JsName
import kotlin.random.Random

private external fun encodeURIComponent(value: String): String

private external fun decodeURIComponent(value: String): String

private external object Date {
  fun now(): Double
}

@JsName("crypto")
private external object JsCrypto {
  fun randomUUID(): String
}

internal fun browserNowMillis(): Double = Date.now()

internal fun encodeBrowserValue(value: String): String = encodeURIComponent(value)

internal fun decodeBrowserValue(value: String): String = decodeURIComponent(value)

internal fun browserStorageRead(key: String): String? =
  runCatching { window.localStorage.getItem(key) }.getOrNull()

internal fun browserStorageWrite(key: String, value: String): Boolean =
  runCatching {
    window.localStorage.setItem(key, value)
    true
  }.getOrDefault(false)

internal fun browserStorageRemove(key: String) {
  runCatching { window.localStorage.removeItem(key) }
}

internal fun browserRandomUuid(): String =
  runCatching { JsCrypto.randomUUID() }.getOrElse {
    "${Date.now().toLong().toString(36)}-${Random.nextLong().toULong().toString(36)}"
  }

internal fun browserRequestPhotos(onComplete: (String) -> Unit) {
  browserPickImageFiles(captureCamera = false, multiple = true, onComplete = onComplete)
}

internal fun browserRequestCameraFallback(onComplete: (String) -> Unit) {
  browserPickImageFiles(captureCamera = true, multiple = false, onComplete = onComplete)
}
