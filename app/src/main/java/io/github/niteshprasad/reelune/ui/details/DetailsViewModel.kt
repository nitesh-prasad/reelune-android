package io.github.niteshprasad.reelune.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.niteshprasad.reelune.ReeluneApp
import io.github.niteshprasad.reelune.data.MovieDetails
import io.github.niteshprasad.reelune.data.ScoresResult
import io.github.niteshprasad.reelune.plugin.MovieRef
import io.github.niteshprasad.reelune.plugin.PluginSources
import io.github.niteshprasad.reelune.plugin.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsState(
    val movie: MovieDetails? = null,
    val error: String? = null,
    val scores: ScoresResult? = null,
    val region: String = "US",
    val pluginCount: Int = 0,
    val sourcesLoading: Boolean = false,
    val sources: List<PluginSources> = emptyList(),
    val opening: Source? = null,
    val streamError: String? = null,
)

class DetailsViewModel(private val movieKey: String) : ViewModel() {
    private val app = ReeluneApp.instance
    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            app.plugins.plugins.collect { list ->
                val count = list.count { it.loaded }
                val changed = count != _state.value.pluginCount
                _state.update { it.copy(pluginCount = count) }
                if (changed) _state.value.movie?.let { loadSources(it) }
            }
        }
    }

    fun load() {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            try {
                val region = app.settings.current().region
                val movie = app.catalogForKey(movieKey).details(movieKey)
                _state.update { it.copy(movie = movie, region = region) }
                movie.imdbId?.takeIf { it.isNotBlank() }?.let { imdb ->
                    launch { _state.update { it.copy(scores = app.omdb.scores(imdb)) } }
                }
                loadSources(movie)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "This movie didn't load.") }
            }
        }
    }

    private fun loadSources(movie: MovieDetails) {
        if (_state.value.pluginCount == 0) {
            _state.update { it.copy(sources = emptyList(), sourcesLoading = false) }
            return
        }
        _state.update { it.copy(sourcesLoading = true) }
        viewModelScope.launch {
            val ref = MovieRef(movie.imdbId?.takeIf { it.isNotBlank() }, movie.title, movie.originalTitle, movie.year)
            val result = app.plugins.findSources(ref)
            _state.update { it.copy(sources = result, sourcesLoading = false) }
        }
    }

    fun retrySources() = _state.value.movie?.let { loadSources(it) }

    /** Starts a stream; [onReady] runs on success so the screen can open the player. */
    fun play(pluginId: String, source: Source, onReady: () -> Unit) {
        if (_state.value.opening != null) return
        _state.update { it.copy(opening = source, streamError = null) }
        viewModelScope.launch {
            try {
                val session = app.plugins.openStream(pluginId, source)
                app.plugins.activeStream = io.github.niteshprasad.reelune.plugin.PluginManager.ActiveStream(
                    "${_state.value.movie?.title.orEmpty()} (${source.title})",
                    session,
                )
                onReady()
            } catch (e: Exception) {
                _state.update { it.copy(streamError = e.message ?: "This stream couldn't start.") }
            } finally {
                _state.update { it.copy(opening = null) }
            }
        }
    }

    fun dismissStreamError() = _state.update { it.copy(streamError = null) }

    companion object {
        fun factory(key: String): ViewModelProvider.Factory = viewModelFactory { initializer { DetailsViewModel(key) } }
    }
}
