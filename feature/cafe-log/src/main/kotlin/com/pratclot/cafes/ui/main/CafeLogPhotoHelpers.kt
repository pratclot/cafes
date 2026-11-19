package com.pratclot.cafes.ui.main

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.pratclot.cafes.core.model.decodeSampledBitmap
import com.pratclot.cafes.core.ui.generated.resources.Res
import com.pratclot.cafes.core.ui.generated.resources.photo_visit_cd
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

internal suspend fun copyPickedPhotosToFilesDir(context: Context, uris: List<Uri>): List<String> =
  withContext(Dispatchers.IO) {
    uris.mapNotNull { uri ->
      runCatching {
        val file = File(context.filesDir, "cafe-${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
          file.outputStream().use(input::copyTo)
        } ?: return@mapNotNull null
        file.absolutePath
      }.getOrNull()
    }
  }

private val localPhotoCache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
  override fun sizeOf(key: String, value: ImageBitmap): Int =
    (value.width * value.height * 4).coerceAtLeast(1)
}

@Composable
internal fun AsyncLocalPhoto(path: String, modifier: Modifier = Modifier) {
  val bitmap by produceState<ImageBitmap?>(initialValue = localPhotoCache.get(path), path) {
    val cached = localPhotoCache.get(path)
    if (cached != null) {
      value = cached
      return@produceState
    }
    value = withContext(Dispatchers.IO) {
      decodeSampledBitmap(path, maxDimensionPx = 1024)
        ?.asImageBitmap()
        ?.also { localPhotoCache.put(path, it) }
    }
  }
  if (bitmap != null) {
    Image(
      bitmap = bitmap!!,
      contentDescription = stringResource(Res.string.photo_visit_cd),
      modifier = modifier,
      contentScale = ContentScale.Crop,
    )
  }
}
