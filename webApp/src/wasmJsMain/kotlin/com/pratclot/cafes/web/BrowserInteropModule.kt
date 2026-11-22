@file:JsModule("./browser-interop.mjs")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.pratclot.cafes.web

internal external fun browserSavePhotoBlob(key: String, dataUrl: String, onDone: (Boolean) -> Unit)

internal external fun browserLoadPhotoBlob(key: String, onDone: (String) -> Unit)

internal external fun browserClassifyPhotos(photos: String, onComplete: (String) -> Unit)

internal external fun browserPickImageFiles(
  captureCamera: Boolean,
  multiple: Boolean,
  onComplete: (String) -> Unit,
)

internal external fun browserStartCamera(facingMode: String, onState: (Boolean, String) -> Unit)

internal external fun browserStopCamera()

internal external fun browserUpdateCameraBounds(
  left: Double,
  top: Double,
  width: Double,
  height: Double,
  visible: Boolean,
)

internal external fun browserCaptureVideoFrame(): String

internal external fun browserRequestLocation(onResult: (Double, Double, Double, String) -> Unit)

internal external fun browserFindNearbyCafes(latitude: Double, longitude: Double, onResult: (String) -> Unit)
