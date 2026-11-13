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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.ClassificationRun
import com.pratclot.cafes.core.model.FoodLabel
import com.pratclot.cafes.core.model.UserPosition
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val ACTIVE_KEY = "cafe-log-active"
private const val ARCHIVED_KEY = "cafe-log-archived"

@Composable
internal fun CafeWebApp() {
  val locationFacade = remember { BrowserGeolocationFacade() }
  val nearbyCafeFacade = remember { OpenStreetMapBrowserCafeFacade() }
  val foodTagger = remember { MediaPipeFoodTaggerFacade() }
  var entries by remember { mutableStateOf(readEntries(ACTIVE_KEY)) }
  var archived by remember { mutableStateOf(readEntries(ARCHIVED_KEY)) }
  var showArchive by remember { mutableStateOf(false) }
  var position by remember { mutableStateOf<UserPosition?>(null) }
  var cafes by remember { mutableStateOf<List<CafeCandidate>>(emptyList()) }
  var locationMessage by remember { mutableStateOf("Location has not been checked yet.") }
  var loadingLocation by remember { mutableStateOf(false) }
  var manualCafe by remember { mutableStateOf("") }
  var selectedCafe by remember { mutableStateOf<CafeCandidate?>(null) }
  var storageWarning by remember { mutableStateOf<String?>(null) }
  var searchQuery by remember { mutableStateOf("") }
  var taggingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var selectedDetailsEntryId by remember { mutableStateOf<String?>(null) }
  var tagDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

  fun persist() {
    val activeSaved = browserStorageWrite(ACTIVE_KEY, writeEntries(entries))
    val archivedSaved = browserStorageWrite(ARCHIVED_KEY, writeEntries(archived))
    storageWarning = if (activeSaved && archivedSaved) null else
      "Browser storage is full or unavailable. This change may not survive a page reload."
  }

  LaunchedEffect(Unit) {
    snapshotFlow { entries + archived }.collect { currentEntries ->
      currentEntries.filter { it.photoPaths.isNotEmpty() && it.taggingModelVersion != BROWSER_FOOD_MODEL_VERSION && it.id !in taggingIds }.forEach { entry ->
        taggingIds = taggingIds + entry.id
        val startedAt = browserNowMillis().toLong()
        var labels = emptyList<FoodLabel>()
        var errorMessage: String? = null
        try {
          labels = foodTagger.classify(entry.photoPaths)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          errorMessage = error.message ?: error.toString()
        }
        val completedAt = browserNowMillis().toLong()
        val run = ClassificationRun(
          occurredAt = completedAt,
          modelVersion = BROWSER_FOOD_MODEL_VERSION,
          photoCount = entry.photoPaths.size,
          durationMillis = completedAt - startedAt,
          outcome = when { errorMessage != null -> "ERROR"; labels.isEmpty() -> "NO_DETECTIONS"; else -> "SUCCESS" },
          labels = labels,
          errorMessage = errorMessage,
        )
        val latestEntry = (entries + archived).firstOrNull { it.id == entry.id } ?: entry
        val photosUnchanged = latestEntry.photoPaths == entry.photoPaths
        val updatedEntry = latestEntry.copy(
          autoTags = if (photosUnchanged && errorMessage == null) labels.filter { it.confidence >= 0.22f }.map { it.name } else latestEntry.autoTags,
          taggingModelVersion = BROWSER_FOOD_MODEL_VERSION.takeIf { photosUnchanged },
          classificationRuns = (listOf(run) + latestEntry.classificationRuns).take(50),
        )
        entries = entries.map { if (it.id == entry.id) updatedEntry else it }
        archived = archived.map { if (it.id == entry.id) updatedEntry else it }
        taggingIds = taggingIds - entry.id
        persist()
      }
    }
  }

  fun findLocation() {
    loadingLocation = true
    locationMessage = "Checking your location…"
    selectedCafe = null
    cafes = emptyList()
    locationFacade.currentPosition { latitude, longitude, accuracy, error ->
      loadingLocation = false
      if (error.isNotEmpty() || !latitude.isFinite() || !longitude.isFinite()) {
        locationMessage = error.ifEmpty { "Could not read a location from this browser." }
        position = null
        cafes = emptyList()
      } else {
        val result = UserPosition(latitude, longitude, accuracy.toFloat(), browserNowMillis().toLong())
        position = result
        locationMessage = "${latitude.format(5)}, ${longitude.format(5)} · ±${accuracy.toInt()} m"
        nearbyCafeFacade.nearby(latitude, longitude) { results, failed ->
          cafes = results
          selectedCafe = chooseCafe(cafes, result)
          locationMessage = when {
            failed -> "Location found, but nearby cafes could not be loaded."
            selectedCafe != null -> "${selectedCafe!!.name} · ${locationMessage}"
            cafes.isNotEmpty() -> "${cafes.size} nearby places need a quick choice."
            else -> "No nearby cafes found. You can enter the name manually."
          }
        }
      }
    }
  }

  fun addPhotosToVisit(id: String) {
    browserRequestPhotos { encoded ->
      val photos = decodePhotoSelection(encoded)
      if (photos.isNotEmpty()) {
        entries = entries.map { entry ->
          if (entry.id == id) entry.copy(photoPaths = (entry.photoPaths + photos).distinct(), autoTags = emptyList(), taggingModelVersion = null) else entry
        }
        archived = archived.map { entry ->
          if (entry.id == id) entry.copy(photoPaths = (entry.photoPaths + photos).distinct(), autoTags = emptyList(), taggingModelVersion = null) else entry
        }
        persist()
      }
    }
  }

  LaunchedEffect(Unit) { findLocation() }

  MaterialTheme {
    Surface(Modifier.fillMaxSize(), color = Color(0xFFFFF8F0)) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val desktopLayout = maxWidth >= 900.dp
        val detailsEntry = (entries + archived).firstOrNull { it.id == selectedDetailsEntryId }
        Column(
          Modifier.fillMaxSize()
            .widthIn(max = 1440.dp)
            .align(Alignment.TopCenter)
            .padding(horizontal = if (desktopLayout) 36.dp else 16.dp, vertical = if (desktopLayout) 28.dp else 16.dp),
        ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("Cafe moments", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("A little log of places you share", color = Color(0xFF6E6258))
          }
          TextButton(onClick = { showArchive = !showArchive }) {
            Text(if (showArchive) "Visit log" else "Archive (${archived.size})")
          }
        }
        Spacer(Modifier.height(14.dp))

        if (!showArchive) {
          Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Where you are", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Text(locationMessage, color = Color(0xFF5E554D))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::findLocation, enabled = !loadingLocation) {
                  Text(if (loadingLocation) "Locating…" else "Refresh location")
                }
                Button(onClick = {
                  browserRequestPhotos { encoded ->
                    val photos = decodePhotoSelection(encoded)
                    if (photos.isNotEmpty()) {
                      val cafe = selectedCafe ?: position?.let { chooseCafe(cafes, it) }
                      val manual = manualCafe.trim()
                      val timestamp = browserNowMillis().toLong()
                      val newEntry = CafeEntry(
                        id = timestamp.toString(),
                        photoPath = photos.first(),
                        venue = cafe?.name ?: manual.ifEmpty { "Cafe visit" },
                        address = cafe?.address.orEmpty(),
                        visitedAt = timestamp,
                        latitude = cafe?.latitude ?: position?.latitude,
                        longitude = cafe?.longitude ?: position?.longitude,
                        photoPaths = photos,
                      )
                      entries = listOf(newEntry) + entries
                      persist()
                    }
                  }
                }) { Text("Take / add photos") }
              }
              if (cafes.size > 1 || (cafes.isNotEmpty() && selectedCafe == null)) {
                Text("Choose the cafe for this visit", style = MaterialTheme.typography.labelLarge)
                cafes.take(5).forEach { cafe ->
                  TextButton(onClick = {
                    selectedCafe = cafe
                    locationMessage = "${cafe.name} selected"
                  }) {
                    Text("${if (selectedCafe?.id == cafe.id) "✓ " else ""}${cafe.name}${cafe.address?.let { " · $it" } ?: ""}")
                  }
                }
              }
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = manualCafe,
                  onValueChange = { manualCafe = it },
                  label = { Text("Cafe name (optional)") },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
                if (manualCafe.isNotBlank()) {
                  OutlinedButton(onClick = {
                    selectedCafe = null
                    cafes = emptyList()
                    locationMessage = "${manualCafe.trim()} will be used for your next visit."
                  }) { Text("Use name") }
                }
              }
            }
          }
          Spacer(Modifier.height(12.dp))
          Text("Recent visits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        } else {
          Text("Archived visits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        val shown = if (showArchive) archived else entries
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Search cafes and dishes") },
          placeholder = { Text("Try “Benedict”") },
          singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        val query = searchQuery.trim()
        val matching = shown.filter { entry ->
          query.isBlank() || entry.venue.contains(query, true) || entry.address.contains(query, true) || (entry.manualTags + entry.autoTags).any { it.contains(query, true) }
        }
        if (matching.isEmpty()) {
          Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(when { shown.isEmpty() && showArchive -> "Nothing archived yet."; shown.isEmpty() -> "Your cafe visits will show up here."; else -> "No matching visits." })
          }
        } else if (desktopLayout) {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            gridItems(matching, key = { it.id }) { entry ->
              VisitCard(
                entry = entry,
                archived = showArchive,
                showArchiveButton = desktopLayout,
                swipeEnabled = !desktopLayout,
                tagging = entry.id in taggingIds,
                tagDraft = tagDrafts[entry.id].orEmpty(),
                onDetails = { selectedDetailsEntryId = entry.id },
                onTagClick = { searchQuery = it },
                onTagDraft = { tagDrafts = tagDrafts + (entry.id to it) },
                onAddTag = {
                  val tag = tagDrafts[entry.id].orEmpty().trim()
                  if (tag.isNotEmpty()) {
                    val updated = entry.copy(manualTags = (entry.manualTags + tag).distinctBy(String::lowercase))
                    entries = entries.map { if (it.id == entry.id) updated else it }
                    archived = archived.map { if (it.id == entry.id) updated else it }
                    tagDrafts = tagDrafts - entry.id
                    persist()
                  }
                },
                onPhotos = { addPhotosToVisit(entry.id) },
                onArchive = {
                  entries = entries.filterNot { it.id == entry.id }
                  archived = listOf(entry) + archived
                  persist()
                },
                onRestore = {
                  archived = archived.filterNot { it.id == entry.id }
                  entries = listOf(entry) + entries
                  persist()
                },
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(matching, key = { it.id }) { entry ->
              VisitCard(
                entry = entry,
                archived = showArchive,
                showArchiveButton = desktopLayout,
                swipeEnabled = !desktopLayout,
                tagging = entry.id in taggingIds,
                tagDraft = tagDrafts[entry.id].orEmpty(),
                onDetails = { selectedDetailsEntryId = entry.id },
                onTagClick = { searchQuery = it },
                onTagDraft = { tagDrafts = tagDrafts + (entry.id to it) },
                onAddTag = {
                  val tag = tagDrafts[entry.id].orEmpty().trim()
                  if (tag.isNotEmpty()) {
                    val updated = entry.copy(manualTags = (entry.manualTags + tag).distinctBy(String::lowercase))
                    entries = entries.map { if (it.id == entry.id) updated else it }
                    archived = archived.map { if (it.id == entry.id) updated else it }
                    tagDrafts = tagDrafts - entry.id
                    persist()
                  }
                },
                onPhotos = { addPhotosToVisit(entry.id) },
                onArchive = {
                  entries = entries.filterNot { it.id == entry.id }
                  archived = listOf(entry) + archived
                  persist()
                },
                onRestore = {
                  archived = archived.filterNot { it.id == entry.id }
                  entries = listOf(entry) + entries
                  persist()
                },
              )
            }
          }
        }
        storageWarning?.let { Text(it, color = Color(0xFFB3261E), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp)) }
        Text("Saved in this browser · no account or device sync yet", style = MaterialTheme.typography.labelSmall, color = Color(0xFF81766D), modifier = Modifier.padding(top = 8.dp))
        }
        if (detailsEntry != null) {
          val onRetryClassification = {
            val updated = detailsEntry.copy(taggingModelVersion = null)
            entries = entries.map { if (it.id == detailsEntry.id) updated else it }
            archived = archived.map { if (it.id == detailsEntry.id) updated else it }
            persist()
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
          SelectionContainer {
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
  val latitude = entry.latitude
  val longitude = entry.longitude
  SelectionContainer {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onClose) { Text(if (fullScreen) "← Back to visits" else "Close") }
        Spacer(Modifier.weight(1f))
        Text("${entry.photoPaths.size} photo${if (entry.photoPaths.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium)
      }
      Text(entry.venue, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      if (entry.address.isNotBlank()) Text(entry.address, color = Color(0xFF6E6258))
      Text("Visited ${browserFormatDate(entry.visitedAt.toDouble())}", color = Color(0xFF6E6258), style = MaterialTheme.typography.bodySmall)
      if (latitude != null && longitude != null) {
        Text("Location: ${latitude.format(5)}, ${longitude.format(5)}", color = Color(0xFF6E6258), style = MaterialTheme.typography.bodySmall)
      }
      val tags = (entry.autoTags + entry.manualTags).distinctBy(String::lowercase)
      if (tags.isNotEmpty()) {
        Text("Tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(tags.joinToString(" · "), color = Color(0xFF8D5E3C))
      }
      androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
      Text("Automatic classification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text("Current model: $BROWSER_FOOD_MODEL_VERSION", color = Color(0xFF6E6258), style = MaterialTheme.typography.bodySmall)
      if (tagging) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
          Text("Classification running…", color = Color(0xFF6E6258), style = MaterialTheme.typography.bodySmall)
        }
      }
      Text("Run history (${entry.classificationRuns.size})", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
      if (entry.classificationRuns.isEmpty()) {
        Text(
          if (entry.taggingModelVersion == null) "No classification runs recorded yet."
          else "This visit was classified before run history was saved.",
          color = Color(0xFF6E6258), style = MaterialTheme.typography.bodySmall,
        )
      } else {
        entry.classificationRuns.forEach { run ->
          val summary = when (run.outcome) {
            "ERROR" -> "Processing error"
            "NO_DETECTIONS" -> "Nothing detected"
            else -> "Detected ${run.labels.size} label${if (run.labels.size == 1) "" else "s"}"
          }
          Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(browserFormatDate(run.occurredAt.toDouble()), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            Text(
              "$summary · ${run.photoCount} photo${if (run.photoCount == 1) "" else "s"} · ${run.durationMillis} ms · ${run.modelVersion}",
              color = if (run.outcome == "ERROR") Color(0xFFB3261E) else Color(0xFF6E6258),
              style = MaterialTheme.typography.bodySmall,
            )
            if (run.labels.isNotEmpty()) {
              Text(run.labels.joinToString(" · ") { "${it.name} (${(it.confidence * 100).toInt()}%)" }, color = Color(0xFF8D5E3C), style = MaterialTheme.typography.bodySmall)
            }
            run.errorMessage?.let { Text(it, color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall) }
          }
        }
      }
      OutlinedButton(onClick = onRetryClassification, enabled = !tagging, modifier = Modifier.padding(top = 8.dp)) {
        Text(if (tagging) "Classification running…" else "Run classification again")
      }
    }
  }
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

private fun readEntries(key: String): List<CafeEntry> =
  browserStorageRead(key)?.let(::parseEntries).orEmpty()

private fun parseEntries(data: String): List<CafeEntry> = data.lineSequence().mapNotNull { line ->
  val fields = line.split('|')
  if (fields.size < 8) return@mapNotNull null
  runCatching {
    val photos = fields[7].takeIf(String::isNotEmpty)?.split(',').orEmpty().map(::decodeBrowserValue)
    val primary = decodeBrowserValue(fields[1])
    CafeEntry(
      id = decodeBrowserValue(fields[0]),
      photoPath = primary,
      venue = decodeBrowserValue(fields[2]),
      address = decodeBrowserValue(fields[3]),
      visitedAt = decodeBrowserValue(fields[4]).toLong(),
      latitude = decodeBrowserValue(fields[5]).toDoubleOrNull(),
      longitude = decodeBrowserValue(fields[6]).toDoubleOrNull(),
      photoPaths = photos.ifEmpty { listOf(primary) },
      manualTags = fields.getOrNull(8)?.let(::decodeBrowserValue)?.split(',')?.filter(String::isNotBlank).orEmpty(),
      autoTags = fields.getOrNull(9)?.let(::decodeBrowserValue)?.split(',')?.filter(String::isNotBlank).orEmpty(),
      taggingModelVersion = fields.getOrNull(10)?.let(::decodeBrowserValue)?.takeIf(String::isNotBlank),
      classificationRuns = parseClassificationRuns(fields.getOrNull(11)),
    )
  }.getOrNull()
}.toList()

private fun writeEntries(entries: List<CafeEntry>): String = entries.joinToString("\n") { entry ->
  listOf(
    entry.id, entry.photoPath, entry.venue, entry.address, entry.visitedAt.toString(),
    entry.latitude?.toString().orEmpty(), entry.longitude?.toString().orEmpty(), entry.photoPaths.joinToString(","),
    entry.manualTags.joinToString(","), entry.autoTags.joinToString(","), entry.taggingModelVersion.orEmpty(),
    serializeClassificationRuns(entry.classificationRuns),
  ).joinToString("|") { encodeBrowserValue(it) }
}

private fun serializeClassificationRuns(runs: List<ClassificationRun>): String = runs.joinToString("\u001e") { run ->
  listOf(
    run.occurredAt.toString(), run.modelVersion, run.photoCount.toString(), run.durationMillis.toString(), run.outcome,
    run.labels.joinToString("\u001d") { "${encodeBrowserValue(it.name)}\u001c${it.confidence}" },
    run.errorMessage.orEmpty(),
  ).joinToString("\u001f") { encodeBrowserValue(it) }
}

private fun parseClassificationRuns(encoded: String?): List<ClassificationRun> {
  val data = encoded?.let(::decodeBrowserValue).orEmpty()
  if (data.isEmpty()) return emptyList()
  return data.split("\u001e").mapNotNull { rawRun ->
    runCatching {
      val fields = rawRun.split("\u001f").map(::decodeBrowserValue)
      val labels = fields.getOrNull(5).orEmpty().split("\u001d").filter(String::isNotBlank).mapNotNull { rawLabel ->
        val tagFields = rawLabel.split("\u001c")
        val name = tagFields.firstOrNull()?.let(::decodeBrowserValue).orEmpty()
        val confidence = tagFields.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        FoodLabel(name, confidence)
      }
      ClassificationRun(
        occurredAt = fields[0].toLong(),
        modelVersion = fields[1],
        photoCount = fields[2].toInt(),
        durationMillis = fields[3].toLong(),
        outcome = fields[4],
        labels = labels,
        errorMessage = fields.getOrNull(6)?.takeIf(String::isNotBlank),
      )
    }.getOrNull()
  }
}

private fun decodePhotoSelection(value: String): List<String> = value.lineSequence()
  .filter(String::isNotBlank)
  .map(::decodeBrowserValue)
  .toList()

private fun chooseCafe(cafes: List<CafeCandidate>, position: UserPosition): CafeCandidate? {
  if (!position.accuracyMeters.isFinite() || position.accuracyMeters > 35f) return null
  val ranked = cafes.mapNotNull { candidate -> candidate.distanceFrom(position)?.let { candidate to it } }
    .sortedBy { it.second }
  val first = ranked.firstOrNull() ?: return null
  val maxDistance = maxOf(25f, position.accuracyMeters * 2f)
  if (first.second > maxDistance) return null
  val second = ranked.getOrNull(1)
  val requiredGap = maxOf(20f, position.accuracyMeters * 1.5f)
  return if (second == null || second.second - first.second >= requiredGap) first.first else null
}

private fun Double.format(digits: Int): String = toString().let { value ->
  val dot = value.indexOf('.')
  if (dot < 0) value else value.take(dot + digits + 1)
}
