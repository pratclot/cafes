package com.pratclot.cafes.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
fun CameraXPreview(
  hasPermission: Boolean,
  lensFacing: Int,
  imageCapture: ImageCapture,
  onRequestPermission: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!hasPermission) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Camera permission needed for live preview",
          color = Color.White,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(onClick = onRequestPermission) {
          Text("Enable camera", color = Color.White)
        }
      }
    }
    return
  }

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
  var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
  var cameraError by remember { mutableStateOf<String?>(null) }
  val coordinateTransformer = remember { MutableCoordinateTransformer() }

  val preview = remember {
    Preview.Builder().build().apply {
      setSurfaceProvider { request -> surfaceRequest = request }
    }
  }

  LaunchedEffect(lifecycleOwner, lensFacing, imageCapture) {
    cameraError = null
    try {
      val cameraProvider = awaitCameraProvider(context)
      val preferredSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()
      val selector = when {
        cameraProvider.hasCamera(preferredSelector) -> preferredSelector
        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
        else -> {
          cameraError = "No camera found on device"
          return@LaunchedEffect
        }
      }
      cameraProvider.unbindAll()
      val camera = cameraProvider.bindToLifecycle(
        lifecycleOwner,
        selector,
        preview,
        imageCapture,
      )
      cameraControl = camera.cameraControl
    } catch (error: Exception) {
      cameraError = error.localizedMessage ?: "Camera unavailable"
    }
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    surfaceRequest?.let { request ->
      CameraXViewfinder(
        surfaceRequest = request,
        coordinateTransformer = coordinateTransformer,
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(request, cameraControl) {
            detectTapGestures { offset ->
              val surfaceCoords = with(coordinateTransformer) { offset.transform() }
              val point = SurfaceOrientedMeteringPointFactory(
                request.resolution.width.toFloat(),
                request.resolution.height.toFloat(),
              ).createPoint(surfaceCoords.x, surfaceCoords.y)
              cameraControl?.startFocusAndMetering(
                FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build(),
              )
            }
          },
      )
    }

    cameraError?.let { message ->
      Text(
        text = message,
        color = Color.White.copy(alpha = 0.85f),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}

fun capturePhoto(
  imageCapture: ImageCapture,
  executor: Executor,
  lensFacing: Int,
  onPhoto: (Bitmap) -> Unit,
  onError: () -> Unit,
) {
  imageCapture.takePicture(
    executor,
    object : ImageCapture.OnImageCapturedCallback() {
      override fun onCaptureSuccess(image: ImageProxy) {
        image.use { proxy ->
          val buffer = proxy.planes[0].buffer
          val bytes = ByteArray(buffer.remaining())
          buffer.get(bytes)
          val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
          if (decoded == null) {
            onError()
            return
          }
          val rotation = proxy.imageInfo.rotationDegrees.toFloat()
          val needsMirror = lensFacing == CameraSelector.LENS_FACING_FRONT
          if (rotation == 0f && !needsMirror) {
            onPhoto(decoded)
            return
          }
          val matrix = Matrix().apply {
            if (rotation != 0f) postRotate(rotation)
            if (needsMirror) postScale(-1f, 1f)
          }
          val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
          if (rotated !== decoded) decoded.recycle()
          onPhoto(rotated)
        }
      }

      override fun onError(exception: ImageCaptureException) {
        Log.e("CameraX", "Capture failed", exception)
        onError()
      }
    },
  )
}

private suspend fun awaitCameraProvider(context: android.content.Context): ProcessCameraProvider =
  suspendCancellableCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener(
      {
        try {
          continuation.resume(future.get())
        } catch (error: Exception) {
          continuation.resumeWithException(error)
        }
      },
      ContextCompat.getMainExecutor(context),
    )
  }
