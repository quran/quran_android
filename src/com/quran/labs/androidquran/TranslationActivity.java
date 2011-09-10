package com.quran.labs.androidquran;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;

import com.quran.labs.androidquran.common.PageViewQuranActivity;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.common.TranslationPageFeeder;
import com.quran.labs.androidquran.common.TranslationsDBAdapter;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.util.QuranSettings;

public class TranslationActivity extends PageViewQuranActivity {

	private static final String TAG = "TranslationViewActivity";
	protected static final int DOWNLOAD_TRANSLATION_CODE = 0;
    private TranslationsDBAdapter dba;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		dba = new TranslationsDBAdapter(this);
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.menu_item_translations).setVisible(false);
		return super.onPrepareOptionsMenu(menu);
	}
	
	@Override
	protected void initQuranPageFeeder(){
		if (quranPageFeeder == null) {
			Log.d(TAG, "Quran Feeder instantiated...");
			quranPageFeeder = new TranslationPageFeeder(this, quranPageCurler,
					R.layout.quran_translation, dba);
		} else {
			quranPageFeeder.setContext(this, quranPageCurler);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		checkTranslationAvailability();
	}
	
	@Override
	public void onBackPressed() {
		Intent data = new Intent();
		data.putExtra("page", quranPageFeeder.getCurrentPagePosition());
		setResult(RESULT_OK, data);
		super.onBackPressed();
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
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == DOWNLOAD_TRANSLATION_CODE && resultCode == RESULT_OK) {
			dba.refresh();
			int page = QuranSettings.getInstance().getLastPage();
			page = page == ApplicationConstants.NO_PAGE_SAVED ? ApplicationConstants.PAGES_FIRST : page;
			quranPageFeeder.jumpToPage(page);
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
					startActivityForResult(intent, DOWNLOAD_TRANSLATION_CODE);
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


	@Override
	protected void loadLastNonConfigurationInstance() {
		super.loadLastNonConfigurationInstance();
		Object [] saved = (Object []) getLastNonConfigurationInstance();
		if (saved != null) {
			Log.d("exp_v", "Adapter retrieved..");
			quranPageFeeder = (TranslationPageFeeder) saved[0];
		} 
	}
}
