package com.quran.labs.androidquran.common;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.quran.labs.androidquran.AboutUsActivity;
import com.quran.labs.androidquran.BookmarksActivity;
import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranViewActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.SettingsActivity;
import com.quran.labs.androidquran.data.ApplicationConstants;

public abstract class BaseQuranActivity extends Activity {
	
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
				intent = new Intent(getApplicationContext(), SettingsActivity.class);
				startActivity(intent);
			break;
			case R.id.menu_item_bookmarks:
		    	intent = new Intent(getApplicationContext(), BookmarksActivity.class);
		    	startActivityForResult(intent, ApplicationConstants.BOOKMARKS_CODE);
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}
	
	public void jumpTo(int page) {
		Intent i = new Intent(this, QuranViewActivity.class);
		i.putExtra("page", page);
		startActivityForResult(i, ApplicationConstants.QURAN_VIEW_CODE);
	}
}
