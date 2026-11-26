package io.github.niteshprasad.reelune.ui.details

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.niteshprasad.reelune.data.CatalogSource
import io.github.niteshprasad.reelune.data.MovieDetails
import io.github.niteshprasad.reelune.data.ScoresResult
import io.github.niteshprasad.reelune.plugin.PluginSources
import io.github.niteshprasad.reelune.plugin.Source
import io.github.niteshprasad.reelune.ui.UseBackdrop
import io.github.niteshprasad.reelune.ui.components.CertBadge
import io.github.niteshprasad.reelune.ui.components.GlassButton
import io.github.niteshprasad.reelune.ui.components.ImdbButton
import io.github.niteshprasad.reelune.ui.components.MessagePanel
import io.github.niteshprasad.reelune.ui.components.PosterCard
import io.github.niteshprasad.reelune.ui.components.PrimaryButton
import io.github.niteshprasad.reelune.ui.components.SectionTitle
import io.github.niteshprasad.reelune.ui.components.Shimmer
import io.github.niteshprasad.reelune.ui.components.formatBytes
import io.github.niteshprasad.reelune.ui.components.formatRuntime
import io.github.niteshprasad.reelune.ui.components.languageDisplayName
import io.github.niteshprasad.reelune.ui.player.PlayerActivity
import io.github.niteshprasad.reelune.ui.theme.Reel
import io.github.niteshprasad.reelune.ui.theme.fadeToTransparent
import io.github.niteshprasad.reelune.ui.theme.glass
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    movieKey: String,
    onBack: () -> Unit,
    onOpenMovie: (String) -> Unit,
    onOpenSettings: () -> Unit,
    vm: DetailsViewModel = viewModel(key = "movie-$movieKey", factory = DetailsViewModel.factory(movieKey)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTrailer by remember { mutableStateOf(false) }
    val movie = state.movie
    UseBackdrop(movie?.backdropSmall)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Backdrop
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                AsyncImage(
                    model = movie?.backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().fadeToTransparent(start = 0.35f),
                )
                Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Reel.Night.copy(alpha = 0.45f), Color.Transparent))))
            }

            when {
                state.error != null && movie == null -> MessagePanel(
                    "This movie didn't load",
                    state.error!!,
                    Modifier.padding(16.dp).fillMaxWidth(),
                ) {
                    PrimaryButton("Try again", onClick = vm::load)
                    if (state.error!!.contains("Settings")) GlassButton("Open settings", onClick = onOpenSettings)
                }
                movie == null -> LoadingDetails()
                else -> AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 12 }) {
                    MovieBody(
                        movie = movie,
                        state = state,
                        onTrailer = { showTrailer = true },
                        onImdb = { openUri(context, "https://www.imdb.com/title/${movie.imdbId}/") },
                        onOpenSettings = onOpenSettings,
                        onOpenMovie = onOpenMovie,
                        onPlay = { pluginId, source ->
                            vm.play(pluginId, source) { context.startActivity(Intent(context, PlayerActivity::class.java)) }
                        },
                        onLink = { openUri(context, it) },
                        onRetrySources = vm::retrySources,
                    )
                }
            }
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(8.dp))
        }

        // Back button floating over the backdrop
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp)
                .size(44.dp)
                .glass(CircleShape, strong = true),
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Reel.Text) }
    }

    if (showTrailer && movie?.trailerKey != null) {
        TrailerDialog(movie.trailerKey!!, movie.title, onDismiss = { showTrailer = false })
    }
    state.streamError?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissStreamError,
            confirmButton = { TextButton(onClick = vm::dismissStreamError) { Text("OK", color = Reel.Moonglow) } },
            title = { Text("This stream couldn't start") },
            text = { Text(msg) },
            containerColor = Color(0xFF1E1840),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovieBody(
    movie: MovieDetails,
    state: DetailsState,
    onTrailer: () -> Unit,
    onImdb: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMovie: (String) -> Unit,
    onPlay: (String, Source) -> Unit,
    onLink: (String) -> Unit,
    onRetrySources: () -> Unit,
) {
    Column(Modifier.offset(y = (-96).dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            AsyncImage(
                model = movie.poster,
                contentDescription = "${movie.title} poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(Reel.Twilight),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(movie.title, style = MaterialTheme.typography.displayMedium, color = Reel.Text, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                movie.certification(state.region)?.let { (cert, country) -> CertBadge(cert, country) }
                movie.year?.let { Meta(it.toString()) }
                formatRuntime(movie.runtime)?.let { Meta(it) }
                movie.originalLanguage?.takeIf { it != "en" }?.let { languageDisplayName(it)?.let { n -> Meta(n) } }
            }
            if (movie.genres.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    movie.genres.forEach { g ->
                        Text(
                            g.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = Reel.TextSoft,
                            modifier = Modifier.glass(CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            movie.tagline?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = Reel.Lunar)
            }
            Text(movie.overview.ifBlank { "No synopsis yet." }, style = MaterialTheme.typography.bodyLarge, color = Reel.TextSoft)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (movie.trailerKey != null) {
                    PrimaryButton("Watch trailer", onClick = onTrailer, icon = { Icon(Icons.Rounded.PlayArrow, null, tint = Reel.Night) })
                }
                if (!movie.imdbId.isNullOrBlank()) ImdbButton(onClick = onImdb)
            }
        }

        Scores(movie, state.scores, onOpenSettings, Modifier.padding(horizontal = 16.dp))

        Facts(movie, Modifier.padding(horizontal = 16.dp))

        if (state.pluginCount > 0) {
            SourcesSection(state, onPlay, onLink, onRetrySources, Modifier.padding(horizontal = 16.dp))
        }

        if (movie.credits.cast.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Cast", Modifier.padding(horizontal = 16.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(movie.credits.cast.take(20), key = { it.id }) { c ->
                        Row(
                            Modifier.glass(CircleShape).clickable(role = Role.Button) { onLink(c.page) }.padding(6.dp).padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Reel.Twilight), contentAlignment = Alignment.Center) {
                                Text(c.name.take(1), color = Reel.TextSoft, style = MaterialTheme.typography.titleSmall)
                                AsyncImage(c.photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Column {
                                Text(c.name, style = MaterialTheme.typography.titleSmall, color = Reel.Text)
                                c.character?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Reel.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(140.dp)) }
                            }
                        }
                    }
                }
            }
        }

        val similar = movie.similar.results.filter { it.poster != null }.take(12)
        if (similar.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("You might also like", Modifier.padding(horizontal = 16.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(similar, key = { it.navKey }) { m -> PosterCard(m, onClick = { onOpenMovie(m.navKey) }, modifier = Modifier.width(120.dp)) }
                }
            }
        }

        Text(
            if (movie.source == CatalogSource.YTS) "Movie data from YTS." else "Movie data from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.",
            style = MaterialTheme.typography.bodySmall,
            color = Reel.TextFaint,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun Meta(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Reel.TextSoft)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Scores(movie: MovieDetails, scores: ScoresResult?, onOpenSettings: () -> Unit, modifier: Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
        val loaded = (scores as? ScoresResult.Loaded)?.scores
        val catalogImdb = movie.sourceImdbRating?.let { String.format(Locale.US, "%.1f/10", it) }
        ScoreCard(
            logo = "IMDb", logoBg = Reel.Imdb, logoFg = Color(0xFF111111),
            value = loaded?.imdbRating?.let { "$it/10" } ?: catalogImdb,
            label = loaded?.imdbVotes?.let { "$it votes" } ?: "IMDb rating",
            loading = scores == null && !movie.imdbId.isNullOrBlank() && catalogImdb == null,
            hint = if (scores is ScoresResult.NoKey && catalogImdb == null) "Add an OMDb key" else null,
            onHint = onOpenSettings,
            modifier = Modifier.weight(1f),
        )
        ScoreCard(
            logo = "RT", logoBg = Reel.Tomato.copy(alpha = 0.18f), logoFg = Reel.Tomato,
            value = loaded?.rottenTomatoes,
            label = "Rotten Tomatoes",
            loading = scores == null && !movie.imdbId.isNullOrBlank(),
            hint = when (scores) {
                is ScoresResult.NoKey -> "Add an OMDb key"
                is ScoresResult.Failed -> if (scores.message.contains("key")) "Fix your OMDb key" else null
                else -> null
            },
            onHint = onOpenSettings,
            modifier = Modifier.weight(1f),
        )
        if (movie.source == CatalogSource.TMDB) ScoreCard(
            logo = "TMDB", logoBg = Color(0xFF0D253F), logoFg = Color(0xFF01B4E4),
            value = movie.voteAverage.takeIf { movie.voteCount > 0 }?.let { String.format(Locale.US, "%.1f/10", it) },
            label = "%,d TMDB votes".format(movie.voteCount),
            loading = false, hint = null, onHint = {},
            modifier = Modifier.weight(1f),
        )
        loaded?.metacritic?.let {
            ScoreCard(logo = "MC", logoBg = Color(0xFF2A2A2A), logoFg = Color.White, value = it, label = "Metacritic", loading = false, hint = null, onHint = {}, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScoreCard(
    logo: String, logoBg: Color, logoFg: Color, value: String?, label: String,
    loading: Boolean, hint: String?, onHint: () -> Unit, modifier: Modifier,
) {
    Row(
        modifier.heightIn(min = 72.dp).glass(RoundedCornerShape(18.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(logoBg), contentAlignment = Alignment.Center) {
            Text(logo, color = logoFg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            when {
                loading -> Shimmer(Modifier.width(64.dp).height(20.dp).clip(RoundedCornerShape(6.dp)))
                value != null -> Text(value, style = MaterialTheme.typography.titleLarge, color = Reel.Text)
                hint == null -> Text("N/A", style = MaterialTheme.typography.titleLarge, color = Reel.TextSoft)
            }
            if (hint != null && !loading) {
                Text(hint, color = Reel.Moonglow, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable(role = Role.Button, onClick = onHint))
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = Reel.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Facts(movie: MovieDetails, modifier: Modifier) {
    val facts = listOfNotNull(
        movie.directors.takeIf { it.isNotEmpty() }?.let { (if (it.size > 1) "Directors" else "Director") to it.joinToString(", ") },
        movie.releaseDate?.takeIf { it.isNotBlank() }?.let { "Released" to it },
        movie.spokenLanguages.takeIf { it.isNotEmpty() }?.let { "Languages" to it.joinToString(", ") { l -> l.englishName.ifBlank { languageDisplayName(l.code) ?: l.code } } },
    )
    if (facts.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        facts.forEach { (k, v) ->
            Column {
                Text(k, style = MaterialTheme.typography.bodySmall, color = Reel.TextFaint)
                Text(v, style = MaterialTheme.typography.bodyMedium, color = Reel.Text)
            }
        }
    }
}

// ---------- Plugin sources ----------

@Composable
private fun SourcesSection(
    state: DetailsState,
    onPlay: (String, Source) -> Unit,
    onLink: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Watch")
        when {
            state.sourcesLoading -> repeat(2) { Shimmer(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(18.dp))) }
            state.sources.all { it.sources.isEmpty() } -> {
                val errors = state.sources.mapNotNull { s -> s.error?.let { "${s.plugin.name}: $it" } }
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (errors.isEmpty()) "No sources found for this movie." else errors.joinToString("\n"), color = Reel.TextSoft, style = MaterialTheme.typography.bodyMedium)
                    if (errors.isNotEmpty()) Text("Try again", color = Reel.Moonglow, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable(role = Role.Button, onClick = onRetry))
                }
            }
            else -> state.sources.forEach { group ->
                if (state.sources.size > 1) Text(group.plugin.name, style = MaterialTheme.typography.labelMedium, color = Reel.TextFaint)
                group.error?.let { Text(it, color = Reel.ToneTeen, style = MaterialTheme.typography.bodySmall) }
                group.sources.forEach { source -> SourceRow(group, source, state.opening == source, onPlay, onLink) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceRow(group: PluginSources, source: Source, opening: Boolean, onPlay: (String, Source) -> Unit, onLink: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(source.title, style = MaterialTheme.typography.titleLarge, color = Reel.Text)
                source.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Reel.TextFaint) }
            }
            if (source.playable) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Reel.PrimaryButton).clickable(role = Role.Button, onClickLabel = "Play ${source.title}") { onPlay(group.plugin.id, source) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (opening) CircularProgressIndicator(Modifier.size(22.dp), color = Reel.Night, strokeWidth = 2.5.dp)
                    else Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${source.title}", tint = Reel.Night)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            source.sizeBytes?.let { Stat(Icons.Rounded.Storage, formatBytes(it)) }
            source.seeds?.let { Stat(Icons.Rounded.ArrowUpward, "$it") }
            source.peers?.let { Stat(Icons.Rounded.ArrowDownward, "$it") }
            source.warning?.let { Text(it, color = Reel.ToneTeen, style = MaterialTheme.typography.bodySmall) }
        }
        if (source.externalLinks.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                source.externalLinks.forEach { link ->
                    GlassButton(link.label, onClick = { onLink(link.uri) }, height = 38.dp, icon = { Icon(Icons.Rounded.OpenInNew, null, tint = Reel.TextSoft, modifier = Modifier.size(16.dp)) })
                }
            }
        }
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = Reel.TextFaint, modifier = Modifier.size(15.dp))
        Text(text, color = Reel.TextSoft, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LoadingDetails() {
    Column(Modifier.padding(16.dp).offset(y = (-80).dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            Shimmer(Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)))
            Shimmer(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)))
        }
        repeat(4) { Shimmer(Modifier.fillMaxWidth(if (it == 3) 0.6f else 1f).height(16.dp).clip(RoundedCornerShape(6.dp))) }
    }
}

fun openUri(context: Context, uri: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        val msg = if (uri.startsWith("magnet:")) "Install a torrent app to open magnet links." else "No app can open this link."
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
}
