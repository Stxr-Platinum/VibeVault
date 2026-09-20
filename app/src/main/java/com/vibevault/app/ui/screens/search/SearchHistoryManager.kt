package com.vibevault.app.ui.screens.search

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)
    private val key = "recent_queries"
    private val gson = Gson()

    private val _history = MutableStateFlow<List<String>>(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private fun loadHistory(): List<String> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = _history.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val updated = current.take(20)
        _history.value = updated
        prefs.edit().putString(key, gson.toJson(updated)).apply()
    }

    fun removeQuery(query: String) {
        val current = _history.value.toMutableList()
        current.remove(query)
        _history.value = current
        prefs.edit().putString(key, gson.toJson(current)).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove(key).apply()
    }
}
