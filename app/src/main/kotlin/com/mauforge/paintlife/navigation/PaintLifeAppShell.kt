package com.mauforge.paintlife.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.debug.DebugMenu
import com.mauforge.paintlife.mytime.StartDestination

/** A tapped reminder asks the app to show [destination]; a new [token] is followed once. */
data class NavRequest(val token: Int, val destination: StartDestination)

/**
 * App frame: an adaptive navigation suite (bottom bar on phones, rail on wider windows) around
 * the navigation host. Debug builds add a small debug menu in the top corner; release builds
 * compile an empty one.
 */
@Composable
fun PaintLifeAppShell(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    openQuietRequest: Int = 0,
    navRequest: NavRequest? = null,
) {
    LaunchedEffect(navRequest?.token) {
        when (navRequest?.destination) {
            null -> Unit
            StartDestination.Today -> navController.navigateToTopLevel(TopLevelDestination.TODAY)
            StartDestination.Quiet -> navController.navigateToTopLevel(TopLevelDestination.QUIET)
            StartDestination.MyTime -> navController.navigateToTopLevel(TopLevelDestination.MY_TIME)
            StartDestination.ExerciseSession -> navController.openExerciseSession()
        }
    }
    // Each new request (a tap on a Quiet Time notification) switches to the Quiet tab once.
    LaunchedEffect(openQuietRequest) {
        if (openQuietRequest > 0) navController.navigateToTopLevel(TopLevelDestination.QUIET)
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val colors = PaintLifeTheme.colors
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = colors.accent,
            selectedTextColor = colors.text,
            indicatorColor = colors.accentMuted,
            unselectedIconColor = colors.textSecondary,
            unselectedTextColor = colors.textSecondary,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = colors.accent,
            selectedTextColor = colors.text,
            indicatorColor = colors.accentMuted,
            unselectedIconColor = colors.textSecondary,
            unselectedTextColor = colors.textSecondary,
        ),
    )

    Box(modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            containerColor = colors.canvas,
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = colors.surface,
                navigationRailContainerColor = colors.surface,
                navigationBarContentColor = colors.textSecondary,
                navigationRailContentColor = colors.textSecondary,
            ),
            navigationSuiteItems = {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
                    item(
                        selected = selected,
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                        colors = itemColors,
                        modifier = Modifier.testTag(navItemTestTag(destination)),
                    )
                }
            },
        ) {
            PaintLifeNavHost(navController = navController)
        }
        DebugMenu(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End)),
        )
    }
}

/** Switches tabs keeping one copy of each, restoring its state; Back returns to Today. */
internal fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

internal fun navItemTestTag(destination: TopLevelDestination): String = "nav_item_${destination.name}"
