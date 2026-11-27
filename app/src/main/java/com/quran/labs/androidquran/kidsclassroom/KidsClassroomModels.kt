package com.quran.labs.androidquran.kidsclassroom

import com.quran.data.model.SuraAyah
import kotlin.math.abs
import kotlin.math.max

/**
 * Stage of the Children Learning Mode classroom loop.
 */
enum class ClassroomStage {
  IDLE,
  DOWNLOADING_MODELS,
  QARI_RECITING,
  KIDS_REPEATING,
  CHILD_RECITING,
  TASMEE_STREAMING,
  SCORING,
  TURN_PASSED,
  TURN_RETRY
}

/**
 * Word-level grading status rendered both on the Tajweed Mushaf page and in the Kids Classroom UI:
 * - [CORRECT]: `✓` emerald
 * - [TASHKEEL_ERROR]: `حركة` amber
 * - [WORD_ERROR]: `خطأ` red
 * - [MISSING]: `نسيان` dark red
 */
enum class WordGradeStatus {
  PENDING,
  ACTIVE_PLAYBACK,
  CORRECT,
  TASHKEEL_ERROR,
  WORD_ERROR,
  MISSING
}

/**
 * Graded result for a single Quranic word in the current classroom turn.
 *
 * @property wordIndexInTurn 0-based index within [TurnSpec.uthmaniWords]
 * @property mushafWordPosition 1-based word position within the Ayah on the Tajweed Mushaf page
 * @property uthmaniWord Uthmani script word
 * @property normalizedWord Standard vocalized Arabic word
 * @property status Grading status
 * @property tokenConfidence Selective monotonic BPE posterior confidence in `[0.0f, 1.0f]`
 * @property matchedHypWord Recognized child word (if aligned)
 */
data class WordGrade(
  val wordIndexInTurn: Int,
  val mushafWordPosition: Int,
  val uthmaniWord: String,
  val normalizedWord: String,
  val status: WordGradeStatus,
  val tokenConfidence: Float = 0f,
  val matchedHypWord: String? = null
)

/**
 * Complete evaluation result for a child's recitation turn.
 */
data class TurnEvaluationResult(
  val suraAyah: SuraAyah,
  val turnIndex: Int,
  val totalTurns: Int,
  val scorePercent: Double,
  val wordAccuracy: Double,
  val tashkeelAccuracy: Double,
  val confidenceScore: Double,
  val passed: Boolean,
  val noSpeechDetected: Boolean,
  val decodedTranscript: String,
  val wordGrades: List<WordGrade>
)

/**
 * Precomputed speaker turn specification from `minshawi_teacher_full.json` (`tilawi-minshawi-teacher-v2-natural`).
 */
data class TurnSpec(
  val turnIndex: Int,
  val totalTurns: Int,
  val wordStartIdx: Int,
  val wordEndIdx: Int,
  val qariStartSec: Double,
  val qariEndSec: Double,
  val kidsStartSec: Double,
  val kidsEndSec: Double,
  val contiguousPlayback: Boolean,
  val cutReason: String,
  val uthmaniWords: List<String>,
  val normalizedWords: List<String>,
  val qariOnnxWords: List<String>,
  val tokenIds: List<Int>
) {
  val effectiveQariStartMs: Long
    get() = (qariStartSec.coerceAtLeast(0.0) * 1000.0).toLong()

  val effectiveQariEndMs: Long
    get() = (max(qariEndSec, qariStartSec + 0.4) * 1000.0).toLong()

  val effectiveKidsStartMs: Long
    get() = (kidsStartSec.coerceAtLeast(0.0) * 1000.0).toLong()

  val effectiveKidsEndMs: Long
    get() = (max(kidsEndSec, kidsStartSec + 0.4) * 1000.0).toLong()

  val effectiveContiguousPlayback: Boolean
    get() = contiguousPlayback && abs(kidsStartSec - qariEndSec) <= 0.35

  /**
   * Dynamic Stage 3 maximum timeout in seconds:
   * `maxOf(12.0f, (turn.qari_end_sec - turn.qari_start_sec) * 1.6f + 4.0f)`
   */
  fun computeChildMaxTimeoutSec(): Float {
    val qariSpanSec = (qariEndSec - qariStartSec).coerceAtLeast(0.0).toFloat()
    return maxOf(12.0f, qariSpanSec * 1.6f + 4.0f)
  }

  companion object {
    /**
     * Sanitizes turns from `minshawi_teacher_full.json` / `minshawi_teacher_juz30.json`:
     * 1. Drops leading/intermediate non-lexical Isti'adhah/Basmalah turns that have empty
     *    `uthmani_words` when at least one non-empty turn exists (e.g. `2:1` `الٓمٓ`).
     * 2. Repairs any upstream timestamp inversions so `ExoPlayer` seek/pause boundaries are monotonic.
     */
    fun sanitizeTurns(rawTurns: List<TurnSpec>, fileDurationSec: Double): List<TurnSpec> {
      if (rawTurns.isEmpty()) return emptyList()
      val nonEmptyTurns = if (rawTurns.size > 1 && rawTurns.any { it.uthmaniWords.isNotEmpty() }) {
        rawTurns.filter { it.uthmaniWords.isNotEmpty() }
      } else {
        rawTurns
      }

      val count = nonEmptyTurns.size
      return nonEmptyTurns.mapIndexed { idx, turn ->
        var qStart = turn.qariStartSec.coerceAtLeast(0.0)
        var qEnd = turn.qariEndSec
        var kStart = turn.kidsStartSec
        var kEnd = turn.kidsEndSec

        if (qEnd <= qStart) {
          val prevEnd = if (idx > 0) nonEmptyTurns[idx - 1].kidsEndSec else 0.0
          if (prevEnd >= 0.0 && prevEnd < qEnd) {
            qStart = prevEnd
          } else {
            qEnd = (kStart.takeIf { it > qStart } ?: (qStart + 2.5))
          }
        }
        if (kStart < qEnd - 0.25) {
          kStart = qEnd
        }
        if (kEnd <= kStart) {
          kEnd = if (fileDurationSec > kStart) fileDurationSec else (kStart + (qEnd - qStart).coerceAtLeast(2.0))
        }
        val isContiguous = turn.contiguousPlayback && abs(kStart - qEnd) <= 0.35
        turn.copy(
          turnIndex = idx,
          totalTurns = count,
          qariStartSec = qStart,
          qariEndSec = qEnd,
          kidsStartSec = kStart,
          kidsEndSec = kEnd,
          contiguousPlayback = isContiguous
        )
      }
    }
  }
}

/**
 * Supported AI Teacher reciters for Children Learning Mode (`teacher_qaris_config.json`).
 * [HUSARY_MUALLIM] is both the default reciter and the canonical stop reference.
 */
enum class KidsTeacherQari(
  val qariId: String,
  val nameEn: String,
  val nameAr: String,
  val hasChildrenRepeat: Boolean,
  val streamBaseUrl: String,
  val fullJsonFileName: String,
  val fallbackAssetFileName: String
) {
  HUSARY_MUALLIM(
    qariId = "husary_muallim",
    nameEn = "Mahmoud Khalil Al-Husary (Muallim)",
    nameAr = "محمود خليل الحصري (المعلم)",
    hasChildrenRepeat = false,
    streamBaseUrl = "https://everyayah.com/data/Husary_Muallim_128kbps/",
    fullJsonFileName = "husary_muallim_full.json",
    fallbackAssetFileName = "husary_muallim_juz30.json"
  ),
  MINSHAWI_TEACHER(
    qariId = "minshawi_teacher",
    nameEn = "Mohamed Siddiq Al-Minshawi (Teacher with Children)",
    nameAr = "محمد صديق المنشاوي (المعلم مع الأطفال)",
    hasChildrenRepeat = true,
    streamBaseUrl = "https://everyayah.com/data/Minshawy_Teacher_128kbps/",
    fullJsonFileName = "minshawi_teacher_full.json",
    fallbackAssetFileName = "minshawi_teacher_juz30.json"
  );

  companion object {
    val DEFAULT: KidsTeacherQari = HUSARY_MUALLIM

    fun fromIdOrDefault(id: String?): KidsTeacherQari {
      if (id.isNullOrBlank()) return DEFAULT
      return entries.firstOrNull { it.qariId == id || it.name == id } ?: DEFAULT
    }
  }
}

/**
 * Root manifest schema for teacher datasets (`husary_muallim_full.json` default,
 * `minshawi_teacher_full.json` aligned to Husary Muallim stops).
 * Both `juz` (`"all"`, `30`, or `null`) and `maxSpeakerSec` (`Double? = null`) are nullable.
 */
data class MinshawiTeacherManifest(
  val schema: String = "tilawi-husary-muallim-v1-reference",
  val streamBaseUrl: String = KidsTeacherQari.DEFAULT.streamBaseUrl,
  val juz: Any? = null,
  val surahRange: List<Int> = listOf(1, 114),
  val totalAyat: Int = 6236,
  val maxSpeakerSec: Double? = null,
  val verses: Map<String, TeacherVerseSpec> = emptyMap(),
  val qariId: String = KidsTeacherQari.DEFAULT.qariId,
  val qariNameEn: String = KidsTeacherQari.DEFAULT.nameEn,
  val qariNameAr: String = KidsTeacherQari.DEFAULT.nameAr,
  val isDefault: Boolean = true,
  val isStopReference: Boolean = true,
  val hasChildrenRepeat: Boolean = KidsTeacherQari.DEFAULT.hasChildrenRepeat
)

/**
 * Precomputed Ayah entry across all 114 Surahs (`1..114`, `6,236` verses) in `minshawi_teacher_full.json`.
 */
data class TeacherVerseSpec(
  val surah: Int,
  val ayah: Int,
  val surahNameAr: String,
  val uthmaniFull: String,
  val audioFile: String,
  val audioUrl: String,
  val fileDurationSec: Double,
  val totalTurns: Int,
  val nextSurah: Int?,
  val nextAyah: Int?,
  val turns: List<TurnSpec>
) {
  val suraAyah: SuraAyah
    get() = SuraAyah(surah, ayah)

  val allUthmaniWords: List<String> by lazy {
    val fromTurns = turns.flatMap { it.uthmaniWords }.filter { it.isNotBlank() }
    if (fromTurns.isNotEmpty()) {
      fromTurns
    } else {
      uthmaniFull.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    }
  }

  val allNormalizedWords: List<String> by lazy {
    val fromTurns = turns.flatMap { it.normalizedWords }.filter { it.isNotBlank() }
    if (fromTurns.size == allUthmaniWords.size) fromTurns else allUthmaniWords
  }

  val allQariOnnxWords: List<String> by lazy {
    val fromTurns = turns.flatMap { it.qariOnnxWords }.filter { it.isNotBlank() }
    if (fromTurns.size == allNormalizedWords.size) fromTurns else allNormalizedWords
  }

  val allTokenIds: IntArray by lazy {
    turns.flatMap { it.tokenIds }.toIntArray()
  }
}

typealias Juz30VerseSpec = TeacherVerseSpec

/**
 * Surah summary entry for the 1..114 Surah selector in the Kids Tab.
 */
data class KidsSurahInfo(
  val surahNumber: Int,
  val nameAr: String,
  val ayahCount: Int,
  val isLoadedOffline: Boolean
)

/**
 * Download progress state for offline AI teacher assets
 * (`fastconformer_quran_int8.onnx`, `vocab.json`, `minshawi_teacher_full.json`).
 */
data class AssetDownloadProgress(
  val isReady: Boolean = false,
  val isDownloading: Boolean = false,
  val currentFileName: String = "",
  val bytesDownloaded: Long = 0L,
  val totalBytes: Long = 138_000_000L,
  val errorMessage: String? = null
) {
  val progressPercent: Int
    get() = if (totalBytes <= 0L) 0 else ((bytesDownloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
}

/**
 * Sub-modes within Children Learning Mode across Surahs `1..114`:
 * - [LISTEN_AND_REPEAT]: **Mode A ("Teacher & Kids Repeat" / `المعلم وترديد الأطفال`)** —
 *   Graded 3-Stage Loop: Stage 1 (Sheikh) -> Stage 2 (Audio Kids) -> Stage 3 (Child's Turn + ONNX Grader).
 * - [RECITE_AND_REVEAL]: **Mode B ("Recite & Reveal / Tasmee'" / `التسميع الحي`)** —
 *   Streaming Blank-to-Revealed Mushaf with no scoring (`ExoPlayer` off, `350 ms` hops over `4.5s` rolling buffer).
 * - [RECITE_ONLY]: Direct single-turn Stage 3 graded recitation without Sheikh/Kids audio playback.
 */
enum class KidsPracticeMode {
  LISTEN_AND_REPEAT,
  RECITE_AND_REVEAL,
  RECITE_ONLY;

  val isTeacherKidsRepeat: Boolean
    get() = this == LISTEN_AND_REPEAT

  val isStreamingTasmee: Boolean
    get() = this == RECITE_AND_REVEAL
}

/**
 * 10 distinct full-screen celebration styles randomly chosen after a child completes a full Ayah.
 */
enum class KidsCelebrationTheme(
  val titleEn: String,
  val titleAr: String,
  val subtitleEn: String,
  val subtitleAr: String,
  val badgeEmoji: String
) {
  GOLDEN_LANTERNS(
    titleEn = "MashaAllah! Shining Bright!",
    titleAr = "ما شاء الله! نورٌ يتلألأ",
    subtitleEn = "Your recitation lit up the golden lanterns",
    subtitleAr = "تلاوتك أضاءت الفوانيس الذهبية",
    badgeEmoji = "🏮"
  ),
  FIREWORKS_FESTIVAL(
    titleEn = "Superstar Reciter!",
    titleAr = "أحسنت يا بطل!",
    subtitleEn = "Fireworks across the sky for your Ayah",
    subtitleAr = "ألعاب نارية احتفالاً بإتقانك للآية",
    badgeEmoji = "🎆"
  ),
  CONFETTI_CARNIVAL(
    titleEn = "Amazing Recitation!",
    titleAr = "ممتاز جداً!",
    subtitleEn = "A shower of joy for every word you recited",
    subtitleAr = "قراءة متقنة تستحق كل الفرح",
    badgeEmoji = "🎉"
  ),
  ROCKET_STARSHIP(
    titleEn = "Blast Off to the Stars!",
    titleAr = "انطلق نحو النجوم!",
    subtitleEn = "Soaring higher with every memorized Ayah",
    subtitleAr = "تحلّق عالياً مع كل آية تحفظها",
    badgeEmoji = "🚀"
  ),
  RAINBOW_SUNSHINE(
    titleEn = "Wonderful Job!",
    titleAr = "رائع ومبدع!",
    subtitleEn = "Bright as a rainbow after the rain",
    subtitleAr = "تلاوة مشرقة كقوس قزح جميل",
    badgeEmoji = "🌈"
  ),
  FLOATING_BALLOONS(
    titleEn = "BarakAllah Feek!",
    titleAr = "بارك الله فيك!",
    subtitleEn = "Celebration balloons rising for your hard work",
    subtitleAr = "بالونات الفرح ترتفع لإنجازك الجميل",
    badgeEmoji = "🎈"
  ),
  GARDEN_BUTTERFLIES(
    titleEn = "Sweet Like Paradise Flowers!",
    titleAr = "تلاوة عذبة كالزهر!",
    subtitleEn = "Your voice made the whole garden bloom",
    subtitleAr = "صوتك الجميل أزهر الحديقة كلها",
    badgeEmoji = "🦋"
  ),
  ROYAL_CROWN_TROPHY(
    titleEn = "Little Quran Champion!",
    titleAr = "بطل القرآن الصغير!",
    subtitleEn = "You earned the golden crown of honor",
    subtitleAr = "نلت التاج الذهبي بإتقانك الرائع",
    badgeEmoji = "👑"
  ),
  OCEAN_PEARLS(
    titleEn = "A Pearl of Light!",
    titleAr = "لؤلؤة من نور!",
    subtitleEn = "Precious and crystal clear recitation",
    subtitleAr = "تلاوة صافية كاللؤلؤ المضيء",
    badgeEmoji = "🫧"
  ),
  DESERT_MOON_OASIS(
    titleEn = "TabarakAllah, Well Done!",
    titleAr = "تبارك الله أحسنت!",
    subtitleEn = "Shining beneath the crescent and shooting stars",
    subtitleAr = "إتقان جميل تحت الهلال والنجوم",
    badgeEmoji = "🌙"
  )
}

/**
 * Configurable pass-threshold difficulty level required to advance to the next Ayah.
 * - [STARTER_70]: 70% score threshold (beginner friendly)
 * - [INTERMEDIATE_80]: 80% score threshold (balanced default)
 * - [PROFICIENT_90]: 90% score threshold (proficient mastery)
 */
enum class KidsDifficultyLevel(
  val passThresholdPercent: Int,
  val minWordAccuracy: Double
) {
  STARTER_70(passThresholdPercent = 70, minWordAccuracy = 0.65),
  INTERMEDIATE_80(passThresholdPercent = 80, minWordAccuracy = 0.75),
  PROFICIENT_90(passThresholdPercent = 90, minWordAccuracy = 0.85);

  companion object {
    fun fromNameOrDefault(name: String?): KidsDifficultyLevel {
      if (name.isNullOrBlank()) return INTERMEDIATE_80
      return entries.firstOrNull { it.name == name } ?: INTERMEDIATE_80
    }
  }
}

/**
 * Full-screen celebration payload emitted when a learner completes a full Surah.
 */
data class AyahCelebrationEvent(
  val eventId: Long,
  val suraAyah: SuraAyah,
  val surahNameAr: String,
  val scorePercent: Int,
  val starsEarned: Int,
  val totalStars: Int,
  val theme: KidsCelebrationTheme,
  val ayahRepetitionCompleted: Int,
  val ayahRepetitionTarget: Int,
  val surahRepetitionCompleted: Int,
  val surahRepetitionTarget: Int,
  val isSurahCompleted: Boolean = true,
  val missedWordsInSurahCount: Int = 0,
  val totalAyatInSurah: Int = 1,
  val awardedSurahBadge: SurahCompletionBadge? = null,
  val isNewSurahBadge: Boolean = false,
  val isUpgradedSurahBadge: Boolean = false
)

/**
 * Immutable UI state for Children Learning Mode (Tajweed Mushaf Kids Mode Memorization HUD).
 */
data class KidsClassroomUiState(
  val isEnabled: Boolean = false,
  val practiceMode: KidsPracticeMode = KidsPracticeMode.LISTEN_AND_REPEAT,
  val stage: ClassroomStage = ClassroomStage.IDLE,
  val isAutoTransitionPending: Boolean = false,
  val isRetryAttempt: Boolean = false,
  val currentAyah: SuraAyah = SuraAyah(78, 1),
  val surahNameAr: String = "النبأ",
  val currentTurnIndex: Int = 0,
  val totalTurns: Int = 1,
  val currentTurn: TurnSpec? = null,
  val ayahWords: List<String> = emptyList(),
  val revealedCount: Int = 0,
  val isFullQuranLoaded: Boolean = false,
  val childMaxTimeoutSec: Float = 12.0f,
  val activePlaybackWordPosition: Int = -1,
  val autoNextEnabled: Boolean = true,
  val ayahRepeatTarget: Int = 1,
  val ayahRepeatCompleted: Int = 0,
  val surahRepeatTarget: Int = 1,
  val surahRepeatCompleted: Int = 0,
  val difficultyLevel: KidsDifficultyLevel = KidsDifficultyLevel.INTERMEDIATE_80,
  val repeatFromFirstMistakeOnly: Boolean = false,
  val activeMistakeTailStartWordIndex: Int = 0,
  val pendingCelebration: AyahCelebrationEvent? = null,
  val isDockedTop: Boolean = false,
  val isMinimized: Boolean = false,
  val micLevelRms: Float = 0f,
  val voicedSpeechMs: Long = 0L,
  val isVadActive: Boolean = false,
  val waveformBars: List<Float> = List(16) { 0f },
  val downloadProgress: AssetDownloadProgress = AssetDownloadProgress(),
  val lastEvaluation: TurnEvaluationResult? = null,
  val wordGradesByMushafPos: Map<Int, WordGrade> = emptyMap(),
  val statusMessageOverride: String? = null,
  val isSlowMoEnabled: Boolean = false,
  val useAudioCluesMode: Boolean = false,
  val streamingMatchedWordCount: Int = 0,
  val streamingPartialTranscript: String = "",
  val lastStarsEarned: Int = 0,
  val isStreamingAsrReady: Boolean = false,
  val selectedTeacherQari: KidsTeacherQari = KidsTeacherQari.DEFAULT
)
