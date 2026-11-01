package com.quran.mobile.feature.sync

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.modifier.autoMirror
import com.quran.mobile.feature.sync.state.HeldItemRowState
import com.quran.mobile.feature.sync.state.SyncActivityState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import androidx.appcompat.R as AppCompatR

@Composable
internal fun SyncActivityScreen(
  activity: SyncActivityState,
  heldRows: ImmutableList<HeldItemRowState>,
  verses: QuranVerses,
  onBackPressed: () -> Unit,
  onSyncNow: () -> Unit
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.quran_sync_activity_title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        navigationIcon = {
          IconButton(onClick = onBackPressed) {
            Icon(
              imageVector = QuranIcons.ArrowBack,
              contentDescription = stringResource(AppCompatR.string.abc_action_bar_up_description),
              modifier = Modifier.autoMirror()
            )
          }
        }
      )
    }
  ) { paddingValues ->
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
      verticalArrangement = Arrangement.spacedBy(24.dp),
      contentPadding = PaddingValues(
        start = paddingValues.calculateStartPadding(layoutDirection) + 16.dp,
        top = paddingValues.calculateTopPadding() + 8.dp,
        end = paddingValues.calculateEndPadding(layoutDirection) + 16.dp,
        bottom = paddingValues.calculateBottomPadding() + 24.dp
      ),
      modifier = Modifier.fillMaxSize()
    ) {
      item(key = "status") {
        SyncStatusCard(
          summary = screenSyncStatusSummary(activity, rememberNow(activity.lastSynced)),
          onSyncNow = onSyncNow
        )
      }
      if (heldRows.isNotEmpty()) {
        item(key = "held") {
          HeldItemsSection(rows = heldRows, count = heldRows.size, verses = verses)
        }
      }
      item(key = "runs") {
        RecentSyncsSection(
          runs = activity.runs,
          footer = if (activity.runs.isEmpty()) null else {
            stringResource(R.string.quran_sync_activity_footer, rememberNumberFormat().format(100))
          }
        )
      }
    }
  }
}

@Preview
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SyncActivityScreenEmptyPreview() {
  QuranTheme {
    SyncActivityScreen(
      activity = SyncActivityState(SyncActivityState.Status.NeverSynced, null, persistentListOf()),
      heldRows = persistentListOf(),
      verses = QuranVerses(List(114) { 286 }),
      onBackPressed = {},
      onSyncNow = {}
    )
  }
}
