package com.quran.labs.androidquran;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class QuranPreferenceActivity extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//Inflate preference screen
		addPreferencesFromResource(R.xml.quran_preferences);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
	
}
