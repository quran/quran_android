package com.quran.mobile.feature.sync

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.mobile.feature.sync.state.HeldItemRowState.Edit
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ReadingBookmarkRenameDialog(
  bookmark: Edit.ReadingBookmark,
  onRename: suspend (String?) -> Unit,
  onDismiss: () -> Unit
) {
  var name by rememberSaveable(bookmark.id) { mutableStateOf(bookmark.name) }
  var saving by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  val saveError = stringResource(R.string.quran_sync_error_unknown)
  val dismiss = { if (!saving) onDismiss() }
  AlertDialog(
    onDismissRequest = dismiss,
    title = { Text(stringResource(R.string.quran_sync_rename)) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = {
          name = it
          error = null
        },
        enabled = !saving,
        singleLine = true,
        label = {
          Text(
            stringResource(
              R.string.quran_sync_bookmark_name,
              ReadingBookmarkSlots.displayName(bookmark.slot.type, null)
            )
          )
        },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } }
      )
    },
    confirmButton = {
      TextButton(
        enabled = !saving,
        onClick = {
          saving = true
          scope.launch {
            try {
              onRename(name.trim().ifEmpty { null })
              onDismiss()
            } catch (exception: CancellationException) {
              throw exception
            } catch (_: Exception) {
              error = saveError
            } finally {
              saving = false
            }
          }
        }
      ) {
        Text(stringResource(R.string.quran_sync_rename))
      }
    },
    dismissButton = {
      TextButton(onClick = dismiss, enabled = !saving) {
        Text(stringResource(android.R.string.cancel))
      }
    }
  )
}

@Preview
@Composable
private fun ReadingBookmarkRenamePreview() {
  QuranTheme {
    ReadingBookmarkRenameDialog(
      bookmark = Edit.ReadingBookmark("bookmark", ReadingBookmarkSlot.TEAL, "My reading"),
      onRename = {},
      onDismiss = {}
    )
  }
}
