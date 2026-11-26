package com.quran.labs.androidquran.common.ui.core.modifier

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

// via https://stackoverflow.com/questions/69849631/auto-mirroring-in-jetpack-compose-icons
@Deprecated(message = "Use ImageVector(autoMirror = true) instead")
@Stable
fun Modifier.autoMirror(): Modifier = this then AutoMirrorElement

private data object AutoMirrorElement : ModifierNodeElement<AutoMirrorNode>() {
  override fun create(): AutoMirrorNode = AutoMirrorNode()

  override fun update(node: AutoMirrorNode) {}

  override fun InspectorInfo.inspectableProperties() {
    name = "autoMirror"
  }
}

private class AutoMirrorNode :
  Modifier.Node(),
  DrawModifierNode,
  CompositionLocalConsumerModifierNode {
  override fun ContentDrawScope.draw() {
    if (currentValueOf(LocalLayoutDirection) == LayoutDirection.Rtl) {
      scale(scaleX = -1f, scaleY = 1f) {
        this@draw.drawContent()
      }
    } else {
      drawContent()
    }
  }
}
