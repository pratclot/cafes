package com.pratclot.cafes.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.UserPosition
import com.pratclot.cafes.core.ui.generated.resources.Res
import com.pratclot.cafes.core.ui.generated.resources.action_change
import com.pratclot.cafes.core.ui.generated.resources.action_use_this_cafe
import com.pratclot.cafes.core.ui.generated.resources.cafe_search_unavailable
import com.pratclot.cafes.core.ui.generated.resources.location_enter_cafe_name
import com.pratclot.cafes.core.ui.generated.resources.location_looking_nearby
import com.pratclot.cafes.core.ui.generated.resources.location_no_fresh
import com.pratclot.cafes.core.ui.generated.resources.location_refresh_cd
import com.pratclot.cafes.core.ui.generated.resources.location_refresh_failed
import com.pratclot.cafes.core.ui.generated.resources.location_which_cafe
import com.pratclot.cafes.data.CafeSearchError
import com.pratclot.cafes.data.RefreshStatusMessage
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RefreshStatusMessage.asText(): String = when (this) {
  RefreshStatusMessage.NO_FRESH_LOCATION -> stringResource(Res.string.location_no_fresh)
  RefreshStatusMessage.REFRESH_FAILED -> stringResource(Res.string.location_refresh_failed)
}

@Composable
internal fun CafeSearchError.asText(): String = when (this) {
  CafeSearchError.UNAVAILABLE -> stringResource(Res.string.cafe_search_unavailable)
}

@Composable
internal fun CurrentLocationBar(
  position: UserPosition?,
  address: String?,
  selectedCafe: CafeCandidate?,
  hasPermission: Boolean,
  locationUnavailable: Boolean,
  refreshing: Boolean,
  refreshMessage: RefreshStatusMessage?,
  onClick: () -> Unit,
  onRefresh: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("📍", style = MaterialTheme.typography.bodyMedium)
      val refreshText = refreshMessage?.asText()
      val displayLine = when {
        refreshText != null -> refreshText
        !hasPermission -> "Location off · tap Change"
        selectedCafe != null -> buildString {
          append(selectedCafe.name)
          val addr = selectedCafe.address ?: address
          if (!addr.isNullOrBlank()) append(" · $addr")
        }
        position == null && locationUnavailable -> "Location unavailable"
        position == null -> "Detecting location…"
        else -> address ?: "Nearby (±${position.accuracyMeters.toInt()}m)"
      }
      Text(
        text = displayLine,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .weight(1f)
          .clickable(onClick = onClick),
      )
      TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
      ) {
        Text(stringResource(Res.string.action_change), style = MaterialTheme.typography.labelMedium)
      }
      val refreshDescription = stringResource(Res.string.location_refresh_cd)
      TextButton(
        onClick = onRefresh,
        enabled = !refreshing,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        modifier = Modifier.semantics { contentDescription = refreshDescription },
      ) {
        Text(if (refreshing) "…" else "↻", style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NearbyCafeSheet(
  position: UserPosition?,
  locationUnavailable: Boolean,
  candidates: List<CafeCandidate>,
  searching: Boolean,
  error: CafeSearchError?,
  manualName: String,
  onManualNameChange: (String) -> Unit,
  onChoose: (CafeCandidate) -> Unit,
  onChooseManual: () -> Unit,
) {
  Column(
    Modifier
      .fillMaxWidth()
      .imePadding()
      .padding(horizontal = 22.dp)
      .padding(bottom = 24.dp),
  ) {
    Text(stringResource(Res.string.location_which_cafe), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(5.dp))
    Text(
      if (position == null) "Nearby lookup needs a location fix." else "Nearby matches are sorted by distance.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(14.dp))
    val errorText = error?.asText()
    if (searching || (position == null && !locationUnavailable)) {
      Row(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(Res.string.location_looking_nearby))
      }
    } else if (candidates.isNotEmpty()) {
      LazyColumn(Modifier.heightIn(max = 260.dp)) {
        items(
          items = candidates.take(12),
          key = { it.id },
          contentType = { "nearby_cafe" },
        ) { cafe ->
          ListItem(
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { onChoose(cafe) },
            headlineContent = { Text(cafe.name, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(cafe.address ?: "OpenStreetMap place") },
            trailingContent = {
              if (position != null) {
                cafe.distanceFrom(position)?.let { Text("${it.toInt()} m", style = MaterialTheme.typography.labelMedium) }
              }
            },
          )
        }
      }
    } else {
      Text(
        errorText ?: "No mapped cafes found nearby. Enter the name instead.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (!errorText.isNullOrBlank() && candidates.isNotEmpty()) {
      Text(errorText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = manualName,
      onValueChange = onManualNameChange,
      modifier = Modifier.fillMaxWidth(),
      label = { Text(stringResource(Res.string.location_enter_cafe_name)) },
      singleLine = true,
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = onChooseManual, enabled = manualName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
      Text(stringResource(Res.string.action_use_this_cafe))
    }
    Spacer(Modifier.height(8.dp))
    Text(
      "Cafe suggestions © OpenStreetMap contributors",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
