package com.ysj.spwindow.demo.windows

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.*
import com.ysj.lib.spwindow.SuspendedWindow
import com.ysj.spwindow.demo.R
import com.ysj.spwindow.demo.databinding.SpDemoBinding
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 视频悬浮窗。
 *
 * @author Ysj
 * Create time: 2023/1/10
 */
class VideoSpWindow(context: Context) : SuspendedWindow(context, R.style.SpWindow) {

    companion object {

        private const val TAG = "VideoSpWindow"

        private const val SCREEN_MODE_DEFAULT = 0
        private const val SCREEN_MODE_MIN = 1
        private const val SCREEN_MODE_MAX = 2

        private const val DEFAULT_WIDTH_PERCENTAGE = 0.6f
        private const val DEFAULT_HEIGHT_PERCENTAGE = 0.3f

        private const val MIN_WIDTH_PERCENTAGE = 0.2f
        private const val MIN_HEIGHT_PERCENTAGE = 0.1f

    }

    private val screenWidth: Int
    private val screenHeight: Int

    private val vb by lazy(LazyThreadSafetyMode.NONE) {
        SpDemoBinding.inflate(layoutInflater)
    }

    // ============================ player ==========================

    private var player: MediaPlayer? = null

    private val surfaceProvider = MediaSurfaceProvider()

    private val showProgress: Runnable = ProgressRunner()

    private var currentSpeed = 0f

    private var isStarted = true

    // ==============================================================

    // =========================== touch ============================

    private val touchHandler = MTouchHandler()

    private val region = Rect()

    private var contentX = 0f
    private var contentY = 0f

    // ==============================================================

    private var screenMode = SCREEN_MODE_DEFAULT

    init {
        val resources = context.resources
        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels
        region.left = 0
        region.top = 0
        region.right = screenWidth
        region.bottom = screenHeight
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(vb.root)
        vb.root.background.alpha = 0
        vb.root.setOnTouchListener(touchHandler)
        vb.window.surfaceTextureListener = surfaceProvider
        initDefaultScreenViews()
        initMaxScreenViews()
        initBackPressed()
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
        val params = window.attributes
        params.gravity = Gravity.TOP
        params.width = screenWidth
        params.height = screenHeight
        params.format = PixelFormat.TRANSLUCENT
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
            WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        WindowCompat.setDecorFitsSystemWindows(window, false)

        refreshSpeedUI()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onCreateNewWindow(context: Context): SuspendedWindow {
        val spWindow = VideoSpWindow(context)
        spWindow.currentSpeed = currentSpeed
        val started = this.isStarted
        setPlayerStart(false)
        spWindow.player = requirePlayer()
        spWindow.isStarted = started
        if (surfaceProvider.isMediaPrepared) {
            spWindow.surfaceProvider.onPlayerPrepared()
        }
        this.player = null
        spWindow.contentX = contentX
        spWindow.contentY = contentY
        spWindow.screenMode = screenMode
        return spWindow
    }

    override fun onDestroyNewWindow(spWindow: SuspendedWindow) {
        spWindow as VideoSpWindow
        currentSpeed = spWindow.currentSpeed
        val started = spWindow.isStarted
        spWindow.setPlayerStart(false)
        player = spWindow.requirePlayer()
        spWindow.player = null
        if (spWindow.surfaceProvider.isMediaPrepared && !surfaceProvider.isMediaPrepared) {
            surfaceProvider.onPlayerPrepared()
        }
        setPlayerStart(started)
        contentX = spWindow.contentX
        contentY = spWindow.contentY
        changeScreenMode(spWindow.screenMode, false)
    }

    override fun onActivityResume(activity: Activity) {
        super.onActivityResume(activity)
        if (isShowing && activity == getAssociatedActivity()) {
            val started = isStarted
            setPlayerStart(true)
            isStarted = started
        }
    }

    override fun onActivityPause(activity: Activity) {
        super.onActivityPause(activity)
        if (isShowing && activity == getAssociatedActivity()) {
            val started = isStarted
            setPlayerStart(false)
            isStarted = started
            surfaceProvider.notFirstRender = true
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        var consumed = super.dispatchTouchEvent(event)
        if (!consumed && screenMode != SCREEN_MODE_MAX) {
            consumed = getAssociatedActivity().dispatchTouchEvent(event)
        }
        return consumed
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var consumed = super.onTouchEvent(event)
        if (!consumed && screenMode != SCREEN_MODE_MAX) {
            consumed = getAssociatedActivity().onTouchEvent(event)
        }
        return consumed
    }

    override fun getAssociatedActivity(): Activity = requireNotNull(super.getAssociatedActivity())

    fun setVideoSource(path: String) {
        var player = this.player
        if (player == null) {
            initPlayer()
            player = requirePlayer()
        } else {
            player.reset()
            surfaceProvider.onPlayerRelease()
        }
        refreshLoadingUI()
        player.setDataSource(path)
        player.prepareAsync()
        player.isLooping = true
        onSpeedChanged(vb.controllerView.maxScreen.btn10)
        isStarted = true
        Log.d(TAG, "setVideoSource: $path")
    }

    private fun initBackPressed() {
        onBackPressedDispatcher.addCallback {
            if (screenMode == SCREEN_MODE_MAX) {
                vb.controllerView.maxScreen.btnShrink.performClick()
            }
        }
    }

    private fun initMaxScreenViews(): Unit = with(vb.controllerView.maxScreen) {
        btnPlay.text = "暂停"
        speedContainer.isInvisible = true
        btnSpeed.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        arrayOf(btn05, btn075, btn10, btn125, btn15, btn20).forEach {
            it.setOnClickListener(::onSpeedChanged)
        }
        btnSpeed.setOnClickListener {
            it.isSelected = !it.isSelected
            speedContainer.animation = AnimationUtils.loadAnimation(
                context,
                if (it.isSelected) R.anim.speed_control_bottom_in
                else R.anim.speed_control_bottom_out
            )
            speedContainer.isInvisible = !it.isSelected
        }
        btnShrink.setOnClickListener {
            changeScreenMode(SCREEN_MODE_DEFAULT)
        }
        btnPlay.setOnClickListener {
            setPlayerStart(!requirePlayer().isPlaying)
        }
        seekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                setPlayerStart(false)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                setPlayerStart(true)
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val player = requirePlayer()
                    val percentage = progress / 100f
                    val timeMs = player.duration * percentage
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        player.seekTo(timeMs.roundToInt())
                    } else {
                        player.seekTo(timeMs.roundToLong(), MediaPlayer.SEEK_CLOSEST)
                    }
                }
            }
        })
    }

    private fun initDefaultScreenViews(): Unit = with(vb.controllerView.defaultScreen) {
        btnPlay.text = "暂停"
        btnClose.setOnClickListener {
            dismiss()
        }
        btnFullscreen.setOnClickListener {
            changeScreenMode(SCREEN_MODE_MAX)
        }
        btnShrink.setOnClickListener {
            if (screenMode == SCREEN_MODE_DEFAULT) {
                changeScreenMode(SCREEN_MODE_MIN)
            } else {
                changeScreenMode(SCREEN_MODE_DEFAULT)
            }
        }
        btnPlay.setOnClickListener {
            setPlayerStart(!requirePlayer().isPlaying)
        }
    }

    private fun onSpeedChanged(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        val player = requirePlayer()
        val maxScreen = vb.controllerView.maxScreen
        val speed = when (view) {
            maxScreen.btn05 -> 0.5f
            maxScreen.btn075 -> 0.75f
            maxScreen.btn125 -> 1.25f
            maxScreen.btn15 -> 1.5f
            maxScreen.btn20 -> 2f
            else -> 0.99f // 这里不能为 1，如果首次从 1 设置到其它会导致从头开播
        }
        player.playbackParams = player.playbackParams.setSpeed(speed)
        setPlayerStart(isStarted)
        currentSpeed = speed
        refreshSpeedUI()
        Log.d(TAG, "onSpeedChanged: $speed")
    }

    private fun refreshSpeedUI(): Unit = with(vb.controllerView.maxScreen) {
        icSpeedSelected.updateLayoutParams<ConstraintLayout.LayoutParams> {
            endToEnd = when (currentSpeed) {
                0.5f -> btn05
                0.75f -> btn075
                1.25f -> btn125
                1.5f -> btn15
                2f -> btn20
                else -> btn10
            }.id
        }
    }

    private fun changeScreenMode(screenMode: Int, anim: Boolean = true) {
        if (screenMode == SCREEN_MODE_MAX) {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            )
            setSystemBarColor(Color.BLACK, Color.BLACK)
            vb.controllerView.root.transitionToEnd()
        } else if (this.screenMode == SCREEN_MODE_MAX) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            )
            setSystemBarColor(startStatusBarColor, startNavigationBarColor)
            vb.controllerView.root.transitionToStart()
        } else if (screenMode == SCREEN_MODE_DEFAULT) {
            vb.controllerView.defaultScreen.root.transitionToStart()
        } else {
            vb.controllerView.defaultScreen.root.transitionToEnd()
        }
        this.screenMode = screenMode
        val player = requirePlayer()
        val videoWidth = player.videoWidth
        val videoHeight = player.videoHeight
        val rotation = screenMode == SCREEN_MODE_MAX && videoWidth > videoHeight
        if (videoWidth <= 0 || videoHeight <= 0) {
            Log.w(TAG, "Unable to parse video")
            return
        }
        var maxSize: SizeF = when (screenMode) {
            SCREEN_MODE_MAX -> SizeF(screenWidth.toFloat(), screenHeight.toFloat())
            SCREEN_MODE_MIN -> SizeF(
                screenWidth * MIN_WIDTH_PERCENTAGE,
                screenHeight * MIN_HEIGHT_PERCENTAGE
            )
            else -> SizeF(
                screenWidth * DEFAULT_WIDTH_PERCENTAGE,
                screenHeight * DEFAULT_HEIGHT_PERCENTAGE
            )
        }
        if (rotation) {
            maxSize = SizeF(maxSize.height, maxSize.width)
        }
        val w = videoWidth * maxSize.height
        val h = videoHeight * maxSize.width
        val size = when {
            w < h -> Size((w / videoHeight).roundToInt(), maxSize.height.roundToInt())
            w > h -> Size(maxSize.width.roundToInt(), (h / videoWidth).roundToInt())
            else -> Size(maxSize.width.roundToInt(), maxSize.height.roundToInt())
        }
        val background = vb.root.background
        val backgroundAlpha = background.alpha
        val coreView = vb.coreView
        val currentWidth = coreView.width
        val currentHeight = coreView.height
        val currentX = coreView.x
        val currentY = coreView.y
        val currentRotation = coreView.rotation
        ValueAnimator.ofFloat(0f, 1f).also { animator ->
            var offsetX = 0f
            animator.duration = if (anim) 200 else 0
            animator.addUpdateListener {
                val value = it.animatedValue as Float
                val params = coreView.layoutParams
                params.width = currentWidth + ((size.width - currentWidth) * value).roundToInt()
                params.height = currentHeight + ((size.height - currentHeight) * value).roundToInt()
                coreView.layoutParams = params
                if (screenMode == SCREEN_MODE_MAX) {
                    coreView.x = if (rotation) (screenWidth - params.width) / 2f else currentX + (0 - currentX) * value
                    coreView.y = currentY + ((screenHeight - size.height) / 2f - currentY) * value
                    background.alpha = backgroundAlpha + ((255 - backgroundAlpha) * value).roundToInt()
                } else {
                    val targetX = currentX + (contentX - currentX) * value
                    val inRight = (coreView.x + params.width / 2f).roundToInt() <= screenWidth / 2
                    coreView.x = if (inRight) targetX else {
                        val rightBorder = region.right - params.width.toFloat()
                        val resultX = if (targetX > rightBorder) min(rightBorder, targetX) else max(rightBorder, targetX)
                        offsetX = resultX - targetX
                        resultX
                    }
                    coreView.y = currentY + (contentY - currentY) * value
                    background.alpha = backgroundAlpha + ((0 - backgroundAlpha) * value).roundToInt()
                }
                coreView.rotation = currentRotation + ((if (rotation) 90f else 0f) - currentRotation) * value
            }
            animator.doOnEnd {
                contentX += offsetX
            }
            animator.start()
        }
        this.screenMode = screenMode
    }

    private fun setPlayerStart(start: Boolean) {
        val player = this.player ?: return
        if (start) {
            if (surfaceProvider.wasSurfaceProvided && !player.isPlaying) {
                player.start()
            }
        } else if (player.isPlaying) {
            player.pause()
        }
        val isPlaying = player.isPlaying
        if (isPlaying) {
            vb.controllerView.root.post(showProgress)
            vb.controllerView.maxScreen.btnPlay.text = "暂停"
            vb.controllerView.defaultScreen.btnPlay.text = "暂停"
        } else {
            vb.controllerView.root.removeCallbacks(showProgress)
            vb.controllerView.maxScreen.btnPlay.text = "播放"
            vb.controllerView.defaultScreen.btnPlay.text = "播放"
        }
        this.isStarted = start
        Log.d(TAG, "setPlayerStart: $start , $isPlaying")
    }

    private fun refreshLoadingUI() {
        if (surfaceProvider.isMediaPrepared) {
            vb.loading.isGone = true
            vb.controllerView.root.isVisible = true
        } else {
            vb.loading.isVisible = true
            vb.controllerView.root.isInvisible = true
            val params = vb.coreView.layoutParams
            if (params.width < 0 || params.height < 0) {
                params.width = (screenWidth * DEFAULT_WIDTH_PERCENTAGE).roundToInt()
                params.height = (screenHeight * DEFAULT_HEIGHT_PERCENTAGE).roundToInt()
                vb.coreView.layoutParams = params
            }
        }
    }

    private fun requirePlayer() = checkNotNull(this.player)

    private fun releasePlayer() {
        val player = this.player ?: return
        player.reset()
        player.release()
        this.surfaceProvider.onPlayerRelease()
        this.player = null
        Log.d(TAG, "release player.")
    }

    private fun initPlayer() {
        var player = this.player
        if (player == null) {
            player = MediaPlayer()
            this.player = player
            Log.d(TAG, "init player.")
        }
        refreshLoadingUI()
        player.setOnPreparedListener {
            surfaceProvider.onPlayerPrepared()
        }
    }

    private inner class ProgressRunner : Runnable {
        override fun run() {
            val player = requirePlayer()
            if (player.isPlaying) {
                val progress = (player.currentPosition * 100f / player.duration).roundToInt()
                vb.controllerView.maxScreen.seekbar.progress = progress
                vb.controllerView.defaultScreen.progressbar.progress = progress
            }
            vb.controllerView.root.postDelayed(this, 66)
        }
    }

    private inner class MediaSurfaceProvider : TextureView.SurfaceTextureListener {

        private var texture: SurfaceTexture? = null
        private var surface: Surface? = null

        var notFirstRender = true

        var isMediaPrepared = false
            private set

        var wasSurfaceProvided = false
            private set

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureAvailable: $width , $height")
            this.texture = texture
            tryToComplete()
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            Log.d(TAG, "onSurfaceTextureDestroyed.")
            setPlayerStart(false)
            this.surface?.release()
            this.surface = null
            this.texture = null
            this.wasSurfaceProvided = false
            this.notFirstRender = true
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            if (notFirstRender) {
                Log.d(TAG, "first render.")
                setPlayerStart(isStarted)
                notFirstRender = false
            }
        }

        fun onPlayerPrepared() {
            Log.d(TAG, "MediaPlayer prepared.")
            isMediaPrepared = true
            refreshLoadingUI()
            tryToComplete()
        }

        fun onPlayerRelease() {
            isMediaPrepared = false
            wasSurfaceProvided = false
            notFirstRender = true
        }

        private fun tryToComplete() {
            if (this.wasSurfaceProvided || !this.isMediaPrepared) {
                return
            }
            val texture = this.texture ?: return
            val player = this@VideoSpWindow.player ?: return
            val videoWidth = player.videoWidth
            val videoHeight = player.videoHeight
            texture.setDefaultBufferSize(videoWidth, videoHeight)
            var surface = this.surface
            if (surface == null) {
                surface = Surface(texture)
                this.surface = surface
            }
            player.setSurface(surface)
            this.wasSurfaceProvided = true
            Log.d(TAG, "Surface set on player. $videoWidth , $videoHeight")
            changeScreenMode(screenMode, false)
            val started = isStarted
            setPlayerStart(true)
            isStarted = started
        }

    }

    private inner class MTouchHandler : TouchHandler() {

        private val location = IntArray(2)
        private val viewRect = Rect()

        override fun onClick() {
            super.onClick()
            val view = vb.controllerView.root
            view.animation = AnimationUtils.loadAnimation(
                context,
                if (view.isVisible) R.anim.media_control_out
                else R.anim.media_control_in
            )
            view.isInvisible = view.isVisible
        }

        override fun onStart(event: MotionEvent): Boolean {
            return screenMode == SCREEN_MODE_MAX || touchIn(vb.coreView, event)
        }

        override fun onEnd(event: MotionEvent) {
            super.onEnd(event)
            if (screenMode != SCREEN_MODE_MAX) {
                toBorder()
            }
        }

        override fun onMove(event: MotionEvent): Boolean {
            if (screenMode == SCREEN_MODE_MAX) {
                return false
            }
            contentX += event.relDx
            contentY += event.relDy
            vb.coreView.x = contentX
            vb.coreView.y = contentY
            return true
        }

        fun toBorder() {
            if ((vb.coreView.x + vb.coreView.width / 2f).roundToInt() > screenWidth / 2) {
                // to right
                vb.coreView.x = region.right.toFloat() - vb.coreView.width
            } else {
                // to left
                vb.coreView.x = region.left.toFloat()
            }
            if (vb.coreView.y + vb.coreView.height > region.bottom) {
                // to bottom
                vb.coreView.y = region.bottom.toFloat() - vb.coreView.height
            } else if (vb.coreView.y < region.top) {
                // to top
                vb.coreView.y = region.top.toFloat()
            }
            contentX = vb.coreView.x
            contentY = vb.coreView.y
        }

        private fun touchIn(view: View, event: MotionEvent, offsetLeft: Int = 0, offsetRight: Int = 0): Boolean {
            view.getLocationOnScreen(location)
            viewRect.left = location[0] + offsetLeft
            viewRect.right = location[0] + view.width + offsetRight
            viewRect.top = location[1]
            viewRect.bottom = location[1] + view.height
            return viewRect.contains(event.rawX.roundToInt(), event.rawY.roundToInt())
        }
    }

}