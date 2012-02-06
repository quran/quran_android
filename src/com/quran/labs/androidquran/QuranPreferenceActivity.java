package com.quran.labs.androidquran;

import java.util.Locale;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;

public class QuranPreferenceActivity extends PreferenceActivity {
	
	private boolean restartRequired = false;
	private Class caller = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		caller = (Class) getIntent().getExtras().getSerializable("activity");
		
		//Inflate preference screen
		addPreferencesFromResource(R.xml.quran_preferences);
		CheckBoxPreference arabicPreference = (CheckBoxPreference) findPreference(getResources().getString(R.string.prefs_use_arabic_names));
		arabicPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener(){

			@Override
			public boolean onPreferenceChange(Preference preference,
					Object newValue) {
				restartRequired = true;
				boolean isArabic = (Boolean)newValue;

				Locale lang = (isArabic? new Locale("ar") : Locale.getDefault());
				Locale.setDefault(lang);
				Configuration config = new Configuration();
				config.locale = lang;
				getBaseContext().getResources().updateConfiguration(config,
				      getBaseContext().getResources().getDisplayMetrics());
				
				return true;
			}
		});
	}

	@Override
	protected void onDestroy() {
		if (restartRequired && caller != null) {
			Intent i = new Intent(this, caller);
			i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(i);
		}
		super.onDestroy();
	}
}
