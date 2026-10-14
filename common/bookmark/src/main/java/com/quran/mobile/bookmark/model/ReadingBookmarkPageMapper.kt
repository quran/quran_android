package com.quran.mobile.bookmark.model

import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.core.QuranInfo
import com.quran.data.dao.Settings
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.source.PageProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.math.abs

@SingleIn(AppScope::class)
class ReadingBookmarkPageMapper @Inject constructor(
  private val settings: Settings,
  private val pageProviders: Map<@JvmSuppressWildcards String, @JvmSuppressWildcards PageProvider>,
  @param:Named(DependencyInjectionConstants.FALLBACK_PAGE_TYPE) private val fallbackPageType: String
) {
  private val quranInfoCache = mutableMapOf<String, QuranInfo>()

  fun pageTypeFlow(): Flow<String> {
    return settings.preferencesFlow()
      .map { currentPageType() }
      .onStart { emit(currentPageType()) }
      .distinctUntilChanged()
  }

  suspend fun currentPageToStoragePage(page: Int): Int {
    return pageToStoragePage(page, settings.pageType())
  }

  suspend fun storagePageToCurrentPage(page: Int): Int {
    return storagePageToPage(page, settings.pageType())
  }

  fun pageToStoragePage(page: Int, pageType: String): Int {
    return mapPage(
      page = page,
      sourceInfo = quranInfoFor(resolveCurrentPageType(pageType)),
      targetInfo = storageQuranInfo()
    )
  }

  fun storagePageToPage(page: Int, pageType: String): Int {
    return mapPage(
      page = page,
      sourceInfo = storageQuranInfo(),
      targetInfo = quranInfoFor(resolveCurrentPageType(pageType))
    )
  }

  fun sourcePageToStoragePage(page: Int, sourcePageType: String?): Int {
    return mapPage(
      page = page,
      sourceInfo = quranInfoFor(resolveSourcePageType(sourcePageType)),
      targetInfo = storageQuranInfo()
    )
  }

  fun sourcePageToSuraAyah(page: Int, sourcePageType: String?): SuraAyah {
    return firstAyahOnPage(page, quranInfoFor(resolveSourcePageType(sourcePageType)))
  }

  fun suraAyahToPage(sura: Int, ayah: Int, pageType: String): Int {
    val quranInfo = quranInfoFor(resolveCurrentPageType(pageType))
    return quranInfo.clampPage(quranInfo.getPageFromSuraAyah(sura, ayah))
  }

  fun isValidSuraAyah(sura: Int, ayah: Int): Boolean {
    val quranInfo = storageQuranInfo()
    val ayahCount = quranInfo.getNumberOfAyahs(sura)
    if (ayah !in 1..ayahCount) return false
    val page = quranInfo.getPageFromSuraAyah(sura, ayah)
    return quranInfo.isValidPage(page)
  }

  suspend fun currentPageType(): String {
    return resolveCurrentPageType(settings.pageType())
  }

  private fun mapPage(
    page: Int,
    sourceInfo: QuranInfo,
    targetInfo: QuranInfo
  ): Int {
    val sourcePage = sourceInfo.clampPage(page)
    val bounds = sourceInfo.getPageBounds(sourcePage)
    val preferredPage = pageForSuraAyah(representativeAyahOnPage(sourcePage, sourceInfo), targetInfo)
    val firstPage = pageForSuraAyah(SuraAyah(bounds[0], bounds[1]), targetInfo)
    val lastPage = pageForSuraAyah(SuraAyah(bounds[2], bounds[3]), targetInfo)
    val candidates = (minOf(firstPage, lastPage, preferredPage)..maxOf(firstPage, lastPage, preferredPage))
      .filter { candidate -> targetInfo.isValidPage(candidate) }
    return candidates
      .filter { candidate ->
        pageForSuraAyah(representativeAyahOnPage(candidate, targetInfo), sourceInfo) == sourcePage
      }
      .ifEmpty { candidates }
      .minByOrNull { candidate -> abs(candidate - preferredPage) }
      ?: preferredPage
  }

  private fun pageForSuraAyah(suraAyah: SuraAyah, quranInfo: QuranInfo): Int {
    return quranInfo.clampPage(quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
  }

  private fun firstAyahOnPage(page: Int, quranInfo: QuranInfo): SuraAyah {
    val bounds = quranInfo.getPageBounds(quranInfo.clampPage(page))
    return SuraAyah(bounds[0], bounds[1])
  }

  private fun representativeAyahOnPage(page: Int, quranInfo: QuranInfo): SuraAyah {
    val bounds = quranInfo.getPageBounds(quranInfo.clampPage(page))
    val firstAyahId = quranInfo.getAyahId(bounds[0], bounds[1])
    val lastAyahId = quranInfo.getAyahId(bounds[2], bounds[3])
    return quranInfo.getSuraAyahFromAyahId((firstAyahId + lastAyahId) / 2)
  }

  private fun storageQuranInfo(): QuranInfo {
    return quranInfoFor(resolveStoragePageType())
  }

  private fun quranInfoFor(pageType: String): QuranInfo {
    return synchronized(quranInfoCache) {
      quranInfoCache.getOrPut(pageType) {
        QuranInfo(pageProviders.requireProvider(pageType).getDataSource())
      }
    }
  }

  private fun resolveCurrentPageType(pageType: String): String {
    return when {
      pageProviders.containsKey(pageType) -> pageType
      pageProviders.containsKey(fallbackPageType) -> fallbackPageType
      else -> resolveStoragePageType()
    }
  }

  private fun resolveSourcePageType(pageType: String?): String {
    return when {
      !pageType.isNullOrBlank() && pageProviders.containsKey(pageType) -> pageType
      pageProviders.containsKey(CANONICAL_PAGE_TYPE) -> CANONICAL_PAGE_TYPE
      pageProviders.containsKey(fallbackPageType) -> fallbackPageType
      else -> pageProviders.firstPageType()
    }
  }

  private fun resolveStoragePageType(): String {
    return when {
      pageProviders.containsKey(CANONICAL_PAGE_TYPE) -> CANONICAL_PAGE_TYPE
      pageProviders.containsKey(fallbackPageType) -> fallbackPageType
      else -> pageProviders.firstPageType()
    }
  }

  private fun QuranInfo.clampPage(page: Int): Int {
    if (isValidPage(page)) return page
    val boundedPage = page.coerceIn(1, numberOfPages)
    if (isValidPage(boundedPage)) return boundedPage
    return (1..numberOfPages).first { candidate -> isValidPage(candidate) }
  }

  private fun Map<String, PageProvider>.requireProvider(pageType: String): PageProvider {
    return get(pageType) ?: error("No page provider registered for $pageType")
  }

  private fun Map<String, PageProvider>.firstPageType(): String {
    return keys.firstOrNull() ?: error("No page providers registered")
  }

  companion object {
    private const val CANONICAL_PAGE_TYPE = "madani"
  }
}
