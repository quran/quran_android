package com.quran.mobile.feature.sync

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme

/**
 * Shows a card prompting the user to sign in to sync their bookmarks, when applicable.
 *
 * The card is hidden entirely (renders nothing) when sync isn't configured for this build, the
 * user is already signed in, or they previously dismissed it.
 */
@Composable
fun BookmarksSignInCard(
  syncManager: QuranSyncManager,
  onSignIn: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isVisible by syncManager.showBookmarksSignInCardFlow.collectAsState(initial = false)
  if (isVisible) {
    SyncSignInCard(
      onSignIn = onSignIn,
      onDismiss = { syncManager.dismissBookmarksSignInCard() },
      modifier = modifier.padding(12.dp)
    )
  }
}

@Composable
private fun SyncSignInCard(
  onSignIn: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(MaterialTheme.colorScheme.surface)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = QuranIcons.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp)
          )
        }
        Column(modifier = Modifier.padding(start = 10.dp, end = 24.dp)) {
          Text(
            text = stringResource(R.string.quran_sync_bookmarks_cta_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(R.string.quran_sync_bookmarks_cta_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
          )
        }
      }
      Button(
        onClick = onSignIn,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp)
      ) {
        Text(text = stringResource(R.string.quran_sync_bookmarks_cta_button))
      }
    }
    IconButton(
      onClick = onDismiss,
      modifier = Modifier.align(Alignment.TopEnd)
    ) {
      Icon(
        imageVector = QuranIcons.Close,
        contentDescription = stringResource(R.string.quran_sync_bookmarks_cta_dismiss),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}

@Preview
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SyncSignInCardPreview() {
  QuranTheme {
    Surface {
      SyncSignInCard(
        onSignIn = {},
        onDismiss = {},
        modifier = Modifier.padding(12.dp)
      )
    }
  }
}
