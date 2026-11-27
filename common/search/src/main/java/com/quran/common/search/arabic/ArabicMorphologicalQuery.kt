package com.quran.common.search.arabic

/**
 * Pure-Kotlin comprehensive Arabic search matcher with 100% parity to the native Rust engine
 * (`common/search/src/main/rust/src/engine.rs`).
 *
 * Supports:
 * - Full Arabic orthographic normalization (harakat/tashkeel, Quranic marks, dagger alif, tatweel,
 *   zero-width chars, Arabic Presentation Forms-B, Alef/Hamza/Ya/Waw folding).
 * - Complete attached Arabic proclitics (`و/ف/ب/ك/ل/ت/أ/س`) AND `لِـ + ال` elision
 *   (`لله`/`ولله`/`فلله`, `للجنة`, `للناس`, `للرحمن`, `للمؤمنين`, `لليل`, etc.).
 * - Uthmani open-Ta (`ة` <-> `ت`) matching (`جنت`/`رحمت`/`نعمت`/`شجرت`/`سنت`/`امرأت`/`كلمت`).
 * - Ranked 3-tier morphological matching (Tier 1: exact/proclitic/`لل`/open-Ta -> Tier 2:
 *   singular indefinite/possessive -> Tier 3: dual & plural / `ون <-> ين`) with strict word
 *   boundaries for zero false positives.
 * - Multi-word phrases (contiguous exact -> contiguous morphological -> topical all-words) and
 *   mid-typing normalized substring fallback.
 */
class ArabicMorphologicalQuery private constructor(
  val query: String,
  private val mode: Mode
) {

  private sealed interface Mode {
    class SingleWord(
      val matcher: WordMatcher,
      val fallbackNeedle: String
    ) : Mode

    class MultiWord(
      val words: Array<WordMatcher>,
      val normPhrase: String
    ) : Mode

    class ShortArabic(val needle: String) : Mode

    class Literal(val compiled: ArabicCompiledQuery?, val rawStripped: String) : Mode

    data object Empty : Mode
  }

  private class WordMatcher(
    val tier1: HashSet<String>,
    val tier2: HashSet<String>,
    val tier3: HashSet<String>
  ) {
    fun matchesAny(token: String): Boolean =
      tier1.contains(token) || tier2.contains(token) || tier3.contains(token)

    fun matchTier(token: String): Int = when {
      tier1.contains(token) -> TIER_EXACT
      tier2.contains(token) -> TIER_SINGULAR_MORPH
      tier3.contains(token) -> TIER_DUAL_PLURAL_OR_TOPICAL
      else -> NO_MATCH
    }
  }

  fun isWordLevel(): Boolean = mode is Mode.SingleWord || mode is Mode.MultiWord

  /**
   * Matches [text] and appends non-overlapping original UTF-16 `[start, end)` highlight spans
   * (absorbing trailing diacritics/marks) into [startsOut] and [endsOut].
   *
   * Returns the match tier (`1`..`3` for word/phrase matches, `4` for substring/literal fallback,
   * or `0` if no match).
   */
  fun matchAndCollectSpans(
    text: String,
    startsOut: ArrayList<Int>,
    endsOut: ArrayList<Int>,
    allowNonContiguousMultiWord: Boolean = true,
    allowSubstringFallback: Boolean = false
  ): Int {
    startsOut.clear()
    endsOut.clear()
    if (text.isEmpty()) return NO_MATCH

    return when (val m = mode) {
      Mode.Empty -> NO_MATCH

      is Mode.Literal -> {
        if (m.compiled != null) {
          var pos = m.compiled.findFirst(text, 0)
          if (pos < 0) return NO_MATCH
          val patLen = m.compiled.length
          while (pos >= 0) {
            val end = pos + patLen
            if (end <= text.length) {
              startsOut.add(pos)
              endsOut.add(end)
            }
            pos = m.compiled.findFirst(text, end)
          }
          if (startsOut.isEmpty()) NO_MATCH else TIER_EXACT
        } else {
          val needle = m.rawStripped
          if (needle.isEmpty()) return NO_MATCH
          var pos = text.indexOf(needle, 0)
          if (pos < 0) return NO_MATCH
          while (pos >= 0) {
            val end = pos + needle.length
            startsOut.add(pos)
            endsOut.add(end)
            pos = text.indexOf(needle, end)
          }
          TIER_EXACT
        }
      }

      is Mode.ShortArabic -> {
        collectNormSubstringSpans(text, m.needle, startsOut, endsOut)
        if (startsOut.isEmpty()) NO_MATCH else TIER_EXACT
      }

      is Mode.SingleWord -> {
        var bestTier = NO_MATCH
        val tokenBuilder = StringBuilder(24)
        var tokStart = 0
        var tokEnd = 0
        val len = text.length

        fun flushToken() {
          if (tokenBuilder.isEmpty()) return
          val tok = tokenBuilder.toString()
          val tier = m.matcher.matchTier(tok)
          if (tier != NO_MATCH) {
            startsOut.add(tokStart)
            endsOut.add(tokEnd)
            if (bestTier == NO_MATCH || tier < bestTier) {
              bestTier = tier
            }
          }
          tokenBuilder.setLength(0)
        }

        for (i in 0 until len) {
          val ch = text[i]
          if (isArabicIgnorable(ch)) {
            if (tokenBuilder.isNotEmpty()) {
              tokEnd = i + 1
            }
            continue
          }
          val folded = foldArabicLetter(ch)
          if (isArabicLetter(folded)) {
            if (tokenBuilder.isEmpty()) {
              tokStart = i
            }
            tokenBuilder.append(folded)
            tokEnd = i + 1
          } else {
            flushToken()
          }
        }
        flushToken()

        if (bestTier != NO_MATCH) {
          return bestTier
        }
        if (allowSubstringFallback) {
          collectNormSubstringSpans(text, m.fallbackNeedle, startsOut, endsOut)
          if (startsOut.isNotEmpty()) return TIER_FALLBACK_SUBSTR
        }
        NO_MATCH
      }

      is Mode.MultiWord -> {
        val k = m.words.size
        val tokNorms = ArrayList<String>(16)
        val tokStarts = ArrayList<Int>(16)
        val tokEnds = ArrayList<Int>(16)
        val tokenBuilder = StringBuilder(24)
        var curStart = 0
        var curEnd = 0
        val len = text.length

        fun flushToken() {
          if (tokenBuilder.isEmpty()) return
          tokNorms.add(tokenBuilder.toString())
          tokStarts.add(curStart)
          tokEnds.add(curEnd)
          tokenBuilder.setLength(0)
        }

        for (i in 0 until len) {
          val ch = text[i]
          if (isArabicIgnorable(ch)) {
            if (tokenBuilder.isNotEmpty()) {
              curEnd = i + 1
            }
            continue
          }
          val folded = foldArabicLetter(ch)
          if (isArabicLetter(folded)) {
            if (tokenBuilder.isEmpty()) {
              curStart = i
            }
            tokenBuilder.append(folded)
            curEnd = i + 1
          } else {
            flushToken()
          }
        }
        flushToken()

        // 1. Check contiguous phrase matches (merged span from first token start to last token end)
        var bestContiguous = NO_MATCH
        if (tokNorms.size >= k) {
          var i = 0
          while (i + k <= tokNorms.size) {
            var worst = TIER_EXACT
            var ok = true
            for (j in 0 until k) {
              val mt = m.words[j].matchTier(tokNorms[i + j])
              if (mt == NO_MATCH) {
                ok = false
                break
              }
              if (mt > worst) worst = mt
            }
            if (ok) {
              val phraseTier = if (worst == TIER_EXACT) TIER_EXACT else TIER_SINGULAR_MORPH
              if (bestContiguous == NO_MATCH || phraseTier < bestContiguous) {
                bestContiguous = phraseTier
              }
              startsOut.add(tokStarts[i])
              endsOut.add(tokEnds[i + k - 1])
              i += k
            } else {
              i++
            }
          }
        }
        if (bestContiguous != NO_MATCH) {
          return bestContiguous
        }

        // 2. Non-contiguous all-words topical match
        if (allowNonContiguousMultiWord && tokNorms.isNotEmpty()) {
          val foundWord = BooleanArray(k)
          for (ti in tokNorms.indices) {
            val tok = tokNorms[ti]
            var matchedAny = false
            for (wi in 0 until k) {
              if (m.words[wi].matchesAny(tok)) {
                foundWord[wi] = true
                matchedAny = true
              }
            }
            if (matchedAny) {
              startsOut.add(tokStarts[ti])
              endsOut.add(tokEnds[ti])
            }
          }
          var allFound = true
          for (wi in 0 until k) {
            if (!foundWord[wi]) {
              allFound = false
              break
            }
          }
          if (allFound && startsOut.isNotEmpty()) {
            return TIER_DUAL_PLURAL_OR_TOPICAL
          }
          startsOut.clear()
          endsOut.clear()
        }

        // 3. Substring fallback (mid-typing last word)
        if (allowSubstringFallback) {
          collectNormSubstringSpans(text, m.normPhrase, startsOut, endsOut)
          if (startsOut.isNotEmpty()) return TIER_FALLBACK_SUBSTR
        }
        NO_MATCH
      }
    }
  }

  /**
   * Returns packed non-overlapping UTF-16 match spans `[start0, end0, start1, end1, ...]`
   * matching Rust `CompiledArabicQuery::find_all_spans` semantics.
   */
  fun findAllSpans(text: String, allowSubstringFallback: Boolean = true): IntArray {
    if (text.isEmpty()) return IntArray(0)
    val starts = ArrayList<Int>(4)
    val ends = ArrayList<Int>(4)
    val tier = matchAndCollectSpans(
      text = text,
      startsOut = starts,
      endsOut = ends,
      allowNonContiguousMultiWord = true,
      allowSubstringFallback = allowSubstringFallback
    )
    if (tier == NO_MATCH || starts.isEmpty()) return IntArray(0)
    val out = IntArray(starts.size * 2)
    for (i in starts.indices) {
      out[i * 2] = starts[i]
      out[i * 2 + 1] = ends[i]
    }
    return out
  }

  fun highlightTo(
    text: String,
    matchStart: String,
    matchEnd: String,
    buffer: StringBuilder,
    allowSubstringFallback: Boolean = true
  ): String? {
    val starts = ArrayList<Int>(4)
    val ends = ArrayList<Int>(4)
    val tier = matchAndCollectSpans(
      text = text,
      startsOut = starts,
      endsOut = ends,
      allowNonContiguousMultiWord = true,
      allowSubstringFallback = allowSubstringFallback
    )
    if (tier == NO_MATCH || starts.isEmpty()) return null
    return formatHighlighted(text, starts, ends, matchStart, matchEnd, buffer)
  }

  private class CacheEntry(val query: String, val compiled: ArabicMorphologicalQuery?)

  companion object {
    const val NO_MATCH = 0
    const val TIER_EXACT = 1
    const val TIER_SINGULAR_MORPH = 2
    const val TIER_DUAL_PLURAL_OR_TOPICAL = 3
    const val TIER_FALLBACK_SUBSTR = 4

    private const val CH_HAMZA = '\u0621'
    private const val CH_ALEF_MADDA = '\u0622'
    private const val CH_ALEF = '\u0627'
    private const val CH_TA_MARBUTA = '\u0629'
    private const val CH_TA = '\u062A'
    private const val CH_LAM = '\u0644'
    private const val CH_MIM = '\u0645'
    private const val CH_NUN = '\u0646'
    private const val CH_HA = '\u0647'
    private const val CH_WAW = '\u0648'
    private const val CH_YA = '\u064A'
    private const val CH_ALEF_WASLA = '\u0671'

    private val ALLAH_PROCLITICS = arrayOf(
      "", "و", "ف", "ب", "ت", "ك", "ا",
      "وب", "فب", "وت", "فت", "اب", "او", "اف", "فو", "وك", "فك"
    )
    private val DEF_PROCLITICS = arrayOf(
      "", "و", "ف", "ب", "ك", "ا",
      "وب", "فب", "وك", "فك", "او", "اف", "اب"
    )
    private val L_PROCLITICS = arrayOf("", "و", "ف", "ا", "او", "اف")
    private val INDEF_PROCLITICS = arrayOf(
      "", "و", "ف", "ب", "ك", "ل",
      "وب", "فب", "ول", "فل", "وك", "فك"
    )
    private val VERB_PROCLITICS = arrayOf("س", "وس", "فس")
    private val SHORT_PHRASE_PROCLITICS = arrayOf("", "و", "ف", "ب", "ل", "ك")
    private val SAFE_WA_BA_PROCLITICS = arrayOf("", "و", "ب", "وب", "فب")
    private val SAFE_NO_KAF_PROCLITICS = arrayOf("", "و", "ف", "ب", "ل", "وب", "فب", "ول", "فل")
    private val SAFE_WA_LAM_PROCLITICS = arrayOf("", "و", "ل")
    private val SAFE_WA_BA_LAM_PROCLITICS = arrayOf("", "و", "ب", "ل")
    private val PRONOUNS = arrayOf("ه", "ها", "هم", "هما", "هن", "ك", "كم", "كما", "كن", "ي", "نا")
    private val MOTHER_PRONOUNS = arrayOf("ه", "ها", "هم", "هما", "هن", "ك", "كم", "كما")

    private val CARDINAL_NUMBERS = hashSetOf(
      "ثلاث", "اربع", "خمس", "ست", "سبع", "ثمان", "ثماني", "تسع", "عشر",
      "عشرون", "عشرين", "ثلاثون", "ثلاثين", "اربعون", "اربعين",
      "خمسون", "خمسين", "ستون", "ستين", "سبعون", "سبعين",
      "ثمانون", "ثمانين", "تسعون", "تسعين", "اثنين", "اثنتين", "مايتين"
    )

    private val NON_PLURAL_WN_YN = hashSetOf(
      "فرعون", "هارون", "قارون", "زيتون", "عرجون", "ماعون",
      "مجنون", "مكنون", "مسنون", "ممنون", "مفتون", "مشحون",
      "موزون", "مرهون", "مسجون", "مديون", "طاعون", "صهيون", "موتون",
      "مسكين", "شياطين", "مساكين", "مداين", "موازين", "مستبين",
      "عليين", "سجين", "غسلين", "يقطين", "بساتين", "فلسطين", "سلاطين", "ملايين"
    )

    private val NON_PLURAL_AT = hashSetOf(
      "مات", "فات", "بات", "يات", "ذات", "نبات", "ثبات", "سبات", "شتات",
      "فرات", "رفات", "ممات", "ميقات", "حيات", "امرات", "مرضات", "صلات",
      "زكات", "غدات", "مشكات", "نجات", "منات", "تورات", "هيهات", "لات",
      "عرفات", "اصوات", "اموات", "اقوات", "اوقات", "انبات", "افات", "ابيات", "بيات"
    )

    private val NON_DUAL_TAN_TIN = hashSetOf(
      "بهتان", "متين", "وتين", "تين", "ستين", "مايتين", "اثنتين", "هاتين",
      "ياتين", "اتين", "يوتين", "لاوتين", "قانتين", "مخبتين", "ميتين", "اختين"
    )

    private val NO_FEM_EXPANSION = hashSetOf(
      "كفار", "فجار", "رحمن", "سلطان", "قران", "شيطان", "انسان", "ميزان",
      "ايمان", "سليمان", "فرعون", "هارون", "قارون"
    )

    private val NO_DUAL_AN_EXPANSION = hashSetOf(
      "سليم", "رحمن", "قران", "شيطان", "انسان", "ميزان", "ايمان", "سلطان", "مستعان"
    )

    private val NON_DUAL_AN = hashSetOf(
      "سليمان", "مستعان", "سلطان", "سبحان", "رمضان", "قربان", "بنيان", "طغيان",
      "عصيان", "غفران", "كفران", "شكران", "حسبان", "نسيان", "عمران", "فرقان",
      "عدوان", "احسان", "خسران", "مرجان", "طوفان", "ريحان", "حيوان", "شيطان",
      "انسان", "ايمان", "ميزان", "برهان", "بهتان"
    )

    private val FEM_PARTICIPLE_EXCLUDE = hashSetOf(
      "مدينة", "سفينة", "حديقة", "خليفة", "ملايكة", "مغفرة", "معذرة", "موعظة",
      "منزلة", "مفسدة", "مخلفة", "طايفة", "قارعة", "واقعة", "حاقة", "غاشية",
      "دابة", "مايدة", "ساعة", "كفارة", "عبادة", "قيامة", "شهادة", "رسالة",
      "تجارة", "عمارة", "ولاية", "هداية", "خيانة", "ضلالة", "جهالة", "سفاهة",
      "شفاعة", "جماعة", "نهاية", "حجارة", "فاحشة", "كبيرة", "بصيرة", "خبيثة",
      "طيبة", "مقبرة", "ناصية", "صاعقة", "دايرة", "قاعدة", "قلادة", "سقاية",
      "سريرة", "شريعة", "قبيلة", "صحيفة", "بهيمة", "زيتونة", "فاكهة", "سنبلة",
      "اريكة", "صومعة", "سلسلة", "نمرقة", "زربية", "مصيبة", "عقوبة", "امانة",
      "خطيية", "مسالة", "معصية", "مشيية", "مرحمة", "متربة", "مسغبة", "مقربة"
    )

    private val ROOT_HA_WORDS = hashSetOf(
      "وجه", "اله", "سفيه", "وجيه", "شبيه", "فقه", "شبه", "كره",
      "وجوه", "افواه", "شفاه", "فواكه", "اشباه", "نبيه", "نزيه"
    )

    private val BROKEN_PLURAL_TA_MARBUTA = hashSetOf(
      "ملايكة", "كفرة", "فجرة", "سحرة", "اخوة", "فتية", "سادة", "الهة",
      "حجارة", "السنة", "افيدة", "اسورة", "اعمدة", "اودية", "نسوة",
      "اشربة", "اطعمة", "امتعة", "ادوية", "البسة", "اتربة"
    )

    private val NO_TANWIN_YA = hashSetOf(
      "موتي", "قري", "مرضي", "قتلي", "يتامي", "اسري", "اساري", "نصاري",
      "انثي", "اعمي", "هوي", "فتي", "ايدي", "موالي", "نواصي", "ليالي", "زرابي",
      "بني", "يدي", "ابي", "اخي", "ابوي"
    )

    private val MAQSURA_PRON_PLURALS = hashSetOf(
      "موتي", "مرضي", "قتلي", "يتامي", "اسري", "اساري", "فتي", "نصاري", "هوي", "انثي"
    )

    private val SAFE_3_REV_BASES = hashSetOf(
      "نبي", "اخر", "اول", "اهل", "ضال", "مضل", "فرح", "ميت", "طيب",
      "رجل", "ملك", "عين", "زوج", "قرن", "بحر", "شهر", "عام", "قلب",
      "حزب", "عبد", "ذكر", "اجل", "اثم", "الف", "كفل", "ضعف", "حول"
    )

    private val NO_PRON_FAMILY_MEMBERS = hashSetOf(
      "فرق", "ظلل", "ورق", "نمل", "كلم", "قري"
    )

    private val BROKEN_PLURAL_N_PRON = hashSetOf(
      "موازين", "شياطين", "مساكين", "بطون", "عيون", "قرون", "ظنون", "سجون", "حصون",
      "اذان", "اذقان", "اوثان", "الوان", "اخوان", "نيران", "غلمان", "ولدان", "فتيان",
      "رهبان", "حيتان", "براهين", "عميان", "ذكران"
    )

    private val LEMMA_FAMILIES: Array<Array<String>> = arrayOf(
      // 1. Prophets, Angels, Worship, Faith
      arrayOf("نبي", "انبياء", "نبيون", "نبيين"),
      arrayOf("رسول", "رسل"),
      arrayOf("ملك", "ملايكة", "ملكان", "ملكين"),
      arrayOf("ملك", "ملوك"),
      arrayOf("اله", "الهة", "الهين"),
      arrayOf("رب", "ارباب"),
      arrayOf("عبد", "عباد", "عبيد", "عبدين"),
      arrayOf("صلاة", "صلوات"),
      arrayOf("زكاة", "زكوات"),
      arrayOf("نعمة", "نعمات"),
      arrayOf("رحمة", "رحمات"),
      arrayOf("سنة", "سنن"),
      arrayOf("سنة", "سنين", "سنوات"),
      arrayOf("مسجد", "مساجد"),
      arrayOf("صومعة", "صوامع"),
      arrayOf("صنم", "اصنام"),
      arrayOf("وثن", "اوثان"),
      arrayOf("شيطان", "شياطين"),
      arrayOf("طاغوت", "طواغيت"),
      arrayOf("نار", "نيران"),
      // 2. Scripture, Speech, Signs, Knowledge
      arrayOf("كتاب", "كتب"),
      arrayOf("صحيفة", "صحف"),
      arrayOf("سفر", "اسفار"),
      arrayOf("لوح", "الواح"),
      arrayOf("قلم", "اقلام"),
      arrayOf("اية", "ايات", "ايتين"),
      arrayOf("سورة", "سور"),
      arrayOf("كلمة", "كلمات", "كلم"),
      arrayOf("قول", "اقوال"),
      arrayOf("حديث", "احاديث"),
      arrayOf("نبا", "انباء"),
      arrayOf("مثل", "امثال"),
      arrayOf("حجة", "حجج"),
      arrayOf("برهان", "براهين"),
      arrayOf("حكم", "احكام"),
      arrayOf("امر", "امور"),
      arrayOf("شان", "شوون", "شءون"),
      arrayOf("اسم", "اسماء"),
      arrayOf("لسان", "السنة"),
      arrayOf("صوت", "اصوات"),
      arrayOf("حلم", "احلام"),
      arrayOf("سر", "اسرار"),
      arrayOf("غيب", "غيوب"),
      arrayOf("سريرة", "سراير"),
      arrayOf("شريعة", "شرايع"),
      arrayOf("ملة", "ملل"),
      arrayOf("عهد", "عهود"),
      arrayOf("ميثاق", "مواثيق"),
      // 3. Deeds, Virtues, Sins, Trials, Rewards
      arrayOf("عمل", "اعمال"),
      arrayOf("فعل", "افعال"),
      arrayOf("ذنب", "ذنوب"),
      arrayOf("خطيية", "خطايا", "خطييات"),
      arrayOf("اثم", "اثام"),
      arrayOf("سيية", "سييات"),
      arrayOf("حسنة", "حسنات"),
      arrayOf("فاحشة", "فواحش"),
      arrayOf("كبيرة", "كباير"),
      arrayOf("وزر", "اوزار"),
      arrayOf("فتنة", "فتن"),
      arrayOf("مرض", "امراض"),
      arrayOf("اجر", "اجور"),
      arrayOf("درجة", "درجات"),
      arrayOf("ميزان", "موازين"),
      arrayOf("حد", "حدود"),
      arrayOf("ظن", "ظنون"),
      arrayOf("هوي", "اهواء"),
      arrayOf("شهوة", "شهوات"),
      arrayOf("خير", "خيرات", "اخيار"),
      arrayOf("طيب", "طيبة", "طيبون", "طيبين", "طيبات"),
      arrayOf("خبيث", "خبيثة", "خبيثون", "خبيثين", "خبيثات", "خبايث"),
      arrayOf("ظلمة", "ظلمات"),
      arrayOf("نور", "انوار"),
      arrayOf("ضلال", "ضلالة"),
      arrayOf("مصيبة", "مصايب"),
      // 4. Human Beings, Kinship, Communities
      arrayOf("انسان", "=انس", "ناس", "اناس", "اناسي"),
      arrayOf("رجل", "رجال", "رجلان", "رجلين"),
      arrayOf("رجل", "ارجل"),
      arrayOf("امراة", "امرات", "نساء", "نسوة", "امراتان", "امراتين"),
      arrayOf("ابن", "ابناء", "بني", "بنو", "بنين"),
      arrayOf("بنت", "بنات"),
      arrayOf(
        "اب", "اباء", "ابوان", "ابوين", "@ابواه", "@ابويه", "@ابويكم", "@ابوي",
        "@ابوه", "@ابوهم", "@ابوك", "@ابوكم", "@ابونا", "@اباه", "@اباكم",
        "@ابيه", "@ابيهم", "@ابيك", "@ابيكم", "!ابي"
      ),
      arrayOf(
        "اخ", "اخوة", "اخوان", "اخوين", "@اخوه", "@اخوهم", "@اخوك", "@اخوكم",
        "@اخاه", "@اخاك", "@اخيه", "@اخيهم", "@اخيك", "!اخي"
      ),
      arrayOf("اخت", "اخوات", "اختان", "اختين"),
      arrayOf("ام", "امهات"),
      arrayOf("امة", "امم"),
      arrayOf("ولد", "اولاد", "ولدان"),
      arrayOf("ذرية", "ذريات"),
      arrayOf("زوج", "ازواج", "زوجان", "زوجين"),
      arrayOf("اهل", "اهلون", "اهلين"),
      arrayOf("قوم", "اقوام"),
      arrayOf("قبيلة", "قبايل"),
      arrayOf("شعب", "شعوب"),
      arrayOf("سبط", "اسباط"),
      arrayOf("نسب", "انساب"),
      arrayOf("رحم", "ارحام"),
      arrayOf("صاحب", "اصحاب", "صاحبة"),
      arrayOf("عدو", "اعداء"),
      arrayOf("ولي", "اولياء", "موالي"),
      arrayOf("صديق", "اصدقاء"),
      arrayOf("خليل", "اخلاء"),
      arrayOf("شريك", "شركاء"),
      arrayOf("قرين", "قرناء"),
      arrayOf("خليط", "خلطاء"),
      arrayOf("نصير", "انصار"),
      arrayOf("شهيد", "شهداء"),
      arrayOf("شاهد", "اشهاد", "شهداء"),
      arrayOf("شفيع", "شفعاء"),
      arrayOf("خليفة", "خلفاء", "خلايف"),
      arrayOf("حبر", "احبار"),
      arrayOf("راهب", "رهبان"),
      arrayOf("حواري", "حواريون", "حواريين"),
      arrayOf("رباني", "ربانيون", "ربانيين"),
      arrayOf("عربي", "عرب", "اعراب"),
      arrayOf("يهودي", "يهود"),
      arrayOf("نصراني", "نصاري"),
      // 5. Human Conditions, Roles, Traits
      arrayOf("نفس", "انفس", "نفوس"),
      arrayOf("روح", "ارواح"),
      arrayOf("حي", "احياء"),
      arrayOf("ميت", "موتي", "اموات", "ميتون", "ميتين", "ميتة"),
      arrayOf("قتيل", "قتلي"),
      arrayOf("مريض", "مرضي"),
      arrayOf("فقير", "فقراء"),
      arrayOf("غني", "اغنياء"),
      arrayOf("مسكين", "مساكين"),
      arrayOf("يتيم", "يتامي"),
      arrayOf("اسير", "اسري", "اساري"),
      arrayOf("ضعيف", "ضعفاء", "ضعاف"),
      arrayOf("قوي", "اقوياء"),
      arrayOf("شديد", "اشداء", "شداد"),
      arrayOf("كبير", "كبار", "كبراء", "كبيرة", "كباير"),
      arrayOf("صغير", "صغار", "صغيرة"),
      arrayOf("عظيم", "عظيمة"),
      arrayOf("كريم", "كرام", "كريمة"),
      arrayOf("سفيه", "سفهاء"),
      arrayOf("عالم", "علماء"),
      arrayOf("شاعر", "شعراء"),
      arrayOf("كافر", "كفار", "كفرة"),
      arrayOf("فاجر", "فجار", "فجرة"),
      arrayOf("ساحر", "سحرة"),
      arrayOf("غلام", "غلمان"),
      arrayOf("فتي", "فتية", "فتيان"),
      arrayOf("شيخ", "شيوخ"),
      arrayOf("طفل", "اطفال"),
      arrayOf("بكر", "ابكار"),
      arrayOf("ذكر", "ذكور", "ذكران", "ذكرين"),
      arrayOf("انثي", "اناث", "انثيين"),
      arrayOf("اعمي", "عمي", "عميان"),
      arrayOf("اصم", "صم"),
      arrayOf("حنيف", "حنفاء"),
      arrayOf("سيد", "سادة"),
      arrayOf("جند", "جنود"),
      arrayOf("حزب", "احزاب", "حزبان", "حزبين"),
      arrayOf("طايفة", "طوايف", "طايفتان", "طايفتين"),
      arrayOf("فية", "فيات", "فيتان", "فيتين"),
      arrayOf("فريق", "فرق", "فريقان", "فريقين"),
      arrayOf("اول", "اولون", "اولين", "اوايل"),
      arrayOf("اخر", "اخرون", "اخرين", "اخران"),
      arrayOf("ضال", "ضالون", "ضالين"),
      arrayOf("مضل", "مضلون", "مضلين"),
      arrayOf("تواب", "توابون", "توابين"),
      // 6. Human Body
      arrayOf("قلب", "قلوب", "قلبين"),
      arrayOf("فواد", "افيدة"),
      arrayOf("صدر", "صدور"),
      arrayOf("وجه", "وجوه"),
      arrayOf("راس", "رءوس", "رووس", "روس"),
      arrayOf("ناصية", "نواصي"),
      arrayOf("عين", "اعين", "عيون", "عينان", "عينين"),
      arrayOf("بصر", "ابصار"),
      arrayOf("اذن", "اذان"),
      arrayOf("سمع", "اسماع"),
      arrayOf("فم", "افواه"),
      arrayOf("شفة", "شفاه", "شفتين"),
      arrayOf("ذقن", "اذقان"),
      arrayOf("عنق", "اعناق"),
      arrayOf("رقبة", "رقاب"),
      arrayOf("يد", "ايدي", "يدان", "يدين", "@يدي", "@يديه", "@يديك", "@يداه", "@يداك", "@يدا"),
      arrayOf("شمال", "شمايل"),
      arrayOf("اصبع", "اصابع"),
      arrayOf("كعب", "كعبين", "كعاب"),
      arrayOf("قدم", "اقدام"),
      arrayOf("بطن", "بطون"),
      arrayOf("ظهر", "ظهور"),
      arrayOf("دبر", "ادبار"),
      arrayOf("جنب", "جنوب"),
      arrayOf("عظم", "عظام"),
      arrayOf("جلد", "جلود"),
      arrayOf("دم", "دماء"),
      arrayOf("لحم", "لحوم"),
      arrayOf("جرح", "جروح"),
      arrayOf("فرج", "فروج"),
      // 7. Cosmos, Nature, Time, Flora, Fauna
      arrayOf("سماء", "سماوات", "سموات"),
      arrayOf("ارض", "ارضين", "اراض"),
      arrayOf("يوم", "ايام", "يومان", "يومين"),
      arrayOf("ليل", "ليلة", "ليالي", "ليال"),
      arrayOf("وقت", "اوقات"),
      arrayOf("شهر", "اشهر", "شهور", "شهران", "شهرين"),
      arrayOf("عام", "اعوام", "عامان", "عامين"),
      arrayOf("حول", "حولين", "احوال"),
      arrayOf("قرن", "قرون", "قرنان", "قرنين"),
      arrayOf("نجم", "نجوم"),
      arrayOf("كوكب", "كواكب"),
      arrayOf("برج", "بروج"),
      arrayOf("افق", "افاق"),
      arrayOf("مشرق", "مشارق", "مشرقين"),
      arrayOf("مغرب", "مغارب", "مغربين"),
      arrayOf("ريح", "رياح"),
      arrayOf("سحاب", "سحابة", "سحب"),
      arrayOf("صاعقة", "صواعق"),
      arrayOf("ظل", "ظلال", "ظلل"),
      arrayOf("نهر", "انهار"),
      arrayOf("بحر", "بحار", "ابحر", "بحران", "بحرين"),
      arrayOf("موج", "امواج"),
      arrayOf("جبل", "جبال"),
      arrayOf("حجر", "حجارة", "احجار"),
      arrayOf("صخرة", "صخر", "صخور"),
      arrayOf("قطر", "اقطار"),
      arrayOf("واد", "وادي", "اودية"),
      arrayOf("سبيل", "سبل"),
      arrayOf("طريق", "طرق", "طرايق"),
      arrayOf("شجرة", "شجر", "اشجار"),
      arrayOf("نخلة", "نخل", "نخيل"),
      arrayOf("ثمرة", "ثمر", "ثمرات", "ثمار"),
      arrayOf("فاكهة", "فواكه"),
      arrayOf("عنب", "اعناب"),
      arrayOf("سنبلة", "سنابل", "سنبلات"),
      arrayOf("حبة", "حبات", "حبوب"),
      arrayOf("ورقة", "ورق", "اوراق"),
      arrayOf("حديقة", "حدايق"),
      arrayOf("روضة", "روضات", "رياض"),
      arrayOf("سدرة", "سدر"),
      arrayOf("زيتونة", "زيتون"),
      arrayOf("دابة", "دواب"),
      arrayOf("بهيمة", "بهايم"),
      arrayOf("وحش", "وحوش"),
      arrayOf("طير", "طيور", "طاير"),
      arrayOf("نملة", "نمل"),
      arrayOf("نحلة", "نحل"),
      arrayOf("حوت", "حيتان"),
      arrayOf("كلب", "كلاب"),
      arrayOf("حمار", "حمير"),
      arrayOf("بغل", "بغال"),
      arrayOf("ناقة", "نوق"),
      arrayOf("بقرة", "بقر", "بقرات"),
      // 8. Places, Buildings, Possessions, Objects
      arrayOf("بيت", "بيوت"),
      arrayOf("دار", "ديار"),
      arrayOf("مدينة", "مداين", "مدن"),
      arrayOf("قرية", "قري", "قريتين", "@قراها"),
      arrayOf("بلد", "بلدة", "بلاد"),
      arrayOf("سوق", "اسواق"),
      arrayOf("قصر", "قصور"),
      arrayOf("حصن", "حصون"),
      arrayOf("غرفة", "غرف", "غرفات"),
      arrayOf("منزل", "منازل"),
      arrayOf("مجلس", "مجالس"),
      arrayOf("مضجع", "مضاجع"),
      arrayOf("معرج", "معارج"),
      arrayOf("قبر", "قبور"),
      arrayOf("جدث", "اجداث"),
      arrayOf("مقبرة", "مقابر"),
      arrayOf("باب", "ابواب"),
      arrayOf("جدار", "جدر"),
      arrayOf("قاعدة", "قواعد"),
      arrayOf("عمود", "عمد", "اعمدة"),
      arrayOf("وتد", "اوتاد"),
      arrayOf("خيمة", "خيام"),
      arrayOf("سجن", "سجون"),
      arrayOf("سفينة", "سفن"),
      arrayOf("مال", "اموال"),
      arrayOf("كنز", "كنوز"),
      arrayOf("ثوب", "ثياب"),
      arrayOf("سوار", "اساور", "اسورة"),
      arrayOf("قلادة", "قلايد"),
      arrayOf("كوب", "اكواب"),
      arrayOf("ابريق", "اباريق"),
      arrayOf("سرير", "سرر"),
      arrayOf("اريكة", "ارايك"),
      arrayOf("نمرقة", "نمارق"),
      arrayOf("زربية", "زرابي"),
      arrayOf("مفتاح", "مفاتح", "مفاتيح"),
      arrayOf("قفل", "اقفال"),
      arrayOf("سلسلة", "سلاسل"),
      arrayOf("غل", "اغلال"),
      arrayOf("صفد", "اصفاد"),
      arrayOf("نفل", "انفال"),
      arrayOf("شيء", "اشياء"),
      arrayOf("سبب", "اسباب"),
      arrayOf("مرفق", "مرافق"),
      arrayOf("دايرة", "دواير"),
      arrayOf("حمل", "احمال"),
      arrayOf("عقد", "عقود"),
      arrayOf("اثر", "اثار"),
      arrayOf("اجل", "اجال", "اجلين"),
      arrayOf("اصل", "اصول"),
      arrayOf("فرع", "فروع"),
      arrayOf("جزء", "اجزاء"),
      arrayOf("لون", "الوان"),
      arrayOf("مكان", "اماكن"),
      arrayOf("موضع", "مواضع"),
      arrayOf("موطن", "مواطن"),
      arrayOf("مشعر", "مشاعر"),
      arrayOf("منسك", "مناسك"),
      arrayOf("قوت", "اقوات"),
      arrayOf("رزق", "ارزاق")
    )

    private val FAMILY_INDEX: HashMap<String, IntArray> by lazy {
      val map = HashMap<String, ArrayList<Int>>(LEMMA_FAMILIES.size * 3)
      for (famIdx in LEMMA_FAMILIES.indices) {
        for (entry in LEMMA_FAMILIES[famIdx]) {
          val key = if (entry.isNotEmpty() && (entry[0] == '=' || entry[0] == '@' || entry[0] == '!')) {
            entry.substring(1)
          } else {
            entry
          }
          val list = map.getOrPut(key) { ArrayList(2) }
          if (list.isEmpty() || list[list.size - 1] != famIdx) {
            list.add(famIdx)
          }
        }
      }
      val out = HashMap<String, IntArray>(map.size * 2)
      for ((k, v) in map) {
        out[k] = v.toIntArray()
      }
      out
    }

    @Volatile
    private var lastEntry: CacheEntry? = null

    fun compile(query: String): ArabicMorphologicalQuery? {
      val cached = lastEntry
      if (cached != null && cached.query == query) {
        return cached.compiled
      }
      val compiled = compileUncached(query)
      lastEntry = CacheEntry(query, compiled)
      return compiled
    }

    fun isArabicIgnorable(ch: Char): Boolean {
      val code = ch.code
      return (code in 0x0610..0x061A) ||
        code == 0x061C ||
        code == 0x0640 ||
        (code in 0x064B..0x065F) ||
        code == 0x0670 ||
        (code in 0x06D6..0x06ED) ||
        (code in 0x200B..0x200F) ||
        code == 0xFEFF
    }

    fun mapPresentationForm(ch: Char): Char {
      val code = ch.code
      if (code !in 0xFE80..0xFEFC) return ch
      return when (code) {
        0xFE80 -> '\u0621'
        0xFE81, 0xFE82 -> '\u0622'
        0xFE83, 0xFE84 -> '\u0623'
        0xFE85, 0xFE86 -> '\u0624'
        0xFE87, 0xFE88 -> '\u0625'
        in 0xFE89..0xFE8C -> '\u0626'
        0xFE8D, 0xFE8E -> '\u0627'
        in 0xFE8F..0xFE92 -> '\u0628'
        0xFE93, 0xFE94 -> '\u0629'
        in 0xFE95..0xFE98 -> '\u062A'
        in 0xFE99..0xFE9C -> '\u062B'
        in 0xFE9D..0xFEA0 -> '\u062C'
        in 0xFEA1..0xFEA4 -> '\u062D'
        in 0xFEA5..0xFEA8 -> '\u062E'
        0xFEA9, 0xFEAA -> '\u062F'
        0xFEAB, 0xFEAC -> '\u0630'
        0xFEAD, 0xFEAE -> '\u0631'
        0xFEAF, 0xFEB0 -> '\u0632'
        in 0xFEB1..0xFEB4 -> '\u0633'
        in 0xFEB5..0xFEB8 -> '\u0634'
        in 0xFEB9..0xFEBC -> '\u0635'
        in 0xFEBD..0xFEC0 -> '\u0636'
        in 0xFEC1..0xFEC4 -> '\u0637'
        in 0xFEC5..0xFEC8 -> '\u0638'
        in 0xFEC9..0xFECC -> '\u0639'
        in 0xFECD..0xFED0 -> '\u063A'
        in 0xFED1..0xFED4 -> '\u0641'
        in 0xFED5..0xFED8 -> '\u0642'
        in 0xFED9..0xFEDC -> '\u0643'
        in 0xFEDD..0xFEE0 -> '\u0644'
        in 0xFEE1..0xFEE4 -> '\u0645'
        in 0xFEE5..0xFEE8 -> '\u0646'
        in 0xFEE9..0xFEEC -> '\u0647'
        0xFEED, 0xFEEE -> '\u0648'
        0xFEEF, 0xFEF0 -> '\u0649'
        in 0xFEF1..0xFEF4 -> '\u064A'
        else -> ch
      }
    }

    fun foldArabicLetter(ch: Char): Char {
      val base = mapPresentationForm(ch)
      return when (base) {
        '\u0622', '\u0623', '\u0625', '\u0671', '\u0672', '\u0673' -> CH_ALEF
        '\u0624' -> CH_WAW
        '\u0626', '\u0649', '\u06CC' -> CH_YA
        else -> base
      }
    }

    fun isArabicLetter(ch: Char): Boolean {
      val code = ch.code
      return code in 0x0621..0x064A
    }

    fun formatHighlighted(
      text: String,
      starts: ArrayList<Int>,
      ends: ArrayList<Int>,
      matchStart: String,
      matchEnd: String,
      buffer: StringBuilder
    ): String {
      buffer.setLength(0)
      var lastEnd = 0
      val len = text.length
      for (i in starts.indices) {
        val s = starts[i]
        val e = ends[i]
        if (s >= lastEnd && e > s && e <= len) {
          buffer.append(text, lastEnd, s)
          buffer.append(matchStart)
          buffer.append(text, s, e)
          buffer.append(matchEnd)
          lastEnd = e
        }
      }
      buffer.append(text, lastEnd, len)
      return buffer.toString()
    }

    private fun compileUncached(query: String): ArabicMorphologicalQuery? {
      if (query.isEmpty()) return null

      var nonParenCount = 0
      var hasArabicLetter = false
      var hasNonArabicSymbol = false

      for (i in query.indices) {
        val ch = query[i]
        if (ch == '(' || ch == ')') continue
        nonParenCount++
        if (isArabicIgnorable(ch) || ch.isWhitespace()) continue
        val folded = foldArabicLetter(ch)
        if (isArabicLetter(folded)) {
          hasArabicLetter = true
        } else {
          hasNonArabicSymbol = true
        }
      }

      if (nonParenCount == 0) return null

      if (!hasArabicLetter || hasNonArabicSymbol) {
        val stripped = buildString(query.length) {
          for (ch in query) {
            if (ch != '(' && ch != ')') append(ch)
          }
        }
        val literalCompiled = ArabicCompiledQuery.compileLiteralOnly(query)
        return ArabicMorphologicalQuery(query, Mode.Literal(literalCompiled, stripped))
      }

      // Pure Arabic query: extract normalized words preserving full unfolded letters alongside folded letters
      val origWords = ArrayList<String>(4)
      val normWords = ArrayList<String>(4)
      val curOrig = StringBuilder(16)
      val curNorm = StringBuilder(16)

      for (i in query.indices) {
        val ch = query[i]
        if (ch == '(' || ch == ')' || isArabicIgnorable(ch)) continue
        if (ch.isWhitespace()) {
          if (curNorm.isNotEmpty()) {
            origWords.add(curOrig.toString())
            normWords.add(curNorm.toString())
            curOrig.setLength(0)
            curNorm.setLength(0)
          }
          continue
        }
        val unpresented = mapPresentationForm(ch)
        val folded = foldArabicLetter(ch)
        if (isArabicLetter(folded)) {
          curOrig.append(unpresented)
          curNorm.append(folded)
        }
      }
      if (curNorm.isNotEmpty()) {
        origWords.add(curOrig.toString())
        normWords.add(curNorm.toString())
      }

      if (normWords.isEmpty()) {
        return ArabicMorphologicalQuery(query, Mode.Empty)
      }

      if (normWords.size == 1) {
        val normW = normWords[0]
        if (normW.length < 3) {
          return ArabicMorphologicalQuery(query, Mode.ShortArabic(normW))
        }
        val wm = buildWordMatcher(origWords[0], normW)
        return ArabicMorphologicalQuery(query, Mode.SingleWord(wm, normW))
      }

      val matchers = Array(normWords.size) { idx ->
        buildWordMatcher(origWords[idx], normWords[idx])
      }
      val phrase = normWords.joinToString(" ")
      return ArabicMorphologicalQuery(query, Mode.MultiWord(matchers, phrase))
    }

    private fun addWithProclitics(target: HashSet<String>, stem: String, proclitics: Array<String>) {
      for (p in proclitics) {
        target.add(p + stem)
      }
    }

    private fun addDefiniteForms(target: HashSet<String>, stem: String) {
      for (p in DEF_PROCLITICS) {
        target.add(p + "ال" + stem)
      }
      val lElided = if (stem.isNotEmpty() && stem[0] == CH_LAM) "ل" else "لل"
      for (p in L_PROCLITICS) {
        target.add(p + lElided + stem)
      }
    }

    private fun addIndefiniteForms(
      target: HashSet<String>,
      stem: String,
      allowVerbFuture: Boolean = false
    ) {
      for (p in INDEF_PROCLITICS) {
        target.add(p + stem)
      }
      if (allowVerbFuture) {
        for (p in VERB_PROCLITICS) {
          target.add(p + stem)
        }
      }
    }

    private fun addPronounForms(
      target: HashSet<String>,
      stem: String,
      proclitics: Array<String> = INDEF_PROCLITICS,
      prons: Array<String> = PRONOUNS,
      skipNa: Boolean = false
    ) {
      for (pron in prons) {
        if (skipNa && pron == "نا") continue
        addWithProclitics(target, stem + pron, proclitics)
      }
      if (stem.length >= 4 && stem.endsWith("اء")) {
        val base = stem.substring(0, stem.length - 1)
        for (pron in prons) {
          addWithProclitics(target, base + CH_WAW + pron, proclitics)
          addWithProclitics(target, base + CH_YA + pron, proclitics)
        }
      }
      if (MAQSURA_PRON_PLURALS.contains(stem)) {
        val base = stem.substring(0, stem.length - 1) + CH_ALEF
        for (pron in prons) {
          addWithProclitics(target, base + pron, proclitics)
        }
      }
    }

    private fun addFemParadigm(
      target: HashSet<String>,
      base: String,
      includeSingular: Boolean,
      includeDualPlural: Boolean
    ) {
      if (includeSingular) {
        for (e in charArrayOf(CH_TA_MARBUTA, CH_TA)) {
          val form = base + e
          addDefiniteForms(target, form)
          addIndefiniteForms(target, form)
        }
        for (pron in PRONOUNS) {
          addIndefiniteForms(target, base + CH_TA + pron)
        }
      }
      if (includeDualPlural) {
        for (dualEnd in arrayOf("تان", "تين")) {
          val form = base + dualEnd
          addDefiniteForms(target, form)
          addIndefiniteForms(target, form)
        }
        for (dualC in arrayOf("تا", "تي")) {
          for (pron in PRONOUNS) {
            addIndefiniteForms(target, base + dualC + pron)
          }
        }
        if (base != "ام") {
          val plurals = if (base.length >= 3 && base[base.length - 1] == CH_ALEF) {
            arrayOf(base + "ات", base.substring(0, base.length - 1) + "وات")
          } else {
            arrayOf(base + "ات")
          }
          for (pl in plurals) {
            addDefiniteForms(target, pl)
            addIndefiniteForms(target, pl)
            for (pron in PRONOUNS) {
              addIndefiniteForms(target, pl + pron)
            }
          }
        }
      }
    }

    private fun addMasculineInflections(
      t2: HashSet<String>,
      t3: HashSet<String>,
      stem: String,
      isParticipleShape: Boolean
    ) {
      if (!NO_FEM_EXPANSION.contains(stem)) {
        for (e in charArrayOf(CH_TA_MARBUTA, CH_TA)) {
          val form = stem + e
          addDefiniteForms(t2, form)
          addIndefiniteForms(t2, form)
        }
        for (pron in PRONOUNS) {
          addIndefiniteForms(t2, stem + CH_TA + pron)
        }
        addFemParadigm(t3, stem, includeSingular = false, includeDualPlural = true)
      }
      for (suf in arrayOf("ون", "ين")) {
        val form = stem + suf
        addDefiniteForms(t3, form)
        addIndefiniteForms(t3, form)
      }
      if (!NO_DUAL_AN_EXPANSION.contains(stem)) {
        val dual = stem + "ان"
        addDefiniteForms(t3, dual)
        addIndefiniteForms(t3, dual)
      }
      if (stem.length >= 4 && isParticipleShape) {
        addIndefiniteForms(t3, stem + CH_WAW)
        addIndefiniteForms(t3, stem + CH_YA)
        for (pron in PRONOUNS) {
          if (pron != "ي") {
            addIndefiniteForms(t3, stem + CH_WAW + pron)
            addIndefiniteForms(t3, stem + CH_YA + pron)
          }
        }
      }
    }

    private fun addFamilyMember(target: HashSet<String>, entry: String) {
      if (entry.startsWith("=")) {
        addDefiniteForms(target, entry.substring(1))
        return
      }
      if (entry.startsWith("!")) {
        addWithProclitics(target, entry.substring(1), SAFE_WA_LAM_PROCLITICS)
        return
      }
      if (entry.startsWith("@")) {
        val tok = entry.substring(1)
        val procls = if (tok.startsWith("يد")) SAFE_WA_BA_PROCLITICS else SAFE_NO_KAF_PROCLITICS
        addWithProclitics(target, tok, procls)
        return
      }
      val m = entry
      if (m.length == 2) {
        addDefiniteForms(target, m)
        when (m) {
          "يد" -> {
            addWithProclitics(target, m, SAFE_WA_BA_PROCLITICS)
            addPronounForms(target, m, proclitics = SAFE_WA_BA_PROCLITICS)
          }
          "رب" -> {
            addWithProclitics(target, m, SAFE_NO_KAF_PROCLITICS)
            addPronounForms(target, m, proclitics = SAFE_NO_KAF_PROCLITICS)
          }
          "ام" -> {
            addPronounForms(target, m, proclitics = SAFE_NO_KAF_PROCLITICS, prons = MOTHER_PRONOUNS)
          }
          "دم" -> {
            target.add("دم")
            target.add("ودم")
            target.add("بدم")
            addPronounForms(target, m, proclitics = SAFE_WA_BA_PROCLITICS)
          }
          "اخ", "صم" -> {
            target.add(m)
            target.add("و$m")
          }
          "حي" -> {
            target.add("حي")
          }
        }
        return
      }
      if (BROKEN_PLURAL_TA_MARBUTA.contains(m)) {
        val base = m.substring(0, m.length - 1)
        addDefiniteForms(target, m)
        addIndefiniteForms(target, m)
        for (pron in PRONOUNS) {
          addIndefiniteForms(target, base + CH_TA + pron)
        }
        return
      }
      if (m.length >= 3 && m[m.length - 1] == CH_TA_MARBUTA) {
        addFemParadigm(target, m.substring(0, m.length - 1), includeSingular = true, includeDualPlural = true)
        return
      }
      if (m == "كلم" || m == "قري") {
        addDefiniteForms(target, m)
        addWithProclitics(target, m, SAFE_WA_BA_LAM_PROCLITICS)
        return
      }

      addDefiniteForms(target, m)
      addIndefiniteForms(target, m)
      val last = m[m.length - 1]
      val endsAt = m.endsWith("ات")
      val endsNonAtInfl = m.endsWith("ون") || m.endsWith("ين") || m.endsWith("ان") ||
        m.endsWith("وا") || m.endsWith("تم") || m.endsWith("تن")
      if (last != CH_ALEF && last != CH_HAMZA && last != CH_HA &&
        !endsAt && !endsNonAtInfl && !NO_TANWIN_YA.contains(m) && m != "رحمن"
      ) {
        addIndefiniteForms(target, m + CH_ALEF)
      }
      if ((!endsNonAtInfl || BROKEN_PLURAL_N_PRON.contains(m)) &&
        !NO_PRON_FAMILY_MEMBERS.contains(m) &&
        (last != CH_HA || ROOT_HA_WORDS.contains(m))
      ) {
        addPronounForms(target, m, skipNa = (m == "كتب"))
      }
    }

    private fun buildWordMatcher(origW: String, normW: String): WordMatcher {
      val t1 = HashSet<String>(32)
      val t2 = HashSet<String>()
      val t3 = HashSet<String>()

      if (normW.length < 3) {
        for (p in SHORT_PHRASE_PROCLITICS) {
          t1.add(p + normW)
        }
        if (normW.length == 2) {
          addDefiniteForms(t1, normW)
        }
        return WordMatcher(t1, t2, t3)
      }

      // Case 1: The Blessed Name "الله" / "اللة"
      if (normW == "الله" || normW == "اللة") {
        for (p in ALLAH_PROCLITICS) t1.add(p + "الله")
        for (p in L_PROCLITICS) t1.add(p + "لله")
        t1.add("اللهم")
        t1.add("واللهم")
        t1.add("فاللهم")
        for (p in SHORT_PHRASE_PROCLITICS) t1.add(p + "عبدالله")
        return WordMatcher(t1, t2, t3)
      }

      val origFirst = origW[0]
      val origLast = origW[origW.length - 1]
      val hasAl = (origFirst == CH_ALEF || origFirst == CH_ALEF_WASLA) &&
        normW.length >= 4 &&
        normW[0] == CH_ALEF &&
        normW[1] == CH_LAM
      val stem = if (hasAl) normW.substring(2) else normW
      val origStem = if (hasAl && origW.length >= 2) origW.substring(2) else origW
      val origStemFirst = origStem[0]
      val origStemSecond = if (origStem.length >= 2) origStem[1] else '\u0000'

      val endsWn = stem.endsWith("ون")
      val endsYn = stem.endsWith("ين")
      val endsAn = stem.endsWith("ان")
      val endsAt = stem.endsWith("ات")
      val endsTan = stem.endsWith("تان")
      val endsTin = stem.endsWith("تين")

      val isFem = stem.length >= 3 && (origLast == CH_TA_MARBUTA || (hasAl && origLast == CH_HA))
      val isFa3ilShape = origStemSecond == CH_ALEF && (
        stem.length == 4 ||
          (stem.length == 5 && origLast == CH_TA_MARBUTA) ||
          (stem.length == 6 && (endsWn || endsYn || endsAn || endsAt))
        )
      val inKnownNouns = FAMILY_INDEX.containsKey(stem)
      val isVerbLike = !hasAl &&
        !isFem &&
        !inKnownNouns &&
        !isFa3ilShape &&
        stem.length >= 4 &&
        (stem[0] == CH_YA || stem[0] == CH_TA || stem[0] == CH_NUN)

      val familyLookupKeys = ArrayList<String>(3)
      familyLookupKeys.add(stem)

      if (isFem) {
        val base = stem.substring(0, stem.length - 1)
        val femCanon = base + CH_TA_MARBUTA

        if (BROKEN_PLURAL_TA_MARBUTA.contains(femCanon)) {
          val femEnds = if (origLast == CH_HA) charArrayOf(CH_TA_MARBUTA, CH_HA)
          else charArrayOf(CH_TA_MARBUTA)
          if (hasAl) {
            for (e in femEnds) {
              val form = base + e
              addDefiniteForms(t1, form)
              addIndefiniteForms(t2, form)
            }
          } else {
            for (e in femEnds) {
              val form = base + e
              addIndefiniteForms(t1, form)
              addDefiniteForms(t1, form)
            }
          }
          for (pron in PRONOUNS) {
            addIndefiniteForms(t2, base + CH_TA + pron)
          }
          if (origLast == CH_HA) familyLookupKeys.add(femCanon)
        } else {
          val femEnds = if (origLast == CH_HA) charArrayOf(CH_TA_MARBUTA, CH_TA, CH_HA)
          else charArrayOf(CH_TA_MARBUTA, CH_TA)
          if (hasAl) {
            for (e in femEnds) {
              val form = base + e
              addDefiniteForms(t1, form)
              addIndefiniteForms(t2, form)
            }
          } else {
            for (e in femEnds) {
              val form = base + e
              addIndefiniteForms(t1, form)
              addDefiniteForms(t1, form)
            }
          }
          for (pron in PRONOUNS) {
            addIndefiniteForms(t2, base + CH_TA + pron)
          }
          addFemParadigm(t3, base, includeSingular = false, includeDualPlural = true)
          if (origLast == CH_HA) familyLookupKeys.add(femCanon)

          val baseLast = base[base.length - 1]
          if (base.length >= 4 &&
            !FEM_PARTICIPLE_EXCLUDE.contains(femCanon) &&
            baseLast != CH_ALEF && baseLast != CH_YA && baseLast != CH_WAW && baseLast != CH_HAMZA
          ) {
            val isBasePart = base[0] == CH_MIM || (base.length == 4 && base[1] == CH_ALEF)
            if (isBasePart) {
              addDefiniteForms(t3, base)
              addIndefiniteForms(t3, base)
              addIndefiniteForms(t3, base + CH_ALEF)
              for (suf in arrayOf("ون", "ين")) {
                addDefiniteForms(t3, base + suf)
                addIndefiniteForms(t3, base + suf)
              }
              familyLookupKeys.add(base)
            }
          }
        }
      } else if (stem.length >= 5 && (endsTan || endsTin) && !isVerbLike && !NON_DUAL_TAN_TIN.contains(stem)) {
        val base = stem.substring(0, stem.length - 3)
        if (hasAl) {
          addDefiniteForms(t1, stem)
          addIndefiniteForms(t2, stem)
        } else {
          addIndefiniteForms(t1, stem)
          addDefiniteForms(t1, stem)
        }
        for (e in charArrayOf(CH_TA_MARBUTA, CH_TA)) {
          val form = base + e
          addDefiniteForms(t2, form)
          addIndefiniteForms(t2, form)
        }
        for (pron in PRONOUNS) {
          addIndefiniteForms(t2, base + CH_TA + pron)
        }
        addFemParadigm(t3, base, includeSingular = false, includeDualPlural = true)
        familyLookupKeys.add(base + CH_TA_MARBUTA)
      } else if (stem.length >= 4 && endsAt && !isVerbLike && !NON_PLURAL_AT.contains(stem)) {
        val base = stem.substring(0, stem.length - 2)
        if (hasAl) {
          addDefiniteForms(t1, stem)
          addIndefiniteForms(t2, stem)
        } else {
          addIndefiniteForms(t1, stem)
          addDefiniteForms(t1, stem)
        }
        for (pron in PRONOUNS) {
          addIndefiniteForms(t2, stem + pron)
        }
        if (base.length >= 2 && base != "بن" && base != "سماو" && base != "ام") {
          for (e in charArrayOf(CH_TA_MARBUTA, CH_TA)) {
            val form = base + e
            addDefiniteForms(t2, form)
            addIndefiniteForms(t2, form)
          }
          for (pron in PRONOUNS) {
            addIndefiniteForms(t2, base + CH_TA + pron)
          }
          if (base.length >= 3 && base[base.length - 1] == CH_WAW) {
            val aBase = base.substring(0, base.length - 1) + CH_ALEF
            for (e in charArrayOf(CH_TA_MARBUTA, CH_TA)) {
              val form = aBase + e
              addDefiniteForms(t2, form)
              addIndefiniteForms(t2, form)
            }
            familyLookupKeys.add(aBase + CH_TA_MARBUTA)
          }
          familyLookupKeys.add(base + CH_TA_MARBUTA)

          for (dualEnd in arrayOf("تان", "تين")) {
            val form = base + dualEnd
            addDefiniteForms(t3, form)
            addIndefiniteForms(t3, form)
          }
          for (dualC in arrayOf("تا", "تي")) {
            for (pron in PRONOUNS) {
              addIndefiniteForms(t3, base + dualC + pron)
            }
          }
          if (base.length >= 4 &&
            (base[0] == CH_MIM || (base.length == 4 && base[1] == CH_ALEF)) &&
            !NO_FEM_EXPANSION.contains(base)
          ) {
            addDefiniteForms(t3, base)
            addIndefiniteForms(t3, base)
            val baseLast = base[base.length - 1]
            if (baseLast != CH_ALEF && baseLast != CH_HAMZA && baseLast != CH_YA) {
              addIndefiniteForms(t3, base + CH_ALEF)
              addPronounForms(t3, base)
              for (suf in arrayOf("ون", "ين")) {
                addDefiniteForms(t3, base + suf)
                addIndefiniteForms(t3, base + suf)
              }
            }
            familyLookupKeys.add(base)
          }
        }
      } else if (stem.length >= 5 &&
        (endsWn || endsYn) &&
        !isVerbLike &&
        !NON_PLURAL_WN_YN.contains(stem) &&
        !CARDINAL_NUMBERS.contains(stem)
      ) {
        val base = stem.substring(0, stem.length - 2)
        val altSuf = if (endsWn) "ين" else "ون"
        if (hasAl) {
          addDefiniteForms(t1, stem)
          addIndefiniteForms(t2, stem)
        } else {
          addIndefiniteForms(t1, stem)
          addDefiniteForms(t1, stem)
        }
        val altPl = base + altSuf
        addDefiniteForms(t3, altPl)
        addIndefiniteForms(t3, altPl)

        val baseLast = base[base.length - 1]
        if (base.length >= 4 || SAFE_3_REV_BASES.contains(base)) {
          addDefiniteForms(t2, base)
          addIndefiniteForms(t2, base)
          if (baseLast != CH_ALEF && baseLast != CH_HAMZA && baseLast != CH_HA &&
            !NO_TANWIN_YA.contains(base) && base != "رحمن"
          ) {
            addIndefiniteForms(t2, base + CH_ALEF)
          }
          if (baseLast != CH_HA || ROOT_HA_WORDS.contains(base)) {
            addPronounForms(t2, base)
          }
          if (stem != "عالمين" && stem != "عالمون") {
            familyLookupKeys.add(base)
          }
        }

        if (base.length >= 3 &&
          baseLast != CH_YA &&
          (base[0] == CH_MIM || (base.length == 3 && base[1] == CH_ALEF) || base == "اعل")
        ) {
          val weakSg = base + CH_YA
          addDefiniteForms(t2, weakSg)
          addIndefiniteForms(t2, weakSg)
        }

        if (base.length >= 4 &&
          baseLast != CH_ALEF && baseLast != CH_HAMZA && baseLast != CH_YA &&
          baseLast != CH_WAW && baseLast != CH_HA &&
          !NO_FEM_EXPANSION.contains(base)
        ) {
          val isPart = base[0] == CH_MIM || (base.length == 4 && base[1] == CH_ALEF)
          if (isPart) {
            for (e in charArrayOf(CH_TA_MARBUTA, CH_TA)) {
              val form = base + e
              addDefiniteForms(t2, form)
              addIndefiniteForms(t2, form)
            }
            for (pron in PRONOUNS) {
              addIndefiniteForms(t2, base + CH_TA + pron)
            }
            addFemParadigm(t3, base, includeSingular = false, includeDualPlural = true)
            if (!NO_DUAL_AN_EXPANSION.contains(base)) {
              val dual = base + "ان"
              addDefiniteForms(t3, dual)
              addIndefiniteForms(t3, dual)
            }
            addIndefiniteForms(t3, base + CH_WAW)
            addIndefiniteForms(t3, base + CH_YA)
            for (pron in PRONOUNS) {
              if (pron != "ي") {
                addIndefiniteForms(t3, base + CH_WAW + pron)
                addIndefiniteForms(t3, base + CH_YA + pron)
              }
            }
          }
        }
      } else if (stem.length >= 6 && endsAn && !isVerbLike && !NON_DUAL_AN.contains(stem)) {
        val base = stem.substring(0, stem.length - 2)
        if (hasAl) {
          addDefiniteForms(t1, stem)
          addIndefiniteForms(t2, stem)
        } else {
          addIndefiniteForms(t1, stem)
          addDefiniteForms(t1, stem)
        }
        if (base.length >= 4 &&
          (base[0] == CH_MIM || (base.length == 4 && base[1] == CH_ALEF) || base == "فريق")
        ) {
          addDefiniteForms(t2, base)
          addIndefiniteForms(t2, base)
          val baseLast = base[base.length - 1]
          if (baseLast != CH_ALEF && baseLast != CH_HAMZA && baseLast != CH_YA && baseLast != CH_HA) {
            addIndefiniteForms(t2, base + CH_ALEF)
            addPronounForms(t2, base)
          }
          val isPart = base[0] == CH_MIM || (base.length == 4 && base[1] == CH_ALEF)
          addMasculineInflections(t2, t3, base, isParticipleShape = isPart)
          familyLookupKeys.add(base)
        }
      } else if (hasAl) {
        addDefiniteForms(t1, stem)
        if (stem.length >= 3) {
          addIndefiniteForms(t2, stem)
          val last = stem[stem.length - 1]
          if (last != CH_ALEF && last != CH_HAMZA && !NO_TANWIN_YA.contains(stem) && stem != "رحمن") {
            addIndefiniteForms(t2, stem + CH_ALEF)
          }
          if ((!endsWn && !endsYn) || BROKEN_PLURAL_N_PRON.contains(stem)) {
            addPronounForms(t2, stem, skipNa = (stem == "كتب"))
          }
          val canMasc = !CARDINAL_NUMBERS.contains(stem) &&
            last != CH_TA_MARBUTA && last != CH_ALEF && last != CH_HAMZA &&
            last != CH_WAW && last != CH_HA && last != CH_YA &&
            !endsWn && !endsYn && !endsAn && !endsAt &&
            (stem.length >= 4 || (stem.length == 3 && origStemFirst == CH_ALEF_MADDA))
          if (canMasc) {
            val isPart = stem[0] == CH_MIM || (stem.length == 4 && origStemSecond == CH_ALEF)
            addMasculineInflections(t2, t3, stem, isParticipleShape = isPart)
          } else if (stem.length >= 4 &&
            last == CH_YA &&
            !CARDINAL_NUMBERS.contains(stem) &&
            ((stem[0] == CH_MIM && stem != "موسي" && !inKnownNouns) ||
              (stem.length == 4 && origStemSecond == CH_ALEF) ||
              stem == "اعلي")
          ) {
            val dropY = stem.substring(0, stem.length - 1)
            for (suf in arrayOf("ون", "ين")) {
              addDefiniteForms(t3, dropY + suf)
              addIndefiniteForms(t3, dropY + suf)
            }
            addFemParadigm(t3, stem, includeSingular = true, includeDualPlural = true)
          }
        } else {
          when (stem) {
            "يد" -> {
              addWithProclitics(t2, stem, SAFE_WA_BA_PROCLITICS)
              addPronounForms(t2, stem, proclitics = SAFE_WA_BA_PROCLITICS)
            }
            "رب" -> {
              addWithProclitics(t2, stem, SAFE_NO_KAF_PROCLITICS)
              addPronounForms(t2, stem, proclitics = SAFE_NO_KAF_PROCLITICS)
            }
            "ام" -> {
              addPronounForms(t2, stem, proclitics = SAFE_NO_KAF_PROCLITICS, prons = MOTHER_PRONOUNS)
            }
            "دم" -> {
              t2.add("دم")
              t2.add("ودم")
              t2.add("بدم")
              addPronounForms(t2, stem, proclitics = SAFE_WA_BA_PROCLITICS)
            }
            else -> {
              t2.add(stem)
              if (stem != "حي") {
                t2.add("و$stem")
              }
            }
          }
        }
      } else {
        addIndefiniteForms(t1, stem, allowVerbFuture = isVerbLike)
        addDefiniteForms(t1, stem)
        val last = stem[stem.length - 1]
        if (last == CH_HA && !ROOT_HA_WORDS.contains(stem)) {
          val altTa = stem.substring(0, stem.length - 1) + CH_TA_MARBUTA
          addIndefiniteForms(t1, altTa)
          addDefiniteForms(t1, altTa)
        }

        val endsWa = stem.endsWith("وا")
        val endsTm = stem.endsWith("تم")
        val endsTn = stem.endsWith("تن")
        val endsPron = (last == CH_HA && !ROOT_HA_WORDS.contains(stem)) ||
          stem.endsWith("ها") ||
          stem.endsWith("هم") ||
          stem.endsWith("كم") ||
          stem.endsWith("نا")

        if (last != CH_ALEF &&
          last != CH_HAMZA &&
          !endsWn &&
          !endsYn &&
          !endsWa &&
          !endsTm &&
          !endsTn &&
          !NO_TANWIN_YA.contains(stem) &&
          stem != "رحمن" &&
          stem != "كلم"
        ) {
          addIndefiniteForms(t2, stem + CH_ALEF)
        }
        if (((!endsWa && !endsWn && !endsYn && !endsTm && !endsTn && !endsPron) ||
            BROKEN_PLURAL_N_PRON.contains(stem)) &&
          stem != "كلم" && stem != "قري"
        ) {
          addPronounForms(t2, stem, skipNa = (stem == "كتب"))
        }

        val startsFoldedAl = stem.startsWith("ال")
        val canMasc = !isVerbLike &&
          !startsFoldedAl &&
          !CARDINAL_NUMBERS.contains(stem) &&
          last != CH_TA_MARBUTA && last != CH_ALEF && last != CH_HAMZA &&
          last != CH_WAW && last != CH_HA && last != CH_YA &&
          !endsWn && !endsYn && !endsAn && !endsAt &&
          !endsWa && !endsTm && !endsTn && !endsPron &&
          (stem.length >= 4 || (stem.length == 3 && origStemFirst == CH_ALEF_MADDA))
        if (canMasc) {
          val isPart = stem[0] == CH_MIM || (stem.length == 4 && origStemSecond == CH_ALEF)
          addMasculineInflections(t2, t3, stem, isParticipleShape = isPart)
        } else if (!isVerbLike &&
          !startsFoldedAl &&
          stem.length >= 4 &&
          last == CH_YA &&
          !CARDINAL_NUMBERS.contains(stem) &&
          ((stem[0] == CH_MIM && stem != "موسي" && !inKnownNouns) ||
            (stem.length == 4 && origStemSecond == CH_ALEF) ||
            stem == "اعلي")
        ) {
          val dropY = stem.substring(0, stem.length - 1)
          for (suf in arrayOf("ون", "ين")) {
            addDefiniteForms(t3, dropY + suf)
            addIndefiniteForms(t3, dropY + suf)
          }
          addFemParadigm(t3, stem, includeSingular = true, includeDualPlural = true)
        }
      }

      // Curated Quranic Broken-Plural & Irregular-Lemma Family Expansion (Tier 3)
      if (!isVerbLike) {
        val seenFams = HashSet<Int>(4)
        for (key in familyLookupKeys) {
          val famIds = FAMILY_INDEX[key] ?: continue
          for (fid in famIds) {
            if (seenFams.add(fid)) {
              for (entry in LEMMA_FAMILIES[fid]) {
                val raw = if (entry.isNotEmpty() && (entry[0] == '=' || entry[0] == '@' || entry[0] == '!')) {
                  entry.substring(1)
                } else {
                  entry
                }
                if (raw != stem) {
                  addFamilyMember(t3, entry)
                }
              }
            }
          }
        }
      }

      t2.removeAll(t1)
      t3.removeAll(t1)
      t3.removeAll(t2)
      return WordMatcher(t1, t2, t3)
    }

    private fun normCharMatches(queryCh: Char, textCh: Char): Boolean {
      if (queryCh == textCh) return true
      return when (queryCh) {
        CH_HA -> textCh == CH_TA_MARBUTA
        CH_TA_MARBUTA -> textCh == CH_HA || textCh == CH_TA
        CH_TA -> textCh == CH_TA_MARBUTA
        else -> false
      }
    }

    private fun collectNormSubstringSpans(
      rawText: String,
      needle: String,
      startsOut: ArrayList<Int>,
      endsOut: ArrayList<Int>
    ) {
      val nLen = needle.length
      val tLen = rawText.length
      if (nLen == 0 || tLen == 0) return

      val norm = CharArray(tLen)
      val origStart = IntArray(tLen)
      val origEnd = IntArray(tLen)
      var normLen = 0
      var prevSpace = true

      for (i in 0 until tLen) {
        val ch = rawText[i]
        if (isArabicIgnorable(ch)) {
          if (normLen > 0 && !prevSpace) {
            origEnd[normLen - 1] = i + 1
          }
          continue
        }
        val folded = foldArabicLetter(ch)
        if (isArabicLetter(folded)) {
          norm[normLen] = folded
          origStart[normLen] = i
          origEnd[normLen] = i + 1
          normLen++
          prevSpace = false
        } else if (!prevSpace && normLen > 0) {
          norm[normLen] = ' '
          origStart[normLen] = i
          origEnd[normLen] = i + 1
          normLen++
          prevSpace = true
        }
      }
      if (normLen > 0 && norm[normLen - 1] == ' ') {
        normLen--
      }
      if (normLen < nLen) return

      val maxStart = normLen - nLen
      val first = needle[0]
      var pos = 0
      while (pos <= maxStart) {
        if (normCharMatches(first, norm[pos])) {
          var j = 1
          while (j < nLen && normCharMatches(needle[j], norm[pos + j])) {
            j++
          }
          if (j == nLen) {
            startsOut.add(origStart[pos])
            endsOut.add(origEnd[pos + nLen - 1])
            pos += nLen
            continue
          }
        }
        pos++
      }
    }
  }
}
