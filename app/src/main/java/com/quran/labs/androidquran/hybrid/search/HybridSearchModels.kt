package com.quran.labs.androidquran.hybrid.search

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString

@Immutable
data class HighlightSpanRange(
  val start: Int,
  val end: Int
)

@Immutable
sealed interface HybridSearchResultItem {
  val stableKey: String
  val contentType: String
}

@Immutable
data class AyahSearchHit(
  val id: Long,
  val sura: Int,
  val ayah: Int,
  val page: Int,
  val suraName: String,
  val text: String,
  val highlightRanges: List<HighlightSpanRange>,
  val isArabic: Boolean,
  val ayahRefLabel: String = "",
  val pageLabel: String = "",
  val precomputedAnnotatedText: AnnotatedString? = null,
  val precomputedNativeText: CharSequence? = null,
  val precomputedHighlightColorArgb: Int = 0,
  val precomputedHighlightBgArgb: Int = 0
) : HybridSearchResultItem {
  override val stableKey: String = "ayah_${sura}_${ayah}_$id"
  override val contentType: String = "ayah"

  internal val cardTag: String get() = SearchTestTags.ayahCard(sura, ayah)
  internal val textTag: String get() = SearchTestTags.ayahText(sura, ayah)
  internal val listenTag: String get() = SearchTestTags.listenButton(sura, ayah)
  internal val bookmarkTag: String get() = SearchTestTags.bookmarkButton(sura, ayah)
  internal val openPageTag: String get() = SearchTestTags.openPageButton(sura, ayah)
}

@Immutable
data class SuraSearchHit(
  val sura: Int,
  val displayName: String,
  val metaString: String,
  val juzNumber: Int,
  val startPage: Int,
  val ayahCount: Int,
  val isMakki: Boolean
) : HybridSearchResultItem {
  override val stableKey: String = "sura_$sura"
  override val contentType: String = "sura"
}

@Immutable
data class TafsirSearchHit(
  val id: Long,
  val sura: Int,
  val ayah: Int,
  val page: Int,
  val suraName: String,
  val translatorName: String,
  val text: String,
  val highlightRanges: List<HighlightSpanRange>,
  val isArabic: Boolean,
  val translationId: Long = 0L,
  val occurrenceIndex: Int = 0
) : HybridSearchResultItem {
  override val stableKey: String = if (occurrenceIndex == 0) {
    "tafsir_${translationId}_${id}_${sura}_${ayah}"
  } else {
    "tafsir_${translationId}_${id}_${sura}_${ayah}_$occurrenceIndex"
  }
  override val contentType: String = "tafsir"
}

@Immutable
data class HybridSearchUiState(
  val query: String = "",
  val scope: HybridSearchScope = HybridSearchScope.AYAHS,
  val ayahHits: List<AyahSearchHit> = emptyList(),
  val totalAyahHitCount: Int = 0,
  val hasMoreAyahHits: Boolean = false,
  val suraHits: List<SuraSearchHit> = emptyList(),
  val tafsirHits: List<TafsirSearchHit> = emptyList(),
  val hasSearchedAyahs: Boolean = false,
  val hasSearchedTafsir: Boolean = false,
  val rustElapsedMs: Double? = null,
  val isLoading: Boolean = false,
  val needsArabicDbWarning: Boolean = false,
  val noTranslationsWarning: Boolean = false,
  val noTafsirInstalled: Boolean = false,
  val recentQueries: List<String> = emptyList(),
  val bookmarkedKeys: Set<Long> = emptySet(),
  val totalTafsirHitCount: Int = 0,
  val hasMoreTafsirHits: Boolean = false
) {
  val activeResultCount: Int
    get() = when (scope) {
      HybridSearchScope.AYAHS -> if (hasSearchedAyahs) totalAyahHitCount else ayahHits.size
      HybridSearchScope.SURAS -> suraHits.size
      HybridSearchScope.TAFSIR -> if (hasSearchedTafsir) {
        maxOf(totalTafsirHitCount, tafsirHits.size)
      } else {
        tafsirHits.size
      }
    }

  val hasExecutedCurrentScope: Boolean
    get() = when (scope) {
      HybridSearchScope.AYAHS -> hasSearchedAyahs
      HybridSearchScope.SURAS -> true
      HybridSearchScope.TAFSIR -> hasSearchedTafsir
    }
}

fun ayahBookmarkKey(sura: Int, ayah: Int): Long {
  return (sura.toLong() shl 16) or (ayah.toLong() and 0xFFFFL)
}
