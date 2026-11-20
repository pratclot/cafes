@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.pratclot.cafes.web

import kotlinx.browser.window
import kotlin.js.JsModule
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

@JsModule("./browser-interop.mjs")
internal external fun browserSavePhotoBlob(key: String, dataUrl: String, onDone: (Boolean) -> Unit)

@JsModule("./browser-interop.mjs")
internal external fun browserLoadPhotoBlob(key: String, onDone: (String) -> Unit)

@JsModule("./browser-interop.mjs")
internal external fun browserClassifyPhotos(photos: String, onComplete: (String) -> Unit)

internal fun browserRequestPhotos(onComplete: (String) -> Unit) {
  browserPickImageFiles(captureCamera = false, multiple = true, onComplete = onComplete)
}

internal fun browserRequestCameraFallback(onComplete: (String) -> Unit) {
  browserPickImageFiles(captureCamera = true, multiple = false, onComplete = onComplete)
}

@JsModule("./browser-interop.mjs")
private external fun browserPickImageFiles(
  captureCamera: Boolean,
  multiple: Boolean,
  onComplete: (String) -> Unit,
)

@JsModule("./browser-interop.mjs")
internal external fun browserStartCamera(facingMode: String, onState: (Boolean, String) -> Unit)

@JsModule("./browser-interop.mjs")
internal external fun browserStopCamera()

@JsModule("./browser-interop.mjs")
internal external fun browserUpdateCameraBounds(
  left: Double,
  top: Double,
  width: Double,
  height: Double,
  visible: Boolean,
)

@JsModule("./browser-interop.mjs")
internal external fun browserCaptureVideoFrame(): String

@JsModule("./browser-interop.mjs")
internal external fun browserRequestLocation(onResult: (Double, Double, Double, String) -> Unit)

@JsModule("./browser-interop.mjs")
internal external fun browserFindNearbyCafes(latitude: Double, longitude: Double, onResult: (String) -> Unit)
