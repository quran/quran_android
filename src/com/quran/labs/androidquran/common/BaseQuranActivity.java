package com.quran.labs.androidquran.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.quran.labs.androidquran.AboutUsActivity;
import com.quran.labs.androidquran.BookmarksActivity;
import com.quran.labs.androidquran.ExpViewActivity;
import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranJumpDialog;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.ApplicationConstants;

public abstract class BaseQuranActivity extends Activity {
	
	public SharedPreferences prefs;
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
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
		}
		return super.onMenuItemSelected(featureId, item);
	}
	
	public void jumpTo(int page) {
		Intent i = new Intent(this, ExpViewActivity.class);
		i.putExtra("page", page);
		startActivityForResult(i, ApplicationConstants.QURAN_VIEW_CODE);
	}
}
