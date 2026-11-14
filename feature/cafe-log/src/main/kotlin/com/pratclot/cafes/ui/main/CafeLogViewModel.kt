package com.pratclot.cafes.ui.main

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.ClassificationRun
import com.pratclot.cafes.core.model.FoodLabel
import com.pratclot.cafes.core.model.FoodTaggerFacade
import com.pratclot.cafes.core.model.UserPosition
import com.pratclot.cafes.data.CafeLogRepository
import com.pratclot.cafes.data.LocalCafeLogRepository
import com.pratclot.cafes.location.CafeLocationFacades
import com.pratclot.cafes.recognition.AndroidFoodTaggerFacade
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
  val modelVersion: String = AndroidFoodTaggerFacade.MODEL_VERSION,
)

class CafeLogViewModel(
  private val repository: CafeLogRepository,
  private val facades: CafeLocationFacades,
  private val foodTagger: FoodTaggerFacade,
  private val modelVersion: String = AndroidFoodTaggerFacade.MODEL_VERSION,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

  private val _uiState = MutableStateFlow(CafeLogUiState(modelVersion = modelVersion))
  val uiState: StateFlow<CafeLogUiState> = _uiState.asStateFlow()

  private var locationJob: Job? = null
  private var locationTimeoutJob: Job? = null
  private var cafeSearchAnchor: UserPosition? = null
  private var lastAddressPosition: UserPosition? = null
  private var lastAddressUpdate: Long = 0L

  init {
    viewModelScope.launch {
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
    locationTimeoutJob = viewModelScope.launch {
      delay(20_000L)
      if (_uiState.value.currentPosition == null) {
        _uiState.update { it.copy(locationUnavailable = true) }
      }
    }

    locationJob = viewModelScope.launch {
      launch {
        try {
          val initial = facades.location.refresh()
          if (initial != null) {
            onNewPosition(initial)
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Exception) {
          // Live updates can still provide a position if a one-shot fix is unavailable.
        }
      }
      facades.location.observeWhileInUse().collect { position ->
        onNewPosition(position)
      }
    }
  }

  fun refreshLocation() {
    if (_uiState.value.refreshingLocation) return
    viewModelScope.launch {
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
      } catch (_: Exception) {
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
      val address = runCatching { facades.address.describe(position) }.getOrNull()
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
    viewModelScope.launch {
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
      } catch (_: Exception) {
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

  fun saveCapturedBitmap(bitmap: Bitmap, filesDir: File) {
    viewModelScope.launch {
      val now = clock()
      val file = File(filesDir, "cafe-$now.jpg")
      withContext(ioDispatcher) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
      }
      val state = _uiState.value
      val position = state.currentPosition
      val entry = CafeEntry(
        id = file.nameWithoutExtension,
        photoPath = file.absolutePath,
        venue = state.selectedCafe?.name ?: "Cafe visit",
        address = state.selectedCafe?.address
          ?: state.currentAddress
          ?: if (position == null) "Location not available" else "Address unavailable",
        visitedAt = now,
        latitude = position?.latitude,
        longitude = position?.longitude,
      )
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

  fun savePickedPhotos(entryId: String?, savedPaths: List<String>) {
    if (savedPaths.isEmpty()) return
    viewModelScope.launch {
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
        val entry = CafeEntry(
          id = "cafe-$now",
          photoPath = savedPaths.first(),
          venue = state.selectedCafe?.name ?: "Cafe visit",
          address = state.selectedCafe?.address
            ?: state.currentAddress
            ?: if (position == null) "Location not available" else "Address unavailable",
          visitedAt = now,
          latitude = position?.latitude,
          longitude = position?.longitude,
          photoPaths = savedPaths,
        )
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
    viewModelScope.launch {
      val updatedEntry = entry.copy(
        manualTags = (entry.manualTags + tag).distinctBy(String::lowercase),
      )
      val (active, archived) = withContext(ioDispatcher) { repository.update(updatedEntry) }
      _uiState.update { it.copy(entries = active, archivedEntries = archived) }
    }
  }

  fun archiveEntry(entry: CafeEntry) {
    viewModelScope.launch {
      val (active, archived) = withContext(ioDispatcher) { repository.archive(entry) }
      _uiState.update { it.copy(entries = active, archivedEntries = archived) }
    }
  }

  fun restoreEntry(entry: CafeEntry) {
    viewModelScope.launch {
      val (active, archived) = withContext(ioDispatcher) { repository.restore(entry) }
      _uiState.update { it.copy(entries = active, archivedEntries = archived) }
    }
  }

  fun retryClassification(entry: CafeEntry) {
    viewModelScope.launch {
      val reset = entry.copy(taggingModelVersion = null)
      val (active, archived) = withContext(ioDispatcher) { repository.update(reset) }
      _uiState.update { it.copy(entries = active, archivedEntries = archived) }
      schedulePendingClassifications(listOf(reset))
    }
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
      viewModelScope.launch {
        val startedAt = clock()
        var labels = emptyList<FoodLabel>()
        var errorMessage: String? = null
        try {
          labels = foodTagger.classify(entry.photoPaths)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
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

  override fun onCleared() {
    super.onCleared()
    (foodTagger as? AutoCloseable)?.close()
  }

  companion object {
    fun factory(context: Context): ViewModelProvider.Factory {
      val appContext = context.applicationContext
      return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return CafeLogViewModel(
            repository = LocalCafeLogRepository(appContext),
            facades = CafeLocationFacades.create(appContext),
            foodTagger = AndroidFoodTaggerFacade(appContext),
          ) as T
        }
      }
    }
  }
}

internal fun chooseUnambiguousCafe(candidates: List<CafeCandidate>, position: UserPosition): CafeCandidate? {
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
