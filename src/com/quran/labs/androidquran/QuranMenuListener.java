package com.quran.labs.androidquran;

import android.app.Activity;
import android.content.Intent;
import android.view.MenuItem;

import com.quran.labs.androidquran.common.ApplicationConstants;
import com.quran.labs.androidquran.util.QuranSettings;

public class QuranMenuListener {
	
	private Activity activity;
	
	public QuranMenuListener(Activity activity) {
		this.activity = activity;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case ApplicationConstants.DATA_CHECK_CODE:
			case ApplicationConstants.SETTINGS_CODE:
				if (activity instanceof QuranActivity) {
					((QuranActivity)activity).showSuras();
				}
				jumpTo(QuranSettings.getInstance().getLastPage());
			break;
			case ApplicationConstants.BOOKMARKS_CODE:
				if (resultCode == Activity.RESULT_OK) {
					int page = data.getIntExtra("page", ApplicationConstants.PAGES_FIRST);
					jumpTo(page);
				} 
			break;
			case ApplicationConstants.QURAN_VIEW_CODE:
				if (resultCode == Activity.RESULT_OK) {
					boolean openSettings = data.getBooleanExtra("openSettings", false);
					if (openSettings) startSettingsActivity();
				}
			break;
		}
	}

	public void jumpTo(int page) {
		Intent i = new Intent(activity, QuranViewActivity.class);
		i.putExtra("page", page);
		activity.startActivityForResult(i, ApplicationConstants.QURAN_VIEW_CODE);
	}
	
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		Intent intent;
		switch (item.getItemId()){
			case R.id.menu_item_jump:
				activity.showDialog(ApplicationConstants.JUMP_DIALOG);
			break;
			case R.id.menu_item_about_us:
		    	intent = new Intent(activity.getApplicationContext(), AboutUsActivity.class);
		    	activity.startActivity(intent);
		    break;
			case R.id.menu_item_settings:
				if (activity instanceof QuranViewActivity) {
					Intent data = new Intent();
					data.putExtra("openSettings", true);
					activity.setResult(Activity.RESULT_OK, data);
					activity.finish(); 
				} else {
			    	startSettingsActivity();
				}
			break;
			case R.id.menu_item_bookmarks:
		    	intent = new Intent(activity.getApplicationContext(), BookmarksActivity.class);
		    	activity.startActivityForResult(intent, ApplicationConstants.BOOKMARKS_CODE);
			break;
		}
		return true;
	}
	
	private void startSettingsActivity() {
		Intent intent = new Intent(activity.getApplicationContext(), SettingsActivity.class);
    	activity.startActivityForResult(intent, ApplicationConstants.SETTINGS_CODE);
	}
}
