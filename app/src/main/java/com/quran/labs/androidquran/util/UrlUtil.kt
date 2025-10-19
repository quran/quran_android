package com.quran.labs.androidquran.util

import com.quran.data.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class UrlUtil @Inject constructor() {

  fun fallbackUrl(url: String): String {
    return url.replace(".quranicaudio.com", ".quranicaudio.org")
  }
}
