package com.nutritiontracker.ui.foods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutritiontracker.AppContainer
import com.nutritiontracker.data.FoodItem
import com.nutritiontracker.data.LocalSearchHit
import com.nutritiontracker.data.OnlineSearchResult
import com.nutritiontracker.data.Recipe
import com.nutritiontracker.remote.RemoteFood
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OnlineState {
    data object Idle : OnlineState
    data object Disabled : OnlineState
    data object Offline : OnlineState
    data object Loading : OnlineState
    data class Done(val result: OnlineSearchResult) : OnlineState
}

data class FoodListsState(
    val recent: List<FoodItem> = emptyList(),
    val favorites: List<FoodItem> = emptyList(),
    val userFoods: List<FoodItem> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val history: List<String> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class FoodSearchViewModel(private val c: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _online = MutableStateFlow<OnlineState>(OnlineState.Idle)
    val online: StateFlow<OnlineState> = _online.asStateFlow()

    private val _opening = MutableStateFlow(false)
    val opening: StateFlow<Boolean> = _opening.asStateFlow()

    val isOnline: StateFlow<Boolean> = c.connectivity.online

    val local: StateFlow<List<LocalSearchHit>> = _query
        .debounce(120)
        .map { it.trim() }
        .distinctUntilChanged()
        .mapLatest { q -> if (q.length < 2) emptyList() else c.foods.searchLocal(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lists: StateFlow<FoodListsState> = combine(
        c.foods.observeRecent(),
        c.foods.observeFavorites(),
        c.foods.observeUserFoods(),
        c.recipes.observeAll(),
        c.foods.observeSearchHistory(),
    ) { recent, fav, user, recipes, history ->
        FoodListsState(recent, fav, user, recipes, history, fav.map { it.id }.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodListsState())

    private var onlineJob: Job? = null

    init {
        viewModelScope.launch {
            _query.debounce(650).map { it.trim() }.distinctUntilChanged().collect { q -> runOnline(q, manual = false) }
        }
    }

    fun setQuery(q: String) { _query.value = q }

    fun retryOnline() = runOnline(_query.value.trim(), manual = true)

    private fun runOnline(q: String, manual: Boolean) {
        onlineJob?.cancel()
        if (q.length < 2) {
            _online.value = OnlineState.Idle
            return
        }
        onlineJob = viewModelScope.launch {
            val settings = c.settings.current()
            if (!settings.onlineSearchEnabled || (!settings.usdaEnabled && !settings.openFoodFactsEnabled)) {
                _online.value = OnlineState.Disabled
                return@launch
            }
            if (!c.connectivity.online.value && !manual) {
                _online.value = OnlineState.Offline
                return@launch
            }
            _online.value = OnlineState.Loading
            val result = c.foods.searchOnline(q, settings)
            _online.value = OnlineState.Done(result)
        }
    }

    /** Сохраняет продукт из интернета в локальную базу и возвращает его id. */
    fun openRemote(remote: RemoteFood, onOpened: (Long) -> Unit) {
        if (_opening.value) return
        _opening.value = true
        viewModelScope.launch {
            try {
                val q = _query.value.trim()
                val id = c.foods.saveRemote(remote, q)
                c.foods.rememberQuery(q)
                onOpened(id)
            } finally {
                _opening.value = false
            }
        }
    }

    fun openLocal(id: Long, onOpened: (Long) -> Unit) {
        viewModelScope.launch {
            c.foods.rememberQuery(_query.value)
            onOpened(id)
        }
    }

    fun clearHistory() { viewModelScope.launch { c.foods.clearSearchHistory() } }

    fun toggleFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { c.foods.setFavorite(id, favorite) }
    }

    /** Для отметки «сохранён» у результатов из интернета. */
    suspend fun cachedId(remote: RemoteFood): Long? =
        remote.food.externalId?.let { c.foods.findCached(remote.food.source, it)?.id }
}
