package com.quran.labs.androidquran.ui.util

import android.content.Context
import android.graphics.Typeface
import com.quran.labs.androidquran.data.QuranFileConstants

object TypefaceManager {

  const val TYPE_UTHMANI_HAFS = 1
  const val TYPE_NOOR_HAYAH = 2
  const val TYPE_UTHMANIC_WARSH = 3
  const val TYPE_UTHMANIC_QALOON = 4

  private var typeface: Typeface? = null
  private var arabicTafseerTypeface: Typeface? = null
  private var arabicHeaderFooterTypeface: Typeface? = null
  private var dyslexicTypeface: Typeface? = null

  @JvmStatic
  fun getUthmaniTypeface(context: Context): Typeface {
    return typeface ?: run {
      val fontName = when (QuranFileConstants.FONT_TYPE) {
        TYPE_NOOR_HAYAH -> "noorehira.ttf"
        TYPE_UTHMANIC_WARSH -> "uthmanic_warsh_ver09.ttf"
        TYPE_UTHMANIC_QALOON -> "uthmanic_qaloon_ver21.ttf"
        else -> "uthmanic_hafs_ver12.otf"
      }
      val instance = Typeface.createFromAsset(context.assets, fontName)
      typeface = instance
      instance
    }
  }

  fun getTafseerTypeface(context: Context): Typeface {
    return arabicTafseerTypeface ?: run {
      val instance = Typeface.createFromAsset(context.assets, "kitab.ttf")
      arabicTafseerTypeface = instance
      instance
    }
  }

  fun getDyslexicTypeface(context: Context): Typeface {
    return dyslexicTypeface ?: run {
      val instance = Typeface.createFromAsset(context.assets, "OpenDyslexic.otf")
      dyslexicTypeface = instance
      instance
    }
  }

  fun getHeaderFooterTypeface(context: Context): Typeface {
    return arabicHeaderFooterTypeface ?: run {
      val instance = Typeface.createFromAsset(context.assets, "UthmanTN1Ver10.otf")
      arabicHeaderFooterTypeface = instance
      instance
    }
  }

}
