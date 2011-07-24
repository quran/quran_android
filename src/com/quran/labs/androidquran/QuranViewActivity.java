package com.quran.labs.androidquran;

import java.util.HashMap;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.markupartist.android.widget.ActionBar.IntentAction;
import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.common.AyahStateListener;
import com.quran.labs.androidquran.common.PageViewQuranActivity;
import com.quran.labs.androidquran.common.QuranPageFeeder;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.AudioServiceBinder;
import com.quran.labs.androidquran.service.QuranAudioService;
import com.quran.labs.androidquran.util.QuranAudioLibrary;
import com.quran.labs.androidquran.util.QuranUtils;

public class QuranViewActivity extends PageViewQuranActivity implements AyahStateListener {

	protected static final String ACTION_NEXT = "ACTION_NEXT";
	protected static final String ACTION_PAUSE = "ACTION_PAUSE";
	protected static final String ACTION_PLAY = "ACTION_PLAY";

	private static final String TAG = "QuranViewActivity";
	
	private boolean bounded = false;
	private AudioServiceBinder quranAudioPlayer = null;
	
	private static final int ACTION_BAR_ACTION_PLAY = 0;
	private static final int ACTION_BAR_ACTION_PAUSE = 1;
	private static final int ACTION_BAR_ACTION_STOP = 2;
	private static final int ACTION_BAR_ACTION_NEXT = 3;
	
	private AyahItem lastAyah;
	
	HashMap<String, IntentAction> actionBarActions = new HashMap<String, IntentAction>();
	
//	private TextView textView;
	
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
//		textView = new TextView(this);
//		textView.setText("");
		bindAudioService();
		
		btnPlay.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Integer[] pageBounds = QuranInfo.getPageBounds(quranPageFeeder.getCurrentPagePosition());
				AyahItem i = QuranAudioLibrary.getAyahItem(getApplicationContext(), pageBounds[0], pageBounds[1], 2);
				quranAudioPlayer.enableRemotePlay(true);
				quranAudioPlayer.play(i);
			}
		});
	}
	
	protected void addActions(){
		super.addActions();
		if(actionBar != null){
			//actionBar.setTitle("QuranAndroid");
			actionBarActions.put("ACTION_PLAY",
					getIntentAction("ACTION_PLAY", android.R.drawable.ic_media_play));
			actionBarActions.put("ACTION_PAUSE", 
					getIntentAction("ACTION_PAUSE", android.R.drawable.ic_media_pause));
			actionBarActions.put("ACTION_NEXT",
					getIntentAction("ACTION_NEXT", 
							android.R.drawable.ic_media_next));
			actionBarActions.put("ACTION_STOP", 
					getIntentAction("ACTION_STOP", R.drawable.stop));
			
			actionBar.addAction(actionBarActions.get("ACTION_PLAY"), 
					ACTION_BAR_ACTION_PLAY);
			actionBar.addAction(actionBarActions.get("ACTION_PAUSE"), 
					ACTION_BAR_ACTION_PAUSE);
			actionBar.addAction(actionBarActions.get("ACTION_STOP"),
					ACTION_BAR_ACTION_STOP);
			actionBar.addAction(actionBarActions.get("ACTION_NEXT"),
					ACTION_BAR_ACTION_NEXT);	
		}		
	}
	
	private IntentAction getIntentAction(String intentAction, int drawable){
		 	Intent i =  new Intent(this, QuranViewActivity.class); 
	        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
	        i.setAction(intentAction);
	        IntentAction action = new IntentAction(this, i, drawable);
	        return action;
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		// TODO Auto-generated method stub
		super.onNewIntent(intent);
		String action = intent.getAction();
		if(quranAudioPlayer != null && action != null){
			if(action.equalsIgnoreCase("ACTION_PLAY")){
				if(quranAudioPlayer.isPaused())
					quranAudioPlayer.resume();
				else{
					Integer[] pageBounds = QuranInfo.getPageBounds(quranPageFeeder.getCurrentPagePosition());
					final AyahItem i = QuranAudioLibrary.getAyahItem(getApplicationContext(), pageBounds[0], pageBounds[1], 2);
					// soura not totall found
					if(QuranUtils.isSouraAudioFound(i.getQuranReaderId(), i.getSoura()) < 0){
						showDownloadDialog(i);
					}else{
						quranAudioPlayer.enableRemotePlay(false);
						playAudio(i);
					}
				}
			}else if(action.equalsIgnoreCase("ACTION_PAUSE")){
				quranAudioPlayer.pause();
			}else if(action.equalsIgnoreCase("ACTION_NEXT")){
				AyahItem ayah = QuranAudioLibrary.getNextAyahAudioItem(this,
						quranAudioPlayer.getCurrentAyah());
				quranAudioPlayer.play(ayah);
			}else if (action.equalsIgnoreCase("ACTION_STOP")){
				lastAyah = null;
				quranAudioPlayer.stop();
			}
		}
	}
	
	private void showDownloadDialog(final AyahItem i) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Do you want to download sura");
		builder.setPositiveButton("download", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				downloadSura(i.getQuranReaderId(), i.getSoura());
			}
		});
		builder.setNeutralButton("Stream", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				quranAudioPlayer.enableRemotePlay(true);
				quranAudioPlayer.play(i);
				dialog.dismiss();
			}
		});
		
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		
		builder.show();
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

	private void playAudio(AyahItem ayah){
		if(quranAudioPlayer != null){
			if (ayah == null) {
				Integer[] pageBounds = QuranInfo.getPageBounds(quranPageFeeder.getCurrentPagePosition());
				ayah = QuranAudioLibrary.getAyahItem(getApplicationContext(), pageBounds[0], pageBounds[1], 2);
			}
			quranAudioPlayer.play(ayah);
		}
	}
	
	@Override
	public void onComplete(AyahItem ayah, AyahItem nextAyah) {
		//String text = "Page(" + QuranInfo.getPageFromSuraAyah(nextAyah.getSoura(), nextAyah.getAyah()) + ")" + System.getProperty("line.separator");
		//text += "Soura: " + QuranInfo.getSuraName(nextAyah.getSoura()-1) + System.getProperty("line.separator");
		//text += "Ayah: " + nextAyah.getAyah() + System.getProperty("line.separator");
		//textView.setText(text);
		lastAyah = ayah;
		int page = QuranInfo.getPageFromSuraAyah(nextAyah.getSoura(), nextAyah.getAyah());
		quranPageFeeder.jumpToPage(page);
	}

	@Override
	public void onNotFound(AyahItem ayah) {
		lastAyah = ayah;
		showDownloadDialog(ayah);
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
	
	@Override
	protected void onFinishDownload() {
		super.onFinishDownload();
		if (quranAudioPlayer != null) {
			quranAudioPlayer.enableRemotePlay(false);
			playAudio(lastAyah);
		}
	}
	
}
