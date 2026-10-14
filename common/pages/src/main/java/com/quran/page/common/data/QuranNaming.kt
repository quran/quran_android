package com.quran.page.common.data

import android.content.Context

interface QuranNaming {
  fun getSuraName(context: Context, sura: Int, wantPrefix: Boolean = true): String
  fun getSuraNameWithNumber(context: Context, sura: Int, wantPrefix: Boolean): String
  fun getSuraAyahString(context: Context, sura: Int, ayah: Int): String
  fun getSuraPageString(context: Context, page: Int): String
}
