package io.github.niteshprasad.reelune.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.niteshprasad.reelune.data.Movie
import io.github.niteshprasad.reelune.ui.theme.Reel
import io.github.niteshprasad.reelune.ui.theme.glass
import java.util.Locale

// ---------- Buttons ----------

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(Reel.PrimaryButton)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        icon?.invoke()
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color(0xFF15112E))
    }
}

@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp, icon: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .height(height)
            .glass(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        icon?.invoke()
        Text(text, style = MaterialTheme.typography.labelLarge, color = Reel.Text)
    }
}

@Composable
fun ImdbButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(Reel.Imdb)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("View on IMDb", style = MaterialTheme.typography.labelLarge, color = Color(0xFF111111))
    }
}

// ---------- Badges ----------

enum class CertTone(val color: Color) { ALL(Reel.ToneAll), GUIDED(Reel.ToneGuided), TEEN(Reel.ToneTeen), ADULT(Reel.ToneAdult), UNRATED(Reel.ToneUnrated) }

data class CertInfo(val tone: CertTone, val meaning: String)

/** Meaning of common certifications: US (MPA/TV), India (CBFC), UK (BBFC) and others TMDB returns. */
fun certInfo(label: String, country: String): CertInfo {
    val l = label.uppercase(Locale.ROOT).replace(" ", "")
    return when {
        country == "IN" && l == "U" -> CertInfo(CertTone.ALL, "Unrestricted public exhibition")
        country == "IN" && (l.startsWith("UA") || l.startsWith("U/A")) -> CertInfo(
            if (l.contains("16")) CertTone.TEEN else CertTone.GUIDED,
            "Parental guidance for children under ${Regex("\\d+").find(l)?.value ?: "12"}",
        )
        country == "IN" && l == "A" -> CertInfo(CertTone.ADULT, "Restricted to adults")
        country == "IN" && l == "S" -> CertInfo(CertTone.ADULT, "Restricted to specialised audiences")
        l in setOf("G", "TV-G", "TV-Y", "U", "0", "ALL", "AL", "L") -> CertInfo(CertTone.ALL, "Suitable for all ages")
        l in setOf("PG", "TV-PG", "TV-Y7", "6", "7", "M") -> CertInfo(CertTone.GUIDED, "Parental guidance suggested")
        l in setOf("PG-13", "TV-14", "12", "12A", "13", "14", "15", "16") -> CertInfo(CertTone.TEEN, "May be unsuitable for younger viewers")
        l in setOf("R", "TV-MA", "NC-17", "X", "18", "R18", "A", "18+") -> CertInfo(CertTone.ADULT, "Restricted to adults or mature viewers")
        l == "NR" || l == "UNRATED" || l == "NOTRATED" -> CertInfo(CertTone.UNRATED, "Not rated")
        else -> CertInfo(CertTone.UNRATED, "Rated $label")
    }
}

@Composable
fun CertBadge(label: String, country: String, modifier: Modifier = Modifier) {
    val info = certInfo(label, country)
    Box(
        modifier
            .semantics { contentDescription = "Rated $label in $country: ${info.meaning}" }
            .clip(RoundedCornerShape(6.dp))
            .background(info.tone.color.copy(alpha = 0.13f))
            .border(1.5.dp, info.tone.color.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = info.tone.color)
    }
}

@Composable
fun RatingPill(rating: Double, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Reel.Night.copy(alpha = 0.62f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = Reel.Imdb, modifier = Modifier.size(12.dp))
        Text(String.format(Locale.US, "%.1f", rating), style = MaterialTheme.typography.labelMedium, color = Reel.Text)
    }
}

// ---------- Poster card ----------

@Composable
fun PosterCard(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier, languageName: (String) -> String? = { null }) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = "Open ${movie.title}", onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Reel.Twilight, Color(0xFF17122F)))),
        ) {
            AsyncImage(
                model = movie.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (movie.voteAverage > 0 && movie.voteCount > 0) {
                RatingPill(movie.voteAverage, Modifier.align(Alignment.TopStart).padding(7.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            movie.title,
            style = MaterialTheme.typography.titleSmall,
            color = Reel.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        val lang = movie.originalLanguage?.takeIf { it != "en" }?.let(languageName)
        Text(
            listOfNotNull(movie.year?.toString(), lang).joinToString("   "),
            style = MaterialTheme.typography.bodySmall,
            color = Reel.TextFaint,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

// ---------- Loading & states ----------

@Composable
fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "x")
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(Color(0x12DCE3F2), Color(0x24DCE3F2), Color(0x12DCE3F2)),
                start = androidx.compose.ui.geometry.Offset(x * 600f, 0f),
                end = androidx.compose.ui.geometry.Offset(x * 600f + 300f, 300f),
            ),
        ),
    )
}

@Composable
fun MessagePanel(title: String, body: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Column(
        modifier
            .glass(RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Reel.Text)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Reel.TextSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Reel.Text, fontWeight = FontWeight.Medium, modifier = modifier)
}

fun formatRuntime(min: Int?): String? = min?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${"%02d".format(it % 60)}m" else "${it}m" }

fun formatBytes(n: Long): String {
    if (n <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = n.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i >= 2) String.format(Locale.US, "%.1f %s", v, units[i]) else String.format(Locale.US, "%.0f %s", v, units[i])
}

fun languageDisplayName(code: String?): String? = code?.let {
    val name = Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH)
    if (name.isNullOrBlank() || name.equals(it, true)) it.uppercase(Locale.ROOT) else name
}
