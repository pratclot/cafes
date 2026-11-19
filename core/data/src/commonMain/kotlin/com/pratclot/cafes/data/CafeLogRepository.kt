package com.pratclot.cafes.data

import com.pratclot.cafes.core.model.CafeEntry
import kotlinx.serialization.Serializable

@Serializable
data class CafeLogSnapshot(
  val active: List<CafeEntry> = emptyList(),
  val archived: List<CafeEntry> = emptyList(),
)

interface CafeLogRepository {
  suspend fun load(): List<CafeEntry>
  suspend fun loadArchived(): List<CafeEntry>
  suspend fun add(entry: CafeEntry): List<CafeEntry>
  suspend fun archive(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>>
  suspend fun restore(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>>
  suspend fun appendPhotos(entryId: String, newPhotoPaths: List<String>): Pair<List<CafeEntry>, List<CafeEntry>>
  suspend fun update(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>>
}

/** Shared pure-Kotlin list operations for CafeLogRepository implementations. */
object CafeEntryListOps {
  fun add(existing: List<CafeEntry>, entry: CafeEntry): List<CafeEntry> =
    (listOf(entry) + existing).distinctBy { it.id }.sortedByDescending { it.visitedAt }

  fun archive(
    active: List<CafeEntry>,
    archived: List<CafeEntry>,
    entry: CafeEntry,
  ): Pair<List<CafeEntry>, List<CafeEntry>> =
    active.filterNot { it.id == entry.id } to add(archived, entry)

  fun restore(
    active: List<CafeEntry>,
    archived: List<CafeEntry>,
    entry: CafeEntry,
  ): Pair<List<CafeEntry>, List<CafeEntry>> =
    add(active, entry) to archived.filterNot { it.id == entry.id }

  fun appendPhotos(
    active: List<CafeEntry>,
    archived: List<CafeEntry>,
    entryId: String,
    newPhotoPaths: List<String>,
  ): Pair<List<CafeEntry>, List<CafeEntry>> {
    fun append(entries: List<CafeEntry>) = entries.map { entry ->
      if (entry.id == entryId) {
        entry.copy(
          photoPaths = (entry.photoPaths + newPhotoPaths).distinct(),
          autoTags = emptyList(),
          taggingModelVersion = null,
        )
      } else {
        entry
      }
    }
    return append(active) to append(archived)
  }

  fun update(
    active: List<CafeEntry>,
    archived: List<CafeEntry>,
    entry: CafeEntry,
  ): Pair<List<CafeEntry>, List<CafeEntry>> {
    fun replace(entries: List<CafeEntry>) = entries.map { if (it.id == entry.id) entry else it }
    return replace(active) to replace(archived)
  }
}

class InMemoryCafeLogRepository(
  initialActive: List<CafeEntry> = emptyList(),
  initialArchived: List<CafeEntry> = emptyList(),
) : CafeLogRepository {
  private var active = initialActive.sortedByDescending { it.visitedAt }
  private var archived = initialArchived.sortedByDescending { it.visitedAt }

  override suspend fun load(): List<CafeEntry> = active

  override suspend fun loadArchived(): List<CafeEntry> = archived

  override suspend fun add(entry: CafeEntry): List<CafeEntry> {
    active = CafeEntryListOps.add(active, entry)
    return active
  }

  override suspend fun archive(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> {
    val result = CafeEntryListOps.archive(active, archived, entry)
    active = result.first
    archived = result.second
    return result
  }

  override suspend fun restore(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> {
    val result = CafeEntryListOps.restore(active, archived, entry)
    active = result.first
    archived = result.second
    return result
  }

  override suspend fun appendPhotos(entryId: String, newPhotoPaths: List<String>): Pair<List<CafeEntry>, List<CafeEntry>> {
    val result = CafeEntryListOps.appendPhotos(active, archived, entryId, newPhotoPaths)
    active = result.first
    archived = result.second
    return result
  }

  override suspend fun update(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> {
    val result = CafeEntryListOps.update(active, archived, entry)
    active = result.first
    archived = result.second
    return result
  }
}
