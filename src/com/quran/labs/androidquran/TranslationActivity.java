package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.quran.labs.androidquran.common.GestureQuranActivity;
import com.quran.labs.androidquran.common.QuranGalleryAdapter;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.common.TranslationsDBAdapter;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.DatabaseHandler;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.GalleryFriendlyScrollView;

public class TranslationActivity extends GestureQuranActivity {

    private TranslationsDBAdapter dba;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		dba = new TranslationsDBAdapter(this);
		super.onCreate(savedInstanceState);
		checkTranslationAvailability();
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.menu_item_translations).setVisible(false);
		return super.onPrepareOptionsMenu(menu);
	}
	
	private boolean checkTranslationAvailability() {
		Log.d("QuranAndroid", "checking translations");
		TranslationItem[] translationLists;
		TranslationItem activeTranslation = dba.getActiveTranslation();
		if (activeTranslation == null){
			translationLists = dba.getAvailableTranslations();
			if (translationLists.length > 0) {
				activeTranslation = translationLists[0];
				QuranSettings.getInstance().setActiveTranslation(activeTranslation.getFileName());
				QuranSettings.save(prefs);
			} else {
				promptForTranslationDownload();
				return false;
			}
		}
		
		return true;
	}

	public String getTranslation(int page){
		Log.d("QuranAndroid", "get translation for page " + page);
		String translation = "";
		if ((page > ApplicationConstants.PAGES_LAST) || (page < ApplicationConstants.PAGES_FIRST)) page = 1;
		setTitle(QuranInfo.getPageTitle(page));

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
	
	public Map<String, String> getVerses(String translation, Integer[] bounds){
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
	
	public void promptForTranslationDownload(){
    	AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    	dialog.setMessage(R.string.downloadTranslationPrompt);
    	dialog.setCancelable(false);
    	dialog.setPositiveButton(R.string.downloadPrompt_ok,
			new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
					Intent intent = new Intent(getApplicationContext(), DownloadActivity.class);
					startActivity(intent);
				}
    	});
    	
    	dialog.setNegativeButton(R.string.downloadPrompt_no, 
    			new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int id) {
    					dialog.dismiss();
    					finish();
    				}
    	});
    	
    	AlertDialog alert = dialog.create();
    	alert.setTitle(R.string.downloadPrompt_title);
    	alert.show();
	}
	
	public class QuranGalleryTranslationAdapter extends QuranGalleryAdapter {
		private Map<String, String> cache = new HashMap<String, String>();
		
	    public QuranGalleryTranslationAdapter(Context context) {
	    	super(context);
	    }
	    
	    @Override
	    public void emptyCache() {
	    	super.emptyCache();
	    	cache.clear();
	    }

	    public View getView(int position, View convertView, ViewGroup parent) {
	    	PageHolder holder;
	    	if (convertView == null){
	    		convertView = mInflater.inflate(R.layout.quran_translation, null);
				holder = new PageHolder();
				holder.page = (TextView)convertView.findViewById(R.id.translationText);
				holder.scroll = (GalleryFriendlyScrollView)convertView.findViewById(R.id.pageScrollView);
				convertView.setTag(holder);
	    	}
	    	else {
	    		holder = (PageHolder)convertView.getTag();
	    	}
	    	
	        int page = ApplicationConstants.PAGES_LAST - position;
	        String str = null;
	        if (cache.containsKey("page_" + page)){
	        	str = cache.get("page_" + page);
	        	Log.d("exp_v", "reading translation for page " + page + " from cache!");
	        }
	        
	        if (str == null){
	        	str = getTranslation(page);
	        	cache.put("page_" + page, str);
	        }
	        
	        holder.page.setText(Html.fromHtml(str));
	        holder.page.setTextSize(QuranSettings.getInstance().getTranslationTextSize());
			QuranSettings.getInstance().setLastPage(page);
			QuranSettings.save(prefs);
			
			if (!inReadingMode)
				updatePageInfo(position);
			//adjustBookmarkView();
	    	return convertView;
	    }
	}
	
	static class PageHolder {
		TextView page;
		ScrollView scroll;
	}
	
	@Override
	protected void initGalleryAdapter() {
		galleryAdapter = new QuranGalleryTranslationAdapter(this);
	}
	
}
