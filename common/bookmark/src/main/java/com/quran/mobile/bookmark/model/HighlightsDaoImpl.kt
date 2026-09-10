package com.quran.mobile.bookmark.model

import com.quran.data.dao.HighlightsDao
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import com.quran.mobile.bookmark.mapper.toPlatform
import com.quran.mobile.bookmark.mapper.fromPlatform
import com.quran.mobile.bookmark.time.MobileSyncTimestampProvider
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepository
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class HighlightsDaoImpl @Inject constructor(
  private val timestampProvider: MobileSyncTimestampProvider,
  private val collectionBookmarksRepository: CollectionBookmarksRepository,
  appCoroutineScope: AppCoroutineScope
) : HighlightsDao {

  private val highlightsDataState: StateFlow<List<Highlight>?> =
    collectionBookmarksRepository.getHighlightsFlow()
      .map { it.map { highlight -> highlight.fromPlatform() } }
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  override fun highlightsFlow(): Flow<List<Highlight>> {
    return highlightsDataState.filterNotNull()
  }

  override suspend fun setHighlight(ayah: SuraAyah, color: HighlightColor) {
    withContext(Dispatchers.IO) {
      collectionBookmarksRepository.setHighlight(
        ayah.sura, ayah.ayah, color.toPlatform(),
        timestampProvider.now()
      )
    }
  }

  override suspend fun clearHighlight(ayah: SuraAyah) {
    withContext(Dispatchers.IO) {
      collectionBookmarksRepository.removeHighlight(ayah.sura, ayah.ayah, timestampProvider.now())
    }
  }
}
