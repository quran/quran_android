package com.quran.page.common.data

import android.content.Context

interface QuranNaming {
  fun getSuraName(context: Context, sura: Int): String
}
