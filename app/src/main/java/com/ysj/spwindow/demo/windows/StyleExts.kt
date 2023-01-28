package com.ysj.spwindow.demo.windows

import android.app.Dialog
import android.graphics.Color
import android.view.Window
import androidx.annotation.FloatRange
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/*
 * 一些通用样式的扩展
 *
 * @author Ysj
 * Create time: 2023/1/28
 */

/**
 * 部分手机给 systemBar 设置 alpha 为 0 的 Color 不会全透明，此时可以使用该值。
 */
const val COLOR_TRANSPARENT: Int = 0x01FFFFFF

/**
 * 部分手机给 systemBar 设置 [Color.BLACK] 会被忽略，此时可用该值。
 */
const val COLOR_BLACK: Int = 0xFF000001.toInt()


fun Dialog.windowController() = lazy(LazyThreadSafetyMode.NONE) {
    checkNotNull(window).controller()
}

fun Window.controller() = WindowController(this, WindowInsetsControllerCompat(this, decorView))


class WindowController(
    private val window: Window,
    private val controller: WindowInsetsControllerCompat
) {

    fun statusBarColor(color: Int) = apply {
        val convertColor = convertColor(color)
        window.statusBarColor = convertColor
        controller.isAppearanceLightStatusBars = luminance(convertColor) > 0.5f
    }

    fun navigationBarColor(color: Int) = apply {
        val convertColor = convertColor(color)
        window.navigationBarColor = convertColor
        controller.isAppearanceLightNavigationBars = luminance(convertColor) > 0.5f
    }

    fun showStatusBar() = controller.show(WindowInsetsCompat.Type.statusBars())
    fun hideStatusBar() = controller.hide(WindowInsetsCompat.Type.statusBars())

    fun showNavigationBar() = controller.show(WindowInsetsCompat.Type.navigationBars())
    fun hideNavigationBar() = controller.hide(WindowInsetsCompat.Type.navigationBars())

    @FloatRange(from = 0.0, to = 1.0)
    fun luminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return ((0.2126f * r) + (0.7152f * g) + (0.0722f * b))
    }

    private fun convertColor(color: Int) = when {
        color == Color.BLACK -> COLOR_BLACK
        Color.alpha(color) == 0 -> COLOR_TRANSPARENT
        else -> color
    }
}