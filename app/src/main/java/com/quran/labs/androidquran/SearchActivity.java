package com.quran.labs.androidquran;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import com.actionbarsherlock.app.SherlockActivity;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.TranslationManagerActivity;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends SherlockActivity
        implements DefaultDownloadReceiver.SimpleDownloadListener {

   public static final String SEARCH_INFO_DOWNLOAD_KEY =
           "SEARCH_INFO_DOWNLOAD_KEY";

	private TextView mMessageView, mWarningView;
	private Button mBtnGetTranslations;
	private boolean mDownloadArabicSearchDb = false;
	private boolean mIsArabicSearch = false;
   private DefaultDownloadReceiver mDownloadReceiver = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
      setTheme(R.style.Theme_Sherlock);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search);
		mMessageView = (TextView)findViewById(R.id.search_area);
		mWarningView = (TextView)findViewById(R.id.search_warning);
		mBtnGetTranslations = (Button)findViewById(R.id.btnGetTranslations);
		mBtnGetTranslations.setOnClickListener(new OnClickListener() {
         public void onClick(View v) {
            Intent intent;
            if (mDownloadArabicSearchDb) {
               downloadArabicSearchDb();
               return;
            } else {
               intent = new Intent(getApplicationContext(),
                       TranslationManagerActivity.class);
            }
            startActivity(intent);
            finish();
         }
      });
		handleIntent(getIntent());
	}

   @Override
   public void onPause(){
      if (mDownloadReceiver != null){
         mDownloadReceiver.setListener(null);
         LocalBroadcastManager.getInstance(this)
                 .unregisterReceiver(mDownloadReceiver);
         mDownloadReceiver = null;
      }
      super.onPause();
   }

	private void downloadArabicSearchDb(){
      if (mDownloadReceiver == null){
         mDownloadReceiver = new DefaultDownloadReceiver(this,
              QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB);
         LocalBroadcastManager.getInstance(this).registerReceiver(
              mDownloadReceiver, new IntentFilter(
              QuranDownloadService.ProgressIntent.INTENT_NAME));
      }
      mDownloadReceiver.setListener(this);

      String url = QuranFileUtils.getArabicSearchDatabaseUrl();
      String notificationTitle = getString(R.string.search_data);
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
              QuranFileUtils.getQuranDatabaseDirectory(), notificationTitle,
              SEARCH_INFO_DOWNLOAD_KEY,
              QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB);
      intent.putExtra(QuranDownloadService.EXTRA_OUTPUT_FILE_NAME,
              QuranDataProvider.QURAN_ARABIC_DATABASE);
      startService(intent);
	}

   @Override
   public void handleDownloadSuccess(){
      mWarningView.setVisibility(View.GONE);
      mBtnGetTranslations.setVisibility(View.GONE);
      handleIntent(getIntent());
   }

   @Override
   public void handleDownloadFailure(int errId){
   }

	@Override
	protected void onNewIntent(Intent intent) {
		setIntent(intent);
		handleIntent(intent);
	}

	private void handleIntent(Intent intent) {
      if (intent == null){ return; }
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

         if (QuranUtils.doesStringContainArabic(query)){
				mIsArabicSearch = true;
         }

			if (mIsArabicSearch){
				// if we come from muyassar and don't have arabic db, we set
				// arabic search to false so we jump to the translation.
				if (!QuranFileUtils.hasArabicSearchDatabase()){
					mIsArabicSearch = false;
            }
			}
         
			Integer id = null;
			try {
				id = intentData.getLastPathSegment() != null?
                    Integer.valueOf(intentData.getLastPathSegment()) : null;
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
            finish();
			}
		}
	}
	
	private void jumpToResult(int sura, int ayah){
		int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
		Intent intent = new Intent(this, PagerActivity.class);
      intent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura);
      intent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah);
      if (!mIsArabicSearch) {
			intent.putExtra(PagerActivity.EXTRA_JUMP_TO_TRANSLATION, true);
      }
		intent.putExtra("page", page);
		startActivity(intent);
	}

	private void showResults(String query){
		mIsArabicSearch = QuranUtils.doesStringContainArabic(query);
		boolean showArabicWarning = (mIsArabicSearch &&
			!QuranFileUtils.hasArabicSearchDatabase());
		if (showArabicWarning){ mIsArabicSearch = false; }
		
		Cursor cursor = getContentResolver().query(
              QuranDataProvider.SEARCH_URI,
              null, null, new String[]{query}, null);
		if (cursor == null) {
         SharedPreferences prefs =
                 PreferenceManager.getDefaultSharedPreferences(
                         getApplicationContext());
         String active = prefs.getString(
                 Constants.PREF_ACTIVE_TRANSLATION, "");
			if (TextUtils.isEmpty(active)) {
				int resource = R.string.no_active_translation;
				int buttonResource = R.string.translation_settings;
				if (showArabicWarning){
					resource = R.string.no_arabic_search_available;
					mDownloadArabicSearchDb = true;
					buttonResource = R.string.get_arabic_search_db;
				}
				mMessageView.setText(getString(resource, new Object[]{query}));
				mBtnGetTranslations.setText(getString(buttonResource));
				mBtnGetTranslations.setVisibility(View.VISIBLE);
			} else {
				if (showArabicWarning){
					mWarningView.setText(
                       getString(R.string.no_arabic_search_available));
					mWarningView.setVisibility(View.VISIBLE);
					mBtnGetTranslations.setText(
                       getString(R.string.get_arabic_search_db));
					mBtnGetTranslations.setVisibility(View.VISIBLE);
				}
				mMessageView.setText(getString(R.string.no_results,
                    new Object[]{query}));
			}
		} else {
			if (showArabicWarning){
				mWarningView.setText(getString(R.string.no_arabic_search_available,
                    new Object[]{query}));
				mWarningView.setVisibility(View.VISIBLE);
				mBtnGetTranslations.setText(
                    getString(R.string.get_arabic_search_db));
				mBtnGetTranslations.setVisibility(View.VISIBLE);
				mDownloadArabicSearchDb = true;
			}
			
			// Display the number of results
			int count = cursor.getCount();
			String countString = count + " " + getResources().getQuantityString(
					R.plurals.search_results,
					count, new Object[] {query});
			mMessageView.setText(countString);
			
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
			EfficientResultAdapter adapter = new EfficientResultAdapter(this,
                 res, mIsArabicSearch);
			listView.setAdapter(adapter);
			listView.setOnItemClickListener(new OnItemClickListener(){
				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					ListView p = (ListView)parent;
					SearchElement res = (SearchElement)p.getAdapter()
                       .getItem(position);
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
		private List<SearchElement> mElements;
      private Context mContext;
      private boolean mIsArabicSearch;
      private boolean mShouldReshape;
      private boolean mUseArabicFont;
		
		public EfficientResultAdapter(Context context,
                                    List<SearchElement> metadata,
                                    boolean isArabicSearch) {
			mInflater = LayoutInflater.from(context);
			mElements = metadata;
         mContext = context;
         mIsArabicSearch = isArabicSearch;
         if (mIsArabicSearch){
            mShouldReshape = QuranSettings.isReshapeArabic(context);
            mUseArabicFont = QuranSettings.needArabicFont(context);
         }
         else {
            mShouldReshape = false;
            mUseArabicFont = false;
         }
      }

		public int getCount() {
			return mElements.size();
		}

		public Object getItem(int position) {
			return mElements.get(position);
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
				holder.text.setTypeface(ArabicStyle.getTypeface(mContext));
				holder.metadata = (TextView)convertView
                    .findViewById(R.id.verseLocation);
            if (mUseArabicFont){
				   holder.metadata.setTypeface(ArabicStyle.getTypeface(mContext));
            }
				convertView.setTag(holder);
			}
			else {
				holder = (ViewHolder) convertView.getTag();
			}

			SearchElement v = mElements.get(position);
         String text = v.text;
         String suraName = QuranInfo.getSuraName(mContext, v.sura, false);
         if (mShouldReshape){
            text = ArabicStyle.reshape(mContext, v.text);
            suraName = ArabicStyle.reshape(mContext, suraName);
         }
			holder.text.setText(Html.fromHtml(text));

			holder.metadata.setText(mInflater.getContext()
                 .getString(R.string.found_in_sura) + " " +
					suraName +
					", " + mInflater.getContext()
                 .getString(R.string.quran_ayah) + " " + v.ayah);
			return convertView;
		}

		static class ViewHolder {
			TextView text;
			TextView metadata;
		}
	}
}
