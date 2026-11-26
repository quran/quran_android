package com.quran.labs.androidquran

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quran.common.search.arabic.ArabicCompiledQuery
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.presenter.data.ReaderReadinessTracker
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver.SimpleDownloadListener
import com.quran.labs.androidquran.service.util.ServiceIntentHelper.getDownloadIntent
import com.quran.labs.androidquran.ui.TranslationManagerActivity
import com.quran.labs.androidquran.ui.helpers.QuranNavigator
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.SearchResultFormatter
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for searching the Quran.
 */
class SearchActivity : AppCompatActivity(), SimpleDownloadListener {
  private var downloadArabicSearchDb = false
  private var query: String = ""
  private var downloadReceiver: DefaultDownloadReceiver? = null

  /**
   * Routes at most one search suggestion at a time.
   *
   * Replacing this job prevents an older [onNewIntent] call from opening after a newer intent.
   */
  private var viewIntentJob: Job? = null
  private var searchJob: Job? = null
  private var jumpToTranslationForCurrentResults: Boolean = false

  private lateinit var messageView: TextView
  private lateinit var warningView: TextView
  private lateinit var buttonGetTranslations: Button
  private lateinit var resultsList: RecyclerView
  private lateinit var adapter: ResultAdapter

  @Inject lateinit var quranNavigatorFactory: QuranNavigator.Factory
  private val quranNavigator by lazy { quranNavigatorFactory.create(this) }

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

    resultsList = findViewById(R.id.results_list)
    resultsList.setHasFixedSize(true)
    resultsList.layoutManager = LinearLayoutManager(this)
    resultsList.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
    adapter = ResultAdapter(
      context = this,
      quranDisplayData = quranDisplayData,
      highlightColor = ContextCompat.getColor(this, R.color.translation_highlight),
      onItemClick = { item ->
        cancelPendingViewIntentNavigation()
        jumpToResult(
          item.sura,
          item.ayah,
          jumpToTranslation = jumpToTranslationForCurrentResults
        )
      }
    )
    resultsList.adapter = adapter

    ViewCompat.setOnApplyWindowInsetsListener(resultsList) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )

      resultsList.setPadding(0, 0, 0, insets.bottom)
      windowInsets
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.setTitle(R.string.menu_search)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    messageView = findViewById(R.id.search_area)
    warningView = findViewById(R.id.search_warning)
    buttonGetTranslations = findViewById(R.id.btnGetTranslations)
    buttonGetTranslations.setOnClickListener {
      if (downloadArabicSearchDb) {
        downloadArabicSearchDb()
      } else {
        val intent = Intent(applicationContext, TranslationManagerActivity::class.java)
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
    val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
    searchView?.setSearchableInfo(searchManager.getSearchableInfo(componentName))

    val currentIntent = intent
    if (Intent.ACTION_SEARCH == currentIntent.action) {
      // Make sure the keyboard is hidden if doing a search from within this activity
      searchView?.clearFocus()
    } else if (currentIntent.action == null) {
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
    downloadReceiver?.let { receiver ->
      receiver.unregister()
      downloadReceiver = null
    }
    super.onPause()
  }

  override fun onStop() {
    cancelPendingViewIntentNavigation()
    super.onStop()
  }

  override fun onDestroy() {
    searchJob?.cancel()
    searchJob = null
    SearchResultFormatter.clearCache()
    super.onDestroy()
  }

  private fun downloadArabicSearchDb() {
    if (downloadReceiver == null) {
      val receiver = DefaultDownloadReceiver(
        this, QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB
      )
      receiver.register(this)
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

  private fun handleIntent(intent: Intent?) {
    if (intent == null) {
      return
    }

    cancelPendingViewIntentNavigation()

    if (Intent.ACTION_SEARCH == intent.action) {
      val searchQuery = intent.getStringExtra(SearchManager.QUERY)
      showResults(searchQuery)
    } else if (Intent.ACTION_VIEW == intent.action) {
      val intentData = intent.data
      var userQuery = intent.getStringExtra(SearchManager.USER_QUERY)
      if (userQuery == null) {
        val extras = intent.extras
        if (extras != null) {
          @Suppress("DEPRECATION")
          val q = extras.get(SearchManager.USER_QUERY)
          if (q is SpannableString) {
            userQuery = q.toString()
          }
        }
      }

      val id = intentData?.lastPathSegment?.toIntOrNull() ?: return
      if (id == -1) {
        showResults(userQuery)
        return
      }
      if (id !in 1..quranInfo.getNumberOfAyahsInQuran()) return

      val (sura, ayah) = quranInfo.getSuraAyahFromAyahId(id)

      val isArabicQuery = QuranUtils.doesStringContainArabic(userQuery)
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
    if (canOpenReaderDirectly()) {
      quranNavigator.jumpTo(SuraAyah(sura, ayah), showTranslation = jumpToTranslation)
    } else {
      startActivity(Intent(this, QuranDataActivity::class.java))
      finish()
    }
  }

  private fun canOpenReaderDirectly(): Boolean {
    return quranSettings.haveMigratedLegacyBookmarksToMobileSync() &&
      readerReadinessTracker.isReady(quranSettings.pageType)
  }

  private fun showResults(searchQuery: String?) {
    val currentQuery = searchQuery ?: ""
    this.query = currentQuery
    searchJob?.cancel()

    val containsArabic = QuranUtils.doesStringContainArabic(currentQuery)
    val compiledArabicQuery = if (containsArabic) {
      ArabicCompiledQuery.compile(currentQuery)
    } else {
      null
    }
    val useRawArabic = containsArabic && compiledArabicQuery != null

    searchJob = lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        executeSearchQuery(
          currentQuery = currentQuery,
          containsArabic = containsArabic,
          useRawArabic = useRawArabic
        )
      }
      if (!isActive || currentQuery != query) {
        return@launch
      }
      renderSearchResults(
        currentQuery = currentQuery,
        containsArabic = containsArabic,
        result = result
      )
    }
  }

  private fun executeSearchQuery(
    currentQuery: String,
    containsArabic: Boolean,
    useRawArabic: Boolean
  ): SearchQueryResult {
    val searchUri = if (useRawArabic) {
      QuranDataProvider.SEARCH_RAW_ARABIC_URI
    } else {
      QuranDataProvider.SEARCH_URI
    }
    val highlightQuery = if (useRawArabic) currentQuery else null

    val items: List<SearchResultItem>? = contentResolver.query(
      searchUri,
      null,
      null,
      arrayOf(currentQuery),
      null
    )?.use { cursor ->
      val list = ArrayList<SearchResultItem>(cursor.count)
      while (cursor.moveToNext()) {
        val id = cursor.getLong(0)
        val sura = cursor.getInt(1)
        val ayah = cursor.getInt(2)
        val text = cursor.getString(3) ?: ""
        val page = quranInfo.getPageFromSuraAyah(sura, ayah)
        list.add(
          SearchResultItem(
            id = id,
            sura = sura,
            ayah = ayah,
            page = page,
            text = text,
            highlightQuery = highlightQuery
          )
        )
      }
      list
    }

    // Arabic queries in QuranDataProvider only return matches from the Arabic database, so
    // non-empty Arabic results already prove the database exists without an extra stat().
    val needsArabicDbWarning = containsArabic &&
      items.isNullOrEmpty() &&
      !quranFileUtils.hasTranslation(QuranDataProvider.QURAN_ARABIC_DATABASE)

    return SearchQueryResult(items = items, needsArabicDbWarning = needsArabicDbWarning)
  }

  private fun renderSearchResults(
    currentQuery: String,
    containsArabic: Boolean,
    result: SearchQueryResult
  ) {
    downloadArabicSearchDb = false
    jumpToTranslationForCurrentResults = !containsArabic
    warningView.visibility = View.GONE
    buttonGetTranslations.visibility = View.GONE

    if (result.needsArabicDbWarning) {
      // Without the Arabic database, Arabic tafseer matches should open in translation view.
      warningView.text = getString(R.string.no_arabic_search_available)
      warningView.visibility = View.VISIBLE
      buttonGetTranslations.text = getString(R.string.get_arabic_search_db)
      buttonGetTranslations.visibility = View.VISIBLE
      downloadArabicSearchDb = true
      jumpToTranslationForCurrentResults = true
    }

    val items = result.items
    if (items == null) {
      messageView.text = getString(R.string.no_results, currentQuery)
      // cursor is null either when the query length is less than 3 characters or when
      // there are no valid databases to search at all. in this case, if it's not an
      // Arabic search, show the "get translations" button.
      if (!containsArabic && currentQuery.length > 2) {
        buttonGetTranslations.setText(R.string.get_translations)
        buttonGetTranslations.visibility = View.VISIBLE
      }
      adapter.submitList(emptyList())
    } else {
      val count = items.size
      val countString = resources.getQuantityString(
        R.plurals.search_results, count, currentQuery, count
      )
      messageView.text = countString
      adapter.submitList(items)
    }
  }

  @VisibleForTesting
  internal data class SearchResultItem(
    val id: Long,
    val sura: Int,
    val ayah: Int,
    val page: Int,
    val text: String,
    val highlightQuery: String? = null
  )

  private data class SearchQueryResult(
    val items: List<SearchResultItem>?,
    val needsArabicDbWarning: Boolean
  )

  private class ResultAdapter(
    private val context: Context,
    private val quranDisplayData: QuranDisplayData,
    @ColorInt private val highlightColor: Int,
    private val onItemClick: (SearchResultItem) -> Unit
  ) : ListAdapter<SearchResultItem, ResultAdapter.ViewHolder>(DIFF_CALLBACK) {
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      val view = inflater.inflate(R.layout.search_result, parent, false)
      return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val item = getItem(position)
      val suraName = quranDisplayData.getSuraName(context, item.sura, false)
      val compiledQuery = item.highlightQuery?.let { ArabicCompiledQuery.compile(it) }
      holder.text.text = SearchResultFormatter.formatArabic(
        text = item.text,
        compiledQuery = compiledQuery,
        highlightColor = highlightColor
      )
      holder.metadata.text = context.getString(
        R.string.found_in_sura,
        item.sura,
        suraName,
        item.ayah,
        item.page
      )
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val text: TextView = itemView.findViewById(R.id.verseText)
      val metadata: TextView = itemView.findViewById(R.id.verseLocation)

      init {
        itemView.setOnClickListener {
          val position = bindingAdapterPosition
          if (position != RecyclerView.NO_POSITION) {
            onItemClick(getItem(position))
          }
        }
      }
    }

    companion object {
      private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SearchResultItem>() {
        override fun areItemsTheSame(
          oldItem: SearchResultItem,
          newItem: SearchResultItem
        ): Boolean = oldItem.id == newItem.id && oldItem.sura == newItem.sura && oldItem.ayah == newItem.ayah

        override fun areContentsTheSame(
          oldItem: SearchResultItem,
          newItem: SearchResultItem
        ): Boolean = oldItem == newItem
      }
    }
  }

  companion object {
    const val SEARCH_INFO_DOWNLOAD_KEY: String = "SEARCH_INFO_DOWNLOAD_KEY"
  }
}
