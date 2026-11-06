package com.example.androidkeyboardunofficial

import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class KeyboardService : InputMethodService() {
    private lateinit var board: LinearLayout
    private lateinit var suggestions: LinearLayout
    private var language = "EN"
    private var showClipboard = false
    private var pendingOriginal: String? = null
    private var pendingReplacement: String? = null
    private var pendingDelimiter: String? = null

    private var clipboard: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClipboard() }

    override fun onCreate() {
        super.onCreate()
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    /** Saves newly copied text into the local clipboard history. */
    private fun captureClipboard() {
        try {
            val clip = clipboard?.primaryClip ?: return
            if (clip.itemCount == 0) return
            // Respect apps that flag a copy as sensitive (e.g. password managers, Android 13+).
            if (Build.VERSION.SDK_INT >= 24) {
                val extras = clip.description.extras
                if (extras != null && extras.getBoolean("android.content.extra.IS_SENSITIVE", false)) return
            }
            val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
            ClipStore(this).add(text)
        } catch (e: Exception) {
            // Clipboard not readable right now; ignore.
        }
    }

    override fun onCreateInputView(): View {
        board = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(5, 7, 5, 7)
            setBackgroundColor(0xFFE9EAF0.toInt())
        }
        suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        board.addView(suggestions)
        buildKeys()
        refreshSuggestions()
        return board
    }

    private fun lp(w: Int, h: Int, weight: Float) = LinearLayout.LayoutParams(w, h, weight)

    private fun buildKeys() {
        while (board.childCount > 1) board.removeViewAt(1)
        suggestions.visibility = if (showClipboard) View.GONE else View.VISIBLE
        if (showClipboard) buildClipboard() else buildTyping()
    }

    private fun buildTyping() {
        val rows = when (language) {
            "FR" -> listOf("azertyuiop", "qsdfghjklm", "wxcvbn")
            "AR" -> listOf("ضصثقفغعهخح", "شسيبلاتنم", "ئءؤرلاىةوزظ")
            else -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        header.addView(key(language, { cycleLanguage() }), lp(0, dp(38), 1f))
        header.addView(key("Select all", { editAction(android.R.id.selectAll) }).apply { textSize = 11f }, lp(0, dp(38), 1.5f))
        header.addView(key("Copy", { editAction(android.R.id.copy) }).apply { textSize = 11f }, lp(0, dp(38), 1f))
        header.addView(key("Cut", { editAction(android.R.id.cut) }).apply { textSize = 11f }, lp(0, dp(38), 1f))
        header.addView(key("Paste", { editAction(android.R.id.paste) }).apply { textSize = 11f }, lp(0, dp(38), 1f))
        header.addView(key("📋", { openClipboard() }), lp(0, dp(38), 1f))
        board.addView(header)
        rows.forEach { row ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            row.forEach { character ->
                line.addView(key(character.toString()) { typeText(character.toString()) },
                    LinearLayout.LayoutParams(0, dp(47), 1f).apply { setMargins(2, 3, 2, 3) })
            }
            board.addView(line)
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(key("⇧", {}), lp(0, dp(46), 1f))
        controls.addView(key("⌫", { backspace() }), lp(0, dp(46), 1f))
        board.addView(controls)
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(key("?123", { cycleLanguage() }), lp(0, dp(46), 1f))
        bottom.addView(key(",", { typeText(",") }), lp(0, dp(46), 0.7f))
        bottom.addView(key("space", { typeText(" ") }), lp(0, dp(46), 4f))
        bottom.addView(key(".", { typeText(".") }), lp(0, dp(46), 0.7f))
        bottom.addView(key("↵", { currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)) }), lp(0, dp(46), 1f))
        board.addView(bottom)
    }

    private fun buildClipboard() {
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        top.addView(key("← ABC", { closeClipboard() }).apply { textSize = 12f }, lp(0, dp(38), 1f))
        top.addView(TextView(this).apply {
            text = "Clipboard"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF20212A.toInt())
        }, lp(0, dp(38), 2f))
        top.addView(key("Clear", {
            ClipStore(this).clearUnpinned()
            buildKeys()
        }).apply { textSize = 12f }, lp(0, dp(38), 1f))
        board.addView(top)

        val items = ClipStore(this).all()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Nothing copied yet.\nCopy text in any app and it will show up here."
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(28), dp(16), dp(28))
                setTextColor(0xFF555866.toInt())
            })
        }
        items.forEach { item ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val preview = item.text.replace("\n", " ").trim().take(100)
            row.addView(key(preview, { pasteClip(item.text) }).apply {
                textSize = 13f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(2, 2, 2, 2) })
            row.addView(key(if (item.pinned) "★" else "☆", {
                ClipStore(this).togglePin(item.text)
                buildKeys()
            }), LinearLayout.LayoutParams(dp(44), dp(48)).apply { setMargins(2, 2, 2, 2) })
            row.addView(key("✕", {
                ClipStore(this).remove(item.text)
                buildKeys()
            }), LinearLayout.LayoutParams(dp(44), dp(48)).apply { setMargins(2, 2, 2, 2) })
            list.addView(row)
        }
        val scroll = ScrollView(this)
        scroll.addView(list)
        board.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(230)))
    }

    private fun openClipboard() {
        showClipboard = true
        buildKeys()
    }

    private fun closeClipboard() {
        showClipboard = false
        buildKeys()
        refreshSuggestions()
    }

    private fun pasteClip(text: String) {
        clearPending()
        currentInputConnection?.commitText(text, 1)
        closeClipboard()
    }

    /** Select all / copy / cut / paste on the text field currently being edited. */
    private fun editAction(id: Int) {
        clearPending()
        currentInputConnection?.performContextMenuAction(id)
        refreshSuggestions()
    }

    private fun clearPending() {
        pendingOriginal = null
        pendingReplacement = null
        pendingDelimiter = null
    }

    private fun cycleLanguage() {
        language = when (language) { "EN" -> "FR"; "FR" -> "AR"; else -> "EN" }
        buildKeys()
        refreshSuggestions()
    }

    private fun key(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            textSize = if (label == "space") 12f else 16f
            setTextColor(0xFF20212A.toInt())
            backgroundTintList = ColorStateList.valueOf(
                if (label == "space" || label == "?123") 0xFFD5D7E0.toInt() else 0xFFFFFFFF.toInt())
            setOnClickListener { action() }
        }

    private fun typeText(value: String) {
        val ic = currentInputConnection ?: return
        // Undo is intentionally available only immediately after the correction.
        clearPending()
        ic.commitText(value, 1)
        if (value == " " || value in listOf(".", ",", "!", "?", "\n")) {
            if (!isPrivateField()) {
                val delimiter = value
                val before = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty()
                val typed = before.dropLast(delimiter.length).takeLastWhile { it.isLetter() }
                if (typed.length >= 3) {
                    val correction = WordMemory(this).bestCorrection(typed)
                    if (correction != null && !correction.equals(typed, true)) {
                        pendingOriginal = typed
                        pendingReplacement = correction
                        pendingDelimiter = delimiter
                        ic.deleteSurroundingText(typed.length + delimiter.length, 0)
                        ic.commitText(correction + delimiter, 1)
                        WordMemory(this).learn(correction)
                        refreshSuggestions()
                        return
                    }
                }
                val word = before.dropLast(delimiter.length).takeLastWhile { it.isLetter() }
                if (word.length >= 2) WordMemory(this).learn(word)
            }
        }
        refreshSuggestions()
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (pendingOriginal != null && pendingReplacement != null) {
            val delimiter = pendingDelimiter ?: ""
            ic.deleteSurroundingText(pendingReplacement!!.length + delimiter.length, 0)
            ic.commitText(pendingOriginal, 1)
            clearPending()
        } else ic.deleteSurroundingText(1, 0)
        refreshSuggestions()
    }

    private fun isPrivateField(): Boolean {
        val type = currentInputEditorInfo?.inputType ?: return false
        val variation = type and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun refreshSuggestions() {
        if (!::suggestions.isInitialized) return
        suggestions.removeAllViews()
        val before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString().orEmpty()
        val prefix = before.takeLastWhile { it.isLetter() }
        val words = WordMemory(this).suggest(prefix)
        suggestions.addView(TextView(this).apply {
            text = if (words.isEmpty()) "  $language  " else "  "
            textSize = 12f
            setTextColor(0xFF555866.toInt())
            gravity = Gravity.CENTER
        })
        words.forEach { word ->
            suggestions.addView(Button(this).apply {
                text = word; isAllCaps = false; textSize = 13f
                setOnClickListener {
                    val ic = currentInputConnection ?: return@setOnClickListener
                    val old = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty().takeLastWhile { it.isLetter() }
                    if (old.isNotEmpty()) {
                        pendingOriginal = old; pendingReplacement = word; pendingDelimiter = ""
                        ic.deleteSurroundingText(old.length, 0)
                        ic.commitText(word, 1)
                        if (!isPrivateField()) WordMemory(this@KeyboardService).learn(word)
                        refreshSuggestions()
                    }
                }
            })
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        clearPending()
        if (::board.isInitialized && showClipboard) closeClipboard()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
