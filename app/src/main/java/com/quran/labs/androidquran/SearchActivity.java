package com.quran.labs.androidquran;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.quran.data.core.QuranInfo;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranDisplayData;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.TranslationManagerActivity;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranUtils;

import javax.inject.Inject;

/**
 * Activity for searching the Quran
 */
public class SearchActivity extends AppCompatActivity
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

  @Inject QuranInfo quranInfo;
  @Inject QuranDisplayData quranDisplayData;
  @Inject QuranFileUtils quranFileUtils;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ((QuranApplication) getApplication())
        .getApplicationComponent().inject(this);
    setContentView(R.layout.search);
    messageView = findViewById(R.id.search_area);
    warningView = findViewById(R.id.search_warning);
    buttonGetTranslations = findViewById(R.id.btnGetTranslations);
    buttonGetTranslations.setOnClickListener(v -> {
      Intent intent;
      if (downloadArabicSearchDb) {
        downloadArabicSearchDb();
        return;
      } else {
        intent = new Intent(getApplicationContext(), TranslationManagerActivity.class);
      }
      startActivity(intent);
      finish();
    });
    handleIntent(getIntent());
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.menu.search_menu, menu);
    MenuItem searchItem = menu.findItem(R.id.search);
    SearchView searchView = (SearchView) searchItem.getActionView();
    SearchManager searchManager = ((SearchManager) getSystemService(Context.SEARCH_SERVICE));
    searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

    Intent intent = getIntent();
    if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
      // Make sure the keyboard is hidden if doing a search from within this activity
      searchView.clearFocus();
    } else if (intent.getAction() == null){
      // If no action is specified, just open the keyboard so the user can quickly start searching
      searchItem.expandActionView();
    }
    return true;
  }

  @Override
  public void onPause() {
    if (downloadReceiver != null) {
      downloadReceiver.setListener(null);
      LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver);
      downloadReceiver = null;
    }
    super.onPause();
  }

  private void downloadArabicSearchDb() {
    if (downloadReceiver == null) {
      downloadReceiver = new DefaultDownloadReceiver(this,
          QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB);
      LocalBroadcastManager.getInstance(this).registerReceiver(
          downloadReceiver, new IntentFilter(QuranDownloadNotifier.ProgressIntent.INTENT_NAME));
    }
    downloadReceiver.setListener(this);

    String url = quranFileUtils.getArabicSearchDatabaseUrl();
    String notificationTitle = getString(R.string.search_data);
    Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
        quranFileUtils.getQuranDatabaseDirectory(this),
        notificationTitle, SEARCH_INFO_DOWNLOAD_KEY,
        QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB);
    final String extension = url.endsWith(".zip") ? ".zip" : "";
    intent.putExtra(QuranDownloadService.EXTRA_OUTPUT_FILE_NAME,
        QuranDataProvider.QURAN_ARABIC_DATABASE + extension);
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
    super.onNewIntent(intent);
    setIntent(intent);
    handleIntent(intent);
  }

  @NonNull
  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    String query = args.getString(EXTRA_QUERY);
    this.query = query;
    return new CursorLoader(this, QuranDataProvider.SEARCH_URI,
        null, null, new String[]{ query }, null);
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
    final boolean containsArabic = QuranUtils.doesStringContainArabic(query);
    isArabicSearch = containsArabic;
    @SuppressLint("WrongThread") boolean showArabicWarning = (isArabicSearch &&
        !quranFileUtils.hasArabicSearchDatabase());

    if (showArabicWarning) {
      // overridden because if we search Arabic tafaseer, this tells us to go
      // to the tafseer page instead of the Arabic page when we open the result.
      isArabicSearch = false;

      warningView.setText(getString(R.string.no_arabic_search_available));
      warningView.setVisibility(View.VISIBLE);
      buttonGetTranslations.setText(getString(R.string.get_arabic_search_db));
      buttonGetTranslations.setVisibility(View.VISIBLE);
      downloadArabicSearchDb = true;
    } else {
      downloadArabicSearchDb = false;
    }

    if (cursor == null) {
      messageView.setText(getString(R.string.no_results, query));
      // cursor is null either when the query length is less than 3 characters or when
      // there are no valid databases to search at all. in this case, if it's not an
      // Arabic search, show the "get translations" button.
      if (!containsArabic && query.length() > 2) {
        buttonGetTranslations.setText(R.string.get_translations);
        buttonGetTranslations.setVisibility(View.VISIBLE);
      }
      if (adapter != null) {
        adapter.swapCursor(null);
      }
    } else {
      // Display the number of results
      int count = cursor.getCount();
      String countString = getResources().getQuantityString(
          R.plurals.search_results, count, query, count);
      messageView.setText(countString);

      ListView listView = findViewById(R.id.results_list);
      if (adapter == null) {
        adapter = new ResultAdapter(this, cursor, quranDisplayData, quranInfo);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
          ListView p = (ListView) parent;
          final Cursor currentCursor = (Cursor) p.getAdapter().getItem(position);
          jumpToResult(currentCursor.getInt(1), currentCursor.getInt(2));
        });
      } else {
        adapter.swapCursor(cursor);
      }
    }
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> loader) {
    if (adapter != null) {
      adapter.swapCursor(null);
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
          if (q instanceof SpannableString) {
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
        if (!quranFileUtils.hasArabicSearchDatabase()) {
          isArabicSearch = false;
        }
      }

      Integer id = null;
      try {
        if (intentData != null) {
          id = intentData.getLastPathSegment() != null ?
              Integer.valueOf(intentData.getLastPathSegment()) : null;
        }
      } catch (NumberFormatException e) {
        // no op
      }

      if (id != null) {
        if (id == -1) {
          showResults(query);
          return;
        }
        int sura = 1;
        int total = id;
        for (int j = 1; j <= 114; j++) {
          int cnt = quranInfo.getNumberOfAyahs(j);
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
          total = quranInfo.getNumberOfAyahs(sura);
        }

        jumpToResult(sura, total);
        finish();
      }
    }
  }

  private void jumpToResult(int sura, int ayah) {
    int page = quranInfo.getPageFromSuraAyah(sura, ayah);
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
    LoaderManager.getInstance(this).restartLoader(0, args, this);
  }

  private static class ResultAdapter extends CursorAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final QuranInfo quranInfo;
    private final QuranDisplayData quranDisplayData;

    ResultAdapter(Context context, Cursor cursor, QuranDisplayData quranDisplayData, QuranInfo quranInfo) {
      super(context, cursor, 0);
      inflater = LayoutInflater.from(context);
      this.context = context;
      this.quranDisplayData = quranDisplayData;
      this.quranInfo = quranInfo;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      final View view = inflater.inflate(R.layout.search_result, parent, false);
      ViewHolder holder = new ViewHolder();
      holder.text = view.findViewById(R.id.verseText);
      holder.metadata = view.findViewById(R.id.verseLocation);
      view.setTag(holder);
      return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      final ViewHolder holder = (ViewHolder) view.getTag();
      int sura = cursor.getInt(1);
      int ayah = cursor.getInt(2);
      int page = quranInfo.getPageFromSuraAyah(sura, ayah);

      String text = cursor.getString(3);
      String suraName = quranDisplayData.getSuraName(this.context, sura, false);
      holder.text.setText(Html.fromHtml(text));
      holder.metadata.setText(this.context.getString(R.string.found_in_sura, suraName, ayah, page));
    }

    static class ViewHolder {
      TextView text;
      TextView metadata;
    }
  }
}
