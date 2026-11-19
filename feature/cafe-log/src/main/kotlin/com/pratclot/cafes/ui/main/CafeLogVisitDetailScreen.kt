package com.pratclot.cafes.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratclot.cafes.core.ui.generated.resources.Res
import com.pratclot.cafes.core.ui.generated.resources.action_add
import com.pratclot.cafes.core.ui.generated.resources.action_add_photos
import com.pratclot.cafes.core.ui.generated.resources.action_archive
import com.pratclot.cafes.core.ui.generated.resources.action_back
import com.pratclot.cafes.core.ui.generated.resources.action_restore
import com.pratclot.cafes.core.ui.generated.resources.detail_placeholder_title
import com.pratclot.cafes.core.ui.generated.resources.detail_visit_not_found
import com.pratclot.cafes.core.ui.generated.resources.identifying_dishes_on_device
import com.pratclot.cafes.core.ui.generated.resources.visit_tag_input_label
import com.pratclot.cafes.ui.SharedClassificationSection
import com.pratclot.cafes.ui.formatTimestamp
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun VisitDetailScreen(
  entryId: String,
  viewModel: CafeLogViewModel,
  showBackButton: Boolean,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val entry = remember(state.entries, state.archivedEntries, entryId) {
    (state.entries + state.archivedEntries).firstOrNull { it.id == entryId }
  }
  val isArchived = remember(state.archivedEntries, entryId) {
    state.archivedEntries.any { it.id == entryId }
  }
  val tagging = entryId in state.taggingIds
  val coroutineScope = rememberCoroutineScope()
  var tagDraft by rememberSaveable(entryId) { mutableStateOf("") }

  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        val savedPaths = copyPickedPhotosToFilesDir(context, uris)
        viewModel.savePickedPhotos(entryId, savedPaths)
      }
    }
  }

  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    if (entry == null) {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(stringResource(Res.string.detail_visit_not_found), style = MaterialTheme.typography.titleMedium)
          if (showBackButton) {
            OutlinedButton(onClick = onBack) { Text(stringResource(Res.string.action_back)) }
          }
        }
      }
      return@Scaffold
    }

    val photos = remember(entry.photoPath, entry.photoPaths) {
      entry.photoPaths.filter { File(it).exists() }.ifEmpty { listOf(entry.photoPath) }
    }
    var photoIndex by remember(entry.id) { mutableIntStateOf(0) }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding)
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (showBackButton) {
          OutlinedButton(
            onClick = onBack,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Text(stringResource(Res.string.action_back))
          }
        }
        Text(
          text = entry.venue,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        TextButton(
          onClick = {
            if (isArchived) viewModel.restoreEntry(entry.id) else viewModel.archiveEntry(entry.id)
          },
        ) {
          Text(stringResource(if (isArchived) Res.string.action_restore else Res.string.action_archive))
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(260.dp)
          .clip(RoundedCornerShape(18.dp))
          .background(Color(0xFF1C1815)),
      ) {
        val currentPath = photos[photoIndex.coerceIn(0, photos.lastIndex)]
        AsyncLocalPhoto(path = currentPath, modifier = Modifier.fillMaxSize())
        FilledTonalButton(
          onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
          modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
          Text(stringResource(Res.string.action_add_photos))
        }
      }

      if (photos.size > 1) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          photos.forEachIndexed { idx, _ ->
            OutlinedButton(
              onClick = { photoIndex = idx },
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
              Text(if (idx == photoIndex) "• Photo ${idx + 1}" else "Photo ${idx + 1}")
            }
          }
        }
      }

      if (entry.address.isNotBlank()) {
        Text(entry.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(
        formatTimestamp(entry.visitedAt),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (tagging) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(Res.string.identifying_dishes_on_device), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }

      val tags = remember(entry.autoTags, entry.manualTags) {
        (entry.autoTags + entry.manualTags).distinctBy(String::lowercase)
      }
      if (tags.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          tags.forEach { tag ->
            SuggestionChip(onClick = {}, label = { Text(tag) })
          }
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = tagDraft,
          onValueChange = { tagDraft = it },
          modifier = Modifier.weight(1f),
          label = { Text(stringResource(Res.string.visit_tag_input_label)) },
          singleLine = true,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
          onClick = {
            viewModel.addManualTag(entry.id, tagDraft)
            tagDraft = ""
          },
          enabled = tagDraft.isNotBlank(),
        ) {
          Text(stringResource(Res.string.action_add))
        }
      }

      HorizontalDivider()
      SharedClassificationSection(
        entry = entry,
        currentModelVersion = entry.taggingModelVersion ?: state.modelVersion,
        tagging = tagging,
        onRetryClassification = { viewModel.retryClassification(entry.id) },
      )
    }
  }
}

@Composable
internal fun VisitDetailPlaceholder(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.fillMaxSize().padding(32.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("☕", style = MaterialTheme.typography.displayMedium)
      Text(stringResource(Res.string.detail_placeholder_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
      Text(
        "Pick a visit from the log to inspect its photos, dish tags, and on-device classification runs.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}
