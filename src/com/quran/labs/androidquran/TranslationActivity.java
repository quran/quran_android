package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.os.Bundle;
import android.text.Html;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.widget.TextView;

import com.quran.labs.androidquran.common.GestureQuranActivity;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.common.TranslationsDBAdapter;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.DatabaseHandler;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.QuranSettings;

public class TranslationActivity extends GestureQuranActivity {

	private int page = 1;
    private TextView txtTranslation;
    private TranslationsDBAdapter dba;
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.quran_translation);
		txtTranslation = (TextView) findViewById(R.id.translationText);
		dba = new TranslationsDBAdapter(getApplicationContext());
		loadPageState(savedInstanceState);
		gestureDetector = new GestureDetector(new QuranGestureDetector());
		adjustTextSize();
		renderTranslation();
	}
	
	private void adjustTextSize() {
		QuranSettings.load(prefs);
		txtTranslation.setTextSize(QuranSettings.getInstance().getTranslationTextSize());
	}
	
	@Override
	protected void onResume() {
		adjustTextSize();
		renderTranslation();
		super.onResume();
	}

	public void goToNextPage(){
		if (page < ApplicationConstants.PAGES_LAST){
			page++;
			renderTranslation();
		}	
	}
	
	public void goToPreviousPage(){
		if (page > ApplicationConstants.PAGES_FIRST){
			page--;
			renderTranslation();
		}	
	}
	
	public void loadPageState(Bundle savedInstanceState){
		page = savedInstanceState != null ? savedInstanceState.getInt("page") : QuranSettings.getInstance().getLastPage();
		if (page == ApplicationConstants.PAGES_FIRST){
			Bundle extras = getIntent().getExtras();
			page = extras != null? extras.getInt("page") : ApplicationConstants.PAGES_FIRST;
		} else if (page == ApplicationConstants.NO_PAGE_SAVED) {
			page = ApplicationConstants.PAGES_FIRST;
		}
		return;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT){
			goToNextPage();
		}
		else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT){
			goToPreviousPage();
		}
		else if (keyCode == KeyEvent.KEYCODE_BACK){
			goBack();
		}

		return super.onKeyDown(keyCode, event);
	}

	public void renderTranslation(){
		if ((page > ApplicationConstants.PAGES_LAST) || (page < ApplicationConstants.PAGES_FIRST)) page = 1;
		setTitle(QuranInfo.getPageTitle(page));

		Integer[] bounds = QuranInfo.getPageBounds(page);
		
		//TranslationItem[] translationLists = dba.getAvailableTranslations(true);
		TranslationItem[] translationLists = new TranslationItem[]{dba.getActiveTranslation()};
		List<String> unavailable = new ArrayList<String>();
		
		int available = 0;
		List<Map<String, String>> translations = new ArrayList<Map<String, String>>();
		for (TranslationItem tl : translationLists){
			Map<String, String> currentTranslation = getVerses(tl.getFileName(), bounds);
			if (currentTranslation != null){
				translations.add(currentTranslation);
				available++;
			}
			else {
				unavailable.add(tl.getDisplayName());
				translations.add(null);
			}
		}
		
		TextView translationArea = (TextView)findViewById(R.id.translationText);
		translationArea.setText("");
		
		if (available == 0){
			promptForTranslationDownload(unavailable);
			translationArea.setText(R.string.translationsNeeded);
			return;
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
						String str = "<b>" + key + ":</b> " + text + "<br>";
						translationArea.append(Html.fromHtml(str));
					}
				}
				if (numAdded == 0) break;
			}
		}
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
	
	public void goBack(){
		Intent i = new Intent();
		i.putExtra("page", page);
		setResult(RESULT_OK, i);
		finish();	
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState){
		super.onSaveInstanceState(outState);
		outState.putInt("page", page);
	}
	
	public void promptForTranslationDownload(final List<String> translationsToGet){
    	AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    	dialog.setMessage(R.string.downloadTranslationPrompt);
    	dialog.setCancelable(false);
    	dialog.setPositiveButton(R.string.downloadPrompt_ok,
			new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
					TranslationActivity.this.finish();
					Intent intent = new Intent(getApplicationContext(), DownloadActivity.class);
					startActivity(intent);
				}
    	});
    	
    	dialog.setNegativeButton(R.string.downloadPrompt_no, 
    			new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int id) {
    					dialog.cancel();
    					goBack();
    				}
    	});
    	
    	AlertDialog alert = dialog.create();
    	alert.setTitle(R.string.downloadPrompt_title);
    	alert.show();
	}
}
