package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.List;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.quran.labs.androidquran.common.InternetActivity;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.common.TranslationsDBAdapter;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranUtils;

public class SearchActivity extends InternetActivity {

	private TextView textView, warningView;
	private Button btnGetTranslations;
	private boolean setActiveTranslation = false;
	private boolean downloadArabicSearchDb = false;
	private boolean isArabicSearch = false;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search);
		textView = (TextView)findViewById(R.id.search_area);
		warningView = (TextView)findViewById(R.id.search_warning);
		btnGetTranslations = (Button)findViewById(R.id.btnGetTranslations);
		btnGetTranslations.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent intent;
				if (setActiveTranslation)
					intent = new Intent(getApplicationContext(), QuranPreferenceActivity.class);
				else if (downloadArabicSearchDb){
					downloadArabicSearchDb();
					return;
				}
				else intent = new Intent(getApplicationContext(), DownloadActivity.class);
				startActivity(intent);
				finish();
			}
		});
		
		handleIntent(getIntent());
	}

	private void downloadArabicSearchDb(){
		String fileUrl =
			"http://labs.quran.com/androidquran/databases/" + QuranDataProvider.QURAN_ARABIC_DATABASE;
		downloadTranslation(fileUrl, QuranDataProvider.QURAN_ARABIC_DATABASE);
	}
	
	@Override
	protected void onFinishDownload() {
		super.onFinishDownload();
		finish();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		setIntent(intent);
		handleIntent(intent);
	}

	private void handleIntent(Intent intent) {
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			String query = intent.getStringExtra(SearchManager.QUERY);
			showResults(query);
		}
		else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
			Uri intentData = intent.getData();
			String query = intent.getStringExtra(SearchManager.USER_QUERY);
			if (query == null){
				Bundle extras = intent.getExtras();
				if (extras != null){
					// bug on ics where the above returns null
					// http://code.google.com/p/android/issues/detail?id=22978
					Object q = extras.get(SearchManager.USER_QUERY);
					if (q != null && q instanceof SpannableString){
						query = ((SpannableString)q).toString();
					}
				}
			}
			if (QuranUtils.doesStringContainArabic(query))
				isArabicSearch = true;
			if (isArabicSearch){
				// if we come from muyassar and don't have arabic db, we set
				// arabic search to false so we jump to the translation.
				if (!QuranUtils.hasTranslation(QuranDataProvider.QURAN_ARABIC_DATABASE))
					isArabicSearch = false;
			}
			
			Integer id = null;
			try {
				id = intentData.getLastPathSegment() != null ? Integer.valueOf(intentData.getLastPathSegment()) : null;
			} catch (NumberFormatException e) {
			}
			
			if (id != null){
				int sura = 1;
				int total = id;
				for (int j = 1; j <= 114; j++){
					int cnt = QuranInfo.getNumAyahs(j);
					total -= cnt;
					if (total >= 0)
						sura++;
					else {
						total += cnt;
						break;
					}
				}
				
				jumpToResult(sura, total);
			}
		}
	}
	
	private void jumpToResult(int sura, int ayah){
		int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
		Intent intent = null;
		if (isArabicSearch)
			intent = new Intent(this, QuranViewActivity.class);
		else intent = new Intent(this, TranslationActivity.class);
		intent.putExtra("page", page);
		startActivity(intent);
		finish();
	}

	private void showResults(String query){
		isArabicSearch = QuranUtils.doesStringContainArabic(query);
		boolean showArabicWarning = (isArabicSearch &&
			!QuranUtils.hasTranslation(QuranDataProvider.QURAN_ARABIC_DATABASE));
		if (showArabicWarning) isArabicSearch = false;
		
		Cursor cursor = managedQuery(QuranDataProvider.SEARCH_URI,
				null, null, new String[] {query}, null);
		if (cursor == null) {
			TranslationsDBAdapter dba = new TranslationsDBAdapter(getApplicationContext());
			TranslationItem [] items = dba.getAvailableTranslations();
			if (items == null || items.length == 0) {
				int resource = R.string.no_translations_available;
				if (showArabicWarning){
					resource = R.string.no_arabic_search_available;
					btnGetTranslations.setText(getString(R.string.get_arabic_search_db));
					downloadArabicSearchDb = true;
				}
				textView.setText(getString(resource, new Object[]{query}));
				btnGetTranslations.setVisibility(View.VISIBLE);
			} else if (items.length > 0 && dba.getActiveTranslation() == null) {
				int resource = R.string.no_active_translation;
				int buttonResource = R.string.set_active_translation;
				setActiveTranslation = true;
				if (showArabicWarning){
					resource = R.string.no_arabic_search_available;
					downloadArabicSearchDb = true;
					setActiveTranslation = false;
					buttonResource = R.string.get_arabic_search_db;
				}
				textView.setText(getString(resource, new Object[]{query}));
				btnGetTranslations.setText(getString(buttonResource));
				btnGetTranslations.setVisibility(View.VISIBLE);
			} else {
				if (showArabicWarning){
					warningView.setText(getString(R.string.no_arabic_search_available));
					warningView.setVisibility(View.VISIBLE);
					btnGetTranslations.setText(getString(R.string.get_arabic_search_db));
					btnGetTranslations.setVisibility(View.VISIBLE);
				}
				textView.setText(getString(R.string.no_results, new Object[]{query}));
			}
		} else {
			if (showArabicWarning){
				warningView.setText(getString(R.string.no_arabic_search_available, new Object[]{ query }));
				warningView.setVisibility(View.VISIBLE);
				btnGetTranslations.setText(getString(R.string.get_arabic_search_db));
				btnGetTranslations.setVisibility(View.VISIBLE);
				downloadArabicSearchDb = true;
			}
			
			// Display the number of results
			int count = cursor.getCount();
			String countString = count + " " + getResources().getQuantityString(
					R.plurals.search_results,
					count, new Object[] {query});
			textView.setText(countString);
			
			List<SearchElement> res = new ArrayList<SearchElement>();
			if (cursor.moveToFirst()){
				do {
					int sura = cursor.getInt(0);
					int ayah = cursor.getInt(1);
					String text = cursor.getString(2);
					SearchElement elem = new SearchElement(sura, ayah, text);
					res.add(elem);
				}
				while (cursor.moveToNext());
			}
			cursor.close();
			
			ListView listView = (ListView)findViewById(R.id.results_list);
			EfficientResultAdapter adapter = new EfficientResultAdapter(this, res);
			listView.setAdapter(adapter);
			listView.setOnItemClickListener(new OnItemClickListener(){
				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					ListView p = (ListView)parent;
					SearchElement res = (SearchElement)p.getAdapter().getItem(position);
					jumpToResult(res.sura, res.ayah);
				}
			});
		}
	}
	
	private class SearchElement {
		public int sura;
		public int ayah;
		public String text;
		
		public SearchElement(int sura, int ayah, String text){
			this.sura = sura;
			this.ayah = ayah;
			this.text = text;
		}
	}
	
	private static class EfficientResultAdapter extends BaseAdapter {
		private LayoutInflater mInflater;
		private List<SearchElement> elements;
		
		public EfficientResultAdapter(Context context, List<SearchElement> metadata) {
			mInflater = LayoutInflater.from(context);
			this.elements = metadata;
		}

		public int getCount() {
			return elements.size();
		}

		public Object getItem(int position) {
			return elements.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.search_result, null);
				holder = new ViewHolder();
				holder.text = (TextView)convertView.findViewById(R.id.verseText);
				holder.text.setTypeface(ArabicStyle.getTypeface());
				holder.metadata = (TextView)convertView.findViewById(R.id.verseLocation);
				holder.metadata.setTypeface(ArabicStyle.getTypeface());
				convertView.setTag(holder);
			}
			else {
				holder = (ViewHolder) convertView.getTag();
			}

			SearchElement v = elements.get(position);
			holder.text.setText(Html.fromHtml(ArabicStyle.reshape(v.text)));

			holder.metadata.setText("Found in Sura " +
					ArabicStyle.reshape(QuranInfo.getSuraName(v.sura-1)) +
					", verse " + v.ayah);
			return convertView;
		}

		static class ViewHolder {
			TextView text;
			TextView metadata;
		}
	}
}