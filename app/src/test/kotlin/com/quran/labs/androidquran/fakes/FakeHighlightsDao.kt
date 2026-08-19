package com.quran.labs.androidquran.fakes

import com.quran.data.dao.HighlightsDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

class FakeHighlightsDao : HighlightsDao {
  private val highlights = MutableStateFlow<List<Highlight>>(emptyList())

  fun setHighlights(highlights: List<Highlight>) {
    this.highlights.value = highlights
  }

  override fun highlightsFlow(): Flow<List<Highlight>> = highlights

  override suspend fun setHighlight(ayah: SuraAyah, color: HighlightColor) {
    highlights.update { existing ->
      existing.filterNot { it.suraAyah == ayah } + Highlight(ayah, color, Clock.System.now())
    }
  }

  override suspend fun clearHighlight(ayah: SuraAyah) {
    highlights.update { existing -> existing.filterNot { it.suraAyah == ayah } }
  }
}
