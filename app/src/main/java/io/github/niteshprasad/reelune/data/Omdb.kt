package io.github.niteshprasad.reelune.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** IMDb rating, Rotten Tomatoes and Metacritic scores from OMDb (https://www.omdbapi.com). */
data class Scores(
    val imdbRating: String?,
    val imdbVotes: String?,
    val rottenTomatoes: String?,
    val metacritic: String?,
)

sealed interface ScoresResult {
    data class Loaded(val scores: Scores) : ScoresResult
    data object NoKey : ScoresResult
    data class Failed(val message: String) : ScoresResult
}

@Serializable
private data class OmdbRating(@SerialName("Source") val source: String, @SerialName("Value") val value: String)

@Serializable
private data class OmdbResponse(
    @SerialName("Response") val response: String = "False",
    @SerialName("Error") val error: String? = null,
    @SerialName("imdbRating") val imdbRating: String? = null,
    @SerialName("imdbVotes") val imdbVotes: String? = null,
    @SerialName("Ratings") val ratings: List<OmdbRating> = emptyList(),
)

class Omdb(private val settings: SettingsRepository) {
    private val cache = ConcurrentHashMap<String, Scores>()

    suspend fun scores(imdbId: String): ScoresResult {
        val key = settings.current().omdbKey
        if (key.isBlank()) return ScoresResult.NoKey
        cache[imdbId]?.let { return ScoresResult.Loaded(it) }
        return try {
            val url = "https://www.omdbapi.com/".toHttpUrl().newBuilder()
                .addQueryParameter("i", imdbId)
                .addQueryParameter("apikey", key)
                .build()
            val body = try {
                Http.get(Request.Builder().url(url).build())
            } catch (e: HttpStatusException) {
                // OMDb answers a bad key with 401 and a JSON body.
                e.body
            }
            val r = Http.json.decodeFromString<OmdbResponse>(body)
            if (r.response != "True") {
                val msg = r.error.orEmpty()
                return ScoresResult.Failed(if (msg.contains("key", true)) "OMDb didn't accept your key. Check it in Settings." else msg.ifBlank { "No scores found." })
            }
            fun clean(v: String?) = v?.takeIf { it.isNotBlank() && it != "N/A" }
            val scores = Scores(
                imdbRating = clean(r.imdbRating),
                imdbVotes = clean(r.imdbVotes),
                rottenTomatoes = clean(r.ratings.firstOrNull { it.source == "Rotten Tomatoes" }?.value),
                metacritic = clean(r.ratings.firstOrNull { it.source == "Metacritic" }?.value),
            )
            cache[imdbId] = scores
            ScoresResult.Loaded(scores)
        } catch (e: UserFacingException) {
            ScoresResult.Failed(e.message ?: "Couldn't load scores.")
        } catch (e: Exception) {
            ScoresResult.Failed("Couldn't load scores.")
        }
    }
}
