package com.quran.mobile.feature.appicon.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap

@Composable
fun AppIconImage(@DrawableRes icon: Int, size: Dp, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val resources = LocalResources.current
  val sizePx = with(LocalDensity.current) { size.roundToPx() }
  // painterResource can't load adaptive icons, so draw the drawable into a bitmap
  val bitmap = remember(icon, sizePx, resources) {
    ResourcesCompat.getDrawable(resources, icon, context.theme)
      ?.toBitmap(sizePx, sizePx)
      ?.asImageBitmap()
  }

  if (bitmap != null) {
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
  }
}
