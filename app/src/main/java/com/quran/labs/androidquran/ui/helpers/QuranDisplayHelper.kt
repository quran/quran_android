package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Point
import android.graphics.Shader
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.Display
import android.widget.Toast
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.Response
import com.quran.labs.androidquran.ui.util.ToastCompat
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranUtils
import okhttp3.OkHttpClient
import timber.log.Timber

object QuranDisplayHelper {

  internal fun getQuranPage(
    okHttpClient: OkHttpClient,
    context: Context,
    widthParam: String,
    page: Int,
    quranFileUtils: QuranFileUtils,
    maxTargetWidth: Int = 0
  ): Response {
    val filename = QuranFileUtils.getPageFileName(page)
    var response = quranFileUtils.getImageFromSD(widthParam, filename, maxTargetWidth)
    if (!response.isSuccessful) {
      // let's only try if an sdcard is found... otherwise, let's tell
      // the user to mount their sdcard and try again.
      if (response.errorCode != Response.ERROR_SD_CARD_NOT_FOUND) {
        Timber.d("failed to get %d with name %s from sd...", page, filename)

        response = if (!QuranUtils.haveInternet(context)) {
          Response(Response.ERROR_NO_INTERNET).apply {
            setPageData(page)
          }
        } else {
          quranFileUtils.getImageFromWeb(
            okHttpClient, context, widthParam, filename, maxTargetWidth
          )
        }
      }
    }
    return response
  }

  /**
   * Shows the first marker on the visible pages, subject to the popup cooldown.
   *
   * @param page the first visible Quran page; the second page, when visible, is `page + 1`
   * @param isDualPageVisible whether two consecutive Quran pages are visible (false for an
   * Arabic/translation split showing the same page on both sides)
   */
  @JvmStatic
  fun displayMarkerPopup(
    context: Context,
    quranInfo: QuranInfo,
    page: Int,
    isDualPageVisible: Boolean,
    lastPopupTime: Long
  ): Long {
    if (System.currentTimeMillis() - lastPopupTime < 3000) {
      return lastPopupTime
    }
    var rub3 = quranInfo.getRub3FromPage(page)
    if (rub3 == -1 && isDualPageVisible) {
      rub3 = quranInfo.getRub3FromPage(page + 1)
    }
    if (rub3 == -1) {
      return lastPopupTime
    }
    val hizb = (rub3 / 4) + 1
    val sb = StringBuilder()

    if (rub3 % 8 == 0) {
      sb.append(context.getString(R.string.quran_juz2)).append(' ')
        .append(QuranUtils.getLocalizedNumber((hizb / 2) + 1))
    } else {
      when (rub3 % 4) {
        1 -> sb.append(context.getString(R.string.quran_rob3)).append(' ')
        2 -> sb.append(context.getString(R.string.quran_nos)).append(' ')
        3 -> sb.append(context.getString(R.string.quran_talt_arb3)).append(' ')
      }
      sb.append(context.getString(R.string.quran_hizb)).append(' ')
        .append(QuranUtils.getLocalizedNumber(hizb))
    }

    ToastCompat.makeText(context, sb.toString(), Toast.LENGTH_SHORT).show()
    return System.currentTimeMillis()
  }

  // same logic used in displayMarkerPopup method
  @JvmStatic
  fun displayRub3(context: Context, quranInfo: QuranInfo?, page: Int): String {
    val rub3 = quranInfo?.getRub3FromPage(page) ?: -1
    if (rub3 == -1) {
      return ""
    }
    val hizb = (rub3 / 4) + 1
    val sb = StringBuilder()
    sb.append(context.getString(R.string.comma))
    sb.append(' ')
    when (rub3 % 4) {
      1 -> sb.append(context.getString(R.string.quran_rob3)).append(' ')
      2 -> sb.append(context.getString(R.string.quran_nos)).append(' ')
      3 -> sb.append(context.getString(R.string.quran_talt_arb3)).append(' ')
    }
    sb.append(context.getString(R.string.quran_hizb)).append(' ')
      .append(QuranUtils.getLocalizedNumber(hizb))

    return sb.toString()
  }

  @JvmStatic
  fun getPaintDrawable(startX: Int, endX: Int): PaintDrawable {
    return PaintDrawable().apply {
      shape = RectShape()
      shaderFactory = getShaderFactory(startX, endX)
    }
  }

  private fun getShaderFactory(startX: Int, endX: Int): ShapeDrawable.ShaderFactory {
    return object : ShapeDrawable.ShaderFactory() {
      override fun resize(width: Int, height: Int): Shader {
        return LinearGradient(
          startX.toFloat(),
          0f,
          endX.toFloat(),
          0f,
          intArrayOf(
            0xFFDCDAD5.toInt(),
            0xFFFDFDF4.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFDFBEF.toInt()
          ),
          floatArrayOf(0f, 0.18f, 0.48f, 1f),
          Shader.TileMode.REPEAT
        )
      }
    }
  }

  @JvmStatic
  @Suppress("DEPRECATION")
  fun getWidthKitKat(display: Display): Int {
    val point = Point()
    display.getRealSize(point)
    return point.x
  }
}
