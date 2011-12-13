package com.quran.labs.androidquran;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import static com.quran.labs.androidquran.data.ApplicationConstants.*;

import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.IntentAction;
import com.quran.labs.androidquran.common.BaseQuranActivity;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.QuranDataService;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;

public class QuranActivity extends BaseQuranActivity {
	
	private ActionBar actionBar;
	private static final String ACTION_BOOKMARK = "BOOKMARK";
	private static final String ACTION_RESUME = "RESUME";
	private static final String ACTION_AUDIO_MANAGER = "DOWNLOAD";
		
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.quran_list);
        QuranSettings.load(prefs);
        
        setArabicFont();		
		actionBar = (ActionBar) findViewById(R.id.actionbar);
		addActions();
    }
    
    /**
     *  set the asset manager to define the Arabic font
     */
    
    public void setArabicFont(){
    	ArabicStyle.setAssetManager(getAssets());
        Intent i = new Intent(this, QuranDataActivity.class);
		startActivityForResult(i, DATA_CHECK_CODE);
    }
    
    /**
     * Sets up the action bar.
     */
    
	protected void addActions() {
		actionBar.setTitle("Quran");
		actionBar.addAction(getIntentAction(ACTION_RESUME, R.drawable.translation));
		actionBar.addAction(getIntentAction(ACTION_BOOKMARK, R.drawable.bookmarks));
		actionBar.addAction(getIntentAction(ACTION_AUDIO_MANAGER, R.drawable.download));
		actionBar.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				ListView list = (ListView)findViewById(R.id.suralist);
				list.setSelectionFromTop(0, 0);
			}
		});
	}
	
	protected void onNewIntent(Intent intent) {
		String action = intent.getAction();
		if (ACTION_BOOKMARK.equals(action)) {
			Intent i = new Intent(getApplicationContext(), BookmarksActivity.class);
	    	startActivityForResult(i, BOOKMARKS_CODE);
		} else if (ACTION_RESUME.equals(action)) {
			jumpTo(QuranSettings.getInstance().getLastPage());
		} else if (ACTION_AUDIO_MANAGER.equals(action)){
			Intent i = new Intent(this, AudioManagerActivity.class);
			startActivity(i);
		}
	}
	
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.menu_item_bookmarks).setVisible(false);
		return super.onPrepareOptionsMenu(menu);
	}
	
	private IntentAction getIntentAction(String intentAction, int drawable) {
		Intent i = new Intent(this, QuranActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		i.setAction(intentAction);
		IntentAction action = new IntentAction(this, i, drawable);
		return action;
	}
    
    public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (QuranScreenInfo.getInstance() == null)
			initializeQuranScreen();
		if (newConfig != null)
			QuranScreenInfo.getInstance().setOrientation(newConfig.orientation);
	}
    
    @Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == DATA_CHECK_CODE){
			showSuras();
			Integer lastPage = QuranSettings.getInstance().getLastPage();
			if (lastPage != null && lastPage != NO_PAGE_SAVED)
				jumpTo(lastPage);
		} else if (requestCode == SETTINGS_CODE) {
			QuranSettings.load(prefs);
			showSuras();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		QuranSettings.load(prefs);
		showSuras();
		
		int lastPage = QuranSettings.getInstance().getLastPage();
		if ((lastPage > 0) && (lastPage < 605)){
			int currentSura = QuranInfo.PAGE_SURA_START[lastPage-1];
			int juz = QuranInfo.getJuzFromPage(lastPage);
			int position = currentSura + juz - 1;
			ListView list = (ListView)findViewById(R.id.suralist);
			list.setSelectionFromTop(position, 20);
		}
	}

	public void showSuras() {
		int pos = 0;
		int sura = 1;
		int next = 1;
		QuranElement[] elements = new QuranElement[SURAS_COUNT + JUZ2_COUNT];
		
		for (int juz=1; juz <= JUZ2_COUNT; juz++){
			elements[pos++] = new QuranElement(QuranInfo.getJuzTitle() + " " + juz, true, juz, QuranInfo.JUZ_PAGE_START[juz-1]);
			next = (juz == JUZ2_COUNT) ? PAGES_LAST+1 : QuranInfo.JUZ_PAGE_START[juz];
			while ((sura <= SURAS_COUNT) && (QuranInfo.SURA_PAGE_START[sura-1] < next)) {
				String title = QuranInfo.getSuraTitle() + " " + QuranInfo.getSuraName(sura-1);
				elements[pos++] = new QuranElement(title, false, sura, QuranInfo.SURA_PAGE_START[sura-1]);
				sura++;
			}
		}

		ListView list = (ListView)findViewById(R.id.suralist);
		EfficientAdapter suraAdapter = new EfficientAdapter(this, elements);
		if(list != null){
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
	
	
	private class EfficientAdapter extends BaseAdapter {
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

		public View getView(final int position, View convertView, ViewGroup parent) {
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
				holder.play = (ImageView)convertView.findViewById(R.id.btnPlay);
				holder.play.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						downloadSuraAudio(elements[position].number);
					}
				});
				convertView.setTag(holder);
			}
			else {
				holder = (ViewHolder) convertView.getTag();
			}

			holder.page.setText(ArabicStyle.reshape(String.valueOf(elements[position].page)));
			holder.text.setText(ArabicStyle.reshape(elements[position].text));
			holder.number.setText(ArabicStyle.reshape(String.valueOf(elements[position].number)));
			
			if (elements[position].isJuz){
				holder.metadata.setVisibility(View.GONE);
				holder.suraicon.setVisibility(View.GONE);
				holder.number.setVisibility(View.GONE);
				holder.play.setVisibility(View.GONE);
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
		
		
		private void downloadSuraAudio(int suraId) {
			Intent intent = new Intent(QuranActivity.this, QuranDataService.class);
			intent.putExtra(QuranDataService.DWONLOAD_TYPE_KEY, QuranDataService.DOWNLOAD_SURA_AUDIO);
			intent.putExtra(QuranDataService.SOURA_KEY, suraId);
			intent.putExtra(QuranDataService.AYAH_KEY, (int)1);
			intent.putExtra(QuranDataService.READER_KEY, 1);
			intent.putExtra(QuranDataService.DOWNLOAD_AYAH_IMAGES_KEY, false);
	    	if (!QuranDataService.isRunning)
	    		startService(intent);
		}

		class ViewHolder {
			TextView text;
			TextView page;
			TextView number;
			TextView metadata;
			ImageView suraicon;
			ImageView play;
		}
	}
}