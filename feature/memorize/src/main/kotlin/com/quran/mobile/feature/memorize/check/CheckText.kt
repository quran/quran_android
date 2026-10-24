package com.quran.mobile.feature.memorize.check

import com.quran.mobile.feature.memorize.tracker.PhonemeNormalizer

/**
 * The letters the double-check compares: what its model writes, and how each word of the Quran is
 * written in the plain-letter text it was trained on (the "clean" Tanzil text of its quran.json).
 * Both sides are reduced to a skeleton of base letters, hamza seats and alif forms unified. A port of
 * the lab's text_norm.py / tiers_stream.py; [CheckTextTest] checks it against the lab's tables.
 */
internal object CheckText {

  /** One piece of the model's vocabulary as letters; "|" where a word starts. */
  fun pieceLetters(piece: String): List<String> = buildList {
    for (ch in piece) {
      if (ch == WORD_START || ch.isWhitespace()) {
        add(WORD_BOUNDARY)
      } else {
        letters(ch.toString()).forEach { add(it.toString()) }
      }
    }
  }

  /** The skeleton of a word of the clean text, runs collapsed. */
  fun wordSkeleton(word: String): String = collapse(letters(word))

  /** The skeleton of a word from the app's lexicon (its phonemes are already letters). */
  fun phonemeSkeleton(wasl: String): String = PhonemeNormalizer.normalize(wasl, false).replace('ة', 'ه')

  /**
   * The clean-text skeleton of each of an ayah's words as the lexicon splits them ([lexicon]: their
   * phonemes), from the ayah's clean text. The two split a few ayahs differently (217 of 6,236), e.g.
   * one word in one and two in the other: then the words are aligned by their letters.
   */
  fun ayahSkeletons(sura: Int, ayah: Int, lexicon: List<String>, clean: String): List<String> {
    var words = clean.replace("﻿", "").split(WHITESPACE).filter { it.isNotEmpty() }
    // the clean text starts each sura's first ayah with the basmala, the lexicon doesn't
    if (ayah == 1 && sura != 1 && sura != 9 && words.take(4) == BASMALA) words = words.drop(4)
    val lex = lexicon.map { phonemeSkeleton(it) }
    val cleanSkeletons = words.map { wordSkeleton(it) }
    if (lex.isEmpty()) return emptyList()
    val aligned = if (lex.size == cleanSkeletons.size) cleanSkeletons else alignWords(lex, cleanSkeletons)
    return aligned.mapIndexed { i, skeleton -> skeleton?.takeIf { it.isNotEmpty() } ?: lex[i] }
  }

  /** Per lexicon word, the clean skeleton covering it (groups 1:1, 1:2 and 2:1). */
  fun alignWords(lex: List<String>, clean: List<String>): List<String?> {
    val n = lex.size
    val m = clean.size
    val cost = Array(n + 1) { DoubleArray(m + 1) { Double.POSITIVE_INFINITY } }
    val step = Array(n + 1) { arrayOfNulls<IntArray>(m + 1) }
    cost[0][0] = 0.0
    for (i in 0..n) {
      for (j in 0..m) {
        if (cost[i][j] == Double.POSITIVE_INFINITY) continue
        for ((di, dj) in STEPS) {
          if (i + di > n || j + dj > m) continue
          val c = cost[i][j] + edit(lex.subList(i, i + di).joinToString(""), clean.subList(j, j + dj).joinToString("")) +
            if (di + dj > 2) 0.5 else 0.0
          if (c < cost[i + di][j + dj]) {
            cost[i + di][j + dj] = c
            step[i + di][j + dj] = intArrayOf(di, dj)
          }
        }
      }
    }
    val out = arrayOfNulls<String>(n)
    var i = n
    var j = m
    while (i > 0) {
      val (di, dj) = step[i][j] ?: return List(n) { null }
      i -= di
      j -= dj
      if (di == 1) {
        out[i] = clean.subList(j, j + dj).joinToString("")
      } else {
        // one clean word for two lexicon words: split it where it fits them best
        val w = clean[j]
        val k = (0..w.length).minBy { edit(lex[i], w.substring(0, it)) + edit(lex[i + 1], w.substring(it)) }
        out[i] = w.substring(0, k)
        out[i + 1] = w.substring(k)
      }
    }
    return out.toList()
  }

  fun edit(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
      val current = IntArray(b.length + 1)
      current[0] = i
      for (j in 1..b.length) {
        current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
      }
      previous = current
    }
    return previous[b.length]
  }

  // small waw/ya dropped (the model has no piece for them), hamza on/under alif and alif wasla
  // folded to alif (the clean text has none), then text_norm's unification; diacritics and marks
  // removed; only base letters kept
  private fun letters(text: String): String {
    val out = StringBuilder()
    for (ch in text) {
      when (ch) {
        'ۥ', 'ۦ', '﻿', 'ـ' -> Unit
        'أ', 'إ', 'ٱ' -> out.append('ا')
        'ؤ', 'ئ' -> out.append('ء')
        'آ' -> out.append("ءا")
        'ى' -> out.append('ي')
        'ة' -> out.append('ه')
        'ٰ' -> out.append('ا')
        else -> if (ch in 'ء'..'ي') out.append(ch)
      }
    }
    return out.toString()
  }

  private fun collapse(text: String): String {
    val out = StringBuilder()
    for (ch in text) if (out.isEmpty() || out.last() != ch) out.append(ch)
    return out.toString()
  }

  const val WORD_BOUNDARY = "|"
  private const val WORD_START = '▁'
  private val WHITESPACE = Regex("\\s+")
  private val BASMALA = listOf("بسم", "الله", "الرحمن", "الرحيم")
  private val STEPS = listOf(1 to 1, 1 to 2, 2 to 1)
}
