package com.pratclot.cafes.web

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import com.pratclot.cafes.core.ui.generated.resources.Res
import com.pratclot.cafes.core.ui.generated.resources.action_add_photo_short
import com.pratclot.cafes.core.ui.generated.resources.action_archive_count
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
import com.pratclot.cafes.core.ui.generated.resources.archived_visits_title
import com.pratclot.cafes.core.ui.generated.resources.cafe_moments_title
import com.pratclot.cafes.core.ui.generated.resources.cafe_search_unavailable
import com.pratclot.cafes.core.ui.generated.resources.camera_fallback_hint
import com.pratclot.cafes.core.ui.generated.resources.camera_opening
import com.pratclot.cafes.core.ui.generated.resources.camera_paused_choosing_cafe
import com.pratclot.cafes.core.ui.generated.resources.camera_unavailable
import com.pratclot.cafes.core.ui.generated.resources.location_cafe_coords_accuracy
import com.pratclot.cafes.core.ui.generated.resources.location_checking
import com.pratclot.cafes.core.ui.generated.resources.location_coords_accuracy
import com.pratclot.cafes.core.ui.generated.resources.location_nearby_places_choice
import com.pratclot.cafes.core.ui.generated.resources.location_no_fresh
import com.pratclot.cafes.core.ui.generated.resources.location_no_nearby_manual
import com.pratclot.cafes.core.ui.generated.resources.location_not_checked
import com.pratclot.cafes.core.ui.generated.resources.location_refresh_failed
import com.pratclot.cafes.core.ui.generated.resources.saved_to_venue
import com.pratclot.cafes.core.ui.generated.resources.search_label
import com.pratclot.cafes.core.ui.generated.resources.search_placeholder
import com.pratclot.cafes.core.ui.generated.resources.visit_log_count
import com.pratclot.cafes.core.ui.generated.resources.visit_log_title
import com.pratclot.cafes.core.ui.generated.resources.web_empty_archive
import com.pratclot.cafes.core.ui.generated.resources.web_empty_log
import com.pratclot.cafes.core.ui.generated.resources.web_empty_search
import com.pratclot.cafes.core.ui.generated.resources.web_storage_notice
import com.pratclot.cafes.data.CafeLogStateHolder
import com.pratclot.cafes.data.RefreshStatusMessage
import com.pratclot.cafes.ui.CompactLocationBarAction
import com.pratclot.cafes.ui.CompactLocationBarState
import com.pratclot.cafes.ui.VisitDetailsAction
import com.pratclot.cafes.ui.VisitDetailsState
import com.pratclot.cafes.ui.VisitEntryCardAction
import com.pratclot.cafes.ui.VisitEntryCardState
import com.pratclot.cafes.ui.theme.ComPratclotCafesTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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

  val refreshMessageText = when (uiState.refreshMessage) {
    RefreshStatusMessage.NO_FRESH_LOCATION -> stringResource(Res.string.location_no_fresh)
    RefreshStatusMessage.REFRESH_FAILED -> stringResource(Res.string.location_refresh_failed)
    null -> null
  }
  val cafeSearchErrorText = uiState.cafeSearchError?.let { stringResource(Res.string.cafe_search_unavailable) }

  val locationMessage = when {
    uiState.refreshingLocation -> stringResource(Res.string.location_checking)
    refreshMessageText != null -> refreshMessageText
    position == null -> stringResource(Res.string.location_not_checked)
    uiState.searchingCafes -> stringResource(
      Res.string.location_coords_accuracy,
      position.latitude.format(5),
      position.longitude.format(5),
      position.accuracyMeters.toInt(),
    )
    cafeSearchErrorText != null -> cafeSearchErrorText
    selectedCafe != null -> stringResource(
      Res.string.location_cafe_coords_accuracy,
      selectedCafe.name,
      position.latitude.format(5),
      position.longitude.format(5),
      position.accuracyMeters.toInt(),
    )
    cafes.isNotEmpty() -> pluralStringResource(Res.plurals.location_nearby_places_choice, cafes.size, cafes.size)
    else -> stringResource(Res.string.location_no_nearby_manual)
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

  ComPratclotCafesTheme {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
              cameraError = if (ok) null else err
            }
          } else {
            cameraReady = false
            browserStopCamera()
          }
        }
        DisposableEffect(Unit) {
          onDispose { browserStopCamera() }
        }

        val onLocationAction: (CompactLocationBarAction) -> Unit = { action ->
          when (action) {
            CompactLocationBarAction.ToggleEditing -> editingLocation = !editingLocation
            CompactLocationBarAction.Refresh -> stateHolder.refreshLocation()
            is CompactLocationBarAction.SelectCafe -> {
              stateHolder.selectCafe(action.cafe)
              editingLocation = false
            }
            is CompactLocationBarAction.ChangeManualCafe -> manualCafe = action.name
            CompactLocationBarAction.ApplyManualCafe -> {
              stateHolder.selectManualCafe(manualCafe)
              editingLocation = false
            }
            CompactLocationBarAction.AddPhotos -> pickPhotosFromDiskForNewVisit()
          }
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
              state = CompactLocationBarState(
                selectedCafe = selectedCafe,
                manualCafe = manualCafe,
                locationMessage = locationMessage,
                loadingLocation = loadingLocation,
                cafes = cafes,
                editing = editingLocation,
                showAddPhotosAction = false,
              ),
              onAction = onLocationAction,
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
                },
              contentAlignment = Alignment.Center,
            ) {
              if (!cameraReady) {
                Column(
                  Modifier.padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  val cameraStatusText = when (val err = cameraError) {
                    null -> stringResource(Res.string.camera_opening)
                    "" -> stringResource(Res.string.camera_unavailable)
                    else -> err
                  }
                  Text(
                    cameraStatusText,
                    color = Color(0xFFF5ECE3),
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  Text(
                    stringResource(Res.string.camera_fallback_hint),
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
                  stringResource(Res.string.camera_paused_choosing_cafe),
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
                color = MaterialTheme.colorScheme.surfaceVariant,
              ) {
                Row(
                  Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    stringResource(Res.string.saved_to_venue, recentSaved.venue),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    stringResource(if (showArchive) Res.string.archived_visits_title else Res.string.cafe_moments_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(stringResource(Res.string.app_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                  onClick = { forceCameraOnDesktop = true },
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                  Text(stringResource(Res.string.action_camera))
                }
                TextButton(onClick = { showArchive = !showArchive }) {
                  Text(
                    if (showArchive) {
                      stringResource(Res.string.visit_log_count, entries.size)
                    } else {
                      stringResource(Res.string.action_archive_count, archived.size)
                    },
                  )
                }
              }
              Spacer(Modifier.height(8.dp))

              if (!showArchive) {
                CompactLocationBar(
                  state = CompactLocationBarState(
                    selectedCafe = selectedCafe,
                    manualCafe = manualCafe,
                    locationMessage = locationMessage,
                    loadingLocation = loadingLocation,
                    cafes = cafes,
                    editing = editingLocation,
                    showAddPhotosAction = true,
                  ),
                  onAction = onLocationAction,
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
                  Text(
                    stringResource(
                      when {
                        shown.isEmpty() && showArchive -> Res.string.web_empty_archive
                        shown.isEmpty() -> Res.string.web_empty_log
                        else -> Res.string.web_empty_search
                      },
                    ),
                  )
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
                      state = VisitEntryCardState(
                        entry = entry,
                        archived = showArchive,
                        swipeEnabled = false,
                        showArchiveButton = true,
                        tagging = entry.id in taggingIds,
                        tagDraft = tagDrafts[entry.id].orEmpty(),
                      ),
                      onAction = { action ->
                        when (action) {
                          VisitEntryCardAction.OpenDetails -> selectedDetailsEntryId = entry.id
                          is VisitEntryCardAction.ClickTag -> searchQuery = action.tag
                          is VisitEntryCardAction.ChangeTagDraft -> tagDrafts = tagDrafts + (entry.id to action.draft)
                          VisitEntryCardAction.AddTag -> {
                            val tag = tagDrafts[entry.id].orEmpty().trim()
                            if (tag.isNotEmpty()) {
                              stateHolder.addManualTag(entry.id, tag)
                              tagDrafts = tagDrafts - entry.id
                            }
                          }
                          VisitEntryCardAction.AddPhotos -> addPhotosToVisit(entry.id)
                          VisitEntryCardAction.Archive -> stateHolder.archiveEntry(entry.id)
                          VisitEntryCardAction.Restore -> stateHolder.restoreEntry(entry.id)
                        }
                      },
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
                        stringResource(if (showArchive) Res.string.archived_visits_title else Res.string.visit_log_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                      )
                      TextButton(onClick = { showArchive = !showArchive }) {
                        Text(
                          if (showArchive) {
                            stringResource(Res.string.visit_log_count, entries.size)
                          } else {
                            stringResource(Res.string.action_archive_count, archived.size)
                          },
                        )
                      }
                    }
                    if (!showArchive) {
                      CompactLocationBar(
                        state = CompactLocationBarState(
                          selectedCafe = selectedCafe,
                          manualCafe = manualCafe,
                          locationMessage = locationMessage,
                          loadingLocation = loadingLocation,
                          cafes = cafes,
                          editing = editingLocation,
                          showAddPhotosAction = true,
                        ),
                        onAction = onLocationAction,
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
                      Text(
                        stringResource(
                          when {
                            shown.isEmpty() && showArchive -> Res.string.web_empty_archive
                            shown.isEmpty() -> Res.string.web_empty_log
                            else -> Res.string.web_empty_search
                          },
                        ),
                      )
                    }
                  }
                } else {
                  items(matching, key = { it.id }) { entry ->
                    VisitCard(
                      state = VisitEntryCardState(
                        entry = entry,
                        archived = showArchive,
                        swipeEnabled = true,
                        showArchiveButton = false,
                        tagging = entry.id in taggingIds,
                        tagDraft = tagDrafts[entry.id].orEmpty(),
                      ),
                      onAction = { action ->
                        when (action) {
                          VisitEntryCardAction.OpenDetails -> selectedDetailsEntryId = entry.id
                          is VisitEntryCardAction.ClickTag -> searchQuery = action.tag
                          is VisitEntryCardAction.ChangeTagDraft -> tagDrafts = tagDrafts + (entry.id to action.draft)
                          VisitEntryCardAction.AddTag -> {
                            val tag = tagDrafts[entry.id].orEmpty().trim()
                            if (tag.isNotEmpty()) {
                              stateHolder.addManualTag(entry.id, tag)
                              tagDrafts = tagDrafts - entry.id
                            }
                          }
                          VisitEntryCardAction.AddPhotos -> addPhotosToVisit(entry.id)
                          VisitEntryCardAction.Archive -> stateHolder.archiveEntry(entry.id)
                          VisitEntryCardAction.Restore -> stateHolder.restoreEntry(entry.id)
                        }
                      },
                    )
                  }
                }
              }
            }
          }
          storageWarning?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp)) }
          Text(stringResource(Res.string.web_storage_notice), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        if (detailsEntry != null) {
          val onDetailsAction: (VisitDetailsAction) -> Unit = { action ->
            when (action) {
              VisitDetailsAction.Close -> selectedDetailsEntryId = null
              VisitDetailsAction.RetryClassification -> stateHolder.retryClassification(detailsEntry.id)
            }
          }
          if (desktopLayout) {
            Dialog(onDismissRequest = { selectedDetailsEntryId = null }) {
              Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).heightIn(max = 820.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
              ) {
                VisitDetailsContent(
                  state = VisitDetailsState(
                    entry = detailsEntry,
                    currentModelVersion = BROWSER_FOOD_MODEL_VERSION,
                    tagging = detailsEntry.id in taggingIds,
                    showCloseButton = true,
                    closeLabel = stringResource(Res.string.action_close),
                  ),
                  onAction = onDetailsAction,
                  modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                )
              }
            }
          } else {
            VisitDetailsScreen(
              state = VisitDetailsState(
                entry = detailsEntry,
                currentModelVersion = BROWSER_FOOD_MODEL_VERSION,
                tagging = detailsEntry.id in taggingIds,
                showCloseButton = true,
                closeLabel = stringResource(Res.string.action_back_to_visits),
              ),
              onAction = onDetailsAction,
            )
          }
        }
      }
    }
  }
}
