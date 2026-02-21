package com.example.expresscode.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expresscode.util.BarcodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun CarouselScreen(
    barcodeList: List<String>,
    currentIndex: Int,
    isPaused: Boolean,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onTogglePause: () -> Unit,
    onNextBarcode: () -> Unit,
    onSettingsClick: () -> Unit, // New callback
    modifier: Modifier = Modifier
) {
    if (barcodeList.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无条码",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // 自动轮播
    LaunchedEffect(isPaused, speed, barcodeList.size) {
        if (!isPaused && barcodeList.size > 1) {
            // Wait for layout animation to finish (approx 500ms from MainActivity)
            delay(600) 
            while (true) {
                delay(speed.toLong())
                onNextBarcode()
            }
        }
    }

    val safeIndex = currentIndex.coerceIn(0, barcodeList.size - 1)
    val currentCode = barcodeList[safeIndex]

    // 使用 LruCache 防止 OOM（上限提升至 50，对应激进的 8 帧预拉高刷策略）
    val bitmapCache = remember { android.util.LruCache<String, Bitmap>(50) }
    
    // 显示用的状态位图，只要它变化界面就会完美刷新
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // 定死超高清渲染分辨率底图，利用 Compose 的 ContentScale.FillWidth 自动高保真适配任何屏幕宽度
    // 彻底摆脱被 onSizeChanged 第一帧测算的延迟回调所绑架，实现首帧零等待秒出图。
    val genW = 1024
    val genH = 340

    // 主动检查/生成当前条码，以及预加载后面几个条码
    LaunchedEffect(currentCode) {
        val cached = bitmapCache.get(currentCode)
        if (cached != null) {
            displayBitmap = cached
        } else {
            // 如果是未缓存的首张/非连贯点击图，置空一次防上一图残影串联
            displayBitmap = null
            val bmp = withContext(Dispatchers.Default) {
                BarcodeGenerator.generateCode128(currentCode, genW, genH)
            }
            if (bmp != null) {
                bitmapCache.put(currentCode, bmp)
                displayBitmap = bmp
            }
        }
        
        // 预加载降级为单线程限流，防止占满 Dispatchers.Default 饿死 Compose 的布局/滚动协程
        withContext(Dispatchers.IO.limitedParallelism(1)) {
            for (i in 1..8) {
                val nextIndex = safeIndex + i
                if (nextIndex < barcodeList.size) {
                    val nextCode = barcodeList[nextIndex]
                    if (bitmapCache.get(nextCode) == null) {
                        val nextBmp = BarcodeGenerator.generateCode128(nextCode, genW, genH)
                        if (nextBmp != null) {
                            bitmapCache.put(nextCode, nextBmp)
                        }
                    }
                }
                // 每生成一张就主动让出 CPU 时间片，避免连续密集计算导致 UI 帧丢失
                yield()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 条码图片与暂停遮罩
            if (displayBitmap != null) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth(0.9f)) {
                    Image(
                        bitmap = displayBitmap!!.asImageBitmap(),
                        contentDescription = currentCode,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                    if (isPaused) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "单击恢复轮播",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 条码文字
            Text(
                text = currentCode,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 计数器
            Text(
                text = "${safeIndex + 1} / ${barcodeList.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
    }
}
