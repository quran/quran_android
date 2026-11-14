package com.quran.mobile.feature.memorize.recording

import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.memorize.R
import com.quran.mobile.feature.memorize.di.MemorizeComponentInterface
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.recitation.pages.PageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Recording sessions: the user's recorded sessions, by day. A session opens in a window that plays
 * it back on its own pages (without going to them in the reader); sessions can be exported (e.g.
 * for training) or deleted.
 */
class RecordingsActivity : AppCompatActivity() {

  private val component by lazy {
    (application as QuranApplicationComponentProvider).provideQuranApplicationComponent() as MemorizeComponentInterface
  }
  private val store by lazy { component.recordingStore() }
  private val player by lazy { component.recordingPlayer() }
  private val quranInfo by lazy { component.quranInfo() }
  private var renderer: PageRenderer? = null
  private val snackbar = SnackbarHostState()
  // deleted, but not yet for good: the user may still undo
  private val pendingDeletes = mutableStateListOf<String>()

  // the recordings being exported while the user picks where to
  private var exporting: List<Recording> = emptyList()
  private val exporter = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
    if (uri != null) export(uri, exporting)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val renderer = component.pagePreviews().open(this).also { renderer = it }
    setContent {
      val changes by store.changes.collectAsState()
      var sessions by remember { mutableStateOf<List<RecordingSession>?>(null) }
      LaunchedEffect(changes) { sessions = withContext(Dispatchers.IO) { store.sessions() } }
      var openId by rememberSaveable { mutableStateOf<String?>(null) }
      val scope = rememberCoroutineScope()
      val shown = sessions?.filter { it.id !in pendingDeletes }

      fun delete(session: RecordingSession) {
        if (player.playback.value?.recording?.session == session.id) player.stop()
        pendingDeletes += session.id
        openId = null
        scope.launch {
          val result = snackbar.showSnackbar(
            message = getString(R.string.memorize_sessions_deleted),
            actionLabel = getString(R.string.memorize_sessions_undo),
            duration = SnackbarDuration.Long,
          )
          if (result == SnackbarResult.ActionPerformed) pendingDeletes -= session.id else commitDelete(session.id)
        }
      }

      BackHandler(enabled = openId != null) {
        player.pause()
        openId = null
      }

      QuranTheme {
        AnimatedContent(
          targetState = openId?.let { id -> shown?.firstOrNull { it.id == id } },
          transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
          contentKey = { it?.id },
          label = "sessions",
        ) { session ->
          if (session == null) {
            SessionListScreen(
              rows = shown?.map { row(it) },
              summary = shown?.takeIf { it.isNotEmpty() }?.let { summary(it) },
              snackbar = snackbar,
              onOpen = { openId = it },
              onDelete = { id -> shown?.firstOrNull { it.id == id }?.let { delete(it) } },
              onExportAll = { startExport(shown.orEmpty().flatMap { it.recordings }) },
              onDeleteAll = {
                player.stop()
                pendingDeletes.clear()
                lifecycleScope.launch(Dispatchers.IO) { store.deleteAll() }
              },
              onBack = ::finish,
            )
          } else {
            SessionWindow(session, renderer, onBack = {
              player.pause()
              openId = null
            }, onDelete = { delete(session) })
          }
        }
      }
    }
  }

  @androidx.compose.runtime.Composable
  private fun SessionWindow(session: RecordingSession, renderer: PageRenderer, onBack: () -> Unit, onDelete: () -> Unit) {
    // the whole session as one recording (all its suras in a row)
    var recording by remember(session.id) { mutableStateOf<Recording?>(null) }
    LaunchedEffect(session.id) { recording = withContext(Dispatchers.IO) { store.session(session.id) } }
    val playback by player.playback.collectAsState()
    val loaded = recording
    val pageOf = remember(session.id) { { word: Recording.Word -> quranInfo.getPageFromSuraAyah(word.id.sura, word.id.ayah) } }
    val pages = remember(loaded) {
      val words = loaded?.words.orEmpty()
      if (words.isEmpty()) {
        listOf(quranInfo.getPageFromSuraAyah(session.start.sura, session.start.ayah))
      } else {
        val onPages = words.map(pageOf)
        (onPages.min()..onPages.max()).toList()
      }
    }
    SessionScreen(
      title = RecordingExport.label(this, session.start, session.end),
      subtitle = listOf(
        modeName(session.mode),
        DateUtils.formatDateTime(this, session.startedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME),
      ).joinToString(" · "),
      recording = loaded,
      counts = SessionCounts(session.recited, session.hinted, session.missed, session.mistakes),
      pages = pages,
      pageOf = pageOf,
      playback = playback?.takeIf { it.recording.id == loaded?.id },
      renderer = renderer,
      onBack = onBack,
      onExport = { startExport(session.recordings) },
      onDelete = onDelete,
      onPlay = { loaded?.let { play(it) } },
      onPause = player::pause,
      onSeek = { position -> loaded?.let { player.seekTo(it, position) } },
      onSkip = player::skip,
    )
  }

  /** Plays [recording]; a session listening now is paused first (the microphone mustn't hear it). */
  private fun play(recording: Recording) {
    val controller = component.memorizeSessionController()
    if (controller.state.value?.status == MemorizeState.Status.LISTENING) controller.pauseOrResume()
    player.play(recording)
  }

  override fun onStop() {
    super.onStop()
    player.pause()
    // leaving: what was deleted is deleted
    pendingDeletes.toList().forEach { commitDelete(it) }
    snackbar.currentSnackbarData?.dismiss()
  }

  override fun onDestroy() {
    renderer?.close()
    renderer = null
    super.onDestroy()
  }

  private fun commitDelete(session: String) {
    pendingDeletes -= session
    lifecycleScope.launch(Dispatchers.IO + NonCancellable) { store.deleteSession(session) }
  }

  private fun row(session: RecordingSession) = SessionRow(
    id = session.id,
    mode = session.mode,
    title = RecordingExport.label(this, session.start, session.end),
    details = listOf(
      modeName(session.mode),
      DateUtils.formatDateTime(this, session.startedAt, DateUtils.FORMAT_SHOW_TIME),
      DateUtils.formatElapsedTime(session.durationMs / 1000),
    ).joinToString(" · "),
    day = day(session.startedAt),
    contributed = session.contribute,
  )

  /** E.g. "12 sessions · 84 MB · Record and contribute is on". */
  private fun summary(sessions: List<RecordingSession>): String = listOfNotNull(
    resources.getQuantityString(R.plurals.memorize_sessions_count, sessions.size, sessions.size),
    Formatter.formatShortFileSize(this, sessions.sumOf { it.sizeBytes }),
    getString(R.string.memorize_sessions_contribute_on).takeIf { component.memorizePreferences().contribute },
  ).joinToString(" · ")

  private fun modeName(mode: MemorizeState.Mode): String = getString(
    when (mode) {
      MemorizeState.Mode.MEMORIZE -> R.string.memorize_mode_memorize_title
      MemorizeState.Mode.RECITE -> R.string.memorize_mode_recite_title
      MemorizeState.Mode.SALAH -> R.string.memorize_mode_salah_title
    }
  )

  /** "Today", "Yesterday", or the date. */
  private fun day(time: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    val day = startOfDay(time)
    return when {
      day == today -> getString(R.string.memorize_sessions_today)
      today - day in 1..DAY_MS + HOUR_MS -> getString(R.string.memorize_sessions_yesterday)
      else -> {
        val thisYear = Calendar.getInstance().get(Calendar.YEAR) == Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.YEAR)
        val flags = DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
          (if (thisYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR)
        DateUtils.formatDateTime(this, time, flags)
      }
    }
  }

  private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
  }.timeInMillis

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

  private companion object {
    const val HOUR_MS = 60 * 60 * 1000L
    // a day can be 23 or 25 hours long when the clocks change
    const val DAY_MS = 24 * HOUR_MS
  }
}
