package com.example.androidkeyboardunofficial

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val memory = WordMemory(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 42, 28, 28)
            setBackgroundColor(0xFFF4F5F8.toInt())
        }
        layout.addView(TextView(this).apply {
            text = "Android Keyboard [Unofficial]"
            textSize = 23f
            setTextColor(0xFF171820.toInt())
        })
        layout.addView(TextView(this).apply {
            text = "\nVersion 3 · English / Français / العربية\n\nSmart Memory learns individual words and keeps a local history on this device. The clipboard (📋 key) keeps your recent copies on this device too. Nothing is uploaded."
            textSize = 15f
            setTextColor(0xFF454752.toInt())
        })
        layout.addView(Button(this).apply {
            text = "Enable keyboard"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        layout.addView(Button(this).apply {
            text = "Choose keyboard"
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })
        layout.addView(Button(this).apply {
            text = "Erase typing history"
            setOnClickListener {
                memory.clearHistory()
                android.widget.Toast.makeText(this@MainActivity, "Typing history erased", android.widget.Toast.LENGTH_SHORT).show()
            }
        })
        layout.addView(Button(this).apply {
            text = "Erase learned words"
            setOnClickListener {
                memory.clearWords()
                android.widget.Toast.makeText(this@MainActivity, "Learned words erased", android.widget.Toast.LENGTH_SHORT).show()
            }
        })
        layout.addView(Button(this).apply {
            text = "Erase clipboard history"
            setOnClickListener {
                ClipStore(this@MainActivity).clearAll()
                android.widget.Toast.makeText(this@MainActivity, "Clipboard history erased", android.widget.Toast.LENGTH_SHORT).show()
            }
        })
        layout.addView(TextView(this).apply {
            text = "\nPrivacy note: Android warns that third-party keyboards can process what you type. This app has no network permission. Avoid using it for passwords or sensitive information."
            textSize = 12f
            setTextColor(0xFF686B76.toInt())
        })
        setContentView(layout)
    }
}
