package com.example.androidkeyboardunofficial

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

class WordMemory(context: Context) {
    private val prefs = context.getSharedPreferences("personal_words", Context.MODE_PRIVATE)

    fun learn(raw: String) {
        val word = normalize(raw)
        if (word.length < 2) return
        val data = JSONObject(prefs.getString("counts", "{}") ?: "{}")
        data.put(word, data.optInt(word, 0) + 1)
        prefs.edit().putString("counts", data.toString()).apply()
        addHistory(word)
    }

    fun addHistory(raw: String) {
        val word = normalize(raw)
        if (word.length < 2) return
        val history = JSONArray(prefs.getString("history", "[]") ?: "[]")
        history.put(word)
        // Keep a bounded local history rather than storing unlimited text.
        val trimmed = JSONArray()
        val start = (history.length() - 500).coerceAtLeast(0)
        for (i in start until history.length()) trimmed.put(history.getString(i))
        prefs.edit().putString("history", trimmed.toString()).apply()
    }

    fun bestCorrection(typed: String): String? {
        val input = normalize(typed)
        if (input.length < 3) return null
        val data = JSONObject(prefs.getString("counts", "{}") ?: "{}")
        val candidates = mutableListOf<Pair<String, Int>>()
        val keys = data.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val count = data.optInt(word, 0)
            if (count >= 3 && abs(word.length - input.length) <= 1 &&
                word != input && editDistanceAtMostOne(input, word)) {
                candidates.add(word to count)
            }
        }
        val ranked = candidates.sortedByDescending { it.second }
        if (ranked.isEmpty()) return null
        // Avoid autocorrecting when two candidates have the same frequency.
        if (ranked.size > 1 && ranked[0].second == ranked[1].second) return null
        return ranked.first().first
    }

    fun suggest(prefix: String): List<String> {
        val p = normalize(prefix)
        if (p.isBlank()) return emptyList()
        val data = JSONObject(prefs.getString("counts", "{}") ?: "{}")
        val result = mutableListOf<Pair<String, Int>>()
        val keys = data.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            if (word.startsWith(p) && word != p) result.add(word to data.optInt(word, 0))
        }
        return result.sortedByDescending { it.second }.take(3).map { it.first }
    }

    fun clearHistory() { prefs.edit().remove("history").apply() }
    fun clearWords() { prefs.edit().remove("counts").apply() }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetter() }

    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (a == b) return true
        if (abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++ }
            else {
                edits++
                if (edits > 1) return false
                if (a.length > b.length) i++
                else if (b.length > a.length) j++
                else { i++; j++ }
            }
        }
        if (i < a.length || j < b.length) edits++
        return edits <= 1
    }
}
