package com.quran.mobile.feature.sync

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.modifier.autoMirror
import com.quran.mobile.feature.sync.state.AccountSettingsEvent
import com.quran.mobile.feature.sync.state.AccountSettingsState

/**
 * Renders the Account row in Settings for [state].
 *
 * Signed out (or configured but not yet authenticated) shows a benefit-forward CTA. Signed in shows
 * the account card with the profile and the live sync status. Both open the account through
 * [onOpenAccount].
 */
@Composable
internal fun AccountSettingsRow(
  state: AccountSettingsState,
  onOpenAccount: () -> Unit,
  modifier: Modifier = Modifier
) {
  when (state) {
    AccountSettingsState.SignedOut -> SignInPromptRow(onClick = onOpenAccount, modifier = modifier)
    is AccountSettingsState.SignedIn -> SyncAccountCard(
      profile = state.profile,
      summary = cardSyncStatusSummary(
        activity = state.activity,
        now = rememberNow(lastSynced = state.activity.lastSynced)
      ),
      onOpenAccount = onOpenAccount,
      onSyncNow = { state.eventSink(AccountSettingsEvent.SyncNow) },
      modifier = modifier
    )
  }
}

@Composable
private fun SignInPromptRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(12.dp)
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = QuranIcons.Person,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.size(18.dp)
        )
      }
      Column(
        modifier = Modifier
          .padding(start = 12.dp)
          .weight(1f)
      ) {
        Text(
          text = stringResource(R.string.quran_sync_sign_in_prompt_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
          text = stringResource(R.string.quran_sync_settings_summary),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Icon(
        imageVector = QuranIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.autoMirror()
      )
    }
  }
}

@Preview
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SignInPromptRowPreview() {
  QuranTheme {
    Surface {
      SignInPromptRow(onClick = {}, modifier = Modifier.padding(12.dp))
    }
  }
}
