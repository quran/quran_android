package com.quran.labs.androidquran.tv.reading

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.material3.MaterialTheme
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/**
 * Ayah data model
 */
data class AyahModel(
  val surah: Int,
  val ayah: Int,
  val page: Int,
  val text: String,
  val arabicText: String = ""
)

/**
 * Complete page data with ayahs
 */
data class QuranPageData(
  val pageNumber: Int,
  val surahsOnPage: List<SurahOnPage>,
  val ayahs: List<AyahModel>
)

data class SurahOnPage(
  val surahNumber: Int,
  val surahName: String,
  val surahNameArabic: String,
  val startAyah: Int,
  val ayahCount: Int
)

/**
 * Get ayahs for a specific page
 * In production, this would load from the database
 */
fun getAyahsForPage(page: Int): List<AyahModel> {
  // Placeholder implementation with sample data
  // In production, this would use the QuranDataProvider to load actual ayah data

  val ayahsMap: Map<Int, List<AyahModel>> = mapOf(
    // Al-Fatihah (page 1)
    1 to listOf(
      AyahModel(1, 1, 1, "In the name of Allah, the Entirely Merciful.", "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ"),
      AyahModel(1, 2, 1, "All praise is due to Allah, Lord of the worlds.", "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ"),
      AyahModel(1, 3, 1, "The Entirely Merciful, the Especially Merciful.", "الرَّحْمَانِ الرَّحِيمِ"),
      AyahModel(1, 4, 1, "Master of the Day of Judgment.", "مَالِكِ يَوْمِ الدِّينِ"),
      AyahModel(1, 5, 1, "You alone we worship, and You alone we ask for help.", "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ"),
      AyahModel(1, 6, 1, "Guide us to the straight path,", "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ"),
      AyahModel(1, 7, 1, "The path of those upon whom You have favored", "صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ")
    ),
    // Al-Baqarah (page 2) - just a sample
    2 to listOf(
      AyahModel(2, 1, 2, "Alif, Lam, Meem.", "الٓم"),
      AyahModel(2, 2, 2, "This is the Book about which there is no doubt", "ذَٰلِكَ لَا رَيْبَ فِيهِ هُدًى لِّلْمُتَّقِينَ"),
      AyahModel(2, 3, 2, "Who believe in the unseen, establish prayer", "الَّذِينَ يُؤْمِنُونَ بِالْغَيْبِ وَيُقِيمُونَ الصَّلَاةَ"),
      AyahModel(2, 4, 2, "And who believe in what has been revealed to you", "وَالَّذِينَ يُؤْمِنُونَ بِمَا أُنْزِلَ إِلَيْكَ"),
      AyahModel(2, 5, 2, "Those are upon [right] guidance from their Lord", "أُولَٰئِكَ عَلَى هُدًى مِّن رَبِّهِمْ")
    ),
    // Ar-Rahman (page 531) - sample
    531 to listOf(
      AyahModel(55, 1, 531, "The Beneficent", "الرَّحْمَنُ"),
      AyahModel(55, 2, 531, "Taught the Quran,", "عَلَّمَ الْقُرْآنَ"),
      AyahModel(55, 3, 531, "Created man,", "خَلَقَ الْإِنسَانَ"),
      AyahModel(55, 4, 531, "[And] taught him eloquence.", "عَلَّمَهُ الْبَيَانَ")
    )
  )

  return ayahsMap[page] ?: listOf(
    AyahModel(1, 1, page, "Page ${page} - Coming Soon", "")
  )
}

/**
 * Get surahs on a specific page
 */
fun getSurahsOnPage(page: Int): List<SurahOnPage> {
  // Simplified mapping - in production, use QuranInfo
  return when (page) {
    1 -> listOf(
      SurahOnPage(1, "Al-Fatihah", "الفاتحة", 1, 7)
    )
    2 -> listOf(
      SurahOnPage(2, "Al-Baqarah", "البقرة", 1, 40)
    )
    531 -> listOf(
      SurahOnPage(55, "Ar-Rahman", "الرحمن", 1, 78)
    )
    else -> listOf(
      SurahOnPage(1, "Quran", "القرآن", 1, 7)
    )
  }
}

/**
 * Get page info with surah and ayah data
 */
fun getQuranPageData(page: Int): QuranPageData {
  val ayahs = getAyahsForPage(page)
  val surahs = getSurahsOnPage(page)

  return QuranPageData(
    pageNumber = page,
    surahsOnPage = surahs,
    ayahs = ayahs
  )
}

/**
 * Get page image file path
 */
fun getPageImagePath(page: Int): String {
  // In production, this would use the page type to determine the correct path
  // For now, using a simplified approach
  val width = 1024 // standard width for page images
  return "file:///android_asset/pages/$width/page_$page.png"
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvPageViewerScreen(
  initialPage: Int = 1,
  onPageChange: (Int) -> Unit = {},
  onBackClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var currentPage by remember { mutableIntStateOf(initialPage) }
  var currentAyah by remember { mutableIntStateOf(1) }
  var showFloatingMenu by remember { mutableStateOf(false) }

  val pageData = remember(currentPage) { getQuranPageData(currentPage) }
  val currentAyahData by remember {
    derivedStateOf {
      pageData.ayahs.find { it.ayah == currentAyah } ?: pageData.ayahs.firstOrNull()
    }
  }

  val focusRequester = remember { FocusRequester() }

  // Auto-focus the page viewer when loaded
  LaunchedEffect(Unit) {
    delay(300)
    focusRequester.requestFocus()
  }

  // Reset to first ayah when page changes
  LaunchedEffect(currentPage) {
    currentAyah = pageData.ayahs.firstOrNull()?.ayah ?: 1
    onPageChange(currentPage)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .focusRequester(focusRequester)
      .onKeyEvent { keyEvent ->
        when (keyEvent.key) {
          Key.DirectionCenter -> {
            showFloatingMenu = !showFloatingMenu
            true
          }
          Key.DirectionRight -> {
            if (currentPage < 604) {
              currentPage++
            }
            true
          }
          Key.DirectionLeft -> {
            if (currentPage > 1) {
              currentPage--
            }
            true
          }
          Key.DirectionUp -> {
            // Navigate to previous ayah
            val currentIndex = pageData.ayahs.indexOfFirst { it.ayah == currentAyah }
            if (currentIndex > 0) {
              currentAyah = pageData.ayahs[currentIndex - 1].ayah
            }
            true
          }
          Key.DirectionDown -> {
            // Navigate to next ayah
            val currentIndex = pageData.ayahs.indexOfFirst { it.ayah == currentAyah }
            if (currentIndex < pageData.ayahs.size - 1) {
              currentAyah = pageData.ayahs[currentIndex + 1].ayah
            }
            true
          }
          Key.Back, Key.Escape -> {
            if (showFloatingMenu) {
              showFloatingMenu = false
              true
            } else {
              onBackClick()
              false
            }
          }
          else -> false
        }
      }
  ) {
    // Quran page content
    QuranPageContent(
      pageData = pageData,
      currentAyah = currentAyah
    )

    // Top info bar (always visible for TV)
    TopInfoBar(
      currentPage = currentPage,
      currentAyah = currentAyah,
      pageData = pageData,
      currentAyahData = currentAyahData,
      modifier = Modifier.align(Alignment.TopCenter)
    )

    // Bottom controls hint (shows briefly)
    ControlsHint(Modifier.align(Alignment.BottomCenter))

    // Floating settings menu (shown on center button)
    if (showFloatingMenu) {
      FloatingSettingsMenu(
        onDismiss = { showFloatingMenu = false },
        onJumpToPage = { page ->
          currentPage = page.coerceIn(1, 604)
          showFloatingMenu = false
        },
        onJumpToSurah = { sura ->
          // Jump to surah's first page
          val surahStartPage = getSurahStartPage(sura)
          currentPage = surahStartPage
          showFloatingMenu = false
        },
        onJumpToJuz = { juz ->
          val juzStartPage = getJuzStartPage(juz)
          currentPage = juzStartPage
          showFloatingMenu = false
        }
      )
    }
  }
}

@Composable
fun QuranPageContent(
  pageData: QuranPageData,
  currentAyah: Int
) {
  val context = LocalContext.current

  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    // Try to load the page image
    SubcomposeAsyncImage(
      model = ImageRequest.Builder(LocalContext.current)
        .data(getPageImagePath(pageData.pageNumber))
        .crossfade(true)
        .build(),
      contentDescription = "Quran Page ${pageData.pageNumber}",
      loading = {
        // Loading placeholder
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Text(
              text = "Loading page ${pageData.pageNumber}...",
              style = MaterialTheme.typography.titleLarge,
              color = Color.White
            )
            Text(
              text = "Page: ${pageData.pageNumber}",
              style = MaterialTheme.typography.bodyLarge,
              color = Color.White.copy(alpha = 0.7f)
            )
          }
        }
      },
      success = { painterState ->
        // Page image loaded
        Box(modifier = Modifier.fillMaxSize()) {
          Image(
            painter = painterState.painter,
            contentDescription = "Quran Page ${pageData.pageNumber}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
          )
        }
      },
      error = {
        // Error placeholder - show text content instead
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            // Show all ayahs for this page
            items(pageData.ayahs) { ayah ->
              val isCurrentAyah = ayah.ayah == currentAyah
              Box(
                modifier = Modifier
                  .fillMaxWidth(0.85f)
                  .background(
                    if (isCurrentAyah) {
                      Color.Yellow.copy(alpha = 0.3f)
                    } else {
                      Color.Transparent
                    },
                    RoundedCornerShape(8.dp)
                  )
                  .padding(16.dp)
                  .border(
                    width = if (isCurrentAyah) 2.dp else 0.dp,
                    color = if (isCurrentAyah) Color.Yellow else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                  )
                  .clickable {
                    // Could open ayah-specific menu
                  }
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  // Arabic text
                  if (ayah.arabicText.isNotEmpty()) {
                    Text(
                      text = ayah.arabicText,
                      style = MaterialTheme.typography.displaySmall,
                      color = if (isCurrentAyah) Color.Yellow else Color.White,
                      fontSize = 28.sp,
                      textAlign = TextAlign.Center,
                      fontWeight = FontWeight.Bold
                    )
                  }
                  // Translation text
                  Text(
                    text = ayah.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                  )
                  // Ayah reference
                  Text(
                    text = "${pageData.surahsOnPage.firstOrNull { it.startAyah <= ayah.ayah }?.surahName ?: ""}:${ayah.ayah}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f)
                  )
                }
              }
            }
          }
        }
      }
    )
  }
}

@Composable
fun TopInfoBar(
  currentPage: Int,
  currentAyah: Int,
  pageData: QuranPageData,
  currentAyahData: AyahModel?,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(Color.Black.copy(alpha = 0.8f))
      .padding(16.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Page and surah info
      Column {
        Text(
          text = "Page $currentPage",
          style = MaterialTheme.typography.titleLarge,
          color = Color.White
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          pageData.surahsOnPage.forEach { surah ->
            Text(
              text = "${surah.surahNameArabic} (${surah.surahName})",
              style = MaterialTheme.typography.bodyLarge,
              color = Color.White.copy(alpha = 0.8f)
            )
          }
        }
      }

      // Current ayah indicator
      if (currentAyahData != null) {
        Text(
          text = "Ayah $currentAyah",
          style = MaterialTheme.typography.titleLarge,
          color = Color.Yellow,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
fun ControlsHint(modifier: Modifier = Modifier) {
  // Shows controls hint - would auto-hide after a few seconds in production
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(Color.Black.copy(alpha = 0.6f))
      .padding(24.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "◄► Pages",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.8f)
      )
      Text(
        text = "▲ ▼ Ayahs",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.8f)
      )
      Text(
        text = "● Menu",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.8f)
      )
    }
  }
}

@Composable
fun FloatingSettingsMenu(
  onDismiss: () -> Unit = {},
  onJumpToPage: (Int) -> Unit = {},
  onJumpToSurah: (Int) -> Unit = {},
  onJumpToJuz: (Int) -> Unit = {},
  modifier: Modifier = Modifier
) {
  var selectedTab by remember { mutableStateOf(0) }
  val tabs = listOf("Page", "Surah", "Juz")

  var jumpPageInput by remember { mutableStateOf("") }
  var jumpSurahInput by remember { mutableStateOf("") }
  var jumpJuzInput by remember { mutableStateOf("") }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.9f))
      .clickable { onDismiss() }
      .padding(48.dp),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.8f)
        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
        .padding(32.dp)
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Jump to",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
          )
          Box(
            modifier = Modifier
              .size(40.dp, 40.dp)
              .background(MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
              .clickable { onDismiss() }
              .padding(8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "✕",
              style = MaterialTheme.typography.titleLarge,
              fontSize = 24.sp,
              color = Color.White
            )
          }
        }

        // Tab selector
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          tabs.forEachIndexed { index, tab ->
            val isSelected = selectedTab == index
            Box(
              modifier = Modifier
                .background(
                  if (isSelected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surfaceVariant,
                  RoundedCornerShape(8.dp)
                )
                .clickable { selectedTab = index }
                .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
              Text(
                text = tab,
                style = MaterialTheme.typography.titleLarge,
                color = if (isSelected) Color.White
                else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        // Content based on selected tab
        when (selectedTab) {
          0 -> {
            // Page jump
            Column(
              verticalArrangement = Arrangement.spacedBy(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = "Enter page number (1-604):",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(200.dp, 50.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = jumpPageInput,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }

                Button(
                  onClick = {
                    jumpPageInput.toIntOrNull()?.let { page ->
                      if (page in 1..604) {
                        onJumpToPage(page)
                        onDismiss()
                      }
                    }
                  },
                  modifier = Modifier.size(60.dp, 50.dp)
                ) {
                  Text(
                    text = "Go",
                    style = MaterialTheme.typography.titleLarge
                  )
                }
              }

              // Quick page buttons
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
              ) {
                listOf(1, 604).forEach { page ->
                  Box(
                    modifier = Modifier
                      .size(60.dp, 40.dp)
                      .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                      .clickable {
                        onJumpToPage(page)
                        onDismiss()
                      }
                      .padding(4.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = "$page",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }
                }
              }
            }
          }

          1 -> {
            // Surah jump
            Column(
              verticalArrangement = Arrangement.spacedBy(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = "Enter surah number (1-114):",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(200.dp, 50.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = jumpSurahInput,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }

                Button(
                  onClick = {
                    jumpSurahInput.toIntOrNull()?.let { sura ->
                      if (sura in 1..114) {
                        onJumpToSurah(sura)
                        onDismiss()
                      }
                    }
                  },
                  modifier = Modifier.size(60.dp, 50.dp)
                ) {
                  Text(
                    text = "Go",
                    style = MaterialTheme.typography.titleLarge
                  )
                }
              }

              // Popular surahs
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
              ) {
                listOf(
                  1 to "Al-Fatihah",
                  2 to "Al-Baqarah",
                  18 to "Al-Kahf",
                  36 to "Ya-Sin",
                  55 to "Ar-Rahman",
                  67 to "Al-Mulk"
                ).forEach { (number, name) ->
                  Box(
                    modifier = Modifier
                      .width(100.dp)
                      .height(40.dp)
                      .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                      .clickable {
                        onJumpToSurah(number)
                        onDismiss()
                      }
                      .padding(4.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = "$number",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                      textAlign = TextAlign.Center
                    )
                  }
                }
              }
            }
          }

          2 -> {
            // Juz jump
            Column(
              verticalArrangement = Arrangement.spacedBy(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = "Enter juz number (1-30):",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(200.dp, 50.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = jumpJuzInput,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }

                Button(
                  onClick = {
                    jumpJuzInput.toIntOrNull()?.let { juz ->
                      if (juz in 1..30) {
                        onJumpToJuz(juz)
                        onDismiss()
                      }
                    }
                  },
                  modifier = Modifier.size(60.dp, 50.dp)
                ) {
                  Text(
                    text = "Go",
                    style = MaterialTheme.typography.titleLarge
                  )
                }
              }

              // Quick juz buttons
              LazyColumn(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                items((1..30).chunked(6)) { row ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                  ) {
                    row.forEach { juz ->
                      Box(
                        modifier = Modifier
                          .width(60.dp)
                          .height(35.dp)
                          .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                          .clickable {
                            onJumpToJuz(juz)
                            onDismiss()
                          }
                          .padding(4.dp),
                        contentAlignment = Alignment.Center
                      ) {
                        Text(
                          text = "$juz",
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

// Helper functions
private fun getSurahStartPage(surah: Int): Int {
  // Simplified - would use QuranInfo in production
  val surahStartPages = mapOf(
    1 to 1, 2 to 2, 3 to 50, 4 to 77, 5 to 106,
    18 to 293, 36 to 440, 55 to 531, 67 to 562
  )
  return surahStartPages[surah] ?: 1
}

private fun getJuzStartPage(juz: Int): Int {
  // Simplified - would use QuranInfo in production
  val juzStartPages = listOf(
    1, 22, 42, 62, 82, 102, 121, 142, 162, 182,
    201, 221, 242, 262, 282, 302, 322, 342, 362, 382,
    402, 422, 442, 462, 482, 502, 522, 542, 562, 582
  )
  return juzStartPages.getOrNull(juz - 1) ?: 1
}
