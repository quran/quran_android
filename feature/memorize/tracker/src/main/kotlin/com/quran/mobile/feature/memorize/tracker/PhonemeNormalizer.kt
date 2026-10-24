package com.quran.mobile.feature.memorize.tracker

/**
 * Folds the quran-transcript phonetic script (what the speech model emits and what the lexicon
 * stores) to the letter level used for memorization: tajweed markers map to their base letter or
 * are dropped, madd letters map to their letter class, and runs of the same symbol collapse (madd
 * lengths, gemination, ghunna). Short vowels are kept unless [keepShortVowels] is false.
 */
object PhonemeNormalizer {

  private val special = mapOf(
    'ڇ' to null, // qalqala
    'ۜ' to null, // sakt
    'ـ' to null, // elongation marker
    'ں' to 'ن', // noon mokhfah (ikhfa)
    '۾' to 'م', // meem mokhfah
    'ٲ' to 'ء', // hamza mosahala
    'ؙ' to 'ُ', // dama mokhtalasa
    '۪' to 'َ', // fatha momala (imala)
  )

  private val longVowels = mapOf('ا' to 'ا', 'ۥ' to 'و', 'ۦ' to 'ي')

  val shortVowels: Set<Char> = setOf('َ', 'ُ', 'ِ')

  fun normalize(phonemes: String, keepShortVowels: Boolean = true): String {
    val out = StringBuilder(phonemes.length)
    for (raw in phonemes) {
      if (raw.isWhitespace()) continue
      val mapped = if (special.containsKey(raw)) special[raw] ?: continue else raw
      val ch = longVowels[mapped] ?: mapped
      if (!keepShortVowels && ch in shortVowels) continue
      if (out.isNotEmpty() && out[out.length - 1] == ch) continue
      out.append(ch)
    }
    return out.toString()
  }
}
