package com.quran.labs.androidquran.ui.fragment

import com.quran.data.model.SuraAyah
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.quran.labs.androidquran.ui.PagerActivity

abstract class AyahActionFragment : Fragment() {
  protected var start: SuraAyah? = null
  protected var end: SuraAyah? = null

  private var justCreated = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    justCreated = true
  }

  override fun onResume() {
    super.onResume()
    if (justCreated) {
      justCreated = false
      (activity as? PagerActivity)?.let { activity ->
        start = activity.selectionStart
        end = activity.selectionEnd
        refreshView()
      }
    }
  }

  fun updateAyahSelection(start: SuraAyah?, end: SuraAyah?) {
    this.start = start
    this.end = end
    refreshView()
  }

  protected abstract fun refreshView()
}
