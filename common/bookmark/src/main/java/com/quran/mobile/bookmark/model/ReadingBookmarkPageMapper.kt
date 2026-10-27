package com.quran.mobile.bookmark.model

import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.core.QuranInfo
import com.quran.data.dao.Settings
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.source.PageProvider
import com.quran.shared.persistence.model.Mushaf
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

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

  /** The mushaf mobile-sync stores and reads [pageType]'s page reading bookmarks in. */
  fun mushafFor(pageType: String): Mushaf {
    return mushafOf(resolveCurrentPageType(pageType))
  }

  /**
   * The mushaf a backup's page of [sourcePageType] is imported in, page unchanged. A page type
   * this build lacks keeps its own mushaf; one with no mushaf (unknown or blank) gets the mushaf of
   * the page type its recent pages are read in ([sourcePageToSuraAyah]), so both agree.
   */
  fun sourceMushafFor(sourcePageType: String?): Mushaf {
    return sourcePageType?.let(::mushafForPageType) ?: mushafOf(resolveSourcePageType(sourcePageType))
  }

  fun sourcePageToSuraAyah(page: Int, sourcePageType: String?): SuraAyah {
    return firstAyahOnPage(page, quranInfoFor(resolveSourcePageType(sourcePageType)))
  }

  fun suraAyahToPage(sura: Int, ayah: Int, pageType: String): Int {
    val quranInfo = quranInfoFor(resolveCurrentPageType(pageType))
    return quranInfo.clampPage(quranInfo.getPageFromSuraAyah(sura, ayah))
  }

  fun isValidSuraAyah(sura: Int, ayah: Int): Boolean {
    val quranInfo = quranInfoFor(resolveReferencePageType())
    val ayahCount = quranInfo.getNumberOfAyahs(sura)
    if (ayah !in 1..ayahCount) return false
    val page = quranInfo.getPageFromSuraAyah(sura, ayah)
    return quranInfo.isValidPage(page)
  }

  suspend fun currentPageType(): String {
    return resolveCurrentPageType(settings.pageType())
  }

  /**
   * [pageType]'s mushaf. Every registered page type has one, so a miss means a page type was added
   * without a [mushafForPageType] entry: it is logged, and its pages are taken as
   * [UNKNOWN_PAGE_TYPE_MUSHAF] pages, unconverted, rather than crashing.
   */
  private fun mushafOf(pageType: String): Mushaf {
    return mushafForPageType(pageType) ?: UNKNOWN_PAGE_TYPE_MUSHAF.also {
      Timber.w("No mobile-sync mushaf for page type %s, using %s", pageType, it)
    }
  }

  private fun firstAyahOnPage(page: Int, quranInfo: QuranInfo): SuraAyah {
    val bounds = quranInfo.getPageBounds(quranInfo.clampPage(page))
    return SuraAyah(bounds[0], bounds[1])
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
      else -> resolveReferencePageType()
    }
  }

  private fun resolveSourcePageType(pageType: String?): String {
    return when {
      !pageType.isNullOrBlank() && pageProviders.containsKey(pageType) -> pageType
      else -> resolveReferencePageType()
    }
  }

  private fun resolveReferencePageType(): String {
    return when {
      pageProviders.containsKey(REFERENCE_PAGE_TYPE) -> REFERENCE_PAGE_TYPE
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
    /**
     * The page type a backup with an unknown page type is read in, and the one ayahs are validated
     * against.
     */
    private const val REFERENCE_PAGE_TYPE = "madani"

    /**
     * The last resort for a registered page type missing from [mushafForPageType]: the 1405 Madani
     * print, [REFERENCE_PAGE_TYPE]'s mushaf.
     */
    private val UNKNOWN_PAGE_TYPE_MUSHAF = Mushaf.MADANI_1405
  }
}
