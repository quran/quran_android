package com.quran.labs.androidquran.service;


import com.quran.labs.androidquran.receivers.CallStateListener;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class QuranAudioService extends Service {

	private PhoneStateListener psl;
	private AudioServiceBinder mBinder;
	@Override
	public void onCreate() {		
		super.onCreate();
		mBinder = new AudioServiceBinder(this);
		psl = new CallStateListener(mBinder);
		((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE))
			.listen(psl, PhoneStateListener.LISTEN_CALL_STATE);	
		//Toast.makeText(this, "Service created...", Toast.LENGTH_LONG).show();
	}
	
	@Override
	public void onDestroy() {
		try{
			mBinder.destory();
		}catch(Exception e){}
		finally{
			super.onDestroy();
			//Toast.makeText(this, "Service destroyed...", Toast.LENGTH_LONG).show();
		}		
	}
	@Override
	public IBinder onBind(Intent arg0) {		
		return mBinder;
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);		
	}
	


}
