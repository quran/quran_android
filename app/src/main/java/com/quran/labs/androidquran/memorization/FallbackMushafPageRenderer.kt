package com.quran.labs.androidquran.memorization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.quran.data.core.QuranInfo
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.data.model.SuraAyahIterator
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.AyahMarkerLocation
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.data.coordinates.GlyphCoords
import com.quran.page.common.data.coordinates.PageGlyphsCoords
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Provides fallback Mushaf page bitmap rendering and word/medallion glyph coordinates
 * when external Mushaf image zips or ayahinfo SQLite databases have not been downloaded yet.
 */
object FallbackMushafPageRenderer {

  private const val CANVAS_WIDTH = 1024
  private const val CANVAS_HEIGHT = 1656

  private val cachedPageCoords = ConcurrentHashMap<Int, PageCoordinates>()
  private val cachedAyahCoords = ConcurrentHashMap<Int, AyahCoordinates>()
  private val downloadedBitmapsByPage = ConcurrentHashMap<Int, Bitmap>()

  private var uthmaniTypeface: Typeface? = null

  private fun getTypeface(context: Context): Typeface {
    uthmaniTypeface?.let { return it }
    val tf = try {
      Typeface.createFromAsset(context.assets, "uthmanic_hafs_ver12.otf")
    } catch (_: Exception) {
      try {
        Typeface.createFromAsset(context.assets, "kitab.ttf")
      } catch (_: Exception) {
        Typeface.DEFAULT
      }
    }
    uthmaniTypeface = tf
    return tf
  }

  fun onBitmapLoadedFromDiskOrWeb(
    context: Context,
    quranInfo: QuranInfo,
    page: Int,
    bitmap: Bitmap
  ) {
    downloadedBitmapsByPage[page] = bitmap
    // If we don't have coordinates built for this downloaded bitmap yet, build them
    buildCoordinatesFromRealBitmap(context, quranInfo, page, bitmap)
  }

  @Synchronized
  fun renderFallbackPageBitmap(
    context: Context,
    quranInfo: QuranInfo,
    page: Int
  ): Bitmap {
    QuranMemorizationManager.ensureWordAlignmentLoaded(context)
    com.quran.labs.androidquran.kidsclassroom.MinshawiJuz30Repository.ensureLoaded(context)
    val bitmap = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val isTajweed = try {
      com.quran.labs.androidquran.util.QuranSettings.getInstance(context).pageType == "tajweed"
    } catch (_: Exception) {
      false
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      typeface = getTypeface(context)
      textSize = if (page <= 2) 52f else 42f
      textAlign = Paint.Align.RIGHT
    }

    val medallionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = if (isTajweed) Color.rgb(27, 94, 32) else Color.BLACK
      typeface = getTypeface(context)
      textSize = if (page <= 2) 46f else 38f
      textAlign = Paint.Align.CENTER
    }

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = if (isTajweed) Color.rgb(46, 125, 50) else Color.BLACK
      style = Paint.Style.STROKE
      strokeWidth = if (isTajweed) 5f else 3f
    }

    val pageBounds = RectF(64f, 96f, CANVAS_WIDTH - 64f, CANVAS_HEIGHT - 96f)
    canvas.drawRect(pageBounds, borderPaint)

    val innerPadding = 32f
    val contentLeft = pageBounds.left + innerPadding
    val contentRight = pageBounds.right - innerPadding
    val contentTop = pageBounds.top + innerPadding
    val contentBottom = pageBounds.bottom - innerPadding

    val ayahs = getAyahsOnPage(quranInfo, page)
    val glyphCoordsList = mutableListOf<GlyphCoords>()
    val ayahBoundsMap = LinkedHashMap<String, MutableList<AyahBounds>>()
    val verseMarkers = mutableListOf<AyahMarkerLocation>()

    // Gather tokens (each word + medallion) for all ayahs on the page
    data class TokenItem(
      val suraAyah: SuraAyah,
      val text: String,
      val glyphPosition: Int,
      val wordPosition: Int?, // null for AyahEndGlyph medallion
      val measuredWidth: Float
    )

    val allTokens = mutableListOf<TokenItem>()
    for (suraAyah in ayahs) {
      val words = getWordsForAyah(suraAyah)
      var glyphPos = 1
      for (i in words.indices) {
        val wordStr = words[i]
        val w = textPaint.measureText(wordStr) + 18f
        allTokens.add(
          TokenItem(
            suraAyah = suraAyah,
            text = wordStr,
            glyphPosition = glyphPos++,
            wordPosition = i + 1,
            measuredWidth = w
          )
        )
      }
      val medallionStr = "﴿${formatArabicDigits(suraAyah.ayah)}﴾"
      val mw = max(medallionPaint.measureText(medallionStr) + 20f, 58f)
      allTokens.add(
        TokenItem(
          suraAyah = suraAyah,
          text = medallionStr,
          glyphPosition = glyphPos,
          wordPosition = null,
          measuredWidth = mw
        )
      )
    }

    // Wrap tokens into RTL lines within [contentLeft .. contentRight]
    val maxLineWidth = contentRight - contentLeft
    val lines = mutableListOf<MutableList<TokenItem>>()
    var currentLine = mutableListOf<TokenItem>()
    var currentLineWidth = 0f

    for (token in allTokens) {
      if (currentLine.isNotEmpty() && currentLineWidth + token.measuredWidth > maxLineWidth) {
        lines.add(currentLine)
        currentLine = mutableListOf()
        currentLineWidth = 0f
      }
      currentLine.add(token)
      currentLineWidth += token.measuredWidth
    }
    if (currentLine.isNotEmpty()) {
      lines.add(currentLine)
    }

    val lineCount = lines.size.coerceAtLeast(1)
    val availableHeight = contentBottom - contentTop
    val lineSlotHeight = (availableHeight / lineCount.coerceAtLeast(if (page <= 2) 8 else 15))
      .coerceIn(76f, 130f)
    val startY = contentTop + 20f

    for (lineIdx in lines.indices) {
      val lineTokens = lines[lineIdx]
      val lineNumber = lineIdx + 1
      val lineTop = startY + lineIdx * lineSlotHeight
      val lineBottom = (lineTop + lineSlotHeight * 0.86f).coerceAtMost(contentBottom)
      val baselineY = lineTop + (lineBottom - lineTop) * 0.72f

      val naturalWidth = lineTokens.sumOf { it.measuredWidth.toDouble() }.toFloat()
      val extraSpace = (maxLineWidth - naturalWidth).coerceAtLeast(0f)
      val gapPerToken = if (lineTokens.size > 1 && lineIdx < lines.lastIndex) {
        (extraSpace / (lineTokens.size - 1)).coerceAtMost(28f)
      } else {
        8f
      }

      var cursorRight = contentRight
      for (token in lineTokens) {
        val tokenLeft = (cursorRight - token.measuredWidth).coerceAtLeast(contentLeft)
        val tokenRect = RectF(tokenLeft, lineTop, cursorRight, lineBottom)

        if (token.wordPosition != null) {
          // Draw Arabic word (with Tajweed color coding when Tajweed Mushaf is active)
          textPaint.color = if (isTajweed) getTajweedWordColor(token.text) else Color.BLACK
          canvas.drawText(token.text, cursorRight - 6f, baselineY, textPaint)
          glyphCoordsList.add(
            GlyphCoords(
              WordGlyph(token.suraAyah, token.glyphPosition, token.wordPosition),
              lineNumber,
              RectF(tokenRect)
            )
          )
        } else {
          // Draw End-of-Ayah Medallion ﴿١﴾
          val centerX = tokenRect.centerX()
          canvas.drawText(token.text, centerX, baselineY, medallionPaint)
          glyphCoordsList.add(
            GlyphCoords(
              AyahEndGlyph(token.suraAyah, token.glyphPosition),
              lineNumber,
              RectF(tokenRect)
            )
          )
          verseMarkers.add(
            AyahMarkerLocation(
              token.suraAyah.sura,
              token.suraAyah.ayah,
              centerX.roundToInt(),
              tokenRect.centerY().roundToInt()
            )
          )
        }

        val key = "${token.suraAyah.sura}:${token.suraAyah.ayah}"
        val boundsForAyah = ayahBoundsMap.getOrPut(key) { mutableListOf() }
        val bound = AyahBounds(
          lineNumber,
          token.glyphPosition,
          tokenRect.left.roundToInt(),
          tokenRect.top.roundToInt(),
          tokenRect.right.roundToInt(),
          tokenRect.bottom.roundToInt()
        )
        val lastBound = boundsForAyah.lastOrNull()
        if (lastBound != null && lastBound.line == lineNumber) {
          lastBound.engulf(bound)
        } else {
          boundsForAyah.add(bound)
        }

        cursorRight = tokenLeft - gapPerToken
      }
    }

    val pageGlyphs = PageGlyphsCoords(page, glyphCoordsList)
    val pageCoords = PageCoordinates(page, pageBounds, emptyList(), verseMarkers)
    val ayahCoords = AyahCoordinates(page, ayahBoundsMap, pageGlyphs)

    cachedPageCoords[page] = pageCoords
    cachedAyahCoords[page] = ayahCoords
    QuranMemorizationManager.registerPageGlyphs(page, pageGlyphs, ayahBoundsMap.keys)

    return bitmap
  }

  private fun buildCoordinatesFromRealBitmap(
    context: Context,
    quranInfo: QuranInfo,
    page: Int,
    bitmap: Bitmap
  ) {
    QuranMemorizationManager.ensureWordAlignmentLoaded(context)
    com.quran.labs.androidquran.kidsclassroom.MinshawiJuz30Repository.ensureLoaded(context)
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return

    val ayahs = getAyahsOnPage(quranInfo, page)
    if (ayahs.isEmpty()) return

    val pageBounds = RectF(
      width * 0.04f,
      height * 0.04f,
      width * 0.96f,
      height * 0.96f
    )

    // Detect horizontal text line bands on the bitmap, or fall back to standard Madani lines
    val detectedLineBands = detectTextLineBands(bitmap, page)
    val lineCount = detectedLineBands.size.coerceAtLeast(1)

    // Total words + 1 medallion per ayah across the page
    val ayahWordCounts = ayahs.map { it to QuranMemorizationManager.getWordCountForAyah(it).coerceAtLeast(1) }
    val totalUnits = ayahWordCounts.sumOf { it.second + 1 }.coerceAtLeast(1)

    val glyphCoordsList = mutableListOf<GlyphCoords>()
    val ayahBoundsMap = LinkedHashMap<String, MutableList<AyahBounds>>()
    val verseMarkers = mutableListOf<AyahMarkerLocation>()

    // Assign each ayah's words + medallion across the detected lines in RTL order
    data class UnitSpec(
      val suraAyah: SuraAyah,
      val glyphPosition: Int,
      val wordPosition: Int? // null for AyahEndGlyph
    )

    val allUnits = mutableListOf<UnitSpec>()
    for ((suraAyah, wordCount) in ayahWordCounts) {
      var pos = 1
      for (w in 1..wordCount) {
        allUnits.add(UnitSpec(suraAyah, pos++, w))
      }
      allUnits.add(UnitSpec(suraAyah, pos, null))
    }

    var unitIndex = 0
    for (lineIdx in 0 until lineCount) {
      val lineNum = lineIdx + 1
      val band = detectedLineBands[lineIdx]
      val remainingLines = lineCount - lineIdx
      val remainingUnits = allUnits.size - unitIndex
      if (remainingUnits <= 0) break

      val unitsOnLine = if (remainingLines == 1) {
        remainingUnits
      } else {
        (remainingUnits.toFloat() / remainingLines).roundToInt().coerceAtLeast(1)
      }

      val lineLeft = band.left
      val lineRight = band.right
      val lineTop = band.top
      val lineBottom = band.bottom
      val segWidth = (lineRight - lineLeft) / unitsOnLine.toFloat()

      for (u in 0 until unitsOnLine) {
        if (unitIndex >= allUnits.size) break
        val spec = allUnits[unitIndex++]
        val segRight = lineRight - u * segWidth
        val segLeft = if (u == unitsOnLine - 1) lineLeft else (lineRight - (u + 1) * segWidth)
        val rect = RectF(segLeft, lineTop, segRight, lineBottom)

        val glyph = if (spec.wordPosition != null) {
          WordGlyph(spec.suraAyah, spec.glyphPosition, spec.wordPosition)
        } else {
          verseMarkers.add(
            AyahMarkerLocation(
              spec.suraAyah.sura,
              spec.suraAyah.ayah,
              rect.centerX().roundToInt(),
              rect.centerY().roundToInt()
            )
          )
          AyahEndGlyph(spec.suraAyah, spec.glyphPosition)
        }
        glyphCoordsList.add(GlyphCoords(glyph, lineNum, rect))

        val key = "${spec.suraAyah.sura}:${spec.suraAyah.ayah}"
        val boundsForAyah = ayahBoundsMap.getOrPut(key) { mutableListOf() }
        val bound = AyahBounds(
          lineNum,
          spec.glyphPosition,
          rect.left.roundToInt(),
          rect.top.roundToInt(),
          rect.right.roundToInt(),
          rect.bottom.roundToInt()
        )
        val last = boundsForAyah.lastOrNull()
        if (last != null && last.line == lineNum) {
          last.engulf(bound)
        } else {
          boundsForAyah.add(bound)
        }
      }
    }

    val pageGlyphs = PageGlyphsCoords(page, glyphCoordsList)
    cachedPageCoords[page] = PageCoordinates(page, pageBounds, emptyList(), verseMarkers)
    cachedAyahCoords[page] = AyahCoordinates(page, ayahBoundsMap, pageGlyphs)
    QuranMemorizationManager.registerPageGlyphs(page, pageGlyphs, ayahBoundsMap.keys)
  }

  private fun detectTextLineBands(bitmap: Bitmap, page: Int): List<RectF> {
    val w = bitmap.width
    val h = bitmap.height
    val targetLines = if (page <= 2) 7 else 15
    val topMargin = if (page <= 2) h * 0.24f else h * 0.065f
    val bottomMargin = if (page <= 2) h * 0.78f else h * 0.935f
    val leftMargin = if (page <= 2) w * 0.14f else w * 0.065f
    val rightMargin = if (page <= 2) w * 0.86f else w * 0.935f

    val bandHeight = (bottomMargin - topMargin) / targetLines.toFloat()
    val bands = mutableListOf<RectF>()
    for (i in 0 until targetLines) {
      val top = topMargin + i * bandHeight
      val bottom = top + bandHeight * 0.92f
      bands.add(RectF(leftMargin, top, rightMargin, bottom))
    }
    return bands
  }

  fun getFallbackPageCoordinates(
    context: Context,
    quranInfo: QuranInfo,
    page: Int,
    wantPageBounds: Boolean
  ): PageCoordinates {
    cachedPageCoords[page]?.let { cached ->
      return if (wantPageBounds) cached else PageCoordinates(
        page,
        RectF(),
        cached.suraHeaders,
        cached.ayahMarkers
      )
    }
    val bmp = downloadedBitmapsByPage[page]
    if (bmp != null) {
      buildCoordinatesFromRealBitmap(context, quranInfo, page, bmp)
    } else {
      renderFallbackPageBitmap(context, quranInfo, page)
    }
    val coords = cachedPageCoords[page] ?: PageCoordinates(
      page,
      RectF(64f, 96f, CANVAS_WIDTH - 64f, CANVAS_HEIGHT - 96f),
      emptyList(),
      emptyList()
    )
    return if (wantPageBounds) coords else PageCoordinates(
      page,
      RectF(),
      coords.suraHeaders,
      coords.ayahMarkers
    )
  }

  fun getFallbackAyahCoordinates(
    context: Context,
    quranInfo: QuranInfo,
    page: Int
  ): AyahCoordinates {
    cachedAyahCoords[page]?.let { return it }
    val bmp = downloadedBitmapsByPage[page]
    if (bmp != null) {
      buildCoordinatesFromRealBitmap(context, quranInfo, page, bmp)
    } else {
      renderFallbackPageBitmap(context, quranInfo, page)
    }
    return cachedAyahCoords[page] ?: AyahCoordinates(page, emptyMap(), null)
  }

  private fun getAyahsOnPage(quranInfo: QuranInfo, page: Int): List<SuraAyah> {
    if (!quranInfo.isValidPage(page)) return emptyList()
    val bounds = quranInfo.getPageBounds(page)
    val start = SuraAyah(bounds[0], bounds[1])
    val end = SuraAyah(bounds[2], bounds[3])
    val iterator = SuraAyahIterator(quranInfo, start, end)
    val result = mutableListOf<SuraAyah>()
    while (iterator.next()) {
      result.add(SuraAyah(iterator.sura, iterator.ayah))
    }
    return result
  }

  fun getWordsForAyah(suraAyah: SuraAyah): List<String> {
    BUNDLED_VERSES[suraAyah]?.let { text ->
      return text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
    com.quran.labs.androidquran.kidsclassroom.MinshawiJuz30Repository
      .peekVerse(suraAyah)
      ?.allUthmaniWords
      ?.takeIf { it.isNotEmpty() }
      ?.let { return it }
    val wordCount = QuranMemorizationManager.getWordCountForAyah(suraAyah).coerceAtLeast(1)
    val sampleWords = COMMON_QURANIC_WORDS
    return List(wordCount) { idx ->
      sampleWords[(suraAyah.sura * 13 + suraAyah.ayah * 7 + idx) % sampleWords.size]
    }
  }

  fun formatArabicDigits(number: Int): String {
    val arabicZero = '\u0660'
    return number.toString().map { ch ->
      if (ch in '0'..'9') arabicZero + (ch - '0') else ch
    }.joinToString("")
  }

  fun getVerseTextWithMedallion(suraAyah: SuraAyah): String {
    val words = getWordsForAyah(suraAyah).joinToString(" ")
    return "$words ﴿${formatArabicDigits(suraAyah.ayah)}﴾"
  }

  private fun getTajweedWordColor(word: String): Int {
    // Standard Dar Al-Ma'rifah Tajweed Mushaf color coding:
    // - Red (#C62828): Madd Lazim / Wajib / Munfasil (آ, ٱلضَّالِّينَ, أولئك, بما أنزل)
    // - Green (#2E7D32): Ghunnah / Ikhfa (نّ, مّ, تنوين, أنعمت, ينفقون, من)
    // - Blue (#1565C0): Qalqalah (قطب جد with sukoon or word-final: أحد, الصمد, الفلق, وقب, عقد, حسد)
    return when {
      word.contains("آ") || word.contains("أُولَٰئِكَ") || word.contains("الضَّالِّينَ") ||
          word == "الم" || word.contains("بِمَا") || word.contains("لَا") -> Color.rgb(198, 40, 40)
      word.contains("نّ") || word.contains("مّ") || word.contains("نْ") ||
          word.contains("نف") || word.contains("نز") || word.contains("ً") ||
          word.contains("ٍ") || word.contains("ٌ") || word == "مِن" || word == "عَن" -> Color.rgb(46, 125, 50)
      word.endsWith("دٌ") || word.endsWith("دُ") || word.endsWith("دْ") ||
          word.endsWith("قِ") || word.endsWith("بَ") || word.endsWith("دِ") -> Color.rgb(21, 101, 192)
      else -> Color.BLACK
    }
  }

  private val COMMON_QURANIC_WORDS = listOf(
    "بِسْمِ", "اللَّهِ", "الرَّحْمَٰنِ", "الرَّحِيمِ",
    "الْحَمْدُ", "لِلَّهِ", "رَبِّ", "الْعَالَمِينَ",
    "الَّذِينَ", "يُؤْمِنُونَ", "بِالْغَيْبِ", "وَيُقِيمُونَ",
    "الصَّلَاةَ", "وَمِمَّا", "رَزَقْنَاهُمْ", "يُنْفِقُونَ",
    "أُولَٰئِكَ", "عَلَىٰ", "هُدًى", "مِنْ", "رَبِّهِمْ", "وَأُولَٰئِكَ", "هُمُ", "الْمُفْلِحُونَ"
  )

  private val BUNDLED_VERSES: Map<SuraAyah, String> = mapOf(
    // Page 1: Al-Fatihah
    SuraAyah(1, 1) to "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
    SuraAyah(1, 2) to "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
    SuraAyah(1, 3) to "الرَّحْمَٰنِ الرَّحِيمِ",
    SuraAyah(1, 4) to "مَالِكِ يَوْمِ الدِّينِ",
    SuraAyah(1, 5) to "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ",
    SuraAyah(1, 6) to "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ",
    SuraAyah(1, 7) to "صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ",
    // Page 2: Al-Baqarah 1-5
    SuraAyah(2, 1) to "الم",
    SuraAyah(2, 2) to "ذَٰلِكَ الْكِتَابُ لَا رَيْبَ فِيهِ هُدًى لِّلْمُتَّقِينَ",
    SuraAyah(2, 3) to "الَّذِينَ يُؤْمِنُونَ بِالْغَيْبِ وَيُقِيمُونَ الصَّلَاةَ وَمِمَّا رَزَقْنَاهُمْ يُنفِقُونَ",
    SuraAyah(2, 4) to "وَالَّذِينَ يُؤْمِنُونَ بِمَا أُنزِلَ إِلَيْكَ وَمَا أُنزِلَ مِن قَبْلِكَ وَبِالْآخِرَةِ هُمْ يُوقِنُونَ",
    SuraAyah(2, 5) to "أُولَٰئِكَ عَلَىٰ هُدًى مِّن رَّبِّهِمْ وَأُولَٰئِكَ هُمُ الْمُفْلِحُونَ",
    // Page 604: Al-Ikhlas, Al-Falaq, An-Nas
    SuraAyah(112, 1) to "قُلْ هُوَ اللَّهُ أَحَدٌ",
    SuraAyah(112, 2) to "اللَّهُ الصَّمَدُ",
    SuraAyah(112, 3) to "لَمْ يَلِدْ وَلَمْ يُولَدْ",
    SuraAyah(112, 4) to "وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ",
    SuraAyah(113, 1) to "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ",
    SuraAyah(113, 2) to "مِن شَرِّ مَا خَلَقَ",
    SuraAyah(113, 3) to "وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ",
    SuraAyah(113, 4) to "وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ",
    SuraAyah(113, 5) to "وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ",
    SuraAyah(114, 1) to "قُلْ أَعُوذُ بِرَبِّ النَّاسِ",
    SuraAyah(114, 2) to "مَلِكِ النَّاسِ",
    SuraAyah(114, 3) to "إِلَٰهِ النَّاسِ",
    SuraAyah(114, 4) to "مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ",
    SuraAyah(114, 5) to "الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ",
    SuraAyah(114, 6) to "مِنَ الْجِنَّةِ وَالنَّاسِ"
  )
}
