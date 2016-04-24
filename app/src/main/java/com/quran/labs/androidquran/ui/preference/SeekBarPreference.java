/* The following code was written by Matthew Wiggins 
 * and is released under the APACHE 2.0 license 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.quran.labs.androidquran.ui.preference;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.util.QuranUtils;

public class SeekBarPreference extends Preference implements SeekBar.OnSeekBarChangeListener {

  private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

  private Context mContext;
  private SeekBar mSeekBar;
  private TextView mValueText;

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
  protected View onCreateView(ViewGroup parent) {
    View view = super.onCreateView(parent);
    mSeekBar = (SeekBar) view.findViewById(R.id.seekbar);
    mValueText = (TextView) view.findViewById(R.id.value);
    mSeekBar.setOnSeekBarChangeListener(this);
    styleSeekBar();
    return view;
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
  protected void onBindView(@NonNull View view) {
    super.onBindView(view);
    if (isEnabled()) {
      final TextView tv = (TextView) view.findViewById(android.R.id.title);
      if (tv != null) {
        tv.setTextColor(Color.WHITE);
      }
    }
    mValue = shouldPersist() ? getPersistedInt(mDefault) : 0;
    mSeekBar.setMax(mMax);
    mSeekBar.setProgress(mValue);
  }

  @Override
  protected void onSetInitialValue(boolean restore, Object defaultValue) {
    super.onSetInitialValue(restore, defaultValue);
    if (restore) {
      mValue = shouldPersist() ? getPersistedInt(mDefault) : 0;
    } else {
      mValue = (Integer) defaultValue;
    }
  }

  @Override
  public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
    String t = QuranUtils.getLocalizedNumber(mContext, value);
    mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
    mCurrentValue = value;
  }

  public void onStartTrackingTouch(SeekBar seek) {
  }

  public void onStopTrackingTouch(SeekBar seek) {
    if (shouldPersist()) {
      persistInt(mCurrentValue);
      callChangeListener(mCurrentValue);
    }
  }
}
