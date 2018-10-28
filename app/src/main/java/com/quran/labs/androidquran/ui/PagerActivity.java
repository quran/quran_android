package com.quran.labs.androidquran.ui;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
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
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.SearchActivity;
import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.component.activity.PagerActivityComponent;
import com.quran.labs.androidquran.dao.audio.AudioRequest;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils;
import com.quran.labs.androidquran.module.activity.PagerActivityModule;
import com.quran.labs.androidquran.presenter.audio.AudioPresenter;
import com.quran.labs.androidquran.presenter.bookmark.RecentPagePresenter;
import com.quran.labs.androidquran.service.AudioService;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.fragment.AddTagDialog;
import com.quran.labs.androidquran.ui.fragment.AyahActionFragment;
import com.quran.labs.androidquran.ui.fragment.JumpFragment;
import com.quran.labs.androidquran.ui.fragment.TabletFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;
import com.quran.labs.androidquran.ui.fragment.TranslationFragment;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.helpers.JumpDestination;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPage;
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter;
import com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter;
import com.quran.labs.androidquran.ui.util.TranslationsSpinnerAdapter;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.QuranAppUtils;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.ShareUtil;
import com.quran.labs.androidquran.widgets.AudioStatusBar;
import com.quran.labs.androidquran.widgets.AyahToolBar;
import com.quran.labs.androidquran.widgets.IconPageIndicator;
import com.quran.labs.androidquran.widgets.QuranSpinner;
import com.quran.labs.androidquran.widgets.SlidingUpPanelLayout;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.NonRestoringViewPager;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

import static com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter.AUDIO_PAGE;
import static com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter.PAGES;
import static com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter.TAG_PAGE;
import static com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter.TRANSLATION_PAGE;
import static com.quran.labs.androidquran.widgets.AyahToolBar.AyahToolBarPosition;

public class PagerActivity extends QuranActionBarActivity implements
    AudioStatusBar.AudioBarListener,
    DefaultDownloadReceiver.DownloadListener,
    TagBookmarkDialog.OnBookmarkTagsUpdateListener,
    AyahSelectedListener,
    JumpDestination {
  private static final String AUDIO_DOWNLOAD_KEY = "AUDIO_DOWNLOAD_KEY";
  private static final String LAST_READ_PAGE = "LAST_READ_PAGE";
  private static final String LAST_READING_MODE_IS_TRANSLATION =
      "LAST_READING_MODE_IS_TRANSLATION";
  private static final String LAST_ACTIONBAR_STATE = "LAST_ACTIONBAR_STATE";
  private static final String LAST_AUDIO_REQUEST = "LAST_AUDIO_REQUEST";
  private static final String LAST_START_POINT = "LAST_START_POINT";
  private static final String LAST_ENDING_POINT = "LAST_ENDING_POINT";

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
  private int highlightedSura = -1;
  private int highlightedAyah = -1;
  private int ayahToolBarTotalHeight;
  private boolean shouldOverridePlaying = false;
  private DefaultDownloadReceiver downloadReceiver;
  private boolean needsPermissionToDownloadOver3g = true;
  private AlertDialog promptDialog = null;
  private AyahToolBar ayahToolBar;
  private AyahToolBarPosition ayahToolBarPos;
  private AudioRequest lastAudioRequest;
  private boolean isDualPages = false;
  private boolean isLandscape;
  private boolean isImmersiveInPortrait;
  private Integer lastPlayingSura;
  private Integer lastPlayingAyah;
  private View toolBarArea;
  private boolean promptedForExtraDownload;
  private QuranSpinner translationsSpinner;
  private ProgressDialog progressDialog;
  private ViewGroup.MarginLayoutParams audioBarParams;
  private boolean isInMultiWindowMode;

  private String[] translationItems;
  private List<LocalTranslation> translations;
  private Set<String> activeTranslations;
  private TranslationsSpinnerAdapter translationsSpinnerAdapter;

  public static final int MSG_HIDE_ACTIONBAR = 1;

  // AYAH ACTION PANEL STUFF
  // Max height of sliding panel (% of screen)
  private static final float PANEL_MAX_HEIGHT = 0.6f;
  private SlidingUpPanelLayout slidingPanel;
  private ViewPager slidingPager;
  private SlidingPagerAdapter slidingPagerAdapter;
  private boolean isInAyahMode;
  private SuraAyah start;
  private SuraAyah end;

  private int numberOfPages;
  private int numberOfPagesDual;

  private PagerActivityComponent pagerActivityComponent;

  @Inject BookmarkModel bookmarkModel;
  @Inject RecentPagePresenter recentPagePresenter;
  @Inject QuranSettings quranSettings;
  @Inject QuranScreenInfo quranScreenInfo;
  @Inject ArabicDatabaseUtils arabicDatabaseUtils;
  @Inject TranslationsDBAdapter translationsDBAdapter;
  @Inject QuranAppUtils quranAppUtils;
  @Inject ShareUtil shareUtil;
  @Inject AudioUtils audioUtils;
  @Inject QuranInfo quranInfo;
  @Inject QuranFileUtils quranFileUtils;
  @Inject AudioPresenter audioPresenter;

  private CompositeDisposable compositeDisposable;

  private final PagerHandler handler = new PagerHandler(this);

  private static class PagerHandler extends Handler {
    private final WeakReference<PagerActivity> activity;

    PagerHandler(PagerActivity activity) {
      this.activity = new WeakReference<>(activity);
    }

    @Override
    public void handleMessage(Message msg) {
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

    // remove the window background to avoid overdraw. note that, per Romain's blog, this is
    // acceptable (as long as we don't set the background color to null in the theme, since
    // that is used to generate preview windows).
    getWindow().setBackgroundDrawable(null);

    numberOfPages = quranInfo.getNumberOfPages();
    numberOfPagesDual = quranInfo.getNumberOfPagesDual();

    int page = -1;
    isActionBarHidden = true;
    if (savedInstanceState != null) {
      Timber.d("non-null saved instance state!");
      page = savedInstanceState.getInt(LAST_READ_PAGE, -1);
      if (page != -1) {
        page = numberOfPages - page;
      }
      showingTranslation = savedInstanceState
          .getBoolean(LAST_READING_MODE_IS_TRANSLATION, false);
      if (savedInstanceState.containsKey(LAST_ACTIONBAR_STATE)) {
        isActionBarHidden = !savedInstanceState
            .getBoolean(LAST_ACTIONBAR_STATE);
      }
      boolean lastWasDualPages = savedInstanceState.getBoolean(LAST_WAS_DUAL_PAGES, isDualPages);
      shouldAdjustPageNumber = (lastWasDualPages != isDualPages);

      start = savedInstanceState.getParcelable(LAST_START_POINT);
      end = savedInstanceState.getParcelable(LAST_ENDING_POINT);
      this.lastAudioRequest = savedInstanceState.getParcelable(LAST_AUDIO_REQUEST);
    } else {
      Intent intent = getIntent();
      Bundle extras = intent.getExtras();
      if (extras != null) {
        page = numberOfPages - extras.getInt("page", Constants.PAGES_FIRST);
        showingTranslation = extras.getBoolean(EXTRA_JUMP_TO_TRANSLATION, showingTranslation);
        highlightedSura = extras.getInt(EXTRA_HIGHLIGHT_SURA, -1);
        highlightedAyah = extras.getInt(EXTRA_HIGHLIGHT_AYAH, -1);
      }
    }

    compositeDisposable = new CompositeDisposable();

    // subscribe to changes in bookmarks
    compositeDisposable.add(
        bookmarkModel.bookmarksObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(ignore -> onBookmarksChanged()));

    final Resources resources = getResources();
    isImmersiveInPortrait = quranSettings.isImmersiveInPortrait();
    isLandscape = resources.getConfiguration().orientation ==
        Configuration.ORIENTATION_LANDSCAPE;
    ayahToolBarTotalHeight = resources
        .getDimensionPixelSize(R.dimen.toolbar_total_height);
    setContentView(R.layout.quran_page_activity_slider);
    audioStatusBar = findViewById(R.id.audio_area);
    audioStatusBar.setIsDualPageMode(quranScreenInfo.isDualPageMode());
    audioStatusBar.setQariList(audioUtils.getQariList(this));
    audioStatusBar.setAudioBarListener(this);
    audioBarParams = (ViewGroup.MarginLayoutParams) audioStatusBar.getLayoutParams();

    toolBarArea = findViewById(R.id.toolbar_area);
    translationsSpinner = findViewById(R.id.spinner);

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

    if (showingTranslation && translationItems != null) {
      updateActionBarSpinner();
    } else {
      updateActionBarTitle(numberOfPages - page);
    }

    lastPopupTime = System.currentTimeMillis();
    pagerAdapter = new QuranPageAdapter(
        getSupportFragmentManager(), isDualPages, showingTranslation, quranInfo);
    ayahToolBar = findViewById(R.id.ayah_toolbar);

    final NonRestoringViewPager nonRestoringViewPager = findViewById(R.id.quran_pager);
    nonRestoringViewPager.setIsDualPagesInLandscape(
        QuranUtils.isDualPagesInLandscape(this, quranScreenInfo));

    viewPager = nonRestoringViewPager;
    viewPager.setAdapter(pagerAdapter);

    ayahToolBar.setOnItemSelectedListener(new AyahMenuItemSelectionHandler());
    viewPager.addOnPageChangeListener(new OnPageChangeListener() {

      @Override
      public void onPageScrollStateChanged(int state) {
      }

      @Override
      public void onPageScrolled(int position, float positionOffset,
                                 int positionOffsetPixels) {
        if (ayahToolBar.isShowing() && ayahToolBarPos != null) {
          final int startPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah);
          int barPos = quranInfo.getPosFromPage(startPage, isDualPages);
          if (position == barPos) {
            // Swiping to next ViewPager page (i.e. prev quran page)
            ayahToolBarPos.xScroll = 0 - positionOffsetPixels;
          } else if (position == barPos - 1) {
            // Swiping to prev ViewPager page (i.e. next quran page)
            ayahToolBarPos.xScroll = viewPager.getWidth() - positionOffsetPixels;
          } else {
            // Totally off screen, should hide toolbar
            ayahToolBar.setVisibility(View.GONE);
            return;
          }
          ayahToolBar.updatePosition(ayahToolBarPos);
          // If the toolbar is not showing, show it
          if (ayahToolBar.getVisibility() != View.VISIBLE) {
            ayahToolBar.setVisibility(View.VISIBLE);
          }
        }
      }

      @Override
      public void onPageSelected(int position) {
        Timber.d("onPageSelected(): %d", position);
        final int page = quranInfo.getPageFromPos(position, isDualPages);
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
          if (isDualPages) {
            if (bookmarksCache.indexOfKey(page - 1) < 0) {
              checkIfPageIsBookmarked(page - 1, page);
            }
          } else {
            // we don't have the key
            checkIfPageIsBookmarked(page);
          }
        }

        // If we're more than 1 page away from ayah selection end ayah mode
        if (isInAyahMode) {
          final int startPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah);
          int ayahPos = quranInfo.getPosFromPage(startPage, isDualPages);
          if (Math.abs(ayahPos - position) > 1) {
            endAyahMode();
          }
        }
      }
    });

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      setUiVisibilityListener();
      audioStatusBar.setVisibility(View.VISIBLE);
    }
    toggleActionBarVisibility(true);

    if (shouldAdjustPageNumber) {
      // when going from two page per screen to one or vice versa, we adjust the page number,
      // such that the first page is always selected.
      int curPage = numberOfPages - page;
      if (isDualPages) {
        if (curPage % 2 != 0) {
          curPage++;
        }
        curPage = numberOfPagesDual - (curPage / 2);
      } else {
        if (curPage % 2 == 0) {
          curPage--;
        }
        curPage = numberOfPages - curPage;
      }
      page = curPage;
    } else if (isDualPages) {
      page = page / 2;
    }

    viewPager.setCurrentItem(page);

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

    LocalBroadcastManager.getInstance(this).registerReceiver(
        audioReceiver,
        new IntentFilter(AudioService.AudioUpdateIntent.INTENT_NAME));

    downloadReceiver = new DefaultDownloadReceiver(this,
        QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
    String action = QuranDownloadNotifier.ProgressIntent.INTENT_NAME;
    LocalBroadcastManager.getInstance(this).registerReceiver(
        downloadReceiver,
        new IntentFilter(action));
    downloadReceiver.setListener(this);
  }

  public Observable<Integer> getViewPagerObservable() {
    return Observable.create(e -> {
      final OnPageChangeListener pageChangedListener =
          new ViewPager.SimpleOnPageChangeListener() {
        @Override
        public void onPageSelected(int position) {
          e.onNext(quranInfo.getPageFromPos(position, isDualPages));
        }
      };

      viewPager.addOnPageChangeListener(pageChangedListener);
      e.onNext(getCurrentPage());

      e.setCancellable(() -> viewPager.removeOnPageChangeListener(pageChangedListener));
    });
  }

  private int getStatusBarHeight() {
    // thanks to https://github.com/jgilfelt/SystemBarTint for this
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      final Resources resources = getResources();
      final int resId = resources.getIdentifier(
          "status_bar_height", "dimen", "android");
      if (resId > 0) {
        return resources.getDimensionPixelSize(resId);
      }
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
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
            (quranSettings.isArabicNames() || QuranUtils.isRtl()));
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

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private void setUiVisibility(boolean isVisible) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
        (isLandscape || isImmersiveInPortrait)){
      setUiVisibilityKitKat(isVisible);
      if (isInMultiWindowMode) {
        animateToolBar(isVisible);
      }
      return;
    }

    int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
    if (!isVisible) {
      flags |= View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_FULLSCREEN;
    }
    viewPager.setSystemUiVisibility(flags);
    if (isInMultiWindowMode) {
      animateToolBar(isVisible);
    }
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
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

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private void setUiVisibilityListener(){
    viewPager.setOnSystemUiVisibilityChangeListener(
        flags -> {
          boolean visible = (flags & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;
          animateToolBar(visible);
        });
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private void clearUiVisibilityListener(){
    viewPager.setOnSystemUiVisibilityChangeListener(null);
  }

  private void animateToolBar(boolean visible) {
    isActionBarHidden = !visible;
    if (visible) {
      audioStatusBar.updateSelectedItem();
    }

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
        && PreferenceManager.getDefaultSharedPreferences(this).
        getBoolean(Constants.PREF_USE_VOLUME_KEY_NAV, false);
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

    // read the list of translations
    requestTranslationsList();

    if (shouldReconnect) {
      startService(audioUtils.getAudioIntent(this, AudioService.ACTION_CONNECT));
      shouldReconnect = false;
    }

    if (highlightedSura > 0 && highlightedAyah > 0) {
      handler.postDelayed(() ->
          highlightAyah(highlightedSura, highlightedAyah, false, HighlightType.SELECTION), 750);
    }
  }

  @NonNull
  public PagerActivityComponent getPagerActivityComponent() {
    // a fragment may call this before Activity's onCreate, so cache and reuse.
    if (pagerActivityComponent == null) {
      pagerActivityComponent = ((QuranApplication) getApplication())
          .getApplicationComponent()
          .pagerActivityComponentBuilder()
          .withPagerActivityModule(new PagerActivityModule(this))
          .build();
    }
    return pagerActivityComponent;
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
      if (QuranUtils.isDualPages(this, quranScreenInfo)) {
        url = quranFileUtils.getAyaPositionFileUrl(
            quranScreenInfo.getTabletWidthParam());
      }
      String destination = quranFileUtils.getQuranAyahDatabaseDirectory(this);
      // start the download
      String notificationTitle = getString(R.string.highlighting_database);
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
          destination, notificationTitle, AUDIO_DOWNLOAD_KEY,
          downloadType);
      ContextCompat.startForegroundService(this, intent);

      haveDownload = true;
    }

    if (!quranFileUtils.hasArabicSearchDatabase(this)) {
      String url = quranFileUtils.getArabicSearchDatabaseUrl();

      // show "downloading required files" unless we already showed that for
      // highlighting database, in which case show "downloading search data"
      String notificationTitle = getString(R.string.highlighting_database);
      if (haveDownload) {
        notificationTitle = getString(R.string.search_data);
      }

      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
          quranFileUtils.getQuranDatabaseDirectory(this), notificationTitle,
          AUDIO_DOWNLOAD_KEY, downloadType);
      intent.putExtra(QuranDownloadService.EXTRA_OUTPUT_FILE_NAME,
          QuranDataProvider.QURAN_ARABIC_DATABASE);
      ContextCompat.startForegroundService(this, intent);
    }

    if (downloadType != QuranDownloadService.DOWNLOAD_TYPE_AUDIO) {
      // if audio is playing, just show a status notification
      Toast.makeText(this, R.string.downloading_title,
          Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onNewIntent(Intent intent) {
    if (intent == null) {
      return;
    }

    recentPagePresenter.onJump();
    Bundle extras = intent.getExtras();
    if (extras != null) {
      int page = numberOfPages - extras.getInt("page", Constants.PAGES_FIRST);

      boolean currentValue = showingTranslation;
      showingTranslation = extras.getBoolean(EXTRA_JUMP_TO_TRANSLATION, showingTranslation);
      highlightedSura = extras.getInt(EXTRA_HIGHLIGHT_SURA, -1);
      highlightedAyah = extras.getInt(EXTRA_HIGHLIGHT_AYAH, -1);

      if (showingTranslation != currentValue) {
        if (showingTranslation) {
          pagerAdapter.setTranslationMode();
          updateActionBarSpinner();
        } else {
          pagerAdapter.setQuranMode();
          updateActionBarTitle(numberOfPages - page);
        }

        supportInvalidateOptionsMenu();
      }

      if (highlightedAyah > 0 && highlightedSura > 0) {
        // this will jump to the right page automagically
        highlightAyah(highlightedSura, highlightedAyah, true, HighlightType.SELECTION);
      } else {
        if (isDualPages) {
          page = page / 2;
        }
        viewPager.setCurrentItem(page);
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
    if (promptDialog != null) {
      promptDialog.dismiss();
      promptDialog = null;
    }
    audioPresenter.unbind(this);
    recentPagePresenter.unbind(this);
    quranSettings.setWasShowingTranslation(pagerAdapter.getIsShowingTranslation());
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    Timber.d("onDestroy()");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      clearUiVisibilityListener();
    }

    // remove broadcast receivers
    LocalBroadcastManager.getInstance(this).unregisterReceiver(audioReceiver);
    if (downloadReceiver != null) {
      downloadReceiver.setListener(null);
      LocalBroadcastManager.getInstance(this)
          .unregisterReceiver(downloadReceiver);
      downloadReceiver = null;
    }

    compositeDisposable.dispose();
    handler.removeCallbacksAndMessages(null);
    dismissProgressDialog();
    super.onDestroy();
  }

  @Override
  public void onSaveInstanceState(Bundle state) {
    int lastPage = quranInfo.getPageFromPos(viewPager.getCurrentItem(), isDualPages);
    state.putInt(LAST_READ_PAGE, lastPage);
    state.putBoolean(LAST_READING_MODE_IS_TRANSLATION, showingTranslation);
    state.putBoolean(LAST_ACTIONBAR_STATE, isActionBarHidden);
    state.putBoolean(LAST_WAS_DUAL_PAGES, isDualPages);
    if (start != null && end != null) {
      state.putParcelable(LAST_START_POINT, start);
      state.putParcelable(LAST_ENDING_POINT, end);
    }
    if (lastAudioRequest != null) {
      state.putParcelable(LAST_AUDIO_REQUEST, lastAudioRequest);
    }
    super.onSaveInstanceState(state);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.quran_menu, menu);
    final MenuItem item = menu.findItem(R.id.search);
    final SearchView searchView = (SearchView) MenuItemCompat.getActionView(item);
    final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
    searchView.setQueryHint(getString(R.string.search_hint));
    searchView.setSearchableInfo(searchManager.getSearchableInfo(
        new ComponentName(this, SearchActivity.class)));
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    MenuItem item = menu.findItem(R.id.favorite_item);
    if (item != null) {
      int page = quranInfo.getPageFromPos(viewPager.getCurrentItem(), isDualPages);

      boolean bookmarked = false;
      if (bookmarksCache.indexOfKey(page) >= 0) {
        bookmarked = bookmarksCache.get(page);
      }

      if (!bookmarked && isDualPages &&
          bookmarksCache.indexOfKey(page - 1) >= 0) {
        bookmarked = bookmarksCache.get(page - 1);
      }

      item.setIcon(bookmarked ? R.drawable.ic_favorite : R.drawable.ic_not_favorite);
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
      switchToTranslation();
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
    pagerAdapter.setQuranMode();
    showingTranslation = false;
    int page = getCurrentPage();
    supportInvalidateOptionsMenu();
    updateActionBarTitle(page);

    if (highlightedSura > 0 && highlightedAyah > 0) {
      highlightAyah(highlightedSura, highlightedAyah, false, HighlightType.SELECTION);
    }
  }

  private void switchToTranslation() {
    if (isInAyahMode) {
      endAyahMode();
    }

    if (translations.size() == 0) {
      startTranslationManager();
    } else {
      pagerAdapter.setTranslationMode();
      showingTranslation = true;
      supportInvalidateOptionsMenu();
      updateActionBarSpinner();

      if (highlightedSura > 0 && highlightedAyah > 0) {
        highlightAyah(highlightedSura, highlightedAyah, false, HighlightType.SELECTION);
      }
    }

    if (!quranFileUtils.hasArabicSearchDatabase(this) && !promptedForExtraDownload) {
      promptedForExtraDownload = true;
      showGetRequiredFilesDialog();
    }
  }

  public void startTranslationManager() {
    startActivity(new Intent(this, TranslationManagerActivity.class));
  }

  private TranslationsSpinnerAdapter.OnSelectionChangedListener translationItemChangedListener =
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

  public String[] getTranslationNames() {
    return translationItems;
  }

  public Set<String> getActiveTranslations() {
    return activeTranslations;
  }

  @Override
  public void onAddTagSelected() {
    FragmentManager fm = getSupportFragmentManager();
    AddTagDialog dialog = new AddTagDialog();
    dialog.show(fm, AddTagDialog.TAG);
  }

  private void onBookmarksChanged() {
    if (isInAyahMode) {
      final int startPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah);
      compositeDisposable.add(
          bookmarkModel.getIsBookmarkedObservable(start.sura, start.ayah, startPage)
              .observeOn(AndroidSchedulers.mainThread())
              .subscribeWith(new DisposableSingleObserver<Boolean>() {
                @Override
                public void onSuccess(Boolean isBookmarked) {
                  updateAyahBookmark(start, isBookmarked, true);
                }

                @Override
                public void onError(Throwable e) {
                }
              }));
    }
  }

  private void updateActionBarTitle(int page) {
    String sura = quranInfo.getSuraNameFromPage(this, page, true);
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      translationsSpinner.setVisibility(View.GONE);
      actionBar.setDisplayShowTitleEnabled(true);
      actionBar.setTitle(sura);
      String desc = quranInfo.getPageSubtitle(this, page);
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
    return quranInfo.getPageFromPos(viewPager.getCurrentItem(), isDualPages);
  }

  private void updateActionBarSpinner() {
    if (translationItems == null || translationItems.length == 0) {
      int page = getCurrentPage();
      updateActionBarTitle(page);
      return;
    }

    if (translationsSpinnerAdapter == null) {
      translationsSpinnerAdapter = new TranslationsSpinnerAdapter(this,
          R.layout.translation_ab_spinner_item, translationItems, translations,
          activeTranslations == null ? quranSettings.getActiveTranslations() : activeTranslations,
          translationItemChangedListener) {
        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
          int type = super.getItemViewType(position);
          convertView = super.getView(position, convertView, parent);
          if (type == 0) {
            SpinnerHolder holder = (SpinnerHolder) convertView.getTag();
            int page = getCurrentPage();

            String sura = quranInfo.getSuraNameFromPage(PagerActivity.this, page, true);
            holder.title.setText(sura);
            String desc = quranInfo.getPageSubtitle(PagerActivity.this, page);
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

  private BroadcastReceiver audioReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent != null) {
        int state = intent.getIntExtra(
            AudioService.AudioUpdateIntent.STATUS, -1);
        int sura = intent.getIntExtra(
            AudioService.AudioUpdateIntent.SURA, -1);
        int ayah = intent.getIntExtra(
            AudioService.AudioUpdateIntent.AYAH, -1);
        int repeatCount = intent.getIntExtra(
            AudioService.AudioUpdateIntent.REPEAT_COUNT, -200);
        AudioRequest request = intent.getParcelableExtra(AudioService.AudioUpdateIntent.REQUEST);
        if (request != null) {
          lastAudioRequest = request;
        }
        if (state == AudioService.AudioUpdateIntent.PLAYING) {
          audioStatusBar.switchMode(AudioStatusBar.PLAYING_MODE);
          highlightAyah(sura, ayah, HighlightType.AUDIO);
          if (repeatCount >= -1) {
            audioStatusBar.setRepeatCount(repeatCount);
          }
        } else if (state == AudioService.AudioUpdateIntent.PAUSED) {
          audioStatusBar.switchMode(AudioStatusBar.PAUSED_MODE);
          highlightAyah(sura, ayah, HighlightType.AUDIO);
        } else if (state == AudioService.AudioUpdateIntent.STOPPED) {
          audioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
          unHighlightAyahs(HighlightType.AUDIO);
          lastAudioRequest = null;
        }
      }
    }
  };

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
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        setUiVisibility(true);
      } else {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        toolBarArea.setVisibility(View.VISIBLE);
        audioStatusBar.updateSelectedItem();
        audioStatusBar.setVisibility(View.VISIBLE);
      }

      isActionBarHidden = false;
    } else {
      handler.removeMessages(MSG_HIDE_ACTIONBAR);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        setUiVisibility(false);
      } else {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        toolBarArea.setVisibility(View.GONE);
        audioStatusBar.setVisibility(View.GONE);
      }

      isActionBarHidden = true;
    }
  }

  public void highlightAyah(int sura, int ayah, HighlightType type) {
    if (HighlightType.AUDIO.equals(type)) {
        lastPlayingSura = sura;
        lastPlayingAyah = ayah;
    }
    highlightAyah(sura, ayah, true, type);
  }

  private void highlightAyah(int sura, int ayah,
                             boolean force, HighlightType type) {
    Timber.d("highlightAyah() - %s:%s", sura, ayah);
    int page = quranInfo.getPageFromSuraAyah(sura, ayah);
    if (page < Constants.PAGES_FIRST ||
        numberOfPages < page) {
      return;
    }

    int position = quranInfo.getPosFromPage(page, isDualPages);
    if (position != viewPager.getCurrentItem() && force) {
      unHighlightAyahs(type);
      viewPager.setCurrentItem(position);
    }

    Fragment f = pagerAdapter.getFragmentIfExists(position);
    if (f instanceof QuranPage && f.isAdded()) {
      ((QuranPage) f).getAyahTracker().highlightAyah(sura, ayah, type, true);
    }
  }

  private void unHighlightAyah(int sura, int ayah, HighlightType type) {
    int position = viewPager.getCurrentItem();
    Fragment f = pagerAdapter.getFragmentIfExists(position);
    if (f instanceof QuranPage && f.isVisible()) {
      ((QuranPage) f).getAyahTracker().unHighlightAyah(sura, ayah, type);
    }
  }

  private void unHighlightAyahs(HighlightType type) {
    if (HighlightType.AUDIO.equals(type)) {
        lastPlayingSura = null;
        lastPlayingAyah = null;
    }
    int position = viewPager.getCurrentItem();
    Fragment f = pagerAdapter.getFragmentIfExists(position);
    if (f instanceof QuranPage && f.isVisible()) {
      ((QuranPage) f).getAyahTracker().unHighlightAyahs(type);
    }
  }

  private void requestTranslationsList() {
    compositeDisposable.add(
        Single.fromCallable(() ->
            translationsDBAdapter.getTranslations())
          .subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribeWith(new DisposableSingleObserver<List<LocalTranslation>>() {
            @Override
            public void onSuccess(List<LocalTranslation> translationList) {
              int items = translationList.size();
              String[] titles = new String[items];
              for (int i = 0; i < items; i++) {
                LocalTranslation item = translationList.get(i);
                if (!TextUtils.isEmpty(item.translatorForeign)) {
                  titles[i] = item.translatorForeign;
                } else if (!TextUtils.isEmpty(item.translator)) {
                  titles[i] = item.translator;
                } else {
                  titles[i] = item.name;
                }
              }

              Set<String> currentActiveTranslations = quranSettings.getActiveTranslations();
              if (currentActiveTranslations.isEmpty() && items > 0) {
                currentActiveTranslations = new HashSet<>();
                for (int i = 0; i < items; i++) {
                  currentActiveTranslations.add(translationList.get(i).filename);
                }
              }
              activeTranslations = currentActiveTranslations;

              if (translationsSpinnerAdapter != null) {
                translationsSpinnerAdapter.updateItems(titles, translationList, activeTranslations);
              }
              translationItems = titles;
              translations = translationList;

              if (showingTranslation) {
                // Since translation items have changed, need to
                updateActionBarSpinner();
              }

              refreshPages();
            }

            @Override
            public void onError(Throwable e) {
            }
          }));
  }

  private void toggleBookmark(final Integer sura, final Integer ayah, final int page) {
    compositeDisposable.add(bookmarkModel.toggleBookmarkObservable(sura, ayah, page)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableSingleObserver<Boolean>() {
          @Override
          public void onSuccess(Boolean isBookmarked) {
            if (sura == null || ayah == null) {
              // page bookmark
              bookmarksCache.put(page, isBookmarked);
              supportInvalidateOptionsMenu();
            } else {
              // ayah bookmark
              SuraAyah suraAyah = new SuraAyah(sura, ayah);
              updateAyahBookmark(suraAyah, isBookmarked, true);
            }
          }

          @Override
          public void onError(Throwable e) {
          }
        }));
  }

  private void checkIfPageIsBookmarked(Integer... pages) {
    compositeDisposable.add(bookmarkModel.getIsBookmarkedObservable(pages)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableObserver<Pair<Integer, Boolean>>() {

          @Override
          public void onNext(Pair<Integer, Boolean> result) {
            bookmarksCache.put(result.first, result.second);
          }

          @Override
          public void onError(Throwable e) {
          }

          @Override
          public void onComplete() {
            supportInvalidateOptionsMenu();
          }
        }));
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
    int page = numberOfPages - position;
    if (isDualPages) {
      page = ((numberOfPagesDual - position) * 2) - 1;
    }

    int startSura = quranInfo.safelyGetSuraOnPage(page);
    int startAyah = quranInfo.getFirstAyahOnPage(page);
    playFromAyah(page, startSura, startAyah);
  }

  private void playFromAyah(int page, int startSura, int startAyah) {
    final SuraAyah start = new SuraAyah(startSura, startAyah);
    playFromAyah(start, null, page, 0, 0, false, false);
  }

  public void playFromAyah(SuraAyah start,
                           SuraAyah end,
                           int page,
                           int verseRepeat,
                           int rangeRepeat,
                           boolean enforceRange,
                           boolean force) {
    if (force) {
      shouldOverridePlaying = true;
    }

    final SuraAyah ending = end != null ? end :
        audioUtils.getLastAyahToPlay(start, page,
            quranSettings.getPreferredDownloadAmount(), isDualPages);

    if (ending != null) {
      final QariItem item = audioStatusBar.getAudioInfo();
      final boolean shouldStream = quranSettings.shouldStream();
      audioPresenter.play(
          start, ending, item, verseRepeat, rangeRepeat, enforceRange, shouldStream);
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
    } else {
      if (isActionBarHidden) {
        toggleActionBar();
      }
      audioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);
      ContextCompat.startForegroundService(this, downloadIntent);
    }
  }

  public void handlePlayback(AudioRequest request) {
    needsPermissionToDownloadOver3g = true;
    Intent i = new Intent(this, AudioService.class);
    i.setAction(AudioService.ACTION_PLAYBACK);
    if (request != null) {
      i.putExtra(AudioService.EXTRA_PLAY_INFO, request);
      lastAudioRequest = request;
      audioStatusBar.setRepeatCount(request.getRepeatInfo());
    }

    if (shouldOverridePlaying) {
      // force the current audio to stop and start playing new request
      i.putExtra(AudioService.EXTRA_STOP_IF_PLAYING, true);
      shouldOverridePlaying = false;
    }
    // just a playback request, so tell audio service to just continue
    // playing (and don't store new audio data) if it was already playing
    else {
      i.putExtra(AudioService.EXTRA_IGNORE_IF_PLAYING, true);
    }
    ContextCompat.startForegroundService(this, i);
  }

  @Override
  public void onPausePressed() {
    startService(audioUtils.getAudioIntent(
        this, AudioService.ACTION_PAUSE));
    audioStatusBar.switchMode(AudioStatusBar.PAUSED_MODE);
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
    if (lastPlayingSura != null) {
      start = new SuraAyah(lastPlayingSura, lastPlayingAyah);
      end = start;
    }

    if (start == null) {
      final int[] bounds = quranInfo.getPageBounds(getCurrentPage());
      start = new SuraAyah(bounds[0], bounds[1]);
      end = start;
    }
    showSlider(AUDIO_PAGE);
  }

  public boolean updatePlayOptions(int rangeRepeat,
      int verseRepeat, boolean enforceRange) {
    if (lastAudioRequest != null) {
      final AudioRequest updatedAudioRequest = new AudioRequest(lastAudioRequest.getStart(),
          lastAudioRequest.getEnd(),
          lastAudioRequest.getQari(),
          verseRepeat,
          rangeRepeat,
          enforceRange,
          lastAudioRequest.getShouldStream(),
          lastAudioRequest.getAudioPathInfo());
      Intent i = new Intent(this, AudioService.class);
      i.setAction(AudioService.ACTION_UPDATE_REPEAT);
      i.putExtra(AudioService.EXTRA_PLAY_INFO, updatedAudioRequest);
      startService(i);

      lastAudioRequest = updatedAudioRequest;
      audioStatusBar.setRepeatCount(verseRepeat);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void setRepeatCount(int repeatCount) {
    if (lastAudioRequest != null) {
      final AudioRequest updatedAudioRequest = new AudioRequest(lastAudioRequest.getStart(),
          lastAudioRequest.getEnd(),
          lastAudioRequest.getQari(),
          repeatCount,
          lastAudioRequest.getRangeRepeatInfo(),
          lastAudioRequest.getEnforceBounds(),
          lastAudioRequest.getShouldStream(),
          lastAudioRequest.getAudioPathInfo());

      Intent i = new Intent(this, AudioService.class);
      i.setAction(AudioService.ACTION_UPDATE_REPEAT);
      i.putExtra(AudioService.EXTRA_PLAY_INFO, updatedAudioRequest);
      startService(i);
      lastAudioRequest = updatedAudioRequest;
    }
  }

  @Override
  public void onStopPressed() {
    startService(audioUtils.getAudioIntent(this, AudioService.ACTION_STOP));
    audioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
    unHighlightAyahs(HighlightType.AUDIO);
    lastAudioRequest = null;
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
    if (isInAyahMode) {
      endAyahMode();
    } else if (showingTranslation) {
      switchToQuran();
    } else {
      super.onBackPressed();
    }
  }

  // region Ayah selection

  @Override
  public boolean isListeningForAyahSelection(EventType eventType) {
    return eventType == EventType.LONG_PRESS ||
        (eventType == EventType.SINGLE_TAP && isInAyahMode);
  }

  @Override
  public boolean onAyahSelected(EventType eventType, SuraAyah suraAyah, AyahTracker tracker) {
    switch (eventType) {
      case SINGLE_TAP:
        if (isInAyahMode) {
          updateAyahStartSelection(suraAyah, tracker);
          return true;
        }
        return false;
      case LONG_PRESS:
        if (isInAyahMode) {
          updateAyahEndSelection(suraAyah);
        } else {
          startAyahMode(suraAyah, tracker);
        }
        viewPager.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS);
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean onClick(EventType eventType) {
    switch (eventType) {
      case SINGLE_TAP:
        if (!isInAyahMode) {
          toggleActionBar();
          return true;
        }
        return false;
      case DOUBLE_TAP:
        if (isInAyahMode) {
          endAyahMode();
          return true;
        }
        return false;
      default:
        return false;
    }
  }

  public SuraAyah getSelectionStart() {
    return start;
  }

  public SuraAyah getSelectionEnd() {
    return end;
  }

  public AudioRequest getLastAudioRequest() {
    return lastAudioRequest;
  }

  private void startAyahMode(SuraAyah suraAyah, AyahTracker tracker) {
    if (!isInAyahMode) {
      start = end = suraAyah;
      updateToolbarPosition(suraAyah, tracker);
      ayahToolBar.showMenu();
      showAyahModeHighlights(suraAyah, tracker);
      isInAyahMode = true;
    }
  }

  public void endAyahMode() {
    ayahToolBar.hideMenu();
    slidingPanel.collapsePane();
    clearAyahModeHighlights();
    isInAyahMode = false;
  }

  public void nextAyah() {
    if (end != null) {
      final int ayat = quranInfo.getNumAyahs(end.sura);

      final SuraAyah s;
      if (end.ayah + 1 <= ayat) {
        s = new SuraAyah(end.sura, end.ayah + 1);
      } else if (end.sura < 114) {
        s = new SuraAyah(end.sura + 1, 1);
      } else {
        return;
      }
      selectAyah(s);
    }
  }

  public void previousAyah() {
    if (end != null) {
      final SuraAyah s;
      if (end.ayah > 1) {
        s = new SuraAyah(end.sura, end.ayah - 1);
      } else if (end.sura > 1) {
        s = new SuraAyah(end.sura - 1, quranInfo.getNumAyahs(end.sura - 1));
      } else {
        return;
      }
      selectAyah(s);
    }
  }

  private void selectAyah(SuraAyah s) {
    final int page = quranInfo.getPageFromSuraAyah(s.sura, s.ayah);
    final int position = quranInfo.getPosFromPage(page, isDualPages);
    Fragment f = pagerAdapter.getFragmentIfExists(position);
    if (f instanceof QuranPage && f.isVisible()) {
      if (position != viewPager.getCurrentItem()) {
        viewPager.setCurrentItem(position);
      }
      updateAyahStartSelection(s, ((QuranPage) f).getAyahTracker());
    }
  }

  private void updateAyahStartSelection(SuraAyah suraAyah, AyahTracker tracker) {
    if (isInAyahMode) {
      clearAyahModeHighlights();
      start = end = suraAyah;
      if (ayahToolBar.isShowing()) {
        ayahToolBar.resetMenu();
        updateToolbarPosition(suraAyah, tracker);
      }
      if (slidingPanel.isPaneVisible()) {
        refreshPages();
      }
      showAyahModeHighlights(suraAyah, tracker);
    }
  }

  private void updateAyahEndSelection(SuraAyah suraAyah) {
    if (isInAyahMode) {
      clearAyahModeHighlights();
      if (suraAyah.after(start)) {
        end = suraAyah;
      } else {
        end = start;
        start = suraAyah;
      }
      if (slidingPanel.isPaneVisible()) {
        refreshPages();
      }
      showAyahModeRangeHighlights();
    }
  }

  //endregion

  private void updateToolbarPosition(final SuraAyah start, AyahTracker tracker) {
    final int startPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah);
    compositeDisposable.add(bookmarkModel
        .getIsBookmarkedObservable(start.sura, start.ayah, startPage)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableSingleObserver<Boolean>() {
          @Override
          public void onSuccess(Boolean isBookmarked) {
            updateAyahBookmark(start, isBookmarked, false);
          }

          @Override
          public void onError(Throwable e) {
          }
        }));

    ayahToolBarPos = tracker.getToolBarPosition(start.sura, start.ayah,
        ayahToolBar.getToolBarWidth(), ayahToolBarTotalHeight);
    if (ayahToolBarPos != null) {
      ayahToolBar.updatePosition(ayahToolBarPos);
      if (ayahToolBar.getVisibility() != View.VISIBLE) {
        ayahToolBar.setVisibility(View.VISIBLE);
      }
    }
  }

  // Used to sync toolbar with page's SV (landscape non-tablet mode)
  public void onQuranPageScroll(int scrollY) {
    if (ayahToolBarPos != null) {
      ayahToolBarPos.yScroll = 0 - scrollY;
      if (isInAyahMode) {
        ayahToolBar.updatePosition(ayahToolBarPos);
      }
    }
  }

  private void refreshPages() {
    for (int page : PAGES) {
      final int mappedTagPage = slidingPagerAdapter.getPagePosition(TAG_PAGE);
      if (page == mappedTagPage) {
        Fragment fragment = slidingPagerAdapter.getFragmentIfExists(mappedTagPage);
        if (fragment instanceof TagBookmarkDialog && start != null) {
          ((TagBookmarkDialog) fragment).updateAyah(start);
        }
      } else {
        AyahActionFragment f = (AyahActionFragment) slidingPagerAdapter
            .getFragmentIfExists(page);
        if (f != null) {
          f.updateAyahSelection(start, end);
        }
      }
    }
  }

  private void showAyahModeRangeHighlights() {
    // Determine the start and end of the selection
    final int startPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah);
    final int endingPage = quranInfo.getPageFromSuraAyah(end.sura, end.ayah);
    int minPage = Math.min(startPage, endingPage);
    int maxPage = Math.max(startPage, endingPage);
    SuraAyah start = SuraAyah.min(this.start, end);
    SuraAyah end = SuraAyah.max(this.start, this.end);
    // Iterate from beginning to end
    for (int i = minPage; i <= maxPage; i++) {
      QuranPage fragment = pagerAdapter.getFragmentIfExistsForPage(i);
      if (fragment != null) {
        Set<String> ayahKeys = quranInfo.getAyahKeysOnPage(i, start, end);
        fragment.getAyahTracker().highlightAyat(i, ayahKeys, HighlightType.SELECTION);
      }
    }
  }

  private void showAyahModeHighlights(SuraAyah suraAyah, AyahTracker tracker) {
    tracker.highlightAyah(
        suraAyah.sura, suraAyah.ayah, HighlightType.SELECTION, false);
  }

  private void clearAyahModeHighlights() {
    if (isInAyahMode) {
      final int startPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah);
      final int endingPage = quranInfo.getPageFromSuraAyah(end.sura, end.ayah);
      for (int i = startPage; i <= endingPage; i++) {
        QuranPage fragment = pagerAdapter.getFragmentIfExistsForPage(i);
        if (fragment != null) {
          fragment.getAyahTracker().unHighlightAyahs(HighlightType.SELECTION);
        }
      }
    }
  }

  private class AyahMenuItemSelectionHandler implements MenuItem.OnMenuItemClickListener {
    @Override
    public boolean onMenuItemClick(MenuItem item) {
      int sliderPage = -1;
      if (start == null || end == null) {
        return false;
      }

      switch (item.getItemId()) {
        case R.id.cab_bookmark_ayah:
          final int startPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah);
          toggleBookmark(start.sura, start.ayah, startPage);
          break;
        case R.id.cab_tag_ayah:
          sliderPage = slidingPagerAdapter.getPagePosition(TAG_PAGE);
          break;
        case R.id.cab_translate_ayah:
          sliderPage = slidingPagerAdapter.getPagePosition(TRANSLATION_PAGE);
          break;
        case R.id.cab_play_from_here:
          sliderPage = slidingPagerAdapter.getPagePosition(AUDIO_PAGE);
          break;
        case R.id.cab_share_ayah_link:
          shareAyahLink(start, end);
          break;
        case R.id.cab_share_ayah_text:
          shareAyah(start, end, false);
          break;
        case R.id.cab_copy_ayah:
          shareAyah(start, end, true);
          break;
        default:
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
    }

    compositeDisposable.add(
        arabicDatabaseUtils
            .getVerses(start, end)
            .filter(quranAyahs -> quranAyahs.size() > 0)
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
              public void onSuccess(String url) {
                shareUtil.shareViaIntent(PagerActivity.this, url, R.string.share_ayah);
                dismissProgressDialog();
              }

              @Override
              public void onError(Throwable e) {
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
    ayahToolBar.hideMenu();
    slidingPager.setCurrentItem(sliderPage);
    slidingPanel.showPane();
    // TODO there's got to be a better way than this hack
    // The issue is that smoothScrollTo returns if mCanSlide is false
    // and it's false when the panel is GONE and showPane only calls
    // requestLayout, and only in onLayout does mCanSlide become true.
    // So by posting this later it gives time for onLayout to run.
    // Another issue is that the fragments haven't been created yet
    // (on first run), so calling refreshPages() before then won't work.
    handler.post(() -> {
      slidingPanel.expandPane();
      refreshPages();
    });
  }

  private void updateAyahBookmark(
      SuraAyah suraAyah, boolean bookmarked, boolean refreshHighlight) {
    // Refresh toolbar icon
    if (isInAyahMode && start.equals(suraAyah)) {
      ayahToolBar.setBookmarked(bookmarked);
    }
    // Refresh highlight
    if (refreshHighlight && quranSettings.shouldHighlightBookmarks()) {
      if (bookmarked) {
        highlightAyah(suraAyah.sura, suraAyah.ayah, HighlightType.BOOKMARK);
      } else {
        unHighlightAyah(suraAyah.sura, suraAyah.ayah, HighlightType.BOOKMARK);
      }
    }
  }

  private class SlidingPanelListener implements SlidingUpPanelLayout.PanelSlideListener {

    @Override
    public void onPanelSlide(View panel, float slideOffset) {
    }

    @Override
    public void onPanelCollapsed(View panel) {
      if (isInAyahMode) {
        endAyahMode();
      }
      slidingPanel.hidePane();
    }

    @Override
    public void onPanelExpanded(View panel) {
    }

    @Override
    public void onPanelAnchored(View panel) {
    }
  }
}
