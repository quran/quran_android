package com.quran.labs.androidquran;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.QuranActionBarActivity;
import com.quran.labs.androidquran.ui.TranslationManagerActivity;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

public class SearchActivity extends QuranActionBarActivity
    implements DefaultDownloadReceiver.SimpleDownloadListener,
    LoaderManager.LoaderCallbacks<Cursor> {

  public static final String SEARCH_INFO_DOWNLOAD_KEY = "SEARCH_INFO_DOWNLOAD_KEY";
  private static final String EXTRA_QUERY = "EXTRA_QUERY";

  private TextView messageView;
  private TextView warningView;
  private Button buttonGetTranslations;
  private boolean downloadArabicSearchDb;
  private boolean isArabicSearch;
  private String query;
  private ResultAdapter adapter;
  private DefaultDownloadReceiver downloadReceiver;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.search);
    messageView = (TextView) findViewById(R.id.search_area);
    warningView = (TextView) findViewById(R.id.search_warning);
    buttonGetTranslations = (Button) findViewById(R.id.btnGetTranslations);
    buttonGetTranslations.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        Intent intent;
        if (downloadArabicSearchDb) {
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
  public void onPause() {
    if (downloadReceiver != null) {
      downloadReceiver.setListener(null);
      LocalBroadcastManager.getInstance(this)
          .unregisterReceiver(downloadReceiver);
      downloadReceiver = null;
    }
    super.onPause();
  }

  private void downloadArabicSearchDb() {
    if (downloadReceiver == null) {
      downloadReceiver = new DefaultDownloadReceiver(this,
          QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB);
      LocalBroadcastManager.getInstance(this).registerReceiver(
          downloadReceiver, new IntentFilter(
              QuranDownloadNotifier.ProgressIntent.INTENT_NAME));
    }
    downloadReceiver.setListener(this);

    String url = QuranFileUtils.getArabicSearchDatabaseUrl();
    String notificationTitle = getString(R.string.search_data);
    Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
        QuranFileUtils.getQuranDatabaseDirectory(this),
        notificationTitle, SEARCH_INFO_DOWNLOAD_KEY,
        QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB);
    intent.putExtra(QuranDownloadService.EXTRA_OUTPUT_FILE_NAME,
        QuranDataProvider.QURAN_ARABIC_DATABASE);
    startService(intent);
  }

  @Override
  public void handleDownloadSuccess() {
    warningView.setVisibility(View.GONE);
    buttonGetTranslations.setVisibility(View.GONE);
    handleIntent(getIntent());
  }

  @Override
  public void handleDownloadFailure(int errId) {
  }

  @Override
  protected void onNewIntent(Intent intent) {
    setIntent(intent);
    handleIntent(intent);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    String query = args.getString(EXTRA_QUERY);
    this.query = query;
    return new CursorLoader(this, QuranDataProvider.SEARCH_URI,
        null, null, new String[]{query}, null);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    isArabicSearch = QuranUtils.doesStringContainArabic(query);
    boolean showArabicWarning = (isArabicSearch &&
        !QuranFileUtils.hasArabicSearchDatabase(this));
    if (showArabicWarning) {
      isArabicSearch = false;
    }

    if (cursor == null) {
      String active = QuranSettings.getInstance(getApplicationContext()).getActiveTranslation();
      if (TextUtils.isEmpty(active)) {
        @StringRes int resource = R.string.no_active_translation;
        @StringRes int buttonResource = R.string.translation_settings;
        if (showArabicWarning) {
          resource = R.string.no_arabic_search_available;
          downloadArabicSearchDb = true;
          buttonResource = R.string.get_arabic_search_db;
        }
        messageView.setText(resource);
        buttonGetTranslations.setText(getString(buttonResource));
        buttonGetTranslations.setVisibility(View.VISIBLE);
      } else {
        if (showArabicWarning) {
          warningView.setText(
              getString(R.string.no_arabic_search_available));
          warningView.setVisibility(View.VISIBLE);
          buttonGetTranslations.setText(
              getString(R.string.get_arabic_search_db));
          buttonGetTranslations.setVisibility(View.VISIBLE);
        }
        messageView.setText(getString(R.string.no_results,
            new Object[]{ query }));
      }
    } else {
      if (showArabicWarning) {
        warningView.setText(R.string.no_arabic_search_available);
        warningView.setVisibility(View.VISIBLE);
        buttonGetTranslations.setText(
            getString(R.string.get_arabic_search_db));
        buttonGetTranslations.setVisibility(View.VISIBLE);
        downloadArabicSearchDb = true;
      }

      // Display the number of results
      int count = cursor.getCount();
      String countString = getResources().getQuantityString(
          R.plurals.search_results, count, query, count);
      messageView.setText(countString);

      ListView listView = (ListView) findViewById(R.id.results_list);
      if (adapter == null) {
        adapter = new ResultAdapter(this, cursor);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view,
              int position, long id) {
            ListView p = (ListView) parent;
            final Cursor currentCursor = (Cursor) p.getAdapter().getItem(position);
            jumpToResult(currentCursor.getInt(1), currentCursor.getInt(2));
          }
        });
      } else {
        adapter.changeCursor(cursor);
      }
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    if (adapter != null) {
      adapter.changeCursor(null);
    }
  }

  private void handleIntent(Intent intent) {
    if (intent == null) {
      return;
    }
    if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
      String query = intent.getStringExtra(SearchManager.QUERY);
      showResults(query);
    } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
      Uri intentData = intent.getData();
      String query = intent.getStringExtra(SearchManager.USER_QUERY);
      if (query == null) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
          // bug on ics where the above returns null
          // http://code.google.com/p/android/issues/detail?id=22978
          Object q = extras.get(SearchManager.USER_QUERY);
          if (q != null && q instanceof SpannableString) {
            query = q.toString();
          }
        }
      }

      if (QuranUtils.doesStringContainArabic(query)) {
        isArabicSearch = true;
      }

      if (isArabicSearch) {
        // if we come from muyassar and don't have arabic db, we set
        // arabic search to false so we jump to the translation.
        if (!QuranFileUtils.hasArabicSearchDatabase(this)) {
          isArabicSearch = false;
        }
      }

      Integer id = null;
      try {
        id = intentData.getLastPathSegment() != null ?
            Integer.valueOf(intentData.getLastPathSegment()) : null;
      } catch (NumberFormatException e) {
        // no op
      }

      if (id != null) {
        int sura = 1;
        int total = id;
        for (int j = 1; j <= 114; j++) {
          int cnt = QuranInfo.getNumAyahs(j);
          total -= cnt;
          if (total >= 0)
            sura++;
          else {
            total += cnt;
            break;
          }
        }

        if (total == 0){
          sura--;
          total = QuranInfo.getNumAyahs(sura);
        }

        jumpToResult(sura, total);
        finish();
      }
    }
  }

  private void jumpToResult(int sura, int ayah) {
    int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
    Intent intent = new Intent(this, PagerActivity.class);
    intent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura);
    intent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah);
    if (!isArabicSearch) {
      intent.putExtra(PagerActivity.EXTRA_JUMP_TO_TRANSLATION, true);
    }
    intent.putExtra("page", page);
    startActivity(intent);
  }

  private void showResults(String query) {
    Bundle args = new Bundle();
    args.putString(EXTRA_QUERY, query);
    getSupportLoaderManager().restartLoader(0, args, this);
  }

  private static class ResultAdapter extends CursorAdapter {
    private LayoutInflater mInflater;
    private Context mContext;

    ResultAdapter(Context context, Cursor cursor) {
      super(context, cursor, 0);
      mInflater = LayoutInflater.from(context);
      mContext = context;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      final View view = mInflater.inflate(R.layout.search_result, parent, false);
      ViewHolder holder = new ViewHolder();
      holder.text = (TextView) view.findViewById(R.id.verseText);
      holder.metadata = (TextView) view.findViewById(R.id.verseLocation);
      view.setTag(holder);
      return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      final ViewHolder holder = (ViewHolder) view.getTag();
      int sura = cursor.getInt(1);
      int ayah = cursor.getInt(2);
      String text = cursor.getString(3);
      String suraName = QuranInfo.getSuraName(mContext, sura, false);
      holder.text.setText(Html.fromHtml(text));
      holder.metadata.setText(mContext.getString(R.string.found_in_sura, suraName, ayah));
    }

    static class ViewHolder {
      TextView text;
      TextView metadata;
    }
  }
}
