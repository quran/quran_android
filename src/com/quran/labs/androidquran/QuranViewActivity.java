package com.quran.labs.androidquran;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.common.AyahStateListener;
import com.quran.labs.androidquran.common.PageViewQuranActivity;
import com.quran.labs.androidquran.common.QuranPageFeeder;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.AudioServiceBinder;
import com.quran.labs.androidquran.service.QuranAudioService;
import com.quran.labs.androidquran.util.BookmarksManager;
import com.quran.labs.androidquran.util.QuranAudioLibrary;
import com.quran.labs.androidquran.util.QuranSettings;

public class QuranViewActivity extends PageViewQuranActivity implements AyahStateListener {
	private static final String TAG = "QuranViewActivity";
	
	private boolean bounded = false;
	private Integer[] pageBounds = null;
	private AudioServiceBinder quranAudioPlayer = null;
	
	private ServiceConnection conn = new ServiceConnection() {						
		@Override
		public void onServiceDisconnected(ComponentName name) {
			unBindAudioService();
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {							
			quranAudioPlayer = (AudioServiceBinder) service;
			quranAudioPlayer.setAyahCompleteListener(QuranViewActivity.this);
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Object [] saved = (Object []) getLastNonConfigurationInstance();
		if (saved != null) {
			Log.d("exp_v", "Adapter retrieved..");
			quranPageFeeder = (QuranPageFeeder) saved[0];
		} 
		super.onCreate(savedInstanceState);
		
		bindAudioService();
		
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		// does requestWindowFeature, has to be before setContentView
		adjustDisplaySettings();

		setContentView(R.layout.quran_exp);
		
		WindowManager manager = getWindowManager();
		Display display = manager.getDefaultDisplay();
		width = display.getWidth();

		initComponents();
		btnPlay.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				pageBounds = QuranInfo.getPageBounds(quranPageFeeder.getCurrentPagePosition());
				AyahItem i = QuranAudioLibrary.getAyahItem(getApplicationContext(), pageBounds[0], pageBounds[1], 1);
				quranAudioPlayer.enableRemotePlay(true);
				quranAudioPlayer.play(i);
			}
		});
		
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

	protected void initQuranPageFeeder(){
		if (quranPageFeeder == null) {
			Log.d(TAG, "Quran Feeder instantiated...");
			quranPageFeeder = new QuranPageFeeder(this, quranPageCurler, R.layout.quran_page_layout);
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
	
	private void unBindAudioService(){
		if (bounded) {
	        // Detach our existing connection.
	        unbindService(conn);
	        if(quranAudioPlayer != null)
	        	quranAudioPlayer.setAyahCompleteListener(null);
	        bounded = false;
	    }
	}
	
	private void bindAudioService(){
		if (!bounded){
			Intent serviceIntent = new Intent(getApplicationContext(), QuranAudioService.class);
			startService(serviceIntent);
			bounded = bindService(serviceIntent, conn, BIND_AUTO_CREATE);
			if(!bounded)
				Toast.makeText(this, "can not bind service", Toast.LENGTH_SHORT);
		}
	}

	@Override
	public void onComplete(AyahItem ayah, AyahItem nextAyah) {
		if (pageBounds != null && ayah.getSoura() == pageBounds[2] && ayah.getAyah() == pageBounds[3]) {
			pageBounds = QuranInfo.getPageBounds(quranPageFeeder.getCurrentPagePosition() + 1);
			quranPageFeeder.goToNextpage();
		}
		
	}

	@Override
	public void onNotFound(AyahItem ayah) {
		
	}
}
