package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * High-accuracy badge tiers awarded when a child completes a **full Surah**:
 * - [SILVER] (`80%–89%`): Silver Crescent
 * - [GOLD] (`90%–96%`): Golden Lantern
 * - [DIAMOND] (`97%–100%`): Diamond Hafiz
 */
enum class SurahBadgeTier(
  val minAccuracyPercent: Int,
  val titleEn: String,
  val titleAr: String,
  val emoji: String
) {
  NONE(
    minAccuracyPercent = 0,
    titleEn = "Keep Practicing",
    titleAr = "واصل التدريب",
    emoji = "📖"
  ),
  SILVER(
    minAccuracyPercent = 80,
    titleEn = "Silver Crescent",
    titleAr = "الهلال الفضي",
    emoji = "🥈"
  ),
  GOLD(
    minAccuracyPercent = 90,
    titleEn = "Golden Lantern",
    titleAr = "الفانوس الذهبي",
    emoji = "🥇"
  ),
  DIAMOND(
    minAccuracyPercent = 97,
    titleEn = "Diamond Hafiz",
    titleAr = "تاج الألماس",
    emoji = "💎"
  );

  fun isHigherThan(other: SurahBadgeTier): Boolean = this.ordinal > other.ordinal

  companion object {
    const val MIN_HIGH_ACCURACY_PERCENT = 80

    fun fromAccuracy(accuracyPercent: Int): SurahBadgeTier = when {
      accuracyPercent >= DIAMOND.minAccuracyPercent -> DIAMOND
      accuracyPercent >= GOLD.minAccuracyPercent -> GOLD
      accuracyPercent >= SILVER.minAccuracyPercent -> SILVER
      else -> NONE
    }

    fun fromNameOrNone(name: String?): SurahBadgeTier {
      if (name.isNullOrBlank()) return NONE
      return entries.firstOrNull { it.name == name } ?: NONE
    }
  }
}

/**
 * Earned high-accuracy full-Surah achievement badge. Retains the child's best accuracy and
 * highest unlocked tier so retrying a Surah can only upgrade, never downgrade, the badge.
 */
data class SurahCompletionBadge(
  val surah: Int,
  val surahNameAr: String,
  val tier: SurahBadgeTier,
  val bestAccuracyPercent: Int,
  val completionsCount: Int = 1,
  val earnedAtMillis: Long = 0L
)

/**
 * Result returned when recording a full-Surah completion so the celebration screen can announce
 * newly unlocked or tier-upgraded badges.
 */
data class SurahBadgeAwardResult(
  val badge: SurahCompletionBadge?,
  val isNewlyUnlocked: Boolean,
  val isTierUpgraded: Boolean,
  val previousTier: SurahBadgeTier
) {
  val earnedHighAccuracyBadge: Boolean
    get() = badge != null && badge.tier != SurahBadgeTier.NONE
}

/**
 * Learner profile rank derived from high-accuracy Surah badges earned across all 114 Surahs.
 */
enum class KidsLearnerRank(
  val minSurahBadges: Int,
  val titleEn: String,
  val titleAr: String,
  val emoji: String
) {
  LITTLE_SPROUT(0, "Little Sprout", "برعم القرآن", "🌱"),
  SHINING_RECITER(1, "Shining Reciter", "القارئ المشرق", "⭐"),
  GOLDEN_LANTERN(3, "Golden Lantern", "حامل النور", "🏮"),
  SURAH_CHAMPION(7, "Surah Champion", "بطل السور", "🏅"),
  MASTER_HAFIZ(15, "Master Hafiz", "الحافظ المتقن", "👑");

  fun nextRank(): KidsLearnerRank? {
    val idx = ordinal + 1
    return if (idx < entries.size) entries[idx] else null
  }

  companion object {
    fun forBadgeCount(surahBadgeCount: Int): KidsLearnerRank {
      var current = LITTLE_SPROUT
      for (rank in entries) {
        if (surahBadgeCount >= rank.minSurahBadges) {
          current = rank
        }
      }
      return current
    }
  }
}

data class KidsBadge(
  val id: String,
  val titleEn: String,
  val titleAr: String,
  val emoji: String,
  val descriptionEn: String = "",
  val descriptionAr: String = "",
  val isUnlocked: Boolean = true,
  val progressCurrent: Int = 1,
  val progressTarget: Int = 1
) {
  val progressFraction: Float
    get() = if (progressTarget <= 0) 1f else (progressCurrent.toFloat() / progressTarget.toFloat()).coerceIn(0f, 1f)
}

data class KidsRewardsState(
  val totalStars: Int = 0,
  val streakDays: Int = 0,
  val ayahStars: Map<String, Int> = emptyMap(),     // key: "surah:ayah" -> best stars (1..3)
  val surahStars: Map<Int, Int> = emptyMap(),       // surah -> total stars earned across its Ayat
  val surahMasteredCount: Map<Int, Int> = emptyMap(), // surah -> count of Ayat with >= 1 star
  val unlockedBadges: List<KidsBadge> = emptyList(),
  val learnerName: String = "",
  val surahBadges: Map<Int, SurahCompletionBadge> = emptyMap(),
  val milestoneBadges: List<KidsBadge> = emptyList()
) {
  val earnedSurahBadgesList: List<SurahCompletionBadge>
    get() = surahBadges.values
      .filter { it.tier != SurahBadgeTier.NONE }
      .sortedWith(
        compareByDescending<SurahCompletionBadge> { it.tier.ordinal }
          .thenByDescending { it.bestAccuracyPercent }
          .thenBy { it.surah }
      )

  val diamondBadgeCount: Int
    get() = surahBadges.values.count { it.tier == SurahBadgeTier.DIAMOND }

  val goldBadgeCount: Int
    get() = surahBadges.values.count { it.tier == SurahBadgeTier.GOLD }

  val silverBadgeCount: Int
    get() = surahBadges.values.count { it.tier == SurahBadgeTier.SILVER }

  val averageSurahBadgeAccuracy: Int
    get() {
      val earned = earnedSurahBadgesList
      if (earned.isEmpty()) return 0
      return earned.map { it.bestAccuracyPercent }.average().roundToInt().coerceIn(0, 100)
    }

  val learnerRank: KidsLearnerRank
    get() = KidsLearnerRank.forBadgeCount(earnedSurahBadgesList.size)
}

/**
 * Persistent local rewards store tracking stars per Ayah & Surah, daily recitation streaks,
 * mastered Ayat, full-Surah high-accuracy badges (`Silver 80%+`, `Gold 90%+`, `Diamond 97%+`),
 * learner profile name, and milestone achievements. Uses [SharedPreferences] backed by a reactive
 * [StateFlow] for instant Compose updates and zero-dependency offline reliability.
 */
object RewardsDataStore {

  private const val PREFS_NAME = "tilawi_kids_rewards_prefs"
  private const val KEY_STREAK_DAYS = "streak_days"
  private const val KEY_LAST_PRACTICE_DAY = "last_practice_day"
  private const val KEY_LEARNER_NAME = "learner_profile_name"
  private const val KEY_AYAH_STARS_PREFIX = "ayah_stars_"
  private const val KEY_SURAH_BADGE_PREFIX = "surah_badge_"

  private val _state = MutableStateFlow(KidsRewardsState())
  val state: StateFlow<KidsRewardsState> = _state.asStateFlow()

  @Volatile
  private var initialized = false

  fun initialize(context: Context) {
    if (initialized) return
    synchronized(this) {
      if (initialized) return
      reloadFromPrefs(prefs(context))
      initialized = true
    }
  }

  /**
   * Saves the child's personalized display name shown on the Profile & Achievements screen.
   */
  fun setLearnerName(context: Context, name: String) {
    val sanitized = name.trim().take(28)
    val sp = prefs(context)
    if (!initialized) {
      reloadFromPrefs(sp)
      initialized = true
    }
    sp.edit().putString(KEY_LEARNER_NAME, sanitized).apply()
    reloadFromPrefs(sp)
  }

  /**
   * Records stars earned (`1..3`) for `(surah, ayah)`, keeping the child's highest score per Ayah
   * and updating the daily practice streak and badges.
   *
   * @return delta of newly added stars (`0..3`).
   */
  fun recordAyahStars(
    context: Context,
    surah: Int,
    ayah: Int,
    starsEarned: Int,
    todayStamp: String = currentDayStamp()
  ): Int {
    val clamped = starsEarned.coerceIn(0, 3)
    if (clamped <= 0) return 0

    val sp = prefs(context)
    if (!initialized) {
      reloadFromPrefs(sp)
      initialized = true
    }

    val key = "$KEY_AYAH_STARS_PREFIX${surah}_$ayah"
    val previousBest = sp.getInt(key, 0)
    val newBest = maxOf(previousBest, clamped)
    val delta = newBest - previousBest

    val editor = sp.edit()
    if (delta > 0) {
      editor.putInt(key, newBest)
    }
    updateStreakLocked(sp, editor, todayStamp)
    editor.apply()

    reloadFromPrefs(sp)
    return delta
  }

  /**
   * Records completion of a **full Surah** with [accuracyPercent] (`0..100`) and awards or upgrades
   * a high-accuracy [SurahCompletionBadge] when `accuracyPercent >= 80%`:
   * - `80%–89%` -> [SurahBadgeTier.SILVER]
   * - `90%–96%` -> [SurahBadgeTier.GOLD]
   * - `97%–100%` -> [SurahBadgeTier.DIAMOND]
   *
   * Badges are never downgraded on subsequent attempts; only higher tiers/accuracies overwrite.
   */
  fun recordSurahCompletion(
    context: Context,
    surah: Int,
    surahNameAr: String,
    accuracyPercent: Int,
    nowMillis: Long = System.currentTimeMillis(),
    todayStamp: String = currentDayStamp()
  ): SurahBadgeAwardResult {
    val safeSurah = surah.coerceIn(1, 114)
    val clampedAccuracy = accuracyPercent.coerceIn(0, 100)
    val safeName = surahNameAr.ifBlank { MinshawiJuz30Repository.getSurahNameAr(safeSurah) }
      .replace("|", " ")
      .trim()

    val sp = prefs(context)
    if (!initialized) {
      reloadFromPrefs(sp)
      initialized = true
    }

    val existing = parseSurahBadge(safeSurah, sp.getString("$KEY_SURAH_BADGE_PREFIX$safeSurah", null))
    val previousTier = existing?.tier ?: SurahBadgeTier.NONE
    val achievedTier = SurahBadgeTier.fromAccuracy(clampedAccuracy)

    val editor = sp.edit()
    updateStreakLocked(sp, editor, todayStamp)

    if (achievedTier == SurahBadgeTier.NONE) {
      editor.apply()
      reloadFromPrefs(sp)
      return SurahBadgeAwardResult(
        badge = existing,
        isNewlyUnlocked = false,
        isTierUpgraded = false,
        previousTier = previousTier
      )
    }

    val isNewlyUnlocked = existing == null || existing.tier == SurahBadgeTier.NONE
    val isTierUpgraded = !isNewlyUnlocked && achievedTier.isHigherThan(previousTier)
    val bestTier = if (achievedTier.isHigherThan(previousTier)) achievedTier else previousTier
    val bestAccuracy = maxOf(existing?.bestAccuracyPercent ?: 0, clampedAccuracy)
    val completionsCount = (existing?.completionsCount ?: 0) + 1
    val earnedAt = if (isNewlyUnlocked || isTierUpgraded) {
      nowMillis
    } else {
      existing?.earnedAtMillis ?: nowMillis
    }

    val updated = SurahCompletionBadge(
      surah = safeSurah,
      surahNameAr = safeName,
      tier = bestTier,
      bestAccuracyPercent = bestAccuracy,
      completionsCount = completionsCount,
      earnedAtMillis = earnedAt
    )

    editor.putString("$KEY_SURAH_BADGE_PREFIX$safeSurah", serializeSurahBadge(updated))
    editor.apply()

    reloadFromPrefs(sp)
    return SurahBadgeAwardResult(
      badge = updated,
      isNewlyUnlocked = isNewlyUnlocked,
      isTierUpgraded = isTierUpgraded,
      previousTier = previousTier
    )
  }

  fun badgeForSurah(surah: Int): SurahCompletionBadge? =
    _state.value.surahBadges[surah]

  fun starsForAyah(surah: Int, ayah: Int): Int =
    _state.value.ayahStars["$surah:$ayah"] ?: 0

  fun starsForSurah(surah: Int): Int =
    _state.value.surahStars[surah] ?: 0

  fun masteredAyatForSurah(surah: Int): Int =
    _state.value.surahMasteredCount[surah] ?: 0

  /**
   * Computes a kid-friendly 0..3 star rating from [TurnEvaluationResult].
   */
  fun computeStarsFromGrade(grade: TurnEvaluationResult): Int {
    if (grade.noSpeechDetected) return 0
    val wordErrorCount = grade.wordGrades.count {
      it.status == WordGradeStatus.WORD_ERROR || it.status == WordGradeStatus.MISSING
    }
    return when {
      grade.scorePercent >= 90.0 && wordErrorCount == 0 -> 3
      grade.passed && grade.scorePercent >= 78.0 -> 2
      grade.passed || grade.scorePercent >= 62.0 -> 1
      else -> 0
    }
  }

  internal fun clearForTests(context: Context) {
    prefs(context).edit().clear().apply()
    initialized = false
    _state.value = KidsRewardsState()
  }

  internal fun reloadFromDiskForTests(context: Context) {
    reloadFromPrefs(prefs(context))
    initialized = true
  }

  private fun prefs(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private fun updateStreakLocked(
    sp: SharedPreferences,
    editor: SharedPreferences.Editor,
    today: String
  ) {
    val lastDay = sp.getString(KEY_LAST_PRACTICE_DAY, null)
    val currentStreak = sp.getInt(KEY_STREAK_DAYS, 0)
    val nextStreak = when {
      lastDay == today -> maxOf(1, currentStreak)
      lastDay != null && isYesterday(lastDay, today) -> currentStreak + 1
      else -> 1
    }
    editor.putInt(KEY_STREAK_DAYS, nextStreak)
    editor.putString(KEY_LAST_PRACTICE_DAY, today)
  }

  private fun reloadFromPrefs(sp: SharedPreferences) {
    val all = sp.all
    val ayahMap = HashMap<String, Int>()
    val surahStars = HashMap<Int, Int>()
    val surahMastered = HashMap<Int, Int>()
    val surahBadges = HashMap<Int, SurahCompletionBadge>()
    var totalStars = 0

    for ((k, v) in all) {
      if (k.startsWith(KEY_AYAH_STARS_PREFIX) && v is Int && v > 0) {
        val rest = k.removePrefix(KEY_AYAH_STARS_PREFIX)
        val parts = rest.split("_")
        if (parts.size == 2) {
          val s = parts[0].toIntOrNull() ?: continue
          val a = parts[1].toIntOrNull() ?: continue
          ayahMap["$s:$a"] = v
          totalStars += v
          surahStars[s] = (surahStars[s] ?: 0) + v
          surahMastered[s] = (surahMastered[s] ?: 0) + 1
        }
      } else if (k.startsWith(KEY_SURAH_BADGE_PREFIX) && v is String) {
        val surahNum = k.removePrefix(KEY_SURAH_BADGE_PREFIX).toIntOrNull() ?: continue
        val parsed = parseSurahBadge(surahNum, v)
        if (parsed != null && parsed.tier != SurahBadgeTier.NONE) {
          surahBadges[surahNum] = parsed
        }
      }
    }

    val streak = sp.getInt(KEY_STREAK_DAYS, 0)
    val learnerName = sp.getString(KEY_LEARNER_NAME, "") ?: ""
    val milestones = buildMilestoneCatalog(totalStars, streak, surahMastered, surahBadges)
    val unlocked = milestones.filter { it.isUnlocked }

    _state.value = KidsRewardsState(
      totalStars = totalStars,
      streakDays = streak,
      ayahStars = ayahMap,
      surahStars = surahStars,
      surahMasteredCount = surahMastered,
      unlockedBadges = unlocked,
      learnerName = learnerName,
      surahBadges = surahBadges,
      milestoneBadges = milestones
    )
  }

  private fun buildMilestoneCatalog(
    totalStars: Int,
    streakDays: Int,
    surahMastered: Map<Int, Int>,
    surahBadges: Map<Int, SurahCompletionBadge>
  ): List<KidsBadge> {
    val earnedSurahCount = surahBadges.size
    val goldOrDiamondCount = surahBadges.values.count {
      it.tier == SurahBadgeTier.GOLD || it.tier == SurahBadgeTier.DIAMOND
    }
    val diamondCount = surahBadges.values.count { it.tier == SurahBadgeTier.DIAMOND }
    val juzAmmaCount = surahBadges.keys.count { it in 78..114 }
    val fatihaCount = (surahMastered[1] ?: 0).coerceAtMost(7)
    val ikhlasCount = (surahMastered[112] ?: 0).coerceAtMost(4)

    return listOf(
      KidsBadge(
        id = "first_surah_badge",
        titleEn = "First Surah Master",
        titleAr = "بطل أول سورة",
        emoji = "🏅",
        descriptionEn = "Complete any full Surah with 80%+ accuracy",
        descriptionAr = "أتمّ أي سورة كاملة بدقة ٨٠٪ فأكثر",
        isUnlocked = earnedSurahCount >= 1,
        progressCurrent = earnedSurahCount.coerceAtMost(1),
        progressTarget = 1
      ),
      KidsBadge(
        id = "golden_surah_badge",
        titleEn = "Golden Reciter",
        titleAr = "القارئ الذهبي",
        emoji = "🥇",
        descriptionEn = "Complete a full Surah with 90%+ accuracy",
        descriptionAr = "أتمّ سورة كاملة بدقة ٩٠٪ فأكثر",
        isUnlocked = goldOrDiamondCount >= 1,
        progressCurrent = goldOrDiamondCount.coerceAtMost(1),
        progressTarget = 1
      ),
      KidsBadge(
        id = "diamond_surah_badge",
        titleEn = "Diamond Hafiz",
        titleAr = "حافظ الألماس",
        emoji = "💎",
        descriptionEn = "Complete a full Surah with 97%+ accuracy",
        descriptionAr = "أتمّ سورة كاملة بدقة ٩٧٪ فأكثر",
        isUnlocked = diamondCount >= 1,
        progressCurrent = diamondCount.coerceAtMost(1),
        progressTarget = 1
      ),
      KidsBadge(
        id = "three_surahs_master",
        titleEn = "3-Surah Champion",
        titleAr = "بطل ٣ سور",
        emoji = "🏆",
        descriptionEn = "Earn high-accuracy badges on 3 Surahs",
        descriptionAr = "احصل على شارات الإتقان في ٣ سور مختلفة",
        isUnlocked = earnedSurahCount >= 3,
        progressCurrent = earnedSurahCount.coerceAtMost(3),
        progressTarget = 3
      ),
      KidsBadge(
        id = "ten_surahs_master",
        titleEn = "10-Surah Scholar",
        titleAr = "نجم ١٠ سور",
        emoji = "👑",
        descriptionEn = "Earn high-accuracy badges on 10 Surahs",
        descriptionAr = "احصل على شارات الإتقان في ١٠ سور مختلفة",
        isUnlocked = earnedSurahCount >= 10,
        progressCurrent = earnedSurahCount.coerceAtMost(10),
        progressTarget = 10
      ),
      KidsBadge(
        id = "juz_amma_guardian",
        titleEn = "Juz 'Amma Guardian",
        titleAr = "حارس جزء عمّ",
        emoji = "🕌",
        descriptionEn = "Earn badges across all 37 Surahs of Juz 'Amma",
        descriptionAr = "احصل على شارات جميع سور جزء عمّ (٣٧ سورة)",
        isUnlocked = juzAmmaCount >= 37,
        progressCurrent = juzAmmaCount.coerceAtMost(37),
        progressTarget = 37
      ),
      KidsBadge(
        id = "first_star",
        titleEn = "First Star",
        titleAr = "أول نجمة",
        emoji = "⭐",
        descriptionEn = "Earn your very first recitation star",
        descriptionAr = "احصل على أول نجمة في التلاوة",
        isUnlocked = totalStars >= 1,
        progressCurrent = totalStars.coerceAtMost(1),
        progressTarget = 1
      ),
      KidsBadge(
        id = "fatiha_champ",
        titleEn = "Al-Fatiha Hero",
        titleAr = "بطل الفاتحة",
        emoji = "🏆",
        descriptionEn = "Master all 7 Ayat of Surah Al-Fatiha",
        descriptionAr = "أتمّ حفظ وتلاوة آيات سورة الفاتحة السبع",
        isUnlocked = fatihaCount >= 7 || surahBadges.containsKey(1),
        progressCurrent = if (surahBadges.containsKey(1)) 7 else fatihaCount,
        progressTarget = 7
      ),
      KidsBadge(
        id = "ikhlas_champ",
        titleEn = "Al-Ikhlas Star",
        titleAr = "نجم الإخلاص",
        emoji = "🌟",
        descriptionEn = "Master all 4 Ayat of Surah Al-Ikhlas",
        descriptionAr = "أتمّ حفظ وتلاوة آيات سورة الإخلاص الأربع",
        isUnlocked = ikhlasCount >= 4 || surahBadges.containsKey(112),
        progressCurrent = if (surahBadges.containsKey(112)) 4 else ikhlasCount,
        progressTarget = 4
      ),
      KidsBadge(
        id = "tajweed_explorer",
        titleEn = "Tajweed Explorer",
        titleAr = "مستكشف التجويد",
        emoji = "🎨",
        descriptionEn = "Collect 30 recitation stars",
        descriptionAr = "اجمع ٣٠ نجمة من تلاواتك المتقنة",
        isUnlocked = totalStars >= 30,
        progressCurrent = totalStars.coerceAtMost(30),
        progressTarget = 30
      ),
      KidsBadge(
        id = "streak_3",
        titleEn = "3-Day Streak",
        titleAr = "مثابرة ٣ أيام",
        emoji = "🔥",
        descriptionEn = "Practice Quran recitation 3 days in a row",
        descriptionAr = "واظب على تلاوة القرآن ٣ أيام متتالية",
        isUnlocked = streakDays >= 3,
        progressCurrent = streakDays.coerceAtMost(3),
        progressTarget = 3
      )
    )
  }

  private fun serializeSurahBadge(badge: SurahCompletionBadge): String =
    "${badge.tier.name}|${badge.bestAccuracyPercent}|${badge.completionsCount}|${badge.earnedAtMillis}|${badge.surahNameAr}"

  private fun parseSurahBadge(surah: Int, raw: String?): SurahCompletionBadge? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split("|")
    if (parts.size < 4) return null
    val tier = SurahBadgeTier.fromNameOrNone(parts[0])
    if (tier == SurahBadgeTier.NONE) return null
    val acc = parts[1].toIntOrNull()?.coerceIn(0, 100) ?: return null
    val count = parts[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
    val earnedAt = parts[3].toLongOrNull() ?: 0L
    val name = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
      ?: MinshawiJuz30Repository.getSurahNameAr(surah)
    return SurahCompletionBadge(
      surah = surah,
      surahNameAr = name,
      tier = tier,
      bestAccuracyPercent = acc,
      completionsCount = count,
      earnedAtMillis = earnedAt
    )
  }

  private fun currentDayStamp(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

  private fun isYesterday(previousStamp: String, todayStamp: String): Boolean {
    return try {
      val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      val prevDate = sdf.parse(previousStamp) ?: return false
      val todayDate = sdf.parse(todayStamp) ?: return false
      val cal = Calendar.getInstance().apply {
        time = prevDate
        add(Calendar.DAY_OF_YEAR, 1)
      }
      sdf.format(cal.time) == sdf.format(todayDate)
    } catch (_: Throwable) {
      false
    }
  }
}
