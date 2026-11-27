package com.quran.labs.androidquran.memorization

import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.page.common.data.AyahBounds
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [33])
class QuranMemorizationTest {

  private val page = 1
  private val ayah1 = SuraAyah(1, 1)
  private val ayah2 = SuraAyah(1, 2)

  @Before
  fun setUp() {
    QuranMemorizationManager.clearMemorizationRange()
    QuranMemorizationManager.setPreviousAyahWindowWords(QuranMemorizationManager.WINDOW_DEFAULT_WORDS)
    QuranMemorizationManager.setHiddenModeEnabled(false, page)
    QuranMemorizationManager.registerAyahWordCountFromText(page, ayah1, 4)
    QuranMemorizationManager.registerAyahWordCountFromText(page, ayah2, 4)
    QuranMemorizationManager.setCurrentPage(page)
  }

  @Test
  fun testHideAndRevealFullAyahMode() {
    // Activate via prominent "Reveal Full Ayah" button
    QuranMemorizationManager.onProminentModeButtonPressed(RevealMode.FULL_AYAH, page)

    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isTrue()
    assertThat(QuranMemorizationManager.getRevealMode()).isEqualTo(RevealMode.FULL_AYAH)
    assertThat(QuranMemorizationManager.isAyahFullyRevealed(page, ayah1)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah1, 1)).isFalse()

    // Pressing on the page in FULL_AYAH mode reveals all words of ayah 1
    val transition1 = QuranMemorizationManager.revealNextOnPage(page)
    assertThat(transition1).isNotNull()
    assertThat(transition1!!.suraAyah).isEqualTo(ayah1)
    assertThat(transition1.isFullAyah).isTrue()
    assertThat(transition1.wordPositions).containsExactly(1, 2, 3, 4)
    assertThat(QuranMemorizationManager.isAyahFullyRevealed(page, ayah1)).isTrue()
    assertThat(QuranMemorizationManager.isAyahFullyRevealed(page, ayah2)).isFalse()

    // Pressing on the page again reveals all words of ayah 2
    val transition2 = QuranMemorizationManager.revealNextOnPage(page)
    assertThat(transition2).isNotNull()
    assertThat(transition2!!.suraAyah).isEqualTo(ayah2)
    assertThat(transition2.isFullAyah).isTrue()
    assertThat(transition2.wordPositions).containsExactly(1, 2, 3, 4)
    assertThat(QuranMemorizationManager.isAyahFullyRevealed(page, ayah2)).isTrue()
  }

  @Test
  fun testHideAndRevealWordByWordMode() {
    // Activate via prominent "Word by Word" button
    QuranMemorizationManager.onProminentModeButtonPressed(RevealMode.WORD_BY_WORD, page)

    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isTrue()
    assertThat(QuranMemorizationManager.getRevealMode()).isEqualTo(RevealMode.WORD_BY_WORD)

    // Pressing on the page reveals word 1 of ayah 1 only
    val t1 = QuranMemorizationManager.revealNextOnPage(page)
    assertThat(t1).isNotNull()
    assertThat(t1!!.suraAyah).isEqualTo(ayah1)
    assertThat(t1.isFullAyah).isFalse()
    assertThat(t1.wordPositions).containsExactly(1)
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah1, 1)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah1, 2)).isFalse()
    assertThat(QuranMemorizationManager.isAyahFullyRevealed(page, ayah1)).isFalse()

    // Reveal words 2, 3, 4 of ayah 1
    QuranMemorizationManager.revealNextOnPage(page)
    QuranMemorizationManager.revealNextOnPage(page)
    val t4 = QuranMemorizationManager.revealNextOnPage(page)
    assertThat(t4!!.wordPositions).containsExactly(4)
    assertThat(QuranMemorizationManager.isAyahFullyRevealed(page, ayah1)).isTrue()

    // Next press on the page reveals word 1 of ayah 2
    val tNextAyah = QuranMemorizationManager.revealNextOnPage(page)
    assertThat(tNextAyah).isNotNull()
    assertThat(tNextAyah!!.suraAyah).isEqualTo(ayah2)
    assertThat(tNextAyah.wordPositions).containsExactly(1)
  }

  @Test
  fun testSynthesizedGlyphsPreserveEndOfAyahMedallionOutsideWordBounds() {
    val coordsMap = mapOf(
      "1:1" to listOf(AyahBounds(1, 1, RectF(100f, 50f, 900f, 120f)))
    )
    val synthesized = AyahGlyphSynthesizer.synthesize(page, coordsMap)
    assertThat(synthesized).isNotNull()

    val glyphs = synthesized!!.glyphsByAyah[ayah1]
    assertThat(glyphs).isNotNull()

    val wordGlyphs = glyphs!!.filter { it.glyph is WordGlyph }
    val endGlyphs = glyphs.filter { it.glyph is AyahEndGlyph }

    assertThat(wordGlyphs).hasSize(4)
    assertThat(endGlyphs).hasSize(1)

    val medallionBounds = endGlyphs.single().bounds
    for (wordGlyph in wordGlyphs) {
      // Word bounds must not overlap the end-of-ayah medallion (﴿١﴾)
      assertThat(wordGlyph.bounds.left).isAtLeast(medallionBounds.right)
    }
  }

  @Test
  fun testExpandedGlyphBoundsPreserveMedallionsAndHandlePauseGlyphs() {
    val builder = com.quran.labs.androidquran.data.GlyphsBuilder()
    // Sura 2 Ayah 2 on Line 3 (positions 1..8, where 5 and 7 are pause marks) and Line 4 (positions 9..10, where 10 is medallion)
    builder.append(2, 2, 1, 3, "word", RectF(745f, 362f, 833f, 429f))
    builder.append(2, 2, 2, 3, "word", RectF(559f, 367f, 742f, 431f))
    builder.append(2, 2, 3, 3, "word", RectF(526f, 364f, 559f, 419f))
    builder.append(2, 2, 4, 3, "word", RectF(449f, 366f, 527f, 432f))
    builder.append(2, 2, 5, 3, "pause", RectF(436f, 339f, 464f, 361f))
    builder.append(2, 2, 6, 3, "word", RectF(387f, 380f, 443f, 439f))
    builder.append(2, 2, 7, 3, "pause", RectF(372f, 340f, 400f, 362f))
    builder.append(2, 2, 8, 3, "word", RectF(293f, 366f, 379f, 421f))
    builder.append(2, 2, 9, 4, "word", RectF(886f, 478f, 1000f, 544f))
    builder.append(2, 2, 10, 4, "end", RectF(804f, 474f, 881f, 545f))

    val pageGlyphs = com.quran.page.common.data.coordinates.PageGlyphsCoords(2, builder.build())
    val ayah2_2 = SuraAyah(2, 2)
    QuranMemorizationManager.registerPageGlyphs(2, pageGlyphs)

    // Must have exactly 7 words (pause marks must not inflate word count)
    val wordPositions = QuranMemorizationManager.getWordPositionsForAyah(2, ayah2_2)
    assertThat(wordPositions).containsExactly(1, 2, 3, 4, 5, 6, 7).inOrder()

    // Word 4 and Word 5 have attached pause marks, so their expanded bounds have 2 rects (word + pause)
    val word4Bounds = pageGlyphs.getBoundsForWord(ayah2_2, 4, expandBounds = true)
    assertThat(word4Bounds).isNotNull()
    assertThat(word4Bounds!!).hasSize(2)

    // Word 7 (adjacent to medallion ﴿٢﴾ on line 4) must not intersect the medallion
    val medallionRect = RectF(804f, 474f, 881f, 545f)
    val word7Bounds = pageGlyphs.getBoundsForWord(ayah2_2, 7, expandBounds = true)
    assertThat(word7Bounds).isNotNull()
    for (rect in word7Bounds!!) {
      assertThat(RectF.intersects(rect, medallionRect)).isFalse()
    }
  }

  @Test
  fun testToggleMemorizationModeOnAndOff() {
    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isFalse()

    // Toggle ON -> hides words on the current page
    QuranMemorizationManager.toggleHiddenMode(page)
    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah1, 1)).isFalse()

    // Reveal one word while ON
    QuranMemorizationManager.setRevealMode(RevealMode.WORD_BY_WORD)
    QuranMemorizationManager.revealNextOnPage(page)
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah1, 1)).isTrue()

    // Toggle OFF -> shows all words
    QuranMemorizationManager.toggleHiddenMode(page)
    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah1, 1)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah1, 2)).isTrue()

    // Toggle ON again -> resets revealed words on the page and hides text
    QuranMemorizationManager.toggleHiddenMode(page)
    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(page, ayah1, 1)).isFalse()
  }

  @Test
  fun testTajweedMushafPageProviderConfiguration() {
    val provider = com.quran.data.page.provider.QuranDataModule.provideTajweedPageSet()
    val sizeCalc = provider.getPageSizeCalculator(com.quran.data.source.DisplaySize(1080, 2400))

    assertThat(provider.pageType()).isEqualTo("tajweed")
    assertThat(provider.getDatabaseDirectoryName()).isEqualTo("databases")
    assertThat(provider.getAyahInfoDirectoryName()).isEqualTo("tajweed/databases")
    assertThat(provider.getImagesDirectoryName()).isEqualTo("tajweed")
    assertThat(provider.getImageVersion()).isEqualTo(7)
    assertThat(provider.getImagesZipBaseUrl()).isEqualTo("https://quran.app/data/tajweed/zips/")
    assertThat(provider.getAyahInfoBaseUrl()).isEqualTo("https://quran.app/data/tajweed/databases/ayahinfo/")
    assertThat(provider.getPatchBaseUrl()).isEqualTo("https://quran.app/data/tajweed/patches/v")
    assertThat(sizeCalc.getWidthParameter()).isEqualTo("1280")
    assertThat(sizeCalc.getTabletWidthParameter()).isEqualTo("1280")
  }

  @Test
  fun testMemoryFlashcardCreationAnd12StepSchedule() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.clearAllCards(context)

    val startMillis = 1_000_000L
    // Create a card from Sura 2 Ayah 1 (Page 2) to Sura 2 Ayah 10 (Page 3)
    val card = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.createFlashcard(
      context = context,
      fromSura = 2,
      fromAyah = 1,
      toSura = 2,
      toAyah = 10,
      nowMillis = startMillis
    )

    assertThat(card.fromSura).isEqualTo(2)
    assertThat(card.fromAyah).isEqualTo(1)
    assertThat(card.toSura).isEqualTo(2)
    assertThat(card.toAyah).isEqualTo(10)
    assertThat(card.startPage).isEqualTo(2)
    assertThat(card.endPage).isEqualTo(3)
    assertThat(card.firstAyahCue).isNotEmpty()
    assertThat(card.lastAyahCue).isNotEmpty()

    // Verify all 12 repetition schedule intervals:
    // 20min, 1h, 9h, 24h, 72h, 7d, 14d, 30d, 60d, 120d, 240d, 360d
    val expectedLabels = listOf(
      "20min", "1h", "9h", "24h", "72h", "7d", "14d", "30d", "60d", "120d", "240d", "360d"
    )
    val expectedIntervalsMs = listOf(
      20L * 60_000L,
      1L * 3_600_000L,
      9L * 3_600_000L,
      24L * 3_600_000L,
      72L * 3_600_000L,
      7L * 86_400_000L,
      14L * 86_400_000L,
      30L * 86_400_000L,
      60L * 86_400_000L,
      120L * 86_400_000L,
      240L * 86_400_000L,
      360L * 86_400_000L
    )
    assertThat(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.SCHEDULE_LABELS)
      .containsExactlyElementsIn(expectedLabels).inOrder()
    assertThat(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.SCHEDULE_INTERVALS_MS)
      .containsExactlyElementsIn(expectedIntervalsMs).inOrder()

    var currentTime = startMillis
    for (step in expectedIntervalsMs.indices) {
      val rated = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.rateCard(
        context = context,
        cardId = card.id,
        isGood = true,
        nowMillis = currentTime
      )!!
      assertThat(rated.nextReviewTimeMillis).isEqualTo(currentTime + expectedIntervalsMs[step])
      currentTime = rated.nextReviewTimeMillis
    }

    // Rating "Repeat" resets the card to the 20min interval (stepIndex = 0)
    val repeated = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.rateCard(
      context = context,
      cardId = card.id,
      isGood = false,
      nowMillis = currentTime
    )!!
    assertThat(repeated.stepIndex).isEqualTo(0)
    assertThat(repeated.nextReviewTimeMillis).isEqualTo(currentTime + 20L * 60_000L)
    assertThat(repeated.lastRating).isEqualTo("REPEAT")
  }

  @Test
  fun testFlashcardReviewSessionOpensTajweedMemorizationAndDetectsLastPageFlip() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.clearAllCards(context)

    // Card 1: Sura 2 Ayah 1..10 (Pages 2..3)
    val card1 = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.createFlashcard(
      context = context,
      fromSura = 2,
      fromAyah = 1,
      toSura = 2,
      toAyah = 10
    )
    // Card 2: Sura 1 Ayah 1..7 (Page 1)
    val card2 = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.createFlashcard(
      context = context,
      fromSura = 1,
      fromAyah = 1,
      toSura = 1,
      toAyah = 7
    )

    val reviewIntent = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.createReviewIntent(
      context = context,
      card = card1,
      queueIds = listOf(card2.id)
    )

    // Verify it opens on the page of the first ayah (page 2), in Memorization Mode, and in Tajweed Mushaf
    assertThat(reviewIntent.getIntExtra("page", -1)).isEqualTo(2)
    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isTrue()
    assertThat(com.quran.labs.androidquran.util.QuranSettings.getInstance(context).pageType)
      .isEqualTo("tajweed")

    // Viewing startPage (page 2) -> not yet flipped past endPage (page 3)
    assertThat(
      com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.onReaderPageSelected(context, 2)
    ).isNull()

    // Viewing endPage (page 3) -> marks endPage visited, still reading endPage
    assertThat(
      com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.onReaderPageSelected(context, 3)
    ).isNull()

    // Flipping the page of the last ayah (to page 4) -> prompts rating for card1!
    val promptedCard = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.onReaderPageSelected(
      context,
      4
    )
    assertThat(promptedCard).isNotNull()
    assertThat(promptedCard!!.id).isEqualTo(card1.id)

    // After rating card1, advancing session switches to card2 (page 1)
    com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.rateCard(context, card1.id, isGood = true)
    val nextCard = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.advanceToNextCardInSession(context)
    assertThat(nextCard).isNotNull()
    assertThat(nextCard!!.id).isEqualTo(card2.id)
    assertThat(nextCard.startPage).isEqualTo(1)
  }

  @Test
  fun testArabicAndEnglishLocalizationSortingAndPopupNotifications() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.clearAllCards(context)

    // 1. Verify Arabic vs English number formatting and schedule labels
    assertThat(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.formatNumber(360, isArabic = true))
      .isEqualTo("٣٦٠")
    assertThat(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.formatNumber(360, isArabic = false))
      .isEqualTo("360")
    assertThat(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.getScheduleLabelForStep(0, isArabic = true))
      .isEqualTo("٢٠ د")
    assertThat(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.getScheduleLabelForStep(0, isArabic = false))
      .isEqualTo("20min")

    // 2. Verify cards are sorted by due date (nextReviewTimeMillis ascending)
    val cardLater = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.createFlashcard(
      context = context,
      fromSura = 1,
      fromAyah = 1,
      toSura = 1,
      toAyah = 7,
      nowMillis = 500_000L
    )
    val cardEarlier = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.createFlashcard(
      context = context,
      fromSura = 2,
      fromAyah = 1,
      toSura = 2,
      toAyah = 5,
      nowMillis = 100_000L
    )
    val cardMiddle = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.createFlashcard(
      context = context,
      fromSura = 3,
      fromAyah = 1,
      toSura = 3,
      toAyah = 5,
      nowMillis = 300_000L
    )

    val sorted = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.getAllCards(context)
    assertThat(sorted.map { it.id })
      .containsExactly(cardEarlier.id, cardMiddle.id, cardLater.id)
      .inOrder()

    // 3. Verify high-importance heads-up popup notification channel
    com.quran.labs.androidquran.flashcards.FlashcardDueNotificationScheduler.ensureNotificationChannel(context)
    val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val channel = nm.getNotificationChannel(
      com.quran.labs.androidquran.flashcards.FlashcardDueNotificationScheduler.CHANNEL_ID
    )
    assertThat(channel).isNotNull()
    assertThat(channel.importance).isEqualTo(android.app.NotificationManager.IMPORTANCE_HIGH)
  }

  @Test
  fun testMemoryFlashcardsActivityLaunchesWithoutCrash() {
    val controller = org.robolectric.Robolectric.buildActivity(
      com.quran.labs.androidquran.flashcards.MemoryFlashcardsActivity::class.java
    ).setup()
    val activity = controller.get()
    assertThat(activity).isNotNull()
    assertThat(activity.isFinishing).isFalse()
    controller.pause().stop().destroy()
  }

  @Test
  fun testSelectReciterInSettingsAndPageMenu() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    // 1. Verify Select Reciter & Play Audio exist in the Quran page settings menu
    val popupMenu = android.widget.PopupMenu(context, android.view.View(context))
    popupMenu.menuInflater.inflate(com.quran.labs.androidquran.R.menu.quran_menu, popupMenu.menu)
    assertThat(popupMenu.menu.findItem(com.quran.labs.androidquran.R.id.select_reciter)).isNotNull()
    assertThat(popupMenu.menu.findItem(com.quran.labs.androidquran.R.id.play_page_audio)).isNotNull()

    // 2. Verify R.xml.quran_preferences includes prefs_select_reciter
    val parser = context.resources.getXml(com.quran.labs.androidquran.R.xml.quran_preferences)
    var foundSelectReciterPref = false
    var eventType = parser.eventType
    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
      if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "Preference") {
        for (i in 0 until parser.attributeCount) {
          if (parser.getAttributeName(i) == "key") {
            val resId = parser.getAttributeResourceValue(i, 0)
            val keyValue = if (resId != 0) context.getString(resId) else parser.getAttributeValue(i)
            if (keyValue == "prefs_select_reciter") {
              foundSelectReciterPref = true
            }
          }
        }
      }
      eventType = parser.next()
    }
    parser.close()
    assertThat(foundSelectReciterPref).isTrue()

    // 3. Verify CurrentQariManager & QariUtil list reciters and persist selection
    val qariUtil = com.quran.labs.androidquran.common.audio.util.QariUtil(
      com.quran.data.page.provider.madani.MadaniPageProvider()
    )
    val qariManager = com.quran.labs.androidquran.common.audio.repository.CurrentQariManager(context, qariUtil)
    val qaris = qariUtil.getQariList(context)
    assertThat(qaris).isNotEmpty()
    val targetQari = qaris.last()
    qariManager.setCurrentQari(targetQari.id)
    assertThat(qariManager.currentQariId()).isEqualTo(targetQari.id)
    assertThat(
      com.quran.labs.androidquran.common.audio.model.QariItem.fromQari(context, qariManager.currentQari()).name
    ).isEqualTo(targetQari.name)
  }

  @Test
  fun testHadithAndNonQuranTextFlashcardCreationAndStudy() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.clearAllCards(context)

    val hadithText =
      "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى، فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى اللَّهِ وَرَسُولِهِ فَهِجْرَتُهُ إِلَى اللَّهِ وَرَسُولِهِ"

    // 1. Create Hadith card with 4-word auto-cue and fast reveal pace
    val hadithCard = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.createTextFlashcard(
      context = context,
      fullText = hadithText,
      customTitle = "حديث شريف (عمدة الأحكام)",
      autoFourWordHint = true,
      fastRevealMode = true,
      nowMillis = 10_000L
    )
    assertThat(hadithCard).isNotNull()
    assertThat(hadithCard!!.isTextCard()).isTrue()
    assertThat(hadithCard.cardType).isEqualTo(com.quran.labs.androidquran.flashcards.FlashcardType.TEXT)
    assertThat(hadithCard.firstAyahCue).isEqualTo("إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا...")
    assertThat(hadithCard.parseWords()).hasSize(18)
    assertThat(hadithCard.fastRevealMode).isTrue()

    // 2. Verify 2-word hint extraction option
    val twoWordCue = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.extractTextFrontCue(
      hadithText,
      hintWordCount = 2
    )
    assertThat(twoWordCue).isEqualTo("إِنَّمَا الأَعْمَالُ...")

    // 3. Verify GOOD -> HARD -> REPEAT difficulty ratings on 12-step schedule
    val afterGood1 = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.rateCardWithDifficulty(
      context = context,
      cardId = hadithCard.id,
      rating = com.quran.labs.androidquran.flashcards.FlashcardRating.GOOD,
      nowMillis = 20_000L
    )!!
    assertThat(afterGood1.stepIndex).isEqualTo(1)
    assertThat(afterGood1.nextReviewTimeMillis).isEqualTo(20_000L + 20L * 60_000L)

    val afterGood2 = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.rateCardWithDifficulty(
      context = context,
      cardId = hadithCard.id,
      rating = com.quran.labs.androidquran.flashcards.FlashcardRating.GOOD,
      nowMillis = afterGood1.nextReviewTimeMillis
    )!!
    assertThat(afterGood2.stepIndex).isEqualTo(2)

    // Rating HARD keeps stepIndex = 2 while scheduling with step 1 interval (1h)
    val afterHard = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.rateCardWithDifficulty(
      context = context,
      cardId = hadithCard.id,
      rating = com.quran.labs.androidquran.flashcards.FlashcardRating.HARD,
      nowMillis = afterGood2.nextReviewTimeMillis
    )!!
    assertThat(afterHard.stepIndex).isEqualTo(2)
    assertThat(afterHard.nextReviewTimeMillis).isEqualTo(afterGood2.nextReviewTimeMillis + 1L * 3_600_000L)

    // 4. Verify MemoryFlashcardsActivity launches directly into Hadith Word-by-Word Study screen without crash
    val studyIntent = android.content.Intent(
      context,
      com.quran.labs.androidquran.flashcards.MemoryFlashcardsActivity::class.java
    ).apply {
      putExtra(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.EXTRA_STUDY_TEXT_CARD_ID, hadithCard.id)
    }
    val controller = org.robolectric.Robolectric.buildActivity(
      com.quran.labs.androidquran.flashcards.MemoryFlashcardsActivity::class.java,
      studyIntent
    ).setup()
    val activity = controller.get()
    assertThat(activity.isFinishing).isFalse()
    controller.pause().stop().destroy()
  }

  @Test
  fun testWordWindowModeHidesPreviousWordsExceptLastFourOfPreviousAyah() {
    val testPage = 50
    val a1 = SuraAyah(3, 1) // 6 words
    val a2 = SuraAyah(3, 2) // 6 words
    val a3 = SuraAyah(3, 3) // 4 words (final ayah on testPage)

    QuranMemorizationManager.registerAyahWordCountFromText(testPage, a1, 6)
    QuranMemorizationManager.registerAyahWordCountFromText(testPage, a2, 6)
    QuranMemorizationManager.registerAyahWordCountFromText(testPage, a3, 4)

    QuranMemorizationManager.onProminentModeButtonPressed(RevealMode.WORD_WINDOW, testPage)
    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isTrue()
    assertThat(QuranMemorizationManager.getRevealMode()).isEqualTo(RevealMode.WORD_WINDOW)

    // 1. Reveal all 6 words of ayah 1
    repeat(6) {
      assertThat(QuranMemorizationManager.revealNextOnPage(testPage)).isNotNull()
    }
    for (w in 1..6) {
      assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, w)).isTrue()
    }
    assertThat(QuranMemorizationManager.isFinalAyahRevealed(testPage)).isFalse()

    // 2. Tap once to reveal word 1 of ayah 2:
    // Ayah 1 must hide words 1..2 and keep ONLY its last 4 words (3, 4, 5, 6) visible!
    val tA2W1 = QuranMemorizationManager.revealNextOnPage(testPage)
    assertThat(tA2W1).isNotNull()
    assertThat(tA2W1!!.suraAyah).isEqualTo(a2)
    assertThat(tA2W1.wordPositions).containsExactly(1)

    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, 1)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, 2)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, 3)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, 4)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, 5)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, 6)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a2, 1)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a2, 2)).isFalse()

    // 3. Reveal remaining words 2..6 of ayah 2, then reveal word 1 of ayah 3:
    // Now ALL words of ayah 1 are hidden, and ayah 2 keeps ONLY its last 4 words (3, 4, 5, 6)!
    repeat(5) {
      assertThat(QuranMemorizationManager.revealNextOnPage(testPage)).isNotNull()
    }
    val tA3W1 = QuranMemorizationManager.revealNextOnPage(testPage)
    assertThat(tA3W1).isNotNull()
    assertThat(tA3W1!!.suraAyah).isEqualTo(a3)
    assertThat(tA3W1.wordPositions).containsExactly(1)

    for (w in 1..6) {
      assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, w)).isFalse()
    }
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a2, 1)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a2, 2)).isFalse()
    for (w in 3..6) {
      assertThat(QuranMemorizationManager.isWordRevealed(testPage, a2, w)).isTrue()
    }
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a3, 1)).isTrue()
    assertThat(QuranMemorizationManager.isFinalAyahRevealed(testPage)).isFalse()

    // 4. Reveal remaining words 2..4 of final ayah (a3)
    repeat(3) {
      assertThat(QuranMemorizationManager.revealNextOnPage(testPage)).isNotNull()
    }
    assertThat(QuranMemorizationManager.isFinalAyahRevealed(testPage)).isTrue()

    // 5. Any additional click after final ayah is revealed returns null so PagerActivity turns to next page
    assertThat(QuranMemorizationManager.revealNextOnPage(testPage)).isNull()
    assertThat(QuranMemorizationManager.isFinalAyahRevealed(testPage)).isTrue()
  }

  @Test
  fun testGlobalHideAndShowAcrossAllPagesAndMenuOption() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    // Verify Word Window option exists in quran_menu.xml
    val popupMenu = android.widget.PopupMenu(context, android.view.View(context))
    popupMenu.menuInflater.inflate(com.quran.labs.androidquran.R.menu.quran_menu, popupMenu.menu)
    assertThat(popupMenu.menu.findItem(com.quran.labs.androidquran.R.id.memorization_word_window)).isNotNull()

    val p10 = 10
    val p11 = 11
    val p12 = 12
    val a10 = SuraAyah(2, 62)
    val a11 = SuraAyah(2, 70)
    val a12 = SuraAyah(2, 77)
    QuranMemorizationManager.registerAyahWordCountFromText(p10, a10, 4)
    QuranMemorizationManager.registerAyahWordCountFromText(p11, a11, 4)
    QuranMemorizationManager.registerAyahWordCountFromText(p12, a12, 4)

    // Enable global hide on page 10 -> pages 10, 11, and 12 are ALL hidden
    QuranMemorizationManager.setHiddenModeEnabled(true, p10, context = context)
    QuranMemorizationManager.setRevealMode(RevealMode.FULL_AYAH, context)
    assertThat(QuranMemorizationManager.isWordRevealed(p10, a10, 1)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(p11, a11, 1)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(p12, a12, 1)).isFalse()

    // Reveal ayahs on page 10 and page 11
    QuranMemorizationManager.revealNextOnPage(p10, context = context)
    QuranMemorizationManager.revealNextOnPage(p11, context = context)
    assertThat(QuranMemorizationManager.isWordRevealed(p10, a10, 1)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(p11, a11, 1)).isTrue()

    // Press global Show button while on page 12 -> ALL pages show text
    QuranMemorizationManager.toggleHiddenMode(p12, context = context)
    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(p10, a10, 1)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(p11, a11, 1)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(p12, a12, 1)).isTrue()

    // Press global Hide button while on page 12 -> ALL pages (including 10 and 11!) become hidden again
    QuranMemorizationManager.toggleHiddenMode(p12, context = context)
    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(p10, a10, 1)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(p11, a11, 1)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(p12, a12, 1)).isFalse()
  }

  @Test
  fun testOptionalWordWindowSizesZeroCustomAndFullPreviousAyah() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val testPage = 60
    val a1 = SuraAyah(4, 1) // 6 words
    val a2 = SuraAyah(4, 2) // 5 words
    val a3 = SuraAyah(4, 3) // 4 words

    QuranMemorizationManager.registerAyahWordCountFromText(testPage, a1, 6)
    QuranMemorizationManager.registerAyahWordCountFromText(testPage, a2, 5)
    QuranMemorizationManager.registerAyahWordCountFromText(testPage, a3, 4)

    QuranMemorizationManager.onProminentModeButtonPressed(RevealMode.WORD_WINDOW, testPage, context = context)

    // Reveal all 6 words of a1, then word 1 of a2
    repeat(7) {
      assertThat(QuranMemorizationManager.revealNextOnPage(testPage, context = context)).isNotNull()
    }

    // 1. Window = 0 (WINDOW_NONE): NO word of previous ayah (a1) is shown!
    QuranMemorizationManager.setPreviousAyahWindowWords(QuranMemorizationManager.WINDOW_NONE, context)
    for (w in 1..6) {
      assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, w)).isFalse()
    }
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a2, 1)).isTrue()
    assertThat(MemorizationFormatting.formatWordWindowMenuTitle(context)).contains("0")

    // 2. Window = 2 words: ONLY last 2 words (5, 6) of a1 are shown
    QuranMemorizationManager.setPreviousAyahWindowWords(2, context)
    for (w in 1..4) {
      assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, w)).isFalse()
    }
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, 5)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, 6)).isTrue()
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a2, 1)).isTrue()

    // 3. Window = Full Previous Ayah (WINDOW_FULL_AYAH): ALL 6 words of a1 are shown!
    QuranMemorizationManager.setPreviousAyahWindowWords(QuranMemorizationManager.WINDOW_FULL_AYAH, context)
    for (w in 1..6) {
      assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, w)).isTrue()
    }
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a2, 1)).isTrue()

    // Now finish a2 (4 remaining words) and reveal word 1 of a3:
    // In WINDOW_FULL_AYAH mode, a1 (2 ayahs back) becomes completely hidden while ALL 5 words of a2 stay shown!
    repeat(5) {
      assertThat(QuranMemorizationManager.revealNextOnPage(testPage, context = context)).isNotNull()
    }
    for (w in 1..6) {
      assertThat(QuranMemorizationManager.isWordRevealed(testPage, a1, w)).isFalse()
    }
    for (w in 1..5) {
      assertThat(QuranMemorizationManager.isWordRevealed(testPage, a2, w)).isTrue()
    }
    assertThat(QuranMemorizationManager.isWordRevealed(testPage, a3, 1)).isTrue()
  }

  @Test
  fun testMemorizationRangeAndRepetitionAcrossMultiplePages() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val quranInfo = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.getQuranInfo()

    // Verify toolbar & page menu items exist
    val ayahPopup = android.widget.PopupMenu(context, android.view.View(context))
    ayahPopup.menuInflater.inflate(
      com.quran.labs.androidquran.common.toolbar.R.menu.ayah_menu,
      ayahPopup.menu
    )
    assertThat(
      ayahPopup.menu.findItem(com.quran.labs.androidquran.common.toolbar.R.id.cab_memorize_range)
    ).isNotNull()

    val pagePopup = android.widget.PopupMenu(context, android.view.View(context))
    pagePopup.menuInflater.inflate(com.quran.labs.androidquran.R.menu.quran_menu, pagePopup.menu)
    assertThat(
      pagePopup.menu.findItem(com.quran.labs.androidquran.R.id.memorization_repeat_range)
    ).isNotNull()

    // Register exact word counts on Page 2 (2:1..2:5) and Page 3 (2:6..2:16)
    for (a in 1..5) {
      QuranMemorizationManager.registerAyahWordCountFromText(2, SuraAyah(2, a), 3)
    }
    for (a in 6..10) {
      QuranMemorizationManager.registerAyahWordCountFromText(3, SuraAyah(2, a), 3)
    }

    // Highlight sequence Sura 2 Ayah 3 (middle of Page 2) -> Sura 2 Ayah 7 (middle of Page 3)
    // with 2 repetitions in WORD_WINDOW mode
    QuranMemorizationManager.setRevealMode(RevealMode.WORD_WINDOW, context)
    QuranMemorizationManager.setPreviousAyahWindowWords(2, context)
    val range = QuranMemorizationManager.startRangeMemorization(
      startAyah = SuraAyah(2, 3),
      endAyah = SuraAyah(2, 7),
      targetRepetitions = 2,
      quranInfo = quranInfo,
      context = context
    )
    assertThat(range.startPage).isEqualTo(2)
    assertThat(range.endPage).isEqualTo(3)
    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isTrue()

    // Helper simulating continuous user presses across pages ("all the user needs to do is keep pressing")
    fun runCompletePassAndVerifyStartAndEnd(expectedCurrentPageAtStart: Int) {
      var curPage = expectedCurrentPageAtStart
      assertThat(curPage).isEqualTo(2)

      // Very first press MUST reveal word 1 of startAyah (2:3), skipping 2:1 and 2:2!
      val firstTap = QuranMemorizationManager.onMemorizationPageTap(curPage, quranInfo, context)
      assertThat(firstTap).isInstanceOf(MemorizationTapOutcome.Revealed::class.java)
      val firstRevealed = (firstTap as MemorizationTapOutcome.Revealed).transition
      assertThat(firstRevealed.suraAyah).isEqualTo(SuraAyah(2, 3))
      assertThat(firstRevealed.wordPositions).containsExactly(1)
      assertThat(QuranMemorizationManager.isWordRevealed(2, SuraAyah(2, 1), 1)).isFalse()
      assertThat(QuranMemorizationManager.isWordRevealed(2, SuraAyah(2, 2), 1)).isFalse()

      // Remaining 8 words on Page 2 (words 2..3 of 2:3, words 1..3 of 2:4, words 1..3 of 2:5)
      repeat(8) {
        val tap = QuranMemorizationManager.onMemorizationPageTap(curPage, quranInfo, context)
        assertThat(tap).isInstanceOf(MemorizationTapOutcome.Revealed::class.java)
      }

      // Next press on Page 2 turns forward to Page 3!
      val turnForward = QuranMemorizationManager.onMemorizationPageTap(curPage, quranInfo, context)
      assertThat(turnForward).isEqualTo(MemorizationTapOutcome.TurnToNextPage(3))
      curPage = 3

      // Reveal 6 words on Page 3 (words 1..3 of 2:6 and words 1..3 of 2:7 = endAyah; ignores 2:8..2:16!)
      var lastRevealedAyah: SuraAyah? = null
      repeat(6) {
        val tap = QuranMemorizationManager.onMemorizationPageTap(curPage, quranInfo, context)
        assertThat(tap).isInstanceOf(MemorizationTapOutcome.Revealed::class.java)
        lastRevealedAyah = (tap as MemorizationTapOutcome.Revealed).transition.suraAyah
      }
      assertThat(lastRevealedAyah).isEqualTo(SuraAyah(2, 7))
      assertThat(QuranMemorizationManager.isWordRevealed(3, SuraAyah(2, 8), 1)).isFalse()
    }

    // Initial pass -> press after 2:7 flips back to Page 2 (Repetition 1/2)
    runCompletePassAndVerifyStartAndEnd(2)
    val loop1 = QuranMemorizationManager.onMemorizationPageTap(3, quranInfo, context)
    assertThat(loop1).isEqualTo(
      MemorizationTapOutcome.LoopBackToRangeStart(
        targetPage = 2,
        completedRepetitions = 1,
        targetRepetitions = 2
      )
    )
    // Range is re-hidden on flip-back!
    assertThat(QuranMemorizationManager.isWordRevealed(2, SuraAyah(2, 3), 1)).isFalse()
    assertThat(QuranMemorizationManager.isWordRevealed(3, SuraAyah(2, 7), 1)).isFalse()

    // Repetition 1 -> press after 2:7 flips back to Page 2 (Repetition 2/2)
    runCompletePassAndVerifyStartAndEnd(2)
    val loop2 = QuranMemorizationManager.onMemorizationPageTap(3, quranInfo, context)
    assertThat(loop2).isEqualTo(
      MemorizationTapOutcome.LoopBackToRangeStart(
        targetPage = 2,
        completedRepetitions = 2,
        targetRepetitions = 2
      )
    )

    // Repetition 2 (final) -> press after 2:7 completes repetition session!
    runCompletePassAndVerifyStartAndEnd(2)
    val finished = QuranMemorizationManager.onMemorizationPageTap(3, quranInfo, context)
    assertThat(finished).isEqualTo(
      MemorizationTapOutcome.RangeRepetitionsCompleted(
        endPage = 3,
        completedRepetitions = 2
      )
    )
    assertThat(QuranMemorizationManager.getActiveRange()).isNull()

    // Also verify REPEAT_UNLIMITED loops indefinitely in FULL_AYAH mode
    QuranMemorizationManager.setRevealMode(RevealMode.FULL_AYAH, context)
    QuranMemorizationManager.startRangeMemorization(
      startAyah = SuraAyah(2, 5),
      endAyah = SuraAyah(2, 6),
      targetRepetitions = QuranMemorizationManager.REPEAT_UNLIMITED,
      quranInfo = quranInfo,
      context = context
    )
    for (cycle in 1..4) {
      // Press 1 on Page 2 -> reveals 2:5
      val t1 = QuranMemorizationManager.onMemorizationPageTap(2, quranInfo, context)
      assertThat((t1 as MemorizationTapOutcome.Revealed).transition.suraAyah).isEqualTo(SuraAyah(2, 5))
      // Press 2 on Page 2 -> turns to Page 3
      assertThat(QuranMemorizationManager.onMemorizationPageTap(2, quranInfo, context))
        .isEqualTo(MemorizationTapOutcome.TurnToNextPage(3))
      // Press 3 on Page 3 -> reveals 2:6
      val t2 = QuranMemorizationManager.onMemorizationPageTap(3, quranInfo, context)
      assertThat((t2 as MemorizationTapOutcome.Revealed).transition.suraAyah).isEqualTo(SuraAyah(2, 6))
      // Press 4 on Page 3 -> flips back to Page 2 infinitely!
      val loopInf = QuranMemorizationManager.onMemorizationPageTap(3, quranInfo, context)
      assertThat(loopInf).isEqualTo(
        MemorizationTapOutcome.LoopBackToRangeStart(
          targetPage = 2,
          completedRepetitions = cycle,
          targetRepetitions = QuranMemorizationManager.REPEAT_UNLIMITED
        )
      )
    }
  }

  @Test
  fun testSingleWordOnlyModeShowsOnlyPressedWordEvenInsideSameAyah() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    // 1. Verify Single Word Only menu item exists in quran_menu.xml
    val popupMenu = android.widget.PopupMenu(context, android.view.View(context))
    popupMenu.menuInflater.inflate(com.quran.labs.androidquran.R.menu.quran_menu, popupMenu.menu)
    assertThat(popupMenu.menu.findItem(com.quran.labs.androidquran.R.id.memorization_single_word)).isNotNull()

    // 2. Activate Single Word Only mode on page 1 (ayah1 = 4 words, ayah2 = 4 words)
    QuranMemorizationManager.onProminentModeButtonPressed(RevealMode.SINGLE_WORD, page, context = context)
    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isTrue()
    assertThat(QuranMemorizationManager.getRevealMode()).isEqualTo(RevealMode.SINGLE_WORD)

    // Initially everything is blank (0 words visible)
    for (w in 1..4) {
      assertThat(QuranMemorizationManager.isWordRevealed(page, ayah1, w)).isFalse()
      assertThat(QuranMemorizationManager.isWordRevealed(page, ayah2, w)).isFalse()
    }

    // 3. Press through every word of ayah 1 (1..4) and ayah 2 (1..4):
    // Even inside the SAME ayah, ONLY the single word just pressed may be visible!
    val expectedSequence = listOf(
      ayah1 to 1,
      ayah1 to 2,
      ayah1 to 3,
      ayah1 to 4,
      ayah2 to 1,
      ayah2 to 2,
      ayah2 to 3,
      ayah2 to 4
    )

    for ((expectedAyah, expectedWord) in expectedSequence) {
      val outcome = QuranMemorizationManager.onMemorizationPageTap(page, context = context)
      assertThat(outcome).isInstanceOf(MemorizationTapOutcome.Revealed::class.java)
      val transition = (outcome as MemorizationTapOutcome.Revealed).transition
      assertThat(transition.suraAyah).isEqualTo(expectedAyah)
      assertThat(transition.wordPositions).containsExactly(expectedWord)
      assertThat(transition.fadingOutWords).isEmpty()

      for (ayah in listOf(ayah1, ayah2)) {
        for (w in 1..4) {
          val shouldBeVisible = (ayah == expectedAyah && w == expectedWord)
          assertThat(QuranMemorizationManager.isWordRevealed(page, ayah, w)).isEqualTo(shouldBeVisible)
        }
      }
    }

    // 4. After pressing the final word on the page, one more press turns to the next page!
    assertThat(QuranMemorizationManager.isFinalAyahRevealed(page, context = context)).isTrue()
    val turnOutcome = QuranMemorizationManager.onMemorizationPageTap(page, context = context)
    assertThat(turnOutcome).isEqualTo(MemorizationTapOutcome.TurnToNextPage(page + 1))
  }

  @Test
  fun testWordWindowFadeOutAnimationWhenRemovingWords() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val testPage = 70
    val a1 = SuraAyah(5, 1) // 6 words
    val a2 = SuraAyah(5, 2) // 5 words
    val a3 = SuraAyah(5, 3) // 4 words

    QuranMemorizationManager.registerAyahWordCountFromText(testPage, a1, 6)
    QuranMemorizationManager.registerAyahWordCountFromText(testPage, a2, 5)
    QuranMemorizationManager.registerAyahWordCountFromText(testPage, a3, 4)

    // Attach a HighlightingImageView configured for testPage so we can verify actual view fade fractions
    val imageView = com.quran.labs.androidquran.view.HighlightingImageView(context)
    val ayahBoundsMap = mapOf(
      "5:1" to listOf(AyahBounds(1, 1, RectF(100f, 100f, 900f, 160f))),
      "5:2" to listOf(AyahBounds(2, 1, RectF(100f, 200f, 900f, 260f))),
      "5:3" to listOf(AyahBounds(3, 1, RectF(100f, 300f, 900f, 360f)))
    )
    imageView.setAyahData(com.quran.page.common.data.AyahCoordinates(testPage, ayahBoundsMap, null))

    // Override with compact deterministic word counts on testPage for clear assertions
    QuranMemorizationManager.registerAyahWordCountFromText(testPage, a1, 6)
    QuranMemorizationManager.registerAyahWordCountFromText(testPage, a2, 5)
    QuranMemorizationManager.registerAyahWordCountFromText(testPage, a3, 4)

    QuranMemorizationManager.setPreviousAyahWindowWords(4, context)
    QuranMemorizationManager.onProminentModeButtonPressed(RevealMode.WORD_WINDOW, testPage, context = context)

    // 1. Reveal all 6 words of a1 and let their reveal animations finish to 1.0f
    repeat(6) {
      val t = QuranMemorizationManager.revealNextOnPage(testPage, context = context)
      assertThat(t).isNotNull()
      assertThat(t!!.fadingOutWords).isEmpty()
    }
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    // 2. Reveal word 1 of a2: words 1 and 2 of a1 leave the 4-word window and MUST fade out!
    val tA2W1 = QuranMemorizationManager.revealNextOnPage(testPage, context = context)!!
    assertThat(tA2W1.suraAyah).isEqualTo(a2)
    assertThat(tA2W1.wordPositions).containsExactly(1)
    assertThat(tA2W1.fadingOutWords).containsExactly(
      FadingWord(a1, 1),
      FadingWord(a1, 2)
    )

    // At t = 0ms, removed words start at 1.0f (no instant snap) and newly revealed word starts at 0.0f
    imageView.advanceRevealAnimationForTest(0L)
    assertThat(imageView.wordAnimationFractions["5:1:1"]).isWithin(0.01f).of(1.0f)
    assertThat(imageView.wordAnimationFractions["5:1:2"]).isWithin(0.01f).of(1.0f)
    assertThat(imageView.wordAnimationFractions["5:2:1"]).isWithin(0.01f).of(0.0f)

    // Mid-animation (t = 260ms): removed words are smoothly partially faded (0.25f)
    imageView.advanceRevealAnimationForTest(260L)
    val midFadeOut1 = imageView.wordAnimationFractions["5:1:1"]!!
    val midFadeOut2 = imageView.wordAnimationFractions["5:1:2"]!!
    val midFadeIn = imageView.wordAnimationFractions["5:2:1"]!!
    assertThat(midFadeOut1).isGreaterThan(0.1f)
    assertThat(midFadeOut1).isLessThan(0.6f)
    assertThat(midFadeOut2).isEqualTo(midFadeOut1)
    assertThat(midFadeIn).isGreaterThan(0.8f)

    // End of fade-out (t = 520ms): animation completes and clears map entries cleanly
    imageView.advanceRevealAnimationForTest(
      com.quran.labs.androidquran.view.HighlightingImageView.WORD_WINDOW_FADE_OUT_DURATION_MS
    )
    assertThat(imageView.wordAnimationFractions).isEmpty()

    // 3. Shrinking the window dynamically (from 4 words -> 2 words) ALSO fades out newly removed words (3 and 4 of a1)!
    QuranMemorizationManager.setPreviousAyahWindowWords(2, context)
    val shrinkTransition = QuranMemorizationManager.uiState.value.lastTransition
    assertThat(shrinkTransition).isNotNull()
    assertThat(shrinkTransition!!.fadingOutWords).containsExactly(
      FadingWord(a1, 3),
      FadingWord(a1, 4)
    )
    imageView.advanceRevealAnimationForTest(200L)
    assertThat(imageView.wordAnimationFractions["5:1:3"]).isNotNull()
    assertThat(imageView.wordAnimationFractions["5:1:3"]!!).isGreaterThan(0.05f)
    assertThat(imageView.wordAnimationFractions["5:1:3"]!!).isLessThan(0.95f)

    imageView.advanceRevealAnimationForTest(
      com.quran.labs.androidquran.view.HighlightingImageView.WORD_WINDOW_FADE_OUT_DURATION_MS
    )
    QuranMemorizationManager.removeListener(imageView)
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
  }

  @Test
  @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
  fun testSingleWordTajweedModeKeepsBlackMadaniTextAndShowsTajweedOnlyForPressedWord() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    // 1. Verify Single Word Tajweed menu item exists in quran_menu.xml
    val popupMenu = android.widget.PopupMenu(context, android.view.View(context))
    popupMenu.menuInflater.inflate(com.quran.labs.androidquran.R.menu.quran_menu, popupMenu.menu)
    assertThat(popupMenu.menu.findItem(com.quran.labs.androidquran.R.id.memorization_single_word_tajweed)).isNotNull()

    // 2. Verify activating SINGLE_WORD_TAJWEED switches pageType to "tajweed" automatically
    com.quran.labs.androidquran.util.QuranSettings.getInstance(context).pageType = "madani"
    QuranMemorizationManager.onProminentModeButtonPressed(
      RevealMode.SINGLE_WORD_TAJWEED,
      page,
      context = context
    )
    assertThat(QuranMemorizationManager.isHiddenModeEnabled()).isTrue()
    assertThat(QuranMemorizationManager.getRevealMode()).isEqualTo(RevealMode.SINGLE_WORD_TAJWEED)
    assertThat(com.quran.labs.androidquran.util.QuranSettings.getInstance(context).pageType).isEqualTo("tajweed")

    // 3. Verify TajweedRevealColorMatrix maps all 7 Tajweed colors to pure Madani black (#000000) at 0f,
    // intermediate color at 0.5f, and full Tajweed RGB at 1f (on both transparent & opaque bitmaps)
    val tajweedColors = listOf(
      0x04943C, // ghunnah
      0x046C94, // tafkheem
      0x049CE4, // qalqalah
      0xE5057A, // madd 4/5
      0xB1055F, // madd 6
      0xEE7A06, // madd 2/4/6
      0xC4A434  // madd 2
    )
    fun applyColorMatrix(matrix: FloatArray, rgb: Int, alpha: Int = 255): Int {
      val input = floatArrayOf(
        (rgb shr 16 and 0xFF).toFloat(),
        (rgb shr 8 and 0xFF).toFloat(),
        (rgb and 0xFF).toFloat(),
        alpha.toFloat()
      )
      val r = ((0..3).sumOf { matrix[it].toDouble() * input[it] } + matrix[4]).toInt().coerceIn(0, 255)
      val g = ((0..3).sumOf { matrix[5 + it].toDouble() * input[it] } + matrix[9]).toInt().coerceIn(0, 255)
      val b = ((0..3).sumOf { matrix[10 + it].toDouble() * input[it] } + matrix[14]).toInt().coerceIn(0, 255)
      val a = ((0..3).sumOf { matrix[15 + it].toDouble() * input[it] } + matrix[19]).toInt().coerceIn(0, 255)
      return android.graphics.Color.argb(a, r, g, b)
    }

    for (hasTransparent in listOf(true, false)) {
      val plainMat = TajweedRevealColorMatrix.create(
        revealFraction = 0f,
        hasTransparentBackground = hasTransparent
      )
      val halfMat = TajweedRevealColorMatrix.create(
        revealFraction = 0.5f,
        hasTransparentBackground = hasTransparent
      )
      val fullMat = TajweedRevealColorMatrix.create(
        revealFraction = 1f,
        hasTransparentBackground = hasTransparent
      )
      val boostedMat = TajweedRevealColorMatrix.create(
        revealFraction = 1f,
        hasTransparentBackground = hasTransparent,
        boostTajweedStrength = true
      )

      // Plain black ink without Tajweed rules stays exact #000000 even with boostTajweedStrength = true
      assertThat(applyColorMatrix(boostedMat, 0x000000) and 0xFFFFFF).isEqualTo(0x000000)

      fun chroma(rgb: Int): Int {
        val r = rgb shr 16 and 0xFF
        val g = rgb shr 8 and 0xFF
        val b = rgb and 0xFF
        return maxOf(r, g, b) - minOf(r, g, b)
      }

      for (tajweedRgb in tajweedColors) {
        val plainOut = applyColorMatrix(plainMat, tajweedRgb)
        assertThat(plainOut and 0xFFFFFF).isEqualTo(0x000000)

        val fullOut = applyColorMatrix(fullMat, tajweedRgb)
        assertThat(fullOut and 0xFFFFFF).isEqualTo(tajweedRgb)

        val boostedOut = applyColorMatrix(boostedMat, tajweedRgb) and 0xFFFFFF
        assertThat(chroma(boostedOut)).isAtLeast(chroma(tajweedRgb))

        if (hasTransparent) {
          val halfOut = applyColorMatrix(halfMat, tajweedRgb)
          assertThat(halfOut and 0xFFFFFF).isNotEqualTo(0x000000)
          assertThat(halfOut and 0xFFFFFF).isNotEqualTo(tajweedRgb)
        }
      }

      if (hasTransparent) {
        // Transparent paper alpha = 0 stays alpha = 0
        assertThat(android.graphics.Color.alpha(applyColorMatrix(plainMat, 0x000000, alpha = 0))).isEqualTo(0)
      } else {
        // Opaque white paper 0xFFFFFF stays 0xFFFFFF
        assertThat(applyColorMatrix(plainMat, 0xFFFFFF) and 0xFFFFFF).isEqualTo(0xFFFFFF)
      }
    }

    // 4. End-to-end Canvas pixel rendering verification on HighlightingImageView:
    // Create a 400x100 transparent Tajweed page bitmap with 3 words in Ayah 80:1 + medallion:
    // Word 1 (RTL right: x=300..380, y=20..80) colored Ghunnah Green (0xFF04943C)
    // Word 2 (RTL middle: x=205..285, y=20..80) colored Madd Red (0xFFE5057A)
    // Word 3 (RTL left: x=110..190, y=20..80) PLAIN BLACK (0xFF000000) — no Tajweed rule on this word!
    // Medallion (x=20..90, y=20..80)
    val renderPage = 80
    val srcBitmap = android.graphics.Bitmap.createBitmap(
      400,
      100,
      android.graphics.Bitmap.Config.ARGB_8888
    )
    val srcCanvas = android.graphics.Canvas(srcBitmap)
    val paintWord1 = android.graphics.Paint().apply { color = 0xFF04943C.toInt() }
    val paintWord2 = android.graphics.Paint().apply { color = 0xFFE5057A.toInt() }
    val paintWord3PlainBlack = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
    srcCanvas.drawRect(315f, 32f, 365f, 68f, paintWord1)
    srcCanvas.drawRect(220f, 32f, 270f, 68f, paintWord2)
    srcCanvas.drawRect(125f, 32f, 175f, 68f, paintWord3PlainBlack)

    val glyphsBuilder = com.quran.labs.androidquran.data.GlyphsBuilder()
    glyphsBuilder.append(80, 1, 1, 1, "word", RectF(300f, 20f, 380f, 80f))
    glyphsBuilder.append(80, 1, 2, 1, "word", RectF(205f, 20f, 285f, 80f))
    glyphsBuilder.append(80, 1, 3, 1, "word", RectF(110f, 20f, 190f, 80f))
    glyphsBuilder.append(80, 1, 4, 1, "end", RectF(20f, 20f, 90f, 80f))
    val pageGlyphs = com.quran.page.common.data.coordinates.PageGlyphsCoords(renderPage, glyphsBuilder.build())
    val ayahCoords = com.quran.page.common.data.AyahCoordinates(
      renderPage,
      mapOf("80:1" to listOf(AyahBounds(1, 1, RectF(20f, 20f, 380f, 80f)))),
      pageGlyphs
    )

    val imageView = com.quran.labs.androidquran.view.HighlightingImageView(context)
    imageView.scaleType = android.widget.ImageView.ScaleType.MATRIX
    imageView.imageMatrix = android.graphics.Matrix()
    imageView.setImageBitmap(srcBitmap)
    imageView.setAyahData(ayahCoords)
    imageView.measure(
      android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
      android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY)
    )
    imageView.layout(0, 0, 400, 100)

    fun renderViewToBitmap(): android.graphics.Bitmap {
      val out = android.graphics.Bitmap.createBitmap(400, 100, android.graphics.Bitmap.Config.ARGB_8888)
      val c = android.graphics.Canvas(out)
      imageView.draw(c)
      return out
    }

    QuranMemorizationManager.setHiddenModeEnabled(false, renderPage, context = context)
    QuranMemorizationManager.onProminentModeButtonPressed(
      RevealMode.SINGLE_WORD_TAJWEED,
      renderPage,
      context = context
    )

    // Initially: ALL 3 words keep black Madani text and NO word has a background highlight pill
    val initialRender = renderViewToBitmap()
    assertThat(initialRender.getPixel(340, 50)).isEqualTo(android.graphics.Color.BLACK)
    assertThat(initialRender.getPixel(245, 50)).isEqualTo(android.graphics.Color.BLACK)
    assertThat(initialRender.getPixel(150, 50)).isEqualTo(android.graphics.Color.BLACK)
    assertThat(android.graphics.Color.alpha(initialRender.getPixel(308, 50))).isEqualTo(0)
    assertThat(android.graphics.Color.alpha(initialRender.getPixel(212, 50))).isEqualTo(0)
    assertThat(android.graphics.Color.alpha(initialRender.getPixel(118, 50))).isEqualTo(0)

    // Press 1: Word 1 gets the spotlight background highlight pill + boosted Ghunnah Green ink!
    QuranMemorizationManager.onMemorizationPageTap(renderPage, context = context)
    imageView.advanceRevealAnimationForTest(
      com.quran.labs.androidquran.view.HighlightingImageView.WORD_REVEAL_DURATION_MS
    )
    val press1Render = renderViewToBitmap()
    val w1Ink = press1Render.getPixel(340, 50)
    assertThat(android.graphics.Color.green(w1Ink)).isGreaterThan(135)
    assertThat(android.graphics.Color.red(w1Ink)).isLessThan(15)
    // Background highlight pill around Word 1 (x=308, y=50) is opaque & bright, while Word 2 & 3 have none
    assertThat(android.graphics.Color.alpha(press1Render.getPixel(308, 50))).isGreaterThan(220)
    assertThat(android.graphics.Color.alpha(press1Render.getPixel(212, 50))).isEqualTo(0)
    assertThat(android.graphics.Color.alpha(press1Render.getPixel(118, 50))).isEqualTo(0)
    assertThat(press1Render.getPixel(245, 50)).isEqualTo(android.graphics.Color.BLACK)
    assertThat(press1Render.getPixel(150, 50)).isEqualTo(android.graphics.Color.BLACK)

    // Press 2: Word 1 reverts to unhighlighted Black; Word 2 gets spotlight highlight + boosted Madd Red!
    QuranMemorizationManager.onMemorizationPageTap(renderPage, context = context)
    imageView.advanceRevealAnimationForTest(
      com.quran.labs.androidquran.view.HighlightingImageView.WORD_REVEAL_DURATION_MS
    )
    val press2Render = renderViewToBitmap()
    assertThat(press2Render.getPixel(340, 50)).isEqualTo(android.graphics.Color.BLACK)
    assertThat(android.graphics.Color.alpha(press2Render.getPixel(308, 50))).isEqualTo(0)
    val w2Ink = press2Render.getPixel(245, 50)
    assertThat(android.graphics.Color.red(w2Ink)).isGreaterThan(210)
    assertThat(android.graphics.Color.green(w2Ink)).isLessThan(15)
    assertThat(android.graphics.Color.alpha(press2Render.getPixel(212, 50))).isGreaterThan(220)
    assertThat(press2Render.getPixel(150, 50)).isEqualTo(android.graphics.Color.BLACK)

    // Press 3: Word 3 has NO Tajweed colors (plain black ink), yet receives the spotlight background
    // highlight pill so the reader can clearly see Word 3 is the active pressed word!
    QuranMemorizationManager.onMemorizationPageTap(renderPage, context = context)
    imageView.advanceRevealAnimationForTest(
      com.quran.labs.androidquran.view.HighlightingImageView.WORD_REVEAL_DURATION_MS
    )
    val press3Render = renderViewToBitmap()
    assertThat(android.graphics.Color.alpha(press3Render.getPixel(212, 50))).isEqualTo(0)
    assertThat(press3Render.getPixel(150, 50)).isEqualTo(android.graphics.Color.BLACK)
    assertThat(android.graphics.Color.alpha(press3Render.getPixel(118, 50))).isGreaterThan(220)

    // Press 4 (after final word on page): turns to next page!
    val nextOutcome = QuranMemorizationManager.onMemorizationPageTap(renderPage, context = context)
    assertThat(nextOutcome).isEqualTo(MemorizationTapOutcome.TurnToNextPage(renderPage + 1))

    // 5. Verify Single Word Only mode (SINGLE_WORD) does NOT draw the spotlight background highlight pill
    QuranMemorizationManager.setHiddenModeEnabled(false, renderPage, context = context)
    QuranMemorizationManager.onProminentModeButtonPressed(
      RevealMode.SINGLE_WORD,
      renderPage,
      context = context
    )
    QuranMemorizationManager.onMemorizationPageTap(renderPage, context = context)
    imageView.advanceRevealAnimationForTest(
      com.quran.labs.androidquran.view.HighlightingImageView.WORD_REVEAL_DURATION_MS
    )
    val singleWordOnlyRender = renderViewToBitmap()
    assertThat(android.graphics.Color.alpha(singleWordOnlyRender.getPixel(308, 50))).isEqualTo(0)

    QuranMemorizationManager.removeListener(imageView)
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
  }
}

