package com.quran.labs.androidquran

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.Button
import android.widget.CursorAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.SearchActivity.ResultAdapter
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver.SimpleDownloadListener
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.service.util.ServiceIntentHelper.getDownloadIntent
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.TranslationManagerActivity
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranUtils
import java.lang.NumberFormatException
import javax.inject.Inject

/**
 * Activity for searching the Quran
 */
class SearchActivity : AppCompatActivity(), SimpleDownloadListener,
  LoaderManager.LoaderCallbacks<Cursor?> {
  private var downloadArabicSearchDb = false
  private var isArabicSearch = false
  private var query: String = ""
  private var adapter: ResultAdapter? = null
  private var downloadReceiver: DefaultDownloadReceiver? = null

  private lateinit var messageView: TextView
  private lateinit var warningView: TextView
  private lateinit var buttonGetTranslations: Button

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  @Inject
  lateinit var quranFileUtils: QuranFileUtils

  public override fun onCreate(savedInstanceState: Bundle?) {
    // override these to always be dark since the app doesn't really
    // have a light theme until now. without this, the clock color in
    // the status bar will be dark on a dark background.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
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
        bottomMargin = insets.bottom
        leftMargin = insets.left
        rightMargin = insets.right
      }

      // if we return WindowInsetsCompat.CONSUMED, the SnackBar won't
      // be properly positioned on Android 29 and below (will be under
      // the navigation bar).
      windowInsets
    }

    messageView = findViewById<TextView>(R.id.search_area)
    warningView = findViewById<TextView>(R.id.search_warning)
    buttonGetTranslations = findViewById<Button>(R.id.btnGetTranslations)
    buttonGetTranslations.setOnClickListener(View.OnClickListener { v: View? ->
      var intent: Intent?
      if (downloadArabicSearchDb) {
        downloadArabicSearchDb()
      } else {
        intent = Intent(applicationContext, TranslationManagerActivity::class.java)
        startActivity(intent)
        finish()
      }
    })
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

  public override fun onPause() {
    val receiver = downloadReceiver
    if (receiver != null) {
      receiver.setListener(null)
      LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
      downloadReceiver = null
    }
    super.onPause()
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
    isArabicSearch = containsArabic
    @SuppressLint("WrongThread") val showArabicWarning = (isArabicSearch &&
        !quranFileUtils.hasArabicSearchDatabase())

    if (showArabicWarning) {
      // overridden because if we search Arabic tafaseer, this tells us to go
      // to the tafseer page instead of the Arabic page when we open the result.
      isArabicSearch = false

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
        listView.onItemClickListener =
          OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
            val p = parent as ListView
            val currentCursor = p.adapter.getItem(position) as Cursor
            jumpToResult(currentCursor.getInt(1), currentCursor.getInt(2))
          }
      } else {
        adapter?.swapCursor(cursor)
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

      if (QuranUtils.doesStringContainArabic(query)) {
        isArabicSearch = true
      }

      if (isArabicSearch) {
        // if we come from muyassar and don't have arabic db, we set
        // arabic search to false so we jump to the translation.
        if (!quranFileUtils.hasArabicSearchDatabase()) {
          isArabicSearch = false
        }
      }

      var id: Int? = null
      try {
        if (intentData != null) {
          id = if (intentData.lastPathSegment != null) intentData.lastPathSegment?.toInt() else null
        }
      } catch (e: NumberFormatException) {
        // no op
      }

      if (id != null) {
        if (id == -1) {
          showResults(query)
          return
        }
        var sura = 1
        var total = id
        for (j in 1..114) {
          val cnt = quranInfo.getNumberOfAyahs(j)
          total -= cnt
          if (total >= 0) sura++
          else {
            total += cnt
            break
          }
        }

        if (total == 0) {
          sura--
          total = quranInfo.getNumberOfAyahs(sura)
        }

        jumpToResult(sura, total)
        finish()
      }
    }
  }

  private fun jumpToResult(sura: Int, ayah: Int) {
    val page = quranInfo.getPageFromSuraAyah(sura, ayah)
    val intent = Intent(this, PagerActivity::class.java)
    intent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura)
    intent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah)
    if (!isArabicSearch) {
      intent.putExtra(PagerActivity.EXTRA_JUMP_TO_TRANSLATION, true)
    }
    intent.putExtra("page", page)
    startActivity(intent)
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
      holder.text = view.findViewById<TextView>(R.id.verseText)
      holder.metadata = view.findViewById<TextView>(R.id.verseLocation)
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
