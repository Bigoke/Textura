package com.textura.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.textura.app.ui.AppViewModel
import com.textura.app.ui.BrowserScreen
import com.textura.app.ui.EditorScreen
import com.textura.app.ui.HandwritingScreen
import com.textura.app.ui.HomeScreen
import com.textura.app.ui.LibraryScreen
import com.textura.app.ui.SettingsScreen
import com.textura.app.ui.TexturaTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        setContent {
            TexturaTheme(vm.settings.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNav(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Text shared into Textura from another app becomes the current text. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let(vm::updateText)
        }
    }
}

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    NavTab("home", "Create", Icons.Rounded.AutoAwesome),
    NavTab("library", "Library", Icons.Rounded.PhotoLibrary),
    NavTab("settings", "Settings", Icons.Rounded.Settings),
)

@Composable
private fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in Tabs.map { it.route }

    fun openBrowser() {
        nav.navigate("browser") { launchSingleTop = true }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(240)),
                exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(160)),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    Tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label) },
                            alwaysShowLabel = false,
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val tabRoutes = Tabs.map { it.route }
        val ease = FastOutSlowInEasing
        NavHost(
            nav,
            startDestination = "home",
            modifier = Modifier.padding(padding),
            enterTransition = {
                if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes)
                    fadeIn(tween(240, delayMillis = 70)) + scaleIn(tween(300, easing = ease), initialScale = 0.97f)
                else slideInHorizontally(tween(340, easing = ease)) { it / 7 } + fadeIn(tween(300))
            },
            exitTransition = {
                if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes)
                    fadeOut(tween(110))
                else slideOutHorizontally(tween(340, easing = ease)) { -it / 9 } + fadeOut(tween(220))
            },
            popEnterTransition = { slideInHorizontally(tween(340, easing = ease)) { -it / 9 } + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(tween(340, easing = ease)) { it / 7 } + fadeOut(tween(240)) },
        ) {
            composable("home") {
                HomeScreen(
                    vm,
                    onCreate = {
                        vm.browserIndex = if (vm.working != null) 1 else 0
                        openBrowser()
                    },
                    onOpenHistory = { item ->
                        vm.restore(item)
                        openBrowser()
                    },
                )
            }
            composable("library") {
                LibraryScreen(
                    vm,
                    onOpenLook = { look ->
                        vm.openLook(look)
                        openBrowser()
                    },
                    onOpenHistory = { item ->
                        vm.restore(item)
                        openBrowser()
                    },
                )
            }
            composable("settings") { SettingsScreen(vm) }
            composable("browser") {
                BrowserScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("editor") { launchSingleTop = true } },
                )
            }
            composable("editor") {
                EditorScreen(vm, onDone = { nav.popBackStack() }, onHandwriting = { nav.navigate("handwriting") { launchSingleTop = true } })
            }
            composable("handwriting") {
                HandwritingScreen(vm, onDone = { nav.popBackStack() })
            }
        }
    }
}
