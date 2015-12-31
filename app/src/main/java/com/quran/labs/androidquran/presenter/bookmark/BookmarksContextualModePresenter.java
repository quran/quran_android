package com.quran.labs.androidquran.presenter.bookmark;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment;

import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class BookmarksContextualModePresenter implements Presenter<BookmarksFragment> {

  private static BookmarksContextualModePresenter sInstance =
      new BookmarksContextualModePresenter();

  private ActionMode mMode;
  private BookmarksFragment mFragment;
  private AppCompatActivity mActivity;

  public static BookmarksContextualModePresenter getInstance() {
    return sInstance;
  }

  public boolean isInActionMode() {
    return mMode != null;
  }

  public void startActionMode() {
    if (mActivity != null) {
      mMode = mActivity.startSupportActionMode(new ModeCallback());
    }
  }

  public void invalidateActionMode(boolean startIfStopped) {
    if (mMode != null) {
      mMode.invalidate();
    } else if (startIfStopped) {
      startActionMode();
    }
  }

  public void finishActionMode() {
    if (mMode != null) {
      mMode.finish();
    }
  }

  private class ModeCallback implements ActionMode.Callback {

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      MenuInflater inflater = mActivity.getMenuInflater();
      inflater.inflate(R.menu.bookmark_contextual_menu, menu);
      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
      if (mFragment != null) {
        mFragment.prepareContextualMenu(menu);
      }
      return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
      boolean result = mFragment != null && mFragment.onContextualActionClicked(item.getItemId());
      finishActionMode();
      return result;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      if (mFragment != null) {
        mFragment.onCloseContextualActionMenu();
      }

      if (mode == mMode) {
        mMode = null;
      }
    }
  }

  @Override
  public void bind(BookmarksFragment fragment) {
    mFragment = fragment;
    mActivity = (AppCompatActivity) fragment.getActivity();
  }

  @Override
  public void unbind(BookmarksFragment fragment) {
    if (fragment == mFragment) {
      mFragment = null;
      mActivity = null;
    }
  }
}
