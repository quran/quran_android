package com.quran.labs.androidquran.ui.helpers

import android.graphics.drawable.BitmapDrawable
import com.quran.labs.androidquran.common.Response

interface PageDownloadListener {
  fun onLoadImageResponse(drawable: BitmapDrawable?, response: Response?)
}
