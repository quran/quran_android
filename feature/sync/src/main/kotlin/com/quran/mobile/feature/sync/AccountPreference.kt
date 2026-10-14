package com.quran.mobile.feature.sync

import android.content.Context
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.quran.labs.androidquran.common.ui.core.QuranTheme

/**
 * Preference row that renders [AccountSettingsRow] via Compose.
 *
 * Compose lets the row react to [QuranSyncManager.authState] directly, so the signed-in/signed-out
 * presentation (and each state's own click targets) stay current without a manual Preference
 * rebuild on fragment resume.
 */
internal class AccountPreference(
  context: Context,
  private val syncManager: QuranSyncManager
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
        AccountSettingsRow(syncManager = syncManager)
      }
    }
  }
}
