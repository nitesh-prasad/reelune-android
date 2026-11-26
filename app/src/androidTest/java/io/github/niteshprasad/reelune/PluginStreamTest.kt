package io.github.niteshprasad.reelune

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.niteshprasad.reelune.plugin.MovieRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * End-to-end check of the plugin system with a real plugin APK.
 *
 * Copy a plugin build into the app's cache first, e.g.:
 *   adb exec-in run-as io.github.niteshprasad.reelune sh -c 'cat > cache/test-plugin.apk' < plugin-debug.apk
 * The test is skipped when no plugin file is present.
 *
 * Uses Night of the Living Dead (1968), which is in the public domain.
 */
@RunWith(AndroidJUnit4::class)
class PluginStreamTest {
    private val tag = "ReeluneTest"

    @Test
    fun addsPluginFindsSourcesAndStreams(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as ReeluneApp
        val file = File(context.cacheDir, "test-plugin.apk")
        assumeTrue("Copy a plugin APK to ${file.absolutePath} to run this test", file.exists())

        // 1. Inspect and add (verifies signature metadata, copies, extracts native libs, loads classes).
        val pending = app.plugins.inspect(Uri.fromFile(file))
        Log.i(tag, "Plugin ${pending.info.id} ${pending.info.version} cert ${pending.info.certificate}")
        app.plugins.confirm(pending)
        val state = app.plugins.plugins.value.single { it.plugin.id == pending.info.id }
        assertTrue("Plugin failed to load: ${state.error}", state.loaded)

        try {
            // 2. Sources by IMDb id.
            val groups = app.plugins.findSources(MovieRef("tt0063350", "Night of the Living Dead", null, 1968))
            val group = groups.single()
            assertEquals(null, group.error)
            Log.i(tag, "Sources: " + group.sources.joinToString { "${it.title} ${it.sizeBytes} seeds=${it.seeds}" })
            assertTrue(group.sources.isNotEmpty())
            assertTrue(group.sources.all { s -> s.externalLinks.any { it.uri.startsWith("magnet:?xt=urn:btih:") } })

            // 3. Stream: metadata, then the first megabyte and a range from the middle (a seek).
            val source = group.sources.firstOrNull { it.title == "720p" } ?: group.sources.first()
            val t0 = System.currentTimeMillis()
            val session = app.plugins.openStream(pending.info.id, source)
            Log.i(tag, "Stream ready in ${System.currentTimeMillis() - t0} ms at ${session.url} (${session.mimeType})")
            try {
                val head = fetchRange(session.url, 0, 1_048_575)
                assertEquals(1_048_576, head.size)
                // MP4 files start with an 'ftyp' box at byte 4.
                if (session.mimeType == "video/mp4") assertEquals("ftyp", String(head, 4, 4, Charsets.US_ASCII))
                Log.i(tag, "First MB after ${System.currentTimeMillis() - t0} ms; status ${session.status()}")

                val total = contentLength(session.url)
                val mid = total / 2
                val t1 = System.currentTimeMillis()
                val middle = fetchRange(session.url, mid, mid + 262_143)
                assertEquals(262_144, middle.size)
                Log.i(tag, "Seek to middle took ${System.currentTimeMillis() - t1} ms; status ${session.status()}")
                Log.i(tag, "Subtitles: ${session.subtitles()}")
            } finally {
                session.close()
            }
        } finally {
            app.plugins.remove(pending.info.id)
            assertTrue(app.plugins.plugins.value.none { it.plugin.id == pending.info.id })
        }
    }

    private fun fetchRange(url: String, start: Long, end: Long): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.readTimeout = 180_000
        conn.setRequestProperty("Range", "bytes=$start-$end")
        try {
            assertEquals(206, conn.responseCode)
            assertEquals("bytes $start-$end/", conn.getHeaderField("Content-Range").substringBefore('/') + "/")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun contentLength(url: String): Long {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        try {
            assertEquals(200, conn.responseCode)
            return conn.getHeaderField("Content-Length").toLong()
        } finally {
            conn.disconnect()
        }
    }
}
