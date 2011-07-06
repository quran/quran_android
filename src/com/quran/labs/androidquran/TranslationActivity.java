package com.quran.labs.androidquran;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.Window;
import android.view.WindowManager;

import com.quran.labs.androidquran.common.PageViewQuranActivity;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.common.TranslationPageFeeder;
import com.quran.labs.androidquran.common.TranslationsDBAdapter;
import com.quran.labs.androidquran.util.BookmarksManager;
import com.quran.labs.androidquran.util.QuranSettings;

public class TranslationActivity extends PageViewQuranActivity {

	private static final String TAG = "TranslationViewActivity";
    private TranslationsDBAdapter dba;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {		
		Object [] saved = (Object []) getLastNonConfigurationInstance();
		if (saved != null) {
			Log.d("exp_v", "Adapter retrieved..");
			quranPageFeeder = (TranslationPageFeeder) saved[0];
		} 
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		// does requestWindowFeature, has to be before setContentView
		adjustDisplaySettings();

		setContentView(R.layout.quran_exp);
		
		dba = new TranslationsDBAdapter(this);
		checkTranslationAvailability();
		
		WindowManager manager = getWindowManager();
		Display display = manager.getDefaultDisplay();
		width = display.getWidth();

		initComponents();		
		BookmarksManager.load(prefs);
		
		int page = loadPageState(savedInstanceState);
		quranPageFeeder.jumpToPage(page);
		//renderPage(ApplicationConstants.PAGES_LAST - page);

		toggleMode();
	}
	

	@Override
	public Object onRetainNonConfigurationInstance() {
		Object [] o = { quranPageFeeder };
		return o;
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		// Always initialize Quran Screen on start so as to be able to retrieve images
		// Error cause: Gallery Adapter was unable to retrieve images from SDCard as QuranScreenInfo
		// was cleared after long sleep..
		initializeQuranScreen();
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
	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt("lastPage", QuranSettings.getInstance().getLastPage());
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onResume() {
		super.onResume();
		checkTranslationAvailability();
		expLayout.setKeepScreenOn(QuranSettings.getInstance().isKeepScreenOn());
		Log.d("QuranAndroid", "Screen on");
		adjustActivityOrientation();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		expLayout.setKeepScreenOn(false);
		Log.d("QuranAndroid","Screen off");
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
}
