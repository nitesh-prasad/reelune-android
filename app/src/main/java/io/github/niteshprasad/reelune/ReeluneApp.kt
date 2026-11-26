package io.github.niteshprasad.reelune

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.github.niteshprasad.reelune.data.Catalog
import io.github.niteshprasad.reelune.data.CatalogSource
import io.github.niteshprasad.reelune.data.Http
import io.github.niteshprasad.reelune.data.YtsCatalog
import io.github.niteshprasad.reelune.data.Omdb
import io.github.niteshprasad.reelune.data.SettingsRepository
import io.github.niteshprasad.reelune.data.Tmdb
import io.github.niteshprasad.reelune.plugin.PluginManager

class ReeluneApp : Application(), SingletonImageLoader.Factory {

    lateinit var settings: SettingsRepository
        private set
    lateinit var tmdb: Tmdb
        private set
    lateinit var omdb: Omdb
        private set
    val yts = YtsCatalog()

    fun catalog(source: CatalogSource): Catalog = when (source) {
        CatalogSource.TMDB -> tmdb
        CatalogSource.YTS -> yts
    }

    /** The catalogue that produced a [io.github.niteshprasad.reelune.data.Movie.navKey]. */
    fun catalogForKey(key: String): Catalog = if (key.startsWith("yts")) yts else tmdb
    lateinit var plugins: PluginManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        Http.init(cacheDir)
        settings = SettingsRepository(this)
        tmdb = Tmdb(settings)
        omdb = Omdb(settings)
        plugins = PluginManager(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Http.client })) }
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: ReeluneApp
            private set
    }
}
