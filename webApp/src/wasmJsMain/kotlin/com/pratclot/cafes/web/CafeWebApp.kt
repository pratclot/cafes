package com.pratclot.cafes.web

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.ClassificationRun
import com.pratclot.cafes.core.model.FoodLabel
import com.pratclot.cafes.core.model.UserPosition
import com.pratclot.cafes.data.CafeLogStateHolder
import com.pratclot.cafes.ui.SharedVisitDetailsContent
import com.pratclot.cafes.ui.theme.ComPratclotCafesTheme
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
internal fun CafeWebApp() {
  val scope = rememberCoroutineScope()
  var storageWarning by remember { mutableStateOf<String?>(null) }
  val stateHolder = remember {
    CafeLogStateHolder(
      scope = scope,
      repository = BrowserCafeLogRepository { warning -> storageWarning = warning },
      facades = createBrowserLocationFacades(),
      foodTagger = MediaPipeFoodTaggerFacade(),
      modelVersion = BROWSER_FOOD_MODEL_VERSION,
      ioDispatcher = Dispatchers.Default,
      clock = { browserNowMillis().toLong() },
    )
  }
  DisposableEffect(stateHolder) {
    onDispose { stateHolder.close() }
  }

  val uiState by stateHolder.uiState.collectAsState()
  val entries = uiState.entries
  val archived = uiState.archivedEntries
  val position = uiState.currentPosition
  val cafes = uiState.nearbyCafes
  val selectedCafe = uiState.selectedCafe
  val taggingIds = uiState.taggingIds
  val lastCapturedEntryId = uiState.lastCapturedEntryId
  val loadingLocation = uiState.refreshingLocation || uiState.searchingCafes

  var showArchive by remember { mutableStateOf(false) }
  var manualCafe by remember { mutableStateOf("") }
  var searchQuery by remember { mutableStateOf("") }
  var selectedDetailsEntryId by remember { mutableStateOf<String?>(null) }
  var tagDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var showingMobileLog by remember { mutableStateOf(false) }
  var forceCameraOnDesktop by remember { mutableStateOf(false) }
  var editingLocation by remember { mutableStateOf(false) }
  var cameraFacing by remember { mutableStateOf("environment") }
  var cameraReady by remember { mutableStateOf(false) }
  var cameraError by remember { mutableStateOf<String?>(null) }
  var cameraRetryTrigger by remember { mutableIntStateOf(0) }
  var viewfinderLeft by remember { mutableStateOf(0.0) }
  var viewfinderTop by remember { mutableStateOf(0.0) }
  var viewfinderWidth by remember { mutableStateOf(0.0) }
  var viewfinderHeight by remember { mutableStateOf(0.0) }

  val locationMessage = when {
    uiState.refreshingLocation -> "Checking your location…"
    uiState.refreshMessage != null -> uiState.refreshMessage!!
    position == null -> "Location has not been checked yet."
    uiState.searchingCafes -> "${position.latitude.format(5)}, ${position.longitude.format(5)} · ±${position.accuracyMeters.toInt()} m"
    uiState.cafeSearchError != null -> "Location found, but nearby cafes could not be loaded."
    selectedCafe != null -> "${selectedCafe.name} · ${position.latitude.format(5)}, ${position.longitude.format(5)} · ±${position.accuracyMeters.toInt()} m"
    cafes.isNotEmpty() -> "${cafes.size} nearby places need a quick choice."
    else -> "No nearby cafes found. You can enter the name manually."
  }

  fun addPhotosToVisit(id: String) {
    browserRequestPhotos { encoded ->
      val photos = decodePhotoSelection(encoded)
      if (photos.isNotEmpty()) {
        stateHolder.savePickedPhotos(entryId = id, savedPaths = photos)
      }
    }
  }

  fun pickPhotosFromDiskForNewVisit() {
    browserRequestPhotos { encoded ->
      val photos = decodePhotoSelection(encoded)
      if (photos.isNotEmpty()) {
        stateHolder.savePickedPhotos(entryId = null, savedPaths = photos)
      }
    }
  }

  fun snapPhotoFromCamera() {
    val liveFrame = decodePhotoSelection(browserCaptureVideoFrame())
    if (liveFrame.isNotEmpty()) {
      stateHolder.savePickedPhotos(entryId = null, savedPaths = liveFrame)
    } else {
      browserRequestCameraFallback { encoded ->
        val photos = decodePhotoSelection(encoded)
        if (photos.isNotEmpty()) {
          stateHolder.savePickedPhotos(entryId = null, savedPaths = photos)
        }
      }
    }
  }

  LaunchedEffect(stateHolder) { stateHolder.refreshLocation() }

  ComPratclotCafesTheme(darkTheme = false) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFFFFF8F0)) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val desktopLayout = maxWidth >= 900.dp
        val detailsEntry = (entries + archived).firstOrNull { it.id == selectedDetailsEntryId }
        val showingCameraScreen = (!desktopLayout && !showingMobileLog) || (desktopLayout && forceCameraOnDesktop)
        val cameraViewActive = showingCameraScreen && detailsEntry == null
        val showVideoElement = cameraViewActive && cameraReady && !editingLocation

        LaunchedEffect(viewfinderLeft, viewfinderTop, viewfinderWidth, viewfinderHeight, showVideoElement) {
          browserUpdateCameraBounds(
            left = viewfinderLeft,
            top = viewfinderTop,
            width = viewfinderWidth,
            height = viewfinderHeight,
            visible = showVideoElement,
          )
        }

        LaunchedEffect(cameraViewActive, cameraFacing, cameraRetryTrigger) {
          if (cameraViewActive) {
            cameraError = null
            browserStartCamera(cameraFacing) { ok, err ->
              cameraReady = ok
              cameraError = if (ok) null else err.ifEmpty { "Camera is unavailable." }
            }
          } else {
            cameraReady = false
            browserStopCamera()
          }
        }
        DisposableEffect(Unit) {
          onDispose { browserStopCamera() }
        }

        Column(
          Modifier.fillMaxSize()
            .widthIn(max = 1440.dp)
            .align(Alignment.TopCenter)
            .padding(horizontal = if (desktopLayout) 36.dp else 14.dp, vertical = if (desktopLayout) 24.dp else 12.dp),
        ) {
          if (showingCameraScreen) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Text("Cafes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
              OutlinedButton(
                onClick = {
                  if (desktopLayout) forceCameraOnDesktop = false
                  else showingMobileLog = true
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              ) {
                Text("Visit log (${entries.size}) →")
              }
            }
            Spacer(Modifier.height(8.dp))
            CompactLocationBar(
              selectedCafe = selectedCafe,
              manualCafe = manualCafe,
              locationMessage = locationMessage,
              loadingLocation = loadingLocation,
              cafes = cafes,
              editing = editingLocation,
              showAddPhotosAction = false,
              onToggleEditing = { editingLocation = !editingLocation },
              onRefresh = { stateHolder.refreshLocation() },
              onSelectCafe = { cafe ->
                stateHolder.selectCafe(cafe)
                editingLocation = false
              },
              onManualCafeChange = { manualCafe = it },
              onApplyManualCafe = {
                stateHolder.selectManualCafe(manualCafe)
                editingLocation = false
              },
              onAddPhotos = ::pickPhotosFromDiskForNewVisit,
            )
            Spacer(Modifier.height(10.dp))
            val density = LocalDensity.current
            Box(
              Modifier.fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1C1815))
                .onGloballyPositioned { coords ->
                  val pos = coords.positionInWindow()
                  val size = coords.size
                  val d = density.density.coerceAtLeast(0.1f)
                  val l = (pos.x / d).toDouble()
                  val t = (pos.y / d).toDouble()
                  val w = (size.width / d).toDouble()
                  val h = (size.height / d).toDouble()
                  viewfinderLeft = l
                  viewfinderTop = t
                  viewfinderWidth = w
                  viewfinderHeight = h
                  browserUpdateCameraBounds(
                    left = l,
                    top = t,
                    width = w,
                    height = h,
                    visible = showVideoElement,
                  )
                },
              contentAlignment = Alignment.Center,
            ) {
              if (!cameraReady) {
                Column(
                  Modifier.padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    cameraError ?: "Opening camera…",
                    color = Color(0xFFF5ECE3),
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  Text(
                    "You can still use Take photo or From disk below.",
                    color = Color(0xFFBDB1A6),
                    style = MaterialTheme.typography.bodySmall,
                  )
                  if (cameraError != null) {
                    OutlinedButton(onClick = { cameraRetryTrigger += 1 }) {
                      Text("Retry camera", color = Color(0xFFF5ECE3))
                    }
                  }
                }
              } else if (editingLocation) {
                Text(
                  "Camera paused while choosing a cafe",
                  color = Color(0xFFF5ECE3),
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
            val recentSaved = lastCapturedEntryId?.let { id -> entries.firstOrNull { it.id == id } }
            if (recentSaved != null) {
              Spacer(Modifier.height(8.dp))
              Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF2E8DE),
              ) {
                Row(
                  Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    "✓ Saved to ${recentSaved.venue}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF3E342C),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  TextButton(
                    onClick = { addPhotosToVisit(recentSaved.id) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                  ) { Text("+ Photo") }
                  TextButton(
                    onClick = { selectedDetailsEntryId = recentSaved.id },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                  ) { Text("Details") }
                  TextButton(
                    onClick = {
                      if (desktopLayout) forceCameraOnDesktop = false
                      else showingMobileLog = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                  ) { Text("Log →") }
                }
              }
            }
            Spacer(Modifier.height(10.dp))
            Row(
              Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(
                onClick = ::pickPhotosFromDiskForNewVisit,
                modifier = Modifier.height(50.dp),
              ) {
                Text("From disk")
              }
              Button(
                onClick = ::snapPhotoFromCamera,
                modifier = Modifier.weight(1f).height(50.dp),
              ) {
                Text("● Take photo", fontWeight = FontWeight.SemiBold)
              }
              OutlinedButton(
                onClick = { cameraFacing = if (cameraFacing == "environment") "user" else "environment" },
                modifier = Modifier.height(50.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
              ) {
                Text("↺")
              }
            }
          } else {
            val shown = if (showArchive) archived else entries
            val query = searchQuery.trim()
            val matching = shown.filter { entry ->
              query.isBlank() || entry.venue.contains(query, true) || entry.address.contains(query, true) || (entry.manualTags + entry.autoTags).any { it.contains(query, true) }
            }

            if (desktopLayout) {
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                  Text(
                    if (showArchive) "Archived visits" else "Cafe moments",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text("A little log of places you share", color = Color(0xFF6E6258), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                  onClick = { forceCameraOnDesktop = true },
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                  Text("📷 Camera")
                }
                TextButton(onClick = { showArchive = !showArchive }) {
                  Text(if (showArchive) "Visit log (${entries.size})" else "Archive (${archived.size})")
                }
              }
              Spacer(Modifier.height(8.dp))

              if (!showArchive) {
                CompactLocationBar(
                  selectedCafe = selectedCafe,
                  manualCafe = manualCafe,
                  locationMessage = locationMessage,
                  loadingLocation = loadingLocation,
                  cafes = cafes,
                  editing = editingLocation,
                  showAddPhotosAction = true,
                  onToggleEditing = { editingLocation = !editingLocation },
                  onRefresh = { stateHolder.refreshLocation() },
                  onSelectCafe = { cafe ->
                    stateHolder.selectCafe(cafe)
                    editingLocation = false
                  },
                  onManualCafeChange = { manualCafe = it },
                  onApplyManualCafe = {
                    stateHolder.selectManualCafe(manualCafe)
                    editingLocation = false
                  },
                  onAddPhotos = ::pickPhotosFromDiskForNewVisit,
                )
                Spacer(Modifier.height(8.dp))
              }

              OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search cafes and dishes") },
                placeholder = { Text("Try “Galette” or “Egg”") },
                singleLine = true,
              )
              Spacer(Modifier.height(10.dp))

              if (matching.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                  Text(when { shown.isEmpty() && showArchive -> "Nothing archived yet."; shown.isEmpty() -> "Your cafe visits will show up here."; else -> "No matching visits." })
                }
              } else {
                LazyVerticalGrid(
                  columns = GridCells.Adaptive(minSize = 320.dp),
                  modifier = Modifier.fillMaxWidth().weight(1f),
                  contentPadding = PaddingValues(bottom = 84.dp),
                  horizontalArrangement = Arrangement.spacedBy(16.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  gridItems(matching, key = { it.id }) { entry ->
                    VisitCard(
                      entry = entry,
                      archived = showArchive,
                      showArchiveButton = true,
                      swipeEnabled = false,
                      tagging = entry.id in taggingIds,
                      tagDraft = tagDrafts[entry.id].orEmpty(),
                      onDetails = { selectedDetailsEntryId = entry.id },
                      onTagClick = { searchQuery = it },
                      onTagDraft = { tagDrafts = tagDrafts + (entry.id to it) },
                      onAddTag = {
                        val tag = tagDrafts[entry.id].orEmpty().trim()
                        if (tag.isNotEmpty()) {
                          stateHolder.addManualTag(entry, tag)
                          tagDrafts = tagDrafts - entry.id
                        }
                      },
                      onPhotos = { addPhotosToVisit(entry.id) },
                      onArchive = { stateHolder.archiveEntry(entry) },
                      onRestore = { stateHolder.restoreEntry(entry) },
                    )
                  }
                }
              }
            } else {
              LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                item(key = "mobile-log-header") {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      OutlinedButton(
                        onClick = { showingMobileLog = false },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                      ) {
                        Text("← Camera")
                      }
                      Text(
                        if (showArchive) "Archived visits" else "Visit log",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                      )
                      TextButton(onClick = { showArchive = !showArchive }) {
                        Text(if (showArchive) "Visit log (${entries.size})" else "Archive (${archived.size})")
                      }
                    }
                    if (!showArchive) {
                      CompactLocationBar(
                        selectedCafe = selectedCafe,
                        manualCafe = manualCafe,
                        locationMessage = locationMessage,
                        loadingLocation = loadingLocation,
                        cafes = cafes,
                        editing = editingLocation,
                        showAddPhotosAction = true,
                        onToggleEditing = { editingLocation = !editingLocation },
                        onRefresh = { stateHolder.refreshLocation() },
                        onSelectCafe = { cafe ->
                          stateHolder.selectCafe(cafe)
                          editingLocation = false
                        },
                        onManualCafeChange = { manualCafe = it },
                        onApplyManualCafe = {
                          stateHolder.selectManualCafe(manualCafe)
                          editingLocation = false
                        },
                        onAddPhotos = ::pickPhotosFromDiskForNewVisit,
                      )
                    }
                    OutlinedTextField(
                      value = searchQuery,
                      onValueChange = { searchQuery = it },
                      modifier = Modifier.fillMaxWidth(),
                      label = { Text("Search cafes and dishes") },
                      placeholder = { Text("Try “Galette” or “Egg”") },
                      singleLine = true,
                    )
                  }
                }

                if (matching.isEmpty()) {
                  item(key = "mobile-log-empty") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                      Text(when { shown.isEmpty() && showArchive -> "Nothing archived yet."; shown.isEmpty() -> "Your cafe visits will show up here."; else -> "No matching visits." })
                    }
                  }
                } else {
                  items(matching, key = { it.id }) { entry ->
                    VisitCard(
                      entry = entry,
                      archived = showArchive,
                      showArchiveButton = false,
                      swipeEnabled = true,
                      tagging = entry.id in taggingIds,
                      tagDraft = tagDrafts[entry.id].orEmpty(),
                      onDetails = { selectedDetailsEntryId = entry.id },
                      onTagClick = { searchQuery = it },
                      onTagDraft = { tagDrafts = tagDrafts + (entry.id to it) },
                      onAddTag = {
                        val tag = tagDrafts[entry.id].orEmpty().trim()
                        if (tag.isNotEmpty()) {
                          stateHolder.addManualTag(entry, tag)
                          tagDrafts = tagDrafts - entry.id
                        }
                      },
                      onPhotos = { addPhotosToVisit(entry.id) },
                      onArchive = { stateHolder.archiveEntry(entry) },
                      onRestore = { stateHolder.restoreEntry(entry) },
                    )
                  }
                }
              }
            }
          }
          storageWarning?.let { Text(it, color = Color(0xFFB3261E), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp)) }
          Text("Saved in this browser · no account or device sync yet", style = MaterialTheme.typography.labelSmall, color = Color(0xFF81766D), modifier = Modifier.padding(top = 6.dp))
        }
        if (detailsEntry != null) {
          val onRetryClassification = {
            stateHolder.retryClassification(detailsEntry)
          }
          if (desktopLayout) {
            Dialog(onDismissRequest = { selectedDetailsEntryId = null }) {
              Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).heightIn(max = 820.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFFF8F0),
              ) {
                VisitDetailsContent(
                  entry = detailsEntry,
                  tagging = detailsEntry.id in taggingIds,
                  onClose = { selectedDetailsEntryId = null },
                  onRetryClassification = onRetryClassification,
                  modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                  fullScreen = false,
                )
              }
            }
          } else {
            VisitDetailsScreen(
              entry = detailsEntry,
              tagging = detailsEntry.id in taggingIds,
              onClose = { selectedDetailsEntryId = null },
              onRetryClassification = onRetryClassification,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CompactLocationBar(
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
      color = Color(0xFFF2E8DE),
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
          color = Color(0xFF3E342C),
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
          Text(locationMessage, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5E554D))
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

@Composable
private fun VisitCard(
  entry: CafeEntry,
  archived: Boolean,
  showArchiveButton: Boolean,
  swipeEnabled: Boolean,
  tagging: Boolean,
  tagDraft: String,
  onDetails: () -> Unit,
  onTagClick: (String) -> Unit,
  onTagDraft: (String) -> Unit,
  onAddTag: () -> Unit,
  onPhotos: () -> Unit,
  onArchive: () -> Unit,
  onRestore: () -> Unit,
) {
  var photoIndex by remember(entry.id) { mutableIntStateOf(0) }
  val photos = entry.photoPaths.ifEmpty { listOf(entry.photoPath) }
  LaunchedEffect(entry.id, photos.size) {
    while (photos.size > 1) {
      kotlinx.coroutines.delay(3200)
      photoIndex = (photoIndex + 1) % photos.size
    }
  }

  val swipeOffset = remember(entry.id) { Animatable(0f) }
  var dragOffset by remember(entry.id) { mutableFloatStateOf(0f) }
  var dragging by remember(entry.id) { mutableStateOf(false) }
  var cardWidthPx by remember(entry.id) { mutableFloatStateOf(1f) }
  val scope = rememberCoroutineScope()
  val offset = if (dragging) dragOffset else swipeOffset.value
  val swipeProgress = (-offset / cardWidthPx).coerceIn(0f, 1f)
  val actionColor = if (archived) Color(0xFF477A58) else Color(0xFFC62828)

  Box(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .semantics {
        customActions = listOf(
          CustomAccessibilityAction(
            label = if (archived) "Restore visit" else "Archive visit",
            action = {
              if (archived) onRestore() else onArchive()
              true
            },
          ),
        )
      }
      .onSizeChanged { cardWidthPx = it.width.toFloat().coerceAtLeast(1f) },
  ) {
    Card(
      Modifier.fillMaxWidth()
      .offset { IntOffset(offset.roundToInt(), 0) },
    ) {
      Box {
        Column {
          Box(
            Modifier.fillMaxWidth().height(220.dp).then(
              if (swipeEnabled) Modifier.pointerInput(entry.id, archived, cardWidthPx) {
                detectHorizontalDragGestures(
                  onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dragging = true
                    dragOffset = (dragOffset + dragAmount).coerceIn(-cardWidthPx, 0f)
                  },
                  onDragEnd = {
                    scope.launch {
                      swipeOffset.snapTo(dragOffset)
                      dragging = false
                      if (dragOffset < -cardWidthPx * 0.38f) {
                        swipeOffset.animateTo(-cardWidthPx, tween(190))
                        if (archived) onRestore() else onArchive()
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
              } else Modifier,
            ),
          ) {
            Crossfade(
              targetState = photos[photoIndex % photos.size],
              modifier = Modifier.fillMaxSize(),
              animationSpec = tween(700),
              label = "visit-photo",
            ) { photo ->
              BrowserPhoto(photo, Modifier.fillMaxSize())
            }
          }
          Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(entry.venue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (entry.address.isNotBlank()) Text(entry.address, color = Color(0xFF6E6258))
                Text(browserFormatDate(entry.visitedAt.toDouble()), style = MaterialTheme.typography.labelMedium, color = Color(0xFF81766D))
              }
              Column(horizontalAlignment = Alignment.End) {
                Text("${photoIndex + 1}/${photos.size}", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = onDetails) { Text("Details") }
              }
            }
            if (tagging) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
                Text("Identifying dishes on this device…", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp), color = Color(0xFF6E6258))
              }
            }
            val tags = (entry.autoTags + entry.manualTags).distinctBy(String::lowercase)
            if (tags.isNotEmpty()) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
              ) {
                tags.forEach { tag ->
                  Surface(
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable { onTagClick(tag) },
                    color = Color(0xFFF2E8DE),
                    shape = RoundedCornerShape(24.dp),
                  ) {
                    Text(tag, color = Color(0xFF8D5E3C), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                  }
                }
              }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
              OutlinedTextField(tagDraft, onTagDraft, modifier = Modifier.weight(1f), label = { Text("Add a dish tag") }, singleLine = true)
              TextButton(onClick = onAddTag, enabled = tagDraft.isNotBlank()) { Text("Add") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
              OutlinedButton(onClick = onPhotos) { Text("Add photos") }
              if (showArchiveButton) {
                if (archived) OutlinedButton(onClick = onRestore) { Text("Restore") }
                else OutlinedButton(onClick = onArchive) { Text("Archive") }
              }
            }
          }
        }
        if (swipeProgress > 0f) {
          Box(
            Modifier.matchParentSize().background(actionColor.copy(alpha = swipeProgress)),
            contentAlignment = Alignment.Center,
          ) {
            Text(if (archived) "↶" else "▣", style = MaterialTheme.typography.displaySmall, color = Color.White.copy(alpha = swipeProgress))
          }
        }
      }
    }
  }
}

@Composable
private fun VisitDetailsScreen(
  entry: CafeEntry,
  tagging: Boolean,
  onClose: () -> Unit,
  onRetryClassification: () -> Unit,
) {
  Surface(Modifier.fillMaxSize(), color = Color(0xFFFFF8F0)) {
    VisitDetailsContent(
      entry = entry,
      tagging = tagging,
      onClose = onClose,
      onRetryClassification = onRetryClassification,
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
      fullScreen = true,
    )
  }
}

@Composable
private fun VisitDetailsContent(
  entry: CafeEntry,
  tagging: Boolean,
  onClose: () -> Unit,
  onRetryClassification: () -> Unit,
  modifier: Modifier,
  fullScreen: Boolean,
) {
  SharedVisitDetailsContent(
    entry = entry,
    currentModelVersion = BROWSER_FOOD_MODEL_VERSION,
    tagging = tagging,
    onClose = onClose,
    closeLabel = if (fullScreen) "← Back to visits" else "Close",
    onRetryClassification = onRetryClassification,
    modifier = modifier,
  )
}

@Composable
private fun BrowserPhoto(dataUrl: String, modifier: Modifier = Modifier) {
  val bitmap = remember(dataUrl) { decodeImage(dataUrl) }
  if (bitmap == null) {
    Box(modifier.background(Color(0xFFE9DED2), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)), contentAlignment = Alignment.Center) {
      Text("Photo unavailable")
    }
  } else {
    Image(bitmap, contentDescription = "Cafe visit photo", modifier = modifier, contentScale = ContentScale.Crop)
  }
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeImage(dataUrl: String): ImageBitmap? = runCatching {
  val bytes = Base64.Default.decode(dataUrl.substringAfter(',', missingDelimiterValue = ""))
  bytes.decodeToImageBitmap()
}.getOrNull()

private fun decodePhotoSelection(value: String): List<String> = value.lineSequence()
  .filter(String::isNotBlank)
  .map(::decodeBrowserValue)
  .toList()

private fun Double.format(digits: Int): String = toString().let { value ->
  val dot = value.indexOf('.')
  if (dot < 0) value else value.take(dot + digits + 1)
}
