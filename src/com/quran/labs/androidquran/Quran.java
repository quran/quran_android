package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class Quran extends ListActivity {
	ProgressDialog pDialog = null;
	private QuranDataService boundService;
	private Handler handler;
	private boolean starting = true;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.quran_list);			
		
        /*
        // remove files for debugging purposes
        QuranUtils.debugRmDir(QuranUtils.getQuranBaseDirectory(), false);
        QuranUtils.debugLsDir(QuranUtils.getQuranBaseDirectory());
        System.exit(0);
        */
        
        // get the screen size
        WindowManager w = getWindowManager();
        Display d = w.getDefaultDisplay();
        int width = d.getWidth();
        int height = d.getHeight();
        Log.d("quran", "screen size: width [" + width + "], height: [" + height + "]");
        QuranScreenInfo.initialize(width, height); 
        
        if (QuranDataService.isRunning){
        	startService();
        	showProgressDialog();
        }
        else {
        	if ((QuranUtils.getQuranDirectory() != null) &&
        			(!QuranUtils.haveAllImages())){
        		promptForDownload();
        	}
        	else showSuras();
        }
    }
    
    /* easiest way i could find to fix the crash on orientation change bug */
    @Override
    public void onConfigurationChanged(Configuration newConfig){
    	super.onConfigurationChanged(newConfig);
    	Log.d("quran", "configuration changed...");
    }
        
    private void promptForDownload(){
    	AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    	dialog.setMessage(R.string.downloadPrompt);
    	dialog.setCancelable(false);
    	dialog.setPositiveButton(R.string.downloadPrompt_ok,
    			new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int id) {
    					dialog.cancel();
    					startService();
    					showProgressDialog();
    				}
    	});
    	
    	dialog.setNegativeButton(R.string.downloadPrompt_no, 
    			new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int id) {
    					dialog.cancel();
    					showSuras();
    				}
    	});
    	
    	AlertDialog alert = dialog.create();
    	alert.setTitle(R.string.downloadPrompt_title);
    	alert.show();
    }
    
    private void startService(){
    	starting = true;
    	if (!QuranDataService.isRunning)
    		startService(new Intent(this, QuranDataService.class));
    	
    	bindService(new Intent(this, QuranDataService.class),
    		conn, Context.BIND_AUTO_CREATE);
    }
    
    private ServiceConnection conn = new ServiceConnection() {
    	public void onServiceConnected(ComponentName name, IBinder service){
    		boundService = 
    			((QuranDataService.QuranDownloadBinder)service).getService();
    		starting = false;
    	}

    	public void onServiceDisconnected(ComponentName className) {
    		boundService = null;
    	}
    };
    
    class ProgressBarUpdateTask extends Thread {
    	public void run(){
    		int iters = 0;
    		while (starting || QuranDataService.phase == 1){
    			try {
    				Thread.sleep(1000);
    				if ((conn != null) && (boundService != null)){
    					int progress = boundService.getProgress();
    					pDialog.setProgress(progress);
    				}
    				iters++;
    			}
    			catch (InterruptedException ie){}
    		}
    		
    		handler.sendEmptyMessage(1);
    		while (QuranDataService.isRunning){
    			try {
    				Thread.sleep(1000);
    				if ((conn != null) && (boundService != null)){
    					int progress = boundService.getProgress();
    					pDialog.setProgress(progress);
    				}
    			}
    			catch (InterruptedException ie){}
    		}
    		
    		handler.sendEmptyMessage(0);
    	}
    }
    
    private void showProgressDialog(){
    	pDialog = new ProgressDialog(Quran.this);
    	pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    	pDialog.setTitle(R.string.downloading_title);
    	pDialog.setCancelable(false);
    	pDialog.setMessage(getString(R.string.downloading_message));
    	pDialog.show();
    	
    	handler = new Handler(){
    		public void handleMessage(Message msg){
    			if (msg.what == 0){
    				pDialog.dismiss();
    				pDialog = null;
    				showSuras();
    			}
    			else {
    				pDialog.setTitle(R.string.extracting_title);
    				pDialog.setMessage(getString(R.string.extracting_message));
    			}
    		}
    	};
    	new ProgressBarUpdateTask().start();
    }
    
    private void showSuras(){
    	ArrayList< Map<String, String> > suraList =
    		new ArrayList< Map<String, String> >();
    	for (int i=0; i<114; i++){
    		String suraStr = (i+1) + ". Surat " + QuranInfo.SURA_NAMES[i];
    		Map<String, String> map = new HashMap<String, String>();
    		map.put("suraname", suraStr);
    		suraList.add(map);
    	}
    	
    	String[] from = new String[]{ "suraname" };
    	int[] to = new int[]{ R.id.surarow };
    	
    	SimpleAdapter suraAdapter =
    		new SimpleAdapter(this, suraList, R.layout.quran_row, from, to);
    	
    	setListAdapter(suraAdapter);
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id){
    	super.onListItemClick(l, v, position, id);
    	Intent i = new Intent(this, QuranView.class);
    	i.putExtra("page", QuranInfo.SURA_PAGE_START[(int)id]);
    	startActivity(i);
    }
}