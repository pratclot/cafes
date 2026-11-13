package com.pratclot.cafes.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.ClassificationRun
import com.pratclot.cafes.core.model.FoodLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface DataRepository {
  val data: Flow<List<String>>
}

class DefaultDataRepository : DataRepository {
  override val data: Flow<List<String>> = flow { emit(listOf("Android")) }
}

/** Local-first storage boundary; a synced repository can be added without changing the UI model. */
class LocalCafeLogRepository(context: Context) {
  private val preferences = context.getSharedPreferences("cafe-log", Context.MODE_PRIVATE)

  fun load(): List<CafeEntry> = loadFrom("entries")

  fun loadArchived(): List<CafeEntry> = loadFrom("archived_entries")

  private fun loadFrom(key: String): List<CafeEntry> = runCatching {
    val json = JSONArray(preferences.getString(key, "[]"))
    (0 until json.length()).mapNotNull { index ->
      val item = json.optJSONObject(index) ?: return@mapNotNull null
      val primaryPhoto = item.getString("photoPath")
      val photos = item.optJSONArray("photoPaths")?.let { array ->
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
      }.orEmpty().ifEmpty { listOf(primaryPhoto) }
      CafeEntry(item.getString("id"), primaryPhoto, item.getString("venue"), item.optString("address"), item.getLong("visitedAt"), item.optionalDouble("latitude"), item.optionalDouble("longitude"), photos,
        item.optJSONArray("manualTags").toStringList(), item.optJSONArray("autoTags").toStringList(), item.optString("taggingModelVersion").takeIf(String::isNotBlank), item.optJSONArray("classificationRuns").toClassificationRuns())
    }.filter { entry -> entry.photoPaths.any { File(it).exists() } }.sortedByDescending { it.visitedAt }
  }.getOrDefault(emptyList())

  fun add(entry: CafeEntry): List<CafeEntry> {
    val entries = (listOf(entry) + load()).distinctBy { it.id }.sortedByDescending { it.visitedAt }
    save("entries", entries)
    return entries
  }

  fun archive(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> {
    val active = load().filterNot { it.id == entry.id }
    val archived = (listOf(entry) + loadArchived()).distinctBy { it.id }.sortedByDescending { it.visitedAt }
    save("entries", active)
    save("archived_entries", archived)
    return active to archived
  }

  fun restore(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> {
    val archived = loadArchived().filterNot { it.id == entry.id }
    val active = (listOf(entry) + load()).distinctBy { it.id }.sortedByDescending { it.visitedAt }
    save("entries", active)
    save("archived_entries", archived)
    return active to archived
  }

  fun appendPhotos(entryId: String, newPhotoPaths: List<String>): Pair<List<CafeEntry>, List<CafeEntry>> {
    fun append(entries: List<CafeEntry>) = entries.map { entry ->
      if (entry.id == entryId) entry.copy(photoPaths = (entry.photoPaths + newPhotoPaths).distinct(), autoTags = emptyList(), taggingModelVersion = null) else entry
    }
    val active = append(load())
    val archived = append(loadArchived())
    save("entries", active)
    save("archived_entries", archived)
    return active to archived
  }

  fun update(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> {
    fun replace(entries: List<CafeEntry>) = entries.map { if (it.id == entry.id) entry else it }
    val active = replace(load())
    val archived = replace(loadArchived())
    save("entries", active)
    save("archived_entries", archived)
    return active to archived
  }

  private fun save(key: String, entries: List<CafeEntry>) {
    val json = JSONArray()
    entries.forEach { item ->
      val photos = JSONArray()
      item.photoPaths.forEach(photos::put)
      val manualTags = JSONArray().also { array -> item.manualTags.forEach(array::put) }
      val autoTags = JSONArray().also { array -> item.autoTags.forEach(array::put) }
      val runs = JSONArray().also { array -> item.classificationRuns.forEach { run ->
        val labels = JSONArray().also { tagArray -> run.labels.forEach { label -> tagArray.put(JSONObject().put("name", label.name).put("confidence", label.confidence.toDouble())) } }
        array.put(JSONObject().put("occurredAt", run.occurredAt).put("modelVersion", run.modelVersion).put("photoCount", run.photoCount).put("durationMillis", run.durationMillis).put("outcome", run.outcome).put("labels", labels).put("errorMessage", run.errorMessage))
      } }
      json.put(JSONObject().put("id", item.id).put("photoPath", item.photoPath).put("photoPaths", photos).put("venue", item.venue).put("address", item.address).put("visitedAt", item.visitedAt).putOptional("latitude", item.latitude).putOptional("longitude", item.longitude).put("manualTags", manualTags).put("autoTags", autoTags).put("taggingModelVersion", item.taggingModelVersion).put("classificationRuns", runs))
    }
    preferences.edit().putString(key, json.toString()).apply()
  }

  private fun JSONObject.optionalDouble(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)
  private fun JSONArray?.toStringList(): List<String> = this?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
  private fun JSONArray?.toClassificationRuns(): List<ClassificationRun> = this?.let { array ->
    (0 until array.length()).mapNotNull { index ->
      val item = array.optJSONObject(index) ?: return@mapNotNull null
      val labels = item.optJSONArray("labels").let { tags ->
        if (tags == null) emptyList() else (0 until tags.length()).mapNotNull { tagIndex ->
          val tag = tags.optJSONObject(tagIndex) ?: return@mapNotNull null
          FoodLabel(tag.optString("name"), tag.optDouble("confidence").toFloat())
        }
      }
      ClassificationRun(item.optLong("occurredAt"), item.optString("modelVersion"), item.optInt("photoCount"), item.optLong("durationMillis"), item.optString("outcome"), labels, item.optString("errorMessage").takeIf(String::isNotBlank))
    }
  }.orEmpty()
  private fun JSONObject.putOptional(key: String, value: Double?): JSONObject = apply { if (value == null) put(key, JSONObject.NULL) else put(key, value) }
}
