package com.ysj.spwindow.demo

import android.content.Intent
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
class Demo2Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wc = WindowInsetsControllerCompat(window, window.decorView)
        window.statusBarColor = Color.WHITE
        wc.isAppearanceLightStatusBars = true
        setContentView(TextView(this).also {
            it.text = "I'm Demo2!\n\nclick me to demo3"
            it.setTextColor(Color.RED)
            it.gravity = Gravity.CENTER
            it.setOnClickListener {
                startActivity(Intent(this, Demo3Activity::class.java))
            }
        })
    }
}