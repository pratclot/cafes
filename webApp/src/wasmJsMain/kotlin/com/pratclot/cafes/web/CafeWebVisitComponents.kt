package com.pratclot.cafes.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.ui.generated.resources.Res
import com.pratclot.cafes.core.ui.generated.resources.action_back_to_visits
import com.pratclot.cafes.core.ui.generated.resources.action_close
import com.pratclot.cafes.core.ui.generated.resources.identifying_dishes_in_browser
import com.pratclot.cafes.ui.SharedCompactLocationBar
import com.pratclot.cafes.ui.SharedVisitDetailsContent
import com.pratclot.cafes.ui.SharedVisitEntryCard
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CompactLocationBar(
  selectedCafe: CafeCandidate?,
  manualCafe: String,
  locationMessage: String,
  loadingLocation: Boolean,
  cafes: List<CafeCandidate>,
  editing: Boolean,
  showAddPhotosAction: Boolean,
  onToggleEditing: () -> Unit,
  onRefresh: () -> Unit,
  onSelectCafe: (CafeCandidate) -> Unit,
  onManualCafeChange: (String) -> Unit,
  onApplyManualCafe: () -> Unit,
  onAddPhotos: () -> Unit,
) {
  SharedCompactLocationBar(
    selectedCafe = selectedCafe,
    manualCafe = manualCafe,
    locationMessage = locationMessage,
    loadingLocation = loadingLocation,
    cafes = cafes,
    editing = editing,
    showAddPhotosAction = showAddPhotosAction,
    onToggleEditing = onToggleEditing,
    onRefresh = onRefresh,
    onSelectCafe = onSelectCafe,
    onManualCafeChange = onManualCafeChange,
    onApplyManualCafe = onApplyManualCafe,
    onAddPhotos = onAddPhotos,
  )
}

@Composable
internal fun VisitCard(
  entry: CafeEntry,
  archived: Boolean,
  showArchiveButton: Boolean,
  swipeEnabled: Boolean,
  tagging: Boolean,
  tagDraft: String,
  onDetails: () -> Unit,
  onTagClick: (String) -> Unit,
  onTagDraft: (String) -> Unit,
  onAddTag: () -> Unit,
  onPhotos: () -> Unit,
  onArchive: () -> Unit,
  onRestore: () -> Unit,
) {
  SharedVisitEntryCard(
    entry = entry,
    archived = archived,
    swipeEnabled = swipeEnabled,
    showArchiveButton = showArchiveButton,
    tagging = tagging,
    taggingLabel = stringResource(Res.string.identifying_dishes_in_browser),
    tagDraft = tagDraft,
    onOpenDetails = onDetails,
    onTagDraft = onTagDraft,
    onTagClick = onTagClick,
    onAddTag = onAddTag,
    onAddPhotos = onPhotos,
    onArchive = onArchive,
    onRestore = onRestore,
    photoContent = { photo, modifier -> BrowserPhoto(photo, modifier) },
  )
}

@Composable
internal fun VisitDetailsScreen(
  entry: CafeEntry,
  tagging: Boolean,
  onClose: () -> Unit,
  onRetryClassification: () -> Unit,
) {
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    VisitDetailsContent(
      entry = entry,
      tagging = tagging,
      onClose = onClose,
      onRetryClassification = onRetryClassification,
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
      fullScreen = true,
    )
  }
}

@Composable
internal fun VisitDetailsContent(
  entry: CafeEntry,
  tagging: Boolean,
  onClose: () -> Unit,
  onRetryClassification: () -> Unit,
  modifier: Modifier,
  fullScreen: Boolean,
) {
  SharedVisitDetailsContent(
    entry = entry,
    currentModelVersion = BROWSER_FOOD_MODEL_VERSION,
    tagging = tagging,
    onClose = onClose,
    closeLabel = stringResource(if (fullScreen) Res.string.action_back_to_visits else Res.string.action_close),
    onRetryClassification = onRetryClassification,
    modifier = modifier,
  )
}

internal fun Double.format(digits: Int): String = toString().let { value ->
  val dot = value.indexOf('.')
  if (dot < 0) value else value.take(dot + digits + 1)
}
