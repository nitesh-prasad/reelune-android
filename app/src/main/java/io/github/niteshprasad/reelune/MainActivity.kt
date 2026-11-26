package io.github.niteshprasad.reelune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.niteshprasad.reelune.ui.Sky
import io.github.niteshprasad.reelune.ui.details.DetailsScreen
import io.github.niteshprasad.reelune.ui.home.HomeScreen
import io.github.niteshprasad.reelune.ui.onboarding.OnboardingScreen
import io.github.niteshprasad.reelune.ui.settings.SettingsScreen
import io.github.niteshprasad.reelune.ui.theme.LocalHaze
import io.github.niteshprasad.reelune.ui.theme.ReeluneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { ReeluneTheme { ReeluneRoot() } }
    }
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val MOVIE = "movie/{key}"
    fun movie(key: String) = "movie/" + android.net.Uri.encode(key)
}

@Composable
private fun ReeluneRoot() {
    val app = ReeluneApp.instance
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val skyHaze = rememberHazeState()

    Box(Modifier.fillMaxSize()) {
        Sky(Modifier.fillMaxSize().hazeSource(skyHaze))
        val s = settings ?: return@Box // Settings load in a few milliseconds; the sky shows meanwhile.
        CompositionLocalProvider(LocalHaze provides skyHaze) {
            val nav = rememberNavController()
            // Decided once: changing settings later must not rebuild the navigation graph.
            val start = remember { if (s.isSetUp) Routes.HOME else Routes.ONBOARDING }
            NavHost(
                navController = nav,
                startDestination = start,
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onDone = {
                        nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    })
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenMovie = { nav.navigate(Routes.movie(it)) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.MOVIE, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                    DetailsScreen(
                        movieKey = entry.arguments?.getString("key").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpenMovie = { nav.navigate(Routes.movie(it)) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
