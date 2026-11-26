package io.github.niteshprasad.reelune.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.niteshprasad.reelune.ReeluneApp
import io.github.niteshprasad.reelune.data.Genre
import io.github.niteshprasad.reelune.data.Language
import io.github.niteshprasad.reelune.data.Movie
import io.github.niteshprasad.reelune.data.MovieFilters
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val filters: MovieFilters = MovieFilters(),
    val searchText: String = "",
    val movies: List<Movie> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val totalResults: Int? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val featured: List<Movie> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val languages: List<Language> = emptyList(),
) {
    val hasMore: Boolean get() = page < totalPages
    val showFeatured: Boolean get() = filters.isDefault && featured.isNotEmpty()
}

@OptIn(FlowPreview::class)
class HomeViewModel : ViewModel() {
    private val tmdb = ReeluneApp.instance.tmdb
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch { runCatching { tmdb.trending() }.onSuccess { list -> _state.update { it.copy(featured = list.filter { m -> m.backdropPath != null }.take(6)) } } }
        viewModelScope.launch { runCatching { tmdb.genres() }.onSuccess { g -> _state.update { it.copy(genres = g) } } }
        viewModelScope.launch { runCatching { tmdb.languages() }.onSuccess { l -> _state.update { it.copy(languages = l) } } }
        // Search as you type, after a short pause.
        viewModelScope.launch {
            _state.map { it.searchText.trim() }.distinctUntilChanged().drop(1).debounce(400).collect { q ->
                setFilters(_state.value.filters.copy(query = q))
            }
        }
        reload()
    }

    fun onSearchText(text: String) = _state.update { it.copy(searchText = text) }

    fun setFilters(filters: MovieFilters) {
        if (filters == _state.value.filters && _state.value.page > 0) return
        _state.update { it.copy(filters = filters) }
        reload()
    }

    fun clearFilters() = setFilters(MovieFilters(query = _state.value.filters.query))

    fun reload() {
        loadJob?.cancel()
        _state.update { it.copy(movies = emptyList(), page = 0, totalPages = 1, totalResults = null, error = null) }
        loadNext()
    }

    fun loadNext() {
        val s = _state.value
        if (s.loading && loadJob?.isActive == true) return
        if (!s.hasMore) return
        val filters = s.filters
        val page = s.page + 1
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val result = ReeluneApp.instance.tmdb.movies(filters, page)
                if (_state.value.filters != filters) return@launch
                _state.update { st ->
                    val seen = st.movies.mapTo(HashSet()) { it.id }
                    st.copy(
                        movies = st.movies + result.results.filter { seen.add(it.id) },
                        page = page,
                        totalPages = minOf(result.totalPages, 500), // TMDB serves at most 500 pages.
                        totalResults = if (filters.query.isBlank()) result.totalResults else null,
                        loading = false,
                    )
                }
                // Search results filtered on the device can come back short: keep filling the first screen.
                if (filters.query.isNotBlank() && _state.value.movies.size < 12 && _state.value.hasMore) loadNext()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Movies didn't load.") }
            }
        }
    }
}
