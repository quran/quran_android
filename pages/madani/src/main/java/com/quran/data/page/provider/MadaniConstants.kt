package com.quran.data.page.provider

import android.os.Build.VERSION

object MadaniConstants {
  @JvmField
  val ARABIC_DATABASE =
    if (VERSION.SDK_INT >= 21) "quran.ar.uthmani.v2.db" else "quran.ar.uthmani_simple.db"
}
