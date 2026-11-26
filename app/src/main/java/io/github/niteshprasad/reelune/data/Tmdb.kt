package io.github.niteshprasad.reelune.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// ---------- Models ----------

@Serializable
data class Movie(
    val id: Int,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
) {
    val year: Int? get() = releaseDate?.take(4)?.toIntOrNull()
}

@Serializable
data class MoviePage(
    val page: Int = 1,
    val results: List<Movie> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
)

@Serializable
data class Genre(val id: Int, val name: String)

@Serializable
data class GenreList(val genres: List<Genre> = emptyList())

@Serializable
data class Language(
    @SerialName("iso_639_1") val code: String,
    @SerialName("english_name") val englishName: String = "",
    val name: String = "",
)

@Serializable
data class Video(val key: String, val site: String = "", val type: String = "", val official: Boolean = false, val name: String = "")

@Serializable
data class VideoList(val results: List<Video> = emptyList())

@Serializable
data class CastMember(val id: Int, val name: String, val character: String? = null, @SerialName("profile_path") val profilePath: String? = null)

@Serializable
data class CrewMember(val id: Int, val name: String, val job: String? = null)

@Serializable
data class Credits(val cast: List<CastMember> = emptyList(), val crew: List<CrewMember> = emptyList())

@Serializable
data class ReleaseDate(val certification: String = "", val type: Int = 0)

@Serializable
data class CountryReleases(@SerialName("iso_3166_1") val country: String, @SerialName("release_dates") val releaseDates: List<ReleaseDate> = emptyList())

@Serializable
data class ReleaseDates(val results: List<CountryReleases> = emptyList())

@Serializable
data class SpokenLanguage(@SerialName("iso_639_1") val code: String, @SerialName("english_name") val englishName: String = "")

@Serializable
data class MovieDetails(
    val id: Int,
    @SerialName("imdb_id") val imdbId: String? = null,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    val overview: String = "",
    val tagline: String? = null,
    val runtime: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val genres: List<Genre> = emptyList(),
    @SerialName("spoken_languages") val spokenLanguages: List<SpokenLanguage> = emptyList(),
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val videos: VideoList = VideoList(),
    val credits: Credits = Credits(),
    @SerialName("release_dates") val releaseDates: ReleaseDates = ReleaseDates(),
    val similar: MoviePage = MoviePage(),
) {
    val year: Int? get() = releaseDate?.take(4)?.toIntOrNull()

    /** Best YouTube trailer: official trailers first, then teasers. */
    val trailerKey: String?
        get() = videos.results
            .filter { it.site.equals("YouTube", true) }
            .sortedWith(
                compareByDescending<Video> { it.type == "Trailer" }
                    .thenByDescending { it.official }
                    .thenByDescending { it.type == "Teaser" },
            )
            .firstOrNull()?.key

    val directors: List<String> get() = credits.crew.filter { it.job == "Director" }.map { it.name }.distinct()

    /** Age certification for [region], falling back to the US, preferring theatrical releases. */
    fun certification(region: String): Pair<String, String>? {
        fun forCountry(code: String): String? = releaseDates.results
            .firstOrNull { it.country.equals(code, true) }
            ?.releaseDates
            ?.filter { it.certification.isNotBlank() }
            ?.sortedBy { if (it.type == 3) 0 else if (it.type == 4) 1 else 2 }
            ?.firstOrNull()?.certification?.trim()
        forCountry(region)?.let { return it to region.uppercase() }
        if (!region.equals("US", true)) forCountry("US")?.let { return it to "US" }
        return null
    }
}

// ---------- Filters ----------

enum class SortOption(val label: String, val apiValue: String) {
    POPULAR("Popular", "popularity.desc"),
    TOP_RATED("Top rated", "vote_average.desc"),
    NEWEST("Newest", "primary_release_date.desc"),
    REVENUE("Box office", "revenue.desc"),
    TITLE("Title A–Z", "title.asc"),
}

/** Year filter: a single year ("2019"), a range ("2010-2019") or empty for any. */
data class MovieFilters(
    val query: String = "",
    val genreId: Int? = null,
    val year: String = "",
    val language: String = "",
    val sort: SortOption = SortOption.POPULAR,
    val minRating: Int = 0,
) {
    val isDefault: Boolean get() = this == MovieFilters()
    val hasFilters: Boolean get() = genreId != null || year.isNotEmpty() || language.isNotEmpty() || sort != SortOption.POPULAR || minRating > 0
}

// ---------- Client ----------

class Tmdb(private val settings: SettingsRepository) {
    private val base = "https://api.themoviedb.org/3"
    private val cache = ConcurrentHashMap<String, Any>()

    private suspend inline fun <reified T> call(path: String, params: Map<String, String?> = emptyMap(), keyOverride: String? = null): T {
        val key = keyOverride ?: settings.current().tmdbKey
        if (key.isBlank()) throw UserFacingException("Add your TMDB key in Settings to load movies.")
        val url = "$base$path".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> if (!v.isNullOrBlank()) addQueryParameter(k, v) }
            if (!isBearer(key)) addQueryParameter("api_key", key)
        }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json").apply {
            if (isBearer(key)) header("Authorization", "Bearer $key")
        }.build()
        val body = try {
            Http.get(request)
        } catch (e: HttpStatusException) {
            throw when (e.code) {
                401 -> UserFacingException("TMDB didn't accept your key. Check it in Settings.", 401)
                404 -> UserFacingException("TMDB has no record of that movie.", 404)
                429 -> UserFacingException("TMDB is limiting requests. Wait a moment and try again.", 429)
                else -> UserFacingException("TMDB responded with an error (${e.code}). Try again later.", e.code)
            }
        }
        return Http.json.decodeFromString<T>(body)
    }

    /** A v4 "API Read Access Token" is a JWT; a v3 "API key" is 32 hex characters. */
    private fun isBearer(key: String) = key.count { it == '.' } == 2 && key.length > 60

    /** Checks a key before saving it. Throws [UserFacingException] when it doesn't work. */
    suspend fun verifyKey(key: String) {
        call<GenreList>("/genre/movie/list", mapOf("language" to "en-US"), keyOverride = key.trim())
    }

    suspend fun trending(): List<Movie> = call<MoviePage>("/trending/movie/week", mapOf("language" to "en-US")).results

    suspend fun genres(): List<Genre> = cached("genres") {
        call<GenreList>("/genre/movie/list", mapOf("language" to "en-US")).genres
    }

    suspend fun languages(): List<Language> = cached("languages") {
        call<List<Language>>("/configuration/languages")
            .filter { it.code != "xx" && it.englishName.isNotBlank() }
            .sortedBy { it.englishName }
    }

    suspend fun movies(filters: MovieFilters, page: Int): MoviePage {
        val (from, to) = yearBounds(filters.year)
        if (filters.query.isNotBlank()) {
            val single = filters.year.takeIf { it.length == 4 }
            val result = call<MoviePage>(
                "/search/movie",
                mapOf("query" to filters.query, "page" to "$page", "include_adult" to "false", "language" to "en-US", "primary_release_year" to single),
            )
            // Search ignores most filters, so apply them to the results here.
            val filtered = result.results.filter { m ->
                (filters.genreId == null || filters.genreId in m.genreIds) &&
                    (filters.language.isEmpty() || m.originalLanguage == filters.language) &&
                    (filters.minRating == 0 || m.voteAverage >= filters.minRating) &&
                    (from == null || (m.year ?: 0) in (from.year..to!!.year))
            }
            return result.copy(results = filtered)
        }
        val today = LocalDate.now().toString()
        val params = mutableMapOf<String, String?>(
            "page" to "$page",
            "include_adult" to "false",
            "include_video" to "false",
            "language" to "en-US",
            "sort_by" to filters.sort.apiValue,
            "with_genres" to filters.genreId?.toString(),
            "with_original_language" to filters.language.ifEmpty { null },
            "primary_release_date.gte" to from?.toString(),
            "primary_release_date.lte" to (to?.toString() ?: if (filters.sort == SortOption.NEWEST) today else null),
            "vote_average.gte" to filters.minRating.takeIf { it > 0 }?.toString(),
        )
        // Keep obscure titles with a handful of votes from dominating rating-based lists.
        when {
            filters.sort == SortOption.TOP_RATED -> params["vote_count.gte"] = "300"
            filters.minRating > 0 -> params["vote_count.gte"] = "50"
            filters.sort == SortOption.NEWEST -> params["vote_count.gte"] = "5"
        }
        return call("/discover/movie", params)
    }

    suspend fun details(id: Int): MovieDetails = cached("movie:$id") {
        call<MovieDetails>(
            "/movie/$id",
            mapOf("language" to "en-US", "append_to_response" to "videos,credits,release_dates,similar"),
        )
    }

    private fun yearBounds(year: String): Pair<LocalDate?, LocalDate?> {
        if (year.isBlank()) return null to null
        val parts = year.split("-").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            1 -> LocalDate.of(parts[0], 1, 1) to LocalDate.of(parts[0], 12, 31)
            2 -> LocalDate.of(minOf(parts[0], parts[1]), 1, 1) to LocalDate.of(maxOf(parts[0], parts[1]), 12, 31)
            else -> null to null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T =
        (cache[key] as T?) ?: load().also { cache[key] = it }

    companion object {
        fun poster(path: String?, size: String = "w342"): String? = path?.let { "https://image.tmdb.org/t/p/$size$it" }
        fun backdrop(path: String?, size: String = "w1280"): String? = path?.let { "https://image.tmdb.org/t/p/$size$it" }
        fun profile(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w185$it" }
    }
}
