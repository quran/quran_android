package com.quran.mobile.feature.memorize.check

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import dev.zacsweers.metro.Inject

/**
 * Whether the double-check suits this phone, and why: it is recommended with the setting, and
 * offered with the recitation model only where it suits.
 *
 * Its model needs about 300 MB more memory while the user recites. It is measured at ~280 MB on a
 * Snapdragon 888 (Mi 11), which takes ~0.5 s per check. Phones with less than ~6 GB of memory, or
 * a 32-bit processor, are not advised to use it. Once it has run on the phone, the measured time
 * decides: hints that come more than 1.5 s late are not worth the fewer wrong ones.
 */
class DoubleCheckAdvice @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val preferences: MemorizePreferences,
) {
  enum class Reason {
    /** Enough memory and a 64-bit processor (not run here yet). */
    SUITS,
    /** It has run here, fast enough. */
    FAST,
    LOW_MEMORY,
    OLD_PROCESSOR,
    /** It has run here, too slowly. */
    SLOW,
  }

  data class Advice(val recommended: Boolean, val reason: Reason, val memoryBytes: Long, val checkMs: Long)

  fun advice(): Advice {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }.totalMem
    return advise(memory, activityManager.isLowRamDevice, Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(), preferences.doubleCheckMs)
  }

  companion object {
    /** About 6 GB phones and up (the system keeps some of it, so they report a little less). */
    const val MIN_MEMORY_BYTES = 5_000_000_000L
    const val MAX_CHECK_MS = 1_500L

    fun advise(memoryBytes: Long, lowRamDevice: Boolean, is64Bit: Boolean, checkMs: Long): Advice {
      val reason = when {
        lowRamDevice || memoryBytes < MIN_MEMORY_BYTES -> Reason.LOW_MEMORY
        !is64Bit -> Reason.OLD_PROCESSOR
        checkMs > MAX_CHECK_MS -> Reason.SLOW
        checkMs > 0 -> Reason.FAST
        else -> Reason.SUITS
      }
      return Advice(reason == Reason.SUITS || reason == Reason.FAST, reason, memoryBytes, checkMs)
    }
  }
}
