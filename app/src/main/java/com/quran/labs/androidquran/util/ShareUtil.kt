package com.quran.labs.androidquran.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

import com.quran.data.model.QuranText
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.LocalTranslation
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.util.ToastCompat

import androidx.annotation.StringRes
import dagger.Reusable
import java.lang.StringBuilder
import javax.inject.Inject

@Reusable
class ShareUtil @Inject internal constructor(private val quranDisplayData: QuranDisplayData) {

  fun copyVerses(activity: Activity, verses: List<QuranText>) {
    val text = getShareText(activity, verses)
    copyToClipboard(activity, text)
  }

  fun copyToClipboard(activity: Activity, text: String?) {
    val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(activity.getString(R.string.app_name), text)
    cm.setPrimaryClip(clip)
    ToastCompat.makeText(
      activity, activity.getString(R.string.ayah_copied_popup),
      Toast.LENGTH_SHORT
    ).show()
  }

  fun shareVerses(activity: Activity, verses: List<QuranText>) {
    val text = getShareText(activity, verses)
    shareViaIntent(activity, text, R.string.share_ayah_text)
  }

  fun shareViaIntent(activity: Activity, text: String?, @StringRes titleResId: Int) {
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
    }
    activity.startActivity(Intent.createChooser(intent, activity.getString(titleResId)))
  }

  fun getShareText(
    context: Context,
    ayahInfo: QuranAyahInfo,
    translationNames: Array<LocalTranslation>
  ): String {
    val sb = StringBuilder()
    ayahInfo.arabicText?.let {
      sb.append(ayahInfo.arabicText)
        .append("\n\n")
    }

    ayahInfo.texts.forEachIndexed { i, translation ->
      val text = translation.text
      if (text.isNotEmpty()) {
        if (i < translationNames.size) {
          sb.append('(')
            .append(translationNames[i].getTranslatorName())
            .append(")\n")
        }
        sb.append(text)
          .append("\n\n")
      }
    }

    sb.append('-')
      .append(quranDisplayData.getSuraAyahString(context, ayahInfo.sura, ayahInfo.ayah))
    return sb.toString()
  }

  private fun getShareText(activity: Activity, verses: List<QuranText>): String {
    val size = verses.size
    val sb = StringBuilder("(")
    for (i in 0 until size) {
      sb.append(verses[i].text)
      if (i + 1 < size) {
        sb.append(" \u06DD  ")
      }
    }

    // append ) and a new line after last ayah
    sb.append(")\n")
    // append [ before sura label
    sb.append("[")
    val (sura, ayah) = verses[0]
    sb.append(quranDisplayData.getSuraName(activity, sura, true))
    sb.append(" ")
    sb.append(ayah)
    if (size > 1) {
      val (sura1, ayah1) = verses[size - 1]
      sb.append(" - ")
      if (sura != sura1) {
        sb.append(quranDisplayData.getSuraName(activity, sura1, true))
        sb.append(" ")
      }
      sb.append(ayah1)
    }
    // close sura label and append two new lines
    sb.append("]")
    return sb.toString()
  }
}
