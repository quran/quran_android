package com.quran.labs.androidquran;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.quran.labs.androidquran.data.Constants;

import java.util.Locale;

public class QuranPreferenceActivity extends SherlockPreferenceActivity {
	
	private boolean mInitiallyIsArabic = false;
   private boolean mIsArabic = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
      setTheme(R.style.Theme_Sherlock);
		super.onCreate(savedInstanceState);

		// add preferences
		addPreferencesFromResource(R.xml.quran_preferences);

      // special handling for the arabic checkbox
		CheckBoxPreference arabicPreference = (CheckBoxPreference)
              findPreference(Constants.PREF_USE_ARABIC_NAMES);
      mInitiallyIsArabic = arabicPreference.isChecked();
      mIsArabic = mInitiallyIsArabic;
		arabicPreference.setOnPreferenceChangeListener(
              new OnPreferenceChangeListener(){
			@Override
			public boolean onPreferenceChange(Preference preference,
					Object newValue) {
				boolean isArabic = (Boolean)newValue;
            mIsArabic = isArabic;

            Locale lang = (isArabic? new Locale("ar") :
                    Resources.getSystem().getConfiguration().locale);
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
	protected void onPause() {
		if (mIsArabic != mInitiallyIsArabic) {
			Intent i = new Intent(this,
                 com.quran.labs.androidquran.ui.QuranActivity.class);
			i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(i);
		}
		super.onPause();
	}
}
