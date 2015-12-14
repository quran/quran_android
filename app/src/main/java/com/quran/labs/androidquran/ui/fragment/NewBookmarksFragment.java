package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.ui.bookmark.BookmarkPresenter;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

public class NewBookmarksFragment extends Fragment {

  private QuranListAdapter mBookmarksAdapter;
  private BookmarkPresenter mBookmarkPresenter;
  private MenuItem mGroupByTagsItem;

  public static NewBookmarksFragment newInstance(){
    return new NewBookmarksFragment();
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    mBookmarkPresenter = BookmarkPresenter.getInstance(context);
    setHasOptionsMenu(true);
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.quran_list, container, false);

    final Context context = getActivity();

    RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(context));
    recyclerView.setItemAnimator(new DefaultItemAnimator());

    mBookmarksAdapter = new QuranListAdapter(context, recyclerView, new QuranRow[0], true);
    recyclerView.setAdapter(mBookmarksAdapter);
    return view;
  }

  @Override
  public void onStart() {
    super.onStart();
    mBookmarkPresenter.bind(this);
  }

  @Override
  public void onStop() {
    mBookmarkPresenter.unbind();
    super.onStop();
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    MenuItem sortItem = menu.findItem(R.id.sort);
    if (sortItem != null) {
      sortItem.setVisible(true);
      sortItem.setEnabled(true);
      mGroupByTagsItem = menu.findItem(R.id.group_by_tags);
      mGroupByTagsItem.setChecked(mBookmarkPresenter.isGroupedByTags());

      // TODO: remove this after removing {Bookmarks,Tags}Fragment and enabling in home_menu
      SubMenu subMenu = sortItem.getSubMenu();
      int[] validOptions = { R.id.sort_location, R.id.sort_date, R.id.group_by_tags };
      for (int validOption : validOptions) {
        MenuItem subMenuItem = subMenu.findItem(validOption);
        subMenuItem.setVisible(true);
        subMenuItem.setEnabled(true);
      }
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
        mGroupByTagsItem.setChecked(mBookmarkPresenter.isGroupedByTags());
        break;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  public void onNewData(List<QuranRow> items) {
    // TODO: why u no notifyDataSetChanged yourself?
    mBookmarksAdapter.setElements(items.toArray(new QuranRow[items.size()]));
    mBookmarksAdapter.notifyDataSetChanged();
  }
}
