package com.quran.mobile.feature.progress.settings

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraPreferencesProvider
import com.quran.mobile.feature.progress.ProgressActivity
import com.quran.mobile.feature.progress.R
import com.quran.mobile.feature.progress.data.ProgressRepository
import com.quran.mobile.feature.progress.reminder.ReminderNotifier
import com.quran.mobile.feature.progress.reminder.ReminderScheduler
import com.quran.mobile.feature.progress.ui.timeOfDay
import com.quran.mobile.progress.ProgressPreferences
import com.quran.mobile.progress.history.ProgressHistory
import com.quran.mobile.progress.reminder.Reminder
import com.quran.mobile.progress.reminder.ReminderDecision
import com.quran.mobile.progress.time.ProgressClock
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The "Progress" section of the app's settings: the reading bookmarks, the daily reminder, the session log. */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
@Inject
class ProgressPreferencesProvider(
  private val preferences: ProgressPreferences,
  private val reminderScheduler: ReminderScheduler,
  private val history: ProgressHistory,
  private val debugSeed: DebugSeed,
  private val repository: ProgressRepository,
  private val notifier: ReminderNotifier,
  private val clock: ProgressClock,
) : ExtraPreferencesProvider {

  override val order: Int = 20

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val store = object : PreferenceDataStore() {
    override fun getBoolean(key: String, defValue: Boolean): Boolean = when (key) {
      KEY_REMINDER -> preferences.reminder
      KEY_SESSION_LOG -> preferences.sessionLog
      else -> defValue
    }

    override fun putBoolean(key: String, value: Boolean) {
      when (key) {
        KEY_REMINDER -> {
          preferences.reminder = value
          reminderScheduler.reschedule()
        }
        KEY_SESSION_LOG -> preferences.sessionLog = value
      }
    }
  }

  override fun addPreferences(root: PreferenceGroup) {
    val context = root.context
    val category = PreferenceCategory(context).apply {
      key = KEY_CATEGORY
      title = context.getString(R.string.progress_settings_category)
      isIconSpaceReserved = false
    }
    root.addPreference(category)
    category.addPreference(Preference(context).apply {
      setup(KEY_BOOKMARKS, R.string.progress_settings_bookmarks, R.string.progress_settings_bookmarks_summary)
    })
    category.addPreference(SwitchPreferenceCompat(context).apply {
      setup(KEY_REMINDER, R.string.progress_settings_reminder, R.string.progress_settings_reminder_summary)
    })
    category.addPreference(Preference(context).apply {
      setup(KEY_REMINDER_TIME, R.string.progress_settings_reminder_time, null)
      summary = context.timeOfDay(preferences.reminderMinute)
    })
    category.addPreference(SwitchPreferenceCompat(context).apply {
      setup(KEY_SESSION_LOG, R.string.progress_settings_session_log, R.string.progress_settings_session_log_summary)
    })
    category.addPreference(Preference(context).apply {
      setup(KEY_CLEAR, R.string.progress_settings_clear, R.string.progress_settings_clear_summary)
    })
    if (preferences.debuggable) {
      category.addPreference(Preference(context).apply {
        setup(KEY_DEBUG_CLOCK, R.string.progress_settings_debug_clock, null)
        summary = context.getString(R.string.progress_settings_debug_clock_summary, preferences.debugDayOffset)
      })
      category.addPreference(Preference(context).apply {
        setup(KEY_DEBUG_CLOCK_RESET, R.string.progress_settings_debug_clock_reset, null)
      })
      category.addPreference(Preference(context).apply {
        setup(KEY_DEBUG_SEED, R.string.progress_settings_debug_seed, null)
      })
      category.addPreference(Preference(context).apply {
        setup(KEY_DEBUG_REMINDER, R.string.progress_settings_debug_reminder, null)
      })
    }
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    return when (preference.key) {
      KEY_BOOKMARKS -> {
        context.startActivity(ProgressActivity.editBookmarksIntent(context))
        true
      }
      KEY_REMINDER_TIME -> {
        val minute = preferences.reminderMinute
        TimePickerDialog(
          context,
          { _, hour, minuteOfHour ->
            preferences.reminderMinute = hour * 60 + minuteOfHour
            preference.summary = context.timeOfDay(preferences.reminderMinute)
            reminderScheduler.reschedule()
          },
          minute / 60,
          minute % 60,
          DateFormat.is24HourFormat(context),
        ).show()
        true
      }
      KEY_CLEAR -> {
        AlertDialog.Builder(context)
          .setTitle(R.string.progress_settings_clear)
          .setMessage(R.string.progress_settings_clear_message)
          .setPositiveButton(R.string.progress_settings_clear_confirm) { _, _ -> scope.launch { history.clear() } }
          .setNegativeButton(android.R.string.cancel, null)
          .show()
        true
      }
      KEY_DEBUG_CLOCK -> {
        preferences.debugDayOffset += 1
        preference.summary = context.getString(R.string.progress_settings_debug_clock_summary, preferences.debugDayOffset)
        reminderScheduler.reschedule()
        true
      }
      KEY_DEBUG_CLOCK_RESET -> {
        preferences.debugDayOffset = 0
        preference.parent?.findPreference<Preference>(KEY_DEBUG_CLOCK)?.summary =
          context.getString(R.string.progress_settings_debug_clock_summary, 0)
        reminderScheduler.reschedule()
        true
      }
      KEY_DEBUG_SEED -> {
        scope.launch { debugSeed.seed() }
        true
      }
      KEY_DEBUG_REMINDER -> {
        // today's reminder as it would come at its time (whatever the time now, and even if it came)
        scope.launch {
          val reminder = ReminderDecision.decide(
            enabled = true,
            nowMs = clock.nowMs(),
            zone = clock.zone(),
            reminderMinute = 0,
            lastReminderDay = -1,
            readerOpen = false,
            bookmarks = repository.snapshot().bookmarks,
          )
          if (reminder == Reminder.None || !notifier.show(reminder)) {
            Toast.makeText(context, R.string.progress_settings_debug_reminder_none, Toast.LENGTH_SHORT).show()
          }
        }
        true
      }
      else -> false
    }
  }

  private fun Preference.setup(key: String, title: Int, summary: Int?) {
    this.key = key
    this.preferenceDataStore = store
    this.isIconSpaceReserved = false
    this.isPersistent = true
    setTitle(title)
    if (summary != null) setSummary(summary)
  }

  private companion object {
    const val KEY_CATEGORY = "progress_category"
    const val KEY_BOOKMARKS = "progress_bookmarks"
    const val KEY_REMINDER = "progress_reminder"
    const val KEY_REMINDER_TIME = "progress_reminder_time"
    const val KEY_SESSION_LOG = "progress_session_log"
    const val KEY_CLEAR = "progress_clear"
    const val KEY_DEBUG_CLOCK = "progress_debug_clock"
    const val KEY_DEBUG_CLOCK_RESET = "progress_debug_clock_reset"
    const val KEY_DEBUG_SEED = "progress_debug_seed"
    const val KEY_DEBUG_REMINDER = "progress_debug_reminder"
  }
}
