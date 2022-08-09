package com.yy.yyeva.player

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yy.yyeva.player.R
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btn1.setOnClickListener {
            startActivity(Intent(this, EvaDemoActivity::class.java))
        }

        btn2.setOnClickListener {
            startActivity(Intent(this, EvaKeyDemoActivity::class.java))
        }
    }
}