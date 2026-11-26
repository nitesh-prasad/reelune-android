package io.github.niteshprasad.reelune.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val tmdbKey: String = "",
    val omdbKey: String = "",
    /** ISO 3166-1 country used for age certifications, e.g. "IN" or "US". */
    val region: String = defaultRegion(),
) {
    val hasTmdbKey: Boolean get() = tmdbKey.isNotBlank()

    companion object {
        fun defaultRegion(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
    }
}

class SettingsRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val tmdb = stringPreferencesKey("tmdb_key")
        val omdb = stringPreferencesKey("omdb_key")
        val region = stringPreferencesKey("region")
    }

    val settings: StateFlow<Settings?> = context.dataStore.data
        .map { p ->
            Settings(
                tmdbKey = p[Keys.tmdb].orEmpty(),
                omdbKey = p[Keys.omdb].orEmpty(),
                region = p[Keys.region] ?: Settings.defaultRegion(),
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun current(): Settings = settings.value ?: context.dataStore.data.first().let {
        Settings(it[Keys.tmdb].orEmpty(), it[Keys.omdb].orEmpty(), it[Keys.region] ?: Settings.defaultRegion())
    }

    suspend fun update(tmdbKey: String? = null, omdbKey: String? = null, region: String? = null) {
        context.dataStore.edit { p ->
            tmdbKey?.let { p[Keys.tmdb] = it.trim() }
            omdbKey?.let { p[Keys.omdb] = it.trim() }
            region?.let { p[Keys.region] = it.trim().uppercase() }
        }
    }
}
