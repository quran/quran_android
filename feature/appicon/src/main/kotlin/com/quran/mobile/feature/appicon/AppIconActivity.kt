package com.quran.mobile.feature.appicon

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.appicon.di.AppIconComponentInterface
import com.quran.mobile.feature.appicon.presenter.AppIconPresenter
import com.quran.mobile.feature.appicon.ui.AppIconScreen
import dev.zacsweers.metro.Inject

class AppIconActivity : AppCompatActivity() {

  @Inject
  lateinit var appIconPresenter: AppIconPresenter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? AppIconComponentInterface
    injector?.appIconComponentFactory()?.generate()?.inject(this)

    enableEdgeToEdge()

    setContent {
      val moleculeScope = rememberCoroutineScope()
      val stateFlow = remember {
        moleculeScope.launchMolecule(mode = RecompositionMode.ContextClock) {
          appIconPresenter.present()
        }
      }
      val state by stateFlow.collectAsState()

      QuranTheme {
        AppIconScreen(state = state, onBackPressed = { finish() })
      }
    }
  }
}
