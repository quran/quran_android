package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.ui.HybridArchitecturalIcons
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.hybrid.ui.VectorPathDrawable
import com.quran.labs.androidquran.util.QuranUtils

internal data class BoundBookmarkBadgeVisuals(
  @ColorInt val iconTint: Int,
  @ColorInt val badgeBg: Int,
  val isFilled: Boolean
)

@ColorRes
internal fun highlightListColorRes(color: HighlightColor): Int = when (color) {
  HighlightColor.YELLOW -> R.color.highlight_list_yellow
  HighlightColor.GREEN -> R.color.highlight_list_green
  HighlightColor.BLUE -> R.color.highlight_list_blue
  HighlightColor.PINK -> R.color.highlight_list_pink
  HighlightColor.PURPLE -> R.color.highlight_list_purple
}

sealed interface HybridBookmarkRow {
  val stableKey: String

  data class SectionHeader(
    val key: String,
    val title: String,
    val showClearRecents: Boolean = false
  ) : HybridBookmarkRow {
    override val stableKey: String = "header_$key"
  }

  data class SavedCard(
    val key: String,
    val title: String,
    val subtitle: String,
    val page: Int,
    val sura: Int = 0,
    val ayah: Int = 0,
    val kind: Kind,
    val readingSlot: ReadingBookmarkType? = null,
    val slotLabel: String? = null,
    val highlightColor: HighlightColor? = null
  ) : HybridBookmarkRow {
    override val stableKey: String = key

    enum class Kind { READING, RECENT, AYAH, HIGHLIGHT }
  }

  data object EmptyCard : HybridBookmarkRow {
    override val stableKey: String = "bookmarks_empty"
  }
}

class HybridBookmarksView(
  context: Context,
  private val onOpenPage: (Int) -> Unit,
  private val onOpenAyah: (page: Int, sura: Int, ayah: Int) -> Unit,
  private val onClearReadingBookmark: (ReadingBookmarkType) -> Unit,
  private val onRemoveRecentPage: (Int) -> Unit,
  private val onClearAllRecents: () -> Unit,
  private val onRemoveAyahBookmark: (sura: Int, ayah: Int) -> Unit,
  private val onClearHighlight: (sura: Int, ayah: Int) -> Unit
) : FrameLayout(context) {

  private val recyclerView: RecyclerView
  private val adapter: BookmarksAdapter
  private var palette: HybridViewPalette? = null
  private var headerTopPaddingPx: Int = 0
  private var bottomInsetPx: Int = 0
  private var lastRenderedBookmarks: HubBookmarksUiState? = null

  init {
    layoutDirection = View.LAYOUT_DIRECTION_RTL
    clipChildren = false
    clipToPadding = false
    adapter = BookmarksAdapter()
    recyclerView = object : RecyclerView(context) {
      override fun hasOverlappingRendering(): Boolean = false
    }.apply {
      layoutManager = LinearLayoutManager(context)
      adapter = this@HybridBookmarksView.adapter
      clipChildren = false
      clipToPadding = false
      itemAnimator = null
    }
    applyRecyclerPadding()
    addView(
      recyclerView,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    )
  }

  override fun hasOverlappingRendering(): Boolean = false

  fun setHeaderTopPadding(headerHeightPx: Int) {
    if (headerTopPaddingPx != headerHeightPx) {
      headerTopPaddingPx = headerHeightPx
      applyRecyclerPadding()
    }
  }

  fun setBottomNavOverlapPadding(bottomPx: Int) {
    if (bottomInsetPx != bottomPx) {
      bottomInsetPx = bottomPx
      applyRecyclerPadding()
    }
  }

  private fun applyRecyclerPadding() {
    val hPad = HybridViewTokens.dp(context, 16f)
    val tPad = headerTopPaddingPx + HybridViewTokens.dp(context, 12f)
    val bPad = if (bottomInsetPx > 0) {
      bottomInsetPx + HybridViewTokens.dp(context, 14f)
    } else {
      HybridViewTokens.dp(context, 96f)
    }
    val wasAtTop = !recyclerView.canScrollVertically(-1)
    if (recyclerView.paddingLeft != hPad ||
      recyclerView.paddingTop != tPad ||
      recyclerView.paddingRight != hPad ||
      recyclerView.paddingBottom != bPad
    ) {
      recyclerView.setPadding(hPad, tPad, hPad, bPad)
      if (wasAtTop) {
        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
      }
    }
  }

  fun applyPalette(newPalette: HybridViewPalette) {
    if (palette != newPalette) {
      palette = newPalette
      background = HybridViewTokens.canvasAmbientGlowDrawable(
        surface = newPalette.surface,
        surfaceTopGlow = newPalette.surfaceTopGlow
      )
      adapter.updatePalette(newPalette)
    }
  }

  fun unbind() {
    recyclerView.adapter = null
    recyclerView.recycledViewPool.clear()
    lastRenderedBookmarks = null
  }

  fun render(uiState: HybridHubUiState) {
    renderIfChanged(uiState)
  }

  fun renderIfChanged(uiState: HybridHubUiState): Boolean {
    val bm = uiState.bookmarks
    if (lastRenderedBookmarks == bm) return false
    lastRenderedBookmarks = bm

    val rows = ArrayList<HybridBookmarkRow>()
    val reading = bm.readingBookmarks
    val recents = bm.recentPages
    val ayahs = bm.ayahBookmarks
    val highlights = bm.highlights

    if (bm.isEmpty) {
      rows.add(HybridBookmarkRow.EmptyCard)
      adapter.submitList(rows)
      return true
    }

    if (reading.isNotEmpty()) {
      rows.add(
        HybridBookmarkRow.SectionHeader(
          key = "reading",
          title = context.getString(R.string.hybrid_hub_saved_reading)
        )
      )
      reading.forEach { item ->
        val cardTitle = if (item.sura != null && item.ayah != null) {
          context.getString(
            R.string.hybrid_hub_ayah_ref_format,
            item.suraName,
            QuranUtils.getLocalizedNumber(item.ayah)
          )
        } else {
          item.suraName
        }
        rows.add(
          HybridBookmarkRow.SavedCard(
            key = "reading_${item.slot}_${item.page}",
            title = cardTitle,
            subtitle = context.getString(
              R.string.hybrid_hub_page_pill_format,
              QuranUtils.getLocalizedNumber(item.page)
            ),
            page = item.page,
            sura = item.sura ?: 0,
            ayah = item.ayah ?: 0,
            kind = HybridBookmarkRow.SavedCard.Kind.READING,
            readingSlot = item.slot,
            slotLabel = ReadingBookmarkSlots.displayName(context, item.slot, item.customName)
          )
        )
      }
    }

    if (recents.isNotEmpty()) {
      rows.add(
        HybridBookmarkRow.SectionHeader(
          key = "recents",
          title = context.getString(R.string.hybrid_hub_saved_recents),
          showClearRecents = true
        )
      )
      recents.forEach { item ->
        rows.add(
          HybridBookmarkRow.SavedCard(
            key = "recent_${item.page}",
            title = item.suraName,
            subtitle = context.getString(
              R.string.hybrid_hub_page_pill_format,
              QuranUtils.getLocalizedNumber(item.page)
            ),
            page = item.page,
            kind = HybridBookmarkRow.SavedCard.Kind.RECENT
          )
        )
      }
    }

    if (ayahs.isNotEmpty()) {
      rows.add(
        HybridBookmarkRow.SectionHeader(
          key = "ayahs",
          title = context.getString(R.string.hybrid_hub_saved_ayahs)
        )
      )
      ayahs.forEach { item ->
        rows.add(
          HybridBookmarkRow.SavedCard(
            key = "ayah_${item.sura}_${item.ayah}",
            title = context.getString(
              R.string.hybrid_hub_ayah_ref_format,
              item.suraName,
              QuranUtils.getLocalizedNumber(item.ayah)
            ),
            subtitle = context.getString(
              R.string.hybrid_hub_page_pill_format,
              QuranUtils.getLocalizedNumber(item.page)
            ),
            page = item.page,
            sura = item.sura,
            ayah = item.ayah,
            kind = HybridBookmarkRow.SavedCard.Kind.AYAH
          )
        )
      }
    }

    if (highlights.isNotEmpty()) {
      rows.add(
        HybridBookmarkRow.SectionHeader(
          key = "highlights",
          title = context.getString(R.string.hybrid_hub_saved_highlights)
        )
      )
      highlights.forEach { item ->
        rows.add(
          HybridBookmarkRow.SavedCard(
            key = "hl_${item.sura}_${item.ayah}",
            title = context.getString(
              R.string.hybrid_hub_ayah_ref_format,
              item.suraName,
              QuranUtils.getLocalizedNumber(item.ayah)
            ),
            subtitle = context.getString(
              R.string.hybrid_hub_page_pill_format,
              QuranUtils.getLocalizedNumber(item.page)
            ),
            page = item.page,
            sura = item.sura,
            ayah = item.ayah,
            kind = HybridBookmarkRow.SavedCard.Kind.HIGHLIGHT,
            highlightColor = item.color
          )
        )
      }
    }

    adapter.submitList(rows)
    return true
  }

  private inner class BookmarksAdapter :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<HybridBookmarkRow> = emptyList()
    private var activePalette: HybridViewPalette? = palette

    override fun getItemCount(): Int = rows.size

    private fun getItem(position: Int): HybridBookmarkRow = rows[position]

    fun submitList(newRows: List<HybridBookmarkRow>) {
      if (rows == newRows) return
      val oldRows = rows
      rows = newRows
      if (oldRows.isEmpty()) {
        if (newRows.isNotEmpty()) {
          notifyItemRangeInserted(0, newRows.size)
        }
        return
      }
      if (newRows.isEmpty()) {
        notifyItemRangeRemoved(0, oldRows.size)
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

    fun updatePalette(newPalette: HybridViewPalette) {
      if (activePalette != newPalette) {
        activePalette = newPalette
        if (itemCount > 0) {
          notifyDataSetChanged()
        }
      }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
      is HybridBookmarkRow.SectionHeader -> 1
      is HybridBookmarkRow.SavedCard -> 2
      HybridBookmarkRow.EmptyCard -> 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
      val ctx = parent.context
      return when (viewType) {
        1 -> HeaderHolder(ctx)
        2 -> CardHolder(ctx)
        3 -> EmptyHolder(ctx)
        else -> error("Unknown type $viewType")
      }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      val p = activePalette ?: palette ?: return
      when (val row = getItem(position)) {
        is HybridBookmarkRow.SectionHeader -> (holder as HeaderHolder).bind(row, p)
        is HybridBookmarkRow.SavedCard -> (holder as CardHolder).bind(row, p)
        HybridBookmarkRow.EmptyCard -> (holder as EmptyHolder).bind(p)
      }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
      HybridViewTokens.resetApplePressFeedback(holder.itemView)
      if (holder is CardHolder) {
        holder.resetTransientMotion()
      }
      super.onViewRecycled(holder)
    }

    private inner class HeaderHolder(context: Context) : RecyclerView.ViewHolder(
      LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(0, HybridViewTokens.dp(context, 10f), 0, HybridViewTokens.dp(context, 6f))
        layoutParams = RecyclerView.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
      }
    ) {
      private val container = itemView as LinearLayout
      private val titleView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      }
      private val clearBtn = TextView(context).apply {
        text = context.getString(R.string.hybrid_hub_saved_clear_recents)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        isClickable = true
        isFocusable = true
        val ph = HybridViewTokens.dp(context, 8f)
        val pv = HybridViewTokens.dp(context, 4f)
        setPadding(ph, pv, ph, pv)
      }

      private val clearBtnBg = HybridViewTokens.lightweightPressRoundedDrawable(
        fillColor = Color.TRANSPARENT,
        radiusPx = HybridViewTokens.dpF(context, 999f),
        pressColor = Color.LTGRAY
      )
      private var lastPalette: HybridViewPalette? = null

      init {
        clearBtn.background = clearBtnBg
        container.addView(
          titleView,
          LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        container.addView(clearBtn)
      }

      fun bind(row: HybridBookmarkRow.SectionHeader, p: HybridViewPalette) {
        if (titleView.text != row.title) {
          titleView.text = row.title
        }
        if (lastPalette != p) {
          lastPalette = p
          titleView.setTextColor(p.textSecondary)
          clearBtn.setTextColor(p.accent)
          clearBtnBg.setPressColor(p.ripple)
        }
        if (row.showClearRecents) {
          clearBtn.visibility = View.VISIBLE
          clearBtn.setOnClickListener { onClearAllRecents() }
        } else {
          clearBtn.visibility = View.GONE
        }
      }
    }

    private inner class CardHolder(context: Context) : RecyclerView.ViewHolder(
      object : LinearLayout(context) {
        override fun hasOverlappingRendering(): Boolean = false
      }.apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        val ph = HybridViewTokens.dp(context, 14f)
        val pv = HybridViewTokens.dp(context, 12f)
        setPadding(ph, pv, ph, pv)
        isClickable = true
        isFocusable = true
        layoutParams = RecyclerView.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = HybridViewTokens.dp(context, 8f) }
      }
    ) {
      private val card = itemView as LinearLayout
      private val bookmarkFilledDrawable = HybridArchitecturalIcons.drawable(
        HybridArchitecturalIcons.BOOKMARK_FILLED,
        Color.GRAY,
        HybridViewTokens.dp(context, 16f)
      )
      private val bookmarkOutlineDrawable = HybridArchitecturalIcons.drawable(
        HybridArchitecturalIcons.BOOKMARK,
        Color.GRAY,
        HybridViewTokens.dp(context, 16f)
      )
      private val iconBadgeBg = HybridViewTokens.circleDrawable(Color.TRANSPARENT)
      private val iconBadgeView = ImageView(context).apply {
        setImageDrawable(bookmarkFilledDrawable)
        scaleType = ImageView.ScaleType.CENTER
        background = iconBadgeBg
      }
      private val titleView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
      }
      private val metaView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
      }
      private val textColumn = object : LinearLayout(context) {
        override fun hasOverlappingRendering(): Boolean = false
      }.apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        addView(
          titleView,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          )
        )
        addView(
          metaView,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { topMargin = HybridViewTokens.dp(context, 2f) }
        )
      }
      private val pagePill = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        maxLines = 1
        val ph = HybridViewTokens.dp(context, 10f)
        val pv = HybridViewTokens.dp(context, 3f)
        setPadding(ph, pv, ph, pv)
      }
      private val removeDrawable = HybridArchitecturalIcons.drawable(
        HybridArchitecturalIcons.CLOSE,
        Color.GRAY,
        HybridViewTokens.dp(context, 15f)
      )
      private val removeBtn = ImageView(context).apply {
        setImageDrawable(removeDrawable)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.hybrid_hub_saved_remove_desc)
        isClickable = true
        isFocusable = true
      }

      private val strokePx = HybridViewTokens.dp(context, 1f)
      private val cardRadiusPx = HybridViewTokens.dpF(context, 16f)
      private val cardBg = HybridViewTokens.lightweightPressRoundedDrawable(
        fillColor = Color.TRANSPARENT,
        strokeColor = Color.TRANSPARENT,
        strokeWidthPx = strokePx,
        radiusPx = cardRadiusPx,
        pressColor = Color.LTGRAY
      )
      private val pagePillBg = HybridViewTokens.roundedDrawable(
        fillColor = Color.TRANSPARENT,
        radiusPx = HybridViewTokens.dpF(context, 999f)
      )
      private val removeBtnBg = HybridViewTokens.lightweightPressCircleDrawable(
        fillColor = Color.TRANSPARENT,
        pressColor = Color.LTGRAY
      )
      private var lastPalette: HybridViewPalette? = null
      private var lastFilledIcon: Boolean? = null
      private var lastBadgeBgColor: Int? = null
      private var lastFilledTintColor: Int? = null
      private var lastOutlineTintColor: Int? = null

      init {
        card.background = cardBg
        pagePill.background = pagePillBg
        removeBtn.background = removeBtnBg
        HybridViewTokens.attachApplePressFeedback(card, 0.985f)
        HybridViewTokens.attachApplePressFeedback(removeBtn, AdaptiveMotionTokens.ICON_PRESS_SCALE)
        val badgeSize = HybridViewTokens.dp(context, 32f)
        card.addView(
          iconBadgeView,
          LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
            marginEnd = HybridViewTokens.dp(context, 10f)
          }
        )
        card.addView(
          textColumn,
          LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = HybridViewTokens.dp(context, 8f)
          }
        )
        card.addView(pagePill)
        val btnSize = HybridViewTokens.dp(context, 34f)
        card.addView(
          removeBtn,
          LinearLayout.LayoutParams(btnSize, btnSize).apply {
            marginStart = HybridViewTokens.dp(context, 6f)
          }
        )
      }

      fun resetTransientMotion() {
        HybridViewTokens.resetApplePressFeedback(card)
        HybridViewTokens.resetApplePressFeedback(removeBtn)
      }

      fun bind(row: HybridBookmarkRow.SavedCard, p: HybridViewPalette) {
        if (lastPalette != p) {
          lastPalette = p
          cardBg.setFillColor(p.cardSurface)
          cardBg.setStroke(strokePx, p.cardHairlineBorder)
          cardBg.setPressColor(p.ripple)
          titleView.setTextColor(p.textPrimary)
          pagePill.setTextColor(p.accent)
          pagePillBg.setColor(p.accentSoftBg)
          removeDrawable.setTintColor(p.textSecondary)
          removeBtnBg.setPressColor(p.ripple)
        }

        // Resolve per-row badge/icon colors on EVERY bind so recycled ViewHolders
        // never leak colors across TEAL/ORANGE/RED/AYAH/RECENT/HIGHLIGHT rows.
        val useFilled = row.kind != HybridBookmarkRow.SavedCard.Kind.RECENT
        val (iconTint, badgeBg) = when (row.kind) {
          HybridBookmarkRow.SavedCard.Kind.READING -> {
            val slot = row.readingSlot
            if (slot != null) {
              val slotColor = ContextCompat.getColor(
                card.context,
                ReadingBookmarkSlots[slot].colorResourceId
              )
              slotColor to ColorUtils.setAlphaComponent(slotColor, if (p.isDark) 46 else 36)
            } else {
              p.editorialAccent to p.editorialSoftBg
            }
          }
          HybridBookmarkRow.SavedCard.Kind.HIGHLIGHT -> {
            val hlColorEnum = row.highlightColor
            if (hlColorEnum != null) {
              val hlColor = ContextCompat.getColor(card.context, highlightListColorRes(hlColorEnum))
              hlColor to ColorUtils.setAlphaComponent(hlColor, if (p.isDark) 46 else 36)
            } else {
              p.editorialAccent to p.editorialSoftBg
            }
          }
          HybridBookmarkRow.SavedCard.Kind.AYAH -> {
            p.editorialAccent to p.editorialSoftBg
          }
          HybridBookmarkRow.SavedCard.Kind.RECENT -> {
            p.textSecondary to p.surfaceVariant
          }
        }

        if (lastBadgeBgColor != badgeBg) {
          lastBadgeBgColor = badgeBg
          iconBadgeBg.setColor(badgeBg)
        }
        if (useFilled) {
          if (lastFilledTintColor != iconTint) {
            lastFilledTintColor = iconTint
            bookmarkFilledDrawable.setTintColor(iconTint)
          }
        } else {
          if (lastOutlineTintColor != iconTint) {
            lastOutlineTintColor = iconTint
            bookmarkOutlineDrawable.setTintColor(iconTint)
          }
        }
        if (lastFilledIcon != useFilled) {
          lastFilledIcon = useFilled
          iconBadgeView.setImageDrawable(
            if (useFilled) bookmarkFilledDrawable else bookmarkOutlineDrawable
          )
        }
        iconBadgeView.tag = BoundBookmarkBadgeVisuals(
          iconTint = iconTint,
          badgeBg = badgeBg,
          isFilled = useFilled
        )

        if (titleView.text != row.title) {
          titleView.text = row.title
        }
        if (!row.slotLabel.isNullOrBlank()) {
          if (metaView.visibility != View.VISIBLE) {
            metaView.visibility = View.VISIBLE
          }
          if (metaView.text != row.slotLabel) {
            metaView.text = row.slotLabel
          }
          metaView.setTextColor(iconTint)
        } else if (metaView.visibility != View.GONE) {
          metaView.visibility = View.GONE
        }
        if (pagePill.text != row.subtitle) {
          pagePill.text = row.subtitle
        }

        card.setOnClickListener {
          if (row.sura > 0 && row.ayah > 0) {
            onOpenAyah(row.page, row.sura, row.ayah)
          } else {
            onOpenPage(row.page)
          }
        }
        removeBtn.setOnClickListener {
          when (row.kind) {
            HybridBookmarkRow.SavedCard.Kind.READING -> row.readingSlot?.let(onClearReadingBookmark)
            HybridBookmarkRow.SavedCard.Kind.RECENT -> onRemoveRecentPage(row.page)
            HybridBookmarkRow.SavedCard.Kind.AYAH -> onRemoveAyahBookmark(row.sura, row.ayah)
            HybridBookmarkRow.SavedCard.Kind.HIGHLIGHT -> onClearHighlight(row.sura, row.ayah)
          }
        }
      }
    }

    private inner class EmptyHolder(context: Context) : RecyclerView.ViewHolder(
      object : LinearLayout(context) {
        override fun hasOverlappingRendering(): Boolean = false
      }.apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        val p = HybridViewTokens.dp(context, 24f)
        setPadding(p, p, p, p)
        layoutParams = RecyclerView.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = HybridViewTokens.dp(context, 20f) }
      }
    ) {
      private val card = itemView as LinearLayout
      private val emptyRadiusPx = HybridViewTokens.dpF(context, 20f)
      private val iconDrawable = HybridArchitecturalIcons.drawable(
        HybridArchitecturalIcons.BOOKMARK,
        Color.GRAY,
        HybridViewTokens.dp(context, 26f)
      )
      private val iconCircleBg = HybridViewTokens.circleDrawable(Color.TRANSPARENT)
      private val iconCircleView = ImageView(context).apply {
        setImageDrawable(iconDrawable)
        scaleType = ImageView.ScaleType.CENTER
        background = iconCircleBg
      }
      private val titleView = TextView(context).apply {
        text = context.getString(R.string.hybrid_hub_saved_empty_title)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
      }
      private val subtitleView = TextView(context).apply {
        text = context.getString(R.string.hybrid_hub_saved_empty_subtitle)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.3f)
      }

      private val emptyCardBg = HybridViewTokens.roundedDrawable(
        fillColor = Color.TRANSPARENT,
        strokeColor = Color.TRANSPARENT,
        strokeWidthPx = HybridViewTokens.dp(context, 1f),
        radiusPx = emptyRadiusPx
      )
      private val emptyStrokePx = HybridViewTokens.dp(context, 1f)
      private var lastPalette: HybridViewPalette? = null

      init {
        card.background = emptyCardBg
        val circleSize = HybridViewTokens.dp(context, 48f)
        card.addView(iconCircleView, LinearLayout.LayoutParams(circleSize, circleSize))
        card.addView(
          titleView,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { topMargin = HybridViewTokens.dp(context, 10f) }
        )
        card.addView(
          subtitleView,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { topMargin = HybridViewTokens.dp(context, 6f) }
        )
      }

      fun bind(p: HybridViewPalette) {
        if (lastPalette != p) {
          lastPalette = p
          emptyCardBg.setColor(p.cardSurface)
          emptyCardBg.setStroke(emptyStrokePx, p.cardHairlineBorder)
          HybridViewTokens.applyZLayerFloatation(
            view = card,
            zLevel = 1,
            isDark = p.isDark,
            shadowTintColor = p.shadowTint,
            radiusPx = emptyRadiusPx
          )
          iconCircleBg.setColor(p.editorialSoftBg)
          iconDrawable.setTintColor(p.editorialAccent)
          titleView.setTextColor(p.textPrimary)
          subtitleView.setTextColor(p.textSecondary)
        }
      }
    }
  }
}
