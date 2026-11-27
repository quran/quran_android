package com.quran.labs.androidquran.flashcards

import android.content.Context
import android.content.Intent
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.memorization.QuranMemorizationManager
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

enum class FlashcardType {
  QURAN,
  TEXT
}

enum class FlashcardRating {
  REPEAT,
  HARD,
  GOOD
}

data class MemoryFlashcard(
  val id: String = UUID.randomUUID().toString(),
  val fromSura: Int,
  val fromAyah: Int,
  val toSura: Int,
  val toAyah: Int,
  val startPage: Int,
  val endPage: Int,
  val firstAyahCue: String,
  val lastAyahCue: String,
  val stepIndex: Int = 0,
  val reviewCount: Int = 0,
  val nextReviewTimeMillis: Long = System.currentTimeMillis(),
  val createdAtMillis: Long = System.currentTimeMillis(),
  val lastReviewedAtMillis: Long = 0L,
  val lastRating: String? = null,
  val lastNotifiedAtReviewTimeMillis: Long = 0L,
  val cardType: FlashcardType = FlashcardType.QURAN,
  val customTitle: String = "",
  val fullText: String = "",
  val hintWordCount: Int = 4,
  val fastRevealMode: Boolean = false
) {
  fun isTextCard(): Boolean = cardType == FlashcardType.TEXT

  fun parseWords(): List<String> = MemoryFlashcardManager.parseTextWords(fullText)

  fun isDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
    return nowMillis >= nextReviewTimeMillis
  }

  fun currentScheduleLabel(isArabic: Boolean = MemoryFlashcardManager.isAppInArabicMode()): String {
    return MemoryFlashcardManager.getScheduleLabelForStep(stepIndex, isArabic)
  }

  fun hardScheduleLabel(isArabic: Boolean = MemoryFlashcardManager.isAppInArabicMode()): String {
    val targetStep = (stepIndex - 1).coerceIn(0, MemoryFlashcardManager.SCHEDULE_LABELS.lastIndex)
    return MemoryFlashcardManager.getScheduleLabelForStep(targetStep, isArabic)
  }

  fun nextGoodScheduleLabel(isArabic: Boolean = MemoryFlashcardManager.isAppInArabicMode()): String {
    val targetStep = stepIndex.coerceIn(0, MemoryFlashcardManager.SCHEDULE_LABELS.lastIndex)
    return MemoryFlashcardManager.getScheduleLabelForStep(targetStep, isArabic)
  }
}

/**
 * Manages Quran & Custom Text (Hadith / Matn) Memory Flashcards, their front cues,
 * the 12-step spaced repetition schedule:
 * 20min, 1h, 9h, 24h, 72h, 7d, 14d, 30d, 60d, 120d, 240d, 360d,
 * sorting by due date, Arabic/English localization (including numbers),
 * and active review sessions inside PagerActivity and MemoryFlashcardsActivity.
 */
object MemoryFlashcardManager {

  const val EXTRA_FLASHCARD_ID = "extra_memory_flashcard_id"
  const val EXTRA_INITIAL_PAGE = "extra_initial_page"
  const val EXTRA_STUDY_TEXT_CARD_ID = "extra_study_text_card_id"
  const val EXTRA_STUDY_QUEUE_IDS = "extra_study_queue_ids"

  private const val PREFS_NAME = "quran_memory_flashcards_prefs"
  private const val KEY_CARDS_JSON = "flashcards_json"
  private const val KEY_ACTIVE_CARD_ID = "active_card_id"
  private const val KEY_ACTIVE_QUEUE_IDS = "active_queue_ids"
  private const val KEY_VISITED_END_PAGE = "visited_end_page"

  val SCHEDULE_LABELS: List<String> = listOf(
    "20min",
    "1h",
    "9h",
    "24h",
    "72h",
    "7d",
    "14d",
    "30d",
    "60d",
    "120d",
    "240d",
    "360d"
  )

  val SCHEDULE_LABELS_AR: List<String> = listOf(
    "٢٠ د",
    "١ س",
    "٩ س",
    "٢٤ س",
    "٧٢ س",
    "٧ ي",
    "١٤ ي",
    "٣٠ ي",
    "٦٠ ي",
    "١٢٠ ي",
    "٢٤٠ ي",
    "٣٦٠ ي"
  )

  val SCHEDULE_INTERVALS_MS: List<Long> = listOf(
    20L * 60_000L,           // 20min
    1L * 3_600_000L,         // 1h
    9L * 3_600_000L,         // 9h
    24L * 3_600_000L,        // 24h
    72L * 3_600_000L,        // 72h
    7L * 86_400_000L,        // 7d
    14L * 86_400_000L,       // 14d
    30L * 86_400_000L,       // 30d
    60L * 86_400_000L,       // 60d
    120L * 86_400_000L,      // 120d
    240L * 86_400_000L,      // 240d
    360L * 86_400_000L       // 360d
  )

  private val defaultQuranInfo: QuranInfo by lazy {
    QuranInfo(MadaniDataSource())
  }

  private val ayahCueCache = mutableMapOf<String, String>()
  private var ayahCuesLoaded = false

  // In-memory session state mirrored with SharedPreferences for robustness
  private var activeCardId: String? = null
  private var activeQueueIds: MutableList<String> = mutableListOf()
  private var hasVisitedEndPage: Boolean = false
  private var ratingDialogPendingForCardId: String? = null

  fun getQuranInfo(): QuranInfo = defaultQuranInfo

  fun isAppInArabicMode(context: Context? = null): Boolean {
    val currentLocale = try {
      QuranUtils.getCurrentLocale()
    } catch (_: Exception) {
      null
    }
    if (currentLocale != null && currentLocale.language.startsWith("ar")) {
      return true
    }
    if (context != null) {
      val configLocale = context.resources?.configuration?.locales?.get(0)
      if (configLocale != null && configLocale.language.startsWith("ar")) {
        return true
      }
      val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
      if (prefs.getBoolean("useArabicNames", false)) {
        return true
      }
    }
    return false
  }

  /**
   * Formats [number] using Eastern Arabic numerals (٠١٢٣٤٥٦٧٨٩) when [isArabic] is true,
   * and standard Western English numerals (0123456789) when [isArabic] is false.
   */
  fun formatNumber(number: Int, isArabic: Boolean = isAppInArabicMode()): String {
    val raw = number.toString()
    if (!isArabic) return raw
    val sb = StringBuilder(raw.length)
    for (ch in raw) {
      if (ch in '0'..'9') {
        sb.append(('٠'.code + (ch.code - '0'.code)).toChar())
      } else {
        sb.append(ch)
      }
    }
    return sb.toString()
  }

  fun getScheduleLabelForStep(stepIndex: Int, isArabic: Boolean = false): String {
    val idx = stepIndex.coerceIn(0, SCHEDULE_LABELS.lastIndex)
    return if (isArabic) SCHEDULE_LABELS_AR[idx] else SCHEDULE_LABELS[idx]
  }

  fun getRepeatScheduleLabel(isArabic: Boolean = isAppInArabicMode()): String {
    return if (isArabic) SCHEDULE_LABELS_AR.first() else SCHEDULE_LABELS.first()
  }

  fun getScheduleIntervalMsForStep(stepIndex: Int): Long {
    return SCHEDULE_INTERVALS_MS[stepIndex.coerceIn(0, SCHEDULE_INTERVALS_MS.lastIndex)]
  }

  @Synchronized
  fun ensureAyahCuesLoaded(context: Context?) {
    if (ayahCuesLoaded || context == null) return
    try {
      context.assets.open("ayah_first_words.txt").use { input ->
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
          lines.forEach { line ->
            val sep = line.indexOf('|')
            if (sep > 0) {
              val key = line.substring(0, sep).trim()
              val cue = line.substring(sep + 1).trim()
              if (key.isNotEmpty() && cue.isNotEmpty()) {
                ayahCueCache[key] = cue
              }
            }
          }
        }
      }
      if (ayahCueCache.isNotEmpty()) {
        ayahCuesLoaded = true
      }
    } catch (_: Exception) {
      // Fallback cues will be used if asset is unavailable in unit tests
    }
  }

  @Synchronized
  fun getAyahFirstWordsCue(context: Context?, sura: Int, ayah: Int): String {
    ensureAyahCuesLoaded(context)
    val key = "$sura:$ayah"
    ayahCueCache[key]?.let { return it }
    return fallbackAyahCue(sura, ayah)
  }

  private fun fallbackAyahCue(sura: Int, ayah: Int): String {
    if (sura == 1) {
      return when (ayah) {
        1 -> "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ"
        2 -> "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ"
        3 -> "ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ"
        4 -> "مَـٰلِكِ يَوْمِ ٱلدِّينِ"
        5 -> "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ"
        6 -> "ٱهْدِنَا ٱلصِّرَٰطَ ٱلْمُسْتَقِيمَ"
        7 -> "صِرَٰطَ ٱلَّذِينَ أَنْعَمْتَ عَلَيْهِمْ …"
        else -> "آية $sura:$ayah …"
      }
    }
    return "آية $sura:$ayah …"
  }

  @Synchronized
  fun createFlashcard(
    context: Context,
    fromSura: Int,
    fromAyah: Int,
    toSura: Int,
    toAyah: Int,
    quranInfo: QuranInfo = defaultQuranInfo,
    nowMillis: Long = System.currentTimeMillis()
  ): MemoryFlashcard {
    val validFromSura = fromSura.coerceIn(1, 114)
    val maxFromAyah = quranInfo.getNumberOfAyahs(validFromSura).coerceAtLeast(1)
    val validFromAyah = fromAyah.coerceIn(1, maxFromAyah)

    var validToSura = toSura.coerceIn(1, 114)
    var maxToAyah = quranInfo.getNumberOfAyahs(validToSura).coerceAtLeast(1)
    var validToAyah = toAyah.coerceIn(1, maxToAyah)

    if (validToSura < validFromSura || (validToSura == validFromSura && validToAyah < validFromAyah)) {
      validToSura = validFromSura
      maxToAyah = maxFromAyah
      validToAyah = validFromAyah
    }

    val startPage = quranInfo.getPageFromSuraAyah(validFromSura, validFromAyah).coerceIn(1, 604)
    val endPage = quranInfo.getPageFromSuraAyah(validToSura, validToAyah).coerceIn(startPage, 604)

    val firstCue = getAyahFirstWordsCue(context, validFromSura, validFromAyah)
    val lastCue = getAyahFirstWordsCue(context, validToSura, validToAyah)

    val card = MemoryFlashcard(
      fromSura = validFromSura,
      fromAyah = validFromAyah,
      toSura = validToSura,
      toAyah = validToAyah,
      startPage = startPage,
      endPage = endPage,
      firstAyahCue = firstCue,
      lastAyahCue = lastCue,
      stepIndex = 0,
      reviewCount = 0,
      nextReviewTimeMillis = nowMillis,
      createdAtMillis = nowMillis
    )

    val existing = getAllCards(context).toMutableList()
    existing.add(card)
    val sorted = sortCardsByDueDate(existing)
    saveAllCards(context, sorted)
    FlashcardDueNotificationScheduler.scheduleNextDueNotification(context)
    return card
  }

  fun parseTextWords(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    return trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
  }

  fun extractTextFrontCue(text: String, hintWordCount: Int = 4): String {
    val words = parseTextWords(text)
    if (words.isEmpty()) return ""
    val count = hintWordCount.coerceAtLeast(1)
    return if (words.size <= count) {
      words.joinToString(" ")
    } else {
      words.take(count).joinToString(" ") + "..."
    }
  }

  fun extractTextEndCue(text: String, endWordCount: Int = 3): String {
    val words = parseTextWords(text)
    if (words.isEmpty()) return ""
    val count = endWordCount.coerceAtLeast(1)
    return if (words.size <= count) {
      words.joinToString(" ")
    } else {
      "..." + words.takeLast(count).joinToString(" ")
    }
  }

  @Synchronized
  fun createTextFlashcard(
    context: Context,
    fullText: String,
    customTitle: String = "",
    autoFourWordHint: Boolean = true,
    fastRevealMode: Boolean = false,
    nowMillis: Long = System.currentTimeMillis()
  ): MemoryFlashcard? {
    val cleanedText = fullText.trim()
    val words = parseTextWords(cleanedText)
    if (words.isEmpty()) return null

    val hintWordCount = if (autoFourWordHint) 4 else 2
    val firstCue = extractTextFrontCue(cleanedText, hintWordCount)
    val lastCue = extractTextEndCue(cleanedText, 3)

    val card = MemoryFlashcard(
      fromSura = 0,
      fromAyah = 0,
      toSura = 0,
      toAyah = 0,
      startPage = 0,
      endPage = 0,
      firstAyahCue = firstCue,
      lastAyahCue = lastCue,
      stepIndex = 0,
      reviewCount = 0,
      nextReviewTimeMillis = nowMillis,
      createdAtMillis = nowMillis,
      cardType = FlashcardType.TEXT,
      customTitle = customTitle.trim(),
      fullText = cleanedText,
      hintWordCount = hintWordCount,
      fastRevealMode = fastRevealMode
    )

    val existing = getAllCards(context).toMutableList()
    existing.add(card)
    val sorted = sortCardsByDueDate(existing)
    saveAllCards(context, sorted)
    FlashcardDueNotificationScheduler.scheduleNextDueNotification(context)
    return card
  }

  /**
   * Returns all flashcards sorted by due date ([MemoryFlashcard.nextReviewTimeMillis] ascending),
   * so that the most urgent / earliest due cards always appear first even when cards become plenty.
   */
  @Synchronized
  fun getAllCards(context: Context): List<MemoryFlashcard> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_CARDS_JSON, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      val list = mutableListOf<MemoryFlashcard>()
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        val rawType = obj.optString("cardType", FlashcardType.QURAN.name)
        val parsedType = if (rawType == FlashcardType.TEXT.name) {
          FlashcardType.TEXT
        } else {
          FlashcardType.QURAN
        }
        list.add(
          MemoryFlashcard(
            id = obj.getString("id"),
            fromSura = obj.optInt("fromSura", 0),
            fromAyah = obj.optInt("fromAyah", 0),
            toSura = obj.optInt("toSura", 0),
            toAyah = obj.optInt("toAyah", 0),
            startPage = obj.optInt("startPage", 0),
            endPage = obj.optInt("endPage", 0),
            firstAyahCue = obj.optString("firstAyahCue", ""),
            lastAyahCue = obj.optString("lastAyahCue", ""),
            stepIndex = obj.optInt("stepIndex", 0).coerceIn(0, SCHEDULE_LABELS.lastIndex),
            reviewCount = obj.optInt("reviewCount", 0),
            nextReviewTimeMillis = obj.optLong("nextReviewTimeMillis", 0L),
            createdAtMillis = obj.optLong("createdAtMillis", 0L),
            lastReviewedAtMillis = obj.optLong("lastReviewedAtMillis", 0L),
            lastRating = obj.optString("lastRating", "").ifEmpty { null },
            lastNotifiedAtReviewTimeMillis = obj.optLong("lastNotifiedAtReviewTimeMillis", 0L),
            cardType = parsedType,
            customTitle = obj.optString("customTitle", ""),
            fullText = obj.optString("fullText", ""),
            hintWordCount = obj.optInt("hintWordCount", 4),
            fastRevealMode = obj.optBoolean("fastRevealMode", false)
          )
        )
      }
      sortCardsByDueDate(list)
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun sortCardsByDueDate(cards: List<MemoryFlashcard>): List<MemoryFlashcard> {
    return cards.sortedWith(
      compareBy<MemoryFlashcard> { it.nextReviewTimeMillis }
        .thenBy { it.createdAtMillis }
    )
  }

  @Synchronized
  fun getDueCards(context: Context, nowMillis: Long = System.currentTimeMillis()): List<MemoryFlashcard> {
    return getAllCards(context)
      .filter { it.isDue(nowMillis) }
  }

  @Synchronized
  fun markDueCardsNotified(context: Context, dueCards: List<MemoryFlashcard>) {
    if (dueCards.isEmpty()) return
    val dueIds = dueCards.map { it.id }.toSet()
    val updated = getAllCards(context).map { card ->
      if (card.id in dueIds) {
        card.copy(lastNotifiedAtReviewTimeMillis = card.nextReviewTimeMillis)
      } else {
        card
      }
    }
    saveAllCards(context, updated)
  }

  @Synchronized
  fun getCardById(context: Context, cardId: String): MemoryFlashcard? {
    return getAllCards(context).firstOrNull { it.id == cardId }
  }

  @Synchronized
  fun deleteCard(context: Context, cardId: String) {
    val updated = getAllCards(context).filterNot { it.id == cardId }
    saveAllCards(context, updated)
    if (activeCardId == cardId) {
      clearActiveSession(context)
    } else {
      activeQueueIds.remove(cardId)
      persistSessionState(context)
    }
    FlashcardDueNotificationScheduler.scheduleNextDueNotification(context)
  }

  @Synchronized
  fun clearAllCards(context: Context) {
    saveAllCards(context, emptyList())
    clearActiveSession(context)
    FlashcardDueNotificationScheduler.scheduleNextDueNotification(context)
  }

  /**
   * Applies the user's rating ("Good" or "Repeat") according to the 12-step schedule:
   * 20min, 1h, 9h, 24h, 72h, 7d, 14d, 30d, 60d, 120d, 240d, 360d.
   *
   * - If [isGood] is true:
   *   Schedules the card using the current step's interval (`SCHEDULE_INTERVALS_MS[stepIndex]`),
   *   and advances `stepIndex` to `(stepIndex + 1).coerceAtMost(11)` for the next review.
   * - If [isGood] is false (Repeat):
   *   Resets `stepIndex` to `0` (`20min`) and schedules `nextReviewTimeMillis = now + 20min`.
   */
  @Synchronized
  fun rateCard(
    context: Context,
    cardId: String,
    isGood: Boolean,
    nowMillis: Long = System.currentTimeMillis()
  ): MemoryFlashcard? {
    return rateCardWithDifficulty(
      context = context,
      cardId = cardId,
      rating = if (isGood) FlashcardRating.GOOD else FlashcardRating.REPEAT,
      nowMillis = nowMillis
    )
  }

  @Synchronized
  fun rateCardWithDifficulty(
    context: Context,
    cardId: String,
    rating: FlashcardRating,
    nowMillis: Long = System.currentTimeMillis()
  ): MemoryFlashcard? {
    val cards = getAllCards(context).toMutableList()
    val index = cards.indexOfFirst { it.id == cardId }
    if (index == -1) return null

    val current = cards[index]
    val appliedStep = when (rating) {
      FlashcardRating.GOOD -> current.stepIndex.coerceIn(0, SCHEDULE_INTERVALS_MS.lastIndex)
      FlashcardRating.HARD -> (current.stepIndex - 1).coerceIn(0, SCHEDULE_INTERVALS_MS.lastIndex)
      FlashcardRating.REPEAT -> 0
    }
    val intervalMs = SCHEDULE_INTERVALS_MS[appliedStep]
    val nextStepIndex = when (rating) {
      FlashcardRating.GOOD -> (appliedStep + 1).coerceAtMost(SCHEDULE_INTERVALS_MS.lastIndex)
      FlashcardRating.HARD -> current.stepIndex.coerceIn(0, SCHEDULE_INTERVALS_MS.lastIndex)
      FlashcardRating.REPEAT -> 0
    }

    val updated = current.copy(
      stepIndex = nextStepIndex,
      reviewCount = current.reviewCount + 1,
      nextReviewTimeMillis = nowMillis + intervalMs,
      lastReviewedAtMillis = nowMillis,
      lastRating = rating.name,
      lastNotifiedAtReviewTimeMillis = 0L
    )
    cards[index] = updated
    saveAllCards(context, sortCardsByDueDate(cards))
    FlashcardDueNotificationScheduler.scheduleNextDueNotification(context)
    return updated
  }

  /**
   * Starts a flashcard review session for [card] (and optional [queueIds] of subsequent cards),
   * switches the Mushaf to Tajweed ("tajweed"), enables Memorization Mode on [card.startPage],
   * and returns the Intent to open [PagerActivity] on [card.startPage].
   */
  @Synchronized
  fun createReviewIntent(
    context: Context,
    card: MemoryFlashcard,
    queueIds: List<String> = emptyList(),
    quranInfo: QuranInfo = defaultQuranInfo
  ): Intent {
    activeCardId = card.id
    activeQueueIds = queueIds.filter { it != card.id }.toMutableList()
    hasVisitedEndPage = card.startPage == card.endPage
    ratingDialogPendingForCardId = null
    persistSessionState(context)

    prepareReaderForCard(context, card, quranInfo)

    return Intent(context, PagerActivity::class.java).apply {
      putExtra("page", card.startPage)
      putExtra(EXTRA_FLASHCARD_ID, card.id)
    }
  }

  @Synchronized
  fun prepareReaderForCard(
    context: Context,
    card: MemoryFlashcard,
    quranInfo: QuranInfo = defaultQuranInfo
  ) {
    // Switch to Tajweed Mushaf
    QuranSettings.getInstance(context).pageType = "tajweed"

    // Reset memorization state across all pages of the card and enable hidden mode on startPage
    for (p in card.startPage..card.endPage) {
      QuranMemorizationManager.resetPage(p, quranInfo, context)
    }
    QuranMemorizationManager.setHiddenModeEnabled(
      enabled = true,
      page = card.startPage,
      quranInfo = quranInfo,
      context = context
    )
    QuranMemorizationManager.setCurrentPage(card.startPage, quranInfo, context)
  }

  @Synchronized
  fun getActiveCard(context: Context): MemoryFlashcard? {
    restoreSessionStateIfNeeded(context)
    val id = activeCardId ?: return null
    return getCardById(context, id)
  }

  @Synchronized
  fun setActiveCardFromIntent(context: Context, cardId: String?, currentPage: Int) {
    if (cardId.isNullOrEmpty()) return
    restoreSessionStateIfNeeded(context)
    val card = getCardById(context, cardId) ?: return
    if (activeCardId != cardId) {
      activeCardId = cardId
      hasVisitedEndPage = currentPage == card.endPage
      ratingDialogPendingForCardId = null
      persistSessionState(context)
    } else if (currentPage == card.endPage) {
      hasVisitedEndPage = true
      persistSessionState(context)
    }
  }

  /**
   * Called whenever a page is selected/flipped in [PagerActivity].
   * Returns the active [MemoryFlashcard] if the user just flipped past the page of the last ayah
   * ([MemoryFlashcard.endPage]) and should now be prompted to rate Good or Repeat.
   */
  @Synchronized
  fun onReaderPageSelected(context: Context, page: Int): MemoryFlashcard? {
    restoreSessionStateIfNeeded(context)
    val card = getActiveCard(context) ?: return null

    if (page == card.endPage) {
      hasVisitedEndPage = true
      persistSessionState(context)
      return null
    }

    // If the user already visited the page of the last ayah (endPage) and now flipped the page
    if (hasVisitedEndPage && page != card.endPage) {
      if (ratingDialogPendingForCardId != card.id) {
        ratingDialogPendingForCardId = card.id
        return card
      }
    }
    return null
  }

  @Synchronized
  fun markRatingDialogDismissed() {
    ratingDialogPendingForCardId = null
  }

  /**
   * Advances the active review session to the next card in the queue, or clears the session
   * and returns null if there are no more cards to review.
   */
  @Synchronized
  fun advanceToNextCardInSession(
    context: Context,
    quranInfo: QuranInfo = defaultQuranInfo
  ): MemoryFlashcard? {
    restoreSessionStateIfNeeded(context)
    while (activeQueueIds.isNotEmpty()) {
      val nextId = activeQueueIds.removeAt(0)
      val nextCard = getCardById(context, nextId)
      if (nextCard != null) {
        if (nextCard.isTextCard()) {
          // Hand off remaining queue to MemoryFlashcardsActivity text study mode
          activeCardId = null
          hasVisitedEndPage = false
          ratingDialogPendingForCardId = null
          persistSessionState(context)
          return nextCard
        }
        activeCardId = nextCard.id
        hasVisitedEndPage = nextCard.startPage == nextCard.endPage
        ratingDialogPendingForCardId = null
        persistSessionState(context)
        prepareReaderForCard(context, nextCard, quranInfo)
        return nextCard
      }
    }
    clearActiveSession(context)
    return null
  }

  @Synchronized
  fun consumeRemainingQueueIds(context: Context): List<String> {
    restoreSessionStateIfNeeded(context)
    val remaining = activeQueueIds.toList()
    clearActiveSession(context)
    return remaining
  }

  @Synchronized
  fun clearActiveSession(context: Context) {
    activeCardId = null
    activeQueueIds.clear()
    hasVisitedEndPage = false
    ratingDialogPendingForCardId = null
    persistSessionState(context)
  }

  private fun restoreSessionStateIfNeeded(context: Context) {
    if (activeCardId != null) return
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedId = prefs.getString(KEY_ACTIVE_CARD_ID, null)
    if (!savedId.isNullOrEmpty()) {
      activeCardId = savedId
      val rawQueue = prefs.getString(KEY_ACTIVE_QUEUE_IDS, "") ?: ""
      activeQueueIds = if (rawQueue.isBlank()) {
        mutableListOf()
      } else {
        rawQueue.split(",").filter { it.isNotBlank() }.toMutableList()
      }
      hasVisitedEndPage = prefs.getBoolean(KEY_VISITED_END_PAGE, false)
    }
  }

  private fun persistSessionState(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
      .putString(KEY_ACTIVE_CARD_ID, activeCardId)
      .putString(KEY_ACTIVE_QUEUE_IDS, activeQueueIds.joinToString(","))
      .putBoolean(KEY_VISITED_END_PAGE, hasVisitedEndPage)
      .apply()
  }

  private fun saveAllCards(context: Context, cards: List<MemoryFlashcard>) {
    val sorted = sortCardsByDueDate(cards)
    val array = JSONArray()
    for (card in sorted) {
      val obj = JSONObject().apply {
        put("id", card.id)
        put("fromSura", card.fromSura)
        put("fromAyah", card.fromAyah)
        put("toSura", card.toSura)
        put("toAyah", card.toAyah)
        put("startPage", card.startPage)
        put("endPage", card.endPage)
        put("firstAyahCue", card.firstAyahCue)
        put("lastAyahCue", card.lastAyahCue)
        put("stepIndex", card.stepIndex)
        put("reviewCount", card.reviewCount)
        put("nextReviewTimeMillis", card.nextReviewTimeMillis)
        put("createdAtMillis", card.createdAtMillis)
        put("lastReviewedAtMillis", card.lastReviewedAtMillis)
        put("lastRating", card.lastRating ?: "")
        put("lastNotifiedAtReviewTimeMillis", card.lastNotifiedAtReviewTimeMillis)
        put("cardType", card.cardType.name)
        put("customTitle", card.customTitle)
        put("fullText", card.fullText)
        put("hintWordCount", card.hintWordCount)
        put("fastRevealMode", card.fastRevealMode)
      }
      array.put(obj)
    }
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_CARDS_JSON, array.toString()).apply()
  }
}
