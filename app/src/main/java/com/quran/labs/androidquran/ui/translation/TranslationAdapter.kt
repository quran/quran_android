package com.quran.labs.androidquran.ui.translation

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.common.search.SearchTextUtil
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.HighlightType
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.ui.helpers.ExpandFootnoteSpan
import com.quran.labs.androidquran.ui.helpers.ExpandTafseerSpan
import com.quran.labs.androidquran.ui.helpers.HighlightTypes
import com.quran.labs.androidquran.ui.helpers.UthmaniSpan
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.AyahNumberView
import com.quran.labs.androidquran.view.DividerView
import kotlin.math.ln1p
import kotlin.math.min

internal class TranslationAdapter(
  private val context: Context,
  private val recyclerView: RecyclerView,
  private val onClickListener: View.OnClickListener,
  private val onVerseSelectedListener: OnVerseSelectedListener,
  private val onJumpToVerseListener: OnJumpToAyahListener
) : RecyclerView.Adapter<TranslationAdapter.RowViewHolder>() {
  private val inflater: LayoutInflater = LayoutInflater.from(context)
  private val data: MutableList<TranslationViewRow> = mutableListOf()

  private var fontSize: Int = 0
  private var textColor: Int = 0
  private var footnoteColor: Int = 0
  private var inlineAyahColor: Int = 0
  private var dividerColor: Int = 0
  private var arabicTextColor: Int = 0
  private var suraHeaderColor: Int = 0
  private var ayahSelectionColor: Int = 0
  private var isNightMode: Boolean = false

  private var highlightedAyah: Int = 0
  private var highlightedRowCount: Int = 0
  private var highlightedStartPosition: Int = 0
  private var highlightType: HighlightType? = null

  private val expandedTafseerAyahs = mutableSetOf<Pair<Int, Int>>()
  private val expandedHyperlinks = mutableSetOf<Pair<Int, Int>>()
  private val expandedFootnotes = mutableMapOf<QuranAyahInfo, List<Int>>()

  private val defaultClickListener = View.OnClickListener { this.handleClick(it) }
  private val defaultLongClickListener = View.OnLongClickListener { this.selectVerseRows(it) }
  private val expandClickListener = View.OnClickListener { v -> toggleExpandTafseer(v) }
  private val expandHyperlinkClickListener = View.OnClickListener { v -> toggleTafseerJump(v) }

  fun getSelectedVersePopupPosition(): IntArray? {
    return if (highlightedStartPosition > -1) {
      val highlightedEndPosition = highlightedStartPosition + highlightedRowCount

      // find the row with the verse number
      val versePosition = data.withIndex().firstOrNull {
        it.index in highlightedStartPosition until highlightedEndPosition &&
            it.value.type == TranslationViewRow.Type.VERSE_NUMBER
      }

      // find out where to position the popup based on the center of the box
      versePosition?.let {
        positionForViewHolderIndex(versePosition.index)
      }
    } else {
      null
    }
  }

  fun getSelectedVersePopupPosition(sura: Int, ayah: Int): IntArray? {
    val (startPosition, _) = adapterInfoForAyah(sura, ayah)
    return if (startPosition > -1) {
      positionForViewHolderIndex(startPosition)
    } else {
      null
    }
  }

  private fun positionForViewHolderIndex(index: Int): IntArray? {
    val viewHolder = recyclerView.findViewHolderForAdapterPosition(index) as RowViewHolder?
    return viewHolder?.ayahNumber?.let { ayahNumberView ->
      val x = (ayahNumberView.left + ayahNumberView.boxCenterX)
      val y = (ayahNumberView.top + ayahNumberView.boxBottomY)
      intArrayOf(x, y)
    }
  }

  fun setData(data: List<TranslationViewRow>) {
    this.data.clear()
    expandedTafseerAyahs.clear()
    this.data.addAll(data)
    if (highlightedAyah > 0) {
      highlightAyah(highlightedAyah, true, highlightType ?: HighlightTypes.SELECTION, true)
    }
  }

  fun setHighlightedAyah(ayahId: Int, highlightType: HighlightType) {
    highlightAyah(ayahId, true, highlightType)
  }

  fun highlightedAyahInfo(): QuranAyahInfo? {
    return data.firstOrNull { it.ayahInfo.ayahId == highlightedAyah }?.ayahInfo
  }

  private fun adapterInfoForAyah(sura: Int, ayah: Int): Pair<Int, Int> {
    val matches =
      data.withIndex().filter {
        it.value.ayahInfo.sura == sura &&
            it.value.ayahInfo.ayah == ayah &&
            // don't factor in basmalah or sura name
            it.value.type > 1
      }
    return (matches.firstOrNull()?.index ?: -1) to matches.size
  }

  private fun highlightAyah(ayahId: Int, notify: Boolean, highlightedType: HighlightType, force: Boolean = false) {
    if (ayahId != highlightedAyah || force) {
      val matches = data.withIndex().filter { it.value.ayahInfo.ayahId == ayahId }
      val (startPosition, count) = (matches.firstOrNull()?.index ?: -1) to matches.size

      // highlight the newly highlighted ayah
      if (count > 0 && notify) {
        var startChangeCount = count
        var startChangeRange = startPosition
        if (highlightedRowCount > 0) {
          when {
            // merge the requests for notifyItemRangeChanged when we're either the next ayah
            highlightedStartPosition + highlightedRowCount + 1 == startPosition -> {
              startChangeRange = highlightedStartPosition
              startChangeCount += highlightedRowCount
            }
            // ... or when we're the previous ayah
            highlightedStartPosition - 1 == startPosition + count ->
              startChangeCount += highlightedRowCount
            else -> {
              // otherwise, unhighlight
              val start = highlightedStartPosition
              val changeCount = highlightedRowCount
              recyclerView.handler.post {
                notifyItemRangeChanged(start, changeCount, HIGHLIGHT_CHANGE)
              }
            }
          }
        }

        // and update rows to be highlighted
        recyclerView.handler.post {
          notifyItemRangeChanged(startChangeRange, startChangeCount, HIGHLIGHT_CHANGE)
          val layoutManager = recyclerView.layoutManager
          if ((force || highlightedType == HighlightTypes.AUDIO) && layoutManager is LinearLayoutManager) {
            layoutManager.scrollToPositionWithOffset(startPosition, 64)
          } else {
            recyclerView.smoothScrollToPosition(startPosition)
          }
        }
      }

      highlightedAyah = ayahId
      highlightedStartPosition = startPosition
      highlightedRowCount = count
      highlightType = highlightedType
    }
  }

  fun unhighlight() {
    if (highlightedAyah > 0 && highlightedRowCount > 0) {
      val start = highlightedStartPosition
      val count = highlightedRowCount
      recyclerView.handler.post {
        notifyItemRangeChanged(start, count)
      }
    }
    highlightedAyah = 0
    highlightedRowCount = 0
    highlightedStartPosition = -1
  }

  fun refresh(quranSettings: QuranSettings) {
    this.fontSize = quranSettings.translationTextSize
    isNightMode = quranSettings.isNightMode
    if (isNightMode) {
      val originalTextBrightness = quranSettings.nightModeTextBrightness
      val backgroundBrightness = quranSettings.nightModeBackgroundBrightness
      // avoid damaging the looks of the Quran page
      val adjustedBrightness = (50 * ln1p(backgroundBrightness.toDouble()) + originalTextBrightness).toInt()
      val textBrightness = min(adjustedBrightness.toFloat(), 255f).toInt()

      this.textColor = Color.rgb(textBrightness, textBrightness, textBrightness)
      this.footnoteColor = ContextCompat.getColor(context, R.color.translation_footnote_color)
      this.arabicTextColor = textColor
      this.dividerColor = textColor
      this.suraHeaderColor = ContextCompat.getColor(context, R.color.translation_sura_header_night)
      this.ayahSelectionColor = ContextCompat.getColor(context, R.color.translation_ayah_selected_color_night)
    } else {
      this.textColor = ContextCompat.getColor(context, R.color.translation_text_color)
      this.footnoteColor = ContextCompat.getColor(context, R.color.translation_footnote_color)
      this.dividerColor = ContextCompat.getColor(context, R.color.translation_divider_color)
      this.arabicTextColor = Color.BLACK
      this.suraHeaderColor = ContextCompat.getColor(context, R.color.translation_sura_header)
      this.ayahSelectionColor = ContextCompat.getColor(context, R.color.translation_ayah_selected_color)
    }
    this.inlineAyahColor = ContextCompat.getColor(context, R.color.translation_translator_color)

    if (this.data.isNotEmpty()) {
      notifyDataSetChanged()
    }
  }

  private fun handleClick(view: View) {
    val position = recyclerView.getChildAdapterPosition(view)
    if (highlightedAyah != 0 && position != RecyclerView.NO_POSITION) {
      val ayahInfo = data[position].ayahInfo
      if (ayahInfo.ayahId != highlightedAyah && highlightType == HighlightTypes.SELECTION) {
        onVerseSelectedListener.onVerseSelected(ayahInfo)
        return
      }
    }
    onClickListener.onClick(view)
  }

  private fun selectVerseRows(view: View): Boolean {
    val position = recyclerView.getChildAdapterPosition(view)
    if (position != RecyclerView.NO_POSITION) {
      val ayahInfo = data[position].ayahInfo
      highlightAyah(ayahInfo.ayahId, true, HighlightTypes.SELECTION)
      onVerseSelectedListener.onVerseSelected(ayahInfo)
      return true
    }
    return false
  }

  private fun toggleExpandTafseer(view: View) {
    val position = recyclerView.getChildAdapterPosition(view)
    if (position != RecyclerView.NO_POSITION) {
      val data = data[position]
      val what = data.ayahInfo.ayahId to data.translationIndex
      if (expandedTafseerAyahs.contains(what)) {
        expandedTafseerAyahs.remove(what)
      } else {
        expandedTafseerAyahs.add(what)
      }
      notifyItemChanged(position)
    }
  }

  private fun toggleTafseerJump(view: View) {
    val position = recyclerView.getChildAdapterPosition(view)
    if (position != RecyclerView.NO_POSITION) {
      val item = data[position]
      val targetAyah = item.link
      val targetPage = item.linkPage
      if (targetAyah != null && targetPage != null) {
        val match = data.indexOfFirst { it.ayahInfo.asSuraAyah() == targetAyah }
        if (match > -1) {
          recyclerView.smoothScrollToPosition(match)
        } else {
          // it's not on this page...
          onJumpToVerseListener.onJumpToAyah(targetAyah, targetPage)
        }
      }
    }
  }

  override fun getItemViewType(position: Int): Int {
    return data[position].type
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
    @LayoutRes val layout = when (viewType) {
      TranslationViewRow.Type.SURA_HEADER -> R.layout.quran_translation_header_row
      TranslationViewRow.Type.BASMALLAH, TranslationViewRow.Type.QURAN_TEXT ->
        R.layout.quran_translation_arabic_row
      TranslationViewRow.Type.SPACER -> R.layout.quran_translation_spacer_row
      TranslationViewRow.Type.VERSE_NUMBER -> R.layout.quran_translation_verse_number_row
      TranslationViewRow.Type.TRANSLATOR -> R.layout.quran_translation_translator_row
      else -> R.layout.quran_translation_text_row
    }

    val view = inflater.inflate(layout, parent, false)
    return RowViewHolder(view)
  }

  override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
    val row = data[position]
    when {
      // a row with text
      holder.text != null -> {
        // reset click listener on the text
        holder.text.setOnClickListener(defaultClickListener)

        val text: CharSequence?
        if (row.type == TranslationViewRow.Type.SURA_HEADER) {
          text = row.data
          holder.text.setBackgroundColor(suraHeaderColor)
        } else if (row.type == TranslationViewRow.Type.BASMALLAH || row.type == TranslationViewRow.Type.QURAN_TEXT) {
          val str = SpannableString(
            if (row.type == TranslationViewRow.Type.BASMALLAH) {
              ArabicDatabaseUtils.AR_BASMALLAH
            } else {
              ArabicDatabaseUtils.getAyahWithoutBasmallah(
                row.ayahInfo.sura, row.ayahInfo.ayah, row.ayahInfo.arabicText
              )
            }
          )
          str.setSpan(UthmaniSpan(context), 0, str.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

          text = str
          holder.text.setTextColor(arabicTextColor)
          holder.text.textSize = ARABIC_MULTIPLIER * fontSize
        } else {
          if (row.type == TranslationViewRow.Type.TRANSLATOR) {
            text = row.data
          } else {
            // translation
            text = row.data?.let { rowText ->
              val length = rowText.length
              val expandHyperlink =
                expandedHyperlinks.contains(row.ayahInfo.ayahId to row.translationIndex)

              if (row.link != null && !expandHyperlink) {
                holder.text.setOnClickListener(expandHyperlinkClickListener)
              }

              val spannableBuilder = SpannableStringBuilder(row.data)

              row.ayat.forEach { range ->
                val span = ForegroundColorSpan(inlineAyahColor)
                spannableBuilder.setSpan(span, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
              }

              val expandedFootnotes = expandedFootnotes[row.ayahInfo] ?: emptyList()
              val spannable = SpannableString(
                row.footnoteCognizantText(
                  spannableBuilder,
                  expandedFootnotes,
                  ::collapsedFootnoteSpan,
                  ::expandedFootnote
                )
              )

              when {
                row.link != null && !expandHyperlink -> getAyahLink(row.link)
                length > MAX_TAFSEER_LENGTH ->
                  truncateTextIfNeeded(spannable, row.ayahInfo.ayahId, row.translationIndex)
                else -> spannable
              }
            }

            // determine text directionality
            val isRtl = when {
              row.isArabic -> true
              text != null -> SearchTextUtil.isRtl(text.toString())
              else -> false
            }

            // reset the typeface
            holder.text.typeface = null

            if (isRtl) {
              // rtl tafseer, style it
              holder.text.layoutDirection = View.LAYOUT_DIRECTION_RTL

              // only allow this for Arabic though since the Arabic font isn't compatible
              // with other RTL languages that share some Arabic characters.
              if (row.isArabic) {
                holder.text.typeface = TypefaceManager.getTafseerTypeface(context)
              }
            } else {
              holder.text.layoutDirection = View.LAYOUT_DIRECTION_INHERIT
              val settings = QuranSettings.getInstance(context)
              if (settings.wantDyslexicFontInTranslationView()){
                holder.text.typeface = TypefaceManager.getDyslexicTypeface(context)
              }
            }

            holder.text.movementMethod = LinkMovementMethod.getInstance()
            holder.text.setTextColor(textColor)
            holder.text.textSize = fontSize.toFloat()
          }
        }
        holder.text.text = text
      }
      // a divider row
      holder.divider != null -> {
        var showLine = true
        if (position + 1 < data.size) {
          val nextRow = data[position + 1]
          if (nextRow.ayahInfo.sura != row.ayahInfo.sura) {
            showLine = false
          }
        } else {
          showLine = false
        }
        holder.divider.toggleLine(showLine)
        holder.divider.setDividerColor(dividerColor)
      }
      // ayah number row
      holder.ayahNumber != null -> {
        val text = context.getString(R.string.sura_ayah, row.ayahInfo.sura, row.ayahInfo.ayah)
        holder.ayahNumber.setAyahString(text)
        holder.ayahNumber.setTextColor(textColor)
        holder.ayahNumber.setNightMode(isNightMode)
      }
    }
    updateHighlight(row, holder)
  }

  private fun collapsedFootnoteSpan(number: Int): SpannableString {
    val text = QuranUtils.getLocalizedNumber(context, number)
    val spannable = SpannableString(text)
    spannable.setSpan(SuperscriptSpan(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    spannable.setSpan(RelativeSizeSpan(0.7f), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    spannable.setSpan(ExpandFootnoteSpan(number, ::expandFootnote), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    spannable.setSpan(ForegroundColorSpan(inlineAyahColor), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return spannable
  }

  private fun expandedFootnote(
    spannableStringBuilder: SpannableStringBuilder,
    start: Int,
    end: Int
  ): SpannableStringBuilder {
    val span = RelativeSizeSpan(0.7f)
    val colorSpan = ForegroundColorSpan(footnoteColor)
    spannableStringBuilder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    spannableStringBuilder.setSpan(colorSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return spannableStringBuilder
  }

  private fun expandFootnote(view: View, number: Int) {
    val position = recyclerView.getChildAdapterPosition(view)
    if (position != RecyclerView.NO_POSITION) {
      val data = data[position]
      val expanded = expandedFootnotes[data.ayahInfo] ?: listOf()
      expandedFootnotes[data.ayahInfo] = expanded + number
      notifyItemChanged(position)
    }
  }

  private fun getAyahLink(link: SuraAyah): CharSequence {
    return context.getString(R.string.see_tafseer_of_verse, link.ayah)
  }

  private fun truncateTextIfNeeded(
    text: CharSequence,
    ayahId: Int,
    translationIndex: Int
  ): CharSequence {
    if (text.length > MAX_TAFSEER_LENGTH &&
      !expandedTafseerAyahs.contains(ayahId to translationIndex)
    ) {
      // let's truncate
      val lastSpace = text.indexOf(' ', MAX_TAFSEER_LENGTH)
      if (lastSpace != -1) {
        return SpannableStringBuilder(text.subSequence(0, lastSpace + 1)).apply {
          append(context.getString(R.string.more_arabic))
          setSpan(
            ExpandTafseerSpan(expandClickListener),
            lastSpace + 1,
            this.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
          )
        }
      }
    }
    return text
  }

  override fun onBindViewHolder(holder: RowViewHolder, position: Int, payloads: List<Any>) {
    if (payloads.contains(HIGHLIGHT_CHANGE)) {
      updateHighlight(data[position], holder)
    } else {
      super.onBindViewHolder(holder, position, payloads)
    }
  }

  private fun updateHighlight(row: TranslationViewRow, holder: RowViewHolder) {
    // toggle highlighting of the ayah, but not for sura headers and basmallah
    val isHighlighted = row.ayahInfo.ayahId == highlightedAyah
    if (row.type != TranslationViewRow.Type.SURA_HEADER &&
      row.type != TranslationViewRow.Type.BASMALLAH &&
      row.type != TranslationViewRow.Type.SPACER
    ) {
      holder.wrapperView.setBackgroundColor(
        if (isHighlighted) ayahSelectionColor else 0
      )
    } else if (holder.divider != null) { // SPACER type
      if (isHighlighted) {
        holder.divider.highlight(ayahSelectionColor)
      } else {
        holder.divider.unhighlight()
      }
    }
  }

  override fun getItemCount(): Int = data.size

  internal inner class RowViewHolder(val wrapperView: View) : RecyclerView.ViewHolder(wrapperView) {
    val text: TextView? = wrapperView.findViewById(R.id.text)
    val divider: DividerView? = wrapperView.findViewById(R.id.divider)
    val ayahNumber: AyahNumberView? = wrapperView.findViewById(R.id.ayah_number)

    init {
      wrapperView.setOnClickListener(defaultClickListener)
      wrapperView.setOnLongClickListener(defaultLongClickListener)
    }
  }

  internal interface OnVerseSelectedListener {
    fun onVerseSelected(ayahInfo: QuranAyahInfo)
  }

  internal interface OnJumpToAyahListener {
    fun onJumpToAyah(target: SuraAyah, page: Int)
  }

  companion object {
    const val ARABIC_MULTIPLIER = 1.4f
    private const val MAX_TAFSEER_LENGTH = 750
    private const val HIGHLIGHT_CHANGE = 1
  }
}
