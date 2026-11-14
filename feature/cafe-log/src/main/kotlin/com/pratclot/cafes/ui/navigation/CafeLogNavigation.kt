package com.pratclot.cafes.ui.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.pratclot.cafes.ui.main.CafeLogViewModel
import com.pratclot.cafes.ui.main.CameraCaptureScreen
import com.pratclot.cafes.ui.main.VisitDetailPlaceholder
import com.pratclot.cafes.ui.main.VisitDetailScreen
import com.pratclot.cafes.ui.main.VisitLogScreen
import kotlinx.serialization.Serializable

@Serializable
data object CameraRoute : NavKey

@Serializable
data object VisitLogRoute : NavKey

@Serializable
data class VisitDetailRoute(val entryId: String) : NavKey

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun CafeLogNavigation(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val viewModel: CafeLogViewModel = viewModel(factory = CafeLogViewModel.factory(context))
  val backStack = rememberNavBackStack(CameraRoute)

  val windowAdaptiveInfo = currentWindowAdaptiveInfo()
  val directive = remember(windowAdaptiveInfo) {
    calculatePaneScaffoldDirective(windowAdaptiveInfo)
      .copy(horizontalPartitionSpacerSize = 0.dp)
  }
  val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
  val isMultiPane = directive.maxHorizontalPartitions > 1

  fun openVisitDetail(entryId: String) {
    val last = backStack.lastOrNull()
    if (last is VisitDetailRoute) {
      backStack[backStack.lastIndex] = VisitDetailRoute(entryId)
    } else {
      backStack.add(VisitDetailRoute(entryId))
    }
  }

  val selectedDetailId = (backStack.lastOrNull() as? VisitDetailRoute)?.entryId

  NavDisplay(
    modifier = modifier,
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    sceneStrategy = listDetailStrategy,
    entryProvider = entryProvider {
      entry<CameraRoute> {
        CameraCaptureScreen(
          viewModel = viewModel,
          onOpenVisitLog = dropUnlessResumed {
            if (backStack.lastOrNull() != VisitLogRoute) {
              backStack.add(VisitLogRoute)
            }
          },
          onOpenDetails = { entryId ->
            if (VisitLogRoute !in backStack) {
              backStack.add(VisitLogRoute)
            }
            openVisitDetail(entryId)
          },
        )
      }
      entry<VisitLogRoute>(
        metadata = ListDetailSceneStrategy.listPane(
          detailPlaceholder = { VisitDetailPlaceholder() },
        ),
      ) {
        VisitLogScreen(
          viewModel = viewModel,
          selectedEntryId = selectedDetailId,
          onBackToCamera = dropUnlessResumed {
            while (backStack.size > 1 && backStack.lastOrNull() != CameraRoute) {
              backStack.removeLastOrNull()
            }
          },
          onSelectEntry = { entryId -> openVisitDetail(entryId) },
        )
      }
      entry<VisitDetailRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
      ) { route ->
        VisitDetailScreen(
          entryId = route.entryId,
          viewModel = viewModel,
          showBackButton = !isMultiPane,
          onBack = dropUnlessResumed { backStack.removeLastOrNull() },
        )
      }
    },
  )
}
