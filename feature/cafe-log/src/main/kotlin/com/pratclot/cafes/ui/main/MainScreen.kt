package com.pratclot.cafes.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pratclot.cafes.data.LocalCafeLogRepository
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.ClassificationRun
import com.pratclot.cafes.core.model.UserPosition
import com.pratclot.cafes.location.CafeLocationFacades
import com.pratclot.cafes.location.hasLocationPermission
import com.pratclot.cafes.recognition.AndroidFoodTaggerFacade
import com.pratclot.cafes.ui.theme.ComPratclotCafesTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CafeLogScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val repository = remember { LocalCafeLogRepository(context) }
  val facades = remember(context) { CafeLocationFacades.create(context) }
  val foodTagger = remember(context) { AndroidFoodTaggerFacade(context) }
  val coroutineScope = rememberCoroutineScope()
  var entries by remember { mutableStateOf(repository.load()) }
  var archivedEntries by remember { mutableStateOf(repository.loadArchived()) }
  var showingLogScreen by remember { mutableStateOf(false) }
  var showingArchive by remember { mutableStateOf(false) }
  var photoPickerEntryId by remember { mutableStateOf<String?>(null) }
  var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }
  var lastCapturedEntryId by remember { mutableStateOf<String?>(null) }
  var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
  var cameraPermissionGranted by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
  }
  var lensFacing by remember { mutableIntStateOf(CameraCharacteristics.LENS_FACING_BACK) }
  var activeTextureView by remember { mutableStateOf<TextureView?>(null) }
  var openSheetAfterPermission by remember { mutableStateOf(false) }
  var currentPosition by remember { mutableStateOf<UserPosition?>(null) }
  var locationUnavailable by remember { mutableStateOf(false) }
  var refreshingLocation by remember { mutableStateOf(false) }
  var refreshMessage by remember { mutableStateOf<String?>(null) }
  var currentAddress by remember { mutableStateOf<String?>(null) }
  var selectedCafe by remember { mutableStateOf<CafeCandidate?>(null) }
  var showLocationHint by remember { mutableStateOf(false) }
  var cafeSheetOpen by remember { mutableStateOf(false) }
  var nearbyCafes by remember { mutableStateOf<List<CafeCandidate>>(emptyList()) }
  var cafeSearchAnchor by remember { mutableStateOf<UserPosition?>(null) }
  var hasSearchedNearby by remember { mutableStateOf(false) }
  var searchingCafes by remember { mutableStateOf(false) }
  var cafeSearchError by remember { mutableStateOf<String?>(null) }
  var manualCafeName by remember { mutableStateOf("") }
  var searchQuery by remember { mutableStateOf("") }
  var taggingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var expandedEntryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var manualTagDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

  LaunchedEffect(Unit) {
    snapshotFlow { entries + archivedEntries }.collect { allEntries ->
      val pending = allEntries.filter { it.photoPaths.isNotEmpty() && it.taggingModelVersion != AndroidFoodTaggerFacade.MODEL_VERSION && it.id !in taggingIds }
      pending.forEach { entry ->
        taggingIds = taggingIds + entry.id
        val startedAt = System.currentTimeMillis()
        var labels = emptyList<com.pratclot.cafes.core.model.FoodLabel>()
        var errorMessage: String? = null
        try {
          labels = foodTagger.classify(entry.photoPaths)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          errorMessage = error.message ?: error::class.simpleName ?: "Unknown classification error"
        }
        val completedAt = System.currentTimeMillis()
        val run = ClassificationRun(
          occurredAt = completedAt,
          modelVersion = AndroidFoodTaggerFacade.MODEL_VERSION,
          photoCount = entry.photoPaths.size,
          durationMillis = completedAt - startedAt,
          outcome = when { errorMessage != null -> "ERROR"; labels.isEmpty() -> "NO_DETECTIONS"; else -> "SUCCESS" },
          labels = labels,
          errorMessage = errorMessage,
        )
        val latestEntry = (entries + archivedEntries).firstOrNull { it.id == entry.id } ?: entry
        val photosUnchanged = latestEntry.photoPaths == entry.photoPaths
        val updated = repository.update(latestEntry.copy(
          autoTags = if (photosUnchanged && errorMessage == null) labels.filter { it.confidence >= 0.22f }.map { it.name } else latestEntry.autoTags,
          taggingModelVersion = AndroidFoodTaggerFacade.MODEL_VERSION.takeIf { photosUnchanged },
          classificationRuns = (listOf(run) + latestEntry.classificationRuns).take(50),
        ))
        entries = updated.first
        archivedEntries = updated.second
        taggingIds = taggingIds - entry.id
      }
    }
  }

  val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
    if (bitmap != null) pendingPhoto = bitmap
  }
  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
    val entryId = photoPickerEntryId
    photoPickerEntryId = null
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        val targetId = entryId ?: "cafe-${System.currentTimeMillis()}"
        val savedPaths = withContext(Dispatchers.IO) {
          uris.mapIndexedNotNull { index, uri ->
            runCatching {
              val file = File(context.filesDir, "cafe-$targetId-${System.currentTimeMillis()}-$index.photo")
              context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
                ?: return@mapIndexedNotNull null
              file.absolutePath
            }.getOrNull()
          }
        }
        if (savedPaths.isNotEmpty()) {
          if (entryId != null) {
            val updated = withContext(Dispatchers.IO) { repository.appendPhotos(entryId, savedPaths) }
            entries = updated.first
            archivedEntries = updated.second
          } else {
            val position = currentPosition
            val newEntry = CafeEntry(
              id = targetId,
              photoPath = savedPaths.first(),
              venue = selectedCafe?.name ?: "Cafe visit",
              address = selectedCafe?.address ?: currentAddress ?: if (position == null) "Location not available" else "Address unavailable",
              visitedAt = System.currentTimeMillis(),
              latitude = position?.latitude,
              longitude = position?.longitude,
              photoPaths = savedPaths,
            )
            entries = withContext(Dispatchers.IO) { repository.add(newEntry) }
            lastCapturedEntryId = newEntry.id
          }
        }
      }
    }
  }
  val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    locationPermissionGranted = hasLocationPermission(context)
    cameraPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    if (openSheetAfterPermission && locationPermissionGranted) cafeSheetOpen = true
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
      locationPermission.launch(needed.toTypedArray())
    }
  }
  val requestCapture: () -> Unit = {
    val bitmap = activeTextureView?.bitmap
    if (bitmap != null) {
      pendingPhoto = bitmap
    } else {
      camera.launch(null)
    }
  }
  val requestDiskPhotosForNewVisit: () -> Unit = {
    photoPickerEntryId = null
    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
  }
  val refreshLocation: () -> Unit = {
    if (!hasLocationPermission(context)) {
      locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    } else if (!refreshingLocation) {
      coroutineScope.launch {
        refreshingLocation = true
        refreshMessage = null
        try {
          val latest = withTimeout(20_000L) { facades.location.refresh() }
          if (latest == null) {
            refreshMessage = "No fresh location yet. Check that device location is on."
          } else {
            currentPosition = latest
            currentAddress = facades.address.describe(latest)
            locationUnavailable = false
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Exception) {
          refreshMessage = "Couldn’t refresh location. Try again in a moment."
        } finally {
          refreshingLocation = false
        }
      }
    }
  }

  LaunchedEffect(locationPermissionGranted, currentPosition == null) {
    if (!locationPermissionGranted || currentPosition != null) {
      locationUnavailable = false
      return@LaunchedEffect
    }
    delay(20_000L)
    if (currentPosition == null) locationUnavailable = true
  }

  LaunchedEffect(lifecycleOwner, locationPermissionGranted) {
    if (!locationPermissionGranted) return@LaunchedEffect
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      launch {
        try {
          val initialPosition = facades.location.refresh()
          if (initialPosition != null) {
            currentPosition = initialPosition
            currentAddress = facades.address.describe(initialPosition)
            locationUnavailable = false
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Exception) {
          // The live updates can still provide a position if a one-shot fix is unavailable.
        }
      }
      var lastAddressPosition: UserPosition? = null
      var lastAddressUpdate = 0L
      facades.location.observeWhileInUse().collect { position ->
        refreshMessage = null
        currentPosition = position
        val addressMoved = lastAddressPosition?.distanceTo(position.latitude, position.longitude)?.let { it > 100f } ?: true
        if (addressMoved || System.currentTimeMillis() - lastAddressUpdate > 120_000L) {
          lastAddressPosition = position
          lastAddressUpdate = System.currentTimeMillis()
          currentAddress = facades.address.describe(position)
        }
      }
    }
  }

  LaunchedEffect(currentPosition) {
    val position = currentPosition ?: return@LaunchedEffect
    val previousSearchPosition = cafeSearchAnchor
    val moved = previousSearchPosition?.distanceTo(position.latitude, position.longitude)
    if (previousSearchPosition == null || moved != null && moved > 100f) {
      if (previousSearchPosition != null) selectedCafe = null
      nearbyCafes = emptyList()
      cafeSearchError = null
      hasSearchedNearby = false
      cafeSearchAnchor = position
    }
  }

  LaunchedEffect(cafeSearchAnchor) {
    val position = cafeSearchAnchor ?: return@LaunchedEffect
    searchingCafes = true
    cafeSearchError = null
    hasSearchedNearby = false
    try {
      val matches = facades.cafes.findNearby(position)
      if (cafeSearchAnchor == position) {
        nearbyCafes = matches
        hasSearchedNearby = true
        if (selectedCafe == null) selectedCafe = chooseUnambiguousCafe(matches, position)
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      if (cafeSearchAnchor == position) {
        cafeSearchError = "Cafe search is unavailable right now. You can still enter the cafe name."
        nearbyCafes = emptyList()
        hasSearchedNearby = true
      }
    } finally {
      if (cafeSearchAnchor == position) searchingCafes = false
    }
  }

  LaunchedEffect(pendingPhoto) {
    val bitmap = pendingPhoto ?: return@LaunchedEffect
    val file = File(context.filesDir, "cafe-${System.currentTimeMillis()}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    val position = currentPosition
    val entry = CafeEntry(
      id = file.nameWithoutExtension,
      photoPath = file.absolutePath,
      venue = selectedCafe?.name ?: "Cafe visit",
      address = selectedCafe?.address ?: currentAddress ?: if (position == null) "Location not available" else "Address unavailable",
      visitedAt = System.currentTimeMillis(),
      latitude = position?.latitude,
      longitude = position?.longitude,
    )
    entries = repository.add(entry)
    lastCapturedEntryId = entry.id
    showLocationHint = position == null
    pendingPhoto = null
  }

  val openCafeSelector = {
    if (locationPermissionGranted) cafeSheetOpen = true
    else {
      openSheetAfterPermission = true
      locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
  }

  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    if (!showingLogScreen) {
      Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            "Cafes",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          OutlinedButton(
            onClick = { showingLogScreen = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Text("Visit log (${entries.size}) →", style = MaterialTheme.typography.labelLarge)
          }
        }

        CurrentLocationCard(
          position = currentPosition,
          address = currentAddress,
          selectedCafe = selectedCafe,
          hasPermission = locationPermissionGranted,
          locationUnavailable = locationUnavailable,
          refreshing = refreshingLocation,
          refreshMessage = refreshMessage,
          onRefresh = refreshLocation,
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
          AndroidCameraPreview(
            hasPermission = cameraPermissionGranted,
            lensFacing = lensFacing,
            onTextureViewReady = { activeTextureView = it },
            onRequestPermission = { locationPermission.launch(arrayOf(Manifest.permission.CAMERA)) },
          )
        }

        val recentSaved = entries.firstOrNull { it.id == lastCapturedEntryId }
        if (recentSaved != null) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "✓ Saved to ${recentSaved.venue} (${recentSaved.photoPaths.size} photo${if (recentSaved.photoPaths.size == 1) "" else "s"})",
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
                Text("+ Photo", style = MaterialTheme.typography.labelMedium)
              }
              TextButton(
                onClick = { showingLogScreen = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
              ) {
                Text("Log →", style = MaterialTheme.typography.labelMedium)
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
            onClick = requestDiskPhotosForNewVisit,
            modifier = Modifier.height(50.dp),
          ) {
            Text("From disk")
          }
          Button(
            onClick = requestCapture,
            modifier = Modifier.weight(1f).height(54.dp),
            shape = RoundedCornerShape(27.dp),
          ) {
            Text("● Take photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = {
              lensFacing = if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                CameraCharacteristics.LENS_FACING_FRONT
              } else {
                CameraCharacteristics.LENS_FACING_BACK
              }
            },
            modifier = Modifier.height(50.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
          ) {
            Text("↺")
          }
        }

        if (showLocationHint) {
          Text(
            "Saved your photo. Turn on location to add where you are.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
              onClick = { showingLogScreen = false },
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
              Text("← Camera", style = MaterialTheme.typography.labelLarge)
            }
            Text(
              if (showingArchive) "Archived visits" else "Visit log",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
          }
          TextButton(onClick = { showingArchive = !showingArchive }) {
            Text(if (showingArchive) "Log (${entries.size})" else "Archive (${archivedEntries.size})")
          }
        }
        Spacer(Modifier.height(8.dp))
        CurrentLocationCard(
          position = currentPosition,
          address = currentAddress,
          selectedCafe = selectedCafe,
          hasPermission = locationPermissionGranted,
          locationUnavailable = locationUnavailable,
          refreshing = refreshingLocation,
          refreshMessage = refreshMessage,
          onRefresh = refreshLocation,
          onClick = openCafeSelector,
        )
        Spacer(Modifier.height(10.dp))
        val visibleEntries = if (showingArchive) archivedEntries else entries
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Search cafes and dishes") },
          placeholder = { Text("Try “Galette”") },
          singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        val query = searchQuery.trim().lowercase()
        val filteredEntries = visibleEntries.filter { entry ->
          query.isBlank() || listOf(entry.venue, entry.address).any { it.contains(query, ignoreCase = true) } || (entry.manualTags + entry.autoTags).any { it.contains(query, ignoreCase = true) }
        }
        if (filteredEntries.isEmpty()) {
          if (showingArchive) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
              Text("No archived visits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
              Spacer(Modifier.height(6.dp))
              Text("Visits you archive will be kept here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          } else if (visibleEntries.isEmpty() && query.isBlank()) EmptyLog(Modifier.weight(1f), onCapture = { showingLogScreen = false })
          else Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("No matching visits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Search by cafe name or a dish in your photos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(filteredEntries, key = { it.id }) { entry ->
              CafeEntryCard(entry, archived = showingArchive, tagging = entry.id in taggingIds, expanded = entry.id in expandedEntryIds, tagDraft = manualTagDrafts[entry.id].orEmpty(), onToggleDetails = { expandedEntryIds = if (entry.id in expandedEntryIds) expandedEntryIds - entry.id else expandedEntryIds + entry.id }, onRetryClassification = {
                val updated = repository.update(entry.copy(taggingModelVersion = null))
                entries = updated.first
                archivedEntries = updated.second
              }, onTagDraft = { draft -> manualTagDrafts = manualTagDrafts + (entry.id to draft) }, onTagClick = { tag -> searchQuery = tag }, onAddTag = {
                val tag = manualTagDrafts[entry.id].orEmpty().trim()
                if (tag.isNotEmpty()) {
                  val updated = repository.update(entry.copy(manualTags = (entry.manualTags + tag).distinctBy(String::lowercase)))
                  entries = updated.first
                  archivedEntries = updated.second
                  manualTagDrafts = manualTagDrafts - entry.id
                }
              }, onAddPhotos = { target ->
                photoPickerEntryId = target.id
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              }, onAction = {
                if (showingArchive) {
                  val updated = repository.restore(entry)
                  entries = updated.first
                  archivedEntries = updated.second
                } else {
                  val updated = repository.archive(entry)
                  entries = updated.first
                  archivedEntries = updated.second
                }
              })
            }
          }
        }
      }
    }
  }

  if (cafeSheetOpen) {
    ModalBottomSheet(onDismissRequest = { cafeSheetOpen = false }) {
      NearbyCafeSheet(
        position = currentPosition,
        locationUnavailable = locationUnavailable,
        candidates = nearbyCafes,
        searching = searchingCafes,
        error = cafeSearchError,
        manualName = manualCafeName,
        onManualNameChange = { manualCafeName = it },
        onChoose = { cafe -> selectedCafe = cafe; cafeSheetOpen = false },
        onChooseManual = {
          val name = manualCafeName.trim()
          if (name.isNotEmpty()) {
            selectedCafe = CafeCandidate("manual:${System.currentTimeMillis()}", name, currentAddress, currentPosition?.latitude, currentPosition?.longitude)
            cafeSheetOpen = false
          }
        },
      )
    }
  }
}

private fun chooseUnambiguousCafe(candidates: List<CafeCandidate>, position: UserPosition): CafeCandidate? {
  val accuracy = position.accuracyMeters
  if (!accuracy.isFinite() || accuracy > 35f) return null
  val ranked = candidates.mapNotNull { cafe ->
    cafe.distanceFrom(position)?.let { distance -> cafe to distance }
  }.sortedBy { it.second }
  val nearest = ranked.firstOrNull() ?: return null
  val maximumDistance = maxOf(25f, accuracy * 2f)
  if (nearest.second > maximumDistance) return null
  val nextNearest = ranked.getOrNull(1) ?: return nearest.first
  val separationNeeded = maxOf(20f, accuracy * 1.5f)
  return nearest.first.takeIf { nextNearest.second - nearest.second >= separationNeeded }
}

@Composable
private fun CurrentLocationCard(
  position: UserPosition?,
  address: String?,
  selectedCafe: CafeCandidate?,
  hasPermission: Boolean,
  locationUnavailable: Boolean,
  refreshing: Boolean,
  refreshMessage: String?,
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
      Text(
        "📍",
        style = MaterialTheme.typography.bodyMedium,
      )
      val displayLine = when {
        refreshMessage != null -> refreshMessage
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
        Text("Change", style = MaterialTheme.typography.labelMedium)
      }
      TextButton(
        onClick = onRefresh,
        enabled = !refreshing,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
      ) {
        Text(if (refreshing) "…" else "↻", style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@SuppressLint("MissingPermission")
@Composable
private fun AndroidCameraPreview(
  hasPermission: Boolean,
  lensFacing: Int,
  onTextureViewReady: (TextureView?) -> Unit,
  onRequestPermission: () -> Unit,
) {
  if (!hasPermission) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        "Camera permission needed for live preview",
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      OutlinedButton(onClick = onRequestPermission) {
        Text("Enable camera", color = Color.White)
      }
    }
    return
  }

  val context = LocalContext.current
  var cameraError by remember { mutableStateOf<String?>(null) }

  DisposableEffect(lensFacing, hasPermission) {
    onDispose {
      onTextureViewReady(null)
    }
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { ctx ->
        TextureView(ctx).apply {
          onTextureViewReady(this)
        }
      },
      update = { textureView ->
        onTextureViewReady(textureView)
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return@AndroidView
        val currentTag = textureView.tag as? Pair<*, *>
        if (currentTag?.first == lensFacing && currentTag.second != null) return@AndroidView

        (currentTag?.second as? CameraDevice)?.close()
        textureView.tag = lensFacing to null

        fun openSelectedCamera() {
          try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
              manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == lensFacing
            } ?: manager.cameraIdList.firstOrNull()
            if (cameraId == null) {
              cameraError = "No camera found on device"
              return
            }
            val handler = Handler(Looper.getMainLooper())
            manager.openCamera(
              cameraId,
              object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                  textureView.tag = lensFacing to camera
                  val surfaceTexture = textureView.surfaceTexture ?: return
                  surfaceTexture.setDefaultBufferSize(1280, 960)
                  val surface = Surface(surfaceTexture)
                  try {
                    val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                      addTarget(surface)
                      set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(
                      listOf(surface),
                      object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                          try {
                            session.setRepeatingRequest(requestBuilder.build(), null, handler)
                          } catch (_: Exception) {}
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                      },
                      handler,
                    )
                  } catch (_: Exception) {}
                }

                override fun onDisconnected(camera: CameraDevice) {
                  camera.close()
                  textureView.tag = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                  camera.close()
                  textureView.tag = null
                  cameraError = "Camera unavailable ($error)"
                }
              },
              handler,
            )
          } catch (e: Exception) {
            cameraError = "Could not open camera"
          }
        }

        if (textureView.isAvailable) {
          openSelectedCamera()
        } else {
          textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
              openSelectedCamera()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
              ((textureView.tag as? Pair<*, *>)?.second as? CameraDevice)?.close()
              textureView.tag = null
              return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
          }
        }
      },
    )

    if (cameraError != null) {
      Text(
        cameraError!!,
        color = Color.White.copy(alpha = 0.85f),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun NearbyCafeSheet(
  position: UserPosition?,
  locationUnavailable: Boolean,
  candidates: List<CafeCandidate>,
  searching: Boolean,
  error: String?,
  manualName: String,
  onManualNameChange: (String) -> Unit,
  onChoose: (CafeCandidate) -> Unit,
  onChooseManual: () -> Unit,
) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 24.dp)) {
    Text("Which cafe are you at?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(5.dp))
    Text(
      if (position == null) "Nearby lookup needs a location fix." else "Nearby matches are sorted by distance.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(14.dp))
    if (searching || (position == null && !locationUnavailable)) {
      Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Looking for nearby cafes…")
      }
    } else if (candidates.isNotEmpty()) {
      LazyColumn(Modifier.heightIn(max = 260.dp)) {
        items(candidates.take(12), key = { it.id }) { cafe ->
          ListItem(
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { onChoose(cafe) },
            headlineContent = { Text(cafe.name, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(cafe.address ?: "OpenStreetMap place") },
            trailingContent = { if (position != null) cafe.distanceFrom(position)?.let { Text("${it.toInt()} m", style = MaterialTheme.typography.labelMedium) } },
          )
        }
      }
    } else {
      Text(error ?: "No mapped cafes found nearby. Enter the name instead.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!error.isNullOrBlank() && candidates.isNotEmpty()) {
      Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = manualName,
      onValueChange = onManualNameChange,
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Enter a cafe name") },
      singleLine = true,
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = onChooseManual, enabled = manualName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
      Text("Use this cafe")
    }
    Spacer(Modifier.height(8.dp))
    Text("Cafe suggestions © OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun EmptyLog(modifier: Modifier, onCapture: () -> Unit) {
  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Box(Modifier.size(176.dp).clip(RoundedCornerShape(42.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
      Text("☕", style = MaterialTheme.typography.displayLarge)
    }
    Spacer(Modifier.height(24.dp))
    Text("Your next favorite spot\nstarts here", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text("Take a photo of what you ordered.\nWe’ll save the moment to your log.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    OutlinedButton(onClick = onCapture) { Text("Take your first photo") }
  }
}

@Composable
private fun CafeEntryCard(entry: CafeEntry, archived: Boolean, tagging: Boolean, expanded: Boolean, tagDraft: String, onToggleDetails: () -> Unit, onRetryClassification: () -> Unit, onTagDraft: (String) -> Unit, onTagClick: (String) -> Unit, onAddTag: () -> Unit, onAddPhotos: (CafeEntry) -> Unit, onAction: () -> Unit) {
  val photos = remember(entry.photoPath, entry.photoPaths) {
    entry.photoPaths.filter { File(it).exists() }.ifEmpty { listOf(entry.photoPath) }
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
  val cardWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 360.dp.toPx() }
  val swipeOffset = remember(entry.id) { Animatable(0f) }
  var dragOffset by remember(entry.id) { mutableFloatStateOf(0f) }
  var dragging by remember(entry.id) { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))) {
    val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }.takeIf { it > 0f } ?: cardWidthPx
    val currentOffset = if (dragging) dragOffset else swipeOffset.value
    val swipeProgress = (-currentOffset / widthPx).coerceIn(0f, 1f)
    val actionColor = if (archived) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val actionContentColor = if (archived) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onError
    Card(
      modifier = Modifier.fillMaxWidth()
        .offset { IntOffset(currentOffset.roundToInt(), 0) }
        .pointerInput(entry.id, archived, widthPx) {
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
                  onAction()
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
        },
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
      Box {
        Column {
          Box(Modifier.fillMaxWidth().height(215.dp)) {
            Crossfade(
              targetState = photos[photoIndex.coerceIn(0, photos.lastIndex)],
              animationSpec = tween(700),
              label = "Cafe visit photos",
            ) { path ->
              val bitmap = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
              if (bitmap != null) {
                Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
              }
            }
            FilledTonalButton(
              onClick = { onAddPhotos(entry) },
              modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) { Text("＋ Add photos") }
          }
          Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(entry.venue, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              TextButton(onClick = onToggleDetails, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(if (expanded) "Hide details" else "Details")
              }
            }
            if (entry.address.isNotBlank()) Text(entry.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(5.dp))
            Text(SimpleDateFormat("EEE, d MMM · HH:mm", Locale.getDefault()).format(Date(entry.visitedAt)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tagging) {
              Spacer(Modifier.height(8.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(7.dp))
                Text("Identifying dishes on this device…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            val tags = (entry.autoTags + entry.manualTags).distinctBy(String::lowercase)
            if (tags.isNotEmpty()) {
              Spacer(Modifier.height(7.dp))
              Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                tags.forEach { tag ->
                  SuggestionChip(onClick = { onTagClick(tag) }, label = { Text(tag) })
                }
              }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              OutlinedTextField(value = tagDraft, onValueChange = onTagDraft, modifier = Modifier.weight(1f), label = { Text("Add a dish tag") }, singleLine = true)
              Spacer(Modifier.width(8.dp))
              TextButton(onClick = onAddTag, enabled = tagDraft.isNotBlank()) { Text("Add") }
            }
            if (expanded) {
              HorizontalDivider(Modifier.padding(vertical = 12.dp))
              Text("Automatic classification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
              Text("Model: ${entry.taggingModelVersion ?: AndroidFoodTaggerFacade.MODEL_VERSION}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(Modifier.height(8.dp))
              Text("Run history (${entry.classificationRuns.size})", style = MaterialTheme.typography.labelLarge)
              if (entry.classificationRuns.isEmpty()) {
                Text(
                  if (entry.taggingModelVersion == null) "No classification runs recorded yet."
                  else "This visit was classified before run history was saved.",
                  style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                entry.classificationRuns.forEach { run -> ClassificationRunDetails(run) }
              }
              TextButton(onClick = onRetryClassification, enabled = !tagging) { Text(if (tagging) "Classification running…" else "Run classification again") }
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

@Composable
private fun ClassificationRunDetails(run: ClassificationRun) {
  Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
    Text(SimpleDateFormat("EEE, d MMM · HH:mm:ss", Locale.getDefault()).format(Date(run.occurredAt)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    val summary = when (run.outcome) {
      "ERROR" -> "Processing error"
      "NO_DETECTIONS" -> "Nothing detected"
      else -> "Detected ${run.labels.size} label${if (run.labels.size == 1) "" else "s"}"
    }
    Text("$summary · ${run.photoCount} photo${if (run.photoCount == 1) "" else "s"} · ${run.durationMillis} ms · ${run.modelVersion}", style = MaterialTheme.typography.bodySmall, color = if (run.outcome == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    if (run.labels.isNotEmpty()) {
      Text(run.labels.joinToString(" · ") { "${it.name} (${(it.confidence * 100).toInt()}%)" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
    run.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
  }
}

// Compatibility for the starter project's existing preview and UI test surface.
@Composable
internal fun MainScreen(data: List<String>, modifier: Modifier = Modifier) {
  Column(modifier) { data.forEach { Text("Hello $it!", modifier = Modifier.padding(16.dp)) } }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() { ComPratclotCafesTheme { MainScreen(listOf("Android")) } }
