/* The following code was written by Matthew Wiggins 
 * and is released under the APACHE 2.0 license 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.quran.labs.androidquran.ui.preference;

import com.quran.labs.androidquran.data.Constants;

import android.content.Context;
import android.graphics.Color;
import android.preference.DialogPreference;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class SeekBarPreference extends DialogPreference implements
		SeekBar.OnSeekBarChangeListener {
	private static final String androidns = "http://schemas.android.com/apk/res/android";

	private SeekBar mSeekBar;
	private TextView mValueText;
	private Context mContext;

	private String mDialogMessage, mSuffix;
	private int mDefault, mMax, mValue = 0;

	public SeekBarPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;

		mDialogMessage = attrs.getAttributeValue(androidns, "dialogMessage");
		mSuffix = attrs.getAttributeValue(androidns, "text");
		mDefault = attrs.getAttributeIntValue(androidns, "defaultValue",
              Constants.DEFAULT_TEXT_SIZE);
		mMax = attrs.getAttributeIntValue(androidns, "max", 100);
	}

	@Override
	protected View onCreateDialogView() {
		LinearLayout.LayoutParams params;
		LinearLayout layout = new LinearLayout(mContext);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(6, 6, 6, 6);

		final TextView splashText = new TextView(mContext);
		if (mDialogMessage != null)
			splashText.setText(mDialogMessage);
		layout.addView(splashText);

		mValueText = new TextView(mContext);
		mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
		mValueText.setTextSize(32);
		params = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.FILL_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		layout.addView(mValueText, params);

		mSeekBar = new SeekBar(mContext);
		mSeekBar.setOnSeekBarChangeListener(this);
		layout.addView(mSeekBar, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.FILL_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));

		if (shouldPersist())
			mValue = getPersistedInt(mDefault);

		mSeekBar.setMax(mMax);
		mSeekBar.setProgress(mValue);
		return layout;
	}

	@Override
	protected void onBindDialogView(@NonNull View v) {
		super.onBindDialogView(v);
		mSeekBar.setMax(mMax);
		mSeekBar.setProgress(mValue);
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

	public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
		String t = String.valueOf(value);
		mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
		if (shouldPersist()){
			persistInt(value);
      }
		callChangeListener(value);
	}

	public void onStartTrackingTouch(SeekBar seek) {
	}

	public void onStopTrackingTouch(SeekBar seek) {
	}

	public void setMax(int max) {
		mMax = max;
	}

	public int getMax() {
		return mMax;
	}

	public void setProgress(int progress) {
		mValue = progress;
		if (mSeekBar != null)
			mSeekBar.setProgress(progress);
	}

	public int getProgress() {
		return mValue;
	}
}
