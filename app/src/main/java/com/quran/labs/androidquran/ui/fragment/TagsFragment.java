package com.quran.labs.androidquran.ui.fragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.BookmarksDBAdapter.Bookmark;
import com.quran.labs.androidquran.database.BookmarksDBAdapter.Tag;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

public class TagsFragment extends AbsMarkersFragment {
   
   private static final int[] VALID_SORT_OPTIONS = {R.id.sort_alphabetical, R.id.sort_date};

   public static TagsFragment newInstance(){
      return new TagsFragment();
   }
   
   @Override
   protected int getContextualMenuId() {
      return R.menu.tag_menu;
   }
   
   @Override
   protected int getEmptyListStringId() {
      return R.string.tags_list_empty;
   }
   
   @Override
   protected int[] getValidSortOptions() {
      return VALID_SORT_OPTIONS;
   }
   
   @Override
   protected boolean prepareActionMode(ActionMode mode, Menu menu, QuranRow selected) {
      MenuItem editItem = menu.findItem(R.id.cab_edit_tag);
      MenuItem removeItem = menu.findItem(R.id.cab_delete_tag);
      MenuItem tagItem = menu.findItem(R.id.cab_tag_bookmark);
      if (selected == null) {
         editItem.setVisible(false);
         removeItem.setVisible(false);
         tagItem.setVisible(false);
      } else if (selected.isBookmarkHeader()) {
         tagItem.setVisible(false);
         editItem.setVisible(selected.tagId >= 0);
         removeItem.setVisible(selected.tagId >= 0);
      } else if (selected.isBookmark()) {
         tagItem.setVisible(true);
         editItem.setVisible(false);
         removeItem.setVisible(selected.tagId >= 0);
      }
      return true;
   }
   
   @Override
   protected boolean actionItemClicked(ActionMode mode, int menuItemId,
         QuranActivity activity, QuranRow selected) {
      switch (menuItemId) {
      case R.id.cab_delete_tag:
         if (selected == null)
            return false;
         new RemoveBookmarkTask().execute(selected);
         return true;
      case R.id.cab_new_tag:
         activity.addTag();
         finishActionMode();
         return true;
      case R.id.cab_edit_tag:
         if (selected != null && selected.isBookmarkHeader()){
            activity.editTag(selected.tagId, selected.text);
         } else {
            return false;
         }
         finishActionMode();
         return true;
      case R.id.cab_tag_bookmark:
         if (selected != null && selected.isBookmark()) {
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
      return getTags();
   }
   
   private QuranRow[] getTags(){
      Activity activity = getActivity();
      if (activity == null){ return null; }

      QuranActivity quranActivity = (QuranActivity)activity;
      BookmarksDBAdapter db = quranActivity.getBookmarksAdapter();

      List<Tag> tags;
      switch (mCurrentSortCriteria) {
      case R.id.sort_date:
         tags = db.getTags(BookmarksDBAdapter.SORT_DATE_ADDED);
         break;
      case R.id.sort_alphabetical:
      default:
         tags = db.getTags(BookmarksDBAdapter.SORT_ALPHABETICAL);
         break;
      }
      List<Bookmark> bookmarks = db.getBookmarks(true);

      List<QuranRow> rows = new ArrayList<QuranRow>();
      
      List<Bookmark> unTagged = new ArrayList<Bookmark>();
      Map<Long, List<Bookmark>> tagMap = new HashMap<Long, List<Bookmark>>();
      
      for (Bookmark bookmark : bookmarks){
         List<Tag> bookmarkTags = bookmark.mTags;
         if (bookmarkTags == null) {
            unTagged.add(bookmark);
         } else {
            for (Tag tag : bookmarkTags) {
               List <Bookmark> tagBookmarkList = tagMap.get(tag.mId);
               if (tagBookmarkList == null) {
                  List<Bookmark> newList = new ArrayList<Bookmark>();
                  newList.add(bookmark);
                  tagMap.put(tag.mId, newList);
               } else {
                  tagBookmarkList.add(bookmark);
               }
            }
         }
      }
      
      for (Tag tag : tags) {
         List<Bookmark> tagBookmarkList = tagMap.get(tag.mId);

         // add the tag header
         QuranRow bookmarkHeader = new QuranRow(
                 tag.mName,
                 /*tag.mDescription*/"TAG",
                 QuranRow.BOOKMARK_HEADER, 0, 0, null);
         bookmarkHeader.tagId = tag.mId;
         
         rows.add(bookmarkHeader);

         // no bookmarks in this tag, so move on
         if (tagBookmarkList == null || tagBookmarkList.isEmpty()){ continue; }

         // and now the bookmarks
         for (Bookmark bookmark : tagBookmarkList) {
            QuranRow row = createRow(activity, tag.mId, bookmark);
            rows.add(row);
         }
      }
      
      if (unTagged.size() > 0) {
         QuranRow header = new QuranRow(
                 activity.getString(R.string.not_tagged), "",
                 QuranRow.BOOKMARK_HEADER, 0, 0, null);
         header.tagId = -1;
         
         rows.add(header);

         for (Bookmark bookmark : unTagged) {
            QuranRow row = createRow(activity, -1, bookmark);
            rows.add(row);
         }
      }
      
      return rows.toArray(new QuranRow[rows.size()]);
   }

   private QuranRow createRow(Activity activity, long tagId, Bookmark bookmark) {
      QuranRow row = null;
      if (bookmark.mSura == null) {
         int sura = QuranInfo.getSuraNumberFromPage(bookmark.mPage);
         row = new QuranRow(
               QuranInfo.getSuraNameString(activity, bookmark.mPage),
               QuranInfo.getPageSubtitle(activity, bookmark.mPage),
               QuranRow.PAGE_BOOKMARK, sura, bookmark.mPage,
               R.drawable.bookmark_page);
      } else {
         row = new QuranRow(
               QuranInfo.getAyahString(bookmark.mSura,
                       bookmark.mAyah, getActivity()),
               QuranInfo.getPageSubtitle(activity, bookmark.mPage),
               QuranRow.AYAH_BOOKMARK, bookmark.mSura,
                 bookmark.mAyah, bookmark.mPage,
               R.drawable.bookmark_ayah);
      }
      row.tagId = tagId;
      row.bookmarkId = bookmark.mId;
      return row;
   }
}
