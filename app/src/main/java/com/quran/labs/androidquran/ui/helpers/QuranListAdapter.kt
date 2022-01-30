package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import android.graphics.PorterDuff
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.util.set
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.util.LocaleUtil
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.JuzView
import com.quran.labs.androidquran.view.TagsViewGroup
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.collections.ArrayList

class QuranListAdapter(
  private val context: Context,
  private val recyclerView: RecyclerView,
  private var elements: Array<QuranRow>,
  private val isEditable: Boolean
) : RecyclerView.Adapter<QuranListAdapter.HeaderHolder>(),
  View.OnClickListener, View.OnLongClickListener {

  private val inflater = LayoutInflater.from(context)
  private val checkedState = SparseBooleanArray()
  private val locale = LocaleUtil.getLocale(context)
  private var tagMap: Map<Long, Tag> = emptyMap()
  private var showTags = false
  private var showDate = false

  private var touchListener: QuranTouchListener? = null

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderHolder {
    return if (viewType == 0) {
      HeaderHolder(inflater.inflate(R.layout.index_header_row, parent, false))
    } else {
      ViewHolder(inflater.inflate(R.layout.index_sura_row, parent, false))
    }
  }

  override fun onBindViewHolder(holder: HeaderHolder, position: Int) {
    val type = getItemViewType(position)
    return if (type == 0) bindHeader(holder, position) else bindRow(holder, position)
  }

  override fun getItemCount(): Int = elements.size

  override fun getItemId(position: Int): Long = position.toLong()

  override fun getItemViewType(position: Int): Int {
    return if (elements[position].isHeader) 0 else 1
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
    val count = checkedState.size()
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

  fun setElements(elements: Array<QuranRow>, tagMap: Map<Long, Tag>) {
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
      number.text = QuranUtils.getLocalizedNumber(context, item.sura)
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
          if (item.imageFilterColor == null) {
            image.colorFilter = null
          } else {
            image.setColorFilter(
              item.imageFilterColor, PorterDuff.Mode.SRC_ATOP
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
              tag?.let { tagList.add(it) }
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
    if (item.page == 0) {
      holder.pageNumber.visibility = View.GONE
    } else {
      holder.pageNumber.visibility = View.VISIBLE
      holder.pageNumber.text = QuranUtils.getLocalizedNumber(context, item.page)
    }
    holder.setChecked(isItemChecked(pos))
    holder.setEnabled(isEnabled(pos))
  }

  private fun isEnabled(position: Int): Boolean {
    val selected = elements[position]
    return !isEditable ||                     // anything in surahs or juzs
        selected.isBookmark ||                // actual bookmarks
        selected.rowType == QuranRow.NONE ||  // the actual "current page"
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

  interface QuranTouchListener {
    fun onClick(row: QuranRow, position: Int)
    fun onLongClick(row: QuranRow, position: Int): Boolean
  }
}
