package com.pratclot.cafes.ui.main

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pratclot.cafes.data.LocalCafeLogRepository
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
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
  var showingArchive by remember { mutableStateOf(false) }
  var photoPickerEntryId by remember { mutableStateOf<String?>(null) }
  var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }
  var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
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
  var failedTaggingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var manualTagDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

  LaunchedEffect(entries, archivedEntries) {
    val allEntries = entries + archivedEntries
    val pending = allEntries.filter { it.photoPaths.isNotEmpty() && it.taggingModelVersion != AndroidFoodTaggerFacade.MODEL_VERSION && it.id !in taggingIds && it.id !in failedTaggingIds }
    pending.forEach { entry ->
      taggingIds = taggingIds + entry.id
      try {
        val labels = foodTagger.classify(entry.photoPaths).filter { it.confidence >= 0.22f }.map { it.name }
        val updated = repository.update(entry.copy(autoTags = labels, taggingModelVersion = AndroidFoodTaggerFacade.MODEL_VERSION))
        entries = updated.first
        archivedEntries = updated.second
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        failedTaggingIds = failedTaggingIds + entry.id
      } finally {
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
    if (entryId != null && uris.isNotEmpty()) {
      coroutineScope.launch {
        val savedPaths = withContext(Dispatchers.IO) {
          uris.mapIndexedNotNull { index, uri ->
            runCatching {
              val file = File(context.filesDir, "cafe-$entryId-${System.currentTimeMillis()}-$index.photo")
              context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
                ?: return@mapIndexedNotNull null
              file.absolutePath
            }.getOrNull()
          }
        }
        if (savedPaths.isNotEmpty()) {
          val updated = withContext(Dispatchers.IO) { repository.appendPhotos(entryId, savedPaths) }
          entries = updated.first
          archivedEntries = updated.second
          failedTaggingIds = failedTaggingIds - entryId
        }
      }
    }
  }
  val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    locationPermissionGranted = hasLocationPermission(context)
    if (openSheetAfterPermission && locationPermissionGranted) cafeSheetOpen = true
    openSheetAfterPermission = false
  }
  val requestCapture: () -> Unit = { camera.launch(null) }
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
    showLocationHint = position == null
    pendingPhoto = null
  }

  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = requestCapture,
        icon = { Text("＋", style = MaterialTheme.typography.headlineSmall) },
        text = { Text("Capture a cafe") },
      )
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
      Spacer(Modifier.height(18.dp))
      Text("little places,\nbig memories", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, lineHeight = MaterialTheme.typography.headlineMedium.lineHeight)
      Spacer(Modifier.height(8.dp))
      Text("Your cafe dates, one photo at a time", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(16.dp))
      CurrentLocationCard(
        position = currentPosition,
        address = currentAddress,
        selectedCafe = selectedCafe,
        hasPermission = locationPermissionGranted,
        locationUnavailable = locationUnavailable,
        refreshing = refreshingLocation,
        refreshMessage = refreshMessage,
        onRefresh = refreshLocation,
        onClick = {
          if (locationPermissionGranted) cafeSheetOpen = true
          else {
            openSheetAfterPermission = true
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
          }
        },
      )
      Spacer(Modifier.height(18.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (showingArchive) "ARCHIVED VISITS" else "YOUR CAFE LOG", style = MaterialTheme.typography.labelLarge, letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing)
        Spacer(Modifier.width(8.dp))
        val visibleEntries = if (showingArchive) archivedEntries else entries
        Text("${visibleEntries.size}", modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { showingArchive = !showingArchive }) {
          Text(if (showingArchive) "Back to log" else "Archive (${archivedEntries.size})")
        }
      }
      Spacer(Modifier.height(12.dp))
      val visibleEntries = if (showingArchive) archivedEntries else entries
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search cafes and dishes") },
        placeholder = { Text("Try “Benedict”") },
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
        } else if (visibleEntries.isEmpty() && query.isBlank()) EmptyLog(Modifier.weight(1f), onCapture = requestCapture)
        else Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
          Text("No matching visits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
          Text("Search by cafe name or a dish in your photos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          items(filteredEntries, key = { it.id }) { entry ->
            CafeEntryCard(entry, archived = showingArchive, tagging = entry.id in taggingIds, tagDraft = manualTagDrafts[entry.id].orEmpty(), onTagDraft = { draft -> manualTagDrafts = manualTagDrafts + (entry.id to draft) }, onTagClick = { tag -> searchQuery = tag }, onAddTag = {
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
      if (showLocationHint) {
        Text("Saved your photo. Turn on location to add where you are.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 84.dp))
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
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  ) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = .75f)), contentAlignment = Alignment.Center) {
        Text("⌖", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
      }
      Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp).clickable(onClick = onClick)) {
        Text("WE THINK YOU’RE", style = MaterialTheme.typography.labelSmall, letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing, color = MaterialTheme.colorScheme.onPrimaryContainer)
        val title = when {
          !hasPermission -> "Location is off"
          selectedCafe != null -> selectedCafe.name
          position == null && locationUnavailable -> "Location unavailable"
          position == null -> "Finding your location…"
          else -> address ?: "Nearby"
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
        val subtitle = when {
          !hasPermission -> "Tap to enable location"
          position == null && locationUnavailable -> "Check that device location is on · tap to choose manually"
          position == null -> "This updates while the app is open"
          selectedCafe != null -> selectedCafe.address ?: address ?: "Cafe selected nearby"
          else -> "About ${position.accuracyMeters.toInt()} m accuracy · tap to choose cafe"
        }
        Text(refreshMessage ?: subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f), maxLines = 2)
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onRefresh, enabled = !refreshing) {
          if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
          else Text("↻", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(if (refreshing) "Updating" else "Refresh", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
      }
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
private fun CafeEntryCard(entry: CafeEntry, archived: Boolean, tagging: Boolean, tagDraft: String, onTagDraft: (String) -> Unit, onTagClick: (String) -> Unit, onAddTag: () -> Unit, onAddPhotos: (CafeEntry) -> Unit, onAction: () -> Unit) {
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
            Text(entry.venue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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

// Compatibility for the starter project's existing preview and UI test surface.
@Composable
internal fun MainScreen(data: List<String>, modifier: Modifier = Modifier) {
  Column(modifier) { data.forEach { Text("Hello $it!", modifier = Modifier.padding(16.dp)) } }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() { ComPratclotCafesTheme { MainScreen(listOf("Android")) } }
