package com.quran.labs.androidquran.memorization.tasmee

import android.content.Context
import android.util.AttributeSet
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.flashcards.MemoryFlashcardManager
import com.quran.labs.androidquran.memorization.MemorizationUiState
import com.quran.labs.androidquran.memorization.QuranMemorizationManager
import com.quran.labs.androidquran.memorization.RevealMode

/**
 * Minimal **Live Recitation ("Recite & Reveal / Tasmee'")** dock with an armed-to-start
 * microphone symbol whose **glyph itself glows** when listening (keeping the button housing calm
 * and matte), paired with flanking **Previous/Next Ayah** navigators and a 1-click Options pill.
 */
class TasmeeBottomRecordingBarView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

  var onMicTapped: (() -> Unit)? = null
  var onStopRecording: (() -> Unit)? = null
  var onRetryDownload: (() -> Unit)? = null
  var onSelectRevealMode: ((RevealMode) -> Unit)? = null
  var onOpenOptions: (() -> Unit)? = null
  var onPreviousAyah: (() -> Unit)? = null
  var onNextAyah: (() -> Unit)? = null

  @Composable
  override fun Content() {
    val tasmeeState by LiveTasmeeManager.uiState.collectAsState()
    val memState by QuranMemorizationManager.uiState.collectAsState()
    if (!tasmeeState.isEnabled) return

    QuranTheme {
      TasmeeBottomRecordingBar(
        tasmeeState = tasmeeState,
        memorizationState = memState,
        onMicTapped = { onMicTapped?.invoke() ?: onStopRecording?.invoke() },
        onRetryDownload = { onRetryDownload?.invoke() },
        onSelectRevealMode = { mode -> onSelectRevealMode?.invoke(mode) },
        onOpenOptions = { onOpenOptions?.invoke() },
        onPreviousAyah = {
          onPreviousAyah?.invoke() ?: LiveTasmeeManager.goToPreviousAyah(context)
        },
        onNextAyah = {
          onNextAyah?.invoke() ?: LiveTasmeeManager.skipToNextAyah(context)
        }
      )
    }
  }
}

private val MicEmeraldCore = Color(0xFF10B981)
private val MicEmeraldBright = Color(0xFF34D399)
private val MicEmeraldIncandescent = Color(0xFFECFDF5)
private val MicMintLuminous = Color(0xFF6EE7B7)
private val MicIdleSilver = Color(0xFFCBD5E1)
private val MicHousingMatte = Color(0xFF0F172A)
private val MicErrorCoral = Color(0xFFEF4444)
private val MicErrorBright = Color(0xFFFCA5A5)
private val TasmeeEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

private enum class TasmeeOrbVisualState {
  DOWNLOADING,
  WARMING_UP,
  MIC_SYMBOL
}

@Composable
fun TasmeeBottomRecordingBar(
  tasmeeState: TasmeeUiState,
  @Suppress("UNUSED_PARAMETER") memorizationState: MemorizationUiState,
  onMicTapped: () -> Unit,
  onRetryDownload: () -> Unit,
  @Suppress("UNUSED_PARAMETER") onSelectRevealMode: (RevealMode) -> Unit,
  onOpenOptions: () -> Unit = {},
  onPreviousAyah: () -> Unit = {},
  onNextAyah: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val isArabic = remember(context) { MemoryFlashcardManager.isAppInArabicMode(context) }

  val isError = tasmeeState.stage == TasmeeStage.ERROR
  val isDownloading = tasmeeState.stage == TasmeeStage.DOWNLOADING_MODELS
  val isWarmingUp = tasmeeState.stage == TasmeeStage.WARMING_UP
  val isListening = tasmeeState.stage == TasmeeStage.LISTENING
  val isVoiceDetected = isListening && tasmeeState.isVadActive

  val rawVoiceEnergy = remember(tasmeeState.micLevel, tasmeeState.waveformBars, isVoiceDetected) {
    val barPeak = tasmeeState.waveformBars.takeLast(4).maxOrNull() ?: 0f
    val levelNorm = (tasmeeState.micLevel * 4.2f).coerceIn(0f, 1f)
    val combined = maxOf(barPeak, levelNorm)
    if (isVoiceDetected) combined.coerceIn(0.45f, 1f) else 0f
  }

  val animatedVoiceEnergy by animateFloatAsState(
    targetValue = rawVoiceEnergy,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessMediumLow
    ),
    label = "micVoiceEnergy"
  )

  val infiniteTransition = rememberInfiniteTransition(label = "micSymbolBreath")
  val listeningBreath by infiniteTransition.animateFloat(
    initialValue = 0.78f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "micBreathFactor"
  )

  // Glow intensity lives ONLY on the microphone glyph itself:
  // 0f when READY (calm unlit mic waiting to be pressed), ~0.62..0.78 while LISTENING idle, 1.0 on voice!
  val targetGlowIntensity = when {
    isError -> 0.75f
    isVoiceDetected -> (0.82f + 0.18f * animatedVoiceEnergy).coerceIn(0.82f, 1f)
    isListening -> 0.64f * listeningBreath
    else -> 0f
  }
  val micGlowIntensity by animateFloatAsState(
    targetValue = targetGlowIntensity,
    animationSpec = tween(durationMillis = 190, easing = TasmeeEaseOut),
    label = "micSymbolGlowIntensity"
  )

  val secondaryChromeAlpha by animateFloatAsState(
    targetValue = if (isVoiceDetected) 0.48f else 0.94f,
    animationSpec = tween(durationMillis = if (isVoiceDetected) 200 else 160, easing = TasmeeEaseOut),
    label = "tasmeeSecondaryAlpha"
  )

  val secondaryChromeScale by animateFloatAsState(
    targetValue = if (isVoiceDetected) 0.94f else 1.0f,
    animationSpec = tween(durationMillis = if (isVoiceDetected) 200 else 160, easing = TasmeeEaseOut),
    label = "tasmeeSecondaryScale"
  )

  val badgeFillColor by animateColorAsState(
    targetValue = when {
      isVoiceDetected -> Color(0xFF065F46).copy(alpha = 0.86f)
      isListening -> Color(0xFF0F2E28).copy(alpha = 0.82f)
      else -> Color(0xFF0F172A).copy(alpha = 0.72f)
    },
    animationSpec = tween(durationMillis = 190, easing = TasmeeEaseOut),
    label = "tasmeeBadgeFill"
  )
  val badgeStrokeColor by animateColorAsState(
    targetValue = when {
      isVoiceDetected -> MicEmeraldBright.copy(alpha = 0.78f)
      isListening -> MicEmeraldBright.copy(alpha = 0.42f)
      else -> Color(0xFF94A3B8).copy(alpha = 0.35f)
    },
    animationSpec = tween(durationMillis = 190, easing = TasmeeEaseOut),
    label = "tasmeeBadgeStroke"
  )

  val micContentDesc = stringResource(R.string.live_tasmee_mic_cd)
  val prevAyahDesc = stringResource(R.string.kids_prev_ayah_cd)
  val nextAyahDesc = stringResource(R.string.kids_next_ayah_cd)
  val optionsContentDesc = stringResource(R.string.memorization_options_menu)

  val micInteraction = remember { MutableInteractionSource() }
  val isMicPressed by micInteraction.collectIsPressedAsState()
  val micPressScale by animateFloatAsState(
    targetValue = if (isMicPressed) 0.92f else 1f,
    animationSpec = tween(durationMillis = if (isMicPressed) 110 else 160, easing = TasmeeEaseOut),
    label = "tasmeeMicPressScale"
  )

  val optionsInteraction = remember { MutableInteractionSource() }
  val isOptionsPressed by optionsInteraction.collectIsPressedAsState()
  val optionsPressScale by animateFloatAsState(
    targetValue = if (isOptionsPressed) 0.92f else 1f,
    animationSpec = tween(durationMillis = if (isOptionsPressed) 110 else 160, easing = TasmeeEaseOut),
    label = "tasmeeOptionsPressScale"
  )

  val orbVisualState = when {
    isDownloading -> TasmeeOrbVisualState.DOWNLOADING
    isWarmingUp -> TasmeeOrbVisualState.WARMING_UP
    else -> TasmeeOrbVisualState.MIC_SYMBOL
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 10.dp, vertical = 2.dp)
      .testTag("tasmee_bottom_recording_bar")
  ) {
    // Subtle active Ayah pill on start side (with live dot only while actively listening)
    tasmeeState.activeAyah?.let { ayah ->
      val ayahNumLabel = "${MemoryFlashcardManager.formatNumber(ayah.sura, isArabic)}:${
        MemoryFlashcardManager.formatNumber(ayah.ayah, isArabic)
      }"
      val label = if (isListening) "● $ayahNumLabel" else ayahNumLabel
      Surface(
        onClick = onOpenOptions,
        shape = RoundedCornerShape(15.dp),
        color = badgeFillColor,
        border = BorderStroke(1.dp, badgeStrokeColor),
        modifier = Modifier
          .align(Alignment.CenterStart)
          .testTag("tasmee_active_ayah_badge")
      ) {
        Text(
          text = label,
          color = Color.White.copy(alpha = 0.94f),
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          style = TextStyle(fontFeatureSettings = "tnum"),
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
      }
    }

    // Centered Next Ayah (Left, RTL proceed) | Matte Housing w/ Glowing Mic Symbol | Prev Ayah
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.align(Alignment.Center)
      ) {
        TasmeeAyahNavButton(
          icon = SkipPreviousVector,
          contentDesc = nextAyahDesc,
          onClick = onNextAyah,
          tag = "tasmee_next_ayah_btn",
          chromeAlpha = secondaryChromeAlpha,
          chromeScale = secondaryChromeScale
        )

        // Centered Microphone Button: calm matte dark circle; the microphone SYMBOL itself glows!
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(50.dp)
            .graphicsLayer {
              scaleX = micPressScale
              scaleY = micPressScale
            }
            .clip(CircleShape)
            .background(MicHousingMatte.copy(alpha = 0.90f))
            .border(
              width = 1.dp,
              color = Color(0xFF94A3B8).copy(alpha = 0.30f),
              shape = CircleShape
            )
            .minimumInteractiveComponentSize()
            .clickable(
              interactionSource = micInteraction,
              indication = ripple(bounded = true, color = Color.White),
              onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (isError) onRetryDownload() else onMicTapped()
              }
            )
            .semantics {
              role = Role.Button
              contentDescription = micContentDesc
            }
            .testTag("stop_recording_button")
        ) {
          AnimatedContent(
            targetState = orbVisualState,
            transitionSpec = {
              (fadeIn(tween(180, easing = TasmeeEaseOut)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(180, easing = TasmeeEaseOut))) togetherWith
                fadeOut(tween(120, easing = TasmeeEaseOut))
            },
            label = "tasmeeOrbContent"
          ) { state ->
            when (state) {
              TasmeeOrbVisualState.DOWNLOADING -> {
                Box(contentAlignment = Alignment.Center) {
                  val pct = tasmeeState.downloadProgress.progressPercent.coerceIn(0, 100)
                  CircularProgressIndicator(
                    progress = { (pct / 100f).coerceIn(0.05f, 1f) },
                    color = MicEmeraldBright,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(34.dp)
                  )
                  Text(
                    text = "$pct%",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum")
                  )
                }
              }
              TasmeeOrbVisualState.WARMING_UP -> {
                CircularProgressIndicator(
                  color = MicMintLuminous,
                  trackColor = Color.White.copy(alpha = 0.18f),
                  strokeWidth = 2.2.dp,
                  modifier = Modifier.size(22.dp)
                )
              }
              TasmeeOrbVisualState.MIC_SYMBOL -> {
                GlowingMicSymbolCanvas(
                  glowIntensity = micGlowIntensity,
                  isVoiceDetected = isVoiceDetected,
                  isError = isError,
                  modifier = Modifier.size(34.dp)
                )
              }
            }
          }
        }

        TasmeeAyahNavButton(
          icon = SkipNextVector,
          contentDesc = prevAyahDesc,
          onClick = onPreviousAyah,
          tag = "tasmee_prev_ayah_btn",
          chromeAlpha = secondaryChromeAlpha,
          chromeScale = secondaryChromeScale
        )
      }
    }

    // Subtle 1-click Options reveal pill on the end side
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .size(48.dp)
        .alpha(secondaryChromeAlpha)
        .graphicsLayer {
          val s = secondaryChromeScale * optionsPressScale
          scaleX = s
          scaleY = s
        }
        .clip(CircleShape)
        .clickable(
          interactionSource = optionsInteraction,
          indication = ripple(bounded = true, color = Color.White),
          onClick = onOpenOptions
        )
        .semantics {
          role = Role.Button
          contentDescription = optionsContentDesc
        }
        .testTag("tasmee_open_options_button")
    ) {
      Surface(
        shape = CircleShape,
        color = Color(0xFF1E293B).copy(alpha = 0.68f),
        border = BorderStroke(1.dp, Color(0xFF94A3B8).copy(alpha = 0.35f)),
        modifier = Modifier.size(34.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            painter = painterResource(R.drawable.ic_recitation_options),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.90f),
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

/**
 * Custom Canvas microphone symbol where **the microphone glyph itself** illuminates from within:
 * - Unlit (`glowIntensity == 0f`): crisp calm silver-white mic silhouette waiting to be tapped.
 * - Active (`glowIntensity > 0f`): layered contour bloom tightly hugging the mic capsule, cradle,
 *   stem, and base + internally-lit emerald-incandescent capsule gradient (or coral on error) —
 *   never glowing the circular button background.
 */
@Composable
private fun GlowingMicSymbolCanvas(
  glowIntensity: Float,
  isVoiceDetected: Boolean,
  isError: Boolean,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f

    // Microphone proportions inside 34dp viewport
    val capW = w * 0.27f
    val capH = h * 0.42f
    val capLeft = cx - capW * 0.5f
    val capTop = h * 0.14f
    val capRadius = CornerRadius(capW * 0.5f, capW * 0.5f)

    val cradleRadius = w * 0.245f
    val cradleTop = capTop + capH * 0.34f
    val cradleArcSize = Size(cradleRadius * 2f, cradleRadius * 2f)
    val cradleTopLeft = Offset(cx - cradleRadius, cradleTop)
    val cradleBottomY = cradleTop + cradleRadius * 2f

    val stemBottomY = h * 0.855f
    val baseHalfW = w * 0.165f
    val baseStroke = w * 0.068f

    val bloomColor = if (isError) MicErrorCoral else MicEmeraldBright
    val coreFillBrush = when {
      isError -> Brush.verticalGradient(
        colors = listOf(MicErrorBright, MicErrorCoral),
        startY = capTop,
        endY = capTop + capH
      )
      glowIntensity > 0.04f -> Brush.verticalGradient(
        colors = if (isVoiceDetected) {
          listOf(MicEmeraldIncandescent, MicMintLuminous, MicEmeraldCore)
        } else {
          listOf(Color(0xFFD1FAE5), MicMintLuminous, MicEmeraldBright)
        },
        startY = capTop,
        endY = capTop + capH
      )
      else -> SolidColor(MicIdleSilver)
    }

    val hardwareColor = when {
      isError -> MicErrorBright
      isVoiceDetected -> MicEmeraldIncandescent
      glowIntensity > 0.04f -> MicMintLuminous
      else -> MicIdleSilver
    }

    // Helper to stroke the mic's cradle + stem + base with a given paint width & color
    fun DrawScope.drawMicStand(color: Color, strokeWidth: Float) {
      drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = cradleTopLeft,
        size = cradleArcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
      )
      drawLine(
        color = color,
        start = Offset(cx, cradleBottomY),
        end = Offset(cx, stemBottomY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
      )
      drawLine(
        color = color,
        start = Offset(cx - baseHalfW, stemBottomY),
        end = Offset(cx + baseHalfW, stemBottomY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
      )
    }

    // 1. Tight glyph-hugging neon bloom around the microphone silhouette itself (never the button bg!)
    if (glowIntensity > 0.02f) {
      val g = glowIntensity.coerceIn(0f, 1f)
      val layers = listOf(
        Triple(baseStroke * 3.2f * (0.9f + 0.25f * g), 0.15f * g, 2.2.dp.toPx()),
        Triple(baseStroke * 2.1f, 0.30f * g, 1.2.dp.toPx()),
        Triple(baseStroke * 1.45f, 0.52f * g, 0.5.dp.toPx())
      )
      for ((strokeW, alpha, capExpand) in layers) {
        val c = bloomColor.copy(alpha = alpha)
        drawRoundRect(
          color = c,
          topLeft = Offset(capLeft - capExpand, capTop - capExpand),
          size = Size(capW + capExpand * 2f, capH + capExpand * 2f),
          cornerRadius = CornerRadius(capRadius.x + capExpand, capRadius.y + capExpand)
        )
        drawMicStand(color = c, strokeWidth = strokeW)
      }
    }

    // 2. Crisp internally-lit microphone capsule
    drawRoundRect(
      brush = coreFillBrush,
      topLeft = Offset(capLeft, capTop),
      size = Size(capW, capH),
      cornerRadius = capRadius
    )

    // 3. Subtle incandescent filament specular highlight inside the capsule when glowing
    if (glowIntensity > 0.08f) {
      val specX = cx - capW * 0.17f
      drawLine(
        color = Color.White.copy(alpha = (0.72f * glowIntensity).coerceIn(0f, 0.85f)),
        start = Offset(specX, capTop + capH * 0.20f),
        end = Offset(specX, capTop + capH * 0.62f),
        strokeWidth = (capW * 0.16f).coerceAtLeast(1.2.dp.toPx()),
        cap = StrokeCap.Round
      )
    }

    // 4. Crisp microphone cradle, stem, and base
    drawMicStand(color = hardwareColor, strokeWidth = baseStroke)
  }
}

@Composable
private fun TasmeeAyahNavButton(
  icon: ImageVector,
  contentDesc: String,
  onClick: () -> Unit,
  tag: String,
  chromeAlpha: Float = 1f,
  chromeScale: Float = 1f
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (isPressed) 0.91f else 1f,
    animationSpec = tween(durationMillis = if (isPressed) 110 else 160, easing = TasmeeEaseOut),
    label = "tasmeeNavPress"
  )

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .size(48.dp)
      .alpha(chromeAlpha)
      .graphicsLayer {
        val s = chromeScale * pressScale
        scaleX = s
        scaleY = s
      }
      .clip(CircleShape)
      .clickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true, color = Color.White),
        onClick = onClick
      )
      .semantics {
        role = Role.Button
        contentDescription = contentDesc
      }
      .testTag(tag)
  ) {
    Surface(
      shape = CircleShape,
      color = Color(0xFF0F172A).copy(alpha = 0.74f),
      border = BorderStroke(1.dp, Color(0xFF94A3B8).copy(alpha = 0.42f)),
      modifier = Modifier.size(36.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

private val SkipPreviousVector: ImageVector by lazy {
  ImageVector.Builder(
    name = "SkipPreviousAyah",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
  ).apply {
    path(
      stroke = SolidColor(Color.White),
      strokeLineWidth = 2.4f,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round
    ) {
      moveTo(7f, 6f)
      lineTo(7f, 18f)
      moveTo(17f, 6f)
      lineTo(10.5f, 12f)
      lineTo(17f, 18f)
    }
  }.build()
}

private val SkipNextVector: ImageVector by lazy {
  ImageVector.Builder(
    name = "SkipNextAyah",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
  ).apply {
    path(
      stroke = SolidColor(Color.White),
      strokeLineWidth = 2.4f,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round
    ) {
      moveTo(7f, 6f)
      lineTo(13.5f, 12f)
      lineTo(7f, 18f)
      moveTo(17f, 6f)
      lineTo(17f, 18f)
    }
  }.build()
}
