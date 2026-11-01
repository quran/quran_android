package com.quran.mobile.feature.sync

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.modifier.autoMirror
import com.quran.mobile.feature.sync.state.AccountProfile
import com.quran.mobile.feature.sync.state.AccountSyncEvent
import com.quran.mobile.feature.sync.state.AccountSyncState
import com.quran.mobile.feature.sync.state.HeldItemLookup
import com.quran.mobile.feature.sync.state.HeldItemRowState
import com.quran.mobile.feature.sync.state.SyncActivityState
import com.quran.mobile.feature.sync.state.accountProfile
import com.quran.mobile.feature.sync.state.heldItemRows
import com.quran.shared.auth.model.UserInfo
import com.quran.shared.persistence.model.Collection
import com.quran.shared.persistence.model.Note
import com.quran.shared.persistence.model.NoteRange
import com.quran.shared.persistence.util.toPlatform
import com.quran.shared.pipeline.HeldReason
import com.quran.shared.pipeline.HeldSyncItem
import com.quran.shared.pipeline.SyncError
import com.quran.shared.pipeline.SyncErrorKind
import com.quran.shared.pipeline.SyncResourceType
import com.quran.shared.pipeline.SyncRun
import com.quran.shared.pipeline.SyncRunResult
import com.quran.shared.pipeline.SyncRunTrigger
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import androidx.appcompat.R as AppCompatR

@Composable
internal fun AccountSyncScreen(
  state: AccountSyncState.SignedIn,
  verses: QuranVerses,
  onBackPressed: () -> Unit,
  onManageAccount: () -> Unit,
  onSyncActivity: () -> Unit
) {
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.quran_sync_account_title),
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
      state = listState,
      verticalArrangement = Arrangement.spacedBy(24.dp),
      contentPadding = PaddingValues(
        start = paddingValues.calculateStartPadding(layoutDirection) + 16.dp,
        top = paddingValues.calculateTopPadding() + 8.dp,
        end = paddingValues.calculateEndPadding(layoutDirection) + 16.dp,
        bottom = paddingValues.calculateBottomPadding() + 24.dp
      ),
      modifier = Modifier.fillMaxSize()
    ) {
      item(key = "header") {
        AccountHeader(state.profile)
      }
      item(key = "status") {
        SyncStatusCard(
          summary = screenSyncStatusSummary(
            activity = state.activity,
            now = rememberNow(lastSynced = state.activity.lastSynced)
          ),
          onSyncNow = { state.eventSink(AccountSyncEvent.SyncNow) }
        )
      }
      if (state.heldRows.isNotEmpty()) {
        item(key = "held") {
          HeldItemsSection(
            rows = state.heldRows.take(3),
            count = state.blockingItemCount,
            verses = verses,
            onShowAll = onSyncActivity
          )
        }
      }
      item(key = "runs") {
        RecentSyncsSection(state.recentRuns, onSeeAll = onSyncActivity)
      }
      item(key = "actions") {
        AccountActions(
          isSigningOut = state.isSigningOut,
          onManageAccount = onManageAccount,
          onSignOut = { state.eventSink(AccountSyncEvent.RequestSignOut) }
        )
      }
    }
  }

  if (state.signOutWarning != null) {
    SignOutConfirmationDialog(
      warning = state.signOutWarning,
      onConfirm = { state.eventSink(AccountSyncEvent.ConfirmSignOut) },
      onDismiss = { state.eventSink(AccountSyncEvent.DismissSignOutConfirmation) },
      onReview = {
        state.eventSink(AccountSyncEvent.DismissSignOutConfirmation)
        if (state.blockingItemCount > 0) {
          scope.launch { listState.animateScrollToItem(2) }
        }
      }
    )
  }

  if (state.signOutError != null) {
    AlertDialog(
      onDismissRequest = { state.eventSink(AccountSyncEvent.DismissSignOutError) },
      text = { Text(state.signOutError.ifBlank { stringResource(R.string.quran_sync_action_error) }) },
      confirmButton = {
        TextButton(onClick = { state.eventSink(AccountSyncEvent.DismissSignOutError) }) {
          Text(stringResource(android.R.string.ok))
        }
      }
    )
  }
}

@Composable
private fun AccountHeader(profile: AccountProfile) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp)
      .semantics(mergeDescendants = true) {}
  ) {
    AccountAvatar(profile = profile, size = 88.dp)
    if (profile.name != null) {
      Text(
        text = profile.name,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp)
      )
    }
    if (profile.email != null) {
      Text(
        text = profile.email,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = if (profile.name == null) Modifier.padding(top = 8.dp) else Modifier
      )
    }
  }
}

@Composable
internal fun SyncStatusCard(summary: SyncStatusSummary, onSyncNow: () -> Unit) {
  SyncCard {
    Column(
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier.padding(16.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (summary.icon != null) {
          SyncStatusIcon(icon = summary.icon, modifier = Modifier.size(SyncRowIconSize))
        }
        Column(
          modifier = Modifier
            .weight(1f)
            .semantics(mergeDescendants = true) {}
        ) {
          Text(
            text = summary.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = summary.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Button(
        onClick = onSyncNow,
        enabled = summary.button.isEnabled,
        colors = ButtonDefaults.buttonColors(
          disabledContainerColor = MaterialTheme.colorScheme.primary,
          disabledContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier
          .fillMaxWidth()
          .alpha(if (summary.button.isEnabled) 1f else 0.45f)
      ) {
        Text(
          text = stringResource(summary.button.title),
          fontWeight = FontWeight.SemiBold
        )
      }
    }
  }
}

@Composable
internal fun HeldItemsSection(
  rows: List<HeldItemRowState>,
  count: Int,
  verses: QuranVerses,
  onShowAll: (() -> Unit)? = null
) {
  val quranColors = LocalQuranColors.current
  SyncSection(
    title = stringResource(R.string.quran_sync_held_header, rememberNumberFormat().format(count)),
    footer = stringResource(R.string.quran_sync_held_footer),
    color = quranColors.cautionBackground
  ) {
    rows.forEachIndexed { index, row ->
      if (index > 0) {
        RowDivider(color = quranColors.cautionSeparator)
      }
      HeldItemRow(row = row, verses = verses)
    }
    if (onShowAll != null && count > rows.size) {
      RowDivider(color = quranColors.cautionSeparator)
      SyncLinkRow(
        title = stringResource(R.string.quran_sync_held_show_all, rememberNumberFormat().format(count)),
        onClick = onShowAll
      )
    }
  }
}

@Composable
internal fun RecentSyncsSection(
  runs: List<SyncRun>,
  footer: String? = null,
  onSeeAll: (() -> Unit)? = null
) {
  SyncSection(title = stringResource(R.string.quran_sync_runs_header), footer = footer) {
    if (runs.isEmpty()) {
      NoSyncRunsRow()
    } else {
      runs.forEachIndexed { index, run ->
        if (index > 0) {
          RowDivider()
        }
        SyncRunRow(run)
      }
      if (onSeeAll != null) {
        RowDivider()
        SyncLinkRow(title = stringResource(R.string.quran_sync_runs_see_all), onClick = onSeeAll)
      }
    }
  }
}

@Composable
private fun SyncLinkRow(title: String, onClick: () -> Unit) {
  Text(
    text = title,
    style = MaterialTheme.typography.bodyLarge,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 14.dp)
  )
}

@Composable
private fun AccountActions(
  isSigningOut: Boolean,
  onManageAccount: () -> Unit,
  onSignOut: () -> Unit
) {
  SyncCard {
    ActionRow(
      icon = QuranIcons.AccountCircle,
      iconTint = MaterialTheme.colorScheme.primary,
      title = stringResource(R.string.quran_sync_manage_account),
      titleColor = MaterialTheme.colorScheme.onSurface,
      onClick = onManageAccount
    ) {
      Icon(
        imageVector = QuranIcons.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(20.dp)
      )
    }
    RowDivider()
    ActionRow(
      icon = QuranIcons.Logout,
      iconTint = MaterialTheme.colorScheme.error,
      title = stringResource(R.string.quran_sync_sign_out),
      titleColor = MaterialTheme.colorScheme.error,
      enabled = !isSigningOut,
      onClick = onSignOut
    ) {
      if (isSigningOut) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
      }
    }
  }
}

@Composable
private fun ActionRow(
  icon: ImageVector,
  iconTint: Color,
  title: String,
  titleColor: Color,
  onClick: () -> Unit,
  enabled: Boolean = true,
  trailing: @Composable () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 14.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = iconTint,
      modifier = Modifier.size(SyncRowIconSize)
    )
    Text(
      text = title,
      style = MaterialTheme.typography.bodyLarge,
      color = titleColor,
      modifier = Modifier.weight(1f)
    )
    trailing()
  }
}

@Composable
private fun SyncSection(
  title: String,
  footer: String? = null,
  color: Color = MaterialTheme.colorScheme.secondaryContainer,
  content: @Composable ColumnScope.() -> Unit
) {
  Column {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        .semantics { heading() }
    )
    SyncCard(color = color, content = content)
    if (footer != null) {
      Text(
        text = footer,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
      )
    }
  }
}

@Composable
private fun SyncCard(
  color: Color = MaterialTheme.colorScheme.secondaryContainer,
  content: @Composable ColumnScope.() -> Unit
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = color,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(content = content)
  }
}

@Composable
private fun RowDivider(color: Color = MaterialTheme.colorScheme.outlineVariant) {
  HorizontalDivider(color = color, modifier = Modifier.padding(start = SyncRowTextStart))
}

private val previewNow = Clock.System.now()

private val previewVerses = QuranVerses(List(114) { 286 })

private fun previewRun(
  ago: Duration,
  trigger: SyncRunTrigger,
  result: SyncRunResult = SyncRunResult.SUCCEEDED,
  pushed: Int = 0,
  pulled: Int = 0,
  held: Int = 0,
  attempts: Int = 1,
  error: SyncErrorKind? = null
) = SyncRun(
  startedAt = previewNow - ago - 5.minutes,
  endedAt = previewNow - ago,
  trigger = trigger,
  result = result,
  attempts = attempts,
  pushed = pushed,
  pulled = pulled,
  held = held,
  error = error?.let { SyncError(it, null, null, "") }
)

private fun previewHeld(type: SyncResourceType, id: String, reason: HeldReason, waitingOn: HeldSyncItem? = null) =
  HeldSyncItem(
    type = type,
    id = id,
    collectionId = waitingOn?.id,
    reason = reason,
    waitingOn = waitingOn,
    httpStatus = null,
    serverCode = null
  )

private fun previewState(
  status: SyncActivityState.Status,
  held: List<HeldSyncItem> = emptyList(),
  runs: List<SyncRun> = emptyList(),
  user: UserInfo = UserInfo(id = "1", name = "Aisha Rahman", email = "aisha.rahman@example.com")
): AccountSyncState.SignedIn {
  val note = Note("note", listOf(NoteRange(2, 255, 2, 255)), previewNow.toPlatform(), "note")
  val collection = Collection("Morning adhkar", previewNow.toPlatform(), "collection")
  val activity = SyncActivityState(
    status = status,
    lastSynced = (status as? SyncActivityState.Status.Synced)?.at,
    heldItems = held.toImmutableList(),
    runs = runs.toImmutableList()
  )
  return AccountSyncState.SignedIn(
    profile = accountProfile(user),
    activity = activity,
    heldRows = heldItemRows(held, HeldItemLookup(listOf(note), listOf(collection))),
    blockingItemCount = activity.blockingItems.size,
    recentRuns = runs.take(3).toImmutableList(),
    signOutWarning = null,
    isSigningOut = false,
    signOutError = null,
    eventSink = {}
  )
}

@Composable
private fun PreviewScreen(state: AccountSyncState.SignedIn) {
  QuranTheme {
    AccountSyncScreen(state = state, verses = previewVerses, onBackPressed = {}, onManageAccount = {}, onSyncActivity = {})
  }
}

@Preview(heightDp = 1400)
@Preview("dark theme", heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountSyncScreenPreview() {
  val heldCollection = previewHeld(SyncResourceType.COLLECTION, "collection", HeldReason.REJECTED_BY_SERVER)
  PreviewScreen(
    previewState(
      status = SyncActivityState.Status.Synced(previewNow - 2.minutes),
      held = listOf(
        previewHeld(SyncResourceType.NOTE, "note", HeldReason.TOO_SHORT),
        heldCollection,
        previewHeld(SyncResourceType.COLLECTION_BOOKMARK, "2:255", HeldReason.WAITING_ON_PARENT, heldCollection),
        previewHeld(SyncResourceType.COLLECTION_BOOKMARK, "2:256", HeldReason.WAITING_ON_PARENT, heldCollection),
        previewHeld(SyncResourceType.NOTE, "deleted", HeldReason.TOO_LONG),
        previewHeld(SyncResourceType.READING_SESSION, "session", HeldReason.NOT_YET_LINKED)
      ),
      runs = listOf(
        previewRun(2.minutes, SyncRunTrigger.MANUAL, pushed = 3, pulled = 1),
        previewRun(3.hours, SyncRunTrigger.LOCAL_CHANGE, pushed = 1, held = 2),
        previewRun(1.days, SyncRunTrigger.PERIODIC, SyncRunResult.FAILED, attempts = 6, error = SyncErrorKind.NETWORK)
      )
    )
  )
}

@Preview(heightDp = 900)
@Preview("dark theme", heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountSyncScreenFirstSyncPreview() {
  PreviewScreen(
    previewState(
      status = SyncActivityState.Status.NeverSynced,
      user = UserInfo(id = "1", email = "aisha.rahman@example.com")
    )
  )
}

@Preview(heightDp = 900)
@Composable
private fun AccountSyncScreenRetryingPreview() {
  PreviewScreen(
    previewState(
      status = SyncActivityState.Status.Retrying(SyncErrorKind.NETWORK),
      runs = listOf(
        previewRun(1.minutes, SyncRunTrigger.MANUAL, SyncRunResult.PULLED_ONLY, pulled = 4),
        previewRun(30.minutes, SyncRunTrigger.FOREGROUND, SyncRunResult.CANCELLED),
        previewRun(2.hours, SyncRunTrigger.APP_START)
      )
    )
  )
}

@Preview(heightDp = 1400)
@Preview("dark theme", heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview("Arabic", heightDp = 1400, locale = "ar")
@Composable
private fun SyncActivityScreenPreview() {
  val state = previewState(
    status = SyncActivityState.Status.Retrying(SyncErrorKind.NETWORK),
    held = listOf(
      previewHeld(SyncResourceType.NOTE, "note", HeldReason.TOO_SHORT),
      previewHeld(SyncResourceType.COLLECTION, "collection", HeldReason.REJECTED_BY_SERVER),
      previewHeld(SyncResourceType.NOTE, "deleted", HeldReason.TOO_LONG),
      previewHeld(SyncResourceType.READING_SESSION, "session", HeldReason.NOT_YET_LINKED)
    ),
    runs = listOf(
      previewRun(1.minutes, SyncRunTrigger.MANUAL, SyncRunResult.FAILED, attempts = 5, error = SyncErrorKind.NETWORK),
      previewRun(30.minutes, SyncRunTrigger.PERIODIC, SyncRunResult.PULLED_ONLY, pulled = 4),
      previewRun(1.hours, SyncRunTrigger.FOREGROUND, SyncRunResult.CANCELLED),
      previewRun(2.hours, SyncRunTrigger.APP_START)
    )
  )
  QuranTheme {
    SyncActivityScreen(state.activity, state.heldRows, previewVerses, onBackPressed = {}, onSyncNow = {})
  }
}
