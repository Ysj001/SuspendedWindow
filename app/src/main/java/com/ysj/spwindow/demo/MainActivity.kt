package com.ysj.spwindow.demo

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/**
 * demo 入口。
 *
 * @author Ysj
 * Create time: 2023/1/10
 */
class MainActivity : AppCompatActivity(R.layout.activity_main) {


    fun onDemo1Clicked(view: View) {
        startActivity(Intent(this, Demo1Activity::class.java))
    }

}