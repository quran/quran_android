package com.quran.mobile.feature.sync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.sync.di.QuranSyncComponentInterface
import dev.zacsweers.metro.Inject

class QuranSyncActivity : ComponentActivity() {

  @Inject
  lateinit var syncManager: QuranSyncManager

  private var authActivityRegistration: QuranSyncManager.AuthActivityRegistration? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? QuranSyncComponentInterface
    injector?.quranSyncComponentFactory()?.generate()?.inject(this)

    if (!syncManager.isConfigured) {
      finish()
      return
    }

    authActivityRegistration = syncManager.registerAuthActivity(this)
    enableEdgeToEdge()

    setContent {
      QuranTheme {
        QuranSyncScreen(
          syncManager = syncManager,
          onBackPressed = { finish() }
        )
      }
    }
  }

  override fun onDestroy() {
    syncManager.unregisterAuthActivity(authActivityRegistration)
    authActivityRegistration = null
    super.onDestroy()
  }
}
