package io.github.niteshprasad.reelune.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.niteshprasad.reelune.ui.theme.Reel

/** The image behind the whole app: the film you're looking at, blurred into the night sky. */
object Backdrop {
    var url by mutableStateOf<String?>(null)
}

/** Sets the sky's image while this composable is shown. */
@Composable
fun UseBackdrop(url: String?) {
    LaunchedEffect(url) { if (url != null) Backdrop.url = url }
}

@Composable
fun Sky(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sky")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(28_000), RepeatMode.Reverse), label = "drift")

    Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF2A1F57), Reel.Night, Reel.Night)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                Brush.radialGradient(
                    listOf(Reel.Halo.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(w * (0.05f + 0.25f * drift), h * (0.08f + 0.1f * drift)),
                    radius = w * 0.9f,
                ),
                radius = w * 0.9f,
                center = Offset(w * (0.05f + 0.25f * drift), h * (0.08f + 0.1f * drift)),
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Reel.Moonglow.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(w * (0.95f - 0.2f * drift), h * (0.55f + 0.08f * drift)),
                    radius = w * 0.75f,
                ),
                radius = w * 0.75f,
                center = Offset(w * (0.95f - 0.2f * drift), h * (0.55f + 0.08f * drift)),
            )
        }
        Crossfade(targetState = Backdrop.url, animationSpec = tween(1200), label = "backdrop") { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(40.dp).alpha(0.5f),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Reel.Night.copy(alpha = 0.15f),
                    0.55f to Reel.Night.copy(alpha = 0.72f),
                    1f to Reel.Night,
                ),
            ),
        )
    }
}
