package com.quran.mobile.feature.sync

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.modifier.autoMirror
import com.quran.shared.auth.model.AuthState
import com.quran.shared.auth.model.UserInfo

/**
 * Renders the Account row in Settings, matching the sign-in state of [syncManager].
 *
 * Signed out (or configured but not yet authenticated) shows a benefit-forward CTA that opens
 * [QuranSyncActivity]. Signed in shows the account and a "sign out" action inline, so sign-out is
 * reachable without opening a sub-page.
 */
@Composable
internal fun AccountSettingsRow(
  syncManager: QuranSyncManager,
  modifier: Modifier = Modifier
) {
  val authState by syncManager.authState.collectAsState()
  val userInfo = (authState as? AuthState.Success)?.userInfo
  if (userInfo != null) {
    SignedInAccountRow(
      userInfo = userInfo,
      onSignOut = { syncManager.signOut() },
      modifier = modifier
    )
  } else {
    SignInPromptRow(modifier = modifier)
  }
}

@Composable
private fun SignInPromptRow(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  Surface(
    onClick = { context.startActivity(quranSyncIntent(context)) },
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
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
          color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
          text = stringResource(R.string.quran_sync_settings_summary),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Icon(
        imageVector = QuranIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.autoMirror()
      )
    }
  }
}

@Composable
private fun SignedInAccountRow(
  userInfo: UserInfo?,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .clickable { context.startActivity(quranSyncIntent(context)) }
        .padding(vertical = 8.dp)
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = accountInitial(userInfo),
          color = MaterialTheme.colorScheme.onPrimary,
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.bodyMedium
        )
      }
      Column(
        modifier = Modifier
          .padding(start = 12.dp)
          .weight(1f)
      ) {
        Text(
          text = userInfo?.email ?: userInfo?.displayName
            ?: stringResource(R.string.quran_sync_signed_in),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = stringResource(R.string.quran_sync_settings_signed_in_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Icon(
        imageVector = QuranIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.autoMirror()
      )
    }
    HorizontalDivider()
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onSignOut)
        .padding(vertical = 12.dp)
    ) {
      Icon(
        imageVector = QuranIcons.Logout,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier
          .autoMirror()
          .size(20.dp)
      )
      Text(
        text = stringResource(R.string.quran_sync_logout),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = 12.dp)
      )
    }
  }
}

private fun accountInitial(userInfo: UserInfo?): String {
  val source = userInfo?.displayName?.takeIf { it.isNotBlank() } ?: userInfo?.email
  return source?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

private fun quranSyncIntent(context: android.content.Context) =
  Intent(context, QuranSyncActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

@Preview
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SignInPromptRowPreview() {
  QuranTheme {
    Surface {
      SignInPromptRow(modifier = Modifier.padding(12.dp))
    }
  }
}
