package com.pratclot.cafes.data

import com.pratclot.cafes.core.model.AddressLookupFacade
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeLocationFacades
import com.pratclot.cafes.core.model.FoodLabel
import com.pratclot.cafes.core.model.FoodTaggerFacade
import com.pratclot.cafes.core.model.LocationFacade
import com.pratclot.cafes.core.model.NearbyCafeFacade
import com.pratclot.cafes.core.model.UserPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CafeLogStateHolderTest {
  @Test
  fun chooseUnambiguousCafe_respectsAccuracyDistanceAndSeparation() {
    val pos = UserPosition(latitude = 52.0, longitude = 4.0, accuracyMeters = 10f, observedAtMillis = 1L)
    val poorPos = pos.copy(accuracyMeters = 50f)
    val near = CafeCandidate("1", "Near Cafe", null, 52.0001, 4.0) // ~11m away
    val far = CafeCandidate("2", "Far Cafe", null, 52.001, 4.0) // ~111m away
    val closeSecond = CafeCandidate("3", "Close Second", null, 52.00015, 4.0) // ~16m away

    assertNull(chooseUnambiguousCafe(listOf(near), poorPos))
    assertEquals(near, chooseUnambiguousCafe(listOf(near, far), pos))
    assertNull(chooseUnambiguousCafe(listOf(near, closeSecond), pos))
    assertNull(chooseUnambiguousCafe(listOf(far), pos))
  }

  @Test
  fun saveCapturedPhoto_classifiesAndRecordsTags() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val repo = InMemoryCafeLogRepository()
    val facades = CafeLocationFacades(
      location = object : LocationFacade {
        override fun observeWhileInUse(): Flow<UserPosition> = MutableSharedFlow()
        override suspend fun refresh(): UserPosition? = null
      },
      address = object : AddressLookupFacade {
        override suspend fun describe(position: UserPosition): String? = null
      },
      cafes = object : NearbyCafeFacade {
        override suspend fun findNearby(position: UserPosition): List<CafeCandidate> = emptyList()
      },
    )
    val tagger = object : FoodTaggerFacade {
      override suspend fun classify(photoPaths: List<String>): List<FoodLabel> =
        listOf(FoodLabel("Espresso", 0.9f), FoodLabel("LowConf", 0.1f))
    }

    val holder = CafeLogStateHolder(
      scope = CoroutineScope(dispatcher + SupervisorJob()),
      repository = repo,
      facades = facades,
      foodTagger = tagger,
      modelVersion = "test-v1",
      ioDispatcher = dispatcher,
      clock = { 1234L },
    )
    advanceUntilIdle()

    holder.selectManualCafe("Blue Bottle")
    holder.saveCapturedPhoto(id = "e1", photoPath = "p1.jpg")
    advanceUntilIdle()

    val saved = holder.uiState.value.entries.single()
    assertEquals("Blue Bottle", saved.venue)
    assertEquals(listOf("Espresso"), saved.autoTags)
    assertEquals("test-v1", saved.taggingModelVersion)
    assertEquals("SUCCESS", saved.classificationRuns.single().outcome)
  }

  @Test
  fun classificationFailure_recordsErrorRunAndClearsTaggingId() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val repo = InMemoryCafeLogRepository()
    val facades = CafeLocationFacades(
      location = object : LocationFacade {
        override fun observeWhileInUse(): Flow<UserPosition> = MutableSharedFlow()
        override suspend fun refresh(): UserPosition? = null
      },
      address = object : AddressLookupFacade {
        override suspend fun describe(position: UserPosition): String? = null
      },
      cafes = object : NearbyCafeFacade {
        override suspend fun findNearby(position: UserPosition): List<CafeCandidate> = emptyList()
      },
    )
    val tagger = object : FoodTaggerFacade {
      override suspend fun classify(photoPaths: List<String>): List<FoodLabel> {
        throw IllegalStateException("model failed")
      }
    }

    val holder = CafeLogStateHolder(
      scope = CoroutineScope(dispatcher + SupervisorJob()),
      repository = repo,
      facades = facades,
      foodTagger = tagger,
      modelVersion = "test-v1",
      ioDispatcher = dispatcher,
      clock = { 2000L },
    )
    advanceUntilIdle()

    holder.saveCapturedPhoto(id = "e2", photoPath = "p2.jpg")
    advanceUntilIdle()

    val saved = holder.uiState.value.entries.single()
    assertEquals("ERROR", saved.classificationRuns.single().outcome)
    assertEquals("model failed", saved.classificationRuns.single().errorMessage)
    assertTrue(holder.uiState.value.taggingIds.isEmpty())
  }
}
