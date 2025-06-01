package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.bookmark.BookmarkResult;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.presenter.bookmark.BookmarkPresenter;
import com.quran.labs.androidquran.presenter.bookmark.BookmarksContextualModePresenter;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

import java.util.List;

import javax.inject.Inject;

public class BookmarksFragment extends Fragment implements QuranListAdapter.QuranTouchListener {

  private RecyclerView recyclerView;
  private QuranListAdapter bookmarksAdapter;

  @Inject BookmarkPresenter bookmarkPresenter;
  @Inject BookmarksContextualModePresenter bookmarksContextualModePresenter;

  public static BookmarksFragment newInstance(){
    return new BookmarksFragment();
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    ((QuranApplication) context.getApplicationContext()).getApplicationComponent().inject(this);
    setHasOptionsMenu(true);
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.quran_list, container, false);

    final Context context = requireContext();

    recyclerView = view.findViewById(R.id.recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(context));
    recyclerView.setItemAnimator(new DefaultItemAnimator());

    bookmarksAdapter = new QuranListAdapter(context, recyclerView, new QuranRow[0], true);
    bookmarksAdapter.setQuranTouchListener(this);
    recyclerView.setAdapter(bookmarksAdapter);
    return view;
  }

  @Override
  public void onStart() {
    super.onStart();
    bookmarkPresenter.bind(this);
    bookmarksContextualModePresenter.bind(this);
  }

  @Override
  public void onStop() {
    bookmarkPresenter.unbind(this);
    bookmarksContextualModePresenter.unbind(this);
    super.onStop();
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    MenuItem sortItem = menu.findItem(R.id.sort);
    if (sortItem != null) {
      sortItem.setVisible(true);
      sortItem.setEnabled(true);

      if (BookmarksDBAdapter.SORT_DATE_ADDED == bookmarkPresenter.getSortOrder()) {
        MenuItem sortByDate = menu.findItem(R.id.sort_date);
        sortByDate.setChecked(true);
      } else {
        MenuItem sortByLocation = menu.findItem(R.id.sort_location);
        sortByLocation.setChecked(true);
      }

      MenuItem groupByTags = menu.findItem(R.id.group_by_tags);
      groupByTags.setChecked(bookmarkPresenter.isGroupedByTags());

      MenuItem showRecents = menu.findItem(R.id.show_recents);
      showRecents.setChecked(bookmarkPresenter.isShowingRecents());

      MenuItem showDates = menu.findItem(R.id.show_date);
      showDates.setChecked(bookmarkPresenter.isDateShowing());

    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.sort_date) {
      bookmarkPresenter.setSortOrder(BookmarksDBAdapter.SORT_DATE_ADDED);
      item.setChecked(true);
      return true;
    } else if (itemId == R.id.sort_location) {
      bookmarkPresenter.setSortOrder(BookmarksDBAdapter.SORT_LOCATION);
      item.setChecked(true);
      return true;
    } else if (itemId == R.id.group_by_tags) {
      bookmarkPresenter.toggleGroupByTags();
      item.setChecked(bookmarkPresenter.isGroupedByTags());
      return true;
    } else if (itemId == R.id.show_recents) {
      bookmarkPresenter.toggleShowRecents();
      item.setChecked(bookmarkPresenter.isShowingRecents());
      return true;
    } else if (itemId == R.id.show_date) {
      bookmarkPresenter.toogleShowDate();
      bookmarksAdapter.setShowDate(bookmarkPresenter.isDateShowing());
      item.setChecked(bookmarkPresenter.isDateShowing());
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  public void onNewData(BookmarkResult items) {
    bookmarksAdapter.setShowTags(bookmarkPresenter.shouldShowInlineTags());
    bookmarksAdapter.setShowDate(bookmarkPresenter.isDateShowing());
    bookmarksAdapter.setElements(
        items.getRows().toArray(new QuranRow[0]), items.getTagMap());
    bookmarksAdapter.notifyDataSetChanged();
  }

  @Override
  public void onClick(@NonNull QuranRow row, int position) {
    if (bookmarksContextualModePresenter.isInActionMode()) {
      boolean checked = isValidSelection(row) &&
          !bookmarksAdapter.isItemChecked(position);
      bookmarksAdapter.setItemChecked(position, checked);
      bookmarksContextualModePresenter.invalidateActionMode(false);
    } else {
      bookmarksAdapter.setItemChecked(position, false);
      handleRowClicked(getActivity(), row);
    }
  }

  @Override
  public boolean onLongClick(@NonNull QuranRow row, int position) {
    if (isValidSelection(row)) {
      bookmarksAdapter.setItemChecked(position, !bookmarksAdapter.isItemChecked(position));
      if (bookmarksContextualModePresenter.isInActionMode() &&
          bookmarksAdapter.getCheckedItems().size() == 0) {
        bookmarksContextualModePresenter.finishActionMode();
      } else {
        bookmarksContextualModePresenter.invalidateActionMode(true);
      }
      return true;
    }
    return false;
  }

  private boolean isValidSelection(QuranRow selected) {
    return selected.isBookmark() || (selected.isBookmarkHeader() && selected.tagId >= 0);
  }

  private final View.OnClickListener mOnUndoClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      bookmarkPresenter.cancelDeletion();
      bookmarkPresenter.requestData(true);
    }
  };

  public void prepareContextualMenu(Menu menu) {
    boolean[] menuVisibility =
        bookmarkPresenter.getContextualOperationsForItems(bookmarksAdapter.getCheckedItems());
    menu.findItem(R.id.cab_edit_tag).setVisible(menuVisibility[0]);
    menu.findItem(R.id.cab_delete).setVisible(menuVisibility[1]);
    menu.findItem(R.id.cab_tag_bookmark).setVisible(menuVisibility[2]);
  }

  public boolean onContextualActionClicked(int itemId) {
    Activity currentActivity = getActivity();
    if (currentActivity instanceof QuranActivity) {
      QuranActivity activity = (QuranActivity) currentActivity;
      if (itemId == R.id.cab_delete) {
        final List<QuranRow> selected = bookmarksAdapter.getCheckedItems();
        final int size = selected.size();
        final Resources res = getResources();
        bookmarkPresenter.deleteAfterSomeTime(selected);
        Snackbar snackbar = Snackbar.make(recyclerView,
            res.getQuantityString(R.plurals.bookmark_tag_deleted, size, size),
            BookmarkPresenter.DELAY_DELETION_DURATION_IN_MS);
        snackbar.setAction(R.string.undo, mOnUndoClickListener);
        snackbar.setTextColor(res.getColor(R.color.default_text));
        snackbar.getView().setBackgroundColor(res.getColor(R.color.snackbar_background_color));
        snackbar.show();
        return true;
      } else if (itemId == R.id.cab_new_tag) {
        activity.addTag();
        return true;
      } else if (itemId == R.id.cab_edit_tag) {
        handleTagEdit(activity, bookmarksAdapter.getCheckedItems());
        return true;
      } else if (itemId == R.id.cab_tag_bookmark) {
        handleTagBookmarks(activity, bookmarksAdapter.getCheckedItems());
        return true;
      }
    }
    return false;
  }

  public void onCloseContextualActionMenu() {
    bookmarksAdapter.uncheckAll();
  }

  private void handleRowClicked(Activity activity, QuranRow row) {
    if (!row.isHeader() && activity instanceof QuranActivity) {
      QuranActivity quranActivity = (QuranActivity) activity;
      if (row.isAyahBookmark()) {
        quranActivity.jumpToAndHighlight(row.page, row.sura, row.ayah);
      } else {
        quranActivity.jumpTo(row.page);
      }
    }
  }

  private void handleTagEdit(QuranActivity activity, List<QuranRow> selected) {
    if (selected.size() == 1) {
      QuranRow row = selected.get(0);
      activity.editTag(row.tagId, row.text);
    }
  }

  private void handleTagBookmarks(QuranActivity activity, List<QuranRow> selected) {
    long[] ids = new long[selected.size()];
    for (int i = 0, selectedItems = selected.size(); i < selectedItems; i++) {
      ids[i] = selected.get(i).bookmarkId;
    }
    activity.tagBookmarks(ids);
  }
}
