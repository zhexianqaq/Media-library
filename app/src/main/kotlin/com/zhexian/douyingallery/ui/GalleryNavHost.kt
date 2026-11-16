package com.zhexian.douyingallery.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhexian.douyingallery.domain.FileSort
import com.zhexian.douyingallery.ui.creator.CreatorScreen
import com.zhexian.douyingallery.ui.home.HomeScreen
import com.zhexian.douyingallery.ui.permission.PermissionScreen
import com.zhexian.douyingallery.ui.search.SearchScreen
import com.zhexian.douyingallery.ui.settings.SettingsScreen
import com.zhexian.douyingallery.ui.viewer.ViewerArgs
import com.zhexian.douyingallery.ui.viewer.ViewerScreen
import com.zhexian.douyingallery.ui.work.WorkScreen
import com.zhexian.douyingallery.util.StoragePermission

private object Route {
    const val HOME = "home"
    const val CREATOR = "creator/{creator}"
    const val WORK = "work/{path}"
    const val VIEWER = "viewer/{args}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    fun creator(name: String) = "creator/${Uri.encode(name)}"
    fun work(path: String) = "work/${Uri.encode(path)}"
    fun viewer(args: String) = "viewer/${Uri.encode(args)}"
}

@Composable
fun GalleryNavHost(deepLinkWorkPath: String?) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(StoragePermission.hasAccess(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasAccess = StoragePermission.hasAccess(context)
    }

    if (!hasAccess) {
        PermissionScreen(onGranted = { hasAccess = true })
        return
    }

    val nav = rememberNavController()

    LaunchedEffect(deepLinkWorkPath) {
        deepLinkWorkPath?.takeIf { it.isNotBlank() }?.let { path ->
            nav.navigate(Route.work(path)) { launchSingleTop = true }
        }
    }

    NavHost(navController = nav, startDestination = Route.HOME) {
        composable(Route.HOME) {
            HomeScreen(
                onOpenCreator = { nav.navigate(Route.creator(it)) },
                onOpenViewerOfTimeline = { filter, index ->
                    nav.navigate(
                        Route.viewer(ViewerArgs.encodeOfTimeline(filter.typeArg, filter.favOnly, index))
                    )
                },
                onOpenSearch = { nav.navigate(Route.SEARCH) },
                onOpenSettings = { nav.navigate(Route.SETTINGS) }
            )
        }

        composable(
            route = Route.CREATOR,
            arguments = listOf(navArgument("creator") { type = NavType.StringType })
        ) { entry ->
            val creator = Uri.decode(entry.arguments?.getString("creator").orEmpty())
            CreatorScreen(
                creator = creator,
                onBack = { nav.popBackStack() },
                onOpenWork = { nav.navigate(Route.work(it)) }
            )
        }

        composable(
            route = Route.WORK,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) { entry ->
            val workPath = Uri.decode(entry.arguments?.getString("path").orEmpty())
            WorkScreen(
                workPath = workPath,
                onBack = { nav.popBackStack() },
                onOpenViewer = { sort, index ->
                    nav.navigate(
                        Route.viewer(ViewerArgs.encodeOfWork(workPath, sort, index))
                    )
                }
            )
        }

        composable(
            route = Route.VIEWER,
            arguments = listOf(navArgument("args") { type = NavType.StringType })
        ) { entry ->
            val decoded = ViewerArgs.decode(Uri.decode(entry.arguments?.getString("args").orEmpty()))
            ViewerScreen(
                scope = decoded.scope,
                startIndex = decoded.index,
                onBack = { nav.popBackStack() }
            )
        }

        composable(Route.SEARCH) {
            SearchScreen(
                onBack = { nav.popBackStack() },
                onOpenCreator = { nav.navigate(Route.creator(it)) },
                onOpenWork = { nav.navigate(Route.work(it)) },
                onOpenFile = { item ->
                    nav.navigate(
                        Route.viewer(
                            ViewerArgs.encodeOfWork(
                                workPath = item.workPath,
                                sort = FileSort.SEQ,
                                index = 0,
                                focusId = item.id
                            )
                        )
                    )
                }
            )
        }

        composable(Route.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
