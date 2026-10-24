package com.quran.labs.androidquran.ui.whatsnew

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal enum class RevealPresentation {
  Animated,
  // for removed animations and TalkBack
  Crossfade
}

internal class RevealBeat(val start: Float, val duration: Float, private val easing: Easing) {
  val end: Float get() = start + duration
  val durationMillis: Int get() = (duration * 1000).roundToInt()

  fun progress(time: Float): Float = easing.transform(((time - start) / duration).coerceIn(0f, 1f))
}

// seconds after the reveal starts, matching quran-ios
internal object RevealTimeline {
  private val CopyEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

  val NightRise = RevealBeat(0.45f, 1.5f, CubicBezierEasing(0.7f, 0f, 0.25f, 1f))
  val HorizonGlowFade = RevealBeat(1.35f, 0.6f, EaseOut)
  val TileFade = RevealBeat(0.9f, 0.8f, EaseInOut)
  val PreviousIconFade = RevealBeat(0.95f, 0.9f, EaseInOut)
  val GlyphWrite = RevealBeat(1.2f, 1.3f, CubicBezierEasing(0.35f, 0f, 0.25f, 1f))
  val PenIn = RevealBeat(1.2f, 0.25f, EaseOut)
  val PenOut = RevealBeat(2.2f, 0.3f, EaseIn)
  val IconGlow = RevealBeat(2.3f, 1.2f, EaseOut)
  val Sheen = RevealBeat(2.75f, 0.9f, EaseInOut)
  val NewIcon = RevealBeat(3.1f, 0.5f, EaseInOut)
  val Lift = RevealBeat(3.2f, 0.9f, CubicBezierEasing(0.65f, 0f, 0.35f, 1f))
  val Title = RevealBeat(3.55f, 0.7f, CopyEasing)
  val Body = RevealBeat(3.7f, 0.7f, CopyEasing)
  val Actions = RevealBeat(3.95f, 0.7f, CopyEasing)
  val Crossfade = RevealBeat(0.3f, 0.6f, EaseInOut)

  // when night covers the status bar
  const val NIGHTFALL = 1.6f
  const val SHEEN_REPEAT_DELAY_MILLIS = 4000L
  const val REWIND_MILLIS = 500

  val end: Float get() = Actions.end
}

@Stable
internal class RevealState(val presentation: RevealPresentation, isSettled: Boolean) {
  private val time = Animatable(
    if (isSettled || presentation == RevealPresentation.Crossfade) RevealTimeline.end else 0f
  )
  private val rewind = Animatable(0f)
  private val shine = Animatable(0f)
  private val crossfade = Animatable(
    if (isSettled || presentation == RevealPresentation.Animated) 1f else 0f
  )

  var areActionsEnabled by mutableStateOf(isSettled)
    private set

  val canReplay: Boolean
    get() = presentation == RevealPresentation.Animated && areActionsEnabled

  val finalLayoutAlpha: Float get() = crossfade.value

  val previousIconFade: Float
    get() = if (presentation == RevealPresentation.Animated) {
      progress(RevealTimeline.PreviousIconFade)
    } else {
      crossfade.value
    }

  val penAlpha: Float
    get() = if (isRewinding) {
      0f
    } else {
      RevealTimeline.PenIn.progress(time.value) * (1f - RevealTimeline.PenOut.progress(time.value))
    }

  val sheenProgress: Float
    get() = when {
      isRewinding -> 0f
      shine.value > 0f -> shine.value
      else -> progress(RevealTimeline.Sheen)
    }

  private val isRewinding: Boolean get() = rewind.value > 0f

  fun progress(beat: RevealBeat): Float = beat.progress(time.value) * (1f - rewind.value)

  suspend fun crossfadeIn() {
    val beat = RevealTimeline.Crossfade
    crossfade.animateTo(
      1f,
      tween(beat.durationMillis, delayMillis = (beat.start * 1000).roundToInt(), easing = EaseInOut)
    )
    areActionsEnabled = true
  }

  suspend fun playNightfall(onNightfall: () -> Unit, onSettle: () -> Unit) {
    playTo(RevealTimeline.NIGHTFALL)
    onNightfall()
    playTo(RevealTimeline.NewIcon.end)
    onSettle()
    playTo(RevealTimeline.Actions.start)
    areActionsEnabled = true
    playTo(RevealTimeline.end)
  }

  suspend fun rewind() {
    areActionsEnabled = false
    shine.snapTo(0f)
    rewind.animateTo(1f, tween(RevealTimeline.REWIND_MILLIS, easing = EaseInOut))
    time.snapTo(0f)
    rewind.snapTo(0f)
  }

  suspend fun shineRepeatedly() {
    if (presentation != RevealPresentation.Animated) return
    while (true) {
      shine.snapTo(0f)
      delay(RevealTimeline.SHEEN_REPEAT_DELAY_MILLIS)
      shine.animateTo(1f, tween(RevealTimeline.Sheen.durationMillis, easing = EaseInOut))
    }
  }

  private suspend fun playTo(target: Float) {
    val millis = ((target - time.value) * 1000).roundToInt().coerceAtLeast(0)
    time.animateTo(target, tween(millis, easing = LinearEasing))
  }
}
