package com.quran.labs.androidquran.pageselect

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper
import io.reactivex.Maybe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.lang.ref.WeakReference

class PageSelectAdapter(val inflater: LayoutInflater, val width: Int) : PagerAdapter() {
  private val items : MutableList<PageTypeItem> = mutableListOf()
  private val compositeDisposable = CompositeDisposable()

  fun replaceItems(updates: List<PageTypeItem>, pager: ViewPager) {
    items.clear()
    items.addAll(updates)
    items.forEach {
      val view : View? = pager.findViewWithTag(it.pageType)
      if (view != null) {
        updateView(view, it)
      }
    }
    notifyDataSetChanged()
  }

  override fun getCount() = items.size

  override fun isViewFromObject(view: View, obj: Any): Boolean {
    return obj === view
  }

  private fun updateView(view: View, data: PageTypeItem) {
    view.findViewById<TextView>(R.id.title).setText(data.title)
    view.findViewById<TextView>(R.id.description).setText(data.description)

    val image = view.findViewById<ImageView>(R.id.preview)
    if (data.previewImage != null) {
      readImage(data.previewImage.path, WeakReference(image))
    } else {
      image.setImageBitmap(null)
    }
  }

  private fun readImage(path: String, imageRef: WeakReference<ImageView>) {
    compositeDisposable.add(
        Maybe.fromCallable {
          val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ALPHA_8 }
          BitmapFactory.decodeFile(path, options)
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe( { imageRef.get()?.setImageBitmap(it) })
    )
  }

  fun cleanUp() {
    compositeDisposable.clear()
  }

  override fun instantiateItem(container: ViewGroup, position: Int): Any {
    val view = inflater.inflate(R.layout.page_select_page, container, false)
    val item = items[position]
    updateView(view, item)
    view.tag = item.pageType

    view.setBackgroundDrawable(QuranDisplayHelper.getPaintDrawable(0, width))
    container.addView(view)
    return view
  }

  override fun destroyItem(container: ViewGroup, position: Int, o: Any) {
    super.destroyItem(container, position, o)
    container.removeView(o as View)
  }
}
