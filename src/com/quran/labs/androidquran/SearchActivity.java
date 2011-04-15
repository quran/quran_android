package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;

public class SearchActivity extends Activity {

	private TextView textView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search);

		textView = (TextView)findViewById(R.id.search_area);
		handleIntent(getIntent());
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
		Intent translation = new Intent(this, TranslationActivity.class);
		translation.putExtra("page", page);
		startActivity(translation);
		finish();
	}

	private void showResults(String query){
		Cursor cursor = managedQuery(QuranDataProvider.SEARCH_URI,
				null, null, new String[] {query}, null);
		if (cursor == null) {
			textView.setText(getString(R.string.no_results, new Object[]{query}));
		} else {
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
				holder.metadata = (TextView)convertView.findViewById(R.id.verseLocation);
				convertView.setTag(holder);
			}
			else {
				holder = (ViewHolder) convertView.getTag();
			}

			SearchElement v = elements.get(position);
			holder.text.setText(v.text);

			holder.metadata.setText("Found in Sura " +
					QuranInfo.getSuraName(v.sura-1) +
					", verse " + v.ayah);
			return convertView;
		}

		static class ViewHolder {
			TextView text;
			TextView metadata;
		}
	}
}