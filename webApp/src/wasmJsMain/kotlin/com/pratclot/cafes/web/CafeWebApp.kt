package com.pratclot.cafes.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.ui.generated.resources.Res
import com.pratclot.cafes.core.ui.generated.resources.action_add_photo_short
import com.pratclot.cafes.core.ui.generated.resources.action_back_to_camera
import com.pratclot.cafes.core.ui.generated.resources.action_back_to_visits
import com.pratclot.cafes.core.ui.generated.resources.action_camera
import com.pratclot.cafes.core.ui.generated.resources.action_close
import com.pratclot.cafes.core.ui.generated.resources.action_details
import com.pratclot.cafes.core.ui.generated.resources.action_from_disk
import com.pratclot.cafes.core.ui.generated.resources.action_log_short
import com.pratclot.cafes.core.ui.generated.resources.action_retry_camera
import com.pratclot.cafes.core.ui.generated.resources.action_take_photo
import com.pratclot.cafes.core.ui.generated.resources.action_visit_log_count
import com.pratclot.cafes.core.ui.generated.resources.app_subtitle
import com.pratclot.cafes.core.ui.generated.resources.app_title
import com.pratclot.cafes.core.ui.generated.resources.identifying_dishes_in_browser
import com.pratclot.cafes.core.ui.generated.resources.photo_unavailable
import com.pratclot.cafes.core.ui.generated.resources.search_label
import com.pratclot.cafes.core.ui.generated.resources.search_placeholder
import com.pratclot.cafes.core.ui.generated.resources.web_storage_notice
import com.pratclot.cafes.data.CafeLogStateHolder
import com.pratclot.cafes.ui.SharedCompactLocationBar
import com.pratclot.cafes.ui.SharedVisitDetailsContent
import com.pratclot.cafes.ui.SharedVisitEntryCard
import com.pratclot.cafes.ui.theme.ComPratclotCafesTheme
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
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
      idGenerator = { "cafe-${browserRandomUuid()}" },
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
              Text(stringResource(Res.string.app_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
              OutlinedButton(
                onClick = {
                  if (desktopLayout) forceCameraOnDesktop = false
                  else showingMobileLog = true
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              ) {
                Text(stringResource(Res.string.action_visit_log_count, entries.size))
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
                      Text(stringResource(Res.string.action_retry_camera), color = Color(0xFFF5ECE3))
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
                  ) { Text(stringResource(Res.string.action_add_photo_short)) }
                  TextButton(
                    onClick = { selectedDetailsEntryId = recentSaved.id },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                  ) { Text(stringResource(Res.string.action_details)) }
                  TextButton(
                    onClick = {
                      if (desktopLayout) forceCameraOnDesktop = false
                      else showingMobileLog = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                  ) { Text(stringResource(Res.string.action_log_short)) }
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
                Text(stringResource(Res.string.action_from_disk))
              }
              Button(
                onClick = ::snapPhotoFromCamera,
                modifier = Modifier.weight(1f).height(50.dp),
              ) {
                Text(stringResource(Res.string.action_take_photo), fontWeight = FontWeight.SemiBold)
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
                  Text(stringResource(Res.string.app_subtitle), color = Color(0xFF6E6258), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                  onClick = { forceCameraOnDesktop = true },
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                  Text(stringResource(Res.string.action_camera))
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
                label = { Text(stringResource(Res.string.search_label)) },
                placeholder = { Text(stringResource(Res.string.search_placeholder)) },
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
                          stateHolder.addManualTag(entry.id, tag)
                          tagDrafts = tagDrafts - entry.id
                        }
                      },
                      onPhotos = { addPhotosToVisit(entry.id) },
                      onArchive = { stateHolder.archiveEntry(entry.id) },
                      onRestore = { stateHolder.restoreEntry(entry.id) },
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
                        Text(stringResource(Res.string.action_back_to_camera))
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
                      label = { Text(stringResource(Res.string.search_label)) },
                      placeholder = { Text(stringResource(Res.string.search_placeholder)) },
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
                          stateHolder.addManualTag(entry.id, tag)
                          tagDrafts = tagDrafts - entry.id
                        }
                      },
                      onPhotos = { addPhotosToVisit(entry.id) },
                      onArchive = { stateHolder.archiveEntry(entry.id) },
                      onRestore = { stateHolder.restoreEntry(entry.id) },
                    )
                  }
                }
              }
            }
          }
          storageWarning?.let { Text(it, color = Color(0xFFB3261E), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp)) }
          Text(stringResource(Res.string.web_storage_notice), style = MaterialTheme.typography.labelSmall, color = Color(0xFF81766D), modifier = Modifier.padding(top = 6.dp))
        }
        if (detailsEntry != null) {
          val onRetryClassification = {
            stateHolder.retryClassification(detailsEntry.id)
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
  SharedCompactLocationBar(
    selectedCafe = selectedCafe,
    manualCafe = manualCafe,
    locationMessage = locationMessage,
    loadingLocation = loadingLocation,
    cafes = cafes,
    editing = editing,
    showAddPhotosAction = showAddPhotosAction,
    onToggleEditing = onToggleEditing,
    onRefresh = onRefresh,
    onSelectCafe = onSelectCafe,
    onManualCafeChange = onManualCafeChange,
    onApplyManualCafe = onApplyManualCafe,
    onAddPhotos = onAddPhotos,
  )
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
  SharedVisitEntryCard(
    entry = entry,
    archived = archived,
    swipeEnabled = swipeEnabled,
    showArchiveButton = showArchiveButton,
    tagging = tagging,
    taggingLabel = stringResource(Res.string.identifying_dishes_in_browser),
    tagDraft = tagDraft,
    onOpenDetails = onDetails,
    onTagDraft = onTagDraft,
    onTagClick = onTagClick,
    onAddTag = onAddTag,
    onAddPhotos = onPhotos,
    onArchive = onArchive,
    onRestore = onRestore,
    photoContent = { photo, modifier -> BrowserPhoto(photo, modifier) },
  )
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
    closeLabel = stringResource(if (fullScreen) Res.string.action_back_to_visits else Res.string.action_close),
    onRetryClassification = onRetryClassification,
    modifier = modifier,
  )
}

private val browserPhotoCache = LinkedHashMap<String, ImageBitmap>()
private const val MAX_BROWSER_PHOTO_CACHE = 32

@Composable
private fun BrowserPhoto(dataUrl: String, modifier: Modifier = Modifier) {
  val bitmap by produceState<ImageBitmap?>(initialValue = browserPhotoCache[dataUrl], dataUrl) {
    browserPhotoCache[dataUrl]?.let { cached ->
      value = cached
      return@produceState
    }
    val resolvedUrl = if (dataUrl.startsWith("idb://")) loadPhotoBlobSuspend(dataUrl) else dataUrl
    if (resolvedUrl.isEmpty()) {
      value = null
      return@produceState
    }
    val decoded = withContext(Dispatchers.Default) { decodeImage(resolvedUrl) }
    if (decoded != null) {
      if (browserPhotoCache.size >= MAX_BROWSER_PHOTO_CACHE) {
        browserPhotoCache.keys.firstOrNull()?.let { oldest -> browserPhotoCache.remove(oldest) }
      }
      browserPhotoCache[dataUrl] = decoded
    }
    value = decoded
  }
  if (bitmap == null) {
    Box(modifier.background(Color(0xFFE9DED2), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)), contentAlignment = Alignment.Center) {
      Text(stringResource(Res.string.photo_unavailable))
    }
  } else {
    Image(bitmap!!, contentDescription = "Cafe visit photo", modifier = modifier, contentScale = ContentScale.Crop)
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
