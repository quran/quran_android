package com.quran.mobile.bookmark.model

import com.quran.data.source.MushafType
import com.quran.shared.persistence.model.Mushaf

/**
 * The mobile-sync [Mushaf] that numbers its pages like this edition. Pages pass to and from
 * mobile-sync unchanged; it maps them between mushafs.
 */
internal fun MushafType.toMushaf(): Mushaf {
  return when (this) {
    MushafType.MADANI_1405 -> Mushaf.MADANI_1405
    MushafType.MADANI_1440 -> Mushaf.MADANI_1440
    MushafType.INDOPAK_15_LINES -> Mushaf.INDOPAK_15_LINES
    MushafType.SHEMERLY -> Mushaf.SHEMERLY
    MushafType.WARSH -> Mushaf.WARSH
    MushafType.QALOUN -> Mushaf.QALOUN
  }
}
