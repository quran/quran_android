package com.quran.mobile.feature.sync

import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.icu.util.ULocale
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.mobile.feature.sync.state.HeldItemRowState
import com.quran.mobile.feature.sync.state.HeldItemRowState.Title
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import com.quran.shared.pipeline.HeldReason
import com.quran.shared.pipeline.HeldSyncItem
import com.quran.shared.pipeline.SyncErrorKind
import com.quran.shared.pipeline.SyncResourceType
import com.quran.shared.pipeline.SyncRun
import com.quran.shared.pipeline.SyncRunResult
import com.quran.shared.pipeline.SyncRunTrigger
import java.util.Date
import kotlin.time.Instant
import com.quran.mobile.common.ui.core.R as UiCoreR

internal val SyncRowIconSize = 24.dp
internal val SyncRowTextStart = 16.dp + SyncRowIconSize + 12.dp

@Composable
internal fun HeldItemRow(
  row: HeldItemRowState,
  verses: QuranVerses,
  modifier: Modifier = Modifier,
  onEdit: (HeldItemRowState.Edit) -> Unit = {}
) {
  val title = heldItemTitle(row.title, verses)
  SyncRow(
    icon = row.item.type.icon,
    iconTint = LocalQuranColors.current.caution,
    alignment = Alignment.Top,
    modifier = modifier,
    trailing = {
      if (row.edit != null) {
        val label = stringResource(R.string.quran_sync_held_edit_accessibility, title)
        TextButton(
          onClick = { onEdit(row.edit) },
          modifier = Modifier.semantics { contentDescription = label }
        ) {
          Text(
            text = stringResource(R.string.quran_sync_held_edit),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clearAndSetSemantics {}
          )
        }
      }
    }
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = stringResource(row.item.reasonText),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
    if (row.waiting != null) {
      Text(
        text = pluralStringResource(
          if (row.waiting.areVerses) R.plurals.quran_sync_held_waiting_verses else R.plurals.quran_sync_held_waiting_items,
          row.waiting.count,
          row.waiting.count
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
internal fun SyncRunRow(run: SyncRun, modifier: Modifier = Modifier) {
  val (icon, tint) = when (run.result) {
    SyncRunResult.SUCCEEDED -> QuranIcons.CheckCircle to LocalQuranColors.current.success
    SyncRunResult.PULLED_ONLY, SyncRunResult.CANCELLED -> QuranIcons.ArrowCircleDown to LocalQuranColors.current.caution
    SyncRunResult.FAILED -> QuranIcons.HighlightOff to MaterialTheme.colorScheme.error
  }
  SyncRow(icon = icon, iconTint = tint, modifier = modifier) {
    Text(
      text = stringResource(
        R.string.quran_sync_run_title,
        syncRunTime(run.endedAt),
        syncRunTrigger(run.trigger)
      ),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = syncRunDetail(run),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
internal fun NoSyncRunsRow(modifier: Modifier = Modifier) {
  SyncRow(icon = null, iconTint = MaterialTheme.colorScheme.onSurface, modifier = modifier) {
    Text(
      text = stringResource(R.string.quran_sync_runs_empty_title),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = stringResource(R.string.quran_sync_runs_empty_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun SyncRow(
  icon: ImageVector?,
  iconTint: Color,
  modifier: Modifier = Modifier,
  alignment: Alignment.Vertical = Alignment.CenterVertically,
  trailing: @Composable () -> Unit = {},
  content: @Composable () -> Unit
) {
  Row(
    verticalAlignment = alignment,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = modifier
      .fillMaxWidth()
      .semantics(mergeDescendants = true) {}
      .padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(SyncRowIconSize)
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
      content()
    }
    trailing()
  }
}

@Composable
private fun heldItemTitle(title: Title, verses: QuranVerses): String = when (title) {
  is Title.Note -> stringResource(R.string.quran_sync_held_note, verseRangeName(title.range, verses))
  is Title.Collection -> stringResource(
    R.string.quran_sync_held_collection,
    if (title.isDefault) stringResource(UiCoreR.string.default_collection) else title.name
  )
  is Title.Bookmark -> stringResource(R.string.quran_sync_held_bookmark, verseName(title.verse, verses))
  is Title.ReadingBookmark -> stringResource(
    R.string.quran_sync_held_reading_bookmark,
    ReadingBookmarkSlots.displayName(title.slot.type, title.name)
  )
  Title.ReadingProgress -> stringResource(R.string.quran_sync_held_reading_progress)
  Title.DeletedNote -> stringResource(R.string.quran_sync_held_deleted_note)
  Title.DeletedCollection -> stringResource(R.string.quran_sync_held_deleted_collection)
  Title.DeletedBookmark -> stringResource(R.string.quran_sync_held_deleted_bookmark)
}

private val SyncResourceType.icon: ImageVector
  get() = when (this) {
    SyncResourceType.NOTE -> QuranIcons.Notes
    SyncResourceType.COLLECTION -> QuranIcons.Star
    SyncResourceType.COLLECTION_BOOKMARK,
    SyncResourceType.READING_BOOKMARK,
    SyncResourceType.READING_SESSION -> QuranIcons.BookmarkBorder
  }

internal val ReadingBookmarkSlot.type: ReadingBookmarkType
  get() = when (this) {
    ReadingBookmarkSlot.TEAL -> ReadingBookmarkType.TEAL
    ReadingBookmarkSlot.ORANGE -> ReadingBookmarkType.ORANGE
    ReadingBookmarkSlot.RED -> ReadingBookmarkType.RED
  }

@get:StringRes
private val HeldSyncItem.reasonText: Int
  get() = when (reason) {
    HeldReason.TOO_SHORT -> R.string.quran_sync_held_reason_too_short
    HeldReason.TOO_LONG -> R.string.quran_sync_held_reason_too_long
    HeldReason.INVALID_RANGE, HeldReason.INVALID_VERSE -> R.string.quran_sync_held_reason_invalid_verses
    HeldReason.INVALID_LOCATION -> R.string.quran_sync_held_reason_invalid_location
    HeldReason.RESERVED_NAME -> R.string.quran_sync_held_reason_reserved_name
    HeldReason.READ_ONLY -> R.string.quran_sync_held_reason_read_only
    HeldReason.INVALID_SLOT, HeldReason.TOO_MUCH_METADATA, HeldReason.REJECTED_BY_SERVER -> when (type) {
      SyncResourceType.NOTE -> R.string.quran_sync_held_reason_not_accepted_note
      SyncResourceType.COLLECTION -> R.string.quran_sync_held_reason_not_accepted_collection
      SyncResourceType.COLLECTION_BOOKMARK,
      SyncResourceType.READING_BOOKMARK -> R.string.quran_sync_held_reason_not_accepted_bookmark
      SyncResourceType.READING_SESSION -> R.string.quran_sync_held_reason_not_accepted
    }
    HeldReason.WAITING_ON_PARENT -> R.string.quran_sync_held_reason_waiting_on_collection
    HeldReason.WAITING_ON_OTHER_ITEM -> R.string.quran_sync_held_reason_waiting_on_other
    HeldReason.NOT_YET_LINKED -> R.string.quran_sync_held_reason_not_linked
  }

/** "Today, 10:42" or "Yesterday, 22:18", in the locale's digits. */
@Composable
private fun syncRunTime(at: Instant): String {
  val locale = LocalConfiguration.current.locales[0]
  val formatter = remember(locale) {
    DateFormat.getDateTimeInstance(DateFormat.RELATIVE_MEDIUM, DateFormat.SHORT, ULocale.forLocale(locale))
      .apply { setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE) }
  }
  return formatter.format(Date(at.toEpochMilliseconds()))
}

@Composable
private fun syncRunTrigger(trigger: SyncRunTrigger): String = when (trigger) {
  SyncRunTrigger.MANUAL -> stringResource(R.string.quran_sync_run_trigger_manual)
  SyncRunTrigger.LOCAL_CHANGE -> stringResource(R.string.quran_sync_run_trigger_local_change)
  SyncRunTrigger.FOREGROUND -> stringResource(R.string.quran_sync_run_trigger_foreground)
  // mobile-sync syncs every 30 minutes
  SyncRunTrigger.PERIODIC -> stringResource(
    R.string.quran_sync_run_trigger_periodic,
    rememberNumberFormat().format(30)
  )
  SyncRunTrigger.APP_START -> stringResource(R.string.quran_sync_run_trigger_app_start)
  SyncRunTrigger.SIGN_IN -> stringResource(R.string.quran_sync_run_trigger_sign_in)
}

@Composable
private fun syncRunDetail(run: SyncRun): String {
  val numbers = rememberNumberFormat()
  val parts = buildList {
    if (run.result == SyncRunResult.FAILED) {
      add(stringResource((run.error?.kind ?: SyncErrorKind.UNKNOWN).text))
      if (run.attempts > 1) {
        add(pluralStringResource(R.plurals.quran_sync_run_attempts, run.attempts, run.attempts))
      }
    } else {
      if (run.pushed > 0) add(stringResource(R.string.quran_sync_run_sent, numbers.format(run.pushed)))
      if (run.pulled > 0) add(stringResource(R.string.quran_sync_run_received, numbers.format(run.pulled)))
      if (run.held > 0) add(stringResource(R.string.quran_sync_run_held, numbers.format(run.held)))
      when (run.result) {
        SyncRunResult.PULLED_ONLY -> add(stringResource(R.string.quran_sync_run_sending_paused))
        SyncRunResult.CANCELLED -> add(stringResource(R.string.quran_sync_run_stopped))
        else -> if (isEmpty()) add(stringResource(R.string.quran_sync_run_up_to_date))
      }
    }
  }
  return parts.joinToString(stringResource(R.string.quran_sync_separator))
}
