package com.quran.labs.androidquran.dao.translation

class TranslationItemDiplaySort : Comparator<TranslationItem> {
  override fun compare(p0: TranslationItem?, p1: TranslationItem?): Int {
    if (p0 == null || p1 == null) return 0;
    return p0.displayOrder.compareTo(p1.displayOrder);
  }
}
