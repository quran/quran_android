package com.quran.labs.androidquran.extra.feature.linebyline.presenter

import com.quran.data.model.highlight.HighlightInfo
import com.quran.data.model.highlight.HighlightType.Mode.HIDE
import com.quran.labs.androidquran.extra.feature.linebyline.model.HighlightAyah
import com.quran.labs.androidquran.extra.feature.linebyline.model.HighlightType
import com.quran.mobile.linebyline.data.dao.AyahHighlight
import com.quran.mobile.linebyline.data.dao.WordHighlight
import com.quran.mobile.linebyline.data.dao.asAyahHighlight
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A recitation session's marks on a line page: the hidden ones become parts of lines that are not
 * drawn, the others are drawn under the text in their own colors.
 */
internal class RecitationHighlights(
  val hidden: ImmutableList<AyahHighlight>,
  val underText: List<HighlightAyah>
) {
  companion object {
    fun of(
      marks: List<HighlightInfo>,
      ayahBounds: List<AyahHighlight>,
      wordBounds: List<WordHighlight>
    ): RecitationHighlights {
      val hidden = mutableListOf<AyahHighlight>()
      val underText = mutableListOf<HighlightAyah>()
      marks.forEach { mark ->
        val bounds = bounds(mark, ayahBounds, wordBounds)
        if (mark.highlightType.mode == HIDE) {
          hidden += bounds
        } else if (bounds.isNotEmpty()) {
          val type = HighlightType.Recitation(mark.highlightType.colorResId, mark.highlightType.nightColorResId)
          underText += HighlightAyah(type, bounds)
        }
      }
      return RecitationHighlights(hidden.distinct().toImmutableList(), underText)
    }

    // a word where the page has word bounds; otherwise its whole ayah, as layouts without them
    // only have one box per ayah and line
    private fun bounds(
      mark: HighlightInfo,
      ayahBounds: List<AyahHighlight>,
      wordBounds: List<WordHighlight>
    ): List<AyahHighlight> {
      if (mark.word > 0) {
        val words = wordBounds.filter { it.sura == mark.sura && it.ayah == mark.ayah }
        if (words.isNotEmpty()) return listOfNotNull(words.getOrNull(mark.word - 1)?.asAyahHighlight())
      }
      return ayahBounds.filter { it.sura == mark.sura && it.ayah == mark.ayah }
    }
  }
}
