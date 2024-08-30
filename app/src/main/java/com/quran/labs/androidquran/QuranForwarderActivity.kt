package com.quran.labs.androidquran

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.ui.PagerActivity
import javax.inject.Inject

class QuranForwarderActivity : Activity() {
  @Inject
  lateinit var quranInfo: QuranInfo

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)

    // handle urls of type quran://sura/ayah
    val intent = intent
    if (intent != null) {
      val data = intent.data
      if (data != null) {
        val urlString = data.toString()
        val pieces = urlString.split("/".toRegex()).toTypedArray()

        var sura: Int? = null
        var ayah = 1
        for (s in pieces) {
          try {
            val i = s.toInt()
            if (sura == null) {
              sura = i
            } else {
              ayah = i
              break
            }
          } catch (nfe: NumberFormatException) {
            // leave it as null
          }
        }

        if (sura != null) {
          val page = quranInfo.getPageFromSuraAyah(sura, ayah)
          val showSuraIntent = Intent(
            this,
            PagerActivity::class.java
          )
          showSuraIntent.putExtra("page", page)
          showSuraIntent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura)
          showSuraIntent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah)
          startActivity(showSuraIntent)
        }
      }
    }
    finish()
  }
}
