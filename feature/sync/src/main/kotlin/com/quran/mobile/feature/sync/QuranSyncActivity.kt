package com.quran.mobile.feature.sync

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.quran.data.core.QuranConstants
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.sync.di.QuranSyncComponentInterface
import com.quran.mobile.feature.sync.presenter.AccountSyncPresenter
import com.quran.mobile.feature.sync.state.AccountSyncEvent
import com.quran.mobile.feature.sync.state.AccountSyncState
import com.quran.shared.auth.model.AuthState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class QuranSyncActivity : ComponentActivity() {

  @Inject
  lateinit var syncManager: QuranSyncManager

  @Inject
  lateinit var quranInfo: QuranInfo

  private var authActivityRegistration: QuranSyncManager.AuthActivityRegistration? = null
  private val accountSyncState = MutableStateFlow<AccountSyncState.SignedIn?>(null)

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

    // the account screen starts with the first sign-in and closes when that session ends
    lifecycleScope.launch {
      syncManager.authState.first { it is AuthState.Success }
      val isStarted = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
      AccountSyncPresenter(syncManager).launchIn(lifecycleScope, isStarted).collect { state ->
        when (state) {
          is AccountSyncState.SignedIn -> accountSyncState.value = state
          AccountSyncState.SessionEnded -> finish()
        }
      }
    }

    val verses = QuranVerses((1..QuranConstants.NUMBER_OF_SURAS).map(quranInfo::getNumberOfAyahs))
    setContent {
      QuranTheme {
        var showSyncActivity by rememberSaveable { mutableStateOf(false) }
        val screenState = rememberSaveableStateHolder()
        val state = accountSyncState.collectAsState().value
        if (state == null) {
          QuranSyncScreen(
            syncManager = syncManager,
            onBackPressed = { finish() }
          )
        } else {
          BackHandler(enabled = showSyncActivity) { showSyncActivity = false }
          screenState.SaveableStateProvider(if (showSyncActivity) "activity" else "account") {
            if (showSyncActivity) {
              SyncActivityScreen(
                activity = state.activity,
                heldRows = state.heldRows,
                verses = verses,
                onBackPressed = { showSyncActivity = false },
                onSyncNow = { state.eventSink(AccountSyncEvent.SyncNow) }
              )
            } else {
              AccountSyncScreen(
                state = state,
                verses = verses,
                onBackPressed = { finish() },
                onManageAccount = ::openProfile,
                onSyncActivity = { showSyncActivity = true }
              )
            }
          }
        }
      }
    }
  }

  override fun onDestroy() {
    syncManager.unregisterAuthActivity(authActivityRegistration)
    authActivityRegistration = null
    super.onDestroy()
  }

  private fun openProfile() {
    try {
      CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .launchUrl(this, Uri.parse(syncManager.profileUrl))
    } catch (e: ActivityNotFoundException) {
      // no browser to open the profile in
    }
  }
}
