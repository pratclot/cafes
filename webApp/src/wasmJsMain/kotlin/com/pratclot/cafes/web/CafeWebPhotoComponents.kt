package com.pratclot.cafes.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pratclot.cafes.core.ui.generated.resources.Res
import com.pratclot.cafes.core.ui.generated.resources.photo_unavailable
import com.pratclot.cafes.core.ui.generated.resources.photo_visit_cd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val browserPhotoCache = LinkedHashMap<String, ImageBitmap>()
private const val MAX_BROWSER_PHOTO_CACHE = 32

@Composable
internal fun BrowserPhoto(dataUrl: String, modifier: Modifier = Modifier) {
  val bitmap by produceState<ImageBitmap?>(initialValue = browserPhotoCache[dataUrl], dataUrl) {
    browserPhotoCache[dataUrl]?.let { cached ->
      value = cached
      return@produceState
    }
    val resolvedUrl = if (dataUrl.startsWith("idb://")) loadPhotoBlobSuspend(dataUrl) else dataUrl
    if (resolvedUrl.isEmpty()) {
      value = null
      return@produceState
    }
    val decoded = withContext(Dispatchers.Default) { decodeImage(resolvedUrl) }
    if (decoded != null) {
      if (browserPhotoCache.size >= MAX_BROWSER_PHOTO_CACHE) {
        browserPhotoCache.keys.firstOrNull()?.let { oldest -> browserPhotoCache.remove(oldest) }
      }
      browserPhotoCache[dataUrl] = decoded
    }
    value = decoded
  }
  if (bitmap == null) {
    Box(
      modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Text(stringResource(Res.string.photo_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  } else {
    Image(
      bitmap!!,
      contentDescription = stringResource(Res.string.photo_visit_cd),
      modifier = modifier,
      contentScale = ContentScale.Crop,
    )
  }
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeImage(dataUrl: String): ImageBitmap? = runCatching {
  val bytes = Base64.Default.decode(dataUrl.substringAfter(',', missingDelimiterValue = ""))
  bytes.decodeToImageBitmap()
}.getOrNull()

internal fun decodePhotoSelection(value: String): List<String> = value.lineSequence()
  .filter(String::isNotBlank)
  .map(::decodeBrowserValue)
  .toList()
