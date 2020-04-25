package com.quran.labs.androidquran.common

class LocalTransationDiplaySort : Comparator<LocalTranslation> {
  override fun compare(p0: LocalTranslation?, p1: LocalTranslation?): Int {
    if (p0 == null || p1 == null) return 0;
    return p0.displayOrder.compareTo(p1.displayOrder);
  }
}
