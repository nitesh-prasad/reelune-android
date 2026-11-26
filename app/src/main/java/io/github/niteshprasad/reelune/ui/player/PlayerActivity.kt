package io.github.niteshprasad.reelune.ui.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import io.github.niteshprasad.reelune.ReeluneApp
import io.github.niteshprasad.reelune.plugin.PluginManager
import io.github.niteshprasad.reelune.plugin.StreamStatus
import io.github.niteshprasad.reelune.ui.components.formatBytes
import io.github.niteshprasad.reelune.ui.theme.Reel
import io.github.niteshprasad.reelune.ui.theme.ReeluneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

class PlayerActivity : ComponentActivity() {
    private var stream: PluginManager.ActiveStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stream = ReeluneApp.instance.plugins.activeStream
        if (stream == null) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val s = stream!!
        setContent { ReeluneTheme { PlayerScreen(s, onClose = ::finish) } }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val s = stream ?: return
            val plugins = ReeluneApp.instance.plugins
            if (plugins.activeStream === s) plugins.activeStream = null
            // Closing may delete files and stop the engine: keep it off the main thread.
            thread(name = "close-stream") { runCatching { s.session.close() } }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(stream: PluginManager.ActiveStream, onClose: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<StreamStatus?>(null) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val player = remember {
        // The plugin's local server waits for pieces to arrive, so allow long reads and keep retrying.
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(90_000)
            .setAllowCrossProtocolRedirects(false)
        val sources = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(http)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(12))
        val subtitles = runCatching { stream.session.subtitles() }.getOrDefault(emptyList()).map { s ->
            MediaItem.SubtitleConfiguration.Builder(s.url.toUri())
                .setMimeType(s.mimeType)
                .setLabel(s.label)
                .setLanguage(s.language)
                .build()
        }
        val item = MediaItem.Builder()
            .setUri(stream.session.url)
            .apply { stream.session.mimeType?.let { setMimeType(it) } }
            .setSubtitleConfigurations(subtitles)
            .build()
        ExoPlayer.Builder(context).setMediaSourceFactory(sources).build().apply {
            setMediaItem(item)
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            }

            override fun onPlayerError(e: PlaybackException) {
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "This phone can't decode this video's format. Try another quality."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "The stream stopped sending data. Not enough people may be sharing it right now."
                    else -> e.localizedMessage ?: "Playback stopped."
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(stream) {
        while (isActive) {
            status = withContext(Dispatchers.IO) { runCatching { stream.session.status() }.getOrNull() }
            delay(1000)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setShowSubtitleButton(true)
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        val s = status
        val waiting = error == null && (s?.state == StreamStatus.State.CONNECTING || (buffering && !playing))
        AnimatedVisibility(waiting, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Column(
                Modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(24.dp)).background(Reel.GlassStrong).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(color = Reel.Moonglow, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                Text(
                    when {
                        s == null || s.peers == 0 -> "Finding people sharing this film"
                        s.state == StreamStatus.State.CONNECTING -> "Connected to ${s.peers} peers"
                        else -> "Buffering"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Reel.Text,
                )
                Text(
                    s?.message ?: "This usually takes a few seconds. Rare films can take longer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Reel.TextSoft,
                    textAlign = TextAlign.Center,
                )
                StatsRow(s)
            }
        }

        error?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).widthIn(max = 420.dp).clip(RoundedCornerShape(24.dp)).background(Reel.GlassStrong).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Playback stopped", style = MaterialTheme.typography.titleMedium, color = Reel.Text)
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = Reel.TextSoft, textAlign = TextAlign.Center)
            }
        }

        if (s?.state == StreamStatus.State.ERROR && error == null) {
            Text(
                s.message ?: "The stream reported an error.",
                color = Reel.ToneTeen,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            )
        }

        Row(Modifier.align(Alignment.TopStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp).clip(CircleShape).background(Reel.Night.copy(alpha = 0.5f))) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close player", tint = Reel.Text)
            }
            Text(stream.title, color = Reel.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun StatsRow(s: StreamStatus?) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${s?.peers ?: 0} peers", color = Reel.TextSoft, style = MaterialTheme.typography.bodySmall)
        Text("${formatBytes(s?.downloadBytesPerSecond ?: 0)}/s", color = Reel.TextSoft, style = MaterialTheme.typography.bodySmall)
        s?.progress?.let {
            LinearProgressIndicator(progress = { it }, color = Reel.Moonglow, trackColor = Reel.GlassLine, modifier = Modifier.widthIn(min = 80.dp, max = 120.dp))
        }
    }
}
