package com.quran.mobile.feature.downloadmanager.presenter

import java.text.Normalizer

object SearchTextUtil {

  fun asSearchableString(string: String): String {
    val normalizedString = if (!Normalizer.isNormalized(string, Normalizer.Form.NFKD)) {
      Normalizer.normalize(string, Normalizer.Form.NFKD)
    } else {
      string
    }
    return normalizedString.replace("\\p{M}".toRegex(), "").lowercase()
  }
}
