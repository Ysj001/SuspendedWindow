package com.ysj.spwindow.demo.windows

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 通用触摸事件处理器
 *
 * @author Ysj
 * Create time: 2021/11/12
 */
abstract class TouchHandler : View.OnTouchListener {

    // ============== 事件基础参数 ===============
    /** 主手指按下时的 X */
    var majorDownX: Float = 0f
        private set

    /** 主手指按下时的 Y */
    var majorDownY: Float = 0f
        private set

    /** 次手指按下时的 X */
    var minorDownX: Float = 0f
        private set

    /** 次手指按下时的 Y */
    var minorDownY: Float = 0f
        private set

    /** 主手指上次事件时的 X */
    var beforeMajorX: Float = 0f
        private set

    /** 主手指上次事件时的 Y */
    var beforeMajorY: Float = 0f
        private set

    /** 次手指上次事件时的 X */
    var beforeMinorX: Float = 0f
        private set

    /** 次手指上次事件时的 Y */
    var beforeMinorY: Float = 0f
        private set

    // ============== 事件处理参数 ==============
    /** 最后一个手指的索引 */
    val MotionEvent.lastPointerIndex get() = pointerCount - 1

    /** 相对 [beforeMajorX] X 移动的距离 */
    val MotionEvent.relDx: Float get() = x - beforeMajorX

    /** 相对 [beforeMajorY] Y 移动的距离 */
    val MotionEvent.relDy: Float get() = y - beforeMajorY

    /** 相对 [majorDownX] X 移动的距离 */
    val MotionEvent.absDx: Float get() = x - majorDownX

    /** 相对 [majorDownY] Y 移动的距离 */
    val MotionEvent.absDy: Float get() = x - majorDownY

    /** 当前主手指和次手指的距离 */
    val MotionEvent.spacing: Float get() = spacing(x - getX(lastPointerIndex), y - getY(lastPointerIndex))

    /** 上一次事件主手指和次手指的距离 */
    val beforeSpacing: Float get() = spacing(beforeMajorX - beforeMinorX, beforeMajorY - beforeMinorY)

    /** 当前主手指和次手指的角度 */
    val MotionEvent.degree: Float get() = degrees(x, getX(lastPointerIndex), y, getY(lastPointerIndex))

    /** 上一次事件主手指和次手指的角度 */
    val beforeDegree: Float get() = degrees(beforeMajorX, beforeMinorX, beforeMajorY, beforeMinorY)

    /** 相对上次事件旋转的角度 */
    val MotionEvent.relRotation: Float get() = degree - beforeDegree

    /** 相对 down 事件旋转的角度 */
    val MotionEvent.absRotation: Float get() = degree - degrees(majorDownX, minorDownX, majorDownY, minorDownY)

    /** 相对上次事件缩放的值 */
    val MotionEvent.relScale: Float get() = spacing / beforeSpacing

    /** 相对 down 事件缩放的值 */
    val MotionEvent.absScale: Float get() = spacing / spacing(majorDownX - minorDownX, majorDownY - minorDownY)

    /** [ViewConfiguration] down 后初始化 */
    lateinit var config: ViewConfiguration
        private set

    /** 若要处理抛掷，则对其赋值 */
    open val flingHandler: FlingHandler? = null

    /** 是否在 [onSlop] 定义的 slop 内 */
    var isInSlop: Boolean = true
        private set

    // 是否在点击的抖动范围内
    private var isInClickSlop: Boolean = true
    open val performClick = PerformClick()
    open val performLongClick = PerformLongClick()

    final override fun onTouch(v: View, event: MotionEvent): Boolean {
        var consumed = false
        val pointerCount = event.pointerCount
        val lastPointerIndex = pointerCount - 1
        val action = event.action
        if (!::config.isInitialized) config = ViewConfiguration.get(v.context)
        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                majorDownX = event.x
                majorDownY = event.y
                beforeMajorX = majorDownX
                beforeMajorY = majorDownY
                isInSlop = true
                consumed = onStart(event)
                if (consumed) flingHandler?.onDown(event)
                resetClick(v, consumed)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                performClick.cancel = true
                v.removeCallbacks(performLongClick)
                val actionIndex = event.actionIndex
                if (actionIndex == 0) {
                    majorDownX = event.x
                    majorDownY = event.y
                    beforeMajorX = majorDownX
                    beforeMajorY = majorDownY
                } else if (actionIndex == lastPointerIndex) {
                    minorDownX = event.getX(actionIndex)
                    minorDownY = event.getY(actionIndex)
                    beforeMinorX = minorDownX
                    beforeMinorY = minorDownY
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (performLongClick.consumedLongClick) {
                    flingHandler?.cancel()
                    return true
                }
                flingHandler?.onMove(event)
                if (isInSlop) isInSlop = onSlop(event)
                consumed = isInSlop || when {
                    pointerCount == 1 -> onMove(event)
                    pointerCount > 1 -> onTransform(event)
                    else -> false
                }
                beforeMajorX = event.x
                beforeMajorY = event.y
                beforeMinorX = event.getX(lastPointerIndex)
                beforeMinorY = event.getY(lastPointerIndex)
                checkClickSlop(v)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val actionIndex = event.actionIndex
                if (actionIndex == 0) {
                    majorDownX = event.getX(1)
                    majorDownY = event.getY(1)
                    beforeMajorX = majorDownX
                    beforeMajorY = majorDownY
                } else if (actionIndex == lastPointerIndex) {
                    minorDownX = event.getX(lastPointerIndex - 1)
                    minorDownY = event.getY(lastPointerIndex - 1)
                    beforeMinorX = minorDownX
                    beforeMinorY = minorDownY
                }
                flingHandler?.onUpPointer(event)
            }
            MotionEvent.ACTION_UP -> {
                beforeMajorX = event.x
                beforeMajorY = event.y
                beforeMinorX = event.getX(lastPointerIndex)
                beforeMinorY = event.getY(lastPointerIndex)
                checkClickSlop(v)
                if (!performClick.cancel) v.post(performClick)
                v.removeCallbacks(performLongClick)
                if (!isInSlop) flingHandler?.onUp(event)
                flingHandler?.cancel()
                onEnd(event)
            }
            MotionEvent.ACTION_CANCEL -> {
                v.removeCallbacks(performLongClick)
                flingHandler?.cancel()
                onEnd(event)
            }
        }
        return consumed
    }

    /**
     * 事件开始 [MotionEvent.ACTION_DOWN]
     *
     * @return 返回 true 表示要处理
     */
    protected abstract fun onStart(event: MotionEvent): Boolean

    /**
     * 事件结束 [MotionEvent.ACTION_UP]，[MotionEvent.ACTION_CANCEL]
     */
    protected open fun onEnd(event: MotionEvent) = Unit

    /**
     * 当点击时回调
     */
    protected open fun onClick() = Unit

    /**
     * 当长按时回调
     */
    protected open fun onLongClick(): Boolean = false

    /**
     * 定义 slop。当可以响应 [MotionEvent.ACTION_MOVE] 时回调。
     *
     * @return 返回 true 表示在 slop 范围内，此时不会响应 [onMove]，[onTransform]
     */
    protected open fun onSlop(event: MotionEvent): Boolean = event.pointerCount == 1 && isInClickSlop

    /**
     * 当处于单手指且可以响应 [MotionEvent.ACTION_MOVE] 时回调
     *
     * @return 返回 true 表示要处理
     */
    protected open fun onMove(event: MotionEvent): Boolean = false

    /**
     * 当 手指数 > 2 且可以响应 [MotionEvent.ACTION_MOVE] 时回调
     *
     * @return 返回 true 表示要处理
     */
    protected open fun onTransform(event: MotionEvent): Boolean = false

    /** 旋转角度 */
    protected fun degrees(x1: Float, x2: Float, y1: Float, y2: Float): Float {
        val deltaX = x1 - x2
        val deltaY = y1 - y2
        val radians = atan2(deltaY, deltaX)
        return Math.toDegrees(radians.toDouble()).toFloat()
    }

    /** 两点间距 */
    protected fun spacing(a: Float, b: Float): Float = sqrt(a.pow(2) + b.pow(2))

    /** 两点中心 */
    protected fun center(a: Float, b: Float): Float = (a + b) / 2

    private fun checkClickSlop(view: View) {
        if (!isInClickSlop) return
        isInClickSlop = config.scaledTouchSlop.let { abs(majorDownX - beforeMajorX) < it && abs(majorDownY - beforeMajorY) < it }
        if (isInClickSlop) return
        performClick.cancel = true
        view.removeCallbacks(performLongClick)
    }

    private fun resetClick(view: View, consumedDown: Boolean) {
        isInClickSlop = true
        performClick.cancel = !consumedDown
        performClick.eventStartTime = System.currentTimeMillis()
        performLongClick.consumedLongClick = false
        if (consumedDown) view.postDelayed(performLongClick, ViewConfiguration.getLongPressTimeout().toLong())
    }

    open inner class PerformClick : Runnable {
        /** 事件的开始时间 */
        var eventStartTime: Long = 0L
        var cancel: Boolean = false
        override fun run() {
            if (cancel) return
            if (System.currentTimeMillis() - eventStartTime >= ViewConfiguration.getTapTimeout()) return
            onClick()
        }
    }

    open inner class PerformLongClick : Runnable {
        var consumedLongClick = false
        override fun run() {
            consumedLongClick = onLongClick()
        }
    }

    abstract inner class FlingHandler {
        private val maximumFlingVelocity get() = config.scaledMaximumFlingVelocity.toFloat()
        private val minimumFlingVelocity get() = config.scaledMinimumFlingVelocity.toFloat()

        private var velocityTracker: VelocityTracker? = null

        open fun onDown(event: MotionEvent) {
            if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
            velocityTracker?.addMovement(event)
        }

        open fun onMove(event: MotionEvent) {
            velocityTracker?.addMovement(event)
        }

        open fun onUpPointer(event: MotionEvent) {
            val velocityTracker = this.velocityTracker ?: return
            // Check the dot product of current velocities.
            // If the pointer that left was opposing another velocity vector, clear.
            velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity)
            val upIndex: Int = event.actionIndex
            val id1: Int = event.getPointerId(upIndex)
            val x1: Float = velocityTracker.getXVelocity(id1)
            val y1: Float = velocityTracker.getYVelocity(id1)
            for (i in 0 until event.pointerCount) {
                if (i == upIndex) continue
                val id2: Int = event.getPointerId(i)
                val x: Float = x1 * velocityTracker.getXVelocity(id2)
                val y: Float = y1 * velocityTracker.getYVelocity(id2)
                val dot = x + y
                if (dot < 0) {
                    velocityTracker.clear()
                    break
                }
            }
        }

        open fun onUp(event: MotionEvent) {
            val velocityTracker = this.velocityTracker ?: return
            velocityTracker.addMovement(event)
            velocityTracker.computeCurrentVelocity(1000 /* 1000ms 内运动了多少 px */, maximumFlingVelocity)
            val pointerId = event.getPointerId(0)
            val velocityY = velocityTracker.getYVelocity(pointerId)
            val velocityX = velocityTracker.getXVelocity(pointerId)
            if ((abs(velocityY) > minimumFlingVelocity || abs(velocityX) > minimumFlingVelocity)) {
                onFling(velocityX, velocityY)
            }
        }

        fun cancel() {
            velocityTracker?.recycle()
            velocityTracker = null
        }

        /**
         * Notified of a fling event when it occurs with the initial on down MotionEvent and the matching up MotionEvent.
         * The calculated velocity is supplied along the x and y axis in pixels per second.
         *
         * @param velocityX – The velocity of this fling measured in pixels per second along the x axis.
         * @param velocityY – The velocity of this fling measured in pixels per second along the y axis.
         */
        abstract fun onFling(velocityX: Float, velocityY: Float)
    }
}