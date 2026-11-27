package com.quran.labs.androidquran.flashcards

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import java.util.Locale
import com.quran.mobile.common.ui.core.R as UiCoreR

internal val LocalFlashcardResources = staticCompositionLocalOf<Resources?> { null }

@Composable
internal fun flashcardString(@StringRes id: Int, vararg formatArgs: Any): String {
  val res = LocalFlashcardResources.current ?: LocalContext.current.resources
  return if (formatArgs.isEmpty()) {
    res.getString(id)
  } else {
    res.getString(id, *formatArgs)
  }
}

class MemoryFlashcardsActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val initialPage = intent?.getIntExtra(MemoryFlashcardManager.EXTRA_INITIAL_PAGE, 1)?.coerceIn(1, 604) ?: 1
    val initialStudyTextCardId = intent?.getStringExtra(MemoryFlashcardManager.EXTRA_STUDY_TEXT_CARD_ID)
    val initialStudyQueueIds = intent?.getStringArrayListExtra(MemoryFlashcardManager.EXTRA_STUDY_QUEUE_IDS)?.toList()
      ?: emptyList()
    FlashcardDueNotificationScheduler.initialize(this)

    setContent {
      QuranTheme {
        MemoryFlashcardsScreen(
          initialPage = initialPage,
          initialStudyTextCardId = initialStudyTextCardId,
          initialStudyQueueIds = initialStudyQueueIds,
          onBackClick = { finish() },
          onOpenCardInQuran = { card, queueIds ->
            val reviewIntent = MemoryFlashcardManager.createReviewIntent(
              context = this,
              card = card,
              queueIds = queueIds
            )
            startActivity(reviewIntent)
          }
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryFlashcardsScreen(
  initialPage: Int,
  initialStudyTextCardId: String? = null,
  initialStudyQueueIds: List<String> = emptyList(),
  onBackClick: () -> Unit,
  onOpenCardInQuran: (MemoryFlashcard, List<String>) -> Unit
) {
  val activityContext = LocalContext.current
  val currentConfig = LocalConfiguration.current
  val isArabic = remember(activityContext, currentConfig) {
    MemoryFlashcardManager.isAppInArabicMode(activityContext)
  }

  val localizedResources = remember(activityContext, isArabic) {
    try {
      val targetLocale = if (isArabic) Locale("ar") else Locale.ENGLISH
      val config = Configuration(activityContext.resources.configuration)
      config.setLocale(targetLocale)
      config.setLayoutDirection(targetLocale)
      activityContext.createConfigurationContext(config).resources
    } catch (_: Exception) {
      activityContext.resources
    }
  }

  val suraNames = remember(localizedResources, isArabic) {
    localizedResources.getStringArray(UiCoreR.array.sura_names)
  }

  val quranInfo = remember { MemoryFlashcardManager.getQuranInfo() }
  val initialBounds = remember(initialPage) { quranInfo.getPageBounds(initialPage) }
  var selectedFromSura by remember { mutableIntStateOf(initialBounds[0].coerceIn(1, 114)) }
  var selectedFromAyah by remember { mutableIntStateOf(initialBounds[1].coerceAtLeast(1)) }
  var selectedToSura by remember { mutableIntStateOf(initialBounds[2].coerceIn(1, 114)) }
  var selectedToAyah by remember { mutableIntStateOf(initialBounds[3].coerceAtLeast(1)) }
  var allowMultiSura by remember { mutableStateOf(initialBounds[0] != initialBounds[2]) }

  var cards by remember { mutableStateOf(MemoryFlashcardManager.getAllCards(activityContext)) }
  var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
  var cardPendingDeletion by remember { mutableStateOf<MemoryFlashcard?>(null) }
  var isCreateTextMode by remember { mutableStateOf(false) }

  var activeStudyTextCard by remember {
    mutableStateOf(
      initialStudyTextCardId?.let { MemoryFlashcardManager.getCardById(activityContext, it) }
    )
  }
  var studyQueueIds by remember { mutableStateOf(initialStudyQueueIds) }
  var studySessionIndex by remember { mutableIntStateOf(1) }
  var studySessionTotal by remember { mutableIntStateOf((1 + initialStudyQueueIds.size).coerceAtLeast(1)) }

  val openCardForStudyOrReview: (MemoryFlashcard, List<String>) -> Unit = { card, queueIds ->
    if (card.isTextCard()) {
      studyQueueIds = queueIds.filter { it != card.id }
      studySessionIndex = 1
      studySessionTotal = (1 + studyQueueIds.size).coerceAtLeast(1)
      activeStudyTextCard = card
    } else {
      onOpenCardInQuran(card, queueIds)
    }
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        nowMillis = System.currentTimeMillis()
        cards = MemoryFlashcardManager.getAllCards(activityContext)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  var hasNotificationPermission by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
          activityContext,
          Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasNotificationPermission = granted
    if (granted) {
      FlashcardDueNotificationScheduler.scheduleNextDueNotification(activityContext)
    }
  }

  // Automatically request push notification permission on Android 13+ when entering the screen
  LaunchedEffect(Unit) {
    if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      try {
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      } catch (_: Exception) {
      }
    }
  }

  BackHandler(enabled = cardPendingDeletion != null) {
    cardPendingDeletion = null
  }

  BackHandler(enabled = cardPendingDeletion == null && activeStudyTextCard != null) {
    activeStudyTextCard = null
    studyQueueIds = emptyList()
  }

  val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

  CompositionLocalProvider(
    LocalFlashcardResources provides localizedResources,
    LocalLayoutDirection provides layoutDirection
  ) {
    // If a card is pending deletion, show the dedicated Delete Confirmation Page
    val targetDeleteCard = cardPendingDeletion
    if (targetDeleteCard != null) {
      DeleteFlashcardConfirmationPage(
        card = targetDeleteCard,
        suraNames = suraNames,
        isArabic = isArabic,
        onConfirmDelete = {
          MemoryFlashcardManager.deleteCard(activityContext, targetDeleteCard.id)
          cards = MemoryFlashcardManager.getAllCards(activityContext)
          if (activeStudyTextCard?.id == targetDeleteCard.id) {
            activeStudyTextCard = null
          }
          cardPendingDeletion = null
        },
        onCancel = {
          cardPendingDeletion = null
        }
      )
      return@CompositionLocalProvider
    }

    // If studying a Hadith / Non-Quran text flashcard, show the 3D Flip Word-by-Word Study Page
    val currentTextStudyCard = activeStudyTextCard
    if (currentTextStudyCard != null) {
      val sessionCompleteMsg = flashcardString(R.string.flashcard_session_complete_toast)
      TextFlashcardStudyPage(
        card = currentTextStudyCard,
        cardIndexInSession = studySessionIndex,
        totalCardsInSession = studySessionTotal,
        isArabic = isArabic,
        onBack = {
          activeStudyTextCard = null
          studyQueueIds = emptyList()
        },
        onRateCard = { rating ->
          MemoryFlashcardManager.rateCardWithDifficulty(
            context = activityContext,
            cardId = currentTextStudyCard.id,
            rating = rating
          )
          nowMillis = System.currentTimeMillis()
          cards = MemoryFlashcardManager.getAllCards(activityContext)

          val remaining = studyQueueIds.toMutableList()
          var nextValidCard: MemoryFlashcard? = null
          while (remaining.isNotEmpty() && nextValidCard == null) {
            val nextId = remaining.removeAt(0)
            nextValidCard = MemoryFlashcardManager.getCardById(activityContext, nextId)
          }

          if (nextValidCard != null) {
            if (nextValidCard.isTextCard()) {
              studySessionIndex += 1
              studyQueueIds = remaining
              activeStudyTextCard = nextValidCard
            } else {
              activeStudyTextCard = null
              studyQueueIds = emptyList()
              onOpenCardInQuran(nextValidCard, remaining)
            }
          } else {
            activeStudyTextCard = null
            studyQueueIds = emptyList()
            android.widget.Toast.makeText(activityContext, sessionCompleteMsg, android.widget.Toast.LENGTH_SHORT).show()
          }
        }
      )
      return@CompositionLocalProvider
    }

    val maxFromAyah = quranInfo.getNumberOfAyahs(selectedFromSura).coerceAtLeast(1)
    if (selectedFromAyah > maxFromAyah) {
      selectedFromAyah = maxFromAyah
    }

    if (!allowMultiSura) {
      selectedToSura = selectedFromSura
    } else if (selectedToSura < selectedFromSura) {
      selectedToSura = selectedFromSura
    }

    val maxToAyah = quranInfo.getNumberOfAyahs(selectedToSura).coerceAtLeast(1)
    val minToAyah = if (selectedToSura == selectedFromSura) selectedFromAyah else 1
    if (selectedToAyah < minToAyah) {
      selectedToAyah = minToAyah
    } else if (selectedToAyah > maxToAyah) {
      selectedToAyah = maxToAyah
    }

    val previewFirstCue = remember(activityContext, selectedFromSura, selectedFromAyah) {
      MemoryFlashcardManager.getAyahFirstWordsCue(activityContext, selectedFromSura, selectedFromAyah)
    }
    val previewLastCue = remember(activityContext, selectedToSura, selectedToAyah) {
      MemoryFlashcardManager.getAyahFirstWordsCue(activityContext, selectedToSura, selectedToAyah)
    }
    val previewStartPage = remember(selectedFromSura, selectedFromAyah) {
      quranInfo.getPageFromSuraAyah(selectedFromSura, selectedFromAyah).coerceIn(1, 604)
    }
    val previewEndPage = remember(selectedToSura, selectedToAyah, previewStartPage) {
      quranInfo.getPageFromSuraAyah(selectedToSura, selectedToAyah).coerceIn(previewStartPage, 604)
    }

    // Always sorted by due date (nextReviewTimeMillis ascending)
    val sortedCards = remember(cards) {
      MemoryFlashcardManager.sortCardsByDueDate(cards)
    }
    val dueCards = remember(sortedCards, nowMillis) {
      sortedCards.filter { it.isDue(nowMillis) }
    }

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(
                text = flashcardString(R.string.memory_flashcards_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = flashcardString(R.string.memory_flashcards_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          navigationIcon = {
            TextButton(
              onClick = onBackClick,
              modifier = Modifier.testTag("flashcards_back_button")
            ) {
              Text(
                text = if (isArabic) "→" else "←",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        )
      }
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.TopCenter
      ) {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("flashcards_list"),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // Notification permission banner on Android 13+ if still not granted
          if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item(key = "notification_permission_banner") {
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = flashcardString(R.string.flashcard_enable_reminders_title),
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                      text = flashcardString(R.string.flashcard_enable_reminders_desc),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Button(
                    onClick = {
                      permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier = Modifier.testTag("enable_flashcard_notifications_button")
                  ) {
                    Text(text = flashcardString(R.string.flashcard_enable_button))
                  }
                }
              }
            }
          }

          // Card creator panel (Quran Ayahs OR Hadith & Custom Text)
          item(key = "create_flashcard_mode_tabs") {
            FlashcardCreationModeTabs(
              isTextMode = isCreateTextMode,
              onSelectTextMode = { isCreateTextMode = it }
            )
          }

          item(key = "create_flashcard_card") {
            if (isCreateTextMode) {
              CreateTextFlashcardPanel(
                isArabic = isArabic,
                onCardCreated = { createdCard ->
                  nowMillis = System.currentTimeMillis()
                  cards = MemoryFlashcardManager.getAllCards(activityContext)
                  if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                      permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } catch (_: Exception) {
                    }
                  }
                  openCardForStudyOrReview(createdCard, emptyList())
                }
              )
            } else {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = flashcardString(R.string.flashcard_create_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  TextButton(
                    onClick = { allowMultiSura = !allowMultiSura },
                    modifier = Modifier.testTag("toggle_multi_sura_button")
                  ) {
                    Text(
                      text = if (allowMultiSura) {
                        flashcardString(R.string.flashcard_single_sura_mode)
                      } else {
                        flashcardString(R.string.flashcard_multi_sura_mode)
                      },
                      style = MaterialTheme.typography.labelMedium
                    )
                  }
                }

                // Sura Selector
                SuraDropdownField(
                  label = flashcardString(R.string.flashcard_sura_label),
                  selectedSura = selectedFromSura,
                  suraNames = suraNames,
                  isArabic = isArabic,
                  onSuraSelected = { newSura ->
                    selectedFromSura = newSura
                    selectedFromAyah = 1
                    if (!allowMultiSura || selectedToSura < newSura) {
                      selectedToSura = newSura
                      selectedToAyah = quranInfo.getNumberOfAyahs(newSura).coerceAtMost(5)
                    }
                  },
                  testTag = "from_sura_selector"
                )

                // From Ayah -> To Ayah row
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  AyahStepperSelector(
                    label = flashcardString(R.string.flashcard_from_ayah_label),
                    value = selectedFromAyah,
                    minValue = 1,
                    maxValue = maxFromAyah,
                    isArabic = isArabic,
                    onValueChange = { newFrom ->
                      selectedFromAyah = newFrom
                      if (selectedToSura == selectedFromSura && selectedToAyah < newFrom) {
                        selectedToAyah = newFrom
                      }
                    },
                    modifier = Modifier.weight(1f),
                    testTagPrefix = "from_ayah"
                  )

                  Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                  )

                  AyahStepperSelector(
                    label = flashcardString(R.string.flashcard_to_ayah_label),
                    value = selectedToAyah,
                    minValue = minToAyah,
                    maxValue = maxToAyah,
                    isArabic = isArabic,
                    onValueChange = { newTo ->
                      selectedToAyah = newTo
                    },
                    modifier = Modifier.weight(1f),
                    testTagPrefix = "to_ayah"
                  )
                }

                if (allowMultiSura) {
                  SuraDropdownField(
                    label = flashcardString(R.string.flashcard_to_sura_label),
                    selectedSura = selectedToSura,
                    suraNames = suraNames,
                    minSura = selectedFromSura,
                    isArabic = isArabic,
                    onSuraSelected = { newToSura ->
                      selectedToSura = newToSura
                      val maxAyahForSura = quranInfo.getNumberOfAyahs(newToSura).coerceAtLeast(1)
                      selectedToAyah = if (newToSura == selectedFromSura) {
                        selectedFromAyah.coerceAtMost(maxAyahForSura)
                      } else {
                        1
                      }
                    },
                    testTag = "to_sura_selector"
                  )
                }

                val fromSuraNumStr = MemoryFlashcardManager.formatNumber(selectedFromSura, isArabic)
                val fromAyahNumStr = MemoryFlashcardManager.formatNumber(selectedFromAyah, isArabic)
                val toSuraNumStr = MemoryFlashcardManager.formatNumber(selectedToSura, isArabic)
                val toAyahNumStr = MemoryFlashcardManager.formatNumber(selectedToAyah, isArabic)
                val startPageStr = MemoryFlashcardManager.formatNumber(previewStartPage, isArabic)
                val endPageStr = MemoryFlashcardManager.formatNumber(previewEndPage, isArabic)

                val fromSuraDisplayName = suraNames.getOrElse(selectedFromSura - 1) { fromSuraNumStr }
                val toSuraDisplayName = suraNames.getOrElse(selectedToSura - 1) { toSuraNumStr }

                // Front Cue Live Preview
                FlashcardFrontCueBox(
                  firstAyahCue = previewFirstCue,
                  lastAyahCue = previewLastCue,
                  fromLabel = "$fromSuraDisplayName ($fromSuraNumStr:$fromAyahNumStr)",
                  toLabel = "$toSuraDisplayName ($toSuraNumStr:$toAyahNumStr)",
                  isArabic = isArabic,
                  pageRangeText = if (previewStartPage == previewEndPage) {
                    flashcardString(R.string.flashcard_single_page_label, startPageStr)
                  } else {
                    flashcardString(R.string.flashcard_page_range_label, startPageStr, endPageStr)
                  }
                )

                Button(
                  onClick = {
                    MemoryFlashcardManager.createFlashcard(
                      context = activityContext,
                      fromSura = selectedFromSura,
                      fromAyah = selectedFromAyah,
                      toSura = selectedToSura,
                      toAyah = selectedToAyah,
                      quranInfo = quranInfo
                    )
                    nowMillis = System.currentTimeMillis()
                    cards = MemoryFlashcardManager.getAllCards(activityContext)
                    if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                      try {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                      } catch (_: Exception) {
                      }
                    }
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("create_flashcard_button"),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Text(
                    text = flashcardString(R.string.flashcard_create_button),
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
            }
          }

          // Repetition schedule legend card
          item(key = "schedule_legend") {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surface,
              tonalElevation = 1.dp,
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = flashcardString(R.string.flashcard_schedule_title),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = flashcardString(R.string.flashcard_schedule_steps_text),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          // Review Due Cards CTA when cards are due
          if (dueCards.isNotEmpty()) {
            item(key = "due_cards_cta") {
              val dueCountStr = MemoryFlashcardManager.formatNumber(dueCards.size, isArabic)
              Button(
                onClick = {
                  val first = dueCards.first()
                  val queueIds = dueCards.drop(1).map { it.id }
                  openCardForStudyOrReview(first, queueIds)
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(52.dp)
                  .testTag("start_due_review_button")
              ) {
                Text(
                  text = flashcardString(R.string.flashcard_review_due_now_button, dueCountStr),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          // Empty state or cards list (sorted by due date)
          if (sortedCards.isEmpty()) {
            item(key = "empty_flashcards_state") {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = flashcardString(R.string.flashcard_empty_state),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center
                )
              }
            }
          } else {
            items(items = sortedCards, key = { it.id }) { card ->
              val isDue = card.isDue(nowMillis)
              FlashcardListItem(
                card = card,
                suraNames = suraNames,
                localizedResources = localizedResources,
                isArabic = isArabic,
                isDue = isDue,
                nowMillis = nowMillis,
                onCardClick = {
                  val otherDueIds = dueCards.filter { it.id != card.id }.map { it.id }
                  val fallbackQueueIds = if (otherDueIds.isNotEmpty()) {
                    otherDueIds
                  } else {
                    sortedCards.filter { it.id != card.id }.map { it.id }
                  }
                  openCardForStudyOrReview(card, fallbackQueueIds)
                },
                onDeleteClick = {
                  cardPendingDeletion = card
                }
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteFlashcardConfirmationPage(
  card: MemoryFlashcard,
  suraNames: Array<String>,
  isArabic: Boolean,
  onConfirmDelete: () -> Unit,
  onCancel: () -> Unit
) {
  val fromSuraStr = MemoryFlashcardManager.formatNumber(card.fromSura, isArabic)
  val fromAyahStr = MemoryFlashcardManager.formatNumber(card.fromAyah, isArabic)
  val toSuraStr = MemoryFlashcardManager.formatNumber(card.toSura, isArabic)
  val toAyahStr = MemoryFlashcardManager.formatNumber(card.toAyah, isArabic)
  val startPageStr = MemoryFlashcardManager.formatNumber(card.startPage, isArabic)
  val endPageStr = MemoryFlashcardManager.formatNumber(card.endPage, isArabic)

  val fromSuraName = suraNames.getOrElse(card.fromSura - 1) { fromSuraStr }
  val toSuraName = suraNames.getOrElse(card.toSura - 1) { toSuraStr }

  val pageRangeText = if (card.startPage == card.endPage) {
    flashcardString(R.string.flashcard_single_page_label, startPageStr)
  } else {
    flashcardString(R.string.flashcard_page_range_label, startPageStr, endPageStr)
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = flashcardString(R.string.flashcard_delete_confirm_page_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          TextButton(
            onClick = onCancel,
            modifier = Modifier.testTag("delete_confirm_back_button")
          ) {
            Text(
              text = if (isArabic) "→" else "←",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(20.dp)
        .testTag("delete_confirmation_page"),
      contentAlignment = Alignment.TopCenter
    ) {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 560.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text(
            text = flashcardString(R.string.flashcard_delete_confirm_page_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
          )

          Text(
            text = flashcardString(R.string.flashcard_delete_confirm_page_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          if (card.isTextCard()) {
            val sourceLabel = card.customTitle.ifBlank {
              flashcardString(R.string.flashcard_text_source_default)
            }
            val wordCountFormatted = MemoryFlashcardManager.formatNumber(card.parseWords().size, isArabic)
            FlashcardFrontCueBox(
              firstAyahCue = card.firstAyahCue,
              lastAyahCue = card.lastAyahCue,
              fromLabel = sourceLabel,
              toLabel = flashcardString(R.string.flashcard_text_word_count_badge, wordCountFormatted),
              isArabic = isArabic,
              pageRangeText = flashcardString(R.string.flashcard_tab_text)
            )
          } else {
            FlashcardFrontCueBox(
              firstAyahCue = card.firstAyahCue,
              lastAyahCue = card.lastAyahCue,
              fromLabel = "$fromSuraName ($fromSuraStr:$fromAyahStr)",
              toLabel = "$toSuraName ($toSuraStr:$toAyahStr)",
              isArabic = isArabic,
              pageRangeText = pageRangeText
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            OutlinedButton(
              onClick = onCancel,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("cancel_delete_flashcard_button")
            ) {
              Text(
                text = flashcardString(R.string.flashcard_delete_cancel_button),
                fontWeight = FontWeight.SemiBold
              )
            }

            Button(
              onClick = onConfirmDelete,
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("confirm_delete_flashcard_button")
            ) {
              Text(
                text = flashcardString(R.string.flashcard_delete_confirm_button),
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FlashcardFrontCueBox(
  firstAyahCue: String,
  lastAyahCue: String,
  fromLabel: String,
  toLabel: String,
  isArabic: Boolean,
  pageRangeText: String
) {
  val arrow = if (isArabic) "←" else "→"
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(14.dp)
      )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "$fromLabel $arrow $toLabel",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = pageRangeText,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      val fromBadgeText = flashcardString(R.string.flashcard_cue_from_badge)
      val toBadgeText = flashcardString(R.string.flashcard_cue_to_badge)

      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            BadgePill(text = fromBadgeText)
            Text(
              text = firstAyahCue,
              style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 19.sp,
                lineHeight = 28.sp
              ),
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            BadgePill(text = toBadgeText)
            Text(
              text = lastAyahCue,
              style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 19.sp,
                lineHeight = 28.sp
              ),
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BadgePill(text: String) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(MaterialTheme.colorScheme.primaryContainer)
      .padding(horizontal = 8.dp, vertical = 2.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onPrimaryContainer
    )
  }
}

@Composable
private fun FlashcardListItem(
  card: MemoryFlashcard,
  suraNames: Array<String>,
  localizedResources: Resources,
  isArabic: Boolean,
  isDue: Boolean,
  nowMillis: Long,
  onCardClick: () -> Unit,
  onDeleteClick: () -> Unit
) {
  val fromSuraStr = MemoryFlashcardManager.formatNumber(card.fromSura, isArabic)
  val fromAyahStr = MemoryFlashcardManager.formatNumber(card.fromAyah, isArabic)
  val toSuraStr = MemoryFlashcardManager.formatNumber(card.toSura, isArabic)
  val toAyahStr = MemoryFlashcardManager.formatNumber(card.toAyah, isArabic)
  val startPageStr = MemoryFlashcardManager.formatNumber(card.startPage, isArabic)
  val endPageStr = MemoryFlashcardManager.formatNumber(card.endPage, isArabic)

  val fromSuraName = suraNames.getOrElse(card.fromSura - 1) { fromSuraStr }
  val toSuraName = suraNames.getOrElse(card.toSura - 1) { toSuraStr }
  val arrow = if (isArabic) "←" else "→"

  val isTextCard = card.isTextCard()
  val rangeTitle = if (isTextCard) {
    card.customTitle.ifBlank { flashcardString(R.string.flashcard_text_source_default) }
  } else if (card.fromSura == card.toSura) {
    "$fromSuraName ($fromSuraStr:$fromAyahStr – $toAyahStr)"
  } else {
    "$fromSuraName ($fromSuraStr:$fromAyahStr) $arrow $toSuraName ($toSuraStr:$toAyahStr)"
  }
  val pageText = if (isTextCard) {
    val wordCountFormatted = MemoryFlashcardManager.formatNumber(card.parseWords().size, isArabic)
    "${flashcardString(R.string.flashcard_tab_text)} • ${flashcardString(R.string.flashcard_text_word_count_badge, wordCountFormatted)}"
  } else if (card.startPage == card.endPage) {
    "${flashcardString(R.string.flashcard_single_page_label, startPageStr)} • ${flashcardString(R.string.tajweed_mushaf_short)} • ${flashcardString(R.string.memorization_mode)}"
  } else {
    "${flashcardString(R.string.flashcard_page_range_label, startPageStr, endPageStr)} • ${flashcardString(R.string.tajweed_mushaf_short)} • ${flashcardString(R.string.memorization_mode)}"
  }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isDue) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
      } else {
        MaterialTheme.colorScheme.surface
      }
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onCardClick)
      .testTag("flashcard_item_${card.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = rangeTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = pageText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Schedule interval badge (localized in Arabic or English)
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.secondaryContainer)
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = card.currentScheduleLabel(isArabic),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSecondaryContainer
            )
          }

          // Delete button (opens confirmation page)
          TextButton(
            onClick = onDeleteClick,
            modifier = Modifier
              .size(48.dp)
              .testTag("delete_flashcard_${card.id}")
          ) {
            Text(
              text = "✕",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.error
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      val fromBadgeText = flashcardString(R.string.flashcard_cue_from_badge)
      val toBadgeText = flashcardString(R.string.flashcard_cue_to_badge)

      // Front Cue: First words of first ayah -> First words of last ayah
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            BadgePill(text = fromBadgeText)
            Text(
              text = card.firstAyahCue,
              style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 20.sp,
                lineHeight = 30.sp
              ),
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            BadgePill(text = toBadgeText)
            Text(
              text = card.lastAyahCue,
              style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 20.sp,
                lineHeight = 30.sp
              ),
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val statusText = if (isDue) {
          flashcardString(R.string.flashcard_status_due_now)
        } else {
          formatRemainingTime(localizedResources, card.nextReviewTimeMillis - nowMillis, isArabic)
        }
        Text(
          text = statusText,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = if (isDue) FontWeight.Bold else FontWeight.Normal,
          color = if (isDue) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          }
        )

        Text(
          text = if (isTextCard) {
            flashcardString(R.string.flashcard_text_tap_to_study_hint)
          } else {
            flashcardString(R.string.flashcard_tap_to_open_hint)
          },
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}

@Composable
private fun SuraDropdownField(
  label: String,
  selectedSura: Int,
  suraNames: Array<String>,
  minSura: Int = 1,
  isArabic: Boolean,
  onSuraSelected: (Int) -> Unit,
  testTag: String
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedSuraNumStr = MemoryFlashcardManager.formatNumber(selectedSura, isArabic)
  val currentName = suraNames.getOrElse(selectedSura - 1) { selectedSuraNumStr }

  Box(modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(
      onClick = { expanded = true },
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag(testTag)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "$selectedSuraNumStr. $currentName ▾",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.height(340.dp)
    ) {
      for (sura in minSura..114) {
        val suraNumStr = MemoryFlashcardManager.formatNumber(sura, isArabic)
        val sName = suraNames.getOrElse(sura - 1) { suraNumStr }
        DropdownMenuItem(
          text = { Text("$suraNumStr. $sName") },
          onClick = {
            expanded = false
            onSuraSelected(sura)
          }
        )
      }
    }
  }
}

@Composable
private fun AyahStepperSelector(
  label: String,
  value: Int,
  minValue: Int,
  maxValue: Int,
  isArabic: Boolean,
  onValueChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  testTagPrefix: String
) {
  var menuExpanded by remember { mutableStateOf(false) }
  val formattedValue = MemoryFlashcardManager.formatNumber(value, isArabic)

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surface,
    modifier = modifier
      .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(10.dp)
      )
      .padding(horizontal = 6.dp, vertical = 4.dp)
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        TextButton(
          onClick = { if (value > minValue) onValueChange(value - 1) },
          enabled = value > minValue,
          modifier = Modifier
            .size(48.dp)
            .testTag("${testTagPrefix}_dec")
        ) {
          Text(text = "−", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Box {
          Text(
            text = "$formattedValue ▾",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .clip(CircleShape)
              .clickable { menuExpanded = true }
              .padding(horizontal = 8.dp, vertical = 6.dp)
              .testTag("${testTagPrefix}_value")
          )
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.height(280.dp)
          ) {
            for (ayah in minValue..maxValue) {
              val ayahNumStr = MemoryFlashcardManager.formatNumber(ayah, isArabic)
              DropdownMenuItem(
                text = { Text(ayahNumStr) },
                onClick = {
                  menuExpanded = false
                  onValueChange(ayah)
                }
              )
            }
          }
        }

        TextButton(
          onClick = { if (value < maxValue) onValueChange(value + 1) },
          enabled = value < maxValue,
          modifier = Modifier
            .size(48.dp)
            .testTag("${testTagPrefix}_inc")
        ) {
          Text(text = "+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
      }
    }
  }
}

private fun formatRemainingTime(resources: Resources, remainingMs: Long, isArabic: Boolean): String {
  if (remainingMs <= 0L) return resources.getString(R.string.flashcard_status_due_now)
  val totalMinutes = (remainingMs / 60_000L).coerceAtLeast(1L)
  val totalHours = totalMinutes / 60L
  val totalDays = totalHours / 24L
  return when {
    totalDays >= 1L -> resources.getString(
      R.string.flashcard_due_in_days,
      MemoryFlashcardManager.formatNumber(totalDays.toInt(), isArabic)
    )
    totalHours >= 1L -> resources.getString(
      R.string.flashcard_due_in_hours,
      MemoryFlashcardManager.formatNumber(totalHours.toInt(), isArabic)
    )
    else -> resources.getString(
      R.string.flashcard_due_in_minutes,
      MemoryFlashcardManager.formatNumber(totalMinutes.toInt(), isArabic)
    )
  }
}
