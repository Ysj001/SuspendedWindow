package com.ysj.spwindow.demo

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat

/**
 *
 *
 * @author Ysj
 * Create time: 2023/1/10
 */
class Demo3Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wc = WindowInsetsControllerCompat(window, window.decorView)
        window.statusBarColor = Color.WHITE
        wc.isAppearanceLightStatusBars = true
        setContentView(TextView(this).also {
            it.text = "I'm Demo3!"
            it.setTextColor(Color.BLUE)
            it.gravity = Gravity.CENTER
        })
    }
}