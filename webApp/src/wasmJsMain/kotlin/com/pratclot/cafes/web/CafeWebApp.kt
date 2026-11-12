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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
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
  var failedTaggingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var tagDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

  fun persist() {
    val activeSaved = browserStorageWrite(ACTIVE_KEY, writeEntries(entries))
    val archivedSaved = browserStorageWrite(ARCHIVED_KEY, writeEntries(archived))
    storageWarning = if (activeSaved && archivedSaved) null else
      "Browser storage is full or unavailable. This change may not survive a page reload."
  }

  LaunchedEffect(entries, archived) {
    (entries + archived).filter { it.photoPaths.isNotEmpty() && it.taggingModelVersion != BROWSER_FOOD_MODEL_VERSION && it.id !in taggingIds && it.id !in failedTaggingIds }.forEach { entry ->
      taggingIds = taggingIds + entry.id
      try {
        val tags = foodTagger.classify(entry.photoPaths).filter { it.confidence >= 0.22f }.map { it.name }
        entries = entries.map { if (it.id == entry.id) it.copy(autoTags = tags, taggingModelVersion = BROWSER_FOOD_MODEL_VERSION) else it }
        archived = archived.map { if (it.id == entry.id) it.copy(autoTags = tags, taggingModelVersion = BROWSER_FOOD_MODEL_VERSION) else it }
        persist()
      } catch (_: Exception) {
        failedTaggingIds = failedTaggingIds + entry.id
      } finally {
        taggingIds = taggingIds - entry.id
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
        failedTaggingIds = failedTaggingIds - id
        persist()
      }
    }
  }

  LaunchedEffect(Unit) { findLocation() }

  MaterialTheme {
    Surface(Modifier.fillMaxSize(), color = Color(0xFFFFF8F0)) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val desktopLayout = maxWidth >= 900.dp
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
                tagging = entry.id in taggingIds,
                tagDraft = tagDrafts[entry.id].orEmpty(),
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
                tagging = entry.id in taggingIds,
                tagDraft = tagDrafts[entry.id].orEmpty(),
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
      }
    }
  }
}

@Composable
private fun VisitCard(
  entry: CafeEntry,
  archived: Boolean,
  showArchiveButton: Boolean,
  tagging: Boolean,
  tagDraft: String,
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
        .offset { IntOffset(offset.roundToInt(), 0) }
        .pointerInput(entry.id, archived, cardWidthPx) {
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
        },
    ) {
      Box {
        Column {
          Crossfade(
            targetState = photos[photoIndex % photos.size],
            animationSpec = tween(700),
            label = "visit-photo",
          ) { photo ->
            BrowserPhoto(photo, Modifier.fillMaxWidth().height(220.dp))
          }
          Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(entry.venue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (entry.address.isNotBlank()) Text(entry.address, color = Color(0xFF6E6258))
                Text(browserFormatDate(entry.visitedAt.toDouble()), style = MaterialTheme.typography.labelMedium, color = Color(0xFF81766D))
              }
              Text("${photoIndex + 1}/${photos.size}", style = MaterialTheme.typography.labelMedium)
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
    )
  }.getOrNull()
}.toList()

private fun writeEntries(entries: List<CafeEntry>): String = entries.joinToString("\n") { entry ->
  listOf(
    entry.id, entry.photoPath, entry.venue, entry.address, entry.visitedAt.toString(),
    entry.latitude?.toString().orEmpty(), entry.longitude?.toString().orEmpty(), entry.photoPaths.joinToString(","),
    entry.manualTags.joinToString(","), entry.autoTags.joinToString(","), entry.taggingModelVersion.orEmpty(),
  ).joinToString("|") { encodeBrowserValue(it) }
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
