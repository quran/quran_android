package com.quran.labs.androidquran.ui.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.util.QuranSettings

object ToastCompat {

  private const val ANDROID_R = 30

  @JvmStatic
  fun makeText(context: Context, @StringRes messageRes: Int, duration: Int): Toast {
    return makeText(context, context.getString(messageRes), duration)
  }

  @JvmStatic
  fun makeText(context: Context, message: CharSequence, duration: Int): Toast {
    @SuppressLint("ShowToast") val toast = Toast.makeText(context, message, duration)
    if (Build.VERSION.SDK_INT < ANDROID_R) {
      // It's unclear what behavior `Toast.setView()` has on R and above, hence the guard.
      toast.view = createToastView(context, message)
    }
    return toast
  }

  private fun createToastView(context: Context, message: CharSequence): View {
    val nightMode = QuranSettings.getInstance(context).isNightMode
    val tintedBackground = createTintedBackground(context, nightMode)

    val toastView = LayoutInflater.from(context).inflate(R.layout.toast, null, false)
    toastView.background = tintedBackground

    val textView = toastView.findViewById<TextView>(R.id.message)
    textView.setTextColor(getTextColor(context, nightMode))
    textView.text = message
    return toastView
  }

  private fun createTintedBackground(context: Context, nightMode: Boolean): Drawable {
    val toastFrame = DrawableCompat.wrap(
      AppCompatResources.getDrawable(context, R.drawable.toast_frame)!!
    )
    DrawableCompat.setTint(toastFrame, getBackgroundColor(context, nightMode))
    return toastFrame
  }

  private fun getTextColor(context: Context, nightMode: Boolean): Int {
    val color = if (nightMode) {
      R.color.toast_text_color_night
    } else R.color.toast_text_color
    return ContextCompat.getColor(context, color)
  }

  private fun getBackgroundColor(context: Context, nightMode: Boolean): Int {
    val color = if (nightMode) {
      R.color.toast_background_color_night
    } else R.color.toast_background_color
    return ContextCompat.getColor(context, color)
  }
}
