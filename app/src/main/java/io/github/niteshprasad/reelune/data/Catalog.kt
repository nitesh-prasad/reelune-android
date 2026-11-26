package io.github.niteshprasad.reelune.data

/** Where Reelune gets its movies. Chosen in Settings. */
enum class CatalogSource(val label: String, val description: String) {
    TMDB("TMDB", "The Movie Database. Huge catalogue, trailers, age ratings for your country. Needs a free key."),
    YTS("YTS", "No key needed. Includes IMDb ratings and US age ratings."),
}

/** A movie catalogue: browse, filter, search and look up details. */
interface Catalog {
    val source: CatalogSource

    /** Sort orders this catalogue supports, in menu order. */
    val sorts: List<SortOption>
    val defaultSort: SortOption

    /** A handful of films with backdrops for the featured carousel. */
    suspend fun featured(): List<Movie>

    suspend fun genres(): List<Genre>

    suspend fun languages(): List<Language>

    suspend fun movies(filters: MovieFilters, page: Int): MoviePage

    /** Details for a [Movie.navKey]. */
    suspend fun details(key: String): MovieDetails
}
