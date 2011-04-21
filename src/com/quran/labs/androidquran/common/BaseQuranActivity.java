package com.quran.labs.androidquran.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.quran.labs.androidquran.AboutUsActivity;
import com.quran.labs.androidquran.BookmarksActivity;
import com.quran.labs.androidquran.DownloadActivity;
import com.quran.labs.androidquran.ExpViewActivity;
import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranJumpDialog;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.TranslationActivity;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.util.QuranSettings;

public abstract class BaseQuranActivity extends Activity {

	protected SharedPreferences prefs;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);	  
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
			case ApplicationConstants.BOOKMARKS_CODE:
				if (resultCode == Activity.RESULT_OK) {
					Integer lastPage = data.getIntExtra("page", ApplicationConstants.PAGES_FIRST);
					jumpTo(lastPage);
				} 
			break;
		}
	}
	
	@Override
    protected Dialog onCreateDialog(int id){
		if (id == ApplicationConstants.JUMP_DIALOG){
    		Dialog dialog = new QuranJumpDialog(this);
    		dialog.setOnCancelListener(new OnCancelListener(){
    			public void onCancel(DialogInterface dialog) {
    				QuranJumpDialog dlg = (QuranJumpDialog)dialog;
    				Integer page = dlg.getPage();
    				removeDialog(ApplicationConstants.JUMP_DIALOG);
    				if (page != null) jumpTo(page);
    			}

    		});
    		return dialog;
    	}
    	return null;
    }
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		Intent intent;
		switch (item.getItemId()){
			case R.id.menu_item_jump:
				showDialog(ApplicationConstants.JUMP_DIALOG);
			break;
			case R.id.menu_item_about_us:
		    	intent = new Intent(getApplicationContext(), AboutUsActivity.class);
		    	startActivity(intent);
		    break;
			case R.id.menu_item_help:
				intent = new Intent(getApplicationContext(), HelpActivity.class);
				startActivity(intent);
			break;
			case R.id.menu_item_settings:
				//intent = new Intent(getApplicationContext(), SettingsActivity.class);
				intent = new Intent(getApplicationContext(), QuranPreferenceActivity.class);
				startActivityForResult(intent, ApplicationConstants.SETTINGS_CODE);
			break;
			case R.id.menu_item_bookmarks:
		    	intent = new Intent(getApplicationContext(), BookmarksActivity.class);
		    	startActivityForResult(intent, ApplicationConstants.BOOKMARKS_CODE);
			break;
			case R.id.menu_item_translations:
				Intent i = new Intent(this, TranslationActivity.class);
				i.putExtra("page", QuranSettings.getInstance().getLastPage());
				startActivityForResult(i, ApplicationConstants.TRANSLATION_VIEW_CODE);
			break;
			case R.id.menu_item_get_translations:
				intent = new Intent(getApplicationContext(), DownloadActivity.class);
				startActivity(intent);
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}
	
	public void jumpTo(int page) {
		Intent i = new Intent(this, ExpViewActivity.class);
		i.putExtra("page", page);
		startActivityForResult(i, ApplicationConstants.QURAN_VIEW_CODE);
	}
	
	public boolean isInternetOn() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm != null && cm.getActiveNetworkInfo() != null) 
			return cm.getActiveNetworkInfo().isConnectedOrConnecting();
		return false;
	}
	
	protected void connect() {
		if (isInternetOn())
        	onConnectionSuccess();
        else
        	onConnectionFailed();
	}
	
	protected void onConnectionSuccess() {
		
	}
	
	protected void onConnectionFailed() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Unable to connect to server, make sure that your Internet connection is active. Retry ?")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   dialog.dismiss();
		        	   connect();
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   dialog.dismiss();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	@Override
    protected void onResume() {
    	super.onResume();
    	QuranSettings.load(prefs);
    }
	
	@Override
	protected void onPause() {
		super.onPause();
		QuranSettings.save(prefs);
	}
}
