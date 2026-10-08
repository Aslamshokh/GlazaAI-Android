package com.aslamshoh.glazaai.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import com.aslamshoh.glazaai.util.FrameQuality
import com.aslamshoh.glazaai.util.FrameStats
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var analysisExecutor: ExecutorService? = null
    private val quality = FrameQuality()

    @Volatile
    private var lastAnalysisMs = 0L

    /**
     * Оценка каждого кадра (чёткость, яркость, движение) — приходит в главном потоке не чаще
     * ~7 раз в секунду. Включается только пока слушатель задан.
     */
    @Volatile
    var qualityListener: ((FrameStats) -> Unit)? = null
        set(value) {
            field = value
            if (value == null) quality.reset()
        }

    /** Есть ли у камеры вспышка (для фонарика). */
    var hasTorch by mutableStateOf(false)
        private set
    var torchOn by mutableStateOf(false)
        private set

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

                // Анализ яркостной плоскости для автосъёмки; если камера не тянет три потока сразу,
                // откатываемся на превью + снимок без автосъёмки (кнопка работает всегда).
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val executor = Executors.newSingleThreadExecutor()
                analysisExecutor?.shutdown()
                analysisExecutor = executor
                analysis.setAnalyzer(executor) { image -> analyze(image) }

                cameraProvider.unbindAll()
                var bound: Camera
                var cases: Array<androidx.camera.core.UseCase>
                try {
                    bound = cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis
                    )
                    cases = arrayOf(preview, capture, analysis)
                } catch (e: Exception) {
                    cameraProvider.unbindAll()
                    bound = cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
                    )
                    cases = arrayOf(preview, capture)
                }
                camera = bound
                hasTorch = bound.cameraInfo.hasFlashUnit()
                torchOn = false
                provider = cameraProvider
                useCases = cases
                imageCapture = capture
                isReady = true
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Не удалось запустить камеру: ${e.message ?: "неизвестная ошибка"}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        qualityListener = null
        if (torchOn) camera?.cameraControl?.enableTorch(false)
        if (useCases.isNotEmpty()) provider?.unbind(*useCases)
        isReady = false
        torchOn = false
        analysisExecutor?.shutdown()
        analysisExecutor = null
    }

    /** Фонарик: в тёмной комнате текст и коды без него не читаются. */
    fun setTorch(on: Boolean) {
        val cam = camera ?: return
        if (!hasTorch) return
        cam.cameraControl.enableTorch(on)
        torchOn = on
    }

    private fun analyze(image: ImageProxy) {
        try {
            val listener = qualityListener
            val now = SystemClock.elapsedRealtime()
            if (listener == null || now - lastAnalysisMs < ANALYSIS_INTERVAL_MS) return
            lastAnalysisMs = now
            val plane = image.planes[0]
            val stats = quality.measure(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride)
            mainHandler.post { qualityListener?.invoke(stats) }
        } catch (e: Exception) {
            // Один плохой кадр не должен ломать камеру.
        } finally {
            image.close()
        }
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

    private companion object {
        const val ANALYSIS_INTERVAL_MS = 140L
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
