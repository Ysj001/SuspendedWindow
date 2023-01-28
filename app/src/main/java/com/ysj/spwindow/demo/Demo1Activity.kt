package com.ysj.spwindow.demo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.ysj.spwindow.demo.windows.VideoSpWindow

/**
 *
 *
 * @author Ysj
 * Create time: 2023/1/10
 */
class Demo1Activity : AppCompatActivity(R.layout.activity_demo1) {

    private val demoWindow by lazy(LazyThreadSafetyMode.NONE) {
        VideoSpWindow(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wc = WindowInsetsControllerCompat(window, window.decorView)
        window.statusBarColor = Color.BLUE
        window.navigationBarColor = Color.RED
        wc.isAppearanceLightStatusBars = true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            Log.d("Demo1Activity", "dispatchTouchEvent: (${ev.x} , ${ev.y}) , (${ev.rawX} , ${ev.rawY})")
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return super.onTouchEvent(event)
    }

    fun onToDemo2(view: View) {
        startActivity(Intent(this, Demo2Activity::class.java))
    }

    fun onSpwShow(view: View) {
        // 拖拽边界测试
//        demoWindow.setBorder(50, 80, 20, 120)
        demoWindow.show()
        // 横屏测试
//        demoWindow.setVideoSource("https://vd2.bdstatic.com/mda-mkbkn3298fc4qgia/sc/cae_h264/1636727844173084473/mda-mkbkn3298fc4qgia.mp4?v_from_s=hkapp-haokan-tucheng&auth_key=1673952367-0-0-942cc56206f7120a7bc07bfce6a8820b&bcevod_channel=searchbox_feed&pd=1&cd=0&pt=3&logid=0966589247&vid=1055240032501393989&abtest=&klogid=0966589247")
        // 竖屏测试
        demoWindow.setVideoSource("https://video.699pic.com/videos/73/92/65/b_Ab0T8auStrwE1597739265.mp4")
//        demoWindow.setVideoSource("/data/data/com.ysj.spwindow.demo/cache/left.mp4")
    }

    fun onSpwHide(view: View) {
        demoWindow.dismiss()
    }

}