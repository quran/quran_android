package com.quran.mobile.feature.sync

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.quran.mobile.feature.sync.state.SyncActivityState
import com.quran.mobile.feature.sync.state.SyncActivityState.Status
import com.quran.shared.pipeline.SyncErrorKind
import kotlin.time.Instant

@Immutable
internal data class SyncStatusSummary(
  val icon: Icon?,
  val tone: Tone,
  val title: String,
  val detail: String,
  val button: Button,
  val hasHeldItems: Boolean
) {
  enum class Icon { SYNCED, SYNCING, FAILED, HELD }

  enum class Tone { NORMAL, FAILURE, CAUTION }

  enum class Button(@StringRes val title: Int, val isEnabled: Boolean) {
    SYNC_NOW(R.string.quran_sync_action_sync_now, true),
    SYNCING(R.string.quran_sync_action_syncing, false),
    TRY_AGAIN(R.string.quran_sync_action_try_again, true)
  }
}

@Composable
internal fun cardSyncStatusSummary(activity: SyncActivityState, now: Instant): SyncStatusSummary {
  val heldCount = activity.blockingItems.size
  val held = if (heldCount > 0) {
    pluralStringResource(R.plurals.quran_sync_held_count, heldCount, heldCount)
  } else {
    null
  }

  return when (val status = activity.status) {
    Status.Syncing -> SyncStatusSummary(
      icon = SyncStatusSummary.Icon.SYNCING,
      tone = SyncStatusSummary.Tone.NORMAL,
      title = stringResource(R.string.quran_sync_status_syncing),
      detail = stringResource(R.string.quran_sync_status_items_short),
      button = SyncStatusSummary.Button.SYNCING,
      hasHeldItems = held != null
    )
    is Status.Retrying -> failedSummary(status.reason, held != null)
    is Status.Failed -> failedSummary(status.reason, held != null)
    is Status.Synced -> {
      val ago = syncedTimeAgo(status.at, now).nonBreaking()
      if (held == null) {
        SyncStatusSummary(
          icon = SyncStatusSummary.Icon.SYNCED,
          tone = SyncStatusSummary.Tone.NORMAL,
          title = stringResource(R.string.quran_sync_status_synced),
          detail = stringResource(R.string.quran_sync_status_synced_card, ago),
          button = SyncStatusSummary.Button.SYNC_NOW,
          hasHeldItems = false
        )
      } else {
        SyncStatusSummary(
          icon = SyncStatusSummary.Icon.HELD,
          tone = SyncStatusSummary.Tone.CAUTION,
          title = held,
          detail = stringResource(R.string.quran_sync_status_everything_else_synced, ago),
          button = SyncStatusSummary.Button.SYNC_NOW,
          hasHeldItems = true
        )
      }
    }
    Status.NeverSynced -> SyncStatusSummary(
      icon = if (held == null) null else SyncStatusSummary.Icon.HELD,
      tone = if (held == null) SyncStatusSummary.Tone.NORMAL else SyncStatusSummary.Tone.CAUTION,
      title = held ?: stringResource(R.string.quran_sync_status_never_synced),
      detail = if (held == null) {
        stringResource(R.string.quran_sync_status_all_items)
      } else {
        stringResource(R.string.quran_sync_status_never_synced)
      },
      button = SyncStatusSummary.Button.SYNC_NOW,
      hasHeldItems = held != null
    )
  }
}

@Composable
private fun failedSummary(reason: SyncErrorKind?, hasHeldItems: Boolean): SyncStatusSummary {
  return SyncStatusSummary(
    icon = SyncStatusSummary.Icon.FAILED,
    tone = SyncStatusSummary.Tone.FAILURE,
    title = stringResource(R.string.quran_sync_status_failed),
    detail = if (reason == null) {
      stringResource(R.string.quran_sync_status_failed_kept_paused)
    } else {
      stringResource(R.string.quran_sync_status_failed_kept, stringResource(reason.text))
    },
    button = SyncStatusSummary.Button.TRY_AGAIN,
    hasHeldItems = hasHeldItems
  )
}

@get:StringRes
private val SyncErrorKind.text: Int
  get() = when (this) {
    SyncErrorKind.NETWORK -> R.string.quran_sync_error_network
    SyncErrorKind.AUTHENTICATION -> R.string.quran_sync_error_authentication
    SyncErrorKind.SERVER -> R.string.quran_sync_error_server
    SyncErrorKind.REJECTED -> R.string.quran_sync_error_rejected
    SyncErrorKind.UNKNOWN -> R.string.quran_sync_error_unknown
  }

// keeps "2 minutes ago" on one line beside the button
private fun String.nonBreaking(): String = replace(' ', ' ')
