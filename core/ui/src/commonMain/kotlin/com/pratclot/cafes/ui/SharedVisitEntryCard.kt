package com.pratclot.cafes.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.ui.generated.resources.Res
import com.pratclot.cafes.core.ui.generated.resources.action_add
import com.pratclot.cafes.core.ui.generated.resources.action_add_photos
import com.pratclot.cafes.core.ui.generated.resources.action_archive
import com.pratclot.cafes.core.ui.generated.resources.action_archive_visit
import com.pratclot.cafes.core.ui.generated.resources.action_details
import com.pratclot.cafes.core.ui.generated.resources.action_restore
import com.pratclot.cafes.core.ui.generated.resources.action_restore_visit
import com.pratclot.cafes.core.ui.generated.resources.identifying_dishes
import com.pratclot.cafes.core.ui.generated.resources.visit_tag_input_label
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Immutable
data class VisitEntryCardState(
  val entry: CafeEntry,
  val archived: Boolean,
  val selected: Boolean = false,
  val swipeEnabled: Boolean = true,
  val showArchiveButton: Boolean = false,
  val tagging: Boolean,
  val taggingLabel: String? = null,
  val tagDraft: String,
  val photoPathsOverride: List<String>? = null,
)

sealed interface VisitEntryCardAction {
  data object OpenDetails : VisitEntryCardAction
  data class ChangeTagDraft(val draft: String) : VisitEntryCardAction
  data class ClickTag(val tag: String) : VisitEntryCardAction
  data object AddTag : VisitEntryCardAction
  data object AddPhotos : VisitEntryCardAction
  data object Archive : VisitEntryCardAction
  data object Restore : VisitEntryCardAction
}

@Composable
fun SharedVisitEntryCard(
  state: VisitEntryCardState,
  onAction: (VisitEntryCardAction) -> Unit,
  modifier: Modifier = Modifier,
  photoContent: @Composable (path: String, modifier: Modifier) -> Unit,
) {
  val entry = state.entry
  val archived = state.archived
  val photos = remember(entry.photoPath, entry.photoPaths, state.photoPathsOverride) {
    (state.photoPathsOverride ?: entry.photoPaths).ifEmpty { listOf(entry.photoPath) }
  }
  var photoIndex by remember(entry.id) { mutableIntStateOf(0) }
  LaunchedEffect(entry.id, photos) {
    photoIndex = 0
    if (photos.size > 1) {
      while (true) {
        delay(3_200L)
        photoIndex = (photoIndex + 1) % photos.size
      }
    }
  }

  val density = LocalDensity.current
  val defaultWidthPx = remember(density) { with(density) { 360.dp.toPx() } }
  val swipeOffset = remember(entry.id) { Animatable(0f) }
  var dragOffset by remember(entry.id) { mutableFloatStateOf(0f) }
  var dragging by remember(entry.id) { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  val actionLabel = stringResource(if (archived) Res.string.action_restore_visit else Res.string.action_archive_visit)

  BoxWithConstraints(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(22.dp))
      .semantics {
        customActions = listOf(
          CustomAccessibilityAction(
            label = actionLabel,
            action = {
              onAction(if (archived) VisitEntryCardAction.Restore else VisitEntryCardAction.Archive)
              true
            },
          ),
        )
      },
  ) {
    val widthPx = with(density) { maxWidth.toPx() }.takeIf { it > 0f } ?: defaultWidthPx
    val currentOffset = if (dragging) dragOffset else swipeOffset.value
    val swipeProgress = (-currentOffset / widthPx).coerceIn(0f, 1f)
    val actionColor = if (archived) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val actionContentColor = if (archived) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onError

    Card(
      modifier = Modifier
        .fillMaxWidth()
        .offset { IntOffset(currentOffset.roundToInt(), 0) }
        .then(
          if (state.swipeEnabled) {
            Modifier.pointerInput(entry.id, archived, widthPx) {
              detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                  change.consume()
                  dragging = true
                  dragOffset = (dragOffset + dragAmount).coerceIn(-widthPx, 0f)
                },
                onDragEnd = {
                  scope.launch {
                    swipeOffset.snapTo(dragOffset)
                    dragging = false
                    if (dragOffset < -widthPx * 0.38f) {
                      swipeOffset.animateTo(-widthPx, tween(190))
                      onAction(if (archived) VisitEntryCardAction.Restore else VisitEntryCardAction.Archive)
                    } else {
                      swipeOffset.animateTo(0f, tween(220))
                    }
                    dragOffset = 0f
                  }
                },
                onDragCancel = {
                  scope.launch {
                    swipeOffset.snapTo(dragOffset)
                    dragging = false
                    swipeOffset.animateTo(0f, tween(220))
                    dragOffset = 0f
                  }
                },
              )
            }
          } else {
            Modifier
          },
        ),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(
        containerColor = if (state.selected) {
          MaterialTheme.colorScheme.secondaryContainer
        } else {
          MaterialTheme.colorScheme.surfaceContainerLow
        },
      ),
    ) {
      Box {
        Column {
          Box(Modifier.fillMaxWidth().height(215.dp).clickable { onAction(VisitEntryCardAction.OpenDetails) }) {
            Crossfade(
              targetState = photos[photoIndex.coerceIn(0, photos.lastIndex)],
              animationSpec = tween(700),
              label = "Cafe visit photos",
            ) { path ->
              photoContent(path, Modifier.fillMaxSize())
            }
            FilledTonalButton(
              onClick = { onAction(VisitEntryCardAction.AddPhotos) },
              modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Text(stringResource(Res.string.action_add_photos))
            }
          }
          Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = entry.venue,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              TextButton(
                onClick = { onAction(VisitEntryCardAction.OpenDetails) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
              ) {
                Text(stringResource(Res.string.action_details))
              }
              if (state.showArchiveButton) {
                TextButton(
                  onClick = { onAction(if (archived) VisitEntryCardAction.Restore else VisitEntryCardAction.Archive) },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                  Text(stringResource(if (archived) Res.string.action_restore else Res.string.action_archive))
                }
              }
            }
            if (entry.address.isNotBlank()) {
              Text(
                entry.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(Modifier.height(5.dp))
            Text(
              formatTimestamp(entry.visitedAt),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.tagging) {
              Spacer(Modifier.height(8.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                  state.taggingLabel ?: stringResource(Res.string.identifying_dishes),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            val tags = remember(entry.autoTags, entry.manualTags) {
              (entry.autoTags + entry.manualTags).distinctBy(String::lowercase)
            }
            if (tags.isNotEmpty()) {
              Spacer(Modifier.height(7.dp))
              Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
              ) {
                tags.forEach { tag ->
                  SuggestionChip(onClick = { onAction(VisitEntryCardAction.ClickTag(tag)) }, label = { Text(tag) })
                }
              }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              OutlinedTextField(
                value = state.tagDraft,
                onValueChange = { onAction(VisitEntryCardAction.ChangeTagDraft(it)) },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(Res.string.visit_tag_input_label)) },
                singleLine = true,
              )
              Spacer(Modifier.width(8.dp))
              TextButton(
                onClick = { onAction(VisitEntryCardAction.AddTag) },
                enabled = state.tagDraft.isNotBlank(),
              ) { Text(stringResource(Res.string.action_add)) }
            }
          }
        }
        if (swipeProgress > 0f) {
          Box(
            Modifier.matchParentSize().background(actionColor.copy(alpha = swipeProgress)),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              if (archived) "↶" else "▣",
              style = MaterialTheme.typography.displaySmall,
              color = actionContentColor.copy(alpha = swipeProgress),
            )
          }
        }
      }
    }
  }
}
