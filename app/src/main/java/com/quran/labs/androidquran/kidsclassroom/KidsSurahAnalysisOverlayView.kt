package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.flashcards.MemoryFlashcardManager
import com.quran.labs.androidquran.memorization.MemorizationFormatting
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class AnalysisMistakeFilter {
  ALL,
  WORD_ERROR,
  MISSING,
  TASHKEEL
}

/**
 * Full-detail **Per-Surah Revision Analysis** Material 3 bottom sheet displaying missed words,
 * word-substitution errors, Harakah/Tashkeel mistakes, per-Ayah accuracy, 1-tap `0.75×` Slow-Mo
 * Sheikh Al-Minshawi replay, and 1-tap jump to practice any Ayah.
 */
@OptIn(ExperimentalMaterial3Api::class)
class KidsSurahAnalysisOverlayView(
  context: Context,
  private val initialSurah: Int,
  private val isNightPage: Boolean = false,
  private val onPracticeAyah: (SuraAyah) -> Unit,
  private val onDismissed: () -> Unit
) : FrameLayout(context) {

  init {
    val composeView = ComposeView(context).apply {
      setContent {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()

        fun dismissThen(action: () -> Unit) {
          scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismissed()
            action()
          }
        }

        QuranTheme(useDarkTheme = isSystemInDarkTheme() || isNightPage) {
          ModalBottomSheet(
            onDismissRequest = onDismissed,
            sheetState = sheetState,
            scrimColor = Color.Black.copy(alpha = 0.40f)
          ) {
            KidsSurahAnalysisContent(
              initialSurah = initialSurah,
              onListenWordSlowMo = { suraAyah, wordPos ->
                KidsClassroomManager.replayWordInSlowMo(context, suraAyah, wordPos)
              },
              onPracticeAyah = { suraAyah ->
                dismissThen { onPracticeAyah(suraAyah) }
              },
              onClearSurah = { surah ->
                KidsSurahAnalysisStore.clearSurah(context, surah)
              }
            )
          }
        }
      }
    }
    addView(composeView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KidsSurahAnalysisContent(
  initialSurah: Int,
  onListenWordSlowMo: (SuraAyah, Int) -> Unit,
  onPracticeAyah: (SuraAyah) -> Unit,
  onClearSurah: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val config = LocalConfiguration.current
  val isArabic = remember(context, config) { MemoryFlashcardManager.isAppInArabicMode(context) }
  val res = remember(context, isArabic) { MemorizationFormatting.localizedResources(context, isArabic) }
  val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

  LaunchedEffect(Unit) {
    KidsSurahAnalysisStore.ensureLoaded(context)
  }

  val allSummaries by KidsSurahAnalysisStore.summaries.collectAsState()
  var selectedSurah by remember(initialSurah) { mutableIntStateOf(initialSurah.coerceIn(1, 114)) }
  var activeFilter by remember(selectedSurah) { mutableStateOf(AnalysisMistakeFilter.ALL) }

  val surahChips = remember(allSummaries, initialSurah) {
    buildList {
      add(initialSurah.coerceIn(1, 114))
      allSummaries.keys.sorted().forEach { s ->
        if (s != initialSurah) add(s)
      }
    }
  }

  val summary = allSummaries[selectedSurah] ?: remember(selectedSurah) {
    KidsSurahAnalysisStore.emptySurahSummary(selectedSurah)
  }

  val filteredWords = remember(summary, activeFilter) {
    when (activeFilter) {
      AnalysisMistakeFilter.ALL -> summary.difficultWords
      AnalysisMistakeFilter.WORD_ERROR -> summary.difficultWords.filter { it.wordErrorCount > 0 }
      AnalysisMistakeFilter.MISSING -> summary.difficultWords.filter { it.missingCount > 0 }
      AnalysisMistakeFilter.TASHKEEL -> summary.difficultWords.filter { it.tashkeelErrorCount > 0 }
    }
  }

  CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
    LazyColumn(
      modifier = modifier
        .fillMaxWidth()
        .widthIn(max = 600.dp)
        .navigationBarsPadding()
        .testTag("kids_surah_analysis_sheet"),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header
      item(key = "header") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = res.getString(R.string.kids_surah_analysis_title),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = res.getString(R.string.kids_surah_analysis_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          if (summary.totalAttempts > 0 || summary.totalMistakes > 0) {
            TextButton(
              onClick = { onClearSurah(selectedSurah) },
              modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("kids_analysis_clear_surah_btn")
            ) {
              Text(
                text = res.getString(R.string.kids_analysis_clear_surah),
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }

      // Practiced Surah selector pills
      item(key = "surah_selector") {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(surahChips, key = { it }) { surahNum ->
            val sSummary = allSummaries[surahNum]
            val surahName = sSummary?.surahNameAr ?: MinshawiJuz30Repository.getSurahNameAr(surahNum)
            val sNumText = MemoryFlashcardManager.formatNumber(surahNum, isArabic)
            val errCount = sSummary?.uniqueDifficultWordCount ?: 0
            val label = if (errCount > 0) {
              "$sNumText · $surahName (${MemoryFlashcardManager.formatNumber(errCount, isArabic)})"
            } else {
              "$sNumText · $surahName"
            }
            FilterChip(
              selected = selectedSurah == surahNum,
              onClick = { selectedSurah = surahNum },
              label = { Text(label, fontWeight = FontWeight.SemiBold) },
              modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
          }
        }
      }

      // Overview Stats Card
      item(key = "overview_card") {
        SurahOverviewMetricsCard(
          summary = summary,
          res = res,
          isArabic = isArabic
        )
      }

      // Filter chips for difficult words
      if (summary.difficultWords.isNotEmpty()) {
        item(key = "filter_chips") {
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            val allCount = MemoryFlashcardManager.formatNumber(summary.difficultWords.size, isArabic)
            val wordCount = MemoryFlashcardManager.formatNumber(
              summary.difficultWords.count { it.wordErrorCount > 0 },
              isArabic
            )
            val missCount = MemoryFlashcardManager.formatNumber(
              summary.difficultWords.count { it.missingCount > 0 },
              isArabic
            )
            val tashkeelCount = MemoryFlashcardManager.formatNumber(
              summary.difficultWords.count { it.tashkeelErrorCount > 0 },
              isArabic
            )

            FilterChip(
              selected = activeFilter == AnalysisMistakeFilter.ALL,
              onClick = { activeFilter = AnalysisMistakeFilter.ALL },
              label = { Text("${res.getString(R.string.kids_analysis_filter_all)} ($allCount)") },
              modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
            FilterChip(
              selected = activeFilter == AnalysisMistakeFilter.WORD_ERROR,
              onClick = { activeFilter = AnalysisMistakeFilter.WORD_ERROR },
              label = { Text("${res.getString(R.string.kids_analysis_filter_word)} ($wordCount)") },
              modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
            FilterChip(
              selected = activeFilter == AnalysisMistakeFilter.MISSING,
              onClick = { activeFilter = AnalysisMistakeFilter.MISSING },
              label = { Text("${res.getString(R.string.kids_analysis_filter_missing)} ($missCount)") },
              modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
            FilterChip(
              selected = activeFilter == AnalysisMistakeFilter.TASHKEEL,
              onClick = { activeFilter = AnalysisMistakeFilter.TASHKEEL },
              label = { Text("${res.getString(R.string.kids_analysis_filter_tashkeel)} ($tashkeelCount)") },
              modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )
          }
        }

        item(key = "words_section_title") {
          Text(
            text = res.getString(R.string.kids_analysis_difficult_words_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
          )
        }

        items(
          items = filteredWords,
          key = { "word_${it.surah}_${it.ayah}_${it.mushafWordPosition}" }
        ) { record ->
          DifficultWordRevisionCard(
            record = record,
            res = res,
            isArabic = isArabic,
            onListenSlowMo = {
              onListenWordSlowMo(SuraAyah(record.surah, record.ayah), record.mushafWordPosition)
            },
            onPracticeAyah = {
              onPracticeAyah(SuraAyah(record.surah, record.ayah))
            }
          )
        }
      } else {
        item(key = "empty_state") {
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("kids_analysis_empty_state")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = "✨",
                fontSize = 32.sp
              )
              Text(
                text = res.getString(R.string.kids_analysis_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
              )
              Text(
                text = res.getString(R.string.kids_analysis_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        }
      }

      // Per-Ayah breakdown section
      if (summary.ayahSummaries.isNotEmpty()) {
        item(key = "ayah_breakdown_divider") {
          HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
        }
        item(key = "ayah_breakdown_title") {
          Text(
            text = res.getString(R.string.kids_analysis_ayah_breakdown),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
          )
        }
        items(
          items = summary.ayahSummaries,
          key = { "ayah_${it.surah}_${it.ayah}" }
        ) { ayahRow ->
          AyahBreakdownRow(
            ayahSummary = ayahRow,
            res = res,
            isArabic = isArabic,
            onPracticeAyah = { onPracticeAyah(SuraAyah(ayahRow.surah, ayahRow.ayah)) }
          )
        }
      }

      item(key = "bottom_spacer") {
        Spacer(modifier = Modifier.height(14.dp))
      }
    }
  }
}

@Composable
private fun SurahOverviewMetricsCard(
  summary: SurahAnalysisSummary,
  res: android.content.res.Resources,
  isArabic: Boolean
) {
  val totalAyat = MinshawiJuz30Repository.getAyahCountForSurah(summary.surah)
  val accStr = MemoryFlashcardManager.formatNumber(summary.avgAccuracyPercent, isArabic)
  val mistakesStr = MemoryFlashcardManager.formatNumber(summary.totalMistakes, isArabic)
  val practicedStr = "${MemoryFlashcardManager.formatNumber(summary.practicedAyahCount, isArabic)}/${
    MemoryFlashcardManager.formatNumber(totalAyat, isArabic)
  }"

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("kids_analysis_overview_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OverviewMetricTile(
          value = if (summary.totalAttempts > 0) "$accStr%" else "—",
          label = res.getString(R.string.kids_analysis_avg_score),
          accent = Color(0xFF059669),
          modifier = Modifier.weight(1f)
        )
        OverviewMetricTile(
          value = mistakesStr,
          label = res.getString(R.string.kids_analysis_total_mistakes),
          accent = Color(0xFFDC2626),
          modifier = Modifier.weight(1f)
        )
        OverviewMetricTile(
          value = practicedStr,
          label = res.getString(R.string.kids_analysis_practiced_ayat),
          accent = Color(0xFF0284C7),
          modifier = Modifier.weight(1f)
        )
      }

      if (summary.totalMistakes > 0) {
        val totalF = summary.totalMistakes.toFloat().coerceAtLeast(1f)
        val wordFrac = summary.wordErrorTotal / totalF
        val missFrac = summary.missingTotal / totalF
        val tashkeelFrac = summary.tashkeelErrorTotal / totalF

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          if (wordFrac > 0f) {
            Box(
              modifier = Modifier
                .weight(wordFrac)
                .height(10.dp)
                .background(Color(0xFFEF4444))
            )
          }
          if (missFrac > 0f) {
            Box(
              modifier = Modifier
                .weight(missFrac)
                .height(10.dp)
                .background(Color(0xFF991B1B))
            )
          }
          if (tashkeelFrac > 0f) {
            Box(
              modifier = Modifier
                .weight(tashkeelFrac)
                .height(10.dp)
                .background(Color(0xFFD97706))
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          ErrorLegendDot(
            color = Color(0xFFEF4444),
            label = "${res.getString(R.string.kids_analysis_filter_word)}: ${
              MemoryFlashcardManager.formatNumber(summary.wordErrorTotal, isArabic)
            }"
          )
          ErrorLegendDot(
            color = Color(0xFF991B1B),
            label = "${res.getString(R.string.kids_analysis_filter_missing)}: ${
              MemoryFlashcardManager.formatNumber(summary.missingTotal, isArabic)
            }"
          )
          ErrorLegendDot(
            color = Color(0xFFD97706),
            label = "${res.getString(R.string.kids_analysis_filter_tashkeel)}: ${
              MemoryFlashcardManager.formatNumber(summary.tashkeelErrorTotal, isArabic)
            }"
          )
        }
      }
    }
  }
}

@Composable
private fun OverviewMetricTile(
  value: String,
  label: String,
  accent: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = accent.copy(alpha = 0.12f),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = accent,
        maxLines = 1
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
private fun ErrorLegendDot(color: Color, label: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Box(
      modifier = Modifier
        .height(8.dp)
        .widthIn(min = 8.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(color)
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = FontWeight.Medium
    )
  }
}

@Composable
private fun DifficultWordRevisionCard(
  record: DifficultWordRecord,
  res: android.content.res.Resources,
  isArabic: Boolean,
  onListenSlowMo: () -> Unit,
  onPracticeAyah: () -> Unit
) {
  val accentColor = when (record.primaryErrorType) {
    WordGradeStatus.MISSING -> Color(0xFF991B1B)
    WordGradeStatus.WORD_ERROR -> Color(0xFFDC2626)
    WordGradeStatus.TASHKEEL_ERROR -> Color(0xFFD97706)
    else -> MaterialTheme.colorScheme.primary
  }
  val typeLabel = when (record.primaryErrorType) {
    WordGradeStatus.MISSING -> res.getString(R.string.kids_analysis_filter_missing)
    WordGradeStatus.WORD_ERROR -> res.getString(R.string.kids_analysis_filter_word)
    WordGradeStatus.TASHKEEL_ERROR -> res.getString(R.string.kids_analysis_filter_tashkeel)
    else -> ""
  }
  val surahNum = MemoryFlashcardManager.formatNumber(record.surah, isArabic)
  val ayahNum = MemoryFlashcardManager.formatNumber(record.ayah, isArabic)
  val mistakesStr = MemoryFlashcardManager.formatNumber(record.totalMistakes, isArabic)

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("difficult_word_card_${record.surah}_${record.ayah}_${record.mushafWordPosition}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = record.uthmaniWord,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          if (!record.lastMatchedHypWord.isNullOrBlank()) {
            Text(
              text = res.getString(R.string.kids_analysis_you_said, record.lastMatchedHypWord),
              style = MaterialTheme.typography.bodySmall,
              color = accentColor,
              fontWeight = FontWeight.Medium
            )
          }
        }

        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = accentColor.copy(alpha = 0.14f)
          ) {
            Text(
              text = "$typeLabel · ${res.getString(R.string.kids_analysis_mistakes_count, mistakesStr)}",
              style = MaterialTheme.typography.labelSmall,
              color = accentColor,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
          ) {
            Text(
              text = "$surahNum:$ayahNum",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilledTonalButton(
          onClick = onListenSlowMo,
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
        ) {
          Text(
            text = "🔊 " + res.getString(R.string.kids_analysis_listen_slow),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Button(
          onClick = onPracticeAyah,
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
        ) {
          Text(
            text = res.getString(R.string.kids_analysis_practice_ayah),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@Composable
private fun AyahBreakdownRow(
  ayahSummary: AyahAnalysisSummary,
  res: android.content.res.Resources,
  isArabic: Boolean,
  onPracticeAyah: () -> Unit
) {
  val surahNum = MemoryFlashcardManager.formatNumber(ayahSummary.surah, isArabic)
  val ayahNum = MemoryFlashcardManager.formatNumber(ayahSummary.ayah, isArabic)
  val avgInt = ayahSummary.avgScorePercent.roundToInt().coerceIn(0, 100)
  val avgStr = MemoryFlashcardManager.formatNumber(avgInt, isArabic)
  val errStr = MemoryFlashcardManager.formatNumber(ayahSummary.mistakeCount, isArabic)
  val scoreColor = when {
    avgInt >= 90 -> Color(0xFF059669)
    avgInt >= 80 -> Color(0xFF0284C7)
    else -> Color(0xFFD97706)
  }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = "$surahNum:$ayahNum · $avgStr%",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = scoreColor
        )
        if (ayahSummary.mistakeCount > 0) {
          Text(
            text = res.getString(R.string.kids_analysis_mistakes_count, errStr),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
          )
        }
      }

      OutlinedButton(
        onClick = onPracticeAyah,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
      ) {
        Text(
          text = res.getString(R.string.kids_analysis_practice_ayah),
          fontWeight = FontWeight.SemiBold
        )
      }
    }
  }
}
