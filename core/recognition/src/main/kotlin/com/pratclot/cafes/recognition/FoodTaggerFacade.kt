package com.pratclot.cafes.recognition

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import com.google.android.gms.litert.LiteRt
import com.google.android.gms.litert.client.LiteRtInitializationOptions
import com.pratclot.cafes.core.model.FoodLabel
import com.pratclot.cafes.core.model.FoodTaggerFacade
import com.pratclot.cafes.core.model.decodeSampledBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors

class AndroidFoodTaggerFacade(context: Context) : FoodTaggerFacade, AutoCloseable {
  private val appContext = context.applicationContext
  private val executor = Executors.newSingleThreadExecutor { Thread(it, "cafe-food-tagger") }
  private val dispatcher = executor.asCoroutineDispatcher()
  private val labels by lazy {
    appContext.assets.open("food_labels.csv").bufferedReader().useLines { lines ->
      lines.drop(1).mapNotNull { row ->
        val id = row.substringBefore(',').toIntOrNull() ?: return@mapNotNull null
        val name = row.substringAfter(',', "").trim().removeSurrounding("\"")
        id to name
      }.toMap()
    }
  }
  private var classifier: FoodClassifier? = null
  @Volatile private var isClosed = false

  override suspend fun classify(photoPaths: List<String>): List<FoodLabel> {
    if (isClosed) return emptyList()
    return try {
      withContext(dispatcher) {
        if (isClosed) return@withContext emptyList()
        val model = classifier ?: FoodClassifier(appContext, getEnvironment(), labels).also { classifier = it }
        val scores = photoPaths.flatMap { path ->
          val bitmap = decodeSampledBitmap(path, maxDimensionPx = 512) ?: return@flatMap emptyList()
          try { model.infer(bitmap) } finally { bitmap.recycle() }
        }
        scores.groupBy { it.name.lowercase() }.map { (_, candidates) -> candidates.maxBy { it.confidence } }
          .sortedByDescending { it.confidence }.take(6)
      }
    } catch (_: java.util.concurrent.RejectedExecutionException) {
      emptyList()
    }
  }

  override fun close() {
    if (isClosed) return
    isClosed = true
    if (!executor.isShutdown) {
      runCatching {
        executor.execute {
          runCatching { classifier?.close() }
          classifier = null
        }
      }
      executor.shutdown()
    }
  }

  private suspend fun getEnvironment(): Environment = EnvironmentHolder.get(appContext)

  private class FoodClassifier(context: Context, environment: Environment, private val labels: Map<Int, String>) : Closeable {
    private val compiledModel = CompiledModel.create(context.assets, "food_v1.tflite", CompiledModel.Options(Accelerator.CPU), environment)
    private var inputs: List<TensorBuffer> = emptyList()
    private var outputs: List<TensorBuffer> = emptyList()

    init {
      try {
        inputs = compiledModel.createInputBuffers()
        outputs = compiledModel.createOutputBuffers()
        Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888).also { warmup ->
          warmup.eraseColor(android.graphics.Color.BLACK)
          try { inferPixels(warmup) } finally { warmup.recycle() }
        }
      } catch (error: Exception) {
        close()
        throw error
      }
    }

    fun infer(bitmap: Bitmap): List<FoodLabel> {
      val side = minOf(bitmap.width, bitmap.height)
      val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
      val scaled = Bitmap.createScaledBitmap(square, INPUT_SIZE, INPUT_SIZE, true)
      if (square !== bitmap) square.recycle()
      return try { inferPixels(scaled) } finally { scaled.recycle() }
    }

    private fun inferPixels(bitmap: Bitmap): List<FoodLabel> {
      val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
      bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
      val input = ByteArray(pixels.size * 3)
      pixels.forEachIndexed { index, pixel ->
        val offset = index * 3
        input[offset] = android.graphics.Color.red(pixel).toByte()
        input[offset + 1] = android.graphics.Color.green(pixel).toByte()
        input[offset + 2] = android.graphics.Color.blue(pixel).toByte()
      }
      inputs[0].writeInt8(input)
      compiledModel.run(inputs, outputs)
      val scores = outputs[0].readInt8()
      return scores.indices.asSequence().filter { it > 0 }.mapNotNull { index ->
        val name = labels[index]?.takeIf { it.isNotBlank() && !it.startsWith('/') } ?: return@mapNotNull null
        val score = (scores[index].toInt() and 0xFF) / 256f
        FoodLabel(name, score)
      }.filter { it.confidence >= 0.12f }.sortedByDescending { it.confidence }.take(4).toList()
    }

    override fun close() {
      // Resources are initialized together before warm-up; close each before its owning model.
      runCatching { inputs.forEach(TensorBuffer::close) }
      runCatching { outputs.forEach(TensorBuffer::close) }
      runCatching { compiledModel.close() }
    }
  }

  companion object {
    const val MODEL_VERSION = "google-aiy-food-v1"
    private const val INPUT_SIZE = 192
  }
}

private object EnvironmentHolder {
  private val mutex = Mutex()
  private var environment: Environment? = null

  suspend fun get(context: Context): Environment = mutex.withLock {
    environment ?: LiteRt.initialize(
      context.applicationContext,
      LiteRtInitializationOptions.builder().setEnableGpu(false).build(),
    ).await().also { environment = it }
  }
}
