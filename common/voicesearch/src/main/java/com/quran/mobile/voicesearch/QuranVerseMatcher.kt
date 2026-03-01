package com.quran.mobile.voicesearch

import com.quran.common.search.SearchTextUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

class QuranVerseMatcher(private val verses: List<IndexedVerse>) {

  // Build inverted index at construction time
  private val wordIndex: Map<String, List<Int>> = buildWordIndex()

  private fun buildWordIndex(): Map<String, List<Int>> {
    val index = mutableMapOf<String, MutableList<Int>>()
    verses.forEachIndexed { i, verse ->
      verse.words.forEach { word ->
        index.getOrPut(word) { mutableListOf() }.add(i)
      }
    }
    return index
  }

  suspend fun match(transcription: String, limit: Int = 10): List<VerseMatch> {
    return withContext(Dispatchers.Default) {
      val normalizedQuery = SearchTextUtil.normalizeArabic(transcription)
      val queryWords = SearchTextUtil.tokenizeArabic(normalizedQuery)

      if (normalizedQuery.isBlank() || queryWords.isEmpty()) {
        return@withContext emptyList()
      }

      // Phase 1: Get candidate verses via inverted index (word overlap)
      val candidateIndices = mutableSetOf<Int>()
      for (word in queryWords) {
        wordIndex[word]?.let { candidateIndices.addAll(it) }
      }

      // Phase 2: Also check exact substring on all verses (for partial word matches)
      for (i in verses.indices) {
        if (verses[i].normalizedText.contains(normalizedQuery)) {
          candidateIndices.add(i)
        }
      }

      // Phase 3: Score only candidates
      val results = mutableListOf<VerseMatch>()
      for (i in candidateIndices) {
        val verse = verses[i]
        val score = scoreVerse(normalizedQuery, queryWords, verse)
        if (score > 0f) {
          val matchType = when {
            verse.normalizedText.contains(normalizedQuery) -> MatchType.EXACT
            jaccardSimilarity(queryWords, verse.words) > 0.1f -> MatchType.WORD_OVERLAP
            else -> MatchType.FUZZY
          }
          results.add(
            VerseMatch(
              sura = verse.sura,
              ayah = verse.ayah,
              verseText = verse.rawText,
              score = score,
              matchType = matchType
            )
          )
        }
      }

      results.sortByDescending { it.score }
      results.take(limit)
    }
  }

  private fun scoreVerse(
    normalizedQuery: String,
    queryWords: List<String>,
    verse: IndexedVerse
  ): Float {
    // Tier 1: Exact substring match
    if (verse.normalizedText.contains(normalizedQuery)) {
      // Score higher when the query covers more of the verse
      val coverage = normalizedQuery.length.toFloat() / verse.normalizedText.length
      return 0.8f + (0.2f * coverage)
    }

    // Tier 2: Word overlap (Jaccard similarity)
    val jaccard = jaccardSimilarity(queryWords, verse.words)
    if (jaccard > 0.1f) {
      return 0.4f + (0.4f * jaccard)
    }

    // Tier 3: Fuzzy matching via word-level edit distance
    val editScore = wordEditDistanceScore(queryWords, verse.words)
    if (editScore > 0.3f) {
      return 0.2f * editScore
    }

    return 0f
  }

  private fun jaccardSimilarity(a: List<String>, b: List<String>): Float {
    val setA = a.toSet()
    val setB = b.toSet()
    val intersection = setA.intersect(setB).size
    val union = setA.union(setB).size
    return if (union == 0) 0f else intersection.toFloat() / union
  }

  private fun wordEditDistanceScore(queryWords: List<String>, verseWords: List<String>): Float {
    if (queryWords.isEmpty() || verseWords.isEmpty()) return 0f

    // Find best matching window of verse words for the query
    val queryLen = queryWords.size
    if (queryLen > verseWords.size) {
      return wordEditDistanceScoreInternal(verseWords, queryWords)
    }

    var bestScore = 0f
    for (i in 0..verseWords.size - queryLen) {
      val window = verseWords.subList(i, min(i + queryLen, verseWords.size))
      val score = wordEditDistanceScoreInternal(queryWords, window)
      if (score > bestScore) bestScore = score
    }
    return bestScore
  }

  private fun wordEditDistanceScoreInternal(a: List<String>, b: List<String>): Float {
    var matches = 0
    val maxLen = maxOf(a.size, b.size)
    for (i in 0 until min(a.size, b.size)) {
      if (a[i] == b[i]) {
        matches++
      } else if (levenshteinDistance(a[i], b[i]) <= 2) {
        matches++
      }
    }
    return if (maxLen == 0) 0f else matches.toFloat() / maxLen
  }

  companion object {
    internal fun levenshteinDistance(s: String, t: String): Int {
      val m = s.length
      val n = t.length
      val dp = Array(m + 1) { IntArray(n + 1) }

      for (i in 0..m) dp[i][0] = i
      for (j in 0..n) dp[0][j] = j

      for (i in 1..m) {
        for (j in 1..n) {
          val cost = if (s[i - 1] == t[j - 1]) 0 else 1
          dp[i][j] = minOf(
            dp[i - 1][j] + 1,
            dp[i][j - 1] + 1,
            dp[i - 1][j - 1] + cost
          )
        }
      }
      return dp[m][n]
    }
  }
}
