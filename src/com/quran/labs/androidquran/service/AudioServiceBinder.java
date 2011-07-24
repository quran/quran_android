package com.quran.labs.androidquran.service;

import java.io.IOException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Binder;

import com.quran.labs.androidquran.QuranViewActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.common.AyahStateListener;
import com.quran.labs.androidquran.common.IAudioPlayer;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.QuranAudioLibrary;

public class AudioServiceBinder extends Binder implements  
	OnCompletionListener, IAudioPlayer, OnPreparedListener{
	
	private MediaPlayer mp = null;
	private Context context;
	private boolean paused = false;	
	private boolean remotePlayEnabled = false;
	private AyahItem currentItem;
	private AyahStateListener ayahListener = null;
	private boolean notified;
	private boolean stopped;
	
	public void setAyahCompleteListener(AyahStateListener ayahListener) {
		this.ayahListener = ayahListener;
	}



	public AudioServiceBinder(Context context){
		this.context = context;
	}
	

	
	/* (non-Javadoc)
	 * @see org.islam.quran.IAudioPlayer#stop()
	 */
	public void stop() {
		if(mp != null && mp.isPlaying())
				mp.stop();		
		paused = false;	
		stopped = true;
	}

	/* (non-Javadoc)
	 * @see org.islam.quran.IAudioPlayer#play(org.islam.quran.AyahAudioItem)
	 */
	public void play(AyahItem item) {
		this.currentItem = item;
		if(mp != null){
			mp.stop();
			mp.release();
			mp = null;
		}
		try {
			if(mp == null)
				mp = new MediaPlayer();			
			mp.reset();
			mp.setOnCompletionListener(this);
			String url = null;
			if(item.isAudioFoundLocally())
				url = item.getLocalAudioUrl();
			else if(remotePlayEnabled) {
				url = item.getRemoteAudioUrl();
			}else{
				if(ayahListener != null)
					ayahListener.onNotFound(item);
				else
				{
					// show notification ayah not found, download it or play remotely	
				}
			}	
			if(url != null){
				mp.setDataSource(url);
				mp.prepare();
				mp.start();
				paused = false;
				stopped = false;
				if(!notified)
					showNotification(item);
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

	/* (non-Javadoc)
	 * @see org.islam.quran.IAudioPlayer#getMediaPlayer()
	 */
	public MediaPlayer getMediaPlayer() {
		if(mp == null){
			mp = new MediaPlayer();
			mp.setOnCompletionListener(this);
		}
		return mp;
	}


	/* (non-Javadoc)
	 * @see org.islam.quran.IAudioPlayer#pause()
	 */
	public void pause() {
		if(mp != null && mp.isPlaying()){			
			mp.pause();					
		}
		if(!stopped)
			paused = true;
	}

	/* (non-Javadoc)
	 * @see org.islam.quran.IAudioPlayer#resume()
	 */
	public void resume() {
		if(mp != null && !mp.isPlaying()){
			paused = false;
			mp.start();
		}
	}

	public boolean isPaused() {
		return paused;
	}
	

	@Override
	public void onCompletion(MediaPlayer mp) {
		AyahItem nextItem = QuranAudioLibrary.getNextAyahAudioItem(context, this.currentItem);
		if(ayahListener != null)
			ayahListener.onComplete(currentItem, nextItem);
		if(nextItem != null){
			this.currentItem = nextItem;
			if(!paused && !stopped)
				this.play(currentItem);
		}
	}		
	
	public void destory(){
		if(mp != null){
			mp.stop();
			mp.release();
		}
	}



	@Override
	public void onPrepared(MediaPlayer mp) {
		this.mp  = mp;
		if(mp != null){
			mp.start();
			paused = false;
			stopped = false;
			if(!notified)
				showNotification(currentItem);
		}
	}
	

	public AyahItem getCurrentAyah(){
		return currentItem;
	}
	
	
	public void enableRemotePlay(boolean remote){
		this.remotePlayEnabled = remote;
	}
	
	public boolean isRemotePlayEnabled(){
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
	     
	     
	        notification.setLatestEventInfo(context,
	        				context.getApplicationInfo().name, 
	        				QuranInfo.getSuraName(item.getSoura() -1)
	        			 + "(" + item.getAyah() + ")", pi);
//	        notification.contentView = new RemoteViews("com.quran.labs.androidquran", 
//	        		R.layout.audio_notification);
	       
	        notification.flags |= Notification.FLAG_ONGOING_EVENT;
	        notification.icon = R.drawable.icon;
            
            mgr.notify(1, notification);
            
	        //notified = true;

	}
	
};
