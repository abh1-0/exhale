/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State behind Home's pinned search: live suggestions while typing, song results on Enter. */
class SearchModel(private val scope: CoroutineScope) {
    var query by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var results by mutableStateOf<List<SongItem>>(emptyList())
        private set
    /** The query [results] belong to; results are stale once the text moves on from it. */
    var searchedQuery by mutableStateOf<String?>(null)
        private set
    var searching by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    val showingResults: Boolean
        get() = searchedQuery != null && searchedQuery == query.trim()

    fun updateQuery(value: String) {
        query = value
        suggestJob?.cancel()
        val q = value.trim()
        if (q.isEmpty()) {
            suggestions = emptyList()
            return
        }
        suggestJob = scope.launch {
            delay(220)
            withContext(Dispatchers.IO) { YouTube.searchSuggestions(q) }
                .onSuccess { suggestions = it.queries.take(6) }
        }
    }

    fun search(text: String = query) {
        val q = text.trim()
        if (q.isEmpty()) return
        query = text
        suggestJob?.cancel()
        suggestions = emptyList()
        searchJob?.cancel()
        searching = true
        error = null
        searchedQuery = q
        searchJob = scope.launch {
            withContext(Dispatchers.IO) { YouTube.search(q, YouTube.SearchFilter.FILTER_SONG) }
                .onSuccess { result -> results = result.items.filterIsInstance<SongItem>() }
                .onFailure { if (it is CancellationException) throw it else error = it.message ?: "Search failed" }
            searching = false
        }
    }

    fun clear() {
        suggestJob?.cancel()
        searchJob?.cancel()
        query = ""
        suggestions = emptyList()
        results = emptyList()
        searchedQuery = null
        searching = false
        error = null
    }
}
