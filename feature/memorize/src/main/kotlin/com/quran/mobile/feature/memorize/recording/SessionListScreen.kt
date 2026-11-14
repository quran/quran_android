package com.quran.mobile.feature.memorize.recording

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.modifier.autoMirror
import com.quran.mobile.feature.memorize.R
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.mobile.feature.memorize.ui.MemorizeIcons
import androidx.appcompat.R as AppCompatR

/** A recorded session as the list shows it. */
internal data class SessionRow(
  val id: String,
  val mode: MemorizeState.Mode,
  /** E.g. "Al-Mulk 1–15". */
  val title: String,
  /** E.g. "Memorization · 21:40 · 4:12". */
  val details: String,
  /** The section it is in, e.g. "Today". */
  val day: String,
  val contributed: Boolean,
)

/** The user's recorded sessions, newest first, by day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionListScreen(
  rows: List<SessionRow>?,
  /** E.g. "12 sessions · 84 MB · Record and contribute is on". */
  summary: String?,
  snackbar: SnackbarHostState,
  onOpen: (String) -> Unit,
  onDelete: (String) -> Unit,
  onExportAll: () -> Unit,
  onDeleteAll: () -> Unit,
  onBack: () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  var confirmDeleteAll by remember { mutableStateOf(false) }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.memorize_sessions_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              QuranIcons.ArrowBack,
              contentDescription = stringResource(AppCompatR.string.abc_action_bar_up_description),
              modifier = Modifier.autoMirror(),
            )
          }
        },
        actions = {
          if (!rows.isNullOrEmpty()) {
            Box {
              IconButton(onClick = { menu = true }) {
                Icon(MemorizeIcons.MoreVert, contentDescription = stringResource(R.string.memorize_sessions_more))
              }
              DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.memorize_recordings_export_all)) },
                  leadingIcon = { Icon(MemorizeIcons.Export, contentDescription = null) },
                  onClick = {
                    menu = false
                    onExportAll()
                  },
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.memorize_recordings_delete_all)) },
                  leadingIcon = { Icon(MemorizeIcons.Delete, contentDescription = null) },
                  onClick = {
                    menu = false
                    confirmDeleteAll = true
                  },
                )
              }
            }
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { padding ->
    when {
      rows == null -> Unit
      rows.isEmpty() -> EmptySessions(Modifier.padding(padding))
      else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding()), modifier = Modifier.fillMaxSize()) {
        if (summary != null) {
          item(key = "summary") {
            Text(
              text = summary,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
          }
        }
        rows.groupBy { it.day }.forEach { (day, dayRows) ->
          item(key = "day:$day") {
            Text(
              text = day,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
                .semantics { heading() },
            )
          }
          items(dayRows, key = { it.id }) { row ->
            SwipeToDelete(onDelete = { onDelete(row.id) }) {
              SessionListRow(row, onClick = { onOpen(row.id) })
            }
          }
        }
        item(key = "end") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
      }
    }
  }
  if (confirmDeleteAll) {
    AlertDialog(
      onDismissRequest = { confirmDeleteAll = false },
      text = { Text(stringResource(R.string.memorize_sessions_delete_all_confirm)) },
      confirmButton = {
        TextButton(onClick = {
          confirmDeleteAll = false
          onDeleteAll()
        }) { Text(stringResource(R.string.memorize_recordings_delete_all)) }
      },
      dismissButton = {
        TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(android.R.string.cancel)) }
      },
    )
  }
}

@Composable
private fun SessionListRow(row: SessionRow, onClick: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .clickable(role = Role.Button, onClick = onClick)
      .heightIn(min = 72.dp)
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Icon(
      modeIcon(row.mode),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(24.dp),
    )
    Spacer(Modifier.width(16.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(row.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
      Text(
        row.details,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
    if (row.contributed) {
      Spacer(Modifier.width(12.dp))
      Text(
        stringResource(R.string.memorize_sessions_contributed),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

/** Swiping a row away deletes it (with Undo); the session window has a delete button too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
  val state = rememberSwipeToDismissBoxState()
  val delete by rememberUpdatedState(onDelete)
  LaunchedEffect(state.currentValue) {
    if (state.currentValue == SwipeToDismissBoxValue.EndToStart) delete()
  }
  SwipeToDismissBox(
    state = state,
    enableDismissFromStartToEnd = false,
    backgroundContent = {
      Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.errorContainer)
          .padding(horizontal = 24.dp),
      ) {
        Icon(MemorizeIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
      }
    },
  ) { content() }
}

/** Says how sessions get here. */
@Composable
private fun EmptySessions(modifier: Modifier = Modifier) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 28.dp),
  ) {
    Icon(
      MemorizeIcons.GraphicEq,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.outline,
      modifier = Modifier.size(48.dp),
    )
    Text(
      text = stringResource(R.string.memorize_sessions_empty_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 16.dp),
    )
    Text(
      text = stringResource(R.string.memorize_sessions_empty_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}

internal fun modeIcon(mode: MemorizeState.Mode): ImageVector = when (mode) {
  MemorizeState.Mode.MEMORIZE -> MemorizeIcons.VisibilityOff
  MemorizeState.Mode.RECITE -> QuranIcons.MenuBook
  MemorizeState.Mode.SALAH -> MemorizeIcons.Mosque
}
