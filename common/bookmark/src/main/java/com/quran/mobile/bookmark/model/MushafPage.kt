package com.quran.mobile.bookmark.model

import com.quran.shared.persistence.model.Mushaf

/** A page reading bookmark's [page] in [mushaf], the way mobile-sync takes and returns it. */
data class MushafPage(val page: Int, val mushaf: Mushaf)

/**
 * The mobile-sync [Mushaf] that numbers its pages like [pageType], or null when mobile-sync has none
 * (Shemerly, Warsh, Qaloon).
 */
internal fun mushafForPageType(pageType: String): Mushaf? {
  return when (pageType) {
    // MadaniDataSource
    "madani", "tajweed" -> Mushaf.MADANI_1405
    // NewMadaniDataSource
    "new_madani", "new_madani_1441", "new_madani_1439_lines", "madani_1441_lines" -> Mushaf.MADANI_1440
    // NaskhDataSource: pages 2..611, page 1 is the cover, as mobile-sync numbers them
    "naskh" -> Mushaf.INDOPAK_15_LINES
    else -> null
  }
}
