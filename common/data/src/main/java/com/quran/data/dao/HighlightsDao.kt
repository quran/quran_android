package com.quran.data.dao

import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import kotlinx.coroutines.flow.Flow

interface HighlightsDao {
  fun highlightsFlow(): Flow<List<Highlight>>
  fun highlightsFlow(currentAyah: SuraAyah): Flow<Highlight?>
  suspend fun setHighlight(ayah: SuraAyah, color: HighlightColor)
  suspend fun clearHighlight(ayah: SuraAyah)
}
