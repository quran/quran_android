package com.quran.labs.androidquran.ui;

import static com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter.AUDIO_PAGE;
import static com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter.TAG_PAGE;
import static com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter.TRANSLATION_PAGE;

import android.Manifest;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.SparseBooleanArray;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.NonRestoringViewPager;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;

import com.quran.data.core.QuranInfo;
import com.quran.data.model.SuraAyah;
import com.quran.data.model.selection.AyahSelection;
import com.quran.data.model.selection.AyahSelectionKt;
import com.quran.data.model.selection.SelectionIndicator;
import com.quran.data.model.selection.SelectionIndicatorKt;
import com.quran.labs.androidquran.BuildConfig;
import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.SearchActivity;
import com.quran.labs.androidquran.bridge.AudioStatusRepositoryBridge;
import com.quran.labs.androidquran.bridge.ReadingEventPresenterBridge;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.common.audio.model.QariItem;
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest;
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranDisplayData;
import com.quran.labs.androidquran.di.component.activity.PagerActivityComponent;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils;
import com.quran.labs.androidquran.presenter.audio.AudioPresenter;
import com.quran.labs.androidquran.presenter.bookmark.RecentPagePresenter;
import com.quran.labs.androidquran.presenter.data.QuranEventLogger;
import com.quran.labs.androidquran.presenter.recitation.PagerActivityRecitationPresenter;
import com.quran.labs.androidquran.presenter.translationlist.TranslationListPresenter;
import com.quran.labs.androidquran.service.AudioService;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.PermissionUtil;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.fragment.AddTagDialog;
import com.quran.labs.androidquran.ui.fragment.JumpFragment;
import com.quran.labs.androidquran.ui.fragment.TabletFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;
import com.quran.labs.androidquran.ui.fragment.TranslationFragment;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.JumpDestination;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPage;
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter;
import com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter;
import com.quran.labs.androidquran.ui.util.ToastCompat;
import com.quran.labs.androidquran.ui.util.TranslationsSpinnerAdapter;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.QuranAppUtils;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.ShareUtil;
import com.quran.labs.androidquran.view.AudioStatusBar;
import com.quran.labs.androidquran.view.CurrentQariBridge;
import com.quran.labs.androidquran.view.IconPageIndicator;
import com.quran.labs.androidquran.view.QuranSpinner;
import com.quran.labs.androidquran.view.SlidingUpPanelLayout;
import com.quran.mobile.di.AyahActionFragmentProvider;
import com.quran.mobile.di.QuranReadingActivityComponent;
import com.quran.mobile.di.QuranReadingActivityComponentProvider;
import com.quran.mobile.di.QuranReadingPageComponent;
import com.quran.mobile.di.QuranReadingPageComponentProvider;
import com.quran.mobile.feature.qarilist.QariListWrapper;
import com.quran.mobile.feature.qarilist.di.QariListWrapperInjector;
import com.quran.mobile.translation.model.LocalTranslation;
import com.quran.page.common.factory.PageViewFactoryProvider;
import com.quran.page.common.toolbar.AyahToolBar;
import com.quran.page.common.toolbar.di.AyahToolBarInjector;
import com.quran.reading.common.ReadingEventPresenter;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.observers.DisposableObserver;
import io.reactivex.rxjava3.observers.DisposableSingleObserver;
import kotlinx.coroutines.Job;
import timber.log.Timber;

/**
 * Activity that displays the Quran (in Arabic or translation mode).
 * <p>
 * Essentially, this activity consists of a {@link ViewPager} of Quran pages (using {@link
 * QuranPageAdapter}). {@link AudioService} is used to handle playing audio, and this is synced with
 * the display of the Quran.
 */
public class PagerActivity extends AppCompatActivity implements
    AudioStatusBar.AudioBarListener,
    DefaultDownloadReceiver.DownloadListener,
    TagBookmarkDialog.OnBookmarkTagsUpdateListener,
    AyahSelectedListener,
    JumpDestination,
    QuranReadingActivityComponentProvider,
    QuranReadingPageComponentProvider,
    AyahToolBarInjector,
    QariListWrapperInjector,
    ActivityCompat.OnRequestPermissionsResultCallback {
  private static final String AUDIO_DOWNLOAD_KEY = "AUDIO_DOWNLOAD_KEY";
  private static final String LAST_READ_PAGE = "LAST_READ_PAGE";
  private static final String LAST_READING_MODE_IS_TRANSLATION =
      "LAST_READING_MODE_IS_TRANSLATION";
  private static final String LAST_ACTIONBAR_STATE = "LAST_ACTIONBAR_STATE";
  private static final String LAST_AUDIO_REQUEST = "LAST_AUDIO_REQUEST";

  public static final String EXTRA_JUMP_TO_TRANSLATION = "jumpToTranslation";
  public static final String EXTRA_HIGHLIGHT_SURA = "highlightSura";
  public static final String EXTRA_HIGHLIGHT_AYAH = "highlightAyah";
  public static final String LAST_WAS_DUAL_PAGES = "wasDualPages";

  private static final long DEFAULT_HIDE_AFTER_TIME = 2000;

  private long lastPopupTime = 0;
  private boolean isActionBarHidden = true;
  private AudioStatusBar audioStatusBar = null;
  private ViewPager viewPager = null;
  private QuranPageAdapter pagerAdapter = null;
  private boolean shouldReconnect = false;
  private SparseBooleanArray bookmarksCache = null;
  private boolean showingTranslation = false;
  private DefaultDownloadReceiver downloadReceiver;
  private boolean needsPermissionToDownloadOver3g = true;
  private AlertDialog promptDialog = null;
  private AyahToolBar ayahToolBar;
  private boolean isDualPages = false;
  private View toolBarArea;
  private FrameLayout overlay;
  private boolean promptedForExtraDownload;
  private QuranSpinner translationsSpinner;
  private ProgressDialog progressDialog;
  private ViewGroup.MarginLayoutParams audioBarParams;
  private boolean isInMultiWindowMode;

  private MenuItem bookmarksMenuItem;

  private String[] translationNames;
  private List<LocalTranslation> translations;
  private Set<String> activeTranslationsFilesNames;
  private TranslationsSpinnerAdapter translationsSpinnerAdapter;

  public static final int MSG_HIDE_ACTIONBAR = 1;

  // AYAH ACTION PANEL STUFF
  // Max height of sliding panel (% of screen)
  private static final float PANEL_MAX_HEIGHT = 0.6f;
  private SlidingUpPanelLayout slidingPanel;
  private ViewPager slidingPager;
  private SlidingPagerAdapter slidingPagerAdapter;
  private ActivityResultLauncher<String> requestPermissionLauncher;

  private int defaultNavigationBarColor;
  private boolean isSplitScreen = false;

  @Nullable private QuranAyahInfo lastSelectedTranslationAyah;
  @Nullable private LocalTranslation[] lastActivatedLocalTranslations;

  private PagerActivityComponent pagerActivityComponent;

  @Inject BookmarkModel bookmarkModel;
  @Inject RecentPagePresenter recentPagePresenter;
  @Inject QuranSettings quranSettings;
  @Inject QuranScreenInfo quranScreenInfo;
  @Inject ArabicDatabaseUtils arabicDatabaseUtils;
  @Inject QuranAppUtils quranAppUtils;
  @Inject ShareUtil shareUtil;
  @Inject AudioUtils audioUtils;
  @Inject QuranDisplayData quranDisplayData;
  @Inject QuranInfo quranInfo;
  @Inject QuranFileUtils quranFileUtils;
  @Inject AudioPresenter audioPresenter;
  @Inject CurrentQariBridge currentQariBridge;
  @Inject QuranEventLogger quranEventLogger;
  @Inject AudioStatusRepository audioStatusRepository;
  @Inject ReadingEventPresenter readingEventPresenter;
  @Inject PageViewFactoryProvider pageProviderFactoryProvider;
  @Inject Set<AyahActionFragmentProvider> additionalAyahPanels;
  @Inject PagerActivityRecitationPresenter pagerActivityRecitationPresenter;
  @Inject TranslationListPresenter translationListPresenter;

  private AudioStatusRepositoryBridge audioStatusRepositoryBridge;
  private ReadingEventPresenterBridge readingEventPresenterBridge;

  private Job translationJob;
  private CompositeDisposable compositeDisposable;
  private final CompositeDisposable foregroundDisposable = new CompositeDisposable();

  private final PagerHandler handler = new PagerHandler(this);

  private static class PagerHandler extends Handler {
    private final WeakReference<PagerActivity> activity;

    PagerHandler(PagerActivity activity) {
      this.activity = new WeakReference<>(activity);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
      PagerActivity activity = this.activity.get();
      if (activity != null) {
        if (msg.what == MSG_HIDE_ACTIONBAR) {
          activity.toggleActionBarVisibility(false);
        } else {
          super.handleMessage(msg);
        }
      }
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    QuranApplication quranApp = (QuranApplication) getApplication();
    quranApp.refreshLocale(this, false);
    super.onCreate(savedInstanceState);

    // field injection
    getPagerActivityComponent().inject(this);

    bookmarksCache = new SparseBooleanArray();

    boolean shouldAdjustPageNumber = false;
    isDualPages = QuranUtils.isDualPages(this, quranScreenInfo);
    isSplitScreen = quranSettings.isQuranSplitWithTranslation();
    audioStatusRepositoryBridge = new AudioStatusRepositoryBridge(
        audioStatusRepository,
        () -> audioStatusBar,
        suraAyah -> { onAudioPlaybackAyahChanged(suraAyah); return null; }
    );
    readingEventPresenterBridge = new ReadingEventPresenterBridge(
        readingEventPresenter,
        () -> { onPageClicked(); return null; },
        ayahSelection -> { onAyahSelectionChanged(ayahSelection); return null; }
    );

    // remove the window background to avoid overdraw. note that, per Romain's blog, this is
    // acceptable (as long as we don't set the background color to null in the theme, since
    // that is used to generate preview windows).
    getWindow().setBackgroundDrawable(null);

    int page = -1;
    isActionBarHidden = true;
    if (savedInstanceState != null) {
      Timber.d("non-null saved instance state!");
      page = savedInstanceState.getInt(LAST_READ_PAGE, -1);
      showingTranslation = savedInstanceState
          .getBoolean(LAST_READING_MODE_IS_TRANSLATION, false);
      if (savedInstanceState.containsKey(LAST_ACTIONBAR_STATE)) {
        isActionBarHidden = !savedInstanceState.getBoolean(LAST_ACTIONBAR_STATE);
      }
      boolean lastWasDualPages = savedInstanceState.getBoolean(LAST_WAS_DUAL_PAGES, isDualPages);
      shouldAdjustPageNumber = (lastWasDualPages != isDualPages);
    } else {
      Intent intent = getIntent();
      Bundle extras = intent.getExtras();
      if (extras != null) {
        page = extras.getInt("page", Constants.PAGES_FIRST);
        showingTranslation = extras.getBoolean(EXTRA_JUMP_TO_TRANSLATION, showingTranslation);
        final int highlightedSura = extras.getInt(EXTRA_HIGHLIGHT_SURA, -1);
        final int highlightedAyah = extras.getInt(EXTRA_HIGHLIGHT_AYAH, -1);

        if (highlightedSura > -1 && highlightedAyah > -1) {
          readingEventPresenterBridge.setSelection(highlightedSura, highlightedAyah, true);
        }
      }
    }

    compositeDisposable = new CompositeDisposable();

    setContentView(R.layout.quran_page_activity_slider);
    audioStatusBar = findViewById(R.id.audio_area);
    audioStatusBar.setCurrentQariBridge(currentQariBridge);
    audioStatusBar.setIsDualPageMode(quranScreenInfo.isDualPageMode());
    audioStatusBar.setAudioBarListener(this);
    audioBarParams = (ViewGroup.MarginLayoutParams) audioStatusBar.getLayoutParams();

    toolBarArea = findViewById(R.id.toolbar_area);
    translationsSpinner = findViewById(R.id.spinner);
    overlay = findViewById(R.id.overlay);

    // this is the colored view behind the status bar on kitkat and above
    final View statusBarBackground = findViewById(R.id.status_bg);
    statusBarBackground.getLayoutParams().height = getStatusBarHeight();

    final Toolbar toolbar = findViewById(R.id.toolbar);
    if (quranSettings.isArabicNames() || QuranUtils.isRtl()) {
      // remove when we remove LTR from quran_page_activity's root
      ViewCompat.setLayoutDirection(toolbar, ViewCompat.LAYOUT_DIRECTION_RTL);
    }
    setSupportActionBar(toolbar);

    final ActionBar ab = getSupportActionBar();
    if (ab != null) {
      ab.setDisplayShowHomeEnabled(true);
      ab.setDisplayHomeAsUpEnabled(true);
    }

    initAyahActionPanel();

    if (showingTranslation && translationNames != null) {
      updateActionBarSpinner();
    } else {
      updateActionBarTitle(page);
    }

    lastPopupTime = System.currentTimeMillis();
    pagerAdapter = new QuranPageAdapter(
        getSupportFragmentManager(),
        isDualPages,
        showingTranslation,
        quranInfo,
        isSplitScreen,
        pageProviderFactoryProvider.providePageViewFactory(quranSettings.getPageType())
    );
    ayahToolBar = findViewById(R.id.ayah_toolbar);
    ayahToolBar.setFlavor(BuildConfig.FLAVOR);
    ayahToolBar.setLongPressLambda(charSequence -> {
      ToastCompat.makeText(PagerActivity.this, charSequence, Toast.LENGTH_SHORT).show();
      return null;
    });

    final NonRestoringViewPager nonRestoringViewPager = findViewById(R.id.quran_pager);
    nonRestoringViewPager.setIsDualPagesInLandscape(
        QuranUtils.isDualPagesInLandscape(this, quranScreenInfo));

    viewPager = nonRestoringViewPager;
    viewPager.setAdapter(pagerAdapter);

    ayahToolBar.setOnItemSelectedListener(new AyahMenuItemSelectionHandler());
    OnPageChangeListener onPageChangeListener = new OnPageChangeListener() {

      @Override
      public void onPageScrollStateChanged(int state) {
      }

      @Override
      public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        final AyahSelection currentSelection = readingEventPresenter.currentAyahSelection();
        final SelectionIndicator selectionIndicator =
            AyahSelectionKt.selectionIndicator(currentSelection);
        final SuraAyah suraAyah = AyahSelectionKt.startSuraAyah(currentSelection);
        if (selectionIndicator != SelectionIndicator.None.INSTANCE &&
            selectionIndicator != SelectionIndicator.ScrollOnly.INSTANCE &&
            suraAyah != null) {
          final int startPage = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah);
          int barPos = quranInfo.getPositionFromPage(startPage, isDualPageVisible());
          if (position == barPos) {
            // Swiping to next ViewPager page (i.e. prev quran page)
            final SelectionIndicator updatedSelectionIndicator =
                SelectionIndicatorKt.withXScroll(selectionIndicator, -positionOffsetPixels);
            readingEventPresenterBridge.withSelectionIndicator(updatedSelectionIndicator);
          } else if (position == barPos - 1 || position == barPos + 1) {
            // Swiping to previous or next ViewPager page (i.e. next or previous quran page)
            final SelectionIndicator updatedSelectionIndicator =
                SelectionIndicatorKt.withXScroll(selectionIndicator, viewPager.getWidth() - positionOffsetPixels);
            readingEventPresenterBridge.withSelectionIndicator(updatedSelectionIndicator);
          } else {
            readingEventPresenterBridge.clearSelectedAyah();
          }
        }
      }

      @Override
      public void onPageSelected(int position) {
        Timber.d("onPageSelected(): %d", position);
        final int page = quranInfo.getPageFromPosition(position, isDualPageVisible());

        if (quranSettings.shouldDisplayMarkerPopup()) {
          lastPopupTime = QuranDisplayHelper.displayMarkerPopup(
              PagerActivity.this, quranInfo, page, lastPopupTime);
          if (isDualPages) {
            lastPopupTime = QuranDisplayHelper.displayMarkerPopup(
                PagerActivity.this, quranInfo, page - 1, lastPopupTime);
          }
        }

        if (!showingTranslation) {
          updateActionBarTitle(page);
        } else {
          refreshActionBarSpinner();
        }

        if (bookmarksCache.indexOfKey(page) < 0) {
          if (isDualPageVisible() && bookmarksCache.indexOfKey(page - 1) < 0) {
            checkIfPageIsBookmarked(page - 1, page);
          } else {
            // we don't have the key
            checkIfPageIsBookmarked(page);
          }
        } else {
          refreshBookmarksMenu();
        }

        // If we're more than 1 page away from ayah selection end ayah mode
        final SuraAyah suraAyah = getSelectionStart();
        if (suraAyah != null) {
          final int startPage = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah);
          int ayahPos = quranInfo.getPositionFromPage(startPage, isDualPageVisible());
          if (Math.abs(ayahPos - position) > 1) {
            endAyahMode();
          }
        }
      }
    };
    viewPager.addOnPageChangeListener(onPageChangeListener);

    setUiVisibilityListener();
    audioStatusBar.setVisibility(View.VISIBLE);
    toggleActionBarVisibility(true);

    if (shouldAdjustPageNumber) {
      // when going from two page per screen to one or vice versa, we adjust the page number,
      // such that the first page is always selected.
      final int curPage;
      if (isDualPageVisible()) {
        curPage = quranInfo.mapSinglePageToDualPage(page);
      } else {
        curPage = quranInfo.mapDualPageToSinglePage(page);
      }
      page = curPage;
    }

    final int pageIndex = quranInfo.getPositionFromPage(page, isDualPageVisible());
    viewPager.setCurrentItem(pageIndex);
    if (page == 0) {
      onPageChangeListener.onPageSelected(0);
    }

    // just got created, need to reconnect to service
    shouldReconnect = true;

    // enforce orientation lock
    if (quranSettings.isLockOrientation()) {
      int current = getResources().getConfiguration().orientation;
      if (quranSettings.isLandscapeOrientation()) {
        if (current == Configuration.ORIENTATION_PORTRAIT) {
          setRequestedOrientation(
              ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
          return;
        }
      } else if (current == Configuration.ORIENTATION_LANDSCAPE) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        return;
      }
    }

    downloadReceiver = new DefaultDownloadReceiver(this,
        QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
    String action = QuranDownloadNotifier.ProgressIntent.INTENT_NAME;
    LocalBroadcastManager.getInstance(this).registerReceiver(
        downloadReceiver,
        new IntentFilter(action));
    downloadReceiver.setListener(this);

    defaultNavigationBarColor = getWindow().getNavigationBarColor();

    quranEventLogger.logAnalytics(isDualPages, showingTranslation, isSplitScreen);

    // Setup recitation (if enabled)
    pagerActivityRecitationPresenter.bind(this, new PagerActivityRecitationPresenter.Bridge(
        this::isDualPageVisible,
        this::getCurrentPage,
        () -> audioStatusBar,
        () -> ayahToolBar,
        ayah -> { ensurePage(ayah.sura, ayah.ayah); return null; },
        sliderPage -> { showSlider(slidingPagerAdapter.getPagePosition(sliderPage)); return null; }
    ));

    requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
          audioPresenter.onPostNotificationsPermissionResponse(isGranted);
        });

    // read the list of translations
    requestTranslationsList();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    pagerActivityRecitationPresenter.onPermissionsResult(requestCode, grantResults);
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  private boolean isDualPageVisible() {
    return isDualPages && !(isSplitScreen && showingTranslation);
  }

  private boolean shouldUpdatePageNumber() {
    return isDualPages && isSplitScreen;
  }

  public Observable<Integer> getViewPagerObservable() {
    return Observable.create(e -> {
      final OnPageChangeListener pageChangedListener =
          new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
              final int page = quranInfo.getPageFromPosition(position, isDualPageVisible());
              e.onNext(page);
            }
          };

      viewPager.addOnPageChangeListener(pageChangedListener);
      e.onNext(getCurrentPage());

      e.setCancellable(() -> viewPager.removeOnPageChangeListener(pageChangedListener));
    });
  }

  private int getStatusBarHeight() {
    // thanks to https://github.com/jgilfelt/SystemBarTint for this
    final Resources resources = getResources();
    final int resId = resources.getIdentifier(
        "status_bar_height", "dimen", "android");
    if (resId > 0) {
      return resources.getDimensionPixelSize(resId);
    }
    return 0;
  }

  private void initAyahActionPanel() {
    slidingPanel = findViewById(R.id.sliding_panel);
    final ViewGroup slidingLayout =
        slidingPanel.findViewById(R.id.sliding_layout);
    slidingPager = slidingPanel
        .findViewById(R.id.sliding_layout_pager);
    final IconPageIndicator slidingPageIndicator =
        slidingPanel
            .findViewById(R.id.sliding_pager_indicator);

    // Find close button and set listener
    final View closeButton = slidingPanel
        .findViewById(R.id.sliding_menu_close);
    closeButton.setOnClickListener(v -> endAyahMode());

    // Create and set fragment pager adapter
    slidingPagerAdapter = new SlidingPagerAdapter(getSupportFragmentManager(),
        quranSettings.isArabicNames() || QuranUtils.isRtl(),
        additionalAyahPanels);
    slidingPager.setAdapter(slidingPagerAdapter);

    // Attach the view pager to the action bar
    slidingPageIndicator.setViewPager(slidingPager);

    // Set sliding layout parameters
    int displayHeight = getResources().getDisplayMetrics().heightPixels;
    slidingLayout.getLayoutParams().height =
        (int) (displayHeight * PANEL_MAX_HEIGHT);
    slidingPanel.setEnableDragViewTouchEvents(true);
    slidingPanel.setPanelSlideListener(new SlidingPanelListener());
    slidingLayout.setVisibility(View.GONE);

    // When clicking any menu items, expand the panel
    slidingPageIndicator.setOnClickListener(v -> {
      if (!slidingPanel.isExpanded()) {
        slidingPanel.expandPane();
      }
    });
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      handler.sendEmptyMessageDelayed(MSG_HIDE_ACTIONBAR, DEFAULT_HIDE_AFTER_TIME);
    } else {
      handler.removeMessages(MSG_HIDE_ACTIONBAR);
    }
  }

  public void onPageClicked() {
    toggleActionBar();
  }

  private void onAudioPlaybackAyahChanged(@Nullable SuraAyah suraAyah) {
    if (suraAyah != null) {
      // continue to snap back to the page when the playback ayah changes
      ensurePage(suraAyah.sura, suraAyah.ayah);
    }
  }

  private void onAyahSelectionChanged(AyahSelection ayahSelection) {
    final boolean haveSelection = ayahSelection != AyahSelection.None.INSTANCE;
    final SelectionIndicator currentSelection = AyahSelectionKt.selectionIndicator(ayahSelection);
    if (currentSelection instanceof SelectionIndicator.None && haveSelection) {
      viewPager.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    if (haveSelection) {
      final SuraAyah startPosition = startPosition(ayahSelection);
      updateLocalTranslations(startPosition);
    } else {
      endAyahMode();
    }
  }

  private SuraAyah startPosition(AyahSelection ayahSelection) {
    if (ayahSelection instanceof AyahSelection.Ayah) {
      return ((AyahSelection.Ayah) ayahSelection).getSuraAyah();
    } else if (ayahSelection instanceof AyahSelection.AyahRange) {
      return ((AyahSelection.AyahRange) ayahSelection).getStartSuraAyah();
    } else {
      return null;
    }
  }

  private void setUiVisibility(boolean isVisible) {
    setUiVisibilityKitKat(isVisible);
    if (isInMultiWindowMode) {
      animateToolBar(isVisible);
    }
  }

  private void setUiVisibilityKitKat(boolean isVisible) {
    int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    if (!isVisible) {
      flags |= View.SYSTEM_UI_FLAG_LOW_PROFILE
          | View.SYSTEM_UI_FLAG_FULLSCREEN
          | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          | View.SYSTEM_UI_FLAG_IMMERSIVE;
    }
    viewPager.setSystemUiVisibility(flags);
  }

  private void setUiVisibilityListener() {
    viewPager.setOnSystemUiVisibilityChangeListener(
        flags -> {
          boolean visible = (flags & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;
          animateToolBar(visible);
        });
  }

  private void clearUiVisibilityListener() {
    viewPager.setOnSystemUiVisibilityChangeListener(null);
  }

  private void animateToolBar(boolean visible) {
    isActionBarHidden = !visible;

    // animate toolbar
    toolBarArea.animate()
        .translationY(visible ? 0 : -toolBarArea.getHeight())
        .setDuration(250)
        .start();

    /* the bottom margin on the audio bar is not part of its height, and so we have to
     * take it into account when animating the audio bar off the screen. */
    final int bottomMargin = audioBarParams.bottomMargin;

    // and audio bar
    audioStatusBar.animate()
        .translationY(visible ? 0 : audioStatusBar.getHeight() + bottomMargin)
        .setDuration(250)
        .start();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    boolean navigate = audioStatusBar.getCurrentMode() !=
        AudioStatusBar.PLAYING_MODE
        && quranSettings.navigateWithVolumeKeys();
    if (navigate && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
      viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
      return true;
    } else if (navigate && keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
    return ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
        keyCode == KeyEvent.KEYCODE_VOLUME_UP) &&
        audioStatusBar.getCurrentMode() !=
            AudioStatusBar.PLAYING_MODE &&
        PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(Constants.PREF_USE_VOLUME_KEY_NAV, false))
        || super.onKeyUp(keyCode, event);
  }

  @Override
  public void onResume() {
    super.onResume();

    audioPresenter.bind(this);
    recentPagePresenter.bind(this);
    isInMultiWindowMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode();

    if (shouldReconnect) {
      foregroundDisposable.add(Completable.timer(500, TimeUnit.MILLISECONDS)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(() -> {
            try {
              startService(
                  audioUtils.getAudioIntent(PagerActivity.this, AudioService.ACTION_CONNECT));
            } catch (IllegalStateException ise) {
              // we're likely in the background, so ignore.
            }
            shouldReconnect = false;
          }));
    }

    updateNavigationBar(quranSettings.isNightMode());
  }

  private void updateNavigationBar(boolean isNightMode) {
    final int color =
        isNightMode ? ContextCompat.getColor(this, R.color.navbar_night_color) :
            defaultNavigationBarColor;
    getWindow().setNavigationBarColor(color);
  }

  @NonNull
  public PagerActivityComponent getPagerActivityComponent() {
    // a fragment may call this before Activity's onCreate, so cache and reuse.
    if (pagerActivityComponent == null) {
      pagerActivityComponent = ((QuranApplication) getApplication())
          .getApplicationComponent()
          .pagerActivityComponentFactory()
          .generate(this, this);
    }
    return pagerActivityComponent;
  }

  @Override
  public void injectQariListWrapper(@NonNull QariListWrapper qariListWrapper) {
    getPagerActivityComponent().inject(qariListWrapper);
  }

  @NonNull
  @Override
  public QuranReadingActivityComponent provideQuranReadingActivityComponent() {
    return getPagerActivityComponent();
  }

  @NonNull
  @Override
  public QuranReadingPageComponent provideQuranReadingPageComponent(@NonNull int... pages) {
    return getPagerActivityComponent()
        .quranPageComponentFactory()
        .generate(pages);
  }

  @Override
  public void injectToolBar(@NonNull AyahToolBar ayahToolBar) {
    getPagerActivityComponent()
        .inject(ayahToolBar);
  }

  public void showGetRequiredFilesDialog() {
    if (promptDialog != null) {
      return;
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(R.string.download_extra_data)
        .setPositiveButton(R.string.downloadPrompt_ok,
            (dialog, option) -> {
              downloadRequiredFiles();
              dialog.dismiss();
              promptDialog = null;
            })
        .setNegativeButton(R.string.downloadPrompt_no,
            (dialog, option) -> {
              dialog.dismiss();
              promptDialog = null;
            });
    promptDialog = builder.create();
    promptDialog.show();
  }

  private void downloadRequiredFiles() {
    int downloadType = QuranDownloadService.DOWNLOAD_TYPE_AUDIO;
    if (audioStatusBar.getCurrentMode() == AudioStatusBar.STOPPED_MODE) {
      // if we're stopped, use audio download bar as our progress bar
      audioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);
      if (isActionBarHidden) {
        toggleActionBar();
      }
    } else {
      // if audio is playing, let's not disrupt it - do this using a
      // different type so the broadcast receiver ignores it.
      downloadType = QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB;
    }

    boolean haveDownload = false;
    if (!quranFileUtils.haveAyaPositionFile(this)) {
      String url = quranFileUtils.getAyaPositionFileUrl();
      if (isDualPages) {
        url = quranFileUtils.getAyaPositionFileUrl(
            quranScreenInfo.getTabletWidthParam());
      }
      String destination = quranFileUtils.getQuranAyahDatabaseDirectory(this);
      // start the download
      String notificationTitle = getString(R.string.highlighting_database);
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
          destination, notificationTitle, AUDIO_DOWNLOAD_KEY,
          downloadType);
      Timber.d("starting service to download ayah position file");
      startService(intent);

      haveDownload = true;
    }

    if (!quranFileUtils.hasArabicSearchDatabase()) {
      String url = quranFileUtils.getArabicSearchDatabaseUrl();

      // show "downloading required files" unless we already showed that for
      // highlighting database, in which case show "downloading search data"
      String notificationTitle = getString(R.string.highlighting_database);
      if (haveDownload) {
        notificationTitle = getString(R.string.search_data);
      }

      final String extension = url.endsWith(".zip") ? ".zip" : "";
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
          quranFileUtils.getQuranDatabaseDirectory(this), notificationTitle,
          AUDIO_DOWNLOAD_KEY, downloadType);
      intent.putExtra(QuranDownloadService.EXTRA_OUTPUT_FILE_NAME,
          QuranDataProvider.QURAN_ARABIC_DATABASE + extension);
      Timber.d("starting service to download arabic database");
      startService(intent);
    }

    if (downloadType != QuranDownloadService.DOWNLOAD_TYPE_AUDIO) {
      // if audio is playing, just show a status notification
      ToastCompat.makeText(this, R.string.downloading_title,
          Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (intent == null) {
      return;
    }

    recentPagePresenter.onJump();
    Bundle extras = intent.getExtras();
    if (extras != null) {
      int page = extras.getInt("page", Constants.PAGES_FIRST);

      boolean currentValue = showingTranslation;
      showingTranslation = extras.getBoolean(EXTRA_JUMP_TO_TRANSLATION, showingTranslation);
      final int highlightedSura = extras.getInt(EXTRA_HIGHLIGHT_SURA, -1);
      final int highlightedAyah = extras.getInt(EXTRA_HIGHLIGHT_AYAH, -1);
      if (highlightedSura > 0 && highlightedAyah > 0) {
        readingEventPresenterBridge.setSelection(highlightedSura, highlightedAyah, true);
      }

      if (showingTranslation != currentValue) {
        if (showingTranslation) {
          pagerAdapter.setTranslationMode();
          updateActionBarSpinner();
        } else {
          pagerAdapter.setQuranMode();
          updateActionBarTitle(page);
        }

        supportInvalidateOptionsMenu();
      }

      if (highlightedAyah > 0 && highlightedSura > 0) {
        // this will jump to the right page automagically
        ensurePage(highlightedSura, highlightedAyah);
      } else {
        final int pagePosition = quranInfo.getPositionFromPage(page, isDualPageVisible());
        viewPager.setCurrentItem(pagePosition);
      }

      setIntent(intent);
    }
  }

  @Override
  public void jumpTo(int page) {
    Intent i = new Intent(this, PagerActivity.class);
    i.putExtra("page", page);
    onNewIntent(i);
  }

  @Override
  public void jumpToAndHighlight(int page, int sura, int ayah) {
    Intent i = new Intent(this, PagerActivity.class);
    i.putExtra("page", page);
    i.putExtra(EXTRA_HIGHLIGHT_SURA, sura);
    i.putExtra(EXTRA_HIGHLIGHT_AYAH, ayah);
    onNewIntent(i);
  }

  @Override
  public void onPause() {
    foregroundDisposable.clear();
    if (promptDialog != null) {
      promptDialog.dismiss();
      promptDialog = null;
    }
    recentPagePresenter.unbind(this);
    quranSettings.setWasShowingTranslation(pagerAdapter.isShowingTranslation());

    super.onPause();
  }

  @Override
  protected void onStop() {
    // the activity will be paused when requesting notification
    // permissions, which will otherwise break audio presenter.
    audioPresenter.unbind(this);
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    Timber.d("onDestroy()");
    clearUiVisibilityListener();

    // remove broadcast receivers
    if (downloadReceiver != null) {
      downloadReceiver.setListener(null);
      LocalBroadcastManager.getInstance(this)
          .unregisterReceiver(downloadReceiver);
      downloadReceiver = null;
    }

    if (translationJob != null) {
      translationJob.cancel(new CancellationException());
    }
    currentQariBridge.unsubscribeAll();
    compositeDisposable.dispose();
    audioStatusRepositoryBridge.dispose();
    readingEventPresenterBridge.dispose();
    handler.removeCallbacksAndMessages(null);
    dismissProgressDialog();
    super.onDestroy();
  }

  private void onSessionEnd() {
    pagerActivityRecitationPresenter.onSessionEnd();
  }

  @Override
  public void onSaveInstanceState(Bundle state) {
    int lastPage = quranInfo.getPageFromPosition(viewPager.getCurrentItem(), isDualPageVisible());
    state.putInt(LAST_READ_PAGE, lastPage);
    state.putBoolean(LAST_READING_MODE_IS_TRANSLATION, showingTranslation);
    state.putBoolean(LAST_ACTIONBAR_STATE, isActionBarHidden);
    state.putBoolean(LAST_WAS_DUAL_PAGES, isDualPages);
    super.onSaveInstanceState(state);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.quran_menu, menu);
    final MenuItem item = menu.findItem(R.id.search);
    final SearchView searchView = (SearchView) item.getActionView();
    final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
    searchView.setQueryHint(getString(R.string.search_hint));
    searchView.setSearchableInfo(searchManager.getSearchableInfo(
        new ComponentName(this, SearchActivity.class)));

    // cache because invalidateOptionsMenu in a toolbar world always calls both
    // onCreateOptionsMenu and onPrepareOptionsMenu, which can be expensive both
    // due to inflation plus due to the search view specific setup work. we can
    // directly modify the bookmark item using a reference to this instead.
    bookmarksMenuItem = menu.findItem(R.id.favorite_item);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    MenuItem item = bookmarksMenuItem;
    if (item != null) {
      refreshBookmarksMenu();
    }

    MenuItem quran = menu.findItem(R.id.goto_quran);
    MenuItem translation = menu.findItem(R.id.goto_translation);
    if (quran != null && translation != null) {
      if (!showingTranslation) {
        quran.setVisible(false);
        translation.setVisible(true);
      } else {
        quran.setVisible(true);
        translation.setVisible(false);
      }
    }

    MenuItem nightMode = menu.findItem(R.id.night_mode);
    if (nightMode != null) {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      final boolean isNightMode = prefs.getBoolean(Constants.PREF_NIGHT_MODE, false);
      nightMode.setChecked(isNightMode);
      nightMode.setIcon(isNightMode ? R.drawable.ic_night_mode : R.drawable.ic_day_mode);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    final int itemId = item.getItemId();
    if (itemId == R.id.favorite_item) {
      int page = getCurrentPage();
      toggleBookmark(null, null, page);
      return true;
    } else if (itemId == R.id.goto_quran) {
      switchToQuran();
      return true;
    } else if (itemId == R.id.goto_translation) {
      if (translations != null) {
        quranEventLogger.switchToTranslationMode(translations.size());
        switchToTranslation();
      }
      return true;
    } else if (itemId == R.id.night_mode) {
      SharedPreferences prefs = PreferenceManager
          .getDefaultSharedPreferences(this);
      SharedPreferences.Editor prefsEditor = prefs.edit();
      final boolean isNightMode = !item.isChecked();
      prefsEditor.putBoolean(Constants.PREF_NIGHT_MODE, isNightMode).apply();
      item.setIcon(isNightMode ? R.drawable.ic_night_mode : R.drawable.ic_day_mode);
      item.setChecked(isNightMode);
      refreshQuranPages();
      updateNavigationBar(isNightMode);
      return true;
    } else if (itemId == R.id.settings) {
      Intent i = new Intent(this, QuranPreferenceActivity.class);
      startActivity(i);
      return true;
    } else if (itemId == R.id.help) {
      Intent i = new Intent(this, HelpActivity.class);
      startActivity(i);
      return true;
    } else if (itemId == android.R.id.home) {
      onSessionEnd();
      finish();
      return true;
    } else if (itemId == R.id.jump) {
      FragmentManager fm = getSupportFragmentManager();
      JumpFragment jumpDialog = new JumpFragment();
      jumpDialog.show(fm, JumpFragment.TAG);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void refreshQuranPages() {
    int pos = viewPager.getCurrentItem();
    int start = (pos == 0) ? pos : pos - 1;
    int end = (pos == pagerAdapter.getCount() - 1) ? pos : pos + 1;
    for (int i = start; i <= end; i++) {
      Fragment f = pagerAdapter.getFragmentIfExists(i);
      if (f instanceof QuranPage) {
        ((QuranPage) f).updateView();
      }
    }
  }

  @Override
  public boolean onSearchRequested() {
    return super.onSearchRequested();
  }

  private void switchToQuran() {
    if (getSelectionStart() != null) {
      endAyahMode();
    }
    final int page = getCurrentPage();
    pagerAdapter.setQuranMode();
    showingTranslation = false;
    if (shouldUpdatePageNumber()) {
      final int position = quranInfo.getPositionFromPage(page, true);
      viewPager.setCurrentItem(position);
    }

    supportInvalidateOptionsMenu();
    updateActionBarTitle(page);
  }

  private void switchToTranslation() {
    if (getSelectionStart() != null) {
      endAyahMode();
    }

    if (translations.isEmpty()) {
      startTranslationManager();
    } else {
      int page = getCurrentPage();
      pagerAdapter.setTranslationMode();
      showingTranslation = true;
      if (shouldUpdatePageNumber()) {
        final int position = quranInfo.getPositionFromPage(page, false);
        viewPager.setCurrentItem(position);
      }
      supportInvalidateOptionsMenu();
      updateActionBarSpinner();
    }

    if (!quranFileUtils.hasArabicSearchDatabase() && !promptedForExtraDownload) {
      promptedForExtraDownload = true;
      showGetRequiredFilesDialog();
    }
  }

  public void startTranslationManager() {
    startActivity(new Intent(this, TranslationManagerActivity.class));
  }

  private final TranslationsSpinnerAdapter.OnSelectionChangedListener translationItemChangedListener =
      selectedItems -> {
        quranSettings.setActiveTranslations(selectedItems);
        int pos = viewPager.getCurrentItem() - 1;
        for (int count = 0; count < 3; count++) {
          if (pos + count < 0) {
            continue;
          }
          Fragment f = pagerAdapter.getFragmentIfExists(pos + count);
          if (f instanceof TranslationFragment) {
            ((TranslationFragment) f).refresh();
          } else if (f instanceof TabletFragment) {
            ((TabletFragment) f).refresh();
          }
        }
      };

  public List<LocalTranslation> getTranslations() {
    return translations;
  }

  @Override
  public void onAddTagSelected() {
    FragmentManager fm = getSupportFragmentManager();
    AddTagDialog dialog = new AddTagDialog();
    dialog.show(fm, AddTagDialog.TAG);
  }

  private void updateActionBarTitle(int page) {
    String sura = quranDisplayData.getSuraNameFromPage(this, page, true);
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      translationsSpinner.setVisibility(View.GONE);
      actionBar.setDisplayShowTitleEnabled(true);
      actionBar.setTitle(sura);
      String desc = quranDisplayData.getPageSubtitle(this, page);
      actionBar.setSubtitle(desc);
    }
  }

  private void refreshActionBarSpinner() {
    if (translationsSpinnerAdapter != null) {
      translationsSpinnerAdapter.notifyDataSetChanged();
    } else {
      updateActionBarSpinner();
    }
  }

  private int getCurrentPage() {
    return quranInfo.getPageFromPosition(viewPager.getCurrentItem(), isDualPageVisible());
  }

  private void updateActionBarSpinner() {
    if (translationNames == null || translationNames.length == 0) {
      int page = getCurrentPage();
      updateActionBarTitle(page);
      return;
    }

    if (translationsSpinnerAdapter == null) {
      translationsSpinnerAdapter = new TranslationsSpinnerAdapter(this,
          R.layout.translation_ab_spinner_item, translationNames, translations,
          activeTranslationsFilesNames == null ? quranSettings.getActiveTranslations() : activeTranslationsFilesNames,
          translationItemChangedListener) {
        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
          int type = super.getItemViewType(position);
          convertView = super.getView(position, convertView, parent);
          if (type == 0) {
            SpinnerHolder holder = (SpinnerHolder) convertView.getTag();
            int page = getCurrentPage();

            String sura = quranDisplayData.getSuraNameFromPage(PagerActivity.this, page, true);
            holder.title.setText(sura);
            String desc = quranDisplayData.getPageSubtitle(PagerActivity.this, page);
            holder.subtitle.setText(desc);
            holder.subtitle.setVisibility(View.VISIBLE);
          }
          return convertView;
        }
      };
      translationsSpinner.setAdapter(translationsSpinnerAdapter);
    }

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayShowTitleEnabled(false);
      translationsSpinner.setVisibility(View.VISIBLE);
    }
  }

  @Override
  public void updateDownloadProgress(int progress,
                                     long downloadedSize, long totalSize) {
    audioStatusBar.switchMode(
        AudioStatusBar.DOWNLOADING_MODE);
    audioStatusBar.setProgress(progress);
  }

  @Override
  public void updateProcessingProgress(int progress,
                                       int processFiles, int totalFiles) {
    audioStatusBar.setProgressText(getString(R.string.extracting_title), false);
    audioStatusBar.setProgress(-1);
  }

  @Override
  public void handleDownloadTemporaryError(int errorId) {
    audioStatusBar.setProgressText(getString(errorId), false);
  }

  @Override
  public void handleDownloadSuccess() {
    refreshQuranPages();
    audioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
    audioPresenter.onDownloadSuccess();
  }

  @Override
  public void handleDownloadFailure(int errId) {
    String s = getString(errId);
    audioStatusBar.setProgressText(s, true);
  }

  public void toggleActionBarVisibility(boolean visible) {
    if (visible == isActionBarHidden) {
      toggleActionBar();
    }
  }

  public void toggleActionBar() {
    if (isActionBarHidden) {
      setUiVisibility(true);

      isActionBarHidden = false;
    } else {
      handler.removeMessages(MSG_HIDE_ACTIONBAR);
      setUiVisibility(false);

      isActionBarHidden = true;
    }
  }

  private void ensurePage(int sura, int ayah) {
    int page = quranInfo.getPageFromSuraAyah(sura, ayah);
    if (quranInfo.isValidPage(page)) {
      int position = quranInfo.getPositionFromPage(page, isDualPageVisible());
      if (position != viewPager.getCurrentItem()) {
        viewPager.setCurrentItem(position);
      }
    }
  }

  private void requestTranslationsList() {
    translationJob = translationListPresenter.registerForTranslations((titles, updatedTranslations) -> {
      Set<String> currentActiveTranslationsFilesNames = quranSettings.getActiveTranslations();
      if (currentActiveTranslationsFilesNames.isEmpty() && !updatedTranslations.isEmpty()) {
        currentActiveTranslationsFilesNames = new HashSet<>();
        final int items = updatedTranslations.size();
        for (int i = 0; i < items; i++) {
          currentActiveTranslationsFilesNames.add(updatedTranslations.get(i).getFilename());
        }
      }
      activeTranslationsFilesNames = currentActiveTranslationsFilesNames;

      if (translationsSpinnerAdapter != null) {
        translationsSpinnerAdapter
            .updateItems(titles, updatedTranslations, activeTranslationsFilesNames);
      }
      translationNames = titles;
      translations = updatedTranslations;

      if (showingTranslation) {
        // Since translation items have changed, need to
        updateActionBarSpinner();
      }
    });
  }

  private void toggleBookmark(final Integer sura, final Integer ayah, final int page) {
    compositeDisposable.add(bookmarkModel.toggleBookmarkObservable(sura, ayah, page)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableSingleObserver<Boolean>() {
          @Override
          public void onSuccess(@NonNull Boolean isBookmarked) {
            if (sura == null || ayah == null) {
              // page bookmark
              bookmarksCache.put(page, isBookmarked);
              bookmarksMenuItem.setIcon(isBookmarked ? com.quran.labs.androidquran.common.toolbar.R.drawable.ic_favorite : com.quran.labs.androidquran.common.toolbar.R.drawable.ic_not_favorite);
            } else {
              // ayah bookmark
              SuraAyah suraAyah = new SuraAyah(sura, ayah);
              updateAyahBookmark(suraAyah, isBookmarked);
            }
          }

          @Override
          public void onError(@NonNull Throwable e) {
          }
        }));
  }

  private void checkIfPageIsBookmarked(Integer... pages) {
    compositeDisposable.add(bookmarkModel.getIsBookmarkedObservable(pages)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableObserver<Pair<Integer, Boolean>>() {
          @Override
          public void onNext(@NonNull Pair<Integer, Boolean> result) {
            bookmarksCache.put(result.first, result.second);
          }

          @Override
          public void onError(Throwable e) {
          }

          @Override
          public void onComplete() {
            refreshBookmarksMenu();
          }
        }));
  }

  private void refreshBookmarksMenu() {
    final MenuItem menuItem = bookmarksMenuItem;
    if (menuItem != null) {
      int page = quranInfo.getPageFromPosition(viewPager.getCurrentItem(), isDualPageVisible());

      boolean bookmarked = false;
      if (bookmarksCache.indexOfKey(page) >= 0) {
        bookmarked = bookmarksCache.get(page);
      }

      if (!bookmarked && isDualPageVisible() &&
          bookmarksCache.indexOfKey(page - 1) >= 0) {
        bookmarked = bookmarksCache.get(page - 1);
      }

      menuItem.setIcon(bookmarked ? com.quran.labs.androidquran.common.toolbar.R.drawable.ic_favorite : com.quran.labs.androidquran.common.toolbar.R.drawable.ic_not_favorite);
    } else {
      supportInvalidateOptionsMenu();
    }
  }

  // region Audio playback

  @Override
  public void onPlayPressed() {
    if (audioStatusBar.getCurrentMode() == AudioStatusBar.PAUSED_MODE) {
      // if we are "paused," just un-pause.
      handlePlayback(null);
      return;
    }

    int position = viewPager.getCurrentItem();
    int page = quranInfo.getPageFromPosition(position, isDualPageVisible());

    // log the event
    quranEventLogger.logAudioPlayback(QuranEventLogger.AudioPlaybackSource.PAGE,
        audioStatusBar.getAudioInfo(), isDualPages, showingTranslation, isSplitScreen);

    int startSura = quranDisplayData.safelyGetSuraOnPage(page);
    int startAyah = quranInfo.getFirstAyahOnPage(page);
    List<Integer> startingSuraList = quranInfo.getListOfSurahWithStartingOnPage(page);
    if (startingSuraList.size() == 0 ||
        (startingSuraList.size() == 1 && startingSuraList.get(0) == startSura)) {
      playFromAyah(startSura, startAyah);
    } else {
      promptForMultipleChoicePlay(page, startSura, startAyah, startingSuraList);
    }
  }

  private void playFromAyah(int startSura, int startAyah) {
    final int page = quranInfo.getPageFromSuraAyah(startSura, startAyah);
    final SuraAyah start = new SuraAyah(startSura, startAyah);
    final SuraAyah end = getSelectionEnd();
    // handle the case of multiple ayat being selected and play them as a range if so
    final SuraAyah ending = (end == null || start.equals(end) || start.after(end))? null : end;
    playFromAyah(start, ending, page, 0, 0, ending != null, 1.0f);
  }

  public void playFromAyah(SuraAyah start,
                           SuraAyah end,
                           int page,
                           int verseRepeat,
                           int rangeRepeat,
                           boolean enforceRange,
                           float playbackSpeed) {
    final SuraAyah ending = end != null ? end :
        audioUtils.getLastAyahToPlay(start, page,
            quranSettings.getPreferredDownloadAmount(), isDualPageVisible());

    if (ending != null) {
      Timber.d("playFromAyah - " + start + ", ending: " +
          ending + " - original: " + end + " -- " +
          quranSettings.getPreferredDownloadAmount());
      final QariItem item = audioStatusBar.getAudioInfo();
      final boolean shouldStream = quranSettings.shouldStream();
      audioPresenter.play(
          start, ending, item, verseRepeat, rangeRepeat, enforceRange, playbackSpeed, shouldStream);
    }
  }

  public void handleRequiredDownload(Intent downloadIntent) {
    boolean needsPermission = needsPermissionToDownloadOver3g;
    if (needsPermission) {
      if (QuranUtils.isOnWifiNetwork(this)) {
        Timber.d("on wifi, don't need permission for download...");
        needsPermission = false;
      }
    }

    if (needsPermission) {
      audioStatusBar.switchMode(AudioStatusBar.PROMPT_DOWNLOAD_MODE);
    } else if (!PermissionUtil.havePostNotificationPermission(this)) {
        if (PermissionUtil.canRequestPostNotificationPermission(this)) {
          promptDialog = PermissionUtil.buildPostPermissionDialog(this,
              () -> {
                promptDialog = null;
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return null;
              }, () -> {
                proceedWithDownload(downloadIntent);
                promptDialog = null;
                return null;
              });
          promptDialog.show();
        } else {
          requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    } else {
      proceedWithDownload(downloadIntent);
    }
  }

  public void proceedWithDownload(Intent downloadIntent) {
    if (isActionBarHidden) {
      toggleActionBar();
    }
    audioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);
    Timber.d("starting service in handleRequiredDownload");
    startService(downloadIntent);
  }

  public void handlePlayback(AudioRequest request) {
    needsPermissionToDownloadOver3g = true;
    final Intent intent = new Intent(this, AudioService.class);
    intent.setAction(AudioService.ACTION_PLAYBACK);
    if (request != null) {
      intent.putExtra(AudioService.EXTRA_PLAY_INFO, request);
    }

    Timber.d("starting service for audio playback");
    startService(intent);
  }

  @Override
  public void onPausePressed() {
    startService(audioUtils.getAudioIntent(
        this, AudioService.ACTION_PAUSE));
    audioStatusBar.switchMode(AudioStatusBar.PAUSED_MODE);
  }

  @Override
  public void setPlaybackSpeed(float speed) {
    final AudioRequest lastAudioRequest = audioStatusRepositoryBridge.audioRequest();
    if (lastAudioRequest != null) {
      final AudioRequest updatedAudioRequest = new AudioRequest(lastAudioRequest.getStart(),
          lastAudioRequest.getEnd(),
          lastAudioRequest.getQari(),
          lastAudioRequest.getRepeatInfo(),
          lastAudioRequest.getRangeRepeatInfo(),
          lastAudioRequest.getEnforceBounds(),
          speed,
          lastAudioRequest.getShouldStream(),
          lastAudioRequest.getAudioPathInfo());

      Intent i = new Intent(this, AudioService.class);
      i.setAction(AudioService.ACTION_UPDATE_SETTINGS);
      i.putExtra(AudioService.EXTRA_PLAY_INFO, updatedAudioRequest);
      startService(i);
    }
  }

  @Override
  public void onNextPressed() {
    startService(audioUtils.getAudioIntent(this,
        AudioService.ACTION_SKIP));
  }

  @Override
  public void onPreviousPressed() {
    startService(audioUtils.getAudioIntent(this,
        AudioService.ACTION_REWIND));
  }

  @Override
  public void onAudioSettingsPressed() {
    showSlider(slidingPagerAdapter.getPagePosition(AUDIO_PAGE));
  }

  @Override
  public void onShowQariList() {
    final int page = getCurrentPage();
    final SuraAyah start = getSelectionStart();
    final SuraAyah end = getSelectionEnd();

    final int startSura = quranDisplayData.safelyGetSuraOnPage(page);
    final int startAyah = quranInfo.getFirstAyahOnPage(page);

    final SuraAyah starting = start != null ? start : new SuraAyah(startSura, startAyah);
    final SuraAyah ending = end != null ? end :
        audioUtils.getLastAyahToPlay(starting, page,
            quranSettings.getPreferredDownloadAmount(), isDualPageVisible());
    final QariListWrapper qariListWrapper = new QariListWrapper(this, starting, ending);
    overlay.removeAllViews();
    overlay.addView(qariListWrapper, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    overlay.setVisibility(View.VISIBLE);
    toggleActionBarVisibility(false);
  }

  public boolean updatePlayOptions(int rangeRepeat,
                                   int verseRepeat,
                                   boolean enforceRange,
                                   float playbackSpeed) {
    final AudioRequest lastAudioRequest = audioStatusRepositoryBridge.audioRequest();
    if (lastAudioRequest != null) {
      final AudioRequest updatedAudioRequest = new AudioRequest(lastAudioRequest.getStart(),
          lastAudioRequest.getEnd(),
          lastAudioRequest.getQari(),
          verseRepeat,
          rangeRepeat,
          enforceRange,
          playbackSpeed,
          lastAudioRequest.getShouldStream(),
          lastAudioRequest.getAudioPathInfo());
      Intent i = new Intent(this, AudioService.class);
      i.setAction(AudioService.ACTION_UPDATE_SETTINGS);
      i.putExtra(AudioService.EXTRA_PLAY_INFO, updatedAudioRequest);
      startService(i);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void setRepeatCount(int repeatCount) {
    final AudioRequest lastAudioRequest = audioStatusRepositoryBridge.audioRequest();
    if (lastAudioRequest != null) {
      final AudioRequest updatedAudioRequest = new AudioRequest(lastAudioRequest.getStart(),
          lastAudioRequest.getEnd(),
          lastAudioRequest.getQari(),
          repeatCount,
          lastAudioRequest.getRangeRepeatInfo(),
          lastAudioRequest.getEnforceBounds(),
          lastAudioRequest.getPlaybackSpeed(),
          lastAudioRequest.getShouldStream(),
          lastAudioRequest.getAudioPathInfo());

      Intent i = new Intent(this, AudioService.class);
      i.setAction(AudioService.ACTION_UPDATE_SETTINGS);
      i.putExtra(AudioService.EXTRA_PLAY_INFO, updatedAudioRequest);
      startService(i);
    }
  }

  @Override
  public void onStopPressed() {
    startService(audioUtils.getAudioIntent(this, AudioService.ACTION_STOP));
    audioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
  }

  @Override
  public void onCancelPressed(boolean cancelDownload) {
    if (cancelDownload) {
      needsPermissionToDownloadOver3g = true;

      int resId = R.string.canceling;
      audioStatusBar.setProgressText(getString(resId), true);
      Intent i = new Intent(this, QuranDownloadService.class);
      i.setAction(QuranDownloadService.ACTION_CANCEL_DOWNLOADS);
      startService(i);
    } else {
      audioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
      startService(audioUtils.getAudioIntent(this, AudioService.ACTION_STOP));
    }
  }

  @Override
  public void onAcceptPressed() {
    needsPermissionToDownloadOver3g = false;
    audioPresenter.onDownloadPermissionGranted();
  }

  //endregion

  @Override
  public void onBackPressed() {
    if (getSelectionStart() != null) {
      endAyahMode();
    } else if (showingTranslation) {
      switchToQuran();
    } else {
      onSessionEnd();
      super.onBackPressed();
    }
  }

  // region Ayah selection

  private SuraAyah getSelectionStart() {
    final AyahSelection currentSelection = readingEventPresenter.currentAyahSelection();
    return AyahSelectionKt.startSuraAyah(currentSelection);
  }

  private SuraAyah getSelectionEnd() {
    final AyahSelection currentSelection = readingEventPresenter.currentAyahSelection();
    return AyahSelectionKt.endSuraAyah(currentSelection);
  }

  public AudioRequest getLastAudioRequest() {
    return audioStatusRepositoryBridge.audioRequest();
  }

  public void endAyahMode() {
    readingEventPresenterBridge.clearSelectedAyah();
    slidingPanel.collapsePane();
  }

  //endregion

  private void updateLocalTranslations(final SuraAyah start) {
    final AyahTracker ayahTracker = resolveCurrentTracker();
    if (ayahTracker != null) {
      lastActivatedLocalTranslations = ayahTracker.getLocalTranslations();
      lastSelectedTranslationAyah = ayahTracker.getQuranAyahInfo(start.sura, start.ayah);
    }
  }

  private AyahTracker resolveCurrentTracker() {
    int position = viewPager.getCurrentItem();
    Fragment f = pagerAdapter.getFragmentIfExists(position);
    if (f instanceof QuranPage && f.isVisible()) {
      return ((QuranPage) f).getAyahTracker();
    } else {
      return null;
    }
  }

  private class AyahMenuItemSelectionHandler implements MenuItem.OnMenuItemClickListener {
    @Override
    public boolean onMenuItemClick(@NonNull MenuItem item) {
      int sliderPage = -1;
      final AyahSelection currentSelection = readingEventPresenter.currentAyahSelection();
      final SuraAyah startSuraAyah = AyahSelectionKt.startSuraAyah(currentSelection);
      final SuraAyah endSuraAyah = AyahSelectionKt.endSuraAyah(currentSelection);
      if (startSuraAyah == null || endSuraAyah == null) {
        return false;
      }

      final int itemId = item.getItemId();
      if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_bookmark_ayah) {
        final int startPage = quranInfo.getPageFromSuraAyah(startSuraAyah.sura, startSuraAyah.ayah);
        toggleBookmark(startSuraAyah.sura, startSuraAyah.ayah, startPage);
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_tag_ayah) {
        sliderPage = slidingPagerAdapter.getPagePosition(TAG_PAGE);
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_translate_ayah) {
        sliderPage = slidingPagerAdapter.getPagePosition(TRANSLATION_PAGE);
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_play_from_here) {
        quranEventLogger.logAudioPlayback(QuranEventLogger.AudioPlaybackSource.AYAH,
            audioStatusBar.getAudioInfo(), isDualPages, showingTranslation, isSplitScreen);
        playFromAyah(startSuraAyah.sura, startSuraAyah.ayah);
        toggleActionBarVisibility(true);
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_recite_from_here) {
        pagerActivityRecitationPresenter.onRecitationPressed();
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_share_ayah_link) {
        shareAyahLink(startSuraAyah, endSuraAyah);
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_share_ayah_text) {
        shareAyah(startSuraAyah, endSuraAyah, false);
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_copy_ayah) {
        shareAyah(startSuraAyah, endSuraAyah, true);
      } else {
        return false;
      }

      if (sliderPage < 0) {
        endAyahMode();
      } else {
        showSlider(sliderPage);
      }
      return true;
    }
  }

  private void shareAyah(SuraAyah start, SuraAyah end, final boolean isCopy) {
    if (start == null || end == null) {
      return;
    } else if (!quranFileUtils.hasArabicSearchDatabase()) {
      showGetRequiredFilesDialog();
      return;
    }

    final LocalTranslation[] translationNames = lastActivatedLocalTranslations;
    if (showingTranslation && translationNames != null) {

      // temporarily required so "lastSelectedTranslationAyah" isn't null
      // the real solution is to move this sharing logic out of PagerActivity
      // in the future and avoid this back and forth with the translation fragment.
      updateLocalTranslations(start);
      final QuranAyahInfo quranAyahInfo = lastSelectedTranslationAyah;
      if (quranAyahInfo != null) {
        final String shareText = shareUtil.getShareText(this, quranAyahInfo, translationNames);
        if (isCopy) {
          shareUtil.copyToClipboard(this, shareText);
        } else {
          shareUtil.shareViaIntent(this, shareText, com.quran.labs.androidquran.common.toolbar.R.string.share_ayah_text);
        }
      }

      return;
    }

    compositeDisposable.add(
        arabicDatabaseUtils
            .getVerses(start, end)
            .filter(quranAyahs -> !quranAyahs.isEmpty())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(quranAyahs -> {
              if (isCopy) {
                shareUtil.copyVerses(PagerActivity.this, quranAyahs);
              } else {
                shareUtil.shareVerses(PagerActivity.this, quranAyahs);
              }
            }));
  }

  public void shareAyahLink(SuraAyah start, SuraAyah end) {
    showProgressDialog();
    compositeDisposable.add(
        quranAppUtils.getQuranAppUrlObservable(getString(R.string.quranapp_key), start, end)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableSingleObserver<String>() {
              @Override
              public void onSuccess(@NonNull String url) {
                shareUtil.shareViaIntent(PagerActivity.this, url, com.quran.labs.androidquran.common.toolbar.R.string.share_ayah);
                dismissProgressDialog();
              }

              @Override
              public void onError(@NonNull Throwable e) {
                dismissProgressDialog();
              }
            })
    );
  }

  private void showProgressDialog() {
    if (progressDialog == null) {
      progressDialog = new ProgressDialog(this);
      progressDialog.setIndeterminate(true);
      progressDialog.setMessage(getString(R.string.index_loading));
      progressDialog.show();
    }
  }

  private void dismissProgressDialog() {
    if (progressDialog != null && progressDialog.isShowing()) {
      progressDialog.dismiss();
    }
    progressDialog = null;
  }

  private void showSlider(int sliderPage) {
    readingEventPresenterBridge.clearMenuForSelection();
    slidingPager.setCurrentItem(sliderPage);
    slidingPanel.showPane();
    // TODO there's got to be a better way than this hack
    // The issue is that smoothScrollTo returns if mCanSlide is false
    // and it's false when the panel is GONE and showPane only calls
    // requestLayout, and only in onLayout does mCanSlide become true.
    // So by posting this later it gives time for onLayout to run.
    handler.post(() -> slidingPanel.expandPane());
  }

  private void updateAyahBookmark(SuraAyah suraAyah, boolean bookmarked) {
    // Refresh toolbar icon
    final SuraAyah start = getSelectionStart();
    if (start != null && start.equals(suraAyah)) {
      ayahToolBar.setBookmarked(bookmarked);
    }
  }

  private void promptForMultipleChoicePlay(int page, int startSura, int startAyah,
                                           List<Integer> startingSuraList) {
    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.select_dialog_item);
    for (Integer integer : startingSuraList) {
      String suraName = quranDisplayData.getSuraName(this, integer, false);
      adapter.add(suraName);
    }
    if (startSura != startingSuraList.get(0)) {
      adapter.insert(getString(R.string.starting_page_label), 0);
      startingSuraList.add(0, startSura);
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this)
        .setTitle(getString(R.string.playback_prompt_title))
        .setAdapter(adapter, (dialog, i) -> {
          if (i == 0) {
            playFromAyah(startSura, startAyah);
          } else {
            playFromAyah(startingSuraList.get(i), 1);
          }
          dialog.dismiss();
          promptDialog = null;
        });
    promptDialog = builder.create();
    promptDialog.show();
  }

  private class SlidingPanelListener implements SlidingUpPanelLayout.PanelSlideListener {

    @Override
    public void onPanelSlide(View panel, float slideOffset) {
    }

    @Override
    public void onPanelCollapsed(View panel) {
      if (getSelectionStart() != null) {
        endAyahMode();
      }
      slidingPanel.hidePane();
      readingEventPresenter.onPanelClosed();
    }

    @Override
    public void onPanelExpanded(View panel) {
      readingEventPresenter.onPanelOpened();
    }

    @Override
    public void onPanelAnchored(View panel) {
    }
  }
}
