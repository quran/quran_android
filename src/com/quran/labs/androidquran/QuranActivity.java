package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Configuration;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.quran.labs.androidquran.common.ApplicationConstants;
import com.quran.labs.androidquran.common.QuranInfo;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;

public class QuranActivity extends ListActivity {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.quran_list);
        QuranSettings.load(getSharedPreferences(ApplicationConstants.PREFERNCES, 0));
        
        Intent i = new Intent(this, QuranDataActivity.class);
		this.startActivityForResult(i, ApplicationConstants.DATA_CHECK_CODE);
    }
    
    public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		QuranScreenInfo.getInstance().setOrientation(newConfig.orientation);
	}
    
    public void onActivityResult(int requestCode, int resultCode, Intent data){
		if (requestCode == ApplicationConstants.DATA_CHECK_CODE 
			|| requestCode == ApplicationConstants.SETTINGS_CODE) { 
			showSuras(); 
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
    				if (page != null)
    					jumpTo(page);
    			}

    		});
    		return dialog;
    	}
    	return null;
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);	  
		return true;
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
			case R.id.menu_item_settings:
		    	intent = new Intent(getApplicationContext(), SettingsActivity.class);
		        startActivityForResult(intent, ApplicationConstants.SETTINGS_CODE);
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	private void showSuras(){
		ArrayList< Map<String, String> > suraList =
			new ArrayList< Map<String, String> >();
		for (int i=0; i<114; i++){
			String suraStr = (i+1) + ". " + QuranInfo.getSuraTitle() + " " + QuranInfo.getSuraName(i);
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
		jumpTo(QuranInfo.SURA_PAGE_START[(int)id]);
	}

	public void jumpTo(int page){
		Intent i = new Intent(this, QuranViewActivity.class);
		i.putExtra("page", page);
		startActivity(i);
	}
}