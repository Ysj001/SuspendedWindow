package com.ysj.spwindow.demo.windows

import android.app.Dialog
import android.graphics.Color
import android.view.Window
import androidx.annotation.ColorInt
import androidx.core.view.WindowInsetsControllerCompat
import java.util.*

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

    private val styleStack = Stack<Style>()

    var statusBarColor: Int
        @ColorInt get() = window.statusBarColor
        set(@ColorInt value) {
            val convertColor = convertColor(value)
            window.statusBarColor = convertColor
        }

    var navigationBarColor: Int
        @ColorInt get() = window.navigationBarColor
        set(@ColorInt value) {
            val convertColor = convertColor(value)
            window.navigationBarColor = convertColor
        }

    var isLightStatusBar: Boolean
        get() = controller.isAppearanceLightStatusBars
        set(value) {
            controller.isAppearanceLightStatusBars = value
        }

    var isLightNavigationBar: Boolean
        get() = controller.isAppearanceLightNavigationBars
        set(value) {
            controller.isAppearanceLightNavigationBars = value
        }

    /**
     * 保存当前样式。
     */
    fun saveStyle() {
        styleStack.push(Style(
            statusBarColor, navigationBarColor,
            isLightStatusBar, isLightNavigationBar,
        ))
    }

    /**
     * 还原之前保存的样式。
     */
    fun restoreStyle() {
        if (styleStack.empty()) {
            return
        }
        val style: Style = styleStack.pop()
        statusBarColor = style.statusBarColor
        navigationBarColor = style.navigationBarColor
        isLightStatusBar = style.isLightStatusBar
        isLightNavigationBar = style.isLightNavigationBar
    }

    private fun convertColor(color: Int) = when {
        color == Color.BLACK -> COLOR_BLACK
        Color.alpha(color) == 0 -> COLOR_TRANSPARENT
        else -> color
    }

    private class Style(
        val statusBarColor: Int,
        val navigationBarColor: Int,
        val isLightStatusBar: Boolean,
        val isLightNavigationBar: Boolean,
    )
}