package com.aslamshoh.glazaai.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Найденный код: текст и признак «двумерный» (QR и подобные) — они не товарные. */
data class BarcodeHit(val value: String, val twoDimensional: Boolean)

/**
 * Камера для сканера QR и штрихкодов: превью + анализ кадров через ML Kit (работает на телефоне,
 * без интернета, заметно надёжнее ZXing на смазанных, наклонных и мелких кодах).
 * Результат приходит в [onResult] в главном потоке; пока [paused] = true, кадры пропускаются.
 */
class BarcodeCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var useCases: Array<UseCase> = emptyArray()
    private var executor: ExecutorService? = null
    private var scanner: BarcodeScanner? = null

    @Volatile
    var onResult: ((BarcodeHit) -> Unit)? = null

    @Volatile
    var paused: Boolean = false

    var isReady by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var hasTorch by mutableStateOf(false)
        private set
    var torchOn by mutableStateOf(false)
        private set

    fun bind(previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                // Мелкий или далёкий код на кадре 640×480 — несколько пикселей; просим ~1280×960.
                val resolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val newExecutor = Executors.newSingleThreadExecutor()
                executor?.shutdown()
                executor = newExecutor
                if (scanner == null) {
                    scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
                    )
                }
                analysis.setAnalyzer(newExecutor) { image -> analyze(image) }

                cameraProvider.unbindAll()
                val bound = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                camera = bound
                provider = cameraProvider
                useCases = arrayOf(preview, analysis)
                hasTorch = bound.cameraInfo.hasFlashUnit()
                torchOn = false
                isReady = true
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Не удалось запустить камеру: ${e.message ?: "неизвестная ошибка"}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setTorch(on: Boolean) {
        val cam = camera ?: return
        if (!hasTorch) return
        cam.cameraControl.enableTorch(on)
        torchOn = on
    }

    /** Останавливает камеру, фонарик и фоновый поток — при уходе с экрана. */
    fun release() {
        onResult = null
        if (torchOn) camera?.cameraControl?.enableTorch(false)
        if (useCases.isNotEmpty()) provider?.unbind(*useCases)
        isReady = false
        torchOn = false
        executor?.shutdown()
        executor = null
        scanner?.close()
        scanner = null
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(image: ImageProxy) {
        val media = image.image
        val client = scanner
        if (paused || media == null || client == null) {
            image.close()
            return
        }
        try {
            val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
            client.process(input)
                .addOnSuccessListener { codes ->
                    val hit = codes.firstOrNull { !it.rawValue.isNullOrBlank() }
                    if (hit != null && !paused) {
                        val twoD = hit.format == Barcode.FORMAT_QR_CODE ||
                            hit.format == Barcode.FORMAT_AZTEC ||
                            hit.format == Barcode.FORMAT_DATA_MATRIX ||
                            hit.format == Barcode.FORMAT_PDF417
                        onResult?.invoke(BarcodeHit(hit.rawValue.orEmpty(), twoD))
                    }
                }
                .addOnCompleteListener { image.close() }
        } catch (e: Exception) {
            image.close()
        }
    }
}

@Composable
fun rememberBarcodeCameraController(): BarcodeCameraController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    return remember { BarcodeCameraController(context, lifecycleOwner) }
}

@Composable
fun BarcodeCameraPreview(controller: BarcodeCameraController, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }.also { previewView -> controller.bind(previewView) }
        }
    )
}
