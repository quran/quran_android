package com.quran.labs.androidquran.ui;

import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.SearchActivity;
import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.service.AudioService;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.AudioRequest;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.DownloadAudioRequest;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.service.util.StreamingAudioRequest;
import com.quran.labs.androidquran.task.AsyncTask;
import com.quran.labs.androidquran.task.ShareAyahTask;
import com.quran.labs.androidquran.task.ShareQuranAppTask;
import com.quran.labs.androidquran.ui.fragment.AddTagDialog;
import com.quran.labs.androidquran.ui.fragment.AyahActionFragment;
import com.quran.labs.androidquran.ui.fragment.JumpFragment;
import com.quran.labs.androidquran.ui.fragment.TabletFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;
import com.quran.labs.androidquran.ui.fragment.TranslationFragment;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter;
import com.quran.labs.androidquran.ui.util.TranslationsSpinnerAdapter;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.TranslationUtils;
import com.quran.labs.androidquran.widgets.AudioStatusBar;
import com.quran.labs.androidquran.widgets.AyahToolBar;
import com.quran.labs.androidquran.widgets.IconPageIndicator;
import com.quran.labs.androidquran.widgets.SlidingUpPanelLayout;

import android.annotation.TargetApi;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
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
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.Pair;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
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

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;

import static com.quran.labs.androidquran.data.Constants.PAGES_LAST;
import static com.quran.labs.androidquran.data.Constants.PAGES_LAST_DUAL;
import static com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter.AUDIO_PAGE;
import static com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter.PAGES;
import static com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter.TAG_PAGE;
import static com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter.TRANSLATION_PAGE;
import static com.quran.labs.androidquran.widgets.AyahToolBar.AyahToolBarPosition;

public class PagerActivity extends QuranActionBarActivity implements
    AudioStatusBar.AudioBarListener,
    DefaultDownloadReceiver.DownloadListener,
    TagBookmarkDialog.OnBookmarkTagsUpdateListener,
    AyahSelectedListener {
  private static final String AUDIO_DOWNLOAD_KEY = "AUDIO_DOWNLOAD_KEY";
  private static final String LAST_AUDIO_DL_REQUEST = "LAST_AUDIO_DL_REQUEST";
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

  private QuranPageWorker mWorker = null;
  private QuranSettings mSettings = null;
  private long mLastPopupTime = 0;
  private boolean mIsActionBarHidden = true;
  private AudioStatusBar mAudioStatusBar = null;
  private ViewPager mViewPager = null;
  private QuranPageAdapter mPagerAdapter = null;
  private boolean mShouldReconnect = false;
  private SparseBooleanArray mBookmarksCache = null;
  private DownloadAudioRequest mLastAudioDownloadRequest = null;
  private boolean mShowingTranslation = false;
  private int mHighlightedSura = -1;
  private int mHighlightedAyah = -1;
  private int mAyahToolBarTotalHeight;
  private boolean mShouldOverridePlaying = false;
  private DefaultDownloadReceiver mDownloadReceiver;
  private boolean mNeedsPermissionToDownloadOver3g = true;
  private AlertDialog mPromptDialog = null;
  private List<TranslationItem> mTranslations;
  private String[] mTranslationItems;
  private TranslationReaderTask mTranslationReaderTask;
  private TranslationsSpinnerAdapter mSpinnerAdapter;
  private AyahInfoDatabaseHandler mAyahInfoAdapter, mTabletAyahInfoAdapter;
  private AyahToolBar mAyahToolBar;
  private AyahToolBarPosition mAyahToolBarPos;
  private AudioRequest mLastAudioRequest;
  private boolean mDualPages = false;
  private boolean mIsLandscape;
  private boolean mImmersiveInPortrait;
  private Integer mLastPlayingSura;
  private Integer mLastPlayingAyah;
  private View mToolBarArea;
  private ViewGroup.MarginLayoutParams mAudioBarParams;

  public static final int MSG_HIDE_ACTIONBAR = 1;
  public static final int MSG_REMOVE_WINDOW_BACKGROUND = 2;

  private Set<AsyncTask> mCurrentTasks = new HashSet<AsyncTask>();

  // AYAH ACTION PANEL STUFF
  // Max height of sliding panel (% of screen)
  private static final float PANEL_MAX_HEIGHT = 0.6f;
  private SlidingUpPanelLayout mSlidingPanel;
  private ViewPager mSlidingPager;
  private SlidingPagerAdapter mSlidingPagerAdapter;
  private boolean mIsInAyahMode;
  private SuraAyah mStart;
  private SuraAyah mEnd;

  private BookmarkModel mBookmarkModel;
  private CompositeSubscription mCompositeSubscription;

  private final PagerHandler mHandler = new PagerHandler(this);

  private static class PagerHandler extends Handler {
    private final WeakReference<PagerActivity> mActivity;

    public PagerHandler(PagerActivity activity) {
      mActivity = new WeakReference<>(activity);
    }

    @Override
    public void handleMessage(Message msg) {
      PagerActivity activity = mActivity.get();
      if (activity != null) {
        if (msg.what == MSG_HIDE_ACTIONBAR) {
          activity.toggleActionBarVisibility(false);
        } else if (msg.what == MSG_REMOVE_WINDOW_BACKGROUND) {
          activity.getWindow().setBackgroundDrawable(null);
        } else {
          super.handleMessage(msg);
        }
      }
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    ((QuranApplication) getApplication()).refreshLocale(this, false);
    super.onCreate(savedInstanceState);
    mBookmarksCache = new SparseBooleanArray();

    boolean refresh = false;
    QuranScreenInfo qsi = QuranScreenInfo.getOrMakeInstance(this);
    mDualPages = QuranUtils.isDualPages(this, qsi);
    if (QuranUtils.isDualPagesInLandscape(this, qsi)) {
      // on tablet, delay removing the window background so that the user sees a gray background
      // during the transition time (while the ViewPager settles on the right page, etc).
      mHandler.sendEmptyMessageDelayed(MSG_REMOVE_WINDOW_BACKGROUND, 750);
    } else {
      // on phone, or when we aren't changing the adapter page count / fragment types, remove the
      // window background right away to avoid overdraw.
      getWindow().setBackgroundDrawable(null);
    }

    // initialize ayah info database
    String filename = QuranFileUtils.getAyaPositionFileName();
    try {
      mAyahInfoAdapter = AyahInfoDatabaseHandler.getDatabaseHandler(this, filename);
    } catch (Exception e) {
      // no ayah info database available
    }

    mTabletAyahInfoAdapter = null;
    if (qsi.isTablet(this)) {
      try {
        filename = QuranFileUtils.getAyaPositionFileName(
            qsi.getTabletWidthParam());
        mTabletAyahInfoAdapter =
            AyahInfoDatabaseHandler.getDatabaseHandler(this, filename);
      } catch (Exception e) {
        // no ayah info database available for tablet
      }
    }

    int page = -1;

    mIsActionBarHidden = true;
    if (savedInstanceState != null) {
      Timber.d("non-null saved instance state!");
      DownloadAudioRequest lastAudioRequest =
          savedInstanceState.getParcelable(LAST_AUDIO_DL_REQUEST);
      if (lastAudioRequest != null) {
        Timber.d("restoring request from saved instance!");
        mLastAudioDownloadRequest = lastAudioRequest;
      }
      page = savedInstanceState.getInt(LAST_READ_PAGE, -1);
      if (page != -1) {
        page = PAGES_LAST - page;
      }
      mShowingTranslation = savedInstanceState
          .getBoolean(LAST_READING_MODE_IS_TRANSLATION, false);
      if (savedInstanceState.containsKey(LAST_ACTIONBAR_STATE)) {
        mIsActionBarHidden = !savedInstanceState
            .getBoolean(LAST_ACTIONBAR_STATE);
      }
      boolean lastWasDualPages = savedInstanceState.getBoolean(
          LAST_WAS_DUAL_PAGES, mDualPages);
      refresh = (lastWasDualPages != mDualPages);

      mStart = savedInstanceState.getParcelable(LAST_START_POINT);
      mEnd = savedInstanceState.getParcelable(LAST_ENDING_POINT);
      mLastAudioRequest = savedInstanceState.getParcelable(LAST_AUDIO_REQUEST);
    }

    mSettings = QuranSettings.getInstance(this);
    mBookmarkModel = BookmarkModel.getInstance(this);
    mCompositeSubscription = new CompositeSubscription();

    // subscribe to changes in bookmarks
    mCompositeSubscription.add(
        mBookmarkModel.bookmarksObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Action1<Void>() {
              @Override
              public void call(Void aVoid) {
                onBookmarksChanged();
              }
            }));

    final Resources resources = getResources();
    mImmersiveInPortrait = resources.getBoolean(R.bool.immersive_in_portrait);
    mIsLandscape = resources.getConfiguration().orientation ==
        Configuration.ORIENTATION_LANDSCAPE;
    mAyahToolBarTotalHeight = resources
        .getDimensionPixelSize(R.dimen.toolbar_total_height);
    setContentView(R.layout.quran_page_activity_slider);
    mAudioStatusBar = (AudioStatusBar) findViewById(R.id.audio_area);
    mAudioStatusBar.setAudioBarListener(this);
    mAudioBarParams = (ViewGroup.MarginLayoutParams) mAudioStatusBar.getLayoutParams();

    mToolBarArea = findViewById(R.id.toolbar_area);

    // this is the colored view behind the status bar on kitkat and above
    final View statusBarBackground = findViewById(R.id.status_bg);
    statusBarBackground.getLayoutParams().height = getStatusBarHeight();

    final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    if (mSettings.isArabicNames() || QuranUtils.isRtl()) {
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

    Intent intent = getIntent();
    Bundle extras = intent.getExtras();
    if (extras != null) {
      if (page == -1) {
        page = PAGES_LAST -
            extras.getInt("page", Constants.PAGES_FIRST);
      }

      mShowingTranslation = extras.getBoolean(EXTRA_JUMP_TO_TRANSLATION,
          mShowingTranslation);
      mHighlightedSura = extras.getInt(EXTRA_HIGHLIGHT_SURA, -1);
      mHighlightedAyah = extras.getInt(EXTRA_HIGHLIGHT_AYAH, -1);
    }

    if (mShowingTranslation && mTranslationItems != null) {
      updateActionBarSpinner();
    } else {
      updateActionBarTitle(PAGES_LAST - page);
    }

    mWorker = QuranPageWorker.getInstance(this);
    mLastPopupTime = System.currentTimeMillis();
    mPagerAdapter = new QuranPageAdapter(
        getSupportFragmentManager(), mDualPages, mShowingTranslation);
    mAyahToolBar = (AyahToolBar) findViewById(R.id.ayah_toolbar);
    mViewPager = (ViewPager) findViewById(R.id.quran_pager);
    mViewPager.setAdapter(mPagerAdapter);

    mAyahToolBar.setOnItemSelectedListener(new AyahMenuItemSelectionHandler());
    mViewPager.setOnPageChangeListener(new OnPageChangeListener() {

      @Override
      public void onPageScrollStateChanged(int state) {
      }

      @Override
      public void onPageScrolled(int position, float positionOffset,
                                 int positionOffsetPixels) {
        if (mAyahToolBar.isShowing() && mAyahToolBarPos != null) {
          int barPos = QuranInfo.getPosFromPage(mStart.getPage(), mDualPages);
          if (position == barPos) {
            // Swiping to next ViewPager page (i.e. prev quran page)
            mAyahToolBarPos.xScroll = 0 - positionOffsetPixels;
          } else if (position == barPos - 1) {
            // Swiping to prev ViewPager page (i.e. next quran page)
            mAyahToolBarPos.xScroll = mViewPager.getWidth() - positionOffsetPixels;
          } else {
            // Totally off screen, should hide toolbar
            mAyahToolBar.setVisibility(View.GONE);
            return;
          }
          mAyahToolBar.updatePosition(mAyahToolBarPos);
          // If the toolbar is not showing, show it
          if (mAyahToolBar.getVisibility() != View.VISIBLE) {
            mAyahToolBar.setVisibility(View.VISIBLE);
          }
        }
      }

      @Override
      public void onPageSelected(int position) {
        Timber.d("onPageSelected(): %d", position);
        final int page = QuranInfo.getPageFromPos(position, mDualPages);
        mSettings.setLastPage(page);
        if (mSettings.shouldDisplayMarkerPopup()) {
          mLastPopupTime = QuranDisplayHelper.displayMarkerPopup(
              PagerActivity.this, page, mLastPopupTime);
          if (mDualPages) {
            mLastPopupTime = QuranDisplayHelper.displayMarkerPopup(
                PagerActivity.this, page - 1, mLastPopupTime);
          }
        }

        if (!mShowingTranslation) {
          updateActionBarTitle(page);
        } else {
          refreshActionBarSpinner();
        }

        if (mBookmarksCache.indexOfKey(page) < 0) {
          if (mDualPages) {
            if (mBookmarksCache.indexOfKey(page - 1) < 0) {
              checkIfPageIsBookmarked(page - 1, page);
            }
          } else {
            // we don't have the key
            checkIfPageIsBookmarked(page);
          }
        }

        // If we're more than 1 page away from ayah selection end ayah mode
        if (mIsInAyahMode) {
          int ayahPos = QuranInfo.getPosFromPage(mStart.getPage(), mDualPages);
          if (Math.abs(ayahPos - position) > 1) {
            endAyahMode();
          }
        }
      }
    });

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      setUiVisibilityListener();
      mAudioStatusBar.setVisibility(View.VISIBLE);
    }
    toggleActionBarVisibility(true);

    if (mDualPages) {
      mViewPager.setCurrentItem(page / 2);
    } else {
      mViewPager.setCurrentItem(page);
    }

    mSettings.setLastPage(PAGES_LAST - page);
    setLoading(false);

    // just got created, need to reconnect to service
    mShouldReconnect = true;

    // enforce orientation lock
    if (mSettings.isLockOrientation()) {
      int current = getResources().getConfiguration().orientation;
      if (mSettings.isLandscapeOrientation()) {
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

    if (refresh) {
      final int curPage = PAGES_LAST - page;
      mHandler.post(new Runnable() {
        @Override
        public void run() {
          mPagerAdapter.notifyDataSetChanged();
          int page = curPage;
          if (mDualPages) {
            if (page % 2 != 0) {
              page++;
            }
            page = PAGES_LAST_DUAL - (page / 2);
          } else {
            if (page % 2 == 0) {
              page--;
            }
            page = PAGES_LAST - page;
          }
          mViewPager.setCurrentItem(page);
        }
      });
    }

    LocalBroadcastManager.getInstance(this).registerReceiver(
        mAudioReceiver,
        new IntentFilter(AudioService.AudioUpdateIntent.INTENT_NAME));

    mDownloadReceiver = new DefaultDownloadReceiver(this,
        QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
    String action = QuranDownloadNotifier.ProgressIntent.INTENT_NAME;
    LocalBroadcastManager.getInstance(this).registerReceiver(
        mDownloadReceiver,
        new IntentFilter(action));
    mDownloadReceiver.setListener(this);
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
    mSlidingPanel = (SlidingUpPanelLayout) findViewById(R.id.sliding_panel);
    final ViewGroup slidingLayout =
        (ViewGroup) mSlidingPanel.findViewById(R.id.sliding_layout);
    mSlidingPager = (ViewPager) mSlidingPanel
        .findViewById(R.id.sliding_layout_pager);
    final IconPageIndicator slidingPageIndicator =
        (IconPageIndicator) mSlidingPanel
            .findViewById(R.id.sliding_pager_indicator);

    // Find close button and set listener
    final View closeButton = mSlidingPanel
        .findViewById(R.id.sliding_menu_close);
    closeButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        endAyahMode();
      }
    });

    // Create and set fragment pager adapter
    mSlidingPagerAdapter = new SlidingPagerAdapter(getSupportFragmentManager(),
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
            (mSettings.isArabicNames() || QuranUtils.isRtl()));
    mSlidingPager.setAdapter(mSlidingPagerAdapter);

    // Attach the view pager to the action bar
    slidingPageIndicator.setViewPager(mSlidingPager);

    // Set sliding layout parameters
    int displayHeight = getResources().getDisplayMetrics().heightPixels;
    slidingLayout.getLayoutParams().height =
        (int) (displayHeight * PANEL_MAX_HEIGHT);
    mSlidingPanel.setEnableDragViewTouchEvents(true);
    mSlidingPanel.setPanelSlideListener(new SlidingPanelListener());
    slidingLayout.setVisibility(View.GONE);

    // When clicking any menu items, expand the panel
    slidingPageIndicator.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (!mSlidingPanel.isExpanded()) {
          mSlidingPanel.expandPane();
        }
      }
    });
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      mHandler.sendEmptyMessageDelayed(
          MSG_HIDE_ACTIONBAR, DEFAULT_HIDE_AFTER_TIME);
    } else {
      mHandler.removeMessages(MSG_HIDE_ACTIONBAR);
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private void setUiVisibility(boolean isVisible){
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
        (mIsLandscape || mImmersiveInPortrait)){
      setUiVisibilityKitKat(isVisible);
      return;
    }

    int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
    if (!isVisible) {
      flags |= View.SYSTEM_UI_FLAG_LOW_PROFILE
          | View.SYSTEM_UI_FLAG_FULLSCREEN;
    }
    mViewPager.setSystemUiVisibility(flags);
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
    mViewPager.setSystemUiVisibility(flags);
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private void setUiVisibilityListener(){
    mViewPager.setOnSystemUiVisibilityChangeListener(
        new View.OnSystemUiVisibilityChangeListener() {
          @Override
          public void onSystemUiVisibilityChange(int flags) {
            boolean visible =
                (flags & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;
            mIsActionBarHidden = !visible;
            if (visible) {
              mAudioStatusBar.updateSelectedItem();
            }

            // animate toolbar
            mToolBarArea.animate()
                .translationY(visible ? 0 : -mToolBarArea.getHeight())
                .setDuration(250)
                .start();

            /* the bottom margin on the audio bar is not part of its height, and so we have to
             * take it into account when animating the audio bar off the screen. */
            final int bottomMargin = mAudioBarParams.bottomMargin;

            // and audio bar
            mAudioStatusBar.animate()
                .translationY(visible ? 0 : mAudioStatusBar.getHeight() + bottomMargin)
                .setDuration(250)
                .start();
          }
        });
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private void clearUiVisibilityListener(){
    mViewPager.setOnSystemUiVisibilityChangeListener(null);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    boolean navigate = mAudioStatusBar.getCurrentMode() !=
        AudioStatusBar.PLAYING_MODE
        && PreferenceManager.getDefaultSharedPreferences(this).
        getBoolean(Constants.PREF_USE_VOLUME_KEY_NAV, false);
    if (navigate && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
      mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1);
      return true;
    } else if (navigate && keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      mViewPager.setCurrentItem(mViewPager.getCurrentItem() + 1);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
    return ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
        keyCode == KeyEvent.KEYCODE_VOLUME_UP) &&
        mAudioStatusBar.getCurrentMode() !=
            AudioStatusBar.PLAYING_MODE &&
        PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(Constants.PREF_USE_VOLUME_KEY_NAV, false))
        || super.onKeyUp(keyCode, event);
  }

  @Override
  public void onResume() {
    // read the list of translations
    if (mTranslationReaderTask != null) {
      mTranslationReaderTask.cancel(true);
    }
    mTranslationReaderTask = new TranslationReaderTask();
    mTranslationReaderTask.execute();

    super.onResume();
    if (mShouldReconnect) {
      startService(AudioUtils.getAudioIntent(
          this, AudioService.ACTION_CONNECT));
      mShouldReconnect = false;
    }

    if (mHighlightedSura > 0 && mHighlightedAyah > 0) {
      mHandler.postDelayed(
          new Runnable() {
            public void run() {
              highlightAyah(mHighlightedSura, mHighlightedAyah, false, HighlightType.SELECTION);
            }
          }, 750);
    }
  }

  public AyahInfoDatabaseHandler getAyahInfoDatabase(String widthParam) {
    if (QuranScreenInfo.getInstance().getWidthParam().equals(widthParam)) {
      return mAyahInfoAdapter;
    } else {
      return mTabletAyahInfoAdapter;
    }
  }

  public void showGetRequiredFilesDialog() {
    if (mPromptDialog != null) {
      return;
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(R.string.download_extra_data)
        .setPositiveButton(R.string.downloadPrompt_ok,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int option) {
                downloadRequiredFiles();
                dialog.dismiss();
                mPromptDialog = null;
              }
            })
        .setNegativeButton(R.string.downloadPrompt_no,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int option) {
                dialog.dismiss();
                mPromptDialog = null;
              }
            });
    mPromptDialog = builder.create();
    mPromptDialog.show();
  }

  public void downloadRequiredFiles() {
    int downloadType = QuranDownloadService.DOWNLOAD_TYPE_AUDIO;
    if (mAudioStatusBar.getCurrentMode() == AudioStatusBar.STOPPED_MODE) {
      // if we're stopped, use audio download bar as our progress bar
      mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);
      if (mIsActionBarHidden) {
        toggleActionBar();
      }
    } else {
      // if audio is playing, let's not disrupt it - do this using a
      // different type so the broadcast receiver ignores it.
      downloadType = QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB;
    }

    boolean haveDownload = false;
    QuranScreenInfo qsi = QuranScreenInfo.getOrMakeInstance(this);
    if (!QuranFileUtils.haveAyaPositionFile(this)) {
      String url = QuranFileUtils.getAyaPositionFileUrl();
      if (QuranUtils.isDualPages(this, qsi)) {
        url = QuranFileUtils.getAyaPositionFileUrl(
            qsi.getTabletWidthParam());
      }
      String destination = QuranFileUtils.getQuranAyahDatabaseDirectory(this);
      // start the download
      String notificationTitle = getString(R.string.highlighting_database);
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
          destination, notificationTitle, AUDIO_DOWNLOAD_KEY,
          downloadType);
      startService(intent);

      haveDownload = true;
    }

    if (!QuranFileUtils.hasArabicSearchDatabase(this)) {
      String url = QuranFileUtils.getArabicSearchDatabaseUrl();

      // show "downloading required files" unless we already showed that for
      // highlighting database, in which case show "downloading search data"
      String notificationTitle = getString(R.string.highlighting_database);
      if (haveDownload) {
        notificationTitle = getString(R.string.search_data);
      }

      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
          QuranFileUtils.getQuranDatabaseDirectory(this), notificationTitle,
          AUDIO_DOWNLOAD_KEY, downloadType);
      intent.putExtra(QuranDownloadService.EXTRA_OUTPUT_FILE_NAME,
          QuranDataProvider.QURAN_ARABIC_DATABASE);
      startService(intent);
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

    Bundle extras = intent.getExtras();
    if (extras != null) {
      int page = PAGES_LAST -
          extras.getInt("page", Constants.PAGES_FIRST);
      updateActionBarTitle(PAGES_LAST - page);

      boolean currentValue = mShowingTranslation;
      mShowingTranslation = extras.getBoolean(EXTRA_JUMP_TO_TRANSLATION,
          mShowingTranslation);
      mHighlightedSura = extras.getInt(EXTRA_HIGHLIGHT_SURA, -1);
      mHighlightedAyah = extras.getInt(EXTRA_HIGHLIGHT_AYAH, -1);

      if (mShowingTranslation != currentValue) {
        if (mShowingTranslation) {
          mPagerAdapter.setTranslationMode();
        } else {
          mPagerAdapter.setQuranMode();
        }

        invalidateOptionsMenu();
      }

      if (mHighlightedAyah > 0 && mHighlightedSura > 0) {
        // this will jump to the right page automagically
        highlightAyah(mHighlightedSura, mHighlightedAyah, true, HighlightType.SELECTION);
      } else {
        if (mDualPages) {
          page = page / 2;
        }
        mViewPager.setCurrentItem(page);
      }

      setIntent(intent);
    }
  }

  public void jumpTo(int page) {
    Intent i = new Intent(this, PagerActivity.class);
    i.putExtra("page", page);
    onNewIntent(i);
  }

  public void jumpToAndHighlight(int page, int sura, int ayah) {
    Intent i = new Intent(this, PagerActivity.class);
    i.putExtra("page", page);
    i.putExtra(EXTRA_HIGHLIGHT_SURA, sura);
    i.putExtra(EXTRA_HIGHLIGHT_AYAH, ayah);
    onNewIntent(i);
  }

  @Override
  public void onPause() {
    if (mPromptDialog != null) {
      mPromptDialog.dismiss();
      mPromptDialog = null;
    }
    mSpinnerAdapter = null;
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    Timber.d("onDestroy()");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      clearUiVisibilityListener();
    }

    // remove broadcast receivers
    LocalBroadcastManager.getInstance(this)
        .unregisterReceiver(mAudioReceiver);
    if (mDownloadReceiver != null) {
      mDownloadReceiver.setListener(null);
      LocalBroadcastManager.getInstance(this)
          .unregisterReceiver(mDownloadReceiver);
      mDownloadReceiver = null;
    }

    // If there are any unfinished tasks, stop them
    if (!mCurrentTasks.isEmpty()) {
      // Use a copy to avoid concurrent modification when calling cancel
      // since cancel causes the task to remove itself from this set
      List<AsyncTask> currentTasks = new ArrayList<AsyncTask>(mCurrentTasks);
      for (AsyncTask task : currentTasks) {
        task.cancel(true);
      }
    }

    mCompositeSubscription.unsubscribe();
    mHandler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  public boolean registerTask(AsyncTask task) {
    return mCurrentTasks.add(task);
  }

  public boolean unregisterTask(AsyncTask task) {
    return mCurrentTasks.remove(task);
  }

  @Override
  public void onSaveInstanceState(Bundle state) {
    if (mLastAudioDownloadRequest != null) {
      state.putParcelable(LAST_AUDIO_DL_REQUEST,
          mLastAudioDownloadRequest);
    }
    int lastPage = QuranInfo.getPageFromPos(mViewPager.getCurrentItem(), mDualPages);
    state.putInt(LAST_READ_PAGE, lastPage);
    state.putBoolean(LAST_READING_MODE_IS_TRANSLATION, mShowingTranslation);
    state.putBoolean(LAST_ACTIONBAR_STATE, mIsActionBarHidden);
    state.putBoolean(LAST_WAS_DUAL_PAGES, mDualPages);
    if (mStart != null && mEnd != null) {
      state.putParcelable(LAST_START_POINT, mStart);
      state.putParcelable(LAST_ENDING_POINT, mEnd);
    }
    if (mLastAudioRequest != null) {
      state.putParcelable(LAST_AUDIO_REQUEST, mLastAudioRequest);
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
      int page = QuranInfo.getPageFromPos(mViewPager.getCurrentItem(), mDualPages);

      boolean bookmarked = false;
      if (mBookmarksCache.indexOfKey(page) >= 0) {
        bookmarked = mBookmarksCache.get(page);
      }

      if (!bookmarked && mDualPages &&
          mBookmarksCache.indexOfKey(page - 1) >= 0) {
        bookmarked = mBookmarksCache.get(page - 1);
      }

      item.setIcon(bookmarked ? R.drawable.ic_favorite :
          R.drawable.ic_not_favorite);
    }

    MenuItem quran = menu.findItem(R.id.goto_quran);
    MenuItem translation = menu.findItem(R.id.goto_translation);
    if (quran != null && translation != null) {
      if (!mShowingTranslation) {
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
      nightMode.setIcon(isNightMode ?
          R.drawable.ic_night_mode : R.drawable.ic_day_mode);
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
      item.setIcon(isNightMode ?
          R.drawable.ic_night_mode : R.drawable.ic_day_mode);
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
    int pos = mViewPager.getCurrentItem();
    int start = (pos == 0) ? pos : pos - 1;
    int end = (pos == mPagerAdapter.getCount() - 1) ? pos : pos + 1;
    for (int i = start; i <= end; i++) {
      Fragment f = mPagerAdapter.getFragmentIfExists(i);
      if (f != null && f instanceof AyahTracker) {
        ((AyahTracker) f).updateView();
      }
    }
  }

  @Override
  public boolean onSearchRequested() {
    return super.onSearchRequested();
  }

  public void setLoading(boolean isLoading) {
    setSupportProgressBarIndeterminateVisibility(isLoading);
  }

  public void setLoadingIfPage(int page) {
    int position = mViewPager.getCurrentItem();
    int currentPage = PAGES_LAST - position;
    if (currentPage == page) {
      setLoading(true);
    }
  }

  public void switchToQuran() {
    mPagerAdapter.setQuranMode();
    mShowingTranslation = false;
    int page = getCurrentPage();
    invalidateOptionsMenu();
    updateActionBarTitle(page);

    if (mHighlightedSura > 0 && mHighlightedAyah > 0) {
      highlightAyah(mHighlightedSura, mHighlightedAyah, false, HighlightType.SELECTION);
    }
  }

  public void switchToTranslation() {
    if (mIsInAyahMode) {
      endAyahMode();
    }
    String activeDatabase = TranslationUtils.getDefaultTranslation(
        this, mTranslations);
    if (activeDatabase == null) {
      startTranslationManager();
    } else {
      mPagerAdapter.setTranslationMode();
      mShowingTranslation = true;
      invalidateOptionsMenu();
      updateActionBarSpinner();

      if (mHighlightedSura > 0 && mHighlightedAyah > 0) {
        highlightAyah(mHighlightedSura, mHighlightedAyah, false, HighlightType.SELECTION);
      }
    }
  }

  public void startTranslationManager() {
    Intent i = new Intent(this, TranslationManagerActivity.class);
    startActivity(i);
  }

  ActionBar.OnNavigationListener mNavigationCallback =
      new ActionBar.OnNavigationListener() {
        @Override
        public boolean onNavigationItemSelected(int itemPosition,
                                                long itemId) {
          Timber.d("item chosen: " + itemPosition);
          if (mTranslations != null &&
              mTranslations.size() > itemPosition) {
            TranslationItem item = mTranslations.get(itemPosition);
            mSettings.setActiveTranslation(item.filename);

            int pos = mViewPager.getCurrentItem() - 1;
            for (int count = 0; count < 3; count++) {
              if (pos + count < 0) {
                continue;
              }
              Fragment f = mPagerAdapter
                  .getFragmentIfExists(pos + count);
              if (f instanceof TranslationFragment) {
                ((TranslationFragment) f).refresh(item.filename);
              } else if (f instanceof TabletFragment) {
                ((TabletFragment) f).refresh(item.filename);
              }
            }
            return true;
          }
          return false;
        }
      };

  public List<TranslationItem> getTranslations() {
    return mTranslations;
  }

  public String[] getTranslationNames() {
    return mTranslationItems;
  }

  @Override
  public void onAddTagSelected() {
    FragmentManager fm = getSupportFragmentManager();
    AddTagDialog dialog = new AddTagDialog();
    dialog.show(fm, AddTagDialog.TAG);
  }

  private void onBookmarksChanged() {
    if (mIsInAyahMode) {
      Subscription subscription =
          mBookmarkModel.getIsBookmarkedObservable(mStart.sura, mStart.ayah, mStart.getPage())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(new Action1<Boolean>() {
            @Override
            public void call(Boolean isBookmarked) {
              updateAyahBookmark(mStart, isBookmarked, true);
            }
          });
      mCompositeSubscription.add(subscription);
    }
  }

  private void updateActionBarTitle(int page) {
    String sura = QuranInfo.getSuraNameFromPage(this, page, true);
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayShowTitleEnabled(true);
      actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
      actionBar.setTitle(sura);
      String desc = QuranInfo.getPageSubtitle(this, page);
      actionBar.setSubtitle(desc);
    }
    mSpinnerAdapter = null;
  }

  private void refreshActionBarSpinner() {
    if (mSpinnerAdapter != null) {
      mSpinnerAdapter.notifyDataSetChanged();
    } else {
      updateActionBarSpinner();
    }
  }

  private int getCurrentPage() {
    return QuranInfo.getPageFromPos(mViewPager.getCurrentItem(), mDualPages);
  }

  private void updateActionBarSpinner() {
    if (mTranslationItems == null || mTranslationItems.length == 0) {
      int page = getCurrentPage();
      updateActionBarTitle(page);
      return;
    }

    mSpinnerAdapter = new TranslationsSpinnerAdapter(this,
        R.layout.support_simple_spinner_dropdown_item,
        mTranslationItems, mTranslations) {
      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        convertView = super.getView(position, convertView, parent);
        SpinnerHolder holder = (SpinnerHolder) convertView.getTag();
        int page = getCurrentPage();
        String subtitle = QuranInfo.getPageSubtitle(PagerActivity.this, page);
        holder.subtitle.setText(subtitle);
        holder.subtitle.setVisibility(View.VISIBLE);
        return convertView;
      }
    };

    // figure out which translation should be selected
    int selected = mSpinnerAdapter.getPositionForActiveTranslation();

    getSupportActionBar().setNavigationMode(
        ActionBar.NAVIGATION_MODE_LIST);
    getSupportActionBar().setListNavigationCallbacks(mSpinnerAdapter,
        mNavigationCallback);
    getSupportActionBar().setSelectedNavigationItem(selected);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
  }

  BroadcastReceiver mAudioReceiver = new BroadcastReceiver() {
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
          mLastAudioRequest = request;
        }
        if (state == AudioService.AudioUpdateIntent.PLAYING) {
          mAudioStatusBar.switchMode(AudioStatusBar.PLAYING_MODE);
          highlightAyah(sura, ayah, HighlightType.AUDIO);
          if (repeatCount >= -1) {
            mAudioStatusBar.setRepeatCount(repeatCount);
          }
        } else if (state == AudioService.AudioUpdateIntent.PAUSED) {
          mAudioStatusBar.switchMode(AudioStatusBar.PAUSED_MODE);
          highlightAyah(sura, ayah, HighlightType.AUDIO);
        } else if (state == AudioService.AudioUpdateIntent.STOPPED) {
          mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
          unHighlightAyahs(HighlightType.AUDIO);
          mLastAudioRequest = null;

          AudioRequest qi = intent.getParcelableExtra(AudioService.EXTRA_PLAY_INFO);
          if (qi != null) {
            // this means we stopped due to missing audio
          }
        }
      }
    }
  };

  @Override
  public void updateDownloadProgress(int progress,
                                     long downloadedSize, long totalSize) {
    mAudioStatusBar.switchMode(
        AudioStatusBar.DOWNLOADING_MODE);
    mAudioStatusBar.setProgress(progress);
  }

  @Override
  public void updateProcessingProgress(int progress,
                                       int processFiles, int totalFiles) {
    mAudioStatusBar.setProgressText(
        getString(R.string.extracting_title), false);
    mAudioStatusBar.setProgress(-1);
  }

  @Override
  public void handleDownloadTemporaryError(int errorId) {
    mAudioStatusBar.setProgressText(getString(errorId), false);
  }

  @Override
  public void handleDownloadSuccess() {
    playAudioRequest(mLastAudioDownloadRequest);
  }

  @Override
  public void handleDownloadFailure(int errId) {
    String s = getString(errId);
    mAudioStatusBar.setProgressText(s, true);
  }

  public void toggleActionBarVisibility(boolean visible) {
    if (!(visible ^ mIsActionBarHidden)) {
      toggleActionBar();
    }
  }

  public void toggleActionBar() {
    if (mIsActionBarHidden) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        setUiVisibility(true);
      } else {
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        getWindow().clearFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mToolBarArea.setVisibility(View.VISIBLE);

        mAudioStatusBar.updateSelectedItem();
        mAudioStatusBar.setVisibility(View.VISIBLE);
      }

      mIsActionBarHidden = false;
    } else {
      mHandler.removeMessages(MSG_HIDE_ACTIONBAR);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        setUiVisibility(false);
      } else {
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(
            WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        mToolBarArea.setVisibility(View.GONE);

        mAudioStatusBar.setVisibility(View.GONE);
      }

      mIsActionBarHidden = true;
    }
  }

  public QuranPageWorker getQuranPageWorker() {
    return mWorker;
  }

  public void highlightAyah(int sura, int ayah, HighlightType type) {
    if (type == HighlightType.AUDIO) {
        mLastPlayingSura = sura;
        mLastPlayingAyah = ayah;
    }
    highlightAyah(sura, ayah, true, type);
  }

  public void highlightAyah(int sura, int ayah,
      boolean force, HighlightType type) {
    Timber.d("highlightAyah() - " + sura + ":" + ayah);
    int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
    if (page < Constants.PAGES_FIRST ||
        PAGES_LAST < page) {
      return;
    }

    int position = QuranInfo.getPosFromPage(page, mDualPages);
    if (position != mViewPager.getCurrentItem() && force) {
      unHighlightAyahs(type);
      mViewPager.setCurrentItem(position);
    }

    Fragment f = mPagerAdapter.getFragmentIfExists(position);
    if (f != null && f instanceof AyahTracker) {
      ((AyahTracker) f).highlightAyah(sura, ayah, type);
    }
  }

  public void unHighlightAyah(int sura, int ayah, HighlightType type) {
    int position = mViewPager.getCurrentItem();
    Fragment f = mPagerAdapter.getFragmentIfExists(position);
    if (f != null && f instanceof AyahTracker) {
      ((AyahTracker) f).unHighlightAyah(sura, ayah, type);
    }
  }

  public void unHighlightAyahs(HighlightType type) {
    if (type == HighlightType.AUDIO) {
        mLastPlayingSura = null;
        mLastPlayingAyah = null;
    }
    int position = mViewPager.getCurrentItem();
    Fragment f = mPagerAdapter.getFragmentIfExists(position);
    if (f != null && f instanceof AyahTracker) {
      ((AyahTracker) f).unHighlightAyahs(type);
    }
  }

  class TranslationReaderTask extends AsyncTask<Void, Void, Void> {
    List<TranslationItem> items = null;

    @Override
    protected Void doInBackground(Void... params) {
      try {
        TranslationsDBAdapter adapter =
            new TranslationsDBAdapter(PagerActivity.this);
        items = adapter.getTranslations();
        adapter.close();
      } catch (Exception e) {
        Timber.d("error getting translations list",e);
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
      if (items != null) {
        mTranslations = items;
      }

      int i = 0;
      String[] items = new String[mTranslations.size()];
      for (TranslationItem item : mTranslations) {
        if (TextUtils.isEmpty(item.translator)) {
          items[i++] = item.name;
        } else {
          items[i++] = item.translator;
        }
      }
      mTranslationItems = items;
      mTranslationReaderTask = null;

      if (mShowingTranslation) {
        updateActionBarSpinner();
      }
    }
  }

  private void toggleBookmark(final Integer sura, final Integer ayah, final int page) {
    Subscription subscription = mBookmarkModel.toggleBookmarkObservable(sura, ayah, page)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Boolean>() {
          @Override
          public void call(Boolean isBookmarked) {
            if (sura == null || ayah == null) {
              // page bookmark
              mBookmarksCache.put(page, isBookmarked);
              invalidateOptionsMenu();
            } else {
              // ayah bookmark
              SuraAyah suraAyah = new SuraAyah(sura, ayah);
              updateAyahBookmark(suraAyah, isBookmarked, true);
            }
          }
        });
    mCompositeSubscription.add(subscription);
  }

  private void checkIfPageIsBookmarked(Integer... pages) {
    Subscription subscription = mBookmarkModel.getIsBookmarkedObservable(pages)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Subscriber<Pair<Integer, Boolean>>() {
          @Override
          public void onCompleted() {
            invalidateOptionsMenu();
          }

          @Override
          public void onError(Throwable e) {
          }

          @Override
          public void onNext(Pair<Integer, Boolean> result) {
            mBookmarksCache.put(result.first, result.second);
          }
        });
    mCompositeSubscription.add(subscription);
  }

  @Override
  public void onPlayPressed() {
    if (mAudioStatusBar.getCurrentMode() == AudioStatusBar.PAUSED_MODE) {
      // if we are "paused," just un-pause.
      play(null);
      return;
    }

    int position = mViewPager.getCurrentItem();
    int page = PAGES_LAST - position;
    if (mDualPages) {
      page = ((PAGES_LAST_DUAL - position) * 2) - 1;
    }

    int startSura = QuranInfo.PAGE_SURA_START[page - 1];
    int startAyah = QuranInfo.PAGE_AYAH_START[page - 1];
    playFromAyah(page, startSura, startAyah, false);
  }

  private void playFromAyah(int page, int startSura,
                            int startAyah, boolean force) {
    final QuranAyah start = new QuranAyah(startSura, startAyah);
    playFromAyah(start, null, page, 0, 0, false, force);
  }

  public void playFromAyah(QuranAyah start, QuranAyah end,
                            int page, int verseRepeat, int rangeRepeat,
                            boolean enforceRange, boolean force) {
    if (force) {
      mShouldOverridePlaying = true;
    }

    QariItem item = mAudioStatusBar.getAudioInfo();
    if (item != null) {
      if (mSettings.shouldStream()) {
        playStreaming(start, end, page, item,
            verseRepeat, rangeRepeat, enforceRange);
      } else {
        downloadAndPlayAudio(start, end, page, item,
            verseRepeat, rangeRepeat, enforceRange);
      }
    }
  }

  private void playStreaming(QuranAyah ayah, QuranAyah end,
                            int page, QariItem item, int verseRepeat,
                            int rangeRepeat, boolean enforceRange) {
    String qariUrl = AudioUtils.getQariUrl(item, true);
    String dbFile = AudioUtils.getQariDatabasePathIfGapless(this, item);
    if (!TextUtils.isEmpty(dbFile)) {
      // gapless audio is "download only"
      downloadAndPlayAudio(ayah, end, page, item,
          verseRepeat, rangeRepeat, enforceRange);
      return;
    }

    final QuranAyah ending;
    if (end != null) {
      ending = end;
    } else {
      // this won't be enforced unless the user sets a range
      // repeat, but we set it to a sane default anyway.
      ending = AudioUtils.getLastAyahToPlay(ayah, page,
          mSettings.getPreferredDownloadAmount(), mDualPages);
    }
    AudioRequest request = new StreamingAudioRequest(qariUrl, ayah);
    request.setPlayBounds(ayah, ending);
    request.setEnforceBounds(enforceRange);
    request.setRangeRepeatCount(rangeRepeat);
    request.setVerseRepeatCount(verseRepeat);
    play(request);

    mAudioStatusBar.switchMode(AudioStatusBar.PLAYING_MODE);
    mAudioStatusBar.setRepeatCount(verseRepeat);
  }

  private void downloadAndPlayAudio(QuranAyah ayah, QuranAyah ending,
                                    int page, @NonNull QariItem item, int verseRepeat,
                                    int rangeRepeat, boolean enforceBounds) {
    final QuranAyah endAyah;
    if (ending != null) {
      endAyah = ending;
    } else {
      endAyah = AudioUtils.getLastAyahToPlay(ayah, page,
          mSettings.getPreferredDownloadAmount(), mDualPages);
    }
    String baseUri = AudioUtils.getLocalQariUrl(this, item);
    if (endAyah == null || baseUri == null) {
      return;
    }
    String dbFile = AudioUtils.getQariDatabasePathIfGapless(this, item);

    String fileUrl;
    if (TextUtils.isEmpty(dbFile)) {
      fileUrl = baseUri + File.separator + "%d" + File.separator +
          "%d" + AudioUtils.AUDIO_EXTENSION;
    } else {
      fileUrl = baseUri + File.separator + "%03d" +
          AudioUtils.AUDIO_EXTENSION;
    }

    DownloadAudioRequest request =
        new DownloadAudioRequest(fileUrl, ayah, item, baseUri);
    request.setGaplessDatabaseFilePath(dbFile);
    request.setPlayBounds(ayah, endAyah);
    request.setEnforceBounds(enforceBounds);
    request.setRangeRepeatCount(rangeRepeat);
    request.setVerseRepeatCount(verseRepeat);
    mLastAudioDownloadRequest = request;
    playAudioRequest(request);
  }

  private void playAudioRequest(DownloadAudioRequest request) {
    if (request == null) {
      mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
      return;
    }

    boolean needsPermission = mNeedsPermissionToDownloadOver3g;
    if (needsPermission) {
      if (QuranUtils.isOnWifiNetwork(this)) {
        Timber.d("on wifi, don't need permission for download...");
        needsPermission = false;
      }
    }

    Timber.d("seeing if we can play audio request...");
    if (!QuranFileUtils.haveAyaPositionFile(this)) {
      if (needsPermission) {
        mAudioStatusBar.switchMode(AudioStatusBar.PROMPT_DOWNLOAD_MODE);
        return;
      }

      if (mIsActionBarHidden) {
        toggleActionBar();
      }
      mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);
      String url = QuranFileUtils.getAyaPositionFileUrl();
      String destination = QuranFileUtils.getQuranDatabaseDirectory(this);
      // start the download
      String notificationTitle = getString(R.string.highlighting_database);
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
          destination, notificationTitle, AUDIO_DOWNLOAD_KEY,
          QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
      startService(intent);
    } else if (AudioUtils.shouldDownloadGaplessDatabase(request)) {
      Timber.d("need to download gapless database...");
      if (needsPermission) {
        mAudioStatusBar.switchMode(AudioStatusBar.PROMPT_DOWNLOAD_MODE);
        return;
      }

      if (mIsActionBarHidden) {
        toggleActionBar();
      }
      mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);
      String url = AudioUtils.getGaplessDatabaseUrl(request);
      String destination = request.getLocalPath();
      // start the download
      String notificationTitle = getString(R.string.timing_database);
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
          destination, notificationTitle, AUDIO_DOWNLOAD_KEY,
          QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
      startService(intent);
    } else if (AudioUtils.haveAllFiles(request)) {
      if (!AudioUtils.shouldDownloadBasmallah(request)) {
        Timber.d("have all files, playing!");
        play(request);
        mLastAudioDownloadRequest = null;
      } else {
        Timber.d("should download basmalla...");
        if (needsPermission) {
          mAudioStatusBar.switchMode(AudioStatusBar.PROMPT_DOWNLOAD_MODE);
          return;
        }

        QuranAyah firstAyah = new QuranAyah(1, 1);
        String qariUrl = AudioUtils.getQariUrl(request.getQariItem(), true);
        mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);

        if (mIsActionBarHidden) {
          toggleActionBar();
        }
        String notificationTitle = QuranInfo.getNotificationTitle(
            this, firstAyah, firstAyah, request.isGapless());
        Intent intent = ServiceIntentHelper.getDownloadIntent(this, qariUrl,
            request.getLocalPath(), notificationTitle,
            AUDIO_DOWNLOAD_KEY,
            QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
        intent.putExtra(QuranDownloadService.EXTRA_START_VERSE, firstAyah);
        intent.putExtra(QuranDownloadService.EXTRA_END_VERSE, firstAyah);
        startService(intent);
      }
    } else {
      if (needsPermission) {
        mAudioStatusBar.switchMode(AudioStatusBar.PROMPT_DOWNLOAD_MODE);
        return;
      }

      if (mIsActionBarHidden) {
        toggleActionBar();
      }
      mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);

      String notificationTitle = QuranInfo.getNotificationTitle(this,
          request.getMinAyah(), request.getMaxAyah(), request.isGapless());
      String qariUrl = AudioUtils.getQariUrl(request.getQariItem(), true);
      Timber.d("need to start download: " + qariUrl);

      // start service
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, qariUrl,
          request.getLocalPath(), notificationTitle, AUDIO_DOWNLOAD_KEY,
          QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
      intent.putExtra(QuranDownloadService.EXTRA_START_VERSE,
          request.getMinAyah());
      intent.putExtra(QuranDownloadService.EXTRA_END_VERSE,
          request.getMaxAyah());
      intent.putExtra(QuranDownloadService.EXTRA_IS_GAPLESS,
          request.isGapless());
      startService(intent);
    }
  }

  private void play(AudioRequest request) {
    mNeedsPermissionToDownloadOver3g = true;
    Intent i = new Intent(this, AudioService.class);
    i.setAction(AudioService.ACTION_PLAYBACK);
    if (request != null) {
      i.putExtra(AudioService.EXTRA_PLAY_INFO, request);
      mLastAudioRequest = request;
      mAudioStatusBar.setRepeatCount(request.getVerseRepeatCount());
    }

    if (mShouldOverridePlaying) {
      // force the current audio to stop and start playing new request
      i.putExtra(AudioService.EXTRA_STOP_IF_PLAYING, true);
      mShouldOverridePlaying = false;
    }
    // just a playback request, so tell audio service to just continue
    // playing (and don't store new audio data) if it was already playing
    else {
      i.putExtra(AudioService.EXTRA_IGNORE_IF_PLAYING, true);
    }
    startService(i);
  }

  @Override
  public void onPausePressed() {
    startService(AudioUtils.getAudioIntent(
        this, AudioService.ACTION_PAUSE));
    mAudioStatusBar.switchMode(AudioStatusBar.PAUSED_MODE);
  }

  @Override
  public void onNextPressed() {
    startService(AudioUtils.getAudioIntent(this,
        AudioService.ACTION_SKIP));
  }

  @Override
  public void onPreviousPressed() {
    startService(AudioUtils.getAudioIntent(this,
        AudioService.ACTION_REWIND));
  }

  @Override
  public void onAudioSettingsPressed() {
    if (mLastPlayingSura != null) {
      mStart = new SuraAyah(mLastPlayingSura, mLastPlayingAyah);
      mEnd = mStart;
    }

    if (mStart == null) {
      final Integer[] bounds = QuranInfo.getPageBounds(getCurrentPage());
      mStart = new SuraAyah(bounds[0], bounds[1]);
      mEnd = mStart;
    }
    showSlider(AUDIO_PAGE);
  }

  public boolean updatePlayOptions(int rangeRepeat,
      int verseRepeat, boolean enforceRange) {
    if (mLastAudioRequest != null) {
      Intent i = new Intent(this, AudioService.class);
      i.setAction(AudioService.ACTION_UPDATE_REPEAT);
      i.putExtra(AudioService.EXTRA_VERSE_REPEAT_COUNT, verseRepeat);
      i.putExtra(AudioService.EXTRA_RANGE_REPEAT_COUNT, rangeRepeat);
      i.putExtra(AudioService.EXTRA_RANGE_RESTRICT, enforceRange);
      startService(i);

      mLastAudioRequest.setVerseRepeatCount(verseRepeat);
      mLastAudioRequest.setRangeRepeatCount(rangeRepeat);
      mLastAudioRequest.setEnforceBounds(enforceRange);
      mAudioStatusBar.setRepeatCount(verseRepeat);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void setRepeatCount(int repeatCount) {
    if (mLastAudioRequest != null) {
      Intent i = new Intent(this, AudioService.class);
      i.setAction(AudioService.ACTION_UPDATE_REPEAT);
      i.putExtra(AudioService.EXTRA_VERSE_REPEAT_COUNT, repeatCount);
      startService(i);
      mLastAudioRequest.setVerseRepeatCount(repeatCount);
    }
  }

  @Override
  public void onStopPressed() {
    startService(AudioUtils.getAudioIntent(this, AudioService.ACTION_STOP));
    mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
    unHighlightAyahs(HighlightType.AUDIO);
    mLastAudioRequest = null;
  }

  @Override
  public void onCancelPressed(boolean cancelDownload) {
    if (cancelDownload) {
      mNeedsPermissionToDownloadOver3g = true;

      int resId = R.string.canceling;
      mAudioStatusBar.setProgressText(getString(resId), true);
      Intent i = new Intent(this, QuranDownloadService.class);
      i.setAction(QuranDownloadService.ACTION_CANCEL_DOWNLOADS);
      startService(i);
    } else {
      mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
    }
  }

  @Override
  public void onAcceptPressed() {
    if (mLastAudioDownloadRequest != null) {
      mNeedsPermissionToDownloadOver3g = false;
      playAudioRequest(mLastAudioDownloadRequest);
    }
  }

  // #######################################################################
  // ####################    AYAH ACTION PANEL STUFF    ####################
  // #######################################################################

  @Override
  public void onBackPressed() {
    if (mIsInAyahMode) {
      endAyahMode();
    } else if (mShowingTranslation) {
      switchToQuran();
    } else {
      super.onBackPressed();
    }
  }

  @Override
  public boolean isListeningForAyahSelection(EventType eventType) {
    return eventType == EventType.LONG_PRESS ||
        eventType == EventType.SINGLE_TAP && mIsInAyahMode;
  }

  @Override
  public boolean onAyahSelected(EventType eventType,
      SuraAyah suraAyah, AyahTracker tracker) {
    switch (eventType) {
      case SINGLE_TAP:
        if (mIsInAyahMode) {
          updateAyahStartSelection(suraAyah, tracker);
          return true;
        }
        return false;
      case LONG_PRESS:
        if (mIsInAyahMode) {
          updateAyahEndSelection(suraAyah);
        } else {
          startAyahMode(suraAyah, tracker);
        }
        mViewPager.performHapticFeedback(
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
        if (!mIsInAyahMode) {
          toggleActionBar();
          return true;
        }
        return false;
      case DOUBLE_TAP:
        if (mIsInAyahMode) {
          endAyahMode();
          return true;
        }
        return false;
      default:
        return false;
    }
  }

  public SuraAyah getSelectionStart() {
    return mStart;
  }

  public SuraAyah getSelectionEnd() {
    return mEnd;
  }

  public AudioRequest getLastAudioRequest() {
    return mLastAudioRequest;
  }

  public void startAyahMode(SuraAyah suraAyah, AyahTracker tracker) {
    if (!mIsInAyahMode) {
      mStart = mEnd = suraAyah;
      updateToolbarPosition(suraAyah, tracker);
      mAyahToolBar.showMenu();
      showAyahModeHighlights(suraAyah, tracker);
      mIsInAyahMode = true;
    }
  }

  public void endAyahMode() {
    mAyahToolBar.hideMenu();
    mSlidingPanel.collapsePane();
    clearAyahModeHighlights();
    mIsInAyahMode = false;
  }

  public void nextAyah() {
    final int ayat = QuranInfo.getNumAyahs(mEnd.sura);

    final SuraAyah s;
    if (mEnd.ayah + 1 <= ayat) {
      s = new SuraAyah(mEnd.sura, mEnd.ayah + 1);
    } else if (mEnd.sura < 114) {
      s = new SuraAyah(mEnd.sura + 1, 1);
    } else {
      return;
    }
    selectAyah(s);
  }

  public void previousAyah() {
    final SuraAyah s;
    if (mEnd.ayah > 1) {
      s = new SuraAyah(mEnd.sura, mEnd.ayah - 1);
    } else if (mEnd.sura > 1) {
      s = new SuraAyah(mEnd.sura - 1, QuranInfo.getNumAyahs(mEnd.sura - 1));
    } else {
      return;
    }
    selectAyah(s);
  }

  private void selectAyah(SuraAyah s) {
    final int page = s.getPage();
    final int position = QuranInfo.getPosFromPage(page, mDualPages);
    Fragment f = mPagerAdapter.getFragmentIfExists(position);
    if (f instanceof AyahTracker) {
      if (position != mViewPager.getCurrentItem()) {
        mViewPager.setCurrentItem(position);
      }
      updateAyahStartSelection(s, (AyahTracker) f);
    }
  }

  public void updateAyahStartSelection(
      SuraAyah suraAyah, AyahTracker tracker) {
    if (mIsInAyahMode) {
      clearAyahModeHighlights();
      mStart = mEnd = suraAyah;
      if (mAyahToolBar.isShowing()) {
        mAyahToolBar.resetMenu();
        updateToolbarPosition(suraAyah, tracker);
      }
      if (mSlidingPanel.isPaneVisible()) {
        refreshPages();
      }
      showAyahModeHighlights(suraAyah, tracker);
    }
  }

  public void updateAyahEndSelection(SuraAyah suraAyah) {
    if (mIsInAyahMode) {
      clearAyahModeHighlights();
      if (suraAyah.after(mStart)) {
        mEnd = suraAyah;
      } else {
        mEnd = mStart;
        mStart = suraAyah;
      }
      if (mSlidingPanel.isPaneVisible()) {
        refreshPages();
      }
      showAyahModeRangeHighlights();
    }
  }

  private void updateToolbarPosition(final SuraAyah start, AyahTracker tracker) {
    Subscription subscription = BookmarkModel.getInstance(this)
        .getIsBookmarkedObservable(start.sura, start.ayah, start.getPage())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Boolean>() {
          @Override
          public void call(Boolean isBookmarked) {
            updateAyahBookmark(start, isBookmarked, false);
          }
        });
    mCompositeSubscription.add(subscription);

    mAyahToolBarPos = tracker.getToolBarPosition(start.sura, start.ayah,
            mAyahToolBar.getToolBarWidth(), mAyahToolBarTotalHeight);
    mAyahToolBar.updatePosition(mAyahToolBarPos);
    if (mAyahToolBar.getVisibility() != View.VISIBLE) {
      mAyahToolBar.setVisibility(View.VISIBLE);
    }
  }

  // Used to sync toolbar with page's SV (landscape non-tablet mode)
  public void onQuranPageScroll(int scrollY) {
    if (mAyahToolBarPos != null) {
      mAyahToolBarPos.yScroll = 0 - scrollY;
      if (mIsInAyahMode) {
        mAyahToolBar.updatePosition(mAyahToolBarPos);
      }
    }
  }

  private void refreshPages() {
    for (int page : PAGES) {
      final int mappedTagPage = mSlidingPagerAdapter.getPagePosition(TAG_PAGE);
      if (page == mappedTagPage) {
        TagBookmarkDialog tagsFrag =
            (TagBookmarkDialog) mSlidingPagerAdapter
                .getFragmentIfExists(mappedTagPage);
        if (tagsFrag != null) {
          tagsFrag.updateAyah(mStart);
        }
      } else {
        AyahActionFragment f = (AyahActionFragment) mSlidingPagerAdapter
            .getFragmentIfExists(page);
        if (f != null) {
          f.updateAyahSelection(mStart, mEnd);
        }
      }
    }
  }

  private void showAyahModeRangeHighlights() {
    // Determine the start and end of the selection
    int minPage = Math.min(mStart.getPage(), mEnd.getPage());
    int maxPage = Math.max(mStart.getPage(), mEnd.getPage());
    SuraAyah start = SuraAyah.min(mStart, mEnd);
    SuraAyah end = SuraAyah.max(mStart, mEnd);
    // Iterate from beginning to end
    for (int i = minPage; i <= maxPage; i++) {
      AyahTracker fragment = mPagerAdapter.getFragmentIfExistsForPage(i);
      if (fragment != null) {
        Set<String> ayahKeys = QuranInfo.getAyahKeysOnPage(i, start, end);
        fragment.highlightAyat(i, ayahKeys, HighlightType.SELECTION);
      }
    }
  }

  private void showAyahModeHighlights(SuraAyah suraAyah, AyahTracker tracker) {
    tracker.highlightAyah(
        suraAyah.sura, suraAyah.ayah, HighlightType.SELECTION, false);
  }

  private void clearAyahModeHighlights() {
    if (mIsInAyahMode) {
      for (int i = mStart.getPage(); i <= mEnd.getPage(); i++) {
        AyahTracker fragment = mPagerAdapter.getFragmentIfExistsForPage(i);
        if (fragment != null) {
          fragment.unHighlightAyahs(HighlightType.SELECTION);
        }
      }
    }
  }

  private class AyahMenuItemSelectionHandler implements MenuItem.OnMenuItemClickListener {
    @Override
    public boolean onMenuItemClick(MenuItem item) {
      int sliderPage = -1;
      switch (item.getItemId()) {
        case R.id.cab_bookmark_ayah:
          toggleBookmark(mStart.sura, mStart.ayah, mStart.getPage());
          break;
        case R.id.cab_tag_ayah:
          sliderPage = mSlidingPagerAdapter.getPagePosition(TAG_PAGE);
          break;
        case R.id.cab_translate_ayah:
          sliderPage = mSlidingPagerAdapter.getPagePosition(TRANSLATION_PAGE);
          break;
        case R.id.cab_play_from_here:
          sliderPage = mSlidingPagerAdapter.getPagePosition(AUDIO_PAGE);
          break;
        case R.id.cab_share_ayah_link:
          new ShareQuranAppTask(PagerActivity.this, mStart, mEnd).execute();
          break;
        case R.id.cab_share_ayah_text:
          new ShareAyahTask(PagerActivity.this, mStart, mEnd, false).execute();
          break;
        case R.id.cab_copy_ayah:
          new ShareAyahTask(PagerActivity.this, mStart, mEnd, true).execute();
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

  private void showSlider(int sliderPage) {
    mAyahToolBar.hideMenu();
    mSlidingPager.setCurrentItem(sliderPage);
    mSlidingPanel.showPane();
    // TODO there's got to be a better way than this hack
    // The issue is that smoothScrollTo returns if mCanSlide is false
    // and it's false when the panel is GONE and showPane only calls
    // requestLayout, and only in onLayout does mCanSlide become true.
    // So by posting this later it gives time for onLayout to run.
    // Another issue is that the fragments haven't been created yet
    // (on first run), so calling refreshPages() before then won't work.
    mHandler.post(new Runnable() {
      @Override
      public void run() {
        mSlidingPanel.expandPane();
        refreshPages();
      }
    });
  }

  public void updateAyahBookmark(
      SuraAyah suraAyah, boolean bookmarked, boolean refreshHighlight) {
    // Refresh toolbar icon
    if (mIsInAyahMode && mStart.equals(suraAyah)) {
      mAyahToolBar.setBookmarked(bookmarked);
    }
    // Refresh highlight
    if (refreshHighlight && mSettings.shouldHighlightBookmarks()) {
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
      if (mIsInAyahMode) {
        endAyahMode();
      }
      mSlidingPanel.hidePane();
    }

    @Override
    public void onPanelExpanded(View panel) {
    }

    @Override
    public void onPanelAnchored(View panel) {
    }
  }
}
