package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.common.TranslationsDBAdapter;

public class SelectTranslationPreference extends ListPreference {

	public SelectTranslationPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		TranslationsDBAdapter tdba = new TranslationsDBAdapter(context);
		TranslationItem[] items = tdba.getAvailableTranslations();
		String[] entries = new String[items.length];
		String[] values = new String[items.length];
		for(int i = 0; i < items.length; i++) {
			entries[i] = items[i].getDisplayName();
			values[i] = items[i].getFileName();
		}
		
		// there is no available translations
		if (items.length == 0) {
			setEnabled(items.length > 0);
			setSummary("No translations installed");
		}

		setEntries(entries);
		setEntryValues(values);
	}
	
	public SelectTranslationPreference(Context context) {
		super(context, null);
	}	

}
