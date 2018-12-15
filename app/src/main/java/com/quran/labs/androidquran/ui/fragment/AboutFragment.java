package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.BuildConfig;
import com.quran.labs.androidquran.R;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;

public class AboutFragment extends PreferenceFragment {

  private static final String[] sImagePrefKeys =
      new String[] { "madaniImages", "naskhImages", "qaloonImages" };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.about);

    String flavor = BuildConfig.FLAVOR + "Images";
    PreferenceCategory parent = (PreferenceCategory) findPreference("aboutDataSources");
    for (String string : sImagePrefKeys) {
      if (!string.equals(flavor)) {
        Preference pref = findPreference(string);
        parent.removePreference(pref);
      }
    }
  }
}
