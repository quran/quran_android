package com.quran.mobile.feature.memorize.recording

import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.memorize.R
import com.quran.mobile.feature.memorize.di.MemorizeComponentInterface
import com.quran.mobile.feature.memorize.ui.MemorizeIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The user's recordings of their own recitation: listen, export (e.g. for training) or delete. */
class RecordingsActivity : ComponentActivity() {

  private val component by lazy {
    (application as QuranApplicationComponentProvider).provideQuranApplicationComponent() as MemorizeComponentInterface
  }
  private val store by lazy { component.recordingStore() }
  private val player by lazy { component.recordingPlayer() }

  // the recordings being exported while the user picks where to
  private var exporting: List<Recording> = emptyList()
  private val exporter = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
    if (uri != null) export(uri, exporting)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val changes by store.changes.collectAsState()
      var recordings by remember { mutableStateOf<List<Recording>?>(null) }
      LaunchedEffect(changes) { recordings = withContext(Dispatchers.IO) { store.list() } }
      val playback by player.playback.collectAsState()
      QuranTheme {
        RecordingsScreen(
          recordings = recordings,
          playback = playback,
          label = { RecordingExport.label(this, it) },
          details = ::details,
          onPlay = player::play,
          onPause = player::pause,
          onExport = ::startExport,
          onDelete = { recording ->
            if (playback?.recording?.id == recording.id) player.stop()
            lifecycleScope.launch(Dispatchers.IO) { store.delete(recording.id) }
          },
          onDeleteAll = {
            player.stop()
            lifecycleScope.launch(Dispatchers.IO) { store.deleteAll() }
          },
          onBack = ::finish,
        )
      }
    }
  }

  override fun onStop() {
    super.onStop()
    player.pause()
  }

  private fun details(recording: Recording): String = listOf(
    DateUtils.formatDateTime(this, recording.startedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME),
    DateUtils.formatElapsedTime(recording.durationMs / 1000),
    Formatter.formatShortFileSize(this, recording.sizeBytes),
  ).joinToString(" · ")

  private fun startExport(recordings: List<Recording>) {
    if (recordings.isEmpty()) return
    exporting = recordings
    exporter.launch(RecordingExport.fileName(recordings))
  }

  private fun export(uri: Uri, recordings: List<Recording>) {
    lifecycleScope.launch {
      val done = withContext(Dispatchers.IO) {
        runCatching { contentResolver.openOutputStream(uri)?.use { RecordingExport.zip(recordings, it) } != null }
          .getOrDefault(false)
      }
      Toast.makeText(
        this@RecordingsActivity,
        if (done) R.string.memorize_recordings_exported else R.string.memorize_recordings_export_failed,
        Toast.LENGTH_LONG,
      ).show()
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingsScreen(
  recordings: List<Recording>?,
  playback: RecordingPlayer.Playback?,
  label: (Recording) -> String,
  details: (Recording) -> String,
  onPlay: (Recording) -> Unit,
  onPause: () -> Unit,
  onExport: (List<Recording>) -> Unit,
  onDelete: (Recording) -> Unit,
  onDeleteAll: () -> Unit,
  onBack: () -> Unit,
) {
  var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.memorize_recordings_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(QuranIcons.ArrowBack, contentDescription = null) }
        },
        actions = {
          if (!recordings.isNullOrEmpty()) {
            IconButton(onClick = { onExport(recordings) }) {
              Icon(MemorizeIcons.Export, contentDescription = stringResource(R.string.memorize_recordings_export_all))
            }
            val deleteAll = stringResource(R.string.memorize_recordings_delete_all_confirm)
            IconButton(onClick = { confirm = deleteAll to onDeleteAll }) {
              Icon(MemorizeIcons.Delete, contentDescription = stringResource(R.string.memorize_recordings_delete_all))
            }
          }
        },
      )
    },
  ) { padding ->
    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
      when {
        recordings == null -> Unit
        recordings.isEmpty() -> Text(
          text = stringResource(R.string.memorize_recordings_empty),
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.align(Alignment.Center).padding(32.dp),
        )
        else -> LazyColumn {
          items(recordings, key = { it.id }) { recording ->
            val playing = playback?.takeIf { it.recording.id == recording.id }
            val deleteOne = stringResource(R.string.memorize_recordings_delete_confirm, label(recording))
            Column {
              ListItem(
                headlineContent = { Text(label(recording)) },
                supportingContent = { Text(details(recording)) },
                trailingContent = {
                  Row {
                    IconButton(onClick = { if (playing?.isPlaying == true) onPause() else onPlay(recording) }) {
                      if (playing?.isPlaying == true) {
                        Icon(QuranIcons.Pause, contentDescription = stringResource(R.string.memorize_recordings_pause))
                      } else {
                        Icon(QuranIcons.PlayArrow, contentDescription = stringResource(R.string.memorize_recordings_play))
                      }
                    }
                    IconButton(onClick = { onExport(listOf(recording)) }) {
                      Icon(MemorizeIcons.Export, contentDescription = stringResource(R.string.memorize_recordings_export))
                    }
                    IconButton(onClick = { confirm = deleteOne to { onDelete(recording) } }) {
                      Icon(MemorizeIcons.Delete, contentDescription = stringResource(R.string.memorize_recordings_delete))
                    }
                  }
                },
              )
              if (playing != null && recording.durationMs > 0) {
                LinearProgressIndicator(
                  progress = { (playing.positionMs.toFloat() / recording.durationMs).coerceIn(0f, 1f) },
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
              }
              HorizontalDivider()
            }
          }
        }
      }
    }
  }
  confirm?.let { (message, action) ->
    AlertDialog(
      onDismissRequest = { confirm = null },
      text = { Text(message) },
      confirmButton = {
        TextButton(onClick = {
          confirm = null
          action()
        }) { Text(stringResource(R.string.memorize_recordings_delete)) }
      },
      dismissButton = {
        TextButton(onClick = { confirm = null }) { Text(stringResource(android.R.string.cancel)) }
      },
    )
  }
}
