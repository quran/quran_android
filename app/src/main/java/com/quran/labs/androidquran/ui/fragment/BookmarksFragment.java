package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.model.bookmark.BookmarkResult;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.presenter.bookmark.BookmarkPresenter;
import com.quran.labs.androidquran.presenter.bookmark.BookmarksContextualModePresenter;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

public class BookmarksFragment extends Fragment implements QuranListAdapter.QuranTouchListener {

  private QuranListAdapter mBookmarksAdapter;
  private BookmarkPresenter mBookmarkPresenter;
  private RecyclerView mRecyclerView;
  private BookmarksContextualModePresenter mBookmarksContextualModePresenter;

  public static BookmarksFragment newInstance(){
    return new BookmarksFragment();
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    mBookmarkPresenter = BookmarkPresenter.getInstance(context);
    mBookmarksContextualModePresenter = BookmarksContextualModePresenter.getInstance();
    setHasOptionsMenu(true);
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.quran_list, container, false);

    final Context context = getActivity();

    mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
    mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
    mRecyclerView.setItemAnimator(new DefaultItemAnimator());

    mBookmarksAdapter = new QuranListAdapter(context, mRecyclerView, new QuranRow[0], true);
    mBookmarksAdapter.setQuranTouchListener(this);
    mRecyclerView.setAdapter(mBookmarksAdapter);
    return view;
  }

  @Override
  public void onStart() {
    super.onStart();
    mBookmarkPresenter.bind(this);
    mBookmarksContextualModePresenter.bind(this);
  }

  @Override
  public void onStop() {
    mBookmarkPresenter.unbind(this);
    mBookmarksContextualModePresenter.unbind(this);
    super.onStop();
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    MenuItem sortItem = menu.findItem(R.id.sort);
    if (sortItem != null) {
      sortItem.setVisible(true);
      sortItem.setEnabled(true);

      boolean isSortByDate =
          BookmarksDBAdapter.SORT_DATE_ADDED == mBookmarkPresenter.getSortOrder();
      MenuItem sortByDate = menu.findItem(R.id.sort_date);
      sortByDate.setVisible(!isSortByDate);
      MenuItem sortByLocation = menu.findItem(R.id.sort_location);
      sortByLocation.setVisible(isSortByDate);

      MenuItem groupByTags = menu.findItem(R.id.group_by_tags);
      groupByTags.setChecked(mBookmarkPresenter.isGroupedByTags());
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    switch (itemId) {
      case R.id.sort_date:
        mBookmarkPresenter.setSortOrder(BookmarksDBAdapter.SORT_DATE_ADDED);
        break;
      case R.id.sort_location: {
        mBookmarkPresenter.setSortOrder(BookmarksDBAdapter.SORT_LOCATION);
        break;
      }
      case R.id.group_by_tags: {
        mBookmarkPresenter.toggleGroupByTags();
        break;
      }
    }
    getActivity().invalidateOptionsMenu();
    return super.onOptionsItemSelected(item);
  }

  public void onNewData(BookmarkResult items) {
    mBookmarksAdapter.setShowTags(mBookmarkPresenter.shouldShowInlineTags());
    mBookmarksAdapter.setElements(
        items.rows.toArray(new QuranRow[items.rows.size()]), items.tagMap);
    mBookmarksAdapter.notifyDataSetChanged();
  }

  @Override
  public void onClick(QuranRow row, int position) {
    if (mBookmarksContextualModePresenter.isInActionMode()) {
      boolean checked = isValidSelection(row) &&
          !mBookmarksAdapter.isItemChecked(position);
      mBookmarksAdapter.setItemChecked(position, checked);
      mBookmarksContextualModePresenter.invalidateActionMode(false);
    } else {
      mBookmarksAdapter.setItemChecked(position, false);
      handleRowClicked(getActivity(), row);
    }
  }

  @Override
  public boolean onLongClick(QuranRow row, int position) {
    if (isValidSelection(row)) {
      mBookmarksAdapter.setItemChecked(position, !mBookmarksAdapter.isItemChecked(position));
      if (mBookmarksContextualModePresenter.isInActionMode() &&
          mBookmarksAdapter.getCheckedItems().size() == 0) {
        mBookmarksContextualModePresenter.finishActionMode();
      } else {
        mBookmarksContextualModePresenter.invalidateActionMode(true);
      }
      return true;
    }
    return false;
  }

  private boolean isValidSelection(QuranRow selected) {
    return selected.isBookmark() || (selected.isBookmarkHeader() && selected.tagId >= 0);
  }

  private View.OnClickListener mOnUndoClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      mBookmarkPresenter.cancelDeletion();
      mBookmarkPresenter.requestData(true);
    }
  };

  public void prepareContextualMenu(Menu menu) {
    boolean[] menuVisibility =
        mBookmarkPresenter.getContextualOperationsForItems(mBookmarksAdapter.getCheckedItems());
    menu.findItem(R.id.cab_edit_tag).setVisible(menuVisibility[0]);
    menu.findItem(R.id.cab_delete).setVisible(menuVisibility[1]);
    menu.findItem(R.id.cab_tag_bookmark).setVisible(menuVisibility[2]);
  }

  public boolean onContextualActionClicked(int itemId) {
    Activity currentActivity = getActivity();
    if (currentActivity instanceof QuranActivity) {
      QuranActivity activity = (QuranActivity) currentActivity;
      switch (itemId) {
        case R.id.cab_delete: {
          final List<QuranRow> selected = mBookmarksAdapter.getCheckedItems();
          final int size = selected.size();
          final Resources res = getResources();
          onNewData(mBookmarkPresenter.predictQuranListAfterDeletion(selected));
          mBookmarkPresenter.deleteAfterSomeTime(selected);
          //noinspection ResourceType
          Snackbar snackbar = Snackbar.make(mRecyclerView,
              res.getQuantityString(R.plurals.bookmark_tag_deleted, size, size),
              BookmarkPresenter.DELAY_DELETION_DURATION_IN_MS);
          snackbar.setAction(R.string.undo, mOnUndoClickListener);
          snackbar.getView().setBackgroundColor(res.getColor(R.color.snackbar_background_color));
          snackbar.show();
          return true;
        }
        case R.id.cab_new_tag: {
          activity.addTag();
          return true;
        }
        case R.id.cab_edit_tag: {
          handleTagEdit(activity, mBookmarksAdapter.getCheckedItems());
          return true;
        }
        case R.id.cab_tag_bookmark: {
          handleTagBookmarks(activity, mBookmarksAdapter.getCheckedItems());
          return true;
        }
      }
    }
    return false;
  }

  public void onCloseContextualActionMenu() {
    mBookmarksAdapter.uncheckAll();
  }

  public void handleRowClicked(Activity activity, QuranRow row) {
    if (!row.isHeader() && activity instanceof QuranActivity) {
      QuranActivity quranActivity = (QuranActivity) activity;
      if (row.isAyahBookmark()) {
        quranActivity.jumpToAndHighlight(row.page, row.sura, row.ayah);
      } else {
        quranActivity.jumpTo(row.page);
      }
    }
  }

  public void handleTagEdit(QuranActivity activity, List<QuranRow> selected) {
    if (selected.size() == 1) {
      QuranRow row = selected.get(0);
      activity.editTag(row.tagId, row.text);
    }
  }

  public void handleTagBookmarks(QuranActivity activity, List<QuranRow> selected) {
    long[] ids = new long[selected.size()];
    for (int i = 0, selectedItems = selected.size(); i < selectedItems; i++) {
      ids[i] = selected.get(i).bookmarkId;
    }
    activity.tagBookmarks(ids);
  }
}
