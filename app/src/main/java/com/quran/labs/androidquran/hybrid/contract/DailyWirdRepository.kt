package com.quran.labs.androidquran.hybrid.contract

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TimeZone
import kotlin.math.roundToInt

@Immutable
data class DailyWirdState(
  val goalPages: Int = DailyWirdRepository.DEFAULT_GOAL_PAGES,
  val pagesReadToday: Int = 0,
  val lastReadDateEpochDay: Long = 0L,
  val lastSuraName: String = "",
  val lastAyah: Int = 1,
  val lastPage: Int = 1,
  val lastJuz: Int = 1
) {
  val progressFraction: Float
    get() = if (goalPages <= 0) 0f else (pagesReadToday.toFloat() / goalPages.toFloat()).coerceIn(0f, 1f)

  val progressPercent: Int
    get() = (progressFraction * 100f).roundToInt().coerceIn(0, 100)
}

@SingleIn(AppScope::class)
class DailyWirdRepository internal constructor(
  private val prefs: SharedPreferences,
  private val epochDayProvider: () -> Long
) {

  @Inject
  constructor(@ApplicationContext context: Context) : this(
    prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    epochDayProvider = ::currentLocalEpochDay
  )

  private val mutex = Mutex()
  private val _stateFlow = MutableStateFlow(loadState())
  val stateFlow: StateFlow<DailyWirdState> = _stateFlow.asStateFlow()

  suspend fun recordPageRead(page: Int) {
    if (page !in MIN_QURAN_PAGE..MAX_QURAN_PAGE) return
    mutex.withLock {
      val today = epochDayProvider()
      val storedDay = prefs.getLong(KEY_LAST_READ_DATE_EPOCH_DAY, today)
      val currentPages = if (storedDay == today) {
        prefs.getStringSet(KEY_PAGES_READ_SET, emptySet()).orEmpty().toMutableSet()
      } else {
        mutableSetOf()
      }
      currentPages.add(page.toString())

      prefs.edit()
        .putLong(KEY_LAST_READ_DATE_EPOCH_DAY, today)
        .putStringSet(KEY_PAGES_READ_SET, currentPages)
        .putInt(KEY_LAST_PAGE, page)
        .apply()

      _stateFlow.value = buildState(today = today, pagesSet = currentPages)
    }
  }

  /**
   * Coalesces [recordPageRead] and [updateLastLocation] into a single [SharedPreferences.Editor.apply]
   * transaction so settled Mushaf page flips issue one write and one StateFlow emission.
   */
  suspend fun recordPageReadAndLocation(
    page: Int,
    suraName: String,
    ayah: Int,
    juz: Int
  ) {
    if (page !in MIN_QURAN_PAGE..MAX_QURAN_PAGE) return
    mutex.withLock {
      val today = epochDayProvider()
      val storedDay = prefs.getLong(KEY_LAST_READ_DATE_EPOCH_DAY, today)
      val currentPages = if (storedDay == today) {
        prefs.getStringSet(KEY_PAGES_READ_SET, emptySet()).orEmpty().toMutableSet()
      } else {
        mutableSetOf()
      }
      currentPages.add(page.toString())
      val safeAyah = ayah.coerceAtLeast(1)
      val safeJuz = juz.coerceIn(1, MAX_QURAN_JUZ)

      prefs.edit()
        .putLong(KEY_LAST_READ_DATE_EPOCH_DAY, today)
        .putStringSet(KEY_PAGES_READ_SET, currentPages)
        .putString(KEY_LAST_SURA_NAME, suraName)
        .putInt(KEY_LAST_AYAH, safeAyah)
        .putInt(KEY_LAST_PAGE, page)
        .putInt(KEY_LAST_JUZ, safeJuz)
        .apply()

      _stateFlow.value = buildState(today = today, pagesSet = currentPages)
    }
  }

  suspend fun setGoalPages(goalPages: Int) {
    val clampedGoal = goalPages.coerceIn(MIN_QURAN_PAGE, MAX_QURAN_PAGE)
    mutex.withLock {
      prefs.edit()
        .putInt(KEY_GOAL_PAGES, clampedGoal)
        .apply()
      val today = epochDayProvider()
      val currentPages = readPagesForToday(today)
      _stateFlow.value = buildState(today = today, pagesSet = currentPages)
    }
  }

  suspend fun updateLastLocation(
    suraName: String,
    ayah: Int,
    page: Int,
    juz: Int
  ) {
    mutex.withLock {
      val safePage = page.coerceIn(MIN_QURAN_PAGE, MAX_QURAN_PAGE)
      val safeAyah = ayah.coerceAtLeast(1)
      val safeJuz = juz.coerceIn(1, MAX_QURAN_JUZ)
      prefs.edit()
        .putString(KEY_LAST_SURA_NAME, suraName)
        .putInt(KEY_LAST_AYAH, safeAyah)
        .putInt(KEY_LAST_PAGE, safePage)
        .putInt(KEY_LAST_JUZ, safeJuz)
        .apply()
      val today = epochDayProvider()
      val currentPages = readPagesForToday(today)
      _stateFlow.value = buildState(today = today, pagesSet = currentPages)
    }
  }

  suspend fun refreshIfNewDay() {
    mutex.withLock {
      val today = epochDayProvider()
      val storedDay = prefs.getLong(KEY_LAST_READ_DATE_EPOCH_DAY, 0L)
      if (storedDay != today && prefs.getStringSet(KEY_PAGES_READ_SET, emptySet())?.isNotEmpty() == true) {
        prefs.edit()
          .putLong(KEY_LAST_READ_DATE_EPOCH_DAY, today)
          .putStringSet(KEY_PAGES_READ_SET, emptySet())
          .apply()
      }
      _stateFlow.value = buildState(today = today, pagesSet = readPagesForToday(today))
    }
  }

  private fun loadState(): DailyWirdState {
    val today = epochDayProvider()
    val pages = readPagesForToday(today)
    return buildState(today = today, pagesSet = pages)
  }

  private fun readPagesForToday(today: Long): Set<String> {
    val storedDay = prefs.getLong(KEY_LAST_READ_DATE_EPOCH_DAY, 0L)
    return if (storedDay == today) {
      prefs.getStringSet(KEY_PAGES_READ_SET, emptySet()).orEmpty()
    } else {
      emptySet()
    }
  }

  private fun buildState(today: Long, pagesSet: Set<String>): DailyWirdState {
    val goal = prefs.getInt(KEY_GOAL_PAGES, DEFAULT_GOAL_PAGES).coerceIn(MIN_QURAN_PAGE, MAX_QURAN_PAGE)
    val storedDay = prefs.getLong(KEY_LAST_READ_DATE_EPOCH_DAY, 0L)
    val effectiveDay = if (storedDay == today) storedDay else today
    return DailyWirdState(
      goalPages = goal,
      pagesReadToday = pagesSet.size,
      lastReadDateEpochDay = effectiveDay,
      lastSuraName = prefs.getString(KEY_LAST_SURA_NAME, "") ?: "",
      lastAyah = prefs.getInt(KEY_LAST_AYAH, 1).coerceAtLeast(1),
      lastPage = prefs.getInt(KEY_LAST_PAGE, 1).coerceIn(MIN_QURAN_PAGE, MAX_QURAN_PAGE),
      lastJuz = prefs.getInt(KEY_LAST_JUZ, 1).coerceIn(1, MAX_QURAN_JUZ)
    )
  }

  companion object {
    const val DEFAULT_GOAL_PAGES: Int = 20
    private const val PREFS_NAME = "hybrid_daily_wird_prefs"
    private const val KEY_GOAL_PAGES = "goal_pages"
    private const val KEY_LAST_READ_DATE_EPOCH_DAY = "last_read_date_epoch_day"
    private const val KEY_PAGES_READ_SET = "pages_read_set"
    private const val KEY_LAST_SURA_NAME = "last_sura_name"
    private const val KEY_LAST_AYAH = "last_ayah"
    private const val KEY_LAST_PAGE = "last_page"
    private const val KEY_LAST_JUZ = "last_juz"
    private const val MIN_QURAN_PAGE = 1
    private const val MAX_QURAN_PAGE = 604
    private const val MAX_QURAN_JUZ = 30
    private const val MILLIS_PER_DAY = 86_400_000L

    internal fun currentLocalEpochDay(): Long {
      val now = System.currentTimeMillis()
      val localMillis = now + TimeZone.getDefault().getOffset(now)
      val quotient = localMillis / MILLIS_PER_DAY
      return if (localMillis < 0L && localMillis % MILLIS_PER_DAY != 0L) {
        quotient - 1L
      } else {
        quotient
      }
    }
  }
}
