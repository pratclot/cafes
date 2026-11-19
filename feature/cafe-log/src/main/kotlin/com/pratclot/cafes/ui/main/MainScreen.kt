package com.pratclot.cafes.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.UserPosition
import com.pratclot.cafes.core.ui.generated.resources.Res
import com.pratclot.cafes.core.ui.generated.resources.action_add
import com.pratclot.cafes.core.ui.generated.resources.action_add_photo_short
import com.pratclot.cafes.core.ui.generated.resources.action_add_photos
import com.pratclot.cafes.core.ui.generated.resources.action_back
import com.pratclot.cafes.core.ui.generated.resources.action_back_to_camera
import com.pratclot.cafes.core.ui.generated.resources.action_change
import com.pratclot.cafes.core.ui.generated.resources.action_details
import com.pratclot.cafes.core.ui.generated.resources.action_from_disk
import com.pratclot.cafes.core.ui.generated.resources.action_log_short
import com.pratclot.cafes.core.ui.generated.resources.action_take_first_photo
import com.pratclot.cafes.core.ui.generated.resources.action_take_photo
import com.pratclot.cafes.core.ui.generated.resources.action_use_this_cafe
import com.pratclot.cafes.core.ui.generated.resources.action_visit_log_count
import com.pratclot.cafes.core.ui.generated.resources.detail_placeholder_title
import com.pratclot.cafes.core.ui.generated.resources.detail_visit_not_found
import com.pratclot.cafes.core.ui.generated.resources.empty_archive_subtitle
import com.pratclot.cafes.core.ui.generated.resources.empty_archive_title
import com.pratclot.cafes.core.ui.generated.resources.empty_log_subtitle
import com.pratclot.cafes.core.ui.generated.resources.empty_log_title
import com.pratclot.cafes.core.ui.generated.resources.empty_search_subtitle
import com.pratclot.cafes.core.ui.generated.resources.empty_search_title
import com.pratclot.cafes.core.ui.generated.resources.identifying_dishes_on_device
import com.pratclot.cafes.core.ui.generated.resources.location_enter_cafe_name
import com.pratclot.cafes.core.ui.generated.resources.location_looking_nearby
import com.pratclot.cafes.core.ui.generated.resources.location_which_cafe
import com.pratclot.cafes.core.ui.generated.resources.search_label
import com.pratclot.cafes.core.ui.generated.resources.search_placeholder
import com.pratclot.cafes.core.ui.generated.resources.visit_tag_input_label
import com.pratclot.cafes.ui.SharedClassificationSection
import com.pratclot.cafes.ui.SharedVisitEntryCard
import org.jetbrains.compose.resources.stringResource
import com.pratclot.cafes.location.hasLocationPermission
import com.pratclot.cafes.ui.camera.CameraXPreview
import com.pratclot.cafes.ui.camera.capturePhoto
import com.pratclot.cafes.ui.navigation.CafeLogNavigation
import java.io.File
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CafeLogScreen(modifier: Modifier = Modifier) {
  CafeLogNavigation(modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CameraCaptureScreen(
  viewModel: CafeLogViewModel,
  onOpenVisitLog: () -> Unit,
  onOpenDetails: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val coroutineScope = rememberCoroutineScope()

  var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
  var cameraPermissionGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
    )
  }
  var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
  var cafeSheetOpen by rememberSaveable { mutableStateOf(false) }
  var openSheetAfterPermission by rememberSaveable { mutableStateOf(false) }
  var manualCafeName by rememberSaveable { mutableStateOf("") }
  var photoPickerEntryId by rememberSaveable { mutableStateOf<String?>(null) }

  val imageCapture = remember {
    ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .build()
  }
  val captureExecutor = remember {
    java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "cafe-camera-capture") }
  }
  androidx.compose.runtime.DisposableEffect(captureExecutor) {
    onDispose { captureExecutor.shutdown() }
  }

  val fallbackCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
    if (bitmap != null) {
      viewModel.saveCapturedBitmap(bitmap, context.filesDir)
    }
  }

  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
    val targetEntryId = photoPickerEntryId
    photoPickerEntryId = null
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        val savedPaths = copyPickedPhotosToFilesDir(context, uris)
        viewModel.savePickedPhotos(targetEntryId, savedPaths)
      }
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    locationPermissionGranted = hasLocationPermission(context)
    cameraPermissionGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    if (openSheetAfterPermission && locationPermissionGranted) {
      cafeSheetOpen = true
    }
    openSheetAfterPermission = false
  }

  LaunchedEffect(Unit) {
    val needed = buildList {
      if (!hasLocationPermission(context)) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
      }
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        add(Manifest.permission.CAMERA)
      }
    }
    if (needed.isNotEmpty()) {
      permissionLauncher.launch(needed.toTypedArray())
    }
  }

  LaunchedEffect(locationPermissionGranted) {
    viewModel.startLocationUpdates(locationPermissionGranted)
  }

  val requestCapture: () -> Unit = {
    if (cameraPermissionGranted) {
      capturePhoto(
        imageCapture = imageCapture,
        executor = captureExecutor,
        lensFacing = lensFacing,
        onPhoto = { bitmap -> viewModel.saveCapturedBitmap(bitmap, context.filesDir) },
        onError = { fallbackCamera.launch(null) },
      )
    } else {
      fallbackCamera.launch(null)
    }
  }

  val openCafeSelector: () -> Unit = {
    if (locationPermissionGranted) {
      cafeSheetOpen = true
    } else {
      openSheetAfterPermission = true
      permissionLauncher.launch(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
      )
    }
  }

  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Cafes",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        OutlinedButton(
          onClick = onOpenVisitLog,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
          Text(stringResource(Res.string.action_visit_log_count, state.entries.size), style = MaterialTheme.typography.labelLarge)
        }
      }

      CurrentLocationBar(
        position = state.currentPosition,
        address = state.currentAddress,
        selectedCafe = state.selectedCafe,
        hasPermission = locationPermissionGranted,
        locationUnavailable = state.locationUnavailable,
        refreshing = state.refreshingLocation,
        refreshMessage = state.refreshMessage,
        onRefresh = {
          if (locationPermissionGranted) viewModel.refreshLocation()
          else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
          )
        },
        onClick = openCafeSelector,
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .clip(RoundedCornerShape(18.dp))
          .background(Color(0xFF14110F)),
        contentAlignment = Alignment.Center,
      ) {
        CameraXPreview(
          hasPermission = cameraPermissionGranted,
          lensFacing = lensFacing,
          imageCapture = imageCapture,
          onRequestPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) },
        )
      }

      val recentSaved = remember(state.entries, state.lastCapturedEntryId) {
        state.entries.firstOrNull { it.id == state.lastCapturedEntryId }
      }
      if (recentSaved != null) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.secondaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "✓ Saved to ${recentSaved.venue} (${recentSaved.photoPaths.size})",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            TextButton(
              onClick = {
                photoPickerEntryId = recentSaved.id
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
              Text(stringResource(Res.string.action_add_photo_short), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
              onClick = { onOpenDetails(recentSaved.id) },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
              Text(stringResource(Res.string.action_details), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
              onClick = onOpenVisitLog,
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
              Text(stringResource(Res.string.action_log_short), style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = {
            photoPickerEntryId = null
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          },
          modifier = Modifier.height(50.dp),
        ) {
          Text(stringResource(Res.string.action_from_disk))
        }
        Button(
          onClick = requestCapture,
          modifier = Modifier
            .weight(1f)
            .height(54.dp)
            .semantics(mergeDescendants = true) {},
          shape = RoundedCornerShape(27.dp),
        ) {
          Text(stringResource(Res.string.action_take_photo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
          onClick = {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
              CameraSelector.LENS_FACING_FRONT
            } else {
              CameraSelector.LENS_FACING_BACK
            }
          },
          modifier = Modifier.height(50.dp),
          contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
          Text("↺")
        }
      }

      if (state.showLocationHint) {
        Text(
          text = "Saved your photo. Turn on location to add where you are.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  if (cafeSheetOpen) {
    ModalBottomSheet(onDismissRequest = { cafeSheetOpen = false }) {
      NearbyCafeSheet(
        position = state.currentPosition,
        locationUnavailable = state.locationUnavailable,
        candidates = state.nearbyCafes,
        searching = state.searchingCafes,
        error = state.cafeSearchError,
        manualName = manualCafeName,
        onManualNameChange = { manualCafeName = it },
        onChoose = { cafe ->
          viewModel.selectCafe(cafe)
          cafeSheetOpen = false
        },
        onChooseManual = {
          viewModel.selectManualCafe(manualCafeName)
          cafeSheetOpen = false
        },
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun VisitLogScreen(
  viewModel: CafeLogViewModel,
  selectedEntryId: String?,
  onBackToCamera: () -> Unit,
  onSelectEntry: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val coroutineScope = rememberCoroutineScope()

  var showingArchive by rememberSaveable { mutableStateOf(false) }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var cafeSheetOpen by rememberSaveable { mutableStateOf(false) }
  var manualCafeName by rememberSaveable { mutableStateOf("") }
  var manualTagDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var photoPickerEntryId by rememberSaveable { mutableStateOf<String?>(null) }
  var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }

  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
    val targetId = photoPickerEntryId
    photoPickerEntryId = null
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        val savedPaths = copyPickedPhotosToFilesDir(context, uris)
        viewModel.savePickedPhotos(targetId, savedPaths)
      }
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    locationPermissionGranted = hasLocationPermission(context)
    if (locationPermissionGranted) {
      viewModel.startLocationUpdates(true)
      cafeSheetOpen = true
    }
  }

  val visibleEntries = if (showingArchive) state.archivedEntries else state.entries
  val filteredEntries = remember(visibleEntries, searchQuery) {
    val query = searchQuery.trim()
    if (query.isBlank()) visibleEntries
    else visibleEntries.filter { entry ->
      listOf(entry.venue, entry.address).any { it.contains(query, ignoreCase = true) } ||
        (entry.manualTags + entry.autoTags).any { it.contains(query, ignoreCase = true) }
    }
  }

  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding)
        .imePadding()
        .padding(horizontal = 18.dp),
    ) {
      Spacer(Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = onBackToCamera,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Text(stringResource(Res.string.action_back_to_camera), style = MaterialTheme.typography.labelLarge)
          }
          Text(
            text = if (showingArchive) "Archived visits" else "Visit log",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        }
        TextButton(onClick = { showingArchive = !showingArchive }) {
          Text(if (showingArchive) "Log (${state.entries.size})" else "Archive (${state.archivedEntries.size})")
        }
      }

      Spacer(Modifier.height(8.dp))
      CurrentLocationBar(
        position = state.currentPosition,
        address = state.currentAddress,
        selectedCafe = state.selectedCafe,
        hasPermission = locationPermissionGranted,
        locationUnavailable = state.locationUnavailable,
        refreshing = state.refreshingLocation,
        refreshMessage = state.refreshMessage,
        onRefresh = {
          if (locationPermissionGranted) viewModel.refreshLocation()
          else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
          )
        },
        onClick = {
          if (locationPermissionGranted) cafeSheetOpen = true
          else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
          )
        },
      )

      Spacer(Modifier.height(10.dp))
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.search_label)) },
        placeholder = { Text(stringResource(Res.string.search_placeholder)) },
        singleLine = true,
      )
      Spacer(Modifier.height(10.dp))

      if (filteredEntries.isEmpty()) {
        if (showingArchive) {
          Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text(stringResource(Res.string.empty_archive_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(Res.string.empty_archive_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else if (visibleEntries.isEmpty() && searchQuery.isBlank()) {
          EmptyLog(Modifier.weight(1f), onCapture = onBackToCamera)
        } else {
          Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text(stringResource(Res.string.empty_search_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.empty_search_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 300.dp),
          modifier = Modifier.fillMaxWidth().weight(1f),
          contentPadding = PaddingValues(bottom = 32.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          gridItems(
            items = filteredEntries,
            key = { it.id },
            contentType = { "visit_card" },
          ) { entry ->
            val photos = remember(entry.photoPath, entry.photoPaths) {
              entry.photoPaths.filter { File(it).exists() }.ifEmpty { listOf(entry.photoPath) }
            }
            SharedVisitEntryCard(
              entry = entry,
              archived = showingArchive,
              selected = entry.id == selectedEntryId,
              tagging = entry.id in state.taggingIds,
              taggingLabel = stringResource(Res.string.identifying_dishes_on_device),
              tagDraft = manualTagDrafts[entry.id].orEmpty(),
              photoPathsOverride = photos,
              onOpenDetails = { onSelectEntry(entry.id) },
              onTagDraft = { draft -> manualTagDrafts = manualTagDrafts + (entry.id to draft) },
              onTagClick = { tag -> searchQuery = tag },
              onAddTag = {
                val tag = manualTagDrafts[entry.id].orEmpty().trim()
                if (tag.isNotEmpty()) {
                  viewModel.addManualTag(entry.id, tag)
                  manualTagDrafts = manualTagDrafts - entry.id
                }
              },
              onAddPhotos = {
                photoPickerEntryId = entry.id
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              },
              onArchive = { viewModel.archiveEntry(entry.id) },
              onRestore = { viewModel.restoreEntry(entry.id) },
              photoContent = { path, modifier -> AsyncLocalPhoto(path = path, modifier = modifier) },
            )
          }
        }
      }
    }
  }

  if (cafeSheetOpen) {
    ModalBottomSheet(onDismissRequest = { cafeSheetOpen = false }) {
      NearbyCafeSheet(
        position = state.currentPosition,
        locationUnavailable = state.locationUnavailable,
        candidates = state.nearbyCafes,
        searching = state.searchingCafes,
        error = state.cafeSearchError,
        manualName = manualCafeName,
        onManualNameChange = { manualCafeName = it },
        onChoose = { cafe ->
          viewModel.selectCafe(cafe)
          cafeSheetOpen = false
        },
        onChooseManual = {
          viewModel.selectManualCafe(manualCafeName)
          cafeSheetOpen = false
        },
      )
    }
  }
}

@Composable
private fun EmptyLog(modifier: Modifier, onCapture: () -> Unit) {
  Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Box(
      Modifier.size(176.dp).clip(RoundedCornerShape(42.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Text("☕", style = MaterialTheme.typography.displayLarge)
    }
    Spacer(Modifier.height(24.dp))
    Text(stringResource(Res.string.empty_log_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(Res.string.empty_log_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    OutlinedButton(onClick = onCapture) { Text(stringResource(Res.string.action_take_first_photo)) }
  }
}
