package io.github.niteshprasad.reelune.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.niteshprasad.reelune.BuildConfig
import io.github.niteshprasad.reelune.ReeluneApp
import io.github.niteshprasad.reelune.plugin.PendingPlugin
import io.github.niteshprasad.reelune.plugin.PluginState
import io.github.niteshprasad.reelune.ui.components.GlassButton
import io.github.niteshprasad.reelune.ui.components.PrimaryButton
import io.github.niteshprasad.reelune.ui.details.openUri
import io.github.niteshprasad.reelune.ui.theme.Reel
import io.github.niteshprasad.reelune.ui.theme.glass
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = ReeluneApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val plugins by app.plugins.plugins.collectAsStateWithLifecycle()

    var tmdb by remember { mutableStateOf("") }
    var omdb by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var tmdbError by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(settings != null) {
        settings?.let { tmdb = it.tmdbKey; omdb = it.omdbKey; region = it.region }
    }

    var pending by remember { mutableStateOf<PendingPlugin?>(null) }
    var pluginError by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<PluginState?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                pending = app.plugins.inspect(uri)
            } catch (e: Exception) {
                pluginError = e.message ?: "That file couldn't be read."
            }
        }
    }

    fun save() {
        if (saving) return
        saving = true
        tmdbError = null
        scope.launch {
            try {
                if (tmdb.trim() != settings?.tmdbKey) app.tmdb.verifyKey(tmdb)
                val r = region.trim().uppercase(Locale.ROOT)
                app.settings.update(tmdbKey = tmdb, omdbKey = omdb, region = if (r.length == 2) r else "US")
                saved = "Settings saved"
            } catch (e: Exception) {
                tmdbError = e.message ?: "That key didn't work."
            } finally {
                saving = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp).glass(CircleShape)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Reel.Text)
            }
            Spacer(Modifier.size(14.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Reel.Text)
        }

        Panel("Movie data") {
            Text("TMDB provides the catalogue, posters, trailers and age ratings.", color = Reel.TextSoft, style = MaterialTheme.typography.bodyMedium)
            KeyField(tmdb, { tmdb = it; tmdbError = null; saved = null }, "TMDB API key or token", tmdbError)
            LinkText("Get a free TMDB key") { openUri(context, "https://www.themoviedb.org/settings/api") }
        }

        Panel("Ratings") {
            Text("An OMDb key adds IMDb ratings, Rotten Tomatoes and Metacritic scores.", color = Reel.TextSoft, style = MaterialTheme.typography.bodyMedium)
            KeyField(omdb, { omdb = it; saved = null }, "OMDb API key (optional)", null)
            LinkText("Get a free OMDb key") { openUri(context, "https://www.omdbapi.com/apikey.aspx") }
            OutlinedTextField(
                value = region,
                onValueChange = { region = it.take(2); saved = null },
                label = { Text("Country for age ratings") },
                supportingText = { Text("Two letters, e.g. IN for U/UA/A or US for PG/R") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PrimaryButton(if (saving) "Saving…" else "Save settings", onClick = ::save, enabled = tmdb.isNotBlank() && !saving)
            saved?.let { Text(it, color = Reel.ToneAll, style = MaterialTheme.typography.bodyMedium) }
        }

        Panel("Plugins") {
            Text(
                "Plugins add more ways to watch to a movie's page. Only add plugin files you trust: a plugin runs inside Reelune with the same access as the app.",
                color = Reel.TextSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
            plugins.forEach { p -> PluginRow(p, onRemove = { removing = p }) }
            if (plugins.isEmpty()) Text("No plugins added.", color = Reel.TextFaint, style = MaterialTheme.typography.bodySmall)
            GlassButton(
                "Add plugin from file",
                onClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")) },
                icon = { Icon(Icons.Rounded.Add, null, tint = Reel.Text, modifier = Modifier.size(18.dp)) },
            )
        }

        Panel("About") {
            Text("Reelune ${BuildConfig.VERSION_NAME}", color = Reel.Text, style = MaterialTheme.typography.titleSmall)
            Text(
                "Movie data and images from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB. Ratings from OMDb.",
                color = Reel.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = Color(0xFF1E1840),
            title = { Text(if (p.replaces != null) "Update “${p.info.name}”?" else "Add “${p.info.name}”?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version ${p.info.version}" + (p.replaces?.let { " (you have ${it.version})" } ?: ""))
                    Text("Package: ${p.info.id}", style = MaterialTheme.typography.bodySmall)
                    if (p.replaces == null) {
                        Text("Signed by certificate (SHA-256):", style = MaterialTheme.typography.bodySmall)
                        SelectionContainer {
                            Text(p.info.certificate, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Reel.Moonglow)
                        }
                        Text(
                            "Check this matches the fingerprint the plugin's author published. Future updates must be signed with the same certificate. The plugin will run with Reelune's access.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text("Signed by the same certificate as the version you have.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = p
                    pending = null
                    scope.launch {
                        try {
                            app.plugins.confirm(target)
                            saved = null
                        } catch (e: Exception) {
                            pluginError = e.message ?: "The plugin couldn't be added."
                        }
                    }
                }) { Text(if (p.replaces != null) "Update" else "Add plugin", color = Reel.Moonglow) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel", color = Reel.TextSoft) } },
        )
    }

    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = Color(0xFF1E1840),
            title = { Text("Remove “${p.plugin.name}”?") },
            text = { Text("Its files and saved data are deleted from this phone. You can add it again later.") },
            confirmButton = {
                TextButton(onClick = { removing = null; scope.launch { app.plugins.remove(p.plugin.id) } }) { Text("Remove", color = Reel.ToneAdult) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel", color = Reel.TextSoft) } },
        )
    }

    pluginError?.let { msg ->
        AlertDialog(
            onDismissRequest = { pluginError = null },
            containerColor = Color(0xFF1E1840),
            title = { Text("Plugin not added") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { pluginError = null }) { Text("OK", color = Reel.Moonglow) } },
        )
    }
}

@Composable
private fun PluginRow(p: PluginState, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.plugin.name, color = Reel.Text, style = MaterialTheme.typography.titleSmall)
                Text("Version ${p.plugin.version}", color = Reel.TextFaint, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRemove) { Text("Remove", color = Reel.ToneAdult) }
        }
        Text(
            if (p.loaded) "Active" else "Not running: ${p.error ?: "unknown problem"}",
            color = if (p.loaded) Reel.ToneAll else Reel.ToneTeen,
            style = MaterialTheme.typography.bodySmall,
        )
        SelectionContainer {
            Text(p.plugin.certificate, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Reel.TextFaint)
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Reel.Text)
        content()
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Text(text, color = Reel.Moonglow, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Reel.Moonglow,
    unfocusedBorderColor = Reel.GlassLine,
    focusedLabelColor = Reel.Moonglow,
    unfocusedLabelColor = Reel.TextFaint,
    cursorColor = Reel.Moonglow,
    focusedContainerColor = Reel.Night.copy(alpha = 0.4f),
    unfocusedContainerColor = Reel.Night.copy(alpha = 0.4f),
)

/** A text field for an API key, hidden by default. */
@Composable
fun KeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = if (visible) "Hide key" else "Show key", tint = Reel.TextFaint)
            }
        },
        keyboardOptions = keyboardOptions.copy(autoCorrectEnabled = false),
        keyboardActions = keyboardActions,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(0.dp))
}
