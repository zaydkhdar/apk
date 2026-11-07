package com.example.androidkeyboardunofficial

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class KeyboardService : InputMethodService() {
    private enum class Panel { LETTERS, SYMBOLS, SYMBOLS2, EMOJI, CLIPBOARD }

    private class Theme(
        val bg: Int, val key: Int, val keyPressed: Int,
        val special: Int, val specialPressed: Int,
        val text: Int, val hint: Int, val accent: Int
    )

    private lateinit var board: LinearLayout
    private lateinit var suggestions: LinearLayout
    private lateinit var th: Theme
    private var language = "EN"
    private var panel = Panel.LETTERS
    private var shift = 0 // 0 = off, 1 = next letter, 2 = caps lock
    private var lastShiftTap = 0L
    private var emojiCategory = 1
    private val letterKeys = mutableListOf<TextView>()
    private var shiftKey: TextView? = null
    private var pendingOriginal: String? = null
    private var pendingReplacement: String? = null
    private var pendingDelimiter: String? = null

    private var clipboard: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClipboard() }

    private val handler = Handler(Looper.getMainLooper())
    private var repeating = false
    private var repeatCount = 0
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (!repeating) return
            backspace(false)
            repeatCount++
            handler.postDelayed(this, if (repeatCount > 10) 28L else 60L)
        }
    }

    // ---------- lifecycle ----------

    override fun onCreate() {
        super.onCreate()
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        repeating = false
        handler.removeCallbacks(repeatRunnable)
        clipboard?.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        th = theme()
        board = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(4))
            setBackgroundColor(th.bg)
        }
        suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        board.addView(suggestions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)))
        buildKeys()
        refreshSuggestions()
        return board
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        clearPending()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        clearPending()
        if (::board.isInitialized) {
            panel = Panel.LETTERS
            buildKeys()
            updateAutoCaps()
            refreshSuggestions()
        }
    }

    /** Saves newly copied text into the local clipboard history. */
    private fun captureClipboard() {
        try {
            val clip = clipboard?.primaryClip ?: return
            if (clip.itemCount == 0) return
            // Respect apps that flag a copy as sensitive (e.g. password managers, Android 13+).
            val extras = clip.description.extras
            if (extras != null && extras.getBoolean("android.content.extra.IS_SENSITIVE", false)) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
            ClipStore(this).add(text)
        } catch (e: Exception) {
            // Clipboard not readable right now; ignore.
        }
    }

    // ---------- theme & drawing helpers ----------

    private fun theme(): Theme {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) {
            Theme(0xFF1B1B1F.toInt(), 0xFF3A3A40.toInt(), 0xFF55555C.toInt(),
                0xFF2A2A2F.toInt(), 0xFF45454B.toInt(), 0xFFF2F2F5.toInt(), 0xFF9A9AA5.toInt(), 0xFF8E94FF.toInt())
        } else {
            Theme(0xFFD3D6DD.toInt(), 0xFFFFFFFF.toInt(), 0xFFE3E5EA.toInt(),
                0xFFB8BCC6.toInt(), 0xFFA3A8B3.toInt(), 0xFF111114.toInt(), 0xFF6B6F7A.toInt(), 0xFF4F46E5.toInt())
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    private fun rounded(color: Int, radiusDp: Int = 8): GradientDrawable {
        val d = GradientDrawable()
        d.setShape(GradientDrawable.RECTANGLE)
        d.setCornerRadius(dp(radiusDp).toFloat())
        d.setColor(color)
        return d
    }

    private fun keyBackground(normal: Int, pressed: Int): StateListDrawable {
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_pressed), rounded(pressed))
        s.addState(intArrayOf(), rounded(normal))
        return s
    }

    private fun haptic(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun key(label: String, special: Boolean = false, size: Float = 20f, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.gravity = Gravity.CENTER
        t.textSize = size
        t.maxLines = 1
        t.setTextColor(th.text)
        t.background = if (special) keyBackground(th.special, th.specialPressed) else keyBackground(th.key, th.keyPressed)
        t.isClickable = true
        t.setOnClickListener {
            haptic(t)
            onClick()
        }
        return t
    }

    private fun toolButton(label: String, size: Float = 13f, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.gravity = Gravity.CENTER
        t.textSize = size
        t.maxLines = 1
        t.setTextColor(th.text)
        t.background = keyBackground(0x00000000, th.specialPressed)
        t.isClickable = true
        t.setOnClickListener {
            haptic(t)
            onClick()
        }
        return t
    }

    private fun newRow(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        return r
    }

    private fun addKey(row: LinearLayout, view: View, weight: Float = 1f) {
        val params = LinearLayout.LayoutParams(0, dp(44), weight)
        params.setMargins(dp(2), dp(3), dp(2), dp(3))
        row.addView(view, params)
    }

    // ---------- panels ----------

    private fun buildKeys() {
        th = theme()
        board.setBackgroundColor(th.bg)
        while (board.childCount > 1) board.removeViewAt(1)
        letterKeys.clear()
        shiftKey = null
        val typing = panel == Panel.LETTERS || panel == Panel.SYMBOLS || panel == Panel.SYMBOLS2
        suggestions.visibility = if (typing) View.VISIBLE else View.GONE
        when (panel) {
            Panel.LETTERS -> {
                addToolbar()
                buildLetters()
            }
            Panel.SYMBOLS, Panel.SYMBOLS2 -> buildSymbols()
            Panel.EMOJI -> buildEmoji()
            Panel.CLIPBOARD -> buildClipboard()
        }
    }

    private fun openPanel(p: Panel) {
        panel = p
        buildKeys()
        if (suggestions.visibility == View.VISIBLE) refreshSuggestions()
    }

    private fun addToolbar() {
        val bar = newRow()
        val lang = toolButton(language, 14f) { cycleLanguage() }
        lang.setTextColor(th.accent)
        bar.addView(lang, lp(0, dp(34), 1f))
        bar.addView(toolButton("Select all") { editAction(android.R.id.selectAll) }, lp(0, dp(34), 1.7f))
        bar.addView(toolButton("Copy") { editAction(android.R.id.copy) }, lp(0, dp(34), 1.1f))
        bar.addView(toolButton("Cut") { editAction(android.R.id.cut) }, lp(0, dp(34), 1f))
        bar.addView(toolButton("Paste") { editAction(android.R.id.paste) }, lp(0, dp(34), 1.2f))
        bar.addView(toolButton("📋", 17f) { openPanel(Panel.CLIPBOARD) }, lp(0, dp(34), 1f))
        board.addView(bar)
    }

    private fun layoutRows(): List<String> = when (language) {
        "FR" -> listOf("azertyuiop", "qsdfghjklm", "wxcvbn'")
        "AR" -> listOf("ضصثقفغعهخحج", "شسيبلاتنمكط", "ذدئءؤرىةوز")
        else -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    }

    private fun languageName(): String = when (language) {
        "FR" -> "Français"
        "AR" -> "العربية"
        else -> "English"
    }

    private fun buildLetters() {
        val rows = layoutRows()
        val r1 = newRow()
        rows[0].forEach { addKey(r1, letterKey(it.toString())) }
        board.addView(r1)

        val r2 = newRow()
        val pad = if (rows[1].length < rows[0].length) (rows[0].length - rows[1].length) / 2f else 0f
        if (pad > 0f) r2.addView(View(this), lp(0, 1, pad))
        rows[1].forEach { addKey(r2, letterKey(it.toString())) }
        if (pad > 0f) r2.addView(View(this), lp(0, 1, pad))
        board.addView(r2)

        val r3 = newRow()
        if (language != "AR") {
            val sk = key("⇧", special = true, size = 18f) { onShiftTap() }
            shiftKey = sk
            addKey(r3, sk, 1.5f)
        }
        rows[2].forEach { addKey(r3, letterKey(it.toString())) }
        addKey(r3, deleteKey(), 1.5f)
        board.addView(r3)

        buildBottomRow()
        applyShift()
    }

    private fun letterKey(base: String): TextView {
        val t = key(base, size = 21f) { typeLetter(base) }
        t.tag = base
        letterKeys.add(t)
        val alts = alternatesFor(base)
        if (alts.isNotEmpty()) {
            t.setOnLongClickListener {
                t.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showAlternates(alts)
                true
            }
        }
        return t
    }

    private fun buildSymbols() {
        val second = panel == Panel.SYMBOLS2
        val rows = if (second) {
            listOf("[]{}#%^*+=", "_\\|~<>€£¥•", ".,?!'")
        } else {
            listOf("1234567890", "-/:;()\$&@\"", ".,?!'")
        }
        val r1 = newRow()
        rows[0].forEach { addKey(r1, symbolKey(it.toString())) }
        board.addView(r1)
        val r2 = newRow()
        rows[1].forEach { addKey(r2, symbolKey(it.toString())) }
        board.addView(r2)
        val r3 = newRow()
        addKey(r3, key(if (second) "123" else "#+=", special = true, size = 14f) {
            openPanel(if (second) Panel.SYMBOLS else Panel.SYMBOLS2)
        }, 1.5f)
        rows[2].forEach { addKey(r3, symbolKey(it.toString()), 1.4f) }
        addKey(r3, deleteKey(), 1.5f)
        board.addView(r3)
        buildBottomRow()
    }

    private fun symbolKey(s: String): TextView = key(s, size = 20f) { typeText(s) }

    private fun buildBottomRow() {
        val bottom = newRow()
        if (panel == Panel.LETTERS) {
            addKey(bottom, key("?123", special = true, size = 14f) { openPanel(Panel.SYMBOLS) }, 1.4f)
        } else {
            addKey(bottom, key("ABC", special = true, size = 14f) { openPanel(Panel.LETTERS) }, 1.4f)
        }
        addKey(bottom, key("😊", special = true, size = 19f) { openPanel(Panel.EMOJI) }, 1f)
        addKey(bottom, key(",", size = 20f) { typeText(",") }, 0.8f)
        val space = key(languageName(), size = 13f) { typeText(" ") }
        space.setTextColor(th.hint)
        space.setOnLongClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            true
        }
        addKey(bottom, space, 4f)
        val dot = key(".", size = 20f) { typeText(".") }
        dot.setOnLongClickListener {
            showAlternates(listOf("!", "?", ":", ";", "'", "\"", "…"))
            true
        }
        addKey(bottom, dot, 0.8f)
        addKey(bottom, key(enterLabel(), special = true, size = 18f) { pressEnter() }, 1.4f)
        board.addView(bottom)
    }

    private fun buildEmoji() {
        val tabs = newRow()
        val icons = listOf("🕘", "😀", "👍", "💖", "🐶", "🍔", "⚽", "💡")
        icons.forEachIndexed { i, icon ->
            val tab = toolButton(icon, 19f) {
                emojiCategory = i
                buildKeys()
            }
            if (i == emojiCategory) tab.background = keyBackground(th.key, th.keyPressed)
            tabs.addView(tab, lp(0, dp(36), 1f))
        }
        board.addView(tabs)

        val items = if (emojiCategory == 0) recentEmoji() else EmojiData.categories[emojiCategory - 1]
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        if (items.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Emoji you use will show up here."
            empty.gravity = Gravity.CENTER
            empty.textSize = 14f
            empty.setTextColor(th.hint)
            empty.setPadding(dp(16), dp(40), dp(16), dp(40))
            grid.addView(empty)
        }
        val perRow = 8
        items.chunked(perRow).forEach { chunk ->
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            chunk.forEach { e ->
                val t = TextView(this)
                t.text = e
                t.gravity = Gravity.CENTER
                t.textSize = 24f
                t.background = keyBackground(0x00000000, th.specialPressed)
                t.isClickable = true
                t.setOnClickListener {
                    haptic(t)
                    typeEmoji(e)
                }
                r.addView(t, LinearLayout.LayoutParams(0, dp(44), 1f))
            }
            for (i in chunk.size until perRow) {
                r.addView(View(this), LinearLayout.LayoutParams(0, dp(44), 1f))
            }
            grid.addView(r)
        }
        val scroll = ScrollView(this)
        scroll.addView(grid)
        board.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(196)))

        val bottom = newRow()
        addKey(bottom, key("ABC", special = true, size = 14f) { openPanel(Panel.LETTERS) }, 1.6f)
        addKey(bottom, key(languageName(), size = 13f) { typeText(" ") }, 4f)
        addKey(bottom, deleteKey(), 1.6f)
        board.addView(bottom)
    }

    private fun buildClipboard() {
        val top = newRow()
        top.addView(toolButton("← ABC") { openPanel(Panel.LETTERS) }, lp(0, dp(36), 1f))
        val title = TextView(this)
        title.text = "Clipboard"
        title.gravity = Gravity.CENTER
        title.textSize = 15f
        title.setTextColor(th.text)
        top.addView(title, lp(0, dp(36), 2f))
        top.addView(toolButton("Clear") {
            ClipStore(this).clearUnpinned()
            buildKeys()
        }, lp(0, dp(36), 1f))
        board.addView(top)

        val items = ClipStore(this).all()
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        if (items.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Nothing copied yet.\nCopy text in any app and it will show up here."
            empty.gravity = Gravity.CENTER
            empty.textSize = 14f
            empty.setTextColor(th.hint)
            empty.setPadding(dp(16), dp(40), dp(16), dp(40))
            list.addView(empty)
        }
        items.forEach { item ->
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            val preview = item.text.replace("\n", " ").trim().take(100)
            val body = key(preview, size = 13f) { pasteClip(item.text) }
            body.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            body.maxLines = 2
            body.ellipsize = TextUtils.TruncateAt.END
            body.setPadding(dp(12), 0, dp(12), 0)
            val bodyParams = LinearLayout.LayoutParams(0, dp(46), 1f)
            bodyParams.setMargins(dp(2), dp(2), dp(2), dp(2))
            r.addView(body, bodyParams)
            val pin = key(if (item.pinned) "★" else "☆", special = true, size = 16f) {
                ClipStore(this).togglePin(item.text)
                buildKeys()
            }
            val pinParams = LinearLayout.LayoutParams(dp(44), dp(46))
            pinParams.setMargins(dp(2), dp(2), dp(2), dp(2))
            r.addView(pin, pinParams)
            val del = key("✕", special = true, size = 15f) {
                ClipStore(this).remove(item.text)
                buildKeys()
            }
            val delParams = LinearLayout.LayoutParams(dp(44), dp(46))
            delParams.setMargins(dp(2), dp(2), dp(2), dp(2))
            r.addView(del, delParams)
            list.addView(r)
        }
        val scroll = ScrollView(this)
        scroll.addView(list)
        board.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(246)))
    }

    // ---------- keys & actions ----------

    private fun deleteKey(): TextView {
        val t = key("⌫", special = true, size = 18f) { }
        t.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    haptic(v)
                    backspace(true)
                    repeating = true
                    repeatCount = 0
                    handler.removeCallbacks(repeatRunnable)
                    handler.postDelayed(repeatRunnable, 400L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopRepeat()
                }
            }
            true
        }
        return t
    }

    private fun stopRepeat() {
        handler.removeCallbacks(repeatRunnable)
        if (!repeating) return
        repeating = false
        refreshSuggestions()
    }

    private fun onShiftTap() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == 2 -> 0
            shift == 1 && now - lastShiftTap < 400L -> 2
            shift == 1 -> 0
            else -> 1
        }
        lastShiftTap = now
        applyShift()
    }

    private fun applyShift() {
        if (!::th.isInitialized) return
        val upper = shift != 0
        letterKeys.forEach { k ->
            val base = k.tag as? String
            if (base != null) k.text = if (upper) base.uppercase() else base
        }
        val sk = shiftKey ?: return
        when (shift) {
            0 -> {
                sk.text = "⇧"
                sk.setTextColor(th.text)
                sk.background = keyBackground(th.special, th.specialPressed)
            }
            1 -> {
                sk.text = "⇧"
                sk.setTextColor(th.accent)
                sk.background = keyBackground(th.key, th.keyPressed)
            }
            else -> {
                sk.text = "⇪"
                sk.setTextColor(0xFFFFFFFF.toInt())
                sk.background = keyBackground(th.accent, th.accent)
            }
        }
    }

    /** Turns on Shift automatically at the start of a sentence (when the app asks for it). */
    private fun updateAutoCaps() {
        if (shift == 2) return
        val info = currentInputEditorInfo
        val ic = currentInputConnection
        var want = 0
        if (info != null && ic != null &&
            (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT) {
            want = if (ic.getCursorCapsMode(info.inputType) != 0) 1 else 0
        }
        if (want != shift) {
            shift = want
            applyShift()
        }
    }

    private fun typeLetter(base: String) {
        val ch = if (shift != 0) base.uppercase() else base
        if (shift == 1) {
            shift = 0
            applyShift()
        }
        typeText(ch)
    }

    private fun alternatesFor(base: String): List<String> {
        val map = when (language) {
            "AR" -> ARABIC_ALTS
            "FR" -> FRENCH_ALTS
            else -> LATIN_ALTS
        }
        val raw = map[base] ?: return emptyList()
        return raw.split(" ")
    }

    private fun showAlternates(list: List<String>) {
        suggestions.removeAllViews()
        list.forEach { alt ->
            val shown = if (shift != 0) alt.uppercase() else alt
            val t = TextView(this)
            t.text = shown
            t.gravity = Gravity.CENTER
            t.textSize = 22f
            t.setTextColor(th.text)
            t.background = keyBackground(0x00000000, th.specialPressed)
            t.isClickable = true
            t.setOnClickListener {
                haptic(t)
                if (shift == 1) {
                    shift = 0
                    applyShift()
                }
                typeText(shown)
            }
            suggestions.addView(t, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun cycleLanguage() {
        language = when (language) {
            "EN" -> "FR"
            "FR" -> "AR"
            else -> "EN"
        }
        shift = 0
        buildKeys()
        refreshSuggestions()
    }

    private fun enterLabel(): String {
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        if ((opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "↵"
        return when (opts and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_SEND -> "➤"
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> "→"
            EditorInfo.IME_ACTION_DONE -> "✓"
            else -> "↵"
        }
    }

    private fun pressEnter() {
        val ic = currentInputConnection ?: return
        clearPending()
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        if ((opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 &&
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        updateAutoCaps()
    }

    private fun pasteClip(text: String) {
        clearPending()
        currentInputConnection?.commitText(text, 1)
        openPanel(Panel.LETTERS)
        updateAutoCaps()
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

    // ---------- emoji ----------

    private fun emojiPrefs() = getSharedPreferences("emoji_recent", Context.MODE_PRIVATE)

    private fun recentEmoji(): List<String> =
        (emojiPrefs().getString("list", "") ?: "").split(" ").filter { it.isNotEmpty() }

    private fun addRecentEmoji(e: String) {
        val list = mutableListOf(e)
        list.addAll(recentEmoji().filter { it != e })
        emojiPrefs().edit().putString("list", list.take(24).joinToString(" ")).apply()
    }

    private fun typeEmoji(e: String) {
        clearPending()
        currentInputConnection?.commitText(e, 1)
        addRecentEmoji(e)
    }

    // ---------- typing ----------

    private fun typeText(value: String) {
        val ic = currentInputConnection ?: return
        clearPending() // Undo is only available immediately after a correction.
        ic.commitText(value, 1)
        if (value == " " || value == "." || value == "," || value == "!" ||
            value == "?" || value == ":" || value == ";") {
            if (!isPrivateField()) handleWordEnd(ic, value)
        }
        refreshSuggestions()
        updateAutoCaps()
    }

    private fun handleWordEnd(ic: InputConnection, delimiter: String) {
        val before = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        if (!before.endsWith(delimiter)) return
        val beforeDelimiter = before.dropLast(delimiter.length)
        val typed = beforeDelimiter.takeLastWhile { it.isLetter() }
        if (typed.isEmpty()) return
        val pre = beforeDelimiter.dropLast(typed.length).lastOrNull()
        val standalone = pre == null || pre.isWhitespace() || pre == '(' || pre == '"'

        var correction: String? = null
        var fromTypo = false
        if (standalone && language != "AR") {
            val typo = SmartText.typoFix(typed)
            if (typo != null) {
                correction = typo
                fromTypo = true
            } else if (typed.length >= 3) {
                val learned = WordMemory(this).bestCorrection(typed)
                if (learned != null) correction = matchCase(learned, typed)
            }
        }
        val fix = correction
        if (fix != null && fix != typed) {
            pendingOriginal = typed
            pendingReplacement = fix
            pendingDelimiter = delimiter
            ic.deleteSurroundingText(typed.length + delimiter.length, 0)
            ic.commitText(fix + delimiter, 1)
            if (!fromTypo) WordMemory(this).learn(fix)
            return
        }
        if (typed.length >= 2) WordMemory(this).learn(typed)
    }

    private fun matchCase(word: String, prefix: String): String {
        if (prefix.isEmpty() || !prefix[0].isUpperCase()) return word
        return word.replaceFirstChar { it.uppercase() }
    }

    private fun backspace(refresh: Boolean = true) {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (pendingOriginal != null && pendingReplacement != null) {
            val delimiter = pendingDelimiter ?: ""
            ic.deleteSurroundingText(pendingReplacement!!.length + delimiter.length, 0)
            ic.commitText(pendingOriginal, 1)
            clearPending()
        } else if (selected != null && selected.isNotEmpty()) {
            ic.commitText("", 1)
        } else {
            val tail = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            val n = if (tail.length == 2 && Character.isHighSurrogate(tail[0]) && Character.isLowSurrogate(tail[1])) 2 else 1
            ic.deleteSurroundingText(n, 0)
        }
        if (refresh) refreshSuggestions()
    }

    private fun isPrivateField(): Boolean {
        val type = currentInputEditorInfo?.inputType ?: return false
        val variation = type and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    // ---------- suggestions ----------

    private fun refreshSuggestions() {
        if (!::suggestions.isInitialized || !::th.isInitialized) return
        if (panel != Panel.LETTERS && panel != Panel.SYMBOLS && panel != Panel.SYMBOLS2) return
        suggestions.removeAllViews()
        val before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString().orEmpty()
        val prefix = before.takeLastWhile { it.isLetter() }
        val words: List<String> = if (isPrivateField()) emptyList() else suggestionsFor(prefix, before)
        words.take(3).forEachIndexed { i, word ->
            if (i > 0) {
                val divider = View(this)
                divider.setBackgroundColor(th.special)
                suggestions.addView(divider, lp(dp(1), dp(22)))
            }
            val t = TextView(this)
            t.text = word
            t.gravity = Gravity.CENTER
            t.textSize = 17f
            t.maxLines = 1
            t.setTextColor(th.text)
            t.background = keyBackground(0x00000000, th.specialPressed)
            t.isClickable = true
            t.setOnClickListener {
                haptic(t)
                acceptSuggestion(word)
            }
            suggestions.addView(t, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun suggestionsFor(prefix: String, before: String): List<String> {
        if (prefix.isEmpty()) {
            val trimmed = before.trimEnd()
            val atStart = trimmed.isEmpty() || trimmed.last() in ".!?"
            return if (atStart) WordBank.starters(language) else emptyList()
        }
        val result = LinkedHashSet<String>()
        WordMemory(this).suggest(prefix).forEach { result.add(matchCase(it, prefix)) }
        WordBank.complete(language, prefix, 6).forEach { result.add(matchCase(it, prefix)) }
        return result.toList()
    }

    private fun acceptSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val old = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty().takeLastWhile { it.isLetter() }
        if (old.isNotEmpty()) ic.deleteSurroundingText(old.length, 0)
        ic.commitText(word + " ", 1)
        pendingOriginal = old
        pendingReplacement = word
        pendingDelimiter = " "
        if (!isPrivateField()) WordMemory(this).learn(word)
        if (shift == 1) {
            shift = 0
            applyShift()
        }
        refreshSuggestions()
        updateAutoCaps()
    }

    companion object {
        private val LATIN_ALTS: Map<String, String> = mapOf(
            "a" to "à á â ä æ ã å ā", "e" to "è é ê ë ē ė ę", "i" to "ì í î ï ī į",
            "o" to "ò ó ô ö œ õ ø ō", "u" to "ù ú û ü ū", "c" to "ç ć č", "n" to "ñ ń",
            "s" to "ß ś š", "y" to "ý ÿ", "z" to "ž ź ż", "l" to "ł"
        )
        private val FRENCH_ALTS: Map<String, String> = mapOf(
            "a" to "à â æ ä", "e" to "é è ê ë", "i" to "î ï", "o" to "ô œ ö",
            "u" to "ù û ü", "c" to "ç", "y" to "ÿ", "'" to "’ ‘"
        )
        private val ARABIC_ALTS: Map<String, String> = mapOf(
            "ا" to "أ إ آ ء", "ه" to "ة", "ي" to "ى ئ", "و" to "ؤ", "ط" to "ظ"
        )
    }
}
