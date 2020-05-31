package com.quran.labs.androidquran.ui.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.QuranSettings;

public class ToastCompat {

  private static final int ANDROID_R = 30;

  public static Toast makeText(@NonNull Context context, @StringRes int messageRes, int duration) {
    return makeText(context, context.getString(messageRes), duration);
  }

  public static Toast makeText(@NonNull Context context, CharSequence message, int duration) {
    @SuppressLint("ShowToast") Toast toast = Toast.makeText(context, message, duration);
    if (Build.VERSION.SDK_INT < ANDROID_R) {
      // It's unclear what behavior `Toast.setView()` has on R and above, hence the guard.
      toast.setView(createToastView(context, message));
    }
    return toast;
  }

  private static View createToastView(Context context, CharSequence message) {
    View toastView = LayoutInflater.from(context).inflate(R.layout.toast, null, false);

    boolean nightMode = QuranSettings.getInstance(context).isNightMode();
    Drawable tintedBackground = createTintedBackground(context, nightMode);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      toastView.setBackground(tintedBackground);
    } else {
      toastView.setBackgroundDrawable(tintedBackground);
    }

    TextView textView = toastView.findViewById(R.id.message);
    textView.setTextColor(getTextColor(context, nightMode));
    textView.setText(message);
    return toastView;
  }

  private static Drawable createTintedBackground(Context context, boolean nightMode) {
    Drawable toastFrame = DrawableCompat.wrap(AppCompatResources.getDrawable(context, R.drawable.toast_frame));
    DrawableCompat.setTint(toastFrame, getBackgroundColor(context, nightMode));
    return toastFrame;
  }

  private static int getTextColor(Context context, boolean nightMode) {
    if (nightMode) {
      return ContextCompat.getColor(context, R.color.toast_text_color_night);
    } else {
      return ContextCompat.getColor(context, R.color.toast_text_color);
    }
  }

  private static int getBackgroundColor(Context context, boolean nightMode) {
    if (nightMode) {
      return ContextCompat.getColor(context, R.color.toast_background_color_night);
    } else {
      return ContextCompat.getColor(context, R.color.toast_background_color);
    }
  }
}
