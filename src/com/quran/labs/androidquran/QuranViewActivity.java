package com.quran.labs.androidquran;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.common.AyahStateListener;
import com.quran.labs.androidquran.common.PageViewQuranActivity;
import com.quran.labs.androidquran.common.QuranPageFeeder;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.AudioServiceBinder;
import com.quran.labs.androidquran.service.QuranAudioService;
import com.quran.labs.androidquran.util.QuranAudioLibrary;

public class QuranViewActivity extends PageViewQuranActivity implements AyahStateListener {
	private static final String TAG = "QuranViewActivity";
	
	private boolean bounded = false;
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
		super.onCreate(savedInstanceState);
		bindAudioService();
		
		btnPlay.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Integer[] pageBounds = QuranInfo.getPageBounds(quranPageFeeder.getCurrentPagePosition());
				AyahItem i = QuranAudioLibrary.getAyahItem(getApplicationContext(), pageBounds[0], pageBounds[1], 1);
				quranAudioPlayer.enableRemotePlay(true);
				quranAudioPlayer.play(i);
			}
		});
	}
	
	protected void initQuranPageFeeder(){
		if (quranPageFeeder == null) {
			Log.d(TAG, "Quran Feeder instantiated...");
			quranPageFeeder = new QuranPageFeeder(this, quranPageCurler, R.layout.quran_page_layout);
		} else {
			quranPageFeeder.setContext(this, quranPageCurler);
		}
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
		int page = QuranInfo.getPageFromSuraAyah(nextAyah.getSoura(), nextAyah.getAyah());
		quranPageFeeder.jumpToPage(page);
	}

	@Override
	public void onNotFound(AyahItem ayah) {
		
	}

	@Override
	protected void loadLastNonConfigurationInstance() {
		super.loadLastNonConfigurationInstance();
		Object [] saved = (Object []) getLastNonConfigurationInstance();
		if (saved != null) {
			Log.d("exp_v", "Adapter retrieved..");
			quranPageFeeder = (QuranPageFeeder) saved[0];
		}
	}
}
