package com.quran.mobile.feature.progress.bookmarks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.quran.data.dao.ReadingBookmarkSlotSettings
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.model.bookmark.ReadingBookmarkPurpose
import com.quran.data.model.bookmark.ReadingBookmarkSlotSetting
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.mobile.feature.progress.R
import com.quran.mobile.feature.progress.ui.ProgressIcons
import com.quran.mobile.feature.progress.ui.bookmarkColor
import com.quran.mobile.feature.progress.ui.purposeName
import kotlinx.coroutines.launch
import androidx.appcompat.R as AppCompatR

/** Days a reading cycle may be set to finish in (0: no deadline). */
private val TargetDays = listOf(0, 7, 10, 15, 20, 30, 40, 60)

/**
 * The reading bookmarks: the three synced ones, and five more that can be turned on and stay on this
 * device. Each can be named, and set to what it's for; a reading bookmark also to the days a cycle
 * should take.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkEditor(
  slotSettings: ReadingBookmarkSlotSettings,
  readingBookmarksDao: ReadingBookmarksDao,
  onBack: () -> Unit,
) {
  val settings by remember { slotSettings.settingsFlow() }.collectAsState(null)
  val bookmarks by remember { readingBookmarksDao.readingBookmarksFlow() }.collectAsState(null)
  val names = remember(bookmarks) { bookmarks.orEmpty().associate { it.slot to it.name } }
  val drafts = remember { mutableStateMapOf<ReadingBookmarkType, String>() }
  val scope = rememberCoroutineScope()

  fun save(slot: ReadingBookmarkType) {
    val draft = drafts.remove(slot) ?: return
    val name = draft.trim().ifBlank { null }
    if (name != names[slot]) scope.launch { readingBookmarksDao.renameReadingBookmark(slot, name) }
  }

  fun back() {
    drafts.keys.toList().forEach(::save)
    onBack()
  }
  BackHandler(onBack = ::back)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.progress_editor_title)) },
        navigationIcon = {
          IconButton(onClick = ::back) {
            Icon(
              QuranIcons.ArrowBack,
              contentDescription = stringResource(AppCompatR.string.abc_action_bar_up_description),
            )
          }
        },
      )
    },
  ) { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
      item {
        Text(
          stringResource(R.string.progress_editor_explained),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )
      }
      items(settings.orEmpty(), key = { it.slot.name }) { setting ->
        SlotRow(
          setting = setting,
          name = drafts[setting.slot] ?: names[setting.slot].orEmpty(),
          onName = { drafts[setting.slot] = it },
          onNameDone = { save(setting.slot) },
          onEnabled = { enabled -> scope.launch { slotSettings.setEnabled(setting.slot, enabled) } },
          onPurpose = { purpose -> scope.launch { slotSettings.setPurpose(setting.slot, purpose) } },
          onDays = { days -> scope.launch { slotSettings.setTargetDays(setting.slot, days) } },
          modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun SlotRow(
  setting: ReadingBookmarkSlotSetting,
  name: String,
  onName: (String) -> Unit,
  onNameDone: () -> Unit,
  onEnabled: (Boolean) -> Unit,
  onPurpose: (ReadingBookmarkPurpose) -> Unit,
  onDays: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val slot = setting.slot
  Column(modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(ProgressIcons.Bookmark, contentDescription = null, tint = bookmarkColor(slot), modifier = Modifier.size(24.dp))
      Column(Modifier.weight(1f).padding(start = 16.dp)) {
        Text(
          ReadingBookmarkSlots.displayName(slot, name.takeIf { it.isNotBlank() }),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          stringResource(if (slot.synced) R.string.progress_editor_synced else R.string.progress_editor_device),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (!slot.synced) Switch(checked = setting.enabled, onCheckedChange = onEnabled)
    }
    if (setting.enabled) {
      OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(stringResource(R.string.progress_editor_name)) },
        placeholder = { Text(stringResource(ReadingBookmarkSlots[slot].nameResourceId)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onNameDone() }),
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp)
          .onFocusChanged { if (!it.isFocused) onNameDone() },
      )
      Text(
        stringResource(R.string.progress_editor_for),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReadingBookmarkPurpose.entries.forEach { purpose ->
          FilterChip(selected = setting.purpose == purpose, onClick = { onPurpose(purpose) }, label = { Text(purposeName(purpose)) })
        }
      }
      if (setting.purpose == ReadingBookmarkPurpose.READING) {
        Text(
          stringResource(R.string.progress_editor_days),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TargetDays.forEach { days ->
            FilterChip(
              selected = setting.targetDays == days,
              onClick = { onDays(days) },
              label = {
                Text(if (days == 0) stringResource(R.string.progress_no_deadline) else pluralStringResource(R.plurals.progress_days, days, days))
              },
            )
          }
        }
      }
    }
  }
  HorizontalDivider(modifier, color = MaterialTheme.colorScheme.outlineVariant)
}
