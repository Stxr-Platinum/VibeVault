package com.vibevault.app.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.YTItem
import com.vibevault.app.ui.screens.search.SearchCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)

@HiltViewModel
class OnlineSearchViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = try {
        URLDecoder.decode(savedStateHandle.get<String>("query")!!, "UTF-8")
    } catch (e: IllegalArgumentException) {
        savedStateHandle.get<String>("query")!!
    }

    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<com.music.innertube.pages.SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    init {
        viewModelScope.launch {
            filter.collect { currentFilter ->
                val filterKey = currentFilter?.value ?: "all"
                if (currentFilter == null) {
                    val cachedSummary = SearchCache.getOnlineSummary(query)
                    if (cachedSummary != null && summaryPage == null) {
                        summaryPage = cachedSummary
                    }
                    YouTube.searchSummary(query).onSuccess { searchSummaryPage ->
                        if (summaryPage != searchSummaryPage) {
                            summaryPage = searchSummaryPage
                        }
                        SearchCache.saveOnlineSummary(query, searchSummaryPage)
                    }.onFailure {
                        it.printStackTrace()
                    }
                } else {
                    val cachedTab = SearchCache.getOnlineTab(query, filterKey)
                    if (cachedTab != null && viewStateMap[filterKey] == null) {
                        viewStateMap[filterKey] = cachedTab
                    }
                    YouTube.search(query, currentFilter).onSuccess { searchResult ->
                        val freshPage = ItemsPage(
                            items = searchResult.items.distinctBy { it.id },
                            continuation = searchResult.continuation
                        )
                        if (viewStateMap[filterKey] != freshPage) {
                            viewStateMap[filterKey] = freshPage
                        }
                        SearchCache.saveOnlineTab(query, filterKey, freshPage)
                    }.onFailure {
                        it.printStackTrace()
                    }
                }
            }
        }
    }

    fun loadMore() {
        val currentFilter = filter.value
        val filterKey = currentFilter?.value ?: "all"
        val currentPage = viewStateMap[filterKey] ?: return
        val continuation = currentPage.continuation ?: return
        
        viewModelScope.launch {
            YouTube.searchContinuation(continuation)
                .onSuccess { result ->
                    viewStateMap[filterKey] = ItemsPage(
                        items = (currentPage.items + result.items).distinctBy { it.id },
                        continuation = result.continuation
                    )
                }.onFailure {
                    it.printStackTrace()
                }
        }
    }
}
