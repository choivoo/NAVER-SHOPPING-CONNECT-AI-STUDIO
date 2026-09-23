package com.shoppingconnect.aistudio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.ui.adaptive.LocalWindowLayout
import com.shoppingconnect.aistudio.ui.adaptive.rememberWindowLayout
import com.shoppingconnect.aistudio.ui.navigation.AppNavHost
import com.shoppingconnect.aistudio.ui.navigation.Routes
import com.shoppingconnect.aistudio.ui.navigation.TopDest
import com.shoppingconnect.aistudio.ui.theme.AiStudioTheme

@Composable
fun AppRoot(settings: AppSettings) {
    AiStudioTheme(settings.theme, settings.dynamicColor, settings.reduceMotion) {
        val wl = rememberWindowLayout()
        CompositionLocalProvider(LocalWindowLayout provides wl) {
            val nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            val topLevel = TopDest.entries.firstOrNull { it.route == route }
            val events: AppEventsViewModel = hiltViewModel()
            val openProject by events.events.openProject.collectAsStateWithLifecycle()
            val sharedLink by events.events.sharedLink.collectAsStateWithLifecycle()
            LaunchedEffect(openProject) { openProject?.let { nav.navigate(Routes.project(it)); events.events.consumeProject() } }
            LaunchedEffect(sharedLink) { if (sharedLink != null && settings.onboardingDone && route != TopDest.HOME.route) navigateTop(nav, TopDest.HOME) }

            val adaptiveType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                NavigationSuiteScaffold(
                    layoutType = if (topLevel == null) NavigationSuiteType.None else adaptiveType,
                    navigationSuiteItems = {
                        TopDest.entries.forEach { d ->
                            item(
                                selected = topLevel == d,
                                onClick = { navigateTop(nav, d) },
                                icon = { Icon(d.icon, contentDescription = d.label) },
                                label = { Text(d.label) },
                            )
                        }
                    },
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost(nav, startDestination = if (settings.onboardingDone) TopDest.HOME.route else Routes.ONBOARDING)
                    }
                }
            }
        }
    }
}

fun navigateTop(nav: NavHostController, d: TopDest) {
    nav.navigate(d.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class AppEventsViewModel @javax.inject.Inject constructor(val events: AppEvents) : androidx.lifecycle.ViewModel()
