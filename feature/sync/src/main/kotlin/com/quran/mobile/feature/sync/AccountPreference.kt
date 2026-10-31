package com.quran.mobile.feature.sync

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.sync.presenter.AccountSettingsPresenter
import kotlinx.coroutines.flow.map

/**
 * Preference row that renders [AccountSettingsRow] via Compose.
 *
 * The row's state comes from [presenter], so the signed-in/signed-out presentation and the sync
 * status stay current without a manual Preference rebuild on fragment resume.
 */
internal class AccountPreference(
  context: Context,
  private val presenter: AccountSettingsPresenter
) : Preference(context) {

  init {
    layoutResource = R.layout.quran_sync_account_preference
    isSelectable = false
    isIconSpaceReserved = false
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)
    // The layout's root is the ComposeView itself (no wrapping preference frame), so itemView
    // is the view to render into.
    val composeView = holder.itemView as ComposeView
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
      QuranTheme {
        AccountSettings()
      }
    }
  }

  @Composable
  private fun AccountSettings() {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val moleculeScope = rememberCoroutineScope()
    val stateFlow = remember {
      // compose pauses frames while stopped, so the lifecycle is read as a flow
      val isStarted = lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
      moleculeScope.launchMolecule(mode = RecompositionMode.ContextClock) {
        presenter.present(observing = isStarted)
      }
    }
    val state by stateFlow.collectAsState()

    AccountSettingsRow(
      state = state,
      onOpenAccount = {
        context.startActivity(
          Intent(context, QuranSyncActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
      }
    )
  }
}
