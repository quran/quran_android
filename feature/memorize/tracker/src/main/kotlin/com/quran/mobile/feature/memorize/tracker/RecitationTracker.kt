package com.quran.mobile.feature.memorize.tracker

import com.quran.mobile.feature.memorize.tracker.TrackerEvent.Mistake
import com.quran.mobile.feature.memorize.tracker.TrackerEvent.NonTarget
import com.quran.mobile.feature.memorize.tracker.TrackerEvent.Repetition
import com.quran.mobile.feature.memorize.tracker.TrackerEvent.WordMissed
import com.quran.mobile.feature.memorize.tracker.TrackerEvent.WordRecited

/**
 * Follows a user reciting a known passage from memory.
 *
 * [words] are the passage's words as normalized phoneme strings ([PhonemeNormalizer]). The
 * recognizer's output is fed in as [TimedPhoneme]s; the tracker answers with [TrackerEvent]s:
 * which words were recited (to reveal them), where the user made a mistake, repeated themselves or
 * skipped ahead.
 *
 * On every update the phonemes heard since the last recited word are aligned with a window of
 * expected words using a weighted edit distance in which:
 *  - the alignment may start at any word at or before the cursor (going back to repeat is normal),
 *  - leading phonemes may be skipped as "garbage" (false starts, coughs, wrong attempts),
 *  - substitutions between vowels and between commonly confused consonants are cheaper,
 *  - insertions/deletions near word boundaries are cheaper (wasl/waqf pronunciation differences),
 *  - the end of the expected side is free (the user is in the middle of the passage).
 * When nothing near the cursor matches after a mistake, a wider search finds where the user is.
 *
 * This is a port of the reference implementation used to benchmark the speech models; keep the
 * two in sync (see the golden tests).
 */
class RecitationTracker(
  private val words: List<String>,
  private val config: TrackerConfig = TrackerConfig(),
  startIndex: Int = 0,
  /** Phrases said around the passage that aren't mistakes: (kind, normalized phonemes). */
  private val phrases: List<Pair<String, String>> = emptyList(),
) {

  var cursor: Int = startIndex
    private set

  val isDone: Boolean get() = cursor >= words.size

  private val pending = ArrayList<TimedPhoneme>()
  private var mistakeAt: Int? = null

  fun feed(phonemes: List<TimedPhoneme>): List<TrackerEvent> {
    pending.addAll(phonemes)
    if (pending.size > config.maxPending) {
      pending.subList(0, pending.size - config.maxPending).clear()
    }
    if (isDone || pending.isEmpty()) return emptyList()
    return update()
  }

  /**
   * The user stopped making sound. A phrase heard completely up to now is complete even though
   * nothing follows it (e.g. the takbir before ruku).
   */
  fun onPause(): List<TrackerEvent> {
    if (pending.isEmpty() || phrases.isEmpty()) return emptyList()
    val heard = CharArray(pending.size) { pending[it].symbol }
    for ((kind, phrase) in phrases) {
      if (phraseCost(heard, phrase) <= 0.5 + 0.2 * phrase.length) {
        val event = NonTarget(cursor, pending.last().timeMs, kind)
        pending.clear()
        mistakeAt = null
        return listOf(event)
      }
    }
    return emptyList()
  }

  /** What [count] sounds heard before a phrase cost (e.g. the rest of the last word, or an earlier phrase). */
  private fun phraseGarbage(count: Int): Double = minOf(count * config.garbageCost, config.phraseGarbageCap)

  /** Cost of [heard] being exactly [phrase] (allowing leading garbage). */
  private fun phraseCost(heard: CharArray, phrase: String): Double {
    val n = heard.size
    val m = phrase.length
    val width = m + 1
    val cost = DoubleArray((n + 1) * width) { Double.POSITIVE_INFINITY }
    for (i in 0..n) cost[i * width] = phraseGarbage(i)
    for (i in 0..n) {
      for (j in 1..m) {
        var c = Double.POSITIVE_INFINITY
        if (i > 0) {
          c = minOf(
            cost[(i - 1) * width + j - 1] + sub(heard[i - 1], phrase[j - 1]),
            cost[(i - 1) * width + j] + config.indelCost
          )
        }
        cost[i * width + j] = minOf(c, cost[i * width + j - 1] + config.indelCost)
      }
    }
    return cost[n * width + m]
  }

  /** Forgets what was heard since the last recited word (it belonged to something else). */
  fun discardPending() {
    pending.clear()
    mistakeAt = null
  }

  /** The app revealed/played the next [count] words: they count as recited. */
  fun markHinted(count: Int) {
    cursor = minOf(words.size, cursor + count)
    pending.clear()
    mistakeAt = null
  }

  // ---------------------------------------------------------------------------------------- costs

  private fun sub(a: Char, b: Char): Double {
    if (a == b) return 0.0
    val aVowel = a in PhonemeNormalizer.shortVowels
    val bVowel = b in PhonemeNormalizer.shortVowels
    if (aVowel && bVowel) return config.vowelSub
    if (aVowel || bVowel) return config.consonantSub
    if (CONFUSABLE.any { a in it && b in it }) return config.confusableSub
    return config.consonantSub
  }

  private fun indel(symbol: Char, pos: Int, length: Int): Double = when {
    pos < config.edge || pos >= length - config.edge -> config.edgeIndelCost
    symbol in PhonemeNormalizer.shortVowels -> config.vowelIndel
    else -> config.indelCost
  }

  // ------------------------------------------------------------------------------------ alignment

  private class Step(val op: Char, val pendingIndex: Int, val column: Int)

  private class Window(val first: Int, val symbols: CharArray, val word: IntArray, val pos: IntArray)

  private fun window(first: Int, last: Int): Window {
    val symbols = StringBuilder()
    val word = ArrayList<Int>()
    val pos = ArrayList<Int>()
    for (w in first until last) {
      words[w].forEachIndexed { i, ch ->
        symbols.append(ch)
        word.add(w)
        pos.add(i)
      }
    }
    return Window(first, symbols.toString().toCharArray(), word.toIntArray(), pos.toIntArray())
  }

  /** Semi-global alignment of [heard] with the window; returns the path (garbage step first). */
  private fun align(heard: CharArray, win: Window): List<Step> {
    val n = heard.size
    val m = win.symbols.size
    val width = m + 1
    val cost = DoubleArray((n + 1) * width) { Double.POSITIVE_INFINITY }
    val back = CharArray((n + 1) * width)

    val allowedStarts = BooleanArray(width)
    for (j in 0 until m) {
      if ((j == 0 || win.word[j - 1] != win.word[j]) && win.word[j] <= cursor) allowedStarts[j] = true
    }
    for (i in 0..n) {
      for (j in 0..m) {
        if (allowedStarts[j]) {
          cost[i * width + j] = i * config.garbageCost
          back[i * width + j] = 'g'
        }
      }
    }

    for (i in 0..n) {
      for (j in (if (i == 0) 1 else 0)..m) {
        val idx = i * width + j
        var best = cost[idx]
        var op = back[idx]
        if (i > 0 && j > 0) {
          val diag = cost[(i - 1) * width + j - 1] + sub(heard[i - 1], win.symbols[j - 1])
          if (diag < best) {
            best = diag
            op = if (heard[i - 1] == win.symbols[j - 1]) 'm' else 's'
          }
          val length = words[win.word[j - 1]].length
          val ins = cost[(i - 1) * width + j] + indel(heard[i - 1], win.pos[j - 1], length)
          if (ins < best) {
            best = ins
            op = 'i'
          }
        }
        if (j > 0) {
          val length = words[win.word[j - 1]].length
          val del = cost[i * width + j - 1] + indel(win.symbols[j - 1], win.pos[j - 1], length)
          if (del < best) {
            best = del
            op = 'd'
          }
        }
        cost[idx] = best
        back[idx] = op
      }
    }

    // best end column for all of the heard phonemes (ties: the furthest column)
    var jBest = 0
    for (j in 1..m) {
      if (cost[n * width + j] <= cost[n * width + jBest]) jBest = j
    }

    val path = ArrayList<Step>()
    var i = n
    var j = jBest
    while ((i > 0 || j > 0) && back[i * width + j] != '\u0000') {
      val op = back[i * width + j]
      if (op == 'g') {
        path.add(Step('g', i, j))
        break
      }
      path.add(Step(op, if (op == 'd') -1 else i - 1, j - 1))
      when (op) {
        'm', 's' -> { i--; j-- }
        'i' -> i--
        else -> j--
      }
    }
    path.reverse()
    return path
  }

  private class WordStats {
    var cost = 0.0
    val covered = HashSet<Int>()
    var lastPending = -1
    var matched = 0
    var heard = 0
  }

  private fun update(): List<TrackerEvent> {
    val events = ArrayList<TrackerEvent>()
    val first = maxOf(0, cursor - config.backWords)
    val last = minOf(words.size, cursor + config.aheadWords)
    val heard = CharArray(pending.size) { pending[it].symbol }
    val win = window(first, last)
    val path = align(heard, win)

    val stats = HashMap<Int, WordStats>()
    for (step in path) {
      if (step.op == 'g') continue
      val col = maxOf(step.column, 0)
      val w = win.word[col]
      val pos = win.pos[col]
      val word = words[w]
      val s = stats.getOrPut(w) { WordStats() }
      s.cost += when (step.op) {
        'm' -> 0.0
        's' -> sub(heard[step.pendingIndex], word[pos])
        'i' -> indel(heard[step.pendingIndex], pos, word.length)
        else -> indel(word[pos], pos, word.length)
      }
      if (step.op == 'm' || step.op == 's' || step.op == 'd') s.covered.add(col)
      if (step.op == 'm' || step.op == 's' || step.op == 'i') s.lastPending = step.pendingIndex
      if (step.op == 'm') s.matched++
      if (step.op == 'm' || step.op == 's') s.heard++
    }
    val columnStart = HashMap<Int, Int>()
    win.word.forEachIndexed { col, w -> columnStart.putIfAbsent(w, col) }

    fun complete(w: Int): Boolean {
      val s = stats[w] ?: return false
      val start = columnStart[w] ?: return false
      return (start until start + words[w].length).all { it in s.covered }
    }

    fun good(w: Int): Boolean {
      val s = stats[w] ?: return false
      return s.lastPending >= 0 && s.matched > 0 && s.cost <= config.allowance(words[w].length)
    }

    fun timeOf(pendingIndex: Int): Long = pending[minOf(pendingIndex, pending.size - 1)].timeMs
    val lastTime = pending.last().timeMs

    val repeated = stats.keys.filter { it < cursor && stats.getValue(it).lastPending >= 0 }.sorted()

    var w = cursor
    var lastPending = -1
    while (w < words.size && complete(w) && good(w)) {
      lastPending = stats.getValue(w).lastPending
      events += WordRecited(w, timeOf(lastPending), stats.getValue(w).cost)
      w++
    }

    // A known phrase can start like the passage (takbir "Allahu akbar" vs. a next word "Allahu").
    // While what was heard is still the beginning of a phrase, wait; once the phrase is complete
    // and explains more than the passage words would, it wins.
    if (lastPending >= 0 && phrases.isNotEmpty()) {
      val phrase = matchPhrase(heard)
      if (phrase != null) {
        val (kind, completedAt) = phrase
        if (completedAt == null) return emptyList()
        if (completedAt > lastPending) {
          val event = NonTarget(cursor, timeOf(completedAt), kind)
          consumePending(completedAt)
          mistakeAt = null
          return listOf(event)
        }
      }
    }

    // Re-synchronization: the user is confidently reciting words after the cursor (the model
    // misheard a word, or the user skipped one). Catch up so the reader never gets stuck.
    if (lastPending < 0) {
      val anchor = resyncAnchor(stats, ::complete, ::good)
      if (anchor != null) {
        for (x in cursor until anchor) {
          val s = stats[x]
          val length = words[x].length
          if (s != null && s.heard >= config.contextMinHeard * length && s.cost <= config.contextCost * length) {
            val time = if (s.lastPending >= 0) timeOf(s.lastPending) else lastTime
            events += WordRecited(x, time, s.cost, fromContext = true)
          } else {
            events += WordMissed(x, lastTime)
          }
        }
        w = anchor
        while (w < words.size && complete(w) && good(w)) {
          lastPending = stats.getValue(w).lastPending
          events += WordRecited(w, timeOf(lastPending), stats.getValue(w).cost)
          w++
        }
      }
    }

    if (lastPending >= 0) {
      if (repeated.isNotEmpty()) {
        events.add(0, Repetition(repeated.first(), repeated.last(), timeOf(stats.getValue(repeated.last()).lastPending)))
      }
      cursor = w
      consumePending(lastPending)
      mistakeAt = null
      return events
    }

    // No new word. A completed repetition is consumed so it doesn't count as unexplained speech.
    if (repeated.isNotEmpty() && repeated.all { complete(it) && good(it) }) {
      val lp = stats.getValue(repeated.last()).lastPending
      events += Repetition(repeated.first(), repeated.last(), timeOf(lp))
      consumePending(lp)
      return events
    }

    // Lost? The user kept reciting after a mistake but nothing near the cursor matches.
    if (mistakeAt == cursor) {
      val recovered = recover()
      if (recovered != null) return events + recovered
    }

    // A known phrase (isti'adha, basmala, takbir...) in progress or completed?
    val phrase = matchPhrase(heard)
    if (phrase != null) {
      val (kind, completedAt) = phrase
      if (completedAt != null) {
        events += NonTarget(cursor, timeOf(completedAt), kind)
        consumePending(completedAt)
        mistakeAt = null
      }
      return events
    }

    // Mistake?
    if (heard.size >= words[cursor].length + config.mistakeMargin && mistakeAt != cursor) {
      val skipped = cursor + 1 < words.size && good(cursor + 1) && (stats[cursor]?.matched ?: 0) == 0
      mistakeAt = cursor
      events += Mistake(cursor, lastTime, if (skipped) Mistake.Kind.SKIP else Mistake.Kind.SUBSTITUTION)
    }
    return events
  }

  private fun consumePending(throughIndex: Int) {
    pending.subList(0, minOf(pending.size, throughIndex + 1)).clear()
  }

  private fun resyncAnchor(
    stats: Map<Int, WordStats>,
    complete: (Int) -> Boolean,
    good: (Int) -> Boolean,
  ): Int? {
    val run = config.resyncRun
    for (j in cursor + 1 until minOf(words.size, cursor + config.aheadWords)) {
      val end = minOf(words.size, j + run)
      if (end - j != run) continue
      if ((j until end).all { complete(it) && good(it) && stats.getValue(it).matched >= 0.6 * words[it].length }) {
        return j
      }
    }
    return null
  }

  /**
   * Searches words[cursor, cursor + farWords) for the most recent phonemes heard. On a confident
   * match, the words before it are missed and the matched words recited.
   */
  private fun recover(): List<TrackerEvent>? {
    val tailLength = minOf(pending.size, config.recoverTail)
    if (tailLength < config.recoverMinTail) return null
    val tail = CharArray(tailLength) { pending[pending.size - tailLength + it].symbol }
    val win = window(cursor, minOf(words.size, cursor + config.farWords))
    val n = tail.size
    val m = win.symbols.size
    if (m == 0) return null
    val width = m + 1
    val cost = DoubleArray((n + 1) * width)
    val start = IntArray((n + 1) * width)
    for (j in 0..m) start[j] = j
    for (i in 1..n) {
      cost[i * width] = i * config.indelCost
      start[i * width] = 0
      for (j in 1..m) {
        var best = cost[(i - 1) * width + j - 1] + sub(tail[i - 1], win.symbols[j - 1])
        var st = start[(i - 1) * width + j - 1]
        val up = cost[(i - 1) * width + j] + config.indelCost
        if (up < best) {
          best = up
          st = start[(i - 1) * width + j]
        }
        val left = cost[i * width + j - 1] + config.indelCost
        if (left < best) {
          best = left
          st = start[i * width + j - 1]
        }
        cost[i * width + j] = best
        start[i * width + j] = st
      }
    }
    val limit = config.recoverCost * n
    val lastTime = pending.last().timeMs
    for (j in 1..m) {
      if (cost[n * width + j] > limit) continue
      val startWord = win.word[minOf(start[n * width + j], m - 1)]
      val endWord = win.word[j - 1]
      if (endWord - startWord + 1 < 2) continue
      // only words fully covered by the match count; the last one may still be in progress
      val endComplete = j == m || win.word[j] != endWord
      val lastWord = if (endComplete) endWord else endWord - 1
      val events = ArrayList<TrackerEvent>()
      for (x in cursor until startWord) events += WordMissed(x, lastTime)
      for (x in startWord..lastWord) events += WordRecited(x, lastTime, 0.0, recovered = true)
      cursor = lastWord + 1
      pending.clear()
      mistakeAt = null
      return events
    }
    return null
  }

  /**
   * If the heard phonemes are (the beginning of) a known phrase, returns its kind and the index of
   * the last heard phoneme of the completed phrase, or null for the index while it's in progress.
   */
  private fun matchPhrase(heard: CharArray): Pair<String, Int?>? {
    var best: Pair<String, Int?>? = null
    for ((kind, phrase) in phrases) {
      val n = heard.size
      val m = phrase.length
      if (n == 0 || m == 0) continue
      val width = m + 1
      val cost = DoubleArray((n + 1) * width) { Double.POSITIVE_INFINITY }
      for (i in 0..n) cost[i * width] = phraseGarbage(i)
      for (i in 0..n) {
        for (j in 1..m) {
          var c = Double.POSITIVE_INFINITY
          if (i > 0) {
            c = minOf(
              cost[(i - 1) * width + j - 1] + sub(heard[i - 1], phrase[j - 1]),
              cost[(i - 1) * width + j] + config.indelCost
            )
          }
          cost[i * width + j] = minOf(c, cost[i * width + j - 1] + config.indelCost)
        }
      }
      // Completed: the best place for the phrase to end is before the end of what was heard (the
      // user has moved on). Ending exactly at the end means its last sounds may still be coming.
      var iEnd = 1
      for (i in 2..n) {
        if (cost[i * width + m] <= cost[iEnd * width + m]) iEnd = i
      }
      if (cost[iEnd * width + m] <= 0.5 + 0.2 * m && iEnd < n) {
        if (best == null || best.second == null) best = kind to (iEnd - 1)
        continue
      }
      var jBest = 0
      for (j in 1..m) {
        if (cost[n * width + j] < cost[n * width + jBest]) jBest = j
      }
      if (jBest > 0 && cost[n * width + jBest] <= 0.5 + 0.2 * n && best == null) {
        best = kind to null
      }
    }
    return best
  }

  private companion object {
    val CONFUSABLE: List<Set<Char>> = listOf(
      setOf('س', 'ص'), setOf('ت', 'ط'), setOf('د', 'ض'), setOf('ذ', 'ز', 'ظ'), setOf('ح', 'ه'),
      setOf('ء', 'ع'), setOf('ق', 'ك'), setOf('ث', 'س'), setOf('ا', 'ء'),
    )
  }
}
