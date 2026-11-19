package com.pratclot.cafes.data

import com.pratclot.cafes.core.model.CafeEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CafeLogRepositoryTest {
  private fun entry(
    id: String,
    visitedAt: Long = 1_000L,
    photoPaths: List<String> = listOf("photo-$id.jpg"),
  ) = CafeEntry(
    id = id,
    photoPath = photoPaths.first(),
    venue = "Cafe $id",
    address = "123 St",
    visitedAt = visitedAt,
    latitude = 1.0,
    longitude = 2.0,
    photoPaths = photoPaths,
  )

  @Test
  fun add_sortsDescendingAndDeduplicatesById() {
    val first = entry("a", visitedAt = 100L)
    val second = entry("b", visitedAt = 300L)
    val updatedFirst = entry("a", visitedAt = 200L)

    val step1 = CafeEntryListOps.add(emptyList(), first)
    val step2 = CafeEntryListOps.add(step1, second)
    val step3 = CafeEntryListOps.add(step2, updatedFirst)

    assertEquals(listOf("b", "a"), step3.map { it.id })
    assertEquals(200L, step3.last().visitedAt)
  }

  @Test
  fun archiveAndRestore_moveEntryBetweenLists() = runTest {
    val a = entry("a", visitedAt = 100L)
    val b = entry("b", visitedAt = 200L)
    val repo = InMemoryCafeLogRepository(initialActive = listOf(a, b))

    val (activeAfterArchive, archivedAfterArchive) = repo.archive(a)
    assertEquals(listOf("b"), activeAfterArchive.map { it.id })
    assertEquals(listOf("a"), archivedAfterArchive.map { it.id })

    val (activeAfterRestore, archivedAfterRestore) = repo.restore(a)
    assertEquals(listOf("b", "a"), activeAfterRestore.map { it.id })
    assertEquals(emptyList(), archivedAfterRestore)
  }

  @Test
  fun appendPhotos_resetsAutoTagsAndModelVersionAndDeduplicatesPaths() = runTest {
    val a = entry("a", photoPaths = listOf("p1.jpg")).copy(
      autoTags = listOf("Coffee"),
      taggingModelVersion = "v1",
    )
    val repo = InMemoryCafeLogRepository(initialActive = listOf(a))

    val (active, _) = repo.appendPhotos("a", listOf("p1.jpg", "p2.jpg"))
    val updated = active.single()
    assertEquals(listOf("p1.jpg", "p2.jpg"), updated.photoPaths)
    assertEquals(emptyList(), updated.autoTags)
    assertEquals(null, updated.taggingModelVersion)
  }
}
