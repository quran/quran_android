package com.quran.labs.androidquran.kidsclassroom

import kotlin.math.roundToInt

/**
 * Scope options for the visual **Surah Journey** map inside Profile & Achievements.
 */
enum class SurahJourneyScope {
  JUZ_AMMA,    // Al-Fatiha + all 37 Surahs of Juz 'Amma (114 -> 78)
  JUZ_TABARAK, // All 11 Surahs of Juz 29 (77 -> 67)
  FULL_QURAN   // All 30 Juz of the Holy Quran (drills into any Juz's Surahs)
}

/**
 * Completion status of an individual Surah stepping stone on the visual journey trail.
 */
enum class JourneyNodeStatus {
  COMPLETED_BADGED,
  COMPLETED_STARS,
  IN_PROGRESS,
  NEXT_UP,
  UPCOMING
}

/**
 * Metadata for one Juz (`1..30`) of the Holy Quran, including its authentic Arabic name
 * and the ordered list of Surahs contained in that Juz.
 */
data class QuranJuzSpec(
  val juzNumber: Int,
  val nameAr: String,
  val surahNumbers: List<Int>
)

/**
 * Single Surah stepping-stone node along the winding visual journey trail.
 */
data class SurahJourneyNode(
  val stepIndex: Int,
  val surahNumber: Int,
  val surahNameAr: String,
  val totalAyat: Int,
  val masteredAyat: Int,
  val starsEarned: Int,
  val badge: SurahCompletionBadge?,
  val status: JourneyNodeStatus
) {
  val isCompleted: Boolean
    get() = status == JourneyNodeStatus.COMPLETED_BADGED || status == JourneyNodeStatus.COMPLETED_STARS

  val progressFraction: Float
    get() = when {
      isCompleted -> 1f
      totalAyat <= 0 -> 0f
      else -> (masteredAyat.toFloat() / totalAyat.toFloat()).coerceIn(0f, 1f)
    }
}

/**
 * Unlockable milestone checkpoint gate rendered directly along the winding journey path.
 */
data class JourneyMilestoneGate(
  val id: String,
  val titleEn: String,
  val titleAr: String,
  val subtitleEn: String,
  val subtitleAr: String,
  val emoji: String,
  val afterStepIndex: Int,
  val requiredCount: Int,
  val currentCount: Int,
  val isUnlocked: Boolean
) {
  val progressFraction: Float
    get() = if (requiredCount <= 0) 1f else (currentCount.toFloat() / requiredCount.toFloat()).coerceIn(0f, 1f)
}

/**
 * Ordered entries placed along a Surah winding trail: either a Surah stepping stone or a
 * milestone checkpoint gate.
 */
sealed interface SurahTrailItem {
  val key: String

  data class SurahStep(val node: SurahJourneyNode) : SurahTrailItem {
    override val key: String = "surah_${node.surahNumber}_${node.stepIndex}"
  }

  data class MilestoneGate(val gate: JourneyMilestoneGate) : SurahTrailItem {
    override val key: String = "gate_${gate.id}"
  }
}

/**
 * Summary + ordered trail items for a Surah-level journey (Juz 'Amma, Juz 29, or a chosen Juz).
 */
data class SurahJourneyPlan(
  val scope: SurahJourneyScope,
  val selectedJuzNumber: Int?,
  val titleEn: String,
  val titleAr: String,
  val nodes: List<SurahJourneyNode>,
  val gates: List<JourneyMilestoneGate>,
  val trailItems: List<SurahTrailItem>,
  val completedSurahCount: Int,
  val badgedSurahCount: Int,
  val unlockedGateCount: Int,
  val nextRecommendedSurah: Int
) {
  val totalSurahCount: Int get() = nodes.size
  val progressPercent: Int
    get() = if (totalSurahCount <= 0) 0 else ((completedSurahCount * 100f) / totalSurahCount).roundToInt().coerceIn(0, 100)
}

/**
 * Single Juz station on the 30-Juz Full Quran overview journey trail.
 */
data class JuzOverviewNode(
  val juzSpec: QuranJuzSpec,
  val completedSurahs: Int,
  val badgedSurahs: Int,
  val totalSurahs: Int,
  val totalStarsInJuz: Int,
  val bestTierInJuz: SurahBadgeTier,
  val status: JourneyNodeStatus
) {
  val isCompleted: Boolean
    get() = status == JourneyNodeStatus.COMPLETED_BADGED || status == JourneyNodeStatus.COMPLETED_STARS

  val progressFraction: Float
    get() = if (totalSurahs <= 0) 0f else (completedSurahs.toFloat() / totalSurahs.toFloat()).coerceIn(0f, 1f)
}

sealed interface FullQuranTrailItem {
  val key: String

  data class JuzStep(val node: JuzOverviewNode) : FullQuranTrailItem {
    override val key: String = "juz_${node.juzSpec.juzNumber}"
  }

  data class MilestoneGate(val gate: JourneyMilestoneGate) : FullQuranTrailItem {
    override val key: String = "quran_gate_${gate.id}"
  }
}

data class FullQuranJourneyPlan(
  val juzNodes: List<JuzOverviewNode>,
  val gates: List<JourneyMilestoneGate>,
  val trailItems: List<FullQuranTrailItem>,
  val completedJuzCount: Int,
  val completedSurahsTotal: Int,
  val badgedSurahsTotal: Int,
  val unlockedGateCount: Int,
  val nextRecommendedJuz: Int
) {
  val progressPercent: Int
    get() = ((completedSurahsTotal * 100f) / 114f).roundToInt().coerceIn(0, 100)
}

/**
 * Pure domain planner that builds the visual **Surah Journey** paths across Juz 'Amma,
 * Juz Tabarak (29), any individual Juz (`1..30`), or the Full 30-Juz Quran directly from
 * [KidsRewardsState].
 */
object SurahJourneyPlanner {

  val QURAN_JUZ_SPECS: List<QuranJuzSpec> = listOf(
    QuranJuzSpec(1, "الم", listOf(1, 2)),
    QuranJuzSpec(2, "سيقول", listOf(2)),
    QuranJuzSpec(3, "تلك الرسل", listOf(2, 3)),
    QuranJuzSpec(4, "لن تنالوا", listOf(3, 4)),
    QuranJuzSpec(5, "والمحصنات", listOf(4)),
    QuranJuzSpec(6, "لا يحب الله", listOf(4, 5)),
    QuranJuzSpec(7, "وإذا سمعوا", listOf(5, 6)),
    QuranJuzSpec(8, "ولو أننا", listOf(6, 7)),
    QuranJuzSpec(9, "قال الملأ", listOf(7, 8)),
    QuranJuzSpec(10, "واعلموا", listOf(8, 9)),
    QuranJuzSpec(11, "يعتذرون", listOf(9, 10, 11)),
    QuranJuzSpec(12, "وما من دابة", listOf(11, 12)),
    QuranJuzSpec(13, "وما أبرئ", listOf(12, 13, 14)),
    QuranJuzSpec(14, "ربما", listOf(15, 16)),
    QuranJuzSpec(15, "سبحان الذي", listOf(17, 18)),
    QuranJuzSpec(16, "قال ألم", listOf(18, 19, 20)),
    QuranJuzSpec(17, "اقترب للناس", listOf(21, 22)),
    QuranJuzSpec(18, "قد أفلح", listOf(23, 24, 25)),
    QuranJuzSpec(19, "وقال الذين", listOf(25, 26, 27)),
    QuranJuzSpec(20, "أمن خلق", listOf(27, 28, 29)),
    QuranJuzSpec(21, "اتل ما أوحي", listOf(29, 30, 31, 32, 33)),
    QuranJuzSpec(22, "ومن يقنت", listOf(33, 34, 35, 36)),
    QuranJuzSpec(23, "وما لي", listOf(36, 37, 38, 39)),
    QuranJuzSpec(24, "فمن أظلم", listOf(39, 40, 41)),
    QuranJuzSpec(25, "إليه يرد", listOf(41, 42, 43, 44, 45)),
    QuranJuzSpec(26, "حم", listOf(46, 47, 48, 49, 50, 51)),
    QuranJuzSpec(27, "قال فما خطبكم", listOf(51, 52, 53, 54, 55, 56, 57)),
    QuranJuzSpec(28, "قد سمع الله", (58..66).toList()),
    QuranJuzSpec(29, "تبارك الذي", (67..77).toList()),
    QuranJuzSpec(30, "عمّ", (78..114).toList())
  )

  fun getJuzSpec(juzNumber: Int): QuranJuzSpec =
    QURAN_JUZ_SPECS[(juzNumber.coerceIn(1, 30)) - 1]

  fun isSurahCompleted(surah: Int, rewards: KidsRewardsState): Boolean {
    val badge = rewards.surahBadges[surah]
    if (badge != null && badge.tier != SurahBadgeTier.NONE) return true
    val totalAyat = MinshawiJuz30Repository.getAyahCountForSurah(surah)
    val masteredAyat = rewards.surahMasteredCount[surah] ?: 0
    return totalAyat > 0 && masteredAyat >= totalAyat
  }

  /**
   * Builds a Surah-level stepping-stone journey plan for Juz 'Amma (default kid order:
   * Al-Fatiha + 114 down to 78), Juz 29 (77 down to 67), or any specific Juz (`1..30`).
   */
  fun buildSurahJourneyPlan(
    scope: SurahJourneyScope,
    rewards: KidsRewardsState,
    selectedJuzNumber: Int? = null
  ): SurahJourneyPlan {
    val orderedSurahs: List<Int>
    val titleEn: String
    val titleAr: String

    if (selectedJuzNumber != null) {
      val spec = getJuzSpec(selectedJuzNumber)
      orderedSurahs = if (selectedJuzNumber >= 28) spec.surahNumbers.reversed() else spec.surahNumbers
      titleEn = "Juz ${spec.juzNumber} · ${spec.nameAr}"
      titleAr = "الجزء ${spec.juzNumber} · ${spec.nameAr}"
    } else if (scope == SurahJourneyScope.JUZ_TABARAK) {
      orderedSurahs = (77 downTo 67).toList()
      titleEn = "Juz 29 · Tabarak Trail"
      titleAr = "رحلة جزء تبارك (٢٩)"
    } else {
      orderedSurahs = listOf(1) + (114 downTo 78).toList()
      titleEn = "Juz 'Amma Adventure"
      titleAr = "رحلة جزء عمّ والفاتحة"
    }

    var foundNextUp = false
    val nodes = orderedSurahs.mapIndexed { idx, surahNum ->
      val totalAyat = MinshawiJuz30Repository.getAyahCountForSurah(surahNum).coerceAtLeast(1)
      val masteredAyat = (rewards.surahMasteredCount[surahNum] ?: 0).coerceAtMost(totalAyat)
      val badge = rewards.surahBadges[surahNum]?.takeIf { it.tier != SurahBadgeTier.NONE }
      val stars = rewards.surahStars[surahNum] ?: 0

      val status = when {
        badge != null -> JourneyNodeStatus.COMPLETED_BADGED
        masteredAyat >= totalAyat -> JourneyNodeStatus.COMPLETED_STARS
        masteredAyat > 0 -> {
          if (!foundNextUp) foundNextUp = true
          JourneyNodeStatus.IN_PROGRESS
        }
        !foundNextUp -> {
          foundNextUp = true
          JourneyNodeStatus.NEXT_UP
        }
        else -> JourneyNodeStatus.UPCOMING
      }

      SurahJourneyNode(
        stepIndex = idx,
        surahNumber = surahNum,
        surahNameAr = MinshawiJuz30Repository.getSurahNameAr(surahNum),
        totalAyat = totalAyat,
        masteredAyat = if (badge != null) totalAyat else masteredAyat,
        starsEarned = stars,
        badge = badge,
        status = status
      )
    }

    val completedCount = nodes.count { it.isCompleted }
    val badgedCount = nodes.count { it.badge != null }
    val gates = buildGatesForSurahTrail(
      scope = scope,
      selectedJuzNumber = selectedJuzNumber,
      nodes = nodes,
      completedCount = completedCount
    )

    val gatesByAfterIndex = gates.groupBy { it.afterStepIndex }
    val trailItems = ArrayList<SurahTrailItem>(nodes.size + gates.size)
    nodes.forEachIndexed { idx, node ->
      trailItems.add(SurahTrailItem.SurahStep(node))
      gatesByAfterIndex[idx]?.forEach { gate ->
        trailItems.add(SurahTrailItem.MilestoneGate(gate))
      }
    }

    val nextRecommended = nodes.firstOrNull {
      it.status == JourneyNodeStatus.IN_PROGRESS || it.status == JourneyNodeStatus.NEXT_UP
    }?.surahNumber ?: nodes.firstOrNull()?.surahNumber ?: 1

    return SurahJourneyPlan(
      scope = scope,
      selectedJuzNumber = selectedJuzNumber,
      titleEn = titleEn,
      titleAr = titleAr,
      nodes = nodes,
      gates = gates,
      trailItems = trailItems,
      completedSurahCount = completedCount,
      badgedSurahCount = badgedCount,
      unlockedGateCount = gates.count { it.isUnlocked },
      nextRecommendedSurah = nextRecommended
    )
  }

  /**
   * Builds the 30-Juz Full Quran Journey map (ordered Juz 30 -> 1 so kids start from the
   * shortest Surahs and climb all the way to Al-Baqarah & Al-Fatiha, with milestone gates every 5 Juz).
   */
  fun buildFullQuranJourneyPlan(rewards: KidsRewardsState): FullQuranJourneyPlan {
    val orderedJuz = QURAN_JUZ_SPECS.reversed() // Juz 30 down to Juz 1
    var foundNextUp = false

    val juzNodes = orderedJuz.map { spec ->
      val surahs = spec.surahNumbers
      val completed = surahs.count { isSurahCompleted(it, rewards) }
      val badged = surahs.count { s ->
        val b = rewards.surahBadges[s]
        b != null && b.tier != SurahBadgeTier.NONE
      }
      val startedAny = surahs.any { s ->
        (rewards.surahMasteredCount[s] ?: 0) > 0 || rewards.surahBadges.containsKey(s)
      }
      val stars = surahs.sumOf { rewards.surahStars[it] ?: 0 }
      val bestTier = surahs
        .mapNotNull { rewards.surahBadges[it]?.tier }
        .maxByOrNull { it.ordinal } ?: SurahBadgeTier.NONE

      val status = when {
        completed >= surahs.size && badged >= surahs.size -> JourneyNodeStatus.COMPLETED_BADGED
        completed >= surahs.size -> JourneyNodeStatus.COMPLETED_STARS
        startedAny -> {
          if (!foundNextUp) foundNextUp = true
          JourneyNodeStatus.IN_PROGRESS
        }
        !foundNextUp -> {
          foundNextUp = true
          JourneyNodeStatus.NEXT_UP
        }
        else -> JourneyNodeStatus.UPCOMING
      }

      JuzOverviewNode(
        juzSpec = spec,
        completedSurahs = completed,
        badgedSurahs = badged,
        totalSurahs = surahs.size,
        totalStarsInJuz = stars,
        bestTierInJuz = bestTier,
        status = status
      )
    }

    val completedJuzCount = juzNodes.count { it.isCompleted }
    val completedSurahsTotal = (1..114).count { isSurahCompleted(it, rewards) }
    val badgedSurahsTotal = rewards.earnedSurahBadgesList.size

    val gates = listOf(
      JourneyMilestoneGate(
        id = "quran_juz_1",
        titleEn = "First Juz Conqueror",
        titleAr = "فاتح أول جزء",
        subtitleEn = "Complete your first full Juz",
        subtitleAr = "أتمّ حفظ وتلاوة أول جزء كامل",
        emoji = "🌟",
        afterStepIndex = 0, // right after Juz 30
        requiredCount = 1,
        currentCount = completedJuzCount.coerceAtMost(1),
        isUnlocked = completedJuzCount >= 1
      ),
      JourneyMilestoneGate(
        id = "quran_juz_5",
        titleEn = "5-Juz Explorer",
        titleAr = "مستكشف ٥ أجزاء",
        subtitleEn = "Complete 5 full Juz of the Quran",
        subtitleAr = "أتمّ ٥ أجزاء كاملة من القرآن الكريم",
        emoji = "🏮",
        afterStepIndex = 4,
        requiredCount = 5,
        currentCount = completedJuzCount.coerceAtMost(5),
        isUnlocked = completedJuzCount >= 5
      ),
      JourneyMilestoneGate(
        id = "quran_juz_10",
        titleEn = "10-Juz Lantern Bearer",
        titleAr = "حامل نور ١٠ أجزاء",
        subtitleEn = "Complete 10 full Juz of the Quran",
        subtitleAr = "أتمّ ١٠ أجزاء كاملة من القرآن الكريم",
        emoji = "🏆",
        afterStepIndex = 9,
        requiredCount = 10,
        currentCount = completedJuzCount.coerceAtMost(10),
        isUnlocked = completedJuzCount >= 10
      ),
      JourneyMilestoneGate(
        id = "quran_juz_15",
        titleEn = "Half-Quran Star",
        titleAr = "نجم نصف القرآن",
        subtitleEn = "Reach halfway — 15 Juz mastered!",
        subtitleAr = "بلغت منتصف الرحلة — ١٥ جزءاً!",
        emoji = "🌠",
        afterStepIndex = 14,
        requiredCount = 15,
        currentCount = completedJuzCount.coerceAtMost(15),
        isUnlocked = completedJuzCount >= 15
      ),
      JourneyMilestoneGate(
        id = "quran_juz_20",
        titleEn = "20-Juz Noble Scholar",
        titleAr = "عالم ٢٠ جزءاً",
        subtitleEn = "Complete 20 full Juz of the Quran",
        subtitleAr = "أتمّ ٢٠ جزءاً من القرآن الكريم",
        emoji = "💎",
        afterStepIndex = 19,
        requiredCount = 20,
        currentCount = completedJuzCount.coerceAtMost(20),
        isUnlocked = completedJuzCount >= 20
      ),
      JourneyMilestoneGate(
        id = "quran_juz_30",
        titleEn = "Crown of the Complete Quran",
        titleAr = "تاج ختم القرآن الكريم",
        subtitleEn = "Complete all 30 Juz — Hafiz Champion!",
        subtitleAr = "أتممت القرآن الكريم كاملاً — ما شاء الله!",
        emoji = "👑",
        afterStepIndex = 29,
        requiredCount = 30,
        currentCount = completedJuzCount.coerceAtMost(30),
        isUnlocked = completedJuzCount >= 30
      )
    )

    val gatesByIdx = gates.groupBy { it.afterStepIndex }
    val trailItems = ArrayList<FullQuranTrailItem>(juzNodes.size + gates.size)
    juzNodes.forEachIndexed { idx, node ->
      trailItems.add(FullQuranTrailItem.JuzStep(node))
      gatesByIdx[idx]?.forEach { gate ->
        trailItems.add(FullQuranTrailItem.MilestoneGate(gate))
      }
    }

    val nextJuz = juzNodes.firstOrNull {
      it.status == JourneyNodeStatus.IN_PROGRESS || it.status == JourneyNodeStatus.NEXT_UP
    }?.juzSpec?.juzNumber ?: 30

    return FullQuranJourneyPlan(
      juzNodes = juzNodes,
      gates = gates,
      trailItems = trailItems,
      completedJuzCount = completedJuzCount,
      completedSurahsTotal = completedSurahsTotal,
      badgedSurahsTotal = badgedSurahsTotal,
      unlockedGateCount = gates.count { it.isUnlocked },
      nextRecommendedJuz = nextJuz
    )
  }

  private fun buildGatesForSurahTrail(
    scope: SurahJourneyScope,
    selectedJuzNumber: Int?,
    nodes: List<SurahJourneyNode>,
    completedCount: Int
  ): List<JourneyMilestoneGate> {
    if (nodes.isEmpty()) return emptyList()

    if (selectedJuzNumber != null) {
      val total = nodes.size
      val half = (total / 2).coerceAtLeast(1)
      return buildList {
        if (total >= 4) {
          add(
            JourneyMilestoneGate(
              id = "juz_${selectedJuzNumber}_half",
              titleEn = "Halfway Milestone",
              titleAr = "منتصف الجزء $selectedJuzNumber",
              subtitleEn = "Complete $half Surahs in Juz $selectedJuzNumber",
              subtitleAr = "أتمّ $half سور في الجزء $selectedJuzNumber",
              emoji = "🏮",
              afterStepIndex = half - 1,
              requiredCount = half,
              currentCount = completedCount.coerceAtMost(half),
              isUnlocked = completedCount >= half
            )
          )
        }
        add(
          JourneyMilestoneGate(
            id = "juz_${selectedJuzNumber}_complete",
            titleEn = "Juz $selectedJuzNumber Master Gate",
            titleAr = "تاج إتمام الجزء $selectedJuzNumber",
            subtitleEn = "Complete all $total Surahs of Juz $selectedJuzNumber",
            subtitleAr = "أتمّ جميع سور الجزء $selectedJuzNumber",
            emoji = "👑",
            afterStepIndex = nodes.lastIndex,
            requiredCount = total,
            currentCount = completedCount.coerceAtMost(total),
            isUnlocked = completedCount >= total
          )
        )
      }
    }

    if (scope == SurahJourneyScope.JUZ_TABARAK) {
      return listOf(
        JourneyMilestoneGate(
          id = "tabarak_half",
          titleEn = "Tabarak Lantern Gate",
          titleAr = "بوابة منتصف جزء تبارك",
          subtitleEn = "Complete 5 Surahs in Juz 29",
          subtitleAr = "أتمّ ٥ سور في جزء تبارك",
          emoji = "🏮",
          afterStepIndex = 4.coerceAtMost(nodes.lastIndex),
          requiredCount = 5,
          currentCount = completedCount.coerceAtMost(5),
          isUnlocked = completedCount >= 5
        ),
        JourneyMilestoneGate(
          id = "tabarak_crown",
          titleEn = "Juz Tabarak Crown",
          titleAr = "تاج جزء تبارك",
          subtitleEn = "Complete all 11 Surahs of Juz 29",
          subtitleAr = "أتمّ جميع سور جزء تبارك الإحدى عشرة",
          emoji = "👑",
          afterStepIndex = nodes.lastIndex,
          requiredCount = nodes.size,
          currentCount = completedCount.coerceAtMost(nodes.size),
          isUnlocked = completedCount >= nodes.size
        )
      )
    }

    // Default: Juz 'Amma Adventure (38 Surahs: Al-Fatiha + 114 down to 78)
    val fourShieldsCount = nodes.take(4).count { it.isCompleted }
    return listOf(
      JourneyMilestoneGate(
        id = "amma_four_shields",
        titleEn = "The Four Shields",
        titleAr = "بوابة الفاتحة والمعوذات",
        subtitleEn = "Complete Al-Fatiha, An-Nas, Al-Falaq & Al-Ikhlas",
        subtitleAr = "أتمّ سورة الفاتحة والناس والفلق والإخلاص",
        emoji = "🛡️",
        afterStepIndex = 3.coerceAtMost(nodes.lastIndex),
        requiredCount = 4,
        currentCount = fourShieldsCount,
        isUnlocked = fourShieldsCount >= 4
      ),
      JourneyMilestoneGate(
        id = "amma_oasis_10",
        titleEn = "Oasis of Short Surahs",
        titleAr = "واحة قصار السور",
        subtitleEn = "Complete 10 Surahs on the trail",
        subtitleAr = "أتمّ ١٠ سور في رحلة جزء عمّ",
        emoji = "🌴",
        afterStepIndex = 9.coerceAtMost(nodes.lastIndex),
        requiredCount = 10,
        currentCount = completedCount.coerceAtMost(10),
        isUnlocked = completedCount >= 10
      ),
      JourneyMilestoneGate(
        id = "amma_lantern_20",
        titleEn = "Lantern Bridge",
        titleAr = "جسر الفوانيس المضيء",
        subtitleEn = "Complete 20 Surahs on the trail",
        subtitleAr = "أتمّ ٢٠ سورة في رحلة جزء عمّ",
        emoji = "🏮",
        afterStepIndex = 19.coerceAtMost(nodes.lastIndex),
        requiredCount = 20,
        currentCount = completedCount.coerceAtMost(20),
        isUnlocked = completedCount >= 20
      ),
      JourneyMilestoneGate(
        id = "amma_starlight_30",
        titleEn = "Starlight Summit",
        titleAr = "قمة النجوم",
        subtitleEn = "Complete 30 Surahs on the trail",
        subtitleAr = "أتمّ ٣٠ سورة في رحلة جزء عمّ",
        emoji = "🌠",
        afterStepIndex = 29.coerceAtMost(nodes.lastIndex),
        requiredCount = 30,
        currentCount = completedCount.coerceAtMost(30),
        isUnlocked = completedCount >= 30
      ),
      JourneyMilestoneGate(
        id = "amma_crown_38",
        titleEn = "Juz 'Amma Crown",
        titleAr = "تاج جزء عمّ الكامل",
        subtitleEn = "Complete Al-Fatiha & all 37 Surahs of Juz 'Amma!",
        subtitleAr = "أتمّ الفاتحة وجميع سور جزء عمّ السبع والثلاثين!",
        emoji = "🕌",
        afterStepIndex = nodes.lastIndex,
        requiredCount = nodes.size,
        currentCount = completedCount.coerceAtMost(nodes.size),
        isUnlocked = completedCount >= nodes.size
      )
    )
  }
}
