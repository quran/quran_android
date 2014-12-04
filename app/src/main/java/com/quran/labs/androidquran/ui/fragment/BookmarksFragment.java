package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.BookmarksDBAdapter.Bookmark;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.BookmarkHandler;
import com.quran.labs.androidquran.ui.helpers.QuranRow;
import com.quran.labs.androidquran.util.QuranSettings;

import android.app.Activity;
import android.support.v7.view.ActionMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;

public class BookmarksFragment extends AbsMarkersFragment {
   private static final String TAG =
         "com.quran.labs.androidquran.ui.fragment.BookmarksFragment";
   
   private static final int[] VALID_SORT_OPTIONS = {R.id.sort_location, R.id.sort_date};

   public static BookmarksFragment newInstance(){
      return new BookmarksFragment();
   }
   
   @Override
   protected int getContextualMenuId() {
      return R.menu.bookmark_menu;
   }
   
   @Override
   protected int getEmptyListStringId() {
      return R.string.bookmarks_list_empty;
   }
   
   @Override
   protected int[] getValidSortOptions() {
      return VALID_SORT_OPTIONS;
   }
   
   @Override
   protected String getSortPref() {
      return Constants.PREF_SORT_BOOKMARKS;
   }
   
   @Override
   protected boolean isValidSelection(QuranRow selected) {
      return selected.isBookmark();
   }
   
   @Override
   protected boolean prepareActionMode(ActionMode mode, Menu menu, QuranRow[] selected) {
      MenuItem removeItem = menu.findItem(R.id.cab_delete_bookmark);
      MenuItem tagItem = menu.findItem(R.id.cab_tag_bookmark);
      if (selected == null || selected.length == 0) {
         removeItem.setVisible(false);
         tagItem.setVisible(false);
         return true;
      }
      for (QuranRow row : selected) {
         if (row == null || !row.isBookmark()) {
            removeItem.setVisible(false);
            tagItem.setVisible(false);
            return true;
         }
      }
      removeItem.setVisible(true);
      tagItem.setVisible(true);
      return true;
   }
   
   @Override
   protected boolean actionItemClicked(ActionMode mode, int menuItemId,
         QuranActivity activity, QuranRow[] selected) {
      if (selected == null)
         return false;
      switch (menuItemId) {
      case R.id.cab_delete_bookmark:
         new RemoveBookmarkTask().execute(selected);
         return true;
      case R.id.cab_tag_bookmark:
         long[] ids = new long[selected.length];
         for (int i = 0; i < selected.length; i++) {
            ids[i] = selected[i].bookmarkId;
         }
         activity.tagBookmarks(ids);
         return true;
      default:
         return false;
      }
   }
   
   @Override
   protected QuranRow[] getItems(){
      return getBookmarks();
   }
   
   private QuranRow[] getBookmarks(){
      List<Bookmark> bookmarks;
      Activity activity = getActivity();
      BookmarksDBAdapter adapter = null;
      if (activity != null && activity instanceof BookmarkHandler){
         adapter = ((BookmarkHandler) activity).getBookmarksAdapter();
      }
      if (adapter == null){ return null; }

      switch (mCurrentSortCriteria) {
      case R.id.sort_location:
         bookmarks = adapter.getBookmarks(false,
                 BookmarksDBAdapter.SORT_LOCATION);
         break;
      case R.id.sort_date:
      default:
         bookmarks = adapter.getBookmarks(false,
                 BookmarksDBAdapter.SORT_DATE_ADDED);
         break;
      }

      int lastPage = QuranSettings.getLastPage(activity);
      boolean showLastPage = lastPage != Constants.NO_PAGE_SAVED;
      if (showLastPage && (lastPage > Constants.PAGES_LAST ||
              lastPage < Constants.PAGES_FIRST)) {
         showLastPage = false;
         Log.w(TAG, "Got invalid last saved page as: "+lastPage);
      }

      List<QuranRow> rows = new ArrayList<>();
      if (showLastPage){
         rows.add(new QuranRow.Builder()
             .withText(getString(R.string.bookmarks_current_page))
             .withType(QuranRow.HEADER).build());
         rows.add(new QuranRow.Builder()
               .withText(QuranInfo.getSuraNameString(activity, lastPage))
               .withMetadata(QuranInfo.getPageSubtitle(activity, lastPage))
               .withSura(QuranInfo.PAGE_SURA_START[lastPage-1])
               .withPage(lastPage)
               .withImageResource(R.drawable.bookmark_currentpage).build());
      }
      
      List<QuranRow> pageBookmarks = new ArrayList<>();
      List<QuranRow> ayahBookmarks = new ArrayList<>();
      for (Bookmark bookmark : bookmarks) {
         QuranRow row = createRowFromBookmark(activity, bookmark);
         if (bookmark.isPageBookmark())
            pageBookmarks.add(row);
         else
            ayahBookmarks.add(row);
      }

      if (!pageBookmarks.isEmpty()){
         rows.add(new QuranRow.Builder()
             .withText(getString(R.string.menu_bookmarks_page))
             .withType(QuranRow.HEADER).build());
         rows.addAll(pageBookmarks);
      }

      if (!ayahBookmarks.isEmpty()){
         rows.add(new QuranRow.Builder()
             .withText(getString(R.string.menu_bookmarks_ayah))
             .withType(QuranRow.HEADER).build());
         rows.addAll(ayahBookmarks);
      }
      return rows.toArray(new QuranRow[rows.size()]);
   }
}
