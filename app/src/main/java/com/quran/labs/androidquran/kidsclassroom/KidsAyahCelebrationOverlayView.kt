package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.flashcards.MemoryFlashcardManager
import com.quran.labs.androidquran.memorization.MemorizationFormatting
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Full-screen joyful celebration overlay displayed when a learner finishes a complete Ayah
 * in **Kids Mode Memorization**, rendering 1 of 10 distinct random animated themes with
 * animated stars, animated score ring, repetition progress, and tap-to-continue controls.
 */
class KidsAyahCelebrationOverlayView(
  context: Context,
  private val event: AyahCelebrationEvent,
  private val autoContinueEnabled: Boolean,
  private val onContinue: () -> Unit,
  private val onDismiss: () -> Unit,
  private val onOpenSurahAnalysis: (() -> Unit)? = null,
  private val onOpenAchievements: (() -> Unit)? = null
) : FrameLayout(context) {

  init {
    isClickable = true
    isFocusable = true
    val composeView = ComposeView(context).apply {
      setContent {
        QuranTheme {
          KidsAyahCelebrationFullScreen(
            event = event,
            autoContinueEnabled = autoContinueEnabled,
            onContinue = onContinue,
            onDismiss = onDismiss,
            onOpenSurahAnalysis = onOpenSurahAnalysis,
            onOpenAchievements = onOpenAchievements
          )
        }
      }
    }
    addView(composeView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }
}

private data class CelebrationPalette(
  val topBg: Color,
  val midBg: Color,
  val bottomBg: Color,
  val primaryAccent: Color,
  val secondaryAccent: Color,
  val cardSurface: Color
)

private fun paletteFor(theme: KidsCelebrationTheme): CelebrationPalette = when (theme) {
  KidsCelebrationTheme.GOLDEN_LANTERNS -> CelebrationPalette(
    topBg = Color(0xFF062C2B),
    midBg = Color(0xFF0B4F4A),
    bottomBg = Color(0xFF12263A),
    primaryAccent = Color(0xFFFFD166),
    secondaryAccent = Color(0xFF34D399),
    cardSurface = Color(0xFF0B3534).copy(alpha = 0.84f)
  )
  KidsCelebrationTheme.FIREWORKS_FESTIVAL -> CelebrationPalette(
    topBg = Color(0xFF0B1026),
    midBg = Color(0xFF1E1B4B),
    bottomBg = Color(0xFF311042),
    primaryAccent = Color(0xFFFBBF24),
    secondaryAccent = Color(0xFFF43F5E),
    cardSurface = Color(0xFF18163D).copy(alpha = 0.86f)
  )
  KidsCelebrationTheme.CONFETTI_CARNIVAL -> CelebrationPalette(
    topBg = Color(0xFF065F46),
    midBg = Color(0xFF0D9488),
    bottomBg = Color(0xFF0369A1),
    primaryAccent = Color(0xFFFDE047),
    secondaryAccent = Color(0xFFFB7185),
    cardSurface = Color(0xFF064E4A).copy(alpha = 0.82f)
  )
  KidsCelebrationTheme.ROCKET_STARSHIP -> CelebrationPalette(
    topBg = Color(0xFF03071E),
    midBg = Color(0xFF0F2942),
    bottomBg = Color(0xFF1B3B6F),
    primaryAccent = Color(0xFF38BDF8),
    secondaryAccent = Color(0xFFF59E0B),
    cardSurface = Color(0xFF0B1E38).copy(alpha = 0.86f)
  )
  KidsCelebrationTheme.RAINBOW_SUNSHINE -> CelebrationPalette(
    topBg = Color(0xFF0284C7),
    midBg = Color(0xFF0EA5E9),
    bottomBg = Color(0xFF059669),
    primaryAccent = Color(0xFFFEF08A),
    secondaryAccent = Color(0xFFF97316),
    cardSurface = Color(0xFF075985).copy(alpha = 0.82f)
  )
  KidsCelebrationTheme.FLOATING_BALLOONS -> CelebrationPalette(
    topBg = Color(0xFF9A3412),
    midBg = Color(0xFFEA580C),
    bottomBg = Color(0xFFD97706),
    primaryAccent = Color(0xFFFEF08A),
    secondaryAccent = Color(0xFF38BDF8),
    cardSurface = Color(0xFF7C2D12).copy(alpha = 0.82f)
  )
  KidsCelebrationTheme.GARDEN_BUTTERFLIES -> CelebrationPalette(
    topBg = Color(0xFF064E3B),
    midBg = Color(0xFF047857),
    bottomBg = Color(0xFF15803D),
    primaryAccent = Color(0xFFFDE047),
    secondaryAccent = Color(0xFFF472B6),
    cardSurface = Color(0xFF064E3B).copy(alpha = 0.84f)
  )
  KidsCelebrationTheme.ROYAL_CROWN_TROPHY -> CelebrationPalette(
    topBg = Color(0xFF2E1065),
    midBg = Color(0xFF4C1D95),
    bottomBg = Color(0xFF1E1B4B),
    primaryAccent = Color(0xFFFACC15),
    secondaryAccent = Color(0xFFE879F9),
    cardSurface = Color(0xFF3B0764).copy(alpha = 0.86f)
  )
  KidsCelebrationTheme.OCEAN_PEARLS -> CelebrationPalette(
    topBg = Color(0xFF082F49),
    midBg = Color(0xFF0E7490),
    bottomBg = Color(0xFF155E75),
    primaryAccent = Color(0xFF67E8F9),
    secondaryAccent = Color(0xFFFDE68A),
    cardSurface = Color(0xFF0C4A6E).copy(alpha = 0.84f)
  )
  KidsCelebrationTheme.DESERT_MOON_OASIS -> CelebrationPalette(
    topBg = Color(0xFF1E1B4B),
    midBg = Color(0xFF7C2D12),
    bottomBg = Color(0xFF0F766E),
    primaryAccent = Color(0xFFFCD34D),
    secondaryAccent = Color(0xFF5EEAD4),
    cardSurface = Color(0xFF1E293B).copy(alpha = 0.84f)
  )
}

@Composable
fun KidsAyahCelebrationFullScreen(
  event: AyahCelebrationEvent,
  autoContinueEnabled: Boolean,
  onContinue: () -> Unit,
  onDismiss: () -> Unit,
  onOpenSurahAnalysis: (() -> Unit)? = null,
  onOpenAchievements: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val isArabic = remember(context) { MemoryFlashcardManager.isAppInArabicMode(context) }
  val res = remember(context, isArabic) { MemorizationFormatting.localizedResources(context, isArabic) }
  val palette = remember(event.theme) { paletteFor(event.theme) }

  val cardScale = remember(event.eventId) { Animatable(0.72f) }
  val scoreAnim = remember(event.eventId) { Animatable(0f) }
  val star1Scale = remember(event.eventId) { Animatable(0f) }
  val star2Scale = remember(event.eventId) { Animatable(0f) }
  val star3Scale = remember(event.eventId) { Animatable(0f) }
  val autoProgress = remember(event.eventId) { Animatable(1f) }

  val infinite = rememberInfiniteTransition(label = "celebrationLoop")
  val phase by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3600, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "phase"
  )
  val sway by infinite.animateFloat(
    initialValue = -1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "sway"
  )

  LaunchedEffect(event.eventId) {
    playCelebrationFanfare(event.starsEarned)
    launch {
      cardScale.animateTo(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
      )
    }
    launch {
      scoreAnim.animateTo(
        targetValue = event.scorePercent.coerceIn(0, 100).toFloat(),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
      )
    }
    launch {
      delay(120L)
      star1Scale.animateTo(
        1f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
      )
    }
    launch {
      delay(280L)
      star2Scale.animateTo(
        1f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
      )
    }
    launch {
      delay(440L)
      star3Scale.animateTo(
        1f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
      )
    }
    if (autoContinueEnabled) {
      autoProgress.animateTo(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 4200, easing = LinearEasing)
      )
      onContinue()
    }
  }

  val totalAyatStr = MemoryFlashcardManager.formatNumber(event.totalAyatInSurah.coerceAtLeast(1), isArabic)
  val scoreDisplay = MemoryFlashcardManager.formatNumber(scoreAnim.value.roundToInt(), isArabic)
  val totalStarsDisplay = MemoryFlashcardManager.formatNumber(event.totalStars, isArabic)
  val willRepeatSameSurah = event.surahRepetitionTarget > 1 &&
    event.surahRepetitionCompleted < event.surahRepetitionTarget

  CompositionLocalProvider(
    LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
  ) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(palette.topBg, palette.midBg, palette.bottomBg)
          )
        )
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = {} // Swallow clicks so underlying Mushaf does not accidentally receive taps
        )
        .testTag("kids_ayah_celebration_overlay")
    ) {
      // Full-screen bespoke Canvas celebration animation (10 distinct visual worlds!)
      Canvas(modifier = Modifier.fillMaxSize()) {
        drawThemeCelebrationArt(
          theme = event.theme,
          palette = palette,
          phase = phase,
          sway = sway
        )
      }

      // Main centered celebration card
      Column(
        modifier = Modifier
          .fillMaxSize()
          .safeDrawingPadding()
          .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Surface(
          shape = RoundedCornerShape(32.dp),
          color = palette.cardSurface,
          border = BorderStroke(2.dp, palette.primaryAccent.copy(alpha = 0.72f)),
          shadowElevation = 14.dp,
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .scale(cardScale.value)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            // Surah Complete Special Ribbon
            if (event.isSurahCompleted) {
              Surface(
                shape = RoundedCornerShape(999.dp),
                color = palette.primaryAccent
              ) {
                Text(
                  text = "🏆 " + res.getString(R.string.kids_celebration_surah_complete),
                  color = Color(0xFF1E1B4B),
                  fontSize = 13.sp,
                  fontWeight = FontWeight.ExtraBold,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
              }
            }

            // Theme Emblem Circle
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(palette.primaryAccent.copy(alpha = 0.22f))
            ) {
              Text(
                text = event.theme.badgeEmoji,
                fontSize = 38.sp
              )
            }

            // Cheerful Bilingual Title & Subtitle
            Text(
              text = if (isArabic) event.theme.titleAr else event.theme.titleEn,
              color = Color.White,
              fontSize = 24.sp,
              fontWeight = FontWeight.ExtraBold,
              textAlign = TextAlign.Center,
              modifier = Modifier.testTag("celebration_title")
            )
            Text(
              text = if (isArabic) event.theme.subtitleAr else event.theme.subtitleEn,
              color = Color.White.copy(alpha = 0.85f),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              textAlign = TextAlign.Center
            )

            // Completed Full Surah Pill
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = Color.White.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
            ) {
              Text(
                text = res.getString(
                  R.string.kids_celebration_surah_badge_format,
                  event.surahNameAr,
                  totalAyatStr
                ),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
              )
            }

            // Animated 3 Giant Stars Row
            Row(
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .padding(vertical = 4.dp)
                .testTag("celebration_stars_row")
            ) {
              val scales = listOf(star1Scale.value, star2Scale.value, star3Scale.value)
              for (idx in 0 until 3) {
                val earned = (idx + 1) <= event.starsEarned
                val isCenter = idx == 1
                CelebrationStarGlyph(
                  earned = earned,
                  sizeDp = if (isCenter) 64 else 52,
                  scale = scales[idx],
                  accentColor = palette.primaryAccent
                )
              }
            }

            // Animated Circular Score Ring + Total Stars Badge
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(18.dp),
              modifier = Modifier.padding(top = 2.dp)
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(88.dp)
                  .testTag("celebration_score_badge")
              ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                  val strokeW = 9.dp.toPx()
                  val inset = strokeW / 2f
                  val arcSize = Size(size.width - strokeW, size.height - strokeW)
                  drawArc(
                    color = Color.White.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                  )
                  drawArc(
                    color = palette.primaryAccent,
                    startAngle = -90f,
                    sweepAngle = 360f * (scoreAnim.value / 100f).coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                  )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(
                    text = "$scoreDisplay%",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold
                  )
                  Text(
                    text = res.getString(R.string.kids_celebration_score_label),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }

              Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.Start
              ) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0xFFFFF8E1).copy(alpha = 0.18f),
                  border = BorderStroke(1.dp, palette.primaryAccent.copy(alpha = 0.55f))
                ) {
                  Text(
                    text = "⭐ " + res.getString(R.string.kids_celebration_total_stars, totalStarsDisplay),
                    color = palette.primaryAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                  )
                }

                if (event.ayahRepetitionTarget > 1) {
                  val doneStr = MemoryFlashcardManager.formatNumber(event.ayahRepetitionCompleted, isArabic)
                  val targetStr = MemoryFlashcardManager.formatNumber(event.ayahRepetitionTarget, isArabic)
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.secondaryAccent.copy(alpha = 0.20f)
                  ) {
                    Text(
                      text = "🔁 " + res.getString(R.string.kids_repeat_progress_ayah, doneStr, targetStr),
                      color = Color.White,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                  }
                }

                if (event.surahRepetitionTarget > 1) {
                  val sDone = MemoryFlashcardManager.formatNumber(event.surahRepetitionCompleted, isArabic)
                  val sTarget = MemoryFlashcardManager.formatNumber(event.surahRepetitionTarget, isArabic)
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.14f)
                  ) {
                    Text(
                      text = "📖 " + res.getString(R.string.kids_repeat_progress_surah, sDone, sTarget),
                      color = Color.White,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                  }
                }
              }
            }

            // High-Accuracy Full-Surah Badge Banner (Silver 80%+ / Gold 90%+ / Diamond 97%+)
            val awardedBadge = event.awardedSurahBadge
            if (awardedBadge != null && awardedBadge.tier != SurahBadgeTier.NONE) {
              val badgeStyle = remember(awardedBadge.tier) { visualStyleForTier(awardedBadge.tier) }
              val badgeAccStr = MemoryFlashcardManager.formatNumber(awardedBadge.bestAccuracyPercent, isArabic)
              val tierTitle = if (isArabic) awardedBadge.tier.titleAr else awardedBadge.tier.titleEn
              val ribbonText = when {
                event.isNewSurahBadge -> res.getString(R.string.kids_celebration_badge_new_ribbon)
                event.isUpgradedSurahBadge -> res.getString(R.string.kids_celebration_badge_upgraded_ribbon)
                else -> res.getString(R.string.kids_celebration_badge_earned_ribbon)
              }
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.13f),
                border = BorderStroke(1.5.dp, badgeStyle.rimHighlight.copy(alpha = 0.72f)),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("celebration_surah_badge_banner")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  SurahBadgeMedallion(
                    tier = awardedBadge.tier,
                    centerLabel = "$badgeAccStr%",
                    size = 54.dp,
                    animateGlow = true
                  )
                  Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                  ) {
                    Text(
                      text = "${awardedBadge.tier.emoji} $ribbonText",
                      color = badgeStyle.rimHighlight,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                      text = "$tierTitle · ${awardedBadge.surahNameAr}",
                      color = Color.White,
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Primary Continue / Repeat Surah Button (52dp+ kid touch target)
            Button(
              onClick = onContinue,
              shape = RoundedCornerShape(18.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = palette.primaryAccent,
                contentColor = Color(0xFF0F172A)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .testTag("celebration_continue_btn")
            ) {
              Text(
                text = if (willRepeatSameSurah) {
                  res.getString(R.string.kids_celebration_repeat_surah_btn)
                } else {
                  res.getString(R.string.kids_celebration_next_surah_btn)
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
              )
            }

            if (onOpenAchievements != null && awardedBadge != null && awardedBadge.tier != SurahBadgeTier.NONE) {
              androidx.compose.material3.OutlinedButton(
                onClick = onOpenAchievements,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, palette.primaryAccent.copy(alpha = 0.72f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primaryAccent),
                modifier = Modifier
                  .fillMaxWidth()
                  .defaultMinSize(minHeight = 48.dp)
                  .testTag("celebration_view_achievements_btn")
              ) {
                Text(
                  text = "🏅 " + res.getString(R.string.kids_celebration_view_achievements_btn),
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            if (onOpenSurahAnalysis != null && event.missedWordsInSurahCount > 0) {
              val missedStr = MemoryFlashcardManager.formatNumber(event.missedWordsInSurahCount, isArabic)
              androidx.compose.material3.OutlinedButton(
                onClick = onOpenSurahAnalysis,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                  .fillMaxWidth()
                  .defaultMinSize(minHeight = 48.dp)
                  .testTag("celebration_review_mistakes_btn")
              ) {
                Text(
                  text = "📝 " + res.getString(R.string.kids_celebration_review_mistakes_btn, missedStr),
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            if (autoContinueEnabled) {
              LinearProgressIndicator(
                progress = { autoProgress.value.coerceIn(0f, 1f) },
                color = palette.primaryAccent,
                trackColor = Color.White.copy(alpha = 0.18f),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(5.dp)
                  .clip(RoundedCornerShape(999.dp))
              )
            }
          }
        }
      }

      // Subtle top-end close button (48dp touch target) to stay on the current Mushaf page without advancing
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .safeDrawingPadding()
          .padding(12.dp)
          .size(48.dp)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.28f))
          .clickable(onClick = onDismiss)
          .testTag("celebration_close_btn")
      ) {
        Text(
          text = "✕",
          color = Color.White,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
private fun CelebrationStarGlyph(
  earned: Boolean,
  sizeDp: Int,
  scale: Float,
  accentColor: Color
) {
  Canvas(
    modifier = Modifier
      .size(sizeDp.dp)
      .scale(scale.coerceAtLeast(0.01f))
  ) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outerR = min(size.width, size.height) * 0.46f
    val innerR = outerR * 0.45f
    val path = Path()
    for (i in 0 until 10) {
      val angle = (-90.0 + i * 36.0) * PI / 180.0
      val r = if (i % 2 == 0) outerR else innerR
      val px = cx + (r * cos(angle)).toFloat()
      val py = cy + (r * sin(angle)).toFloat()
      if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()

    if (earned) {
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(accentColor.copy(alpha = 0.45f), Color.Transparent),
          center = Offset(cx, cy),
          radius = outerR * 1.3f
        ),
        radius = outerR * 1.3f,
        center = Offset(cx, cy)
      )
      drawPath(path = path, color = Color(0xFFFFD54F))
      drawPath(path = path, color = Color(0xFFFFF59D), style = Stroke(width = 3f))
    } else {
      drawPath(path = path, color = Color.White.copy(alpha = 0.16f))
      drawPath(path = path, color = Color.White.copy(alpha = 0.35f), style = Stroke(width = 2.5f))
    }
  }
}

private fun DrawScope.drawThemeCelebrationArt(
  theme: KidsCelebrationTheme,
  palette: CelebrationPalette,
  phase: Float,
  sway: Float
) {
  val w = size.width
  val h = size.height

  when (theme) {
    KidsCelebrationTheme.GOLDEN_LANTERNS -> {
      // Swaying hanging lanterns + floating golden sparkles
      val xs = listOf(0.16f, 0.36f, 0.64f, 0.84f)
      val lengths = listOf(0.16f, 0.11f, 0.13f, 0.18f)
      xs.forEachIndexed { idx, xFrac ->
        val anchorX = w * xFrac
        val ropeH = h * lengths[idx]
        val dx = sway * (12f + idx * 3f) * if (idx % 2 == 0) 1f else -1f
        val lanternCenter = Offset(anchorX + dx, ropeH + 34f)
        drawLine(
          color = palette.primaryAccent.copy(alpha = 0.65f),
          start = Offset(anchorX, 0f),
          end = Offset(lanternCenter.x, lanternCenter.y - 24f),
          strokeWidth = 3f
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(palette.primaryAccent.copy(alpha = 0.55f), Color.Transparent),
            center = lanternCenter,
            radius = 78f
          ),
          radius = 78f,
          center = lanternCenter
        )
        drawRoundRect(
          color = palette.primaryAccent,
          topLeft = Offset(lanternCenter.x - 18f, lanternCenter.y - 24f),
          size = Size(36f, 48f),
          cornerRadius = CornerRadius(12f, 12f)
        )
        drawRoundRect(
          color = Color(0xFFFFFBEB),
          topLeft = Offset(lanternCenter.x - 9f, lanternCenter.y - 14f),
          size = Size(18f, 28f),
          cornerRadius = CornerRadius(6f, 6f)
        )
      }
      drawSparkleField(count = 26, phase = phase, color = palette.primaryAccent)
    }

    KidsCelebrationTheme.FIREWORKS_FESTIVAL -> {
      val burstColors = listOf(Color(0xFFFDE047), Color(0xFFF43F5E), Color(0xFF38BDF8), Color(0xFF34D399), Color(0xFFE879F9))
      val centers = listOf(
        Offset(w * 0.22f, h * 0.18f),
        Offset(w * 0.78f, h * 0.22f),
        Offset(w * 0.50f, h * 0.13f),
        Offset(w * 0.18f, h * 0.80f),
        Offset(w * 0.82f, h * 0.78f)
      )
      centers.forEachIndexed { idx, center ->
        val localP = (phase + idx * 0.21f) % 1f
        val maxR = min(w, h) * 0.18f
        val r = maxR * localP
        val alpha = (1f - localP).coerceIn(0f, 1f)
        val c = burstColors[idx % burstColors.size]
        for (ray in 0 until 14) {
          val a = (ray * (360f / 14f)) * PI / 180.0
          val start = Offset(center.x + (r * 0.45f * cos(a)).toFloat(), center.y + (r * 0.45f * sin(a)).toFloat())
          val end = Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
          drawLine(color = c.copy(alpha = alpha), start = start, end = end, strokeWidth = 5f, cap = StrokeCap.Round)
          drawCircle(color = Color.White.copy(alpha = alpha), radius = 3.5f, center = end)
        }
      }
    }

    KidsCelebrationTheme.CONFETTI_CARNIVAL -> {
      val colors = listOf(Color(0xFFFDE047), Color(0xFFFB7185), Color(0xFF38BDF8), Color(0xFF4ADE80), Color(0xFFC084FC), Color.White)
      for (i in 0 until 42) {
        val x = ((i * 73) % 100) / 100f * w + sin((phase * 2 * PI + i).toDouble()).toFloat() * 18f
        val y = (((phase + (i * 0.07f)) % 1f) * (h + 80f)) - 40f
        val c = colors[i % colors.size]
        rotate(degrees = (phase * 360f + i * 29f) % 360f, pivot = Offset(x, y)) {
          drawRoundRect(
            color = c.copy(alpha = 0.86f),
            topLeft = Offset(x - 7f, y - 4f),
            size = Size(14f, 8f),
            cornerRadius = CornerRadius(2f, 2f)
          )
        }
      }
    }

    KidsCelebrationTheme.ROCKET_STARSHIP -> {
      drawSparkleField(count = 36, phase = phase, color = Color(0xFFE0F2FE))
      // Soaring stylized rocket along bottom-left to top-right diagonal
      val rocketProgress = phase
      val rx = w * (0.14f + 0.72f * rocketProgress)
      val ry = h * (0.88f - 0.76f * rocketProgress)
      // Exhaust flame trail
      for (t in 1..7) {
        val tx = rx - t * 14f
        val ty = ry + t * 14f
        drawCircle(
          color = Color(0xFFF59E0B).copy(alpha = (1f - t / 8f) * 0.75f),
          radius = (15f - t * 1.4f).coerceAtLeast(3f),
          center = Offset(tx, ty)
        )
      }
      rotate(degrees = 45f, pivot = Offset(rx, ry)) {
        drawRoundRect(
          color = Color.White,
          topLeft = Offset(rx - 14f, ry - 26f),
          size = Size(28f, 48f),
          cornerRadius = CornerRadius(14f, 14f)
        )
        drawCircle(color = Color(0xFF38BDF8), radius = 7f, center = Offset(rx, ry - 6f))
      }
    }

    KidsCelebrationTheme.RAINBOW_SUNSHINE -> {
      val rainbowColors = listOf(
        Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFFACC15),
        Color(0xFF22C55E), Color(0xFF38BDF8), Color(0xFF818CF8)
      )
      val arcCenter = Offset(w / 2f, h * 0.42f)
      val baseR = min(w, h) * 0.56f
      rainbowColors.forEachIndexed { idx, col ->
        val r = baseR - idx * 14f
        drawArc(
          color = col.copy(alpha = 0.48f),
          startAngle = 180f,
          sweepAngle = 180f,
          useCenter = false,
          topLeft = Offset(arcCenter.x - r, arcCenter.y - r),
          size = Size(r * 2f, r * 2f),
          style = Stroke(width = 13f, cap = StrokeCap.Round)
        )
      }
      // Rotating sun rays in top-left
      val sunCenter = Offset(w * 0.16f, h * 0.14f)
      drawCircle(color = Color(0xFFFEF08A), radius = 36f, center = sunCenter)
      for (i in 0 until 10) {
        val a = (i * 36f + phase * 60f) * PI / 180.0
        drawLine(
          color = Color(0xFFFEF08A).copy(alpha = 0.72f),
          start = Offset(sunCenter.x + (44f * cos(a)).toFloat(), sunCenter.y + (44f * sin(a)).toFloat()),
          end = Offset(sunCenter.x + (64f * cos(a)).toFloat(), sunCenter.y + (64f * sin(a)).toFloat()),
          strokeWidth = 5f,
          cap = StrokeCap.Round
        )
      }
    }

    KidsCelebrationTheme.FLOATING_BALLOONS -> {
      val bColors = listOf(Color(0xFFFDE047), Color(0xFF38BDF8), Color(0xFFF472B6), Color(0xFF4ADE80), Color(0xFFFCA5A5), Color.White)
      for (i in 0 until 12) {
        val bx = w * (0.08f + ((i * 19) % 86) / 100f) + sway * 10f * if (i % 2 == 0) 1f else -1f
        val by = h + 60f - (((phase + i * 0.11f) % 1f) * (h + 180f))
        val col = bColors[i % bColors.size]
        drawLine(
          color = Color.White.copy(alpha = 0.5f),
          start = Offset(bx, by + 28f),
          end = Offset(bx + sway * 6f, by + 76f),
          strokeWidth = 2.5f
        )
        drawOval(
          color = col.copy(alpha = 0.82f),
          topLeft = Offset(bx - 22f, by - 28f),
          size = Size(44f, 56f)
        )
        drawCircle(color = Color.White.copy(alpha = 0.45f), radius = 5f, center = Offset(bx - 7f, by - 12f))
      }
    }

    KidsCelebrationTheme.GARDEN_BUTTERFLIES -> {
      val petalColors = listOf(Color(0xFFF472B6), Color(0xFFFDE047), Color(0xFFA7F3D0), Color(0xFFFDBA74))
      for (i in 0 until 16) {
        val fx = w * ((i * 23 + 9) % 92) / 100f
        val fy = h * ((i * 31 + 7) % 90) / 100f
        val col = petalColors[i % petalColors.size]
        val flutter = 0.78f + 0.22f * sin((phase * 2 * PI * 2 + i).toDouble()).toFloat()
        // Butterfly / blossom twin wings
        drawOval(
          color = col.copy(alpha = 0.55f),
          topLeft = Offset(fx - 18f * flutter, fy - 12f),
          size = Size(18f * flutter, 24f)
        )
        drawOval(
          color = col.copy(alpha = 0.55f),
          topLeft = Offset(fx, fy - 12f),
          size = Size(18f * flutter, 24f)
        )
        drawCircle(color = Color.White.copy(alpha = 0.85f), radius = 3.5f, center = Offset(fx, fy))
      }
    }

    KidsCelebrationTheme.ROYAL_CROWN_TROPHY -> {
      // Rotating royal golden light rays from center
      val center = Offset(w / 2f, h / 2f)
      val maxR = maxOf(w, h) * 0.75f
      for (i in 0 until 16) {
        val a = (i * 22.5f + phase * 36f) * PI / 180.0
        drawLine(
          color = palette.primaryAccent.copy(alpha = 0.16f),
          start = center,
          end = Offset(center.x + (maxR * cos(a)).toFloat(), center.y + (maxR * sin(a)).toFloat()),
          strokeWidth = 22f
        )
      }
      drawSparkleField(count = 28, phase = phase, color = palette.primaryAccent)
    }

    KidsCelebrationTheme.OCEAN_PEARLS -> {
      for (i in 0 until 24) {
        val bx = w * ((i * 29 + 5) % 94) / 100f + sin((phase * 2 * PI + i).toDouble()).toFloat() * 12f
        val by = h + 40f - (((phase + i * 0.08f) % 1f) * (h + 100f))
        val r = 8f + (i % 4) * 5f
        drawCircle(color = Color(0xFF67E8F9).copy(alpha = 0.22f), radius = r, center = Offset(bx, by))
        drawCircle(color = Color.White.copy(alpha = 0.58f), radius = r, center = Offset(bx, by), style = Stroke(width = 2f))
        drawCircle(color = Color.White.copy(alpha = 0.75f), radius = r * 0.25f, center = Offset(bx - r * 0.3f, by - r * 0.3f))
      }
    }

    KidsCelebrationTheme.DESERT_MOON_OASIS -> {
      drawSparkleField(count = 30, phase = phase, color = Color(0xFFFDE68A))
      // Glowing crescent top-right
      val moonCenter = Offset(w * 0.80f, h * 0.14f)
      drawCircle(color = Color(0xFFFCD34D), radius = 34f, center = moonCenter)
      drawCircle(color = palette.topBg, radius = 29f, center = Offset(moonCenter.x - 12f, moonCenter.y - 6f))
      // Shooting star streaking across upper sky
      val sx = w * (0.95f - phase * 0.85f)
      val sy = h * (0.08f + phase * 0.26f)
      drawLine(
        brush = Brush.linearGradient(
          colors = listOf(Color.White, Color(0xFFFCD34D).copy(alpha = 0f)),
          start = Offset(sx, sy),
          end = Offset(sx + 95f, sy - 32f)
        ),
        start = Offset(sx, sy),
        end = Offset(sx + 95f, sy - 32f),
        strokeWidth = 5f,
        cap = StrokeCap.Round
      )
      drawCircle(color = Color.White, radius = 5.5f, center = Offset(sx, sy))
    }
  }
}

private fun DrawScope.drawSparkleField(count: Int, phase: Float, color: Color) {
  val w = size.width
  val h = size.height
  for (i in 0 until count) {
    val x = ((i * 47 + 13) % 96) / 100f * w
    val y = ((i * 59 + 11) % 92) / 100f * h
    val twinkle = 0.30f + 0.70f * ((sin((phase * 2 * PI + i * 0.9).toDouble()).toFloat() + 1f) / 2f)
    val r = (4f + (i % 3) * 2.5f) * twinkle
    drawLine(
      color = color.copy(alpha = twinkle * 0.85f),
      start = Offset(x - r, y),
      end = Offset(x + r, y),
      strokeWidth = 2.5f,
      cap = StrokeCap.Round
    )
    drawLine(
      color = color.copy(alpha = twinkle * 0.85f),
      start = Offset(x, y - r),
      end = Offset(x, y + r),
      strokeWidth = 2.5f,
      cap = StrokeCap.Round
    )
  }
}

private fun playCelebrationFanfare(starsEarned: Int) {
  runCatching {
    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 58)
    val tone = if (starsEarned >= 3) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP2
    tg.startTone(tone, 220)
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
      { runCatching { tg.release() } },
      360L
    )
  }
}
