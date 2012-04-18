package com.quran.labs.androidquran.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.database.Cursor;
import android.database.SQLException;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.TranslationActivity;
import com.quran.labs.androidquran.data.DatabaseHandler;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.QuranPageCurlView;
import com.quran.labs.androidquran.widgets.QuranPageCurlView.OnPageFlipListener;

public class TranslationPageFeeder extends QuranPageFeeder
	implements OnPageFlipListener {
	
	private static final String TAG = "TranslationPageFeeder";
	
    private TranslationsDBAdapter dba;
	private Map<String, String> cache = new HashMap<String, String>();
	
	public TranslationPageFeeder(TranslationActivity context,
			QuranPageCurlView quranPage, int page_layout,
			TranslationsDBAdapter dba) {
		super(context, quranPage, page_layout);
		this.dba = dba;
	}
	
	@Override
	protected View createPage(int index) {
		View v = mInflater.inflate(mPageLayout, null);
		
		ScrollView sv = (ScrollView)v.findViewById(R.id.page_scroller);
		if (sv == null)
			v.setTag(new Boolean(false));
		else
			v.setTag(new Boolean(true));
		
		String text = null;
		if (cache.containsKey("page_" + index))
        	text = cache.get("page_" + index);
		if (text == null){
			text = getTranslation(index);
        	if (text != null && !"".equals(text))
        		cache.put("page_" + index, text);
		}
		
		TextView tv = (TextView)v.findViewById(R.id.translationText);
		tv.setText(Html.fromHtml(ArabicStyle.reshape(text)));
        tv.setTextSize(QuranSettings.getInstance().getTranslationTextSize());
		
		updateViewForUser(v, false, false);
		
        return v;
	}
	
	@Override
	protected void updateViewForUser(View v, boolean loading,
			boolean pageNotFound){
		TextView tv = (TextView)v.findViewById(R.id.translationText);
		Log.d(TAG, "text: " + tv.getText().toString());
        tv.setVisibility(View.VISIBLE);
	}
	
	private String getTranslation(int page){
		if (dba.getActiveTranslation() == null)
			return "";
		
		String translation = "";
		Integer[] bounds = QuranInfo.getPageBounds(page);
		TranslationItem[] translationLists = {dba.getActiveTranslation()};
		
		List<Map<String, String>> translations = new ArrayList<Map<String, String>>();
		for (TranslationItem tl : translationLists){
			Map<String, String> currentTranslation = getVerses(tl.getFileName(), bounds);
			if (currentTranslation != null){
				translations.add(currentTranslation);
			}
		}
		
		int numTranslations = translationLists.length;
		
		int i = bounds[0];
		for (; i <= bounds[2]; i++){
			int j = (i == bounds[0])? bounds[1] : 1;
			for (;;){
				int numAdded = 0;
				String key = i + ":" + j++;
				for (int t = 0; t < numTranslations; t++){
					if (translations.get(t) == null) continue;
					String text = translations.get(t).get(key);
					if (text != null){
						numAdded++; 
						translation += "<b>" + key + ":</b> " + text + "<br/>";
					}
				}
				if (numAdded == 0) break;
			}
		}
		
		return translation;
	}
	
	private Map<String, String> getVerses(String translation, Integer[] bounds){
		DatabaseHandler handler = null;
		try {
			Map<String, String> ayahs = new HashMap<String, String>();
			handler = new DatabaseHandler(translation);
			for (int i = bounds[0]; i <= bounds[2]; i++){
				int max = (i == bounds[2])? bounds[3] : QuranInfo.getNumAyahs(i);
				int min = (i == bounds[0])? bounds[1] : 1;
				Cursor res = handler.getVerses(i, min, max);
				if ((res == null) || (!res.moveToFirst())) continue;
				do {
					int sura = res.getInt(0);
					int ayah = res.getInt(1);
					String text = res.getString(2);
					ayahs.put(sura + ":" + ayah, text);
				}
				while (res.moveToNext());
				res.close();
			}
			handler.closeDatabase();
			return ayahs;
		}
		catch (SQLException ex){
			ex.printStackTrace();
			if (handler != null) handler.closeDatabase();
			return null;
		}
	}
	
	@Override
	public void onShortClick(float x, float y) {
		// Do nothing
	}
	
	@Override
	public void onLongClick(float x, float y) {
		// Do nothing
	}
}
