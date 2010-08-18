package com.quran.labs.androidquran;

import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

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
    	switch (requestCode) {
    		case ApplicationConstants.DATA_CHECK_CODE:
    		case ApplicationConstants.SETTINGS_CODE:
    			showSuras();
    			jumpTo(QuranSettings.getInstance().getLastPage());
			break;
    		case ApplicationConstants.BOOKMARKS_CODE:
    			if (resultCode == RESULT_OK) {
    				int page = data.getIntExtra("page", 0);
    				jumpTo(page);
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
			case R.id.menu_item_bookmarks:
		    	intent = new Intent(getApplicationContext(), BookmarksActivity.class);
		        startActivityForResult(intent, ApplicationConstants.BOOKMARKS_CODE);
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	private void showSuras() {
		int pos = 0;
		int sura = 1;
		int next = 1;
		QuranElement[] elements = new QuranElement[114+30];
		
		for (int juz=1; juz<=30; juz++){
			elements[pos++] = new QuranElement(
					"Juz' " + juz, true, juz, QuranInfo.JUZ_PAGE_START[juz-1]);
			next = (juz == 30)? 604 : QuranInfo.JUZ_PAGE_START[juz];
			while ((sura <= 114) && (QuranInfo.SURA_PAGE_START[sura-1] < next)){
				String title = (sura) + ". " + QuranInfo.getSuraTitle() + " " + 
					QuranInfo.getSuraName(sura-1);
				elements[pos++] = new QuranElement(title, false, sura,
						QuranInfo.SURA_PAGE_START[sura-1]);
				sura++;
			}
		}

		EfficientAdapter suraAdapter =
			new EfficientAdapter(this, elements);

		setListAdapter(suraAdapter);
	}
	
	private class QuranElement {
		public boolean isJuz;
		public int number;
		public int page;
		public String text;
		
		public QuranElement(String text, boolean isJuz, int number, int page){
			this.text = text;
			this.isJuz = isJuz;
			this.number = number;
			this.page = page;
		}
	}
	
	// http://www.androidpeople.com/android-custom-listview-tutorial-example/
	private static class EfficientAdapter extends BaseAdapter {
		private LayoutInflater mInflater;
		private QuranElement[] elements;
		
		public EfficientAdapter(Context context, QuranElement[] metadata) {
			mInflater = LayoutInflater.from(context);
			this.elements = metadata;
		}

		public int getCount() {
			return elements.length;
		}

		public Object getItem(int position) {
			return elements[position];
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.quran_row, null);
				holder = new ViewHolder();
				holder.text = (TextView)convertView.findViewById(R.id.sura_title);
				holder.metadata = (TextView)convertView.findViewById(R.id.sura_info);
				holder.page = (TextView)convertView.findViewById(R.id.page_info);
				convertView.setTag(holder);
			}
			else {
				holder = (ViewHolder) convertView.getTag();
			}

			holder.page.setText("" + elements[position].page);
			holder.text.setText(elements[position].text);
			if (elements[position].isJuz){
				holder.metadata.setVisibility(View.GONE);
			}
			else {
				String info = 
					QuranInfo.SURA_IS_MAKKI[elements[position].number-1]?
							"Makki" : "Madani";
				info += " - " +
					QuranInfo.SURA_NUM_AYAHS[elements[position].number-1] +
					" verses.";
				holder.metadata.setVisibility(View.VISIBLE);
				holder.metadata.setText(info);
			}
			return convertView;
		}

		static class ViewHolder {
			TextView text;
			TextView page;
			TextView metadata;
		}
	}


	@Override
	protected void onListItemClick(ListView l, View v, int position, long id){
		super.onListItemClick(l, v, position, id);
		QuranElement elem = (QuranElement)getListAdapter().getItem((int)id);
		jumpTo(elem.page);
	}

	public void jumpTo(int page){
		Intent i = new Intent(this, QuranViewActivity.class);
		i.putExtra("page", page);
		startActivity(i);
	}
}