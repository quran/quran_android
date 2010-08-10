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
	protected CheckBox chkHideTitle;
	private QuranSettings settings;
	
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
        settings = QuranSettings.getInstance();
        chkArabicNames = (CheckBox)findViewById(R.id.chkArbaicNames);
        chkArabicNames.setOnCheckedChangeListener(this);
        chkHideTitle = (CheckBox)findViewById(R.id.chkHideTitle);
        chkHideTitle.setOnCheckedChangeListener(this);
        loadSettings();
	}
	
	private void loadSettings() {
		chkArabicNames.setChecked(settings.isArabicNames());
		chkHideTitle.setChecked(settings.isHideTitle());
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		switch (buttonView.getId()) {
			case R.id.chkArbaicNames:
				settings.setArabicNames(isChecked);
			break;
			case R.id.chkHideTitle:
				settings.setHideTitle(isChecked);
			break;
		}
		QuranSettings.save(getSharedPreferences(ApplicationConstants.PREFERNCES, 0));
	}

}
