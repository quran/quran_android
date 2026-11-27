package com.quran.labs.androidquran.extra.feature.linebyline.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.quran.data.source.PageContentType
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.extra.feature.linebyline.model.EmptyPageInfo
import com.quran.labs.androidquran.extra.feature.linebyline.model.PageInfo
import com.quran.labs.androidquran.extra.feature.linebyline.model.SidelineDirection
import com.quran.labs.androidquran.extra.feature.linebyline.model.SidelineModel
import com.quran.labs.androidquran.extra.feature.linebyline.model.SidelinesPosition
import com.quran.mobile.linebyline.data.dao.AyahHighlight
import com.quran.mobile.linebyline.ui.renderer.calculator.Measurements
import com.quran.mobile.linebyline.ui.renderer.calculator.PageCalculator
import com.quran.mobile.linebyline.ui.renderer.calculator.QuranPageCalculator
import com.quran.mobile.linebyline.ui.renderer.calculator.QuranScrollablePageCalculator
import com.quran.mobile.linebyline.ui.renderer.calculator.SidelinesWrapperCalculator
import kotlinx.collections.immutable.persistentListOf
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val BOOKMARK_INSET_RATIO = 1f / 358f
private const val BOOKMARK_BOTTOM_OFFSET_RATIO = 7f / 358f
private const val BOOKMARK_THICKNESS_RATIO = 4.5f / 358f

internal class QuranLineByLineCanvasView(
  context: Context,
  private var pageNumber: Int,
  private val pageContentType: PageContentType,
  private val isScrollable: Boolean,
  private val ayahNumberFormatter: NumberFormat,
  private val onClick: () -> Unit,
  private val onPagePositioned: (Float, Float, Int, Int) -> Unit,
  private val onSelectionStart: (Float, Float) -> Unit,
  private val onSelectionModified: (Float, Float) -> Unit,
  private val onSelectionEnd: () -> Unit
) : View(context) {

  private var pageInfo: PageInfo = EmptyPageInfo
  private var suraHeaderBitmap: Bitmap? = null

  private val density = resources.displayMetrics.density
  private var nonScrollableCalculator: PageCalculator? = null
  private var nonScrollableSidelines: Boolean? = null
  private var scrollableCalculator: PageCalculator? = null
  private var scrollableSidelines: Boolean? = null

  // Cached measurements & layout coordinates
  private var lastMeasurements: Measurements? = null
  private var pageLeft = 0
  private var pageTop = 0
  private var headerLeft = 0
  private var headerTop = 0
  private var footerTop = 0
  private var sidelinesLeft = 0
  private var extraVerticalPadding = 0
  private var portraitTopClearance = (48f * density).roundToInt()
  private var portraitBottomClearance = 0
  private var hasResolvedInsets = false

  // Window position deduplication
  private val windowLoc = IntArray(2)
  private var lastReportedWinX = Float.NaN
  private var lastReportedWinY = Float.NaN
  private var lastReportedWidth = -1
  private var lastReportedHeight = -1

  // Pre-allocated paints & geometry objects (zero allocations in onDraw)
  private val bgGradientPaint = Paint()
  private var cachedGradWidth = -1
  private var cachedGradStartWithWidth: Boolean? = null

  private val linePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
  private val argbLinePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
  private val compositedPagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
  private var cachedLineColor: Int = Int.MIN_VALUE

  private val highlightPaint = Paint()
  private val headerFooterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
  }

  private val dstRect = Rect()
  private val dstRectF = RectF()
  private val pillPath = Path()
  private var sortedSidelines: List<SidelineModel> = emptyList()
  private var sidelineLocationsBuffer: FloatArray = FloatArray(0)

  // Touch & long-press selection state
  private var isSelecting = false
  private var lastDragX = 0f
  private var lastDragY = 0f

  private val gestureDetector = GestureDetector(
    context,
    object : GestureDetector.SimpleOnGestureListener() {
      override fun onDown(e: MotionEvent): Boolean = true

      override fun onSingleTapUp(e: MotionEvent): Boolean {
        onClick()
        return true
      }

      override fun onLongPress(e: MotionEvent) {
        isSelecting = true
        lastDragX = e.x
        lastDragY = e.y
        parent?.requestDisallowInterceptTouchEvent(true)
        onSelectionStart(e.x - pageLeft, e.y - pageTop)
      }
    }
  )

  init {
    isClickable = true
    isFocusable = false
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
      val bars = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      hasResolvedInsets = true
      if (applyResolvedBarsInsets(bars.top, bars.bottom) && isLaidOut) {
        requestLayout()
      }
      insets
    }
  }

  private fun applyResolvedBarsInsets(top: Int, bottom: Int): Boolean {
    return if (isScrollable) {
      val maxPad = max(top, bottom)
      if (maxPad > extraVerticalPadding) {
        extraVerticalPadding = maxPad
        true
      } else {
        false
      }
    } else {
      val pillClearance = (48f * density).roundToInt()
      val desiredTop = top + pillClearance
      val desiredBottom = bottom
      if (desiredTop != portraitTopClearance || desiredBottom != portraitBottomClearance) {
        portraitTopClearance = desiredTop
        portraitBottomClearance = desiredBottom
        true
      } else {
        false
      }
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (!hasResolvedInsets) {
      val rootInsets = ViewCompat.getRootWindowInsets(this)
      if (rootInsets != null) {
        val bars = rootInsets.getInsets(
          WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        hasResolvedInsets = true
        applyResolvedBarsInsets(bars.top, bars.bottom)
      } else if (portraitTopClearance == 0 && portraitBottomClearance == 0) {
        ViewCompat.requestApplyInsets(this)
      }
    }
  }

  fun setPageInfo(info: PageInfo, headerBitmap: Bitmap? = null) {
    val prevShowSidelines = pageInfo.showSidelines
    val prevSidelines = pageInfo.sidelines
    val prevHadBitmap = pageInfo.compositedPageBitmap != null
    pageInfo = info
    suraHeaderBitmap = headerBitmap
    if (prevSidelines !== info.sidelines) {
      sortedSidelines = sortSidelinesIfNeeded(info.sidelines)
      if (sidelineLocationsBuffer.size < sortedSidelines.size * 2) {
        sidelineLocationsBuffer = FloatArray(sortedSidelines.size * 2)
      }
    }
    val nowHasBitmap = info.compositedPageBitmap != null
    if (shouldRequestLayoutOnPageInfoUpdate(
        isScrollable = isScrollable,
        hasMeasurements = lastMeasurements != null,
        prevShowSidelines = prevShowSidelines,
        nextShowSidelines = info.showSidelines,
        prevHadBitmap = prevHadBitmap,
        nowHasBitmap = nowHasBitmap
      )
    ) {
      requestLayout()
    } else if (!prevHadBitmap && nowHasBitmap && width > 0 && height > 0) {
      computePlacements(width, height)
      reportPagePositionIfNeeded()
    }
    invalidate()
  }

  fun clearPageInfo() {
    pageInfo = EmptyPageInfo
    suraHeaderBitmap = null
    sortedSidelines = emptyList()
  }

  fun rebindPage(newPageNumber: Int) {
    pageNumber = newPageNumber
    pageInfo = EmptyPageInfo
    suraHeaderBitmap = null
    sortedSidelines = emptyList()
    cachedGradWidth = -1
    cachedGradStartWithWidth = null
    isSelecting = false
    lastReportedWinX = Float.NaN
    lastReportedWinY = Float.NaN
    lastReportedWidth = -1
    lastReportedHeight = -1
  }

  fun clearCompositedBitmap() {
    if (pageInfo.compositedPageBitmap != null || pageInfo.sidelines.isNotEmpty()) {
      pageInfo = pageInfo.copy(
        compositedPageBitmap = null,
        sidelines = persistentListOf()
      )
      sortedSidelines = emptyList()
      invalidate()
    }
  }

  fun updateSidelineAndHeaderOnly(headerBitmap: Bitmap?) {
    if (suraHeaderBitmap !== headerBitmap) {
      suraHeaderBitmap = headerBitmap
      invalidate()
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val handled = gestureDetector.onTouchEvent(event)
    if (isSelecting) {
      when (event.actionMasked) {
        MotionEvent.ACTION_MOVE -> {
          val dx = event.x - lastDragX
          val dy = event.y - lastDragY
          lastDragX = event.x
          lastDragY = event.y
          onSelectionModified(dx, dy)
          return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          isSelecting = false
          parent?.requestDisallowInterceptTouchEvent(false)
          onSelectionEnd()
          return true
        }
      }
      return true
    }
    return handled || super.onTouchEvent(event)
  }

  private fun calculatorFor(showSidelines: Boolean): PageCalculator {
    return if (isScrollable) {
      val cached = scrollableCalculator
      if (cached != null && scrollableSidelines == showSidelines) {
        cached
      } else {
        val inner = QuranScrollablePageCalculator(density)
        val created = if (showSidelines) SidelinesWrapperCalculator(inner) else inner
        scrollableCalculator = created
        scrollableSidelines = showSidelines
        created
      }
    } else {
      val cached = nonScrollableCalculator
      if (cached != null && nonScrollableSidelines == showSidelines) {
        cached
      } else {
        val inner = QuranPageCalculator()
        val created = if (showSidelines) SidelinesWrapperCalculator(inner) else inner
        nonScrollableCalculator = created
        nonScrollableSidelines = showSidelines
        created
      }
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = MeasureSpec.getSize(widthMeasureSpec)
    val specHeight = MeasureSpec.getSize(heightMeasureSpec)
    val showSidelines = pageInfo.showSidelines
    val calc = calculatorFor(showSidelines)
    if (!isScrollable) {
      val availHeight = (specHeight - portraitTopClearance - portraitBottomClearance)
        .coerceAtLeast((specHeight * 0.65f).roundToInt())
      val m = calc.calculate(width, availHeight)
      lastMeasurements = m
      setMeasuredDimension(width, specHeight)
    } else {
      val screenHeight = resources.displayMetrics.heightPixels
      val maxHeight = if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED && specHeight > 0) {
        specHeight
      } else {
        screenHeight
      }
      val m = calc.calculate(width, maxHeight)
      lastMeasurements = m
      val contentInset = (0.5 * m.headerFooterHeight).toInt()
      val totalHeight = 2 * extraVerticalPadding + contentInset + m.headerFooterHeight + m.quranImageHeight + m.headerFooterHeight
      setMeasuredDimension(width, totalHeight)
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    computePlacements(right - left, bottom - top)
    reportPagePositionIfNeeded()
  }

  private fun computePlacements(viewWidth: Int, viewHeight: Int) {
    val m = lastMeasurements ?: return
    val showSidelines = pageInfo.showSidelines
    val page = if (pageInfo.page > 0) pageInfo.page else pageNumber
    val nonSidelinesWidth = viewWidth - m.sidelinesWidth
    val sidelinesStartDelta = if (showSidelines && page % 2 == 1) m.sidelinesWidth else 0

    if (!isScrollable) {
      val availHeight = (viewHeight - portraitTopClearance - portraitBottomClearance)
        .coerceAtLeast((viewHeight * 0.65f).roundToInt())
      val centeredTop = portraitTopClearance +
        (availHeight - m.headerFooterHeight - m.quranImageHeight - m.headerFooterHeight) / 2
      val hTop = max(portraitTopClearance, centeredTop)
      headerTop = hTop
      headerLeft = sidelinesStartDelta + (nonSidelinesWidth - m.headerFooterWidth) / 2
      pageTop = hTop + m.headerFooterHeight
      pageLeft = sidelinesStartDelta + (nonSidelinesWidth - m.quranImageWidth) / 2
      footerTop = pageTop + m.quranImageHeight
    } else {
      val contentInset = (0.5 * m.headerFooterHeight).toInt()
      headerTop = extraVerticalPadding
      headerLeft = sidelinesStartDelta + (nonSidelinesWidth - m.headerFooterWidth) / 2
      pageTop = m.headerFooterHeight + extraVerticalPadding
      pageLeft = sidelinesStartDelta + (nonSidelinesWidth - m.quranImageWidth) / 2
      footerTop = viewHeight - (contentInset + m.headerFooterHeight) - extraVerticalPadding
    }
    sidelinesLeft = if (sidelinesStartDelta > 0) 0 else viewWidth - m.sidelinesWidth
  }

  fun reportPagePositionIfNeeded() {
    val m = lastMeasurements ?: return
    if (m.quranImageWidth <= 0 || m.quranImageHeight <= 0) return
    getLocationInWindow(windowLoc)
    val x = windowLoc[0] + pageLeft.toFloat()
    val y = windowLoc[1] + pageTop.toFloat()
    if (x != lastReportedWinX || y != lastReportedWinY ||
      m.quranImageWidth != lastReportedWidth || m.quranImageHeight != lastReportedHeight
    ) {
      lastReportedWinX = x
      lastReportedWinY = y
      lastReportedWidth = m.quranImageWidth
      lastReportedHeight = m.quranImageHeight
      onPagePositioned(x, y, m.quranImageWidth, m.quranImageHeight)
    }
  }

  override fun onDraw(canvas: Canvas) {
    val viewWidth = width
    val viewHeight = height
    if (viewWidth <= 0 || viewHeight <= 0) return

    val info = pageInfo
    val displaySettings = info.displaySettings
    val systemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
    val isNightMode = displaySettings.isNightMode(systemNight)

    // 0. Draw page background
    if (isNightMode) {
      canvas.drawColor(AppThemeStyle.current.darkMushafBackgroundArgb(displaySettings.nightModeBackgroundBrightness))
    } else {
      val dayBg = displaySettings.dayBackgroundColor
      if (dayBg != null) {
        canvas.drawColor(dayBg)
      } else {
        val target = info.skippedPageCount % 2
        val startWithWidth = info.page % 2 == target
        if (cachedGradWidth != viewWidth || cachedGradStartWithWidth != startWithWidth) {
          cachedGradWidth = viewWidth
          cachedGradStartWithWidth = startWithWidth
          val x0 = if (startWithWidth) viewWidth.toFloat() else 0f
          val x1 = if (startWithWidth) 0f else viewWidth.toFloat()
          bgGradientPaint.shader = LinearGradient(
            x0, 0f, x1, 0f,
            GRADIENT_COLORS,
            GRADIENT_STOPS,
            Shader.TileMode.REPEAT
          )
        }
        canvas.drawRect(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat(), bgGradientPaint)
      }
    }

    val m = lastMeasurements ?: return
    if (m.quranImageWidth <= 0 || m.quranImageHeight <= 0) return

    val overlayColor = if (isNightMode) OVERLAY_COLOR_NIGHT else OVERLAY_COLOR_DAY

    // 1. Header & Footer
    if (displaySettings.showHeaderFooter && info.page > 0) {
      val headerWidthDp = m.headerFooterWidth / density
      val spValue = min(headerWidthDp, 480f) * 0.0345f
      headerFooterPaint.textSize = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        spValue,
        resources.displayMetrics
      )
      headerFooterPaint.color = overlayColor
      val fm = headerFooterPaint.fontMetrics
      val baselineOffset = (m.headerFooterHeight - (fm.descent - fm.ascent)) * 0.5f - fm.ascent

      // Header: suraText on left, juzAreaText on right (pinned LTR positions like QuranHeaderFooter)
      val hBaseline = headerTop + baselineOffset
      headerFooterPaint.textAlign = Paint.Align.LEFT
      canvas.drawText(info.displayText.suraText, headerLeft.toFloat(), hBaseline, headerFooterPaint)
      headerFooterPaint.textAlign = Paint.Align.RIGHT
      canvas.drawText(
        info.displayText.juzAreaText,
        (headerLeft + m.headerFooterWidth).toFloat(),
        hBaseline,
        headerFooterPaint
      )

      // Footer: even page on left, odd page on right
      val fBaseline = footerTop + baselineOffset
      if (info.page % 2 == 0) {
        headerFooterPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(info.displayText.localizedPageText, headerLeft.toFloat(), fBaseline, headerFooterPaint)
      } else {
        headerFooterPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
          info.displayText.localizedPageText,
          (headerLeft + m.headerFooterWidth).toFloat(),
          fBaseline,
          headerFooterPaint
        )
      }
    }

    val compositedBmp = info.compositedPageBitmap
    if (compositedBmp == null || compositedBmp.isRecycled) return

    val lineConfig = pageContentType as PageContentType.Line
    val ratio = lineConfig.ratio

    val totalWidth = m.quranImageWidth.toFloat()
    val totalHeight = m.quranImageHeight.toFloat()
    val totalWidthInt = m.quranImageWidth
    val totalHeightInt = m.quranImageHeight
    val lineHeight = totalWidth * ratio
    val lastLineIndex = 14f
    val lineHeightWithoutOverlap = (totalHeight - lineHeight) / lastLineIndex
    val highlightYStart = (lineHeight - lineHeightWithoutOverlap) / 2f

    val lineColor = if (isNightMode) {
      Color.argb(displaySettings.textBrightness.coerceIn(0, 255), 255, 255, 255)
    } else {
      Color.BLACK
    }
    if (cachedLineColor != lineColor) {
      cachedLineColor = lineColor
      linePaint.color = lineColor
      linePaint.colorFilter = null
      argbLinePaint.color = lineColor
      argbLinePaint.colorFilter = PorterDuffColorFilter(lineColor, PorterDuff.Mode.SRC_IN)
    }

    val saveCount = canvas.save()
    canvas.translate(pageLeft.toFloat(), pageTop.toFloat())

    // 2. Under-text highlights
    val highlights = info.ayahHighlights
    for (i in 0 until highlights.size) {
      val group = highlights[i]
      if (!group.highlightType.isUnderText) continue
      highlightPaint.color = group.highlightType.paintColor(isNightMode).toArgb()
      val items = group.ayahHighlights
      for (j in 0 until items.size) {
        drawPageHighlight(canvas, items[j], totalWidth, lineHeightWithoutOverlap, highlightYStart)
      }
    }

    // 3. Reading bookmark underlines
    val readingBookmarks = info.readingBookmarks
    for (i in 0 until readingBookmarks.size) {
      val rb = readingBookmarks[i]
      val slots = rb.slots
      val items = rb.ayahHighlights
      for (j in 0 until items.size) {
        drawReadingBookmarkUnderline(
          canvas = canvas,
          highlight = items[j],
          colorsCount = slots.size,
          colorAt = { idx -> slots[idx].paintColor(isNightMode).toArgb() },
          totalWidth = totalWidth,
          lineHeightWithoutOverlap = lineHeightWithoutOverlap,
          yStart = highlightYStart
        )
      }
    }

    // 4. Single composited HARDWARE page bitmap (lines + dividers + sura headers + ayah markers)
    dstRect.set(0, 0, totalWidthInt, totalHeightInt)
    canvas.drawBitmap(compositedBmp, null, dstRect, compositedPagePaint)

    // 5. Overlay highlights (selection / audio / bookmark)
    for (i in 0 until highlights.size) {
      val group = highlights[i]
      if (group.highlightType.isUnderText) continue
      highlightPaint.color = group.highlightType.paintColor(isNightMode).toArgb()
      val items = group.ayahHighlights
      for (j in 0 until items.size) {
        drawPageHighlight(canvas, items[j], totalWidth, lineHeightWithoutOverlap, highlightYStart)
      }
    }

    canvas.restoreToCount(saveCount)

    // 6. Sidelines
    if (info.showSidelines && sortedSidelines.isNotEmpty() && m.sidelinesWidth > 0) {
      val sideSave = canvas.save()
      canvas.translate(sidelinesLeft.toFloat(), pageTop.toFloat())
      drawSidelines(
        canvas = canvas,
        sidelines = sortedSidelines,
        totalWidth = m.sidelinesWidth.toFloat(),
        totalHeight = totalHeight,
        quranImageLineHeight = lineConfig.lineHeight,
        position = if (info.page % 2 == 0) SidelinesPosition.RIGHT else SidelinesPosition.LEFT
      )
      canvas.restoreToCount(sideSave)
    }
  }

  private fun drawPageHighlight(
    canvas: Canvas,
    highlight: AyahHighlight,
    totalWidth: Float,
    lineHeightWithoutOverlap: Float,
    yStart: Float
  ) {
    val highlightWidth = ceil((highlight.right - highlight.left) * totalWidth)
    val y = yStart + lineHeightWithoutOverlap * highlight.lineId
    val x = highlight.left * totalWidth
    canvas.drawRect(x, y, x + highlightWidth, y + lineHeightWithoutOverlap, highlightPaint)
  }

  private inline fun drawReadingBookmarkUnderline(
    canvas: Canvas,
    highlight: AyahHighlight,
    colorsCount: Int,
    colorAt: (Int) -> Int,
    totalWidth: Float,
    lineHeightWithoutOverlap: Float,
    yStart: Float
  ) {
    if (colorsCount <= 0) return
    val lineBottom = yStart + lineHeightWithoutOverlap * (highlight.lineId + 1)
    val inset = BOOKMARK_INSET_RATIO * totalWidth
    val thickness = BOOKMARK_THICKNESS_RATIO * totalWidth
    val x = highlight.left * totalWidth + inset
    val y = lineBottom - BOOKMARK_BOTTOM_OFFSET_RATIO * totalWidth
    val underlineWidth = (highlight.right - highlight.left) * totalWidth - 2f * inset
    val radius = thickness / 2f

    if (colorsCount == 1) {
      highlightPaint.color = colorAt(0)
      dstRectF.set(x, y, x + underlineWidth, y + thickness)
      canvas.drawRoundRect(dstRectF, radius, radius, highlightPaint)
    } else {
      pillPath.reset()
      dstRectF.set(x, y, x + underlineWidth, y + thickness)
      pillPath.addRoundRect(dstRectF, radius, radius, Path.Direction.CW)
      val save = canvas.save()
      canvas.clipPath(pillPath)
      val segmentWidth = underlineWidth / colorsCount
      for (index in 0 until colorsCount) {
        highlightPaint.color = colorAt(index)
        val segmentStart = x + underlineWidth - segmentWidth * (index + 1)
        canvas.drawRect(segmentStart, y, segmentStart + segmentWidth, y + thickness, highlightPaint)
      }
      canvas.restoreToCount(save)
    }
  }

  private fun drawSidelines(
    canvas: Canvas,
    sidelines: List<SidelineModel>,
    totalWidth: Float,
    totalHeight: Float,
    quranImageLineHeight: Int,
    position: SidelinesPosition
  ) {
    val lineHeight = totalHeight / 15f
    val count = sidelines.size
    for (i in 0 until count) {
      val item = sidelines[i]
      val bmp = item.bitmap
      val targetLineTop = lineHeight * (item.targetLine - 1)
      val y = if (item.direction == SidelineDirection.UP) {
        val potentialY = targetLineTop + lineHeight
        max(0f, potentialY - bmp.height)
      } else {
        targetLineTop
      }
      sidelineLocationsBuffer[i * 2] = y
      sidelineLocationsBuffer[i * 2 + 1] = y + bmp.height
    }

    for (index in 0 until count) {
      val model = sidelines[index]
      val bmp = model.bitmap
      if (bmp.isRecycled) continue
      val locStart = sidelineLocationsBuffer[index * 2]
      val locEnd = sidelineLocationsBuffer[index * 2 + 1]
      val hasNextOverlap = index + 1 < count && sidelineLocationsBuffer[(index + 1) * 2] < locEnd

      val targetW: Int
      val targetH: Int
      if (hasNextOverlap) {
        val originalLinesSpanned = ceil(1f * bmp.height / (1.35 * quranImageLineHeight)).toInt()
        val nextUsedLine = if (model.direction == SidelineDirection.UP) {
          var prev = index - 1
          while (prev >= 0 && sidelines[prev].targetLine == model.targetLine) {
            prev--
          }
          (if (prev >= 0) sidelines[prev].targetLine else 1) - 1
        } else {
          var next = index + 1
          while (next < count && sidelines[next].targetLine == model.targetLine) {
            next++
          }
          (if (next < count) sidelines[next].targetLine else 16) - 1
        }
        val targetLinesToSpan = max(originalLinesSpanned, abs(nextUsedLine - model.targetLine))
        val targetHeight = targetLinesToSpan * lineHeight
        if (bmp.height - targetHeight < 25) {
          targetW = bmp.width
          targetH = bmp.height
        } else {
          targetW = ((targetHeight / bmp.height) * bmp.width).toInt()
          targetH = targetHeight.toInt()
        }
      } else if (bmp.width > totalWidth) {
        targetW = totalWidth.toInt()
        targetH = (totalWidth / bmp.width * bmp.height).toInt()
      } else {
        val originalLinesSpanned = ceil(1f * bmp.height / (1.35 * quranImageLineHeight)).toInt()
        val originalTargetHeight = originalLinesSpanned * lineHeight
        val targetHeight = abs(bmp.height + originalTargetHeight) / 2.0f
        targetW = ((targetHeight / bmp.height) * bmp.width).toInt()
        targetH = targetHeight.toInt()
      }

      val y = if (index + 1 < count && sidelineLocationsBuffer[(index + 1) * 2] < (locStart + targetH)) {
        val updatedY = locStart + targetH
        locStart - (updatedY - sidelineLocationsBuffer[(index + 1) * 2])
      } else if (locStart + targetH > totalHeight) {
        locStart - ((locStart + targetH) - totalHeight.toInt())
      } else {
        locStart
      }

      val deltaX = if (position == SidelinesPosition.LEFT) {
        (totalWidth - targetW).toInt()
      } else {
        0
      }

      val yi = y.toInt()
      dstRect.set(deltaX, yi, deltaX + targetW, yi + targetH)
      val sidePaint = if (bmp.config == Bitmap.Config.ALPHA_8) linePaint else argbLinePaint
      canvas.drawBitmap(bmp, null, dstRect, sidePaint)
    }
  }

  private fun sortSidelinesIfNeeded(sidelines: List<SidelineModel>): List<SidelineModel> {
    var isSorted = true
    for (i in 1 until sidelines.size) {
      if (sidelines[i].targetLine < sidelines[i - 1].targetLine) {
        isSorted = false
        break
      }
    }
    return if (isSorted) sidelines else sidelines.sortedBy { it.targetLine }
  }

  private companion object {
    private val GRADIENT_COLORS = intArrayOf(0xFFF0EADF.toInt(), 0xFFFFFEFA.toInt(), 0xFFF0EADF.toInt())
    private val GRADIENT_STOPS = floatArrayOf(0f, 0.46f, 1f)

    private const val OVERLAY_COLOR_NIGHT = 0xFF848A91.toInt()
    private const val OVERLAY_COLOR_DAY = 0xFF686E7D.toInt()
  }
}

internal fun shouldRequestLayoutOnPageInfoUpdate(
  isScrollable: Boolean,
  hasMeasurements: Boolean,
  prevShowSidelines: Boolean,
  nextShowSidelines: Boolean,
  prevHadBitmap: Boolean,
  nowHasBitmap: Boolean
): Boolean {
  if (prevShowSidelines != nextShowSidelines) return true
  if (!prevHadBitmap && nowHasBitmap) {
    return isScrollable || !hasMeasurements
  }
  return false
}
