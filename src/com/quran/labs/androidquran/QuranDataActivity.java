package com.quran.labs.androidquran;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;

import com.quran.labs.androidquran.common.InternetActivity;
import com.quran.labs.androidquran.service.QuranDataService;
import com.quran.labs.androidquran.util.QuranFileUtils;

public class QuranDataActivity extends InternetActivity {
	
	protected Handler splashHandler = null;
	protected Runnable splashRunner = null;
	protected int _splashTime = 3000; // time to display the splash screen in ms
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
        		WindowManager.LayoutParams.FLAG_FULLSCREEN,
        		WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.splash_screen);
        
        /*
        // remove files for debugging purposes
        QuranUtils.debugRmDir(QuranUtils.getQuranBaseDirectory(), false);
        QuranUtils.debugLsDir(QuranUtils.getQuranBaseDirectory());
        System.exit(0);
        */
        
        initializeQuranScreen();
        showSplashScreen();
    }
    
	private void showSplashScreen() {
		splashHandler = new Handler();
		splashRunner = new Runnable(){
			@Override
			public void run(){
				checkDataStatus();
			}
		};
		splashHandler.postDelayed(splashRunner, _splashTime);
	}

	public void checkDataStatus(){
        if (QuranDataService.isRunning){
        	startDownloadService(new Intent(this, QuranDataService.class));
        } else {
        	if ((QuranFileUtils.getQuranDirectory() != null) &&
        			(!QuranFileUtils.haveAllImages())){
        		promptForDownload();
        	}
        	else runListView();
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
     	if (event.getAction() == MotionEvent.ACTION_DOWN) {
     		splashHandler.removeCallbacks(splashRunner);
     		checkDataStatus();
       	}
       	return true;
    }

	/* easiest way i could find to fix the crash on orientation change bug */
    @Override
    public void onConfigurationChanged(Configuration newConfig){
    	super.onConfigurationChanged(newConfig);
    	Log.d("quran", "configuration changed...");
    }
    
    @Override
    protected void onDestroy(){
    	super.onDestroy();
    	if ((currentTask != null) && (currentTask.getStatus() == Status.RUNNING))
    		currentTask.cancel(true);
    }
        
    private void promptForDownload(){
    	AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    	dialog.setMessage(R.string.downloadPrompt);
    	dialog.setCancelable(false);
    	dialog.setPositiveButton(R.string.downloadPrompt_ok,
			new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
					downloadQuranImages();
				}
    	});
    	
    	dialog.setNegativeButton(R.string.downloadPrompt_no, 
    			new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int id) {
    					dialog.cancel();
    					runListView();
    				}
    	});
    	
    	AlertDialog alert = dialog.create();
    	alert.setTitle(R.string.downloadPrompt_title);
    	alert.show();
    }
    
    @Override
    protected void onFinishDownload() {
    	super.onFinishDownload();
    	runListView();
    }
    
    protected void runListView(){
		Intent i = new Intent(this, QuranActivity.class);
		startActivity(i);
    }
}
