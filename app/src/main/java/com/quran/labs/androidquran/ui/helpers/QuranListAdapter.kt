package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import android.graphics.PorterDuff
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.util.set
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.CollectionNames
import com.quran.labs.androidquran.common.ui.core.HighlightColors
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.JuzView
import com.quran.labs.androidquran.view.TagsViewGroup
import java.text.SimpleDateFormat
import java.util.Date
import androidx.core.util.size

class QuranListAdapter(
  private val context: Context,
  private val recyclerView: RecyclerView,
  private var elements: Array<QuranRow>,
  private val isEditable: Boolean
) : RecyclerView.Adapter<QuranListAdapter.HeaderHolder>(),
  View.OnClickListener, View.OnLongClickListener {

  private val inflater = LayoutInflater.from(context)
  private val checkedState = SparseBooleanArray()
  private val locale = QuranUtils.getCurrentLocale()
  private var tagMap: Map<String, Tag> = emptyMap()
  private var showTags = false
  private var showDate = false

  private var touchListener: QuranTouchListener? = null

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderHolder {
    return when (viewType) {
      VIEW_TYPE_HEADER ->
        HeaderHolder(inflater.inflate(R.layout.index_header_row, parent, false))

      VIEW_TYPE_COLLECTION_HEADER ->
        CollectionHeaderHolder(
          inflater.inflate(R.layout.index_collection_header_row, parent, false)
        )

      VIEW_TYPE_HIGHLIGHT_COLOR ->
        HighlightColorHolder(
          inflater.inflate(R.layout.index_highlight_color_row, parent, false)
        )

      VIEW_TYPE_HIGHLIGHTED_AYAH ->
        HighlightedAyahHolder(
          inflater.inflate(R.layout.index_highlighted_ayah_row, parent, false)
        )

      else -> ViewHolder(inflater.inflate(R.layout.index_sura_row, parent, false))
    }
  }

  override fun onBindViewHolder(holder: HeaderHolder, position: Int) {
    when (getItemViewType(position)) {
      VIEW_TYPE_HEADER -> bindHeader(holder, position)
      VIEW_TYPE_COLLECTION_HEADER -> bindCollectionHeader(holder as CollectionHeaderHolder, position)
      VIEW_TYPE_HIGHLIGHT_COLOR -> bindHighlightColor(holder as HighlightColorHolder, position)
      VIEW_TYPE_HIGHLIGHTED_AYAH -> bindHighlightedAyah(holder as HighlightedAyahHolder, position)
      else -> bindRow(holder, position)
    }
  }

  override fun getItemCount(): Int = elements.size

  override fun getItemId(position: Int): Long = position.toLong()

  override fun getItemViewType(position: Int): Int {
    val element = elements[position]
    return when {
      element.isBookmarkHeader -> VIEW_TYPE_COLLECTION_HEADER
      element.isHeader -> VIEW_TYPE_HEADER
      element.isHighlightColor -> VIEW_TYPE_HIGHLIGHT_COLOR
      element.isHighlightedAyah -> VIEW_TYPE_HIGHLIGHTED_AYAH
      else -> VIEW_TYPE_ROW
    }
  }

  override fun onClick(v: View) {
    val position = recyclerView.getChildAdapterPosition(v)
    if (position != RecyclerView.NO_POSITION) {
      val element = elements[position]
      if (touchListener == null) {
        (context as QuranActivity).jumpTo(element.page)
      } else {
        touchListener?.onClick(element, position)
      }
    }
  }

  override fun onLongClick(v: View): Boolean {
    touchListener?.let { listener ->
      val position = recyclerView.getChildAdapterPosition(v)
      if (position != RecyclerView.NO_POSITION) {
        return listener.onLongClick(elements[position], position)
      }
    }
    return false
  }

  fun setElements(elements: Array<QuranRow>) {
    this.elements = elements
    notifyDataSetChanged()
  }

  fun isItemChecked(position: Int): Boolean = checkedState[position]

  fun setItemChecked(position: Int, checked: Boolean) {
    checkedState[position] = checked
    notifyItemChanged(position)
  }

  fun uncheckAll() {
    checkedState.clear()
    notifyDataSetChanged()
  }

  fun getCheckedItems(): List<QuranRow> {
    val result = ArrayList<QuranRow>()
    val count = checkedState.size
    val elements = itemCount
    for (i in 0 until count) {
      val key = checkedState.keyAt(i)
      // TODO: figure out why sometimes elements > key
      if (checkedState[key] && elements > key) {
        result.add(getQuranRow(key))
      }
    }
    return result
  }

  fun setQuranTouchListener(listener: QuranTouchListener) {
    touchListener = listener
  }

  fun setElements(elements: Array<QuranRow>, tagMap: Map<String, Tag>) {
    this.elements = elements
    this.tagMap = tagMap
  }

  fun setShowTags(showTags: Boolean) {
    this.showTags = showTags
  }

  fun setShowDate(showDate: Boolean) {
    this.showDate = showDate
  }

  private fun getQuranRow(position: Int): QuranRow = elements[position]

  private fun bindRow(vh: HeaderHolder, position: Int) {
    val holder = vh as ViewHolder
    bindHeader(vh, position)
    val item = elements[position]

    with(holder) {
      number.text = QuranUtils.getLocalizedNumber(item.sura)
      metadata.visibility = View.VISIBLE
      metadata.text = item.metadata
      tags.visibility = View.GONE


      when {
        item.juzType != null -> {
          image.setImageDrawable(
            JuzView(context, item.juzType, item.juzOverlayText)
          )
          image.visibility = View.VISIBLE
          number.visibility = View.GONE
        }
        item.imageResource == null -> {
          number.visibility = View.VISIBLE
          image.visibility = View.GONE
        }
        else -> {
          image.setImageResource(item.imageResource)
          if (item.imageFilterColorResource == null) {
            image.colorFilter = null
          } else {
            image.setColorFilter(
              ContextCompat.getColor(context, item.imageFilterColorResource), PorterDuff.Mode.SRC_ATOP
            )
          }

          if (showDate) {
            val date = SimpleDateFormat("MMM dd, HH:mm", locale)
              .format(Date(item.dateAddedInMillis))
            holder.metadata.text = buildString {
              append(item.metadata)
              append(" - ")
              append(date)
            }
          }

          image.visibility = View.VISIBLE
          number.visibility = View.GONE

          val tagList = ArrayList<Tag>()
          val bookmark = item.bookmark
          if (bookmark != null && bookmark.tags.isNotEmpty() && showTags) {
            for (i in 0 until bookmark.tags.size) {
              val tagId = bookmark.tags[i]
              val tag = tagMap[tagId]
              tag?.let { tagList.add(it.copy(name = CollectionNames.displayName(context, it))) }
            }
          }

          if (tagList.isEmpty()) {
            tags.visibility = View.GONE
          } else {
            tags.setTags(tagList)
            tags.visibility = View.VISIBLE
          }
        }
      }
    }
  }

  private fun bindHeader(holder: HeaderHolder, pos: Int) {
    val item = elements[pos]
    holder.title.text = item.text
    val trailingNumber = item.itemCount ?: item.page
    if (trailingNumber == 0) {
      holder.pageNumber.visibility = View.GONE
    } else {
      holder.pageNumber.visibility = View.VISIBLE
      holder.pageNumber.text = QuranUtils.getLocalizedNumber(trailingNumber)
    }
    holder.setChecked(isItemChecked(pos))
    holder.setEnabled(isEnabled(pos))
  }

  private fun bindCollectionHeader(holder: CollectionHeaderHolder, pos: Int) {
    val item = elements[pos]
    holder.title.text = item.text

    val itemCount = item.itemCount
    if (itemCount == null) {
      holder.pageNumber.visibility = View.GONE
    } else {
      holder.pageNumber.visibility = View.VISIBLE
      holder.pageNumber.text = QuranUtils.getLocalizedNumber(itemCount)
    }

    if (item.isCollapsible) {
      holder.expandIcon.visibility = View.VISIBLE
      holder.expandIcon.setImageResource(
        if (item.isCollapsed) R.drawable.ic_expand_more_24 else R.drawable.ic_expand_less_24
      )
      holder.expandIcon.contentDescription = context.getString(
        if (item.isCollapsed) R.string.expand_collection else R.string.collapse_collection
      )
    } else {
      holder.expandIcon.visibility = View.INVISIBLE
    }

    holder.openIcon.visibility = if (item.tagId == null) View.GONE else View.VISIBLE
    holder.setChecked(isItemChecked(pos))
    holder.setEnabled(isEnabled(pos))
  }

  private fun bindHighlightColor(holder: HighlightColorHolder, pos: Int) {
    val item = elements[pos]
    val color = item.highlightColor
    holder.title.text = item.text

    val count = item.itemCount ?: 0
    holder.pageNumber.visibility = View.VISIBLE
    holder.pageNumber.text = QuranUtils.getLocalizedNumber(count)

    if (color != null) {
      holder.swatch.setColorFilter(HighlightColors[color].color.toArgb(), PorterDuff.Mode.SRC_ATOP)
    }

    // a color with nothing in it stays in the list, but dims rather than shouting
    val alpha = if (count == 0) EMPTY_COLOR_ALPHA else 1f
    holder.swatch.alpha = alpha
    holder.title.alpha = alpha
    holder.pageNumber.alpha = alpha

    holder.setChecked(isItemChecked(pos))
    holder.setEnabled(isEnabled(pos))
  }

  private fun bindHighlightedAyah(holder: HighlightedAyahHolder, pos: Int) {
    val item = elements[pos]
    holder.title.text = item.text
    holder.metadata.text = item.metadata
    holder.pageNumber.visibility = View.VISIBLE
    holder.pageNumber.text = QuranUtils.getLocalizedNumber(item.page)

    val color = item.highlightColor
    if (color != null) {
      holder.colorRail.setBackgroundColor(HighlightColors[color].color.toArgb())
    }

    holder.setChecked(isItemChecked(pos))
    holder.setEnabled(isEnabled(pos))
  }

  private fun isEnabled(position: Int): Boolean {
    val selected = elements[position]
    return !isEditable ||                     // anything in surahs or juzs
        selected.isBookmark ||                // actual bookmarks
        selected.isReadingBookmark ||         // the non-editable reading bookmark shortcut
        selected.rowType == QuranRow.NONE ||  // the actual "current page"
        selected.isHighlightColor ||          // the five highlight colors
        selected.isHighlightedAyah ||         // a highlighted ayah inside a color's screen
        selected.isBookmarkHeader             // tags
  }

  open inner class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val view: View = itemView
    val title: TextView = itemView.findViewById(R.id.title)
    val pageNumber: TextView = itemView.findViewById(R.id.pageNumber)

    fun setEnabled(enabled: Boolean) {
      view.isEnabled = true
      itemView.setOnClickListener(
        if (enabled) this@QuranListAdapter else null
      )
      itemView.setOnLongClickListener(
        if (isEditable && enabled) this@QuranListAdapter else null
      )
    }

    fun setChecked(checked: Boolean) {
      view.isActivated = checked
    }
  }

  private inner class ViewHolder(itemView: View) : HeaderHolder(itemView) {
    val metadata: TextView = itemView.findViewById(R.id.metadata)
    val number: TextView = itemView.findViewById(R.id.suraNumber)
    val image: ImageView = itemView.findViewById(R.id.rowIcon)
    val tags: TagsViewGroup = itemView.findViewById(R.id.tags)
    val date: TextView? = itemView.findViewById(R.id.show_date)
  }

  private inner class CollectionHeaderHolder(itemView: View) : HeaderHolder(itemView) {
    val expandIcon: ImageView = itemView.findViewById(R.id.expandIcon)
    val openIcon: ImageView = itemView.findViewById<ImageView>(R.id.openIcon).also { icon ->
      icon.setOnClickListener {
        val position = bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
          touchListener?.onOpenClicked(elements[position], position)
        }
      }
    }
  }

  private inner class HighlightColorHolder(itemView: View) : HeaderHolder(itemView) {
    val swatch: ImageView = itemView.findViewById(R.id.rowIcon)
  }

  private inner class HighlightedAyahHolder(itemView: View) : HeaderHolder(itemView) {
    val metadata: TextView = itemView.findViewById(R.id.metadata)
    val colorRail: View = itemView.findViewById(R.id.colorRail)
  }

  interface QuranTouchListener {
    fun onClick(row: QuranRow, position: Int)
    fun onLongClick(row: QuranRow, position: Int): Boolean
    fun onOpenClicked(row: QuranRow, position: Int) = Unit
  }

  private companion object {
    private const val VIEW_TYPE_HEADER = 0
    private const val VIEW_TYPE_ROW = 1
    private const val VIEW_TYPE_COLLECTION_HEADER = 2
    private const val VIEW_TYPE_HIGHLIGHT_COLOR = 3
    private const val VIEW_TYPE_HIGHLIGHTED_AYAH = 4
    private const val EMPTY_COLOR_ALPHA = 0.5f
  }
}
