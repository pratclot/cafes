package com.pratclot.cafes.data

import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.CafeLocationFacades
import com.pratclot.cafes.core.model.CafeLog
import com.pratclot.cafes.core.model.ClassificationRun
import com.pratclot.cafes.core.model.FoodLabel
import com.pratclot.cafes.core.model.FoodTaggerFacade
import com.pratclot.cafes.core.model.UserPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class CafeLogUiState(
  val entries: List<CafeEntry> = emptyList(),
  val archivedEntries: List<CafeEntry> = emptyList(),
  val currentPosition: UserPosition? = null,
  val currentAddress: String? = null,
  val selectedCafe: CafeCandidate? = null,
  val nearbyCafes: List<CafeCandidate> = emptyList(),
  val searchingCafes: Boolean = false,
  val cafeSearchError: String? = null,
  val locationUnavailable: Boolean = false,
  val refreshingLocation: Boolean = false,
  val refreshMessage: String? = null,
  val taggingIds: Set<String> = emptySet(),
  val lastCapturedEntryId: String? = null,
  val showLocationHint: Boolean = false,
  val modelVersion: String = "",
)

/** Shared multiplatform state holder for cafe logging, location/venue discovery, and food tagging. */
class CafeLogStateHolder(
  private val scope: CoroutineScope,
  private val repository: CafeLogRepository,
  private val facades: CafeLocationFacades,
  private val foodTagger: FoodTaggerFacade,
  private val modelVersion: String,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val clock: () -> Long,
) {
  private val _uiState = MutableStateFlow(CafeLogUiState(modelVersion = modelVersion))
  val uiState: StateFlow<CafeLogUiState> = _uiState.asStateFlow()

  private var locationJob: Job? = null
  private var locationTimeoutJob: Job? = null
  private var cafeSearchAnchor: UserPosition? = null
  private var lastAddressPosition: UserPosition? = null
  private var lastAddressUpdate: Long = 0L

  init {
    scope.launch {
      val (loaded, loadedArchived) = withContext(ioDispatcher) {
        repository.load() to repository.loadArchived()
      }
      _uiState.update { it.copy(entries = loaded, archivedEntries = loadedArchived) }
      schedulePendingClassifications(loaded + loadedArchived)
    }
  }

  fun startLocationUpdates(hasPermission: Boolean) {
    if (!hasPermission) {
      locationJob?.cancel()
      locationTimeoutJob?.cancel()
      locationJob = null
      locationTimeoutJob = null
      _uiState.update { it.copy(locationUnavailable = false) }
      return
    }
    if (locationJob?.isActive == true) return

    locationTimeoutJob?.cancel()
    locationTimeoutJob = scope.launch {
      delay(20_000L)
      if (_uiState.value.currentPosition == null) {
        _uiState.update { it.copy(locationUnavailable = true) }
      }
    }

    locationJob = scope.launch {
      launch {
        try {
          val initial = facades.location.refresh()
          if (initial != null) {
            onNewPosition(initial)
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          // Live updates can still provide a position if a one-shot fix is unavailable.
          CafeLog.w("CafeLogStateHolder", "Initial location refresh failed", error)
        }
      }
      facades.location.observeWhileInUse().collect { position ->
        onNewPosition(position)
      }
    }
  }

  fun refreshLocation() {
    if (_uiState.value.refreshingLocation) return
    scope.launch {
      _uiState.update { it.copy(refreshingLocation = true, refreshMessage = null) }
      try {
        val latest = withTimeout(20_000L) { facades.location.refresh() }
        if (latest == null) {
          _uiState.update {
            it.copy(refreshMessage = "No fresh location yet. Check that device location is on.")
          }
        } else {
          onNewPosition(latest)
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        CafeLog.w("CafeLogStateHolder", "Manual location refresh failed", error)
        _uiState.update {
          it.copy(refreshMessage = "Couldn’t refresh location. Try again in a moment.")
        }
      } finally {
        _uiState.update { it.copy(refreshingLocation = false) }
      }
    }
  }

  private suspend fun onNewPosition(position: UserPosition) {
    locationTimeoutJob?.cancel()
    _uiState.update {
      it.copy(
        currentPosition = position,
        locationUnavailable = false,
        refreshMessage = null,
      )
    }
    val now = clock()
    val addressMoved = lastAddressPosition?.distanceTo(position.latitude, position.longitude)?.let { it > 100f } ?: true
    if (addressMoved || now - lastAddressUpdate > 120_000L) {
      lastAddressPosition = position
      lastAddressUpdate = now
      val address = try {
        facades.address.describe(position)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        CafeLog.w("CafeLogStateHolder", "Address lookup failed", error)
        null
      }
      _uiState.update { it.copy(currentAddress = address) }
    }

    val previousAnchor = cafeSearchAnchor
    val moved = previousAnchor?.distanceTo(position.latitude, position.longitude)
    if (previousAnchor == null || (moved != null && moved > 100f)) {
      if (previousAnchor != null) {
        _uiState.update { it.copy(selectedCafe = null) }
      }
      cafeSearchAnchor = position
      searchNearbyCafes(position)
    }
  }

  private fun searchNearbyCafes(position: UserPosition) {
    scope.launch {
      _uiState.update { it.copy(searchingCafes = true, cafeSearchError = null) }
      try {
        val matches = facades.cafes.findNearby(position)
        if (cafeSearchAnchor == position) {
          _uiState.update { state ->
            state.copy(
              nearbyCafes = matches,
              selectedCafe = state.selectedCafe ?: chooseUnambiguousCafe(matches, position),
            )
          }
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        CafeLog.w("CafeLogStateHolder", "Nearby cafe search failed", error)
        if (cafeSearchAnchor == position) {
          _uiState.update {
            it.copy(
              cafeSearchError = "Cafe search is unavailable right now. You can still enter the cafe name.",
              nearbyCafes = emptyList(),
            )
          }
        }
      } finally {
        if (cafeSearchAnchor == position) {
          _uiState.update { it.copy(searchingCafes = false) }
        }
      }
    }
  }

  fun selectCafe(cafe: CafeCandidate) {
    _uiState.update { it.copy(selectedCafe = cafe) }
  }

  fun selectManualCafe(name: String) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    val state = _uiState.value
    val position = state.currentPosition
    _uiState.update {
      it.copy(
        selectedCafe = CafeCandidate(
          id = "manual:${clock()}",
          name = trimmed,
          address = state.currentAddress,
          latitude = position?.latitude,
          longitude = position?.longitude,
        ),
      )
    }
  }

  suspend fun saveCapturedPhoto(id: String, photoPath: String, visitedAt: Long = clock()) {
    val state = _uiState.value
    val position = state.currentPosition
    val entry = buildNewEntry(id = id, photoPaths = listOf(photoPath), visitedAt = visitedAt, state = state)
    val updatedEntries = withContext(ioDispatcher) { repository.add(entry) }
    _uiState.update {
      it.copy(
        entries = updatedEntries,
        lastCapturedEntryId = entry.id,
        showLocationHint = position == null,
      )
    }
    schedulePendingClassifications(updatedEntries)
  }

  fun savePickedPhotos(entryId: String?, savedPaths: List<String>) {
    if (savedPaths.isEmpty()) return
    scope.launch {
      if (entryId != null) {
        val (active, archived) = withContext(ioDispatcher) {
          repository.appendPhotos(entryId, savedPaths)
        }
        _uiState.update { it.copy(entries = active, archivedEntries = archived) }
        schedulePendingClassifications(active + archived)
      } else {
        val state = _uiState.value
        val position = state.currentPosition
        val now = clock()
        val entry = buildNewEntry(id = "cafe-$now", photoPaths = savedPaths, visitedAt = now, state = state)
        val updatedEntries = withContext(ioDispatcher) { repository.add(entry) }
        _uiState.update {
          it.copy(
            entries = updatedEntries,
            lastCapturedEntryId = entry.id,
            showLocationHint = position == null,
          )
        }
        schedulePendingClassifications(updatedEntries)
      }
    }
  }

  fun addManualTag(entry: CafeEntry, rawTag: String) {
    val tag = rawTag.trim()
    if (tag.isEmpty()) return
    scope.launch {
      val updatedEntry = entry.copy(
        manualTags = (entry.manualTags + tag).distinctBy(String::lowercase),
      )
      val (active, archived) = withContext(ioDispatcher) { repository.update(updatedEntry) }
      _uiState.update { it.copy(entries = active, archivedEntries = archived) }
    }
  }

  fun archiveEntry(entry: CafeEntry) {
    scope.launch {
      val (active, archived) = withContext(ioDispatcher) { repository.archive(entry) }
      _uiState.update { it.copy(entries = active, archivedEntries = archived) }
    }
  }

  fun restoreEntry(entry: CafeEntry) {
    scope.launch {
      val (active, archived) = withContext(ioDispatcher) { repository.restore(entry) }
      _uiState.update { it.copy(entries = active, archivedEntries = archived) }
    }
  }

  fun retryClassification(entry: CafeEntry) {
    scope.launch {
      val reset = entry.copy(taggingModelVersion = null)
      val (active, archived) = withContext(ioDispatcher) { repository.update(reset) }
      _uiState.update { it.copy(entries = active, archivedEntries = archived) }
      schedulePendingClassifications(listOf(reset))
    }
  }

  private fun buildNewEntry(
    id: String,
    photoPaths: List<String>,
    visitedAt: Long,
    state: CafeLogUiState,
  ): CafeEntry {
    val position = state.currentPosition
    return CafeEntry(
      id = id,
      photoPath = photoPaths.first(),
      venue = state.selectedCafe?.name ?: "Cafe visit",
      address = state.selectedCafe?.address
        ?: state.currentAddress
        ?: if (position == null) "Location not available" else "Address unavailable",
      visitedAt = visitedAt,
      latitude = position?.latitude,
      longitude = position?.longitude,
      photoPaths = photoPaths,
    )
  }

  private fun schedulePendingClassifications(candidates: List<CafeEntry>) {
    val activeTagging = _uiState.value.taggingIds
    val pending = candidates.filter {
      it.photoPaths.isNotEmpty() &&
        it.taggingModelVersion != modelVersion &&
        it.id !in activeTagging
    }
    pending.forEach { entry ->
      _uiState.update { it.copy(taggingIds = it.taggingIds + entry.id) }
      scope.launch {
        val startedAt = clock()
        var labels = emptyList<FoodLabel>()
        var errorMessage: String? = null
        try {
          labels = foodTagger.classify(entry.photoPaths)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          CafeLog.w("CafeLogStateHolder", "Food classification failed for entry ${entry.id}", error)
          errorMessage = error.message ?: error::class.simpleName ?: "Unknown classification error"
        }
        val completedAt = clock()
        val run = ClassificationRun(
          occurredAt = completedAt,
          modelVersion = modelVersion,
          photoCount = entry.photoPaths.size,
          durationMillis = completedAt - startedAt,
          outcome = when {
            errorMessage != null -> "ERROR"
            labels.isEmpty() -> "NO_DETECTIONS"
            else -> "SUCCESS"
          },
          labels = labels,
          errorMessage = errorMessage,
        )
        val latest = (_uiState.value.entries + _uiState.value.archivedEntries)
          .firstOrNull { it.id == entry.id } ?: entry
        val photosUnchanged = latest.photoPaths == entry.photoPaths
        val updatedEntry = latest.copy(
          autoTags = if (photosUnchanged && errorMessage == null) {
            labels.filter { it.confidence >= 0.22f }.map { it.name }
          } else {
            latest.autoTags
          },
          taggingModelVersion = modelVersion.takeIf { photosUnchanged },
          classificationRuns = (listOf(run) + latest.classificationRuns).take(50),
        )
        val (active, archived) = withContext(ioDispatcher) { repository.update(updatedEntry) }
        _uiState.update {
          it.copy(
            entries = active,
            archivedEntries = archived,
            taggingIds = it.taggingIds - entry.id,
          )
        }
      }
    }
  }

  fun close() {
    (foodTagger as? AutoCloseable)?.close()
  }
}

fun chooseUnambiguousCafe(candidates: List<CafeCandidate>, position: UserPosition): CafeCandidate? {
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
