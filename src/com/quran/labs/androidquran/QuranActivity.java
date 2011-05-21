package com.quran.labs.androidquran;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.quran.labs.androidquran.common.BaseQuranActivity;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;

public class QuranActivity extends BaseQuranActivity {
		
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.quran_list);
        QuranSettings.load(prefs);
        // set the asset manager to define the Arabic font
        ArabicStyle.setAssetManager(getAssets());
        Intent i = new Intent(this, QuranDataActivity.class);
		this.startActivityForResult(i, ApplicationConstants.DATA_CHECK_CODE);
    }
    
    public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (newConfig != null)
			QuranScreenInfo.getInstance().setOrientation(newConfig.orientation);
	}
    
    @Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == ApplicationConstants.DATA_CHECK_CODE){
			showSuras();
			Integer lastPage = QuranSettings.getInstance().getLastPage();
			if (lastPage != null && lastPage != ApplicationConstants.NO_PAGE_SAVED)
				jumpTo(lastPage);
		} else if (requestCode == ApplicationConstants.SETTINGS_CODE) {
			QuranSettings.load(prefs);
			showSuras();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		QuranSettings.load(prefs);
		showSuras();
	}

	public void showSuras() {
		int pos = 0;
		int sura = 1;
		int next = 1;
		QuranElement[] elements = new QuranElement[114+30];
		
		for (int juz=1; juz <= ApplicationConstants.JUZ2_COUNT; juz++){
			elements[pos++] = new QuranElement(QuranInfo.getJuzTitle() + " " + juz, true, juz, QuranInfo.JUZ_PAGE_START[juz-1]);
			next = (juz == ApplicationConstants.JUZ2_COUNT) ? ApplicationConstants.PAGES_LAST+1 : QuranInfo.JUZ_PAGE_START[juz];
			while ((sura <= ApplicationConstants.SURAS_COUNT) && (QuranInfo.SURA_PAGE_START[sura-1] < next)) {
				String title = QuranInfo.getSuraTitle() + " " + QuranInfo.getSuraName(sura-1);
				elements[pos++] = new QuranElement(title, false, sura, QuranInfo.SURA_PAGE_START[sura-1]);
				sura++;
			}
		}

		ListView list = (ListView)findViewById(R.id.suralist);
		EfficientAdapter suraAdapter = new EfficientAdapter(this, elements);
		list.setAdapter(suraAdapter);
		list.setOnItemClickListener(new OnItemClickListener(){

			@Override
			public void onItemClick(AdapterView<?> parent, View v, int position,
					long id) {
				ListView p = (ListView)parent;
				QuranElement elem = (QuranElement)p.getAdapter().getItem((int)id);
				jumpTo(elem.page);				
			}
		});
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
				holder.text.setTypeface(ArabicStyle.getTypeface());
				holder.metadata = (TextView)convertView.findViewById(R.id.sura_info);
				holder.metadata.setTypeface(ArabicStyle.getTypeface());
				holder.page = (TextView)convertView.findViewById(R.id.page_info);
				holder.number = (TextView)convertView.findViewById(R.id.sura_number);
				holder.suraicon = (ImageView)convertView.findViewById(R.id.sura_icon_img);
				convertView.setTag(holder);
			}
			else {
				holder = (ViewHolder) convertView.getTag();
			}

			holder.page.setText("" + elements[position].page);
			holder.text.setText(ArabicStyle.reshape(elements[position].text));
			holder.number.setText("" + elements[position].number);
			
			if (elements[position].isJuz){
				holder.metadata.setVisibility(View.GONE);
				holder.suraicon.setVisibility(View.GONE);
				holder.number.setVisibility(View.GONE);
			}
			else {
				String info = QuranInfo.getSuraListMetaString(elements[position].number);
				holder.metadata.setVisibility(View.VISIBLE);
				holder.suraicon.setVisibility(View.VISIBLE);
				holder.number.setVisibility(View.VISIBLE);
				holder.metadata.setText(ArabicStyle.reshape(info));
			}
			return convertView;
		}

		static class ViewHolder {
			TextView text;
			TextView page;
			TextView number;
			TextView metadata;
			ImageView suraicon;
		}
	}
}