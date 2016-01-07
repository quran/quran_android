package com.quran.labs.androidquran.ui;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.quran.labs.androidquran.AboutUsActivity;
import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.SearchActivity;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.service.AudioService;
import com.quran.labs.androidquran.task.TranslationListTask;
import com.quran.labs.androidquran.ui.fragment.AddTagDialog;
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment;
import com.quran.labs.androidquran.ui.fragment.JumpFragment;
import com.quran.labs.androidquran.ui.fragment.JuzListFragment;
import com.quran.labs.androidquran.ui.fragment.SuraListFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.UpgradeTranslationListener;
import com.quran.labs.androidquran.widgets.SlidingTabLayout;

import android.app.Application;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;

public class QuranActivity extends QuranActionBarActivity
    implements TagBookmarkDialog.OnBookmarkTagsUpdateListener {

  private static int[] TITLES = new int[]{
      R.string.quran_sura,
      R.string.quran_juz2,
      R.string.menu_bookmarks };
  private static int[] ARABIC_TITLES = new int[]{
      R.string.menu_bookmarks,
      R.string.quran_juz2,
      R.string.quran_sura };

  public static final String EXTRA_SHOW_TRANSLATION_UPGRADE = "transUp";
  public static final String SI_SHOWED_UPGRADE_DIALOG = "si_showed_dialog";

  private static final int SURA_LIST = 0;
  private static final int JUZ2_LIST = 1;
  private static final int BOOKMARKS_LIST = 2;

  private static boolean sUpdatedTranslations;

  private AlertDialog mUpgradeDialog = null;
  private boolean mShowedTranslationUpgradeDialog = false;
  private boolean mIsRtl;
  private boolean mIsPaused;
  private MenuItem mSearchItem;
  private ActionMode mSupportActionMode;
  private CompositeSubscription mCompositeSubscription;
  private QuranSettings mSettings;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Application application = getApplication();
    if (application instanceof QuranApplication) {
      // instant reload often triggers a crash here, stating that
      // application is not a QuranApplication
      ((QuranApplication) application).refreshLocale(this, false);
    }
    super.onCreate(savedInstanceState);
    setContentView(R.layout.quran_index);
    mCompositeSubscription = new CompositeSubscription();

    mSettings = QuranSettings.getInstance(this);
    mIsRtl = isRtl();

    final Toolbar tb = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(tb);

    final ActionBar ab = getSupportActionBar();
    if (ab != null) {
      ab.setTitle(R.string.app_name);
    }

    final ViewPager pager = (ViewPager) findViewById(R.id.index_pager);
    pager.setOffscreenPageLimit(3);
    PagerAdapter pagerAdapter = new PagerAdapter(getSupportFragmentManager());
    pager.setAdapter(pagerAdapter);

    final SlidingTabLayout indicator =
        (SlidingTabLayout) findViewById(R.id.indicator);
    indicator.setViewPager(pager);

    if (mIsRtl) {
      pager.setCurrentItem(TITLES.length - 1);
    }

    if (savedInstanceState != null) {
      mShowedTranslationUpgradeDialog = savedInstanceState.getBoolean(
          SI_SHOWED_UPGRADE_DIALOG, false);
    }

    Intent intent = getIntent();
    if (intent != null) {
      Bundle extras = intent.getExtras();
      if (extras != null) {
        if (extras.getBoolean(EXTRA_SHOW_TRANSLATION_UPGRADE, false)) {
          if (!mShowedTranslationUpgradeDialog) {
            showTranslationsUpgradeDialog();
          }
        }
      }
    }

    updateTranslationsListAsNeeded();
  }

  @Override
  public void onResume() {
    super.onResume();
    final boolean isRtl = isRtl();
    if (isRtl != mIsRtl) {
      final Intent i = getIntent();
      finish();
      startActivity(i);
    } else {
      startService(AudioUtils.getAudioIntent(this, AudioService.ACTION_STOP));
    }
    mIsPaused = false;
  }

  @Override
  protected void onPause() {
    mIsPaused = true;
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    mCompositeSubscription.unsubscribe();
    super.onDestroy();
  }

  private boolean isRtl() {
    return mSettings.isArabicNames() || QuranUtils.isRtl();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.home_menu, menu);
    mSearchItem = menu.findItem(R.id.search);
    final SearchView searchView = (SearchView) MenuItemCompat.getActionView(mSearchItem);
    final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
    searchView.setQueryHint(getString(R.string.search_hint));
    searchView.setSearchableInfo(searchManager.getSearchableInfo(
        new ComponentName(this, SearchActivity.class)));
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.settings: {
        Intent i = new Intent(this, QuranPreferenceActivity.class);
        startActivity(i);
        return true;
      }
      case R.id.last_page: {
        int page = mSettings.getLastPage();
        jumpTo(page);
        return true;
      }
      case R.id.help: {
        Intent i = new Intent(this, HelpActivity.class);
        startActivity(i);
        return true;
      }
      case R.id.about: {
        Intent i = new Intent(this, AboutUsActivity.class);
        startActivity(i);
        return true;
      }
      case R.id.jump: {
        gotoPageDialog();
        return true;
      }
      case R.id.other_apps: {
        Answers.getInstance().logCustom(new CustomEvent("menuOtherApps"));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("market://search?q=pub:quran.com"));
        if (getPackageManager().resolveActivity(intent,
            PackageManager.MATCH_DEFAULT_ONLY) == null) {
          intent.setData(Uri.parse("http://play.google.com/store/search?q=pub:quran.com"));
        }
        startActivity(intent);
        return true;
      }
      default: {
        return super.onOptionsItemSelected(item);
      }
    }
  }

  @Override
  public void onSupportActionModeFinished(ActionMode mode) {
    mSupportActionMode = null;
    super.onSupportActionModeFinished(mode);
  }

  @Override
  public void onSupportActionModeStarted(ActionMode mode) {
    mSupportActionMode = mode;
    super.onSupportActionModeStarted(mode);
  }

  @Override
  public void onBackPressed() {
    if (mSupportActionMode != null) {
      mSupportActionMode.finish();
    } else if (mSearchItem != null && mSearchItem.isActionViewExpanded()) {
      mSearchItem.collapseActionView();
    } else {
      super.onBackPressed();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    outState.putBoolean(SI_SHOWED_UPGRADE_DIALOG,
        mShowedTranslationUpgradeDialog);
    super.onSaveInstanceState(outState);
  }

  private void updateTranslationsListAsNeeded() {
    if (mSettings.haveUpdatedTranslations()) {
      showTranslationsUpgradeDialog();
    } else if (!sUpdatedTranslations) {
      long time = mSettings.getLastUpdatedTranslationDate();
      Timber.d("checking whether we should update translations..");
      if (System.currentTimeMillis() - time > Constants.TRANSLATION_REFRESH_TIME) {
        Timber.d("updating translations list...");
        sUpdatedTranslations = true;
        new TranslationListTask(
            this, new UpgradeTranslationListener(this)).execute();
      }
    }
  }

  private void showTranslationsUpgradeDialog() {
    mShowedTranslationUpgradeDialog = true;
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(R.string.translation_updates_available);
    builder.setCancelable(false);
    builder.setPositiveButton(R.string.translation_dialog_yes,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int id) {
            dialog.dismiss();
            mUpgradeDialog = null;
            launchTranslationActivity();
          }
        });

    builder.setNegativeButton(R.string.translation_dialog_later,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            mUpgradeDialog = null;

            // pretend we don't have updated translations.  we'll
            // check again after 10 days.
            mSettings.setHaveUpdatedTranslations(false);
          }
        });

    mUpgradeDialog = builder.create();
    mUpgradeDialog.show();
  }

  public void launchTranslationActivity() {
    Intent i = new Intent(this, TranslationManagerActivity.class);
    startActivity(i);
  }

  public void jumpTo(int page) {
    Intent i = new Intent(this, PagerActivity.class);
    i.putExtra("page", page);
    startActivity(i);
  }

  public void jumpToAndHighlight(int page, int sura, int ayah) {
    Intent i = new Intent(this, PagerActivity.class);
    i.putExtra("page", page);
    i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura);
    i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah);
    startActivity(i);
  }

  private void gotoPageDialog() {
    if (!mIsPaused) {
      FragmentManager fm = getSupportFragmentManager();
      JumpFragment jumpDialog = new JumpFragment();
      jumpDialog.show(fm, JumpFragment.TAG);
    }
  }

  public void addTag() {
    if (!mIsPaused) {
      FragmentManager fm = getSupportFragmentManager();
      AddTagDialog addTagDialog = new AddTagDialog();
      addTagDialog.show(fm, AddTagDialog.TAG);
    }
  }

  public void editTag(long id, String name) {
    if (!mIsPaused) {
      FragmentManager fm = getSupportFragmentManager();
      AddTagDialog addTagDialog = AddTagDialog.newInstance(id, name);
      addTagDialog.show(fm, AddTagDialog.TAG);
    }
  }

  public void tagBookmarks(long[] ids) {
    if (ids != null && ids.length == 1) {
      tagBookmark(ids[0]);
      return;
    }

    if (!mIsPaused) {
      FragmentManager fm = getSupportFragmentManager();
      TagBookmarkDialog tagBookmarkDialog = TagBookmarkDialog.newInstance(ids);
      tagBookmarkDialog.show(fm, TagBookmarkDialog.TAG);
    }
  }

  private void tagBookmark(long id) {
    if (!mIsPaused) {
      FragmentManager fm = getSupportFragmentManager();
      TagBookmarkDialog tagBookmarkDialog = TagBookmarkDialog.newInstance(id);
      tagBookmarkDialog.show(fm, TagBookmarkDialog.TAG);
    }
  }

  @Override
  public void onAddTagSelected() {
    FragmentManager fm = getSupportFragmentManager();
    AddTagDialog dialog = new AddTagDialog();
    dialog.show(fm, AddTagDialog.TAG);
  }

  public class PagerAdapter extends FragmentPagerAdapter {

    public PagerAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public int getCount() {
      return 3;
    }

    @Override
    public Fragment getItem(int position) {
      int pos = position;
      if (mIsRtl) {
        pos = Math.abs(position - 2);
      }

      switch (pos) {
        case QuranActivity.SURA_LIST:
          return SuraListFragment.newInstance();
        case QuranActivity.JUZ2_LIST:
          return JuzListFragment.newInstance();
        case QuranActivity.BOOKMARKS_LIST:
        default:
          return BookmarksFragment.newInstance();
      }
    }

    @Override
    public long getItemId(int position) {
      int pos = mIsRtl ? Math.abs(position - 2) : position;
      switch (pos) {
        case QuranActivity.SURA_LIST:
          return SURA_LIST;
        case QuranActivity.JUZ2_LIST:
          return JUZ2_LIST;
        case QuranActivity.BOOKMARKS_LIST:
        default:
          return BOOKMARKS_LIST;
      }
    }

    @Override
    public CharSequence getPageTitle(int position) {
      final int resId = mIsRtl ?
          ARABIC_TITLES[position] : TITLES[position];
      return getString(resId);
    }
  }
}
