package com.quran.labs.androidquran

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.Button
import android.widget.CursorAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.presenter.data.ReaderReadinessTracker
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver.SimpleDownloadListener
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.service.util.ServiceIntentHelper.getDownloadIntent
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.TranslationManagerActivity
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for searching the Quran
 */
class SearchActivity : AppCompatActivity(), SimpleDownloadListener,
  LoaderManager.LoaderCallbacks<Cursor?> {
  private var downloadArabicSearchDb = false
  private var query: String = ""
  private var adapter: ResultAdapter? = null
  private var downloadReceiver: DefaultDownloadReceiver? = null

  /**
   * Routes at most one search suggestion at a time.
   *
   * Replacing this job prevents an older [onNewIntent] call from opening after a newer intent.
   */
  private var viewIntentJob: Job? = null

  private lateinit var messageView: TextView
  private lateinit var warningView: TextView
  private lateinit var buttonGetTranslations: Button

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  @Inject
  lateinit var quranFileUtils: QuranFileUtils

  @Inject
  lateinit var quranSettings: QuranSettings

  @Inject
  lateinit var readerReadinessTracker: ReaderReadinessTracker

  public override fun onCreate(savedInstanceState: Bundle?) {
    // override these to always be dark since the app doesn't really
    // have a light theme until now. without this, the clock color in
    // the status bar will be dark on a dark background.
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    (application as QuranApplication)
      .applicationComponent.inject(this)
    setContentView(R.layout.search)

    val root = findViewById<ViewGroup>(R.id.root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = insets.top
        leftMargin = insets.left
        rightMargin = insets.right
      }

      // if we return WindowInsetsCompat.CONSUMED, the SnackBar won't
      // be properly positioned on Android 29 and below (will be under
      // the navigation bar).
      windowInsets
    }

    val listView = findViewById<ListView>(R.id.results_list)
    ViewCompat.setOnApplyWindowInsetsListener(listView) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )

      listView.setPadding(0, 0, 0, insets.bottom)
      windowInsets
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.setTitle(R.string.menu_search)
    setSupportActionBar(toolbar)
    val ab = supportActionBar
    ab?.setDisplayHomeAsUpEnabled(true)

    messageView = findViewById(R.id.search_area)
    warningView = findViewById(R.id.search_warning)
    buttonGetTranslations = findViewById(R.id.btnGetTranslations)
    buttonGetTranslations.setOnClickListener { v: View? ->
      var intent: Intent?
      if (downloadArabicSearchDb) {
        downloadArabicSearchDb()
      } else {
        intent = Intent(applicationContext, TranslationManagerActivity::class.java)
        startActivity(intent)
        finish()
      }
    }
    handleIntent(intent)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    super.onCreateOptionsMenu(menu)
    menuInflater.inflate(R.menu.search_menu, menu)
    val searchItem = menu.findItem(R.id.search)
    val searchView = searchItem.actionView as SearchView?
    val searchManager = (getSystemService(SEARCH_SERVICE) as SearchManager)
    searchView?.setSearchableInfo(searchManager.getSearchableInfo(componentName))

    val intent = getIntent()
    if (Intent.ACTION_SEARCH == intent.action) {
      // Make sure the keyboard is hidden if doing a search from within this activity
      searchView?.clearFocus()
    } else if (intent.action == null) {
      // If no action is specified, just open the keyboard so the user can quickly start searching
      searchItem.expandActionView()
    }
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  public override fun onPause() {
    val receiver = downloadReceiver
    if (receiver != null) {
      receiver.setListener(null)
      LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
      downloadReceiver = null
    }
    super.onPause()
  }

  override fun onStop() {
    cancelPendingViewIntentNavigation()
    super.onStop()
  }

  private fun downloadArabicSearchDb() {
    if (downloadReceiver == null) {
      val receiver = DefaultDownloadReceiver(
        this, QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB
      )
      LocalBroadcastManager.getInstance(this).registerReceiver(
        receiver, IntentFilter(QuranDownloadNotifier.ProgressIntent.INTENT_NAME)
      )
      downloadReceiver = receiver
    }
    downloadReceiver?.setListener(this)

    val url = quranFileUtils.arabicSearchDatabaseUrl
    val notificationTitle = getString(R.string.search_data)
    val intent = getDownloadIntent(
      this, url,
      quranFileUtils.getQuranDatabaseDirectory().absolutePath,
      notificationTitle, SEARCH_INFO_DOWNLOAD_KEY,
      QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB
    )
    val extension = if (url.endsWith(".zip")) ".zip" else ""
    intent.putExtra(
      QuranDownloadService.EXTRA_OUTPUT_FILE_NAME,
      QuranDataProvider.QURAN_ARABIC_DATABASE + extension
    )
    startService(intent)
  }

  override fun handleDownloadSuccess() {
    warningView.visibility = View.GONE
    buttonGetTranslations.visibility = View.GONE
    handleIntent(intent)
  }

  override fun handleDownloadFailure(errId: Int) {
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor?> {
    this.query = args?.getString(EXTRA_QUERY) ?: ""
    return CursorLoader(
      this, QuranDataProvider.SEARCH_URI,
      null, null, arrayOf<String?>(query), null
    )
  }

  override fun onLoadFinished(loader: Loader<Cursor?>, cursor: Cursor?) {
    val containsArabic = QuranUtils.doesStringContainArabic(query)
    val showArabicWarning = containsArabic &&
        !quranFileUtils.hasTranslation(QuranDataProvider.QURAN_ARABIC_DATABASE)
    val jumpToTranslation = !containsArabic || showArabicWarning

    if (showArabicWarning) {
      // Without the Arabic database, Arabic tafseer matches should open in translation view.
      warningView.text = getString(R.string.no_arabic_search_available)
      warningView.visibility = View.VISIBLE
      buttonGetTranslations.text = getString(R.string.get_arabic_search_db)
      buttonGetTranslations.visibility = View.VISIBLE
      downloadArabicSearchDb = true
    } else {
      downloadArabicSearchDb = false
    }

    if (cursor == null) {
      messageView.text = getString(R.string.no_results, query)
      // cursor is null either when the query length is less than 3 characters or when
      // there are no valid databases to search at all. in this case, if it's not an
      // Arabic search, show the "get translations" button.
      if (!containsArabic && query.length > 2) {
        buttonGetTranslations.setText(R.string.get_translations)
        buttonGetTranslations.visibility = View.VISIBLE
      }
      if (adapter != null) {
        adapter?.swapCursor(null)
      }
    } else {
      // Display the number of results
      val count = cursor.count
      val countString = getResources().getQuantityString(
        R.plurals.search_results, count, query, count
      )
      messageView.text = countString

      val listView = findViewById<ListView>(R.id.results_list)
      if (adapter == null) {
        adapter = ResultAdapter(this, cursor, quranDisplayData, quranInfo)
        listView.adapter = adapter
      } else {
        adapter?.swapCursor(cursor)
      }
      listView.onItemClickListener =
        OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
          cancelPendingViewIntentNavigation()
          val p = parent as ListView
          val currentCursor = p.adapter.getItem(position) as Cursor
          jumpToResult(
            currentCursor.getInt(1),
            currentCursor.getInt(2),
            jumpToTranslation = jumpToTranslation
          )
        }
    }
  }

  override fun onLoaderReset(loader: Loader<Cursor?>) {
    adapter?.swapCursor(null)
  }

  private fun handleIntent(intent: Intent?) {
    if (intent == null) {
      return
    }

    cancelPendingViewIntentNavigation()

    if (Intent.ACTION_SEARCH == intent.action) {
      val query = intent.getStringExtra(SearchManager.QUERY)
      showResults(query)
    } else if (Intent.ACTION_VIEW == intent.action) {
      val intentData = intent.data
      var query = intent.getStringExtra(SearchManager.USER_QUERY)
      if (query == null) {
        val extras = intent.extras
        if (extras != null) {
          // bug on ics where the above returns null
          // http://code.google.com/p/android/issues/detail?id=22978
          val q = extras.get(SearchManager.USER_QUERY)
          if (q is SpannableString) {
            query = q.toString()
          }
        }
      }

      val id = intentData?.lastPathSegment?.toIntOrNull() ?: return
      if (id == -1) {
        showResults(query)
        return
      }
      if (id !in 1..quranInfo.getNumberOfAyahsInQuran()) return

      val (sura, ayah) = quranInfo.getSuraAyahFromAyahId(id)

      val isArabicQuery = QuranUtils.doesStringContainArabic(query)
      viewIntentJob = lifecycleScope.launch {
        // This check can copy the database. Await it off main before deciding where to open.
        val openAsArabic = isArabicQuery && withContext(Dispatchers.IO) {
          quranFileUtils.hasArabicSearchDatabase()
        }

        jumpToResult(sura, ayah, jumpToTranslation = !openAsArabic)
        finish()
      }
    }
  }

  private fun cancelPendingViewIntentNavigation() {
    viewIntentJob?.cancel()
    viewIntentJob = null
  }

  /**
   * Opens an ayah in the reader.
   *
   * @param sura the one-based sura number to open.
   * @param ayah the one-based ayah number to highlight.
   * @param jumpToTranslation whether the reader should open its translation view.
   */
  private fun jumpToResult(sura: Int, ayah: Int, jumpToTranslation: Boolean) {
    val page = quranInfo.getPageFromSuraAyah(sura, ayah)
    val intent = if (canOpenReaderDirectly()) {
      Intent(this, PagerActivity::class.java).apply {
        putExtra("page", page)
        putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura)
        putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah)
        if (jumpToTranslation) {
          putExtra(PagerActivity.EXTRA_JUMP_TO_TRANSLATION, true)
        }
      }
    } else {
      QuranDataActivity.openPageIntent(
        context = this,
        page = page,
        sura = sura,
        ayah = ayah,
        jumpToTranslation = jumpToTranslation
      )
    }
    startActivity(intent)
  }

  private fun canOpenReaderDirectly(): Boolean {
    return quranSettings.haveMigratedLegacyBookmarksToMobileSync() &&
      readerReadinessTracker.isReady(quranSettings.pageType)
  }

  private fun showResults(query: String?) {
    val args = Bundle()
    args.putString(EXTRA_QUERY, query)
    LoaderManager.getInstance<SearchActivity?>(this).restartLoader<Cursor?>(0, args, this)
  }

  private class ResultAdapter(
    private val context: Context,
    cursor: Cursor?,
    private val quranDisplayData: QuranDisplayData,
    private val quranInfo: QuranInfo
  ) : CursorAdapter(context, cursor, 0) {
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup?): View {
      val view = inflater.inflate(R.layout.search_result, parent, false)
      val holder = ViewHolder()
      holder.text = view.findViewById(R.id.verseText)
      holder.metadata = view.findViewById(R.id.verseLocation)
      view.tag = holder
      return view
    }

    override fun bindView(view: View, context: Context?, cursor: Cursor) {
      val holder = view.tag as ViewHolder
      val sura = cursor.getInt(1)
      val ayah = cursor.getInt(2)
      val page = quranInfo.getPageFromSuraAyah(sura, ayah)

      val text = cursor.getString(3)
      val suraName = quranDisplayData.getSuraName(this.context, sura, false)
      holder.text.text = Html.fromHtml(text)
      holder.metadata.text = this.context.getString(
        R.string.found_in_sura,
        sura,
        suraName,
        ayah,
        page
      )
    }

    private class ViewHolder {
      lateinit var text: TextView
      lateinit var metadata: TextView
    }
  }

  companion object {
    const val SEARCH_INFO_DOWNLOAD_KEY: String = "SEARCH_INFO_DOWNLOAD_KEY"
    private const val EXTRA_QUERY = "EXTRA_QUERY"
  }
}
