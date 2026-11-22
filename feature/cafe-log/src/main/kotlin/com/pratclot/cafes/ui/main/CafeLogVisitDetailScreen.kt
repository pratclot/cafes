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
import androidx.compose.runtime.Immutable
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
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.ui.generated.resources.Res
import com.pratclot.cafes.core.ui.generated.resources.action_add
import com.pratclot.cafes.core.ui.generated.resources.action_add_photos
import com.pratclot.cafes.core.ui.generated.resources.action_archive
import com.pratclot.cafes.core.ui.generated.resources.action_back
import com.pratclot.cafes.core.ui.generated.resources.action_restore
import com.pratclot.cafes.core.ui.generated.resources.detail_placeholder_subtitle
import com.pratclot.cafes.core.ui.generated.resources.detail_placeholder_title
import com.pratclot.cafes.core.ui.generated.resources.detail_visit_not_found
import com.pratclot.cafes.core.ui.generated.resources.identifying_dishes_on_device
import com.pratclot.cafes.core.ui.generated.resources.photo_index
import com.pratclot.cafes.core.ui.generated.resources.photo_index_selected
import com.pratclot.cafes.core.ui.generated.resources.visit_tag_input_label
import com.pratclot.cafes.ui.ClassificationSectionState
import com.pratclot.cafes.ui.SharedClassificationSection
import com.pratclot.cafes.ui.formatTimestamp
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Immutable
internal data class VisitDetailContentState(
  val entry: CafeEntry,
  val isArchived: Boolean,
  val showBackButton: Boolean,
  val tagging: Boolean,
  val modelVersion: String,
  val tagDraft: String,
)

internal sealed interface VisitDetailAction {
  data object Back : VisitDetailAction
  data object ToggleArchive : VisitDetailAction
  data object AddPhotos : VisitDetailAction
  data class ChangeTagDraft(val draft: String) : VisitDetailAction
  data object AddManualTag : VisitDetailAction
  data object RetryClassification : VisitDetailAction
}

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

    VisitDetailContent(
      state = VisitDetailContentState(
        entry = entry,
        isArchived = isArchived,
        showBackButton = showBackButton,
        tagging = tagging,
        modelVersion = entry.taggingModelVersion ?: state.modelVersion,
        tagDraft = tagDraft,
      ),
      onAction = { action ->
        when (action) {
          VisitDetailAction.Back -> onBack()
          VisitDetailAction.ToggleArchive -> {
            if (isArchived) viewModel.restoreEntry(entry.id) else viewModel.archiveEntry(entry.id)
          }
          VisitDetailAction.AddPhotos -> {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          }
          is VisitDetailAction.ChangeTagDraft -> tagDraft = action.draft
          VisitDetailAction.AddManualTag -> {
            viewModel.addManualTag(entry.id, tagDraft)
            tagDraft = ""
          }
          VisitDetailAction.RetryClassification -> viewModel.retryClassification(entry.id)
        }
      },
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding),
    )
  }
}

@Composable
private fun VisitDetailContent(
  state: VisitDetailContentState,
  onAction: (VisitDetailAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  val entry = state.entry
  val photos = remember(entry.photoPath, entry.photoPaths) {
    entry.photoPaths.filter { File(it).exists() }.ifEmpty { listOf(entry.photoPath) }
  }
  var photoIndex by remember(entry.id) { mutableIntStateOf(0) }

  Column(
    modifier = modifier
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
      if (state.showBackButton) {
        OutlinedButton(
          onClick = { onAction(VisitDetailAction.Back) },
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
        onClick = { onAction(VisitDetailAction.ToggleArchive) },
      ) {
        Text(stringResource(if (state.isArchived) Res.string.action_restore else Res.string.action_archive))
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
        onClick = { onAction(VisitDetailAction.AddPhotos) },
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
            Text(
              stringResource(
                if (idx == photoIndex) Res.string.photo_index_selected else Res.string.photo_index,
                idx + 1,
              ),
            )
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

    if (state.tagging) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
          stringResource(Res.string.identifying_dishes_on_device),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        value = state.tagDraft,
        onValueChange = { onAction(VisitDetailAction.ChangeTagDraft(it)) },
        modifier = Modifier.weight(1f),
        label = { Text(stringResource(Res.string.visit_tag_input_label)) },
        singleLine = true,
      )
      Spacer(Modifier.width(8.dp))
      TextButton(
        onClick = { onAction(VisitDetailAction.AddManualTag) },
        enabled = state.tagDraft.isNotBlank(),
      ) {
        Text(stringResource(Res.string.action_add))
      }
    }

    HorizontalDivider()
    SharedClassificationSection(
      state = ClassificationSectionState(
        entry = entry,
        currentModelVersion = state.modelVersion,
        tagging = state.tagging,
      ),
      onRetryClassification = { onAction(VisitDetailAction.RetryClassification) },
    )
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
        stringResource(Res.string.detail_placeholder_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}
