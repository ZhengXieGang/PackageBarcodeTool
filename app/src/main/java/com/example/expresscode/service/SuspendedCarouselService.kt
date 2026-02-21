package com.example.expresscode.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import com.example.expresscode.R
import com.example.expresscode.util.BarcodeGenerator
import com.example.expresscode.util.PreferenceUtils
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SuspendedCarouselService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var barcodeList: ArrayList<String>? = null
    private var currentIndex = 0
    private var speed = 1000L
    private var isPaused = false
    
    // 异步协同服务范围与激进缓冲池（50层），防 OOM 及跨帧爆音卡顿
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val bitmapCache = android.util.LruCache<String, Bitmap>(50)

    // Mode: "RESIZE" or "CAROUSEL"
    private var currentMode = MODE_CAROUSEL
    
    // 即时响应测算出的无损尺寸，抛弃系统后置延迟。
    private var genW = 1024
    private var genH = 340

    private val handler = Handler(Looper.getMainLooper())
    private val carouselRunnable = object : Runnable {
        override fun run() {
            if (currentMode == MODE_CAROUSEL) {
                val list = barcodeList ?: return
                if (list.isEmpty()) return
                if (!isPaused) {
                    currentIndex = (currentIndex + 1) % list.size
                    updateBarcodeDisplay()
                    handler.postDelayed(this, speed)
                } else {
                    // 暂停中仍保持轮询，以便恢复时能立刻衔接
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            if (action == ACTION_STOP) {
                stopSelf()
                return START_NOT_STICKY
            }

            // Init WindowManager
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // Determine mode
            val isResize = action == ACTION_RESIZE

            if (isResize) {
                currentMode = MODE_RESIZE
                if (floatingView != null) {
                    windowManager.removeView(floatingView)
                    floatingView = null
                }
                createResizeWindow()
            } else {
                currentMode = MODE_CAROUSEL
                barcodeList = intent.getStringArrayListExtra(EXTRA_BARCODE_LIST)
                speed = intent.getFloatExtra(EXTRA_SPEED, 1000f).toLong()
                isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false)

                if (barcodeList.isNullOrEmpty()) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (floatingView != null) {
                    windowManager.removeView(floatingView)
                    floatingView = null
                }
                // 彻底破除“首图闪一下才出来”的尴尬与“左右死板留大白底”的问题：
                // 为悬浮窗进行最贴近真实预先测算，抛弃后置回调，在启动期算出最完美占位的分辨率！
                val (savedW, savedH) = PreferenceUtils.getWindowSize(this)
                val isVertical = savedH > savedW
                val tmpLayoutW = if (isVertical) savedH else savedW
                // 这里原来分配的 0.7f 太高了，因为条码本来就很长条。
                // 把留给条码相框的实际高度拔低到 0.4f 以迫使其自然修身
                val tmpLayoutH = if (isVertical) (savedW * 0.9f).toInt() else (savedH * 0.4f).toInt()
                val tmpLrPadding = if (isVertical) (savedH * 0.05f).toInt() else (savedW * 0.05f).toInt()

                genW = (tmpLayoutW - 2 * tmpLrPadding).coerceAtLeast(300)
                genH = (tmpLayoutH * 0.9f).toInt().coerceAtLeast(100) // 让出一点给文字，剩余全塞满

                // 为了严防协程和 WindowManager 载入视图的时序抢夺，先把首图用这套精准长宽预加载到缓存池！
                val firstCode = barcodeList!![0]
                if (bitmapCache.get(firstCode) == null) {
                    val bmp = BarcodeGenerator.generateCode128(firstCode, genW, genH)
                    if (bmp != null) bitmapCache.put(firstCode, bmp)
                }
                
                startForegroundService()
                createCarouselWindow() // 内部现在必定能瞬间同步取到图并设置了再 addView
                
                handler.removeCallbacks(carouselRunnable)
                // 确保悬浮窗挂载和动画完全结束后才开始第一次跃迁（直接等待一个轮播周期 speed）
                handler.postDelayed(carouselRunnable, speed)
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "suspended_carousel_service"
        val channelName = "悬浮轮播服务"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                channelName,
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("悬浮轮播进行中")
            .setContentText("点击此处或长按悬浮窗关闭服务")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setOngoing(true)
            .setContentIntent(
                android.app.PendingIntent.getService(
                    this, 0,
                    Intent(this, SuspendedCarouselService::class.java).apply { action = ACTION_STOP },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        startForeground(1, notification)
    }

    // ==========================================
    // Resize Window Logic
    // ==========================================
    private fun createResizeWindow() {
        val context = this
        val (savedW, savedH) = PreferenceUtils.getWindowSize(context)
        val (savedX, savedY) = PreferenceUtils.getWindowPosition(context)

        // Dark Mode Check
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isDark) 0xFF1C1B1F.toInt() else Color.WHITE
        val iconColor = if (isDark) Color.WHITE else Color.BLACK
        val handleColor = if (isDark) Color.LTGRAY else Color.DKGRAY

        // Root Layout
        val root = FrameLayout(context).apply {
            // MD3 Style Background for Resize Window too
            background = GradientDrawable().apply {
                setColor(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getColor(android.R.color.system_accent1_100).let {
                       if (isDark) 0xFF333333.toInt() else it
                    }
                } else {
                    bgColor
                })
                cornerRadius = 32f 
                setStroke(0, Color.TRANSPARENT)
            }
            elevation = 12f
            clipToOutline = true
        }

        // Save Button (Centered Icon)
        val saveIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_window_save)
            setColorFilter(iconColor) 
            layoutParams = FrameLayout.LayoutParams(120, 120, Gravity.CENTER)
            
            // Interaction feedback
            isClickable = true
            isFocusable = true
            
            // Add a simple background ripple or click effect if possible, or just click listener
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tooltipText = "保存设置"
            }
            
            setOnClickListener {
                val params = root.layoutParams as WindowManager.LayoutParams
                PreferenceUtils.saveWindowSize(context, params.width, params.height)
                PreferenceUtils.saveWindowPosition(context, params.x, params.y)
                Toast.makeText(context, "已保存设置", Toast.LENGTH_SHORT).show()
                stopSelf()
            }
        }
        root.addView(saveIcon)

        // Close Button (Top End)
        val closeIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_close_circle)
            setColorFilter(iconColor) 
            layoutParams = FrameLayout.LayoutParams(60, 60, Gravity.TOP or Gravity.END).apply {
                setMargins(0, 32, 32, 0)
            }
            isClickable = true
            isFocusable = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tooltipText = "关闭窗口"
            }
            setOnClickListener {
                stopSelf()
            }
        }
        root.addView(closeIcon)

        // Resize Handle Container (High Touch Area)
        val resizeHandleContainer = FrameLayout(context).apply {
             isClickable = true
             isFocusable = true
        }
        val containerParams = FrameLayout.LayoutParams(150, 150) // Even larger touch area
        containerParams.gravity = Gravity.BOTTOM or Gravity.END
        root.addView(resizeHandleContainer, containerParams)

        // Resize Handle Icon (Visual)
        val resizeIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_window_resize)
            setColorFilter(handleColor) 
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val iconParams = FrameLayout.LayoutParams(60, 60) // 与关闭按钮同尺寸
        iconParams.gravity = Gravity.BOTTOM or Gravity.END
        iconParams.setMargins(0, 0, 32, 32) // 与关闭按钮的 32px 边距镜像对齐
        resizeHandleContainer.addView(resizeIcon, iconParams)

        floatingView = root

        val params = WindowManager.LayoutParams(
            savedW,
            savedH,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = savedX
        params.y = savedY

        // --- Touch Logic for Move and Resize ---
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var initialWidth = 0
        var initialHeight = 0

        // Resize Listener (on Container)
        resizeHandleContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialWidth = params.width
                    initialHeight = params.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    params.width = (initialWidth + dx).coerceAtLeast(300)
                    params.height = (initialHeight + dy).coerceAtLeast(200)
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        // Move Listener (on Root)
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    // Consume only if not clicking the save button? 
                    // Save button has its own click listener which should take precedence if hit.
                    // But since root captures touch, save button might not get click if we return true here.
                    // Actually, if we return true, we consume. 
                    // We need to handle click manually or let saveIcon be on top and consume its own touch?
                    // FrameLayout Z-ordering: last added is on top. SaveIcon added before Handle.
                    // Root OnTouch might intercept? 
                    // Let's rely on event return. Reference: usually fine if children handle click.
                    // But if root consumes ACTION_DOWN, children won't see it.
                    // Fix: Check if touch is inside saveIcon.
                    false // Return false to let children handle events? BUT we want to drag.
                    // Correct pattern: Move listener should use a GestureDetector or check touch targets.
                    // Simplified: Drag anywhere EXCEPT the icons.
                    // But SaveIcon needs click.
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    // Simple drag threshold to differentiate click?
                    if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) {
                         params.x = initialX + dx
                         params.y = initialY + dy
                         windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }

                else -> false
            }
        }
        
        // Re-implement move logic to not block Save Button Click
        // The issue is root.setOnTouchListener captures everything if it returns true.
        // We can attach move listener to root, but strictly speaking, if we tap Save Button, root's onTouch is called?
        // Yes, bubbling. 
        // Better approach: Make Save Button consume its touch.
        // SaveButton has setOnClickListener. We need to ensure it gets touches.
        // If Root returns true in ACTION_DOWN, it captures the stream.
        
        // Let's look at the touch event dispatch. 
        // We can just rely on the fact that we can move by dragging the "Background".
        // If we touch the Save Icon, we shouldn't drag? 
        // Save Icon is centered. 
        
        // Refined Move Logic:
        val moveListener = View.OnTouchListener { view, event ->
             when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    // Check if we hit the Save Button?
                    val location = IntArray(2)
                    saveIcon.getLocationOnScreen(location)
                    val saveRect = android.graphics.Rect(location[0], location[1], location[0] + saveIcon.width, location[1] + saveIcon.height)
                    if (saveRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        return@OnTouchListener false // Let save button handle it
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                     val dx = (event.rawX - initialTouchX).toInt()
                     val dy = (event.rawY - initialTouchY).toInt()
                     params.x = initialX + dx
                     params.y = initialY + dy
                     windowManager.updateViewLayout(floatingView, params)
                     true
                }
                else -> false
            }
        }
        root.setOnTouchListener(moveListener)

        windowManager.addView(floatingView, params)
    }



    // ==========================================
    // Carousel Window Logic
    // ==========================================
    private fun createCarouselWindow() {
        val context = this
        val (savedW, savedH) = PreferenceUtils.getWindowSize(context)
        val (savedX, savedY) = PreferenceUtils.getWindowPosition(context)
        val showText = PreferenceUtils.getShowText(context)

        // Dark Mode Check
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isDark) 0xFF1C1B1F.toInt() else Color.WHITE
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val progressColor = if (isDark) Color.LTGRAY else Color.DKGRAY

        // Root Layout
        val root = FrameLayout(context).apply {
            // MD3 Style Background
            background = GradientDrawable().apply {
                setColor(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getColor(android.R.color.system_accent1_100).let {
                       if (isDark) 0xFF333333.toInt() else it
                    }
                } else {
                    bgColor
                })
                cornerRadius = 32f 
                setStroke(0, Color.TRANSPARENT)
            }
            elevation = 8f
            clipToOutline = true
        }

        // Layout Logic:
        // Case A (Horizontal W >= H):
        //   Container W = 0.9 * W, H = 0.7 * H
        //   Rotation = 0
        // Case B (Vertical H > W):
        //   Container W = 0.7 * H (Visual Height), H = 0.9 * W (Visual Width)
        //   Rotation = 90
        
        val isVertical = savedH > savedW
        val layoutW: Int
        val layoutH: Int
        val rotationAngle: Float
        val lrPadding: Int

        if (isVertical) {
            // 原先宽度 0.7H 放大为 1.0H 铺满短边（即容器的旋转后 Width）
            layoutW = savedH
            // 高度为了留给底部操作栏仍旧 0.9W （即容器旋转后 Height）
            layoutH = (savedW * 0.9f).toInt()
            rotationAngle = 90f
            lrPadding = (savedH * 0.05f).toInt()
        } else {
            // 原先宽度 0.9W 放大为 1.0W 铺满长边
            layoutW = savedW
            // 高度不要留那么多空底色了，压到 0.4H 足以放下。
            layoutH = (savedH * 0.4f).toInt()
            rotationAngle = 0f
            lrPadding = (savedW * 0.05f).toInt()
        }

        val contentContainer = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            rotation = rotationAngle
        }
        
        val contentParams = FrameLayout.LayoutParams(layoutW, layoutH).apply {
            gravity = Gravity.CENTER
        }
        root.addView(contentContainer, contentParams)

        // 外层弹性定高框
        val imageContainer = FrameLayout(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            // 实现两边留白 5%
            setPadding(lrPadding, 0, lrPadding, 0)
        }

        // Image View — 高度 WRAP_CONTENT + adjustViewBounds 让条码按宽度自适应高度
        val imageView = ImageView(context).apply {
            tag = "barcode_image"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        
        // Pause Mask — MATCH_PARENT 填满包装容器，而包装容器高度由 imageView 撑开
        val pauseMask = TextView(context).apply {
            tag = "pause_mask"
            text = "单击恢复轮播"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(153, 0, 0, 0)) // 60% black
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            visibility = if (isPaused) View.VISIBLE else View.GONE
        }
        
        // 包装容器：宽 MATCH_PARENT、高 WRAP_CONTENT，遮罩高度精确跟随条码实际渲染高度
        val barcodeWrapper = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        barcodeWrapper.addView(imageView)
        barcodeWrapper.addView(pauseMask)
        
        imageContainer.addView(barcodeWrapper)
        
        // Code Text
        val textView = TextView(context).apply {
            tag = "barcode_text"
            textSize = 12f 
            gravity = Gravity.CENTER
            setTextColor(textColor)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4
            }
            visibility = if (showText) View.VISIBLE else View.GONE
        }

        contentContainer.addView(imageContainer)
        contentContainer.addView(textView)

        // Progress Text
        val progressText = TextView(context).apply {
            tag = "progress_text"
            textSize = 10f
            setTextColor(progressColor)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 10, 10)
            }
        }
        root.addView(progressText)

        floatingView = root

        val params = WindowManager.LayoutParams(
            savedW,
            savedH,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = savedX
        params.y = savedY

        // Touch Listener (Move & Pause)
        // Touch Logic with Drag vs Click/LongPress conflict handling
        var isDragging = false
        val touchSlop = 15 // px threshold

        val gestureDetector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                togglePause()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                // Prevent long press if we were dragging
                if (isDragging) return
                
                // Close Animation
                root.animate()
                    .scaleX(0f)
                    .scaleY(0f)
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction {
                        stopSelf()
                    }
                    .start()
            }
        })

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                isDragging = false
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
            }

            if (event.action == MotionEvent.ACTION_MOVE) {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                    isDragging = true
                }
                if (isDragging) {
                     params.x = initialX + dx
                     params.y = initialY + dy
                     windowManager.updateViewLayout(floatingView, params)
                     PreferenceUtils.saveWindowPosition(context, params.x, params.y)
                }
            }
            
            gestureDetector.onTouchEvent(event)
            true
        }

        windowManager.addView(floatingView, params)
        updateBarcodeDisplay()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun updateBarcodeDisplay() {
        val view = floatingView ?: return
        val list = barcodeList
        if (list.isNullOrEmpty()) return
        if (currentMode != MODE_CAROUSEL) return

        val safeIndex = currentIndex.coerceIn(0, list.size - 1)
        val currentCode = list[safeIndex]
        
        val imageView = view.findViewWithTag<ImageView>("barcode_image") ?: return
        val textView = view.findViewWithTag<TextView>("barcode_text") ?: return
        val progressText = view.findViewWithTag<TextView>("progress_text") ?: return

        textView.text = currentCode
        progressText.text = "${safeIndex + 1}/${list.size}"

        // 此处延用挂载初期就算好的 genW 与 genH 进行所有异步无阻塞调度
        val cached = bitmapCache.get(currentCode)
        if (cached != null) {
            imageView.setImageBitmap(cached)
        } else {
            // 如果缓冲里没命中，切后台临时加急补生成当前长条码（先置空残留）
            imageView.setImageDrawable(null)
            serviceScope.launch {
                val bmp = withContext(Dispatchers.Default) {
                    BarcodeGenerator.generateCode128(currentCode, genW, genH)
                }
                if (bmp != null) {
                    bitmapCache.put(currentCode, bmp)
                    imageView.setImageBitmap(bmp)
                }
            }
        }

        // 预加载降级为单线程限流，防止与 UI 线程争抢 CPU
        serviceScope.launch {
            withContext(Dispatchers.IO.limitedParallelism(1)) {
                for (i in 1..8) {
                    val nextIndex = safeIndex + i
                    if (nextIndex < list.size) {
                        val nextCode = list[nextIndex]
                        if (bitmapCache.get(nextCode) == null) {
                            val nextBmp = BarcodeGenerator.generateCode128(nextCode, genW, genH)
                            if (nextBmp != null) {
                                bitmapCache.put(nextCode, nextBmp)
                            }
                        }
                    }
                    kotlinx.coroutines.yield()
                }
            }
        }
    }

    private fun togglePause() {
        isPaused = !isPaused
        val msg = if (isPaused) "暂停轮播" else "恢复轮播"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        
        val view = floatingView ?: return
        val mask = view.findViewWithTag<TextView>("pause_mask")
        mask?.visibility = if (isPaused) View.VISIBLE else View.GONE
        
        // 修复暂停恢复后轮播不继续的 Bug：重新启动 handler 轮转链条
        if (!isPaused) {
            handler.removeCallbacks(carouselRunnable)
            handler.postDelayed(carouselRunnable, speed)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            windowManager.removeView(floatingView)
            floatingView = null
        }
        handler.removeCallbacks(carouselRunnable)
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_STOP = "STOP_SERVICE"
        const val ACTION_RESIZE = "RESIZE_WINDOW"
        const val ACTION_CAROUSEL = "CAROUSEL_WINDOW"
        
        const val MODE_RESIZE = 1
        const val MODE_CAROUSEL = 2

        const val EXTRA_BARCODE_LIST = "BARCODE_LIST"
        const val EXTRA_SPEED = "SPEED"
        const val EXTRA_IS_PAUSED = "IS_PAUSED"
    }
}

