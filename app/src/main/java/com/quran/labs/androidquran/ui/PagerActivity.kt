package com.quran.labs.androidquran.ui

import android.Manifest
import android.app.SearchManager
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
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
import com.quran.labs.androidquran.ui.fragment.TabletFragment
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog.OnBookmarkTagsUpdateListener
import com.quran.labs.androidquran.ui.fragment.TranslationFragment
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.helpers.AyahTracker
import com.quran.labs.androidquran.ui.helpers.JumpDestination
import com.quran.labs.androidquran.ui.helpers.MushafPageFlipTransformer
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
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.ShareUtil
import com.quran.labs.androidquran.kidsclassroom.ClassroomStage
import com.quran.labs.androidquran.kidsclassroom.KidsAyahCelebrationOverlayView
import com.quran.labs.androidquran.kidsclassroom.KidsSurahAnalysisOverlayView
import com.quran.labs.androidquran.kidsclassroom.KidsClassroomManager
import com.quran.labs.androidquran.kidsclassroom.KidsClassroomOverlayView
import com.quran.labs.androidquran.kidsclassroom.KidsClassroomUiState
import com.quran.labs.androidquran.kidsclassroom.MinshawiJuz30Repository
import com.quran.labs.androidquran.memorization.KidsClassroomOptionsOverlayView
import com.quran.labs.androidquran.memorization.MemorizationBottomBarView
import com.quran.labs.androidquran.memorization.MemorizationControlsSheetOverlayView
import com.quran.labs.androidquran.memorization.MemorizationFormatting
import com.quran.labs.androidquran.memorization.MemorizationRepetitionOverlayView
import com.quran.labs.androidquran.memorization.MemorizationTapOutcome
import com.quran.labs.androidquran.memorization.QuranMemorizationManager
import com.quran.labs.androidquran.memorization.RevealMode
import com.quran.labs.androidquran.memorization.WordWindowSetupOverlayView
import com.quran.labs.androidquran.memorization.tasmee.LiveTasmeeManager
import com.quran.labs.androidquran.memorization.tasmee.TasmeeBottomRecordingBarView
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
import com.quran.page.common.data.QuranNaming
import com.quran.page.common.factory.PageViewFactoryProvider
import com.quran.page.common.toolbar.AyahToolBar
import com.quran.page.common.toolbar.di.AyahToolBarInjector
import com.quran.reading.common.ReadingEventPresenter
import dev.zacsweers.metro.Inject
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
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
  private var isActionBarHidden = false
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

  private lateinit var bottomBarContainer: View
  private lateinit var memorizationBar: MemorizationBottomBarView
  private lateinit var tasmeeBottomRecordingBar: TasmeeBottomRecordingBarView
  private lateinit var kidsClassroomOverlay: KidsClassroomOverlayView
  private var subtleOptionsRevealBtn: View? = null
  private lateinit var audioStatusBar: AudioBarWrapper
  private lateinit var viewPager: ViewPager
  private var isProgrammaticRecitationPageTurn = false
  private var lastObservedReaderPage = -1
  private var activeCelebrationEventId: Long = -1L
  private val themePreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    if (key in PAGE_COLOR_PREFERENCES) {
      syncReaderSurfaceBackground()
      refreshQuranPages()
    }
  }
  private lateinit var pagerAdapter: QuranPageAdapter
  private lateinit var ayahToolBar: AyahToolBar
  private lateinit var slidingPanel: SlidingUpPanelLayout
  private lateinit var slidingPager: ViewPager
  private lateinit var slidingPagerAdapter: SlidingPagerAdapter
  private lateinit var translationsSpinner: QuranSpinner
  private lateinit var overlay: FrameLayout
  private lateinit var toolBarArea: View

  private var requestPermissionLauncher: ActivityResultLauncher<String>? = null
  private var requestMicPermissionLauncher: ActivityResultLauncher<String>? = null

  private var isSplitScreen = false

  private lateinit var onClearAyahModeBackCallback: OnBackPressedCallback
  private lateinit var onShowingTranslationBackCallback: OnBackPressedCallback
  private lateinit var onEndSessionBackCallback: OnBackPressedCallback
  private lateinit var onKidsClassroomBackCallback: OnBackPressedCallback
  private lateinit var onLiveTasmeeBackCallback: OnBackPressedCallback
  private var pendingMicRequester: Int = MIC_REQUESTER_NONE
  private var isKidsSessionMode: Boolean = false
  private var kidsSessionInitialStars: Int = 0
  private var kidsSessionInitialSurahMastered: Int = 0
  private var kidsSessionSurah: Int = 1

  private var lastSelectedTranslationAyah: QuranAyahInfo? = null
  private var lastActivatedLocalTranslations: Array<LocalTranslation> = emptyArray()
  private var flashcardRatingDialog: AlertDialog? = null

  @Inject lateinit var quranNavigator: QuranNavigator

  @Inject lateinit var bookmarksDao: BookmarksDao
  @Inject lateinit var recentPagePresenter: RecentPagePresenter
  @Inject lateinit var readingBookmarkPresenter: ReadingBookmarkPresenter
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var quranNaming: QuranNaming
  @Inject lateinit var quranScreenInfo: QuranScreenInfo
  @Inject lateinit var arabicDatabaseUtils: ArabicDatabaseUtils
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

  private lateinit var audioStatusRepositoryBridge: AudioStatusRepositoryBridge
  private lateinit var readingEventPresenterBridge: ReadingEventPresenterBridge
  private lateinit var windowInsetsController: WindowInsetsControllerCompat

  private var translationJob: Job? = null
  private lateinit var compositeDisposable: CompositeDisposable
  private val foregroundDisposable = CompositeDisposable()
  private val scope = MainScope()

  private val kidsNavigateCallback: (SuraAyah) -> Unit = { targetAyah ->
    isProgrammaticRecitationPageTurn = true
    try {
      ensurePage(targetAyah.sura, targetAyah.ayah)
      refreshQuranPages()
    } finally {
      isProgrammaticRecitationPageTurn = false
    }
  }
  private val kidsMicPermissionCallback: () -> Unit = {
    pendingMicRequester = MIC_REQUESTER_KIDS_CLASSROOM
    requestMicPermissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
  }
  private val tasmeeMicPermissionCallback: () -> Unit = {
    pendingMicRequester = MIC_REQUESTER_LIVE_TASMEE
    requestMicPermissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
  }
  private val tasmeeAdvancePageCallback: (Int) -> Unit = { completedPage ->
    if (canShowUi()) {
      isProgrammaticRecitationPageTurn = true
      try {
        handleMemorizationTapOnPage(completedPage)
      } finally {
        isProgrammaticRecitationPageTurn = false
      }
    }
  }
  private val tasmeeStopPlaybackCallback: () -> Unit = {
    onStopPressed()
  }
  private val tasmeeWordsRevealedCallback: () -> Unit = {
    if (!isFinishing) {
      refreshQuranPages()
    }
  }
  private val tasmeeJumpToPageCallback: (Int) -> Unit = { targetPage ->
    if (canShowUi()) {
      isProgrammaticRecitationPageTurn = true
      try {
        val clampedPage = targetPage.coerceIn(1, 604)
        val pos = quranInfo.getPositionFromPage(clampedPage, isDualPageVisible)
        viewPager.setCurrentItem(pos, true)
        refreshQuranPages()
      } finally {
        isProgrammaticRecitationPageTurn = false
      }
    }
  }

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
          activity.toggleActionBarVisibility(false)
        } else {
          super.handleMessage(msg)
        }
      }
    }
  }

  public override fun onCreate(savedInstanceState: Bundle?) {
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

    val lightStatusBar = resources.getBoolean(R.bool.light_navigation_bar)
    windowInsetsController = WindowInsetsControllerCompat(
      window, findViewById<ViewGroup>(R.id.sliding_panel)
    ).apply {
      isAppearanceLightStatusBars = lightStatusBar
      isAppearanceLightNavigationBars = lightStatusBar
    }
    registerBackPressedCallbacks()
    initialize(savedInstanceState)
    requestPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean? ->
        audioPresenter.onPostNotificationsPermissionResponse(
          isGranted!!
        )
      }
    requestMicPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean? ->
        val granted = isGranted == true
        val requester = pendingMicRequester
        pendingMicRequester = MIC_REQUESTER_NONE
        if (requester == MIC_REQUESTER_LIVE_TASMEE) {
          LiveTasmeeManager.onRecordAudioPermissionResult(this, granted, currentPage, quranInfo)
          onLiveTasmeeBackCallback.isEnabled = LiveTasmeeManager.isEnabled()
          refreshQuranPages()
          supportInvalidateOptionsMenu()
          if (!granted) {
            makeText(this, R.string.live_tasmee_mic_permission_needed, Toast.LENGTH_SHORT).show()
          }
        } else {
          KidsClassroomManager.onRecordAudioPermissionResult(this, granted)
          if (!granted) {
            makeText(this, R.string.kids_classroom_mic_permission_needed, Toast.LENGTH_SHORT).show()
          }
        }
      }
    KidsClassroomManager.onNavigateToAyahPage = kidsNavigateCallback
    KidsClassroomManager.onRequestMicPermission = kidsMicPermissionCallback
    LiveTasmeeManager.onRequestMicPermission = tasmeeMicPermissionCallback
    LiveTasmeeManager.onAdvancePageOrLoopRange = tasmeeAdvancePageCallback
    LiveTasmeeManager.onStopPlaybackAudio = tasmeeStopPlaybackCallback
    LiveTasmeeManager.onWordsRevealed = tasmeeWordsRevealedCallback
    LiveTasmeeManager.onJumpToPage = tasmeeJumpToPageCallback

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          QuranMemorizationManager.uiState.collect {
            syncMemorizationAndTasmeeBarsVisibility()
          }
        }
        launch {
          LiveTasmeeManager.uiState.collect {
            syncMemorizationAndTasmeeBarsVisibility()
          }
        }
        launch {
          KidsClassroomManager.uiState.collect { kidsState ->
            syncMemorizationAndTasmeeBarsVisibility()
            syncKidsAyahCelebrationOverlay(kidsState)
          }
        }
      }
    }

    val kidsSessionRequested = intent?.getBooleanExtra(EXTRA_KIDS_SESSION_MODE, false) == true ||
      savedInstanceState?.getBoolean(LAST_KIDS_SESSION_MODE, false) == true
    val kidsPracticeSurah = intent?.getIntExtra(EXTRA_START_KIDS_MODE_SURAH, -1) ?: -1
    val kidsPracticeAyah = intent?.getIntExtra(EXTRA_START_KIDS_MODE_AYAH, 1)?.coerceAtLeast(1) ?: 1
    val kidsAutoModeRaw = intent?.getStringExtra(EXTRA_START_KIDS_AUTO_MODE)
    if (kidsSessionRequested || kidsPracticeSurah in 1..114) {
      isKidsSessionMode = true
      intent?.removeExtra(EXTRA_KIDS_SESSION_MODE)
    }
    if (kidsPracticeSurah in 1..114) {
      intent?.removeExtra(EXTRA_START_KIDS_MODE_SURAH)
      intent?.removeExtra(EXTRA_START_KIDS_MODE_AYAH)
      intent?.removeExtra(EXTRA_START_KIDS_AUTO_MODE)
      val launchMode = com.quran.labs.androidquran.kidsclassroom.KidsLessonLaunchMode.entries
        .firstOrNull { it.name == kidsAutoModeRaw }
        ?: com.quran.labs.androidquran.kidsclassroom.KidsLessonLaunchMode.OPEN_READY
      startKidsModeForSurah(kidsPracticeSurah, kidsPracticeAyah, launchMode)
    } else if (intent?.getBooleanExtra(EXTRA_START_CHILDREN_LEARNING_MODE, false) == true) {
      intent.removeExtra(EXTRA_START_CHILDREN_LEARNING_MODE)
      activateOrToggleChildrenLearningMode(forceEnable = true)
    } else if (intent?.getBooleanExtra(EXTRA_START_LIVE_TASMEE_MODE, false) == true) {
      intent.removeExtra(EXTRA_START_LIVE_TASMEE_MODE)
      activateOrToggleLiveTasmeeMode(forceEnable = true)
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
    isActionBarHidden = false
    if (savedInstanceState != null) {
      Timber.d("non-null saved instance state!")
      page = savedInstanceState.getInt(LAST_READ_PAGE, -1)
      showingTranslation = savedInstanceState
        .getBoolean(LAST_READING_MODE_IS_TRANSLATION, false)
      val lastWasDualPages = savedInstanceState.getBoolean(LAST_WAS_DUAL_PAGES, isDualPages)
      shouldAdjustPageNumber = (lastWasDualPages != isDualPages)
    } else {
      val intent = intent
      val extras = intent.extras
      if (extras != null) {
        page = extras.getInt("page", Constants.PAGES_FIRST)
        showingTranslation =
          extras.getBoolean(EXTRA_JUMP_TO_TRANSLATION, showingTranslation)
        val highlightedSura = extras.getInt(EXTRA_HIGHLIGHT_SURA, -1)
        val highlightedAyah = extras.getInt(EXTRA_HIGHLIGHT_AYAH, -1)

        if (highlightedSura > -1 && highlightedAyah > -1) {
          readingEventPresenterBridge.setSelection(
            highlightedSura,
            highlightedAyah,
            true
          )
        }
      }
    }
    onShowingTranslationBackCallback.isEnabled = showingTranslation

    compositeDisposable = CompositeDisposable()

    bottomBarContainer = findViewById(R.id.bottom_bar_container)
    memorizationBar = findViewById(R.id.memorization_bar)
    tasmeeBottomRecordingBar = findViewById(R.id.tasmee_bottom_recording_bar)
    kidsClassroomOverlay = findViewById(R.id.kids_classroom_overlay)
    subtleOptionsRevealBtn = findViewById(R.id.subtle_options_reveal_btn)
    audioStatusBar = findViewById(R.id.audio_area)

    syncReaderSurfaceBackground()
    QuranMemorizationManager.ensurePreferencesLoaded(this)

    subtleOptionsRevealBtn?.setOnClickListener {
      showMemorizationControlsSheet()
    }
    tasmeeBottomRecordingBar.onMicTapped = {
      LiveTasmeeManager.onMicButtonTapped(this@PagerActivity, currentPage, quranInfo)
      syncMemorizationAndTasmeeBarsVisibility()
      refreshQuranPages()
      supportInvalidateOptionsMenu()
    }
    tasmeeBottomRecordingBar.onStopRecording = {
      stopActiveRecitationRecording()
    }
    tasmeeBottomRecordingBar.onRetryDownload = {
      LiveTasmeeManager.retryDownloadIfNeeded(this@PagerActivity)
    }
    tasmeeBottomRecordingBar.onSelectRevealMode = { mode ->
      QuranMemorizationManager.setRevealMode(mode, this@PagerActivity)
      refreshQuranPages()
      supportInvalidateOptionsMenu()
    }
    tasmeeBottomRecordingBar.onOpenOptions = {
      showMemorizationControlsSheet()
    }
    tasmeeBottomRecordingBar.onPreviousAyah = {
      LiveTasmeeManager.goToPreviousAyah(this@PagerActivity)
    }
    tasmeeBottomRecordingBar.onNextAyah = {
      LiveTasmeeManager.skipToNextAyah(this@PagerActivity)
    }
    kidsClassroomOverlay.onRequestOptionsSheet = {
      showKidsClassroomOptionsSheet()
    }
    kidsClassroomOverlay.onPreviousAyah = {
      KidsClassroomManager.goToPreviousAyah(this@PagerActivity, autoPlay = false)
      refreshQuranPages()
    }
    kidsClassroomOverlay.onNextAyah = {
      KidsClassroomManager.advanceToNextAyah(this@PagerActivity, autoPlay = false)
      refreshQuranPages()
    }

    memorizationBar.onToggleHideText = {
      QuranMemorizationManager.toggleHiddenMode(currentPage, quranInfo, this@PagerActivity)
      refreshQuranPages()
      supportInvalidateOptionsMenu()
    }
    memorizationBar.onSelectFullAyahMode = {
      QuranMemorizationManager.onProminentModeButtonPressed(
        RevealMode.FULL_AYAH,
        currentPage,
        quranInfo,
        this@PagerActivity
      )
      refreshQuranPages()
      supportInvalidateOptionsMenu()
    }
    memorizationBar.onSelectWordByWordMode = {
      QuranMemorizationManager.onProminentModeButtonPressed(
        RevealMode.WORD_BY_WORD,
        currentPage,
        quranInfo,
        this@PagerActivity
      )
      refreshQuranPages()
      supportInvalidateOptionsMenu()
    }
    memorizationBar.onSelectWordWindowMode = {
      QuranMemorizationManager.onProminentModeButtonPressed(
        RevealMode.WORD_WINDOW,
        currentPage,
        quranInfo,
        this@PagerActivity
      )
      refreshQuranPages()
      supportInvalidateOptionsMenu()
    }
    memorizationBar.onSelectSingleWordMode = {
      QuranMemorizationManager.onProminentModeButtonPressed(
        RevealMode.SINGLE_WORD,
        currentPage,
        quranInfo,
        this@PagerActivity
      )
      refreshQuranPages()
      supportInvalidateOptionsMenu()
    }
    memorizationBar.onSelectSingleWordTajweedMode = {
      val wasTajweed = quranSettings.pageType == "tajweed"
      QuranMemorizationManager.onProminentModeButtonPressed(
        RevealMode.SINGLE_WORD_TAJWEED,
        currentPage,
        quranInfo,
        this@PagerActivity
      )
      ensureTajweedPageLoadedOrRefresh(wasTajweed, currentPage)
    }
    memorizationBar.onResetPage = {
      QuranMemorizationManager.resetAllPages(currentPage, quranInfo, this@PagerActivity)
      refreshQuranPages()
      supportInvalidateOptionsMenu()
    }
    memorizationBar.onToggleLiveTasmee = {
      activateOrToggleLiveTasmeeMode(forceEnable = false)
    }
    memorizationBar.onRetryTasmeeDownload = {
      LiveTasmeeManager.retryDownloadIfNeeded(this@PagerActivity)
    }
    memorizationBar.showInlineTasmeeStrip = false
    memorizationBar.isTajweedMushaf = quranSettings.pageType == "tajweed"
    memorizationBar.onToggleTajweedMushaf = {
      val nextPageType = if (quranSettings.pageType == "tajweed") "madani" else "tajweed"
      quranSettings.pageType = nextPageType
      memorizationBar.isTajweedMushaf = nextPageType == "tajweed"
      val targetPage = currentPage
      val isTranslation = showingTranslation
      val restartIntent = Intent(this@PagerActivity, PagerActivity::class.java).apply {
        putExtra("page", targetPage)
        putExtra(EXTRA_JUMP_TO_TRANSLATION, isTranslation)
      }
      finish()
      overridePendingTransition(0, 0)
      startActivity(restartIntent)
      overridePendingTransition(0, 0)
    }

    toolBarArea = findViewById(R.id.toolbar_area)
    translationsSpinner = findViewById(R.id.spinner)
    overlay = findViewById(R.id.overlay)

    ViewCompat.setOnApplyWindowInsetsListener(toolBarArea) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.statusBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.navigationBars()
      )
      view.updatePadding(insets.left, insets.top, insets.right, 0)
      windowInsets
    }

    (findViewById<View>(R.id.reader_body_container) ?: findViewById<View>(R.id.page_area_container))?.let { bodyArea ->
      ViewCompat.setOnApplyWindowInsetsListener(bodyArea) { view, windowInsets ->
        val insets = windowInsets.getInsets(
          WindowInsetsCompat.Type.navigationBars() or
              WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(insets.left, 0, insets.right, insets.bottom)
        windowInsets
      }
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)

    supportActionBar?.setDisplayShowHomeEnabled(true)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    initAyahActionPanel()

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
    nonRestoringViewPager.setIsDualPagesInLandscape(
      QuranUtils.isDualPagesInLandscape(this, quranScreenInfo)
    )

    viewPager = nonRestoringViewPager
    viewPager.setAdapter(pagerAdapter)
    MushafPageFlipTransformer.installSmoothFlipping(viewPager)

    ayahToolBar.setOnItemSelectedListener(AyahMenuItemSelectionHandler())
    val onPageChangeListener: OnPageChangeListener = object : OnPageChangeListener {
      override fun onPageScrollStateChanged(state: Int) {
      }

      override fun onPageScrolled(
        position: Int,
        positionOffset: Float,
        positionOffsetPixels: Int
      ) {
        val currentSelection = readingEventPresenter.currentAyahSelection()
        val selectionIndicator =
          currentSelection.selectionIndicator()
        val anchorAyah = currentSelection.endSuraAyah() ?: currentSelection.startSuraAyah()
        if (selectionIndicator !== SelectionIndicator.None && selectionIndicator !== SelectionIndicator.ScrollOnly && anchorAyah != null) {
          val anchorPage = quranInfo.getPageFromSuraAyah(anchorAyah.sura, anchorAyah.ayah)
          val barPos = quranInfo.getPositionFromPage(anchorPage, isDualPageVisible)
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
            // Move floating toolbar offscreen without clearing selectionStart so users can
            // swipe across pages and long-press an ending ayah for multi-page memorization ranges
            val updatedSelectionIndicator =
              selectionIndicator.withXScroll((viewPager.width * 2).toFloat())
            readingEventPresenterBridge.withSelectionIndicator(
              updatedSelectionIndicator
            )
          }
        }
      }

      override fun onPageSelected(position: Int) {
        Timber.d("onPageSelected(): %d", position)
        val page = quranInfo.getPageFromPosition(position, isDualPageVisible)
        if (lastObservedReaderPage != -1 &&
          page != lastObservedReaderPage &&
          !isProgrammaticRecitationPageTurn
        ) {
          stopActiveRecitationRecording()
        }
        lastObservedReaderPage = page
        QuranMemorizationManager.setCurrentPage(page, quranInfo, this@PagerActivity)
        if (KidsClassroomManager.isEnabled() && page in 1..604) {
          val curClassroomAyah = KidsClassroomManager.currentState().currentAyah
          val curClassroomPage = quranInfo.getPageFromSuraAyah(curClassroomAyah.sura, curClassroomAyah.ayah)
          val curClassroomPos = quranInfo.getPositionFromPage(curClassroomPage, isDualPageVisible)
          if (curClassroomPos != position) {
            val firstSuraOnPage = quranInfo.getSuraOnPage(page)
            val firstAyahOnPage = quranInfo.getFirstAyahOnPage(page).coerceAtLeast(1)
            val candidate = SuraAyah(firstSuraOnPage, firstAyahOnPage)
            if (MinshawiJuz30Repository.isSupportedAyah(candidate)) {
              KidsClassroomManager.selectAyahAndTurn(this@PagerActivity, candidate, 0, autoPlayLoop = false)
            }
          }
        }
        val cardToRate = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.onReaderPageSelected(
          this@PagerActivity,
          page
        )
        if (cardToRate != null) {
          showFlashcardRatingDialog(cardToRate)
        }

        if (quranSettings.shouldDisplayMarkerPopup()) {
          lastPopupTime = QuranDisplayHelper.displayMarkerPopup(
            this@PagerActivity, quranInfo, page, isDualPageVisible, lastPopupTime
          )
        }

        if (!showingTranslation) {
          updateActionBarTitle(page)
        } else {
          refreshActionBarSpinner()
        }
      }
    }
    viewPager.addOnPageChangeListener(onPageChangeListener)

    setUiVisibilityListener()
    audioStatusBar.visibility = View.VISIBLE
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
    if (page == 0) {
      onPageChangeListener.onPageSelected(0)
    }
    lastObservedReaderPage = currentPage
    QuranMemorizationManager.setCurrentPage(currentPage, quranInfo, this@PagerActivity)
    syncMemorizationAndTasmeeBarsVisibility()
    com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.setActiveCardFromIntent(
      this@PagerActivity,
      intent?.getStringExtra(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.EXTRA_FLASHCARD_ID),
      currentPage
    )

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

    onKidsClassroomBackCallback = object : OnBackPressedCallback(KidsClassroomManager.isEnabled() || isKidsSessionMode) {
      override fun handleOnBackPressed() {
        if (dismissVisibleOverlayIfAny()) {
          return
        }
        val state = KidsClassroomManager.currentState()
        if (isKidsLoopActivelyRunning(state)) {
          // 1st Back press during active audio/recording stops the turn cleanly instead of leaving
          KidsClassroomManager.stopActiveLoop()
          syncMemorizationAndTasmeeBarsVisibility()
          refreshQuranPages()
          return
        }
        if (isKidsSessionMode) {
          returnToKidsProgressIslands()
          return
        }
        KidsClassroomManager.disableClassroomMode(this@PagerActivity)
        onKidsClassroomBackCallback.isEnabled = false
        syncMemorizationAndTasmeeBarsVisibility()
        refreshQuranPages()
        supportInvalidateOptionsMenu()
      }
    }
    onBackPressedDispatcher.addCallback(this, onKidsClassroomBackCallback)

    onLiveTasmeeBackCallback = object : OnBackPressedCallback(LiveTasmeeManager.isEnabled()) {
      override fun handleOnBackPressed() {
        if (dismissVisibleOverlayIfAny()) {
          return
        }
        if (LiveTasmeeManager.isActivelyListening()) {
          // 1st Back press pauses listening cleanly back to READY without discarding highlights
          LiveTasmeeManager.pauseListeningToReady()
          syncMemorizationAndTasmeeBarsVisibility()
          refreshQuranPages()
          return
        }
        stopActiveRecitationRecording()
      }
    }
    onBackPressedDispatcher.addCallback(this, onLiveTasmeeBackCallback)

    onClearAyahModeBackCallback = object : OnBackPressedCallback(selectionStart != null) {
      override fun handleOnBackPressed() {
        endAyahMode()
      }
    }
    onBackPressedDispatcher.addCallback(this, onClearAyahModeBackCallback)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int, permissions: Array<String>, grantResults: IntArray
  ) {
    pagerActivityRecitationPresenter.onPermissionsResult(requestCode, grantResults)
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

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

  private fun initAyahActionPanel() {
    slidingPanel = findViewById(R.id.sliding_panel)
    val slidingLayout =
      slidingPanel.findViewById<ViewGroup>(R.id.sliding_layout)
    slidingPager = slidingPanel
      .findViewById(R.id.sliding_layout_pager)
    val slidingPageIndicator =
      slidingPanel
        .findViewById<IconPageIndicator>(R.id.sliding_pager_indicator)

    // Find close button and set listener
    val closeButton = slidingPanel
      .findViewById<View>(R.id.sliding_menu_close)
    closeButton.setOnClickListener { v: View? -> endAyahMode() }

    // Create and set fragment pager adapter
    slidingPagerAdapter = SlidingPagerAdapter(
      supportFragmentManager,
      QuranUtils.isRtl(),
      additionalAyahPanels
    )
    slidingPager.setAdapter(slidingPagerAdapter)

    // Attach the view pager to the action bar
    slidingPageIndicator.setViewPager(slidingPager)

    // Set sliding layout parameters
    val displayHeight = resources.displayMetrics.heightPixels
    slidingLayout.layoutParams.height = (displayHeight * PANEL_MAX_HEIGHT).toInt()
    slidingPanel.setEnableDragViewTouchEvents(true)
    slidingPanel.setPanelSlideListener(SlidingPanelListener())
    slidingLayout.visibility = View.GONE

    // When clicking any menu items, expand the panel
    slidingPageIndicator.setOnClickListener { v: View? ->
      if (!slidingPanel.isExpanded) {
        slidingPanel.expandPane()
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (!hasFocus) {
      handler.removeMessages(MSG_HIDE_ACTIONBAR)
    }
  }

  private fun onPageClicked() {
    if (KidsClassroomManager.onMushafPageTapped()) {
      return
    }
    if (QuranMemorizationManager.isHiddenModeEnabled()) {
      handleMemorizationTapOnPage(currentPage)
    }
  }

  fun handleMemorizationTapOnPage(page: Int = currentPage, tappedAyah: SuraAyah? = null) {
    if (!QuranMemorizationManager.isHiddenModeEnabled()) {
      return
    }
    when (val outcome = QuranMemorizationManager.onMemorizationPageTap(page, quranInfo, this, tappedAyah)) {
      is MemorizationTapOutcome.NotInHiddenMode -> {
        // Keep upper and lower bars steady without toggling fullscreen
      }
      is MemorizationTapOutcome.Revealed -> {
        // StateFlow/listener updates the Quran page view in-place
      }
      is MemorizationTapOutcome.TurnToNextPage -> {
        turnToNextPageInMemorization(fromPage = page, explicitTargetPage = outcome.nextPage)
      }
      is MemorizationTapOutcome.LoopBackToRangeStart -> {
        val targetPos = quranInfo.getPositionFromPage(outcome.targetPage, isDualPageVisible)
        if (viewPager.currentItem != targetPos) {
          viewPager.setCurrentItem(targetPos, true)
        }
        QuranMemorizationManager.setCurrentPage(outcome.targetPage, quranInfo, this)
        refreshQuranPages()
        supportInvalidateOptionsMenu()

        if (outcome.completedRepetitions > 0) {
          val isArabic = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.isAppInArabicMode(this)
          val res = MemorizationFormatting.localizedResources(this, isArabic)
          val completedStr = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.formatNumber(
            outcome.completedRepetitions,
            isArabic
          )
          val msg = if (outcome.targetRepetitions == QuranMemorizationManager.REPEAT_UNLIMITED) {
            res.getString(R.string.memorization_repeat_loop_unlimited_toast, completedStr)
          } else {
            val targetStr = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.formatNumber(
              outcome.targetRepetitions,
              isArabic
            )
            res.getString(R.string.memorization_repeat_loop_toast, completedStr, targetStr)
          }
          makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
      }
      is MemorizationTapOutcome.RangeRepetitionsCompleted -> {
        supportInvalidateOptionsMenu()
        val isArabic = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.isAppInArabicMode(this)
        val res = MemorizationFormatting.localizedResources(this, isArabic)
        makeText(
          this,
          res.getString(R.string.memorization_repeat_completed_toast),
          Toast.LENGTH_SHORT
        ).show()
      }
      is MemorizationTapOutcome.EndOfPageReached -> {
        val activeCard = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.getActiveCard(this)
        if (activeCard != null && page == activeCard.endPage) {
          showFlashcardRatingDialog(activeCard)
        }
      }
    }
  }

  fun turnToNextPageInMemorization(
    fromPage: Int = currentPage,
    explicitTargetPage: Int = fromPage + 1
  ): Boolean {
    val nextPage = explicitTargetPage
    if (quranInfo.isValidPage(nextPage)) {
      val nextPos = quranInfo.getPositionFromPage(nextPage, isDualPageVisible)
      if (nextPos != viewPager.currentItem) {
        viewPager.setCurrentItem(nextPos, true)
        return true
      } else if (isDualPageVisible) {
        val nextOutcome = QuranMemorizationManager.onMemorizationPageTap(nextPage, quranInfo, this)
        if (nextOutcome is MemorizationTapOutcome.Revealed) {
          return true
        } else if (nextOutcome is MemorizationTapOutcome.LoopBackToRangeStart) {
          val targetPos = quranInfo.getPositionFromPage(nextOutcome.targetPage, true)
          if (viewPager.currentItem != targetPos) {
            viewPager.setCurrentItem(targetPos, true)
          }
          refreshQuranPages()
          supportInvalidateOptionsMenu()
          return true
        } else if (nextOutcome is MemorizationTapOutcome.TurnToNextPage &&
          quranInfo.isValidPage(nextOutcome.nextPage)
        ) {
          val nextSpreadPos = quranInfo.getPositionFromPage(nextOutcome.nextPage, true)
          if (nextSpreadPos != viewPager.currentItem) {
            viewPager.setCurrentItem(nextSpreadPos, true)
            return true
          }
        }
      }
    }

    val activeCard = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.getActiveCard(this)
    if (activeCard != null && fromPage == activeCard.endPage) {
      showFlashcardRatingDialog(activeCard)
    }
    return false
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
      val startPosition = startPosition(ayahSelection)
      if (KidsClassroomManager.isEnabled() && startPosition != null && MinshawiJuz30Repository.isSupportedAyah(startPosition)) {
        KidsClassroomManager.selectAyahAndTurn(this, startPosition, 0, autoPlayLoop = false)
        endAyahMode()
        refreshQuranPages()
        return
      }
      updateLocalTranslations(startPosition)
      onClearAyahModeBackCallback.isEnabled = selectionStart != null
    } else {
      endAyahMode()
      onClearAyahModeBackCallback.isEnabled = false
    }
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

  private fun setUiVisibility(@Suppress("UNUSED_PARAMETER") isVisible: Boolean) {
    // Keep system bars, top toolbar, and bottom container permanently steady to eliminate screen-tap flickering
    isActionBarHidden = false
    if (::toolBarArea.isInitialized) {
      toolBarArea.translationY = 0f
    }
    if (::bottomBarContainer.isInitialized) {
      bottomBarContainer.translationY = 0f
    }
  }

  private fun setUiVisibilityListener() {
    isActionBarHidden = false
    toolBarArea.translationY = 0f
    bottomBarContainer.translationY = 0f
  }

  private fun clearUiVisibilityListener() {
    viewPager.setOnSystemUiVisibilityChangeListener(null)
  }

  private fun animateToolBar(@Suppress("UNUSED_PARAMETER") visible: Boolean) {
    isActionBarHidden = false
    toolBarArea.translationY = 0f
    bottomBarContainer.translationY = 0f
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
        PreferenceManager.getDefaultSharedPreferences(this)
          .getBoolean(Constants.PREF_USE_VOLUME_KEY_NAV, false))
        || super.onKeyUp(keyCode, event))
  }

  public override fun onResume() {
    super.onResume()
    quranSettings.registerPreferencesListener(themePreferencesListener)
    syncReaderSurfaceBackground()
    refreshQuranPages()

    audioPresenter.bind(this)
    recentPagePresenter.bind(currentPageFlow)
    readingBookmarkPresenter.bind(currentPageFlow, this)

    if (shouldReconnect) {
      foregroundDisposable.add(
        Completable.timer(500, TimeUnit.MILLISECONDS)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe {
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
            shouldReconnect = false
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
      val page = extras.getInt("page", Constants.PAGES_FIRST)

      val currentValue = showingTranslation
      showingTranslation = extras.getBoolean(EXTRA_JUMP_TO_TRANSLATION, showingTranslation)
      val highlightedSura = extras.getInt(EXTRA_HIGHLIGHT_SURA, -1)
      val highlightedAyah = extras.getInt(EXTRA_HIGHLIGHT_AYAH, -1)
      if (highlightedSura > 0 && highlightedAyah > 0) {
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

      if (highlightedAyah > 0 && highlightedSura > 0) {
        // this will jump to the right page automagically
        ensurePage(highlightedSura, highlightedAyah)
      } else {
        val pagePosition = quranInfo.getPositionFromPage(page, isDualPageVisible)
        viewPager.currentItem = pagePosition
      }

      setIntent(intent)
      val kidsSessionRequested = intent.getBooleanExtra(EXTRA_KIDS_SESSION_MODE, false)
      val kidsPracticeSurah = intent.getIntExtra(EXTRA_START_KIDS_MODE_SURAH, -1)
      val kidsPracticeAyah = intent.getIntExtra(EXTRA_START_KIDS_MODE_AYAH, 1).coerceAtLeast(1)
      val kidsAutoModeRaw = intent.getStringExtra(EXTRA_START_KIDS_AUTO_MODE)
      if (kidsSessionRequested || kidsPracticeSurah in 1..114) {
        isKidsSessionMode = true
        intent.removeExtra(EXTRA_KIDS_SESSION_MODE)
      } else if (!intent.getBooleanExtra(EXTRA_START_CHILDREN_LEARNING_MODE, false)) {
        // Explicit Normal Mode navigation clears any previous Kids Session flag
        if (isKidsSessionMode) {
          isKidsSessionMode = false
          if (KidsClassroomManager.isEnabled()) {
            KidsClassroomManager.disableClassroomMode(this)
          }
          syncMemorizationAndTasmeeBarsVisibility()
          supportInvalidateOptionsMenu()
        }
      }
      if (kidsPracticeSurah in 1..114) {
        intent.removeExtra(EXTRA_START_KIDS_MODE_SURAH)
        intent.removeExtra(EXTRA_START_KIDS_MODE_AYAH)
        intent.removeExtra(EXTRA_START_KIDS_AUTO_MODE)
        val launchMode = com.quran.labs.androidquran.kidsclassroom.KidsLessonLaunchMode.entries
          .firstOrNull { it.name == kidsAutoModeRaw }
          ?: com.quran.labs.androidquran.kidsclassroom.KidsLessonLaunchMode.OPEN_READY
        startKidsModeForSurah(kidsPracticeSurah, kidsPracticeAyah, launchMode)
      } else if (intent.getBooleanExtra(EXTRA_START_CHILDREN_LEARNING_MODE, false)) {
        intent.removeExtra(EXTRA_START_CHILDREN_LEARNING_MODE)
        activateOrToggleChildrenLearningMode(forceEnable = true)
      } else if (intent.getBooleanExtra(EXTRA_START_LIVE_TASMEE_MODE, false)) {
        intent.removeExtra(EXTRA_START_LIVE_TASMEE_MODE)
        activateOrToggleLiveTasmeeMode(forceEnable = true)
      }
      com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.setActiveCardFromIntent(
        this,
        intent.getStringExtra(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.EXTRA_FLASHCARD_ID),
        currentPage
      )
    }
  }

  override fun jumpTo(page: Int) {
    if (page != currentPage) {
      stopActiveRecitationRecording()
    }
    quranNavigator.jumpTo(Page(page))
  }

  override fun jumpToAndHighlight(page: Int, sura: Int, ayah: Int) {
    if (page != currentPage) {
      stopActiveRecitationRecording()
    }
    quranNavigator.jumpTo(SuraAyah(sura, ayah))
  }

  public override fun onPause() {
    if (!isChangingConfigurations) {
      stopActiveRecitationRecording()
    }
    quranSettings.unregisterPreferencesListener(themePreferencesListener)
    foregroundDisposable.clear()
    promptDialog?.dismiss()
    promptDialog = null
    flashcardRatingDialog?.dismiss()
    flashcardRatingDialog = null
    recentPagePresenter.unbind()
    readingBookmarkPresenter.unbind(this)
    quranSettings.wasShowingTranslation = pagerAdapter.isShowingTranslation

    super.onPause()
  }

  override fun onStop() {
    // the activity will be paused when requesting notification
    // permissions, which will otherwise break audio presenter.
    audioPresenter.unbind(this)
    super.onStop()
  }

  override fun onDestroy() {
    Timber.d("onDestroy()")
    clearUiVisibilityListener()

    translationJob?.cancel(CancellationException())
    compositeDisposable.dispose()
    audioStatusRepositoryBridge.dispose()
    readingEventPresenterBridge.dispose()
    downloadBridge.unsubscribe()
    if (KidsClassroomManager.onNavigateToAyahPage === kidsNavigateCallback) {
      KidsClassroomManager.onNavigateToAyahPage = null
    }
    if (KidsClassroomManager.onRequestMicPermission === kidsMicPermissionCallback) {
      KidsClassroomManager.onRequestMicPermission = null
    }
    if (LiveTasmeeManager.onRequestMicPermission === tasmeeMicPermissionCallback) {
      LiveTasmeeManager.onRequestMicPermission = null
    }
    if (LiveTasmeeManager.onAdvancePageOrLoopRange === tasmeeAdvancePageCallback) {
      LiveTasmeeManager.onAdvancePageOrLoopRange = null
    }
    if (LiveTasmeeManager.onStopPlaybackAudio === tasmeeStopPlaybackCallback) {
      LiveTasmeeManager.onStopPlaybackAudio = null
    }
    if (LiveTasmeeManager.onWordsRevealed === tasmeeWordsRevealedCallback) {
      LiveTasmeeManager.onWordsRevealed = null
    }
    if (LiveTasmeeManager.onJumpToPage === tasmeeJumpToPageCallback) {
      LiveTasmeeManager.onJumpToPage = null
    }
    if (isFinishing && !isChangingConfigurations) {
      LiveTasmeeManager.stopTasmee()
      KidsClassroomManager.stopActiveLoop()
    }
    handler.removeCallbacksAndMessages(null)
    scope.cancel()
    super.onDestroy()
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
    state.putBoolean(LAST_KIDS_SESSION_MODE, isKidsSessionMode)
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

    val isMemorizationActive = QuranMemorizationManager.isHiddenModeEnabled()
    val currentRevealMode = QuranMemorizationManager.getRevealMode()

    val memorizationItem = menu.findItem(R.id.memorization_mode)
    if (memorizationItem != null) {
      memorizationItem.isChecked = isMemorizationActive
      memorizationItem.setIcon(
        if (isMemorizationActive) R.drawable.ic_memorization_hidden else R.drawable.ic_memorization_mode
      )
      memorizationItem.setTitle(
        if (isMemorizationActive) R.string.show_quran_text else R.string.hide_quran_text
      )
      memorizationItem.icon?.alpha = if (isMemorizationActive) 255 else 210
    }

    menu.findItem(R.id.memorization_full_ayah)?.apply {
      isChecked = isMemorizationActive && currentRevealMode == RevealMode.FULL_AYAH
    }
    menu.findItem(R.id.memorization_word_by_word)?.apply {
      isChecked = isMemorizationActive && currentRevealMode == RevealMode.WORD_BY_WORD
    }
    menu.findItem(R.id.memorization_word_window)?.apply {
      isChecked = isMemorizationActive && currentRevealMode == RevealMode.WORD_WINDOW
      title = MemorizationFormatting.formatWordWindowMenuTitle(this@PagerActivity)
    }
    menu.findItem(R.id.memorization_single_word)?.apply {
      isChecked = isMemorizationActive && currentRevealMode == RevealMode.SINGLE_WORD
    }
    menu.findItem(R.id.memorization_live_tasmee)?.apply {
      val isTasmeeActive = LiveTasmeeManager.isEnabled()
      isChecked = isTasmeeActive
      setIcon(
        if (isTasmeeActive) R.drawable.ic_live_tasmee_mic_active else R.drawable.ic_live_tasmee_mic
      )
      icon?.alpha = if (isTasmeeActive) 255 else 215
    }
    menu.findItem(R.id.memorization_single_word_tajweed)?.apply {
      isChecked = isMemorizationActive && currentRevealMode == RevealMode.SINGLE_WORD_TAJWEED
    }
    menu.findItem(R.id.memorization_repeat_range)?.apply {
      val activeRange = QuranMemorizationManager.getActiveRange()
      isChecked = activeRange != null
      title = MemorizationFormatting.formatActiveRangeMenuTitle(this@PagerActivity, activeRange)
    }
    // Keep memorization & recitation sub-items hidden from the overflow dropdown;
    // all controls live in the unified clean Options UI sheet.
    menu.findItem(R.id.memorization_reset)?.apply {
      isVisible = false
    }
    menu.findItem(R.id.tajweed_mushaf)?.apply {
      isChecked = quranSettings.pageType == "tajweed"
    }
    menu.findItem(R.id.children_learning_mode)?.apply {
      isChecked = KidsClassroomManager.isEnabled()
    }
    menu.findItem(R.id.select_reciter)?.apply {
      title = getString(R.string.select_reciter_with_current, currentQariItem().name)
    }

    val quran = menu.findItem(R.id.goto_quran)
    val translation = menu.findItem(R.id.goto_translation)
    if (isKidsSessionMode) {
      // Dedicated Kids Mode toolbar: hide adult-only actions so the lesson stays calm & focused
      menu.findItem(R.id.favorite_item)?.isVisible = false
      quran?.isVisible = false
      translation?.isVisible = false
      menu.findItem(R.id.search)?.isVisible = false
      menu.findItem(R.id.memorization_mode)?.isVisible = false
      menu.findItem(R.id.jump)?.isVisible = false
      menu.findItem(R.id.settings)?.isVisible = false
      menu.findItem(R.id.help)?.isVisible = false
      menu.findItem(R.id.page_theme)?.isVisible = true
      menu.findItem(R.id.memorization_options_sheet)?.isVisible = true
    } else {
      menu.findItem(R.id.favorite_item)?.isVisible = true
      menu.findItem(R.id.search)?.isVisible = true
      menu.findItem(R.id.memorization_mode)?.isVisible = true
      menu.findItem(R.id.jump)?.isVisible = true
      menu.findItem(R.id.settings)?.isVisible = true
      menu.findItem(R.id.help)?.isVisible = true
      if (quran != null && translation != null) {
        if (!showingTranslation) {
          quran.isVisible = false
          translation.isVisible = true
        } else {
          quran.isVisible = true
          translation.isVisible = false
        }
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
    } else if (itemId == R.id.memorization_mode) {
      QuranMemorizationManager.toggleHiddenMode(currentPage, quranInfo, this)
      refreshQuranPages()
      supportInvalidateOptionsMenu()
      return true
    } else if (itemId == R.id.memorization_options_sheet) {
      if (isKidsSessionMode || KidsClassroomManager.isEnabled()) {
        showKidsClassroomOptionsSheet()
      } else {
        showMemorizationControlsSheet()
      }
      return true
    } else if (itemId == R.id.memorization_full_ayah) {
      QuranMemorizationManager.onProminentModeButtonPressed(
        RevealMode.FULL_AYAH,
        currentPage,
        quranInfo,
        this
      )
      refreshQuranPages()
      supportInvalidateOptionsMenu()
      return true
    } else if (itemId == R.id.memorization_word_by_word) {
      QuranMemorizationManager.onProminentModeButtonPressed(
        RevealMode.WORD_BY_WORD,
        currentPage,
        quranInfo,
        this
      )
      refreshQuranPages()
      supportInvalidateOptionsMenu()
      return true
    } else if (itemId == R.id.memorization_word_window) {
      showWordWindowSetupSheet()
      return true
    } else if (itemId == R.id.memorization_single_word) {
      QuranMemorizationManager.onProminentModeButtonPressed(
        RevealMode.SINGLE_WORD,
        currentPage,
        quranInfo,
        this
      )
      refreshQuranPages()
      supportInvalidateOptionsMenu()
      return true
    } else if (itemId == R.id.memorization_live_tasmee) {
      activateOrToggleLiveTasmeeMode(forceEnable = false)
      return true
    } else if (itemId == R.id.memorization_single_word_tajweed) {
      val wasTajweed = quranSettings.pageType == "tajweed"
      QuranMemorizationManager.onProminentModeButtonPressed(
        RevealMode.SINGLE_WORD_TAJWEED,
        currentPage,
        quranInfo,
        this
      )
      ensureTajweedPageLoadedOrRefresh(wasTajweed, currentPage)
      return true
    } else if (itemId == R.id.memorization_repeat_range) {
      showMemorizationRepetitionSheet(selectionStart, selectionEnd)
      return true
    } else if (itemId == R.id.memorization_reset) {
      QuranMemorizationManager.resetAllPages(currentPage, quranInfo, this)
      val activeStartPage = QuranMemorizationManager.getActiveRange()?.startPage
      if (activeStartPage != null) {
        val targetPos = quranInfo.getPositionFromPage(activeStartPage, isDualPageVisible)
        if (viewPager.currentItem != targetPos) {
          viewPager.setCurrentItem(targetPos, true)
        }
      }
      refreshQuranPages()
      supportInvalidateOptionsMenu()
      return true
    } else if (itemId == R.id.tajweed_mushaf) {
      val nextPageType = if (quranSettings.pageType == "tajweed") "madani" else "tajweed"
      quranSettings.pageType = nextPageType
      memorizationBar.isTajweedMushaf = nextPageType == "tajweed"
      val targetPage = currentPage
      val isTranslation = showingTranslation
      val restartIntent = Intent(this@PagerActivity, PagerActivity::class.java).apply {
        putExtra("page", targetPage)
        putExtra(EXTRA_JUMP_TO_TRANSLATION, isTranslation)
      }
      finish()
      overridePendingTransition(0, 0)
      startActivity(restartIntent)
      overridePendingTransition(0, 0)
      return true
    } else if (itemId == R.id.children_learning_mode) {
      activateOrToggleChildrenLearningMode(forceEnable = false)
      return true
    } else if (itemId == R.id.memory_flashcards) {
      val flashcardsIntent = Intent(
        this,
        com.quran.labs.androidquran.flashcards.MemoryFlashcardsActivity::class.java
      ).apply {
        putExtra(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.EXTRA_INITIAL_PAGE, currentPage)
      }
      startActivity(flashcardsIntent)
      return true
    } else if (itemId == R.id.select_reciter) {
      onShowQariList()
      return true
    } else if (itemId == R.id.play_page_audio) {
      onPlayPressed()
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
      if (dismissVisibleOverlayIfAny()) {
        return true
      }
      if (isKidsSessionMode) {
        returnToKidsProgressIslands()
        return true
      }
      val kidsState = KidsClassroomManager.currentState()
      if (LiveTasmeeManager.isActivelyListening()) {
        LiveTasmeeManager.pauseListeningToReady()
        syncMemorizationAndTasmeeBarsVisibility()
        refreshQuranPages()
        return true
      }
      if (isKidsLoopActivelyRunning(kidsState)) {
        stopActiveRecitationRecording()
        return true
      }
      if (LiveTasmeeManager.isEnabled()) {
        LiveTasmeeManager.stopTasmee()
      }
      if (KidsClassroomManager.isEnabled()) {
        KidsClassroomManager.disableClassroomMode(this)
      }
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
    val totalCount = pagerAdapter.count
    for (i in 0 until totalCount) {
      val f = pagerAdapter.getFragmentIfExists(i)
      if (f is QuranPage) {
        (f as QuranPage).updateView()
      }
    }
  }

  private fun showFlashcardRatingDialog(
    card: com.quran.labs.androidquran.flashcards.MemoryFlashcard
  ) {
    if (!canShowUi() || flashcardRatingDialog?.isShowing == true) return
    val isArabic = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.isAppInArabicMode(this)
    val builder = AlertDialog.Builder(this)
    builder.setTitle(R.string.flashcard_rate_dialog_title)
      .setMessage(
        getString(
          R.string.flashcard_rate_dialog_message,
          card.firstAyahCue,
          card.lastAyahCue
        )
      )
      .setPositiveButton(
        getString(R.string.flashcard_rate_good, card.nextGoodScheduleLabel(isArabic))
      ) { dialog: DialogInterface, _: Int ->
        dialog.dismiss()
        flashcardRatingDialog = null
        handleFlashcardRated(card, isGood = true)
      }
      .setNegativeButton(
        getString(
          R.string.flashcard_rate_repeat,
          com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.getRepeatScheduleLabel(isArabic)
        )
      ) { dialog: DialogInterface, _: Int ->
        dialog.dismiss()
        flashcardRatingDialog = null
        handleFlashcardRated(card, isGood = false)
      }
      .setOnCancelListener {
        flashcardRatingDialog = null
        com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.markRatingDialogDismissed()
      }
    val dialog = builder.create()
    flashcardRatingDialog = dialog
    dialog.show()
  }

  private fun handleFlashcardRated(
    card: com.quran.labs.androidquran.flashcards.MemoryFlashcard,
    isGood: Boolean
  ) {
    val isArabic = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.isAppInArabicMode(this)
    com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.rateCard(
      context = this,
      cardId = card.id,
      isGood = isGood
    )
    val nextCard = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.advanceToNextCardInSession(
      context = this,
      quranInfo = quranInfo
    )
    if (nextCard != null) {
      if (nextCard.isTextCard()) {
        val remainingIds = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.consumeRemainingQueueIds(this)
        val studyTextIntent = Intent(
          this,
          com.quran.labs.androidquran.flashcards.MemoryFlashcardsActivity::class.java
        ).apply {
          putExtra(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.EXTRA_STUDY_TEXT_CARD_ID, nextCard.id)
          putStringArrayListExtra(
            com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.EXTRA_STUDY_QUEUE_IDS,
            ArrayList(remainingIds)
          )
          putExtra(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.EXTRA_INITIAL_PAGE, currentPage)
          flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(studyTextIntent)
        finish()
        return
      }
      val nextPos = quranInfo.getPositionFromPage(nextCard.startPage, isDualPageVisible)
      viewPager.currentItem = nextPos
      QuranMemorizationManager.setCurrentPage(nextCard.startPage, quranInfo, this)
      refreshQuranPages()
      supportInvalidateOptionsMenu()
      val formattedPage = com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.formatNumber(
        nextCard.startPage,
        isArabic
      )
      makeText(
        this,
        getString(R.string.flashcard_next_card_toast, formattedPage),
        Toast.LENGTH_SHORT
      ).show()
    } else {
      makeText(
        this,
        getString(R.string.flashcard_session_complete_toast),
        Toast.LENGTH_SHORT
      ).show()
      val flashcardsIntent = Intent(
        this,
        com.quran.labs.androidquran.flashcards.MemoryFlashcardsActivity::class.java
      ).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.EXTRA_INITIAL_PAGE, currentPage)
      }
      startActivity(flashcardsIntent)
    }
  }

  private fun switchToQuran() {
    if (selectionStart != null) {
      endAyahMode()
    }
    val page = currentPage
    pagerAdapter.setQuranMode()
    showingTranslation = false
    onShowingTranslationBackCallback.isEnabled = false
    syncMemorizationAndTasmeeBarsVisibility()
    if (shouldUpdatePageNumber()) {
      val position = quranInfo.getPositionFromPage(page, true)
      viewPager.currentItem = position
    }

    supportInvalidateOptionsMenu()
    updateActionBarTitle(page)
  }

  private fun switchToTranslation() {
    stopActiveRecitationRecording()
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
      syncMemorizationAndTasmeeBarsVisibility()
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
    val sura = quranDisplayData.getSuraNameFromPage(this, page, true)
    val actionBar = supportActionBar
    if (actionBar != null) {
      translationsSpinner.visibility = View.GONE
      actionBar.setDisplayShowTitleEnabled(true)
      actionBar.title = sura
      val desc = if (isKidsSessionMode) {
        getString(R.string.kids_mode_toolbar_subtitle)
      } else {
        quranDisplayData.getPageSubtitle(this, page)
      }
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
            holder.title.setTextColor(ResourcesCompat.getColor(resources, R.color.toolbar_text, null))
            val desc = quranDisplayData.getPageSubtitle(this@PagerActivity, page)
            holder.subtitle.text = desc
            holder.subtitle.visibility = View.VISIBLE
            holder.subtitle.setTextColor(ResourcesCompat.getColor(resources, R.color.toolbar_secondary_text, null))
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

  fun toggleActionBarVisibility(@Suppress("UNUSED_PARAMETER") visible: Boolean) {
    handler.removeMessages(MSG_HIDE_ACTIONBAR)
    isActionBarHidden = false
    setUiVisibility(true)
  }

  fun toggleActionBar() {
    handler.removeMessages(MSG_HIDE_ACTIONBAR)
    isActionBarHidden = false
    setUiVisibility(true)
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
    // the sliding panel that hosts the rest of the reading UI claims every touch within its
    // collapsed (audio bar) region for its own drag/tap handling, regardless of what's drawn on
    // top of it there - so rather than fight that, sit the toast just above the audio bar (when
    // one is visible, its own height already accounts for the navigation bar inset) or above the
    // navigation bar directly (when it isn't, e.g. three-button navigation in edge-to-edge mode).
    // side padding covers landscape three-button navigation and display cutouts.
    ViewCompat.setOnApplyWindowInsetsListener(toastView) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
      )
      view.updatePadding(left = insets.left, right = insets.right)
      val params = view.layoutParams as FrameLayout.LayoutParams
      params.bottomMargin = if (audioStatusBar.isVisible && audioStatusBar.height > 0) {
        audioStatusBar.height
      } else {
        insets.bottom
      }
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
    handlePlayback(null)
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
    playFromAyah(start, ending, page, 0, 0, ending != null, 1.0f)
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
    val qariListWrapper = QariListWrapper(this, starting, ending) {
      overlay.removeAllViews()
      overlay.visibility = View.GONE
      toggleActionBarVisibility(true)
      supportInvalidateOptionsMenu()
    }
    overlay.removeAllViews()
    overlay.addView(
      qariListWrapper,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    toggleActionBarVisibility(false)
  }

  private fun showReadingBookmarkSheet(target: ReadingBookmarkTarget) {
    val readingBookmarkSheetWrapper = ReadingBookmarkSheetWrapper(
      context = this,
      target = target,
      onDismissed = {
        overlay.removeAllViews()
        overlay.visibility = View.GONE
        toggleActionBarVisibility(true)
      }
    )
    overlay.removeAllViews()
    overlay.addView(
      readingBookmarkSheetWrapper,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    toggleActionBarVisibility(false)
  }

  private fun showPageThemeSheet() {
    val pageThemeSheetWrapper = PageThemeSheetWrapper(this, quranSettings) {
      overlay.removeAllViews()
      overlay.visibility = View.GONE
      toggleActionBarVisibility(true)
    }
    overlay.removeAllViews()
    overlay.addView(
      pageThemeSheetWrapper,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    toggleActionBarVisibility(false)
  }

  private fun showWordWindowSetupSheet() {
    val sheet = WordWindowSetupOverlayView(
      context = this,
      isNightPage = quranSettings.isNightMode,
      onApplyWindow = { chosenWindowWords ->
        QuranMemorizationManager.setPreviousAyahWindowWords(chosenWindowWords, this)
        if (!QuranMemorizationManager.isHiddenModeEnabled() ||
          QuranMemorizationManager.getRevealMode() != RevealMode.WORD_WINDOW
        ) {
          QuranMemorizationManager.onProminentModeButtonPressed(
            RevealMode.WORD_WINDOW,
            currentPage,
            quranInfo,
            this
          )
        }
        refreshQuranPages()
        supportInvalidateOptionsMenu()
      },
      onDismissed = {
        overlay.removeAllViews()
        overlay.visibility = View.GONE
        toggleActionBarVisibility(true)
      }
    )
    overlay.removeAllViews()
    overlay.addView(
      sheet,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    toggleActionBarVisibility(false)
  }

  private fun showMemorizationRepetitionSheet(
    initialStartAyah: SuraAyah? = null,
    initialEndAyah: SuraAyah? = null
  ) {
    val sheet = MemorizationRepetitionOverlayView(
      context = this,
      quranInfo = quranInfo,
      currentPage = currentPage,
      initialStartAyah = initialStartAyah,
      initialEndAyah = initialEndAyah,
      isNightPage = quranSettings.isNightMode,
      onStartRange = { startAyah, endAyah, repetitions, revealMode, windowWords ->
        val wasTajweed = quranSettings.pageType == "tajweed"
        QuranMemorizationManager.setPreviousAyahWindowWords(windowWords, this)
        QuranMemorizationManager.setRevealMode(revealMode, this)
        val range = QuranMemorizationManager.startRangeMemorization(
          startAyah = startAyah,
          endAyah = endAyah,
          targetRepetitions = repetitions,
          quranInfo = quranInfo,
          context = this
        )
        endAyahMode()
        if (revealMode == RevealMode.SINGLE_WORD_TAJWEED && !wasTajweed) {
          ensureTajweedPageLoadedOrRefresh(wasTajweed = false, targetPage = range.startPage)
        } else {
          val startPos = quranInfo.getPositionFromPage(range.startPage, isDualPageVisible)
          if (viewPager.currentItem != startPos) {
            viewPager.setCurrentItem(startPos, true)
          }
          QuranMemorizationManager.setCurrentPage(range.startPage, quranInfo, this)
          refreshQuranPages()
          supportInvalidateOptionsMenu()
        }
      },
      onStopRange = {
        QuranMemorizationManager.clearMemorizationRange()
        refreshQuranPages()
        supportInvalidateOptionsMenu()
      },
      onDismissed = {
        overlay.removeAllViews()
        overlay.visibility = View.GONE
        toggleActionBarVisibility(true)
      }
    )
    overlay.removeAllViews()
    overlay.addView(
      sheet,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    toggleActionBarVisibility(false)
  }

  private fun activateOrToggleChildrenLearningMode(forceEnable: Boolean) {
    val wasTajweed = quranSettings.pageType == "tajweed"
    val targetAyah = if (forceEnable) {
      KidsClassroomManager.enableClassroomMode(
        context = this,
        currentPage = currentPage,
        selectedAyah = selectionStart,
        quranInfo = quranInfo,
        enableWordHiding = true
      )
    } else {
      KidsClassroomManager.toggleClassroomMode(
        context = this,
        currentPage = currentPage,
        selectedAyah = selectionStart,
        quranInfo = quranInfo,
        enableWordHiding = true
      )
    }

    onKidsClassroomBackCallback.isEnabled = KidsClassroomManager.isEnabled()
    syncMemorizationAndTasmeeBarsVisibility()

    if (targetAyah != null) {
      val targetPage = quranInfo.getPageFromSuraAyah(targetAyah.sura, targetAyah.ayah)
        .takeIf { quranInfo.isValidPage(it) } ?: currentPage
      if (showingTranslation) {
        switchToQuran()
      }
      onStopPressed()
      endAyahMode()
      if (!wasTajweed) {
        quranSettings.pageType = "tajweed"
        memorizationBar.isTajweedMushaf = true
        val restartIntent = Intent(this@PagerActivity, PagerActivity::class.java).apply {
          putExtra("page", targetPage)
          putExtra(EXTRA_JUMP_TO_TRANSLATION, false)
          putExtra(EXTRA_START_CHILDREN_LEARNING_MODE, true)
          putExtra(EXTRA_KIDS_SESSION_MODE, isKidsSessionMode)
        }
        finish()
        overridePendingTransition(0, 0)
        startActivity(restartIntent)
        overridePendingTransition(0, 0)
      } else {
        ensurePage(targetAyah.sura, targetAyah.ayah)
        refreshQuranPages()
        supportInvalidateOptionsMenu()
      }
    } else {
      refreshQuranPages()
      supportInvalidateOptionsMenu()
    }
  }

  private fun activateOrToggleLiveTasmeeMode(forceEnable: Boolean) {
    if (showingTranslation) {
      switchToQuran()
    }
    endAyahMode()
    if (forceEnable) {
      LiveTasmeeManager.prepareTasmeeReady(this, currentPage, quranInfo)
    } else {
      LiveTasmeeManager.toggleTasmee(this, currentPage, quranInfo)
    }
    onKidsClassroomBackCallback.isEnabled = KidsClassroomManager.isEnabled() || isKidsSessionMode
    syncMemorizationAndTasmeeBarsVisibility()
    refreshQuranPages()
    supportInvalidateOptionsMenu()
  }

  private fun showMemorizationControlsSheet() {
    val sheet = MemorizationControlsSheetOverlayView(
      context = this,
      isNightPage = quranSettings.isNightMode,
      isTajweedMushaf = quranSettings.pageType == "tajweed",
      currentReciterName = currentQariItem().name,
      onOpenKidsMode = {
        stopActiveRecitationRecording()
        com.quran.labs.androidquran.kidsclassroom.KidsModeActivity.start(this)
      },
      onToggleLiveTasmee = {
        activateOrToggleLiveTasmeeMode(forceEnable = false)
      },
      onToggleHideText = {
        QuranMemorizationManager.toggleHiddenMode(currentPage, quranInfo, this)
        refreshQuranPages()
        supportInvalidateOptionsMenu()
      },
      onSelectRevealMode = { mode ->
        val wasTajweed = quranSettings.pageType == "tajweed"
        QuranMemorizationManager.onProminentModeButtonPressed(
          mode,
          currentPage,
          quranInfo,
          this
        )
        if (mode == RevealMode.SINGLE_WORD_TAJWEED) {
          ensureTajweedPageLoadedOrRefresh(wasTajweed, currentPage)
        } else {
          refreshQuranPages()
          supportInvalidateOptionsMenu()
        }
      },
      onOpenWordWindowSetup = {
        showWordWindowSetupSheet()
      },
      onOpenRepetitionSetup = {
        showMemorizationRepetitionSheet(selectionStart, selectionEnd)
      },
      onResetPage = {
        QuranMemorizationManager.resetAllPages(currentPage, quranInfo, this)
        refreshQuranPages()
        supportInvalidateOptionsMenu()
      },
      onToggleTajweedMushaf = {
        val nextPageType = if (quranSettings.pageType == "tajweed") "madani" else "tajweed"
        quranSettings.pageType = nextPageType
        memorizationBar.isTajweedMushaf = nextPageType == "tajweed"
        val restartIntent = Intent(this@PagerActivity, PagerActivity::class.java).apply {
          putExtra("page", currentPage)
          putExtra(EXTRA_JUMP_TO_TRANSLATION, showingTranslation)
        }
        finish()
        overridePendingTransition(0, 0)
        startActivity(restartIntent)
        overridePendingTransition(0, 0)
      },
      onPlayPageAudio = {
        onPlayPressed()
      },
      onSelectReciter = {
        onShowQariList()
      },
      onOpenFlashcards = {
        val flashcardsIntent = Intent(
          this,
          com.quran.labs.androidquran.flashcards.MemoryFlashcardsActivity::class.java
        ).apply {
          putExtra(
            com.quran.labs.androidquran.flashcards.MemoryFlashcardManager.EXTRA_INITIAL_PAGE,
            currentPage
          )
        }
        startActivity(flashcardsIntent)
      },
      onDismissed = {
        overlay.removeAllViews()
        overlay.visibility = View.GONE
        toggleActionBarVisibility(true)
      }
    )
    overlay.removeAllViews()
    overlay.addView(
      sheet,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    toggleActionBarVisibility(false)
  }

  private fun showKidsClassroomOptionsSheet() {
    if (!::overlay.isInitialized || isFinishing || isDestroyed) return
    val sheet = KidsClassroomOptionsOverlayView(
      context = this,
      isNightPage = quranSettings.isNightMode,
      isKidsSessionMode = isKidsSessionMode,
      onExitClassroomMode = {
        if (isKidsSessionMode) {
          returnToKidsProgressIslands()
        } else {
          KidsClassroomManager.disableClassroomMode(this@PagerActivity)
          syncMemorizationAndTasmeeBarsVisibility()
          refreshQuranPages()
          supportInvalidateOptionsMenu()
        }
      },
      onOpenSurahAnalysis = {
        val surah = if (KidsClassroomManager.isEnabled()) {
          KidsClassroomManager.currentState().currentAyah.sura
        } else {
          quranInfo.getSuraOnPage(currentPage)
        }
        showKidsSurahAnalysisSheet(surah)
      },
      onOpenAchievements = {
        if (isKidsSessionMode) {
          returnToKidsProgressIslands(
            targetTab = com.quran.labs.androidquran.kidsclassroom.KidsModeTab.REWARDS
          )
        } else {
          com.quran.labs.androidquran.kidsclassroom.KidsModeActivity.start(
            this,
            com.quran.labs.androidquran.kidsclassroom.KidsModeTab.REWARDS
          )
        }
      },
      onDismissed = {
        overlay.removeAllViews()
        overlay.visibility = View.GONE
        toggleActionBarVisibility(true)
      }
    )
    overlay.removeAllViews()
    overlay.addView(
      sheet,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    toggleActionBarVisibility(false)
  }

  private fun showKidsSurahAnalysisSheet(initialSurah: Int) {
    if (!::overlay.isInitialized || isFinishing || isDestroyed) return
    val sheet = KidsSurahAnalysisOverlayView(
      context = this,
      initialSurah = initialSurah.coerceIn(1, 114),
      isNightPage = quranSettings.isNightMode,
      onPracticeAyah = { targetAyah ->
        if (!KidsClassroomManager.isEnabled()) {
          KidsClassroomManager.enableClassroomMode(
            context = this@PagerActivity,
            currentPage = currentPage,
            selectedAyah = targetAyah,
            quranInfo = quranInfo,
            enableWordHiding = true
          )
          syncMemorizationAndTasmeeBarsVisibility()
        }
        KidsClassroomManager.selectAyahAndTurn(
          context = this@PagerActivity,
          suraAyah = targetAyah,
          turnIndex = 0,
          autoStartMode = com.quran.labs.androidquran.kidsclassroom.KidsPracticeMode.LISTEN_AND_REPEAT
        )
        refreshQuranPages()
      },
      onDismissed = {
        overlay.removeAllViews()
        overlay.visibility = View.GONE
        toggleActionBarVisibility(true)
      }
    )
    overlay.removeAllViews()
    overlay.addView(
      sheet,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    toggleActionBarVisibility(false)
  }

  private fun startKidsModeForSurah(
    surah: Int,
    ayah: Int = 1,
    launchMode: com.quran.labs.androidquran.kidsclassroom.KidsLessonLaunchMode =
      com.quran.labs.androidquran.kidsclassroom.KidsLessonLaunchMode.OPEN_READY
  ) {
    val safeSurah = surah.coerceIn(1, 114)
    val safeAyah = ayah.coerceAtLeast(1)
    val targetAyah = SuraAyah(safeSurah, safeAyah)

    // Snapshot starting rewards so returning to Progress Islands celebrates newly earned progress
    com.quran.labs.androidquran.kidsclassroom.RewardsDataStore.initialize(applicationContext)
    val snapshotRewards = com.quran.labs.androidquran.kidsclassroom.RewardsDataStore.state.value
    kidsSessionSurah = safeSurah
    kidsSessionInitialStars = snapshotRewards.totalStars
    kidsSessionInitialSurahMastered = snapshotRewards.surahMasteredCount[safeSurah] ?: 0

    ensurePage(safeSurah, safeAyah)
    if (!KidsClassroomManager.isEnabled()) {
      KidsClassroomManager.enableClassroomMode(
        context = this,
        currentPage = quranInfo.getPageFromSuraAyah(safeSurah, safeAyah),
        selectedAyah = targetAyah,
        quranInfo = quranInfo,
        enableWordHiding = true
      )
      syncMemorizationAndTasmeeBarsVisibility()
    } else {
      KidsClassroomManager.selectAyahAndTurn(
        context = this,
        suraAyah = targetAyah,
        turnIndex = 0,
        autoStartMode = null
      )
    }

    when (launchMode) {
      com.quran.labs.androidquran.kidsclassroom.KidsLessonLaunchMode.LISTEN_AND_REPEAT -> {
        KidsClassroomManager.selectAyahAndTurn(
          context = this,
          suraAyah = targetAyah,
          turnIndex = 0,
          autoStartMode = com.quran.labs.androidquran.kidsclassroom.KidsPracticeMode.LISTEN_AND_REPEAT
        )
      }
      com.quran.labs.androidquran.kidsclassroom.KidsLessonLaunchMode.RECITE_AND_REVEAL -> {
        KidsClassroomManager.selectAyahAndTurn(
          context = this,
          suraAyah = targetAyah,
          turnIndex = 0,
          autoStartMode = com.quran.labs.androidquran.kidsclassroom.KidsPracticeMode.RECITE_AND_REVEAL
        )
      }
      com.quran.labs.androidquran.kidsclassroom.KidsLessonLaunchMode.OPEN_READY -> Unit
    }

    updateActionBarTitle(currentPage)
    supportInvalidateOptionsMenu()
    refreshQuranPages()
  }

  private fun returnToKidsProgressIslands(
    targetTab: com.quran.labs.androidquran.kidsclassroom.KidsModeTab =
      com.quran.labs.androidquran.kidsclassroom.KidsModeTab.ISLANDS
  ) {
    val activeSurah = if (KidsClassroomManager.isEnabled()) {
      KidsClassroomManager.currentState().currentAyah.sura
    } else {
      kidsSessionSurah
    }.coerceIn(1, 114)

    com.quran.labs.androidquran.kidsclassroom.RewardsDataStore.initialize(applicationContext)
    val latestRewards = com.quran.labs.androidquran.kidsclassroom.RewardsDataStore.state.value
    val starsGained = (latestRewards.totalStars - kidsSessionInitialStars).coerceAtLeast(0)
    val masteredNow = latestRewards.surahMasteredCount[activeSurah] ?: 0
    val masteredBaseline = if (activeSurah == kidsSessionSurah) kidsSessionInitialSurahMastered else masteredNow
    val ayatGained = (masteredNow - masteredBaseline).coerceAtLeast(0)
    val surahCompleted = com.quran.labs.androidquran.kidsclassroom.SurahJourneyPlanner
      .isSurahCompleted(activeSurah, latestRewards)

    if (LiveTasmeeManager.isEnabled()) {
      LiveTasmeeManager.stopTasmee()
    }
    if (KidsClassroomManager.isEnabled()) {
      KidsClassroomManager.disableClassroomMode(this)
    }
    onSessionEnd()

    if (targetTab == com.quran.labs.androidquran.kidsclassroom.KidsModeTab.ISLANDS) {
      com.quran.labs.androidquran.kidsclassroom.KidsModeNavigationState.emitReturnToIslands(
        surahNumber = activeSurah,
        starsGained = starsGained,
        ayatMasteredGained = ayatGained,
        surahJustCompleted = surahCompleted && (starsGained > 0 || ayatGained > 0)
      )
    }

    val islandsIntent = com.quran.labs.androidquran.kidsclassroom.KidsModeActivity.createIntent(
      context = this,
      initialTab = targetTab
    ).apply {
      addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    startActivity(islandsIntent)
    @Suppress("DEPRECATION")
    overridePendingTransition(R.anim.kids_islands_enter, R.anim.kids_lesson_exit)
    finish()
  }

  private fun syncReaderSurfaceBackground() {
    val surfaceColor = if (!quranSettings.isLightPage) {
      quranSettings.pageThemeBackgroundColor
    } else {
      ContextCompat.getColor(this, com.quran.mobile.feature.pagetheme.R.color.page_background)
    }
    findViewById<View>(R.id.reader_body_container)?.setBackgroundColor(surfaceColor)
    findViewById<View>(R.id.bottom_recitation_dock)?.setBackgroundColor(surfaceColor)
  }

  private fun isKidsLoopActivelyRunning(state: KidsClassroomUiState): Boolean {
    return state.stage == ClassroomStage.QARI_RECITING ||
      state.stage == ClassroomStage.KIDS_REPEATING ||
      state.stage == ClassroomStage.CHILD_RECITING ||
      state.stage == ClassroomStage.TASMEE_STREAMING ||
      state.stage == ClassroomStage.SCORING ||
      state.isAutoTransitionPending
  }

  private fun dismissVisibleOverlayIfAny(): Boolean {
    if (KidsClassroomManager.currentState().pendingCelebration != null) {
      KidsClassroomManager.dismissCelebrationOnly()
      return true
    }
    if (::overlay.isInitialized && overlay.visibility == View.VISIBLE && overlay.childCount > 0) {
      overlay.removeAllViews()
      overlay.visibility = View.GONE
      toggleActionBarVisibility(true)
      return true
    }
    return false
  }

  private fun animateDockEntranceIfShowing(view: View, shouldShow: Boolean) {
    val wasVisible = view.visibility == View.VISIBLE
    view.visibility = if (shouldShow) View.VISIBLE else View.GONE
    if (shouldShow && !wasVisible && view.isAttachedToWindow) {
      val shiftPx = 10f * resources.displayMetrics.density
      view.animate().cancel()
      view.alpha = 0f
      view.translationY = shiftPx
      view.animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(200L)
        .setInterpolator(android.view.animation.PathInterpolator(0.23f, 1f, 0.32f, 1f))
        .start()
    } else if (!shouldShow) {
      view.animate().cancel()
      view.alpha = 1f
      view.translationY = 0f
    }
  }

  private fun syncMemorizationAndTasmeeBarsVisibility() {
    if (!::memorizationBar.isInitialized || !::tasmeeBottomRecordingBar.isInitialized) {
      return
    }
    val isTasmeeOn = LiveTasmeeManager.isEnabled()
    val isTasmeeActivelyListening = LiveTasmeeManager.isActivelyListening()
    val isKidsOn = KidsClassroomManager.isEnabled() && !isTasmeeOn
    val isActiveRecordingOrLesson = isTasmeeActivelyListening ||
      (isKidsOn && isKidsLoopActivelyRunning(KidsClassroomManager.currentState()))

    // Keep top memorization_bar hidden so the Mushaf page remains full-screen;
    // options are accessed via the 1-click subtle options reveal button or toolbar icon.
    memorizationBar.visibility = View.GONE
    animateDockEntranceIfShowing(tasmeeBottomRecordingBar, !showingTranslation && isTasmeeOn)
    if (::kidsClassroomOverlay.isInitialized) {
      animateDockEntranceIfShowing(kidsClassroomOverlay, !showingTranslation && isKidsOn)
    }
    subtleOptionsRevealBtn?.visibility =
      if (!showingTranslation && !isTasmeeOn && !isKidsOn) View.VISIBLE else View.GONE

    // Calmly soften top toolbar chrome during an active recording or learning session
    // so the Mushaf glyphs and bottom bar hold clean visual focus (still tappable).
    findViewById<View>(R.id.toolbar_area)?.let { topBar ->
      val targetAlpha = if (isActiveRecordingOrLesson && !showingTranslation) 0.68f else 1.0f
      if (kotlin.math.abs(topBar.alpha - targetAlpha) > 0.02f) {
        topBar.animate()
          .alpha(targetAlpha)
          .setDuration(if (isActiveRecordingOrLesson) 200L else 160L)
          .setInterpolator(android.view.animation.PathInterpolator(0.23f, 1f, 0.32f, 1f))
          .start()
      }
    }

    if (::onLiveTasmeeBackCallback.isInitialized) {
      onLiveTasmeeBackCallback.isEnabled = isTasmeeOn
    }
    if (::onKidsClassroomBackCallback.isInitialized) {
      onKidsClassroomBackCallback.isEnabled = KidsClassroomManager.isEnabled() || isKidsSessionMode
    }
  }

  private fun syncKidsAyahCelebrationOverlay(kidsState: KidsClassroomUiState) {
    if (!::overlay.isInitialized || isFinishing || isDestroyed) return
    val event = kidsState.pendingCelebration
    if (event == null || !kidsState.isEnabled) {
      activeCelebrationEventId = -1L
      if (overlay.childCount > 0 && overlay.getChildAt(0) is KidsAyahCelebrationOverlayView) {
        overlay.removeAllViews()
        overlay.visibility = View.GONE
      }
      return
    }
    if (event.eventId == activeCelebrationEventId) return
    activeCelebrationEventId = event.eventId

    val celebrationView = KidsAyahCelebrationOverlayView(
      context = this,
      event = event,
      autoContinueEnabled = kidsState.autoNextEnabled,
      onContinue = {
        KidsClassroomManager.dismissCelebrationAndContinue(this@PagerActivity)
        refreshQuranPages()
      },
      onDismiss = {
        KidsClassroomManager.dismissCelebrationOnly()
      },
      onOpenSurahAnalysis = {
        val completedSurah = event.suraAyah.sura
        KidsClassroomManager.dismissCelebrationOnly()
        showKidsSurahAnalysisSheet(completedSurah)
      },
      onOpenAchievements = {
        KidsClassroomManager.dismissCelebrationOnly()
        if (isKidsSessionMode) {
          returnToKidsProgressIslands(
            targetTab = com.quran.labs.androidquran.kidsclassroom.KidsModeTab.REWARDS
          )
        } else {
          com.quran.labs.androidquran.kidsclassroom.KidsModeActivity.start(
            this@PagerActivity,
            com.quran.labs.androidquran.kidsclassroom.KidsModeTab.REWARDS
          )
        }
      }
    )
    overlay.removeAllViews()
    overlay.addView(
      celebrationView,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    toggleActionBarVisibility(false)
  }

  private fun stopActiveRecitationRecording() {
    var changed = false
    if (LiveTasmeeManager.isEnabled()) {
      LiveTasmeeManager.stopTasmee()
      changed = true
    }
    val kidsState = KidsClassroomManager.currentState()
    if (kidsState.stage == ClassroomStage.QARI_RECITING ||
      kidsState.stage == ClassroomStage.KIDS_REPEATING ||
      kidsState.stage == ClassroomStage.CHILD_RECITING ||
      kidsState.stage == ClassroomStage.TASMEE_STREAMING ||
      kidsState.isAutoTransitionPending
    ) {
      KidsClassroomManager.stopActiveLoop()
      changed = true
    }
    syncMemorizationAndTasmeeBarsVisibility()
    if (changed && !isFinishing) {
      refreshQuranPages()
      supportInvalidateOptionsMenu()
    }
  }

  private fun ensureTajweedPageLoadedOrRefresh(wasTajweed: Boolean, targetPage: Int) {
    quranSettings.pageType = "tajweed"
    memorizationBar.isTajweedMushaf = true
    if (!wasTajweed) {
      val isTranslation = showingTranslation
      val restartIntent = Intent(this@PagerActivity, PagerActivity::class.java).apply {
        putExtra("page", targetPage)
        putExtra(EXTRA_JUMP_TO_TRANSLATION, isTranslation)
      }
      finish()
      overridePendingTransition(0, 0)
      startActivity(restartIntent)
      overridePendingTransition(0, 0)
    } else {
      refreshQuranPages()
      supportInvalidateOptionsMenu()
    }
  }

  private fun showAyahBookmarkSheet(suraAyah: SuraAyah) {
    val ayahBookmarkWrapper = AyahBookmarkWrapper(this, suraAyah) { isBookmarked ->
      overlay.removeAllViews()
      overlay.visibility = View.GONE
      toggleActionBarVisibility(true)
      updateAyahBookmark(suraAyah, isBookmarked)
    }
    overlay.removeAllViews()
    overlay.addView(
      ayahBookmarkWrapper,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    toggleActionBarVisibility(false)
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
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_memorize_range) {
        endAyahMode()
        showMemorizationRepetitionSheet(startSuraAyah, endSuraAyah)
        return true
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
          arabicDatabaseUtils
            .getVerses(start, end)
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

  companion object {
    private val PAGE_COLOR_PREFERENCES = setOf(
      Constants.PREF_PAGE_THEME,
      Constants.PREF_PAGE_APPEARANCE,
      Constants.PREF_APP_THEME,
      Constants.PREF_NIGHT_MODE_TEXT_BRIGHTNESS,
      Constants.PREF_NIGHT_MODE_BACKGROUND_BRIGHTNESS,
      Constants.PREF_USE_OLD_BACKGROUND
    )
    private const val AUDIO_DOWNLOAD_KEY = "AUDIO_DOWNLOAD_KEY"
    private const val LAST_READ_PAGE = "LAST_READ_PAGE"
    private const val LAST_READING_MODE_IS_TRANSLATION = "LAST_READING_MODE_IS_TRANSLATION"
    private const val LAST_ACTIONBAR_STATE = "LAST_ACTIONBAR_STATE"
    private const val LAST_FOLDING_STATE = "LAST_FOLDING_STATE"

    const val EXTRA_JUMP_TO_TRANSLATION: String = "jumpToTranslation"
    const val EXTRA_HIGHLIGHT_SURA: String = "highlightSura"
    const val EXTRA_HIGHLIGHT_AYAH: String = "highlightAyah"
    const val EXTRA_START_CHILDREN_LEARNING_MODE: String = "startChildrenLearningMode"
    const val EXTRA_KIDS_SESSION_MODE: String = "kidsSessionMode"
    const val EXTRA_START_KIDS_MODE_SURAH: String = "startKidsModeSurah"
    const val EXTRA_START_KIDS_MODE_AYAH: String = "startKidsModeAyah"
    const val EXTRA_START_KIDS_AUTO_MODE: String = "startKidsAutoMode"
    const val EXTRA_START_LIVE_TASMEE_MODE: String = "startLiveTasmeeMode"
    const val LAST_WAS_DUAL_PAGES: String = "wasDualPages"
    private const val LAST_KIDS_SESSION_MODE: String = "lastKidsSessionMode"

    private const val MIC_REQUESTER_NONE = 0
    private const val MIC_REQUESTER_KIDS_CLASSROOM = 1
    private const val MIC_REQUESTER_LIVE_TASMEE = 2

    private const val DEFAULT_HIDE_AFTER_TIME: Long = 2000

    const val MSG_HIDE_ACTIONBAR: Int = 1

    // AYAH ACTION PANEL STUFF
    // Max height of sliding panel (% of screen)
    private const val PANEL_MAX_HEIGHT = 0.6f
  }
}
