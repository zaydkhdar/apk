package com.example.androidkeyboardunofficial

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val purple = 0xFF4F46E5.toInt()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun box(color: Int, radiusDp: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setShape(GradientDrawable.RECTANGLE)
        d.setCornerRadius(dp(radiusDp).toFloat())
        d.setColor(color)
        return d
    }

    private fun params(topDp: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.setMargins(0, dp(topDp), 0, 0)
        return p
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun button(text: String, filled: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 16f
        t.gravity = Gravity.CENTER
        t.setTextColor(if (filled) Color.WHITE else purple)
        t.background = box(if (filled) purple else 0xFFE6E7FB.toInt(), 14)
        t.setPadding(dp(16), dp(14), dp(16), dp(14))
        t.isClickable = true
        t.setOnClickListener { onClick() }
        return t
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val memory = WordMemory(this)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(dp(20), dp(36), dp(20), dp(28))
        layout.setBackgroundColor(0xFFF4F5F8.toInt())

        layout.addView(label("Android Keyboard", 28f, 0xFF171820.toInt(), true))
        layout.addView(label("Unofficial · Version 2.0", 14f, 0xFF686B76.toInt()))
        layout.addView(label(
            "Smart typing, emoji, special characters and a clipboard. Everything stays on your phone. Nothing is uploaded.",
            15f, 0xFF454752.toInt()), params(12))

        layout.addView(label("Set up", 18f, 0xFF171820.toInt(), true), params(24))
        layout.addView(button("1. Enable keyboard", true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }, params(10))
        layout.addView(button("2. Choose keyboard", true) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, params(10))

        layout.addView(label("3. Try it", 18f, 0xFF171820.toInt(), true), params(24))
        val test = EditText(this)
        test.hint = "Tap here and type..."
        test.textSize = 16f
        test.setTextColor(0xFF171820.toInt())
        test.background = box(Color.WHITE, 12)
        test.setPadding(dp(14), dp(14), dp(14), dp(14))
        test.minLines = 3
        test.gravity = Gravity.TOP
        layout.addView(test, params(10))

        layout.addView(label("Tips", 18f, 0xFF171820.toInt(), true), params(24))
        layout.addView(label(
            "• Hold ⌫ to delete fast\n" +
            "• Hold a letter for accents (é, ñ, ü...)\n" +
            "• Tap ⇧ twice for caps lock\n" +
            "• 😊 opens emoji, 📋 opens your clipboard\n" +
            "• Hold space to switch keyboards\n" +
            "• Backspace right after an auto-fix undoes it",
            15f, 0xFF454752.toInt()), params(8))

        layout.addView(label("Privacy", 18f, 0xFF171820.toInt(), true), params(24))
        layout.addView(button("Erase typing history", false) {
            memory.clearHistory()
            toast("Typing history erased")
        }, params(10))
        layout.addView(button("Erase learned words", false) {
            memory.clearWords()
            toast("Learned words erased")
        }, params(8))
        layout.addView(button("Erase clipboard history", false) {
            ClipStore(this).clearAll()
            toast("Clipboard history erased")
        }, params(8))
        layout.addView(label(
            "Android warns that third-party keyboards can see what you type. This app has no internet permission. Avoid using it for passwords or very sensitive text.",
            12f, 0xFF686B76.toInt()), params(18))

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(0xFFF4F5F8.toInt())
        scroll.addView(layout)
        setContentView(scroll)
    }
}
