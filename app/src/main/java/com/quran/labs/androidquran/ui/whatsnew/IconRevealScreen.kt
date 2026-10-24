package com.quran.labs.androidquran.ui.whatsnew

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.util.lerp
import androidx.core.content.getSystemService
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme

private val ContentMaxWidth = 440.dp
private val CopyRise = 14.dp

// the "Nightfall" app icon reveal from quran-ios; double tapping the settled icon replays it
@Composable
internal fun IconRevealScreen(onContinue: () -> Unit, onNightChange: (isNight: Boolean) -> Unit) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val isPreview = LocalInspectionMode.current
  var hasStarted by rememberSaveable { mutableStateOf(isPreview) }
  val state = remember {
    val presentation = if (prefersCrossfade(context)) {
      RevealPresentation.Crossfade
    } else {
      RevealPresentation.Animated
    }
    RevealState(presentation, isSettled = hasStarted)
  }
  var playback by remember { mutableIntStateOf(0) }
  val currentOnNightChange by rememberUpdatedState(onNightChange)

  LaunchedEffect(state, playback) {
    val onNightfall = { currentOnNightChange(true) }
    val onSettle = { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    when {
      playback > 0 -> {
        currentOnNightChange(false)
        state.rewind()
        state.playNightfall(onNightfall, onSettle)
      }
      // recreated after starting
      hasStarted -> onNightfall()
      else -> {
        hasStarted = true
        if (state.presentation == RevealPresentation.Crossfade) {
          state.crossfadeIn()
          onNightfall()
        } else {
          state.playNightfall(onNightfall, onSettle)
        }
      }
    }
    state.shineRepeatedly()
  }

  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val layout = RevealLayout(
      height = maxHeight,
      topInset = insets.calculateTopPadding(),
      isCompact = LocalDensity.current.fontScale >= 1.5f || maxHeight < 480.dp
    )
    val bottomFade = if (layout.isCompact) insets.calculateBottomPadding() else 20.dp

    RevealNight(state, Modifier.fillMaxSize())

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .fadeEdges(top = insets.calculateTopPadding(), bottom = bottomFade)
      ) {
        Box(
          Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
        ) {
          Column(
            modifier = Modifier
              .align(Alignment.TopCenter)
              .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
              .widthIn(max = ContentMaxWidth)
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            // room for the lifted icon, which scrolls with the copy
            Spacer(Modifier.height(layout.copyTop))
            RevealCopy(state)
            if (layout.isCompact) {
              RevealActions(
                state = state,
                onContinue = onContinue,
                modifier = Modifier.padding(bottom = max(bottomFade, 20.dp))
              )
            } else {
              // keeps the last line clear of the bottom fade
              Spacer(Modifier.height(bottomFade))
            }
          }

          RevealIcons(
            state = state,
            layout = layout,
            onReplay = { playback++ },
            modifier = Modifier
              .align(Alignment.TopCenter)
              .offset(y = layout.restingCenterY - RevealIconSize / 2)
          )
        }
      }

      if (!layout.isCompact) {
        RevealActions(
          state = state,
          onContinue = onContinue,
          modifier = Modifier
            .windowInsetsPadding(
              WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
            .widthIn(max = ContentMaxWidth)
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
        )
      }
    }
  }
}

private class RevealLayout(height: Dp, topInset: Dp, val isCompact: Boolean) {
  val restingCenterY = height / 2
  val liftedScale = if (isCompact) 0.6f else 0.9f
  private val liftedCenterY = if (isCompact) {
    topInset + 24.dp + RevealIconSize * liftedScale / 2
  } else {
    restingCenterY - 122.dp
  }
  val liftOffset = liftedCenterY - restingCenterY
  val copyTop = liftedCenterY + RevealIconSize * liftedScale / 2 + 28.dp
}

@Composable
private fun RevealIcons(
  state: RevealState,
  layout: RevealLayout,
  onReplay: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(modifier.size(RevealIconSize)) {
    RevealNewIcon(
      state = state,
      shape = rememberLauncherIconShape(),
      modifier = Modifier
        .graphicsLayer {
          val lift = state.progress(RevealTimeline.Lift)
          val scale = lerp(1f, layout.liftedScale, lift)
          scaleX = scale
          scaleY = scale
          translationY = layout.liftOffset.toPx() * lift
          alpha = state.finalLayoutAlpha
          compositingStrategy = CompositingStrategy.ModulateAlpha
        }
        .then(
          if (state.canReplay) {
            Modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReplay() }) }
          } else {
            Modifier
          }
        )
    )
    RevealPreviousIcon(state, Modifier.align(Alignment.Center))
  }
}

@Composable
private fun RevealCopy(state: RevealState) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      text = stringResource(R.string.icon_reveal_title),
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.Bold,
      color = Color.White,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .semantics { heading() }
        .rising(state, RevealTimeline.Title)
    )
    Text(
      text = stringResource(R.string.icon_reveal_body),
      style = MaterialTheme.typography.bodyLarge,
      color = Color.White.copy(alpha = 0.76f),
      textAlign = TextAlign.Center,
      modifier = Modifier.rising(state, RevealTimeline.Body)
    )
  }
}

@Composable
private fun RevealActions(state: RevealState, onContinue: () -> Unit, modifier: Modifier = Modifier) {
  Button(
    onClick = onContinue,
    enabled = state.areActionsEnabled,
    shape = CircleShape,
    colors = ButtonDefaults.buttonColors(
      containerColor = RevealPalette.Gold,
      contentColor = RevealPalette.ContinueText,
      disabledContainerColor = RevealPalette.Gold,
      disabledContentColor = RevealPalette.ContinueText
    ),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    modifier = modifier
      .rising(state, RevealTimeline.Actions)
      .padding(top = 16.dp)
      .fillMaxWidth()
      .heightIn(min = 52.dp)
  ) {
    Text(
      text = stringResource(R.string.whats_new_continue),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center
    )
  }
}

private fun Modifier.rising(state: RevealState, beat: RevealBeat): Modifier = graphicsLayer {
  val progress = state.progress(beat)
  alpha = progress * state.finalLayoutAlpha
  translationY = (1f - progress) * CopyRise.toPx()
}

// fades scrolled content under the status bar and at the bottom edge
private fun Modifier.fadeEdges(top: Dp, bottom: Dp): Modifier = this
  .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
  .drawWithContent {
    drawContent()
    val topEnd = (top.toPx() / size.height).coerceIn(0f, 1f)
    val bottomStart = (1f - bottom.toPx() / size.height).coerceIn(topEnd, 1f)
    drawRect(
      Brush.verticalGradient(
        0f to Color.Transparent,
        topEnd to Color.Black,
        bottomStart to Color.Black,
        1f to Color.Transparent
      ),
      blendMode = BlendMode.DstIn
    )
  }

private fun prefersCrossfade(context: Context): Boolean {
  val animatorScale = Settings.Global.getFloat(
    context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
  )
  val accessibilityManager = context.getSystemService<AccessibilityManager>()
  return animatorScale == 0f || accessibilityManager?.isTouchExplorationEnabled == true
}

@Preview(widthDp = 390, heightDp = 844)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 390, heightDp = 844)
@Preview(name = "Arabic", locale = "ar", widthDp = 390, heightDp = 844)
@Preview(name = "Large text", fontScale = 2f, widthDp = 320, heightDp = 640)
@Preview(name = "Landscape", widthDp = 840, heightDp = 400)
@Composable
private fun IconRevealPreview() {
  QuranTheme {
    IconRevealScreen(onContinue = {}, onNightChange = {})
  }
}
