package io.github.niteshprasad.reelune.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.niteshprasad.reelune.data.Movie
import io.github.niteshprasad.reelune.data.MovieFilters
import io.github.niteshprasad.reelune.data.SortOption
import io.github.niteshprasad.reelune.ui.UseBackdrop
import io.github.niteshprasad.reelune.ui.components.GlassButton
import io.github.niteshprasad.reelune.ui.components.MessagePanel
import io.github.niteshprasad.reelune.ui.components.PosterCard
import io.github.niteshprasad.reelune.ui.components.PrimaryButton
import io.github.niteshprasad.reelune.ui.components.RatingPill
import io.github.niteshprasad.reelune.ui.components.Shimmer
import androidx.compose.ui.layout.layout
import io.github.niteshprasad.reelune.ui.theme.Body
import io.github.niteshprasad.reelune.ui.theme.fadeToTransparent
import androidx.compose.ui.graphics.Color
import io.github.niteshprasad.reelune.ui.theme.Reel
import io.github.niteshprasad.reelune.ui.theme.glass
import kotlinx.coroutines.delay
import java.time.Year

@Composable
fun HomeScreen(onOpenMovie: (String) -> Unit, onOpenSettings: () -> Unit, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    var sheet by remember { mutableStateOf<FilterKind?>(null) }
    val languageNames = remember(state.languages) { state.languages.associate { it.code to it.englishName } }

    // Load the next page when the last rows come into view.
    LaunchedEffect(grid, state.filters) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= grid.layoutInfo.totalItemsCount - 8) vm.loadNext() }
    }
    LaunchedEffect(state.filters) { if (grid.firstVisibleItemIndex > 2) grid.scrollToItem(if (state.showFeatured) 1 else 0) }
    if (!state.showFeatured) UseBackdrop(state.movies.firstOrNull()?.let { it.backdrop ?: it.poster })

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(minSize = 112.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "top") {
                if (state.showFeatured) {
                    FeaturedCarousel(state.featured, onOpenMovie)
                } else {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = if (state.showFeatured) 8.dp else 72.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            heading(state.filters, state.genres.firstOrNull { it.id == state.filters.genreId }?.name, languageNames[state.filters.language]),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Reel.Text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    state.totalResults?.let {
                        Text("%,d movies".format(it), style = MaterialTheme.typography.bodySmall, color = Reel.TextFaint)
                    }
                    FilterRow(state, languageNames, onOpen = { sheet = it }, onClear = vm::clearFilters)
                }
            }

            items(state.movies, key = { it.navKey }) { movie ->
                PosterCard(movie, onClick = { onOpenMovie(movie.navKey) }, languageName = { languageNames[it] })
            }

            if (state.loading && state.movies.isEmpty()) {
                items(12, key = { "ph$it" }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Shimmer(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)))
                        Shimmer(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(6.dp)))
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "foot") {
                Box(Modifier.fillMaxWidth().heightIn(min = 80.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.error != null && state.movies.isEmpty() -> MessagePanel(
                            "Movies didn't load",
                            state.error!!,
                            Modifier.padding(top = 12.dp),
                        ) {
                            PrimaryButton("Try again", onClick = vm::reload)
                            if (state.error!!.contains("Settings")) GlassButton("Open settings", onClick = onOpenSettings)
                        }
                        state.error != null -> GlassButton(
                            "Couldn't load more. Try again",
                            onClick = vm::loadNext,
                            icon = { Icon(Icons.Rounded.Refresh, null, tint = Reel.Text, modifier = Modifier.size(18.dp)) },
                        )
                        state.loading && state.movies.isNotEmpty() -> CircularProgressIndicator(color = Reel.Moonglow, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                        !state.loading && state.movies.isEmpty() -> MessagePanel(
                            "No movies match",
                            "Try another spelling, a wider year range or fewer filters.",
                            Modifier.padding(top = 12.dp),
                        ) { GlassButton("Clear filters", onClick = { vm.onSearchText(""); vm.clearFilters() }) }
                        !state.hasMore && state.movies.isNotEmpty() -> Text("That's every match.", color = Reel.TextFaint, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        TopBar(
            searchText = state.searchText,
            onSearch = vm::onSearchText,
            onOpenSettings = onOpenSettings,
            elevated = remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 40 } }.value,
        )
    }

    sheet?.let { kind ->
        FilterSheet(
            kind = kind,
            state = state,
            onDismiss = { sheet = null },
            onPick = { filters -> sheet = null; vm.setFilters(filters) },
        )
    }
}

private fun heading(f: MovieFilters, genre: String?, language: String?): String {
    if (f.query.isNotBlank()) return "Results for “${f.query}”"
    val noun = listOfNotNull(language, genre?.lowercase()).joinToString(" ").replaceFirstChar { it.uppercase() }
    val base = if (noun.isBlank()) {
        when {
            f.hasFilters -> "Movies"
            f.defaultSort == SortOption.LATEST -> "Recently added"
            else -> "Popular right now"
        }
    } else "$noun movies"
    val year = when {
        f.year.isBlank() -> ""
        f.year.length == 4 -> " from ${f.year}"
        f.year.matches(Regex("""\d{3}0-\d{3}9""")) && f.year.take(3) == f.year.substring(5, 8) -> " from the ${f.year.take(4)}s"
        else -> " from ${f.year.replace("-", "–")}"
    }
    return base + year
}

// ---------- Top bar ----------

@Composable
private fun TopBar(searchText: String, onSearch: (String) -> Unit, onOpenSettings: () -> Unit, elevated: Boolean) {
    val focus = LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(52.dp)
            .glass(CircleShape, strong = elevated)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Reel.TextFaint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (searchText.isEmpty()) Text("Search movies", color = Reel.TextFaint, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                value = searchText,
                onValueChange = onSearch,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Reel.Text, fontFamily = Body),
                cursorBrush = SolidColor(Reel.Moonglow),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (searchText.isNotEmpty()) {
            IconButton(onClick = { onSearch("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = Reel.TextSoft) }
        }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Reel.TextSoft) }
    }
}

// ---------- Featured ----------

@Composable
private fun FeaturedCarousel(items: List<Movie>, onOpenMovie: (String) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(items, index) {
        delay(8000)
        index = (index + 1) % items.size
    }
    val movie = items[index.coerceIn(0, items.lastIndex)]
    UseBackdrop(movie.backdrop)

    Box(Modifier.fillMaxWidth().fullBleed(16.dp).heightIn(min = 520.dp)) {
        AnimatedContent(
            targetState = movie,
            transitionSpec = { fadeIn(tween(900)) togetherWith fadeOut(tween(700)) },
            label = "hero",
            modifier = Modifier.matchParentSize(),
        ) { m ->
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = m.backdropLarge,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(440.dp).fadeToTransparent(start = 0.45f),
                )
                // Darken the top so the status bar and search bar stay readable.
                Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(Reel.Night.copy(alpha = 0.45f), Color.Transparent))))
            }
        }
        AnimatedContent(
            targetState = movie,
            transitionSpec = { (fadeIn(tween(600, 250)) + slideInVertically(tween(700, 250)) { it / 6 }) togetherWith fadeOut(tween(250)) },
            label = "heroText",
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, top = 260.dp),
        ) { m ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                Text(m.title, style = MaterialTheme.typography.displayLarge, color = Reel.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (m.voteAverage > 0) RatingPill(m.voteAverage)
                    m.year?.let { Text(it.toString(), color = Reel.TextSoft, style = MaterialTheme.typography.bodyMedium) }
                }
                Text(m.overview, style = MaterialTheme.typography.bodyMedium, color = Reel.TextSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(
                        "Details and trailer",
                        onClick = { onOpenMovie(m.navKey) },
                        icon = { Icon(Icons.Rounded.Info, null, tint = Reel.Night, modifier = Modifier.size(18.dp)) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.forEachIndexed { i, _ ->
                            Box(
                                Modifier
                                    .size(width = if (i == index) 18.dp else 7.dp, height = 7.dp)
                                    .clip(CircleShape)
                                    .background(if (i == index) Reel.Lunar else Reel.Lunar.copy(alpha = 0.3f))
                                    .clickable(role = Role.Tab, onClickLabel = "Show featured movie ${i + 1}") { index = i },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- Filters ----------

enum class FilterKind { GENRE, YEAR, LANGUAGE, SORT, RATING }

@Composable
private fun FilterRow(state: HomeState, languageNames: Map<String, String>, onOpen: (FilterKind) -> Unit, onClear: () -> Unit) {
    val f = state.filters
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip("Genre", state.genres.firstOrNull { it.id == f.genreId }?.name, onClick = { onOpen(FilterKind.GENRE) })
        FilterChip("Year", yearLabel(f.year), onClick = { onOpen(FilterKind.YEAR) })
        FilterChip("Language", languageNames[f.language] ?: f.language.ifEmpty { null }, onClick = { onOpen(FilterKind.LANGUAGE) })
        FilterChip("Rating", f.minRating.takeIf { it > 0 }?.let { "$it+" }, onClick = { onOpen(FilterKind.RATING) })
        FilterChip("Sort", f.sort.label.takeIf { f.sort != f.defaultSort }, onClick = { onOpen(FilterKind.SORT) })
        if (f.hasFilters) {
            Box(
                Modifier.height(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClear).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Clear", color = Reel.Moonglow, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun FilterChip(label: String, value: String?, onClick: () -> Unit) {
    val active = value != null
    Row(
        Modifier
            .height(40.dp)
            .glass(RoundedCornerShape(12.dp))
            .then(if (active) Modifier.background(Reel.Moonglow.copy(alpha = 0.12f)) else Modifier)
            .clickable(role = Role.Button, onClickLabel = "Change $label filter", onClick = onClick)
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = Reel.TextFaint, style = MaterialTheme.typography.labelMedium)
        Text(value ?: "Any", color = if (active) Reel.Moonglow else Reel.Text, style = MaterialTheme.typography.labelMedium)
        Icon(Icons.Rounded.ExpandMore, null, tint = Reel.TextFaint, modifier = Modifier.size(18.dp))
    }
}

fun yearLabel(year: String): String? = when {
    year.isBlank() -> null
    year.length == 4 -> year
    else -> decadeOptions().firstOrNull { it.first == year }?.second ?: year.replace("-", "–")
}

fun decadeOptions(): List<Pair<String, String>> {
    val now = Year.now().value
    return listOf("${now - 4}-$now" to "Last 5 years") +
        (2020 downTo 1930 step 10).map { "$it-${it + 9}" to "${it}s" }
}

/** Lets a grid item ignore the grid's side padding so images can run edge to edge. */
private fun Modifier.fullBleed(side: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val extra = side.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra, minWidth = constraints.minWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-side.roundToPx(), 0) }
}
