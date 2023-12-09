package com.quran.labs.androidquran.common

import com.quran.mobile.translation.model.LocalTranslation

class LocalTranslationDisplaySort : Comparator<LocalTranslation> {
  override fun compare(first: LocalTranslation, second: LocalTranslation): Int {
    return first.displayOrder.compareTo(second.displayOrder)
  }
}
