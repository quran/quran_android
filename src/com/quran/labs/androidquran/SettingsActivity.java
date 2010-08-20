package com.quran.labs.androidquran;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.quran.labs.androidquran.common.ApplicationConstants;
import com.quran.labs.androidquran.util.QuranSettings;

public class SettingsActivity extends Activity implements OnCheckedChangeListener {
	protected CheckBox chkArabicNames;
	protected CheckBox chkFullScreen;
	protected CheckBox chkShowClock;
	protected CheckBox chkKeepScreenOn;
	private QuranSettings settings;
	
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
        settings = QuranSettings.getInstance();
        chkArabicNames = (CheckBox)findViewById(R.id.chkArbaicNames);
        chkArabicNames.setOnCheckedChangeListener(this);
        chkFullScreen = (CheckBox)findViewById(R.id.chkFullScreen);
        chkFullScreen.setOnCheckedChangeListener(this);
        chkShowClock = (CheckBox)findViewById(R.id.chkShowClock);
        chkShowClock.setOnCheckedChangeListener(this);
        chkKeepScreenOn = (CheckBox)findViewById(R.id.chkKeepScreenOn);
        chkKeepScreenOn.setOnCheckedChangeListener(this);
        loadSettings();
	}
	
	private void loadSettings() {
		chkArabicNames.setChecked(settings.isArabicNames());
		chkKeepScreenOn.setChecked(settings.isKeepScreenOn());
		chkFullScreen.setChecked(settings.isFullScreen());
		chkShowClock.setChecked(settings.isShowClock());
		chkShowClock.setEnabled(settings.isFullScreen());
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		switch (buttonView.getId()) {
			case R.id.chkArbaicNames:
				settings.setArabicNames(isChecked);
			break;
			case R.id.chkKeepScreenOn:
				settings.setKeepScreenOn(isChecked);
			break;
			case R.id.chkFullScreen:
				settings.setFullScreen(isChecked);
				chkShowClock.setChecked(false);
				chkShowClock.setEnabled(isChecked);
			break;
			case R.id.chkShowClock:
				settings.setShowClock(isChecked);
			break;
		}
		QuranSettings.save(getSharedPreferences(ApplicationConstants.PREFERNCES, 0));
	}

}
