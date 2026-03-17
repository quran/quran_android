package com.quran.labs.androidquran.tv.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.quran.labs.androidquran.tv.home.TvHomeScreen
import com.quran.labs.androidquran.tv.juz.TvJuzGridScreen
import com.quran.labs.androidquran.tv.surah.TvSurahGridScreen
import com.quran.labs.androidquran.tv.bookmark.TvBookmarksScreen
import com.quran.labs.androidquran.tv.settings.TvSettingsScreen
import com.quran.labs.androidquran.tv.search.TvSearchScreen
import com.quran.labs.androidquran.tv.reading.TvPageViewerScreen
import com.quran.labs.androidquran.tv.audio.TvAudioScreen
import com.quran.labs.androidquran.tv.audio.TvAudioControlOverlay
import com.quran.labs.androidquran.tv.audio.AudioState

sealed class TvScreen(val route: String) {
  object Home : TvScreen("home")
  object SurahGrid : TvScreen("surahs")
  object JuzGrid : TvScreen("juz")
  object Bookmarks : TvScreen("bookmarks")
  object Settings : TvScreen("settings")
  object Search : TvScreen("search")
  object Audio : TvScreen("audio")
  object PageViewer : TvScreen("page_viewer") {
    fun createRoute(page: Int) = "page_viewer/$page"
  }

  object SurahViewer : TvScreen("surah_viewer") {
    fun createRoute(sura: Int) = "surah_viewer/$sura"
  }
}

@Composable
fun TvNavHost(
  navController: NavHostController,
  startDestination: String = TvScreen.Home.route
) {
  NavHost(
    navController = navController,
    startDestination = startDestination
  ) {
    composable(TvScreen.Home.route) {
      TvHomeScreen(
        onNavigate = { destination ->
          when (destination) {
            "surahs" -> navController.navigate(TvScreen.SurahGrid.route)
            "juz" -> navController.navigate(TvScreen.JuzGrid.route)
            "bookmarks" -> navController.navigate(TvScreen.Bookmarks.route)
            "settings" -> navController.navigate(TvScreen.Settings.route)
            "search" -> navController.navigate(TvScreen.Search.route)
            "continue_reading" -> {
              // Navigate to page viewer with last read page (604 for now)
              navController.navigate(TvScreen.PageViewer.createRoute(604))
            }
          }
        }
      )
    }

    composable(TvScreen.SurahGrid.route) {
      TvSurahGridScreen(
        onSurahClick = { sura ->
          navController.navigate(TvScreen.SurahViewer.createRoute(sura))
        }
      )
    }

    composable(TvScreen.JuzGrid.route) {
      TvJuzGridScreen(
        onJuzClick = { juz ->
          // Navigate to the page viewer with the starting page of this Juz
          navController.navigate(TvScreen.PageViewer.createRoute(juz))
        }
      )
    }

    composable(TvScreen.Bookmarks.route) {
      TvBookmarksScreen(
        onBookmarkClick = { page ->
          navController.navigate(TvScreen.PageViewer.createRoute(page))
        }
      )
    }

    composable(TvScreen.Settings.route) {
      TvSettingsScreen(
        onNavigateToAudio = {
          navController.navigate(TvScreen.Audio.route)
        }
      )
    }

    composable(TvScreen.Search.route) {
      TvSearchScreen(
        onSearchResultClick = { sura, ayah, page ->
          navController.navigate(TvScreen.PageViewer.createRoute(page))
        }
      )
    }

    composable(TvScreen.Audio.route) {
      TvAudioScreen()
    }

    composable(
      route = "${TvScreen.PageViewer.route}/{page}",
      arguments = listOf(navArgument("page") { type = NavType.IntType })
    ) { backStackEntry ->
      val page = backStackEntry.arguments?.getInt("page") ?: 1
      TvPageViewerScreen(
        initialPage = page,
        onBackClick = { navController.popBackStack() }
      )
    }

    composable(
      route = "${TvScreen.SurahViewer.route}/{sura}",
      arguments = listOf(navArgument("sura") { type = NavType.IntType })
    ) { backStackEntry ->
      val sura = backStackEntry.arguments?.getInt("sura") ?: 1
      PlaceholderScreen("Surah $sura Viewer")
    }
  }
}

@Composable
private fun PlaceholderScreen(title: String) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      androidx.compose.material3.Text(
        text = title,
        style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
      )
      androidx.compose.material3.Text(
        text = "Coming Soon",
        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = 16.dp)
      )
      androidx.compose.material3.Text(
        text = "This feature will be available in a future update",
        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
      )
    }
  }
}
