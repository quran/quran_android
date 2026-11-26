package com.quran.labs.androidquran.hybrid.search

import androidx.annotation.StringRes
import com.quran.labs.androidquran.R

enum class HybridSearchScope(@StringRes val labelRes: Int) {
  AYAHS(R.string.hybrid_search_scope_ayahs),
  SURAS(R.string.hybrid_search_scope_suras),
  TAFSIR(R.string.hybrid_search_scope_tafsir)
}
