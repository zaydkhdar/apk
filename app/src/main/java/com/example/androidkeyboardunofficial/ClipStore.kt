package com.example.androidkeyboardunofficial

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ClipItem(val text: String, val pinned: Boolean)

/** Local-only clipboard history. Stored in app-private storage, never uploaded. */
class ClipStore(context: Context) {
    private val prefs = context.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)

    /** Newest first, in storage order. */
    private fun load(): MutableList<ClipItem> {
        val arr = JSONArray(prefs.getString("items", "[]") ?: "[]")
        val list = mutableListOf<ClipItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(ClipItem(o.getString("t"), o.optBoolean("p", false)))
        }
        return list
    }

    private fun save(list: List<ClipItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.text).put("p", it.pinned)) }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    /** Pinned items first, then newest first. */
    fun all(): List<ClipItem> {
        val list = load()
        return list.filter { it.pinned } + list.filter { !it.pinned }
    }

    fun add(raw: String) {
        val text = raw.take(MAX_LENGTH)
        if (text.isBlank()) return
        val list = load()
        val existing = list.firstOrNull { it.text == text }
        list.removeAll { it.text == text }
        list.add(0, ClipItem(text, existing?.pinned ?: false))
        // Keep only the newest unpinned items; pinned items are never dropped.
        var unpinned = 0
        save(list.filter { if (it.pinned) true else ++unpinned <= MAX_ITEMS })
    }

    fun togglePin(text: String) {
        save(load().map { if (it.text == text) it.copy(pinned = !it.pinned) else it })
    }

    fun remove(text: String) {
        save(load().filter { it.text != text })
    }

    fun clearUnpinned() {
        save(load().filter { it.pinned })
    }

    fun clearAll() {
        prefs.edit().remove("items").apply()
    }

    companion object {
        const val MAX_ITEMS = 30
        const val MAX_LENGTH = 5000
    }
}
