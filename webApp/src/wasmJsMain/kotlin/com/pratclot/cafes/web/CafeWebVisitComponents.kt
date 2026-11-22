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
import com.pratclot.cafes.core.ui.generated.resources.Res
import com.pratclot.cafes.core.ui.generated.resources.identifying_dishes_in_browser
import com.pratclot.cafes.ui.CompactLocationBarAction
import com.pratclot.cafes.ui.CompactLocationBarState
import com.pratclot.cafes.ui.SharedCompactLocationBar
import com.pratclot.cafes.ui.SharedVisitDetailsContent
import com.pratclot.cafes.ui.SharedVisitEntryCard
import com.pratclot.cafes.ui.VisitDetailsAction
import com.pratclot.cafes.ui.VisitDetailsState
import com.pratclot.cafes.ui.VisitEntryCardAction
import com.pratclot.cafes.ui.VisitEntryCardState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CompactLocationBar(
  state: CompactLocationBarState,
  onAction: (CompactLocationBarAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  SharedCompactLocationBar(
    state = state,
    onAction = onAction,
    modifier = modifier,
  )
}

@Composable
internal fun VisitCard(
  state: VisitEntryCardState,
  onAction: (VisitEntryCardAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  SharedVisitEntryCard(
    state = state.copy(
      taggingLabel = state.taggingLabel ?: stringResource(Res.string.identifying_dishes_in_browser),
    ),
    onAction = onAction,
    modifier = modifier,
    photoContent = { photo, photoModifier -> BrowserPhoto(photo, photoModifier) },
  )
}

@Composable
internal fun VisitDetailsScreen(
  state: VisitDetailsState,
  onAction: (VisitDetailsAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    VisitDetailsContent(
      state = state,
      onAction = onAction,
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    )
  }
}

@Composable
internal fun VisitDetailsContent(
  state: VisitDetailsState,
  onAction: (VisitDetailsAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  SharedVisitDetailsContent(
    state = state,
    onAction = onAction,
    modifier = modifier,
  )
}

internal fun Double.format(digits: Int): String = toString().let { value ->
  val dot = value.indexOf('.')
  if (dot < 0) value else value.take(dot + digits + 1)
}
