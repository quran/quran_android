package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.DailyWirdState
import com.quran.labs.androidquran.hybrid.ui.HybridArchitecturalIcons
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.hybrid.ui.RubElHizbView
import com.quran.labs.androidquran.hybrid.ui.VectorPathDrawable
import com.quran.labs.androidquran.util.QuranUtils

sealed interface HybridIndexRow {
  val stableKey: String

  data class Hero(
    val dailyWird: DailyWirdState,
    val lastSuraName: String,
    val lastJuz: Int
  ) : HybridIndexRow {
    override val stableKey: String = "hero_wird"
  }

  data class SegmentBar(
    val selectedSegment: HubIndexSegment
  ) : HybridIndexRow {
    override val stableKey: String = "segment_bar"
  }

  data class SurahRow(
    val item: SuraIndexItem
  ) : HybridIndexRow {
    override val stableKey: String = "sura_${item.sura}"
  }

  data class JuzRow(
    val item: JuzIndexItem
  ) : HybridIndexRow {
    override val stableKey: String = "juz_${item.juz}"
  }

  data class HizbRow(
    val item: HizbIndexItem
  ) : HybridIndexRow {
    override val stableKey: String = "hizb_${item.hizb}"
  }
}

class HybridIndexAdapter(
  private val onContinueReading: () -> Unit,
  private val onOpenWirdGoal: () -> Unit,
  private val onSegmentSelected: (HubIndexSegment) -> Unit,
  private val onOpenPage: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  private var palette: HybridViewPalette? = null
  private var rows: List<HybridIndexRow> = emptyList()
  private var currentSegment: HubIndexSegment? = null

  fun submitRows(newRows: List<HybridIndexRow>, segment: HubIndexSegment) {
    val oldRows = rows
    if (oldRows == newRows) return
    val prevSegment = currentSegment
    currentSegment = segment
    rows = newRows

    if (prevSegment != segment || oldRows.isEmpty()) {
      notifyDataSetChanged()
      return
    }

    if (newRows.size > oldRows.size &&
      oldRows.indices.all { idx -> newRows[idx] == oldRows[idx] }
    ) {
      notifyItemRangeInserted(oldRows.size, newRows.size - oldRows.size)
      return
    }

    if (oldRows.size == newRows.size &&
      oldRows.size > 1 &&
      oldRows[0] != newRows[0] &&
      oldRows[1] == newRows[1] &&
      (2 until oldRows.size).all { idx -> oldRows[idx] === newRows[idx] }
    ) {
      notifyItemChanged(0)
      return
    }

    val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
      override fun getOldListSize(): Int = oldRows.size
      override fun getNewListSize(): Int = newRows.size
      override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldRows[oldItemPosition].stableKey == newRows[newItemPosition].stableKey
      }
      override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldRows[oldItemPosition] == newRows[newItemPosition]
      }
    })
    diff.dispatchUpdatesTo(this)
  }

  override fun getItemCount(): Int = rows.size

  fun updatePalette(newPalette: HybridViewPalette) {
    if (palette != newPalette) {
      palette = newPalette
      notifyDataSetChanged()
    }
  }

  override fun getItemViewType(position: Int): Int {
    return when (rows[position]) {
      is HybridIndexRow.Hero -> TYPE_HERO
      is HybridIndexRow.SegmentBar -> TYPE_SEGMENT_BAR
      is HybridIndexRow.SurahRow -> TYPE_SURA
      is HybridIndexRow.JuzRow -> TYPE_JUZ
      is HybridIndexRow.HizbRow -> TYPE_HIZB
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val ctx = parent.context
    val p = palette ?: error("Palette must be set before creating HybridIndexAdapter holders")
    return when (viewType) {
      TYPE_HERO -> HeroViewHolder(ctx, p)
      TYPE_SEGMENT_BAR -> SegmentBarViewHolder(ctx)
      TYPE_SURA -> SurahViewHolder(ctx)
      TYPE_JUZ -> JuzViewHolder(ctx)
      TYPE_HIZB -> HizbViewHolder(ctx)
      else -> error("Unknown viewType $viewType")
    }
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    val p = palette ?: return
    when (val row = rows[position]) {
      is HybridIndexRow.Hero -> (holder as HeroViewHolder).bind(row, p)
      is HybridIndexRow.SegmentBar -> (holder as SegmentBarViewHolder).bind(row, p)
      is HybridIndexRow.SurahRow -> (holder as SurahViewHolder).bind(row, p)
      is HybridIndexRow.JuzRow -> (holder as JuzViewHolder).bind(row, p)
      is HybridIndexRow.HizbRow -> (holder as HizbViewHolder).bind(row, p)
    }
  }

  override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
    HybridViewTokens.resetApplePressFeedback(holder.itemView)
    when (holder) {
      is HeroViewHolder -> holder.resetTransientMotion()
      is SegmentBarViewHolder -> holder.resetTransientMotion()
    }
    super.onViewRecycled(holder)
  }

  private fun rowLayoutParams(context: Context, bottomDp: Float = 8f): RecyclerView.LayoutParams {
    return RecyclerView.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
      bottomMargin = HybridViewTokens.dp(context, bottomDp)
    }
  }

  private inner class HeroViewHolder(
    context: Context,
    initialPalette: HybridViewPalette
  ) : RecyclerView.ViewHolder(
    LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      val p = HybridViewTokens.dp(context, 16f)
      setPadding(p, p, p, HybridViewTokens.dp(context, 14f))
      layoutParams = rowLayoutParams(context, bottomDp = 12f)
    }
  ) {
    private val card = itemView as LinearLayout
    private val cardRadiusPx = HybridViewTokens.dpF(context, 22f)
    private val pillRadiusPx = HybridViewTokens.dpF(context, 999f)
    private val strokePx = HybridViewTokens.dp(context, 1f)
    private val overlineView = TextView(context).apply {
      text = context.getString(R.string.hybrid_hub_continue_reading)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
    }
    private val surahTitleView = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    private val metaView = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    private val ctaTextView = TextView(context).apply {
      text = context.getString(R.string.hybrid_hub_resume_cta)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
    }
    private val ctaArrow = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.CHEVRON_LEFT,
      initialPalette.onCta,
      HybridViewTokens.dp(context, 13f)
    )
    private val ctaPill = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      isClickable = true
      isFocusable = true
      minimumHeight = HybridViewTokens.dp(context, 36f)
      val ph = HybridViewTokens.dp(context, 14f)
      val pv = HybridViewTokens.dp(context, 7f)
      setPadding(ph, pv, ph, pv)
      addView(ctaTextView)
      addView(
        ImageView(context).apply { setImageDrawable(ctaArrow) },
        LinearLayout.LayoutParams(
          HybridViewTokens.dp(context, 13f),
          HybridViewTokens.dp(context, 13f)
        ).apply { marginStart = HybridViewTokens.dp(context, 4f) }
      )
    }

    private val divider = View(context)
    private val wirdPercentView = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
    }
    private val wirdPagesView = TextView(context).apply {
      id = R.id.daily_wird_progress
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      maxLines = 1
    }
    private val progressBarView = View(context)
    private val wirdRow = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      isClickable = true
      isFocusable = true
      val pv = HybridViewTokens.dp(context, 4f)
      setPadding(0, pv, 0, pv)
    }

    private var lastPalette: HybridViewPalette? = null
    private val ctaBgDrawable = HybridViewTokens.lightweightPressRoundedDrawable(
      fillColor = initialPalette.ctaBackground,
      radiusPx = pillRadiusPx,
      pressColor = initialPalette.ripple
    )
    private val progressFillDrawable = HybridViewTokens.roundedDrawable(
      initialPalette.editorialAccent,
      radiusPx = pillRadiusPx
    )
    private val progressClip = ClipDrawable(progressFillDrawable, Gravity.START, ClipDrawable.HORIZONTAL)

    init {
      ctaPill.background = ctaBgDrawable
      ctaPill.setOnClickListener { onContinueReading() }
      HybridViewTokens.attachApplePressFeedback(ctaPill, AdaptiveMotionTokens.PRESS_SCALE)
      wirdRow.setOnClickListener { onOpenWirdGoal() }
      HybridViewTokens.attachApplePressFeedback(wirdRow, 0.985f)
      val topRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        val leftCol = LinearLayout(context).apply {
          orientation = LinearLayout.VERTICAL
          addView(overlineView)
          addView(
            surahTitleView,
            LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = HybridViewTokens.dp(context, 3f) }
          )
          addView(
            metaView,
            LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = HybridViewTokens.dp(context, 3f) }
          )
        }
        addView(
          leftCol,
          LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = HybridViewTokens.dp(context, 12f)
          }
        )
        addView(ctaPill)
      }
      card.addView(topRow)
      card.addView(
        divider,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          HybridViewTokens.dp(context, 1f)
        ).apply {
          topMargin = HybridViewTokens.dp(context, 12f)
          bottomMargin = HybridViewTokens.dp(context, 10f)
        }
      )

      val wirdHeader = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
          wirdPercentView,
          LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(wirdPagesView)
      }
      wirdRow.addView(wirdHeader)
      wirdRow.addView(
        progressBarView,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          HybridViewTokens.dp(context, 7f)
        ).apply { topMargin = HybridViewTokens.dp(context, 7f) }
      )
      card.addView(wirdRow)
    }

    fun resetTransientMotion() {
      HybridViewTokens.resetApplePressFeedback(ctaPill)
      HybridViewTokens.resetApplePressFeedback(wirdRow)
    }

    fun bind(row: HybridIndexRow.Hero, palette: HybridViewPalette) {
      val ctx = card.context
      if (lastPalette != palette) {
        lastPalette = palette
        card.background = HybridViewTokens.floatingSurfaceDrawable(
          fillColor = palette.cardSurface,
          borderColor = palette.cardHairlineBorder,
          specularTopColor = palette.glassRimHighlight,
          strokeWidthPx = strokePx,
          radiusPx = cardRadiusPx,
          topGradientColor = if (palette.isDark) palette.surfaceElevated else Color.WHITE
        )
        HybridViewTokens.applyZLayerFloatation(
          view = card,
          zLevel = 2,
          isDark = palette.isDark,
          shadowTintColor = palette.shadowTint,
          radiusPx = cardRadiusPx
        )
        overlineView.setTextColor(palette.editorialAccent)
        surahTitleView.setTextColor(palette.textPrimary)
        metaView.setTextColor(palette.textSecondary)
        ctaTextView.setTextColor(palette.onCta)
        ctaArrow.setTintColor(palette.onCta)
        ctaBgDrawable.setFillColor(palette.ctaBackground)
        ctaBgDrawable.setPressColor(palette.ripple)
        HybridViewTokens.applyZLayerFloatation(
          view = ctaPill,
          zLevel = 2,
          isDark = palette.isDark,
          shadowTintColor = palette.ctaBackground,
          radiusPx = pillRadiusPx
        )
        divider.setBackgroundColor(palette.divider)
        wirdPercentView.setTextColor(palette.textPrimary)
        wirdPagesView.setTextColor(palette.textSecondary)
        progressFillDrawable.setColor(palette.editorialAccent)
        val trackInnerShadow = ColorUtils.setAlphaComponent(
          palette.shadowTint,
          if (palette.isDark) 72 else 22
        )
        val trackBg = HybridViewTokens.recessedTrackDrawable(
          trackColor = palette.surfaceVariant,
          innerShadowColor = trackInnerShadow,
          borderColor = Color.TRANSPARENT,
          radiusPx = pillRadiusPx
        )
        progressBarView.background = LayerDrawable(arrayOf<Drawable>(trackBg, progressClip))
      }

      surahTitleView.text = row.lastSuraName

      val juzLabel = ctx.getString(
        R.string.hybrid_hub_juz_format,
        QuranUtils.getLocalizedNumber(row.lastJuz)
      )
      val pageLabel = ctx.getString(
        R.string.hybrid_hub_page_full_format,
        QuranUtils.getLocalizedNumber(row.dailyWird.lastPage)
      )
      metaView.text = "$juzLabel — $pageLabel"

      val goal = row.dailyWird.goalPages.coerceAtLeast(1)
      val read = row.dailyWird.pagesReadToday.coerceAtLeast(0)
      val pct = ((read * 100f) / goal).toInt().coerceIn(0, 100)
      val pctText = "${QuranUtils.getLocalizedNumber(pct)}%"
      val fullWirdLabel = ctx.getString(R.string.hybrid_hub_daily_wird_percent_format, pctText)
      val pctIdx = fullWirdLabel.indexOf('(')
      wirdPercentView.text = if (pctIdx >= 0) {
        SpannableString(fullWirdLabel).apply {
          setSpan(
            ForegroundColorSpan(palette.editorialAccent),
            pctIdx,
            fullWirdLabel.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
          )
        }
      } else {
        fullWirdLabel
      }

      wirdPagesView.text = ctx.getString(
        R.string.hybrid_hub_wird_pages_format,
        QuranUtils.getLocalizedNumber(read),
        QuranUtils.getLocalizedNumber(goal)
      )

      val level = (pct * 100).coerceIn(if (read > 0) 400 else 0, 10000)
      if (progressClip.level != level) {
        progressClip.level = level
      }
    }
  }

  private inner class SegmentBarViewHolder(
    context: Context
  ) : RecyclerView.ViewHolder(
    LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      val p = HybridViewTokens.dp(context, 4f)
      setPadding(p, p, p, p)
      layoutParams = rowLayoutParams(context, bottomDp = 12f)
    }
  ) {
    private val container = itemView as LinearLayout
    private val tabs = LinkedHashMap<HubIndexSegment, TextView>()
    private val inactiveTabBgs = LinkedHashMap<HubIndexSegment, HybridViewTokens.LightweightPressColorDrawable>()
    private val railRadius = HybridViewTokens.dpF(context, 999f)
    private val strokePx = HybridViewTokens.dp(context, 1f)
    private var lastPalette: HybridViewPalette? = null
    private var lastSegment: HubIndexSegment? = null

    init {
      val items = listOf(
        Triple(HubIndexSegment.SURAS, R.string.hybrid_hub_tab_suras, "hub_segment_suras"),
        Triple(HubIndexSegment.JUZ, R.string.hybrid_hub_tab_juz, "hub_segment_juz"),
        Triple(HubIndexSegment.HIZB, R.string.hybrid_hub_tab_hizb, "hub_segment_hizb")
      )
      items.forEach { (segment, titleRes, tagValue) ->
        val bg = HybridViewTokens.lightweightPressRoundedDrawable(
          fillColor = Color.TRANSPARENT,
          radiusPx = railRadius,
          pressColor = Color.TRANSPARENT
        )
        inactiveTabBgs[segment] = bg
        val tv = TextView(context).apply {
          tag = tagValue
          text = context.getString(titleRes)
          setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
          gravity = Gravity.CENTER
          maxLines = 1
          isClickable = true
          isFocusable = true
          minimumHeight = HybridViewTokens.dp(context, 36f)
          background = bg
          setOnClickListener { onSegmentSelected(segment) }
          HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
        }
        tabs[segment] = tv
        container.addView(
          tv,
          LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
      }
    }

    fun resetTransientMotion() {
      tabs.values.forEach { HybridViewTokens.resetApplePressFeedback(it) }
    }

    fun bind(row: HybridIndexRow.SegmentBar, palette: HybridViewPalette) {
      if (lastPalette == palette && lastSegment == row.selectedSegment) return
      val paletteChanged = lastPalette != palette
      lastPalette = palette
      lastSegment = row.selectedSegment
      if (paletteChanged) {
        val innerShadow = ColorUtils.setAlphaComponent(
          palette.shadowTint,
          if (palette.isDark) 90 else 22
        )
        container.background = HybridViewTokens.recessedTrackDrawable(
          trackColor = if (palette.isDark) palette.surface else palette.surfaceVariant,
          innerShadowColor = innerShadow,
          borderColor = palette.cardHairlineBorder,
          radiusPx = railRadius
        )
      }
      tabs.forEach { (segment, tv) ->
        val selected = segment == row.selectedSegment
        tv.isSelected = selected
        tv.setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
        tv.setTextColor(if (selected) palette.accent else palette.textSecondary)
        val inactiveBg = inactiveTabBgs.getValue(segment)
        if (paletteChanged) {
          inactiveBg.setPressColor(palette.ripple)
        }
        if (selected) {
          tv.background = HybridViewTokens.floatingSurfaceDrawable(
            fillColor = palette.surfaceElevated,
            borderColor = palette.cardHairlineBorder,
            specularTopColor = palette.glassRimHighlight,
            strokeWidthPx = strokePx,
            radiusPx = railRadius
          )
          HybridViewTokens.applyZLayerFloatation(
            view = tv,
            zLevel = 2,
            isDark = palette.isDark,
            shadowTintColor = palette.shadowTint,
            radiusPx = railRadius
          )
        } else {
          tv.background = inactiveBg
          HybridViewTokens.applyZLayerFloatation(
            view = tv,
            zLevel = 0,
            isDark = palette.isDark,
            shadowTintColor = palette.shadowTint,
            radiusPx = railRadius
          )
        }
      }
    }
  }

  private open inner class MedallionCardViewHolder(
    context: Context
  ) : RecyclerView.ViewHolder(
    LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      val ph = HybridViewTokens.dp(context, 14f)
      val pv = HybridViewTokens.dp(context, 12f)
      setPadding(ph, pv, ph, pv)
      isClickable = true
      isFocusable = true
      layoutParams = rowLayoutParams(context, bottomDp = 8f)
    }
  ) {
    protected val card = itemView as LinearLayout
    protected val medallion = RubElHizbView(context).apply { setSizeDp(38f) }
    protected val titleView = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    protected val subtitleView = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
    }
    protected val pagePill = TextView(context).apply {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      val ph = HybridViewTokens.dp(context, 10f)
      val pv = HybridViewTokens.dp(context, 4f)
      setPadding(ph, pv, ph, pv)
    }

    private val strokePx = HybridViewTokens.dp(context, 1f)
    private val cardRadiusPx = HybridViewTokens.dpF(context, 16f)
    private val cardBg = HybridViewTokens.lightweightPressRoundedDrawable(
      fillColor = Color.TRANSPARENT,
      strokeColor = Color.TRANSPARENT,
      strokeWidthPx = strokePx,
      radiusPx = cardRadiusPx,
      pressColor = Color.TRANSPARENT
    )
    private val pagePillBg = HybridViewTokens.roundedDrawable(
      fillColor = Color.TRANSPARENT,
      radiusPx = HybridViewTokens.dpF(context, 999f)
    )
    private var lastPalette: HybridViewPalette? = null
    private var boundPage: Int = 1

    init {
      card.background = cardBg
      card.setOnClickListener { onOpenPage(boundPage) }
      HybridViewTokens.attachApplePressFeedback(card, 0.985f)
      pagePill.background = pagePillBg
      card.addView(medallion)
      val textCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(titleView)
        addView(
          subtitleView,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { topMargin = HybridViewTokens.dp(context, 2f) }
        )
      }
      card.addView(
        textCol,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          marginStart = HybridViewTokens.dp(context, 12f)
          marginEnd = HybridViewTokens.dp(context, 8f)
        }
      )
      card.addView(pagePill)
    }

    protected fun bindCommon(
      number: Int,
      title: String,
      subtitle: String,
      page: Int,
      palette: HybridViewPalette
    ) {
      val ctx = card.context
      boundPage = page
      if (lastPalette != palette) {
        lastPalette = palette
        cardBg.setFillColor(palette.cardSurface)
        cardBg.setStroke(strokePx, palette.cardHairlineBorder)
        cardBg.setPressColor(palette.ripple)
        HybridViewTokens.applyZLayerFloatation(
          view = card,
          zLevel = 1,
          isDark = palette.isDark,
          shadowTintColor = palette.shadowTint,
          radiusPx = cardRadiusPx
        )
        medallion.applyPalette(palette)
        titleView.setTextColor(palette.textPrimary)
        subtitleView.setTextColor(palette.textSecondary)
        pagePill.setTextColor(palette.accent)
        pagePillBg.setColor(palette.accentSoftBg)
      }

      medallion.setNumber(number)
      titleView.text = title
      subtitleView.text = subtitle
      pagePill.text = ctx.getString(
        R.string.hybrid_hub_page_pill_format,
        QuranUtils.getLocalizedNumber(page)
      )
    }
  }

  private inner class SurahViewHolder(context: Context) : MedallionCardViewHolder(context) {
    fun bind(row: HybridIndexRow.SurahRow, palette: HybridViewPalette) {
      val item = row.item
      bindCommon(item.sura, item.name, item.metaText, item.startPage, palette)
    }
  }

  private inner class JuzViewHolder(context: Context) : MedallionCardViewHolder(context) {
    fun bind(row: HybridIndexRow.JuzRow, palette: HybridViewPalette) {
      val item = row.item
      val ctx = card.context
      val title = ctx.getString(
        R.string.hybrid_hub_juz_format,
        QuranUtils.getLocalizedNumber(item.juz)
      )
      bindCommon(item.juz, title, item.startSuraName, item.startPage, palette)
    }
  }

  private inner class HizbViewHolder(context: Context) : MedallionCardViewHolder(context) {
    fun bind(row: HybridIndexRow.HizbRow, palette: HybridViewPalette) {
      val item = row.item
      val ctx = card.context
      val title = ctx.getString(
        R.string.hybrid_hub_hizb_format,
        QuranUtils.getLocalizedNumber(item.hizb)
      )
      val juzLabel = ctx.getString(
        R.string.hybrid_hub_juz_format,
        QuranUtils.getLocalizedNumber(item.juz)
      )
      bindCommon(item.hizb, title, "$juzLabel — ${item.startSuraName}", item.startPage, palette)
    }
  }

  companion object {
    private const val TYPE_HERO = 1
    private const val TYPE_SEGMENT_BAR = 2
    private const val TYPE_SURA = 3
    private const val TYPE_JUZ = 4
    private const val TYPE_HIZB = 5

    private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HybridIndexRow>() {
      override fun areItemsTheSame(oldItem: HybridIndexRow, newItem: HybridIndexRow): Boolean {
        return oldItem.stableKey == newItem.stableKey
      }

      override fun areContentsTheSame(oldItem: HybridIndexRow, newItem: HybridIndexRow): Boolean {
        return oldItem == newItem
      }
    }
  }
}
