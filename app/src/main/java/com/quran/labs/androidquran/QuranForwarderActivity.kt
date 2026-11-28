package com.quran.labs.androidquran

import android.app.Activity
import android.os.Bundle
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.common.audio.util.QariUtil
import dev.zacsweers.metro.Inject

class QuranForwarderActivity : Activity() {
  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var qariUtil: QariUtil

  @Inject
  lateinit var currentQariManager: CurrentQariManager

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

        // a link may also name the reciter, by the recitation id quran.com uses,
        // so one link means the same voice wherever it is opened. A recitation
        // that is not one of ours leaves the reader's own qari alone.
        data.getQueryParameter("reciter")
          ?.toIntOrNull()
          ?.let { qariUtil.qariIdForQuranComId(it) }
          ?.let { currentQariManager.setCurrentQari(it) }

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
