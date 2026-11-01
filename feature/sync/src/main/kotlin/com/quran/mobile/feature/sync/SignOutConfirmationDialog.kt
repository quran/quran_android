package com.quran.mobile.feature.sync

import android.content.res.Configuration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.sync.state.SignOutWarning
import com.quran.mobile.common.ui.core.R as UiCoreR

@Composable
internal fun SignOutConfirmationDialog(
  warning: SignOutWarning,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  onReview: () -> Unit
) {
  val offersReview = warning != SignOutWarning.Synced
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.quran_sync_sign_out_confirmation_title)) },
    text = { Text(signOutWarningMessage(warning)) },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
      ) {
        Text(stringResource(R.string.quran_sync_sign_out))
      }
    },
    dismissButton = {
      TextButton(onClick = if (offersReview) onReview else onDismiss) {
        Text(stringResource(if (offersReview) R.string.quran_sync_sign_out_review else UiCoreR.string.cancel))
      }
    }
  )
}

@Composable
private fun signOutWarningMessage(warning: SignOutWarning): String = when (warning) {
  SignOutWarning.Unknown -> stringResource(R.string.quran_sync_sign_out_unknown)
  SignOutWarning.Synced -> stringResource(R.string.quran_sync_sign_out_synced)
  is SignOutWarning.Unsynced -> {
    val numbers = rememberNumberFormat()
    buildList {
      if (warning.pending > 0) {
        add(pluralStringResource(R.plurals.quran_sync_sign_out_pending, warning.pending, numbers.format(warning.pending)))
      }
      if (warning.toFix > 0) {
        if (warning.waiting > 0) {
          add(stringResource(
            R.string.quran_sync_sign_out_held_waiting,
            pluralStringResource(R.plurals.quran_sync_sign_out_held_parent, warning.toFix, numbers.format(warning.toFix)),
            pluralStringResource(R.plurals.quran_sync_sign_out_waiting, warning.waiting, numbers.format(warning.waiting))
          ))
        } else {
          add(pluralStringResource(R.plurals.quran_sync_sign_out_held, warning.toFix, numbers.format(warning.toFix)))
        }
      }
      add(stringResource(R.string.quran_sync_sign_out_lost))
    }.joinToString("\n")
  }
}

@Preview
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview("Arabic", locale = "ar")
@Composable
private fun SignOutUnsyncedPreview() {
  QuranTheme {
    SignOutConfirmationDialog(SignOutWarning.Unsynced(3, 1, 2), onConfirm = {}, onDismiss = {}, onReview = {})
  }
}

@Preview
@Composable
private fun SignOutSyncedPreview() {
  QuranTheme {
    SignOutConfirmationDialog(SignOutWarning.Synced, onConfirm = {}, onDismiss = {}, onReview = {})
  }
}

@Preview
@Composable
private fun SignOutUnknownPreview() {
  QuranTheme {
    SignOutConfirmationDialog(SignOutWarning.Unknown, onConfirm = {}, onDismiss = {}, onReview = {})
  }
}
