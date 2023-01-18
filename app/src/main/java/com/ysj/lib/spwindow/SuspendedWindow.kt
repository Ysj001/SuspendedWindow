package com.ysj.lib.spwindow

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.view.WindowManager
import androidx.annotation.FloatRange
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Base class for Suspended Window.
 *
 * @author Ysj
 * Create time: 2023/1/10
 */
open class SuspendedWindow @JvmOverloads constructor(
    context: Context,
    @StyleRes protected val themeId: Int = 0,
) : AppCompatDialog(context, themeId) {

    companion object {
        private const val TAG = "SuspendedWindow"
    }

    protected var startStatusBarColor = 0
    protected var startNavigationBarColor = 0
    protected var startStatusBarShow = true
    protected var startNavigationBarShow = true

    private val activityCallback = ActivityCallback()

    override fun getWindow(): Window = checkNotNull(super.getWindow()) {
        "Dialog $this does not have a window."
    }

    override fun onStart() {
        super.onStart()
        setActivityLifecycleCallback(true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val associatedActivity = getAssociatedActivity()
        if (associatedActivity != null) {
            val aw = associatedActivity.window
            startStatusBarColor = aw.statusBarColor
            startNavigationBarColor = aw.navigationBarColor
            val insets = ViewCompat.getRootWindowInsets(aw.decorView)
            if (insets != null) {
                startStatusBarShow = insets.isVisible(WindowInsetsCompat.Type.statusBars())
                startNavigationBarShow = insets.isVisible(WindowInsetsCompat.Type.navigationBars())
            }
        }
        Log.d(TAG, "associatedActivity=$associatedActivity , statusBarColor=$startStatusBarColor , navigationBarColor=$startNavigationBarColor")
    }

    override fun onStop() {
        super.onStop()
        setSystemBarColor(startStatusBarColor, startNavigationBarColor)
        showSystemBar(startStatusBarShow, startNavigationBarShow)
        setActivityLifecycleCallback(false)
    }

    protected open fun onCreateNewWindow(context: Context): SuspendedWindow {
        val spWindow = javaClass
            .getConstructor(Context::class.java, Int::class.java)
            .newInstance(context, themeId)
        val currentParams = window.attributes
        val newParams = spWindow.window.attributes
        newParams.width = currentParams.width
        newParams.height = currentParams.height
        newParams.x = currentParams.x
        newParams.y = currentParams.y
        return spWindow
    }

    protected open fun onDestroyNewWindow(spWindow: SuspendedWindow) {
        val currentParams = window.attributes
        val newParams = spWindow.window.attributes
        currentParams.width = newParams.width
        currentParams.height = newParams.height
        currentParams.x = newParams.x
        currentParams.y = newParams.y
    }

    protected open fun onActivityResume(activity: Activity) = Unit
    protected open fun onActivityPause(activity: Activity) = Unit

    /**
     *  @return The activity associated with this dialog, or null if there is no associated activity.
     */
    protected open fun getAssociatedActivity(): Activity? {
        var activity: Activity? = ownerActivity
        var context: Context? = context
        while (activity == null && context != null) {
            if (context is Activity) {
                activity = context
            } else {
                context = if (context is ContextWrapper) context.baseContext else null
            }
        }
        return activity
    }

    protected fun overlayType() = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> WindowManager.LayoutParams.TYPE_TOAST
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_PHONE
        else -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }

    protected fun showSystemBar(statusBar: Boolean, navigationBar: Boolean) {
        val associatedActivity = getAssociatedActivity() ?: return
        val aw = associatedActivity.window
        val wc = WindowInsetsControllerCompat(aw, aw.decorView)
        if (statusBar) {
            wc.show(WindowInsetsCompat.Type.statusBars())
        } else {
            wc.hide(WindowInsetsCompat.Type.statusBars())
        }
        if (navigationBar) {
            wc.show(WindowInsetsCompat.Type.navigationBars())
        } else {
            wc.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    protected fun setSystemBarColor(statusBar: Int, navigationBar: Int) {
        val associatedActivity = getAssociatedActivity() ?: return
        val aw = associatedActivity.window
        val wc = WindowInsetsControllerCompat(aw, aw.decorView)
        aw.statusBarColor = statusBar
        wc.isAppearanceLightStatusBars = luminance(statusBar) > 0.5f
        aw.navigationBarColor = navigationBar
        wc.isAppearanceLightNavigationBars = luminance(statusBar) > 0.5f
    }

    @FloatRange(from = 0.0, to = 1.0)
    protected fun luminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return ((0.2126f * r) + (0.7152f * g) + (0.0722f * b))
    }

    private fun setActivityLifecycleCallback(register: Boolean) {
        val application = context.applicationContext as Application
        if (register) {
            application.registerActivityLifecycleCallbacks(activityCallback)
        } else {
            application.unregisterActivityLifecycleCallbacks(activityCallback)
        }
    }

    private inner class ActivityCallback : Application.ActivityLifecycleCallbacks {

        private var newSpWindow: SuspendedWindow? = null

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity == getAssociatedActivity() || newSpWindow != null) {
                return
            }
            if (window.attributes.type == overlayType()) {
                return
            }
            val spWindow = onCreateNewWindow(activity)
            dismissInLifecycle()
            spWindow.show()
            newSpWindow = spWindow
        }

        override fun onActivityStarted(activity: Activity) {
            val spWindow = newSpWindow
            if (spWindow != null && activity == getAssociatedActivity()) {
                if (spWindow.isShowing) {
                    onDestroyNewWindow(spWindow)
                    spWindow.dismiss()
                    showInLifecycle()
                } else {
                    setActivityLifecycleCallback(false)
                }
                newSpWindow = null
            }
        }

        override fun onActivityResumed(activity: Activity) = onActivityResume(activity)
        override fun onActivityPaused(activity: Activity) = onActivityPause(activity)
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            if (activity == getAssociatedActivity()) {
                dismiss()
            }
        }

        private fun dismissInLifecycle() {
            dismiss()
            setActivityLifecycleCallback(true)
        }

        private fun showInLifecycle() {
            setActivityLifecycleCallback(false)
            show()
        }
    }
}