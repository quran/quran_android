package com.quran.labs.autoquran.common

import android.content.Context
import android.text.TextUtils
import com.quran.data.core.QuranConstants
import com.quran.labs.autoquran.R

fun getSuraName(
    context: Context, sura: Int, wantPrefix: Boolean, wantTranslation: Boolean
): String {
  if (sura < QuranConstants.FIRST_SURA || sura > QuranConstants.LAST_SURA) return ""

  val builder = StringBuilder()
  val suraNames = context.resources.getStringArray(R.array.sura_names)
  if (wantPrefix) {
    builder.append(context.getString(R.string.quran_sura_title, suraNames[sura - 1]))
  } else {
    builder.append(suraNames[sura - 1])
  }
  if (wantTranslation) {
    val translation = context.resources.getStringArray(R.array.sura_names_translation)[sura - 1]
    if (!TextUtils.isEmpty(translation)) {
      // Some sura names may not have translation
      builder.append(" (")
      builder.append(translation)
      builder.append(")")
    }
  }

  return builder.toString()
}

fun makeThreeDigit(number: Int): String {
  var numStr = number.toString()
  if (numStr.length < 3) {
    numStr = "0".repeat(3 - numStr.length) + numStr
  }
  return numStr
}
