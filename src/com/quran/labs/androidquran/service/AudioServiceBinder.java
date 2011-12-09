package com.quran.labs.androidquran.service;

import java.io.IOException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Binder;
import android.util.Log;

import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.QuranViewActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.common.AyahStateListener;
import com.quran.labs.androidquran.common.IAudioPlayer;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.QuranAudioLibrary;

public class AudioServiceBinder extends Binder implements  
	OnCompletionListener, IAudioPlayer, OnPreparedListener, OnErrorListener{
	
	private MediaPlayer mp = null;
	private Context context;
	private boolean paused = false;	
	private boolean remotePlayEnabled = false;
	private AyahItem currentItem;
	private AyahStateListener ayahListener = null;
	private boolean notified;
	//private boolean stopped = true;
	private boolean stopped = true;
	private boolean preparing = false;
	
	private int numberOfRepeats = 0;	
	
	private int errorCount = 0;
	private int numberOfTries = 3;
	private String url;
	
	private int repeats = 0;
	
	public int getNumberOfRepeats() {
		return numberOfRepeats;
	}



	public void setNumberOfRepeats(int numberOfRepeats) {
		this.numberOfRepeats = numberOfRepeats;
	}



	public synchronized void setAyahCompleteListener(AyahStateListener ayahListener) {
		this.ayahListener = ayahListener;
	}

	public AudioServiceBinder(Context context){
		this.context = context;
	}
	

	public synchronized void stop() {
		// reset enable remote play to false
		enableRemotePlay(false);
		if(mp != null && mp.isPlaying()){
			try{
			mp.stop();
			mp.release();
			mp = null;
			}catch (Exception e) {
				Log.d("AudioServiceBinder:stop",
						"Exception on calling media player stop " + e.toString() + " " + e.getMessage());
			}
		}
		paused = false;	
		stopped = true;
		currentItem = null;
		clearNotification();
	}

	
	private void clearNotification(){
		NotificationManager mgr = 
			(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		 mgr.cancel(ApplicationConstants.NOTIFICATION_AUDIO);
	}
	/* (non-Javadoc)
	 * @see org.islam.quran.IAudioPlayer#play(org.islam.quran.AyahAudioItem)
	 */
	public synchronized void play(AyahItem item) {
		Log.d("Play", "1 playing ayah " + item.getAyah());
		stopped = false;
		paused = false;
		// eclipse says this is dead code
		// if (item == null)
		//	  return;
		this.currentItem = item;
		if(mp != null && mp.isPlaying()){
			mp.stop();
//			try{
//				mp.release();
//			}catch(Exception e){}
//			mp = null;
		}
		try {
			if(mp == null)
				mp = new MediaPlayer();			
			mp.reset();			
			mp.setOnCompletionListener(this);
			url = null;
			// FIXME Even though soura is downloaded, Basmallah of every sura asks for download since Al-Fati7a isn't downloaded -AF
			if(item.isAudioFoundLocally())
				url = item.getLocalAudioUrl();
			else if(remotePlayEnabled) {
				url = item.getRemoteAudioUrl();
				Log.d("quran_audio", url);
			}else{
				if(ayahListener != null){
					clearNotification();
					ayahListener.onAyahNotFound(item);
				}
				else
				{
					// TODO show notification ayah not found, download it or play remotely	
				}
			}	
			if(url != null){
				mp.setDataSource(url);
				mp.setOnPreparedListener(this);
				mp.setOnErrorListener(this);
				preparing = true;
				mp.prepareAsync();				
//				mp.prepare();
//				mp.start();
//				paused = false;
//				stopped = false;
//				if(!notified)
//					showNotification(item);
			}
			
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public synchronized MediaPlayer getMediaPlayer() {
		if(mp == null){
			mp = new MediaPlayer();
			mp.setOnCompletionListener(this);
		}
		return mp;
	}


	public synchronized void pause() {
		if(mp != null && mp.isPlaying()){			
			mp.pause();					
		}
		if(!stopped)
			paused = true;
	}

	public synchronized void resume() {
		if(mp != null && !mp.isPlaying()){
			paused = false;
			if(!preparing)
				mp.start();
		}
	}

	public synchronized boolean isPaused() {
		return paused;
	}
	

	@Override
	public synchronized void onCompletion(MediaPlayer mp) {
		Log.d("onCompletion", "1");
		if(repeats < numberOfRepeats){
			repeats++;

			mp.seekTo(0);
			mp.start();
			//play(currentItem);
		}else{
			repeats = 0;
			if(mp != null && mp.isPlaying()){
				Log.d("onCompletion", "1.1 mp is playing !!! stop it!!!");
				mp.stop();
			}

			AyahItem nextItem = null;
			if (this.currentItem != null)
				nextItem = QuranAudioLibrary.getNextAyahAudioItem(context, this.currentItem);
			Log.d("onCompletion", "2");
			boolean continuePlaying = false;
			if(ayahListener != null && !stopped && nextItem != null)
				continuePlaying = ayahListener.onAyahComplete(currentItem, nextItem);
			if(nextItem != null){
				Log.d("onCompletion", "3 next ayah is not null, next ayah is " + nextItem.getAyah());
				//this.currentItem = nextItem;
				try{
					Log.d("onCompletion", "4 before try " + (mp == null) + " " +  mp.isPlaying());
					if(continuePlaying && !paused && !stopped && mp != null && !mp.isPlaying()){
						Log.d("onCompletion", "5 inside if");
						this.play(nextItem);
					}
				}catch(Exception ex){Log.e("OnCompletion", "error on play " + ex.toString() + ex.getMessage());}
			}
		}
	}		
	
	public void destory(){
		if(mp != null){
			mp.stop();
			mp.release();
		}
	}



	@Override
	public synchronized void onPrepared(MediaPlayer mp) {
		errorCount = 0;
		preparing = false;
		this.mp  = mp;
		if(stopped){
			mp.stop();
			return;
		}
		if(paused){
			mp.start();
			mp.pause();
			return;
		}
		if(mp != null){
			mp.start();
			paused = false;
			stopped = false;
			if(!notified)
				showNotification(currentItem);
			if (ayahListener != null)
				ayahListener.onAyahPlay(currentItem);
		}
	}
	

	public synchronized AyahItem getCurrentAyah(){
		return currentItem;
	}
	
	
	public synchronized void enableRemotePlay(boolean remote){
		this.remotePlayEnabled = remote;
	}
	
	public synchronized boolean isRemotePlayEnabled(){
		return this.remotePlayEnabled;
	}
	
	private void showNotification(AyahItem item){
		NotificationManager mgr = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		 // cancel all previous notifications ..
	     //mgr.cancelAll();
	     Intent i = new Intent(context, QuranViewActivity.class);
	     i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
	     PendingIntent pi = PendingIntent.getActivity(context.getApplicationContext(), 
	    		 	0,
	    		 	i
	     			, PendingIntent.FLAG_UPDATE_CURRENT | Notification.FLAG_AUTO_CANCEL);
	     Notification notification = new Notification(
	                //android.R.drawable.ic_notification_overlay,
	    		 	R.drawable.icon,
	    		 	QuranInfo.getSuraName(item.getSoura() - 1),
	                System.currentTimeMillis());
	     
	     
	     	String name = context.getString(R.string.app_name);
	        notification.setLatestEventInfo(context, name,
	        				item.getSoura() + " - " + QuranInfo.getSuraName(item.getSoura() -1)
	        			 + " (" + item.getAyah() + ")", pi);
//	        notification.contentView = new RemoteViews("com.quran.labs.androidquran", 
//	        		R.layout.audio_notification);
	       
	        notification.flags |= Notification.FLAG_ONGOING_EVENT;
	        notification.icon = R.drawable.icon;
            
            mgr.notify(ApplicationConstants.NOTIFICATION_AUDIO, notification);
            
	        //notified = true;

	}
	
	public synchronized boolean isPlaying(){
		return !paused && !stopped;
	}

	@Override
	public synchronized boolean onError(MediaPlayer mp, int what, int extra) {
		if(this.mp != null && mp.isPlaying()){
			try{
				this.mp.stop();
				stopped = true;
				if(mp != this.mp && mp.isPlaying())
					mp.stop();
			}catch (Exception e) {
				try{mp.release();}catch(Exception ex){}
				mp = null;
			}
		}
		if(errorCount >= numberOfTries || url == null || mp == null){
			errorCount = 0;
			if(ayahListener != null && !stopped) {
				if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED)
					ayahListener.onConnectionLost(currentItem);
				else 
					ayahListener.onUnknownError(currentItem);
			}
		}else{
			try {
				errorCount++;
				mp.stop();
				mp.reset();
				mp.setDataSource(url);
				mp.prepareAsync();				
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
			
		return true;
	}
	
};
