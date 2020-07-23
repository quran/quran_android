package com.quran.labs.androidquran.dao.translation

class TranslationItemDisplaySort : Comparator<TranslationItem> {
  override fun compare(p0: TranslationItem, p1: TranslationItem): Int {
    return p0.displayOrder.compareTo(p1.displayOrder);
  }
}
