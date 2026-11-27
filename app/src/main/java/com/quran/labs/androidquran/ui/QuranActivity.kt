package com.quran.labs.androidquran.ui

import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.quran.data.dao.RecentPagesDao
import com.quran.data.model.Page
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.QuranPreferenceActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.SearchActivity
import com.quran.labs.androidquran.ShortcutsActivity
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.feature.reading.model.LatestPageTracker
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.hub.DailyWirdGoalDialogNative
import com.quran.labs.androidquran.hybrid.hub.HybridHubStateHolder
import com.quran.labs.androidquran.hybrid.hub.HybridHubView
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.presenter.data.QuranIndexEventLogger
import com.quran.labs.androidquran.presenter.translation.TranslationManagerPresenter
import com.quran.labs.androidquran.service.AudioService
import com.quran.labs.androidquran.ui.fragment.AddTagDialog
import com.quran.labs.androidquran.ui.fragment.AddTagDialog.Companion.newInstance
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog.OnBookmarkTagsUpdateListener
import com.quran.labs.androidquran.ui.helpers.JumpDestination
import com.quran.labs.androidquran.ui.helpers.QuranNavigator
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.mobile.di.ExtraScreenProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * The Proposal 5 Main Card Hub activity hosting:
 *  - Hero "Continue Reading" + Daily Wird progress card
 *  - Segmented Index (Surahs | Juz | Hizb)
 *  - Embedded Search tab ([HybridSearchHost])
 *  - Saved Bookmarks & Recents tab
 *  - Floating 4-tab bottom navigation ([HybridNavTab])
 */
class QuranActivity : AppCompatActivity(),
    OnBookmarkTagsUpdateListener,
    JumpDestination {
  private var upgradeDialog: AlertDialog? = null
  private var showedTranslationUpgradeDialog = false
  private var isRtl = false
  private var isPaused = false
  private var supportActionMode: ActionMode? = null
  private val compositeDisposable = CompositeDisposable()
  private val latestPageFlow: Flow<Int> by lazy {
    combine(
      recentPagesDao().recentPagesFlow()
        .map { recentPages -> recentPages.firstOrNull()?.page ?: Constants.NO_PAGE },
      latestPageTracker().latestPage
    ) { persistedPage, latestPage ->
      latestPage
        ?.takeIf { it.pageType == settings.pageType }
        ?.page
        ?: persistedPage
    }
      .distinctUntilChanged()
  }

  suspend fun latestPage(): Int {
    return latestPageFlow.first()
  }

  private var backStackListener: FragmentManager.OnBackStackChangedListener? = null
  private lateinit var hubBackCallback: OnBackPressedCallback
  private lateinit var supportActionModeClearingCallback: OnBackPressedCallback

  private var showPaletteSheet: Boolean = false
  private var showWirdGoalDialog: Boolean = false
  private var wirdGoalDialog: AlertDialog? = null

  @VisibleForTesting
  internal lateinit var hubView: HybridHubView
    private set

  @VisibleForTesting
  internal val isPaletteSheetShownForTest: Boolean
    get() = showPaletteSheet

  @VisibleForTesting
  internal val appliedThemeStyleForTest: AppThemeStyle
    get() = appliedThemeStyle

  @VisibleForTesting
  internal val appliedOledDarkModeForTest: Boolean
    get() = appliedOledDarkMode

  @Inject lateinit var quranNavigator: Provider<QuranNavigator>
  @Inject lateinit var settings: QuranSettings
  @Inject lateinit var audioUtils: Provider<AudioUtils>
  @Inject lateinit var recentPagesDao: Provider<RecentPagesDao>
  @Inject lateinit var latestPageTracker: Provider<LatestPageTracker>
  @Inject lateinit var translationManagerPresenter: Provider<TranslationManagerPresenter>
  @Inject lateinit var quranIndexEventLogger: Provider<QuranIndexEventLogger>
  @Inject lateinit var extraScreens: Set<@JvmSuppressWildcards ExtraScreenProvider>
  @Inject lateinit var hybridSearchEngine: Provider<HybridSearchEngine>
  @Inject lateinit var hubStateHolder: HybridHubStateHolder

  private var appliedThemeStyle: AppThemeStyle = AppThemeStyle.DEFAULT
  private var appliedDarkMode: Boolean = false
  private var appliedOledDarkMode: Boolean = false

  private val searchActionHandler = object : HybridSearchActionHandler {
    override fun openPage(page: Int, sura: Int, ayah: Int) {
      jumpToAndHighlight(page, sura, ayah)
    }

    override fun playAyah(sura: Int, ayah: Int, page: Int) {
      val intent = quranNavigator().intentFor(
        SuraAyah(sura, ayah),
        showTranslation = settings.wasShowingTranslation
      )
      HybridReaderContract.applyToIntent(
        intent = intent,
        sura = sura,
        ayah = ayah,
        initialStudyTab = HybridReaderContract.StudyTab.RECITATION,
        autoPlayAyah = true
      )
      startActivity(intent)
    }

    override fun toggleBookmark(sura: Int, ayah: Int, page: Int) {
      lifecycleScope.launch {
        hubStateHolder.toggleAyahBookmark(sura, ayah)
      }
    }
  }

  public override fun onCreate(savedInstanceState: Bundle?) {
    appliedThemeStyle = ThemeUtil.applyActivityTheme(this)
    ThemeUtil.enableThemedEdgeToEdge(this)
    super.onCreate(savedInstanceState)
    val quranApp = application as QuranApplication
    quranApp.applicationComponent
      .activityComponentFactory()
      .generate(this)
      .quranActivityComponentFactory()
      .generate()
      .inject(this)

    isRtl = isRtl()
    registerBackPressedCallbacks()
    hubStateHolder.startObserving(lifecycleScope, latestPageFlow)

    val isSystemDark =
      (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    appliedDarkMode = ThemeUtil.isDarkAppearance(settings.currentTheme(), isSystemDark)
    appliedOledDarkMode = settings.isOledDarkMode
    val initialPalette = HybridViewPalette.from(appliedThemeStyle, appliedDarkMode, appliedOledDarkMode)

    hubView = HybridHubView(
      context = this,
      scope = lifecycleScope,
      searchEngineProvider = { hybridSearchEngine() },
      searchActionHandler = searchActionHandler,
      onNavTabSelected = { tab ->
        if (tab == HybridNavTab.READ) {
          jumpToLastPage()
        } else {
          hubStateHolder.selectNavTab(tab)
          syncHubBackCallback()
        }
      },
      onIndexSegmentSelected = { segment ->
        hubStateHolder.selectIndexSegment(segment)
      },
      onContinueReadingClick = {
        val targetPage = hubStateHolder.uiState.value.dailyWird.lastPage
        if (targetPage in 1..MAX_QURAN_PAGES_FALLBACK) {
          jumpTo(targetPage)
        } else {
          jumpToLastPage()
        }
      },
      onOpenPage = { page ->
        jumpTo(page)
      },
      onOpenAyah = { page, sura, ayah ->
        jumpToAndHighlight(page, sura, ayah)
      },
      onOpenSettingsClick = {
        startActivity(Intent(this@QuranActivity, QuranPreferenceActivity::class.java))
      },
      onOpenPaletteSheet = {
        showPaletteSheet = true
        syncHubBackCallback()
        hubView.render(hubStateHolder.uiState.value, showPaletteSheet)
      },
      onDismissPaletteSheet = {
        showPaletteSheet = false
        syncHubBackCallback()
        hubView.render(hubStateHolder.uiState.value, showPaletteSheet)
      },
      onSelectThemeMode = { mode ->
        hubStateHolder.updateThemeMode(mode)
        val systemDarkNow =
          (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val darkNow = ThemeUtil.isDarkAppearance(mode, systemDarkNow)
        if (darkNow != appliedDarkMode) {
          appliedDarkMode = darkNow
          hubView.applyPalette(
            HybridViewPalette.from(appliedThemeStyle, darkNow, appliedOledDarkMode)
          )
          ThemeUtil.enableThemedEdgeToEdge(this@QuranActivity)
        }
        syncHubBackCallback()
        hubView.render(hubStateHolder.uiState.value, showPaletteSheet)
      },
      onSelectThemeStyle = { style ->
        hubStateHolder.updateThemeStyle(style)
        if (style != appliedThemeStyle) {
          appliedThemeStyle = style
          theme.applyStyle(ThemeUtil.themeOverlayRes(style), true)
          hubView.applyPalette(HybridViewPalette.from(style, appliedDarkMode, appliedOledDarkMode))
          ThemeUtil.enableThemedEdgeToEdge(this@QuranActivity)
        }
        syncHubBackCallback()
        hubView.render(hubStateHolder.uiState.value, showPaletteSheet)
      },
      onOpenWirdGoalDialog = {
        openDailyWirdGoalDialog()
      },
      onClearReadingBookmark = { slot ->
        hubStateHolder.clearReadingBookmark(slot)
      },
      onRemoveRecentPage = { page ->
        hubStateHolder.removeRecentPage(page)
      },
      onClearAllRecents = {
        hubStateHolder.clearAllRecentPages()
      },
      onRemoveAyahBookmark = { sura, ayah ->
        hubStateHolder.removeAyahBookmark(sura, ayah)
      },
      onClearHighlight = { sura, ayah ->
        hubStateHolder.clearHighlight(sura, ayah)
      },
      onDownloadArabicSearchDb = {
        startActivity(Intent(this@QuranActivity, SearchActivity::class.java))
      },
      onOpenTranslationManager = {
        startActivity(Intent(this@QuranActivity, TranslationManagerActivity::class.java))
      },
      initialPalette = initialPalette,
      onToggleOledDarkMode = { enabled ->
        hubStateHolder.updateOledDarkMode(enabled)
        if (enabled != appliedOledDarkMode) {
          appliedOledDarkMode = enabled
          hubView.applyPalette(
            HybridViewPalette.from(appliedThemeStyle, appliedDarkMode, appliedOledDarkMode)
          )
          ThemeUtil.enableThemedEdgeToEdge(this@QuranActivity)
        }
        syncHubBackCallback()
        hubView.render(hubStateHolder.uiState.value, showPaletteSheet)
      }
    )

    ViewCompat.setOnApplyWindowInsetsListener(hubView) { _, insets ->
      val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
      val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
      val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
      hubView.applyWindowInsets(statusTop, navBottom, imeBottom)
      insets
    }

    setContentView(hubView)
    window?.setBackgroundDrawable(null)

    if (savedInstanceState != null) {
      showedTranslationUpgradeDialog = savedInstanceState.getBoolean(
          SI_SHOWED_UPGRADE_DIALOG, false
      )
      showPaletteSheet = savedInstanceState.getBoolean(
          SI_SHOW_PALETTE_SHEET, false
      )
    }
    syncHubBackCallback()

    val intent = intent
    if (intent != null) {
      val extras = intent.extras
      if (extras != null) {
        if (extras.getBoolean(EXTRA_SHOW_TRANSLATION_UPGRADE, false)) {
          if (!showedTranslationUpgradeDialog) {
            showTranslationsUpgradeDialog()
          }
        }
      }
      if (ShortcutsActivity.ACTION_JUMP_TO_LATEST == intent.action) {
        jumpToLastPage()
      }
    }
    applyInitialNavTab(intent)
    hubView.render(hubStateHolder.uiState.value, showPaletteSheet)

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        hubStateHolder.uiState.collectLatest { uiState ->
          val systemDarkNow =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
              Configuration.UI_MODE_NIGHT_YES
          val darkNow = ThemeUtil.isDarkAppearance(uiState.appTheme, systemDarkNow)
          val oledNow = uiState.isOledDarkMode
          if (darkNow != appliedDarkMode || oledNow != appliedOledDarkMode) {
            appliedDarkMode = darkNow
            appliedOledDarkMode = oledNow
            hubView.applyPalette(HybridViewPalette.from(appliedThemeStyle, darkNow, oledNow))
            ThemeUtil.enableThemedEdgeToEdge(this@QuranActivity)
          }
          syncHubBackCallback()
          hubView.render(uiState, showPaletteSheet)
        }
      }
    }

    window.decorView.post {
      if (!isFinishing && !isDestroyed) {
        updateTranslationsListAsNeeded()
        quranIndexEventLogger().logAnalytics()
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    applyInitialNavTab(intent)
  }

  private fun applyInitialNavTab(intent: Intent?) {
    val raw = intent?.getStringExtra(EXTRA_INITIAL_NAV_TAB) ?: return
    val tab = HybridNavTab.entries.firstOrNull { it.name == raw && it != HybridNavTab.READ } ?: return
    hubStateHolder.selectNavTab(tab)
    syncHubBackCallback()
  }

  override fun onStart() {
    super.onStart()
    if (::hubView.isInitialized) {
      hubView.scheduleIdlePrebuildIfNeeded()
    }
  }

  public override fun onResume() {
    super.onResume()
    if (AppThemeStyle.fromPreference(settings.appThemeStyle) != appliedThemeStyle) {
      recreate()
      return
    }
    val isRtl = isRtl()
    if (isRtl != this.isRtl) {
      val i = intent
      finish()
      startActivity(i)
    } else {
      if (::hubView.isInitialized) {
        hubView.onActivityResume()
        val systemDarkNow =
          (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val darkNow = ThemeUtil.isDarkAppearance(settings.currentTheme(), systemDarkNow)
        val oledNow = settings.isOledDarkMode
        AppThemeStyle.setOledDarkMode(oledNow)
        if (darkNow != appliedDarkMode || oledNow != appliedOledDarkMode) {
          appliedDarkMode = darkNow
          appliedOledDarkMode = oledNow
          hubView.applyPalette(HybridViewPalette.from(appliedThemeStyle, darkNow, oledNow))
          ThemeUtil.enableThemedEdgeToEdge(this)
        }
      }
      hubStateHolder.syncThemeFromSettings()
      hubStateHolder.refreshDailyWirdForNewDay()

      compositeDisposable.add(
          Completable.timer(500, MILLISECONDS)
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe {
                if (!AudioService.isRunning()) {
                  return@subscribe
                }
                try {
                  startService(
                    audioUtils().getAudioIntent(this@QuranActivity, AudioService.ACTION_STOP)
                  )
                } catch (_: IllegalStateException) {
                  // do nothing, we might be in the background
                  // onPause should have stopped us from needing this, but it sometimes happens
                }
              }
      )
    }
    isPaused = false
  }

  override fun onPause() {
    compositeDisposable.clear()
    isPaused = true
    super.onPause()
  }

  override fun onStop() {
    if (!isChangingConfigurations) {
      if (::hubView.isInitialized) {
        hubView.releaseTransientOnStop()
        hubStateHolder.releaseTransientBookmarksOnStop()
      }
      SQLiteDatabase.releaseMemory()
    }
    super.onStop()
  }

  override fun onDestroy() {
    upgradeDialog?.dismiss()
    upgradeDialog = null
    wirdGoalDialog?.dismiss()
    wirdGoalDialog = null
    if (::hubView.isInitialized) {
      hubView.unbind()
    }
    window?.setBackgroundDrawable(null)
    // only set to handle Android Q memory leaks
    backStackListener?.let {
      supportFragmentManager.removeOnBackStackChangedListener(it)
    }
    super.onDestroy()
  }

  // on back pressed, these are run in reverse order of registration
  private fun registerBackPressedCallbacks() {
    // this block works around a memory leak in Android Q
    // https://issuetracker.google.com/issues/139738913
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && isTaskRoot) {
      val enabled = (supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.backStackEntryCount ?: 0) == 0 &&
          supportFragmentManager.backStackEntryCount == 0
      val callback = object : OnBackPressedCallback(enabled) {
        override fun handleOnBackPressed() {
          finishAfterTransition()
        }
      }
      onBackPressedDispatcher.addCallback(this, callback)

      val listener = FragmentManager.OnBackStackChangedListener {
        callback.isEnabled =
          (supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.backStackEntryCount
            ?: 0) == 0 &&
              supportFragmentManager.backStackEntryCount == 0
      }
      backStackListener = listener
      supportFragmentManager.addOnBackStackChangedListener(listener)
    }

    hubBackCallback = object : OnBackPressedCallback(false) {
      override fun handleOnBackPressed() {
        when {
          showWirdGoalDialog -> {
            wirdGoalDialog?.dismiss()
            showWirdGoalDialog = false
          }
          showPaletteSheet -> {
            showPaletteSheet = false
            hubView.render(hubStateHolder.uiState.value, showPaletteSheet)
          }
          hubStateHolder.uiState.value.selectedTab != HybridNavTab.INDEX -> {
            hubStateHolder.selectNavTab(HybridNavTab.INDEX)
          }
        }
        syncHubBackCallback()
      }
    }
    onBackPressedDispatcher.addCallback(this, hubBackCallback)

    // clear the action mode if it's active on back press
    val supportActionModeEnabled = supportActionMode != null
    supportActionModeClearingCallback = object : OnBackPressedCallback(supportActionModeEnabled) {
      override fun handleOnBackPressed() {
        supportActionMode?.finish()
      }
    }
    onBackPressedDispatcher.addCallback(this, supportActionModeClearingCallback)
  }

  private fun openDailyWirdGoalDialog() {
    wirdGoalDialog?.dismiss()
    showWirdGoalDialog = true
    syncHubBackCallback()
    val palette = HybridViewPalette.from(appliedThemeStyle, appliedDarkMode, appliedOledDarkMode)
    wirdGoalDialog = DailyWirdGoalDialogNative.show(
      context = this,
      initialGoalPages = hubStateHolder.uiState.value.dailyWird.goalPages,
      palette = palette,
      onSave = { goalPages ->
        hubStateHolder.setDailyWirdGoal(goalPages)
      },
      onDismiss = {
        showWirdGoalDialog = false
        wirdGoalDialog = null
        syncHubBackCallback()
      }
    )
  }

  private fun syncHubBackCallback() {
    if (::hubBackCallback.isInitialized) {
      hubBackCallback.isEnabled = showWirdGoalDialog ||
        showPaletteSheet ||
        hubStateHolder.uiState.value.selectedTab != HybridNavTab.INDEX
    }
  }

  private fun isRtl(): Boolean {
    return QuranUtils.isRtl()
  }

  override fun onSupportActionModeFinished(mode: ActionMode) {
    supportActionMode = null
    supportActionModeClearingCallback.isEnabled = false
    super.onSupportActionModeFinished(mode)
  }

  override fun onSupportActionModeStarted(mode: ActionMode) {
    supportActionMode = mode
    supportActionModeClearingCallback.isEnabled = true
    super.onSupportActionModeStarted(mode)
    ThemeUtil.applyActionModeStatusGuard(this)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean(
        SI_SHOWED_UPGRADE_DIALOG,
        showedTranslationUpgradeDialog
    )
    outState.putBoolean(
        SI_SHOW_PALETTE_SHEET,
        showPaletteSheet
    )
    super.onSaveInstanceState(outState)
  }

  private fun jumpToLastPage() {
    lifecycleScope.launch {
      val recentPage = latestPage()
      jumpTo(
        if (recentPage == Constants.NO_PAGE) 1 else recentPage
      )
    }
  }

  private fun updateTranslationsListAsNeeded() {
    if (!updatedTranslations) {
      updatedTranslations = true
      val isCacheStale = System.currentTimeMillis() -
        settings.lastUpdatedTranslationDate > Constants.MIN_TRANSLATION_REFRESH_TIME
      if (isCacheStale) {
        translationManagerPresenter().checkForUpdates()
      }
    }
  }

  private fun showTranslationsUpgradeDialog() {
    showedTranslationUpgradeDialog = true

    val builder = Builder(this)
    builder.setMessage(R.string.translation_updates_available)
    builder.setCancelable(false)
    builder.setPositiveButton(R.string.translation_dialog_yes) { dialog: DialogInterface, _: Int ->
      dialog.dismiss()
      upgradeDialog = null
      launchTranslationActivity()
    }

    builder.setNegativeButton(R.string.translation_dialog_later) { dialog: DialogInterface, _: Int ->
      dialog.dismiss()
      upgradeDialog = null
      // pretend we don't have updated translations.  we'll
      // check again after 10 days.
      settings.setHaveUpdatedTranslations(false)
    }

    val dialog = builder.create()
    dialog.show()
    upgradeDialog = dialog
  }

  private fun launchTranslationActivity() {
    val i = Intent(this, TranslationManagerActivity::class.java)
    startActivity(i)
  }

  override fun jumpTo(page: Int) {
    quranNavigator().jumpTo(Page(page), showTranslation = settings.wasShowingTranslation)
  }

  override fun jumpToAndHighlight(page: Int, sura: Int, ayah: Int) {
    quranNavigator().jumpTo(SuraAyah(sura, ayah), showTranslation = settings.wasShowingTranslation)
  }

  fun jumpTo(row: QuranRow) {
    val location = if (row.isAyahBookmark || row.isHighlightedAyah) {
      SuraAyah(row.sura, row.ayah)
    } else {
      Page(row.page)
    }
    quranNavigator().jumpTo(location, row.readingBookmarkType, settings.wasShowingTranslation)
  }

  fun addTag() {
    if (!isPaused) {
      val fm = supportFragmentManager
      val addTagDialog = AddTagDialog()
      addTagDialog.show(fm, AddTagDialog.TAG)
    }
  }

  fun editTag(id: String, name: String?) {
    if (!isPaused) {
      val fm = supportFragmentManager
      val addTagDialog = newInstance(id, name!!)
      addTagDialog.show(fm, AddTagDialog.TAG)
    }
  }

  fun tagBookmarks(ids: Array<String>?) {
    if (ids != null && ids.size == 1) {
      tagBookmark(ids[0])
      return
    }

    if (!isPaused) {
      val fm = supportFragmentManager
      val tagBookmarkDialog = TagBookmarkDialog.newInstance(ids)
      tagBookmarkDialog.show(fm, TagBookmarkDialog.TAG)
    }
  }

  private fun tagBookmark(id: String) {
    if (!isPaused) {
      val fm = supportFragmentManager
      val tagBookmarkDialog = TagBookmarkDialog.newInstance(id)
      tagBookmarkDialog.show(fm, TagBookmarkDialog.TAG)
    }
  }

  override fun onAddTagSelected() {
    val fm = supportFragmentManager
    val dialog = AddTagDialog()
    dialog.show(fm, AddTagDialog.TAG)
  }

  companion object {
    const val EXTRA_SHOW_TRANSLATION_UPGRADE = "transUp"
    const val EXTRA_INITIAL_NAV_TAB = "hybrid_initial_nav_tab"
    private const val SI_SHOWED_UPGRADE_DIALOG = "si_showed_dialog"
    private const val SI_SHOW_PALETTE_SHEET = "si_show_palette_sheet"
    private var updatedTranslations = false
  }
}

private const val MAX_QURAN_PAGES_FALLBACK = 604
