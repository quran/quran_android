package com.quran.labs.androidquran.flashcards

import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import java.util.Locale

private data class SampleHadithItem(
  val titleAr: String,
  val titleEn: String,
  val text: String
)

private val SAMPLE_HADITHS = listOf(
  SampleHadithItem(
    titleAr = "حديث شريف (عمدة الأحكام - متفق عليه)",
    titleEn = "Hadith (Agreed Upon - Umdat al-Ahkam)",
    text = "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى، فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى اللَّهِ وَرَسُولِهِ فَهِجْرَتُهُ إِلَى اللَّهِ وَرَسُولِهِ"
  ),
  SampleHadithItem(
    titleAr = "حديث شريف (متفق عليه)",
    titleEn = "Hadith (Sahih Al-Bukhari & Muslim)",
    text = "مَنْ كَانَ يُؤْمِنُ بِاللَّهِ وَالْيَوْمِ الآخِرِ فَلْيَقُلْ خَيْرًا أَوْ لِيَصْمُتْ، وَمَنْ كَانَ يُؤْمِنُ بِاللَّهِ وَالْيَوْمِ الآخِرِ فَلْيُكْرِمْ جَارَهُ"
  ),
  SampleHadithItem(
    titleAr = "حديث شريف (رواه مسلم)",
    titleEn = "Hadith (Sahih Muslim)",
    text = "الدِّينُ النَّصِيحَةُ، قُلْنَا: لِمَنْ؟ قَالَ: لِلَّهِ وَلِكِتَابِهِ وَلِرَسُولِهِ وَلِأَئِمَّةِ الْمُسْلِمِينَ وَعَامَّتِهِمْ"
  )
)

private val ARABIC_SCRIPT_REGEX = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF]")

internal fun containsArabicScript(text: String): Boolean {
  return ARABIC_SCRIPT_REGEX.containsMatchIn(text)
}

@Composable
internal fun FlashcardCreationModeTabs(
  isTextMode: Boolean,
  onSelectTextMode: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      ModeTabPill(
        title = flashcardString(R.string.flashcard_tab_quran),
        icon = QuranIcons.MenuBook,
        selected = !isTextMode,
        onClick = { onSelectTextMode(false) },
        modifier = Modifier
          .weight(1f)
          .testTag("tab_create_quran_card")
      )
      ModeTabPill(
        title = flashcardString(R.string.flashcard_tab_text),
        icon = QuranIcons.Notes,
        selected = isTextMode,
        onClick = { onSelectTextMode(true) },
        modifier = Modifier
          .weight(1f)
          .testTag("tab_create_text_card")
      )
    }
  }
}

@Composable
private fun ModeTabPill(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val bgColor = if (selected) {
    MaterialTheme.colorScheme.surface
  } else {
    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
  }
  val contentColor = if (selected) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = bgColor,
    shadowElevation = if (selected) 2.dp else 0.dp,
    modifier = modifier
      .height(48.dp)
      .clip(RoundedCornerShape(10.dp))
      .clickable(onClick = onClick)
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
internal fun CreateTextFlashcardPanel(
  isArabic: Boolean,
  onCardCreated: (MemoryFlashcard) -> Unit
) {
  val context = LocalContext.current
  var inputText by remember { mutableStateOf("") }
  var sourceTitle by remember { mutableStateOf("") }
  var autoFourWordHint by remember { mutableStateOf(true) }
  var fastRevealMode by remember { mutableStateOf(false) }
  var sampleIndex by remember { mutableIntStateOf(0) }

  val words = remember(inputText) { MemoryFlashcardManager.parseTextWords(inputText) }
  val hintCount = if (autoFourWordHint) 4 else 2
  val liveFrontPrompt = remember(inputText, hintCount) {
    MemoryFlashcardManager.extractTextFrontCue(inputText, hintCount)
  }
  val emptyWarningMsg = flashcardString(R.string.flashcard_text_empty_warning)
  val defaultSourceLabel = flashcardString(R.string.flashcard_text_source_default)

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("create_text_flashcard_panel")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Header row with icon + title + Try Sample button
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = QuranIcons.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
              )
            }
            Column {
              Text(
                text = flashcardString(R.string.flashcard_text_create_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = flashcardString(R.string.flashcard_text_create_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
              .height(36.dp)
              .clip(CircleShape)
              .clickable {
                val sample = SAMPLE_HADITHS[sampleIndex % SAMPLE_HADITHS.size]
                sampleIndex += 1
                inputText = sample.text
                sourceTitle = if (isArabic) sample.titleAr else sample.titleEn
              }
              .testTag("load_sample_hadith_button")
          ) {
            Box(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = flashcardString(R.string.flashcard_text_try_sample),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
              )
            }
          }
        }

        // Source / Title input + Clipboard Paste button
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = sourceTitle,
            onValueChange = { sourceTitle = it },
            placeholder = {
              Text(
                text = flashcardString(R.string.flashcard_text_source_label),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("text_flashcard_source_input")
          )

          FilledTonalButton(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
              val pasted = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
              if (!pasted.isNullOrEmpty()) {
                inputText = pasted
              }
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier
              .height(52.dp)
              .testTag("paste_clipboard_button")
          ) {
            Text(
              text = flashcardString(R.string.flashcard_text_paste_clipboard),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // Main Tashkeel-preserving textarea
        val textLayoutDir = if (inputText.isNotBlank()) {
          if (containsArabicScript(inputText)) LayoutDirection.Rtl else LayoutDirection.Ltr
        } else {
          if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
        }

        CompositionLocalProvider(LocalLayoutDirection provides textLayoutDir) {
          OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = {
              Text(
                text = flashcardString(R.string.flashcard_text_input_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
              )
            },
            textStyle = MaterialTheme.typography.titleMedium.copy(
              fontFamily = FontFamily.Serif,
              fontSize = 19.sp,
              lineHeight = 32.sp
            ),
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("text_flashcard_body_input")
          )
        }

        // Live Front Cue Preview when text is entered
        AnimatedVisibility(visible = words.isNotEmpty()) {
          val wordCountStr = MemoryFlashcardManager.formatNumber(words.size, isArabic)
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            modifier = Modifier
              .fillMaxWidth()
              .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp)
              )
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = flashcardString(R.string.flashcard_study_front_badge),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = flashcardString(R.string.flashcard_text_word_count_badge, wordCountStr),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              CompositionLocalProvider(LocalLayoutDirection provides textLayoutDir) {
                Text(
                  text = liveFrontPrompt,
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    lineHeight = 28.sp
                  ),
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }

        // Generator Settings Switches (matching reference design)
        SettingSwitchRow(
          icon = QuranIcons.Notes,
          label = flashcardString(R.string.flashcard_text_auto_hint_4_words),
          checked = autoFourWordHint,
          onCheckedChange = { autoFourWordHint = it },
          testTag = "switch_auto_4_word_hint"
        )

        SettingSwitchRow(
          icon = QuranIcons.Speed,
          label = flashcardString(R.string.flashcard_text_fast_reveal_pace),
          checked = fastRevealMode,
          onCheckedChange = { fastRevealMode = it },
          testTag = "switch_fast_reveal_pace"
        )

        // Primary Create Button
        Button(
          onClick = {
            if (words.isEmpty()) {
              Toast.makeText(context, emptyWarningMsg, Toast.LENGTH_SHORT).show()
              return@Button
            }
            val effectiveTitle = sourceTitle.trim().ifEmpty { defaultSourceLabel }
            val created = MemoryFlashcardManager.createTextFlashcard(
              context = context,
              fullText = inputText,
              customTitle = effectiveTitle,
              autoFourWordHint = autoFourWordHint,
              fastRevealMode = fastRevealMode
            )
            if (created != null) {
              inputText = ""
              sourceTitle = ""
              onCardCreated(created)
            }
          },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("create_text_flashcard_button")
        ) {
          Icon(
            imageVector = QuranIcons.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = flashcardString(R.string.flashcard_text_create_button),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // Mini Help Tip Card for Tashkeel support
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Icon(
          imageVector = QuranIcons.Star,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier
            .padding(top = 2.dp)
            .size(18.dp)
        )
        Text(
          text = flashcardString(R.string.flashcard_text_tashkeel_tip),
          style = MaterialTheme.typography.labelSmall.copy(lineHeight = 18.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun SettingSwitchRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .clickable { onCheckedChange(!checked) }
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(18.dp)
        )
        Text(
          text = label,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.testTag(testTag)
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TextFlashcardStudyPage(
  card: MemoryFlashcard,
  cardIndexInSession: Int,
  totalCardsInSession: Int,
  isArabic: Boolean,
  onBack: () -> Unit,
  onRateCard: (FlashcardRating) -> Unit
) {
  val context = LocalContext.current
  val words = remember(card.id, card.fullText) { card.parseWords() }
  val initialRevealed = remember(card.id) { 1.coerceAtMost(words.size) }
  var isFlipped by remember(card.id) { mutableStateOf(false) }
  var revealedCount by remember(card.id) { mutableIntStateOf(initialRevealed) }

  var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
  var ttsReady by remember { mutableStateOf(false) }

  DisposableEffect(context) {
    var instance: TextToSpeech? = null
    try {
      instance = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
          ttsReady = true
        }
      }
      ttsEngine = instance
    } catch (_: Exception) {
    }
    onDispose {
      try {
        instance?.stop()
        instance?.shutdown()
      } catch (_: Exception) {
      }
      ttsEngine = null
    }
  }

  val speakCardText = {
    val engine = ttsEngine
    if (engine != null && ttsReady) {
      val locale = if (containsArabicScript(card.fullText)) Locale("ar") else Locale.ENGLISH
      engine.language = locale
      engine.speak(card.fullText, TextToSpeech.QUEUE_FLUSH, null, "flashcard_tts_${card.id}")
    }
  }

  val flipRotation by animateFloatAsState(
    targetValue = if (isFlipped) 180f else 0f,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "card_flip_rotation"
  )

  val stepIncrement = if (card.fastRevealMode) 2 else 1
  val textDirection = if (containsArabicScript(card.fullText)) LayoutDirection.Rtl else LayoutDirection.Ltr
  val effectiveSource = card.customTitle.ifBlank {
    flashcardString(R.string.flashcard_text_source_default)
  }

  val currentIndexFormatted = MemoryFlashcardManager.formatNumber(cardIndexInSession, isArabic)
  val totalCardsFormatted = MemoryFlashcardManager.formatNumber(totalCardsInSession.coerceAtLeast(1), isArabic)

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = effectiveSource,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = flashcardString(R.string.flashcard_study_header_subtitle),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          TextButton(
            onClick = onBack,
            modifier = Modifier.testTag("study_text_back_button")
          ) {
            Text(
              text = if (isArabic) "→" else "←",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
          }
        },
        actions = {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
          ) {
            Text(
              text = flashcardString(
                R.string.flashcard_study_card_counter,
                currentIndexFormatted,
                totalCardsFormatted
              ),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
        .testTag("text_flashcard_study_screen"),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // 3D Perspective Flip Card
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 295.dp)
            .border(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
              shape = RoundedCornerShape(20.dp)
            )
            .graphicsLayer {
              rotationY = if (flipRotation <= 90f) flipRotation else flipRotation - 180f
              cameraDistance = 14f * density
            }
            .clickable {
              isFlipped = !isFlipped
            }
            .testTag("study_flip_card_surface")
        ) {
          if (flipRotation <= 90f) {
            // CARD FRONT
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 295.dp)
                .padding(18.dp),
              verticalArrangement = Arrangement.SpaceBetween
            ) {
              // Front Top Header
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Icon(
                      imageVector = QuranIcons.Star,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.size(14.dp)
                    )
                    Text(
                      text = flashcardString(R.string.flashcard_study_front_badge),
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                  }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  IconButton(
                    onClick = speakCardText,
                    modifier = Modifier
                      .size(48.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                      .testTag("study_tts_button")
                  ) {
                    Icon(
                      imageVector = QuranIcons.PlayArrow,
                      contentDescription = flashcardString(R.string.flashcard_study_listen_tts),
                      tint = MaterialTheme.colorScheme.tertiary
                    )
                  }

                  IconButton(
                    onClick = { isFlipped = true },
                    modifier = Modifier
                      .size(48.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primaryContainer)
                      .testTag("study_flip_to_back_icon_button")
                  ) {
                    Icon(
                      imageVector = QuranIcons.Sync,
                      contentDescription = flashcardString(R.string.flashcard_study_flip_card),
                      tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                  }
                }
              }

              // Main Front Prompt Text
              CompositionLocalProvider(LocalLayoutDirection provides textDirection) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Text(
                    text = card.firstAyahCue,
                    style = MaterialTheme.typography.headlineSmall.copy(
                      fontFamily = FontFamily.Serif,
                      fontSize = 25.sp,
                      lineHeight = 42.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                  )
                  Text(
                    text = effectiveSource,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                  )
                }
              }

              // Front Bottom Cue
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = flashcardString(R.string.flashcard_study_tap_or_flip_hint),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.secondary
                )
                TextButton(
                  onClick = { isFlipped = true },
                  modifier = Modifier.testTag("study_quick_show_text_button")
                ) {
                  Text(
                    text = flashcardString(R.string.flashcard_study_show_text_action),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                  )
                }
              }
            }
          } else {
            // CARD BACK (Word-by-Word Progressive Reveal)
            val safeRevealed = revealedCount.coerceIn(0, words.size)
            val revealedFormatted = MemoryFlashcardManager.formatNumber(safeRevealed, isArabic)
            val totalWordsFormatted = MemoryFlashcardManager.formatNumber(words.size, isArabic)
            val progressFraction = if (words.isEmpty()) 0f else safeRevealed.toFloat() / words.size.toFloat()

            Column(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 295.dp)
                .padding(18.dp),
              verticalArrangement = Arrangement.SpaceBetween
            ) {
              // Back Top Header
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.primaryContainer
                ) {
                  Text(
                    text = flashcardString(R.string.flashcard_study_back_badge),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                  )
                }

                IconButton(
                  onClick = { isFlipped = false },
                  modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .testTag("study_flip_to_front_icon_button")
                ) {
                  Icon(
                    imageVector = QuranIcons.Sync,
                    contentDescription = flashcardString(R.string.flashcard_study_flip_card),
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
              }

              // Word-by-Word Interactive FlowRow
              CompositionLocalProvider(LocalLayoutDirection provides textDirection) {
                FlowRow(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                  verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  words.forEachIndexed { index, word ->
                    val isWordRevealed = index < safeRevealed
                    if (isWordRevealed) {
                      Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                      ) {
                        Text(
                          text = word,
                          style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontSize = 21.sp,
                            lineHeight = 34.sp
                          ),
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                      }
                    } else {
                      Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier
                          .clip(RoundedCornerShape(8.dp))
                          .clickable {
                            revealedCount = (index + 1).coerceAtMost(words.size)
                          }
                          .testTag("masked_word_$index")
                      ) {
                        Text(
                          text = "••••",
                          style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            letterSpacing = 2.sp
                          ),
                          color = MaterialTheme.colorScheme.outline,
                          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                      }
                    }
                  }
                }
              }

              // Progress Ribbon
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = flashcardString(R.string.flashcard_study_revealed_words_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = flashcardString(
                      R.string.flashcard_study_revealed_words_count,
                      revealedFormatted,
                      totalWordsFormatted
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                  )
                }
                LinearProgressIndicator(
                  progress = { progressFraction },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                  color = MaterialTheme.colorScheme.primary,
                  trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
              }
            }
          }
        }

        // Active Recitation Controls
        Button(
          onClick = {
            if (!isFlipped) {
              isFlipped = true
            } else if (revealedCount < words.size) {
              revealedCount = (revealedCount + stepIncrement).coerceAtMost(words.size)
            }
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("study_reveal_next_word_button")
        ) {
          Icon(
            imageVector = QuranIcons.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (card.fastRevealMode) {
              flashcardString(R.string.flashcard_study_reveal_next_two)
            } else {
              flashcardString(R.string.flashcard_study_reveal_next_one)
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
        }

        // Secondary 3-button Utility Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilledTonalButton(
            onClick = {
              isFlipped = true
              revealedCount = words.size
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("study_reveal_all_button")
          ) {
            Text(
              text = flashcardString(R.string.flashcard_study_reveal_all),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          FilledTonalButton(
            onClick = {
              isFlipped = true
              revealedCount = 0
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("study_reset_words_button")
          ) {
            Text(
              text = flashcardString(R.string.flashcard_study_reset_words),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          FilledTonalButton(
            onClick = { isFlipped = !isFlipped },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("study_toggle_flip_button")
          ) {
            Text(
              text = flashcardString(R.string.flashcard_study_flip_card),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        // Self-Evaluation Spaced Repetition Rating Card
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth()
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
              Text(
                text = flashcardString(R.string.flashcard_study_eval_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = flashcardString(R.string.flashcard_study_eval_algo),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
              )
            }

            val repeatLabel = MemoryFlashcardManager.getRepeatScheduleLabel(isArabic)
            val hardLabel = card.hardScheduleLabel(isArabic)
            val goodLabel = card.nextGoodScheduleLabel(isArabic)

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Forgot / Repeat
              Button(
                onClick = { onRateCard(FlashcardRating.REPEAT) },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.errorContainer,
                  contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("rate_text_again_button")
              ) {
                Text(
                  text = flashcardString(R.string.flashcard_study_rate_again, repeatLabel),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  textAlign = TextAlign.Center
                )
              }

              // Hard
              Button(
                onClick = { onRateCard(FlashcardRating.HARD) },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("rate_text_hard_button")
              ) {
                Text(
                  text = flashcardString(R.string.flashcard_study_rate_hard, hardLabel),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  textAlign = TextAlign.Center
                )
              }

              // Good / Easy
              Button(
                onClick = { onRateCard(FlashcardRating.GOOD) },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("rate_text_good_button")
              ) {
                Text(
                  text = flashcardString(R.string.flashcard_study_rate_good, goodLabel),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  textAlign = TextAlign.Center
                )
              }
            }
          }
        }
      }
    }
  }
}
