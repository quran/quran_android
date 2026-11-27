package com.quran.labs.androidquran.common.search.arabic

import com.google.common.truth.Truth.assertThat
import com.quran.common.search.arabic.ArabicCharacterHelper
import com.quran.common.search.arabic.ArabicCompiledQuery
import com.quran.common.search.arabic.ArabicMorphologicalQuery
import org.junit.Test
import java.util.regex.Pattern

class ArabicCompiledQueryTest {

  @Test
  fun `compiled query matches and highlights identically to Pattern across Arabic equivalence classes`() {
    val verses = listOf(
      "بسم الله الرحمن الرحيم",
      "الله لا إله إلا هو الحي القيوم والله عليم",
      "إياك نعبد وإياك نستعين",
      "وأوحينا إلى موسى أن ألق عصاك",
      "إن الله يحب التوابين ويحب المتطهرين",
      "للل لل للل",
      "قوة توبة هداية هاء ة ت",
      "a.b*c? (literal) a.b*c?"
    )
    val queries = listOf(
      "الله",
      "الرحمن",
      "إياك",
      "موسى",
      "موسي",
      "توبة",
      "لل",
      "اه", // all characters are char-groups (no exact anchor char)
      "(الله)",
      "a.b*c?"
    )

    val matchStart = "<font color=\"-13388315\">"
    val matchEnd = "</font>"
    val buffer = StringBuilder()

    for (query in queries) {
      val compiled = ArabicCompiledQuery.compile(query)
      assertThat(compiled).isNotNull()

      val pattern = Pattern.compile(ArabicCharacterHelper.generateRegex(query))
      val matcher = pattern.matcher("")

      for (verse in verses) {
        val actual = compiled!!.highlightTo(verse, matchStart, matchEnd, buffer)
        matcher.reset(verse)
        val expected = if (matcher.find()) {
          val sb = StringBuilder()
          var lastEnd = 0
          do {
            sb.append(verse, lastEnd, matcher.start())
            sb.append(matchStart)
            sb.append(verse, matcher.start(), matcher.end())
            sb.append(matchEnd)
            lastEnd = matcher.end()
          } while (matcher.find())
          sb.append(verse, lastEnd, verse.length)
          sb.toString()
        } else {
          null
        }
        assertThat(actual).isEqualTo(expected)
      }
    }
  }

  @Test
  fun `empty parentheses-only and surrogate queries return null for Pattern fallback`() {
    assertThat(ArabicCompiledQuery.compile("")).isNull()
    assertThat(ArabicCompiledQuery.compile("()")).isNull()
    assertThat(ArabicCompiledQuery.compile("\uD83D\uDE00")).isNull()
  }

  @Test
  fun `compile caches repeated query instance safely`() {
    val first = ArabicCompiledQuery.compile("الله")
    val second = ArabicCompiledQuery.compile("الله")
    assertThat(second).isSameInstanceAs(first)

    val different = ArabicCompiledQuery.compile("الرحيم")
    assertThat(different).isNotSameInstanceAs(first)
  }

  @Test
  fun `morphological query covers allah proclitics, lillah elision, and rejects false positives`() {
    val qAllah = ArabicMorphologicalQuery.compile("الله")!!
    val qAllahDiacritics = ArabicMorphologicalQuery.compile("اللَّهِ")!!
    val qAllahTatweel = ArabicMorphologicalQuery.compile("ال\u0640ل\u0640ه")!!

    val mustMatch = listOf(
      "بسم الله الرحمن الرحيم" to "الله",
      "الحمد لله رب العالمين" to "لله",
      "ولله المشرق والمغرب" to "ولله",
      "فلله الآخرة والأولى" to "فلله",
      "قل اللهم مالك الملك" to "اللهم",
      "قل آلله أذن لكم" to "آلله",
      "بالله وتالله وأبالله" to "بالله",
      "قُلْ هُوَ اللَّهُ أَحَدٌ" to "اللَّهُ"
    )
    for ((text, expectedToken) in mustMatch) {
      for (q in listOf(qAllah, qAllahDiacritics, qAllahTatweel)) {
        val spans = q.findAllSpans(text, allowSubstringFallback = false)
        assertThat(spans.size).isAtLeast(2)
        val firstMatch = text.substring(spans[0], spans[1])
        assertThat(firstMatch).isEqualTo(expectedToken)
      }
    }

    val mustReject = listOf("اللهو واللعب", "ذات اللهب", "يضلله من دونه", "للهدى والرشاد")
    for (text in mustReject) {
      assertThat(qAllah.findAllSpans(text, allowSubstringFallback = false)).isEmpty()
    }
  }

  @Test
  fun `morphological query ranks jannah tiers cleanly and rejects jinn and unrelated roots`() {
    val qJannah = ArabicMorphologicalQuery.compile("الجنة")!!
    val qJannahHa = ArabicMorphologicalQuery.compile("الجنه")!!
    val starts = ArrayList<Int>()
    val ends = ArrayList<Int>()

    // Tier 1: explicit definite singular + proclitics + لِـ+ال elision
    for (text in listOf("اسكن أنت وزوجك الجنة", "وأبشروا بالجنة", "طريق للجنة", "وللجنة أبواب")) {
      assertThat(qJannah.matchAndCollectSpans(text, starts, ends)).isEqualTo(ArabicMorphologicalQuery.TIER_EXACT)
      assertThat(qJannahHa.matchAndCollectSpans(text, starts, ends)).isEqualTo(ArabicMorphologicalQuery.TIER_EXACT)
    }

    // Tier 2: indefinite singular + Uthmani open-Ta + possessive singular
    for (text in listOf("وجنة عرضها السماوات", "فروح وريحان وجنت نعيم", "وادخلي جنتي", "ودخل جنته", "لولا إذ دخلت جنتك")) {
      assertThat(qJannah.matchAndCollectSpans(text, starts, ends)).isEqualTo(ArabicMorphologicalQuery.TIER_SINGULAR_MORPH)
    }

    // Tier 3: dual & sound feminine plural
    for (text in listOf("كلتا الجنتين", "جنتان عن يمين", "وبدلناهم بجنتيهم جنتين", "لهم جنات تجري", "في روضات الجنات")) {
      assertThat(qJannah.matchAndCollectSpans(text, starts, ends)).isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)
    }

    // Zero false positives on short stem "جن"
    for (text in listOf("شركاء الجن", "جند من السماء", "فلا جناح عليه", "أنتم أجنة", "السجن أحب إلي", "سجنت نفسك", "مجنون")) {
      assertThat(qJannah.matchAndCollectSpans(text, starts, ends, allowSubstringFallback = false))
        .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
    }
  }

  @Test
  fun `morphological query supports al-to-lil elision, open-ta, plural alternation, and presentation forms`() {
    val starts = ArrayList<Int>()
    val ends = ArrayList<Int>()

    assertThat(ArabicMorphologicalQuery.compile("الرحمن")!!.matchAndCollectSpans("عهد للرحمن", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_EXACT)
    assertThat(ArabicMorphologicalQuery.compile("الرحمن")!!.matchAndCollectSpans("ربنا رحمنا", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
    assertThat(ArabicMorphologicalQuery.compile("الناس")!!.matchAndCollectSpans("هدى للناس", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_EXACT)
    assertThat(ArabicMorphologicalQuery.compile("الإيمان")!!.matchAndCollectSpans("للإيمان وإيمانهم", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_EXACT)
    assertThat(ArabicMorphologicalQuery.compile("الصلاة")!!.matchAndCollectSpans("حافظوا على الصلوات", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)
    assertThat(ArabicMorphologicalQuery.compile("المؤمنون")!!.matchAndCollectSpans("بشرى للمؤمنين", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)
    // Arabic Presentation Forms-B (found in real quran.ar.muyassar.db e.g. ﺑ«مكة»)
    assertThat(ArabicMorphologicalQuery.compile("مكة")!!.matchAndCollectSpans("\uFE91«مكة»", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_EXACT)
  }

  @Test
  fun `universal morphology covers muslim inflections, bidirectional plurals, weak-ya, broken plurals, and strict guardrails`() {
    val starts = ArrayList<Int>()
    val ends = ArrayList<Int>()

    // 1. "مسلم" forward expansion across ALL forms
    val qMuslim = ArabicMorphologicalQuery.compile("مسلم")!!
    assertThat(qMuslim.matchAndCollectSpans("كان حنيفا مسلما", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_SINGULAR_MORPH)
    assertThat(qMuslim.matchAndCollectSpans("أمة مسلمة لك", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_SINGULAR_MORPH)
    for (pluralText in listOf(
      "وأنتم مسلمون",
      "ونحن له المسلمون",
      "إن المسلمين والمسلمات",
      "بشرى للمسلمين",
      "مسلمين ومسلمات",
      "رجلان مسلمان"
    )) {
      assertThat(qMuslim.matchAndCollectSpans(pluralText, starts, ends))
        .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)
    }

    // 2. Bidirectional reverse lookup: searching plural/feminine keeps exact form Tier 1 and finds siblings
    val qMuslimin = ArabicMorphologicalQuery.compile("المسلمين")!!
    assertThat(qMuslimin.matchAndCollectSpans("بشرى للمسلمين", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_EXACT)
    assertThat(qMuslimin.matchAndCollectSpans("حنيفا مسلما", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_SINGULAR_MORPH)
    assertThat(qMuslimin.matchAndCollectSpans("وأنتم مسلمون", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)

    val qJannat = ArabicMorphologicalQuery.compile("جنات")!!
    assertThat(qJannat.matchAndCollectSpans("لهم جنات تجري", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_EXACT)
    assertThat(qJannat.matchAndCollectSpans("اسكن أنت وزوجك الجنة", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_SINGULAR_MORPH)
    assertThat(qJannat.matchAndCollectSpans("كلتا الجنتين", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)

    // 3. Defective weak-ي stems
    assertThat(ArabicMorphologicalQuery.compile("متقي")!!.matchAndCollectSpans("هدى للمتقين", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)
    assertThat(ArabicMorphologicalQuery.compile("مهتدي")!!.matchAndCollectSpans("هم المهتدون", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)
    assertThat(ArabicMorphologicalQuery.compile("معتدي")!!.matchAndCollectSpans("لا يحب المعتدين", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)

    // 4. Broken plurals & hamza-final seat alternation (آباءهم / آباؤهم / آبائهم)
    assertThat(ArabicMorphologicalQuery.compile("نبي")!!.matchAndCollectSpans("ويقتلون الأنبياء والنبيين", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)
    assertThat(ArabicMorphologicalQuery.compile("قلب")!!.matchAndCollectSpans("ختم الله على قلوبهم", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)
    assertThat(ArabicMorphologicalQuery.compile("كتاب")!!.matchAndCollectSpans("وملائكته وكتبه ورسله", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)
    assertThat(ArabicMorphologicalQuery.compile("كتاب")!!.matchAndCollectSpans("ولو أنا كتبنا عليهم", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
    assertThat(ArabicMorphologicalQuery.compile("الأب")!!.matchAndCollectSpans("ما وجدنا عليه آباؤنا وآبائهم", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)
    assertThat(ArabicMorphologicalQuery.compile("اليد")!!.matchAndCollectSpans("غلت أيديهم بل يداه مبسوطتان", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.TIER_DUAL_PLURAL_OR_TOPICAL)
    assertThat(ArabicMorphologicalQuery.compile("اليد")!!.matchAndCollectSpans("فيكيدوا لك كيدا", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)

    // 5. Strict precision & homograph guardrails
    assertThat(ArabicMorphologicalQuery.compile("الأم")!!.matchAndCollectSpans("يقول آمنا بالله", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
    assertThat(ArabicMorphologicalQuery.compile("أمة")!!.matchAndCollectSpans("فأماته الله مائة عام", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
    assertThat(ArabicMorphologicalQuery.compile("سنة")!!.matchAndCollectSpans("بلسان عربي مبين", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
    assertThat(ArabicMorphologicalQuery.compile("السن")!!.matchAndCollectSpans("سنة الله في الذين خلوا", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
    assertThat(ArabicMorphologicalQuery.compile("نعمة")!!.matchAndCollectSpans("قالوا نعم", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
    assertThat(ArabicMorphologicalQuery.compile("نعم")!!.matchAndCollectSpans("واذكروا نعمة الله", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
    assertThat(ArabicMorphologicalQuery.compile("الجن")!!.matchAndCollectSpans("أصحاب الجنة", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
    assertThat(ArabicMorphologicalQuery.compile("نملة")!!.matchAndCollectSpans("إنما نملي لهم", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
    assertThat(ArabicMorphologicalQuery.compile("عظيم")!!.matchAndCollectSpans("يحيي العظام وهي رميم", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
    assertThat(ArabicMorphologicalQuery.compile("الملائكة")!!.matchAndCollectSpans("إن الملوك إذا دخلوا قرية", starts, ends))
      .isEqualTo(ArabicMorphologicalQuery.NO_MATCH)
  }
}
