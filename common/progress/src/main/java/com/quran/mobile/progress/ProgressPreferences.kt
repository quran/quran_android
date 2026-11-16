package com.quran.mobile.progress

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.edit
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Progress settings. Kept on the device. */
@SingleIn(AppScope::class)
class ProgressPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {
  private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
  private val _changes = MutableStateFlow(0)

  /** Bumps on every change, for screens that show settings. */
  val changes: Flow<Int> = _changes.asStateFlow()

  val debuggable: Boolean
    get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

  /** The daily reminder to finish a reading cycle in time. */
  var reminder: Boolean
    get() = prefs.getBoolean(KEY_REMINDER, true)
    set(value) = put { putBoolean(KEY_REMINDER, value) }

  /** When the reminder comes, in minutes after local midnight. */
  var reminderMinute: Int
    get() = prefs.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE)
    set(value) = put { putInt(KEY_REMINDER_MINUTE, value.coerceIn(0, 24 * 60 - 1)) }

  /** The local day the reminder last came on, so it comes at most once a day. */
  var lastReminderDay: Long
    get() = prefs.getLong(KEY_LAST_REMINDER_DAY, -1L)
    set(value) = put { putLong(KEY_LAST_REMINDER_DAY, value) }

  /** Whether Memorize, Recitation and Salah sessions keep their results here. */
  var sessionLog: Boolean
    get() = prefs.getBoolean(KEY_SESSION_LOG, true)
    set(value) = put { putBoolean(KEY_SESSION_LOG, value) }

  /** Debuggable builds only: days the progress clock is moved by. */
  var debugDayOffset: Int
    get() = if (debuggable) prefs.getInt(KEY_DEBUG_DAY_OFFSET, 0) else 0
    set(value) = put { putInt(KEY_DEBUG_DAY_OFFSET, value) }

  private inline fun put(crossinline block: android.content.SharedPreferences.Editor.() -> Unit) {
    prefs.edit { block() }
    _changes.value += 1
  }

  companion object {
    private const val NAME = "progress"
    private const val KEY_REMINDER = "reminder"
    private const val KEY_REMINDER_MINUTE = "reminder_minute"
    private const val KEY_LAST_REMINDER_DAY = "last_reminder_day"
    private const val KEY_SESSION_LOG = "session_log"
    private const val KEY_DEBUG_DAY_OFFSET = "debug_day_offset"
    const val DEFAULT_REMINDER_MINUTE = 20 * 60
  }
}
