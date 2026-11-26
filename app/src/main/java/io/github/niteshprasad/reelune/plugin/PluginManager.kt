package io.github.niteshprasad.reelune.plugin

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import dalvik.system.DexClassLoader
import io.github.niteshprasad.reelune.BuildConfig
import io.github.niteshprasad.reelune.data.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** A plugin the user has added. */
@Serializable
data class InstalledPlugin(
    /** The plugin APK's package name, e.g. "com.example.reelune.stream". */
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val apiVersion: Int,
    val className: String,
    /** SHA-256 of the APK signing certificate, as colon-separated hex. */
    val certificate: String,
)

/** A plugin file read from disk, waiting for the user to approve it. */
data class PendingPlugin(
    val info: InstalledPlugin,
    /** The same plugin is already added; this file updates it. */
    val replaces: InstalledPlugin?,
    internal val file: File,
)

data class PluginState(
    val plugin: InstalledPlugin,
    val loaded: Boolean,
    val error: String?,
)

/** Sources from one plugin for one movie. */
data class PluginSources(
    val plugin: InstalledPlugin,
    val sources: List<Source>,
    val error: String?,
)

class PluginException(message: String) : Exception(message)

/**
 * Adds, verifies, loads and removes plugins.
 *
 * A plugin is a `.reelune-plugin` file: a signed package (APK structure, so Android's signature
 * verification applies) that is marked test-only, so the system installer refuses it. Reelune keeps a
 * read-only copy in its private storage, extracts the plugin's native libraries for this device's CPU, and
 * loads its classes with a [DexClassLoader] whose parent is Reelune's own class loader, so the
 * plugin shares Reelune's copy of the plugin API and Kotlin runtime.
 *
 * Trust: the first time a plugin id is added, the user approves its signing certificate. Later
 * updates must be signed with the same certificate.
 */
class PluginManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val root = File(context.filesDir, "plugins")
    private val registryFile = File(root, "registry.json")

    private val loaded = mutableMapOf<String, ReelunePlugin>()
    private val loadErrors = mutableMapOf<String, String>()
    private val _plugins = MutableStateFlow<List<PluginState>>(emptyList())
    val plugins: StateFlow<List<PluginState>> = _plugins.asStateFlow()

    /** The stream the player is showing, handed from the movie screen to the player screen. */
    @Volatile
    var activeStream: ActiveStream? = null

    data class ActiveStream(val title: String, val session: StreamSession)

    init {
        scope.launch { mutex.withLock { loadAllLocked() } }
    }

    // ---------- Adding ----------

    /** Reads a plugin file chosen by the user. Call [confirm] to add it. */
    suspend fun inspect(uri: Uri): PendingPlugin = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "plugin-staging").apply { mkdirs() }
        staging.listFiles()?.forEach { it.delete() }
        val file = File(staging, "candidate-${System.currentTimeMillis()}.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        } ?: throw PluginException("Couldn't open that file.")

        val info = readPluginInfo(file)
        val existing = registry().firstOrNull { it.id == info.id }
        if (existing != null && existing.certificate != info.certificate) {
            file.delete()
            throw PluginException(
                "This file claims to be “${existing.name}” but is signed by someone else. " +
                    "It wasn't added. Remove the existing plugin first if you trust the new signer.",
            )
        }
        PendingPlugin(info, existing, file)
    }

    /** Adds (or updates) a plugin the user approved in [inspect]. */
    suspend fun confirm(pending: PendingPlugin) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = pending.info.id
            loaded.remove(id)?.let { runCatching { it.shutdown() } }
            val dir = pluginDir(id)
            File(dir, "code").deleteRecursively()
            val codeDir = File(dir, "code").apply { mkdirs() }
            // A new file name per version: a class loader keeps the old file open until restart.
            val apk = File(codeDir, "plugin-${pending.info.versionCode}-${System.currentTimeMillis()}.apk")
            pending.file.copyTo(apk, overwrite = true)
            pending.file.delete()
            // Android 14+ refuses to load code from writable files.
            if (!apk.setReadOnly()) throw PluginException("Couldn't protect the plugin file.")
            extractNativeLibs(apk, File(codeDir, "lib"))

            val list = registry().filterNot { it.id == id } + pending.info
            saveRegistry(list)
            loadLocked(pending.info)
            publish(list)
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loaded.remove(id)?.let { runCatching { it.shutdown() } }
            pluginDir(id).deleteRecursively()
            File(context.cacheDir, "plugins/$id").deleteRecursively()
            val list = registry().filterNot { it.id == id }
            saveRegistry(list)
            publish(list)
        }
    }

    // ---------- Using ----------

    val hasPlugins: Boolean get() = _plugins.value.any { it.loaded }

    suspend fun findSources(movie: MovieRef): List<PluginSources> = coroutineScope {
        val active = mutex.withLock { registry().mapNotNull { info -> loaded[info.id]?.let { info to it } } }
        active.map { (info, plugin) ->
            async(Dispatchers.IO) {
                try {
                    PluginSources(info, plugin.findSources(movie), null)
                } catch (t: Throwable) {
                    Log.w(TAG, "${info.id} findSources failed", t)
                    PluginSources(info, emptyList(), t.message ?: t.javaClass.simpleName)
                }
            }
        }.awaitAll()
    }

    suspend fun openStream(pluginId: String, source: Source): StreamSession = withContext(Dispatchers.IO) {
        val plugin = mutex.withLock { loaded[pluginId] } ?: throw PluginException("That plugin isn't loaded.")
        try {
            plugin.openStream(source)
        } catch (t: Throwable) {
            Log.w(TAG, "$pluginId openStream failed", t)
            throw PluginException(t.message ?: "The plugin couldn't start this stream.")
        }
    }

    // ---------- Internals ----------

    private fun readPluginInfo(apk: File): InstalledPlugin {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_META_DATA or
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pkg: PackageInfo = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw PluginException("That file isn't a Reelune plugin. Plugins are .reelune-plugin files.")
        val app = pkg.applicationInfo ?: throw PluginException("That file isn't a Reelune plugin.")
        val meta = app.metaData
        val className = meta?.getString("reelune.plugin.class")
            ?: throw PluginException("That file isn't a Reelune plugin (no plugin class declared).")
        val api = meta.getInt("reelune.plugin.api", -1)
        if (api != PLUGIN_API_VERSION) {
            throw PluginException(
                if (api < 0) "That file isn't a Reelune plugin."
                else "This plugin was built for plugin API $api, but Reelune ${BuildConfig.VERSION_NAME} uses API $PLUGIN_API_VERSION. Get a matching version.",
            )
        }
        if (pkg.packageName == context.packageName) throw PluginException("That's Reelune itself, not a plugin.")

        @Suppress("DEPRECATION")
        val cert = if (Build.VERSION.SDK_INT >= 28) {
            val signing = pkg.signingInfo ?: throw PluginException("The plugin isn't signed.")
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            pkg.signatures
        }?.firstOrNull()?.toByteArray() ?: throw PluginException("The plugin isn't signed.")

        app.sourceDir = apk.absolutePath
        app.publicSourceDir = apk.absolutePath
        val label = meta.getString("reelune.plugin.name") ?: runCatching { pm.getApplicationLabel(app).toString() }.getOrNull()

        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else pkg.versionCode.toLong()
        return InstalledPlugin(
            id = pkg.packageName,
            name = label?.takeIf { it.isNotBlank() } ?: pkg.packageName,
            version = pkg.versionName ?: versionCode.toString(),
            versionCode = versionCode,
            apiVersion = api,
            className = className,
            certificate = sha256(cert),
        )
    }

    private fun extractNativeLibs(apk: File, libDir: File) {
        libDir.deleteRecursively()
        libDir.mkdirs()
        ZipFile(apk).use { zip ->
            val entries = zip.entries().toList().filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
            if (entries.isEmpty()) return
            val abi = Build.SUPPORTED_ABIS.firstOrNull { abi -> entries.any { it.name.startsWith("lib/$abi/") } }
                ?: throw PluginException("This plugin doesn't support this phone's processor (${Build.SUPPORTED_ABIS.joinToString()}).")
            entries.filter { it.name.startsWith("lib/$abi/") }.forEach { entry ->
                val out = File(libDir, entry.name.substringAfterLast('/'))
                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                out.setReadOnly()
            }
        }
    }

    private fun loadAllLocked() {
        val list = registry()
        list.forEach { info -> if (info.id !in loaded) runCatching { loadLocked(info) } }
        publish(list)
    }

    private fun loadLocked(info: InstalledPlugin) {
        try {
            val codeDir = File(pluginDir(info.id), "code")
            val apk = codeDir.listFiles { f -> f.name.endsWith(".apk") }?.maxByOrNull { it.lastModified() }
                ?: throw PluginException("The plugin file is missing. Add it again.")
            val loader = DexClassLoader(apk.absolutePath, null, File(codeDir, "lib").absolutePath, javaClass.classLoader)
            val instance = loader.loadClass(info.className).getDeclaredConstructor().newInstance() as? ReelunePlugin
                ?: throw PluginException("${info.className} doesn't implement the Reelune plugin interface.")
            instance.init(HostImpl(info.id))
            loaded[info.id] = instance
            loadErrors.remove(info.id)
            Log.i(TAG, "Loaded plugin ${info.id} ${info.version}")
        } catch (t: Throwable) {
            Log.e(TAG, "Couldn't load ${info.id}", t)
            loadErrors[info.id] = t.message ?: t.javaClass.simpleName
            throw if (t is PluginException) t else PluginException("Couldn't start “${info.name}”: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun publish(list: List<InstalledPlugin>) {
        _plugins.value = list.map { PluginState(it, it.id in loaded, loadErrors[it.id]) }
    }

    private fun pluginDir(id: String) = File(root, id.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    private fun registry(): List<InstalledPlugin> =
        runCatching { Http.json.decodeFromString<List<InstalledPlugin>>(registryFile.readText()) }.getOrDefault(emptyList())

    private fun saveRegistry(list: List<InstalledPlugin>) {
        root.mkdirs()
        val tmp = File(root, "registry.json.tmp")
        tmp.writeText(Http.json.encodeToString(list))
        if (!tmp.renameTo(registryFile)) {
            registryFile.delete()
            tmp.renameTo(registryFile)
        }
    }

    private inner class HostImpl(private val id: String) : PluginHost {
        override val cacheDir: File = File(context.cacheDir, "plugins/$id").apply { mkdirs() }
        override val dataDir: File = File(pluginDir(id), "data").apply { mkdirs() }
        override val appVersion: String = BuildConfig.VERSION_NAME
        override fun log(message: String) {
            Log.i(TAG, "[$id] $message")
        }
    }

    companion object {
        const val TAG = "Reelune"

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }
    }
}
