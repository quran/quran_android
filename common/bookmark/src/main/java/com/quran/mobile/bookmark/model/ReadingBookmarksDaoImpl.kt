package com.quran.mobile.bookmark.model

import com.quran.data.dao.ReadingBookmarkSlotSettings
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkSlotSetting
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.mobile.bookmark.time.MobileSyncTimestampProvider
import com.quran.shared.persistence.model.Mushaf
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepository
import com.quran.shared.persistence.util.PlatformDateTime
import com.quran.shared.persistence.util.fromPlatform
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.quran.shared.persistence.model.AyahReadingBookmark as SyncAyahReadingBookmark
import com.quran.shared.persistence.model.EmptyReadingBookmark as SyncEmptyReadingBookmark
import com.quran.shared.persistence.model.PageReadingBookmark as SyncPageReadingBookmark
import com.quran.shared.persistence.model.ReadingBookmark as SyncReadingBookmark

/**
 * Reading bookmark DAO backed by mobile-sync persistence for the synced slots, and by
 * [LocalReadingBookmarks] for the slots kept on the device (offered once turned on, see
 * [ReadingBookmarkSlotSettings]).
 *
 * Page bookmarks are set and read in the current page type's mobile-sync [Mushaf], which maps them
 * between mushafs, so their pages pass through unchanged.
 *
 * @param pageMapper resolves the current page type and its [Mushaf].
 * @param readingBookmarksRepository mobile-sync repository that owns persisted reading bookmark rows.
 * @param timestampProvider provides the timestamp assigned to locally written bookmarks.
 * @param localBookmarks keeps the slots that don't sync.
 * @param slotSettings which of those are on.
 */
@SingleIn(AppScope::class)
class ReadingBookmarksDaoImpl @Inject constructor(
  private val pageMapper: ReadingBookmarkPageMapper,
  private val readingBookmarksRepository: ReadingBookmarksRepository,
  private val timestampProvider: MobileSyncTimestampProvider,
  private val localBookmarks: LocalReadingBookmarks,
  private val slotSettings: ReadingBookmarkSlotSettings,
) : ReadingBookmarksDao {
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun readingBookmarksFlow(): Flow<List<ReadingBookmark>> {
    // Keep this flow cold so new collectors start from the repository's current value instead of a
    // previously cached null. The IO dispatcher preserves the old off-main mapping behavior.
    return pageMapper.pageTypeFlow()
      .flatMapLatest { pageType ->
        combine(
          readingBookmarksRepository.getReadingBookmarksFlow(pageMapper.mushafFor(pageType)),
          localBookmarks.rowsFlow(),
          slotSettings.settingsFlow(),
        ) { synced, local, settings ->
          synced.map { toReadingBookmark(it) } +
            localBookmarks.toReadingBookmarks(local, settings.enabledSlots(), pageType)
        }
      }
      .distinctUntilChanged()
      .flowOn(Dispatchers.IO)
  }

  override suspend fun readingBookmarks(): List<ReadingBookmark> {
    return withContext(Dispatchers.IO) {
      val synced = readingBookmarksRepository.getReadingBookmarks(currentMushaf()).map { toReadingBookmark(it) }
      synced + localBookmarks.toReadingBookmarks(
        localBookmarks.rows(), slotSettings.settings().enabledSlots(), pageMapper.currentPageType()
      )
    }
  }

  override suspend fun setPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    if (!slot.synced) {
      localBookmarks.setPage(slot, page, pageMapper.currentPageType(), timestampProvider.nowEpochMillis())
      return true
    }
    val timestamp = timestampProvider.now()
    return withContext(Dispatchers.IO) {
      setPageReadingBookmark(slot, page, currentMushaf(), timestamp)
    }
  }

  /** Sets [slot] to [page] of [mushaf]; false when [mushaf] has no such page, such as the IndoPak cover. */
  private suspend fun setPageReadingBookmark(
    slot: ReadingBookmarkType,
    page: Int,
    mushaf: Mushaf,
    timestamp: PlatformDateTime
  ): Boolean {
    return try {
      readingBookmarksRepository.setPageReadingBookmark(slot.toSyncSlot(), page, mushaf, timestamp)
      true
    } catch (_: IllegalArgumentException) {
      false
    }
  }

  private suspend fun currentMushaf(): Mushaf {
    return pageMapper.mushafFor(pageMapper.currentPageType())
  }

  override suspend fun setAyahReadingBookmark(slot: ReadingBookmarkType, suraAyah: SuraAyah): Boolean {
    if (!slot.synced) {
      localBookmarks.setAyah(slot, suraAyah, timestampProvider.nowEpochMillis())
      return true
    }
    val timestamp = timestampProvider.now()
    val set = withContext(Dispatchers.IO) {
      readingBookmarksRepository.setAyahReadingBookmark(slot.toSyncSlot(), suraAyah.sura, suraAyah.ayah, timestamp)
      true
    }
    return set
  }

  override suspend fun clearReadingBookmark(slot: ReadingBookmarkType): ReadingBookmark {
    if (!slot.synced) return localBookmarks.clear(slot, timestampProvider.nowEpochMillis())
    val cleared = withContext(Dispatchers.IO) {
      readingBookmarksRepository.clearReadingBookmark(slot.toSyncSlot())
    }
    return toReadingBookmark(cleared)
  }

  override suspend fun renameReadingBookmark(slot: ReadingBookmarkType, name: String?) {
    if (!slot.synced) return localBookmarks.rename(slot, name, timestampProvider.nowEpochMillis())
    withContext(Dispatchers.IO) {
      readingBookmarksRepository.renameReadingBookmark(slot.toSyncSlot(), name, currentMushaf())
    }
  }

  override suspend fun isPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    if (!slot.synced) return localBookmarks.isOnPage(slot, page, pageMapper.currentPageType())
    return withContext(Dispatchers.IO) {
      isPageReadingBookmark(slot, page, currentMushaf())
    }
  }

  /** Whether [slot] is on [page] of [mushaf], compared in the pages [readingBookmarks] shows. */
  private suspend fun isPageReadingBookmark(slot: ReadingBookmarkType, page: Int, mushaf: Mushaf): Boolean {
    val syncSlot = slot.toSyncSlot()
    return readingBookmarksRepository.getReadingBookmarks(mushaf)
      .any { it.slot == syncSlot && it is SyncPageReadingBookmark && it.page == page }
  }

  override suspend fun togglePageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    if (!slot.synced) {
      return if (isPageReadingBookmark(slot, page)) {
        clearReadingBookmark(slot)
        false
      } else {
        setPageReadingBookmark(slot, page)
      }
    }
    val timestamp = timestampProvider.now()
    return withContext(Dispatchers.IO) {
      val mushaf = currentMushaf()
      if (isPageReadingBookmark(slot, page, mushaf)) {
        readingBookmarksRepository.clearReadingBookmark(slot.toSyncSlot())
        false
      } else {
        setPageReadingBookmark(slot, page, mushaf, timestamp)
      }
    }
  }

  private fun toReadingBookmark(bookmark: SyncReadingBookmark): ReadingBookmark {
    return when (bookmark) {
      is SyncAyahReadingBookmark -> {
        AyahReadingBookmark(
          slot = bookmark.slot.asBookmarkType(),
          sura = bookmark.sura,
          ayah = bookmark.ayah,
          timestamp = bookmark.lastUpdated,
          name = bookmark.name
        )
      }
      is SyncPageReadingBookmark -> PageReadingBookmark(
        slot = bookmark.slot.asBookmarkType(),
        page = bookmark.page,
        timestamp = bookmark.lastUpdated,
        name = bookmark.name
      )
      is SyncEmptyReadingBookmark -> EmptyReadingBookmark(
        slot = bookmark.slot.asBookmarkType(),
        timestamp = bookmark.lastUpdated,
        name = bookmark.name
      )
    }
  }

  override suspend fun updateReadingBookmarks(
    ayah: SuraAyah,
    added: List<ReadingBookmark>,
    removed: List<ReadingBookmark>
  ): Boolean {
    val timestamp = timestampProvider.now()
    return withContext(Dispatchers.IO) {
      val mushaf = currentMushaf()
      var allSet = true
      added.forEach { readingBookmark ->
        if (!readingBookmark.slot.synced) {
          when (readingBookmark) {
            is AyahReadingBookmark -> localBookmarks.setAyah(
              readingBookmark.slot, readingBookmark.asSuraAyah(), timestamp.fromPlatform().toEpochMilliseconds()
            )
            is PageReadingBookmark -> localBookmarks.setPage(
              readingBookmark.slot, readingBookmark.page, pageMapper.currentPageType(),
              timestamp.fromPlatform().toEpochMilliseconds()
            )
            is EmptyReadingBookmark -> {}
          }
          return@forEach
        }
        when (readingBookmark) {
          is AyahReadingBookmark ->
            readingBookmarksRepository.setAyahReadingBookmark(
              readingBookmark.slot.toSyncSlot(),
              readingBookmark.sura,
              readingBookmark.ayah
            )

          is PageReadingBookmark -> {
            val isSet = setPageReadingBookmark(readingBookmark.slot, readingBookmark.page, mushaf, timestamp)
            allSet = allSet && isSet
          }

          is EmptyReadingBookmark -> {}
        }
      }

      removed.forEach { readingBookmark ->
        if (readingBookmark.slot.synced) {
          readingBookmarksRepository.clearReadingBookmark(readingBookmark.slot.toSyncSlot())
        } else {
          localBookmarks.clear(readingBookmark.slot, timestamp.fromPlatform().toEpochMilliseconds())
        }
      }
      allSet
    }
  }

  private fun ReadingBookmarkSlot.asBookmarkType(): ReadingBookmarkType {
    return when (this) {
      ReadingBookmarkSlot.TEAL -> ReadingBookmarkType.TEAL
      ReadingBookmarkSlot.ORANGE -> ReadingBookmarkType.ORANGE
      ReadingBookmarkSlot.RED -> ReadingBookmarkType.RED
    }
  }

  internal fun ReadingBookmarkType.toSyncSlot(): ReadingBookmarkSlot =
    when (this) {
      ReadingBookmarkType.TEAL -> ReadingBookmarkSlot.TEAL
      ReadingBookmarkType.ORANGE -> ReadingBookmarkSlot.ORANGE
      ReadingBookmarkType.RED -> ReadingBookmarkSlot.RED
      else -> error("$this is kept on the device, not by mobile-sync")
    }

  private fun List<ReadingBookmarkSlotSetting>.enabledSlots(): Set<ReadingBookmarkType> =
    filter { it.enabled }.mapTo(HashSet()) { it.slot }
}
