package io.github.niteshprasad.reelune.ui.onboarding

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.niteshprasad.reelune.R
import io.github.niteshprasad.reelune.ReeluneApp
import io.github.niteshprasad.reelune.ui.components.PrimaryButton
import io.github.niteshprasad.reelune.ui.settings.KeyField
import io.github.niteshprasad.reelune.ui.theme.Reel
import io.github.niteshprasad.reelune.ui.theme.glass
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = ReeluneApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    fun submit() {
        if (key.isBlank() || checking) return
        checking = true
        error = null
        scope.launch {
            try {
                app.tmdb.verifyKey(key)
                app.settings.update(tmdbKey = key)
                onDone()
            } catch (e: io.github.niteshprasad.reelune.data.UserFacingException) {
                error = if (e.code == 401) "TMDB didn't accept that key. Check you copied all of it." else e.message
            } catch (e: Exception) {
                error = e.message ?: "That key didn't work."
            } finally {
                checking = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Image(
            painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)),
        )
        Text("Reelune", style = MaterialTheme.typography.displayMedium, color = Reel.Text)
        Text(
            "Find something to watch tonight: ratings from IMDb and Rotten Tomatoes, trailers, cast and age ratings for every film.",
            style = MaterialTheme.typography.bodyLarge,
            color = Reel.TextSoft,
        )
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connect to TMDB", style = MaterialTheme.typography.titleLarge, color = Reel.Text)
            Text(
                "Reelune gets its movie information from The Movie Database. Create a free account, open Settings › API, and paste your API key or API Read Access Token here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Reel.TextSoft,
            )
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.themoviedb.org/settings/api".toUri()))
            }) { Text("Get a free TMDB key", color = Reel.Moonglow) }
            KeyField(
                value = key,
                onValueChange = { key = it; error = null },
                label = "TMDB API key or token",
                error = error,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            PrimaryButton(
                text = if (checking) "Checking…" else "Continue",
                onClick = ::submit,
                enabled = key.isNotBlank() && !checking,
                modifier = Modifier.fillMaxWidth(),
                icon = if (checking) ({ CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Reel.Night) }) else null,
            )
        }
        Text(
            "This product uses the TMDB API but is not endorsed or certified by TMDB.",
            style = MaterialTheme.typography.bodySmall,
            color = Reel.TextFaint,
        )
    }
}
