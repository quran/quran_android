package com.quran.mobile.feature.sync

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cash.molecule.AndroidUiDispatcher
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.sync.di.AuthComponentInterface
import com.quran.mobile.feature.sync.presenter.QuranLoginPresenter
import com.quran.mobile.feature.sync.ui.LoginScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

class QuranLoginActivity : AppCompatActivity() {
  @Inject
  lateinit var quranLoginPresenter: QuranLoginPresenter

  private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)

  private val authFlow by lazy(LazyThreadSafetyMode.NONE) {
    scope.launchMolecule(mode = RecompositionMode.ContextClock) {
      quranLoginPresenter.present()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? AuthComponentInterface
    injector?.authComponentFactory()?.generate()?.inject(this)

    setContent {
      QuranTheme {
        val authenticationState = authFlow.collectAsState()

        Scaffold(topBar = {
          TopAppBar(
            title = { Text(stringResource(R.string.sync_with_quran_com)) },
            navigationIcon = {
              IconButton(onClick = { finish() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
              }
            }
          )
        }) { paddingValues ->
          LoginScreen(authenticationState.value, Modifier.padding(paddingValues))
        }
      }
    }
  }
}
