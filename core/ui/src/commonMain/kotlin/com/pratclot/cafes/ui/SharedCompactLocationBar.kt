package com.pratclot.cafes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pratclot.cafes.core.model.CafeCandidate

@Composable
fun SharedCompactLocationBar(
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
  val activeLabel = when {
    selectedCafe != null -> "${selectedCafe.name}${selectedCafe.address?.takeIf(String::isNotBlank)?.let { " · $it" } ?: ""}"
    manualCafe.isNotBlank() -> manualCafe.trim()
    else -> locationMessage
  }
  Column(Modifier.fillMaxWidth()) {
    Surface(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggleEditing),
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          "📍 $activeLabel",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = if (selectedCafe != null || manualCafe.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        TextButton(
          onClick = onToggleEditing,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
          Text(if (editing) "Done" else "Change")
        }
        TextButton(
          onClick = onRefresh,
          enabled = !loadingLocation,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
          Text(if (loadingLocation) "…" else "↻")
        }
        if (showAddPhotosAction) {
          Button(
            onClick = onAddPhotos,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Text("+ Add photos")
          }
        }
      }
    }
    if (editing) {
      Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(
          Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            locationMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (cafes.isNotEmpty()) {
            Text("Nearby cafes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            cafes.take(6).forEach { cafe ->
              TextButton(
                onClick = { onSelectCafe(cafe) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
              ) {
                Text("${if (selectedCafe?.id == cafe.id) "✓ " else ""}${cafe.name}${cafe.address?.let { " · $it" } ?: ""}")
              }
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = manualCafe,
              onValueChange = onManualCafeChange,
              label = { Text("Custom cafe name") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            if (manualCafe.isNotBlank()) {
              OutlinedButton(onClick = onApplyManualCafe) { Text("Use") }
            }
          }
        }
      }
    }
  }
}
