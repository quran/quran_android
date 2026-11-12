package com.quran.labs.androidquran.ui.whatsnew

import android.graphics.Matrix
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.quran.labs.androidquran.R

// fixed colors from quran-ios, independent of the app theme
internal object RevealPalette {
  val Gold = Color(0xFFE2AF56)
  val GoldLight = Color(0xFFF6D493)
  val GoldDark = Color(0xFFB8893F)
  val Cream = Color(0xFFFFF3D6)
  val NightTop = Color(0xFF00085A)
  val NightMiddle = Color(0xFF000A48)
  val NightBottom = Color(0xFF00062A)
  val ContinueText = Color(0xFF000A3D)
}

internal val RevealIconSize = 148.dp
private val NightEdgeHeight = 160.dp
private val PenWidth = 40.dp
private val PenHeight = 104.dp
private val SheenWidth = 36.dp

@Composable
internal fun RevealNight(state: RevealState, modifier: Modifier = Modifier) {
  Spacer(
    modifier.drawBehind {
      val edge = NightEdgeHeight.toPx()
      val top = lerp(size.height, -edge, state.progress(RevealTimeline.NightRise))
      val alpha = state.finalLayoutAlpha
      if (top >= size.height || alpha == 0f) return@drawBehind

      drawRect(
        brush = Brush.verticalGradient(
          listOf(RevealPalette.NightTop.copy(alpha = 0f), RevealPalette.NightTop),
          startY = top,
          endY = top + edge
        ),
        topLeft = Offset(0f, top),
        size = Size(size.width, edge),
        alpha = alpha
      )
      drawRect(
        brush = Brush.verticalGradient(
          listOf(RevealPalette.NightTop, RevealPalette.NightMiddle, RevealPalette.NightBottom),
          startY = top + edge,
          endY = top + edge + size.height
        ),
        topLeft = Offset(0f, top + edge),
        size = Size(size.width, size.height),
        alpha = alpha
      )

      val glow = 1f - state.progress(RevealTimeline.HorizonGlowFade)
      if (glow > 0f) {
        val center = Offset(size.width / 2, top + edge)
        val radius = edge * 0.8f
        scale(scaleX = size.width * 1.4f / (radius * 2), scaleY = 1f, pivot = center) {
          drawCircle(
            brush = Brush.radialGradient(
              listOf(
                RevealPalette.Gold.copy(alpha = 0.5f),
                RevealPalette.Gold.copy(alpha = 0.16f),
                RevealPalette.Gold.copy(alpha = 0f)
              ),
              center = center,
              radius = radius
            ),
            radius = radius,
            center = center,
            alpha = glow * alpha
          )
        }
      }
    }
  )
}

@Composable
internal fun RevealPreviousIcon(state: RevealState, modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(R.drawable.ic_previous_app_icon),
    contentDescription = null,
    modifier = modifier
      // the legacy PNG has a 152px icon in a 192px canvas
      .requiredSize(RevealIconSize * 192f / 152f)
      .graphicsLayer {
        val fade = state.previousIconFade
        alpha = 1f - fade
        if (state.presentation == RevealPresentation.Animated) {
          val scale = 1f - 0.04f * fade
          scaleX = scale
          scaleY = scale
          val blur = 6.dp.toPx() * fade
          renderEffect = if (blur > 0f) BlurEffect(blur, blur, TileMode.Decal) else null
        }
      }
  )
}

@Composable
internal fun RevealNewIcon(state: RevealState, shape: Shape, modifier: Modifier = Modifier) {
  val background = painterResource(R.drawable.ic_launcher_background)
  val foreground = painterResource(R.drawable.ic_launcher_foreground)
  Box(modifier.size(RevealIconSize)) {
    Spacer(
      Modifier
        .matchParentSize()
        .drawBehind { drawGlow(state.progress(RevealTimeline.IconGlow)) }
    )
    Spacer(
      Modifier
        .matchParentSize()
        .graphicsLayer {
          alpha = state.progress(RevealTimeline.TileFade)
          compositingStrategy = CompositingStrategy.ModulateAlpha
        }
        .dropShadow(shape) {
          radius = 18.dp.toPx()
          offset = Offset(0f, 10.dp.toPx())
          color = Color.Black
          alpha = 0.35f
        }
        .clip(shape)
        .drawBehind { drawLauncherLayer(background) }
        .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
    )
    Spacer(
      Modifier
        .matchParentSize()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawBehind {
          // written right to left in every locale
          val progress = state.progress(RevealTimeline.GlyphWrite)
          if (progress > 0f) {
            clipRect(left = size.width * (1f - progress)) {
              drawLauncherLayer(foreground)
              drawRect(
                Brush.verticalGradient(
                  listOf(RevealPalette.GoldLight, RevealPalette.Gold, RevealPalette.GoldDark)
                ),
                blendMode = BlendMode.SrcIn
              )
            }
          }
        }
    )
    Spacer(
      Modifier
        .align(Alignment.Center)
        // room for the pen and its blur past the icon's edges
        .requiredSize(RevealIconSize + PenWidth * 2, PenHeight + 24.dp)
        .graphicsLayer {
          alpha = state.penAlpha
          val blur = 6.dp.toPx()
          renderEffect = BlurEffect(blur, blur, TileMode.Decal)
        }
        .drawBehind {
          val glyph = state.progress(RevealTimeline.GlyphWrite)
          val iconSize = RevealIconSize.toPx()
          val center = Offset(
            (size.width - iconSize) / 2 + iconSize * (1f - glyph),
            size.height / 2
          )
          val radius = PenHeight.toPx() / 2
          scale(scaleX = PenWidth / PenHeight, scaleY = 1f, pivot = center) {
            drawCircle(
              brush = Brush.radialGradient(
                listOf(
                  RevealPalette.Cream,
                  RevealPalette.Gold.copy(alpha = 0.7f),
                  RevealPalette.Gold.copy(alpha = 0f)
                ),
                center = center,
                radius = radius
              ),
              radius = radius,
              center = center
            )
          }
        }
    )
    Spacer(
      Modifier
        .matchParentSize()
        .graphicsLayer { alpha = state.progress(RevealTimeline.NewIcon) }
        .clip(shape)
        .drawBehind {
          drawLauncherLayer(background)
          drawLauncherLayer(foreground)
        }
    )
    // above the new icon, so the sheen keeps sweeping across it
    Spacer(
      Modifier
        .matchParentSize()
        .clip(shape)
        .drawBehind { drawSheen(state.sheenProgress) }
    )
  }
}

// the device's launcher icon mask
@Composable
internal fun rememberLauncherIconShape(): Shape = remember {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val mask = AdaptiveIconDrawable(null, null).iconMask
    GenericShape { size, _ ->
      val path = android.graphics.Path(mask)
      path.transform(Matrix().apply { setScale(size.width / 100f, size.height / 100f) })
      addPath(path.asComposePath())
    }
  } else {
    RoundedCornerShape(percent = 8)
  }
}

// adaptive icon layers are 108dp, with the visible 72dp in the middle
private fun DrawScope.drawLauncherLayer(painter: Painter) {
  val inset = size.width / 4
  translate(-inset, -inset) {
    with(painter) { draw(Size(size.width * 1.5f, size.height * 1.5f)) }
  }
}

private fun DrawScope.drawGlow(alpha: Float) {
  if (alpha == 0f) return
  val radius = size.width * 1.1f
  drawCircle(
    brush = Brush.radialGradient(
      listOf(
        RevealPalette.Gold.copy(alpha = 0.34f),
        RevealPalette.Gold.copy(alpha = 0.1f),
        RevealPalette.Gold.copy(alpha = 0f)
      ),
      center = center,
      radius = radius
    ),
    radius = radius,
    alpha = alpha
  )
}

private fun DrawScope.drawSheen(progress: Float) {
  val width = SheenWidth.toPx()
  val x = center.x + size.width * (2 * progress - 1)
  rotate(20f, pivot = Offset(x, center.y)) {
    drawRect(
      brush = Brush.horizontalGradient(
        listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0f)),
        startX = x - width / 2,
        endX = x + width / 2
      ),
      topLeft = Offset(x - width / 2, center.y - size.height),
      size = Size(width, size.height * 2),
      blendMode = BlendMode.Plus
    )
  }
}
