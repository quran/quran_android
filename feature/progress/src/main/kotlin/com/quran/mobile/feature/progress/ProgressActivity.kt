package com.quran.mobile.feature.progress

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.progress.bookmarks.BookmarkEditor
import com.quran.mobile.feature.progress.data.ProgressMapper
import com.quran.mobile.feature.progress.di.ProgressComponentInterface
import com.quran.mobile.feature.progress.ui.LocalSuraNames
import com.quran.mobile.feature.progress.ui.ProgressActions
import com.quran.mobile.feature.progress.ui.ProgressScreen
import com.quran.mobile.feature.progress.ui.ProgressSection
import com.quran.mobile.feature.progress.ui.ProgressSelection
import com.quran.mobile.feature.progress.ui.ReminderView
import com.quran.mobile.feature.progress.ui.noonOf
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Progress: every reading bookmark's cycle and days, memorizing, salah and sessions. Opens on a
 * bookmark ([EXTRA_BOOKMARK]) or on the bookmark editor ([EXTRA_EDIT_BOOKMARKS]).
 */
class ProgressActivity : AppCompatActivity() {

  private val component by lazy {
    (application as QuranApplicationComponentProvider).provideQuranApplicationComponent() as ProgressComponentInterface
  }
  private var notificationsAllowed by mutableStateOf(false)

  private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    notificationsAllowed = component.reminderNotifier().allowed()
    if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
    ) {
      openNotificationSettings()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val preferences = component.progressPreferences()
    val naming = component.quranNaming()
    val initialBookmark = intent.getStringExtra(EXTRA_BOOKMARK)?.let { name ->
      ReadingBookmarkType.entries.firstOrNull { it.name == name }
    }
    val mapper = ProgressMapper(
      bookmarkName = { slot, name -> ReadingBookmarkSlots.displayName(this, slot, name) },
      suraName = { sura -> naming.getSuraName(this, sura, false) },
      dayOfMonth = { day -> Calendar.getInstance().apply { timeInMillis = noonOf(day) }.get(Calendar.DAY_OF_MONTH) },
    )
    val snapshots = component.progressRepository().snapshots()

    setContent {
      val snapshot by remember { snapshots }.collectAsState(null)
      val changes by preferences.changes.collectAsState(0)
      var selection by rememberSaveable(stateSaver = SelectionSaver) {
        mutableStateOf(ProgressSelection(bookmark = initialBookmark))
      }
      var editing by rememberSaveable { mutableStateOf(intent.getBooleanExtra(EXTRA_EDIT_BOOKMARKS, false)) }
      var confirmCycle by remember { mutableStateOf<ReadingBookmarkType?>(null) }
      val reminder = remember(changes, notificationsAllowed) {
        ReminderView(preferences.reminder, preferences.reminderMinute, notificationsAllowed)
      }
      val state = remember(snapshot, reminder) {
        snapshot?.let { mapper.map(it, reminder, offered = ReadingBookmarkType.entries.size) }
      }
      val dark = isSystemInDarkTheme()

      QuranTheme {
        CompositionLocalProvider(LocalSuraNames provides { sura -> naming.getSuraName(this, sura, false) }) {
          when {
            editing -> BookmarkEditor(
              slotSettings = component.readingBookmarkSlotSettings(),
              readingBookmarksDao = component.readingBookmarksDao(),
              onBack = {
                if (intent.getBooleanExtra(EXTRA_EDIT_BOOKMARKS, false)) finish() else editing = false
              },
            )
            state != null -> ProgressScreen(
              state = state,
              selection = selection,
              actions = ProgressActions(
                onSelection = { selection = it },
                onAddBookmark = { editing = true },
                onAllowNotifications = ::allowNotifications,
                onReminderTime = ::pickReminderTime,
                onStartCycle = { confirmCycle = it },
                onPlayRecording = { id -> component.recordedSessionLaunchers().firstOrNull()?.open(this, id) },
                onBack = ::finish,
              ),
              dark = dark,
            )
          }
          confirmCycle?.let { slot ->
            AlertDialog(
              onDismissRequest = { confirmCycle = null },
              text = { Text(getString(R.string.progress_start_cycle)) },
              confirmButton = {
                TextButton(onClick = {
                  confirmCycle = null
                  lifecycleScope.launch { component.bookmarkMoveRecorder().markCycleStart(slot.name) }
                }) { Text(getString(android.R.string.ok)) }
              },
              dismissButton = {
                TextButton(onClick = { confirmCycle = null }) { Text(getString(android.R.string.cancel)) }
              },
            )
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    notificationsAllowed = component.reminderNotifier().allowed()
  }

  private fun allowNotifications() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      openNotificationSettings()
    }
  }

  private fun openNotificationSettings() {
    val settings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    runCatching { startActivity(settings) }
  }

  private fun pickReminderTime() {
    val preferences = component.progressPreferences()
    val minute = preferences.reminderMinute
    TimePickerDialog(
      this,
      { _, hour, minuteOfHour ->
        preferences.reminderMinute = hour * 60 + minuteOfHour
        preferences.reminder = true
        component.reminderScheduler().reschedule()
        if (!component.reminderNotifier().allowed()) allowNotifications()
      },
      minute / 60,
      minute % 60,
      DateFormat.is24HourFormat(this),
    ).show()
  }

  companion object {
    const val EXTRA_BOOKMARK = "progress_bookmark"
    const val EXTRA_EDIT_BOOKMARKS = "progress_edit_bookmarks"

    /** Progress, open on [bookmark] if given. */
    fun intent(context: Context, bookmark: ReadingBookmarkType? = null): Intent =
      Intent(context, ProgressActivity::class.java).apply {
        bookmark?.let { putExtra(EXTRA_BOOKMARK, it.name) }
      }

    /** The bookmark editor (from Settings). */
    fun editBookmarksIntent(context: Context): Intent =
      Intent(context, ProgressActivity::class.java).putExtra(EXTRA_EDIT_BOOKMARKS, true)

    private val SelectionSaver = listSaver<ProgressSelection, String?>(
      save = { listOf(it.section.name, it.bookmark?.name, it.session) },
      restore = { saved ->
        ProgressSelection(
          section = ProgressSection.entries.firstOrNull { it.name == saved[0] } ?: ProgressSection.BOOKMARKS,
          bookmark = ReadingBookmarkType.entries.firstOrNull { it.name == saved[1] },
          session = saved[2],
        )
      },
    )
  }
}
