package com.example.expresscode.ui

import android.util.Log
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.expresscode.scanner.BarcodeAnalyzer
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isScanningPaused: Boolean = false,
    onToggleScanPause: () -> Unit = {},
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    val analyzer = remember { BarcodeAnalyzer(onBarcodeDetected) }

    // 同步暂停状态到 analyzer
    LaunchedEffect(isScanningPaused) {
        analyzer.isPaused = isScanningPaused
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // 使用 COMPATIBLE 模式 (TextureView) 渲染相机画面。
            // 绝不能使用 PERFORMANCE (SurfaceView)，因为现代 Android 的 SurfaceFlinger 
            // 侦测到存在 30fps 的独立 Surface 窗口时，会为了省电强行把整个屏幕硬件降频到 30/60 Hz，
            // 从而产生“全局锁 30 帧”的恶劣掉帧体验。
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        // 等待 PreviewView 完成布局后再绑定相机，确保 viewPort 可用
        val layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                previewView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder()
                            .build()
                            .also { it.surfaceProvider = previewView.surfaceProvider }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(executor, analyzer)
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()

                        // 使用 ViewPort 裁切分析区域与预览保持一致
                        val viewPort = previewView.viewPort
                        if (viewPort != null) {
                            val useCaseGroup = UseCaseGroup.Builder()
                                .setViewPort(viewPort)
                                .addUseCase(preview)
                                .addUseCase(imageAnalysis)
                                .build()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                useCaseGroup
                            )
                        } else {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "相机启动失败", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
        previewView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

        onDispose {
            try {
                previewView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            } catch (_: Exception) {}
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (_: Exception) {}
            executor.shutdownNow()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // MD3 风格暂停提示
        if (isScanningPaused) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "扫描已暂停\n点击恢复",
                        style = MaterialTheme.typography.titleSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }
}
