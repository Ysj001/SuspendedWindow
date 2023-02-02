package com.ysj.lib.spwindow

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.view.WindowManager
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.res.ResourcesCompat

/**
 * Base class for Suspended Window.
 *
 * @author Ysj
 * Create time: 2023/1/10
 */
open class SuspendedWindow @JvmOverloads constructor(
    context: Context,
    @StyleRes protected val themeId: Int = ResourcesCompat.ID_NULL,
) : AppCompatDialog(context, themeId) {

    private val activityCallback = ActivityCallback()

    override fun getWindow(): Window = checkNotNull(super.getWindow()) {
        "Dialog $this does not have a window."
    }

    override fun onStart() {
        super.onStart()
        setActivityLifecycleCallback(true)
    }

    override fun onStop() {
        super.onStop()
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

    protected open fun onActivityStarted(activity: Activity) = Unit

    protected open fun onActivityResumed(activity: Activity) = Unit

    protected open fun onActivityPaused(activity: Activity) = Unit

    protected open fun onActivityStopped(activity: Activity) = Unit

    protected open fun onActivityDestroyed(activity: Activity) = Unit

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
            if (activity == getAssociatedActivity()) {
                this@SuspendedWindow.onActivityStarted(activity)
                if (spWindow != null) {
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
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity == getAssociatedActivity()) {
                this@SuspendedWindow.onActivityResumed(activity)
            }
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity == getAssociatedActivity()) {
                this@SuspendedWindow.onActivityPaused(activity)
            }
        }

        override fun onActivityStopped(activity: Activity) {
            if (activity == getAssociatedActivity()) {
                this@SuspendedWindow.onActivityStopped(activity)
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            if (activity == getAssociatedActivity()) {
                this@SuspendedWindow.onActivityDestroyed(activity)
                Handler(Looper.getMainLooper()).post {
                    setActivityLifecycleCallback(false)
                }
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