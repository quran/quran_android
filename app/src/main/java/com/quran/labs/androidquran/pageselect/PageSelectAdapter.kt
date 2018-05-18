package com.quran.labs.androidquran.pageselect

import android.support.v4.view.PagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.quran.labs.androidquran.R

class PageSelectAdapter(val inflater: LayoutInflater) : PagerAdapter() {
  private val items : MutableList<PageTypeItem> = mutableListOf()

  fun replaceItems(updates: List<PageTypeItem>) {
    items.clear()
    items.addAll(updates)
    notifyDataSetChanged()
  }

  override fun getCount() = items.size

  override fun isViewFromObject(view: View, obj: Any): Boolean {
    return obj === view
  }

  override fun instantiateItem(container: ViewGroup, position: Int): Any {
    val view = inflater.inflate(R.layout.page_select_page, container, false)
    view.findViewById<TextView>(R.id.title).setText(items[position].title)
    view.findViewById<TextView>(R.id.description).setText(items[position].description)
    container.addView(view)
    return view
  }
}
