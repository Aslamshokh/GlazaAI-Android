package com.aslamshoh.glazaai.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Непрерывный видеопоток для живого режима «Предметы»: превью + ImageAnalysis (кадры без
 * звука затвора и без пауз на фокусировку, в отличие от ImageCapture.takePicture).
 *
 * Кадры приходят в frameListener на фоновом потоке не чаще, чем раз в MIN_FRAME_INTERVAL_MS;
 * слушатель сам решает, брать ли кадр (например, пропускать, пока не пришёл ответ backend на
 * предыдущий) — лишние кадры здесь просто отбрасываются (KEEP_ONLY_LATEST).
 */
class LiveCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var provider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    var frameListener: ((Bitmap) -> Unit)? = null

    @Volatile
    private var lastFrameMs = 0L

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
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image -> handleFrame(image) }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                provider = cameraProvider
                isReady = true
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Не удалось запустить камеру: ${e.message ?: "неизвестная ошибка"}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Останавливает камеру и фоновый поток анализа — вызывается при уходе с экрана. */
    fun release() {
        frameListener = null
        provider?.unbindAll()
        isReady = false
        analysisExecutor.shutdown()
    }

    private fun handleFrame(image: ImageProxy) {
        try {
            val listener = frameListener
            val now = SystemClock.elapsedRealtime()
            if (listener == null || now - lastFrameMs < MIN_FRAME_INTERVAL_MS) return
            lastFrameMs = now
            val bitmap = toUprightBitmap(image) ?: return
            listener(bitmap)
        } catch (e: Exception) {
            // Один неудачный кадр не должен ронять поток анализа — просто пропускаем его.
        } finally {
            image.close()
        }
    }

    /** RGBA-кадр CameraX -> Bitmap с учётом возможного выравнивания строк (rowStride) и
     * поворота кадра относительно экрана. */
    private fun toUprightBitmap(image: ImageProxy): Bitmap? {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        if (pixelStride <= 0) return null
        val rowPixels = plane.rowStride / pixelStride
        val buffer = plane.buffer
        buffer.rewind()
        val padded = Bitmap.createBitmap(rowPixels, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        val cropped = if (rowPixels == image.width) {
            padded
        } else {
            Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        }
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return cropped
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
    }

    companion object {
        private const val MIN_FRAME_INTERVAL_MS = 150L
    }
}

@Composable
fun rememberLiveCameraController(): LiveCameraController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    return remember { LiveCameraController(context, lifecycleOwner) }
}

@Composable
fun LiveCameraPreview(controller: LiveCameraController, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }.also { previewView -> controller.bind(previewView) }
        }
    )
}
