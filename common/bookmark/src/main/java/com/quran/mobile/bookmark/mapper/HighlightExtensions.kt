package com.quran.mobile.bookmark.mapper

import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import com.quran.shared.persistence.model.AyahHighlight
import com.quran.shared.persistence.model.AyahHighlightColor
import com.quran.shared.persistence.util.toPlatform

internal fun AyahHighlightColor.fromPlatform(): HighlightColor {
  return when (this) {
    AyahHighlightColor.BLUE -> HighlightColor.BLUE
    AyahHighlightColor.RED -> HighlightColor.RED
    AyahHighlightColor.GREEN -> HighlightColor.GREEN
    AyahHighlightColor.YELLOW -> HighlightColor.YELLOW
    AyahHighlightColor.PURPLE -> HighlightColor.PURPLE
  }
}

internal fun HighlightColor.toPlatform(): AyahHighlightColor {
  return when (this) {
    HighlightColor.BLUE -> AyahHighlightColor.BLUE
    HighlightColor.RED -> AyahHighlightColor.RED
    HighlightColor.GREEN -> AyahHighlightColor.GREEN
    HighlightColor.YELLOW -> AyahHighlightColor.YELLOW
    HighlightColor.PURPLE -> AyahHighlightColor.PURPLE
  }
}

internal fun AyahHighlight.fromPlatform(): Highlight {
  return Highlight(SuraAyah(sura, ayah), color.fromPlatform(), lastUpdated.toPlatform())
}

internal fun Highlight.toPlatform(): AyahHighlight {
  return AyahHighlight(suraAyah.sura, suraAyah.ayah, color.toPlatform(), timestamp.toPlatform())
}
