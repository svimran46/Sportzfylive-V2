package com.sportzfy.live.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.domain.repository.MatchRepository
import com.sportzfy.live.presentation.model.CatalogUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CatalogViewModel(
    private val repository: MatchRepository,
) : ViewModel() {

    private val category = MutableStateFlow<SportCategory?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<CatalogUiState> =
        combine(repository.feed, category, query) { feed, selected, text ->
            CatalogUiState.from(feed, selected, text)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CatalogUiState.from(repository.feed.value, null, ""),
        )

    /** Cheap to call often: the repository serves from its 60s cache. */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch { repository.refresh(force) }
    }

    fun selectCategory(value: SportCategory?) {
        category.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }
}
