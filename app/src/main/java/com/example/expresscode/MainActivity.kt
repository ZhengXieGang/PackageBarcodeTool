package com.example.expresscode

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expresscode.service.SuspendedCarouselService
import com.example.expresscode.ui.BarcodeListScreen
import com.example.expresscode.ui.CameraPreview
import com.example.expresscode.ui.CarouselScreen
import com.example.expresscode.ui.components.SettingsDialog
import com.example.expresscode.util.PreferenceUtils
import com.example.expresscode.viewmodel.BarcodeViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

// 预分配常用圆角形状，避免每次 recomposition 分配新对象
private val SurfaceShape = RoundedCornerShape(16.dp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpressCodeTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun ExpressCodeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: BarcodeViewModel = viewModel()) {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    val haptic = LocalHapticFeedback.current

    val isCarousel by viewModel.isCarouselMode
    val isPaused by viewModel.isCarouselPaused
    val currentIndex by viewModel.currentCarouselIndex
    val speed by viewModel.carouselSpeed
    val isScanningPaused by viewModel.isScanningPaused

    val context = LocalContext.current
    val carouselDisplayMode by viewModel.carouselDisplayMode

    // 缓存高频回调 lambda，防止子组件因引用变化而无效 recompose
    val onBarcodeDetected = remember<(String) -> Unit> {
        { code ->
            val added = viewModel.addBarcode(code)
            if (added) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
    val onToggleScanPause = remember { { viewModel.toggleScanningPause() } }
    val onTogglePause = remember { { viewModel.togglePause() } }
    val onNextBarcode = remember { { viewModel.nextBarcode() } }
    val onAddBarcode = remember<(String) -> Boolean> { { viewModel.addBarcode(it) } }
    val onRemoveBarcode = remember<(String) -> Unit> { { viewModel.removeBarcode(it) } }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showText by remember { 
        mutableStateOf(PreferenceUtils.getShowText(context)) 
    }
    var autoJump by remember { 
        mutableStateOf(PreferenceUtils.getAutoJump(context)) 
    }
    var urlScheme by remember { 
        mutableStateOf(PreferenceUtils.getUrlScheme(context)) 
    }
    var autoStartDisabled by remember { 
        mutableStateOf(PreferenceUtils.getAutoStartDisabled(context)) 
    }
    
    // Hoist list state for scroll control
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 首次启动请求相机权限 & 加载配置
    LaunchedEffect(Unit) {
        viewModel.loadCarouselMode(context)
        viewModel.loadCarouselSpeed(context)
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            showDialog = showSettingsDialog,
            onDismiss = { showSettingsDialog = false },
            currentMode = carouselDisplayMode,
            onModeChange = { newMode ->
                viewModel.setCarouselMode(context, newMode)
                if (isCarousel && newMode == BarcodeViewModel.CarouselMode.Suspended) {
                   viewModel.toggleCarouselMode()
                }
            },
            currentSpeed = speed,
            onSpeedChange = { 
                viewModel.carouselSpeed.floatValue = it 
                PreferenceUtils.saveCarouselSpeed(context, it)
            },
            showText = showText,
            onShowTextChange = {
                showText = it
                PreferenceUtils.saveShowText(context, it)
            },
            autoJump = autoJump,
            onAutoJumpChange = {
                autoJump = it
                PreferenceUtils.saveAutoJump(context, it)
            },
            urlScheme = urlScheme,
            onUrlSchemeChange = {
                urlScheme = it
                PreferenceUtils.saveUrlScheme(context, it)
            },
            autoStartDisabled = autoStartDisabled,
            onAutoStartDisabledChange = {
                autoStartDisabled = it
                PreferenceUtils.saveAutoStartDisabled(context, it)
            },
            onAdjustWindowClick = {
                if (!android.provider.Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                } else {
                    val intent = Intent(context, SuspendedCarouselService::class.java)
                    intent.action = SuspendedCarouselService.ACTION_RESIZE
                    context.startService(intent)
                    showSettingsDialog = false
                    // Optional: Minimize app to see window
                    // (context as? android.app.Activity)?.moveTaskToBack(true)
                }
            }
        )
    }

    // 动态调整顶部高度
    val topSurfaceHeight by animateDpAsState(
        targetValue = if (isCarousel) 200.dp else 320.dp,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "topSurfaceHeight"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                val showStartFab = !isCarousel && viewModel.barcodeList.isNotEmpty()
                
                // Settings FAB (Always Visible, animated position)
                // Use a Column with weight/spacer or just simple padding animation?
                // Better: Use AnimatedVisibility for Start FAB, and let Column layout handle the rest.
                // But we want smooth animation. AnimatedVisibility expands/shrinks space.
                
                Column(
                    modifier = Modifier.padding(bottom = 16.dp), // Check if Scaffold padding is enough
                    horizontalAlignment = Alignment.End
                ) {
                     // Settings FAB
                    FloatingActionButton(
                        onClick = { showSettingsDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = SurfaceShape,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }

                    // Start Carousel FAB (Animated)
                    AnimatedVisibility(
                        visible = showStartFab,
                        enter = slideInVertically { height -> height } + fadeIn(),
                        exit = slideOutVertically { height -> height } + fadeOut()
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                if (carouselDisplayMode == BarcodeViewModel.CarouselMode.Suspended) {
                                    if (!android.provider.Settings.canDrawOverlays(context)) {
                                        val intent = Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        if (autoJump && urlScheme.isNotBlank()) {
                                            try {
                                            val jumpIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlScheme))
                                                jumpIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(jumpIntent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        val intent = Intent(context, SuspendedCarouselService::class.java)
                                        intent.action = SuspendedCarouselService.ACTION_CAROUSEL
                                        intent.putStringArrayListExtra(
                                            SuspendedCarouselService.EXTRA_BARCODE_LIST,
                                            java.util.ArrayList(viewModel.barcodeList)
                                        )
                                        intent.putExtra(SuspendedCarouselService.EXTRA_SPEED, speed)
                                        intent.putExtra(SuspendedCarouselService.EXTRA_IS_PAUSED, autoStartDisabled)
                                        context.startService(intent)
                                    }
                                } else {
                                    // In-App Carousel Start Logic
                                    // Smooth scroll to top if not already at top
                                    if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(0)
                                            viewModel.toggleCarouselMode(autoStartDisabled)
                                        }
                                    } else {
                                        viewModel.toggleCarouselMode(autoStartDisabled)
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = SurfaceShape,
                            modifier = Modifier
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Text(
                                text = if (carouselDisplayMode == BarcodeViewModel.CarouselMode.Suspended) "开启悬浮窗" else "开始轮播",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }


            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Removed verticalScroll to prevent nested scrolling issues
        ) {
            // 顶部区域 — 相机或轮播
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topSurfaceHeight) // 使用动画高度
                    .padding(
                        horizontal = if (isCarousel) 0.dp else 12.dp, 
                        vertical = if (isCarousel) 0.dp else 8.dp
                    )
                    .clip(SurfaceShape)
                    .combinedClickable(
                        onClick = {
                            if (isCarousel) {
                                viewModel.togglePause()
                            } else {
                                viewModel.toggleScanningPause()
                            }
                        },
                        onLongClick = {
                            if (isCarousel) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.isCarouselMode.value = false
                                viewModel.isScanningPaused.value = false
                            }
                        }
                    ),
                tonalElevation = 2.dp,
                shape = SurfaceShape
            ) {
                AnimatedContent(
                    targetState = isCarousel,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "camera_carousel_switch"
                ) { showCarousel ->
                    if (showCarousel) {
                        CarouselScreen(
                            barcodeList = viewModel.barcodeList,
                            currentIndex = currentIndex,
                            isPaused = isPaused,
                            speed = speed,
                            onSpeedChange = { viewModel.carouselSpeed.floatValue = it },
                            onTogglePause = onTogglePause,
                            onNextBarcode = onNextBarcode,
                            onSettingsClick = { }
                        )
                    } else {
                        if (cameraPermission.status.isGranted) {
                            CameraPreview(
                                isScanningPaused = isScanningPaused,
                                onToggleScanPause = onToggleScanPause,
                                onBarcodeDetected = onBarcodeDetected
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "请授予相机权限",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 底部区域 — 条码列表
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Fill remaining space
                tonalElevation = 0.dp
            ) {
                BarcodeListScreen(
                    barcodeList = viewModel.barcodeList,
                    onAddBarcode = onAddBarcode,
                    onRemoveBarcode = onRemoveBarcode,
                    listState = listState,
                    contentPadding = PaddingValues(bottom = 150.dp) // Increased space for FABs + padding
                )
            }
        }
    }
}
