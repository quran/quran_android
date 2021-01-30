/* The following code was written by Matthew Wiggins 
 * and is released under the APACHE 2.0 license 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.quran.labs.androidquran.ui.preference;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.util.QuranUtils;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

public class SeekBarPreference extends Preference implements SeekBar.OnSeekBarChangeListener {

  private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

  private Context mContext;
  private SeekBar mSeekBar;
  private TextView mValueText;
  protected TextView mPreviewText;

  private String mSuffix;
  private int mTintColor;
  private int mCurrentValue;
  private int mDefault, mMax, mValue = 0;

  public SeekBarPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    mContext = context;
    mSuffix = attrs.getAttributeValue(ANDROID_NS, "text");
    mDefault = attrs.getAttributeIntValue(ANDROID_NS, "defaultValue",
        Constants.DEFAULT_TEXT_SIZE);
    mMax = attrs.getAttributeIntValue(ANDROID_NS, "max", 100);
    setLayoutResource(R.layout.seekbar_pref);
    mTintColor = ContextCompat.getColor(context, R.color.accent_color);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);
    mSeekBar = (SeekBar) holder.findViewById(R.id.seekbar);
    mValueText = (TextView) holder.findViewById(R.id.value);
    mPreviewText = (TextView) holder.findViewById(R.id.pref_preview);
    mPreviewText.setVisibility(getPreviewVisibility());
    mSeekBar.setOnSeekBarChangeListener(this);
    styleSeekBar();
    mValue = shouldPersist() ? getPersistedInt(mDefault) : 0;
    mSeekBar.setMax(mMax);
    mSeekBar.setProgress(mValue);
  }

  /**
   * Visibility of the preview view under the seek bar
   */
  protected int getPreviewVisibility() {
    return View.GONE;
  }

  private void styleSeekBar() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      final Drawable progressDrawable = mSeekBar.getProgressDrawable();
      if (progressDrawable != null) {
        if (progressDrawable instanceof LayerDrawable) {
          LayerDrawable ld = (LayerDrawable) progressDrawable;
          int layers = ld.getNumberOfLayers();
          for (int i = 0; i < layers; i++) {
            ld.getDrawable(i).mutate().setColorFilter(mTintColor, PorterDuff.Mode.SRC_ATOP);
          }
        } else {
          progressDrawable.mutate().setColorFilter(mTintColor, PorterDuff.Mode.SRC_ATOP);
        }
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        final Drawable thumb = mSeekBar.getThumb();
        if (thumb != null) {
          thumb.mutate().setColorFilter(mTintColor, PorterDuff.Mode.SRC_ATOP);
        }
      }
    }
  }

  @Override
  protected void onSetInitialValue(@Nullable Object defaultValue) {
    super.onSetInitialValue(defaultValue);
    mValue = shouldPersist()
        ? getPersistedInt(mDefault)
        : (defaultValue != null ? (Integer) defaultValue : 0);
  }

  @Override
  public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
    String t = QuranUtils.getLocalizedNumber(mContext, value);
    mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
    mCurrentValue = value;
  }

  @Override
  public void onStartTrackingTouch(SeekBar seek) {
  }

  @Override
  public void onStopTrackingTouch(SeekBar seek) {
    if (shouldPersist()) {
      persistInt(mCurrentValue);
      callChangeListener(mCurrentValue);
    }
  }
}
