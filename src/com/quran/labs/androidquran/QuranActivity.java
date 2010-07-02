package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.quran.labs.androidquran.common.ApplicationConstants;
import com.quran.labs.androidquran.common.QuranInfo;

public class QuranActivity extends ListActivity {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.quran_list);
        
        Intent i = new Intent(this, QuranDataActivity.class);
		this.startActivityForResult(i, ApplicationConstants.DATA_CHECK_CODE);
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data){
		if (requestCode == ApplicationConstants.DATA_CHECK_CODE){ showSuras(); }
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
		MenuItem item;
		item = menu.add(0, ApplicationConstants.JUMP_MENU_ID, 0, R.string.menu_jump);
		item.setIcon(android.R.drawable.ic_menu_set_as);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()){
		case ApplicationConstants.JUMP_MENU_ID:
			showDialog(ApplicationConstants.JUMP_DIALOG);
			break;
		}

		return super.onMenuItemSelected(featureId, item);
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
		jumpTo(QuranInfo.SURA_PAGE_START[(int)id]);
	}

	public void jumpTo(int page){
		Intent i = new Intent(this, QuranViewActivity.class);
		i.putExtra("page", page);
		startActivity(i);
	}
}