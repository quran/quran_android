package com.quran.labs.androidquran.extra.feature.linebyline.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap

data class SidelineModel(
  val image: ImageBitmap,
  val targetLine: Int,
  val direction: SidelineDirection,
  val bitmap: Bitmap
)
