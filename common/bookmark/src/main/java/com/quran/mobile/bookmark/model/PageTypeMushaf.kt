package com.quran.mobile.bookmark.model

import com.quran.shared.persistence.model.Mushaf

/**
 * The mobile-sync [Mushaf] that numbers its pages like [pageType]. Pages pass to and from
 * mobile-sync unchanged; it maps them between mushafs.
 *
 * Builds register only the page types listed here, and the current page type always resolves to a
 * registered one. Any other page type (a backup's, or one added without updating this table) is
 * taken as madani pages, the reference page type backups of an unknown page type are read in.
 */
internal fun mushafForPageType(pageType: String): Mushaf {
  return when (pageType) {
    // MadaniDataSource
    "madani", "tajweed" -> Mushaf.MADANI_1405
    // NewMadaniDataSource
    "new_madani", "new_madani_1441", "new_madani_1439_lines", "madani_1441_lines" -> Mushaf.MADANI_1440
    // NaskhDataSource: pages 2..611, page 1 is the cover, as mobile-sync numbers them
    "naskh" -> Mushaf.INDOPAK_15_LINES
    // ShemerlyDataSource: pages 1..521
    "shemerly" -> Mushaf.SHEMERLY
    // the Warsh and Qaloon riwayat: pages 1..604, which mobile-sync never maps to another mushaf
    "warsh" -> Mushaf.WARSH
    "qaloon" -> Mushaf.QALOUN
    else -> Mushaf.MADANI_1405
  }
}
