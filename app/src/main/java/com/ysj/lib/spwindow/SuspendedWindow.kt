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
import androidx.collection.ArraySet
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

    companion object {
        private const val KEY_BLACK_LIST = "KEY_BLACK_LIST"
        private const val KEY_INTERCEPT_LIST = "KEY_INTERCEPT_LIST"

        private val SuspendedWindow.keyPrefix
            get() = "${javaClass.name}@${Integer.toHexString(hashCode())}:"
    }

    private val activityCallback = ActivityCallback()

    private val blacklist = ArraySet<Class<out Activity>>()

    private val interceptList = ArraySet<Class<out Activity>>()

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

    fun addBlackList(vararg clazz: Class<out Activity>) {
        for (index in clazz.indices) {
            blacklist.add(clazz[index])
        }
    }

    fun removeBlackList(vararg clazz: Class<out Activity>) {
        for (index in clazz.indices) {
            blacklist.remove(clazz[index])
        }
    }

    fun addInterceptList(vararg clazz: Class<out Activity>) {
        for (index in clazz.indices) {
            interceptList.add(clazz[index])
        }
    }

    fun removeInterceptList(vararg clazz: Class<out Activity>) {
        for (index in clazz.indices) {
            interceptList.remove(clazz[index])
        }
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
        application.unregisterActivityLifecycleCallbacks(activityCallback)
        if (register) {
            application.registerActivityLifecycleCallbacks(activityCallback)
        }
        activityCallback.registered = register
    }

    private inner class ActivityCallback : Application.ActivityLifecycleCallbacks {

        var registered = false

        private var newSpWindow: SuspendedWindow? = null

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity == getAssociatedActivity() || newSpWindow != null) {
                return
            }
            if (window.attributes.type == overlayType()) {
                return
            }
            val actClazz = activity.javaClass
            if (actClazz in interceptList) {
                dismiss()
                return
            }
            val spWindow = onCreateNewWindow(activity)
            spWindow.blacklist.addAll(blacklist)
            spWindow.interceptList.addAll(interceptList)
            dismissInLifecycle()
            if (actClazz in blacklist) {
                spWindow.setActivityLifecycleCallback(true)
            } else {
                spWindow.show()
            }
            newSpWindow = spWindow
        }

        override fun onActivityStarted(activity: Activity) {
            if (activity == getAssociatedActivity()) {
                destroyNewWindow(activity)
                this@SuspendedWindow.onActivityStarted(activity)
            }
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity == getAssociatedActivity()) {
                destroyNewWindow(activity)
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

        private fun destroyNewWindow(activity: Activity) {
            val spWindow = newSpWindow ?: return
            if (spWindow.activityCallback.registered) {
                blacklist.addAll(spWindow.blacklist)
                interceptList.addAll(spWindow.interceptList)
                onDestroyNewWindow(spWindow)
                spWindow.dismiss()
                if (activity.javaClass !in blacklist) {
                    showInLifecycle()
                }
            } else {
                setActivityLifecycleCallback(false)
            }
            newSpWindow = null
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