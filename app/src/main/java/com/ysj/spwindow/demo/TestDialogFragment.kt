package com.ysj.spwindow.demo

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * @author xcc
 * @date 2023/1/24
 */
class TestDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

        }
        val titleBar = AppCompatTextView(requireContext()).apply {
            textSize = 20f
            text = "标题栏"
            setBackgroundColor(Color.WHITE)
            setPadding(10)
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        val view = View(requireContext()).apply {
            setBackgroundColor(Color.LTGRAY)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }

        val editText = AppCompatEditText(requireContext()).apply {
            textSize = 20f
            hint = "hahahahaha"
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        return root.apply {
            addView(titleBar)
            addView(view)
            addView(editText)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initWindow(view)
        initBehavior(view)
    }

    private fun initWindow(view: View) {
        val window = dialog!!.window!!
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 兼容Android各版本IME的insets分发
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // 状态栏和手势导航栏边到边的处理：
        // 1. 不将insets应用到decorView，去除decorView自身的导航栏视图高度和背景色
        // 2. 跳过decorView到view之间的insets分发，确保view本身能得到insets分发。
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            this.view?.let { ViewCompat.dispatchApplyWindowInsets(it, insets) }
            WindowInsetsCompat.CONSUMED
        }

        // Android各版本insets分发正常，insets动画正常
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.ime())
            insets
        }

        ViewCompat.setWindowInsetsAnimationCallback(
            view,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    insets.getInsets(WindowInsetsCompat.Type.ime())
                    return insets
                }
            }
        )
    }

    private fun initBehavior(view: View) {
        val behavior = view.parent?.let { it as? ViewGroup }
            ?.layoutParams?.let { it as? CoordinatorLayout.LayoutParams }
            ?.let { it.behavior as? BottomSheetBehavior<View> } ?: return

        val window = dialog!!.window!!
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.peekHeight = requireActivity().window.decorView.height
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            init {
                controller.isAppearanceLightStatusBars = true
                view.doOnAttach { (it.parent as View).let(this::updatePadding) }
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                updatePadding(bottomSheet)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                updatePadding(bottomSheet)
            }

            private fun updatePadding(bottomSheet: View) {
                val rootInsets = ViewCompat.getRootWindowInsets(bottomSheet) ?: return
                val statusBars = rootInsets.getInsets(WindowInsetsCompat.Type.statusBars())
                if (bottomSheet.top < statusBars.top) {
                    controller.isAppearanceLightStatusBars = true
                    view.updatePadding(top = statusBars.top - bottomSheet.top)
                } else if (bottomSheet.top != 0) {
                    controller.isAppearanceLightStatusBars = false
                    view.updatePadding(top = 0)
                }
            }
        })
    }
}