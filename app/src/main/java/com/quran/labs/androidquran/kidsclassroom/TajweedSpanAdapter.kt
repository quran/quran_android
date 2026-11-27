package com.quran.labs.androidquran.kidsclassroom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Color-matched visual cue rendered on [TeacherAvatarCanvas] when a Tajweed rule is active.
 */
enum class TajweedVisualEffect {
  NONE,
  SKIP,
  MADD_RIBBON_2,
  MADD_RIBBON_4,
  MADD_RIBBON_6,
  NASAL_SPARKLE_LIPS,
  NASAL_SPARKLE_TONGUE,
  NASAL_SPARKLE_HOVER,
  BOUNCE_POP,
  HEAVY_ROUNDING;

  val isNasalSparkle: Boolean
    get() = this == NASAL_SPARKLE_LIPS ||
      this == NASAL_SPARKLE_TONGUE ||
      this == NASAL_SPARKLE_HOVER

  val isMaddBar: Boolean
    get() = this == MADD_RIBBON_2 ||
      this == MADD_RIBBON_4 ||
      this == MADD_RIBBON_6
}

/**
 * Authentic Mushaf Tajweed rules mapped to precision lip-sync duration weights,
 * Makhraj-specific visual effects, and standard Dar Al-Ma'rifah Mushaf colors.
 */
enum class TajweedRuleType(
  val durationMultiplier: Float,
  val effect: TajweedVisualEffect,
  val defaultColor: Color,
  val labelAr: String,
  val labelEn: String,
  val maddCounts: Int = 0
) {
  NORMAL(
    durationMultiplier = 1.0f,
    effect = TajweedVisualEffect.NONE,
    defaultColor = Color(0xFF1A202C),
    labelAr = "حركة طبيعية",
    labelEn = "Clear Harakah"
  ),
  SILENT(
    durationMultiplier = 0.0f,
    effect = TajweedVisualEffect.SKIP,
    defaultColor = Color(0xFF9E9E9E),
    labelAr = "حرف صامت (لا يُنطق)",
    labelEn = "Silent Letter (Skipped)"
  ),
  MADD_2(
    durationMultiplier = 2.2f,
    effect = TajweedVisualEffect.MADD_RIBBON_2,
    defaultColor = Color(0xFFE67E22),
    labelAr = "مد طبيعي (حركتان)",
    labelEn = "Madd 2 Counts",
    maddCounts = 2
  ),
  MADD_4_5(
    durationMultiplier = 4.5f,
    effect = TajweedVisualEffect.MADD_RIBBON_4,
    defaultColor = Color(0xFFD93829),
    labelAr = "مد واجب/جائز (٤-٥ حركات)",
    labelEn = "Madd 4–5 Counts",
    maddCounts = 4
  ),
  MADD_6(
    durationMultiplier = 6.0f,
    effect = TajweedVisualEffect.MADD_RIBBON_6,
    defaultColor = Color(0xFF8B0000),
    labelAr = "مد لازم (٦ حركات)",
    labelEn = "Madd Lazim (6 Counts)",
    maddCounts = 6
  ),
  GHUNNAH_MEEM(
    durationMultiplier = 2.6f,
    effect = TajweedVisualEffect.NASAL_SPARKLE_LIPS,
    defaultColor = Color(0xFF2E7D32),
    labelAr = "غنة الميم (إطباق الشفتين)",
    labelEn = "Meem Ghunnah (Closed Lips)"
  ),
  GHUNNAH_NOON(
    durationMultiplier = 2.6f,
    effect = TajweedVisualEffect.NASAL_SPARKLE_TONGUE,
    defaultColor = Color(0xFF2E7D32),
    labelAr = "غنة النون (طرف اللسان)",
    labelEn = "Noon Ghunnah (Tongue Up)"
  ),
  IKHFA(
    durationMultiplier = 2.4f,
    effect = TajweedVisualEffect.NASAL_SPARKLE_HOVER,
    defaultColor = Color(0xFF2E7D32),
    labelAr = "إخفاء بغنة (لسان معلّق)",
    labelEn = "Ikhfa (Hovering Tongue)"
  ),
  IQLAB(
    durationMultiplier = 2.5f,
    effect = TajweedVisualEffect.NASAL_SPARKLE_LIPS,
    defaultColor = Color(0xFF2E7D32),
    labelAr = "إقلاب بغنة (تلامس الشفتين)",
    labelEn = "Iqlab (Soft Lip Touch)"
  ),
  QALQALAH(
    durationMultiplier = 1.35f,
    effect = TajweedVisualEffect.BOUNCE_POP,
    defaultColor = Color(0xFF1565C0),
    labelAr = "قلقلة (ارتداد)",
    labelEn = "Qalqalah Bounce"
  ),
  TAFKHEEM(
    durationMultiplier = 1.15f,
    effect = TajweedVisualEffect.HEAVY_ROUNDING,
    defaultColor = Color(0xFF0D47A1),
    labelAr = "تفخيم",
    labelEn = "Heavy Letter"
  );

  val visualEffect: TajweedVisualEffect
    get() = effect

  val isGhunnahFamily: Boolean
    get() = this == GHUNNAH_MEEM ||
      this == GHUNNAH_NOON ||
      this == IKHFA ||
      this == IQLAB

  /**
   * True for active Tajweed rules that Ustadh Noor explicitly articulates
   * (Ghunnah Meem/Noon, Ikhfa, Iqlab, Madd 2/4-5/6, Qalqalah, Tafkheem).
   * Ordinary [NORMAL] and [SILENT] letters keep the mouth cleanly closed.
   */
  val isArticulatedTajweedRule: Boolean
    get() = this != NORMAL && this != SILENT
}

/**
 * Normalized phoneme-cluster token extracted from Tajweed Mushaf spans or Uthmani Quran text.
 */
data class TajweedPhonemeToken(
  val baseChar: Char,
  val harakah: Char? = null, // ـَ ـُ ـِ ـْ ـّ ـً ـٌ ـٍ or null
  val isMaddLetter: Boolean = false, // true if ا/آ/ٰ after Fatha, و after Damma, ي/ى after Kasra
  val ruleType: TajweedRuleType = TajweedRuleType.NORMAL,
  val tajweedColor: Color = ruleType.defaultColor,
  val wordIndex: Int = 0,
  val hasShaddah: Boolean = false,
  val clusterText: String = baseChar.toString()
) {
  val char: Char
    get() = baseChar
}

data class TajweedWordAnalysis(
  val wordIndex: Int,
  val rawWord: String,
  val tokens: List<TajweedPhonemeToken>,
  val dominantRule: TajweedRuleType
)

data class TajweedAyahAnalysis(
  val words: List<TajweedWordAnalysis>,
  val allTokens: List<TajweedPhonemeToken>,
  val annotatedString: AnnotatedString
)

/**
 * Connects existing color-coded Tajweed Quran spans (Dar Al-Ma'rifah Mushaf colors, HTML `<tajweed>`
 * markup, or Compose [AnnotatedString] color spans) and Uthmani Quran text directly into the
 * precision 18-viseme lip-sync engine ([TajweedVisemePlanner]) and Ayah renderer ([KidsClassroomScreen]).
 */
object TajweedSpanAdapter {

  internal const val FATHA = '\u064E'
  internal const val DAMMA = '\u064F'
  internal const val KASRA = '\u0650'
  internal const val FATHATAN = '\u064B'
  internal const val DAMMATAN = '\u064C'
  internal const val KASRATAN = '\u064D'
  internal const val SHADDAH = '\u0651'
  internal const val SUKOON = '\u0652'
  private const val QURAN_SUKOON = '\u06E1'
  private const val MADDAH_ABOVE = '\u0653'
  internal const val DAGGER_ALIF = '\u0670'
  private const val SMALL_HIGH_ZERO = '\u06DF'
  private const val SMALL_HIGH_MEEM = '\u06E2'
  private const val SMALL_LOW_MEEM = '\u06ED'
  private const val SMALL_WAW = '\u06E5'
  private const val SMALL_YA = '\u06E6'
  private const val HAMZAT_WASL = '\u0671'

  private val QALQALAH_LETTERS = setOf('ق', 'ط', 'ب', 'ج', 'د')
  private val SUN_LETTERS = setOf(
    'ت', 'ث', 'د', 'ذ', 'ر', 'ز', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ل', 'ن'
  )
  private val HEAVY_LETTERS = setOf('خ', 'ص', 'ض', 'غ', 'ط', 'ق', 'ظ')
  private val HAMZAH_LETTERS = setOf('ء', 'أ', 'إ', 'ئ', 'ؤ', 'ٲ', 'ٳ')
  private val IDGHAM_GHUNNAH_LETTERS = setOf('ي', 'ن', 'م', 'و')
  private val IDGHAM_BILA_GHUNNAH_LETTERS = setOf('ل', 'ر')
  private val IKHFA_LETTERS = setOf(
    'ت', 'ث', 'ج', 'د', 'ذ', 'ز', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ف', 'ق', 'ك'
  )
  private val MUQATTAAT_WORDS = setOf(
    "الم", "الٓمٓ", "المص", "الٓمٓصٓ", "المر", "الٓمٓر", "الر", "الٓر",
    "كهيعص", "كٓهيعٓصٓ", "طه", "طسم", "طسٓمٓ", "طس", "طسٓ", "يس", "يسٓ",
    "ص", "صٓ", "حم", "حمٓ", "عسق", "عٓسٓقٓ", "ق", "قٓ", "ن", "نٓ"
  )

  private data class RawCluster(
    val baseChar: Char,
    val marks: String,
    val rawText: String,
    val wordIndex: Int,
    val indexInWord: Int,
    val wordLength: Int,
    val overrideColor: Color? = null,
    val overrideRule: TajweedRuleType? = null
  ) {
    val hasShaddah: Boolean get() = marks.contains(SHADDAH)
    val hasSukoon: Boolean get() = marks.contains(SUKOON) || marks.contains(QURAN_SUKOON)
    val hasMaddah: Boolean get() = marks.contains(MADDAH_ABOVE) || baseChar == 'آ'
    val hasDaggerAlif: Boolean get() = marks.contains(DAGGER_ALIF)
    val hasSmallZero: Boolean get() = marks.contains(SMALL_HIGH_ZERO)
    val hasIqlabMeem: Boolean get() = marks.contains(SMALL_HIGH_MEEM) || marks.contains(SMALL_LOW_MEEM)
    val hasSmallMaddWawYa: Boolean get() = marks.contains(SMALL_WAW) || marks.contains(SMALL_YA)
    val hasTanween: Boolean get() = marks.any { it == FATHATAN || it == DAMMATAN || it == KASRATAN }

    val primaryHarakah: Char?
      get() = when {
        marks.contains(FATHA) -> FATHA
        marks.contains(DAMMA) -> DAMMA
        marks.contains(KASRA) -> KASRA
        marks.contains(FATHATAN) -> FATHATAN
        marks.contains(DAMMATAN) -> DAMMATAN
        marks.contains(KASRATAN) -> KASRATAN
        marks.contains(DAGGER_ALIF) -> FATHA
        marks.contains(SMALL_WAW) -> DAMMA
        marks.contains(SMALL_YA) -> KASRA
        hasSukoon -> SUKOON
        else -> null
      }
  }

  /**
   * Analyzes a list of Uthmani Arabic words into color-coded [TajweedPhonemeToken]s and a matching
   * Compose [AnnotatedString] whose colors match the Mushaf Tajweed palette 1-to-1.
   */
  fun analyzeWords(words: List<String>): TajweedAyahAnalysis {
    val cleanWords = words.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleanWords.isEmpty()) {
      return TajweedAyahAnalysis(emptyList(), emptyList(), AnnotatedString(""))
    }

    val allClusters = ArrayList<RawCluster>(cleanWords.size * 5)
    cleanWords.forEachIndexed { wIdx, word ->
      val clusters = splitIntoClusters(word, wIdx)
      allClusters.addAll(clusters)
    }

    val tokens = classifyClusters(allClusters, cleanWords)
    val wordAnalyses = buildWordAnalyses(cleanWords, tokens)
    val annotated = buildAnnotatedAyah(wordAnalyses)
    return TajweedAyahAnalysis(
      words = wordAnalyses,
      allTokens = tokens,
      annotatedString = annotated
    )
  }

  /**
   * Convenience helper for a space-delimited Ayah string.
   */
  fun analyzeText(ayahText: String): TajweedAyahAnalysis {
    val words = ayahText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return analyzeWords(words)
  }

  /**
   * Adapts an existing Compose [AnnotatedString] carrying Mushaf Tajweed color spans directly into
   * [TajweedPhonemeToken]s so pre-colored Tajweed spans preserve their exact RGB colors and rules.
   */
  fun adaptAnnotatedString(annotated: AnnotatedString): TajweedAyahAnalysis {
    val text = annotated.text.trim()
    if (text.isEmpty()) {
      return TajweedAyahAnalysis(emptyList(), emptyList(), AnnotatedString(""))
    }
    val charColorMap = arrayOfNulls<Color>(annotated.text.length)
    for (span in annotated.spanStyles) {
      if (span.item.color != Color.Unspecified) {
        val start = span.start.coerceIn(0, annotated.text.length)
        val end = span.end.coerceIn(start, annotated.text.length)
        for (i in start until end) {
          charColorMap[i] = span.item.color
        }
      }
    }

    val words = ArrayList<String>()
    val allClusters = ArrayList<RawCluster>()
    var idx = 0
    var wordIdx = 0
    while (idx < annotated.text.length) {
      while (idx < annotated.text.length && annotated.text[idx].isWhitespace()) idx++
      if (idx >= annotated.text.length) break
      val wordStart = idx
      while (idx < annotated.text.length && !annotated.text[idx].isWhitespace()) idx++
      val word = annotated.text.substring(wordStart, idx)
      words.add(word)
      val clusters = splitIntoClusters(
        word = word,
        wordIndex = wordIdx,
        colorProvider = { localPos -> charColorMap.getOrNull(wordStart + localPos) }
      )
      allClusters.addAll(clusters)
      wordIdx++
    }

    val tokens = classifyClusters(allClusters, words)
    val wordAnalyses = buildWordAnalyses(words, tokens)
    val rebuilt = buildAnnotatedAyah(wordAnalyses)
    return TajweedAyahAnalysis(wordAnalyses, tokens, rebuilt)
  }

  /**
   * Adapts HTML `<tajweed class="rule">...</tajweed>` or `<span style="color:#...">` markup into
   * [TajweedPhonemeToken]s for lip-sync and Compose display.
   */
  fun adaptHtmlTajweedSpans(html: String): TajweedAyahAnalysis {
    val tagRegex = Regex(
      """<(tajweed|span)\s+[^>]*(?:class=["']([^"']+)["']|color\s*:\s*(#[0-9a-fA-F]{6}))[^>]*>(.*?)</\1>""",
      setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    data class SpanPiece(val text: String, val color: Color?, val explicitRule: TajweedRuleType?)

    val pieces = ArrayList<SpanPiece>()
    var cursor = 0
    for (match in tagRegex.findAll(html)) {
      if (match.range.first > cursor) {
        pieces.add(SpanPiece(stripHtmlTags(html.substring(cursor, match.range.first)), null, null))
      }
      val className = match.groupValues[2].lowercase()
      val hexColor = match.groupValues[3]
      val inner = stripHtmlTags(match.groupValues[4])
      val rule = mapTajweedClassToRule(className, inner)
      val color = parseHexColorOrNull(hexColor) ?: rule?.defaultColor
      pieces.add(SpanPiece(inner, color, rule))
      cursor = match.range.last + 1
    }
    if (cursor < html.length) {
      pieces.add(SpanPiece(stripHtmlTags(html.substring(cursor)), null, null))
    }

    val fullBuilder = StringBuilder()
    pieces.forEach { fullBuilder.append(it.text) }
    val fullText = fullBuilder.toString()
    val charColorMap = arrayOfNulls<Color>(fullText.length)
    val charRuleMap = arrayOfNulls<TajweedRuleType>(fullText.length)
    var offset = 0
    for (piece in pieces) {
      for (i in piece.text.indices) {
        charColorMap[offset + i] = piece.color
        charRuleMap[offset + i] = piece.explicitRule
      }
      offset += piece.text.length
    }

    val words = ArrayList<String>()
    val allClusters = ArrayList<RawCluster>()
    var idx = 0
    var wordIdx = 0
    while (idx < fullText.length) {
      while (idx < fullText.length && fullText[idx].isWhitespace()) idx++
      if (idx >= fullText.length) break
      val wordStart = idx
      while (idx < fullText.length && !fullText[idx].isWhitespace()) idx++
      val word = fullText.substring(wordStart, idx)
      words.add(word)
      val clusters = splitIntoClusters(
        word = word,
        wordIndex = wordIdx,
        colorProvider = { localPos -> charColorMap.getOrNull(wordStart + localPos) },
        ruleProvider = { localPos -> charRuleMap.getOrNull(wordStart + localPos) }
      )
      allClusters.addAll(clusters)
      wordIdx++
    }

    val tokens = classifyClusters(allClusters, words)
    val wordAnalyses = buildWordAnalyses(words, tokens)
    val rebuilt = buildAnnotatedAyah(wordAnalyses)
    return TajweedAyahAnalysis(wordAnalyses, tokens, rebuilt)
  }

  /**
   * Maps a rendered Mushaf Tajweed RGB color directly to its [TajweedRuleType].
   */
  fun mapColorToRule(color: Color, baseChar: Char = 'ن'): TajweedRuleType {
    val r = (color.red * 255f).toInt()
    val g = (color.green * 255f).toInt()
    val b = (color.blue * 255f).toInt()

    // Grey / muted silent letters
    val maxC = maxOf(r, g, b)
    val minC = minOf(r, g, b)
    if (maxC - minC <= 28 && r in 105..215) return TajweedRuleType.SILENT

    // Dark Red: Madd Lazim (6 counts)
    if (r in 100..175 && g < 60 && b < 60) return TajweedRuleType.MADD_6
    // Red / Coral: Madd Wajib / Ja'iz (4-5 counts)
    if (r >= 175 && g < 110 && b < 110) return TajweedRuleType.MADD_4_5
    // Orange / Gold: Madd Tabi'i (2 counts)
    if (r >= 180 && g in 95..205 && b < 110) return TajweedRuleType.MADD_2
    // Green: Ghunnah / Ikhfa / Iqlab / Idgham w/ Ghunnah
    if (g >= 100 && g > r + 25 && g > b + 20) {
      return if (baseChar == 'م') TajweedRuleType.GHUNNAH_MEEM else TajweedRuleType.GHUNNAH_NOON
    }
    // Blue / Cyan: Qalqalah vs Deep Navy Tafkheem
    if (b >= 120 && b > r + 25) {
      return if (r < 45 && g < 95 && b <= 170) TajweedRuleType.TAFKHEEM else TajweedRuleType.QALQALAH
    }
    return TajweedRuleType.NORMAL
  }

  private fun classifyClusters(
    allClusters: List<RawCluster>,
    words: List<String>
  ): List<TajweedPhonemeToken> {
    if (allClusters.isEmpty()) return emptyList()
    val result = ArrayList<TajweedPhonemeToken>(allClusters.size)

    for (i in allClusters.indices) {
      val cur = allClusters[i]
      val prev = allClusters.getOrNull(i - 1)
      val next = allClusters.getOrNull(i + 1)
      val next2 = allClusters.getOrNull(i + 2)
      val wordRaw = words.getOrElse(cur.wordIndex) { "" }

      // If an explicit Tajweed color span was provided, respect its exact color and refine green Ghunnah sub-type
      if (cur.overrideColor != null || cur.overrideRule != null) {
        val rawMapped = cur.overrideRule ?: mapColorToRule(cur.overrideColor!!, cur.baseChar)
        val refinedRule = if (rawMapped.isGhunnahFamily && cur.overrideRule == null) {
          refineGreenGhunnahRule(cur, next)
        } else {
          rawMapped
        }
        val isMadd = computeIsMaddLetter(cur, prev, refinedRule)
        result.add(
          TajweedPhonemeToken(
            baseChar = cur.baseChar,
            harakah = cur.primaryHarakah,
            isMaddLetter = isMadd,
            ruleType = refinedRule,
            tajweedColor = cur.overrideColor ?: refinedRule.defaultColor,
            wordIndex = cur.wordIndex,
            hasShaddah = cur.hasShaddah,
            clusterText = cur.rawText
          )
        )
        continue
      }

      val rule = determineTajweedRule(cur, prev, next, next2, wordRaw, i == 0)
      val isMadd = computeIsMaddLetter(cur, prev, rule)
      result.add(
        TajweedPhonemeToken(
          baseChar = cur.baseChar,
          harakah = cur.primaryHarakah,
          isMaddLetter = isMadd,
          ruleType = rule,
          tajweedColor = rule.defaultColor,
          wordIndex = cur.wordIndex,
          hasShaddah = cur.hasShaddah,
          clusterText = cur.rawText
        )
      )
    }
    return result
  }

  private fun computeIsMaddLetter(
    cur: RawCluster,
    prev: RawCluster?,
    rule: TajweedRuleType
  ): Boolean {
    if (rule == TajweedRuleType.SILENT) return false
    if (rule == TajweedRuleType.MADD_2 || rule == TajweedRuleType.MADD_4_5 || rule == TajweedRuleType.MADD_6) {
      return true
    }
    if (cur.hasMaddah || cur.hasDaggerAlif || cur.hasSmallMaddWawYa) return true
    val ch = cur.baseChar
    return (ch == 'ا' && cur.primaryHarakah == null && prev?.primaryHarakah == FATHA) ||
      (ch == 'و' && (cur.primaryHarakah == null || cur.hasSukoon) && prev?.primaryHarakah == DAMMA) ||
      ((ch == 'ي' || ch == 'ى') && (cur.primaryHarakah == null || cur.hasSukoon) && prev?.primaryHarakah == KASRA)
  }

  private fun refineGreenGhunnahRule(cur: RawCluster, next: RawCluster?): TajweedRuleType {
    if (cur.hasIqlabMeem) return TajweedRuleType.IQLAB
    if (cur.baseChar == 'م') return TajweedRuleType.GHUNNAH_MEEM
    val nextBase = next?.baseChar
    if ((cur.baseChar == 'ن' && !cur.hasShaddah) || cur.hasTanween) {
      return when {
        nextBase == 'ب' -> TajweedRuleType.IQLAB
        nextBase == 'م' -> TajweedRuleType.GHUNNAH_MEEM
        nextBase != null && nextBase in IKHFA_LETTERS -> TajweedRuleType.IKHFA
        else -> TajweedRuleType.GHUNNAH_NOON
      }
    }
    return TajweedRuleType.GHUNNAH_NOON
  }

  private fun determineTajweedRule(
    cur: RawCluster,
    prev: RawCluster?,
    next: RawCluster?,
    next2: RawCluster?,
    wordRaw: String,
    isFirstInAyah: Boolean
  ): TajweedRuleType {
    val ch = cur.baseChar

    // 1. Muqatta'at opening letters (e.g., الم، حم، ق، ن) -> Madd Lazim 6 counts or Madd 2 counts
    val bareWord = wordRaw.filter { it in '\u0621'..'\u064A' || it == '\u0671' }
    if (wordRaw in MUQATTAAT_WORDS || bareWord in MUQATTAAT_WORDS) {
      if (ch in setOf('ل', 'م', 'ص', 'ك', 'ع', 'س', 'ق', 'ن')) {
        return TajweedRuleType.MADD_6
      }
      if (ch in setOf('ر', 'ح', 'ي', 'ط', 'ه')) {
        return TajweedRuleType.MADD_2
      }
    }

    // 2. SILENT letters (Grey, 0.0x duration — skipped completely by lip-sync engine):
    // 2a. Explicit Uthmani silent circle (small high rounded zero ۟, e.g., أُولَٰئِكَ / قَالُوا۟)
    if (cur.hasSmallZero) {
      return TajweedRuleType.SILENT
    }
    // 2b. Hamzat Wasl (ٱ) inside connected speech (not initial letter of the Ayah)
    if (ch == HAMZAT_WASL && !isFirstInAyah) {
      return TajweedRuleType.SILENT
    }
    // 2c. Silent Alif of plural Waw (وا at word end, e.g., آمَنُوا / قَالُوا)
    if (ch == 'ا' && cur.indexInWord == cur.wordLength - 1 && prev != null &&
      prev.wordIndex == cur.wordIndex && prev.baseChar == 'و' &&
      cur.primaryHarakah == null && !cur.hasMaddah
    ) {
      return TajweedRuleType.SILENT
    }
    // 2d. Lam Shamsiyyah (ال + Sun letter with Shaddah, e.g. الرَّحْمَٰنِ، الشَّمْسِ، الضَّالِّينَ)
    if (ch == 'ل' && prev != null && prev.wordIndex == cur.wordIndex &&
      (prev.baseChar == 'ا' || prev.baseChar == HAMZAT_WASL) &&
      !cur.hasSukoon && cur.primaryHarakah == null &&
      next != null && next.wordIndex == cur.wordIndex &&
      next.baseChar in SUN_LETTERS && (next.hasShaddah || next.primaryHarakah != null)
    ) {
      return TajweedRuleType.SILENT
    }
    // 2e. Idgham bila Ghunnah (Noon Sakinah before ل or ر -> Noon is completely assimilated/silent)
    if (ch == 'ن' && (cur.hasSukoon || cur.primaryHarakah == null) && !cur.hasShaddah &&
      next != null && next.baseChar in IDGHAM_BILA_GHUNNAH_LETTERS
    ) {
      return TajweedRuleType.SILENT
    }

    // 3. MADD LAZIM (6 counts, Dark Red): Maddah (ٓ or آ) followed by Shaddah or Sukoon (e.g. الضَّالِّينَ)
    if (cur.hasMaddah) {
      val nextIsShaddahOrSukoon = next != null &&
        next.wordIndex == cur.wordIndex &&
        (next.hasShaddah || next.hasSukoon)
      if (nextIsShaddahOrSukoon || (ch == 'آ' && next != null && next.wordIndex == cur.wordIndex && next.hasShaddah)) {
        return TajweedRuleType.MADD_6
      }
      // 4. MADD WAJIB / JA'IZ (4-5 counts, Red/Coral): Maddah before Hamzah (same or next word)
      val nextIsHamzah = next != null && (
        next.baseChar in HAMZAH_LETTERS ||
          (next.baseChar == HAMZAT_WASL && next2?.baseChar in HAMZAH_LETTERS)
        )
      return if (nextIsHamzah || cur.marks.contains(MADDAH_ABOVE)) {
        TajweedRuleType.MADD_4_5
      } else {
        // Standalone آ without Maddah-above mark and without following Hamzah/Shaddah is Madd Badal (2 counts)
        TajweedRuleType.MADD_2
      }
    }

    // 4b. Long vowel ا/و/ي or dagger Alif
    val isBareLongVowel = (ch == 'ا' && cur.primaryHarakah == null && prev?.primaryHarakah == FATHA) ||
      (ch == 'و' && (cur.primaryHarakah == null || cur.hasSukoon) && prev?.primaryHarakah == DAMMA) ||
      ((ch == 'ي' || ch == 'ى') && (cur.primaryHarakah == null || cur.hasSukoon) && prev?.primaryHarakah == KASRA) ||
      cur.hasDaggerAlif || cur.hasSmallMaddWawYa
    if (isBareLongVowel) {
      if (next != null && next.wordIndex == cur.wordIndex && next.hasShaddah) {
        return TajweedRuleType.MADD_6
      }
      if (next != null && next.baseChar in HAMZAH_LETTERS) {
        return TajweedRuleType.MADD_4_5
      }
      // 5. MADD TABI'I (2 counts, Orange/Gold)
      return TajweedRuleType.MADD_2
    }

    // 6. SEPARATED GHUNNAH RULES (Green: GHUNNAH_MEEM, GHUNNAH_NOON, IKHFA, IQLAB)
    // 6a. Mushaddad Meem (مّ) -> closed cushioned lips
    if (ch == 'م' && cur.hasShaddah) {
      return TajweedRuleType.GHUNNAH_MEEM
    }
    // 6b. Mushaddad Noon (نّ) -> tongue tip held up on alveolar ridge
    if (ch == 'ن' && cur.hasShaddah) {
      return TajweedRuleType.GHUNNAH_NOON
    }
    // 6c. Explicit Uthmani Iqlab Meem mark (ۢ / ۭ)
    if (cur.hasIqlabMeem) {
      return TajweedRuleType.IQLAB
    }
    // 6d. Meem Sakinah before ب (Ikhfa Shafawi) or م (Idgham Shafawi)
    if (ch == 'م' && (cur.hasSukoon || cur.primaryHarakah == null) && next != null &&
      (next.baseChar == 'ب' || next.baseChar == 'م')
    ) {
      return TajweedRuleType.GHUNNAH_MEEM
    }
    // 6e. Noon Sakinah or unvoweled Noon before ب / Ikhfa / Idgham-with-Ghunnah
    if (ch == 'ن' && (cur.hasSukoon || cur.primaryHarakah == null) && next != null) {
      return when {
        next.baseChar == 'ب' -> TajweedRuleType.IQLAB
        next.baseChar in IKHFA_LETTERS -> TajweedRuleType.IKHFA
        next.baseChar == 'م' -> TajweedRuleType.GHUNNAH_MEEM
        next.baseChar in IDGHAM_GHUNNAH_LETTERS -> TajweedRuleType.GHUNNAH_NOON
        else -> TajweedRuleType.NORMAL
      }
    }
    // 6f. Tanween before ب / Ikhfa / Idgham-with-Ghunnah
    if (cur.hasTanween && next != null) {
      when {
        next.baseChar == 'ب' -> return TajweedRuleType.IQLAB
        next.baseChar in IKHFA_LETTERS -> return TajweedRuleType.IKHFA
        next.baseChar == 'م' -> return TajweedRuleType.GHUNNAH_MEEM
        next.baseChar in IDGHAM_GHUNNAH_LETTERS -> return TajweedRuleType.GHUNNAH_NOON
      }
    }

    // 7. QALQALAH (Blue/Cyan, 1.35x): ق ط ب ج د with Sukoon or at end of Ayah/verse pause
    if (ch in QALQALAH_LETTERS) {
      val isEndOfAyah = next == null
      if (cur.hasSukoon || isEndOfAyah) {
        return TajweedRuleType.QALQALAH
      }
    }

    // 8. TAFKHEEM (Heavy rounding, 1.15x): Heavy letters, emphatic Ra, and emphatic Lam in Allah
    if (ch in HEAVY_LETTERS && cur.primaryHarakah != KASRA) {
      return TajweedRuleType.TAFKHEEM
    }
    if (ch == 'ر') {
      val emphaticRa = cur.primaryHarakah == FATHA ||
        cur.primaryHarakah == DAMMA ||
        (cur.hasSukoon && (prev?.primaryHarakah == FATHA || prev?.primaryHarakah == DAMMA))
      if (emphaticRa) return TajweedRuleType.TAFKHEEM
    }
    if (ch == 'ل' && cur.hasShaddah && bareWord.contains("الله") && prev?.primaryHarakah != KASRA) {
      return TajweedRuleType.TAFKHEEM
    }

    return TajweedRuleType.NORMAL
  }

  private fun splitIntoClusters(
    word: String,
    wordIndex: Int,
    colorProvider: ((Int) -> Color?)? = null,
    ruleProvider: ((Int) -> TajweedRuleType?)? = null
  ): List<RawCluster> {
    val rawClusters = ArrayList<Triple<Char, StringBuilder, Int>>()
    for (i in word.indices) {
      val c = word[i]
      if (isArabicCombiningMark(c)) {
        if (rawClusters.isNotEmpty()) {
          rawClusters.last().second.append(c)
        }
      } else if (isArabicBaseLetter(c)) {
        rawClusters.add(Triple(c, StringBuilder(), i))
      }
    }
    val total = rawClusters.size
    return rawClusters.mapIndexed { idx, (base, marksSb, origPos) ->
      val marks = marksSb.toString()
      val col = colorProvider?.invoke(origPos)
      val explicitRule = ruleProvider?.invoke(origPos)
      RawCluster(
        baseChar = base,
        marks = marks,
        rawText = base.toString() + marks,
        wordIndex = wordIndex,
        indexInWord = idx,
        wordLength = total,
        overrideColor = col,
        overrideRule = explicitRule
      )
    }
  }

  private fun buildWordAnalyses(
    words: List<String>,
    tokens: List<TajweedPhonemeToken>
  ): List<TajweedWordAnalysis> {
    val grouped = tokens.groupBy { it.wordIndex }
    return words.mapIndexed { idx, rawWord ->
      val wordTokens = grouped[idx].orEmpty()
      val dominant = wordTokens
        .filter { it.ruleType != TajweedRuleType.NORMAL && it.ruleType != TajweedRuleType.SILENT }
        .maxByOrNull { it.ruleType.durationMultiplier }
        ?.ruleType
        ?: TajweedRuleType.NORMAL
      TajweedWordAnalysis(
        wordIndex = idx,
        rawWord = rawWord,
        tokens = wordTokens,
        dominantRule = dominant
      )
    }
  }

  private fun buildAnnotatedAyah(wordAnalyses: List<TajweedWordAnalysis>): AnnotatedString {
    return buildAnnotatedString {
      wordAnalyses.forEachIndexed { wIdx, word ->
        if (wIdx > 0) append(' ')
        if (word.tokens.isEmpty()) {
          append(word.rawWord)
        } else {
          for (tok in word.tokens) {
            withStyle(SpanStyle(color = tok.tajweedColor)) {
              append(tok.clusterText)
            }
          }
        }
      }
    }
  }

  fun buildAnnotatedWord(wordAnalysis: TajweedWordAnalysis): AnnotatedString {
    return buildAnnotatedString {
      if (wordAnalysis.tokens.isEmpty()) {
        append(wordAnalysis.rawWord)
      } else {
        for (tok in wordAnalysis.tokens) {
          withStyle(SpanStyle(color = tok.tajweedColor)) {
            append(tok.clusterText)
          }
        }
      }
    }
  }

  private fun mapTajweedClassToRule(className: String, innerText: String): TajweedRuleType? = when {
    className.contains("madda_necessary") || className.contains("madd_6") || className.contains("lazim") ->
      TajweedRuleType.MADD_6
    className.contains("madda_obligatory") || className.contains("madda_permissible") ||
      className.contains("madd_4") || className.contains("wajib") || className.contains("jaiz") ->
      TajweedRuleType.MADD_4_5
    className.contains("madda_normal") || className.contains("madd_2") || className.contains("tabii") ->
      TajweedRuleType.MADD_2
    className.contains("iqlab") ->
      TajweedRuleType.IQLAB
    className.contains("ikhfa_shafawi") || className.contains("idgham_shafawi") ->
      TajweedRuleType.GHUNNAH_MEEM
    className.contains("ikhfa") ->
      TajweedRuleType.IKHFA
    className.contains("ghunnah") || className.contains("idgham_ghunnah") ->
      if (innerText.contains('م')) TajweedRuleType.GHUNNAH_MEEM else TajweedRuleType.GHUNNAH_NOON
    className.contains("qalqalah") -> TajweedRuleType.QALQALAH
    className.contains("silent") || className.contains("ham_wasl") || className.contains("laam_shamsiyah") ||
      className.contains("idgham_wo_ghunnah") || className.contains("slnt") ->
      TajweedRuleType.SILENT
    className.contains("tafkheem") -> TajweedRuleType.TAFKHEEM
    else -> null
  }

  private fun parseHexColorOrNull(hex: String): Color? {
    if (hex.length != 7 || !hex.startsWith("#")) return null
    val rgb = hex.substring(1).toLongOrNull(16) ?: return null
    return Color((0xFF000000L or rgb).toInt())
  }

  private fun stripHtmlTags(input: String): String =
    input.replace(Regex("<[^>]+>"), "")

  private fun isArabicCombiningMark(c: Char): Boolean {
    val code = c.code
    return (code in 0x0610..0x061A) ||
      (code in 0x064B..0x065F) ||
      code == 0x0670 ||
      (code in 0x06D6..0x06ED)
  }

  private fun isArabicBaseLetter(c: Char): Boolean {
    val code = c.code
    return (code in 0x0621..0x064A) || code == 0x0671 || code == 0x06CC || code == 0x067E
  }
}
