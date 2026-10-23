package com.quran.mobile.feature.memorize.check

import com.quran.mobile.feature.memorize.data.Passage

/** The double-check of a session, if it is on and installed (loading takes seconds). */
fun interface DoubleCheckSource {
  fun open(): DoubleCheck?
}

/**
 * A second opinion before the session corrects the user: whether the word the session is waiting
 * for was said after all, in the last seconds the user said. If it was, the hint isn't spoken, or
 * the mistake isn't shown.
 */
interface DoubleCheck : AutoCloseable {
  /** Whether word [k] of [passage] was said in [audio] (16 kHz mono, the last seconds). */
  fun said(audio: FloatArray, passage: Passage, k: Int): Boolean

  /** How long the last check took, in milliseconds. */
  val lastMs: Long
}

/** An offline CTC model: the pieces of its vocabulary it hears in some audio (16 kHz mono). */
interface PieceRecognizer : AutoCloseable {
  /** Blanks and repeats collapsed. */
  fun pieces(audio: FloatArray): IntArray

  override fun close() = Unit
}

/**
 * The double-check with a larger model, the Tilawi FastConformer. It listens again to the last
 * seconds, with all of their context. It was measured in the lab (REPORT.md §15) and on a phone.
 *
 * The rule: the word must be in the model's transcript, within a quarter of its letters, right after
 * the word before it (for the passage's first word, right before the next one). One word heard alone
 * isn't enough, because a short word matches too much.
 */
class TilawiDoubleCheck internal constructor(
  private val recognizer: PieceRecognizer,
  /** Per piece of the vocabulary, its letters ("|" where a word starts). */
  private val pieces: List<List<String>>,
  /** The clean text of an ayah, as the model was trained on. */
  private val cleanText: (sura: Int, ayah: Int) -> String?,
) : DoubleCheck {

  // per ayah (sura * 1000 + ayah), the skeletons of its words
  private val skeletons = HashMap<Int, List<String>>()

  @Volatile override var lastMs: Long = 0L
    private set

  /** What the model heard, as letters: runs within a word collapsed, word boundaries dropped. */
  internal fun letters(audio: FloatArray): String {
    val out = StringBuilder()
    var last: String? = null
    var inWord = false
    for (id in recognizer.pieces(audio)) {
      for (letter in pieces.getOrNull(id).orEmpty()) {
        if (letter == CheckText.WORD_BOUNDARY) {
          inWord = false
          last = null
          continue
        }
        if (inWord && letter == last) continue
        out.append(letter)
        last = letter
        inWord = true
      }
    }
    return out.toString()
  }

  override fun said(audio: FloatArray, passage: Passage, k: Int): Boolean {
    if (audio.size < MIN_SAMPLES || k !in passage.words.indices) return false
    val started = System.nanoTime()
    try {
      val word = skeleton(passage, k) ?: return false
      val before = if (k > 0) skeleton(passage, k - 1) else null
      val after = if (k == 0) skeleton(passage, k + 1) else null
      if (before == null && after == null) return false
      val text = letters(audio)
      val n = word.length
      for (i in text.indices) {
        for (d in -1..1) {
          if (n + d <= 0 || i + n + d > text.length) continue
          if (CheckText.edit(text.substring(i, i + n + d), word) > tolerance(word)) continue
          if (before != null && endsWith(text.substring(0, i), before)) return true
          if (after != null && startsWith(text.substring(i + n + d), after)) return true
        }
      }
      return false
    } finally {
      lastMs = (System.nanoTime() - started) / 1_000_000
    }
  }

  private fun skeleton(passage: Passage, index: Int): String? {
    val word = passage.words.getOrNull(index)?.id ?: return null
    val key = word.sura * 1000 + word.ayah
    val ayah = skeletons.getOrPut(key) {
      val clean = cleanText(word.sura, word.ayah) ?: return null
      val lexicon = passage.words.filter { it.id.sura == word.sura && it.id.ayah == word.ayah }.map { it.wasl }
      CheckText.ayahSkeletons(word.sura, word.ayah, lexicon, clean)
    }
    return ayah.getOrNull(word.word - 1)
  }

  private fun tolerance(word: String) = word.length / 4

  private fun endsWith(text: String, word: String) = (-1..1).any { d ->
    val n = word.length + d
    n > 0 && text.length >= n && CheckText.edit(text.substring(text.length - n), word) <= tolerance(word)
  }

  private fun startsWith(text: String, word: String) = (-1..1).any { d ->
    val n = word.length + d
    n > 0 && text.length >= n && CheckText.edit(text.substring(0, n), word) <= tolerance(word)
  }

  override fun close() = recognizer.close()

  private companion object {
    // less than a second can't hold a word and its neighbour
    const val MIN_SAMPLES = 16_000
  }
}
