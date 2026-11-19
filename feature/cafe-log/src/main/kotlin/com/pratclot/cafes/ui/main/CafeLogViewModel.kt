package com.pratclot.cafes.ui.main

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.CafeLocationFacades
import com.pratclot.cafes.core.model.FoodTaggerFacade
import com.pratclot.cafes.data.CafeLogRepository
import com.pratclot.cafes.data.CafeLogStateHolder
import com.pratclot.cafes.data.CafeLogUiState
import com.pratclot.cafes.data.LocalCafeLogRepository
import com.pratclot.cafes.location.create
import com.pratclot.cafes.recognition.AndroidFoodTaggerFacade
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CafeLogViewModel(
  repository: CafeLogRepository,
  facades: CafeLocationFacades,
  foodTagger: FoodTaggerFacade,
  modelVersion: String = AndroidFoodTaggerFacade.MODEL_VERSION,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

  private val stateHolder = CafeLogStateHolder(
    scope = viewModelScope,
    repository = repository,
    facades = facades,
    foodTagger = foodTagger,
    modelVersion = modelVersion,
    ioDispatcher = ioDispatcher,
    clock = clock,
  )

  val uiState: StateFlow<CafeLogUiState> = stateHolder.uiState

  fun startLocationUpdates(hasPermission: Boolean) = stateHolder.startLocationUpdates(hasPermission)

  fun refreshLocation() = stateHolder.refreshLocation()

  fun selectCafe(cafe: CafeCandidate) = stateHolder.selectCafe(cafe)

  fun selectManualCafe(name: String) = stateHolder.selectManualCafe(name)

  fun saveCapturedBitmap(bitmap: Bitmap, filesDir: File) {
    viewModelScope.launch {
      val now = clock()
      val file = File(filesDir, "cafe-$now.jpg")
      withContext(ioDispatcher) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
      }
      stateHolder.saveCapturedPhoto(
        id = file.nameWithoutExtension,
        photoPath = file.absolutePath,
        visitedAt = now,
      )
    }
  }

  fun savePickedPhotos(entryId: String?, savedPaths: List<String>) =
    stateHolder.savePickedPhotos(entryId, savedPaths)

  fun addManualTag(entryId: String, rawTag: String) =
    stateHolder.addManualTag(entryId, rawTag)

  fun archiveEntry(entryId: String) = stateHolder.archiveEntry(entryId)

  fun restoreEntry(entryId: String) = stateHolder.restoreEntry(entryId)

  fun retryClassification(entryId: String) = stateHolder.retryClassification(entryId)

  override fun onCleared() {
    super.onCleared()
    stateHolder.close()
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
