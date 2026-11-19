package com.pratclot.cafes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.ClassificationRun
import com.pratclot.cafes.core.ui.generated.resources.Res
import com.pratclot.cafes.core.ui.generated.resources.classification_current_model
import com.pratclot.cafes.core.ui.generated.resources.classification_detected_labels
import com.pratclot.cafes.core.ui.generated.resources.classification_error
import com.pratclot.cafes.core.ui.generated.resources.classification_heading
import com.pratclot.cafes.core.ui.generated.resources.classification_label_confidence
import com.pratclot.cafes.core.ui.generated.resources.classification_legacy_history
import com.pratclot.cafes.core.ui.generated.resources.classification_no_detections
import com.pratclot.cafes.core.ui.generated.resources.classification_none_yet
import com.pratclot.cafes.core.ui.generated.resources.classification_retry
import com.pratclot.cafes.core.ui.generated.resources.classification_run_history
import com.pratclot.cafes.core.ui.generated.resources.classification_run_summary
import com.pratclot.cafes.core.ui.generated.resources.classification_running
import com.pratclot.cafes.core.ui.generated.resources.photo_count
import com.pratclot.cafes.core.ui.generated.resources.visit_location
import com.pratclot.cafes.core.ui.generated.resources.visit_tags_heading
import com.pratclot.cafes.core.ui.generated.resources.visit_visited_at
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ClassificationRunDetails(run: ClassificationRun) {
  Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(formatTimestamp(run.occurredAt), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    val summary = when (run.outcome) {
      "ERROR" -> stringResource(Res.string.classification_error)
      "NO_DETECTIONS" -> stringResource(Res.string.classification_no_detections)
      else -> pluralStringResource(Res.plurals.classification_detected_labels, run.labels.size, run.labels.size)
    }
    val photoText = pluralStringResource(Res.plurals.photo_count, run.photoCount, run.photoCount)
    Text(
      stringResource(Res.string.classification_run_summary, summary, photoText, run.durationMillis, run.modelVersion),
      style = MaterialTheme.typography.bodySmall,
      color = if (run.outcome == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (run.labels.isNotEmpty()) {
      val formattedLabels = run.labels.map {
        stringResource(Res.string.classification_label_confidence, it.name, (it.confidence * 100).toInt())
      }.joinToString(" · ")
      Text(
        formattedLabels,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    run.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
  }
}

@Composable
fun SharedVisitDetailsContent(
  entry: CafeEntry,
  currentModelVersion: String,
  tagging: Boolean,
  onClose: (() -> Unit)? = null,
  closeLabel: String = "Close",
  onRetryClassification: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val latitude = entry.latitude
  val longitude = entry.longitude
  SelectionContainer {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (onClose != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          TextButton(onClick = onClose) { Text(closeLabel) }
          Spacer(Modifier.weight(1f))
          Text(
            pluralStringResource(Res.plurals.photo_count, entry.photoPaths.size, entry.photoPaths.size),
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
      Text(entry.venue, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      if (entry.address.isNotBlank()) {
        Text(entry.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(
        stringResource(Res.string.visit_visited_at, formatTimestamp(entry.visitedAt)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (latitude != null && longitude != null) {
        Text(
          stringResource(Res.string.visit_location, formatCoordinate(latitude), formatCoordinate(longitude)),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      val tags = (entry.autoTags + entry.manualTags).distinctBy(String::lowercase)
      if (tags.isNotEmpty()) {
        Text(
          stringResource(Res.string.visit_tags_heading),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(top = 8.dp),
        )
        Text(tags.joinToString(" · "), color = MaterialTheme.colorScheme.primary)
      }
      HorizontalDivider(Modifier.padding(vertical = 8.dp))
      SharedClassificationSection(
        entry = entry,
        currentModelVersion = currentModelVersion,
        tagging = tagging,
        onRetryClassification = onRetryClassification,
      )
    }
  }
}

@Composable
fun SharedClassificationSection(
  entry: CafeEntry,
  currentModelVersion: String,
  tagging: Boolean,
  onRetryClassification: () -> Unit,
) {
  Text(stringResource(Res.string.classification_heading), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
  Text(
    stringResource(Res.string.classification_current_model, currentModelVersion),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  if (tagging) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
      Text(
        stringResource(Res.string.classification_running),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  Text(
    stringResource(Res.string.classification_run_history, entry.classificationRuns.size),
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier.padding(top = 4.dp),
  )
  if (entry.classificationRuns.isEmpty()) {
    Text(
      stringResource(
        if (entry.taggingModelVersion == null) Res.string.classification_none_yet
        else Res.string.classification_legacy_history,
      ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  } else {
    entry.classificationRuns.forEach { run ->
      ClassificationRunDetails(run)
    }
  }
  OutlinedButton(
    onClick = onRetryClassification,
    enabled = !tagging,
    modifier = Modifier.padding(top = 8.dp),
  ) {
    Text(
      stringResource(
        if (tagging) Res.string.classification_running else Res.string.classification_retry,
      ),
    )
  }
}
