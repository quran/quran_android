package com.quran.labs.androidquran.kidsclassroom

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Visible anatomical tongue posture drawn inside Ustadh Noor's oral cavity.
 */
enum class TonguePose {
  HIDDEN,            // Tongue not visible (closed lips, tight Waw, sibilants)
  FLAT_RELAXED,      // Low/relaxed inside open mouth (Fatha, Alif Madd, throat letters)
  BETWEEN_TEETH,     // Pink tongue tip clearly peeking BETWEEN upper & lower teeth (ث ذ ظ)
  TIP_UP_ALVEOLAR,   // Pink tongue tip visibly lifted UP touching behind upper teeth (ن ل ر ت د ط)
  HOVER_MID,         // Tongue hovering mid-cavity without touching teeth (Ikhfa green ن)
  ARCHED_MID_PALATE  // Mid-tongue visibly raised inside mid-open mouth (ج ش ي ض)
}

/**
 * Precision numeric articulation geometry for smooth 60 fps Compose Canvas interpolation.
 *
 * Enforces the Anti-"Always Open Mouth" rule:
 * - Closed (`0.00f`) for silence, `م`, `ب` press, and Iqlab.
 * - Narrow (`0.14–0.28f`) for `و`, `ف`, `س/ز/ص`, `ث/ذ/ظ`, short/long Damma & Kasra.
 * - Middle (`0.32–0.46f`) for alveolar/palatal/throat consonants and `SHORT_FATHA`.
 * - Wide (`0.78f`) reserved **exclusively** for `MADD_ALIF` (`ا / آ / ـٰ`).
 */
data class VisemeGeometry(
  val jawOpen: Float = 0.0f,        // 0.0f (closed) -> 0.40f (middle) -> 0.82f (full Madd)
  val mouthWidth: Float = 0.64f,    // 0.35f (tight Waw) -> 0.65f (neutral) -> 0.95f (wide Kasra)
  val lipPucker: Float = 0.0f,      // 0.0f (flat) -> 1.0f (strong forward circular rounding)
  val lipPress: Float = 0.15f,      // 0.0f -> 1.0f (firm bilabial press for ب / م)
  val upperTeethVis: Float = 0.0f,  // 0.0f -> 1.0f (visibility of upper front teeth)
  val lowerTeethVis: Float = 0.0f,  // 0.0f -> 1.0f (visibility of lower front teeth)
  val tonguePose: TonguePose = TonguePose.HIDDEN,
  val tongueLift: Float = 0.0f,     // 0.0f (low) -> 1.0f (touching upper gum ridge)
  val cheekFullness: Float = 0.0f   // 0.0f (light) -> 1.0f (heavy Tafkheem rounding)
)

/**
 * 18 discrete Arabic Makharij, Harakat, and Tajweed mouth shapes articulated by Ustadh Noor.
 */
enum class ArabicViseme {
  REST_CLOSED,        // 1. Silence, breath, Waqf pause (jaw 0.00f)
  MID_NEUTRAL,        // 2. Light Sukun transition, soft هـ / ء (jaw 0.24f)
  SHORT_FATHA,        // 3. Short Fatha ـَ / ـً (jaw 0.46f natural middle opening)
  MADD_ALIF,          // 4. Alif Madd ا / آ / ـٰ — 2/4/6 counts (jaw 0.78f wide vertical drop)
  SHORT_DAMMA,        // 5. Short Damma ـُ / ـٌ (jaw 0.24f, pucker 0.78f)
  MADD_WAW,           // 6. Waw Madd و — 2/4/6 counts (jaw 0.28f, pucker 0.92f)
  SHORT_KASRA,        // 7. Short Kasra ـِ / ـٍ (jaw 0.22f, width 0.82f)
  MADD_YA,            // 8. Ya Madd ي / ى — 2/4/6 counts (jaw 0.26f, width 0.94f)
  LIPS_BA_POP,        // 9. Ba ب two-phase plosive (0.00f firm press -> 0.30f release)
  LIPS_MEEM_HOLD,     // 10. Meem م / مّ & Iqlab ن→ب (jaw 0.00f cushioned closed hold)
  LIPS_WAW_CONS,      // 11. Consonant Waw وَ / وِ / وُ / وْ (jaw 0.14f, tight pucker 1.0f)
  TEETH_FA,           // 12. Fa ف labiodental (jaw 0.16f, upper teeth on lower lip)
  TONGUE_INTERDENTAL, // 13. ث / ذ / ظ (jaw 0.22f, pink tongue tip between teeth)
  TONGUE_ALVEOLAR_UP, // 14. ن / ل / ر / ت / د / ط (jaw 0.36f, tongue tip lifted to upper ridge)
  TONGUE_IKHFA_HOVER, // 15. Green Ikhfa ن / Tanween (jaw 0.32f, tongue hovering mid-mouth)
  TONGUE_MID_PALATAL, // 16. ج / ش / ي (cons) / ض (jaw 0.32f, arched mid-tongue)
  TEETH_SIBILANT,     // 17. س / ز / ص (jaw 0.14f, upper & lower teeth nearly touching)
  THROAT_MID_OPEN;    // 18. ع / ح / خ / غ / ق / ك (jaw 0.40f controlled middle opening)

  /**
   * True when entering crisp consonant closure/contact that requires fast spring attack (`650f`).
   */
  val usesFastConsonantAttack: Boolean
    get() = this == LIPS_BA_POP ||
      this == LIPS_MEEM_HOLD ||
      this == LIPS_WAW_CONS ||
      this == TEETH_FA ||
      this == TONGUE_INTERDENTAL ||
      this == TONGUE_ALVEOLAR_UP ||
      this == TEETH_SIBILANT
}

/**
 * Scheduled lip-sync keyframe across Sheikh Al-Minshawi's recitation window `[qariStartMs, qariEndMs]`.
 */
data class TajweedVisemeKeyframe(
  val startMs: Long,
  val endMs: Long,
  val viseme: ArabicViseme,
  val ruleType: TajweedRuleType,
  val tajweedColor: Color,
  val wordIndex: Int,
  val harakahHint: String,
  val consonantChar: Char = ' ',
  val isQalqalahBounce: Boolean = false,
  val isTwoPhaseStandaloneBa: Boolean = false
) {
  val durationMs: Long
    get() = max(1L, endMs - startMs)
}

/**
 * Instantaneous 60 fps sample consumed by [TeacherAvatarCanvas].
 */
data class VisemeSample(
  val viseme: ArabicViseme = ArabicViseme.REST_CLOSED,
  val geometry: VisemeGeometry = VisemeGeometry(),
  val mouthOpenness: Float = 0f,
  val isVoiceActive: Boolean = false,
  val ruleType: TajweedRuleType = TajweedRuleType.NORMAL,
  val tajweedColor: Color = TajweedRuleType.NORMAL.defaultColor,
  val wordIndex: Int = -1,
  val harakahHint: String = "",
  val maddProgress: Float = 0f,
  val isQalqalahBounce: Boolean = false
)

/**
 * Converts [TajweedPhonemeToken]s from [TajweedSpanAdapter] into a Tajweed-focused timeline of
 * [TajweedVisemeKeyframe]s across Sheikh Al-Minshawi's `[qariStartMs, qariEndMs]` audio turn.
 *
 * Tajweed-Only Articulation Design:
 * 1. Only active Tajweed rules animate Ustadh Noor's mouth and visual cues:
 *    - `GHUNNAH_MEEM` (`2.6×`): closed cushioned lips (`LIPS_MEEM_HOLD`) + green nose sparkle.
 *    - `GHUNNAH_NOON` (`2.6×`): tongue tip held up on upper ridge (`TONGUE_ALVEOLAR_UP`) + green nose sparkle.
 *    - `IKHFA` (`2.4×`): hovering mid-cavity tongue (`TONGUE_IKHFA_HOVER`) + green nose sparkle.
 *    - `IQLAB` (`2.5×`): soft lip touch (`LIPS_MEEM_HOLD`) + green nose sparkle.
 *    - `MADD_2` (`2.2×`), `MADD_4_5` (`4.5×`), `MADD_6` (`6.0×`): sustained Madd shape + vertical Madd bar.
 *    - `QALQALAH` (`1.35×`): crisp contact -> springy micro-open bounce + blue bounce ring.
 *    - `TAFKHEEM` (`1.15×`): heavy letter rounding + fuller cheeks.
 *    - `SILENT` (`0.0×`): skipped completely.
 * 2. All other (`NORMAL`) letters keep Ustadh Noor's mouth **cleanly closed** (`REST_CLOSED`) while
 *    preserving timing weight so Tajweed cues stay tightly synced to Sheikh Al-Minshawi's voice.
 * 3. ExoPlayer RMS silence gating (`RMS < 0.06f` for `>= 80 ms` -> `REST_CLOSED`) and `0.75 + 0.25*RMS`
 *    modulation never force closed-lip rules (`GHUNNAH_MEEM` / `IQLAB`) open.
 */
object TajweedVisemePlanner {

  const val SILENCE_RMS_THRESHOLD = 0.06f
  const val SILENCE_HOLD_MS = 80L

  private const val FATHA = '\u064E'
  private const val DAMMA = '\u064F'
  private const val KASRA = '\u0650'
  private const val FATHATAN = '\u064B'
  private const val DAMMATAN = '\u064C'
  private const val KASRATAN = '\u064D'
  private const val SUKOON = '\u0652'

  private val PURE_MADD_LETTERS = setOf('ا', 'آ', 'و', 'ي', 'ى')

  private data class WeightedStep(
    val weight: Float,
    val viseme: ArabicViseme,
    val ruleType: TajweedRuleType,
    val tajweedColor: Color,
    val wordIndex: Int,
    val harakahHint: String,
    val consonantChar: Char,
    val isQalqalahBounce: Boolean = false,
    val isTwoPhaseStandaloneBa: Boolean = false
  )

  /**
   * Canonical numeric geometry for each of the 18 [ArabicViseme]s with letter-specific Makhraj
   * refinements (`ظ`, `ط`, `ش`, `ض`, `ص`, `خ/غ/ق`, and `IQLAB` soft lip touch).
   */
  fun defaultGeometryFor(
    viseme: ArabicViseme,
    consonantChar: Char = ' ',
    ruleType: TajweedRuleType = TajweedRuleType.NORMAL
  ): VisemeGeometry {
    val base = when (viseme) {
      ArabicViseme.REST_CLOSED -> VisemeGeometry(
        jawOpen = 0.00f, mouthWidth = 0.64f, lipPucker = 0.0f, lipPress = 0.15f,
        upperTeethVis = 0.0f, lowerTeethVis = 0.0f, tonguePose = TonguePose.HIDDEN,
        tongueLift = 0.0f, cheekFullness = 0.0f
      )
      ArabicViseme.MID_NEUTRAL -> VisemeGeometry(
        jawOpen = 0.24f, mouthWidth = 0.65f, lipPucker = 0.05f, lipPress = 0.0f,
        upperTeethVis = 0.32f, lowerTeethVis = 0.16f, tonguePose = TonguePose.FLAT_RELAXED,
        tongueLift = 0.15f, cheekFullness = 0.0f
      )
      ArabicViseme.SHORT_FATHA -> VisemeGeometry(
        jawOpen = 0.46f, mouthWidth = 0.70f, lipPucker = 0.0f, lipPress = 0.0f,
        upperTeethVis = 0.55f, lowerTeethVis = 0.25f, tonguePose = TonguePose.FLAT_RELAXED,
        tongueLift = 0.12f, cheekFullness = 0.0f
      )
      ArabicViseme.MADD_ALIF -> VisemeGeometry(
        jawOpen = 0.78f, mouthWidth = 0.74f, lipPucker = 0.0f, lipPress = 0.0f,
        upperTeethVis = 0.75f, lowerTeethVis = 0.45f, tonguePose = TonguePose.FLAT_RELAXED,
        tongueLift = 0.10f, cheekFullness = 0.0f
      )
      ArabicViseme.SHORT_DAMMA -> VisemeGeometry(
        jawOpen = 0.24f, mouthWidth = 0.45f, lipPucker = 0.78f, lipPress = 0.0f,
        upperTeethVis = 0.10f, lowerTeethVis = 0.05f, tonguePose = TonguePose.HIDDEN,
        tongueLift = 0.0f, cheekFullness = 0.15f
      )
      ArabicViseme.MADD_WAW -> VisemeGeometry(
        jawOpen = 0.28f, mouthWidth = 0.40f, lipPucker = 0.92f, lipPress = 0.0f,
        upperTeethVis = 0.05f, lowerTeethVis = 0.0f, tonguePose = TonguePose.HIDDEN,
        tongueLift = 0.0f, cheekFullness = 0.20f
      )
      ArabicViseme.SHORT_KASRA -> VisemeGeometry(
        jawOpen = 0.22f, mouthWidth = 0.82f, lipPucker = 0.0f, lipPress = 0.0f,
        upperTeethVis = 0.82f, lowerTeethVis = 0.45f, tonguePose = TonguePose.ARCHED_MID_PALATE,
        tongueLift = 0.45f, cheekFullness = 0.0f
      )
      ArabicViseme.MADD_YA -> VisemeGeometry(
        jawOpen = 0.26f, mouthWidth = 0.94f, lipPucker = 0.0f, lipPress = 0.0f,
        upperTeethVis = 0.90f, lowerTeethVis = 0.72f, tonguePose = TonguePose.ARCHED_MID_PALATE,
        tongueLift = 0.65f, cheekFullness = 0.0f
      )
      ArabicViseme.LIPS_BA_POP -> VisemeGeometry(
        jawOpen = 0.00f, mouthWidth = 0.62f, lipPucker = 0.0f, lipPress = 1.0f,
        upperTeethVis = 0.0f, lowerTeethVis = 0.0f, tonguePose = TonguePose.HIDDEN,
        tongueLift = 0.0f, cheekFullness = 0.05f
      )
      ArabicViseme.LIPS_MEEM_HOLD -> VisemeGeometry(
        jawOpen = 0.00f,
        mouthWidth = 0.64f,
        lipPucker = 0.0f,
        lipPress = if (ruleType == TajweedRuleType.IQLAB) 0.58f else 0.70f,
        upperTeethVis = 0.0f,
        lowerTeethVis = 0.0f,
        tonguePose = TonguePose.HIDDEN,
        tongueLift = 0.0f,
        cheekFullness = 0.05f
      )
      ArabicViseme.LIPS_WAW_CONS -> VisemeGeometry(
        jawOpen = 0.14f, mouthWidth = 0.35f, lipPucker = 1.0f, lipPress = 0.0f,
        upperTeethVis = 0.0f, lowerTeethVis = 0.0f, tonguePose = TonguePose.HIDDEN,
        tongueLift = 0.0f, cheekFullness = 0.15f
      )
      ArabicViseme.TEETH_FA -> VisemeGeometry(
        jawOpen = 0.16f, mouthWidth = 0.66f, lipPucker = 0.0f, lipPress = 0.35f,
        upperTeethVis = 1.0f, lowerTeethVis = 0.0f, tonguePose = TonguePose.HIDDEN,
        tongueLift = 0.0f, cheekFullness = 0.0f
      )
      ArabicViseme.TONGUE_INTERDENTAL -> VisemeGeometry(
        jawOpen = 0.22f,
        mouthWidth = 0.68f,
        lipPucker = 0.0f,
        lipPress = 0.0f,
        upperTeethVis = 0.90f,
        lowerTeethVis = 0.55f,
        tonguePose = TonguePose.BETWEEN_TEETH,
        tongueLift = 0.52f,
        cheekFullness = if (consonantChar == 'ظ') 0.65f else 0.0f
      )
      ArabicViseme.TONGUE_ALVEOLAR_UP -> VisemeGeometry(
        jawOpen = 0.36f,
        mouthWidth = 0.68f,
        lipPucker = 0.0f,
        lipPress = 0.0f,
        upperTeethVis = 0.85f,
        lowerTeethVis = 0.35f,
        tonguePose = TonguePose.TIP_UP_ALVEOLAR,
        tongueLift = 0.95f,
        cheekFullness = if (consonantChar == 'ط') 0.70f else 0.0f
      )
      ArabicViseme.TONGUE_IKHFA_HOVER -> VisemeGeometry(
        jawOpen = 0.32f, mouthWidth = 0.66f, lipPucker = 0.05f, lipPress = 0.0f,
        upperTeethVis = 0.72f, lowerTeethVis = 0.35f, tonguePose = TonguePose.HOVER_MID,
        tongueLift = 0.55f, cheekFullness = 0.0f
      )
      ArabicViseme.TONGUE_MID_PALATAL -> VisemeGeometry(
        jawOpen = 0.32f,
        mouthWidth = 0.70f,
        lipPucker = if (consonantChar == 'ش') 0.35f else 0.08f,
        lipPress = 0.0f,
        upperTeethVis = 0.78f,
        lowerTeethVis = 0.45f,
        tonguePose = TonguePose.ARCHED_MID_PALATE,
        tongueLift = 0.72f,
        cheekFullness = if (consonantChar == 'ض') 0.75f else 0.0f
      )
      ArabicViseme.TEETH_SIBILANT -> VisemeGeometry(
        jawOpen = 0.14f,
        mouthWidth = 0.78f,
        lipPucker = if (consonantChar == 'ص') 0.35f else 0.0f,
        lipPress = 0.0f,
        upperTeethVis = 0.90f,
        lowerTeethVis = 0.80f,
        tonguePose = TonguePose.HIDDEN,
        tongueLift = 0.25f,
        cheekFullness = if (consonantChar == 'ص') 0.65f else 0.0f
      )
      ArabicViseme.THROAT_MID_OPEN -> VisemeGeometry(
        jawOpen = 0.40f,
        mouthWidth = 0.66f,
        lipPucker = 0.05f,
        lipPress = 0.0f,
        upperTeethVis = 0.55f,
        lowerTeethVis = 0.30f,
        tonguePose = TonguePose.FLAT_RELAXED,
        tongueLift = 0.15f,
        cheekFullness = if (consonantChar == 'خ' || consonantChar == 'غ' || consonantChar == 'ق') 0.60f else 0.0f
      )
    }

    return if (ruleType == TajweedRuleType.TAFKHEEM) {
      base.copy(
        cheekFullness = maxOf(base.cheekFullness, 0.58f),
        lipPucker = (base.lipPucker + 0.12f).coerceAtMost(1.0f)
      )
    } else {
      base
    }
  }

  /**
   * Builds the co-articulated keyframe timeline for an Ayah or turn across `[startMs, endMs]`.
   */
  fun planKeyframes(
    tokens: List<TajweedPhonemeToken>,
    startMs: Long,
    endMs: Long
  ): List<TajweedVisemeKeyframe> {
    val totalMs = (endMs - startMs).coerceAtLeast(120L)
    if (tokens.isEmpty()) return emptyList()

    val steps = ArrayList<WeightedStep>(tokens.size * 2)
    var i = 0

    while (i < tokens.size) {
      val token = tokens[i]
      val rule = token.ruleType

      // 0. Grey SILENT letters are skipped completely (weight = 0.0f)
      if (rule == TajweedRuleType.SILENT || rule.durationMultiplier <= 0f) {
        i++
        continue
      }

      // Look ahead for following non-silent Madd letter in the SAME word (e.g., بَا, نُو, فِي)
      val nextIdx = findNextNonSilentInSameWord(tokens, i + 1, token.wordIndex)
      val nextToken = nextIdx?.let { tokens[it] }
      val nextIsPureMadd = nextToken != null &&
        (nextToken.isMaddLetter || nextToken.ruleType.maddCounts > 0) &&
        nextToken.baseChar in PURE_MADD_LETTERS &&
        token.baseChar !in setOf('ا', 'آ')

      val mul = rule.durationMultiplier
      val consonantViseme = mapConsonantToViseme(token.baseChar, token.isMaddLetter, token.harakah)
      val shortVowelViseme = mapShortHarakahToViseme(token.harakah)
      val hint = buildHarakahHint(token)

      when {
        // 1. Consonant followed immediately by a pure Madd vowel letter (e.g., بَا, قَالَ, نُوحٍ, فِيهَا)
        // Only articulate the preceding consonant if it carries an active Tajweed rule (Ghunnah or Tafkheem);
        // otherwise keep the mouth closed (~18% onset) and open cleanly on the Madd hold (~82%).
        nextIsPureMadd && nextToken != null -> {
          val maddRule = if (nextToken.ruleType.maddCounts > 0) nextToken.ruleType else TajweedRuleType.MADD_2
          val maddWeight = maddRule.durationMultiplier
          val maddViseme = mapMaddLetterToViseme(nextToken.baseChar, token.harakah)
          val maddHint = buildHarakahHint(nextToken)

          if (rule.isGhunnahFamily) {
            // e.g., إِنَّا / عَمَّا / لَمَّا -> hold full nasal Ghunnah first, then full Madd!
            val nasalViseme = mapGhunnahRuleToViseme(rule, token.baseChar)
            steps.add(
              WeightedStep(
                weight = mul,
                viseme = nasalViseme,
                ruleType = rule,
                tajweedColor = token.tajweedColor,
                wordIndex = token.wordIndex,
                harakahHint = hint,
                consonantChar = token.baseChar
              )
            )
          } else if (rule == TajweedRuleType.TAFKHEEM) {
            // Heavy letter followed by Madd (e.g., قَالَ / الضَّالِّينَ): 18% Tafkheem onset -> 82% Madd hold
            val consWeight = maddWeight * (0.18f / 0.82f)
            steps.add(
              WeightedStep(
                weight = consWeight,
                viseme = consonantViseme,
                ruleType = rule,
                tajweedColor = token.tajweedColor,
                wordIndex = token.wordIndex,
                harakahHint = hint,
                consonantChar = token.baseChar
              )
            )
          } else {
            // Ordinary consonant before Madd: keep mouth CLOSED for the 18% onset so kids focus purely on the Madd
            val consWeight = maddWeight * (0.18f / 0.82f)
            steps.add(
              WeightedStep(
                weight = consWeight,
                viseme = ArabicViseme.REST_CLOSED,
                ruleType = TajweedRuleType.NORMAL,
                tajweedColor = TajweedRuleType.NORMAL.defaultColor,
                wordIndex = token.wordIndex,
                harakahHint = "",
                consonantChar = token.baseChar
              )
            )
          }

          steps.add(
            WeightedStep(
              weight = maddWeight,
              viseme = maddViseme,
              ruleType = maddRule,
              tajweedColor = nextToken.tajweedColor,
              wordIndex = nextToken.wordIndex,
              harakahHint = maddHint,
              consonantChar = nextToken.baseChar
            )
          )
          i = nextIdx + 1
          continue
        }

        // 2. Single-cluster Madd (e.g., Dagger Alif رَحْمَٰنِ, Muqatta'at قٓ/نٓ, or standalone آ/ا/و/ي)
        rule == TajweedRuleType.MADD_2 ||
          rule == TajweedRuleType.MADD_4_5 ||
          rule == TajweedRuleType.MADD_6 -> {
          val maddViseme = mapMaddLetterToViseme(token.baseChar, token.harakah)
          if (token.baseChar !in setOf('ا', 'و', 'ي', 'ى')) {
            // Consonant + Dagger Alif or آ: 18% closed onset -> 82% steady Madd
            steps.add(
              WeightedStep(
                weight = mul * 0.18f,
                viseme = ArabicViseme.REST_CLOSED,
                ruleType = TajweedRuleType.NORMAL,
                tajweedColor = TajweedRuleType.NORMAL.defaultColor,
                wordIndex = token.wordIndex,
                harakahHint = "",
                consonantChar = token.baseChar
              )
            )
            steps.add(
              WeightedStep(
                weight = mul * 0.82f,
                viseme = maddViseme,
                ruleType = rule,
                tajweedColor = token.tajweedColor,
                wordIndex = token.wordIndex,
                harakahHint = hint,
                consonantChar = token.baseChar
              )
            )
          } else {
            steps.add(
              WeightedStep(
                weight = mul,
                viseme = maddViseme,
                ruleType = rule,
                tajweedColor = token.tajweedColor,
                wordIndex = token.wordIndex,
                harakahHint = hint,
                consonantChar = token.baseChar
              )
            )
          }
        }

        // 3. Separated Ghunnah family: GHUNNAH_MEEM, GHUNNAH_NOON, IKHFA, IQLAB
        rule.isGhunnahFamily -> {
          val nasalViseme = mapGhunnahRuleToViseme(rule, token.baseChar)
          val isTanweenCarrier = token.harakah == FATHATAN ||
            token.harakah == DAMMATAN ||
            token.harakah == KASRATAN

          if (isTanweenCarrier && shortVowelViseme != null) {
            // Tanween carrier (e.g., غَاسِقٍ / سَمِيعٌۢ بَصِيرٌ): keep non-Tajweed host letter closed,
            // then clearly hold the nasal Ghunnah / Ikhfa / Iqlab posture!
            steps.add(
              WeightedStep(
                weight = 1.0f,
                viseme = ArabicViseme.REST_CLOSED,
                ruleType = TajweedRuleType.NORMAL,
                tajweedColor = TajweedRuleType.NORMAL.defaultColor,
                wordIndex = token.wordIndex,
                harakahHint = "",
                consonantChar = token.baseChar
              )
            )
            steps.add(
              WeightedStep(
                weight = mul,
                viseme = nasalViseme,
                ruleType = rule,
                tajweedColor = token.tajweedColor,
                wordIndex = token.wordIndex,
                harakahHint = hint,
                consonantChar = token.baseChar
              )
            )
          } else {
            // Noon/Meem Sakinah or Mushaddad نّ / مّ
            steps.add(
              WeightedStep(
                weight = mul,
                viseme = nasalViseme,
                ruleType = rule,
                tajweedColor = token.tajweedColor,
                wordIndex = token.wordIndex,
                harakahHint = hint,
                consonantChar = token.baseChar
              )
            )
            // Keep mouth cleanly closed during the trailing ordinary vowel after نّ/مّ so the nasal rule stands out
            if (token.hasShaddah && shortVowelViseme != null) {
              steps.add(
                WeightedStep(
                  weight = 0.65f,
                  viseme = ArabicViseme.REST_CLOSED,
                  ruleType = TajweedRuleType.NORMAL,
                  tajweedColor = TajweedRuleType.NORMAL.defaultColor,
                  wordIndex = token.wordIndex,
                  harakahHint = "",
                  consonantChar = token.baseChar
                )
              )
            }
          }
        }

        // 4. Qalqalah (ق ط ب ج د with Sukun / Waqf): 45% crisp contact -> 55% fast springy micro-open bounce
        rule == TajweedRuleType.QALQALAH -> {
          steps.add(
            WeightedStep(
              weight = mul * 0.45f,
              viseme = consonantViseme,
              ruleType = rule,
              tajweedColor = token.tajweedColor,
              wordIndex = token.wordIndex,
              harakahHint = hint,
              consonantChar = token.baseChar,
              isQalqalahBounce = false
            )
          )
          steps.add(
            WeightedStep(
              weight = mul * 0.55f,
              viseme = ArabicViseme.MID_NEUTRAL,
              ruleType = rule,
              tajweedColor = token.tajweedColor,
              wordIndex = token.wordIndex,
              harakahHint = hint,
              consonantChar = token.baseChar,
              isQalqalahBounce = true
            )
          )
        }

        // 5. TAFKHEEM (Heavy letters خ ص ض غ ط ق ظ / heavy ر & ل): show fuller cheeks & rounded heavy posture
        rule == TajweedRuleType.TAFKHEEM -> {
          if (shortVowelViseme != null) {
            val syllableTotal = if (token.hasShaddah) 1.25f * mul else 1.0f * mul
            val consRatio = if (token.hasShaddah) 0.44f else 0.35f
            steps.add(
              WeightedStep(
                weight = syllableTotal * consRatio,
                viseme = consonantViseme,
                ruleType = rule,
                tajweedColor = token.tajweedColor,
                wordIndex = token.wordIndex,
                harakahHint = hint,
                consonantChar = token.baseChar
              )
            )
            steps.add(
              WeightedStep(
                weight = syllableTotal * (1f - consRatio),
                viseme = shortVowelViseme,
                ruleType = rule,
                tajweedColor = token.tajweedColor,
                wordIndex = token.wordIndex,
                harakahHint = hint,
                consonantChar = token.baseChar
              )
            )
          } else {
            steps.add(
              WeightedStep(
                weight = (if (token.hasShaddah) 0.85f else 0.60f) * mul,
                viseme = consonantViseme,
                ruleType = rule,
                tajweedColor = token.tajweedColor,
                wordIndex = token.wordIndex,
                harakahHint = hint,
                consonantChar = token.baseChar
              )
            )
          }
        }

        // 6. NORMAL (all other letters): KEEP MOUTH CLOSED (REST_CLOSED) so kids are never distracted,
        // while preserving accurate syllable timing weight across the Ayah!
        else -> {
          val normalWeight = when {
            shortVowelViseme != null && token.hasShaddah -> 1.25f * mul
            shortVowelViseme != null -> 1.0f * mul
            token.hasShaddah -> 0.85f * mul
            else -> 0.60f * mul
          }
          steps.add(
            WeightedStep(
              weight = normalWeight,
              viseme = ArabicViseme.REST_CLOSED,
              ruleType = TajweedRuleType.NORMAL,
              tajweedColor = TajweedRuleType.NORMAL.defaultColor,
              wordIndex = token.wordIndex,
              harakahHint = "",
              consonantChar = token.baseChar
            )
          )
        }
      }

      i++
    }

    if (steps.isEmpty()) return emptyList()
    val totalWeight = steps.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(0.001f)

    val keyframes = ArrayList<TajweedVisemeKeyframe>(steps.size)
    var cursorMs = startMs
    steps.forEachIndexed { idx, step ->
      val isLast = idx == steps.lastIndex
      val stepDuration = if (isLast) {
        (endMs - cursorMs).coerceAtLeast(1L)
      } else {
        ((step.weight / totalWeight) * totalMs).toLong().coerceAtLeast(16L)
      }
      val stepEnd = if (isLast) max(cursorMs + 1L, endMs) else (cursorMs + stepDuration).coerceAtMost(endMs)
      keyframes.add(
        TajweedVisemeKeyframe(
          startMs = cursorMs,
          endMs = stepEnd,
          viseme = step.viseme,
          ruleType = step.ruleType,
          tajweedColor = step.tajweedColor,
          wordIndex = step.wordIndex,
          harakahHint = step.harakahHint,
          consonantChar = step.consonantChar,
          isQalqalahBounce = step.isQalqalahBounce,
          isTwoPhaseStandaloneBa = step.isTwoPhaseStandaloneBa
        )
      )
      cursorMs = stepEnd
    }
    return keyframes
  }

  private fun findNextNonSilentInSameWord(
    tokens: List<TajweedPhonemeToken>,
    startIndex: Int,
    wordIndex: Int
  ): Int? {
    var j = startIndex
    while (j < tokens.size && tokens[j].wordIndex == wordIndex) {
      if (tokens[j].ruleType != TajweedRuleType.SILENT && tokens[j].ruleType.durationMultiplier > 0f) {
        return j
      }
      j++
    }
    return null
  }

  /**
   * Computes an estimated `[startMs, endMs]` audio slice for a single [wordIndex] within a turn's
   * keyframes — used when a child taps a missed word to watch Ustadh Noor replay it at `0.75×` Slow-Mo.
   */
  fun wordTimeWindowMs(
    keyframes: List<TajweedVisemeKeyframe>,
    wordIndex: Int,
    fallbackStartMs: Long,
    fallbackEndMs: Long
  ): Pair<Long, Long> {
    val wordFrames = keyframes.filter { it.wordIndex == wordIndex }
    if (wordFrames.isEmpty()) return fallbackStartMs to fallbackEndMs
    val padMs = 140L
    val start = (wordFrames.first().startMs - padMs).coerceAtLeast(fallbackStartMs)
    val end = (wordFrames.last().endMs + padMs).coerceAtMost(fallbackEndMs)
    return start to max(start + 350L, end)
  }

  /**
   * Samples the planned keyframe timeline at 60 fps and modulates geometry with live ExoPlayer RMS:
   * - Whenever RMS `< 0.06f` for `>= 80 ms` (`silenceDurationMs`), snaps/blends cleanly to `REST_CLOSED`.
   * - During active speech, modulates `jawOpen` by `0.75f + 0.25f * normalizedRms` without EVER
   *   forcing closed-lip consonants (`م` / `ب`) open.
   * - Adds subtle 2 Hz alive micro-motion (`±0.03f`) on long Mudood (`MADD_ALIF`, `MADD_WAW`, `MADD_YA`)
   *   and `35 ms` tongue-tip micro-taps on `ر`.
   */
  fun sampleAt(
    keyframes: List<TajweedVisemeKeyframe>,
    positionMs: Long,
    rmsEnvelope: Float,
    silenceDurationMs: Long = 100L
  ): VisemeSample {
    if (keyframes.isEmpty()) return VisemeSample()
    val clampedRms = rmsEnvelope.coerceIn(0f, 1f)
    val frameIndex = findKeyframeIndex(keyframes, positionMs)
    val frame = keyframes[frameIndex]

    // Tajweed-only guard: non-Tajweed (NORMAL/SILENT) letters ALWAYS keep the mouth closed!
    if (!frame.ruleType.isArticulatedTajweedRule) {
      return VisemeSample(
        viseme = ArabicViseme.REST_CLOSED,
        geometry = defaultGeometryFor(ArabicViseme.REST_CLOSED),
        mouthOpenness = 0f,
        isVoiceActive = clampedRms >= SILENCE_RMS_THRESHOLD,
        ruleType = frame.ruleType,
        tajweedColor = frame.tajweedColor,
        wordIndex = frame.wordIndex,
        harakahHint = "",
        maddProgress = 0f,
        isQalqalahBounce = false
      )
    }

    // Silence gating: RMS < 0.06f for >= 80 ms closes mouth cleanly to REST_CLOSED
    if (clampedRms < SILENCE_RMS_THRESHOLD && silenceDurationMs >= SILENCE_HOLD_MS) {
      return VisemeSample(
        viseme = ArabicViseme.REST_CLOSED,
        geometry = defaultGeometryFor(ArabicViseme.REST_CLOSED),
        mouthOpenness = 0f,
        isVoiceActive = false,
        ruleType = frame.ruleType,
        tajweedColor = frame.tajweedColor,
        wordIndex = frame.wordIndex,
        harakahHint = frame.harakahHint,
        maddProgress = 0f,
        isQalqalahBounce = false
      )
    }

    val elapsedInFrameMs = (positionMs - frame.startMs).coerceAtLeast(0L)
    val progress = (elapsedInFrameMs.toFloat() / frame.durationMs.toFloat()).coerceIn(0f, 1f)

    var geom = defaultGeometryFor(frame.viseme, frame.consonantChar, frame.ruleType)

    // Two-phase standalone Ba (بْ): Phase 1 (45%) firm wet-lip press (0.0f) -> Phase 2 (55%) crisp 0.30f pop
    if (frame.viseme == ArabicViseme.LIPS_BA_POP && frame.isTwoPhaseStandaloneBa && progress >= 0.45f) {
      geom = geom.copy(
        jawOpen = 0.30f,
        lipPress = 0.0f,
        upperTeethVis = 0.30f,
        tonguePose = TonguePose.FLAT_RELAXED
      )
    }

    // Qalqalah bounce phase: crisp springy micro-open bounce (0.28f for ب, 0.30f for ق/ط/ج/د — never a wide yawn)
    if (frame.isQalqalahBounce) {
      val bouncePeak = sin(progress * PI).toFloat().coerceIn(0.35f, 1f)
      val targetBounceJaw = if (frame.consonantChar == 'ب') 0.28f else 0.30f
      geom = geom.copy(
        jawOpen = targetBounceJaw * bouncePeak,
        mouthWidth = 0.66f,
        lipPress = 0.0f,
        upperTeethVis = 0.45f,
        lowerTeethVis = 0.25f,
        tonguePose = TonguePose.FLAT_RELAXED
      )
    }

    // Qaf (ق) Qalqalah contact phase has a brief uvular/velar narrowing before the bounce
    if (frame.ruleType == TajweedRuleType.QALQALAH && !frame.isQalqalahBounce && frame.consonantChar == 'ق') {
      geom = geom.copy(jawOpen = 0.20f)
    }

    // Ra (ر): 1–2 rapid 35 ms tongue-tip micro-taps on upper alveolar ridge
    if (frame.viseme == ArabicViseme.TONGUE_ALVEOLAR_UP && frame.consonantChar == 'ر' && elapsedInFrameMs <= 105L) {
      val tapWave = sin((elapsedInFrameMs.toDouble() / 35.0) * PI).toFloat()
      val tappedLift = (0.95f - 0.34f * kotlin.math.abs(tapWave)).coerceIn(0.55f, 0.98f)
      geom = geom.copy(tongueLift = tappedLift)
    }

    // Rule 2.3.4: Active speech modulates jawOpen by (0.75f + 0.25f * normalizedRms)
    // Because closed-lip visemes (LIPS_MEEM_HOLD, LIPS_BA_POP Phase 1, REST_CLOSED) have jawOpen == 0.0f,
    // multiplying by rmsScale keeps them strictly at 0.0f!
    val rmsScale = if (clampedRms < SILENCE_RMS_THRESHOLD) {
      // Smooth sub-80ms fade toward closed during brief voiceless stop dips
      val fade = (1f - (silenceDurationMs.toFloat() / SILENCE_HOLD_MS.toFloat())).coerceIn(0.15f, 1f)
      0.75f * fade
    } else {
      0.75f + 0.25f * clampedRms
    }

    var modulatedJaw = geom.jawOpen * rmsScale

    // Rule 2.3.3: Subtle alive 2 Hz micro-motion (±0.03f) on sustained Mudood so 4–6 count holds feel alive
    val isMaddHold = frame.viseme == ArabicViseme.MADD_ALIF ||
      frame.viseme == ArabicViseme.MADD_WAW ||
      frame.viseme == ArabicViseme.MADD_YA
    if (isMaddHold && modulatedJaw > 0.05f) {
      val twoHzWave = sin(2.0 * PI * 2.0 * (elapsedInFrameMs.toDouble() / 1000.0)).toFloat()
      val microDelta = 0.03f * twoHzWave * (0.5f + 0.5f * clampedRms)
      modulatedJaw = (modulatedJaw + microDelta).coerceIn(0.12f, 0.84f)
    }

    // Inter-word micro-relax: gently ease jaw on the final 24 ms of a word before the next word
    val nextFrame = keyframes.getOrNull(frameIndex + 1)
    val remainingInFrameMs = frame.endMs - positionMs
    if (nextFrame != null &&
      nextFrame.wordIndex != frame.wordIndex &&
      !frame.ruleType.isGhunnahFamily &&
      !isMaddHold &&
      remainingInFrameMs in 0..24L &&
      modulatedJaw > 0.24f
    ) {
      val relaxBlend = (remainingInFrameMs.toFloat() / 24f).coerceIn(0f, 1f)
      modulatedJaw = 0.22f + (modulatedJaw - 0.22f) * relaxBlend
    }

    val finalGeom = geom.copy(jawOpen = modulatedJaw.coerceIn(0f, 0.85f))
    val maddProgress = if (frame.ruleType.maddCounts > 0) progress else 0f

    return VisemeSample(
      viseme = frame.viseme,
      geometry = finalGeom,
      mouthOpenness = finalGeom.jawOpen,
      isVoiceActive = clampedRms >= SILENCE_RMS_THRESHOLD,
      ruleType = frame.ruleType,
      tajweedColor = frame.tajweedColor,
      wordIndex = frame.wordIndex,
      harakahHint = frame.harakahHint,
      maddProgress = maddProgress,
      isQalqalahBounce = frame.isQalqalahBounce
    )
  }

  private fun findKeyframeIndex(
    keyframes: List<TajweedVisemeKeyframe>,
    positionMs: Long
  ): Int {
    if (positionMs < keyframes.first().startMs) return 0
    if (positionMs >= keyframes.last().endMs) return keyframes.lastIndex
    var low = 0
    var high = keyframes.lastIndex
    while (low <= high) {
      val mid = (low + high) ushr 1
      val kf = keyframes[mid]
      when {
        positionMs < kf.startMs -> high = mid - 1
        positionMs >= kf.endMs -> low = mid + 1
        else -> return mid
      }
    }
    return low.coerceIn(0, keyframes.lastIndex)
  }

  internal fun mapConsonantToViseme(
    ch: Char,
    isMaddLetter: Boolean = false,
    harakah: Char? = null
  ): ArabicViseme = when (ch) {
    'ب' -> ArabicViseme.LIPS_BA_POP
    'م' -> ArabicViseme.LIPS_MEEM_HOLD
    'و' -> if (isMaddLetter && harakah == null) ArabicViseme.MADD_WAW else ArabicViseme.LIPS_WAW_CONS
    'ف' -> ArabicViseme.TEETH_FA
    'ث', 'ذ', 'ظ' -> ArabicViseme.TONGUE_INTERDENTAL
    'ن', 'ل', 'ر', 'ت', 'د', 'ط' -> ArabicViseme.TONGUE_ALVEOLAR_UP
    'ج', 'ش', 'ض' -> ArabicViseme.TONGUE_MID_PALATAL
    'ي', 'ى' -> if (isMaddLetter && harakah == null) ArabicViseme.MADD_YA else ArabicViseme.TONGUE_MID_PALATAL
    'س', 'ز', 'ص' -> ArabicViseme.TEETH_SIBILANT
    'ع', 'ح', 'خ', 'غ', 'ق', 'ك' -> ArabicViseme.THROAT_MID_OPEN
    'ء', 'أ', 'إ', 'ئ', 'ؤ', 'ه', 'ة', 'ٱ' -> ArabicViseme.MID_NEUTRAL
    'ا', 'آ' -> ArabicViseme.MADD_ALIF
    else -> ArabicViseme.MID_NEUTRAL
  }

  private fun mapShortHarakahToViseme(harakah: Char?): ArabicViseme? = when (harakah) {
    FATHA, FATHATAN -> ArabicViseme.SHORT_FATHA
    DAMMA, DAMMATAN -> ArabicViseme.SHORT_DAMMA
    KASRA, KASRATAN -> ArabicViseme.SHORT_KASRA
    SUKOON -> null
    else -> null
  }

  private fun mapMaddLetterToViseme(ch: Char, precedingHarakah: Char?): ArabicViseme = when {
    ch == 'و' || precedingHarakah == DAMMA -> ArabicViseme.MADD_WAW
    ch == 'ي' || ch == 'ى' || precedingHarakah == KASRA -> ArabicViseme.MADD_YA
    else -> ArabicViseme.MADD_ALIF
  }

  private fun mapGhunnahRuleToViseme(rule: TajweedRuleType, baseChar: Char): ArabicViseme = when (rule) {
    TajweedRuleType.GHUNNAH_MEEM, TajweedRuleType.IQLAB -> ArabicViseme.LIPS_MEEM_HOLD
    TajweedRuleType.IKHFA -> ArabicViseme.TONGUE_IKHFA_HOVER
    TajweedRuleType.GHUNNAH_NOON -> if (baseChar == 'م') ArabicViseme.LIPS_MEEM_HOLD else ArabicViseme.TONGUE_ALVEOLAR_UP
    else -> ArabicViseme.TONGUE_ALVEOLAR_UP
  }

  private fun buildHarakahHint(token: TajweedPhonemeToken): String {
    return if (token.ruleType.isArticulatedTajweedRule) {
      "${token.ruleType.labelAr} · ${token.clusterText}"
    } else {
      ""
    }
  }
}
