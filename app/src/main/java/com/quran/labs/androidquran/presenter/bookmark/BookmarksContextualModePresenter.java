package com.quran.labs.androidquran.presenter.bookmark;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BookmarksContextualModePresenter implements Presenter<BookmarksFragment> {

  private ActionMode actionMode;
  private BookmarksFragment fragment;
  private AppCompatActivity activity;

  @Inject
  BookmarksContextualModePresenter() {
  }

  public boolean isInActionMode() {
    return actionMode != null;
  }

  private void startActionMode() {
    if (activity != null) {
      actionMode = activity.startSupportActionMode(new ModeCallback());
    }
  }

  public void invalidateActionMode(boolean startIfStopped) {
    if (actionMode != null) {
      actionMode.invalidate();
    } else if (startIfStopped) {
      startActionMode();
    }
  }

  public void finishActionMode() {
    if (actionMode != null) {
      actionMode.finish();
    }
  }

  private class ModeCallback implements ActionMode.Callback {

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      MenuInflater inflater = activity.getMenuInflater();
      inflater.inflate(R.menu.bookmark_contextual_menu, menu);
      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
      if (fragment != null) {
        fragment.prepareContextualMenu(menu);
      }
      return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
      boolean result = fragment != null && fragment.onContextualActionClicked(item.getItemId());
      finishActionMode();
      return result;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      if (fragment != null) {
        fragment.onCloseContextualActionMenu();
      }

      if (mode == actionMode) {
        actionMode = null;
      }
    }
  }

  @Override
  public void bind(BookmarksFragment fragment) {
    this.fragment = fragment;
    activity = (AppCompatActivity) fragment.getActivity();
  }

  @Override
  public void unbind(BookmarksFragment fragment) {
    if (fragment.equals(this.fragment)) {
      this.fragment = null;
      activity = null;
    }
  }
}
