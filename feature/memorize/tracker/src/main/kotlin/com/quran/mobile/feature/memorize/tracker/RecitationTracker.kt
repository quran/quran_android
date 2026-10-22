package com.quran.mobile.feature.memorize.tracker

import com.quran.mobile.feature.memorize.tracker.TrackerEvent.Mistake
import com.quran.mobile.feature.memorize.tracker.TrackerEvent.NonTarget
import com.quran.mobile.feature.memorize.tracker.TrackerEvent.Repetition
import com.quran.mobile.feature.memorize.tracker.TrackerEvent.WordMissed
import com.quran.mobile.feature.memorize.tracker.TrackerEvent.WordRecited
import kotlin.math.abs

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
 * With a [startWindow] the user may start anywhere in the first [startWindow] words (e.g. the pages
 * around the one on screen): the tracker first finds where they started, and reports a
 * [Mistake.Kind.ELSEWHERE] on the word they were expected to start at (startIndex: e.g. the first
 * word on screen) if what they recite isn't there.
 *
 * This is a port of the reference implementation used to benchmark the speech models; keep the
 * two in sync (see the golden tests).
 */
class RecitationTracker(
  words: List<String>,
  private val config: TrackerConfig = TrackerConfig(),
  startIndex: Int = 0,
  /** Phrases said around the passage that aren't mistakes: (kind, normalized phonemes). */
  phrases: List<Pair<String, String>> = emptyList(),
  /** How many of the first words the user may start at; 0 or 1: the first word. */
  private val startWindow: Int = 0,
) {

  private val words = ArrayList(words)
  private val phrases = ArrayList(phrases)

  var cursor: Int = startIndex
    private set

  /** Whether it is known where the user started (always, without a [startWindow]). */
  var anchored: Boolean = startWindow <= 1
    private set

  // where the user was expected to start, and how many words they recited from the start found:
  // until a few have been, a mistake means the start was wrong and is looked for again
  private val home = startIndex
  private var sinceStart = if (startWindow <= 1) CONFIRMED_START_WORDS else 0

  /** Whether every word loaded so far has been recited (see [extend]). */
  val isDone: Boolean get() = cursor >= words.size

  /** More of the passage, e.g. the next sura once the user nears the end of this one. */
  fun extend(more: List<String>) {
    words.addAll(more)
  }

  /** Phrases that become possible later, e.g. the basmala once the passage leaves Al-Fatiha. */
  fun addPhrases(more: List<Pair<String, String>>) {
    phrases.addAll(more.filter { it !in phrases })
  }

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
    // a hint before the user found the start: they start where they were expected to
    if (!anchored) cursor = home
    anchored = true
    sinceStart = CONFIRMED_START_WORDS
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

  /**
   * Semi-global alignment of [heard] with the window; returns the path (garbage step first). Of
   * equally good ends, the furthest one, or with [nearest] the one closest to that word.
   */
  private fun align(heard: CharArray, win: Window, nearest: Int? = null): List<Step> {
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
    if (nearest != null) {
      // as good (within half a phoneme), and starting closer to where the user is expected: e.g. a
      // phrase that the Quran repeats on several pages
      fun startWord(end: Int): Int {
        var i = n
        var j = end
        while ((i > 0 || j > 0) && back[i * width + j] != '\u0000' && back[i * width + j] != 'g') {
          when (back[i * width + j]) {
            'm', 's' -> { i--; j-- }
            'i' -> i--
            else -> j--
          }
        }
        return win.word[minOf(j, m - 1)]
      }
      val best = cost[n * width + jBest]
      var bestDistance = abs(startWord(jBest) - nearest)
      for (j in 1..m) {
        if (cost[n * width + j] > best + 0.5) continue
        val distance = abs(startWord(j) - nearest)
        if (distance < bestDistance) {
          jBest = j
          bestDistance = distance
        }
      }
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
    var firstPending = -1
    var lastPending = -1
    var matched = 0
    var heard = 0
  }

  private fun update(): List<TrackerEvent> {
    if (!anchored) return findStart()
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
      if (step.op == 'm' || step.op == 's' || step.op == 'i') {
        if (s.firstPending < 0) s.firstPending = step.pendingIndex
        s.lastPending = step.pendingIndex
      }
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

    fun recited(w: Int): WordRecited {
      val s = stats.getValue(w)
      return WordRecited(w, timeOf(s.lastPending), s.cost, startMs = timeOf(s.firstPending))
    }

    var w = cursor
    var lastPending = -1
    while (w < words.size && complete(w) && good(w)) {
      lastPending = stats.getValue(w).lastPending
      events += recited(w)
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
            val start = if (s.firstPending >= 0) timeOf(s.firstPending) else null
            events += WordRecited(x, time, s.cost, fromContext = true, startMs = start)
          } else {
            events += WordMissed(x, lastTime)
          }
        }
        w = anchor
        while (w < words.size && complete(w) && good(w)) {
          lastPending = stats.getValue(w).lastPending
          events += recited(w)
          w++
        }
      }
    }

    if (lastPending >= 0) {
      sinceStart += w - cursor
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

    // Mistake? Right after the start was found, rather a wrong start: look for it again.
    if (heard.size >= words[cursor].length + config.mistakeMargin && sinceStart < CONFIRMED_START_WORDS) {
      anchored = false
      cursor = home
      mistakeAt = null
      return findStart()
    }
    if (heard.size >= words[cursor].length + config.mistakeMargin && mistakeAt != cursor) {
      val skipped = cursor + 1 < words.size && good(cursor + 1) && (stats[cursor]?.matched ?: 0) == 0
      mistakeAt = cursor
      events += Mistake(cursor, lastTime, if (skipped) Mistake.Kind.SKIP else Mistake.Kind.SUBSTITUTION)
    }
    return events
  }

  /**
   * Before it is known where the user started: the start is where what they recite matches a run of
   * words in the start window. A known phrase (isti'adha, basmala...) is waited out; anything else
   * long enough that matches nowhere in the window is recited elsewhere.
   */
  private fun findStart(): List<TrackerEvent> {
    val heard = CharArray(pending.size) { pending[it].symbol }
    val windowEnd = minOf(startWindow, words.size)
    // align as if the user could be repeating any word of the window
    val saved = cursor
    cursor = windowEnd - 1
    val win = window(0, minOf(words.size, windowEnd + config.aheadWords))
    val path = align(heard, win, nearest = home)
    cursor = saved
    val firstWords = LinkedHashMap<Int, WordStats>()
    for (step in path) {
      if (step.op == 'g') continue
      val col = maxOf(step.column, 0)
      val w = win.word[col]
      val pos = win.pos[col]
      val s = firstWords.getOrPut(w) { WordStats() }
      s.cost += when (step.op) {
        'm' -> 0.0
        's' -> sub(heard[step.pendingIndex], words[w][pos])
        'i' -> indel(heard[step.pendingIndex], pos, words[w].length)
        else -> indel(words[w][pos], pos, words[w].length)
      }
      if (step.op == 'm' || step.op == 's' || step.op == 'd') s.covered.add(col)
      if (step.op == 'm' || step.op == 'i' || step.op == 's') s.lastPending = step.pendingIndex
      if (step.op == 'm') s.matched++
    }
    val columnStart = HashMap<Int, Int>()
    win.word.forEachIndexed { col, w -> columnStart.putIfAbsent(w, col) }
    fun confident(w: Int): Boolean {
      val s = firstWords[w] ?: return false
      val start = columnStart[w] ?: return false
      val complete = (start until start + words[w].length).all { it in s.covered }
      return complete && s.lastPending >= 0 && s.cost <= config.allowance(words[w].length) &&
        s.matched >= 0.6 * words[w].length
    }
    // enough to tell the place: a few words in a row, and more of them if they are short ones (the
    // window is large)
    val start = firstWords.keys.filter { it < windowEnd }.firstOrNull { j ->
      var count = 0
      var phonemes = 0
      while (j + count < words.size && confident(j + count) && (count < START_WORDS || phonemes < START_PHONEMES)) {
        phonemes += words[j + count].length
        count++
      }
      count >= START_WORDS && phonemes >= START_PHONEMES
    }
    if (start != null) {
      anchored = true
      sinceStart = 0
      cursor = start
      mistakeAt = null
      return update()
    }

    val phrase = matchPhrase(heard)
    val completedAt = phrase?.second
    if (phrase != null && completedAt != null) {
      val event = NonTarget(cursor, timeOf(completedAt), phrase.first)
      consumePending(completedAt)
      // then whatever followed it
      return listOf(event) + if (pending.isEmpty()) emptyList() else findStart()
    }
    // the beginning of a phrase (what is heard is cleared after each one, so it starts with it)
    if (phrases.any { (_, phrase) -> prefixCost(heard, phrase) <= 0.5 + 0.2 * heard.size }) return emptyList()
    // long enough to be several words, and nowhere in the window
    if (heard.size >= ELSEWHERE_PHONEMES && mistakeAt != cursor) {
      mistakeAt = cursor
      val event = Mistake(cursor, pending.last().timeMs, Mistake.Kind.ELSEWHERE)
      // what was recited elsewhere can't help finding the start
      pending.clear()
      return listOf(event)
    }
    // only recent speech can be the start
    if (pending.size > 2 * ELSEWHERE_PHONEMES) pending.subList(0, pending.size - 2 * ELSEWHERE_PHONEMES).clear()
    return emptyList()
  }

  private fun timeOf(pendingIndex: Int): Long = pending[minOf(pendingIndex, pending.size - 1)].timeMs

  /** Cost of [heard] being the beginning of [phrase] (from its first sound). */
  private fun prefixCost(heard: CharArray, phrase: String): Double {
    val n = heard.size
    val m = phrase.length
    val width = m + 1
    val cost = DoubleArray((n + 1) * width)
    for (j in 1..m) cost[j] = j * config.indelCost
    for (i in 1..n) {
      cost[i * width] = i * config.indelCost
      for (j in 1..m) {
        cost[i * width + j] = minOf(
          cost[(i - 1) * width + j - 1] + sub(heard[i - 1], phrase[j - 1]),
          cost[(i - 1) * width + j] + config.indelCost,
          cost[i * width + j - 1] + config.indelCost,
        )
      }
    }
    // the rest of the phrase may still be coming
    return (1..m).minOf { cost[n * width + it] }
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
    /** Phonemes heard (about six words) before a start that matches nowhere is a mistake. */
    const val ELSEWHERE_PHONEMES = 30
    /** Words, and phonemes in them, that must be heard confidently to know where the user started. */
    const val START_WORDS = 3
    const val START_PHONEMES = 10
    /** Words recited from the start found after which it is no longer doubted. */
    const val CONFIRMED_START_WORDS = 6

    val CONFUSABLE: List<Set<Char>> = listOf(
      setOf('س', 'ص'), setOf('ت', 'ط'), setOf('د', 'ض'), setOf('ذ', 'ز', 'ظ'), setOf('ح', 'ه'),
      setOf('ء', 'ع'), setOf('ق', 'ك'), setOf('ث', 'س'), setOf('ا', 'ء'),
    )
  }
}
