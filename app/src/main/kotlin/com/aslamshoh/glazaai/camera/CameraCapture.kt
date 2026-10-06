package com.aslamshoh.glazaai.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Управляет одной сессией CameraX (превью + захват кадра) для режимов с одиночным снимком —
 * Валюта/Текст/Предметы/Документы. Аналог CameraController.swift на iOS, но без
 * непрерывного сканирования штрих-кодов — для него отдельный путь через ZXing
 * (см. ui/BarcodeScreen.kt), так как CameraX и zxing-android-embedded не делят одну камеру.
 */
class CameraCaptureController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var imageCapture: ImageCapture? = null
    private var provider: ProcessCameraProvider? = null
    // Только свои use case: при смене экрана старый экран не должен отключать камеру нового.
    private var useCases: Array<androidx.camera.core.UseCase> = emptyArray()

    var isReady by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun bind(previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
                provider = cameraProvider
                useCases = arrayOf(preview, capture)
                imageCapture = capture
                isReady = true
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Не удалось запустить камеру: ${e.message ?: "неизвестная ошибка"}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        if (useCases.isNotEmpty()) provider?.unbind(*useCases)
        isReady = false
    }

    fun captureFrame(onResult: (Bitmap?) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            onResult(null)
            return
        }
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    onResult(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(null)
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return decoded
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }
}

@Composable
fun rememberCameraCaptureController(): CameraCaptureController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    return remember { CameraCaptureController(context, lifecycleOwner) }
}

@Composable
fun CameraPreview(controller: CameraCaptureController, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }.also { previewView -> controller.bind(previewView) }
        }
    )
}
