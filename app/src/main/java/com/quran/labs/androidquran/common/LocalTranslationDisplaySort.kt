package com.quran.labs.androidquran.common

class LocalTranslationDisplaySort : Comparator<LocalTranslation> {
  override fun compare(first: LocalTranslation, second: LocalTranslation): Int {
    return first.displayOrder.compareTo(second.displayOrder);
  }
}
