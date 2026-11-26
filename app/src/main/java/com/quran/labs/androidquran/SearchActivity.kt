package com.quran.labs.androidquran

import android.app.SearchManager
import android.content.Intent
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.text.SpannableString
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.Page
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.hybrid.contract.DailyWirdRepository
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.search.HybridBookmarkToggler
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.hybrid.search.HybridSearchScope
import com.quran.labs.androidquran.hybrid.search.NativeHybridSearchView
import com.quran.labs.androidquran.hybrid.ui.FloatingBottomNavView
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.hybrid.ui.PostDestroyMemoryReclaimer
import com.quran.labs.androidquran.presenter.data.ReaderReadinessTracker
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver.SimpleDownloadListener
import com.quran.labs.androidquran.service.util.ServiceIntentHelper.getDownloadIntent
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.ui.TranslationManagerActivity
import com.quran.labs.androidquran.ui.helpers.QuranNavigator
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.SearchResultFormatter
import com.quran.labs.androidquran.util.ThemeUtil
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Standalone Activity hosting Proposal 5 Calm Rust Search.
 */
class SearchActivity : AppCompatActivity(), SimpleDownloadListener {
  private var downloadReceiver: DefaultDownloadReceiver? = null

  /**
   * Routes at most one search suggestion at a time.
   *
   * Replacing this job prevents an older [onNewIntent] call from opening after a newer intent.
   */
  private var viewIntentJob: Job? = null

  @Inject lateinit var quranNavigatorFactory: QuranNavigator.Factory
  private val quranNavigator by lazy { quranNavigatorFactory.create(this) }

  @Inject lateinit var quranInfo: QuranInfo
  @Inject lateinit var quranFileUtils: QuranFileUtils
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var readerReadinessTracker: ReaderReadinessTracker
  @Inject lateinit var hybridSearchEngine: HybridSearchEngine
  @Inject lateinit var hybridBookmarkToggler: HybridBookmarkToggler
  @Inject lateinit var dailyWirdRepository: DailyWirdRepository
  @Inject lateinit var appCoroutineScope: AppCoroutineScope

  @VisibleForTesting
  internal lateinit var rootView: FrameLayout
    private set

  @VisibleForTesting
  internal lateinit var nativeSearchView: NativeHybridSearchView
    private set

  @VisibleForTesting
  internal lateinit var bottomFadeView: View
    private set

  @VisibleForTesting
  internal lateinit var bottomNavView: FloatingBottomNavView
    private set

  private var appliedThemeStyle: AppThemeStyle = AppThemeStyle.DEFAULT
  private var appliedDarkMode: Boolean = false

  private val searchActionHandler = object : HybridSearchActionHandler {
    override fun openPage(page: Int, sura: Int, ayah: Int) {
      cancelPendingViewIntentNavigation()
      val state = hybridSearchEngine.uiState.value
      val jumpToTranslation = state.scope == HybridSearchScope.TAFSIR ||
        state.needsArabicDbWarning ||
        (state.scope == HybridSearchScope.AYAHS &&
          state.query.isNotBlank() &&
          !QuranUtils.doesStringContainArabic(state.query))
      if (sura > 0 && ayah > 0) {
        jumpToResult(sura, ayah, jumpToTranslation = jumpToTranslation)
      } else if (canOpenReaderDirectly()) {
        quranNavigator.jumpTo(Page(page.coerceAtLeast(1)), showTranslation = jumpToTranslation)
      } else {
        startActivity(Intent(this@SearchActivity, QuranDataActivity::class.java))
        finish()
      }
    }

    override fun playAyah(sura: Int, ayah: Int, page: Int) {
      cancelPendingViewIntentNavigation()
      if (canOpenReaderDirectly()) {
        val target = if (sura > 0 && ayah > 0) {
          SuraAyah(sura, ayah)
        } else {
          Page(page.coerceAtLeast(1))
        }
        val playIntent = quranNavigator.intentFor(target, showTranslation = false).apply {
          if (sura > 0 && ayah > 0) {
            HybridReaderContract.applyToIntent(
              intent = this,
              sura = sura,
              ayah = ayah,
              initialStudyTab = HybridReaderContract.StudyTab.RECITATION,
              autoPlayAyah = true
            )
          }
        }
        startActivity(playIntent)
      } else {
        startActivity(Intent(this@SearchActivity, QuranDataActivity::class.java))
        finish()
      }
    }

    override fun toggleBookmark(sura: Int, ayah: Int, page: Int) {
      appCoroutineScope.launch {
        hybridBookmarkToggler.toggleAyahBookmark(sura, ayah)
      }
    }
  }

  public override fun onCreate(savedInstanceState: Bundle?) {
    appliedThemeStyle = ThemeUtil.applyActivityTheme(this)
    ThemeUtil.enableThemedEdgeToEdge(this)
    super.onCreate(savedInstanceState)

    (application as QuranApplication)
      .applicationComponent.inject(this)

    // Launch IO search immediately before waiting for first frame to keep
    // intent_to_rust_done_ms <= 32.5 ms.
    handleIntent(intent)

    val isSystemDark =
      (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    appliedDarkMode = ThemeUtil.isDarkAppearance(quranSettings.currentTheme(), isSystemDark)
    val palette = HybridViewPalette.from(appliedThemeStyle, appliedDarkMode)

    rootView = FrameLayout(this).apply {
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      background = HybridViewTokens.canvasAmbientGlowDrawable(
        surface = palette.surface,
        surfaceTopGlow = palette.surfaceTopGlow
      )
    }

    nativeSearchView = NativeHybridSearchView(this).apply {
      applyPalette(palette)
      onDownloadArabicSearchDb = ::downloadArabicSearchDb
      onOpenTranslationManager = ::openTranslationManager
      bind(hybridSearchEngine, searchActionHandler, lifecycleScope, this@SearchActivity)
    }

    bottomFadeView = View(this).apply {
      isClickable = false
      isFocusable = false
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
      background = HybridViewTokens.scrollUnderVeilDrawable(palette.surface)
    }

    bottomNavView = FloatingBottomNavView(this).apply {
      applyPalette(palette)
      setSelectedTab(HybridNavTab.SEARCH)
      setOnTabSelectedListener(::handleBottomNavTabSelected)
    }

    val navHeightEstimate = HybridViewTokens.dp(this, 96f)
    nativeSearchView.setBottomNavOverlapPadding(navHeightEstimate)

    rootView.addView(
      nativeSearchView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    )
    rootView.addView(
      bottomFadeView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        navHeightEstimate,
        Gravity.BOTTOM
      )
    )
    rootView.addView(
      bottomNavView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM
      )
    )

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
      val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
      val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
      val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
      val imeVisible = imeBottom > 0

      nativeSearchView.setStatusBarTopInset(statusTop)
      rootView.setPadding(0, 0, 0, imeBottom)

      if (imeVisible) {
        bottomFadeView.visibility = View.GONE
        bottomNavView.visibility = View.GONE
        nativeSearchView.setBottomNavOverlapPadding(HybridViewTokens.dp(this, 12f))
      } else {
        bottomFadeView.visibility = View.VISIBLE
        bottomNavView.visibility = View.VISIBLE
        bottomNavView.setPadding(
          HybridViewTokens.dp(this, 14f),
          HybridViewTokens.dp(this, 2f),
          HybridViewTokens.dp(this, 14f),
          navBottom + HybridViewTokens.dp(this, 12f)
        )
        val totalNavSpace = navBottom + HybridViewTokens.dp(this, 96f)
        (bottomFadeView.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
          if (lp.height != totalNavSpace) {
            lp.height = totalNavSpace
            bottomFadeView.layoutParams = lp
          }
        }
        nativeSearchView.setBottomNavOverlapPadding(totalNavSpace)
      }
      insets
    }

    setContentView(rootView)
    window?.setBackgroundDrawable(null)
  }

  override fun onResume() {
    super.onResume()
    val isSystemDark =
      (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val nowDark = ThemeUtil.isDarkAppearance(quranSettings.currentTheme(), isSystemDark)
    if (AppThemeStyle.fromPreference(quranSettings.appThemeStyle) != appliedThemeStyle ||
      nowDark != appliedDarkMode
    ) {
      recreate()
      return
    }
    if (::nativeSearchView.isInitialized) {
      nativeSearchView.restoreInputIfNeeded()
    }
  }

  public override fun onPause() {
    downloadReceiver?.let { receiver ->
      receiver.unregister()
      downloadReceiver = null
    }
    if (isFinishing && !isChangingConfigurations && ::nativeSearchView.isInitialized) {
      nativeSearchView.releaseFocusAndInput()
    }
    super.onPause()
  }

  override fun onStop() {
    cancelPendingViewIntentNavigation()
    if (isFinishing && !isChangingConfigurations) {
      if (::nativeSearchView.isInitialized) {
        nativeSearchView.releaseFocusAndInput()
        nativeSearchView.resultsRecyclerView.adapter = null
        nativeSearchView.resultsRecyclerView.recycledViewPool.clear()
        nativeSearchView.clearRecentsViews()
      }
      hybridSearchEngine.clearTransientResults()
    } else {
      hybridSearchEngine.trimTransientWindow()
    }
    if (!isChangingConfigurations) {
      SQLiteDatabase.releaseMemory()
    }
    super.onStop()
  }

  override fun onDestroy() {
    cancelPendingViewIntentNavigation()
    downloadReceiver?.let { receiver ->
      receiver.unregister()
      downloadReceiver = null
    }
    SearchResultFormatter.clearCache()
    val finishingCleanly = isFinishing && !isChangingConfigurations
    if (finishingCleanly) {
      hybridSearchEngine.clearTransientResults()
    }
    if (::bottomNavView.isInitialized) {
      bottomNavView.setOnTabSelectedListener(null)
      bottomNavView.unbind()
    }
    if (::nativeSearchView.isInitialized) {
      nativeSearchView.resultsRecyclerView.adapter = null
      nativeSearchView.resultsRecyclerView.recycledViewPool.clear()
      nativeSearchView.unbind()
      nativeSearchView.removeAllViews()
    }
    if (::rootView.isInitialized) {
      ViewCompat.setOnApplyWindowInsetsListener(rootView, null)
      rootView.removeAllViews()
    }
    window?.setBackgroundDrawable(null)
    super.onDestroy()
    if (finishingCleanly) {
      PostDestroyMemoryReclaimer.schedulePostDestroyCleanup()
    }
  }

  private fun openTranslationManager() {
    startActivity(Intent(applicationContext, TranslationManagerActivity::class.java))
    finish()
  }

  private fun handleBottomNavTabSelected(tab: HybridNavTab) {
    when (tab) {
      HybridNavTab.READ -> {
        if (canOpenReaderDirectly()) {
          val lastPage = dailyWirdRepository.stateFlow.value.lastPage.coerceIn(1, quranInfo.numberOfPages)
          quranNavigator.jumpTo(
            Page(lastPage),
            showTranslation = quranSettings.wasShowingTranslation
          )
        } else {
          startActivity(Intent(this, QuranDataActivity::class.java))
          finish()
        }
      }

      HybridNavTab.INDEX, HybridNavTab.BOOKMARKS -> {
        val intent = Intent(this, QuranActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
          putExtra(QuranActivity.EXTRA_INITIAL_NAV_TAB, tab.name)
        }
        startActivity(intent)
        finish()
      }

      HybridNavTab.SEARCH -> Unit
    }
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
    hybridSearchEngine.refreshCurrentSearch()
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
      val searchQuery = intent.getStringExtra(SearchManager.QUERY).orEmpty()
      hybridSearchEngine.submitQuery(
        rawQuery = searchQuery,
        scopeOverride = HybridSearchScope.AYAHS,
        recordRecent = true
      )
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
        hybridSearchEngine.submitQuery(
          rawQuery = userQuery.orEmpty(),
          scopeOverride = HybridSearchScope.AYAHS,
          recordRecent = true
        )
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

  companion object {
    const val SEARCH_INFO_DOWNLOAD_KEY: String = "SEARCH_INFO_DOWNLOAD_KEY"
  }
}
