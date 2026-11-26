package io.github.niteshprasad.reelune.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

// ---------- YTS API models ----------

@Serializable
private data class YtsEnvelope(val status: String = "", @SerialName("status_message") val message: String = "", val data: JsonObject? = null)

@Serializable
private data class YtsList(@SerialName("movie_count") val count: Int = 0, val movies: List<YtsMovie> = emptyList())

@Serializable
private data class YtsDetail(val movie: YtsMovie? = null)

@Serializable
private data class YtsCast(
    val name: String = "",
    @SerialName("character_name") val character: String? = null,
    @SerialName("url_small_image") val image: String? = null,
    @SerialName("imdb_code") val imdbCode: String? = null,
)

@Serializable
private data class YtsMovie(
    val id: Int = 0,
    @SerialName("imdb_code") val imdbCode: String = "",
    val title: String = "",
    @SerialName("title_english") val titleEnglish: String? = null,
    val year: Int = 0,
    val rating: Double = 0.0,
    val runtime: Int = 0,
    val genres: List<String> = emptyList(),
    val summary: String? = null,
    @SerialName("description_full") val descriptionFull: String? = null,
    @SerialName("yt_trailer_code") val trailer: String? = null,
    val language: String? = null,
    @SerialName("mpa_rating") val mpaRating: String? = null,
    @SerialName("background_image_original") val background: String? = null,
    @SerialName("medium_cover_image") val cover: String? = null,
    @SerialName("large_cover_image") val coverLarge: String? = null,
    val cast: List<YtsCast> = emptyList(),
)

/**
 * The YTS catalogue (https://yts.gg/api). No key needed.
 *
 * The API ignores year and language filters, so those are read from YTS's public browse pages,
 * which return slugs rather than ids; such movies get a "ytsslug:" key resolved on opening.
 */
class YtsCatalog : Catalog {
    override val source = CatalogSource.YTS
    override val sorts = listOf(SortOption.LATEST, SortOption.POPULAR, SortOption.TOP_RATED, SortOption.NEWEST, SortOption.TITLE)
    override val defaultSort = SortOption.LATEST

    private val apiBases = listOf("https://movies-api.accel.li/api/v2", "https://yts.gg/api/v2")
    private val siteBase = "https://yts.gg"
    private val pageSize = 24
    private val detailsCache = ConcurrentHashMap<String, MovieDetails>()
    private var languagesCache: List<Language>? = null

    private val genreNames = listOf(
        "Action", "Adventure", "Animation", "Biography", "Comedy", "Crime", "Documentary", "Drama", "Family",
        "Fantasy", "Film-Noir", "History", "Horror", "Music", "Musical", "Mystery", "Romance", "Sci-Fi",
        "Sport", "Thriller", "War", "Western",
    )

    override suspend fun genres(): List<Genre> = genreNames.mapIndexed { i, n -> Genre(i + 1, n) }

    override suspend fun featured(): List<Movie> {
        val list = api<YtsList>("list_movies.json", mapOf("limit" to "20", "sort_by" to "date_added", "minimum_rating" to "6"))
        return list.movies.filter { !it.background.isNullOrBlank() && !it.summary.isNullOrBlank() }.take(6).map { it.toCard() }
    }

    override suspend fun languages(): List<Language> {
        languagesCache?.let { return it }
        val result = try {
            val doc = Jsoup.parse(site("/browse-movies"))
            val seen = LinkedHashMap<String, Language>()
            seen["en"] = Language("en", "English", "")
            val counts = HashMap<String, Int>()
            doc.select("select[name=language] option").forEach { opt ->
                val code = opt.attr("value")
                val m = Regex("""^(.*?)\s*\((\d+)\)$""").find(opt.text().trim()) ?: return@forEach
                val name = m.groupValues[1].trim()
                val count = m.groupValues[2].toInt()
                if (name == "English" || name == "xx" || !code.matches(Regex("[a-z-]{2,8}"))) return@forEach
                // Some languages are listed twice (e.g. "fr" and "fra"): keep the one with more films.
                if ((counts[name] ?: -1) < count) {
                    counts[name] = count
                    seen[name] = Language(code, name, "")
                }
            }
            seen.values.toList().drop(1).sortedByDescending { counts[it.englishName] ?: 0 }.let { listOf(seen.getValue("en")) + it }
        } catch (e: Exception) {
            listOf("en" to "English", "fr" to "French", "ja" to "Japanese", "es" to "Spanish", "it" to "Italian", "de" to "German",
                "zh" to "Chinese", "ko" to "Korean", "hi" to "Hindi", "ta" to "Tamil", "te" to "Telugu", "ml" to "Malayalam")
                .map { (c, n) -> Language(c, n, "") }
        }
        if (result.size > 12) languagesCache = result
        return result
    }

    override suspend fun movies(filters: MovieFilters, page: Int): MoviePage {
        val genre = filters.genreId?.let { id -> genreNames.getOrNull(id - 1) }
        if (filters.year.isNotBlank() || filters.language.isNotBlank()) return browseSite(filters, genre, page)

        val (sortBy, order) = apiSort(filters.sort)
        val list = api<YtsList>(
            "list_movies.json",
            mapOf(
                "page" to "$page",
                "limit" to "$pageSize",
                "sort_by" to sortBy,
                "order_by" to order,
                "genre" to genre,
                "minimum_rating" to filters.minRating.takeIf { it > 0 }?.toString(),
                "query_term" to filters.query.trim().ifBlank { null },
            ),
        )
        val totalPages = maxOf(1, (list.count + pageSize - 1) / pageSize)
        return MoviePage(page, list.movies.map { it.toCard() }, totalPages, list.count)
    }

    override suspend fun details(key: String): MovieDetails {
        detailsCache[key]?.let { return it }
        val id = when {
            key.startsWith("yts:") -> key.removePrefix("yts:").toIntOrNull()
            key.startsWith("ytsslug:") -> resolveSlug(key.removePrefix("ytsslug:"))
            else -> null
        } ?: throw UserFacingException("That movie link isn't valid.")
        return coroutineScope {
            val similar = async { runCatching { api<YtsList>("movie_suggestions.json", mapOf("movie_id" to "$id")).movies.map { it.toCard() } }.getOrDefault(emptyList()) }
            val m = api<YtsDetail>("movie_details.json", mapOf("movie_id" to "$id", "with_images" to "true", "with_cast" to "true")).movie
                ?.takeIf { it.id > 0 } ?: throw UserFacingException("YTS has no record of that movie.", 404)
            m.toDetails(similar.await()).also { detailsCache[key] = it; detailsCache["yts:$id"] = it }
        }
    }

    // ---------- Site browsing (year and language filters) ----------

    private suspend fun browseSite(filters: MovieFilters, genre: String?, page: Int): MoviePage {
        val order = when (filters.sort) {
            SortOption.LATEST -> "latest"
            SortOption.POPULAR -> "downloads"
            SortOption.TOP_RATED -> "rating"
            SortOption.NEWEST -> "year"
            SortOption.TITLE -> "alphabetical"
            SortOption.REVENUE -> "latest"
        }
        val segments = listOf(
            filters.query.trim().ifBlank { "0" }.let { URLEncoder.encode(it, "UTF-8").replace("+", "%20") },
            "all",
            genre?.lowercase() ?: "all",
            filters.minRating.toString(),
            order,
            filters.year.ifBlank { "0" },
            filters.language.ifBlank { "all" },
        )
        // The site redirects "?page=1" to its unfiltered list, so only send page > 1.
        val path = "/browse-movies/" + segments.joinToString("/") + if (page > 1) "?page=$page" else ""
        val html = site(path, mustKeepPath = true)
        val doc = Jsoup.parse(html, siteBase)
        val movies = doc.select(".browse-movie-wrap").mapNotNull { el ->
            val href = el.selectFirst("a.browse-movie-link")?.attr("href") ?: return@mapNotNull null
            val slug = href.substringAfter("/movies/", "").substringBefore('?').trim('/')
            if (slug.isBlank()) return@mapNotNull null
            val titleEl = el.selectFirst(".browse-movie-title")
            val lang = titleEl?.selectFirst("span")?.text()?.trim('[', ']', ' ')?.lowercase()
            titleEl?.select("span")?.remove()
            val year = el.selectFirst(".browse-movie-year")?.text()?.trim()?.toIntOrNull()
            val rating = el.selectFirst("h4.rating")?.text()?.substringBefore('/')?.trim()?.toDoubleOrNull() ?: 0.0
            val genres = el.select("figcaption h4").filterNot { it.hasClass("rating") }.map { it.text().trim() }
            val img = el.selectFirst("img")?.absUrl("src")?.ifBlank { null }
            Movie(
                id = slug.hashCode(),
                title = titleEl?.text()?.trim().orEmpty().ifBlank { slug },
                originalLanguage = lang ?: filters.language.ifBlank { null } ?: "en",
                releaseDate = year?.toString(),
                voteAverage = rating,
                voteCount = if (rating > 0) 1 else 0,
                sourceKey = "ytsslug:$slug",
                posterUrl = img,
                genreNames = genres,
            )
        }
        val hasNext = Regex("""page=\d+["'][^>]*>\s*Next""", RegexOption.IGNORE_CASE).containsMatchIn(html)
        return MoviePage(page, movies, if (hasNext) page + 1 else page, -1)
    }

    private suspend fun resolveSlug(slug: String): Int {
        if (!slug.matches(Regex("[a-z0-9-]{1,200}"))) throw UserFacingException("That movie link isn't valid.")
        val html = site("/movies/$slug")
        return Regex("""data-movie-id="(\d+)"""").find(html)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw UserFacingException("Couldn't find that movie on YTS.", 404)
    }

    // ---------- HTTP ----------

    private suspend inline fun <reified T> api(endpoint: String, params: Map<String, String?>): T {
        var lastError: Exception? = null
        for (base in apiBases) {
            val url = "$base/$endpoint".toHttpUrl().newBuilder().apply {
                params.forEach { (k, v) -> if (!v.isNullOrBlank()) addQueryParameter(k, v) }
            }.build()
            try {
                val body = Http.get(Request.Builder().url(url).header("Accept", "application/json").build())
                val env = Http.json.decodeFromString<YtsEnvelope>(body)
                if (env.status != "ok" || env.data == null) throw UserFacingException(env.message.ifBlank { "YTS returned an error." })
                return Http.json.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), env.data)
            } catch (e: UserFacingException) {
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw (lastError as? UserFacingException) ?: UserFacingException("Couldn't reach YTS. Check your connection and try again.")
    }

    private suspend fun site(path: String, mustKeepPath: Boolean = false): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(siteBase + path)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36")
            .header("Accept", "text/html")
            .build()
        val response = try {
            Http.client.newCall(request).execute()
        } catch (e: IOException) {
            throw UserFacingException("Couldn't reach YTS. Check your connection and try again.")
        }
        response.use { r ->
            if (r.code == 404) throw UserFacingException("Not found on YTS.", 404)
            if (!r.isSuccessful) throw UserFacingException("YTS responded with an error (${r.code}).", r.code)
            if (mustKeepPath) {
                val wanted = path.substringBefore('?').trimEnd('/')
                val got = r.request.url.encodedPath.trimEnd('/')
                if (URLDecoder.decode(got, "UTF-8") != URLDecoder.decode(wanted, "UTF-8")) {
                    throw UserFacingException("YTS couldn't apply these filters. Try a different combination.")
                }
            }
            r.body.string()
        }
    }

    private fun apiSort(sort: SortOption): Pair<String, String> = when (sort) {
        SortOption.LATEST -> "date_added" to "desc"
        SortOption.POPULAR -> "download_count" to "desc"
        SortOption.TOP_RATED -> "rating" to "desc"
        SortOption.NEWEST -> "year" to "desc"
        SortOption.TITLE -> "title" to "asc"
        SortOption.REVENUE -> "date_added" to "desc"
    }

    // ---------- Mapping ----------

    private fun YtsMovie.displayTitle() = titleEnglish?.takeIf { it.isNotBlank() } ?: title

    private fun YtsMovie.toCard() = Movie(
        id = id,
        title = displayTitle(),
        originalLanguage = language,
        overview = summary.orEmpty().ifBlank { descriptionFull.orEmpty() },
        releaseDate = year.takeIf { it > 0 }?.toString(),
        voteAverage = rating,
        voteCount = if (rating > 0) 1 else 0,
        sourceKey = "yts:$id",
        posterUrl = cover,
        backdropUrl = background?.ifBlank { null },
        genreNames = genres,
    )

    private fun YtsMovie.toDetails(similar: List<Movie>) = MovieDetails(
        id = id,
        imdbId = imdbCode.ifBlank { null },
        title = displayTitle(),
        originalTitle = title,
        originalLanguage = language,
        overview = descriptionFull.orEmpty().ifBlank { summary.orEmpty() },
        runtime = runtime.takeIf { it > 0 },
        releaseDate = year.takeIf { it > 0 }?.toString(),
        genres = genres.mapIndexed { i, g -> Genre(i, g) },
        spokenLanguages = listOfNotNull(language?.let { SpokenLanguage(it, "") }),
        videos = VideoList(listOfNotNull(trailer?.takeIf { it.isNotBlank() }?.let { Video(it, "YouTube", "Trailer", true) })),
        credits = Credits(
            cast = cast.mapIndexed { i, c ->
                CastMember(
                    id = i,
                    name = c.name,
                    character = c.character,
                    photoUrl = c.image?.ifBlank { null },
                    link = c.imdbCode?.takeIf { it.isNotBlank() }?.let { "https://www.imdb.com/name/nm${it.removePrefix("nm")}/" },
                )
            },
        ),
        similar = MoviePage(results = similar),
        source = CatalogSource.YTS,
        posterUrl = coverLarge?.ifBlank { null } ?: cover,
        backdropUrl = background?.ifBlank { null },
        sourceImdbRating = rating.takeIf { it > 0 },
        sourceCertification = mpaRating?.takeIf { it.isNotBlank() },
    )
}
