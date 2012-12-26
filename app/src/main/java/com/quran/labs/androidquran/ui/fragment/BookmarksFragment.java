package com.quran.labs.androidquran.ui.fragment;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.BookmarksDBAdapter.Bookmark;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

public class BookmarksFragment extends AbsMarkersFragment {
   
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
   protected boolean prepareActionMode(ActionMode mode, Menu menu, QuranRow selected) {
      MenuItem removeItem = menu.findItem(R.id.cab_delete_bookmark);
      MenuItem tagItem = menu.findItem(R.id.cab_tag_bookmark);
      if (selected == null || !selected.isBookmark()) {
         removeItem.setVisible(false);
         tagItem.setVisible(false);
      } else {
         removeItem.setVisible(true);
         tagItem.setVisible(true);
      }
      return true;
   }
   
   @Override
   protected boolean actionItemClicked(ActionMode mode, int menuItemId,
         QuranActivity activity, QuranRow selected) {
      if (selected == null)
         return false;
      switch (menuItemId) {
      case R.id.cab_delete_bookmark:
         new RemoveBookmarkTask().execute(selected);
         return true;
      case R.id.cab_tag_bookmark:
         if (selected.isBookmark() && selected.bookmarkId >= 0) {
            activity.tagBookmark(selected.bookmarkId);
         } else {
            return false;
         }
         finishActionMode();
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
      BookmarksDBAdapter db = new BookmarksDBAdapter(getActivity());
      db.open();
      switch (mCurrentSortCriteria) {
      case R.id.sort_location:
         bookmarks = db.getBookmarks(false, BookmarksDBAdapter.SORT_LOCATION);
         break;
      case R.id.sort_date:
      default:
         bookmarks = db.getBookmarks(false, BookmarksDBAdapter.SORT_DATE_ADDED);
         break;
      }
      db.close();
      
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
            getActivity().getApplicationContext());
      int lastPage = prefs.getInt(Constants.PREF_LAST_PAGE, Constants.NO_PAGE_SAVED);
      boolean showLastPage = lastPage != Constants.NO_PAGE_SAVED;
      
      Activity activity = getActivity();

      List<QuranRow> rows = new ArrayList<QuranRow>();
      if (showLastPage){
         rows.add(new QuranRow(
               getString(R.string.bookmarks_current_page),
               null, QuranRow.HEADER, 0, 0, null));
         rows.add(new QuranRow(
               QuranInfo.getSuraNameString(activity, lastPage),
               QuranInfo.getPageSubtitle(activity, lastPage),
               QuranInfo.PAGE_SURA_START[lastPage-1], lastPage,
               R.drawable.bookmark_currentpage));
      }
      
      List<QuranRow> pageBookmarks = new ArrayList<QuranRow>();
      List<QuranRow> ayahBookmarks = new ArrayList<QuranRow>();
      for (Bookmark bookmark : bookmarks) {
         QuranRow row = createRow(activity, bookmark);
         if (bookmark.isPageBookmark())
            pageBookmarks.add(row);
         else
            ayahBookmarks.add(row);
      }

      if (!pageBookmarks.isEmpty()){
         rows.add(new QuranRow(getString(R.string.menu_bookmarks_page),
               null, QuranRow.HEADER, 0, 0, null));
         rows.addAll(pageBookmarks);
      }
      if (!ayahBookmarks.isEmpty()){
         rows.add(new QuranRow(getString(R.string.menu_bookmarks_ayah),
               null, QuranRow.HEADER, 0, 0, null));
         rows.addAll(ayahBookmarks);
      }
      
      return rows.toArray(new QuranRow[rows.size()]);
   }
   
   private QuranRow createRow(Activity activity, Bookmark bookmark) {
      QuranRow row = null;
      if (bookmark.isPageBookmark()) {
         int sura = QuranInfo.getSuraNumberFromPage(bookmark.mPage);
         row = new QuranRow(
               QuranInfo.getSuraNameString(activity, bookmark.mPage),
               QuranInfo.getPageSubtitle(activity, bookmark.mPage),
               QuranRow.PAGE_BOOKMARK, sura, bookmark.mPage,
               R.drawable.bookmark_page);
      } else {
         row = new QuranRow(
               QuranInfo.getAyahString(bookmark.mSura, bookmark.mAyah, getActivity()),
               QuranInfo.getPageSubtitle(activity, bookmark.mPage),
               QuranRow.AYAH_BOOKMARK, bookmark.mSura, bookmark.mAyah, bookmark.mPage,
               R.drawable.bookmark_ayah);
      }
      row.bookmarkId = bookmark.mId;
      return row;
   }
}
