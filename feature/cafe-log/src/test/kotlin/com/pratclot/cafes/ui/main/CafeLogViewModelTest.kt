package com.pratclot.cafes.ui.main

import com.google.common.truth.Truth.assertThat
import com.pratclot.cafes.core.model.AddressLookupFacade
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.CafeLocationFacades
import com.pratclot.cafes.core.model.FoodLabel
import com.pratclot.cafes.core.model.FoodTaggerFacade
import com.pratclot.cafes.core.model.LocationFacade
import com.pratclot.cafes.core.model.NearbyCafeFacade
import com.pratclot.cafes.core.model.UserPosition
import com.pratclot.cafes.data.CafeLogRepository
import com.pratclot.cafes.data.chooseUnambiguousCafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CafeLogViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(
    repository: FakeCafeLogRepository = FakeCafeLogRepository(),
    foodTagger: FoodTaggerFacade = FakeFoodTaggerFacade(listOf(FoodLabel("Galette", 0.98f), FoodLabel("Egg", 0.95f))),
    cafes: List<CafeCandidate> = emptyList(),
  ): CafeLogViewModel {
    Dispatchers.setMain(testDispatcher)
    return CafeLogViewModel(
      repository = repository,
      facades = CafeLocationFacades(
        location = FakeLocationFacade(null),
        address = FakeAddressLookupFacade("Rue de Rennes, Paris"),
        cafes = FakeNearbyCafeFacade(cafes),
      ),
      foodTagger = foodTagger,
      modelVersion = "test-model-v1",
      ioDispatcher = testDispatcher,
      clock = { 1_700_000_000_000L },
    )
  }

  @Test
  fun savePickedPhotos_persistsEntryAndRunsFoodClassification() = runTest(testDispatcher) {
    val repo = FakeCafeLogRepository()
    val viewModel = createViewModel(repository = repo)
    advanceUntilIdle()

    viewModel.selectManualCafe("Crêperie Josselin")
    viewModel.savePickedPhotos(entryId = null, savedPaths = listOf("/tmp/galette.jpg"))
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertThat(state.entries).hasSize(1)
    val entry = state.entries.single()
    assertThat(entry.venue).isEqualTo("Crêperie Josselin")
    assertThat(entry.autoTags).containsExactly("Galette", "Egg").inOrder()
    assertThat(entry.taggingModelVersion).isEqualTo("test-model-v1")
    assertThat(entry.classificationRuns).hasSize(1)
    assertThat(entry.classificationRuns.single().outcome).isEqualTo("SUCCESS")
    assertThat(state.lastCapturedEntryId).isEqualTo(entry.id)
  }

  @Test
  fun addManualTag_deduplicatesCaseInsensitively() = runTest(testDispatcher) {
    val initial = CafeEntry(
      id = "visit-1",
      photoPath = "/tmp/p1.jpg",
      venue = "Cafe Flore",
      address = "Paris",
      visitedAt = 1_000L,
      latitude = null,
      longitude = null,
      manualTags = listOf("Matcha"),
      taggingModelVersion = "test-model-v1",
    )
    val repo = FakeCafeLogRepository(initialEntries = listOf(initial))
    val viewModel = createViewModel(repository = repo)
    advanceUntilIdle()

    viewModel.addManualTag(initial.id, "matcha")
    viewModel.addManualTag(initial.id, "Galette")
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.entries.single().manualTags)
      .containsExactly("Matcha", "Galette")
      .inOrder()
  }

  @Test
  fun archiveAndRestore_movesEntryBetweenLists() = runTest(testDispatcher) {
    val initial = CafeEntry(
      id = "visit-1",
      photoPath = "/tmp/p1.jpg",
      venue = "Cafe Flore",
      address = "Paris",
      visitedAt = 1_000L,
      latitude = null,
      longitude = null,
      taggingModelVersion = "test-model-v1",
    )
    val repo = FakeCafeLogRepository(initialEntries = listOf(initial))
    val viewModel = createViewModel(repository = repo)
    advanceUntilIdle()

    viewModel.archiveEntry(initial.id)
    advanceUntilIdle()
    assertThat(viewModel.uiState.value.entries).isEmpty()
    assertThat(viewModel.uiState.value.archivedEntries).containsExactly(initial)

    viewModel.restoreEntry(initial.id)
    advanceUntilIdle()
    assertThat(viewModel.uiState.value.entries).containsExactly(initial)
    assertThat(viewModel.uiState.value.archivedEntries).isEmpty()
  }

  @Test
  fun chooseUnambiguousCafe_requiresGoodAccuracyAndSeparation() {
    val pos = UserPosition(latitude = 48.842, longitude = 2.325, accuracyMeters = 10f, observedAtMillis = 0L)
    val near = CafeCandidate("1", "Near Cafe", null, 48.84205, 2.325)
    val far = CafeCandidate("2", "Far Cafe", null, 48.8425, 2.325)
    assertThat(chooseUnambiguousCafe(listOf(near, far), pos)).isEqualTo(near)

    val ambiguous = CafeCandidate("3", "Neighbor Cafe", null, 48.84208, 2.325)
    assertThat(chooseUnambiguousCafe(listOf(near, ambiguous), pos)).isNull()

    val lowAccuracy = pos.copy(accuracyMeters = 60f)
    assertThat(chooseUnambiguousCafe(listOf(near), lowAccuracy)).isNull()
  }
}

private class FakeCafeLogRepository(
  initialEntries: List<CafeEntry> = emptyList(),
  initialArchived: List<CafeEntry> = emptyList(),
) : CafeLogRepository {
  private var active = initialEntries.toMutableList()
  private var archived = initialArchived.toMutableList()

  override fun load(): List<CafeEntry> = active.toList()
  override fun loadArchived(): List<CafeEntry> = archived.toList()

  override fun add(entry: CafeEntry): List<CafeEntry> {
    active = (listOf(entry) + active).distinctBy { it.id }.sortedByDescending { it.visitedAt }.toMutableList()
    return active.toList()
  }

  override fun archive(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> {
    active.removeAll { it.id == entry.id }
    archived = (listOf(entry) + archived).distinctBy { it.id }.sortedByDescending { it.visitedAt }.toMutableList()
    return active.toList() to archived.toList()
  }

  override fun restore(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> {
    archived.removeAll { it.id == entry.id }
    active = (listOf(entry) + active).distinctBy { it.id }.sortedByDescending { it.visitedAt }.toMutableList()
    return active.toList() to archived.toList()
  }

  override fun appendPhotos(entryId: String, newPhotoPaths: List<String>): Pair<List<CafeEntry>, List<CafeEntry>> {
    fun append(list: MutableList<CafeEntry>) = list.map { entry ->
      if (entry.id == entryId) entry.copy(photoPaths = (entry.photoPaths + newPhotoPaths).distinct(), autoTags = emptyList(), taggingModelVersion = null) else entry
    }.toMutableList()
    active = append(active)
    archived = append(archived)
    return active.toList() to archived.toList()
  }

  override fun update(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> {
    active = active.map { if (it.id == entry.id) entry else it }.toMutableList()
    archived = archived.map { if (it.id == entry.id) entry else it }.toMutableList()
    return active.toList() to archived.toList()
  }
}

private class FakeFoodTaggerFacade(private val labels: List<FoodLabel>) : FoodTaggerFacade {
  override suspend fun classify(photoPaths: List<String>): List<FoodLabel> = labels
}

private class FakeLocationFacade(private val initial: UserPosition?) : LocationFacade {
  override fun observeWhileInUse(): Flow<UserPosition> = emptyFlow()
  override suspend fun refresh(): UserPosition? = initial
}

private class FakeAddressLookupFacade(private val address: String?) : AddressLookupFacade {
  override suspend fun describe(position: UserPosition): String? = address
}

private class FakeNearbyCafeFacade(private val cafes: List<CafeCandidate>) : NearbyCafeFacade {
  override suspend fun findNearby(position: UserPosition): List<CafeCandidate> = cafes
}
