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
import androidx.compose.runtime.Immutable
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
import com.pratclot.cafes.core.ui.generated.resources.location_detecting
import com.pratclot.cafes.core.ui.generated.resources.location_distance_meters
import com.pratclot.cafes.core.ui.generated.resources.location_enter_cafe_name
import com.pratclot.cafes.core.ui.generated.resources.location_looking_nearby
import com.pratclot.cafes.core.ui.generated.resources.location_nearby_accuracy
import com.pratclot.cafes.core.ui.generated.resources.location_nearby_needs_fix
import com.pratclot.cafes.core.ui.generated.resources.location_nearby_sorted
import com.pratclot.cafes.core.ui.generated.resources.location_no_fresh
import com.pratclot.cafes.core.ui.generated.resources.location_no_mapped_cafes
import com.pratclot.cafes.core.ui.generated.resources.location_off_tap_change
import com.pratclot.cafes.core.ui.generated.resources.location_osm_attribution
import com.pratclot.cafes.core.ui.generated.resources.location_osm_place
import com.pratclot.cafes.core.ui.generated.resources.location_refresh_cd
import com.pratclot.cafes.core.ui.generated.resources.location_refresh_failed
import com.pratclot.cafes.core.ui.generated.resources.location_unavailable
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

@Immutable
internal data class CurrentLocationBarState(
  val position: UserPosition?,
  val address: String?,
  val selectedCafe: CafeCandidate?,
  val hasPermission: Boolean,
  val locationUnavailable: Boolean,
  val refreshing: Boolean,
  val refreshMessage: RefreshStatusMessage?,
)

internal sealed interface CurrentLocationBarAction {
  data object OpenPicker : CurrentLocationBarAction
  data object Refresh : CurrentLocationBarAction
}

@Composable
internal fun CurrentLocationBar(
  state: CurrentLocationBarState,
  onAction: (CurrentLocationBarAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
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
      val refreshText = state.refreshMessage?.asText()
      val selectedCafe = state.selectedCafe
      val position = state.position
      val displayLine = when {
        refreshText != null -> refreshText
        !state.hasPermission -> stringResource(Res.string.location_off_tap_change)
        selectedCafe != null -> buildString {
          append(selectedCafe.name)
          val addr = selectedCafe.address ?: state.address
          if (!addr.isNullOrBlank()) append(" · $addr")
        }
        position == null && state.locationUnavailable -> stringResource(Res.string.location_unavailable)
        position == null -> stringResource(Res.string.location_detecting)
        else -> state.address ?: stringResource(Res.string.location_nearby_accuracy, position.accuracyMeters.toInt())
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
          .clickable { onAction(CurrentLocationBarAction.OpenPicker) },
      )
      TextButton(
        onClick = { onAction(CurrentLocationBarAction.OpenPicker) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
      ) {
        Text(stringResource(Res.string.action_change), style = MaterialTheme.typography.labelMedium)
      }
      val refreshDescription = stringResource(Res.string.location_refresh_cd)
      TextButton(
        onClick = { onAction(CurrentLocationBarAction.Refresh) },
        enabled = !state.refreshing,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        modifier = Modifier.semantics { contentDescription = refreshDescription },
      ) {
        Text(if (state.refreshing) "…" else "↻", style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@Immutable
internal data class NearbyCafeSheetState(
  val position: UserPosition?,
  val locationUnavailable: Boolean,
  val candidates: List<CafeCandidate>,
  val searching: Boolean,
  val error: CafeSearchError?,
  val manualName: String,
)

internal sealed interface NearbyCafeSheetAction {
  data class ChangeManualName(val name: String) : NearbyCafeSheetAction
  data class ChooseCafe(val cafe: CafeCandidate) : NearbyCafeSheetAction
  data object ChooseManual : NearbyCafeSheetAction
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NearbyCafeSheet(
  state: NearbyCafeSheetState,
  onAction: (NearbyCafeSheetAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  val position = state.position
  Column(
    modifier
      .fillMaxWidth()
      .imePadding()
      .padding(horizontal = 22.dp)
      .padding(bottom = 24.dp),
  ) {
    Text(stringResource(Res.string.location_which_cafe), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(5.dp))
    Text(
      stringResource(
        if (position == null) Res.string.location_nearby_needs_fix else Res.string.location_nearby_sorted,
      ),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(14.dp))
    val errorText = state.error?.asText()
    if (state.searching || (position == null && !state.locationUnavailable)) {
      Row(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(Res.string.location_looking_nearby))
      }
    } else if (state.candidates.isNotEmpty()) {
      LazyColumn(Modifier.heightIn(max = 260.dp)) {
        items(
          items = state.candidates.take(12),
          key = { it.id },
          contentType = { "nearby_cafe" },
        ) { cafe ->
          ListItem(
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { onAction(NearbyCafeSheetAction.ChooseCafe(cafe)) },
            headlineContent = { Text(cafe.name, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(cafe.address ?: stringResource(Res.string.location_osm_place)) },
            trailingContent = {
              if (position != null) {
                cafe.distanceFrom(position)?.let {
                  Text(stringResource(Res.string.location_distance_meters, it.toInt()), style = MaterialTheme.typography.labelMedium)
                }
              }
            },
          )
        }
      }
    } else {
      Text(
        errorText ?: stringResource(Res.string.location_no_mapped_cafes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (!errorText.isNullOrBlank() && state.candidates.isNotEmpty()) {
      Text(errorText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = state.manualName,
      onValueChange = { onAction(NearbyCafeSheetAction.ChangeManualName(it)) },
      modifier = Modifier.fillMaxWidth(),
      label = { Text(stringResource(Res.string.location_enter_cafe_name)) },
      singleLine = true,
    )
    Spacer(Modifier.height(8.dp))
    Button(
      onClick = { onAction(NearbyCafeSheetAction.ChooseManual) },
      enabled = state.manualName.isNotBlank(),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(stringResource(Res.string.action_use_this_cafe))
    }
    Spacer(Modifier.height(8.dp))
    Text(
      stringResource(Res.string.location_osm_attribution),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
