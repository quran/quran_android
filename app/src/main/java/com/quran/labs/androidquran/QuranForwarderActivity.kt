package com.quran.labs.androidquran

import android.app.Activity
import android.os.Bundle
import com.quran.data.core.QuranInfo
import dev.zacsweers.metro.Inject

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
        // the authority and the path segments, rather than the whole uri split
        // on "/": a query string would otherwise be left inside the last piece
        // ("255?play=true"), fail to parse as a number, and send the reader to
        // the first ayah of the sura with no error anywhere.
        val numbers = (listOfNotNull(data.authority) + data.pathSegments)
          .mapNotNull { it.toIntOrNull() }
        val sura = numbers.firstOrNull()
        val ayah = numbers.getOrNull(1) ?: 1

        if (sura != null) {
          val page = quranInfo.getPageFromSuraAyah(sura, ayah)
          val showSuraIntent = QuranDataActivity.openPageIntent(
            context = this,
            page = page,
            sura = sura,
            ayah = ayah
          )
          startActivity(showSuraIntent)
        }
      }
    }
    finish()
  }
}
