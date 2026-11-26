package com.quran.labs.androidquran.hybrid.search

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.ui.HybridArchitecturalIcons
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.hybrid.ui.NativeUthmaniTypefaceCache
import com.quran.labs.androidquran.hybrid.ui.RubElHizbView
import com.quran.labs.androidquran.util.QuranUtils
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

sealed interface HybridSearchRow {
  val stableKey: String
  val stableId: Long

  data class AyahCard(
    val hit: AyahSearchHit,
    val isBookmarked: Boolean
  ) : HybridSearchRow {
    override val stableKey: String = "ayah_${hit.sura}_${hit.ayah}_${hit.id}"
    override val stableId: Long =
      (1L shl 56) or
        ((hit.sura.toLong() and 0xFFL) shl 40) or
        ((hit.ayah.toLong() and 0xFFFFL) shl 24) or
        (hit.id and 0xFFFFFFL)
  }

  data class SuraCard(
    val hit: SuraSearchHit
  ) : HybridSearchRow {
    override val stableKey: String = "sura_${hit.sura}"
    override val stableId: Long = (2L shl 56) or hit.sura.toLong()
  }

  data class TafsirCard(
    val hit: TafsirSearchHit,
    val isBookmarked: Boolean
  ) : HybridSearchRow {
    override val stableKey: String = "tafsir_${hit.stableKey}"
    override val stableId: Long =
      (3L shl 56) or
        ((hit.translationId and 0xFFFFL) shl 40) or
        ((hit.sura.toLong() and 0xFFL) shl 32) or
        ((hit.ayah.toLong() and 0xFFFFL) shl 16) or
        (hit.occurrenceIndex.toLong() and 0xFFFFL)
  }

  data class ActionCard(
    val key: String,
    val message: String,
    val ctaLabel: String,
    val isTranslationCta: Boolean = false
  ) : HybridSearchRow {
    override val stableKey: String = "action_$key"
    override val stableId: Long = (4L shl 56) or (key.hashCode().toLong() and 0xFFFFFFFFL)
  }

  data object TafsirEmptyCard : HybridSearchRow {
    override val stableKey: String = "tafsir_empty"
    override val stableId: Long = 5L shl 56
  }

  data class EmptyMessageCard(
    val key: String,
    val title: String,
    val subtitle: String?
  ) : HybridSearchRow {
    override val stableKey: String = "empty_$key"
    override val stableId: Long = (6L shl 56) or (key.hashCode().toLong() and 0xFFFFFFFFL)
  }
}

class HybridSearchAdapter(
  private val onOpenPage: (page: Int, sura: Int, ayah: Int) -> Unit,
  private val onPlayAyah: (sura: Int, ayah: Int) -> Unit,
  private val onToggleBookmark: (sura: Int, ayah: Int, page: Int) -> Unit,
  private val onDownloadArabicDb: () -> Unit,
  private val onOpenTranslationManager: () -> Unit,
  private val staticLayoutProvider: ((sura: Int, ayah: Int) -> StaticLayout?)? = null,
  private val refLayoutProvider: ((sura: Int, ayah: Int) -> StaticLayout?)? = null,
  private val pagePillLayoutProvider: ((page: Int, textColorArgb: Int) -> StaticLayout?)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  private var palette: HybridViewPalette? = null
  private var rows: List<HybridSearchRow> = emptyList()
  private var cachedBookmarkedLabel: String? = null
  private var cachedUnbookmarkedLabel: String? = null

  private fun bookmarkLabel(context: Context, isBookmarked: Boolean): String {
    return if (isBookmarked) {
      cachedBookmarkedLabel ?: context.getString(R.string.hybrid_search_action_bookmarked).also {
        cachedBookmarkedLabel = it
      }
    } else {
      cachedUnbookmarkedLabel ?: context.getString(R.string.hybrid_search_action_bookmark).also {
        cachedUnbookmarkedLabel = it
      }
    }
  }

  init {
    setHasStableIds(true)
  }

  val currentList: List<HybridSearchRow>
    get() = rows

  fun getItem(position: Int): HybridSearchRow = rows[position]

  override fun getItemCount(): Int = rows.size

  override fun getItemId(position: Int): Long = rows[position].stableId

  fun submitList(newRows: List<HybridSearchRow>?) {
    val target = newRows ?: emptyList()
    val oldRows = rows
    if (oldRows === target) return
    rows = target

    if (oldRows.isEmpty()) {
      if (target.isNotEmpty()) {
        notifyItemRangeInserted(0, target.size)
      }
      return
    }
    if (target.isEmpty()) {
      notifyItemRangeRemoved(0, oldRows.size)
      return
    }
    if (target.size > oldRows.size &&
      oldRows.indices.all { idx -> oldRows[idx] === target[idx] || oldRows[idx] == target[idx] }
    ) {
      notifyItemRangeInserted(oldRows.size, target.size - oldRows.size)
      return
    }
    if (oldRows.size > target.size &&
      target.indices.all { idx -> oldRows[idx] === target[idx] || oldRows[idx] == target[idx] }
    ) {
      notifyItemRangeRemoved(target.size, oldRows.size - target.size)
      return
    }

    val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
      override fun getOldListSize(): Int = oldRows.size
      override fun getNewListSize(): Int = target.size
      override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldRows[oldItemPosition].stableKey == target[newItemPosition].stableKey
      }

      override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldRows[oldItemPosition] == target[newItemPosition]
      }
    })
    diff.dispatchUpdatesTo(this)
  }

  fun updatePalette(newPalette: HybridViewPalette) {
    if (palette != newPalette) {
      palette = newPalette
      notifyDataSetChanged()
    }
  }

  override fun getItemViewType(position: Int): Int {
    return when (getItem(position)) {
      is HybridSearchRow.AyahCard -> TYPE_AYAH
      is HybridSearchRow.SuraCard -> TYPE_SURA
      is HybridSearchRow.TafsirCard -> TYPE_TAFSIR
      is HybridSearchRow.ActionCard -> TYPE_ACTION
      HybridSearchRow.TafsirEmptyCard -> TYPE_TAFSIR_EMPTY
      is HybridSearchRow.EmptyMessageCard -> TYPE_EMPTY_MESSAGE
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val ctx = parent.context
    val currentPalette = palette ?: error("Palette must be set before binding HybridSearchAdapter")
    return when (viewType) {
      TYPE_AYAH -> AyahViewHolder(ctx, currentPalette)
      TYPE_SURA -> SuraViewHolder(ctx, currentPalette)
      TYPE_TAFSIR -> TafsirViewHolder(ctx, currentPalette)
      TYPE_ACTION -> ActionViewHolder(ctx, currentPalette)
      TYPE_TAFSIR_EMPTY -> TafsirEmptyViewHolder(ctx, currentPalette)
      TYPE_EMPTY_MESSAGE -> EmptyMessageViewHolder(ctx, currentPalette)
      else -> error("Unsupported viewType $viewType")
    }
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    val currentPalette = palette ?: return
    when (val row = getItem(position)) {
      is HybridSearchRow.AyahCard -> (holder as AyahViewHolder).bind(row, currentPalette)
      is HybridSearchRow.SuraCard -> (holder as SuraViewHolder).bind(row, currentPalette)
      is HybridSearchRow.TafsirCard -> (holder as TafsirViewHolder).bind(row, currentPalette)
      is HybridSearchRow.ActionCard -> (holder as ActionViewHolder).bind(row, currentPalette)
      HybridSearchRow.TafsirEmptyCard -> (holder as TafsirEmptyViewHolder).bind(currentPalette)
      is HybridSearchRow.EmptyMessageCard -> (holder as EmptyMessageViewHolder).bind(row, currentPalette)
    }
  }

  override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
    HybridViewTokens.resetApplePressFeedback(holder.itemView)
    when (holder) {
      is AyahViewHolder -> holder.resetTransientMotion()
      is TafsirViewHolder -> holder.resetTransientMotion()
      is ActionViewHolder -> holder.resetTransientMotion()
      is TafsirEmptyViewHolder -> holder.resetTransientMotion()
    }
    super.onViewRecycled(holder)
  }

  private fun cardLayoutParams(context: Context): RecyclerView.LayoutParams {
    return RecyclerView.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
      bottomMargin = HybridViewTokens.dp(context, 10f)
    }
  }

  private inner class ActionBarViews(
    context: Context,
    palette: HybridViewPalette
  ) {
    val root = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private val btnRadius = HybridViewTokens.dpF(context, 8f)
    private val listenBg = HybridViewTokens.lightweightPressRoundedDrawable(
      Color.TRANSPARENT,
      radiusPx = btnRadius,
      pressColor = palette.ripple
    )
    private val bookmarkBg = HybridViewTokens.lightweightPressRoundedDrawable(
      Color.TRANSPARENT,
      radiusPx = btnRadius,
      pressColor = palette.ripple
    )
    private val openBg = HybridViewTokens.lightweightPressRoundedDrawable(
      Color.TRANSPARENT,
      radiusPx = btnRadius,
      pressColor = palette.ripple
    )

    private var lastPalette: HybridViewPalette? = null
    private var lastBookmarked: Boolean? = null
    private var currentOnListen: (() -> Unit)? = null
    private var currentOnBookmark: (() -> Unit)? = null
    private var currentOnOpen: (() -> Unit)? = null

    private val listenIcon = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.PLAY,
      palette.textSecondary,
      HybridViewTokens.dp(context, 12f)
    )
    private val listenText = TextView(context).apply {
      text = context.getString(R.string.hybrid_search_action_listen)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      includeFontPadding = false
    }
    val listenButton = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      isClickable = true
      isFocusable = true
      val ph = HybridViewTokens.dp(context, 6f)
      val pv = HybridViewTokens.dp(context, 5f)
      setPadding(ph, pv, ph, pv)
      background = listenBg
      setOnClickListener { currentOnListen?.invoke() }
      addView(ImageView(context).apply { setImageDrawable(listenIcon) })
      addView(
        listenText,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = HybridViewTokens.dp(context, 4f) }
      )
    }

    private val verticalDivider = View(context)

    private val bookmarkIcon = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.BOOKMARK,
      palette.textSecondary,
      HybridViewTokens.dp(context, 13f)
    )
    private val bookmarkIconFilled = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.BOOKMARK_FILLED,
      palette.editorialAccent,
      HybridViewTokens.dp(context, 13f)
    )
    private val bookmarkIconView = ImageView(context).apply {
      setImageDrawable(bookmarkIcon)
    }
    private val bookmarkText = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      includeFontPadding = false
    }
    val bookmarkButton = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      isClickable = true
      isFocusable = true
      val ph = HybridViewTokens.dp(context, 6f)
      val pv = HybridViewTokens.dp(context, 5f)
      setPadding(ph, pv, ph, pv)
      background = bookmarkBg
      setOnClickListener { currentOnBookmark?.invoke() }
      addView(bookmarkIconView)
      addView(
        bookmarkText,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = HybridViewTokens.dp(context, 4f) }
      )
    }

    private val openIcon = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.CHEVRON_LEFT,
      palette.accent,
      HybridViewTokens.dp(context, 12f)
    )
    private val openText = TextView(context).apply {
      text = context.getString(R.string.hybrid_search_action_open_page)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      includeFontPadding = false
    }
    val openPageButton = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      isClickable = true
      isFocusable = true
      val ph = HybridViewTokens.dp(context, 6f)
      val pv = HybridViewTokens.dp(context, 5f)
      setPadding(ph, pv, ph, pv)
      background = openBg
      setOnClickListener { currentOnOpen?.invoke() }
      addView(openText)
      addView(
        ImageView(context).apply { setImageDrawable(openIcon) },
        LinearLayout.LayoutParams(
          HybridViewTokens.dp(context, 12f),
          HybridViewTokens.dp(context, 12f)
        ).apply { marginStart = HybridViewTokens.dp(context, 3f) }
      )
    }

    init {
      HybridViewTokens.attachApplePressFeedback(listenButton, 0.965f)
      HybridViewTokens.attachApplePressFeedback(bookmarkButton, 0.965f)
      HybridViewTokens.attachApplePressFeedback(openPageButton, 0.965f)
      val leftGroup = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(listenButton)
        addView(
          verticalDivider,
          LinearLayout.LayoutParams(
            HybridViewTokens.dp(context, 1f),
            HybridViewTokens.dp(context, 10f)
          ).apply {
            val m = HybridViewTokens.dp(context, 4f)
            marginStart = m
            marginEnd = m
          }
        )
        addView(bookmarkButton)
      }
      root.addView(
        leftGroup,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      )
      root.addView(openPageButton)
    }

    fun bind(
      sura: Int,
      ayah: Int,
      palette: HybridViewPalette,
      isBookmarked: Boolean,
      onListen: () -> Unit,
      onBookmark: () -> Unit,
      onOpen: () -> Unit
    ) {
      currentOnListen = onListen
      currentOnBookmark = onBookmark
      currentOnOpen = onOpen

      listenButton.tag = SearchTestTags.listenButton(sura, ayah)
      bookmarkButton.tag = SearchTestTags.bookmarkButton(sura, ayah)
      openPageButton.tag = SearchTestTags.openPageButton(sura, ayah)

      val paletteChanged = lastPalette != palette
      if (paletteChanged) {
        lastPalette = palette
        listenIcon.setTintColor(palette.textSecondary)
        listenText.setTextColor(palette.textSecondary)
        listenBg.setPressColor(palette.ripple)

        verticalDivider.setBackgroundColor(palette.divider)

        bookmarkIcon.setTintColor(palette.textSecondary)
        bookmarkIconFilled.setTintColor(palette.editorialAccent)
        bookmarkBg.setPressColor(palette.ripple)

        openIcon.setTintColor(palette.accent)
        openText.setTextColor(palette.accent)
        openBg.setPressColor(palette.ripple)
      }

      if (paletteChanged || lastBookmarked != isBookmarked) {
        lastBookmarked = isBookmarked
        val ctx = root.context
        bookmarkIconView.setImageDrawable(if (isBookmarked) bookmarkIconFilled else bookmarkIcon)
        bookmarkText.text = bookmarkLabel(ctx, isBookmarked)
        bookmarkText.setTextColor(
          if (isBookmarked) palette.editorialAccent else palette.textSecondary
        )
      }
    }

    fun resetTransientMotion() {
      HybridViewTokens.resetApplePressFeedback(listenButton)
      HybridViewTokens.resetApplePressFeedback(bookmarkButton)
      HybridViewTokens.resetApplePressFeedback(openPageButton)
    }
  }

  /**
   * [TextView] subclass that measures and draws single-line header/pill text via an off-thread
   * pre-built [StaticLayout] when width fits, eliminating UI-thread HarfBuzz shaping in `onMeasure`
   * while preserving [getText] and [AccessibilityNodeInfo] for UIAutomator.
   */
  internal open class StaticLayoutSingleLineView(context: Context) : TextView(context) {
    private var prebuiltLayout: StaticLayout? = null
    private var boundText: CharSequence? = ""
    private var fallbackSyncedText: CharSequence? = null
    private var suppressRelayout = false

    override fun requestLayout() {
      if (!suppressRelayout) {
        super.requestLayout()
      }
    }

    override fun getText(): CharSequence = boundText ?: super.getText() ?: ""

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
      super.onInitializeAccessibilityNodeInfo(info)
      info.text = getText()
    }

    private fun syncFallbackTextIfNeeded() {
      val target = boundText
      if (target != null && fallbackSyncedText !== target) {
        fallbackSyncedText = target
        suppressRelayout = true
        super.setText(target, BufferType.NORMAL)
        suppressRelayout = false
      }
    }

    fun setPrebuiltContent(newText: CharSequence, layout: StaticLayout?) {
      val sameText = boundText === newText || boundText == newText
      val prevLayout = prebuiltLayout
      prebuiltLayout = layout
      boundText = newText

      if (layout != null) {
        val matchesMeasuredBounds = isLaidOut &&
          (width - compoundPaddingLeft - compoundPaddingRight) == layout.width &&
          measuredHeight == layout.height + compoundPaddingTop + compoundPaddingBottom
        if (!matchesMeasuredBounds && isLaidOut && !isInLayout && (!sameText || prevLayout !== layout)) {
          requestLayout()
        }
        invalidate()
        return
      }

      if (!sameText || fallbackSyncedText !== newText) {
        fallbackSyncedText = newText
        super.setText(newText, BufferType.NORMAL)
      } else if (prevLayout !== layout) {
        if (isLaidOut && !isInLayout) {
          requestLayout()
        }
        invalidate()
      } else {
        invalidate()
      }
    }

    internal fun hasPrebuiltLayout(): Boolean = prebuiltLayout != null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
      val layout = prebuiltLayout
      if (layout != null) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val specW = MeasureSpec.getSize(widthMeasureSpec)
        val availW = if (mode == MeasureSpec.UNSPECIFIED) {
          Int.MAX_VALUE
        } else {
          (specW - compoundPaddingLeft - compoundPaddingRight).coerceAtLeast(0)
        }
        if (layout.width <= availW) {
          val w = if (mode == MeasureSpec.EXACTLY) {
            specW
          } else {
            layout.width + compoundPaddingLeft + compoundPaddingRight
          }
          val h = layout.height + compoundPaddingTop + compoundPaddingBottom
          setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
          return
        }
      }
      syncFallbackTextIfNeeded()
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
      val layout = prebuiltLayout
      val availW = (width - compoundPaddingLeft - compoundPaddingRight).coerceAtLeast(0)
      if (layout != null && layout.width <= availW) {
        val saveCount = canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), compoundPaddingTop.toFloat())
        if (layout.paint.color != currentTextColor) {
          layout.paint.color = currentTextColor
        }
        layout.draw(canvas)
        canvas.restoreToCount(saveCount)
        return
      }
      syncFallbackTextIfNeeded()
      super.onDraw(canvas)
    }
  }

  /**
   * [TextView] subclass that measures and draws via an off-thread pre-built immutable [StaticLayout]
   * when available width matches, falling back cleanly to [TextView] if width differs or missing.
   */
  internal open class StaticLayoutVerseView(context: Context) : TextView(context) {
    private var prebuiltLayout: StaticLayout? = null
    private var boundText: CharSequence? = ""
    private var fallbackSyncedText: CharSequence? = null
    private var suppressRelayout = false
    private var highlightRanges: List<HighlightSpanRange> = emptyList()
    @ColorInt private var highlightBgColor: Int = Color.TRANSPARENT
    private val highlightRadiusPx = HybridViewTokens.dpF(context, 4f)
    private val highlightPadHPx = HybridViewTokens.dpF(context, 2f)
    private val highlightPadVPx = HybridViewTokens.dpF(context, 1f)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
    }
    private val highlightPath = Path()
    private val highlightBoundsRect = RectF()
    private val highlightDrawRect = RectF()
    private val highlightFontMetrics = Paint.FontMetrics()

    override fun requestLayout() {
      if (!suppressRelayout) {
        super.requestLayout()
      }
    }

    override fun getText(): CharSequence = boundText ?: super.getText() ?: ""

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
      super.onInitializeAccessibilityNodeInfo(info)
      info.text = getText()
    }

    private fun syncFallbackTextIfNeeded() {
      val target = boundText
      if (target != null && fallbackSyncedText !== target) {
        fallbackSyncedText = target
        suppressRelayout = true
        super.setText(target, BufferType.NORMAL)
        suppressRelayout = false
      }
    }

    fun setPrebuiltContent(
      newText: CharSequence,
      layout: StaticLayout?,
      ranges: List<HighlightSpanRange> = emptyList(),
      @ColorInt highlightBg: Int = Color.TRANSPARENT
    ) {
      val sameText = boundText === newText
      val prevLayout = prebuiltLayout
      val highlightChanged = highlightBgColor != highlightBg || highlightRanges != ranges
      prebuiltLayout = layout
      boundText = newText
      highlightRanges = ranges
      highlightBgColor = highlightBg
      highlightPaint.color = highlightBg

      if (layout != null) {
        val matchesMeasuredBounds = isLaidOut &&
          (width - compoundPaddingLeft - compoundPaddingRight) == layout.width &&
          measuredHeight == layout.height + compoundPaddingTop + compoundPaddingBottom
        if (!matchesMeasuredBounds && isLaidOut && !isInLayout && (!sameText || prevLayout !== layout)) {
          requestLayout()
        }
        invalidate()
        return
      }

      if (!sameText || fallbackSyncedText !== newText) {
        fallbackSyncedText = newText
        super.setText(newText, BufferType.NORMAL)
      } else if (prevLayout !== layout) {
        if (isLaidOut && !isInLayout) {
          requestLayout()
        }
        invalidate()
      } else if (highlightChanged) {
        invalidate()
      }
    }

    internal fun hasPrebuiltLayout(): Boolean = prebuiltLayout != null

    private inline fun forEachHighlightRect(layout: Layout, action: (RectF) -> Unit) {
      if (highlightRanges.isEmpty() || highlightBgColor == Color.TRANSPARENT) return
      val textLen = layout.text.length
      val lineCount = layout.lineCount
      if (textLen <= 0 || lineCount <= 0) return
      val lastVisibleEnd = layout.getLineEnd(lineCount - 1)
      layout.paint.getFontMetrics(highlightFontMetrics)
      val fmAscent = highlightFontMetrics.ascent
      val fmDescent = highlightFontMetrics.descent
      val layoutW = layout.width.toFloat()

      for (i in 0 until highlightRanges.size) {
        val range = highlightRanges[i]
        val start = range.start.coerceIn(0, textLen)
        val end = range.end.coerceIn(start, textLen)
        if (start >= end || start >= lastVisibleEnd) continue
        val clampedEnd = end.coerceAtMost(lastVisibleEnd)
        val startLine = layout.getLineForOffset(start)
        val endLine = layout.getLineForOffset((clampedEnd - 1).coerceAtLeast(start))
        for (line in startLine..endLine) {
          val lineStart = maxOf(start, layout.getLineStart(line))
          val lineEnd = minOf(clampedEnd, layout.getLineVisibleEnd(line))
          if (lineEnd <= lineStart) continue
          highlightPath.rewind()
          layout.getSelectionPath(lineStart, lineEnd, highlightPath)
          highlightPath.computeBounds(highlightBoundsRect, true)
          if (highlightBoundsRect.width() <= 0f) continue

          val baseline = layout.getLineBaseline(line).toFloat()
          val lineTop = layout.getLineTop(line).toFloat()
          val lineBottom = layout.getLineBottom(line).toFloat()
          // Clamp strictly to unmultiplied glyph ascent..descent so multi-line
          // lineSpacingMultiplier gaps never stretch highlights vertically.
          val glyphTop = (baseline + fmAscent - highlightPadVPx).coerceAtLeast(lineTop)
          val glyphBottom = (baseline + fmDescent + highlightPadVPx).coerceAtMost(lineBottom)
          if (glyphBottom <= glyphTop) continue

          val left = (highlightBoundsRect.left - highlightPadHPx).coerceAtLeast(0f)
          val right = (highlightBoundsRect.right + highlightPadHPx).coerceAtMost(layoutW)
          if (right <= left) continue

          highlightDrawRect.set(left, glyphTop, right, glyphBottom)
          action(highlightDrawRect)
        }
      }
    }

    internal fun computeHighlightRectsForTest(layout: Layout): List<RectF> {
      val out = ArrayList<RectF>()
      forEachHighlightRect(layout) { rect -> out.add(RectF(rect)) }
      return out
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
      val layout = prebuiltLayout
      if (layout != null && MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
        val totalW = MeasureSpec.getSize(widthMeasureSpec)
        val availW = (totalW - compoundPaddingLeft - compoundPaddingRight).coerceAtLeast(0)
        if (availW == layout.width) {
          val h = layout.height + compoundPaddingTop + compoundPaddingBottom
          setMeasuredDimension(totalW, resolveSize(h, heightMeasureSpec))
          return
        }
      }
      syncFallbackTextIfNeeded()
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
      val layout = prebuiltLayout
      val availW = (width - compoundPaddingLeft - compoundPaddingRight).coerceAtLeast(0)
      if (layout != null && availW == layout.width) {
        val saveCount = canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), compoundPaddingTop.toFloat())
        if (layout.paint.color != currentTextColor) {
          layout.paint.color = currentTextColor
        }
        forEachHighlightRect(layout) { rect ->
          canvas.drawRoundRect(rect, highlightRadiusPx, highlightRadiusPx, highlightPaint)
        }
        layout.draw(canvas)
        canvas.restoreToCount(saveCount)
        return
      }
      syncFallbackTextIfNeeded()
      val tvLayout = this.layout
      if (tvLayout != null && highlightRanges.isNotEmpty() && highlightBgColor != Color.TRANSPARENT) {
        val saveCount = canvas.save()
        canvas.translate(totalPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
        forEachHighlightRect(tvLayout) { rect ->
          canvas.drawRoundRect(rect, highlightRadiusPx, highlightRadiusPx, highlightPaint)
        }
        canvas.restoreToCount(saveCount)
      }
      super.onDraw(canvas)
    }
  }

  /**
   * Flat 4-child [ViewGroup] for [AyahViewHolder], drawing card fill/border, header ref text,
   * page pill, and internal hairline dividers directly in [onDraw], while caching static action
   * button dimensions once per `(availW, fontScale)` and keeping `verseText`, `listenButton`,
   * `bookmarkButton`, and `openPageButton` as the only 4 child Views.
   */
  private class AyahCardLayout(context: Context) : ViewGroup(context) {
    private val padH = HybridViewTokens.dp(context, 14f)
    private val padV = HybridViewTokens.dp(context, 10f)
    private val headerGap = HybridViewTokens.dp(context, 8f)
    private val pillPadH = HybridViewTokens.dp(context, 8f)
    private val pillPadV = HybridViewTokens.dp(context, 2f)
    private val pillRadiusPx = HybridViewTokens.dpF(context, 999f)
    private val verseTopGap = HybridViewTokens.dp(context, 6f)
    private val dividerTopGap = HybridViewTokens.dp(context, 8f)
    private val dividerHeight = HybridViewTokens.dp(context, 1f)
    private val dividerBottomGap = HybridViewTokens.dp(context, 4f)
    private val actionDividerW = HybridViewTokens.dp(context, 1f)
    private val actionDividerH = HybridViewTokens.dp(context, 10f)
    private val actionDividerGap = HybridViewTokens.dp(context, 4f)
    private val cardRadiusPx = HybridViewTokens.dpF(context, 18f)
    private val strokePx = HybridViewTokens.dp(context, 1f)
    private val halfStrokePx = strokePx * 0.5f
    private val cardStrokeRadiusPx = (cardRadiusPx - halfStrokePx).coerceAtLeast(0f)

    private val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
    }
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = strokePx.toFloat().coerceAtLeast(1f)
    }
    private val cardPressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
    }
    private val pagePillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
    }
    private val dividerPaint = Paint().apply {
      style = Paint.Style.STROKE
      strokeWidth = dividerHeight.toFloat().coerceAtLeast(1f)
    }

    private val dm = context.resources.displayMetrics
    private val refTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      density = dm.density
      textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f, dm)
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val pagePillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      density = dm.density
      textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10.5f, dm)
      typeface = Typeface.DEFAULT
    }

    private val cardFillRect = RectF()
    private val cardStrokeRect = RectF()
    private val pillRectF = RectF()
    private var pillTextX = 0f
    private var pillTextY = 0f
    private var refTextX = 0f
    private var refTextY = 0f

    private var dividerY = 0f
    private var dividerLeft = 0f
    private var dividerRight = 0f
    private var actionDivX = 0f
    private var actionDivTop = 0f
    private var actionDivBottom = 0f

    private var boundRefLabel: String = ""
    private var boundPageLabel: String = ""
    private var a11yHeaderText: String = ""
    private var prebuiltRefLayout: StaticLayout? = null
    private var prebuiltPagePillLayout: StaticLayout? = null
    private var fallbackRefLayout: StaticLayout? = null
    private var fallbackRefKey: String = ""
    private var fallbackRefMaxW: Int = -1
    private var fallbackPillLayout: StaticLayout? = null
    private var fallbackPillKey: String = ""
    private var fallbackPillMaxW: Int = -1
    @ColorInt private var refTextColor: Int = Color.BLACK
    @ColorInt private var pageTextColor: Int = Color.GRAY
    private var activeRefLayout: StaticLayout? = null
    private var activePillLayout: StaticLayout? = null
    private var lastMeasuredHeaderH: Int = -1

    private var cachedActionAvailW = -1
    private var cachedActionFontScale = -1f
    private var cachedListenW = -1
    private var cachedListenH = -1
    private var cachedOpenW = -1
    private var cachedOpenH = -1
    private var cachedBookmarkSavedW = -1
    private var cachedBookmarkSavedH = -1
    private var cachedBookmarkUnsavedW = -1
    private var cachedBookmarkUnsavedH = -1
    private var isBookmarkedState = false
    private var wasPressedForDraw = false

    lateinit var verseText: StaticLayoutVerseView
    lateinit var listenButton: TextView
    lateinit var bookmarkButton: TextView
    lateinit var openPageButton: TextView

    init {
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      setPadding(padH, padV, padH, padV)
      setWillNotDraw(false)
    }

    override fun hasOverlappingRendering(): Boolean = false

    override fun dispatchSetPressed(pressed: Boolean) {
      super.dispatchSetPressed(pressed)
      if (wasPressedForDraw != pressed) {
        wasPressedForDraw = pressed
        invalidate()
      }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
      super.onInitializeAccessibilityNodeInfo(info)
      if (a11yHeaderText.isNotEmpty()) {
        info.text = a11yHeaderText
      }
    }

    fun updatePaintColors(
      @ColorInt fillColor: Int,
      @ColorInt borderColor: Int,
      @ColorInt pressColor: Int,
      @ColorInt dividerColor: Int,
      @ColorInt refColor: Int,
      @ColorInt pageColor: Int,
      @ColorInt pageBgColor: Int
    ) {
      var changed = false
      if (cardFillPaint.color != fillColor) {
        cardFillPaint.color = fillColor
        changed = true
      }
      if (cardStrokePaint.color != borderColor) {
        cardStrokePaint.color = borderColor
        changed = true
      }
      if (cardPressPaint.color != pressColor) {
        cardPressPaint.color = pressColor
        if (isPressed) changed = true
      }
      if (dividerPaint.color != dividerColor) {
        dividerPaint.color = dividerColor
        changed = true
      }
      if (refTextColor != refColor) {
        refTextColor = refColor
        changed = true
      }
      if (pageTextColor != pageColor) {
        pageTextColor = pageColor
        changed = true
      }
      if (pagePillBgPaint.color != pageBgColor) {
        pagePillBgPaint.color = pageBgColor
        changed = true
      }
      if (changed) {
        invalidate()
      }
    }

    fun updateHeaderAndBookmarkState(
      refLabel: String,
      refLayout: StaticLayout?,
      pageLabel: String,
      pagePillLayout: StaticLayout?,
      isBookmarked: Boolean
    ) {
      isBookmarkedState = isBookmarked
      val refChanged = boundRefLabel != refLabel || prebuiltRefLayout !== refLayout
      val pillChanged = boundPageLabel != pageLabel || prebuiltPagePillLayout !== pagePillLayout
      if (!refChanged && !pillChanged) return

      boundRefLabel = refLabel
      prebuiltRefLayout = refLayout
      if (refLayout != null) {
        fallbackRefLayout = null
        fallbackRefKey = ""
      }

      boundPageLabel = pageLabel
      prebuiltPagePillLayout = pagePillLayout
      if (pagePillLayout != null) {
        fallbackPillLayout = null
        fallbackPillKey = ""
      }

      a11yHeaderText = when {
        pageLabel.isNotEmpty() && refLabel.isNotEmpty() -> "$pageLabel  $refLabel"
        refLabel.isNotEmpty() -> refLabel
        else -> pageLabel
      }
      val cd = when {
        refLabel.isNotEmpty() && pageLabel.isNotEmpty() -> "$refLabel — $pageLabel"
        refLabel.isNotEmpty() -> refLabel
        else -> pageLabel
      }
      if (contentDescription != cd) {
        contentDescription = cd
      }

      val availW = (width - padH * 2).coerceAtLeast(0)
      if (isLaidOut && availW > 0 && lastMeasuredHeaderH >= 0) {
        val (resolvedRef, resolvedPill, headerH) = resolveHeaderLayouts(availW)
        if (headerH == lastMeasuredHeaderH) {
          activeRefLayout = resolvedRef
          activePillLayout = resolvedPill
          updateHeaderGeometry(width, resolvedRef, resolvedPill, headerH)
          invalidate()
          return
        }
      }
      requestLayout()
      invalidate()
    }

    fun attachChildren(
      verseText: StaticLayoutVerseView,
      listenButton: TextView,
      bookmarkButton: TextView,
      openPageButton: TextView
    ) {
      this.verseText = verseText
      this.listenButton = listenButton
      this.bookmarkButton = bookmarkButton
      this.openPageButton = openPageButton
      addView(verseText)
      addView(listenButton)
      addView(bookmarkButton)
      addView(openPageButton)
    }

    private fun buildSingleLineFallback(
      text: String,
      paint: TextPaint,
      maxWidthPx: Int
    ): StaticLayout? {
      if (text.isEmpty() || maxWidthPx <= 0) return null
      val desiredW = ceil(Layout.getDesiredWidth(text, paint).toDouble())
        .toInt()
        .coerceIn(0, maxWidthPx)
      return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          StaticLayout.Builder.obtain(text, 0, text.length, paint, desiredW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setEllipsizedWidth(desiredW)
            .setMaxLines(1)
            .build()
        } else {
          @Suppress("DEPRECATION")
          StaticLayout(text, paint, desiredW, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, true)
        }
      }.getOrNull()
    }

    private fun resolveHeaderLayouts(availW: Int): Triple<StaticLayout?, StaticLayout?, Int> {
      val maxPillTextW = (availW - pillPadH * 2).coerceAtLeast(0)
      val prePill = prebuiltPagePillLayout
      val resolvedPill = when {
        boundPageLabel.isEmpty() -> null
        prePill != null && prePill.width <= maxPillTextW -> prePill
        fallbackPillLayout != null &&
          fallbackPillKey == boundPageLabel &&
          (fallbackPillMaxW == maxPillTextW || (fallbackPillLayout?.width ?: Int.MAX_VALUE) <= maxPillTextW) ->
          fallbackPillLayout
        else -> buildSingleLineFallback(boundPageLabel, pagePillTextPaint, maxPillTextW).also {
          fallbackPillLayout = it
          fallbackPillKey = boundPageLabel
          fallbackPillMaxW = maxPillTextW
        }
      }
      val pillW = if (resolvedPill != null) resolvedPill.width + pillPadH * 2 else 0
      val pillH = if (resolvedPill != null) resolvedPill.height + pillPadV * 2 else 0

      val refMaxW = (availW - pillW - headerGap).coerceAtLeast(0)
      val preRef = prebuiltRefLayout
      val resolvedRef = when {
        boundRefLabel.isEmpty() || refMaxW <= 0 -> null
        preRef != null && preRef.width <= refMaxW -> preRef
        fallbackRefLayout != null &&
          fallbackRefKey == boundRefLabel &&
          (fallbackRefMaxW == refMaxW || (fallbackRefLayout?.width ?: Int.MAX_VALUE) <= refMaxW) ->
          fallbackRefLayout
        else -> buildSingleLineFallback(boundRefLabel, refTextPaint, refMaxW).also {
          fallbackRefLayout = it
          fallbackRefKey = boundRefLabel
          fallbackRefMaxW = refMaxW
        }
      }
      val refH = resolvedRef?.height ?: 0
      return Triple(resolvedRef, resolvedPill, max(refH, pillH))
    }

    private fun updateHeaderGeometry(
      totalWidth: Int,
      refLayout: StaticLayout?,
      pillLayout: StaticLayout?,
      headerH: Int
    ) {
      val contentLeft = padH
      val contentRight = totalWidth - padH
      val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
      val pillW = if (pillLayout != null) pillLayout.width + pillPadH * 2 else 0
      val pillH = if (pillLayout != null) pillLayout.height + pillPadV * 2 else 0
      val refW = refLayout?.width ?: 0
      val refH = refLayout?.height ?: 0
      val refTop = padV + (headerH - refH) / 2
      val pillTop = padV + (headerH - pillH) / 2

      if (isRtl) {
        refTextX = (contentRight - refW).toFloat()
        refTextY = refTop.toFloat()
        if (pillLayout != null) {
          pillRectF.set(
            contentLeft.toFloat(),
            pillTop.toFloat(),
            (contentLeft + pillW).toFloat(),
            (pillTop + pillH).toFloat()
          )
          pillTextX = (contentLeft + pillPadH).toFloat()
          pillTextY = (pillTop + pillPadV).toFloat()
        } else {
          pillRectF.setEmpty()
        }
      } else {
        refTextX = contentLeft.toFloat()
        refTextY = refTop.toFloat()
        if (pillLayout != null) {
          val pillLeft = contentRight - pillW
          pillRectF.set(
            pillLeft.toFloat(),
            pillTop.toFloat(),
            contentRight.toFloat(),
            (pillTop + pillH).toFloat()
          )
          pillTextX = (pillLeft + pillPadH).toFloat()
          pillTextY = (pillTop + pillPadV).toFloat()
        } else {
          pillRectF.setEmpty()
        }
      }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
      super.onSizeChanged(w, h, oldw, oldh)
      if (w > 0 && h > 0) {
        cardFillRect.set(0f, 0f, w.toFloat(), h.toFloat())
        cardStrokeRect.set(
          halfStrokePx,
          halfStrokePx,
          w - halfStrokePx,
          h - halfStrokePx
        )
      } else {
        cardFillRect.setEmpty()
        cardStrokeRect.setEmpty()
      }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
      val totalW = MeasureSpec.getSize(widthMeasureSpec)
      val availW = (totalW - padH * 2).coerceAtLeast(0)
      val atMostW = MeasureSpec.makeMeasureSpec(availW, MeasureSpec.AT_MOST)
      val exactW = MeasureSpec.makeMeasureSpec(availW, MeasureSpec.EXACTLY)
      val unspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

      val (resolvedRef, resolvedPill, headerH) = resolveHeaderLayouts(availW)
      activeRefLayout = resolvedRef
      activePillLayout = resolvedPill
      lastMeasuredHeaderH = headerH

      verseText.measure(exactW, unspec)

      val fontScale = resources.configuration.fontScale
      val actionConfigChanged = cachedActionAvailW != availW || cachedActionFontScale != fontScale
      if (actionConfigChanged) {
        cachedActionAvailW = availW
        cachedActionFontScale = fontScale
        cachedListenW = -1
        cachedListenH = -1
        cachedOpenW = -1
        cachedOpenH = -1
        cachedBookmarkSavedW = -1
        cachedBookmarkSavedH = -1
        cachedBookmarkUnsavedW = -1
        cachedBookmarkUnsavedH = -1
      }

      if (cachedOpenW <= 0 || openPageButton.measuredWidth <= 0 || openPageButton.isLayoutRequested) {
        openPageButton.measure(atMostW, unspec)
        cachedOpenW = openPageButton.measuredWidth
        cachedOpenH = openPageButton.measuredHeight
      }
      if (cachedListenW <= 0 || listenButton.measuredWidth <= 0 || listenButton.isLayoutRequested) {
        listenButton.measure(atMostW, unspec)
        cachedListenW = listenButton.measuredWidth
        cachedListenH = listenButton.measuredHeight
      }

      val cachedBmW = if (isBookmarkedState) cachedBookmarkSavedW else cachedBookmarkUnsavedW
      if (cachedBmW <= 0 || bookmarkButton.measuredWidth <= 0 || bookmarkButton.isLayoutRequested) {
        bookmarkButton.measure(atMostW, unspec)
        if (isBookmarkedState) {
          cachedBookmarkSavedW = bookmarkButton.measuredWidth
          cachedBookmarkSavedH = bookmarkButton.measuredHeight
        } else {
          cachedBookmarkUnsavedW = bookmarkButton.measuredWidth
          cachedBookmarkUnsavedH = bookmarkButton.measuredHeight
        }
      }

      val bmH = if (isBookmarkedState) cachedBookmarkSavedH else cachedBookmarkUnsavedH
      val actionH = max(cachedListenH, max(bmH, cachedOpenH))

      val totalH = padV + headerH + verseTopGap + verseText.measuredHeight +
        dividerTopGap + dividerHeight + dividerBottomGap + actionH + padV
      setMeasuredDimension(totalW, resolveSize(totalH, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
      val totalW = r - l
      val contentLeft = padH
      val contentRight = totalW - padH
      val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
      val headerH = lastMeasuredHeaderH.coerceAtLeast(0)
      updateHeaderGeometry(totalW, activeRefLayout, activePillLayout, headerH)

      var y = padV + headerH + verseTopGap

      verseText.layout(
        contentLeft,
        y,
        contentLeft + verseText.measuredWidth,
        y + verseText.measuredHeight
      )
      y += verseText.measuredHeight + dividerTopGap

      dividerLeft = contentLeft.toFloat()
      dividerRight = contentRight.toFloat()
      dividerY = y + dividerHeight * 0.5f
      y += dividerHeight + dividerBottomGap

      val listenW = cachedListenW.coerceAtLeast(listenButton.measuredWidth)
      val listenH = cachedListenH.coerceAtLeast(listenButton.measuredHeight)
      val bmW = (if (isBookmarkedState) cachedBookmarkSavedW else cachedBookmarkUnsavedW)
        .coerceAtLeast(bookmarkButton.measuredWidth)
      val bmH = (if (isBookmarkedState) cachedBookmarkSavedH else cachedBookmarkUnsavedH)
        .coerceAtLeast(bookmarkButton.measuredHeight)
      val openW = cachedOpenW.coerceAtLeast(openPageButton.measuredWidth)
      val openH = cachedOpenH.coerceAtLeast(openPageButton.measuredHeight)

      val actionH = max(listenH, max(bmH, openH))
      val listenTop = y + (actionH - listenH) / 2
      val actDivTop = y + (actionH - actionDividerH) / 2
      val bmTop = y + (actionH - bmH) / 2
      val openTop = y + (actionH - openH) / 2
      actionDivTop = actDivTop.toFloat()
      actionDivBottom = (actDivTop + actionDividerH).toFloat()

      if (isRtl) {
        val listenL = contentRight - listenW
        listenButton.layout(listenL, listenTop, contentRight, listenTop + listenH)
        val divR = listenL - actionDividerGap
        val divL = divR - actionDividerW
        actionDivX = divL + actionDividerW * 0.5f
        val bmR = divL - actionDividerGap
        val bmL = bmR - bmW
        bookmarkButton.layout(bmL, bmTop, bmR, bmTop + bmH)
        openPageButton.layout(
          contentLeft,
          openTop,
          contentLeft + openW,
          openTop + openH
        )
      } else {
        val listenR = contentLeft + listenW
        listenButton.layout(contentLeft, listenTop, listenR, listenTop + listenH)
        val divL = listenR + actionDividerGap
        val divR = divL + actionDividerW
        actionDivX = divL + actionDividerW * 0.5f
        val bmL = divR + actionDividerGap
        val bmR = bmL + bmW
        bookmarkButton.layout(bmL, bmTop, bmR, bmTop + bmH)
        openPageButton.layout(
          contentRight - openW,
          openTop,
          contentRight,
          openTop + openH
        )
      }
    }

    override fun onDraw(canvas: Canvas) {
      if (width <= 0 || height <= 0) return
      canvas.drawRoundRect(cardFillRect, cardRadiusPx, cardRadiusPx, cardFillPaint)
      if (isPressed && cardPressPaint.color != Color.TRANSPARENT) {
        canvas.drawRoundRect(cardFillRect, cardRadiusPx, cardRadiusPx, cardPressPaint)
      }
      if (cardStrokePaint.color != Color.TRANSPARENT) {
        canvas.drawRoundRect(cardStrokeRect, cardStrokeRadiusPx, cardStrokeRadiusPx, cardStrokePaint)
      }

      val pillLayout = activePillLayout
      if (pillLayout != null && !pillRectF.isEmpty) {
        canvas.drawRoundRect(pillRectF, pillRadiusPx, pillRadiusPx, pagePillBgPaint)
        val savePill = canvas.save()
        canvas.translate(pillTextX, pillTextY)
        if (pillLayout.paint.color != pageTextColor) {
          pillLayout.paint.color = pageTextColor
        }
        pillLayout.draw(canvas)
        canvas.restoreToCount(savePill)
      }

      val refLayout = activeRefLayout
      if (refLayout != null) {
        val saveRef = canvas.save()
        canvas.translate(refTextX, refTextY)
        if (refLayout.paint.color != refTextColor) {
          refLayout.paint.color = refTextColor
        }
        refLayout.draw(canvas)
        canvas.restoreToCount(saveRef)
      }

      canvas.drawLine(dividerLeft, dividerY, dividerRight, dividerY, dividerPaint)
      canvas.drawLine(actionDivX, actionDivTop, actionDivX, actionDivBottom, dividerPaint)
    }
  }

  private inner class AyahViewHolder(
    context: Context,
    palette: HybridViewPalette
  ) : RecyclerView.ViewHolder(
    AyahCardLayout(context).apply {
      layoutParams = cardLayoutParams(context)
    }
  ) {
    private val container = itemView as AyahCardLayout
    private val verseText = StaticLayoutVerseView(context).apply {
      id = R.id.verseText
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.5f)
      setLineSpacing(0f, 1.42f)
      maxLines = 6
      ellipsize = TextUtils.TruncateAt.END
      textAlignment = View.TEXT_ALIGNMENT_VIEW_START
      typeface = NativeUthmaniTypefaceCache.get(context) ?: Typeface.DEFAULT
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
      }
    }

    private val btnPadH = HybridViewTokens.dp(context, 6f)
    private val btnPadV = HybridViewTokens.dp(context, 5f)
    private val btnIconGap = HybridViewTokens.dp(context, 4f)
    private val listenBg = HybridViewTokens.lightweightPressDrawable(palette.ripple)
    private val bookmarkBg = HybridViewTokens.lightweightPressDrawable(palette.ripple)
    private val openBg = HybridViewTokens.lightweightPressDrawable(palette.ripple)

    private val listenIcon = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.PLAY,
      palette.textSecondary,
      HybridViewTokens.dp(context, 12f)
    )
    private val listenButton = TextView(context).apply {
      text = context.getString(R.string.hybrid_search_action_listen)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      includeFontPadding = false
      gravity = Gravity.CENTER_VERTICAL
      isClickable = true
      isFocusable = true
      setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
      compoundDrawablePadding = btnIconGap
      setCompoundDrawablesRelativeWithIntrinsicBounds(listenIcon, null, null, null)
      background = listenBg
      setOnClickListener { onPlayAyah(boundSura, boundAyah) }
    }

    private val bookmarkIcon = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.BOOKMARK,
      palette.textSecondary,
      HybridViewTokens.dp(context, 13f)
    )
    private val bookmarkIconFilled = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.BOOKMARK_FILLED,
      palette.editorialAccent,
      HybridViewTokens.dp(context, 13f)
    )
    private val bookmarkButton = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      includeFontPadding = false
      gravity = Gravity.CENTER_VERTICAL
      isClickable = true
      isFocusable = true
      setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
      compoundDrawablePadding = btnIconGap
      setCompoundDrawablesRelativeWithIntrinsicBounds(bookmarkIcon, null, null, null)
      background = bookmarkBg
      setOnClickListener { onToggleBookmark(boundSura, boundAyah, boundPage) }
    }

    private val openIcon = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.CHEVRON_LEFT,
      palette.accent,
      HybridViewTokens.dp(context, 12f)
    )
    private val openPageButton = TextView(context).apply {
      text = context.getString(R.string.hybrid_search_action_open_page)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      includeFontPadding = false
      gravity = Gravity.CENTER_VERTICAL
      isClickable = true
      isFocusable = true
      setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
      compoundDrawablePadding = btnIconGap
      setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, openIcon, null)
      background = openBg
      setOnClickListener { onOpenPage(boundPage, boundSura, boundAyah) }
    }

    private val cardRadiusPx = HybridViewTokens.dpF(context, 18f)
    private var lastPalette: HybridViewPalette? = null
    private var lastBookmarked: Boolean? = null
    private var boundPage = 1
    private var boundSura = 1
    private var boundAyah = 1

    init {
      container.setOnClickListener { onOpenPage(boundPage, boundSura, boundAyah) }
      HybridViewTokens.attachApplePressFeedback(container, 0.985f)
      container.attachChildren(
        verseText = verseText,
        listenButton = listenButton,
        bookmarkButton = bookmarkButton,
        openPageButton = openPageButton
      )
    }

    fun bind(row: HybridSearchRow.AyahCard, palette: HybridViewPalette) {
      val hit = row.hit
      val ctx = container.context
      boundPage = hit.page
      boundSura = hit.sura
      boundAyah = hit.ayah
      container.tag = hit.cardTag
      listenButton.tag = hit.listenTag
      bookmarkButton.tag = hit.bookmarkTag
      openPageButton.tag = hit.openPageTag

      val paletteChanged = lastPalette != palette
      if (paletteChanged) {
        lastPalette = palette
        container.updatePaintColors(
          fillColor = palette.cardSurface,
          borderColor = palette.cardHairlineBorder,
          pressColor = palette.ripple,
          dividerColor = palette.divider,
          refColor = palette.editorialAccent,
          pageColor = palette.accent,
          pageBgColor = palette.accentSoftBg
        )
        HybridViewTokens.applyZLayerFloatation(
          view = container,
          zLevel = 1,
          isDark = palette.isDark,
          shadowTintColor = palette.shadowTint,
          radiusPx = cardRadiusPx
        )
        verseText.setTextColor(palette.textPrimary)

        listenIcon.setTintColor(palette.textSecondary)
        listenButton.setTextColor(palette.textSecondary)
        listenBg.setPressColor(palette.ripple)
        bookmarkIcon.setTintColor(palette.textSecondary)
        bookmarkIconFilled.setTintColor(palette.editorialAccent)
        bookmarkBg.setPressColor(palette.ripple)
        openIcon.setTintColor(palette.accent)
        openPageButton.setTextColor(palette.accent)
        openBg.setPressColor(palette.ripple)
      }

      if (paletteChanged || lastBookmarked != row.isBookmarked) {
        lastBookmarked = row.isBookmarked
        bookmarkButton.text = bookmarkLabel(ctx, row.isBookmarked)
        bookmarkButton.setTextColor(
          if (row.isBookmarked) palette.editorialAccent else palette.textSecondary
        )
        bookmarkButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
          if (row.isBookmarked) bookmarkIconFilled else bookmarkIcon,
          null,
          null,
          null
        )
      }

      val nextRef = hit.ayahRefLabel.ifEmpty {
        ctx.getString(
          R.string.hybrid_search_sura_ayah_header,
          hit.suraName,
          QuranUtils.getLocalizedNumber(hit.ayah)
        )
      }
      val prebuiltRef = if (hit.ayahRefLabel.isNotEmpty()) {
        refLayoutProvider?.invoke(hit.sura, hit.ayah)
      } else {
        null
      }

      val nextPage = hit.pageLabel.ifEmpty {
        ctx.getString(R.string.hybrid_search_page_pill, QuranUtils.getLocalizedNumber(hit.page))
      }
      val prebuiltPill = if (hit.pageLabel.isNotEmpty()) {
        pagePillLayoutProvider?.invoke(hit.page, palette.accent)
      } else {
        null
      }

      container.updateHeaderAndBookmarkState(
        refLabel = nextRef,
        refLayout = prebuiltRef,
        pageLabel = nextPage,
        pagePillLayout = prebuiltPill,
        isBookmarked = row.isBookmarked
      )

      val uthmaniTf = NativeUthmaniTypefaceCache.get(ctx)
      if (uthmaniTf != null && verseText.typeface !== uthmaniTf) {
        verseText.typeface = uthmaniTf
      }
      val desiredDir = if (hit.isArabic) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_FIRST_STRONG
      if (verseText.textDirection != desiredDir) {
        verseText.textDirection = desiredDir
      }
      verseText.tag = hit.textTag

      val precomputed = hit.precomputedNativeText
      val matchesPalette = precomputed != null &&
        hit.precomputedHighlightColorArgb == palette.searchHighlight &&
        hit.precomputedHighlightBgArgb == palette.searchHighlightBg
      val verseContent: CharSequence = if (matchesPalette && precomputed != null) {
        precomputed
      } else {
        HybridSearchHighlighter.buildForegroundHighlightedSpan(
          text = hit.text,
          ranges = hit.highlightRanges,
          highlightColor = palette.searchHighlight
        )
      }
      val prebuiltLayout = if (hit.isArabic && matchesPalette) {
        staticLayoutProvider?.invoke(hit.sura, hit.ayah)
      } else {
        null
      }
      verseText.setPrebuiltContent(
        newText = verseContent,
        layout = prebuiltLayout,
        ranges = hit.highlightRanges,
        highlightBg = palette.searchHighlightBg
      )
    }

    fun resetTransientMotion() {
      HybridViewTokens.resetApplePressFeedback(container)
    }
  }

  private inner class SuraViewHolder(
    context: Context,
    palette: HybridViewPalette
  ) : RecyclerView.ViewHolder(
    LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      val ph = HybridViewTokens.dp(context, 14f)
      val pv = HybridViewTokens.dp(context, 11f)
      setPadding(ph, pv, ph, pv)
      layoutParams = cardLayoutParams(context)
    }
  ) {
    private val container = itemView as LinearLayout
    private val medallion = RubElHizbView(context).apply { setSizeDp(36f) }
    private val titleView = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    private val metaView = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    private val juzDivider = View(context)
    private val juzView = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      maxLines = 1
    }
    private val pagePill = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
      maxLines = 1
      val ph = HybridViewTokens.dp(context, 8f)
      val pv = HybridViewTokens.dp(context, 2f)
      setPadding(ph, pv, ph, pv)
    }
    private val openText = TextView(context).apply {
      text = context.getString(R.string.hybrid_search_action_open_page)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
    }
    private val openIcon = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.CHEVRON_LEFT,
      palette.accent,
      HybridViewTokens.dp(context, 12f)
    )
    private val strokePx = HybridViewTokens.dp(context, 1f)
    private val cardRadiusPx = HybridViewTokens.dpF(context, 18f)
    private val cardBg = HybridViewTokens.lightweightPressRoundedDrawable(
      fillColor = palette.cardSurface,
      strokeColor = palette.cardHairlineBorder,
      strokeWidthPx = strokePx,
      radiusPx = cardRadiusPx,
      pressColor = palette.ripple
    )
    private val pagePillBg = HybridViewTokens.roundedDrawable(
      fillColor = palette.accentSoftBg,
      radiusPx = HybridViewTokens.dpF(context, 999f)
    )
    private var lastPalette: HybridViewPalette? = null
    private var boundPage = 1
    private var boundSura = 1

    init {
      container.background = cardBg
      container.setOnClickListener { onOpenPage(boundPage, boundSura, 1) }
      HybridViewTokens.attachApplePressFeedback(container, 0.985f)
      pagePill.background = pagePillBg
      container.addView(medallion)
      val middleCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(titleView)
        val metaRow = LinearLayout(context).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          addView(metaView)
          addView(
            juzDivider,
            LinearLayout.LayoutParams(
              HybridViewTokens.dp(context, 1f),
              HybridViewTokens.dp(context, 10f)
            ).apply {
              val m = HybridViewTokens.dp(context, 6f)
              marginStart = m
              marginEnd = m
            }
          )
          addView(juzView)
        }
        addView(
          metaRow,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { topMargin = HybridViewTokens.dp(context, 2f) }
        )
      }
      container.addView(
        middleCol,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          marginStart = HybridViewTokens.dp(context, 12f)
          marginEnd = HybridViewTokens.dp(context, 8f)
        }
      )

      val endCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.END
        addView(pagePill)
        val openRow = LinearLayout(context).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          addView(openText)
          addView(
            ImageView(context).apply { setImageDrawable(openIcon) },
            LinearLayout.LayoutParams(
              HybridViewTokens.dp(context, 11f),
              HybridViewTokens.dp(context, 11f)
            ).apply { marginStart = HybridViewTokens.dp(context, 3f) }
          )
        }
        addView(
          openRow,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { topMargin = HybridViewTokens.dp(context, 6f) }
        )
      }
      container.addView(endCol)
    }

    fun bind(row: HybridSearchRow.SuraCard, palette: HybridViewPalette) {
      val hit = row.hit
      val ctx = container.context
      boundPage = hit.startPage
      boundSura = hit.sura
      container.tag = SearchTestTags.suraCard(hit.sura)

      if (lastPalette != palette) {
        lastPalette = palette
        cardBg.setFillColor(palette.cardSurface)
        cardBg.setStroke(strokePx, palette.cardHairlineBorder)
        cardBg.setPressColor(palette.ripple)
        HybridViewTokens.applyZLayerFloatation(
          view = container,
          zLevel = 1,
          isDark = palette.isDark,
          shadowTintColor = palette.shadowTint,
          radiusPx = cardRadiusPx
        )
        medallion.applyPalette(palette)
        titleView.setTextColor(palette.textPrimary)
        metaView.setTextColor(palette.textSecondary)
        juzDivider.setBackgroundColor(palette.divider)
        juzView.setTextColor(palette.textSecondary)
        pagePill.setTextColor(palette.accent)
        pagePillBg.setColor(palette.accentSoftBg)
        openText.setTextColor(palette.accent)
        openIcon.setTintColor(palette.accent)
      }

      medallion.setNumber(hit.sura)
      titleView.text = ctx.getString(R.string.hybrid_search_sura_card_title, hit.displayName)
      metaView.text = hit.metaString
      juzView.text = ctx.getString(
        R.string.hybrid_search_juz_label,
        QuranUtils.getLocalizedNumber(hit.juzNumber)
      )
      pagePill.text = ctx.getString(
        R.string.hybrid_search_page_pill,
        QuranUtils.getLocalizedNumber(hit.startPage)
      )
    }
  }

  private inner class TafsirViewHolder(
    context: Context,
    palette: HybridViewPalette
  ) : RecyclerView.ViewHolder(
    LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      val ph = HybridViewTokens.dp(context, 14f)
      val pv = HybridViewTokens.dp(context, 10f)
      setPadding(ph, pv, ph, pv)
      layoutParams = cardLayoutParams(context)
    }
  ) {
    private val container = itemView as LinearLayout
    private val refView = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    private val pagePill = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
      maxLines = 1
      val ph = HybridViewTokens.dp(context, 8f)
      val pv = HybridViewTokens.dp(context, 2f)
      setPadding(ph, pv, ph, pv)
    }
    private val translatorPill = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
      val ph = HybridViewTokens.dp(context, 8f)
      val pv = HybridViewTokens.dp(context, 2f)
      setPadding(ph, pv, ph, pv)
    }
    private val snippetView = StaticLayoutVerseView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
      setLineSpacing(0f, 1.35f)
      maxLines = 5
      ellipsize = TextUtils.TruncateAt.END
      textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }
    private val divider = View(context)
    private val actionBar = ActionBarViews(context, palette)

    private val strokePx = HybridViewTokens.dp(context, 1f)
    private val cardRadiusPx = HybridViewTokens.dpF(context, 18f)
    private val cardBg = HybridViewTokens.lightweightPressRoundedDrawable(
      fillColor = palette.cardSurface,
      strokeColor = palette.cardHairlineBorder,
      strokeWidthPx = strokePx,
      radiusPx = cardRadiusPx,
      pressColor = palette.ripple
    )
    private val pagePillBg = HybridViewTokens.roundedDrawable(
      fillColor = palette.accentSoftBg,
      radiusPx = HybridViewTokens.dpF(context, 999f)
    )
    private val translatorPillBg = HybridViewTokens.roundedDrawable(
      fillColor = palette.editorialSoftBg,
      radiusPx = HybridViewTokens.dpF(context, 999f)
    )
    private var lastPalette: HybridViewPalette? = null
    private var boundPage = 1
    private var boundSura = 1
    private var boundAyah = 1
    private val listenLambda: () -> Unit = { onPlayAyah(boundSura, boundAyah) }
    private val bookmarkLambda: () -> Unit = { onToggleBookmark(boundSura, boundAyah, boundPage) }
    private val openLambda: () -> Unit = { onOpenPage(boundPage, boundSura, boundAyah) }

    init {
      container.background = cardBg
      container.setOnClickListener { onOpenPage(boundPage, boundSura, boundAyah) }
      HybridViewTokens.attachApplePressFeedback(container, 0.985f)
      pagePill.background = pagePillBg
      translatorPill.background = translatorPillBg
      val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(refView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(
          pagePill,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { marginStart = HybridViewTokens.dp(context, 8f) }
        )
      }
      container.addView(headerRow)
      container.addView(
        translatorPill,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = HybridViewTokens.dp(context, 5f) }
      )
      container.addView(
        snippetView,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = HybridViewTokens.dp(context, 6f) }
      )
      container.addView(
        divider,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          HybridViewTokens.dp(context, 1f)
        ).apply {
          topMargin = HybridViewTokens.dp(context, 8f)
          bottomMargin = HybridViewTokens.dp(context, 4f)
        }
      )
      container.addView(actionBar.root)
    }

    fun bind(row: HybridSearchRow.TafsirCard, palette: HybridViewPalette) {
      val hit = row.hit
      val ctx = container.context
      boundPage = hit.page
      boundSura = hit.sura
      boundAyah = hit.ayah
      container.tag = SearchTestTags.tafsirCard(hit.sura, hit.ayah)

      if (lastPalette != palette) {
        lastPalette = palette
        cardBg.setFillColor(palette.cardSurface)
        cardBg.setStroke(strokePx, palette.cardHairlineBorder)
        cardBg.setPressColor(palette.ripple)
        HybridViewTokens.applyZLayerFloatation(
          view = container,
          zLevel = 1,
          isDark = palette.isDark,
          shadowTintColor = palette.shadowTint,
          radiusPx = cardRadiusPx
        )
        refView.setTextColor(palette.editorialAccent)
        pagePill.setTextColor(palette.accent)
        pagePillBg.setColor(palette.accentSoftBg)
        translatorPill.setTextColor(palette.editorialAccent)
        translatorPillBg.setColor(palette.editorialSoftBg)
        snippetView.setTextColor(palette.textPrimary)
        divider.setBackgroundColor(palette.divider)
      }

      refView.text = ctx.getString(
        R.string.hybrid_search_sura_ayah_header,
        hit.suraName,
        QuranUtils.getLocalizedNumber(hit.ayah)
      )

      pagePill.text = ctx.getString(
        R.string.hybrid_search_page_pill,
        QuranUtils.getLocalizedNumber(hit.page)
      )

      if (hit.translatorName.isNotBlank()) {
        translatorPill.visibility = View.VISIBLE
        translatorPill.text = hit.translatorName
      } else {
        translatorPill.visibility = View.GONE
      }

      val desiredDir = if (hit.isArabic) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_FIRST_STRONG
      if (snippetView.textDirection != desiredDir) {
        snippetView.textDirection = desiredDir
      }
      val snippetContent = HybridSearchHighlighter.buildForegroundHighlightedSpan(
        text = hit.text,
        ranges = hit.highlightRanges,
        highlightColor = palette.searchHighlight
      )
      snippetView.setPrebuiltContent(
        newText = snippetContent,
        layout = null,
        ranges = hit.highlightRanges,
        highlightBg = palette.searchHighlightBg
      )

      actionBar.bind(
        sura = hit.sura,
        ayah = hit.ayah,
        palette = palette,
        isBookmarked = row.isBookmarked,
        onListen = listenLambda,
        onBookmark = bookmarkLambda,
        onOpen = openLambda
      )
    }

    fun resetTransientMotion() {
      HybridViewTokens.resetApplePressFeedback(container)
      actionBar.resetTransientMotion()
    }
  }

  private inner class ActionViewHolder(
    context: Context,
    palette: HybridViewPalette
  ) : RecyclerView.ViewHolder(
    LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      val p = HybridViewTokens.dp(context, 16f)
      setPadding(p, p, p, p)
      layoutParams = cardLayoutParams(context)
    }
  ) {
    private val container = itemView as LinearLayout
    private val msgView = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      setLineSpacing(0f, 1.3f)
    }
    private val ctaButton = Button(context).apply {
      isAllCaps = false
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      val ph = HybridViewTokens.dp(context, 18f)
      val pv = HybridViewTokens.dp(context, 8f)
      setPadding(ph, pv, ph, pv)
      minHeight = 0
      minimumHeight = 0
    }

    init {
      HybridViewTokens.attachApplePressFeedback(ctaButton, AdaptiveMotionTokens.PRESS_SCALE)
      container.addView(msgView)
      container.addView(
        ctaButton,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = HybridViewTokens.dp(context, 12f) }
      )
    }

    fun resetTransientMotion() {
      HybridViewTokens.resetApplePressFeedback(ctaButton)
    }

    fun bind(row: HybridSearchRow.ActionCard, palette: HybridViewPalette) {
      val ctx = container.context
      val radiusPx = HybridViewTokens.dpF(ctx, 18f)
      container.background = HybridViewTokens.roundedDrawable(
        fillColor = palette.cardSurface,
        strokeColor = palette.cardHairlineBorder,
        strokeWidthPx = HybridViewTokens.dp(ctx, 1f),
        radiusPx = radiusPx
      )
      HybridViewTokens.applyZLayerFloatation(
        view = container,
        zLevel = 1,
        isDark = palette.isDark,
        shadowTintColor = palette.shadowTint,
        radiusPx = radiusPx
      )
      msgView.tag = SearchTestTags.WARNING_TEXT
      msgView.text = row.message
      msgView.setTextColor(palette.textPrimary)

      ctaButton.tag = SearchTestTags.ACTION_CTA_BUTTON
      ctaButton.text = row.ctaLabel
      ctaButton.setTextColor(palette.onCta)
      ctaButton.background = HybridViewTokens.lightweightPressRoundedDrawable(
        fillColor = palette.ctaBackground,
        radiusPx = HybridViewTokens.dpF(ctx, 999f),
        pressColor = palette.ripple
      )
      ctaButton.setOnClickListener {
        if (row.isTranslationCta) onOpenTranslationManager() else onDownloadArabicDb()
      }
    }
  }

  private inner class TafsirEmptyViewHolder(
    context: Context,
    palette: HybridViewPalette
  ) : RecyclerView.ViewHolder(
    LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      val p = HybridViewTokens.dp(context, 16f)
      setPadding(p, p, p, p)
      layoutParams = cardLayoutParams(context)
    }
  ) {
    private val container = itemView as LinearLayout
    private val bookIcon = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.BOOK,
      palette.editorialAccent,
      HybridViewTokens.dp(context, 20f)
    )
    private val titleView = TextView(context).apply {
      text = context.getString(R.string.hybrid_search_tafsir_empty_title)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bodyView = TextView(context).apply {
      text = context.getString(R.string.hybrid_search_tafsir_empty_body)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setLineSpacing(0f, 1.3f)
    }
    private val ctaButton = Button(context).apply {
      text = context.getString(R.string.hybrid_search_tafsir_download_cta)
      isAllCaps = false
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      val ph = HybridViewTokens.dp(context, 18f)
      val pv = HybridViewTokens.dp(context, 8f)
      setPadding(ph, pv, ph, pv)
      minHeight = 0
      minimumHeight = 0
    }

    init {
      HybridViewTokens.attachApplePressFeedback(ctaButton, AdaptiveMotionTokens.PRESS_SCALE)
      val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(context).apply { setImageDrawable(bookIcon) })
        addView(
          titleView,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { marginStart = HybridViewTokens.dp(context, 8f) }
        )
      }
      container.addView(header)
      container.addView(
        bodyView,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = HybridViewTokens.dp(context, 8f) }
      )
      container.addView(
        ctaButton,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = HybridViewTokens.dp(context, 10f) }
      )
    }

    fun resetTransientMotion() {
      HybridViewTokens.resetApplePressFeedback(ctaButton)
    }

    fun bind(palette: HybridViewPalette) {
      val ctx = container.context
      val radiusPx = HybridViewTokens.dpF(ctx, 18f)
      container.background = HybridViewTokens.roundedDrawable(
        fillColor = palette.cardSurface,
        strokeColor = palette.cardHairlineBorder,
        strokeWidthPx = HybridViewTokens.dp(ctx, 1f),
        radiusPx = radiusPx
      )
      HybridViewTokens.applyZLayerFloatation(
        view = container,
        zLevel = 1,
        isDark = palette.isDark,
        shadowTintColor = palette.shadowTint,
        radiusPx = radiusPx
      )
      bookIcon.setTintColor(palette.editorialAccent)
      titleView.tag = SearchTestTags.WARNING_TEXT
      titleView.setTextColor(palette.textPrimary)
      bodyView.setTextColor(palette.textSecondary)
      ctaButton.tag = SearchTestTags.ACTION_CTA_BUTTON
      ctaButton.setTextColor(palette.onCta)
      ctaButton.background = HybridViewTokens.lightweightPressRoundedDrawable(
        fillColor = palette.ctaBackground,
        radiusPx = HybridViewTokens.dpF(ctx, 999f),
        pressColor = palette.ripple
      )
      ctaButton.setOnClickListener { onOpenTranslationManager() }
    }
  }

  private inner class EmptyMessageViewHolder(
    context: Context,
    palette: HybridViewPalette
  ) : RecyclerView.ViewHolder(
    LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      val p = HybridViewTokens.dp(context, 16f)
      setPadding(p, p, p, p)
      layoutParams = cardLayoutParams(context)
    }
  ) {
    private val container = itemView as LinearLayout
    private val titleView = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subtitleView = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setLineSpacing(0f, 1.3f)
    }

    init {
      container.addView(titleView)
      container.addView(
        subtitleView,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = HybridViewTokens.dp(context, 6f) }
      )
    }

    fun bind(row: HybridSearchRow.EmptyMessageCard, palette: HybridViewPalette) {
      val ctx = container.context
      val radiusPx = HybridViewTokens.dpF(ctx, 18f)
      container.background = HybridViewTokens.roundedDrawable(
        fillColor = palette.cardSurface,
        strokeColor = palette.cardHairlineBorder,
        strokeWidthPx = HybridViewTokens.dp(ctx, 1f),
        radiusPx = radiusPx
      )
      HybridViewTokens.applyZLayerFloatation(
        view = container,
        zLevel = 1,
        isDark = palette.isDark,
        shadowTintColor = palette.shadowTint,
        radiusPx = radiusPx
      )
      titleView.text = row.title
      titleView.setTextColor(palette.textPrimary)
      if (!row.subtitle.isNullOrBlank()) {
        subtitleView.visibility = View.VISIBLE
        subtitleView.text = row.subtitle
        subtitleView.setTextColor(palette.textSecondary)
      } else {
        subtitleView.visibility = View.GONE
      }
    }
  }

  companion object {
    internal const val TYPE_AYAH = 1
    private const val TYPE_SURA = 2
    private const val TYPE_TAFSIR = 3
    private const val TYPE_ACTION = 4
    private const val TYPE_TAFSIR_EMPTY = 5
    private const val TYPE_EMPTY_MESSAGE = 6

    fun formatCountLocalized(count: Int): String {
      return NumberFormat.getIntegerInstance(Locale("ar")).format(count)
    }

    fun formatLatencyLocalized(latencyMs: Float): String {
      val formatted = String.format(Locale("ar"), "%.1f", latencyMs)
      return formatted
    }
  }
}
