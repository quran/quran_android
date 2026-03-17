package com.quran.labs.androidquran.tv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.quran.labs.androidquran.tv.home.TvHomeScreen
import com.quran.labs.androidquran.tv.home.TvHomeViewModel
import com.quran.labs.androidquran.tv.navigation.TvNavHost
import com.quran.labs.androidquran.tv.navigation.TvScreen
import com.quran.labs.androidquran.tv.ui.theme.QuranTvTheme

class TvMainActivity : ComponentActivity() {

  private val homeViewModel: TvHomeViewModel by viewModels()
  private lateinit var navController: NavHostController

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      QuranTvTheme {
        TvApp()
      }
    }
  }

  @Composable
  fun TvApp() {
    navController = rememberNavController()

    TvNavHost(
      navController = navController,
      startDestination = TvScreen.Home.route
    )
  }
}
