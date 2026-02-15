package com.quran.labs.feature.autoquran.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.graphics.createBitmap
import com.quran.data.model.audio.Qari
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Provides deterministic "album art" for Android Auto surfaces.
 *
 * Instead of embedding artwork bytes into every MediaItem (expensive to generate + marshal),
 * we expose a stable content Uri that a ContentProvider can resolve on-demand.
 */
class QariArtworkProvider @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
) {

  private val suraTitleTypeface: Typeface by lazy {
    Typeface.createFromAsset(appContext.assets, "quran_titles.ttf")
  }

  fun artworkUriFor(qari: Qari): Uri {
    return Uri.Builder()
      .scheme("content")
      .authority(authority(appContext))
      .appendPath(QariArtworkContentProvider.PATH_ARTWORK)
      .appendPath(QariArtworkContentProvider.PATH_QARI)
      .appendPath(qari.id.toString())
      .build()
  }

  fun suraArtworkUriFor(qari: Qari, sura: Int): Uri {
    return Uri.Builder()
      .scheme("content")
      .authority(authority(appContext))
      .appendPath(QariArtworkContentProvider.PATH_ARTWORK)
      .appendPath(QariArtworkContentProvider.PATH_QARI)
      .appendPath(qari.id.toString())
      .appendPath(QariArtworkContentProvider.PATH_SURA)
      .appendPath(sura.toString())
      .build()
  }

  internal fun generateQariPng(qariId: Int, displayName: String): ByteArray? {
    return renderPng(qariId) { canvas, sizePx, textColor ->
      val initials = initialsFor(displayName).ifEmpty { "Q" }
      val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = if (initials.length <= 1) sizePx * 0.48f else sizePx * 0.38f
      }

      // Center baseline using font metrics.
      val fm = textPaint.fontMetrics
      val x = sizePx / 2f
      val y = sizePx / 2f - (fm.ascent + fm.descent) / 2f
      canvas.drawText(initials, x, y, textPaint)
    }
  }

  internal fun generateSuraPng(qariId: Int, sura: Int): ByteArray? {
    return renderPng(qariId) { canvas, sizePx, textColor ->
      val glyph = suraGlyph(sura)
      val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        typeface = suraTitleTypeface
        textSize = sizePx * 0.20f
      }

      val fm = glyphPaint.fontMetrics
      val x = sizePx / 2f
      val y = sizePx / 2f - (fm.ascent + fm.descent) / 2f
      canvas.drawText(glyph, x, y, glyphPaint)
    }
  }

  private inline fun renderPng(
    qariId: Int,
    drawForeground: (canvas: Canvas, sizePx: Int, textColor: Int) -> Unit,
  ): ByteArray? {
    return try {
      val bitmap = createBitmap(ARTWORK_SIZE_PX, ARTWORK_SIZE_PX)
      val canvas = Canvas(bitmap)

      val (bgColor, textColor) = colorsFor(qariId)
      val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = bgColor
      }
      canvas.drawRect(0f, 0f, ARTWORK_SIZE_PX.toFloat(), ARTWORK_SIZE_PX.toFloat(), bgPaint)

      drawForeground(canvas, ARTWORK_SIZE_PX, textColor)

      ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
      }
    } catch (_: OutOfMemoryError) {
      null
    }
  }

  private fun suraGlyph(sura: Int): String {
    val i = sura - 1
    val codePoint = 0xFB8D + i + if (i >= 37) 0x21 else 0
    return String(Character.toChars(codePoint))
  }

  private fun colorsFor(qariId: Int): Pair<Int, Int> {
    val hue = ((qariId * 37) % 360).toFloat()
    val bg = Color.HSVToColor(floatArrayOf(hue, 0.20f, 0.95f))
    val text = Color.HSVToColor(floatArrayOf(hue, 0.65f, 0.35f))
    return bg to text
  }

  private fun initialsFor(name: String): String {
    val cleaned = name.trim()
    return if (cleaned.isEmpty()) {
      ""
    } else {
      val parts = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
      val a = firstLetterOrDigit(parts.getOrNull(0)).orEmpty()
      val b = firstLetterOrDigit(parts.getOrNull(1)).orEmpty()
      val raw = (a + b).ifEmpty {
        // Try from the full string.
        firstLetterOrDigit(cleaned).orEmpty()
      }

      if (raw.isEmpty()) {
        ""
      } else {
        val locale = Locale.getDefault()
        raw.uppercase(locale).take(2)
      }
    }
  }

  private fun firstLetterOrDigit(s: String?): String? {
    val str = s?.trim().orEmpty()
    val ch = str.firstOrNull { it.isLetterOrDigit() }
    return ch?.toString()
  }

  companion object {
    private const val ARTWORK_SIZE_PX = 512

    fun authority(context: Context): String = "${context.packageName}.autoquran.artwork"
  }
}
