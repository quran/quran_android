package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.common.TranslationsDBAdapter;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

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
		
		setEntries(entries);
		setEntryValues(values);
	}
	
	public SelectTranslationPreference(Context context) {
		super(context, null);
	}	

}
