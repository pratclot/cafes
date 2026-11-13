package com.pratclot.cafes.web

import com.pratclot.cafes.core.model.FoodLabel
import com.pratclot.cafes.core.model.FoodTaggerFacade
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Browser-local inference boundary. The model and photos are processed by the device browser. */
internal class MediaPipeFoodTaggerFacade : FoodTaggerFacade {
  override suspend fun classify(photoPaths: List<String>): List<FoodLabel> = suspendCancellableCoroutine { continuation ->
    browserClassifyPhotos(photoPaths.joinToString("\n") { encodeBrowserValue(it) }) { result ->
      if (!continuation.isActive) return@browserClassifyPhotos
      if (result.startsWith("!ERROR")) {
        val detail = result.substringAfter('|', "").takeIf(String::isNotEmpty)?.let(::decodeBrowserValue)
        val message = if (detail.isNullOrBlank()) {
          "The browser food classifier could not be loaded."
        } else {
          "The browser food classifier could not be loaded ($detail)."
        }
        continuation.resumeWith(Result.failure(IllegalStateException(message)))
      } else {
        continuation.resume(result.lineSequence().mapNotNull { row ->
          val name = decodeBrowserValue(row.substringBefore('|'))
          val confidence = row.substringAfter('|', "").toFloatOrNull() ?: return@mapNotNull null
          FoodLabel(name, confidence)
        }.toList())
      }
    }
  }

}

internal const val BROWSER_FOOD_MODEL_VERSION = "google-siglip-food-v1"
