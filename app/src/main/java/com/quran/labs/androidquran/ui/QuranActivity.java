package com.quran.labs.androidquran.ui;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.core.view.MenuItemCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.quran.labs.androidquran.AboutUsActivity;
import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.SearchActivity;
import com.quran.labs.androidquran.ShortcutsActivity;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.model.bookmark.RecentPageModel;
import com.quran.labs.androidquran.presenter.translation.TranslationManagerPresenter;
import com.quran.labs.androidquran.service.AudioService;
import com.quran.labs.androidquran.ui.fragment.AddTagDialog;
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment;
import com.quran.labs.androidquran.ui.fragment.JumpFragment;
import com.quran.labs.androidquran.ui.fragment.JuzListFragment;
import com.quran.labs.androidquran.ui.fragment.SuraListFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;
import com.quran.labs.androidquran.ui.helpers.JumpDestination;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.SlidingTabLayout;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import timber.log.Timber;

public class QuranActivity extends QuranActionBarActivity
    implements TagBookmarkDialog.OnBookmarkTagsUpdateListener, JumpDestination {

  private static int[] TITLES = new int[]{
      R.string.quran_sura,
      R.string.quran_juz2,
      R.string.menu_bookmarks };
  private static int[] ARABIC_TITLES = new int[]{
      R.string.menu_bookmarks,
      R.string.quran_juz2,
      R.string.quran_sura };

  public static final String EXTRA_SHOW_TRANSLATION_UPGRADE = "transUp";
  private static final String SI_SHOWED_UPGRADE_DIALOG = "si_showed_dialog";

  private static final int SURA_LIST = 0;
  private static final int JUZ2_LIST = 1;
  private static final int BOOKMARKS_LIST = 2;

  private static boolean updatedTranslations;

  private AlertDialog upgradeDialog = null;
  private boolean showedTranslationUpgradeDialog = false;
  private boolean isRtl;
  private boolean isPaused;
  private MenuItem searchItem;
  private ActionMode supportActionMode;
  private CompositeDisposable compositeDisposable;
  private QuranSettings settings;
  private Observable<Integer> recentPages;

  @Inject AudioUtils audioUtils;
  @Inject RecentPageModel recentPageModel;
  @Inject TranslationManagerPresenter translationManagerPresenter;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    QuranApplication quranApp = (QuranApplication) getApplication();
    quranApp.refreshLocale(this, false);

    super.onCreate(savedInstanceState);
    quranApp.getApplicationComponent().inject(this);

    setContentView(R.layout.quran_index);
    compositeDisposable = new CompositeDisposable();

    settings = QuranSettings.getInstance(this);
    isRtl = isRtl();

    final Toolbar tb = findViewById(R.id.toolbar);
    setSupportActionBar(tb);

    final ActionBar ab = getSupportActionBar();
    if (ab != null) {
      ab.setTitle(R.string.app_name);
    }

    final ViewPager pager = findViewById(R.id.index_pager);
    pager.setOffscreenPageLimit(3);
    PagerAdapter pagerAdapter = new PagerAdapter(getSupportFragmentManager());
    pager.setAdapter(pagerAdapter);

    final SlidingTabLayout indicator =
        findViewById(R.id.indicator);
    indicator.setViewPager(pager);

    if (isRtl) {
      pager.setCurrentItem(TITLES.length - 1);
    }

    if (savedInstanceState != null) {
      showedTranslationUpgradeDialog = savedInstanceState.getBoolean(
          SI_SHOWED_UPGRADE_DIALOG, false);
    }

    recentPages = recentPageModel.getLatestPageObservable();
    Intent intent = getIntent();
    if (intent != null) {
      Bundle extras = intent.getExtras();
      if (extras != null) {
        if (extras.getBoolean(EXTRA_SHOW_TRANSLATION_UPGRADE, false)) {
          if (!showedTranslationUpgradeDialog) {
            showTranslationsUpgradeDialog();
          }
        }
      }

      if (ShortcutsActivity.ACTION_JUMP_TO_LATEST.equals(intent.getAction())) {
        jumpToLastPage();
      }
    }

    updateTranslationsListAsNeeded();
  }

  @Override
  public void onResume() {
    compositeDisposable.add(recentPages.subscribe());

    super.onResume();
    final boolean isRtl = isRtl();
    if (isRtl != this.isRtl) {
      final Intent i = getIntent();
      finish();
      startActivity(i);
    } else {
      startService(audioUtils.getAudioIntent(this, AudioService.ACTION_STOP));
    }
    isPaused = false;
  }

  @Override
  protected void onPause() {
    compositeDisposable.clear();
    isPaused = true;
    super.onPause();
  }

  private boolean isRtl() {
    return settings.isArabicNames() || QuranUtils.isRtl();
  }

  public Observable<Integer> getLatestPageObservable() {
    return recentPages;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.home_menu, menu);
    searchItem = menu.findItem(R.id.search);
    final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
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
        jumpToLastPage();
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
  public void onSupportActionModeFinished(@NonNull ActionMode mode) {
    supportActionMode = null;
    super.onSupportActionModeFinished(mode);
  }

  @Override
  public void onSupportActionModeStarted(@NonNull ActionMode mode) {
    supportActionMode = mode;
    super.onSupportActionModeStarted(mode);
  }

  @Override
  public void onBackPressed() {
    if (supportActionMode != null) {
      supportActionMode.finish();
    } else if (searchItem != null && searchItem.isActionViewExpanded()) {
      searchItem.collapseActionView();
    } else {
      super.onBackPressed();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    outState.putBoolean(SI_SHOWED_UPGRADE_DIALOG,
        showedTranslationUpgradeDialog);
    super.onSaveInstanceState(outState);
  }

  private void jumpToLastPage() {
    compositeDisposable.add(recentPages
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(recentPage -> jumpTo(recentPage == Constants.NO_PAGE ? 1 : recentPage)));
  }

  private void updateTranslationsListAsNeeded() {
    if (!updatedTranslations) {
      long time = settings.getLastUpdatedTranslationDate();
      Timber.d("checking whether we should update translations..");
      if (System.currentTimeMillis() - time > Constants.TRANSLATION_REFRESH_TIME) {
        Timber.d("updating translations list...");
        updatedTranslations = true;
        translationManagerPresenter.checkForUpdates();
      }
    }
  }

  private void showTranslationsUpgradeDialog() {
    showedTranslationUpgradeDialog = true;
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(R.string.translation_updates_available);
    builder.setCancelable(false);
    builder.setPositiveButton(R.string.translation_dialog_yes,
        (dialog, id) -> {
          dialog.dismiss();
          upgradeDialog = null;
          launchTranslationActivity();
        });

    builder.setNegativeButton(R.string.translation_dialog_later,
        (dialog, which) -> {
          dialog.dismiss();
          upgradeDialog = null;

          // pretend we don't have updated translations.  we'll
          // check again after 10 days.
          settings.setHaveUpdatedTranslations(false);
        });

    upgradeDialog = builder.create();
    upgradeDialog.show();
  }

  private void launchTranslationActivity() {
    Intent i = new Intent(this, TranslationManagerActivity.class);
    startActivity(i);
  }

  @Override
  public void jumpTo(int page) {
    Intent i = new Intent(this, PagerActivity.class);
    i.putExtra("page", page);
    i.putExtra(PagerActivity.EXTRA_JUMP_TO_TRANSLATION, settings.getWasShowingTranslation());
    startActivity(i);
  }

  @Override
  public void jumpToAndHighlight(int page, int sura, int ayah) {
    Intent i = new Intent(this, PagerActivity.class);
    i.putExtra("page", page);
    i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura);
    i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah);
    startActivity(i);
  }

  private void gotoPageDialog() {
    if (!isPaused) {
      FragmentManager fm = getSupportFragmentManager();
      JumpFragment jumpDialog = new JumpFragment();
      jumpDialog.show(fm, JumpFragment.TAG);
    }
  }

  public void addTag() {
    if (!isPaused) {
      FragmentManager fm = getSupportFragmentManager();
      AddTagDialog addTagDialog = new AddTagDialog();
      addTagDialog.show(fm, AddTagDialog.TAG);
    }
  }

  public void editTag(long id, String name) {
    if (!isPaused) {
      FragmentManager fm = getSupportFragmentManager();
      AddTagDialog addTagDialog = AddTagDialog.Companion.newInstance(id, name);
      addTagDialog.show(fm, AddTagDialog.TAG);
    }
  }

  public void tagBookmarks(long[] ids) {
    if (ids != null && ids.length == 1) {
      tagBookmark(ids[0]);
      return;
    }

    if (!isPaused) {
      FragmentManager fm = getSupportFragmentManager();
      TagBookmarkDialog tagBookmarkDialog = TagBookmarkDialog.newInstance(ids);
      tagBookmarkDialog.show(fm, TagBookmarkDialog.TAG);
    }
  }

  private void tagBookmark(long id) {
    if (!isPaused) {
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

  private class PagerAdapter extends FragmentPagerAdapter {

    PagerAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public int getCount() {
      return 3;
    }

    @Override
    public Fragment getItem(int position) {
      int pos = position;
      if (isRtl) {
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
      int pos = isRtl ? Math.abs(position - 2) : position;
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
      final int resId = isRtl ?
          ARABIC_TITLES[position] : TITLES[position];
      return getString(resId);
    }
  }
}
