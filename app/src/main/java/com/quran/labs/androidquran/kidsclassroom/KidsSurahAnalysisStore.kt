package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Aggregated revision statistics for a single Quranic word within a Surah.
 */
data class DifficultWordRecord(
  val surah: Int,
  val ayah: Int,
  val mushafWordPosition: Int,
  val uthmaniWord: String,
  val normalizedWord: String,
  val wordErrorCount: Int = 0,
  val missingCount: Int = 0,
  val tashkeelErrorCount: Int = 0,
  val totalAttempts: Int = 0,
  val correctCount: Int = 0,
  val lastMatchedHypWord: String? = null,
  val lastPracticedAtMs: Long = 0L
) {
  val totalMistakes: Int
    get() = wordErrorCount + missingCount + tashkeelErrorCount

  /**
   * Severity-weighted score so frequently missed/wrong words rank above occasional Harakah slips.
   */
  val difficultyScore: Double
    get() = wordErrorCount * 3.0 + missingCount * 3.0 + tashkeelErrorCount * 1.5

  val primaryErrorType: WordGradeStatus
    get() = when {
      missingCount >= wordErrorCount && missingCount >= tashkeelErrorCount && missingCount > 0 ->
        WordGradeStatus.MISSING
      wordErrorCount >= tashkeelErrorCount && wordErrorCount > 0 ->
        WordGradeStatus.WORD_ERROR
      tashkeelErrorCount > 0 ->
        WordGradeStatus.TASHKEEL_ERROR
      else -> WordGradeStatus.CORRECT
    }
}

/**
 * Aggregated recitation accuracy and mistake summary for a single Ayah inside a Surah.
 */
data class AyahAnalysisSummary(
  val surah: Int,
  val ayah: Int,
  val attemptCount: Int,
  val passCount: Int,
  val lastScorePercent: Double,
  val bestScorePercent: Double,
  val avgScorePercent: Double,
  val mistakeCount: Int
)

/**
 * Full revision analytics for one Surah, surfacing difficult/missed words and per-Ayah accuracy.
 */
data class SurahAnalysisSummary(
  val surah: Int,
  val surahNameAr: String,
  val practicedAyahCount: Int,
  val totalAttempts: Int,
  val avgAccuracyPercent: Int,
  val totalMistakes: Int,
  val wordErrorTotal: Int,
  val missingTotal: Int,
  val tashkeelErrorTotal: Int,
  val difficultWords: List<DifficultWordRecord>,
  val ayahSummaries: List<AyahAnalysisSummary>
) {
  val uniqueDifficultWordCount: Int
    get() = difficultWords.size
}

/**
 * Persistent per-Surah revision analytics store backed by `SharedPreferences` JSON.
 * Records every graded recitation turn (word errors, missing words, Harakah/Tashkeel errors,
 * "You said" hypothesis, and per-Ayah accuracy) so learners and parents can review weak spots
 * per Surah and jump straight into Slow-Mo (`0.75×`) word playback or Ayah revision.
 */
object KidsSurahAnalysisStore {

  private const val PREFS_NAME = "tilawi_kids_surah_analysis_prefs"
  private const val KEY_ANALYSIS_JSON = "surah_analysis_v1_json"

  private data class MutableWordEntry(
    val surah: Int,
    val ayah: Int,
    val mushafWordPosition: Int,
    var uthmaniWord: String,
    var normalizedWord: String,
    var wordErrorCount: Int = 0,
    var missingCount: Int = 0,
    var tashkeelErrorCount: Int = 0,
    var totalAttempts: Int = 0,
    var correctCount: Int = 0,
    var lastMatchedHypWord: String? = null,
    var lastPracticedAtMs: Long = 0L
  )

  private data class MutableAyahEntry(
    val surah: Int,
    val ayah: Int,
    var attemptCount: Int = 0,
    var passCount: Int = 0,
    var scoreSum: Double = 0.0,
    var lastScorePercent: Double = 0.0,
    var bestScorePercent: Double = 0.0,
    var mistakeCount: Int = 0
  )

  private data class MutableSurahEntry(
    val surah: Int,
    var surahNameAr: String,
    val words: MutableMap<String, MutableWordEntry> = LinkedHashMap(),
    val ayat: MutableMap<Int, MutableAyahEntry> = LinkedHashMap()
  )

  private val lock = Any()
  private var loaded = false
  private val surahs = LinkedHashMap<Int, MutableSurahEntry>()

  private val _summaries = MutableStateFlow<Map<Int, SurahAnalysisSummary>>(emptyMap())
  val summaries: StateFlow<Map<Int, SurahAnalysisSummary>> = _summaries.asStateFlow()

  fun ensureLoaded(context: Context) {
    synchronized(lock) {
      if (loaded) return
      loadFromPrefsLocked(context.applicationContext)
      loaded = true
      emitSnapshotLocked()
    }
  }

  /**
   * Records a graded recitation evaluation (ignoring `noSpeechDetected` attempts) into the
   * persistent per-Surah revision analytics store.
   */
  fun recordEvaluation(
    context: Context,
    evalResult: TurnEvaluationResult,
    effectivePassed: Boolean = evalResult.passed,
    surahNameAr: String = MinshawiJuz30Repository.getSurahNameAr(evalResult.suraAyah.sura)
  ) {
    if (evalResult.noSpeechDetected) return
    val appCtx = context.applicationContext
    val nowMs = System.currentTimeMillis()

    synchronized(lock) {
      if (!loaded) {
        loadFromPrefsLocked(appCtx)
        loaded = true
      }

      val surahNum = evalResult.suraAyah.sura
      val ayahNum = evalResult.suraAyah.ayah
      val surahEntry = surahs.getOrPut(surahNum) {
        MutableSurahEntry(
          surah = surahNum,
          surahNameAr = surahNameAr.ifBlank { MinshawiJuz30Repository.getSurahNameAr(surahNum) }
        )
      }
      if (surahNameAr.isNotBlank()) {
        surahEntry.surahNameAr = surahNameAr
      }

      val ayahEntry = surahEntry.ayat.getOrPut(ayahNum) {
        MutableAyahEntry(surah = surahNum, ayah = ayahNum)
      }
      val clampedScore = evalResult.scorePercent.coerceIn(0.0, 100.0)
      ayahEntry.attemptCount += 1
      if (effectivePassed) {
        ayahEntry.passCount += 1
      }
      ayahEntry.scoreSum += clampedScore
      ayahEntry.lastScorePercent = clampedScore
      if (clampedScore > ayahEntry.bestScorePercent) {
        ayahEntry.bestScorePercent = clampedScore
      }

      var mistakesInEval = 0
      for (grade in evalResult.wordGrades) {
        if (grade.status == WordGradeStatus.PENDING || grade.status == WordGradeStatus.ACTIVE_PLAYBACK) {
          continue
        }
        val key = wordKey(ayahNum, grade.mushafWordPosition)
        val wordEntry = surahEntry.words.getOrPut(key) {
          MutableWordEntry(
            surah = surahNum,
            ayah = ayahNum,
            mushafWordPosition = grade.mushafWordPosition,
            uthmaniWord = grade.uthmaniWord,
            normalizedWord = grade.normalizedWord
          )
        }
        if (grade.uthmaniWord.isNotBlank()) wordEntry.uthmaniWord = grade.uthmaniWord
        if (grade.normalizedWord.isNotBlank()) wordEntry.normalizedWord = grade.normalizedWord
        wordEntry.totalAttempts += 1
        wordEntry.lastPracticedAtMs = nowMs

        when (grade.status) {
          WordGradeStatus.CORRECT -> {
            wordEntry.correctCount += 1
          }
          WordGradeStatus.WORD_ERROR -> {
            wordEntry.wordErrorCount += 1
            wordEntry.lastMatchedHypWord = grade.matchedHypWord
            mistakesInEval += 1
          }
          WordGradeStatus.MISSING -> {
            wordEntry.missingCount += 1
            wordEntry.lastMatchedHypWord = null
            mistakesInEval += 1
          }
          WordGradeStatus.TASHKEEL_ERROR -> {
            wordEntry.tashkeelErrorCount += 1
            wordEntry.lastMatchedHypWord = grade.matchedHypWord
            mistakesInEval += 1
          }
          WordGradeStatus.PENDING, WordGradeStatus.ACTIVE_PLAYBACK -> Unit
        }
      }

      ayahEntry.mistakeCount += mistakesInEval

      persistLocked(appCtx)
      emitSnapshotLocked()
    }
  }

  fun getSurahSummary(context: Context, surah: Int): SurahAnalysisSummary {
    ensureLoaded(context)
    return _summaries.value[surah] ?: emptySurahSummary(surah)
  }

  fun getAllPracticedSurahs(context: Context): List<SurahAnalysisSummary> {
    ensureLoaded(context)
    return _summaries.value.values.sortedBy { it.surah }
  }

  fun clearSurah(context: Context, surah: Int) {
    val appCtx = context.applicationContext
    synchronized(lock) {
      if (!loaded) {
        loadFromPrefsLocked(appCtx)
        loaded = true
      }
      surahs.remove(surah)
      persistLocked(appCtx)
      emitSnapshotLocked()
    }
  }

  fun clearForTests(context: Context) {
    val appCtx = context.applicationContext
    synchronized(lock) {
      surahs.clear()
      loaded = true
      appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .remove(KEY_ANALYSIS_JSON)
        .commit()
      emitSnapshotLocked()
    }
  }

  internal fun reloadFromDiskForTests(context: Context) {
    val appCtx = context.applicationContext
    synchronized(lock) {
      surahs.clear()
      loadFromPrefsLocked(appCtx)
      loaded = true
      emitSnapshotLocked()
    }
  }

  fun emptySurahSummary(surah: Int): SurahAnalysisSummary {
    return SurahAnalysisSummary(
      surah = surah,
      surahNameAr = MinshawiJuz30Repository.getSurahNameAr(surah),
      practicedAyahCount = 0,
      totalAttempts = 0,
      avgAccuracyPercent = 0,
      totalMistakes = 0,
      wordErrorTotal = 0,
      missingTotal = 0,
      tashkeelErrorTotal = 0,
      difficultWords = emptyList(),
      ayahSummaries = emptyList()
    )
  }

  private fun wordKey(ayah: Int, mushafWordPosition: Int): String = "$ayah:$mushafWordPosition"

  private fun emitSnapshotLocked() {
    val map = LinkedHashMap<Int, SurahAnalysisSummary>(surahs.size)
    for ((surahNum, entry) in surahs) {
      map[surahNum] = buildSummary(entry)
    }
    _summaries.value = map
  }

  private fun buildSummary(entry: MutableSurahEntry): SurahAnalysisSummary {
    val difficultWords = entry.words.values
      .map { w ->
        DifficultWordRecord(
          surah = w.surah,
          ayah = w.ayah,
          mushafWordPosition = w.mushafWordPosition,
          uthmaniWord = w.uthmaniWord,
          normalizedWord = w.normalizedWord,
          wordErrorCount = w.wordErrorCount,
          missingCount = w.missingCount,
          tashkeelErrorCount = w.tashkeelErrorCount,
          totalAttempts = w.totalAttempts,
          correctCount = w.correctCount,
          lastMatchedHypWord = w.lastMatchedHypWord,
          lastPracticedAtMs = w.lastPracticedAtMs
        )
      }
      .filter { it.totalMistakes > 0 }
      .sortedWith(
        compareByDescending<DifficultWordRecord> { it.difficultyScore }
          .thenByDescending { it.totalMistakes }
          .thenBy { it.ayah }
          .thenBy { it.mushafWordPosition }
      )

    val ayahSummaries = entry.ayat.values
      .map { a ->
        val avg = if (a.attemptCount > 0) a.scoreSum / a.attemptCount else 0.0
        AyahAnalysisSummary(
          surah = a.surah,
          ayah = a.ayah,
          attemptCount = a.attemptCount,
          passCount = a.passCount,
          lastScorePercent = a.lastScorePercent,
          bestScorePercent = a.bestScorePercent,
          avgScorePercent = avg,
          mistakeCount = a.mistakeCount
        )
      }
      .sortedBy { it.ayah }

    val totalAttempts = entry.ayat.values.sumOf { it.attemptCount }
    val totalScoreSum = entry.ayat.values.sumOf { it.scoreSum }
    val avgAccuracy = if (totalAttempts > 0) {
      (totalScoreSum / totalAttempts).roundToInt().coerceIn(0, 100)
    } else {
      0
    }
    val wordErrorTotal = difficultWords.sumOf { it.wordErrorCount }
    val missingTotal = difficultWords.sumOf { it.missingCount }
    val tashkeelErrorTotal = difficultWords.sumOf { it.tashkeelErrorCount }

    return SurahAnalysisSummary(
      surah = entry.surah,
      surahNameAr = entry.surahNameAr.ifBlank { MinshawiJuz30Repository.getSurahNameAr(entry.surah) },
      practicedAyahCount = ayahSummaries.size,
      totalAttempts = totalAttempts,
      avgAccuracyPercent = avgAccuracy,
      totalMistakes = wordErrorTotal + missingTotal + tashkeelErrorTotal,
      wordErrorTotal = wordErrorTotal,
      missingTotal = missingTotal,
      tashkeelErrorTotal = tashkeelErrorTotal,
      difficultWords = difficultWords,
      ayahSummaries = ayahSummaries
    )
  }

  private fun persistLocked(appCtx: Context) {
    try {
      val root = JSONObject()
      val surahsArr = JSONArray()
      for (s in surahs.values) {
        val sObj = JSONObject()
        sObj.put("surah", s.surah)
        sObj.put("surahNameAr", s.surahNameAr)

        val wordsArr = JSONArray()
        for (w in s.words.values) {
          val wObj = JSONObject()
          wObj.put("ayah", w.ayah)
          wObj.put("pos", w.mushafWordPosition)
          wObj.put("uthmani", w.uthmaniWord)
          wObj.put("norm", w.normalizedWord)
          wObj.put("we", w.wordErrorCount)
          wObj.put("ms", w.missingCount)
          wObj.put("tk", w.tashkeelErrorCount)
          wObj.put("att", w.totalAttempts)
          wObj.put("ok", w.correctCount)
          if (w.lastMatchedHypWord != null) {
            wObj.put("hyp", w.lastMatchedHypWord)
          }
          wObj.put("ts", w.lastPracticedAtMs)
          wordsArr.put(wObj)
        }
        sObj.put("words", wordsArr)

        val ayatArr = JSONArray()
        for (a in s.ayat.values) {
          val aObj = JSONObject()
          aObj.put("ayah", a.ayah)
          aObj.put("att", a.attemptCount)
          aObj.put("pass", a.passCount)
          aObj.put("sum", a.scoreSum)
          aObj.put("last", a.lastScorePercent)
          aObj.put("best", a.bestScorePercent)
          aObj.put("err", a.mistakeCount)
          ayatArr.put(aObj)
        }
        sObj.put("ayat", ayatArr)
        surahsArr.put(sObj)
      }
      root.put("surahs", surahsArr)
      appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ANALYSIS_JSON, root.toString())
        .apply()
    } catch (t: Throwable) {
      Timber.e(t, "Failed to persist KidsSurahAnalysisStore")
    }
  }

  private fun loadFromPrefsLocked(appCtx: Context) {
    try {
      val raw = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_ANALYSIS_JSON, null)
      if (raw.isNullOrBlank()) return
      val root = JSONObject(raw)
      val surahsArr = root.optJSONArray("surahs") ?: return
      for (i in 0 until surahsArr.length()) {
        val sObj = surahsArr.optJSONObject(i) ?: continue
        val surahNum = sObj.optInt("surah", 0)
        if (surahNum !in 1..114) continue
        val surahName = sObj.optString("surahNameAr", MinshawiJuz30Repository.getSurahNameAr(surahNum))
        val entry = MutableSurahEntry(surah = surahNum, surahNameAr = surahName)

        val wordsArr = sObj.optJSONArray("words")
        if (wordsArr != null) {
          for (j in 0 until wordsArr.length()) {
            val wObj = wordsArr.optJSONObject(j) ?: continue
            val ayah = wObj.optInt("ayah", 0)
            val pos = wObj.optInt("pos", 0)
            if (ayah <= 0 || pos <= 0) continue
            val w = MutableWordEntry(
              surah = surahNum,
              ayah = ayah,
              mushafWordPosition = pos,
              uthmaniWord = wObj.optString("uthmani", ""),
              normalizedWord = wObj.optString("norm", ""),
              wordErrorCount = wObj.optInt("we", 0),
              missingCount = wObj.optInt("ms", 0),
              tashkeelErrorCount = wObj.optInt("tk", 0),
              totalAttempts = wObj.optInt("att", 0),
              correctCount = wObj.optInt("ok", 0),
              lastMatchedHypWord = if (wObj.has("hyp")) wObj.optString("hyp") else null,
              lastPracticedAtMs = wObj.optLong("ts", 0L)
            )
            entry.words[wordKey(ayah, pos)] = w
          }
        }

        val ayatArr = sObj.optJSONArray("ayat")
        if (ayatArr != null) {
          for (j in 0 until ayatArr.length()) {
            val aObj = ayatArr.optJSONObject(j) ?: continue
            val ayah = aObj.optInt("ayah", 0)
            if (ayah <= 0) continue
            entry.ayat[ayah] = MutableAyahEntry(
              surah = surahNum,
              ayah = ayah,
              attemptCount = aObj.optInt("att", 0),
              passCount = aObj.optInt("pass", 0),
              scoreSum = aObj.optDouble("sum", 0.0),
              lastScorePercent = aObj.optDouble("last", 0.0),
              bestScorePercent = aObj.optDouble("best", 0.0),
              mistakeCount = aObj.optInt("err", 0)
            )
          }
        }

        surahs[surahNum] = entry
      }
    } catch (t: Throwable) {
      Timber.e(t, "Failed to load KidsSurahAnalysisStore")
    }
  }
}
