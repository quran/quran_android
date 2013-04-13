package com.quran.labs.androidquran;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;

import android.util.Log;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.StorageUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class QuranPreferenceActivity extends SherlockPreferenceActivity {

	private boolean mInitiallyIsArabic = false;
    private boolean mIsArabic = false;
    private ListPreference lstStorageOptions;
    private MoveFilesAsyncTask moveFilesTask;
    private List<StorageUtils.Storage> storageList;

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
		arabicPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener(){
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


        lstStorageOptions = (ListPreference) findPreference(getString(R.string.prefs_app_location));

        CheckBoxPreference chkCustomLocation = (CheckBoxPreference)
                findPreference(getString(R.string.prefs_use_custom_location));

        Preference advancedPrefs = findPreference(getString(R.string.prefs_advanced_settings));
        advancedPrefs.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                loadStorageOptions();
                return false;
            }
        });
	}

    private void loadStorageOptions() {
        try {
            String msg = lstStorageOptions.getSummary().toString();
            final int appSize = QuranFileUtils.getAppUsedSpace(this);
            msg += "\n" + getString(R.string.prefs_app_size) + " " + appSize
                    + " " + getString(R.string.prefs_megabytes);
            lstStorageOptions.setSummary(msg);
            storageList = StorageUtils.getAllStorageLocations(getBaseContext());
            CharSequence[] values = new CharSequence[storageList.size()];
            CharSequence[] displayNames = new CharSequence[storageList.size()];
            int i = 0;
            final HashMap<String, Integer> storageEmptySpaceMap = new HashMap<String, Integer>(storageList.size());
            for (StorageUtils.Storage storage: storageList) {
                values[i] = storage.getMountPoint();
                displayNames[i] = storage.getLabel() + " " + storage.getFreeSpace()
                        + " " + getString(R.string.prefs_megabytes);
                i++;
                storageEmptySpaceMap.put(storage.getMountPoint(), storage.getFreeSpace());
            }

            lstStorageOptions.setEntries(displayNames);
            lstStorageOptions.setEntryValues(values);

            lstStorageOptions.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String newLocation = (String) newValue;
                    if (appSize < storageEmptySpaceMap.get(newLocation)) {
                        moveFilesTask = new MoveFilesAsyncTask(newLocation);
                        moveFilesTask.execute();
                    } else {
                        Toast.makeText(QuranPreferenceActivity.this,
                                getString(R.string.prefs_no_enough_space_to_move_files), Toast.LENGTH_LONG);
                    }
                    return false;
                }
            });
        } catch(Exception e) {
            Log.e("QPA", "error loading storage options", e);
        }
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

    @Override
    public void onBackPressed() {
        // disable back press while task in progress
        if (moveFilesTask == null)
            super.onBackPressed();
    }

    private class MoveFilesAsyncTask extends AsyncTask<Void, Void, Boolean> {

        private String newLocation;
        private ProgressDialog dialog;

        private MoveFilesAsyncTask(String newLocation) {
            this.newLocation = newLocation;
        }

        @Override
        protected void onPreExecute() {
            dialog = new ProgressDialog(QuranPreferenceActivity.this);
            dialog.setMessage(getString(R.string.prefs_copying_app_files));
            dialog.setCancelable(false);
            dialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            return QuranFileUtils.moveAppFiles(QuranPreferenceActivity.this, newLocation);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            dialog.dismiss();
            if (result) {
                QuranSettings.setAppCustomLocation(QuranPreferenceActivity.this, newLocation);
            } else {
                Toast.makeText(QuranPreferenceActivity.this,
                        getString(R.string.prefs_err_moving_app_files), Toast.LENGTH_LONG).show();
            }
            dialog = null;
            moveFilesTask = null;
        }
    }
}
