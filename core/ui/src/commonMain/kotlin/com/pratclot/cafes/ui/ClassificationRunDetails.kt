package com.pratclot.cafes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

@Composable
fun ClassificationRunDetails(run: ClassificationRun) {
  Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(formatTimestamp(run.occurredAt), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    val summary = when (run.outcome) {
      "ERROR" -> "Processing error"
      "NO_DETECTIONS" -> "Nothing detected"
      else -> "Detected ${run.labels.size} label${if (run.labels.size == 1) "" else "s"}"
    }
    Text(
      "$summary · ${run.photoCount} photo${if (run.photoCount == 1) "" else "s"} · ${run.durationMillis} ms · ${run.modelVersion}",
      style = MaterialTheme.typography.bodySmall,
      color = if (run.outcome == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (run.labels.isNotEmpty()) {
      Text(
        run.labels.joinToString(" · ") { "${it.name} (${(it.confidence * 100).toInt()}%)" },
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
            "${entry.photoPaths.size} photo${if (entry.photoPaths.size == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
      Text(entry.venue, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      if (entry.address.isNotBlank()) {
        Text(entry.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(
        "Visited ${formatTimestamp(entry.visitedAt)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (latitude != null && longitude != null) {
        Text(
          "Location: ${formatCoordinate(latitude)}, ${formatCoordinate(longitude)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      val tags = (entry.autoTags + entry.manualTags).distinctBy(String::lowercase)
      if (tags.isNotEmpty()) {
        Text("Tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
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
  Text("Automatic classification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
  Text(
    "Current model: $currentModelVersion",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  if (tagging) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
      Text(
        "Classification running…",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  Text("Run history (${entry.classificationRuns.size})", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
  if (entry.classificationRuns.isEmpty()) {
    Text(
      if (entry.taggingModelVersion == null) {
        "No classification runs recorded yet."
      } else {
        "This visit was classified before run history was saved."
      },
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
    Text(if (tagging) "Classification running…" else "Run classification again")
  }
}
