package com.pratclot.cafes.core.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface

/** Decodes a local image file with power-of-two subsampling and EXIF orientation applied. */
fun decodeSampledBitmap(path: String, maxDimensionPx: Int): Bitmap? {
  if (path.isBlank() || maxDimensionPx <= 0) return null
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(path, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

  var sampleSize = 1
  val maxSource = maxOf(bounds.outWidth, bounds.outHeight)
  while (maxSource / (sampleSize * 2) >= maxDimensionPx) {
    sampleSize *= 2
  }

  val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
  val decoded = BitmapFactory.decodeFile(path, decodeOptions) ?: return null
  val matrix = exifMatrixFor(path) ?: return decoded
  return try {
    val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (transformed !== decoded) decoded.recycle()
    transformed
  } catch (error: OutOfMemoryError) {
    CafeLog.w("AndroidBitmapDecoder", "OOM applying EXIF transform for $path", error)
    decoded
  }
}

private fun exifMatrixFor(path: String): Matrix? {
  val orientation = runCatching {
    ExifInterface(path).getAttributeInt(
      ExifInterface.TAG_ORIENTATION,
      ExifInterface.ORIENTATION_NORMAL,
    )
  }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

  val matrix = Matrix()
  when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
    ExifInterface.ORIENTATION_TRANSPOSE -> {
      matrix.postRotate(90f)
      matrix.postScale(-1f, 1f)
    }
    ExifInterface.ORIENTATION_TRANSVERSE -> {
      matrix.postRotate(270f)
      matrix.postScale(-1f, 1f)
    }
    else -> return null
  }
  return matrix
}
