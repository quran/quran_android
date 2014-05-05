package com.quran.labs.androidquran.ui;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.service.AudioService;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.AudioRequest;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.DownloadAudioRequest;
import com.quran.labs.androidquran.service.util.RepeatInfo;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.fragment.AddTagDialog;
import com.quran.labs.androidquran.ui.fragment.JumpFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;
import com.quran.labs.androidquran.ui.fragment.TranslationFragment;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.BookmarkHandler;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.ui.helpers.AyahActionPanel;
import com.quran.labs.androidquran.util.AsyncTask;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.TranslationUtils;
import com.quran.labs.androidquran.widgets.AudioStatusBar;
import com.quran.labs.androidquran.widgets.HighlightingImageView;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PagerActivity extends SherlockFragmentActivity implements
    AudioStatusBar.AudioBarListener,
    BookmarkHandler,
    DefaultDownloadReceiver.DownloadListener,
    TagBookmarkDialog.OnBookmarkTagsUpdateListener,
    AddTagDialog.OnTagChangedListener,
    AyahSelectedListener {
  private static final String TAG = "PagerActivity";
  private static final String AUDIO_DOWNLOAD_KEY = "AUDIO_DOWNLOAD_KEY";
  private static final String LAST_AUDIO_DL_REQUEST = "LAST_AUDIO_DL_REQUEST";
  private static final String LAST_READ_PAGE = "LAST_READ_PAGE";
  private static final String LAST_READING_MODE_IS_TRANSLATION =
      "LAST_READING_MODE_IS_TRANSLATION";
  private static final String LAST_ACTIONBAR_STATE = "LAST_ACTIONBAR_STATE";

  public static final String EXTRA_JUMP_TO_TRANSLATION = "jumpToTranslation";
  public static final String EXTRA_HIGHLIGHT_SURA = "highlightSura";
  public static final String EXTRA_HIGHLIGHT_AYAH = "highlightAyah";
  public static final String LAST_WAS_DUAL_PAGES = "wasDualPages";

  private static final long DEFAULT_HIDE_AFTER_TIME = 2000;

  private QuranPageWorker mWorker = null;
  private SharedPreferences mPrefs = null;
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
  private boolean mShouldOverridePlaying = false;
  private DefaultDownloadReceiver mDownloadReceiver;
  private boolean mNeedsPermissionToDownloadOver3g = true;
  private AlertDialog mPromptDialog = null;
  private List<TranslationItem> mTranslations;
  private String[] mTranslationItems;
  private TranslationReaderTask mTranslationReaderTask;
  private SpinnerAdapter mSpinnerAdapter;
  private BookmarksDBAdapter mBookmarksAdapter;
  private AyahInfoDatabaseHandler mAyahInfoAdapter, mTabletAyahInfoAdapter;
  private boolean mDualPages = false;
  private AyahActionPanel mAyahActionPanel;

  public static final int MSG_HIDE_ACTIONBAR = 1;

  private Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      if (msg.what == MSG_HIDE_ACTIONBAR) {
        toggleActionBarVisibility(false);
      } else {
        super.handleMessage(msg);
      }
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    ((QuranApplication) getApplication()).refreshLocale(false);

    setTheme(R.style.QuranAndroid);
    requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

    super.onCreate(savedInstanceState);
    mBookmarksCache = new SparseBooleanArray();
    mBookmarksAdapter = new BookmarksDBAdapter(this);

    boolean refresh = false;
    // make sure to remake QuranScreenInfo if it doesn't exist, as it
    // is needed to get images, to get the highlighting db, etc.
    QuranScreenInfo qsi = QuranScreenInfo.getOrMakeInstance(this);
    mDualPages = QuranUtils.isDualPages(this, qsi);

    // initialize ayah info database
    String filename = QuranFileUtils.getAyaPositionFileName();
    try {
      mAyahInfoAdapter = new AyahInfoDatabaseHandler(this, filename);
    } catch (Exception e) {
      // no ayah info database available
    }

    mTabletAyahInfoAdapter = null;
    if (qsi.isTablet(this)) {
      try {
        filename = QuranFileUtils.getAyaPositionFileName(
            qsi.getTabletWidthParam());
        mTabletAyahInfoAdapter =
            new AyahInfoDatabaseHandler(this, filename);
      } catch (Exception e) {
        // no ayah info database available for tablet
      }
    }

    int page = -1;

    mIsActionBarHidden = true;
    if (savedInstanceState != null) {
      android.util.Log.d(TAG, "non-null saved instance state!");
      Serializable lastAudioRequest =
          savedInstanceState.getSerializable(LAST_AUDIO_DL_REQUEST);
      if (lastAudioRequest != null &&
          lastAudioRequest instanceof DownloadAudioRequest) {
        android.util.Log.d(TAG, "restoring request from saved instance!");
        mLastAudioDownloadRequest = (DownloadAudioRequest) lastAudioRequest;
      }
      page = savedInstanceState.getInt(LAST_READ_PAGE, -1);
      if (page != -1) {
        page = Constants.PAGES_LAST - page;
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
    }

    mPrefs = PreferenceManager.getDefaultSharedPreferences(
        getApplicationContext());

    getSupportActionBar().setDisplayShowHomeEnabled(true);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    int background = getResources().getColor(
        R.color.transparent_actionbar_color);
    setContentView(R.layout.quran_page_activity_slider);
    getSupportActionBar().setBackgroundDrawable(
        new ColorDrawable(background));
    mAudioStatusBar = (AudioStatusBar) findViewById(R.id.audio_area);
    mAudioStatusBar.setAudioBarListener(this);

    // Try initializing the AyahActionPanel
    try {
      mAyahActionPanel = new AyahActionPanel(this);
    } catch (Exception e) {
      // TODO do something, at least report to crashlytics
    }

    Intent intent = getIntent();
    Bundle extras = intent.getExtras();
    if (extras != null) {
      if (page == -1) {
        page = Constants.PAGES_LAST -
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
      updateActionBarTitle(Constants.PAGES_LAST - page);
    }

    mWorker = QuranPageWorker.getInstance(this);
    mLastPopupTime = System.currentTimeMillis();
    mPagerAdapter = new QuranPageAdapter(
        getSupportFragmentManager(), mDualPages, mShowingTranslation);
    mViewPager = (ViewPager) findViewById(R.id.quran_pager);
    mViewPager.setAdapter(mPagerAdapter);

    mViewPager.setOnPageChangeListener(new OnPageChangeListener() {

      @Override
      public void onPageScrollStateChanged(int state) {
      }

      @Override
      public void onPageScrolled(int position, float positionOffset,
                                 int positionOffsetPixels) {
      }

      @Override
      public void onPageSelected(int position) {
        Log.d(TAG, "onPageSelected(): " + position);
        int page = Constants.PAGES_LAST - position;
        if (mDualPages) {
          page = (302 - position) * 2;
        }
        QuranSettings.setLastPage(PagerActivity.this, page);
        if (QuranSettings.shouldDisplayMarkerPopup(PagerActivity.this)) {
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
              new IsPageBookmarkedTask().execute(page - 1, page);
            }
          } else {
            // we don't have the key
            new IsPageBookmarkedTask().execute(page);
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

    QuranSettings.setLastPage(this, Constants.PAGES_LAST - page);
    setLoading(false);

    // just got created, need to reconnect to service
    mShouldReconnect = true;

    // enforce orientation lock
    if (QuranSettings.isLockOrientation(this)) {
      int current = getResources().getConfiguration().orientation;
      if (QuranSettings.isLandscapeOrientation(this)) {
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
      final int curPage = Constants.PAGES_LAST - page;
      mHandler.post(new Runnable() {
        @Override
        public void run() {
          mPagerAdapter.notifyDataSetChanged();
          int page = curPage;
          if (mDualPages) {
            if (page % 2 != 0) {
              page++;
            }
            page = 302 - (page / 2);
          } else {
            if (page % 2 == 0) {
              page--;
            }
            page = Constants.PAGES_LAST - page;
          }
          mViewPager.setCurrentItem(page);
        }
      });
    }
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
    int flags;
    if (isVisible){
      flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
    } else {
      flags = View.SYSTEM_UI_FLAG_LOW_PROFILE
          | View.SYSTEM_UI_FLAG_FULLSCREEN
          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
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
        if (visible){
          mAudioStatusBar.updateSelectedItem();
          getSherlock().getActionBar().show();
        } else {
          getSherlock().getActionBar().hide();
        }

        mAudioStatusBar.animate()
            .translationY(visible? 0 : mAudioStatusBar.getHeight())
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
  public boolean onKeyUp(int keyCode, KeyEvent event) {
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

    mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
    LocalBroadcastManager.getInstance(this).registerReceiver(
        mAudioReceiver,
        new IntentFilter(AudioService.AudioUpdateIntent.INTENT_NAME));

    mDownloadReceiver = new DefaultDownloadReceiver(this,
        QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
    String action = QuranDownloadService.ProgressIntent.INTENT_NAME;
    LocalBroadcastManager.getInstance(this).registerReceiver(
        mDownloadReceiver,
        new IntentFilter(action));
    mDownloadReceiver.setListener(this);

    super.onResume();
    if (mShouldReconnect) {
      startService(new Intent(AudioService.ACTION_CONNECT));
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

  @Override
  public BookmarksDBAdapter getBookmarksAdapter() {
    return mBookmarksAdapter;
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
      String destination = QuranFileUtils.getQuranDatabaseDirectory(this);
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
      int page = Constants.PAGES_LAST -
          extras.getInt("page", Constants.PAGES_FIRST);
      updateActionBarTitle(Constants.PAGES_LAST - page);

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

  @Override
  public void onPause() {
    if (mPromptDialog != null) {
      mPromptDialog.dismiss();
      mPromptDialog = null;
    }
    mSpinnerAdapter = null;
    LocalBroadcastManager.getInstance(this)
        .unregisterReceiver(mAudioReceiver);
    if (mDownloadReceiver != null) {
      mDownloadReceiver.setListener(null);
      LocalBroadcastManager.getInstance(this)
          .unregisterReceiver(mDownloadReceiver);
      mDownloadReceiver = null;
    }
    if (mAyahActionPanel != null && mAyahActionPanel.isInActionMode()) {
      mAyahActionPanel.endActionMode();
    }
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    android.util.Log.d(TAG, "onDestroy()");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      clearUiVisibilityListener();
    }

    mBookmarksAdapter.close();
    if (mAyahInfoAdapter != null) {
      mAyahInfoAdapter.closeDatabase();
    }

    if (mTabletAyahInfoAdapter != null) {
      mTabletAyahInfoAdapter.closeDatabase();
    }

    if (mAyahActionPanel != null) {
      mAyahActionPanel.cleanup();
      mAyahActionPanel = null;
    }

    super.onDestroy();
  }

  @Override
  public void onSaveInstanceState(Bundle state) {
    if (mLastAudioDownloadRequest != null) {
      state.putSerializable(LAST_AUDIO_DL_REQUEST,
          mLastAudioDownloadRequest);
    }
    int lastPage = Constants.PAGES_LAST - mViewPager.getCurrentItem();
    if (mDualPages) {
      lastPage = 302 - mViewPager.getCurrentItem();
      lastPage *= 2;
    }
    state.putSerializable(LAST_READ_PAGE, lastPage);
    state.putBoolean(LAST_READING_MODE_IS_TRANSLATION, mShowingTranslation);
    state.putBoolean(LAST_ACTIONBAR_STATE, mIsActionBarHidden);
    state.putBoolean(LAST_WAS_DUAL_PAGES, mDualPages);
    super.onSaveInstanceState(state);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    MenuInflater inflater = getSupportMenuInflater();
    inflater.inflate(R.menu.quran_menu, menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    MenuItem item = menu.findItem(R.id.favorite_item);
    if (item != null) {
      int page = Constants.PAGES_LAST - mViewPager.getCurrentItem();
      if (mDualPages) {
        page = (302 - mViewPager.getCurrentItem()) * 2;
      }

      boolean bookmarked = false;
      if (mBookmarksCache.indexOfKey(page) >= 0) {
        bookmarked = mBookmarksCache.get(page);
      }

      if (!bookmarked && mDualPages &&
          mBookmarksCache.indexOfKey(page - 1) >= 0) {
        bookmarked = mBookmarksCache.get(page - 1);
      }

      if (bookmarked) {
        item.setIcon(R.drawable.favorite);
      } else {
        item.setIcon(R.drawable.not_favorite);
      }
    }

    MenuItem quran = menu.findItem(R.id.goto_quran);
    MenuItem translation = menu.findItem(R.id.goto_translation);
    if (!mShowingTranslation) {
      quran.setVisible(false);
      translation.setVisible(true);
    } else {
      quran.setVisible(true);
      translation.setVisible(false);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.favorite_item) {
      int page = getCurrentPage();
      toggleBookmark(null, null, page);
      return true;
    } else if (item.getItemId() == R.id.goto_quran) {
      mPagerAdapter.setQuranMode();
      mShowingTranslation = false;
      int page = getCurrentPage();
      invalidateOptionsMenu();
      updateActionBarTitle(page);
      return true;
    } else if (item.getItemId() == R.id.goto_translation) {
      switchToTranslation();
      return true;
    } else if (item.getItemId() == R.id.settings) {
      Intent i = new Intent(this, QuranPreferenceActivity.class);
      startActivity(i);
      return true;
    } else if (item.getItemId() == R.id.help) {
      Intent i = new Intent(this, HelpActivity.class);
      startActivity(i);
      return true;
    } else if (item.getItemId() == R.id.search) {
      return onSearchRequested();
    } else if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    } else if (item.getItemId() == R.id.jump) {
      FragmentManager fm = getSupportFragmentManager();
      JumpFragment jumpDialog = new JumpFragment();
      jumpDialog.show(fm, JumpFragment.TAG);
      return true;
    }
    return super.onOptionsItemSelected(item);
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
    int currentPage = Constants.PAGES_LAST - position;
    if (currentPage == page) {
      setLoading(true);
    }
  }

  public void switchToTranslation() {
    String activeDatabase = TranslationUtils.getDefaultTranslation(
        this, mTranslations);
    if (activeDatabase == null) {
      Intent i = new Intent(this, TranslationManagerActivity.class);
      startActivity(i);
    } else {
      mPagerAdapter.setTranslationMode();
      mShowingTranslation = true;
      invalidateOptionsMenu();
      updateActionBarSpinner();
    }
  }

  ActionBar.OnNavigationListener mNavigationCallback =
      new ActionBar.OnNavigationListener() {
        @Override
        public boolean onNavigationItemSelected(int itemPosition,
                                                long itemId) {
          Log.d(TAG, "item chosen: " + itemPosition);
          if (mTranslations != null &&
              mTranslations.size() > itemPosition) {
            TranslationItem item = mTranslations.get(itemPosition);
            mPrefs.edit().putString(Constants.PREF_ACTIVE_TRANSLATION,
                item.filename).commit();

            int pos = mViewPager.getCurrentItem() - 1;
            for (int count = 0; count < 3; count++) {
              if (pos + count < 0) {
                continue;
              }
              Fragment f = mPagerAdapter
                  .getFragmentIfExists(pos + count);
              if (f != null && f instanceof TranslationFragment) {
                ((TranslationFragment) f).refresh(item.filename);
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

  public void toggleBookmark(Integer sura, Integer ayah, int page) {
    new ToggleBookmarkTask().execute(sura, ayah, page);
  }

  @Override
  public void onAddTagSelected() {
    if (mAyahActionPanel != null) {
      mAyahActionPanel.onAddTagSelected();
    }
  }

  @Override
  public void onBookmarkTagsUpdated() {
    if (mAyahActionPanel != null) {
      mAyahActionPanel.onBookmarkTagsUpdated();
    }
  }

  @Override
  public void onTagAdded(final String name) {
    if (mAyahActionPanel != null) {
      mAyahActionPanel.onTagAdded(name);
    }
  }

  @Override
  public void onTagUpdated(long id, String name) {
    if (mAyahActionPanel != null) {
      mAyahActionPanel.onTagUpdated(id, name);
    }
  }

  private void updateActionBarTitle(int page) {
    String sura = QuranInfo.getSuraNameFromPage(this, page, true);
    ActionBar actionBar = getSupportActionBar();
    actionBar.setDisplayShowTitleEnabled(true);
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    actionBar.setTitle(sura);
    String desc = QuranInfo.getPageSubtitle(this, page);
    actionBar.setSubtitle(desc);
    mSpinnerAdapter = null;
  }

  private static class SpinnerHolder {
    TextView title;
    TextView subtitle;
  }

  private void refreshActionBarSpinner() {
    if (mSpinnerAdapter != null) {
      if (mSpinnerAdapter instanceof ArrayAdapter) {
        ((ArrayAdapter) mSpinnerAdapter).notifyDataSetChanged();
      } else {
        updateActionBarSpinner();
      }
    } else {
      updateActionBarSpinner();
    }
  }

  private int getCurrentPage() {
    if (mDualPages) {
      return (302 - mViewPager.getCurrentItem()) * 2;
    }
    return Constants.PAGES_LAST - mViewPager.getCurrentItem();
  }

  private void updateActionBarSpinner() {
    if (mTranslationItems == null || mTranslationItems.length == 0) {
      int page = getCurrentPage();
      updateActionBarTitle(page);
      return;
    }

    mSpinnerAdapter = new ArrayAdapter<String>(this,
        R.layout.sherlock_spinner_dropdown_item,
        mTranslationItems) {
      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        SpinnerHolder holder;
        if (convertView == null) {
          holder = new SpinnerHolder();
          convertView = getLayoutInflater().inflate(
              R.layout.translation_ab_spinner_selected,
              parent, false);
          holder.title = (TextView) convertView.findViewById(R.id.title);
          holder.subtitle = (TextView) convertView.findViewById(
              R.id.subtitle);
          convertView.setTag(holder);
        }
        holder = (SpinnerHolder) convertView.getTag();

        holder.title.setText(mTranslationItems[position]);
        int page = getCurrentPage();
        String subtitle = QuranInfo.getPageSubtitle(
            PagerActivity.this, page);
        holder.subtitle.setText(subtitle);
        return convertView;
      }
    };

    // figure out which translation should be selected
    int selected = 0;
    String activeTranslation = TranslationUtils
        .getDefaultTranslation(this, mTranslations);
    if (activeTranslation != null) {
      int index = 0;
      for (TranslationItem item : mTranslations) {
        if (item.filename.equals(activeTranslation)) {
          selected = index;
          break;
        } else {
          index++;
        }
      }
    }

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
        if (state == AudioService.AudioUpdateIntent.PLAYING) {
          mAudioStatusBar.switchMode(AudioStatusBar.PLAYING_MODE);
          highlightAyah(sura, ayah, HighlightType.AUDIO);
        } else if (state == AudioService.AudioUpdateIntent.PAUSED) {
          mAudioStatusBar.switchMode(AudioStatusBar.PAUSED_MODE);
          highlightAyah(sura, ayah, HighlightType.AUDIO);
        } else if (state == AudioService.AudioUpdateIntent.STOPPED) {
          mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
          unHighlightAyahs(HighlightType.AUDIO);

          Serializable qi = intent.getSerializableExtra(
              AudioService.EXTRA_PLAY_INFO);
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
    if (visible && mIsActionBarHidden) {
      toggleActionBar();
    } else if (!visible && !mIsActionBarHidden) {
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
        getSupportActionBar().show();

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
        getSupportActionBar().hide();

        mAudioStatusBar.setVisibility(View.GONE);
      }

      mIsActionBarHidden = true;
    }
  }

  public QuranPageWorker getQuranPageWorker() {
    return mWorker;
  }

  public void highlightAyah(int sura, int ayah, HighlightType type) {
    highlightAyah(sura, ayah, true, type);
  }

  public void highlightAyah(int sura, int ayah, boolean force, HighlightType type) {
    Log.d(TAG, "highlightAyah() - " + sura + ":" + ayah);
    int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
    if (page < Constants.PAGES_FIRST ||
        Constants.PAGES_LAST < page) {
      return;
    }

    int position = Constants.PAGES_LAST - page;
    if (mDualPages) {
      if (page % 2 != 0) {
        page++;
      }
      position = 302 - (page / 2);
    }

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
        Log.d(TAG, "error getting translations list", e);
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
        items[i++] = item.name;
      }
      mTranslationItems = items;
      mTranslationReaderTask = null;

      if (mShowingTranslation) {
        updateActionBarSpinner();
      }
    }
  }

  class ToggleBookmarkTask extends AsyncTask<Integer, Void, Boolean> {
    private Integer mSura;
    private Integer mAyah;
    private int mPage;
    private boolean mPageOnly;

    @Override
    protected Boolean doInBackground(Integer... params) {
      mSura = params[0];
      mAyah = params[1];
      mPage = params[2];
      mPageOnly = (mSura == null || mAyah == null);

      boolean result = false;
      long bookmarkId = mBookmarksAdapter.getBookmarkId(mSura, mAyah, mPage);
      if (bookmarkId >= 0) {
        // if (mBookmarksAdapter.isTagged(bookmarkId)) {
        // TODO show warning dialog that all tags will be removed
        // }
        mBookmarksAdapter.removeBookmark(bookmarkId);
      } else {
        mBookmarksAdapter.addBookmark(mSura, mAyah, mPage);
        result = true;
      }
      return result;
    }

    @Override
    protected void onPostExecute(Boolean result) {
      if (result != null) {
        if (mPageOnly) {
          mBookmarksCache.put(mPage, result);
          invalidateOptionsMenu();
        } else if (QuranSettings.shouldHighlightBookmarks(PagerActivity.this)) {
          if (result) {
            highlightAyah(mSura, mAyah, HighlightType.BOOKMARK);
          } else {
            unHighlightAyah(mSura, mAyah, HighlightType.BOOKMARK);
          }
          if (mAyahActionPanel != null) {
            mAyahActionPanel.onAyahBookmarkUpdated(new SuraAyah(mSura, mPage), result);
          }
        }
      }
    }
  }

  class IsPageBookmarkedTask extends AsyncTask<Integer, Void, SparseBooleanArray> {

    @Override
    protected SparseBooleanArray doInBackground(Integer... params) {
      if (params == null) {
        return null;
      }

      SparseBooleanArray result = new SparseBooleanArray();
      for (Integer page : params) {
        boolean bookmarked = mBookmarksAdapter.isPageBookmarked(page);
        result.put(page, bookmarked);
      }

      return result;
    }

    @Override
    protected void onPostExecute(SparseBooleanArray result) {
      if (result != null) {
        int size = result.size();
        for (int i = 0; i < size; i++) {
          int page = result.keyAt(i);
          boolean bookmarked = result.get(page);
          mBookmarksCache.put(page, bookmarked);
        }
        invalidateOptionsMenu();
      }
    }
  }

  @Override
  public void onPlayPressed() {
    if (mAudioStatusBar.getCurrentMode() == AudioStatusBar.PAUSED_MODE) {
      // if we are "paused," just un-pause.
      play(null);
      return;
    }

    int position = mViewPager.getCurrentItem();
    int page = Constants.PAGES_LAST - position;
    if (mDualPages) {
      page = ((302 - position) * 2) - 1;
    }

    int startSura = QuranInfo.PAGE_SURA_START[page - 1];
    int startAyah = QuranInfo.PAGE_AYAH_START[page - 1];
    playFromAyah(page, startSura, startAyah, false);
  }

  public void playFromAyah(int page, int sura, int ayah) {
    playFromAyah(page, sura, ayah, true);
  }

  private void playFromAyah(int page, int startSura,
                            int startAyah, boolean force) {
    if (force) {
      mShouldOverridePlaying = true;
    }
    int currentQari = mAudioStatusBar.getCurrentQari();

    QuranAyah ayah = new QuranAyah(startSura, startAyah);
    if (QuranSettings.shouldStream(this)) {
      playStreaming(ayah, page, currentQari);
    } else {
      downloadAndPlayAudio(ayah, page, currentQari);
    }
  }

  private void playStreaming(QuranAyah ayah, int page, int qari) {
    String qariUrl = AudioUtils.getQariUrl(this, qari, true);
    String dbFile = AudioUtils.getQariDatabasePathIfGapless(
        this, qari);
    if (!TextUtils.isEmpty(dbFile)) {
      // gapless audio is "download only"
      downloadAndPlayAudio(ayah, page, qari);
      return;
    }

    AudioRequest request = new AudioRequest(qariUrl, ayah);
    request.setGaplessDatabaseFilePath(dbFile);
    play(request);

    mAudioStatusBar.switchMode(AudioStatusBar.PLAYING_MODE);
  }

  private void downloadAndPlayAudio(QuranAyah ayah, int page, int qari) {
    QuranAyah endAyah = AudioUtils.getLastAyahToPlay(ayah, page,
        QuranSettings.getPreferredDownloadAmount(this), mDualPages);
    String baseUri = AudioUtils.getLocalQariUrl(this, qari);
    if (endAyah == null || baseUri == null) {
      return;
    }
    String dbFile = AudioUtils.getQariDatabasePathIfGapless(this, qari);

    String fileUrl;
    if (TextUtils.isEmpty(dbFile)) {
      fileUrl = baseUri + File.separator + "%d" + File.separator +
          "%d" + AudioUtils.AUDIO_EXTENSION;
    } else {
      fileUrl = baseUri + File.separator + "%03d" +
          AudioUtils.AUDIO_EXTENSION;
    }

    DownloadAudioRequest request =
        new DownloadAudioRequest(fileUrl, ayah, qari, baseUri);
    request.setGaplessDatabaseFilePath(dbFile);
    request.setPlayBounds(ayah, endAyah);
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
        Log.d(TAG, "on wifi, don't need permission for download...");
        needsPermission = false;
      }
    }

    Log.d(TAG, "seeing if we can play audio request...");
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
    } else if (AudioUtils.shouldDownloadGaplessDatabase(this, request)) {
      Log.d(TAG, "need to download gapless database...");
      if (needsPermission) {
        mAudioStatusBar.switchMode(AudioStatusBar.PROMPT_DOWNLOAD_MODE);
        return;
      }

      if (mIsActionBarHidden) {
        toggleActionBar();
      }
      mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);
      String url = AudioUtils.getGaplessDatabaseUrl(this, request);
      String destination = request.getLocalPath();
      // start the download
      String notificationTitle = getString(R.string.timing_database);
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
          destination, notificationTitle, AUDIO_DOWNLOAD_KEY,
          QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
      startService(intent);
    } else if (AudioUtils.haveAllFiles(request)) {
      if (!AudioUtils.shouldDownloadBasmallah(this, request)) {
        android.util.Log.d(TAG, "have all files, playing!");
        request.removePlayBounds();
        play(request);
        mLastAudioDownloadRequest = null;
      } else {
        android.util.Log.d(TAG, "should download basmalla...");
        if (needsPermission) {
          mAudioStatusBar.switchMode(AudioStatusBar.PROMPT_DOWNLOAD_MODE);
          return;
        }

        QuranAyah firstAyah = new QuranAyah(1, 1);
        String qariUrl = AudioUtils.getQariUrl(this,
            request.getQariId(), true);
        mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);

        if (mIsActionBarHidden) {
          toggleActionBar();
        }
        String notificationTitle =
            QuranInfo.getNotificationTitle(this, firstAyah, firstAyah);
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
          request.getMinAyah(), request.getMaxAyah());
      String qariUrl = AudioUtils.getQariUrl(this,
          request.getQariId(), true);
      android.util.Log.d(TAG, "need to start download: " + qariUrl);

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
    Intent i = new Intent(AudioService.ACTION_PLAYBACK);
    if (request != null) {
      i.putExtra(AudioService.EXTRA_PLAY_INFO, request);
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
    startService(new Intent(AudioService.ACTION_PAUSE));
    mAudioStatusBar.switchMode(AudioStatusBar.PAUSED_MODE);
  }

  @Override
  public void onNextPressed() {
    startService(new Intent(AudioService.ACTION_SKIP));
  }

  @Override
  public void onPreviousPressed() {
    startService(new Intent(AudioService.ACTION_REWIND));
  }

  @Override
  public void setRepeatCount(int repeatCount) {
    Intent i = new Intent(AudioService.ACTION_UPDATE_REPEAT);
    i.putExtra(AudioService.EXTRA_REPEAT_INFO, new RepeatInfo(repeatCount));
    startService(i);
  }

  @Override
  public void onStopPressed() {
    startService(new Intent(AudioService.ACTION_STOP));
    mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
    unHighlightAyahs(HighlightType.AUDIO);
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
    if (mAyahActionPanel != null && mAyahActionPanel.isInActionMode()) {
      mAyahActionPanel.endActionMode();
    } else {
      super.onBackPressed();
    }
  }

  public boolean isInActionMode() {
    return mAyahActionPanel != null && mAyahActionPanel.isInActionMode();
  }


  @Override
  public boolean isListeningForAyahSelection(EventType eventType) {
    return eventType == EventType.LONG_PRESS ||
        eventType == EventType.SINGLE_TAP && isInActionMode();
  }

  @Override
  public boolean onAyahSelected(EventType eventType,
      SuraAyah suraAyah, HighlightingImageView hv) {
    switch (eventType) {
      case SINGLE_TAP:
        if (isInActionMode()) {
          updateAyahStartSelection(suraAyah, hv);
          return true;
        }
        return false;
      case LONG_PRESS:
        if (isInActionMode()) {
          updateAyahEndSelection(suraAyah);
        } else {
          startActionMode(suraAyah, hv);
        }
        mViewPager.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean onClick(EventType eventType) {
    switch (eventType) {
      case SINGLE_TAP:
        if (!isInActionMode()) {
          toggleActionBar();
          return true;
        }
        return false;
      case DOUBLE_TAP:
        if (!isInActionMode()) {
          return false;
        }
        endActionMode();
        return true;
      default:
        return false;
    }
  }

  public void startActionMode(SuraAyah suraAyah, HighlightingImageView hv) {
    if (mAyahActionPanel != null) {
      mAyahActionPanel.startActionMode(suraAyah);
      showActionModeHighlights(suraAyah, hv);
    }
  }

  public void endActionMode() {
    if (mAyahActionPanel != null) {
      clearActionModeHighlights();
      mAyahActionPanel.endActionMode();
    }
  }

  public void updateAyahStartSelection(SuraAyah suraAyah, HighlightingImageView hv) {
    if (mAyahActionPanel != null) {
      clearActionModeHighlights();
      mAyahActionPanel.updateStartSelection(suraAyah);
      showActionModeHighlights(suraAyah, hv);
    }
  }

  public void updateAyahEndSelection(SuraAyah suraAyah) {
    if (mAyahActionPanel != null) {
      clearActionModeHighlights();
      mAyahActionPanel.updateEndSelection(suraAyah);
      // Determine the start and end of the selection
      int minPage = Math.min(mAyahActionPanel.mStart.getPage(), mAyahActionPanel.mEnd.getPage());
      int maxPage = Math.max(mAyahActionPanel.mStart.getPage(), mAyahActionPanel.mEnd.getPage());
      SuraAyah start = new SuraAyah(mAyahActionPanel.mStart.sura, mAyahActionPanel.mStart.ayah);
      SuraAyah end = new SuraAyah(mAyahActionPanel.mEnd.sura, mAyahActionPanel.mEnd.ayah);
      if (start.compareTo(end) > 0) {
        SuraAyah swap = start;
        start = end;
        end = swap;
      }
      // Iterate from beginning to end
      for (int i = minPage; i <= maxPage; i++) {
        AyahTracker fragment = mPagerAdapter.getFragmentIfExistsForPage(i);
        if (fragment != null) {
          HighlightingImageView imageView = fragment.getHighlightingImageView(i);
          if (imageView != null) {
            Set<String> ayahKeys = QuranInfo.getAyahKeysOnPage(i, start, end);
            imageView.highlightAyahs(ayahKeys, HighlightType.SELECTION);
            imageView.invalidate();
          }
        }
      }
    }
  }

  private void showActionModeHighlights(SuraAyah suraAyah, HighlightingImageView hv) {
    if (hv != null) {
      hv.highlightAyah(suraAyah.sura, suraAyah.ayah, HighlightType.SELECTION);
      hv.invalidate();
    }
  }

  private void clearActionModeHighlights() {
    if (mAyahActionPanel != null) {
      for (int i = mAyahActionPanel.mStart.getPage(); i <= mAyahActionPanel.mEnd.getPage(); i++) {
        AyahTracker fragment = mPagerAdapter.getFragmentIfExistsForPage(i);
        if (fragment != null) {
          HighlightingImageView imageView = fragment.getHighlightingImageView(i);
          if (imageView != null) {
            imageView.unHighlight(HighlightType.SELECTION);
          }
        }
      }
    }
  }

}