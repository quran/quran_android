package com.quran.labs.androidquran.hybrid.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.SearchActivity
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.contract.HybridSearchHost
import com.quran.labs.androidquran.hybrid.contract.VerticalHairlineDivider
import com.quran.labs.androidquran.hybrid.search.SearchTestTags.highlightRanges
import com.quran.labs.androidquran.ui.TranslationManagerActivity
import com.quran.labs.androidquran.util.QuranUtils
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

val LocalHybridArabicDbDownloadHandler = compositionLocalOf<(() -> Unit)?> { null }
val LocalHybridTranslationManagerHandler = compositionLocalOf<(() -> Unit)?> { null }

@SingleIn(AppScope::class)
class RealHybridSearchHost @Inject constructor(
  private val searchEngine: HybridSearchEngine
) : HybridSearchHost {

  @Composable
  override fun HybridSearchScreen(
    initialQuery: String?,
    actionHandler: HybridSearchActionHandler,
    bottomContentPadding: Dp,
    onBackOrClose: (() -> Unit)?,
    modifier: Modifier
  ) {
    val context = LocalContext.current
    val customDownloadHandler = LocalHybridArabicDbDownloadHandler.current
    val customTranslationHandler = LocalHybridTranslationManagerHandler.current

    val state by searchEngine.uiState.collectAsState()

    LaunchedEffect(initialQuery) {
      if (!initialQuery.isNullOrBlank() && initialQuery != searchEngine.uiState.value.query) {
        searchEngine.submitQuery(initialQuery, recordRecent = true)
      }
    }

    val openTranslations = remember(context, customTranslationHandler) {
      {
        if (customTranslationHandler != null) {
          customTranslationHandler()
        } else {
          launchActivitySafely(context, Intent(context, TranslationManagerActivity::class.java))
        }
      }
    }

    val downloadArabicDb = remember(context, customDownloadHandler) {
      {
        if (customDownloadHandler != null) {
          customDownloadHandler()
        } else {
          val intent = Intent(context, SearchActivity::class.java).apply {
            action = Intent.ACTION_SEARCH
            putExtra(android.app.SearchManager.QUERY, searchEngine.uiState.value.query)
          }
          launchActivitySafely(context, intent)
        }
      }
    }

    HybridSearchContent(
      state = state,
      bottomContentPadding = bottomContentPadding,
      onQueryChanged = searchEngine::onQueryTextChanged,
      onSubmitQuery = { query ->
        searchEngine.submitQuery(query, recordRecent = true)
      },
      onClearQuery = searchEngine::clearQuery,
      onScopeSelected = searchEngine::onScopeSelected,
      onRecentChipClicked = { chip ->
        searchEngine.submitQuery(chip, recordRecent = true)
      },
      onOpenPage = { page, sura, ayah ->
        searchEngine.recordRecentQuery(state.query)
        actionHandler.openPage(page, sura, ayah)
      },
      onPlayAyah = { sura, ayah, page ->
        searchEngine.recordRecentQuery(state.query)
        actionHandler.playAyah(sura, ayah, page)
      },
      onToggleBookmark = { sura, ayah, page ->
        actionHandler.toggleBookmark(sura, ayah, page)
      },
      onDownloadArabicDb = downloadArabicDb,
      onOpenTranslationManager = openTranslations,
      onLoadMoreAyahs = searchEngine::loadMoreAyahHits,
      onLoadMoreTafsir = searchEngine::loadMoreTafsirHits,
      onHighlightPaletteResolved = searchEngine::updateHighlightPalette,
      modifier = modifier
    )
  }

  private fun launchActivitySafely(context: Context, intent: Intent) {
    if (context !is Activity) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }
}

@Composable
internal fun HybridSearchContent(
  state: HybridSearchUiState,
  bottomContentPadding: Dp,
  onQueryChanged: (String) -> Unit,
  onSubmitQuery: (String) -> Unit,
  onClearQuery: () -> Unit,
  onScopeSelected: (HybridSearchScope) -> Unit,
  onRecentChipClicked: (String) -> Unit,
  onOpenPage: (page: Int, sura: Int, ayah: Int) -> Unit,
  onPlayAyah: (sura: Int, ayah: Int, page: Int) -> Unit,
  onToggleBookmark: (sura: Int, ayah: Int, page: Int) -> Unit,
  onDownloadArabicDb: () -> Unit,
  onOpenTranslationManager: () -> Unit,
  onLoadMoreAyahs: () -> Unit = {},
  onLoadMoreTafsir: () -> Unit = {},
  onHighlightPaletteResolved: (Color, Color) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val uthmaniFont = remember(context) { UthmaniFontCache.get(context) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val listState = rememberLazyListState()

  val highlightBg = remember(quranColors.editorialAccent, isDark) {
    quranColors.editorialAccent.copy(alpha = if (isDark) 0.20f else 0.16f)
  }
  val bgColor = MaterialTheme.colorScheme.background
  val ambientCanvasBrush = remember(quranColors.surfaceTopGlow, bgColor) {
    Brush.verticalGradient(
      colorStops = arrayOf(
        0.0f to quranColors.surfaceTopGlow,
        0.42f to bgColor,
        1.0f to bgColor
      )
    )
  }

  SideEffect {
    onHighlightPaletteResolved(quranColors.searchHighlight, highlightBg)
  }

  LaunchedEffect(
    listState,
    state.scope,
    state.ayahHits.size,
    state.hasMoreAyahHits,
    state.tafsirHits.size,
    state.hasMoreTafsirHits
  ) {
    if (state.scope == HybridSearchScope.AYAHS && state.hasMoreAyahHits) {
      snapshotFlow {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        lastVisible >= (state.ayahHits.size - 12).coerceAtLeast(0)
      }.collect { shouldLoadMore ->
        if (shouldLoadMore) {
          onLoadMoreAyahs()
        }
      }
    } else if (state.scope == HybridSearchScope.TAFSIR && state.hasMoreTafsirHits) {
      snapshotFlow {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        lastVisible >= (state.tafsirHits.size - 12).coerceAtLeast(0)
      }.collect { shouldLoadMore ->
        if (shouldLoadMore) {
          onLoadMoreTafsir()
        }
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(ambientCanvasBrush)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .padding(top = 10.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
      SearchInputPill(
        query = state.query,
        scope = state.scope,
        resultCount = state.activeResultCount,
        showCountBadge = state.query.isNotBlank() &&
          (state.activeResultCount > 0 || state.hasExecutedCurrentScope),
        isDark = isDark,
        onQueryChanged = onQueryChanged,
        onSubmit = {
          keyboardController?.hide()
          onSubmitQuery(state.query)
        },
        onClear = onClearQuery
      )

      if (state.recentQueries.isNotEmpty()) {
        RecentSearchChipsRow(
          queries = state.recentQueries,
          onChipClick = { chip ->
            keyboardController?.hide()
            onRecentChipClicked(chip)
          }
        )
      }

      ScopeRow(
        selectedScope = state.scope,
        onScopeSelected = onScopeSelected
      )
    }

    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .testTag(SearchTestTags.RESULTS_LIST),
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 8.dp,
        bottom = bottomContentPadding
      ),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      when {
        state.scope == HybridSearchScope.AYAHS && state.needsArabicDbWarning -> {
          item(key = "warning_arabic_db", contentType = "warning") {
            ActionableMessageCard(
              message = stringResource(R.string.no_arabic_search_available),
              ctaLabel = stringResource(R.string.get_arabic_search_db),
              onCtaClick = onDownloadArabicDb
            )
          }
        }

        state.scope == HybridSearchScope.AYAHS && state.noTranslationsWarning -> {
          item(key = "warning_no_translations", contentType = "warning") {
            ActionableMessageCard(
              message = stringResource(R.string.no_results, state.query),
              ctaLabel = stringResource(R.string.get_translations),
              onCtaClick = onOpenTranslationManager
            )
          }
        }

        state.scope == HybridSearchScope.TAFSIR && state.noTafsirInstalled -> {
          item(key = "empty_no_tafsir", contentType = "warning") {
            TafsirEmptyStateCard(onDownloadClick = onOpenTranslationManager)
          }
        }

        state.scope == HybridSearchScope.AYAHS &&
          state.query.isNotBlank() &&
          state.hasSearchedAyahs &&
          state.ayahHits.isEmpty() &&
          !state.isLoading -> {
          item(key = "no_ayah_results", contentType = "empty") {
            CalmEmptyMessageCard(
              title = stringResource(R.string.hybrid_search_no_results, state.query),
              subtitle = null,
              testTag = SearchTestTags.NO_RESULTS_TEXT
            )
          }
        }

        state.scope == HybridSearchScope.SURAS &&
          state.query.isNotBlank() &&
          state.suraHits.isEmpty() -> {
          item(key = "no_sura_results", contentType = "empty") {
            CalmEmptyMessageCard(
              title = stringResource(R.string.hybrid_search_no_results, state.query),
              subtitle = null,
              testTag = SearchTestTags.NO_RESULTS_TEXT
            )
          }
        }

        state.scope == HybridSearchScope.TAFSIR &&
          state.query.isNotBlank() &&
          state.hasSearchedTafsir &&
          state.tafsirHits.isEmpty() &&
          !state.isLoading -> {
          item(key = "no_tafsir_results", contentType = "empty") {
            CalmEmptyMessageCard(
              title = stringResource(R.string.hybrid_search_no_results, state.query),
              subtitle = null,
              testTag = SearchTestTags.NO_RESULTS_TEXT
            )
          }
        }

        state.query.isBlank() && state.scope != HybridSearchScope.SURAS -> {
          item(key = "prompt_empty", contentType = "empty") {
            CalmEmptyMessageCard(
              title = stringResource(R.string.hybrid_search_empty_title),
              subtitle = stringResource(R.string.hybrid_search_empty_subtitle),
              testTag = null
            )
          }
        }

        else -> {
          when (state.scope) {
            HybridSearchScope.AYAHS -> {
              items(
                items = state.ayahHits,
                key = { it.stableKey },
                contentType = { it.contentType }
              ) { hit ->
                val isBookmarked = ayahBookmarkKey(hit.sura, hit.ayah) in state.bookmarkedKeys
                AyahSearchResultCard(
                  hit = hit,
                  isBookmarked = isBookmarked,
                  uthmaniFont = uthmaniFont,
                  highlightColor = quranColors.searchHighlight,
                  highlightBg = highlightBg,
                  onOpenPage = { onOpenPage(hit.page, hit.sura, hit.ayah) },
                  onPlayAyah = { onPlayAyah(hit.sura, hit.ayah, hit.page) },
                  onToggleBookmark = { onToggleBookmark(hit.sura, hit.ayah, hit.page) }
                )
              }
            }

            HybridSearchScope.SURAS -> {
              items(
                items = state.suraHits,
                key = { it.stableKey },
                contentType = { it.contentType }
              ) { hit ->
                SuraSearchResultCard(
                  hit = hit,
                  onOpenPage = { onOpenPage(hit.startPage, hit.sura, 1) }
                )
              }
            }

            HybridSearchScope.TAFSIR -> {
              items(
                items = state.tafsirHits,
                key = { it.stableKey },
                contentType = { it.contentType }
              ) { hit ->
                val isBookmarked = ayahBookmarkKey(hit.sura, hit.ayah) in state.bookmarkedKeys
                TafsirSearchResultCard(
                  hit = hit,
                  isBookmarked = isBookmarked,
                  highlightColor = quranColors.searchHighlight,
                  highlightBg = highlightBg,
                  onOpenPage = { onOpenPage(hit.page, hit.sura, hit.ayah) },
                  onPlayAyah = { onPlayAyah(hit.sura, hit.ayah, hit.page) },
                  onToggleBookmark = { onToggleBookmark(hit.sura, hit.ayah, hit.page) }
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SearchInputPill(
  query: String,
  scope: HybridSearchScope,
  resultCount: Int,
  showCountBadge: Boolean,
  isDark: Boolean,
  onQueryChanged: (String) -> Unit,
  onSubmit: () -> Unit,
  onClear: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  val pillShape = RoundedCornerShape(28.dp)
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  val editorialSoftBg = quranColors.editorialAccent.copy(alpha = if (isDark) 0.15f else 0.13f)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(46.dp)
      .shadow(
        elevation = if (isDark) 7.dp else 6.dp,
        shape = pillShape,
        ambientColor = quranColors.shadowTint,
        spotColor = quranColors.shadowTint
      )
      .clip(pillShape)
      .background(quranColors.surfaceElevated)
      .border(
        width = 1.dp,
        color = cardBorder,
        shape = pillShape
      )
      .padding(start = 14.dp, end = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Icon(
      imageVector = HybridSearchIcons.Search,
      contentDescription = stringResource(R.string.hybrid_nav_search),
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp)
    )

    Box(
      modifier = Modifier.weight(1f),
      contentAlignment = Alignment.CenterStart
    ) {
      if (query.isEmpty()) {
        Text(
          text = stringResource(R.string.hybrid_search_placeholder),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 14.5.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        textStyle = TextStyle(
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 15.5.sp,
          fontWeight = FontWeight.Bold
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = Modifier
          .fillMaxWidth()
          .testTag(SearchTestTags.SEARCH_INPUT)
      )
    }

    if (showCountBadge) {
      val countRes = when (scope) {
        HybridSearchScope.AYAHS -> R.string.hybrid_search_count_ayahs
        HybridSearchScope.SURAS -> R.string.hybrid_search_count_suras
        HybridSearchScope.TAFSIR -> R.string.hybrid_search_count_tafsir
      }
      val countText = stringResource(countRes, QuranUtils.getLocalizedNumber(resultCount))
      Box(
        modifier = Modifier
          .clip(CircleShape)
          .background(editorialSoftBg)
          .padding(horizontal = 9.dp, vertical = 3.dp)
          .testTag(SearchTestTags.HIT_COUNT_BADGE)
      ) {
        Text(
          text = countText,
          color = quranColors.editorialAccent,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1
        )
      }
    }

    if (query.isNotEmpty()) {
      val clearCd = stringResource(R.string.hybrid_search_clear_cd)
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .clickable(
            role = Role.Button,
            onClick = onClear
          )
          .semantics { contentDescription = clearCd }
          .testTag(SearchTestTags.CLEAR_BUTTON),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = HybridSearchIcons.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun RecentSearchChipsRow(
  queries: List<String>,
  onChipClick: (String) -> Unit
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  LazyRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    items(
      items = queries,
      key = { it },
      contentType = { "recent_chip" }
    ) { recentQuery ->
      Row(
        modifier = Modifier
          .shadow(
            elevation = if (isDark) 2.5.dp else 2.dp,
            shape = CircleShape,
            ambientColor = quranColors.shadowTint,
            spotColor = quranColors.shadowTint
          )
          .clip(CircleShape)
          .background(quranColors.cardSurface)
          .border(width = 1.dp, color = cardBorder, shape = CircleShape)
          .semantics(mergeDescendants = true) {}
          .clickable(role = Role.Button) { onChipClick(recentQuery) }
          .padding(horizontal = 9.dp, vertical = 5.dp)
          .testTag(SearchTestTags.recentChip(recentQuery)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(
          imageVector = HybridSearchIcons.Clock,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(11.dp)
        )
        Text(
          text = recentQuery,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun ScopeRow(
  selectedScope: HybridSearchScope,
  onScopeSelected: (HybridSearchScope) -> Unit
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    HybridSearchScope.entries.forEach { scope ->
      val selected = scope == selectedScope
      val fillColor by animateColorAsState(
        targetValue = if (selected) {
          quranColors.ctaBackground
        } else {
          quranColors.cardSurface
        },
        animationSpec = AdaptiveMotionTokens.TabSwitchColorSpec,
        label = "scopePillFill"
      )
      val textColor by animateColorAsState(
        targetValue = if (selected) {
          quranColors.onCta
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = AdaptiveMotionTokens.TabSwitchColorSpec,
        label = "scopePillText"
      )
      val borderColor = if (selected) Color.Transparent else cardBorder
      val tag = when (scope) {
        HybridSearchScope.AYAHS -> SearchTestTags.SCOPE_AYAHS
        HybridSearchScope.SURAS -> SearchTestTags.SCOPE_SURAS
        HybridSearchScope.TAFSIR -> SearchTestTags.SCOPE_TAFSIR
      }
      val interactionSource = remember { MutableInteractionSource() }
      Box(
        modifier = Modifier
          .then(
            if (selected) {
              Modifier.shadow(
                elevation = if (isDark) 2.5.dp else 2.dp,
                shape = CircleShape,
                ambientColor = quranColors.ctaBackground,
                spotColor = quranColors.ctaBackground
              )
            } else {
              Modifier
            }
          )
          .clip(CircleShape)
          .background(fillColor)
          .border(width = 1.dp, color = borderColor, shape = CircleShape)
          .semantics(mergeDescendants = true) { this.selected = selected }
          .clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Tab,
            onClick = { onScopeSelected(scope) }
          )
          .padding(horizontal = 12.dp, vertical = 5.dp)
          .testTag(tag),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = stringResource(scope.labelRes),
          color = textColor,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1
        )
      }
    }
  }
}

@Composable
private fun AyahSearchResultCard(
  hit: AyahSearchHit,
  isBookmarked: Boolean,
  uthmaniFont: FontFamily,
  highlightColor: Color,
  highlightBg: Color,
  onOpenPage: () -> Unit,
  onPlayAyah: () -> Unit,
  onToggleBookmark: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val cardShape = RoundedCornerShape(18.dp)
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  val highlightColorArgb = highlightColor.toArgb()
  val highlightBgArgb = highlightBg.toArgb()
  val annotatedVerse = if (
    hit.precomputedAnnotatedText != null &&
    hit.precomputedHighlightColorArgb == highlightColorArgb &&
    hit.precomputedHighlightBgArgb == highlightBgArgb
  ) {
    hit.precomputedAnnotatedText
  } else {
    remember(hit.stableKey, hit.text, hit.highlightRanges, highlightColor, highlightBg) {
      HybridSearchHighlighter.buildHighlightedAnnotatedString(
        text = hit.text,
        ranges = hit.highlightRanges,
        highlightColor = highlightColor,
        highlightBackgroundColor = highlightBg
      )
    }
  }
  val headerText = hit.ayahRefLabel.ifEmpty {
    stringResource(
      R.string.hybrid_search_sura_ayah_header,
      hit.suraName,
      QuranUtils.getLocalizedNumber(hit.ayah)
    )
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(
        elevation = if (isDark) 2.5.dp else 2.dp,
        shape = cardShape,
        ambientColor = quranColors.shadowTint,
        spotColor = quranColors.shadowTint
      )
      .clip(cardShape)
      .background(quranColors.cardSurface)
      .border(width = 1.dp, color = cardBorder, shape = cardShape)
      .clickable(onClick = onOpenPage)
      .padding(horizontal = 14.dp, vertical = 10.dp)
      .testTag(SearchTestTags.ayahCard(hit.sura, hit.ayah)),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = headerText,
        color = quranColors.editorialAccent,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false)
      )
      Spacer(modifier = Modifier.width(8.dp))
      PageNumberPill(page = hit.page, preformattedLabel = hit.pageLabel)
    }

    Text(
      text = annotatedVerse,
      fontFamily = if (hit.isArabic) uthmaniFont else FontFamily.Default,
      fontSize = if (hit.isArabic) 18.5.sp else 15.sp,
      lineHeight = if (hit.isArabic) 35.sp else 23.sp,
      color = MaterialTheme.colorScheme.onSurface,
      style = TextStyle(
        textDirection = if (hit.isArabic) TextDirection.Rtl else TextDirection.Content
      ),
      modifier = Modifier
        .fillMaxWidth()
        .semantics {
          highlightRanges = hit.highlightRanges
        }
        .testTag(SearchTestTags.ayahText(hit.sura, hit.ayah))
    )

    HorizontalDivider(thickness = 1.dp, color = quranColors.divider)

    InlineResultActionBar(
      sura = hit.sura,
      ayah = hit.ayah,
      isBookmarked = isBookmarked,
      onPlayAyah = onPlayAyah,
      onToggleBookmark = onToggleBookmark,
      onOpenPage = onOpenPage
    )
  }
}

@Composable
private fun SuraSearchResultCard(
  hit: SuraSearchHit,
  onOpenPage: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val cardShape = RoundedCornerShape(18.dp)
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(
        elevation = if (isDark) 2.5.dp else 2.dp,
        shape = cardShape,
        ambientColor = quranColors.shadowTint,
        spotColor = quranColors.shadowTint
      )
      .clip(cardShape)
      .background(quranColors.cardSurface)
      .border(width = 1.dp, color = cardBorder, shape = cardShape)
      .clickable(onClick = onOpenPage)
      .padding(horizontal = 14.dp, vertical = 11.dp)
      .testTag(SearchTestTags.suraCard(hit.sura)),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    SearchEditorialRubElHizbMedallion(number = hit.sura, size = 36.dp)

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = stringResource(R.string.hybrid_search_sura_card_title, hit.displayName),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = hit.metaString,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.5.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        VerticalHairlineDivider(height = 10.dp, color = quranColors.divider)
        Text(
          text = stringResource(
            R.string.hybrid_search_juz_label,
            QuranUtils.getLocalizedNumber(hit.juzNumber)
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.5.sp,
          maxLines = 1
        )
      }
    }

    Column(
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      PageNumberPill(page = hit.startPage)
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable(onClick = onOpenPage)
          .padding(horizontal = 4.dp, vertical = 2.dp)
          .testTag(SearchTestTags.openPageButton(hit.sura, 1)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        Text(
          text = stringResource(R.string.hybrid_search_action_open_page),
          color = MaterialTheme.colorScheme.primary,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold
        )
        Icon(
          imageVector = HybridSearchIcons.OpenPageArrow,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(12.dp)
        )
      }
    }
  }
}

@Composable
private fun TafsirSearchResultCard(
  hit: TafsirSearchHit,
  isBookmarked: Boolean,
  highlightColor: Color,
  highlightBg: Color,
  onOpenPage: () -> Unit,
  onPlayAyah: () -> Unit,
  onToggleBookmark: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val cardShape = RoundedCornerShape(18.dp)
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  val editorialSoftBg = quranColors.editorialAccent.copy(alpha = if (isDark) 0.15f else 0.13f)
  val annotatedSnippet = remember(hit.stableKey, hit.text, hit.highlightRanges, highlightColor, highlightBg) {
    HybridSearchHighlighter.buildHighlightedAnnotatedString(
      text = hit.text,
      ranges = hit.highlightRanges,
      highlightColor = highlightColor,
      highlightBackgroundColor = highlightBg
    )
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(
        elevation = if (isDark) 2.5.dp else 2.dp,
        shape = cardShape,
        ambientColor = quranColors.shadowTint,
        spotColor = quranColors.shadowTint
      )
      .clip(cardShape)
      .background(quranColors.cardSurface)
      .border(width = 1.dp, color = cardBorder, shape = cardShape)
      .clickable(onClick = onOpenPage)
      .padding(horizontal = 14.dp, vertical = 10.dp)
      .testTag(SearchTestTags.tafsirCard(hit.sura, hit.ayah)),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = stringResource(
          R.string.hybrid_search_sura_ayah_header,
          hit.suraName,
          QuranUtils.getLocalizedNumber(hit.ayah)
        ),
        color = quranColors.editorialAccent,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false)
      )
      Spacer(modifier = Modifier.width(8.dp))
      PageNumberPill(page = hit.page)
    }

    if (hit.translatorName.isNotBlank()) {
      Box(
        modifier = Modifier
          .clip(CircleShape)
          .background(editorialSoftBg)
          .padding(horizontal = 8.dp, vertical = 2.dp)
      ) {
        Text(
          text = hit.translatorName,
          color = quranColors.editorialAccent,
          fontSize = 10.5.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    Text(
      text = annotatedSnippet,
      fontSize = 14.5.sp,
      lineHeight = 23.sp,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 5,
      overflow = TextOverflow.Ellipsis,
      style = TextStyle(
        textDirection = if (hit.isArabic) TextDirection.Rtl else TextDirection.Content
      ),
      modifier = Modifier.fillMaxWidth()
    )

    HorizontalDivider(thickness = 1.dp, color = quranColors.divider)

    InlineResultActionBar(
      sura = hit.sura,
      ayah = hit.ayah,
      isBookmarked = isBookmarked,
      onPlayAyah = onPlayAyah,
      onToggleBookmark = onToggleBookmark,
      onOpenPage = onOpenPage
    )
  }
}

@Composable
private fun InlineResultActionBar(
  sura: Int,
  ayah: Int,
  isBookmarked: Boolean,
  onPlayAyah: () -> Unit,
  onToggleBookmark: () -> Unit,
  onOpenPage: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .semantics(mergeDescendants = true) {}
          .clickable(role = Role.Button, onClick = onPlayAyah)
          .padding(horizontal = 6.dp, vertical = 4.dp)
          .testTag(SearchTestTags.listenButton(sura, ayah)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(
          imageVector = HybridSearchIcons.Play,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(12.dp)
        )
        Text(
          text = stringResource(R.string.hybrid_search_action_listen),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1
        )
      }

      VerticalHairlineDivider(height = 10.dp, color = quranColors.divider)

      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .semantics(mergeDescendants = true) {}
          .clickable(role = Role.Button, onClick = onToggleBookmark)
          .padding(horizontal = 6.dp, vertical = 4.dp)
          .testTag(SearchTestTags.bookmarkButton(sura, ayah)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(
          imageVector = if (isBookmarked) {
            HybridSearchIcons.BookmarkFilled
          } else {
            HybridSearchIcons.BookmarkOutline
          },
          contentDescription = null,
          tint = if (isBookmarked) {
            quranColors.editorialAccent
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
          modifier = Modifier.size(13.dp)
        )
        Text(
          text = stringResource(
            if (isBookmarked) {
              R.string.hybrid_search_action_bookmarked
            } else {
              R.string.hybrid_search_action_bookmark
            }
          ),
          color = if (isBookmarked) {
            quranColors.editorialAccent
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1
        )
      }
    }

    Row(
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .semantics(mergeDescendants = true) {}
        .clickable(role = Role.Button, onClick = onOpenPage)
        .padding(horizontal = 6.dp, vertical = 4.dp)
        .testTag(SearchTestTags.openPageButton(sura, ayah)),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      Text(
        text = stringResource(R.string.hybrid_search_action_open_page),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
      )
      Icon(
        imageVector = HybridSearchIcons.OpenPageArrow,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(12.dp)
      )
    }
  }
}

@Composable
private fun PageNumberPill(
  page: Int,
  preformattedLabel: String = ""
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val accentSoftBg = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.14f else 0.11f)
  Text(
    text = preformattedLabel.ifEmpty {
      stringResource(
        R.string.hybrid_search_page_pill,
        QuranUtils.getLocalizedNumber(page)
      )
    },
    color = MaterialTheme.colorScheme.primary,
    fontSize = 10.5.sp,
    fontWeight = FontWeight.SemiBold,
    maxLines = 1,
    modifier = Modifier
      .clip(CircleShape)
      .background(accentSoftBg)
      .padding(horizontal = 8.dp, vertical = 2.dp)
  )
}

@Composable
private fun SearchEditorialRubElHizbMedallion(
  number: Int,
  size: Dp = 36.dp,
  modifier: Modifier = Modifier
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val editorialColor = quranColors.editorialAccent
  val fillAlpha = if (isDark) 0.15f else 0.13f

  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center
  ) {
    Spacer(
      modifier = Modifier
        .fillMaxSize()
        .drawWithCache {
          val squareSide = this.size.minDimension * (26f / 40f)
          val topLeftOffset = Offset(
            x = (this.size.width - squareSide) / 2f,
            y = (this.size.height - squareSide) / 2f
          )
          val squareSize = Size(squareSide, squareSide)
          val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
          val fillColor = editorialColor.copy(alpha = fillAlpha)
          val stroke = Stroke(width = 1.35.dp.toPx())
          val pivot = Offset(this.size.width * 0.5f, this.size.height * 0.5f)

          onDrawBehind {
            drawRoundRect(
              color = fillColor,
              topLeft = topLeftOffset,
              size = squareSize,
              cornerRadius = corner
            )
            drawRoundRect(
              color = editorialColor,
              topLeft = topLeftOffset,
              size = squareSize,
              cornerRadius = corner,
              style = stroke
            )
            rotate(degrees = 45f, pivot = pivot) {
              drawRoundRect(
                color = editorialColor,
                topLeft = topLeftOffset,
                size = squareSize,
                cornerRadius = corner,
                style = stroke
              )
            }
          }
        }
    )
    Text(
      text = QuranUtils.getLocalizedNumber(number),
      fontSize = 11.5.sp,
      fontWeight = FontWeight.Bold,
      color = editorialColor,
      maxLines = 1
    )
  }
}

@Composable
private fun ActionableMessageCard(
  message: String,
  ctaLabel: String,
  onCtaClick: () -> Unit
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val cardShape = RoundedCornerShape(18.dp)
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(
        elevation = if (isDark) 2.5.dp else 2.dp,
        shape = cardShape,
        ambientColor = quranColors.shadowTint,
        spotColor = quranColors.shadowTint
      )
      .clip(cardShape)
      .background(quranColors.cardSurface)
      .border(width = 1.dp, color = cardBorder, shape = cardShape)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      text = message,
      color = MaterialTheme.colorScheme.onSurface,
      fontSize = 14.sp,
      lineHeight = 21.sp,
      modifier = Modifier.testTag(SearchTestTags.WARNING_TEXT)
    )
    Button(
      onClick = onCtaClick,
      shape = CircleShape,
      colors = ButtonDefaults.buttonColors(
        containerColor = quranColors.ctaBackground,
        contentColor = quranColors.onCta
      ),
      modifier = Modifier.testTag(SearchTestTags.ACTION_CTA_BUTTON)
    ) {
      Text(
        text = ctaLabel,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}

@Composable
private fun TafsirEmptyStateCard(onDownloadClick: () -> Unit) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val cardShape = RoundedCornerShape(18.dp)
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(
        elevation = if (isDark) 2.5.dp else 2.dp,
        shape = cardShape,
        ambientColor = quranColors.shadowTint,
        spotColor = quranColors.shadowTint
      )
      .clip(cardShape)
      .background(quranColors.cardSurface)
      .border(width = 1.dp, color = cardBorder, shape = cardShape)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(
        imageVector = HybridSearchIcons.Book,
        contentDescription = null,
        tint = quranColors.editorialAccent,
        modifier = Modifier.size(20.dp)
      )
      Text(
        text = stringResource(R.string.hybrid_search_tafsir_empty_title),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(SearchTestTags.WARNING_TEXT)
      )
    }
    Text(
      text = stringResource(R.string.hybrid_search_tafsir_empty_body),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 13.sp,
      lineHeight = 20.sp
    )
    Spacer(modifier = Modifier.height(2.dp))
    Button(
      onClick = onDownloadClick,
      shape = CircleShape,
      colors = ButtonDefaults.buttonColors(
        containerColor = quranColors.ctaBackground,
        contentColor = quranColors.onCta
      ),
      modifier = Modifier.testTag(SearchTestTags.ACTION_CTA_BUTTON)
    ) {
      Text(
        text = stringResource(R.string.hybrid_search_tafsir_download_cta),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}

@Composable
private fun CalmEmptyMessageCard(
  title: String,
  subtitle: String?,
  testTag: String?
) {
  val quranColors = LocalQuranColors.current
  val isDark = !quranColors.isLightToolbar
  val cardShape = RoundedCornerShape(18.dp)
  val cardBorder = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(
        elevation = if (isDark) 2.5.dp else 2.dp,
        shape = cardShape,
        ambientColor = quranColors.shadowTint,
        spotColor = quranColors.shadowTint
      )
      .clip(cardShape)
      .background(quranColors.cardSurface)
      .border(width = 1.dp, color = cardBorder, shape = cardShape)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Text(
      text = title,
      color = MaterialTheme.colorScheme.onSurface,
      fontSize = 14.5.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
    )
    if (!subtitle.isNullOrBlank()) {
      Text(
        text = subtitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        lineHeight = 20.sp
      )
    }
  }
}
