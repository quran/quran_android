package com.quran.labs.androidquran;

import java.util.Locale;

import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;

import com.quran.labs.androidquran.util.QuranSettings;

public class QuranPreferenceActivity extends PreferenceActivity {
	
	private boolean isArabic;
	private CheckBoxPreference arabicPreference;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//Inflate preference screen
		addPreferencesFromResource(R.xml.quran_preferences);
		isArabic = QuranSettings.getInstance().isArabicNames();
		arabicPreference = (CheckBoxPreference) findPreference(getResources().getString(R.string.prefs_use_arabic_names));
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	public void onBackPressed() {
		// Now reload configurations if language has changed..
		if (isArabic != arabicPreference.isChecked()) {
			Locale locale = arabicPreference.isChecked() ? new Locale("ar") : new Locale("");
			Locale.setDefault(locale);
			Configuration config = new Configuration();
			config.locale = locale;
			getBaseContext().getResources().updateConfiguration(config,
			      getBaseContext().getResources().getDisplayMetrics());
		}
		super.onBackPressed();
	}
}
