package io.github.niteshprasad.reelune.plugin

import java.io.File

/**
 * Version of this contract. A plugin declares the version it was built against in its manifest
 * (`<meta-data android:name="reelune.plugin.api" android:value="1" />`); Reelune refuses plugins
 * built for a different version.
 */
const val PLUGIN_API_VERSION: Int = 1

/**
 * A Reelune plugin adds viewing sources to a movie's page.
 *
 * Plugins are APK files the user adds in Settings. Reelune loads the class named in the plugin's
 * manifest (`<meta-data android:name="reelune.plugin.class" android:value="com.example.MyPlugin" />`),
 * which must implement this interface and have a public no-argument constructor.
 *
 * Every method except [shutdown] may block; Reelune always calls them off the main thread.
 * Methods may throw; Reelune shows the exception message to the user.
 */
interface ReelunePlugin {
    /** Called once after loading, before any other call. */
    fun init(host: PluginHost)

    /** Sources for one movie, best first. Return an empty list when there are none. */
    fun findSources(movie: MovieRef): List<Source>

    /** Start playing [source]. Only called for sources with [Source.playable] = true. */
    fun openStream(source: Source): StreamSession

    /** Release everything (network, files, threads). Called when the plugin is removed or the app closes. */
    fun shutdown()
}

/** What Reelune gives a plugin. */
interface PluginHost {
    /** A private folder for this plugin's temporary data. Reelune may clear it when the plugin is removed. */
    val cacheDir: File

    /** A private folder for data the plugin wants to keep. */
    val dataDir: File

    /** Reelune's version name, e.g. "1.0.0". */
    val appVersion: String

    /** Write a line to Reelune's log (visible with `adb logcat -s Reelune`). */
    fun log(message: String)
}

/** The movie the user is looking at. */
data class MovieRef(
    /** IMDb id such as "tt0063350", when known. */
    val imdbId: String?,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
)

/** One way to watch a movie. */
data class Source(
    /** Stable id, unique within this plugin for this movie. Passed back to [ReelunePlugin.openStream]. */
    val id: String,
    /** Main label, e.g. "1080p". */
    val title: String,
    /** Secondary label, e.g. "Blu-ray · x264 · 5.1". */
    val subtitle: String?,
    val sizeBytes: Long?,
    /** Health indicators when the plugin knows them. */
    val seeds: Int?,
    val peers: Int?,
    /** True when Reelune can play it with [ReelunePlugin.openStream]. */
    val playable: Boolean,
    /** Optional warning shown on the row, e.g. "May not play on older phones". */
    val warning: String?,
    /** Links Reelune can hand to other apps (shown as buttons). Use https:// or magnet: URIs. */
    val externalLinks: List<ExternalLink>,
)

data class ExternalLink(val label: String, val uri: String)

/** A running stream. Reelune plays [url] and polls [status] about once a second. */
interface StreamSession {
    /** An http://127.0.0.1 URL that supports HTTP Range requests. */
    val url: String

    /** MIME type such as "video/mp4" or "video/x-matroska", or null to let the player detect it. */
    val mimeType: String?

    fun status(): StreamStatus

    /** Subtitle files that come with the stream. */
    fun subtitles(): List<SubtitleTrack>

    /** Stop and delete temporary data. Called when the player closes. */
    fun close()
}

data class StreamStatus(
    val state: State,
    /** Human-readable detail, e.g. an error message or "Connected to 12 peers". */
    val message: String?,
    val peers: Int,
    val downloadBytesPerSecond: Long,
    /** 0.0 to 1.0, or null when unknown. */
    val progress: Float?,
) {
    enum class State { CONNECTING, BUFFERING, READY, ERROR }
}

data class SubtitleTrack(
    /** http://127.0.0.1 URL of the subtitle file. */
    val url: String,
    /** e.g. "application/x-subrip" or "text/vtt". */
    val mimeType: String,
    val label: String,
    /** BCP-47 language code when known. */
    val language: String?,
)
