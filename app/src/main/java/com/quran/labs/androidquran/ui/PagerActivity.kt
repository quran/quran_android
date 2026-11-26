package com.quran.labs.androidquran.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.SearchManager
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.preference.PreferenceManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager.widget.NonRestoringViewPager
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.quran.data.core.QuranInfo
import com.quran.data.dao.BookmarksDao
import com.quran.data.model.Page
import com.quran.data.model.QuranText
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.AyahSelection.AyahRange
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.endSuraAyah
import com.quran.data.model.selection.selectionIndicator
import com.quran.data.model.selection.startSuraAyah
import com.quran.data.model.selection.withXScroll
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.HelpActivity
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.QuranPreferenceActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.SearchActivity
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.model.QariItem.Companion.fromQari
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.model.playback.AudioStatus
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.data.TimingRepository
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.di.component.activity.PagerActivityComponent
import com.quran.labs.androidquran.feature.reading.bridge.AudioStatusRepositoryBridge
import com.quran.labs.androidquran.feature.reading.bridge.DownloadBridge
import com.quran.labs.androidquran.feature.reading.bridge.ReadingEventPresenterBridge
import com.quran.labs.androidquran.feature.reading.presenter.AudioPresenter
import com.quran.labs.androidquran.feature.reading.presenter.AudioPresenterScreen
import com.quran.labs.androidquran.feature.reading.presenter.ReadingBookmarkChange
import com.quran.labs.androidquran.feature.reading.presenter.ReadingBookmarkPresenter
import com.quran.labs.androidquran.feature.reading.presenter.RecentPagePresenter
import com.quran.labs.androidquran.feature.reading.presenter.recitation.PagerActivityRecitationPresenter
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.DailyWirdRepository
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.hybrid.reader.AyahOverlayBounds
import com.quran.labs.androidquran.hybrid.reader.HybridReaderController
import com.quran.labs.androidquran.hybrid.reader.HybridReaderIntentParser
import com.quran.labs.androidquran.hybrid.reader.HybridReaderOverlayView
import com.quran.labs.androidquran.hybrid.reader.TafsirLookupHelper
import com.quran.labs.androidquran.hybrid.reader.computeSelectedItemOverlayBounds
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.presenter.data.QuranEventLogger
import com.quran.labs.androidquran.presenter.translationlist.TranslationListPresenter
import com.quran.labs.androidquran.service.AudioService
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.PermissionUtil.buildPostPermissionDialog
import com.quran.labs.androidquran.service.util.PermissionUtil.canRequestPostNotificationPermission
import com.quran.labs.androidquran.service.util.PermissionUtil.havePostNotificationPermission
import com.quran.labs.androidquran.service.util.ServiceIntentHelper.getDownloadIntent
import com.quran.labs.androidquran.ui.fragment.AddTagDialog
import com.quran.labs.androidquran.ui.fragment.JumpFragment
import com.quran.labs.androidquran.ui.fragment.QuranPageFragment
import com.quran.labs.androidquran.ui.fragment.TabletFragment
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog.OnBookmarkTagsUpdateListener
import com.quran.labs.androidquran.ui.fragment.TranslationFragment
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.helpers.AyahTracker
import com.quran.labs.androidquran.ui.helpers.JumpDestination
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper
import com.quran.labs.androidquran.ui.helpers.QuranNavigator
import com.quran.labs.androidquran.ui.helpers.QuranPage
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter
import com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter
import com.quran.labs.androidquran.ui.listener.AudioBarListener
import com.quran.labs.androidquran.ui.readingbookmark.createReadingBookmarkToastView
import com.quran.labs.androidquran.ui.util.ToastCompat.makeText
import com.quran.labs.androidquran.ui.util.TranslationsSpinnerAdapter
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.OrientationLockUtils
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.BitmapPoolTrimRegistry
import com.quran.labs.androidquran.extra.feature.linebyline.resource.LineBitmapRecycler
import com.quran.labs.androidquran.hybrid.ui.PostDestroyMemoryReclaimer
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.SearchResultFormatter
import com.quran.labs.androidquran.util.ShareUtil
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.labs.androidquran.view.IconPageIndicator
import com.quran.labs.androidquran.view.QuranSpinner
import com.quran.labs.androidquran.view.SlidingUpPanelLayout
import com.quran.mobile.common.download.DownloadInfoStreams
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.di.QuranReadingActivityComponent
import com.quran.mobile.di.QuranReadingActivityComponentProvider
import com.quran.mobile.di.QuranReadingPageComponent
import com.quran.mobile.di.QuranReadingPageComponentProvider
import com.quran.mobile.feature.audiobar.AudioBarInjector
import com.quran.mobile.feature.audiobar.AudioBarWrapper
import com.quran.mobile.feature.audiobar.presenter.AudioBarEventRepository
import com.quran.mobile.feature.ayahbookmark.AyahBookmarkWrapper
import com.quran.mobile.feature.ayahbookmark.di.AyahBookmarkWrapperInjector
import com.quran.mobile.feature.ayahbookmark.readingbookmark.ReadingBookmarkSheetWrapper
import com.quran.mobile.feature.ayahbookmark.readingbookmark.di.ReadingBookmarkSheetWrapperInjector
import com.quran.mobile.feature.pagetheme.PageThemeSheetWrapper
import com.quran.mobile.feature.qarilist.QariListWrapper
import com.quran.mobile.feature.qarilist.di.QariListWrapperInjector
import com.quran.mobile.translation.model.LocalTranslation
import com.quran.mobile.translation.verse.TranslationModel
import com.quran.page.common.data.QuranNaming
import com.quran.page.common.factory.PageViewFactoryProvider
import com.quran.page.common.toolbar.AyahToolBar
import com.quran.page.common.toolbar.di.AyahToolBarInjector
import com.quran.reading.common.ReadingEventPresenter
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Activity that displays the Quran (in Arabic or translation mode).
 *
 *
 * Essentially, this activity consists of a [ViewPager] of Quran pages (using [ ]).
 * [AudioService] is used to handle playing audio, and this is synced with
 * the display of the Quran.
 */
class PagerActivity : AppCompatActivity(), AudioBarListener, OnBookmarkTagsUpdateListener,
  AyahSelectedListener, JumpDestination, QuranReadingActivityComponentProvider,
  QuranReadingPageComponentProvider, AyahToolBarInjector, QariListWrapperInjector,
  AudioBarInjector, AyahBookmarkWrapperInjector, ReadingBookmarkSheetWrapperInjector,
  ActivityCompat.OnRequestPermissionsResultCallback, AudioPresenterScreen,
  ReadingBookmarkPresenter.Screen {
  private var lastPopupTime: Long = 0
  private var isActionBarHidden = true
  private var shouldReconnect = false
  private var showingTranslation = false
  private var needsPermissionToDownloadOver3g = true
  private var promptDialog: AlertDialog? = null
  private var isDualPages = false
  private var promptedForExtraDownload = false
  private var isFoldableDeviceOpenAndVertical = false

  private var bookmarksMenuItem: MenuItem? = null
  private var isCurrentPageReadingBookmarked = false
  private var readingBookmarkToastView: View? = null

  private var translationNames: Array<String> = emptyArray()
  private var translations: List<LocalTranslation>? = null
  private var activeTranslationsFilesNames: Set<String?>? = null
  private var translationsSpinnerAdapter: TranslationsSpinnerAdapter? = null

  private lateinit var audioStatusBar: AudioBarWrapper
  private lateinit var viewPager: ViewPager
  private val themePreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    if (key in PAGE_COLOR_PREFERENCES) {
      if (key == Constants.PREF_APP_THEME_STYLE) {
        val updatedStyle = AppThemeStyle.fromPreference(quranSettings.appThemeStyle)
        if (updatedStyle != appliedThemeStyle) {
          appliedThemeStyle = updatedStyle
          AppThemeStyle.select(updatedStyle)
          theme.applyStyle(ThemeUtil.themeOverlayRes(updatedStyle), true)
        }
      }
      refreshQuranPages()
      applyHybridMushafBackdrop()
      updateSystemBarAppearance()
    }
  }
  private lateinit var pagerAdapter: QuranPageAdapter
  private lateinit var ayahToolBar: AyahToolBar
  private lateinit var slidingPanel: SlidingUpPanelLayout
  private lateinit var slidingPager: ViewPager
  private lateinit var slidingPageIndicator: IconPageIndicator
  private lateinit var slidingPagerAdapter: SlidingPagerAdapter
  private var isSlidingPagerAttached = false
  private lateinit var translationsSpinner: QuranSpinner
  private lateinit var overlay: FrameLayout
  private lateinit var toolBarArea: View
  private lateinit var hybridReaderOverlay: HybridReaderOverlayView
  private var mushafClipContainer: FrameLayout? = null
  private lateinit var hybridReaderController: HybridReaderController
  private var pageSettleDebounceJob: Job? = null
  private var qariListObserverJob: Job? = null
  private var pageChangeListener: OnPageChangeListener? = null
  private var pendingMarkerPopupPage: Int? = null
  private var lastViewPagerScrollState: Int = ViewPager.SCROLL_STATE_IDLE
  private var lastScrolledBoundsOffsetPx: Int = Int.MIN_VALUE
  private var statusBarTopInsetPx: Int = 0
  private var mushafClipRect: Rect? = null

  private var requestPermissionLauncher: ActivityResultLauncher<String>? = null

  private var isSplitScreen = false

  private lateinit var onHybridOverlayOrDrawerBackCallback: OnBackPressedCallback
  private lateinit var onClearAyahModeBackCallback: OnBackPressedCallback
  private lateinit var onShowingTranslationBackCallback: OnBackPressedCallback
  private lateinit var onEndSessionBackCallback: OnBackPressedCallback

  private var lastSelectedTranslationAyah: QuranAyahInfo? = null
  private var lastActivatedLocalTranslations: Array<LocalTranslation> = emptyArray()

  @Inject lateinit var quranNavigator: QuranNavigator

  @Inject lateinit var bookmarksDao: BookmarksDao
  @Inject lateinit var recentPagePresenter: RecentPagePresenter
  @Inject lateinit var readingBookmarkPresenter: ReadingBookmarkPresenter
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var quranNaming: QuranNaming
  @Inject lateinit var quranScreenInfo: QuranScreenInfo
  @Inject lateinit var arabicDatabaseUtils: Provider<ArabicDatabaseUtils>
  @Inject lateinit var shareUtil: ShareUtil
  @Inject lateinit var audioUtils: AudioUtils
  @Inject lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var quranInfo: QuranInfo
  @Inject lateinit var quranFileUtils: QuranFileUtils
  @Inject lateinit var audioPresenter: AudioPresenter
  @Inject lateinit var quranEventLogger: QuranEventLogger
  @Inject lateinit var audioStatusRepository: AudioStatusRepository
  @Inject lateinit var readingEventPresenter: ReadingEventPresenter
  @Inject lateinit var pageProviderFactoryProvider: PageViewFactoryProvider
  @Inject lateinit var additionalAyahPanels: Set<@JvmSuppressWildcards AyahActionFragmentProvider>
  @Inject lateinit var pagerActivityRecitationPresenter: PagerActivityRecitationPresenter
  @Inject lateinit var translationListPresenter: TranslationListPresenter
  @Inject lateinit var audioBarEventRepository: AudioBarEventRepository
  @Inject lateinit var downloadInfoStreams: DownloadInfoStreams
  @Inject lateinit var qariManager: CurrentQariManager
  @Inject lateinit var downloadBridge: DownloadBridge
  @Inject lateinit var dailyWirdRepository: DailyWirdRepository
  @Inject lateinit var translationsDBAdapter: TranslationsDBAdapter
  @Inject lateinit var translationModel: TranslationModel
  @Inject lateinit var translationUtil: TranslationUtil
  @Inject lateinit var timingRepository: TimingRepository

  private lateinit var audioStatusRepositoryBridge: AudioStatusRepositoryBridge
  private lateinit var readingEventPresenterBridge: ReadingEventPresenterBridge
  private lateinit var windowInsetsController: WindowInsetsControllerCompat

  private var translationJob: Job? = null
  private lateinit var compositeDisposable: CompositeDisposable
  private val foregroundDisposable = CompositeDisposable()
  private val scope = MainScope()

  val pagerActivityComponent: PagerActivityComponent by lazy {
    (application as QuranApplication)
      .applicationComponent
      .activityComponentFactory()
      .generate(this)
      .pagerActivityComponentFactory()
      .generate(this)
  }

  private val handler = PagerHandler(this)

  private class PagerHandler(activity: PagerActivity) : Handler(Looper.getMainLooper()) {
    private val activity = WeakReference(activity)

    override fun handleMessage(msg: Message) {
      val activity = activity.get()
      if (activity != null) {
        if (msg.what == MSG_HIDE_ACTIONBAR) {
          if (activity.shouldAutoHideChrome()) {
            activity.toggleActionBarVisibility(false)
          }
        } else {
          super.handleMessage(msg)
        }
      }
    }
  }

  private var appliedThemeStyle: AppThemeStyle = AppThemeStyle.DEFAULT

  public override fun onCreate(savedInstanceState: Bundle?) {
    appliedThemeStyle = ThemeUtil.applyActivityTheme(this)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    super.onCreate(savedInstanceState)

    // field injection
    pagerActivityComponent.inject(this)

    isFoldableDeviceOpenAndVertical =
      savedInstanceState?.getBoolean(LAST_FOLDING_STATE, isFoldableDeviceOpenAndVertical)
        ?: isFoldableDeviceOpenAndVertical

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        WindowInfoTracker.getOrCreate(this@PagerActivity)
          .windowLayoutInfo(this@PagerActivity)
          .map { it.displayFeatures }
          .collectLatest {
            val foldingFeatures = it.filterIsInstance<FoldingFeature>().firstOrNull()
            if (foldingFeatures != null) {
              val localState =
                foldingFeatures.orientation == FoldingFeature.Orientation.VERTICAL &&
                    (foldingFeatures.state == FoldingFeature.State.FLAT || foldingFeatures.state == FoldingFeature.State.HALF_OPENED)
              if (isFoldableDeviceOpenAndVertical != localState) {
                isFoldableDeviceOpenAndVertical = localState
                updateDualPageMode()
              }
            } else if (isFoldableDeviceOpenAndVertical) {
              // this else case suggests that the device is not open and vertical, otherwise
              // we'd have some information given via the folding features.
              isFoldableDeviceOpenAndVertical = false
              updateDualPageMode()
            }
          }
      }
    }

    setContentView(R.layout.quran_page_activity_slider)

    windowInsetsController = WindowInsetsControllerCompat(
      window, findViewById<ViewGroup>(R.id.sliding_panel)
    )
    updateSystemBarAppearance()
    registerBackPressedCallbacks()
    initialize(savedInstanceState)
    requestPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean? ->
        audioPresenter.onPostNotificationsPermissionResponse(
          isGranted!!
        )
      }

    // read the list of translations
    requestTranslationsList()

    downloadBridge.subscribeToDownloads {
      onDownloadSuccess()
    }

  }

  private fun updateDualPageMode() {
    val lastIsDualPages = isDualPages
    val nextIsDualPages = QuranUtils.isDualPages(this, quranScreenInfo, isFoldableDeviceOpenAndVertical)
    if (lastIsDualPages != nextIsDualPages) {
      // due to the conversion after setting the adapter, we need this page to be
      // the page _before_ we switched from dual to single or vice versa. otherwise,
      // we end up double-converting the page.
      //
      // might be worth trying later to undo this and just remove the conversion below.
      val page = currentPage
      isDualPages = nextIsDualPages

      if (::pagerAdapter.isInitialized) {
        pagerAdapter.clearViewPool()
      }
      pagerAdapter = QuranPageAdapter(
        supportFragmentManager,
        isDualPages,
        showingTranslation,
        quranInfo,
        isSplitScreen,
        pageProviderFactoryProvider.providePageViewFactory(quranSettings.pageType)
      )
      viewPager.adapter = pagerAdapter
      // when going from two page per screen to one or vice versa, we adjust the page number,
      // such that the first page is always selected.
      val curPage = if (isDualPageVisible) {
        quranInfo.mapSinglePageToDualPage(page)
      } else {
        quranInfo.mapDualPageToSinglePage(page)
      }

      val pageIndex = quranInfo.getPositionFromPage(curPage, isDualPageVisible)
      viewPager.setCurrentItem(pageIndex)
    }
  }

  private fun initialize(savedInstanceState: Bundle?) {
    var shouldAdjustPageNumber = false
    isDualPages = QuranUtils.isDualPages(this, quranScreenInfo, isFoldableDeviceOpenAndVertical)
    isSplitScreen = quranSettings.isQuranSplitWithTranslation
    audioStatusRepositoryBridge = AudioStatusRepositoryBridge(
      audioStatusRepository,
      audioBarEventRepository,
      { suraAyah: SuraAyah? ->
        onAudioPlaybackAyahChanged(suraAyah)
      },
      this,
      pagerActivityRecitationPresenter
    )
    readingEventPresenterBridge = ReadingEventPresenterBridge(
      readingEventPresenter,
      {
        onPageClicked()
      },
      handleSelection = { ayahSelection: AyahSelection ->
        onAyahSelectionChanged(ayahSelection)
      }
    )

    // remove the window background to avoid overdraw. note that, per Romain's blog, this is
    // acceptable (as long as we don't set the background color to null in the theme, since
    // that is used to generate preview windows).
    window.setBackgroundDrawable(null)

    var page = -1
    isActionBarHidden = true
    var restoredDrawerOpen = false
    var restoredAppearanceOpen = false
    var restoredStudyTab = HybridReaderContract.StudyTab.RECITATION
    var restoredSelectedSura = -1
    var restoredSelectedAyah = -1
    var restoredSelectedEndSura = -1
    var restoredSelectedEndAyah = -1
    var parsedLaunchExtras = HybridReaderIntentParser.parse(intent, quranInfo, Constants.PAGES_FIRST)

    if (savedInstanceState != null) {
      Timber.d("non-null saved instance state!")
      page = savedInstanceState.getInt(LAST_READ_PAGE, -1)
      showingTranslation = savedInstanceState
        .getBoolean(LAST_READING_MODE_IS_TRANSLATION, false)
      if (savedInstanceState.containsKey(LAST_ACTIONBAR_STATE)) {
        isActionBarHidden = !savedInstanceState.getBoolean(LAST_ACTIONBAR_STATE)
      }
      val lastWasDualPages = savedInstanceState.getBoolean(LAST_WAS_DUAL_PAGES, isDualPages)
      shouldAdjustPageNumber = (lastWasDualPages != isDualPages)
      restoredDrawerOpen = savedInstanceState.getBoolean(KEY_HYBRID_DRAWER_OPEN, false)
      restoredAppearanceOpen = savedInstanceState.getBoolean(KEY_HYBRID_APPEARANCE_OPEN, false)
      restoredStudyTab = HybridReaderContract.StudyTab.fromExtra(
        savedInstanceState.getString(KEY_HYBRID_STUDY_TAB)
      ) ?: HybridReaderContract.StudyTab.RECITATION
      restoredSelectedSura = savedInstanceState.getInt(KEY_HYBRID_SELECTED_SURA, -1)
      restoredSelectedAyah = savedInstanceState.getInt(KEY_HYBRID_SELECTED_AYAH, -1)
      restoredSelectedEndSura = savedInstanceState.getInt(KEY_HYBRID_SELECTED_END_SURA, -1)
      restoredSelectedEndAyah = savedInstanceState.getInt(KEY_HYBRID_SELECTED_END_AYAH, -1)
    } else {
      page = parsedLaunchExtras.page
      showingTranslation = parsedLaunchExtras.jumpToTranslation || showingTranslation
    }
    onShowingTranslationBackCallback.isEnabled = showingTranslation

    compositeDisposable = CompositeDisposable()

    audioStatusBar = findViewById(R.id.audio_area)

    toolBarArea = findViewById(R.id.toolbar_area)
    translationsSpinner = findViewById(R.id.spinner)
    overlay = findViewById(R.id.overlay)
    hybridReaderOverlay = findViewById(R.id.hybrid_reader_overlay)

    initHybridReaderController()
    applyHybridMushafBackdrop()

    if (savedInstanceState != null) {
      if (restoredSelectedSura > 0 && restoredSelectedAyah > 0) {
        val restoredStart = SuraAyah(restoredSelectedSura, restoredSelectedAyah)
        if (restoredSelectedEndSura > 0 && restoredSelectedEndAyah > 0) {
          val restoredEnd = SuraAyah(restoredSelectedEndSura, restoredSelectedEndAyah)
          if (restoredEnd != restoredStart) {
            readingEventPresenter.onAyahSelection(
              AyahRange(restoredStart, restoredEnd, SelectionIndicator.None)
            )
          } else {
            readingEventPresenterBridge.setSelection(restoredSelectedSura, restoredSelectedAyah, false)
          }
        } else {
          readingEventPresenterBridge.setSelection(restoredSelectedSura, restoredSelectedAyah, false)
        }
      }
      if (restoredDrawerOpen || restoredAppearanceOpen) {
        hybridReaderController.restoreUiState(
          isDrawerOpen = restoredDrawerOpen,
          studyTab = restoredStudyTab,
          isAppearanceSheetOpen = restoredAppearanceOpen
        )
      }
    } else {
      val highlightedSura = parsedLaunchExtras.highlightSura
      val highlightedAyah = parsedLaunchExtras.highlightAyah
      if (highlightedSura != null && highlightedAyah != null) {
        readingEventPresenterBridge.setSelection(
          highlightedSura,
          highlightedAyah,
          true
        )
      }
      parsedLaunchExtras.initialStudyTab?.let { tab ->
        hybridReaderController.openStudyDrawer(tab)
      }
    }

    ViewCompat.setOnApplyWindowInsetsListener(toolBarArea) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.statusBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.navigationBars()
      )
      view.updatePadding(insets.left, insets.top, insets.right, 0)
      windowInsets
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)

    supportActionBar?.setDisplayShowHomeEnabled(true)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    initAyahActionPanel(savedInstanceState)

    if (showingTranslation && translationNames.isNotEmpty()) {
      updateActionBarSpinner()
    } else {
      updateActionBarTitle(page)
    }

    lastPopupTime = System.currentTimeMillis()
    pagerAdapter = QuranPageAdapter(
      supportFragmentManager,
      isDualPages,
      showingTranslation,
      quranInfo,
      isSplitScreen,
      pageProviderFactoryProvider.providePageViewFactory(quranSettings.pageType)
    )
    ayahToolBar = findViewById(R.id.ayah_toolbar)
    ayahToolBar.flavor = BuildConfig.FLAVOR
    ayahToolBar.longPressLambda = { charSequence: CharSequence? ->
      makeText(this@PagerActivity, charSequence!!, Toast.LENGTH_SHORT).show()
    }

    ViewCompat.setOnApplyWindowInsetsListener(ayahToolBar) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.statusBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.navigationBars()
      )
      ayahToolBar.insets = insets
      windowInsets
    }

    val nonRestoringViewPager = findViewById<NonRestoringViewPager>(R.id.quran_pager)
    mushafClipContainer = findViewById(R.id.mushaf_clip_container)
    nonRestoringViewPager.setIsDualPagesInLandscape(
      QuranUtils.isDualPagesInLandscape(this, quranScreenInfo)
    )

    viewPager = nonRestoringViewPager
    viewPager.offscreenPageLimit = 1
    viewPager.setAdapter(pagerAdapter)
    ViewCompat.setOnApplyWindowInsetsListener(viewPager) { _, windowInsets ->
      val top = windowInsets.getInsets(
        WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
      ).top
      val navBottom = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
      if (::hybridReaderOverlay.isInitialized) {
        hybridReaderOverlay.applyWindowInsets(top, navBottom)
      }
      if (top != statusBarTopInsetPx) {
        statusBarTopInsetPx = top
        applyMushafStatusBarClip(targetNudgePx = viewPager.translationY)
      }
      windowInsets
    }

    ayahToolBar.setOnItemSelectedListener(AyahMenuItemSelectionHandler())
    // Proposal 5 FloatingAyahActionBar replaces legacy AyahToolBar; detach so onAttachedToWindow never binds AyahToolBarPresenter.
    ayahToolBar.visibility = View.GONE
    (ayahToolBar.parent as? ViewGroup)?.removeView(ayahToolBar)
    val onPageChangeListener: OnPageChangeListener = object : OnPageChangeListener {
      override fun onPageScrollStateChanged(state: Int) {
        lastViewPagerScrollState = state
        if (state == ViewPager.SCROLL_STATE_IDLE) {
          lastScrolledBoundsOffsetPx = Int.MIN_VALUE
          pageSettleDebounceJob?.cancel()
          val settledPage = currentPage
          val markerPage = pendingMarkerPopupPage
          pendingMarkerPopupPage = null
          viewPager.post {
            if (!isFinishing && !isDestroyed && ::hybridReaderController.isInitialized) {
              hybridReaderController.recordSettledPage(settledPage)
              if (markerPage != null && quranSettings.shouldDisplayMarkerPopup()) {
                lastPopupTime = QuranDisplayHelper.displayMarkerPopup(
                  this@PagerActivity, quranInfo, markerPage, isDualPageVisible, lastPopupTime
                )
              }
            }
          }
          refreshHybridAyahBounds()
        }
      }

      override fun onPageScrolled(
        position: Int,
        positionOffset: Float,
        positionOffsetPixels: Int
      ) {
        val currentSelection = readingEventPresenter.currentAyahSelection()
        val selectionIndicator =
          currentSelection.selectionIndicator()
        val suraAyah = currentSelection.startSuraAyah()
        if (selectionIndicator !== SelectionIndicator.None && selectionIndicator !== SelectionIndicator.ScrollOnly && suraAyah != null) {
          val startPage = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
          val barPos = quranInfo.getPositionFromPage(startPage, isDualPageVisible)
          if (position == barPos) {
            // Swiping to next ViewPager page (i.e. prev quran page)
            val updatedSelectionIndicator =
              selectionIndicator.withXScroll(-positionOffsetPixels.toFloat())
            readingEventPresenterBridge.withSelectionIndicator(
              updatedSelectionIndicator
            )
          } else if (position == barPos - 1 || position == barPos + 1) {
            // Swiping to previous or next ViewPager page (i.e. next or previous quran page)
            val updatedSelectionIndicator =
              selectionIndicator.withXScroll((viewPager.width - positionOffsetPixels).toFloat())
            readingEventPresenterBridge.withSelectionIndicator(
              updatedSelectionIndicator
            )
          } else {
            readingEventPresenterBridge.clearSelectedAyah()
          }
        }
        if (suraAyah != null &&
          (positionOffsetPixels == 0 || abs(positionOffsetPixels - lastScrolledBoundsOffsetPx) >= 8)
        ) {
          lastScrolledBoundsOffsetPx = positionOffsetPixels
          refreshHybridAyahBounds()
        }
      }

      override fun onPageSelected(position: Int) {
        Timber.d("onPageSelected(): %d", position)
        lastScrolledBoundsOffsetPx = Int.MIN_VALUE
        val page = quranInfo.getPageFromPosition(position, isDualPageVisible)
        hybridReaderController.updateCurrentPage(page)
        schedulePageSettle(page)

        if (quranSettings.shouldDisplayMarkerPopup()) {
          if (lastViewPagerScrollState == ViewPager.SCROLL_STATE_IDLE) {
            pendingMarkerPopupPage = null
            lastPopupTime = QuranDisplayHelper.displayMarkerPopup(
              this@PagerActivity, quranInfo, page, isDualPageVisible, lastPopupTime
            )
          } else {
            pendingMarkerPopupPage = page
          }
        }

        if (toolBarArea.visibility == View.VISIBLE) {
          if (!showingTranslation) {
            updateActionBarTitle(page)
          } else {
            refreshActionBarSpinner()
          }
        }

        // If we're more than 1 page away from ayah selection end ayah mode
        val suraAyah: SuraAyah? = selectionStart
        if (suraAyah != null) {
          val startPage = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
          val ayahPos = quranInfo.getPositionFromPage(startPage, isDualPageVisible)
          if (abs((ayahPos - position).toDouble()) > 1) {
            endAyahMode()
          }
        }
      }
    }
    pageChangeListener = onPageChangeListener
    viewPager.addOnPageChangeListener(onPageChangeListener)

    setUiVisibilityListener()
    toolBarArea.visibility = View.GONE
    audioStatusBar.visibility = View.GONE
    ayahToolBar.visibility = View.GONE
    toggleActionBarVisibility(true)

    if (shouldAdjustPageNumber) {
      // when going from two page per screen to one or vice versa, we adjust the page number,
      // such that the first page is always selected.
      val curPage = if (isDualPageVisible) {
        quranInfo.mapSinglePageToDualPage(page)
      } else {
        quranInfo.mapDualPageToSinglePage(page)
      }
      page = curPage
    }

    val pageIndex = quranInfo.getPositionFromPage(page, isDualPageVisible)
    viewPager.setCurrentItem(pageIndex)
    hybridReaderController.updateCurrentPage(page)
    if (page == 0) {
      onPageChangeListener.onPageSelected(0)
    }

    bindHybridReaderOverlay()
    viewPager.post {
      if (!isFinishing && !isDestroyed) {
        hybridReaderController.recordSettledPage(currentPage)
        refreshHybridAyahBounds()
        if (savedInstanceState == null &&
          parsedLaunchExtras.autoPlayAyah &&
          parsedLaunchExtras.highlightSura != null &&
          parsedLaunchExtras.highlightAyah != null
        ) {
          playFromAyah(parsedLaunchExtras.highlightSura, parsedLaunchExtras.highlightAyah)
        }
      }
    }

    // just got created, need to reconnect to service
    shouldReconnect = true

    // enforce orientation lock
    if (quranSettings.isLockOrientation &&
      OrientationLockUtils.isOrientationLockSupported(resources.configuration)
    ) {
      val current = resources.configuration.orientation
      if (quranSettings.isLandscapeOrientation) {
        if (current == Configuration.ORIENTATION_PORTRAIT) {
          requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
          return
        }
      } else if (current == Configuration.ORIENTATION_LANDSCAPE) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        return
      }
    }

    quranEventLogger.logAnalytics(isDualPages, showingTranslation, isSplitScreen)

    // Setup recitation (if enabled)
    pagerActivityRecitationPresenter.bind(
      this, PagerActivityRecitationPresenter.Bridge(
        { isDualPageVisible },
        { currentPage },
        { ayahToolBar },
        { ayah: SuraAyah -> ensurePage(ayah.sura, ayah.ayah) },
        { sliderPage: Int -> showSlider(slidingPagerAdapter.getPagePosition(sliderPage)) }
      ))
  }

  private fun registerBackPressedCallbacks() {
    val isSessionEnabled = false
    onEndSessionBackCallback = object : OnBackPressedCallback(isSessionEnabled) {
      override fun handleOnBackPressed() {
        onSessionEnd()
      }
    }
    onBackPressedDispatcher.addCallback(this, onEndSessionBackCallback)

    onShowingTranslationBackCallback = object : OnBackPressedCallback(showingTranslation) {
      override fun handleOnBackPressed() {
        switchToQuran()
      }
    }
    onBackPressedDispatcher.addCallback(this, onShowingTranslationBackCallback)

    onClearAyahModeBackCallback = object : OnBackPressedCallback(selectionStart != null) {
      override fun handleOnBackPressed() {
        endAyahMode()
      }
    }
    onBackPressedDispatcher.addCallback(this, onClearAyahModeBackCallback)

    onHybridOverlayOrDrawerBackCallback = object : OnBackPressedCallback(false) {
      override fun handleOnBackPressed() {
        if (overlay.isVisible) {
          qariListObserverJob?.cancel()
          qariListObserverJob = null
          overlay.removeAllViews()
          overlay.visibility = View.GONE
          toggleActionBarVisibility(true)
          updateHybridBackCallbackState()
        } else if (::hybridReaderOverlay.isInitialized && hybridReaderOverlay.dismissTafsirPicker()) {
          updateHybridBackCallbackState()
        } else if (::hybridReaderController.isInitialized && hybridReaderController.uiState.value.isAppearanceSheetOpen) {
          hybridReaderController.closeAppearanceSheet()
          updateHybridBackCallbackState()
        } else if (::hybridReaderController.isInitialized && hybridReaderController.uiState.value.isStudyDrawerOpen) {
          hybridReaderController.closeStudyDrawer()
          updateHybridBackCallbackState()
        }
      }
    }
    onBackPressedDispatcher.addCallback(this, onHybridOverlayOrDrawerBackCallback)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int, permissions: Array<String>, grantResults: IntArray
  ) {
    pagerActivityRecitationPresenter.onPermissionsResult(requestCode, grantResults)
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

  internal fun isDualPageLayout(): Boolean = isDualPages

  private val isDualPageVisible: Boolean
    get() = isDualPages && !(isSplitScreen && showingTranslation)

  private fun shouldUpdatePageNumber(): Boolean {
    return isDualPages && isSplitScreen
  }

  private val currentPageFlow: Flow<Int> =
    callbackFlow {
      val pageChangedListener: OnPageChangeListener =
        object : SimpleOnPageChangeListener() {
          override fun onPageSelected(position: Int) {
            val page = quranInfo.getPageFromPosition(position, isDualPageVisible)
            trySend(page)
          }
        }
      viewPager.addOnPageChangeListener(pageChangedListener)
      awaitClose { viewPager.removeOnPageChangeListener(pageChangedListener) }
    }
      .onStart { emit(currentPage) }
      .buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST)
      .shareIn(scope, SharingStarted.Eagerly, 1)

  private fun initAyahActionPanel(savedInstanceState: Bundle?) {
    slidingPanel = findViewById(R.id.sliding_panel)
    val slidingLayout =
      slidingPanel.findViewById<ViewGroup>(R.id.sliding_layout)
    slidingPager = slidingPanel
      .findViewById(R.id.sliding_layout_pager)
    slidingPageIndicator =
      slidingPanel
        .findViewById(R.id.sliding_pager_indicator)

    // Find close button and set listener
    val closeButton = slidingPanel
      .findViewById<View>(R.id.sliding_menu_close)
    closeButton.setOnClickListener { v: View? -> endAyahMode() }

    // Create the adapter object (lightweight), but defer attaching to ViewPager until
    // the sliding panel is actually opened so hidden fragments/pickers aren't inflated
    // on every reader cold launch.
    slidingPagerAdapter = SlidingPagerAdapter(
      this,
      supportFragmentManager,
      QuranUtils.isRtl(),
      additionalAyahPanels
    )
    if (savedInstanceState?.getBoolean(LAST_SLIDER_INITIALIZED, false) == true) {
      ensureSlidingPagerAttached()
    }

    // Retire legacy SlidingUpPanelLayout UI in favor of Proposal 5 Unified Study Drawer
    val displayHeight = resources.displayMetrics.heightPixels
    slidingLayout.layoutParams.height = (displayHeight * PANEL_MAX_HEIGHT).toInt()
    slidingPanel.setSlidingEnabled(false)
    slidingPanel.setCoveredFadeColor(0)
    slidingPanel.setPanelSlideListener(SlidingPanelListener())
    slidingLayout.visibility = View.GONE
    slidingPanel.hidePane()
  }

  private fun ensureSlidingPagerAttached() {
    if (!isSlidingPagerAttached) {
      isSlidingPagerAttached = true
      slidingPager.setAdapter(slidingPagerAdapter)
      slidingPageIndicator.setViewPager(slidingPager)
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      scheduleAutoHideChromeIfAllowed()
    } else {
      handler.removeMessages(MSG_HIDE_ACTIONBAR)
    }
  }

  private fun scheduleAutoHideChromeIfAllowed() {
    handler.removeMessages(MSG_HIDE_ACTIONBAR)
    val hasSelection = selectionStart != null
    val isSheetOpen = ::hybridReaderController.isInitialized && hybridReaderController.uiState.value.isAnySheetOpen
    val isModalOpen = ::overlay.isInitialized && overlay.isVisible
    if (!hasSelection && !isSheetOpen && !isModalOpen) {
      handler.sendEmptyMessageDelayed(MSG_HIDE_ACTIONBAR, DEFAULT_HIDE_AFTER_TIME)
    }
  }

  private fun onPageClicked() {
    if (::hybridReaderOverlay.isInitialized && hybridReaderOverlay.dismissTafsirPicker()) {
      updateHybridBackCallbackState()
      return
    }
    if (::hybridReaderController.isInitialized && hybridReaderController.uiState.value.isAppearanceSheetOpen) {
      hybridReaderController.closeAppearanceSheet()
      updateHybridBackCallbackState()
      return
    }
    if (::hybridReaderController.isInitialized && hybridReaderController.uiState.value.isStudyDrawerOpen) {
      hybridReaderController.closeStudyDrawer()
      updateHybridBackCallbackState()
      return
    }
    toggleActionBar()
  }

  private fun onAudioPlaybackAyahChanged(suraAyah: SuraAyah?) {
    if (suraAyah != null) {
      // continue to snap back to the page when the playback ayah changes
      ensurePage(suraAyah.sura, suraAyah.ayah)
    }
  }

  private fun onAyahSelectionChanged(ayahSelection: AyahSelection) {
    val haveSelection = ayahSelection !== AyahSelection.None
    val currentSelection = ayahSelection.selectionIndicator()
    if (currentSelection is SelectionIndicator.None && haveSelection) {
      viewPager.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    if (haveSelection) {
      handler.removeMessages(MSG_HIDE_ACTIONBAR)
      val startPosition = startPosition(ayahSelection)
      updateLocalTranslations(startPosition)
      onClearAyahModeBackCallback.isEnabled = selectionStart != null
      val endPosition = ayahSelection.endSuraAyah()
      val bounds = if (startPosition != null) computeAyahOverlayBounds(startPosition, endPosition, ayahSelection.selectionIndicator()) else null
      if (::hybridReaderController.isInitialized) {
        hybridReaderController.onAyahSelectionChanged(startPosition, endPosition, bounds)
      }
      if (bounds == null && startPosition != null) {
        viewPager.post {
          if (!isFinishing && !isDestroyed) {
            refreshHybridAyahBounds()
          }
        }
      }
    } else {
      endAyahMode()
      onClearAyahModeBackCallback.isEnabled = false
      if (::hybridReaderController.isInitialized) {
        hybridReaderController.onAyahSelectionChanged(null, null, null)
      }
    }
    updateHybridBackCallbackState()
  }

  private fun startPosition(ayahSelection: AyahSelection): SuraAyah? {
    return when (ayahSelection) {
      is AyahSelection.Ayah -> {
        ayahSelection.suraAyah
      }

      is AyahRange -> {
        ayahSelection.startSuraAyah
      }

      else -> {
        null
      }
    }
  }

  private fun setUiVisibility(isVisible: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      setUiVisibilityR(isVisible)
    } else {
      setUiVisibilityKitKat(isVisible)
    }
  }

  private fun setUiVisibilityR(isVisible: Boolean) {
    if (isVisible) {
      windowInsetsController.show(
        WindowInsetsCompat.Type.statusBars() or
            WindowInsetsCompat.Type.navigationBars()
      )
    } else {
      windowInsetsController.hide(
        WindowInsetsCompat.Type.statusBars() or
            WindowInsetsCompat.Type.navigationBars()
      )
      windowInsetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode) {
      animateToolBar(isVisible)
    }
  }

  private fun setUiVisibilityKitKat(isVisible: Boolean) {
    var flags = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    if (!isVisible) {
      flags = flags or (View.SYSTEM_UI_FLAG_LOW_PROFILE
          or View.SYSTEM_UI_FLAG_FULLSCREEN
          or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          or View.SYSTEM_UI_FLAG_IMMERSIVE)
    }
    viewPager.systemUiVisibility = flags

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode) {
      animateToolBar(isVisible)
    }
  }

  private fun setUiVisibilityListener() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
        val isStatusBarVisible = insets.isVisible(WindowInsetsCompat.Type.statusBars())
        // on devices with "hide full screen indicator" or "hide the bottom bar,"
        // this always returns false, which causes the touches to not work.
        val isNavigationBarVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars())

        // as a fix for the aforementioned point, make either one's visibility suggest
        // visibility (instead of requiring both to agree).
        val isVisible = isStatusBarVisible || isNavigationBarVisible

        animateToolBar(isVisible)
        insets
      }
    } else {
      viewPager.setOnSystemUiVisibilityChangeListener { flags: Int ->
        val visible = (flags and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0
        animateToolBar(visible)
      }
    }
  }

  private fun clearUiVisibilityListener() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      ViewCompat.setOnApplyWindowInsetsListener(window.decorView, null)
    }
    viewPager.setOnSystemUiVisibilityChangeListener(null)
  }

  private fun updateSystemBarAppearance() {
    if (!::windowInsetsController.isInitialized) {
      return
    }
    val lightNavigationBar = resources.getBoolean(R.bool.light_navigation_bar)
    val isLightToolbar = ThemeUtil.resolveThemeBoolean(
      this,
      R.attr.quranIsLightToolbar,
      fallback = lightNavigationBar
    )
    windowInsetsController.isAppearanceLightStatusBars = shouldUseLightStatusBar(
      isActionBarHidden = isActionBarHidden,
      isNightMode = quranSettings.isNightMode,
      isLightToolbar = isLightToolbar
    )
    windowInsetsController.isAppearanceLightNavigationBars = shouldUseLightSystemBars(
      isActionBarHidden = isActionBarHidden,
      isNightMode = quranSettings.isNightMode,
      lightNavigationBar = lightNavigationBar
    )
  }

  private fun animateToolBar(visible: Boolean) {
    isActionBarHidden = !visible
    updateSystemBarAppearance()
    toolBarArea.visibility = View.GONE
    audioStatusBar.visibility = View.GONE
    ayahToolBar.visibility = View.GONE
    if (::hybridReaderController.isInitialized) {
      hybridReaderController.setChromeVisible(visible)
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val navigate = (audioStatusRepositoryBridge.audioRequest() == null
        && quranSettings.navigateWithVolumeKeys())
    if (navigate && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
      viewPager.currentItem -= 1
      return true
    } else if (navigate && keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      viewPager.currentItem += 1
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    return (((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
        keyCode == KeyEvent.KEYCODE_VOLUME_UP) && (
        audioStatusRepositoryBridge.audioRequest() == null) &&
        quranSettings.navigateWithVolumeKeys())
        || super.onKeyUp(keyCode, event))
  }

  override fun onStart() {
    super.onStart()
    BitmapPoolTrimRegistry.notifyRestore()
  }

  public override fun onResume() {
    super.onResume()
    if (AppThemeStyle.fromPreference(quranSettings.appThemeStyle) != appliedThemeStyle) {
      recreate()
      return
    }
    quranSettings.registerPreferencesListener(themePreferencesListener)
    refreshQuranPages()
    updateSystemBarAppearance()
    if (::hybridReaderController.isInitialized) {
      hybridReaderController.syncAppearanceState()
      hybridReaderController.refreshTafsirAfterResume()
    }
    applyHybridMushafBackdrop()

    audioPresenter.bind(this)
    recentPagePresenter.bind(currentPageFlow)
    readingBookmarkPresenter.bind(currentPageFlow, this)

    if (shouldReconnect) {
      shouldReconnect = false
      foregroundDisposable.add(
        Completable.timer(500, TimeUnit.MILLISECONDS)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe {
            if (!AudioService.isRunning()) {
              return@subscribe
            }
            try {
              startService(
                audioUtils.getAudioIntent(
                  this@PagerActivity,
                  AudioService.ACTION_CONNECT
                )
              )
            } catch (ise: IllegalStateException) {
              // we're likely in the background, so ignore.
            }
          })
    }
  }

  override fun inject(audioBarWrapper: AudioBarWrapper) {
    pagerActivityComponent.inject(audioBarWrapper)
  }

  override fun injectQariListWrapper(qariListWrapper: QariListWrapper) {
    pagerActivityComponent.inject(qariListWrapper)
  }

  override fun injectAyahBookmarkWrapper(ayahBookmarkWrapper: AyahBookmarkWrapper) {
    pagerActivityComponent.inject(ayahBookmarkWrapper)
  }

  override fun injectReadingBookmarkSheetWrapper(
    readingBookmarkSheetWrapper: ReadingBookmarkSheetWrapper
  ) {
    pagerActivityComponent.inject(readingBookmarkSheetWrapper)
  }

  override fun provideQuranReadingActivityComponent(): QuranReadingActivityComponent {
    return pagerActivityComponent
  }

  override fun provideQuranReadingPageComponent(vararg pages: Int): QuranReadingPageComponent {
    return pagerActivityComponent
      .quranPageComponentFactory()
      .generate(pages)
  }

  override fun injectToolBar(ayahToolBar: AyahToolBar) {
    pagerActivityComponent
      .inject(ayahToolBar)
  }

  private fun canShowUi(): Boolean {
    return !isFinishing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
  }

  fun showGetRequiredFilesDialog() {
    if (canShowUi() && promptDialog == null) {
      val builder = AlertDialog.Builder(this)
      builder.setMessage(R.string.download_extra_data)
        .setPositiveButton(
          R.string.downloadPrompt_ok
        ) { dialog: DialogInterface, _: Int ->
          scope.launch { downloadRequiredFiles() }
          dialog.dismiss()
          promptDialog = null
        }
        .setNegativeButton(
          R.string.downloadPrompt_no
        ) { dialog: DialogInterface, _: Int ->
          dialog.dismiss()
          promptDialog = null
        }
      val dialog = builder.create()
      promptDialog = dialog
      dialog.show()
    }
  }

  private suspend fun downloadRequiredFiles() {
    val hasArabicDatabase = withContext(Dispatchers.IO) {
      quranFileUtils.hasArabicSearchDatabase()
    }

    if (canShowUi()) {
      var downloadType = QuranDownloadService.DOWNLOAD_TYPE_AUDIO
      if (audioStatusRepositoryBridge.audioRequest() == null) {
        // if we're not playing any audio, use audio download bar as our progress bar
        if (isActionBarHidden) {
          toggleActionBar()
        }
      } else {
        // if audio is playing, let's not disrupt it - do this using a
        // different type so the broadcast receiver ignores it.
        downloadType = QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB
      }

      var haveDownload = false
      if (!quranFileUtils.haveAyaPositionFile()) {
        var url = quranFileUtils.ayaPositionFileUrl
        if (isDualPages) {
          url = quranFileUtils.getAyaPositionFileUrl(
            quranScreenInfo.tabletWidthParam
          )
        }
        val destination = quranFileUtils.quranAyahDatabaseDirectory
        // start the download
        val notificationTitle = getString(R.string.highlighting_database)
        val intent = getDownloadIntent(
          this, url,
          destination.absolutePath, notificationTitle, AUDIO_DOWNLOAD_KEY,
          downloadType
        )
        Timber.d("starting service to download ayah position file")
        startService(intent)

        haveDownload = true
      }

      if (!hasArabicDatabase) {
        val url = quranFileUtils.arabicSearchDatabaseUrl

        // show "downloading required files" unless we already showed that for
        // highlighting database, in which case show "downloading search data"
        var notificationTitle = getString(R.string.highlighting_database)
        if (haveDownload) {
          notificationTitle = getString(R.string.search_data)
        }

        val extension = if (url.endsWith(".zip")) ".zip" else ""
        val intent = getDownloadIntent(
          this, url,
          quranFileUtils.getQuranDatabaseDirectory().absolutePath, notificationTitle,
          AUDIO_DOWNLOAD_KEY, downloadType
        )
        intent.putExtra(
          QuranDownloadService.EXTRA_OUTPUT_FILE_NAME,
          QuranDataProvider.QURAN_ARABIC_DATABASE + extension
        )
        Timber.d("starting service to download arabic database")
        startService(intent)
      }

      if (downloadType != QuranDownloadService.DOWNLOAD_TYPE_AUDIO) {
        // if audio is playing, just show a status notification
        makeText(
          this, com.quran.mobile.common.download.R.string.downloading,
          Toast.LENGTH_SHORT
        ).show()
      }
    }
  }

  public override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)

    recentPagePresenter.onJump()
    val extras = intent.extras
    if (extras != null) {
      val parsed = HybridReaderIntentParser.parse(intent, quranInfo, Constants.PAGES_FIRST)
      val page = parsed.page

      val currentValue = showingTranslation
      showingTranslation = parsed.jumpToTranslation || showingTranslation
      val highlightedSura = parsed.highlightSura
      val highlightedAyah = parsed.highlightAyah
      if (highlightedSura != null && highlightedAyah != null) {
        readingEventPresenterBridge.setSelection(highlightedSura, highlightedAyah, true)
      }

      if (showingTranslation != currentValue) {
        if (showingTranslation) {
          pagerAdapter.setTranslationMode()
          updateActionBarSpinner()
          onShowingTranslationBackCallback.isEnabled = true
        } else {
          pagerAdapter.setQuranMode()
          updateActionBarTitle(page)
          onShowingTranslationBackCallback.isEnabled = false
        }

        supportInvalidateOptionsMenu()
      }

      if (highlightedAyah != null && highlightedSura != null) {
        // this will jump to the right page automagically
        ensurePage(highlightedSura, highlightedAyah)
      } else {
        val pagePosition = quranInfo.getPositionFromPage(page, isDualPageVisible)
        viewPager.currentItem = pagePosition
      }

      if (::hybridReaderController.isInitialized) {
        hybridReaderController.updateCurrentPage(page)
        schedulePageSettle(page)
        parsed.initialStudyTab?.let { tab ->
          hybridReaderController.openStudyDrawer(tab)
          updateHybridBackCallbackState()
        }
      }

      if (parsed.autoPlayAyah && highlightedSura != null && highlightedAyah != null) {
        viewPager.post {
          if (!isFinishing && !isDestroyed) {
            playFromAyah(highlightedSura, highlightedAyah)
          }
        }
      }

      setIntent(intent)
    }
  }

  override fun jumpTo(page: Int) {
    quranNavigator.jumpTo(Page(page))
  }

  override fun jumpToAndHighlight(page: Int, sura: Int, ayah: Int) {
    quranNavigator.jumpTo(SuraAyah(sura, ayah))
  }

  public override fun onPause() {
    quranSettings.unregisterPreferencesListener(themePreferencesListener)
    foregroundDisposable.clear()
    pageSettleDebounceJob?.cancel()
    if (::hybridReaderController.isInitialized && ::viewPager.isInitialized) {
      hybridReaderController.recordSettledPage(currentPage)
    }
    promptDialog?.dismiss()
    promptDialog = null
    recentPagePresenter.unbind()
    readingBookmarkPresenter.unbind(this)
    quranSettings.wasShowingTranslation = pagerAdapter.isShowingTranslation

    super.onPause()
  }

  override fun onStop() {
    // the activity will be paused when requesting notification
    // permissions, which will otherwise break audio presenter.
    audioPresenter.unbind(this)
    if (!isChangingConfigurations) {
      if (::pagerAdapter.isInitialized) {
        if (isFinishing) {
          pagerAdapter.clearViewPool()
        } else {
          pagerAdapter.clearDetachedViewPool()
        }
      }
      LineBitmapRecycler.recycleNow()
      BitmapPoolTrimRegistry.clearAll()
      LineBitmapRecycler.recycleNow()
      SQLiteDatabase.releaseMemory()
    }
    if (::hybridReaderOverlay.isInitialized) {
      hybridReaderOverlay.cancelPendingWarmupsOnStop()
    }
    super.onStop()
  }

  override fun onDestroy() {
    Timber.d("onDestroy()")
    clearUiVisibilityListener()

    translationJob?.cancel(CancellationException())
    pageSettleDebounceJob?.cancel()
    qariListObserverJob?.cancel()
    qariListObserverJob = null
    compositeDisposable.dispose()
    audioStatusRepositoryBridge.dispose()
    readingEventPresenterBridge.dispose()
    downloadBridge.unsubscribe()
    handler.removeCallbacksAndMessages(null)
    scope.cancel()

    if (::onEndSessionBackCallback.isInitialized) {
      onEndSessionBackCallback.remove()
    }
    if (::onShowingTranslationBackCallback.isInitialized) {
      onShowingTranslationBackCallback.remove()
    }
    if (::onClearAyahModeBackCallback.isInitialized) {
      onClearAyahModeBackCallback.remove()
    }
    if (::onHybridOverlayOrDrawerBackCallback.isInitialized) {
      onHybridOverlayOrDrawerBackCallback.remove()
    }

    if (::viewPager.isInitialized) {
      viewPager.animate().setListener(null)
      viewPager.animate().cancel()
      viewPager.setLayerType(View.LAYER_TYPE_NONE, null)
      pageChangeListener?.let { viewPager.removeOnPageChangeListener(it) }
      pageChangeListener = null
      viewPager.clearOnPageChangeListeners()
      viewPager.adapter = null
    }
    if (::pagerAdapter.isInitialized) {
      pagerAdapter.clearViewPool()
    }
    LineBitmapRecycler.recycleNow()
    if (isSlidingPagerAttached && ::slidingPager.isInitialized) {
      slidingPager.adapter = null
    }
    if (::overlay.isInitialized) {
      overlay.removeAllViews()
      (overlay.parent as? ViewGroup)?.removeView(overlay)
    }
    if (::hybridReaderOverlay.isInitialized) {
      hybridReaderOverlay.unbind()
      (hybridReaderOverlay.parent as? ViewGroup)?.removeView(hybridReaderOverlay)
    }
    window?.setBackgroundDrawable(null)

    val shouldScheduleGc = isFinishing && !isChangingConfigurations
    val isConfigChange = isChangingConfigurations
    super.onDestroy()
    BitmapPoolTrimRegistry.clearAll()
    LineBitmapRecycler.recycleNow()
    if (!isConfigChange) {
      QuranUtils.clearLocalizedNumberCache()
      SearchResultFormatter.clearCache()
      clearSuraHeaderCache()
    }
    if (shouldScheduleGc) {
      PostDestroyMemoryReclaimer.schedulePostDestroyCleanup()
    }
  }

  private fun clearSuraHeaderCache() {
    try {
      val clazz = Class.forName(SURA_HEADER_DRAWER_CLASS)
      clazz.getMethod("clearCache").invoke(null)
    } catch (_: Throwable) {
      // Flavor may not include SuraHeaderDrawer; ignore safely.
    }
  }

  private fun onSessionEnd() {
    onEndSessionBackCallback.isEnabled = false
    pagerActivityRecitationPresenter.onSessionEnd()
  }

  public override fun onSaveInstanceState(state: Bundle) {
    val lastPage = quranInfo.getPageFromPosition(viewPager.currentItem, isDualPageVisible)
    state.putInt(LAST_READ_PAGE, lastPage)
    state.putBoolean(LAST_READING_MODE_IS_TRANSLATION, showingTranslation)
    state.putBoolean(LAST_ACTIONBAR_STATE, isActionBarHidden)
    state.putBoolean(LAST_WAS_DUAL_PAGES, isDualPages)
    state.putBoolean(LAST_FOLDING_STATE, isFoldableDeviceOpenAndVertical)
    state.putBoolean(LAST_SLIDER_INITIALIZED, isSlidingPagerAttached)
    if (::hybridReaderController.isInitialized) {
      val ui = hybridReaderController.uiState.value
      state.putBoolean(KEY_HYBRID_DRAWER_OPEN, ui.isStudyDrawerOpen)
      state.putBoolean(KEY_HYBRID_APPEARANCE_OPEN, ui.isAppearanceSheetOpen)
      state.putString(KEY_HYBRID_STUDY_TAB, ui.selectedStudyTab.extraValue)
      ui.selectedStart?.let { sel ->
        state.putInt(KEY_HYBRID_SELECTED_SURA, sel.sura)
        state.putInt(KEY_HYBRID_SELECTED_AYAH, sel.ayah)
      }
      ui.selectedEnd?.let { endSel ->
        state.putInt(KEY_HYBRID_SELECTED_END_SURA, endSel.sura)
        state.putInt(KEY_HYBRID_SELECTED_END_AYAH, endSel.ayah)
      }
    }
    super.onSaveInstanceState(state)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    super.onCreateOptionsMenu(menu)
    val inflater = menuInflater
    inflater.inflate(R.menu.quran_menu, menu)
    val item = menu.findItem(R.id.search)
    val searchView = item.actionView as SearchView
    val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
    searchView.queryHint = getString(R.string.search_hint)
    searchView.setSearchableInfo(
      searchManager.getSearchableInfo(
        ComponentName(this, SearchActivity::class.java)
      )
    )

    // cache because invalidateOptionsMenu in a toolbar world always calls both
    // onCreateOptionsMenu and onPrepareOptionsMenu, which can be expensive both
    // due to inflation plus due to the search view specific setup work. we can
    // directly modify the bookmark item using a reference to this instead.
    bookmarksMenuItem = menu.findItem(R.id.favorite_item)
    return true
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    super.onPrepareOptionsMenu(menu)

    if (bookmarksMenuItem != null) {
      refreshBookmarksMenu()
    }

    val quran = menu.findItem(R.id.goto_quran)
    val translation = menu.findItem(R.id.goto_translation)
    if (quran != null && translation != null) {
      if (!showingTranslation) {
        quran.isVisible = false
        translation.isVisible = true
      } else {
        quran.isVisible = true
        translation.isVisible = false
      }
    }
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val itemId = item.itemId
    if (itemId == R.id.favorite_item) {
      showReadingBookmarkSheet(ReadingBookmarkTarget.Page(currentPage))
      return true
    } else if (itemId == R.id.goto_quran) {
      switchToQuran()
      return true
    } else if (itemId == R.id.goto_translation) {
      if (translations != null) {
        quranEventLogger.switchToTranslationMode(translations!!.size)
        switchToTranslation()
      }
      return true
    } else if (itemId == R.id.page_theme) {
      showPageThemeSheet()
      return true
    } else if (itemId == R.id.settings) {
      val i = Intent(this, QuranPreferenceActivity::class.java)
      startActivity(i)
      return true
    } else if (itemId == R.id.help) {
      val i = Intent(this, HelpActivity::class.java)
      startActivity(i)
      return true
    } else if (itemId == android.R.id.home) {
      onSessionEnd()
      finish()
      return true
    } else if (itemId == R.id.jump) {
      val fm = supportFragmentManager
      val jumpDialog = JumpFragment()
      jumpDialog.show(fm, JumpFragment.TAG)
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  private fun refreshQuranPages() {
    val pos = viewPager.currentItem
    val start = if (pos == 0) 0 else pos - 1
    val end = if (pos == pagerAdapter.count - 1) pos else pos + 1
    for (i in start..end) {
      val f = pagerAdapter.getFragmentIfExists(i)
      if (f is QuranPage) {
        (f as QuranPage).updateView()
      }
    }
    pagerAdapter.invalidateActivePageViews()
  }

  private fun switchToQuran() {
    if (selectionStart != null) {
      endAyahMode()
    }
    val page = currentPage
    pagerAdapter.setQuranMode()
    showingTranslation = false
    onShowingTranslationBackCallback.isEnabled = false
    if (shouldUpdatePageNumber()) {
      val position = quranInfo.getPositionFromPage(page, true)
      viewPager.currentItem = position
    }

    supportInvalidateOptionsMenu()
    updateActionBarTitle(page)
  }

  private fun switchToTranslation() {
    if (selectionStart != null) {
      endAyahMode()
    }

    if (translations!!.isEmpty()) {
      startTranslationManager()
    } else {
      val page = currentPage
      pagerAdapter.setTranslationMode()
      showingTranslation = true
      onShowingTranslationBackCallback.isEnabled = true
      if (shouldUpdatePageNumber()) {
        val position = quranInfo.getPositionFromPage(page, false)
        viewPager.currentItem = position
      }
      supportInvalidateOptionsMenu()
      updateActionBarSpinner()
    }

    scope.launch {
      val hasArabicDatabase = withContext(Dispatchers.IO) {
        quranFileUtils.hasArabicSearchDatabase()
      }
      if (!canShowUi()) {
        return@launch
      }
      if (!hasArabicDatabase && !promptedForExtraDownload) {
        promptedForExtraDownload = true
        showGetRequiredFilesDialog()
      }
    }
  }

  fun startTranslationManager() {
    startActivity(Intent(this, TranslationManagerActivity::class.java))
  }

  private val translationItemChangedListener =
    TranslationsSpinnerAdapter.OnSelectionChangedListener { selectedItems: Set<String?>? ->
      quranSettings.activeTranslations = selectedItems
      val pos = viewPager.currentItem - 1
      for (count in 0..2) {
        if (pos + count < 0) {
          continue
        }
        val f = pagerAdapter.getFragmentIfExists(pos + count)
        if (f is TranslationFragment) {
          f.refresh()
        } else if (f is TabletFragment) {
          f.refresh()
        }
      }
    }

  override fun onAddTagSelected() {
    val fm = supportFragmentManager
    val dialog = AddTagDialog()
    dialog.show(fm, AddTagDialog.TAG)
  }

  private fun updateActionBarTitle(page: Int) {
    if (::toolBarArea.isInitialized && toolBarArea.visibility != View.VISIBLE) {
      return
    }
    val sura = quranDisplayData.getSuraNameFromPage(this, page, true)
    val actionBar = supportActionBar
    if (actionBar != null) {
      translationsSpinner.visibility = View.GONE
      actionBar.setDisplayShowTitleEnabled(true)
      actionBar.title = sura
      val desc = quranDisplayData.getPageSubtitle(this, page)
      actionBar.subtitle = desc
    }
  }

  private fun refreshActionBarSpinner() {
    if (translationsSpinnerAdapter != null) {
      translationsSpinnerAdapter!!.notifyDataSetChanged()
    } else {
      updateActionBarSpinner()
    }
  }

  private val currentPage: Int
    get() = quranInfo.getPageFromPosition(viewPager.currentItem, isDualPageVisible)

  private fun updateActionBarSpinner() {
    if (translationNames.isEmpty()) {
      val page = currentPage
      updateActionBarTitle(page)
      return
    }

    if (translationsSpinnerAdapter == null) {
      translationsSpinnerAdapter = object : TranslationsSpinnerAdapter(
        this,
        R.layout.translation_ab_spinner_item, translationNames, translations,
        if (activeTranslationsFilesNames == null) quranSettings.activeTranslations else activeTranslationsFilesNames,
        translationItemChangedListener
      ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
          val type = super.getItemViewType(position)
          val view = super.getView(position, convertView, parent)
          if (type == 0) {
            val holder = view.tag as SpinnerHolder
            val page: Int = currentPage

            val sura =
              quranDisplayData.getSuraNameFromPage(this@PagerActivity, page, true)
            holder.title.text = sura
            holder.title.setTextColor(
              ThemeUtil.resolveThemeColor(
                this@PagerActivity,
                R.attr.quranToolbarText,
                ResourcesCompat.getColor(resources, R.color.toolbar_text, null)
              )
            )
            val desc = quranDisplayData.getPageSubtitle(this@PagerActivity, page)
            holder.subtitle.text = desc
            holder.subtitle.visibility = View.VISIBLE
            holder.subtitle.setTextColor(
              ThemeUtil.resolveThemeColor(
                this@PagerActivity,
                R.attr.quranToolbarSecondaryText,
                ResourcesCompat.getColor(resources, R.color.toolbar_secondary_text, null)
              )
            )
          }
          return view
        }
      }
      translationsSpinner.adapter = translationsSpinnerAdapter
    }

    val actionBar = supportActionBar
    if (actionBar != null) {
      actionBar.setDisplayShowTitleEnabled(false)
      translationsSpinner.visibility = View.VISIBLE
    }
  }

  private fun onDownloadSuccess() {
    refreshQuranPages()
    audioPresenter.onDownloadSuccess()
  }

  fun toggleActionBarVisibility(visible: Boolean) {
    if (visible == isActionBarHidden) {
      toggleActionBar()
    }
  }

  fun toggleActionBar() {
    if (isActionBarHidden) {
      setUiVisibility(true)
      animateToolBar(true)
    } else {
      handler.removeMessages(MSG_HIDE_ACTIONBAR)
      setUiVisibility(false)
      animateToolBar(false)
    }
  }

  private fun ensurePage(sura: Int, ayah: Int) {
    val page = quranInfo.getPageFromSuraAyah(sura, ayah)
    if (quranInfo.isValidPage(page)) {
      val position = quranInfo.getPositionFromPage(page, isDualPageVisible)
      if (position != viewPager.currentItem) {
        viewPager.currentItem = position
      }
    }
  }

  private fun requestTranslationsList() {
    translationJob =
      translationListPresenter.registerForTranslations { titles: Array<String>, updatedTranslations: List<LocalTranslation> ->
        var currentActiveTranslationsFilesNames = quranSettings.activeTranslations
        if (currentActiveTranslationsFilesNames.isEmpty() && updatedTranslations.isNotEmpty()) {
          currentActiveTranslationsFilesNames = HashSet()
          val items = updatedTranslations.size
          for (i in 0 until items) {
            currentActiveTranslationsFilesNames.add(updatedTranslations[i].filename)
          }
        }
        activeTranslationsFilesNames = currentActiveTranslationsFilesNames

        if (translationsSpinnerAdapter != null) {
          translationsSpinnerAdapter!!
            .updateItems(titles, updatedTranslations, activeTranslationsFilesNames)
        }
        translationNames = titles
        translations = updatedTranslations
        if (showingTranslation) {
          // Since translation items have changed, need to
          updateActionBarSpinner()
        }
      }
  }

  private fun refreshBookmarksMenu() {
    refreshBookmarksMenu(isCurrentPageReadingBookmarked)
  }

  private fun refreshBookmarksMenu(isBookmarked: Boolean) {
    val menuItem = bookmarksMenuItem
    if (menuItem != null) {
      menuItem.setIcon(if (isBookmarked) com.quran.labs.androidquran.common.toolbar.R.drawable.ic_favorite else com.quran.labs.androidquran.common.toolbar.R.drawable.ic_not_favorite)
    } else {
      supportInvalidateOptionsMenu()
    }
  }

  override fun setPageReadingBookmarkSelected(isBookmarked: Boolean) {
    isCurrentPageReadingBookmarked = isBookmarked
    refreshBookmarksMenu(isBookmarked)
    if (::hybridReaderController.isInitialized) {
      hybridReaderController.setCurrentPageBookmarked(isBookmarked)
    }
  }

  override fun showReadingBookmarkChangedToast(
    change: ReadingBookmarkChange,
    isEducation: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
  ) {
    val rootView = findViewById<ViewGroup>(android.R.id.content)
    readingBookmarkToastView?.let { rootView.removeView(it) }

    val toastView = createReadingBookmarkToastView(
      this,
      change,
      isEducation,
      quranNaming,
      onUndo = onUndo,
      onDismiss = onDismiss
    )
    readingBookmarkToastView = toastView
    rootView.addView(
      toastView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM
      )
    )
    ViewCompat.setOnApplyWindowInsetsListener(toastView) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
      )
      view.updatePadding(left = insets.left, right = insets.right)
      val params = view.layoutParams as FrameLayout.LayoutParams
      val capsuleReservePx = insets.bottom + (86f * resources.displayMetrics.density).toInt()
      params.bottomMargin = readingBookmarkToastBottomMargin(
        isActionBarHidden = isActionBarHidden,
        isAudioBarVisible = isHybridBottomCapsuleVisible(),
        audioBarHeight = capsuleReservePx,
        bottomInset = insets.bottom
      )
      view.layoutParams = params
      windowInsets
    }
    ViewCompat.requestApplyInsets(toastView)
  }

  override fun dismissReadingBookmarkChangedToast() {
    readingBookmarkToastView?.let {
      findViewById<ViewGroup>(android.R.id.content).removeView(it)
    }
    readingBookmarkToastView = null
  }

  // region Audio playback
  private fun currentQariItem(): QariItem {
    return fromQari(this, qariManager.currentQari())
  }

  override fun onContinuePlaybackPressed() {
    val activeRequest = audioStatusRepositoryBridge.audioRequest()
    val selectedQari = currentQariItem()
    if (activeRequest != null && activeRequest.qari.id != selectedQari.id) {
      val uiState = if (::hybridReaderController.isInitialized) {
        hybridReaderController.uiState.value
      } else {
        null
      }
      val resumeAyah = uiState?.selectedStart
        ?: uiState?.playbackAyah
        ?: activeRequest.start
      playFromAyah(resumeAyah.sura, resumeAyah.ayah)
    } else {
      handlePlayback(null)
    }
  }

  override fun onPlayPressed() {
    val position = viewPager.currentItem
    val page = quranInfo.getPageFromPosition(position, isDualPageVisible)

    // log the event
    quranEventLogger.logAudioPlayback(
      QuranEventLogger.AudioPlaybackSource.PAGE,
      currentQariItem(), isDualPages, showingTranslation, isSplitScreen
    )

    val startSura = quranDisplayData.safelyGetSuraOnPage(page)
    val startAyah = quranInfo.getFirstAyahOnPage(page)
    val startingSuraList = quranInfo.getListOfSurahWithStartingOnPage(page)
    if (startingSuraList.isEmpty() ||
      (startingSuraList.size == 1 && startingSuraList[0] == startSura)
    ) {
      playFromAyah(startSura, startAyah)
    } else {
      promptForMultipleChoicePlay(page, startSura, startAyah, startingSuraList)
    }
  }

  private fun playFromAyah(startSura: Int, startAyah: Int) {
    val page = quranInfo.getPageFromSuraAyah(startSura, startAyah)
    val start = SuraAyah(startSura, startAyah)
    val end = selectionEnd
    // handle the case of multiple ayat being selected and play them as a range if so
    val ending = if ((end == null || start == end || start.after(end))) null else end
    val pendingSpeed = if (::hybridReaderController.isInitialized) {
      hybridReaderController.pendingPlaybackSpeed
    } else {
      1.0f
    }
    val pendingRepeat = if (::hybridReaderController.isInitialized) {
      hybridReaderController.pendingRepeatCount
    } else {
      0
    }
    playFromAyah(start, ending, page, pendingRepeat, 0, ending != null, pendingSpeed)
  }

  fun playFromAyah(
    start: SuraAyah,
    end: SuraAyah?,
    page: Int,
    verseRepeat: Int,
    rangeRepeat: Int,
    enforceRange: Boolean,
    playbackSpeed: Float
  ) {
    val ending = end
      ?: audioUtils.getLastAyahToPlay(
        start, page,
        quranSettings.preferredDownloadAmount, isDualPageVisible
      )

    if (ending != null) {
      Timber.d(
        "playFromAyah - " + start + ", ending: " +
            ending + " - original: " + end + " -- " +
            quranSettings.preferredDownloadAmount
      )
      val item = currentQariItem()
      val shouldStream = quranSettings.shouldStream()
      audioPresenter.play(
        start,
        ending,
        item,
        verseRepeat,
        rangeRepeat,
        enforceRange,
        playbackSpeed,
        shouldStream
      )
    }
  }

  override fun handleRequiredDownload(downloadIntent: Intent?) {
    var needsPermission = needsPermissionToDownloadOver3g
    if (needsPermission) {
      if (QuranUtils.isOnWifiNetwork(this)) {
        Timber.d("on wifi, don't need permission for download...")
        needsPermission = false
      }
    }

    if (needsPermission) {
      downloadInfoStreams.requestDownloadNetworkPermission()
    } else if (!havePostNotificationPermission(this)) {
      if (canRequestPostNotificationPermission(this)) {
        promptDialog = buildPostPermissionDialog(
          this,
          {
            promptDialog = null
            requestPermissionLauncher!!.launch(Manifest.permission.POST_NOTIFICATIONS)
          }, {
            proceedWithDownload(downloadIntent)
            promptDialog = null
          })
        promptDialog!!.show()
      } else {
        requestPermissionLauncher!!.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    } else {
      proceedWithDownload(downloadIntent)
    }
  }

  override fun proceedWithDownload(downloadIntent: Intent?) {
    if (isActionBarHidden) {
      toggleActionBar()
    }
    downloadInfoStreams.downloadRequested()
    Timber.d("starting service in handleRequiredDownload")
    startService(downloadIntent)
  }

  override fun handlePlayback(request: AudioRequest?) {
    needsPermissionToDownloadOver3g = true
    val intent = Intent(this, AudioService::class.java)
    intent.setAction(AudioService.ACTION_PLAYBACK)
    if (request != null) {
      intent.putExtra(AudioService.EXTRA_PLAY_INFO, request)
    }

    Timber.d("starting service for audio playback")
    startService(intent)
  }

  override fun onPausePressed() {
    startService(
      audioUtils.getAudioIntent(
        this, AudioService.ACTION_PAUSE
      )
    )
  }

  override fun setPlaybackSpeed(speed: Float) {
    val lastAudioRequest = audioStatusRepositoryBridge.audioRequest()
    if (lastAudioRequest != null) {
      val updatedAudioRequest = lastAudioRequest.copy(playbackSpeed = speed)

      val i = Intent(this, AudioService::class.java)
      i.setAction(AudioService.ACTION_UPDATE_SETTINGS)
      i.putExtra(AudioService.EXTRA_PLAY_INFO, updatedAudioRequest)
      startService(i)
    }
  }

  override fun onNextPressed() {
    startService(
      audioUtils.getAudioIntent(
        this,
        AudioService.ACTION_SKIP
      )
    )
  }

  override fun onPreviousPressed() {
    startService(
      audioUtils.getAudioIntent(
        this,
        AudioService.ACTION_REWIND
      )
    )
  }

  override fun onAudioSettingsPressed() {
    showSlider(slidingPagerAdapter.getPagePosition(SlidingPagerAdapter.AUDIO_PAGE))
  }

  override fun onShowQariList() {
    val page = currentPage
    val start = selectionStart
    val end = selectionEnd

    val startSura = quranDisplayData.safelyGetSuraOnPage(page)
    val startAyah = quranInfo.getFirstAyahOnPage(page)

    val starting = start ?: SuraAyah(startSura, startAyah)
    val ending = end
      ?: audioUtils.getLastAyahToPlay(
        starting, page,
        quranSettings.preferredDownloadAmount, isDualPageVisible
      )!!
    val initialQariId = qariManager.currentQari().id
    val qariListWrapper = QariListWrapper(this, starting, ending)
    qariListObserverJob?.cancel()
    qariListObserverJob = null
    overlay.removeAllViews()
    qariListWrapper.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(v: View) = Unit
      override fun onViewDetachedFromWindow(v: View) {
        qariListObserverJob?.cancel()
        qariListObserverJob = null
      }
    })
    overlay.addView(
      qariListWrapper,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    handler.removeMessages(MSG_HIDE_ACTIONBAR)
    toggleActionBarVisibility(false)
    updateHybridBackCallbackState()

    qariListObserverJob = scope.launch {
      qariManager.flow().collectLatest { qari ->
        if (qari.id != initialQariId && overlay.isVisible && overlay.getChildAt(0) === qariListWrapper) {
          delay(250L)
          if (!isFinishing && !isDestroyed && overlay.getChildAt(0) === qariListWrapper) {
            overlay.removeAllViews()
            overlay.visibility = View.GONE
            toggleActionBarVisibility(true)
            updateHybridBackCallbackState()
            qariListObserverJob?.cancel()
            qariListObserverJob = null

            val uiState = if (::hybridReaderController.isInitialized) {
              hybridReaderController.uiState.value
            } else {
              null
            }
            val currentPlaybackStatus = uiState?.playbackStatus
              ?: (audioStatusRepository.audioPlaybackFlow.value as? AudioStatus.Playback)?.playbackStatus
            if (currentPlaybackStatus == PlaybackStatus.PLAYING ||
              currentPlaybackStatus == PlaybackStatus.PREPARING
            ) {
              val targetAyah = uiState?.playbackAyah
                ?: (audioStatusRepository.audioPlaybackFlow.value as? AudioStatus.Playback)?.currentAyah
                ?: uiState?.selectedStart
                ?: SuraAyah(
                  quranDisplayData.safelyGetSuraOnPage(currentPage),
                  quranInfo.getFirstAyahOnPage(currentPage).coerceAtLeast(1)
                )
              playFromAyah(targetAyah.sura, targetAyah.ayah)
            }
          }
        }
      }
    }
  }

  private fun showReadingBookmarkSheet(target: ReadingBookmarkTarget) {
    val readingBookmarkSheetWrapper = ReadingBookmarkSheetWrapper(
      context = this,
      target = target,
      onDismissed = {
        overlay.removeAllViews()
        overlay.visibility = View.GONE
        toggleActionBarVisibility(true)
        updateHybridBackCallbackState()
      }
    )
    qariListObserverJob?.cancel()
    qariListObserverJob = null
    overlay.removeAllViews()
    overlay.addView(
      readingBookmarkSheetWrapper,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    handler.removeMessages(MSG_HIDE_ACTIONBAR)
    toggleActionBarVisibility(false)
    updateHybridBackCallbackState()
  }

  private fun showPageThemeSheet() {
    if (::hybridReaderController.isInitialized) {
      hybridReaderController.openAppearanceSheet()
      updateHybridBackCallbackState()
      return
    }
    val pageThemeSheetWrapper = PageThemeSheetWrapper(this, quranSettings) {
      overlay.removeAllViews()
      overlay.visibility = View.GONE
      toggleActionBarVisibility(true)
      updateHybridBackCallbackState()
    }
    qariListObserverJob?.cancel()
    qariListObserverJob = null
    overlay.removeAllViews()
    overlay.addView(
      pageThemeSheetWrapper,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    handler.removeMessages(MSG_HIDE_ACTIONBAR)
    toggleActionBarVisibility(false)
    updateHybridBackCallbackState()
  }

  private fun showAyahBookmarkSheet(suraAyah: SuraAyah) {
    val ayahBookmarkWrapper = AyahBookmarkWrapper(this, suraAyah) { isBookmarked ->
      overlay.removeAllViews()
      overlay.visibility = View.GONE
      toggleActionBarVisibility(true)
      updateAyahBookmark(suraAyah, isBookmarked)
      updateHybridBackCallbackState()
    }
    qariListObserverJob?.cancel()
    qariListObserverJob = null
    overlay.removeAllViews()
    overlay.addView(
      ayahBookmarkWrapper,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    handler.removeMessages(MSG_HIDE_ACTIONBAR)
    toggleActionBarVisibility(false)
    updateHybridBackCallbackState()
  }

  fun updatePlayOptions(
    rangeRepeat: Int,
    verseRepeat: Int,
    enforceRange: Boolean,
    playbackSpeed: Float
  ): Boolean {
    val lastAudioRequest = audioStatusRepositoryBridge.audioRequest()
    if (lastAudioRequest != null) {
      val updatedAudioRequest = lastAudioRequest.copy(
        repeatInfo = verseRepeat,
        rangeRepeatInfo = rangeRepeat,
        enforceBounds = enforceRange,
        playbackSpeed = playbackSpeed
      )
      val i = Intent(this, AudioService::class.java)
      i.setAction(AudioService.ACTION_UPDATE_SETTINGS)
      i.putExtra(AudioService.EXTRA_PLAY_INFO, updatedAudioRequest)
      startService(i)
      return true
    } else {
      return false
    }
  }

  override fun setRepeatCount(repeatCount: Int) {
    val lastAudioRequest = audioStatusRepositoryBridge.audioRequest()
    if (lastAudioRequest != null) {
      val updatedAudioRequest = lastAudioRequest.copy(repeatInfo = repeatCount)

      val i = Intent(this, AudioService::class.java)
      i.setAction(AudioService.ACTION_UPDATE_SETTINGS)
      i.putExtra(AudioService.EXTRA_PLAY_INFO, updatedAudioRequest)
      startService(i)
    }
  }

  override fun onStopPressed() {
    startService(audioUtils.getAudioIntent(this, AudioService.ACTION_STOP))
  }

  override fun onCancelPressed(cancelDownload: Boolean) {
    if (cancelDownload) {
      needsPermissionToDownloadOver3g = true

      val i = Intent(this, QuranDownloadService::class.java)
      i.setAction(QuranDownloadService.ACTION_CANCEL_DOWNLOADS)
      startService(i)
    } else {
      startService(audioUtils.getAudioIntent(this, AudioService.ACTION_STOP))
    }
  }

  override fun onAcceptPressed() {
    needsPermissionToDownloadOver3g = false
    audioPresenter.onDownloadPermissionGranted()
  }

  //endregion

  private val selectionStart: SuraAyah?
    // region Ayah selection
    get() {
      val currentSelection = readingEventPresenter.currentAyahSelection()
      return currentSelection.startSuraAyah()
    }

  private val selectionEnd: SuraAyah?
    get() {
      val currentSelection = readingEventPresenter.currentAyahSelection()
      return currentSelection.endSuraAyah()
    }

  val lastAudioRequest: AudioRequest?
    get() = audioStatusRepositoryBridge.audioRequest()

  fun endAyahMode() {
    readingEventPresenterBridge.clearSelectedAyah()
    slidingPanel.collapsePane()
  }

  //endregion
  private fun updateLocalTranslations(start: SuraAyah?) {
    val ayahTracker = resolveCurrentTracker()
    if (ayahTracker != null) {
      lastActivatedLocalTranslations = ayahTracker.getLocalTranslations() ?: emptyArray()
      lastSelectedTranslationAyah = ayahTracker.getQuranAyahInfo(start!!.sura, start.ayah)
    }
  }

  private fun resolveCurrentTracker(): AyahTracker? {
    val position = viewPager.currentItem
    val f = pagerAdapter.getFragmentIfExists(position)
    return if (f is QuranPage && f.isVisible) {
      (f as QuranPage).getAyahTracker()
    } else {
      null
    }
  }

  private inner class AyahMenuItemSelectionHandler : MenuItem.OnMenuItemClickListener {
    override fun onMenuItemClick(item: MenuItem): Boolean {
      var sliderPage = -1
      val currentSelection = readingEventPresenter.currentAyahSelection()
      val startSuraAyah = currentSelection.startSuraAyah()
      val endSuraAyah = currentSelection.endSuraAyah()
      if (startSuraAyah == null || endSuraAyah == null) {
        return false
      }

      val itemId = item.itemId
      if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_bookmark_ayah) {
        showAyahBookmarkSheet(startSuraAyah)
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_translate_ayah) {
        sliderPage =
          slidingPagerAdapter.getPagePosition(SlidingPagerAdapter.TRANSLATION_PAGE)
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_play_from_here) {
        quranEventLogger.logAudioPlayback(
          QuranEventLogger.AudioPlaybackSource.AYAH,
          currentQariItem(), isDualPages, showingTranslation, isSplitScreen
        )
        playFromAyah(startSuraAyah.sura, startSuraAyah.ayah)
        toggleActionBarVisibility(true)
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_recite_from_here) {
        onEndSessionBackCallback.isEnabled = true
        pagerActivityRecitationPresenter.onRecitationPressed()
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_share_ayah_link) {
        shareAyahLink(startSuraAyah, endSuraAyah)
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_share_ayah_text) {
        shareAyah(startSuraAyah, endSuraAyah, false)
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_copy_ayah) {
        shareAyah(startSuraAyah, endSuraAyah, true)
      } else {
        return false
      }

      if (sliderPage < 0) {
        endAyahMode()
      } else {
        showSlider(sliderPage)
      }
      return true
    }
  }

  private fun shareAyah(start: SuraAyah, end: SuraAyah, isCopy: Boolean) {
    val translationNames = lastActivatedLocalTranslations
    val shareTranslation = showingTranslation && translationNames.isNotEmpty()
    val quranAyahInfo = if (shareTranslation) {
      // temporarily required so "lastSelectedTranslationAyah" isn't null
      // the real solution is to move this sharing logic out of PagerActivity
      // in the future and avoid this back and forth with the translation fragment.

      updateLocalTranslations(start)
      lastSelectedTranslationAyah
    } else {
      null
    }

    scope.launch {
      val hasArabicDatabase = withContext(Dispatchers.IO) {
        quranFileUtils.hasArabicSearchDatabase()
      }

      val canShowUi = canShowUi()
      if (canShowUi && !hasArabicDatabase) {
        showGetRequiredFilesDialog()
      } else if (canShowUi && shareTranslation) {
        if (quranAyahInfo != null) {
          val shareText =
            shareUtil.getShareText(this@PagerActivity, quranAyahInfo, translationNames)
          if (isCopy) {
            shareUtil.copyToClipboard(this@PagerActivity, shareText)
          } else {
            shareUtil.shareViaIntent(
              this@PagerActivity,
              shareText,
              com.quran.labs.androidquran.common.toolbar.R.string.share_ayah_text
            )
          }
        }
      } else if (canShowUi) {
        compositeDisposable.add(
          Single.defer { arabicDatabaseUtils().getVerses(start, end) }
            .subscribeOn(Schedulers.io())
            .filter { quranAyahs: List<QuranText?> -> quranAyahs.isNotEmpty() }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { quranAyahs: List<QuranText> ->
              if (!canShowUi()) {
                return@subscribe
              }
              if (isCopy) {
                shareUtil.copyVerses(this@PagerActivity, quranAyahs)
              } else {
                shareUtil.shareVerses(this@PagerActivity, quranAyahs)
              }
            })
      }
    }
  }

  fun shareAyahLink(start: SuraAyah, end: SuraAyah) {
    shareUtil.shareAyahLink(this, start, end)
  }

  private fun showSlider(sliderPage: Int) {
    ensureSlidingPagerAttached()
    readingEventPresenterBridge.clearMenuForSelection()
    slidingPager.currentItem = sliderPage
    slidingPanel.showPane()
    // TODO there's got to be a better way than this hack
    // The issue is that smoothScrollTo returns if mCanSlide is false
    // and it's false when the panel is GONE and showPane only calls
    // requestLayout, and only in onLayout does mCanSlide become true.
    // So by posting this later it gives time for onLayout to run.
    handler.post { slidingPanel.expandPane() }
  }

  private fun updateAyahBookmark(suraAyah: SuraAyah, bookmarked: Boolean) {
    // Refresh toolbar icon
    val start = selectionStart
    if (start != null && start == suraAyah) {
      ayahToolBar.setBookmarked(bookmarked)
    }
    if (::hybridReaderController.isInitialized) {
      hybridReaderController.setSelectedAyahBookmarked(suraAyah, bookmarked)
    }
  }

  private fun promptForMultipleChoicePlay(
    page: Int, startSura: Int, startAyah: Int, startingSuraList: List<Int>
  ) {
    val adapter = ArrayAdapter<String>(this, android.R.layout.select_dialog_item)
    startingSuraList
      .map { quranDisplayData.getSuraName(this, it, false) }
      .onEach { adapter.add(it) }

    val suraList = if (startSura != startingSuraList[0]) {
      adapter.insert(getString(R.string.starting_page_label), 0)
      listOf(startSura) + startingSuraList
    } else {
      startingSuraList
    }

    val builder = AlertDialog.Builder(this)
      .setTitle(getString(R.string.playback_prompt_title))
      .setAdapter(adapter) { dialog: DialogInterface, i: Int ->
        if (i == 0) {
          playFromAyah(startSura, startAyah)
        } else {
          playFromAyah(suraList[i], 1)
        }
        dialog.dismiss()
        promptDialog = null
      }
    promptDialog = builder.create()
    promptDialog!!.show()
  }

  private inner class SlidingPanelListener : SlidingUpPanelLayout.PanelSlideListener {
    override fun onPanelSlide(panel: View, slideOffset: Float) {
    }

    override fun onPanelCollapsed(panel: View) {
      if (selectionStart != null) {
        endAyahMode()
      }
      slidingPanel.hidePane()
      readingEventPresenter.onPanelClosed()
    }

    override fun onPanelExpanded(panel: View) {
      readingEventPresenter.onPanelOpened()
    }

    override fun onPanelAnchored(panel: View) {
    }
  }

  private fun initHybridReaderController() {
    val tafsirHelper = TafsirLookupHelper(
      translationsDBAdapter = translationsDBAdapter,
      translationModel = translationModel,
      translationUtil = translationUtil,
      arabicTextLoader = { suraAyah -> loadSingleAyahArabicText(suraAyah) },
      ioDispatcher = Dispatchers.IO
    )
    hybridReaderController = HybridReaderController(
      appContext = applicationContext,
      quranInfo = quranInfo,
      quranDisplayData = quranDisplayData,
      quranSettings = quranSettings,
      dailyWirdRepository = dailyWirdRepository,
      audioStatusRepository = audioStatusRepository,
      currentQariManager = qariManager,
      downloadInfoStreams = downloadInfoStreams,
      timingRepository = timingRepository,
      bookmarksDao = bookmarksDao,
      tafsirLookupHelper = tafsirHelper,
      scope = scope,
      ioDispatcher = Dispatchers.IO,
      defaultPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    )
    hybridReaderController.startObserving()

    scope.launch {
      var prevSheetOpen = false
      hybridReaderController.uiState.collectLatest { state ->
        val sheetOpen = state.isAnySheetOpen
        if (sheetOpen != prevSheetOpen) {
          prevSheetOpen = sheetOpen
          if (sheetOpen) {
            handler.removeMessages(MSG_HIDE_ACTIONBAR)
            if (::viewPager.isInitialized &&
              ::toolBarArea.isInitialized &&
              ::audioStatusBar.isInitialized &&
              ::ayahToolBar.isInitialized &&
              ::windowInsetsController.isInitialized
            ) {
              toggleActionBarVisibility(true)
            }
          }
          updateHybridBackCallbackState()
        }
      }
    }
  }

  private fun bindHybridReaderOverlay() {
    hybridReaderOverlay.bind(
      readerController = hybridReaderController,
      scope = lifecycleScope,
      onBackToHub = {
        onSessionEnd()
        finish()
      },
      onJumpPage = {
        JumpFragment().show(supportFragmentManager, JumpFragment.TAG)
      },
      onTogglePageBookmark = {
        showReadingBookmarkSheet(ReadingBookmarkTarget.Page(currentPage))
      },
      onAyahListen = { ayah ->
        quranEventLogger.logAudioPlayback(
          QuranEventLogger.AudioPlaybackSource.AYAH,
          currentQariItem(),
          isDualPages,
          showingTranslation,
          isSplitScreen
        )
        playFromAyah(ayah.sura, ayah.ayah)
        endAyahMode()
      },
      onAyahTafsir = { _ ->
        // Keep the ayah selected & highlighted while inspecting its Tafsir in Study Drawer
        hybridReaderController.openStudyDrawer(HybridReaderContract.StudyTab.TAFSIR)
        updateHybridBackCallbackState()
      },
      onAyahBookmark = { ayah ->
        showAyahBookmarkSheet(ayah)
      },
      onAyahCopy = { start, end ->
        shareAyah(start, end, isCopy = true)
      },
      onAyahShare = { start, end ->
        shareAyah(start, end, isCopy = false)
      },
      onPlayPause = {
        val state = hybridReaderController.uiState.value
        when (state.playbackStatus) {
          PlaybackStatus.PLAYING -> onPausePressed()
          PlaybackStatus.PAUSED -> {
            val activeRequest = audioStatusRepositoryBridge.audioRequest()
            val selectedQari = currentQariItem()
            if (activeRequest != null && activeRequest.qari.id != selectedQari.id) {
              val resumeAyah = state.selectedStart
                ?: state.playbackAyah
                ?: SuraAyah(state.suraNumber, state.firstAyahOnPage.coerceAtLeast(1))
              quranEventLogger.logAudioPlayback(
                QuranEventLogger.AudioPlaybackSource.AYAH,
                selectedQari,
                isDualPages,
                showingTranslation,
                isSplitScreen
              )
              playFromAyah(resumeAyah.sura, resumeAyah.ayah)
            } else {
              onContinuePlaybackPressed()
            }
          }
          PlaybackStatus.PREPARING -> onCancelPressed(false)
          null -> {
            val sel = state.selectedStart
            if (sel != null) {
              quranEventLogger.logAudioPlayback(
                QuranEventLogger.AudioPlaybackSource.AYAH,
                currentQariItem(),
                isDualPages,
                showingTranslation,
                isSplitScreen
              )
              playFromAyah(sel.sura, sel.ayah)
            } else {
              onPlayPressed()
            }
          }
        }
      },
      onSkipNext = { onNextPressed() },
      onSkipPrev = { onPreviousPressed() },
      onSpeedChanged = { speed -> setPlaybackSpeed(speed) },
      onRepeatChanged = { repeat -> setRepeatCount(repeat) },
      onShowQariList = { onShowQariList() },
      onCancelDownload = { onCancelPressed(true) },
      onAllowMobileDownload = { onAcceptPressed() },
      onOpenTafsirManager = { startTranslationManager() },
      onTafsirStepAyah = { next ->
        val current = hybridReaderController.resolveTargetStudyAyah()
        val stepped = hybridReaderController.adjacentAyah(current, next)
        if (stepped != null) {
          ensurePage(stepped.sura, stepped.ayah)
          readingEventPresenterBridge.setSelection(stepped.sura, stepped.ayah, true)
        }
      },
      onAppThemeStyleSelected = { style ->
        appliedThemeStyle = style
        theme.applyStyle(ThemeUtil.themeOverlayRes(style), true)
        refreshQuranPages()
        applyHybridMushafBackdrop()
        updateSystemBarAppearance()
      },
      onAppThemeModeSelected = { _ ->
        refreshQuranPages()
        applyHybridMushafBackdrop()
        updateSystemBarAppearance()
      },
      onPageAppearanceUpdated = {
        refreshQuranPages()
        applyHybridMushafBackdrop()
        updateSystemBarAppearance()
      },
      onMushafNudgeChanged = { nudgePx ->
        if (::viewPager.isInitialized) {
          if (abs(viewPager.translationY - nudgePx) > 0.5f) {
            viewPager.animate().setListener(null)
            viewPager.animate().cancel()
            // Apply static clip on parent container ONCE before animating translationY on GPU
            // (eliminates per-frame clipBounds mutation jank).
            if (nudgePx < -0.5f) {
              applyMushafStatusBarClip(targetNudgePx = nudgePx)
            }
            if (viewPager.isAttachedToWindow && AdaptiveMotionTokens.areAnimationsEnabled(this)) {
              viewPager.animate()
                .translationY(nudgePx)
                .setDuration(AdaptiveMotionTokens.TAB_SWITCH_DURATION_MS.toLong())
                .setListener(object : AnimatorListenerAdapter() {
                  override fun onAnimationCancel(animation: Animator) {
                    viewPager.animate().setListener(null)
                  }

                  override fun onAnimationEnd(animation: Animator) {
                    viewPager.animate().setListener(null)
                    applyMushafStatusBarClip(targetNudgePx = nudgePx)
                    if (!isFinishing && !isDestroyed && !hybridReaderController.uiState.value.isAnySheetOpen) {
                      refreshHybridAyahBounds()
                    }
                  }
                })
                .start()
            } else {
              viewPager.translationY = nudgePx
              applyMushafStatusBarClip(targetNudgePx = nudgePx)
              if (!isFinishing && !isDestroyed && !hybridReaderController.uiState.value.isAnySheetOpen) {
                refreshHybridAyahBounds()
              }
            }
          } else {
            applyMushafStatusBarClip(targetNudgePx = nudgePx)
          }
        }
      },
      onStop = { onStopPressed() }
    )
  }

  private fun applyMushafStatusBarClip(targetNudgePx: Float = 0f) {
    val container = mushafClipContainer
    if (container != null) {
      val statusTop = statusBarTopInsetPx
      if (targetNudgePx < -0.5f && statusTop > 0 && container.width > 0 && container.height > 0) {
        val rect = mushafClipRect ?: Rect().also { mushafClipRect = it }
        rect.set(0, statusTop, container.width, container.height)
        if (container.clipBounds != rect) {
          container.clipBounds = rect
        }
      } else if (container.clipBounds != null) {
        container.clipBounds = null
      }
      return
    }
    if (!::viewPager.isInitialized) return
    val statusTop = statusBarTopInsetPx
    if (targetNudgePx < -0.5f && statusTop > 0 && viewPager.width > 0 && viewPager.height > 0) {
      val rect = mushafClipRect ?: Rect().also { mushafClipRect = it }
      rect.set(0, statusTop, viewPager.width, viewPager.height)
      viewPager.clipBounds = rect
    } else if (viewPager.clipBounds != null) {
      viewPager.clipBounds = null
    }
  }

  private fun applyHybridMushafBackdrop() {
    if (!::slidingPanel.isInitialized) return
    val isNightUi = ThemeUtil.isDarkAppearance(
      quranSettings.currentTheme(),
      (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    )
    val bgColor = if (quranSettings.isNightMode) {
      appliedThemeStyle.darkMushafBackgroundArgb(quranSettings.nightModeBackgroundBrightness)
    } else {
      appliedThemeStyle.palette(isNightUi).mushafOverlayBg.toArgb()
    }
    slidingPanel.setBackgroundColor(bgColor)
    if (::viewPager.isInitialized) {
      viewPager.setBackgroundColor(bgColor)
    }
  }

  private fun schedulePageSettle(page: Int) {
    pageSettleDebounceJob?.cancel()
    if (lastViewPagerScrollState == ViewPager.SCROLL_STATE_IDLE) {
      hybridReaderController.recordSettledPage(page)
    } else {
      pageSettleDebounceJob = scope.launch {
        delay(600L)
        if (!isFinishing && !isDestroyed) {
          hybridReaderController.recordSettledPage(currentPage)
        }
      }
    }
  }

  fun onPageAyahCoordinatesReady(page: Int) {
    if (!::hybridReaderController.isInitialized) return
    val selection = readingEventPresenter.currentAyahSelection()
    val start = selection.startSuraAyah() ?: return
    val selectedPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah)
    if (selectedPage == page || page == currentPage) {
      refreshHybridAyahBounds()
    }
  }

  private fun refreshHybridAyahBounds() {
    if (!::hybridReaderController.isInitialized || !::hybridReaderOverlay.isInitialized) return
    val selection = readingEventPresenter.currentAyahSelection()
    val start = selection.startSuraAyah()
    val end = selection.endSuraAyah()
    if (start == null) {
      hybridReaderController.updateAyahOverlayBounds(null)
      return
    }
    val bounds = computeAyahOverlayBounds(start, end, selection.selectionIndicator())
    hybridReaderController.updateAyahOverlayBounds(bounds)
  }

  private fun computeAyahOverlayBounds(
    start: SuraAyah,
    end: SuraAyah?,
    indicator: SelectionIndicator
  ): AyahOverlayBounds? {
    if (!::viewPager.isInitialized || !::hybridReaderOverlay.isInitialized) return null
    val ayahPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah)
    val ayahPos = quranInfo.getPositionFromPage(ayahPage, isDualPageVisible)
    val currentPos = viewPager.currentItem
    if (lastViewPagerScrollState == ViewPager.SCROLL_STATE_IDLE && ayahPos != currentPos) {
      return null
    }
    val fragment = pagerAdapter.getFragmentIfExists(ayahPos)
    if (fragment is QuranPageFragment) {
      val startRects = fragment.getAyahOverlayRects(start.sura, start.ayah, hybridReaderOverlay)
      if (!startRects.isNullOrEmpty()) {
        val first = startRects.first()
        val lastRects = if (end != null && end != start) {
          val endPage = quranInfo.getPageFromSuraAyah(end.sura, end.ayah)
          val endPos = quranInfo.getPositionFromPage(endPage, isDualPageVisible)
          val endFragment = if (endPos == ayahPos) fragment else pagerAdapter.getFragmentIfExists(endPos) as? QuranPageFragment
          endFragment?.getAyahOverlayRects(end.sura, end.ayah, hybridReaderOverlay)
        } else {
          null
        }
        val last = (lastRects?.takeIf { it.isNotEmpty() } ?: startRects).last()
        return AyahOverlayBounds(
          firstLineLeft = first.left,
          firstLineTop = first.top,
          firstLineRight = first.right,
          firstLineBottom = first.bottom,
          lastLineLeft = last.left,
          lastLineTop = last.top,
          lastLineRight = last.right,
          lastLineBottom = last.bottom,
          unNudgedLastLineBottom = last.bottom - viewPager.translationY
        )
      }
    }

    if (indicator is SelectionIndicator.SelectedItemPosition && ayahPos == currentPos) {
      val overlayLoc = IntArray(2)
      hybridReaderOverlay.getLocationInWindow(overlayLoc)
      val tracker = resolveCurrentTracker() as? com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter
      val imageView = tracker?.getQuranPageImageView(ayahPage)
      val ivLoc = imageView?.let { iv ->
        IntArray(2).also { iv.getLocationInWindow(it) }
      }
      return computeSelectedItemOverlayBounds(
        indicator = indicator,
        overlayLocationInWindow = overlayLoc,
        imageViewLocationInWindow = ivLoc,
        imageViewWidth = imageView?.width ?: 0,
        viewPagerTranslationY = viewPager.translationY
      )
    }

    return null
  }

  private suspend fun loadSingleAyahArabicText(suraAyah: SuraAyah): String? {
    return withContext(Dispatchers.IO) {
      if (!quranFileUtils.hasArabicSearchDatabase()) {
        return@withContext null
      }
      runCatching {
        arabicDatabaseUtils().getVerses(suraAyah, suraAyah)
          .blockingGet()
          .firstOrNull()
          ?.text
      }.getOrNull()
    }
  }

  private fun updateHybridBackCallbackState() {
    if (!::onHybridOverlayOrDrawerBackCallback.isInitialized) return
    val isOverlayOpen = ::overlay.isInitialized && overlay.isVisible
    val isSheetOpen = ::hybridReaderController.isInitialized && hybridReaderController.uiState.value.isAnySheetOpen
    val isTafsirPickerOpen = ::hybridReaderOverlay.isInitialized && hybridReaderOverlay.isTafsirPickerOpen()
    onHybridOverlayOrDrawerBackCallback.isEnabled = isOverlayOpen || isSheetOpen || isTafsirPickerOpen
  }

  private fun shouldAutoHideChrome(): Boolean {
    val hasSelection = selectionStart != null
    val isSheetOpen = ::hybridReaderController.isInitialized && hybridReaderController.uiState.value.isAnySheetOpen
    val isModalOpen = ::overlay.isInitialized && overlay.isVisible
    return !hasSelection && !isSheetOpen && !isModalOpen
  }

  private fun isHybridBottomCapsuleVisible(): Boolean {
    if (!::hybridReaderController.isInitialized) return false
    val ui = hybridReaderController.uiState.value
    return !ui.isAnySheetOpen && (ui.isChromeVisible || ui.isAudioActive || ui.downloadProgress != null || ui.showNetworkPrompt)
  }

  companion object {
    private val PAGE_COLOR_PREFERENCES = setOf(
      Constants.PREF_PAGE_THEME,
      Constants.PREF_PAGE_APPEARANCE,
      Constants.PREF_APP_THEME,
      Constants.PREF_APP_THEME_STYLE,
      Constants.PREF_NIGHT_MODE_TEXT_BRIGHTNESS,
      Constants.PREF_NIGHT_MODE_BACKGROUND_BRIGHTNESS,
      Constants.PREF_USE_OLD_BACKGROUND
    )
    private const val AUDIO_DOWNLOAD_KEY = "AUDIO_DOWNLOAD_KEY"
    private const val SURA_HEADER_DRAWER_CLASS =
      "com.quran.labs.androidquran.common.drawing.helper.SuraHeaderDrawer"
    private const val LAST_READ_PAGE = "LAST_READ_PAGE"
    private const val LAST_READING_MODE_IS_TRANSLATION = "LAST_READING_MODE_IS_TRANSLATION"
    private const val LAST_ACTIONBAR_STATE = "LAST_ACTIONBAR_STATE"
    private const val LAST_FOLDING_STATE = "LAST_FOLDING_STATE"
    private const val LAST_SLIDER_INITIALIZED = "LAST_SLIDER_INITIALIZED"
    private const val KEY_HYBRID_DRAWER_OPEN = "hybrid_drawer_open"
    private const val KEY_HYBRID_APPEARANCE_OPEN = "hybrid_appearance_open"
    private const val KEY_HYBRID_STUDY_TAB = "hybrid_study_tab"
    private const val KEY_HYBRID_SELECTED_SURA = "hybrid_selected_sura"
    private const val KEY_HYBRID_SELECTED_AYAH = "hybrid_selected_ayah"
    private const val KEY_HYBRID_SELECTED_END_SURA = "hybrid_selected_end_sura"
    private const val KEY_HYBRID_SELECTED_END_AYAH = "hybrid_selected_end_ayah"

    const val EXTRA_JUMP_TO_TRANSLATION: String = "jumpToTranslation"
    const val EXTRA_HIGHLIGHT_SURA: String = "highlightSura"
    const val EXTRA_HIGHLIGHT_AYAH: String = "highlightAyah"
    const val LAST_WAS_DUAL_PAGES: String = "wasDualPages"

    private const val DEFAULT_HIDE_AFTER_TIME: Long = 2000

    const val MSG_HIDE_ACTIONBAR: Int = 1

    // AYAH ACTION PANEL STUFF
    // Max height of sliding panel (% of screen)
    private const val PANEL_MAX_HEIGHT = 0.6f

    internal fun shouldUseLightSystemBars(
      isActionBarHidden: Boolean,
      isNightMode: Boolean,
      lightNavigationBar: Boolean
    ): Boolean {
      return if (isActionBarHidden) !isNightMode else lightNavigationBar
    }

    internal fun shouldUseLightStatusBar(
      isActionBarHidden: Boolean,
      isNightMode: Boolean,
      isLightToolbar: Boolean
    ): Boolean {
      return if (isActionBarHidden) !isNightMode else isLightToolbar
    }

    internal fun readingBookmarkToastBottomMargin(
      isActionBarHidden: Boolean,
      isAudioBarVisible: Boolean,
      audioBarHeight: Int,
      bottomInset: Int
    ): Int {
      return if (!isActionBarHidden && isAudioBarVisible) {
        audioBarHeight
      } else {
        bottomInset
      }
    }
  }
}
