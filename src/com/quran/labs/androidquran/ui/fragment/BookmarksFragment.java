package com.quran.labs.androidquran.ui.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.BookmarksDBAdapter.Bookmark;
import com.quran.labs.androidquran.database.BookmarksDBAdapter.BookmarkMap;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

public class BookmarksFragment extends SherlockFragment {

   private ListView mListView;
   private ActionMode mMode;
   private QuranListAdapter mAdapter;
   private TextView mEmptyTextView;
   private AsyncTask<Void, Void, QuranRow[]> loadingTask = null;

   public static BookmarksFragment newInstance(){
      return new BookmarksFragment();
   }
   
   @Override
   public View onCreateView(LayoutInflater inflater,
         ViewGroup container, Bundle savedInstanceState){
      View view = inflater.inflate(R.layout.quran_list, container, false);
      mListView = (ListView)view.findViewById(R.id.list);
      mEmptyTextView = (TextView)view.findViewById(R.id.empty_list);
      mListView.setEmptyView(mEmptyTextView);
      
      mAdapter = new QuranListAdapter(getActivity(),
            R.layout.index_sura_row, new QuranRow[]{});
      mListView.setAdapter(mAdapter);
      
      mMode = null;
      mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
      mListView.setItemsCanFocus(false);
      
      mListView.setOnItemClickListener(new OnItemClickListener(){
         public void onItemClick(AdapterView<?> parent, View v,
               int position, long id){
            QuranRow elem = (QuranRow)mAdapter.getItem((int)id);
            
            // If we're in CAB mode don't handle the click
            if (mMode != null){
               mListView.setItemChecked(position,
                       mListView.isItemChecked(position));
               mMode.invalidate();
               return;
            } else {
               mListView.setItemChecked(position, false);
            }
            
            // We're not in CAB mode so handle the click normally
            if (!elem.isHeader()) {
               if (elem.isAyahBookmark()){
                  ((QuranActivity)getActivity()).jumpToAndHighlight(
                        elem.page, elem.sura, elem.ayah);
               }
               else {
                  ((QuranActivity)getActivity()).jumpTo(elem.page);
               }
            }
         }
      });
      
      mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
         public boolean onItemLongClick(AdapterView<?> parent, View view,
               int position, long id) {
            if (!mListView.isItemChecked(position)) {
               mListView.setItemChecked(position, true);
               if (mMode != null)
                  mMode.invalidate();
               else
                  mMode = getSherlockActivity().startActionMode(new ModeCallback());
            } else if (mMode != null) {
               mMode.finish();
            }
            
            return true;
         }
      });
      
      return view;
   }
   
   @Override
   public void onResume() {
      super.onResume();
      if (loadingTask == null){
         loadingTask = new BookmarksLoadingTask();
         loadingTask.execute();
      }
   }
   
   @Override
   public void onPause() {
      super.onPause();
      if (loadingTask != null){
         loadingTask.cancel(true);
      }
      loadingTask = null;
      // If currently in CAB mode, end it
      if (mMode != null) {
         mMode.finish();
      }
   }

   private class ModeCallback implements ActionMode.Callback {

      @Override
      public boolean onCreateActionMode(ActionMode mode, Menu menu) {
         MenuInflater inflater = getSherlockActivity().getSupportMenuInflater();
         inflater.inflate(R.menu.bookmark_menu, menu);
         return true;
      }

      @Override
      public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
         MenuItem editItem = menu.findItem(R.id.cab_edit_bookmark);
         MenuItem removeItem = menu.findItem(R.id.cab_remove_bookmark);
         int position = mListView.getCheckedItemPosition();
         if (position < 0 || position >= mAdapter.getCount()) {
            editItem.setVisible(false);
            removeItem.setVisible(false);
            return true;
         }
         QuranRow selected = (QuranRow)mAdapter.getItem(position);
         if (selected.isBookmarkHeader()) {
            editItem.setVisible(true);
            removeItem.setVisible(true);
         } else if (selected.isBookmark()) {
            editItem.setVisible(false);
            removeItem.setVisible(true);
         } else {
            editItem.setVisible(false);
            removeItem.setVisible(false);
         }
         return true;
      }

      @Override
      public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
         switch (item.getItemId()) {
         case R.id.cab_remove_bookmark:
            int position = mListView.getCheckedItemPosition();
            if (position < 0 || position >= mAdapter.getCount())
               return false;
            QuranRow row = (QuranRow)mAdapter.getItem(position);
            new RemoveBookmarkTask().execute(row);
            return true;
         case R.id.cab_new_bookmark:
            showBookmarkDialog(null);
            mMode.finish();
            return true;
         case R.id.cab_edit_bookmark:
            int selectedBookmark = mListView.getCheckedItemPosition();
            if (selectedBookmark < 0 || selectedBookmark >= mAdapter.getCount())
               return false;
            QuranRow bookmark = (QuranRow)mAdapter.getItem(selectedBookmark);
            if (!bookmark.isBookmarkHeader() || bookmark.bookmarkId < 0)
               return false;
            new ShowBookmarkDialogTask().execute(bookmark.bookmarkId);
            mMode.finish();
            return true;
         default:
            return false;
         }
      }

      @Override
      public void onDestroyActionMode(ActionMode mode) {
         int checkedPosition = mListView.getCheckedItemPosition();
         if (checkedPosition >= 0 && checkedPosition < mAdapter.getCount())
            mListView.setItemChecked(checkedPosition, false);
         if (mode == mMode)
            mMode = null;
      }
      
   }
   
   private class SaveBookmarkTask extends AsyncTask<Bookmark, Void, Void> {
      @Override
      protected Void doInBackground(Bookmark... params) {
         BookmarksDBAdapter db = new BookmarksDBAdapter(getActivity());
         db.open();
         db.saveBookmark(params[0]);
         db.close();
         return null;
      }
      
      @Override
      protected void onPostExecute(Void result) {
         if (loadingTask == null){
            loadingTask = new BookmarksLoadingTask();
            loadingTask.execute();
         }
      }
   }
   
   private class RemoveBookmarkTask extends AsyncTask<QuranRow, Void, Void> {
      @Override
      protected Void doInBackground(QuranRow... params) {
         BookmarksDBAdapter db = new BookmarksDBAdapter(getActivity());
         db.open();
         for (int i = 0; i < params.length; i++) {
            QuranRow elem = params[i];
            if (elem.isBookmarkHeader() && elem.bookmarkId >= 0) {
               db.deleteBookmark(elem.bookmarkId);
            } else if (elem.isBookmark() && elem.bookmarkMapId >= 0) {
               db.deleteBookmarkMap(elem.bookmarkMapId);
            }
         }
         db.close();
         return null;
      }
      
      @Override
      protected void onPostExecute(Void result) {
          if (loadingTask == null){
              loadingTask = new BookmarksLoadingTask();
              loadingTask.execute();
           }
      }
   }

   private class ShowBookmarkDialogTask extends AsyncTask<Long, Void, Bookmark> {
      
      @Override
      protected Bookmark doInBackground(Long... params) {
         if (params[0] == null) return null;
         BookmarksDBAdapter db = new BookmarksDBAdapter(getActivity());
         db.open();
         Bookmark result = db.getBookmark(params[0]);
         db.close();
         return result;
      }
      
      @Override
      protected void onPostExecute(final Bookmark result) {
         if (result == null) return;
         showBookmarkDialog(result);
      }
   }
   
   private class BookmarksLoadingTask extends AsyncTask<Void, Void, QuranRow[]> {
      @Override
      protected QuranRow[] doInBackground(Void... params) {
         return getBookmarks();
      }

      @Override
      protected void onPostExecute(QuranRow[] result) {
         if (result.length == 0){
            mEmptyTextView.setText(R.string.no_bookmarks);
         }
         mAdapter.setElements(result);
         mAdapter.notifyDataSetChanged();
         loadingTask = null;
         
         if (mMode != null)
            mMode.invalidate();
      }
   }

   private QuranRow[] getBookmarks(){
      BookmarksDBAdapter db = new BookmarksDBAdapter(getActivity());
      db.open();
      Map<Long, Bookmark> bookmarks = db.getBookmarkMaps();
      db.close();
      
      Activity activity = getActivity();
      List<QuranRow> rows = new ArrayList<QuranRow>();
      
      SharedPreferences prefs = PreferenceManager
            .getDefaultSharedPreferences(
                  getActivity().getApplicationContext());
      int lastPage = prefs.getInt(Constants.PREF_LAST_PAGE,
            Constants.NO_PAGE_SAVED);
      if (lastPage != Constants.NO_PAGE_SAVED){
         QuranRow header = new QuranRow(
               getString(R.string.bookmarks_current_page),
               null, QuranRow.HEADER, 0, 0, null);
         rows.add(header);
         QuranRow currentPosition = new QuranRow(
               QuranInfo.getSuraNameString(activity, lastPage),
               QuranInfo.getPageSubtitle(activity, lastPage),
               QuranInfo.PAGE_SURA_START[lastPage-1], lastPage,
               R.drawable.bookmark_page);
         rows.add(currentPosition);
      }
      
      for (Bookmark bookmark : bookmarks.values()) {
         QuranRow bookmarkHeader = new QuranRow(
               bookmark.name,
               null, QuranRow.BOOKMARK_HEADER, 0, 0, null);
         bookmarkHeader.bookmarkId = bookmark.id;
         rows.add(bookmarkHeader);
         for (BookmarkMap bookmarkMap : bookmark.bookmarkMaps) {
            QuranRow row = null;
            if (bookmarkMap.isPageBookmark) {
               row = new QuranRow(
                     QuranInfo.getSuraNameString(activity, bookmarkMap.page),
                     QuranInfo.getPageSubtitle(activity, bookmarkMap.page),
                     QuranRow.PAGE_BOOKMARK, bookmarkMap.sura, bookmarkMap.page,
                     R.drawable.bookmark_page);
            } else {
               row = new QuranRow(
                     QuranInfo.getAyahString(bookmarkMap.sura, bookmarkMap.ayah, getActivity()),
                     QuranInfo.getPageSubtitle(activity, bookmarkMap.page),
                     QuranRow.AYAH_BOOKMARK, bookmarkMap.sura, bookmarkMap.ayah, bookmarkMap.page,
                     R.drawable.bookmark_ayah);
            }
            row.bookmarkMapId = bookmarkMap.bookmarkMapId;
            row.bookmarkId = bookmarkMap.bookmarkId;
            rows.add(row);
         }
      }
      
      return rows.toArray(new QuranRow[rows.size()]);
   }
   
   private void showBookmarkDialog(final Bookmark bookmark) {
      LayoutInflater inflater = getActivity().getLayoutInflater();
      View layout = inflater.inflate(R.layout.bookmark_dialog, null);

      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setTitle(getString(R.string.bookmark_dlg_title));
      
      // Page text
      final EditText nameText = (EditText)layout.findViewById(R.id.bookmark_name);
      final EditText descriptionText = (EditText)layout.findViewById(R.id.bookmark_description);
      final CheckBox isCollectionChkBx = (CheckBox)layout.findViewById(R.id.bookmark_is_collection);
      final ImageButton collectionHint = (ImageButton)layout.findViewById(R.id.bookmark_collection_hint);
      collectionHint.setOnClickListener(new OnClickListener() {
         public void onClick(View v) {
            Toast.makeText(getActivity(), R.string.bookmark_collection_hint, Toast.LENGTH_LONG).show();
         }
      });
      
      if (bookmark != null) {
         nameText.setText(bookmark.name != null ? bookmark.name : "");
         descriptionText.setText(bookmark.description != null ? bookmark.description : "");
         isCollectionChkBx.setChecked(bookmark.isCollection);
      }

      builder.setView(layout);
      builder.setPositiveButton(getString(R.string.dialog_ok),
            new DialogInterface.OnClickListener() {
               @Override
               public void onClick(DialogInterface dialog, int which) {
                  Long bookmarkId = null;
                  if (bookmark != null && bookmark.id >= 0) {
                     bookmarkId = bookmark.id;
                  }
                  String bookmarkName = nameText.getText().toString();
                  String bookmarkDescription = descriptionText.getText().toString();
                  boolean isCollection = isCollectionChkBx.isChecked();
                  Bookmark bookmark = new Bookmark(bookmarkId, bookmarkName, isCollection, bookmarkDescription);
                  new SaveBookmarkTask().execute(bookmark);
                  dialog.dismiss();
               }
            });
      
      AlertDialog dlg = builder.create();
      dlg.show();
   }
}
