package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import com.actionbarsherlock.app.SherlockDialogFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.BookmarksDBAdapter.Tag;

import java.util.ArrayList;
import java.util.List;

public class TagBookmarkDialog extends SherlockDialogFragment {
   public static final String TAG = "TagBookmarkDialog";

   private long mBookmarkId = -1;
   private Integer mSura;
   private Integer mAyah;
   private int mPage = -1;
   private List<Tag> mTags;
   private List<Long> mBookmarkTags;
   private TagsAdapter mAdapter;

   private ListView mListView;

   private static final String BOOKMARK_ID = "bookmarkid";
   private static final String PAGE = "page";
   private static final String SURA = "sura";
   private static final String AYAH = "ayah";
   private static final String TAG_LIST = "taglist";

   public TagBookmarkDialog(long bookmarkId){
      mBookmarkId = bookmarkId;
   }
   
   public TagBookmarkDialog(Integer sura, Integer ayah, int page){
      mSura = sura;
      mAyah = ayah;
      mPage = page;
   }

   // do not remove - this is required when resuming from onSaveInstanceState
   public TagBookmarkDialog(){
   }

   @Override
   public void onSaveInstanceState(Bundle outState) {
      outState.putLong(BOOKMARK_ID, mBookmarkId);
      outState.putInt(PAGE, mPage);
      outState.putInt(SURA, mSura == null? 0 : mSura);
      outState.putInt(AYAH, mAyah == null? 0 : mAyah);
      outState.putParcelableArrayList(TAG_LIST,
              (ArrayList<? extends Parcelable>) mTags);
      super.onSaveInstanceState(outState);
   }
   
   public void handleTagAdded(String name) {
	   new AddTagTask().execute(name);
   }

   @Override
   public Dialog onCreateDialog(Bundle savedInstanceState) {
      final FragmentActivity activity = getActivity();

      if (savedInstanceState != null){
         mBookmarkId = savedInstanceState.getLong(BOOKMARK_ID);
         mSura = savedInstanceState.getInt(SURA);
         mAyah = savedInstanceState.getInt(AYAH);
         mPage = savedInstanceState.getInt(PAGE);
         mTags = savedInstanceState.getParcelableArrayList(TAG_LIST);

         if (mSura == 0){ mSura = null; }
         if (mAyah == 0){ mAyah = null; }
      }

      if (mTags == null) {
         mTags = new ArrayList<Tag>();
         new RefreshTagsTask().execute();
      }

      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      mAdapter = new TagsAdapter(activity);

      mListView = new ListView(activity);
      mListView.setAdapter(mAdapter);
      mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
         public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Tag tag = (Tag)mAdapter.getItem(position);
            if (tag.mId >= 0) {
            	tag.toggle();
            }
            else if (tag.mId == -1) {
	           Context context = getActivity();
	           if (context != null &&
                      context instanceof OnBookmarkTagsUpdateListener) {
                  ((OnBookmarkTagsUpdateListener)context).onAddTagSelected();
	           }
            }

            if (view.getTag() != null){
               Object viewTag = view.getTag();
               if (viewTag instanceof ViewHolder){
                  ViewHolder holder = (ViewHolder)viewTag;
                  holder.checkBox.setChecked(tag.isChecked());
               }
            }
         }
      });

      builder.setView(mListView);
      builder.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int which) {
             final Activity curAct = getActivity();
             if (curAct != null &&
                     curAct instanceof OnBookmarkTagsUpdateListener){
                 new UpdateBookmarkTagsTask(
                         (OnBookmarkTagsUpdateListener)curAct).execute();
             }
         }
      });
      builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int which) {
            dismiss();
         }
      });
      return builder.create();
   }

   public class TagsAdapter extends BaseAdapter {
      private LayoutInflater mInflater;

      public TagsAdapter(Context context){
         mInflater = LayoutInflater.from(context);
      }

      @Override
      public int getCount() {
         return mTags.size();
      }

      @Override
      public Object getItem(int position) {
         return mTags.get(position);
      }

      @Override
      public long getItemId(int position) {
         return mTags.get(position).mId;
      }

      @Override
      public boolean hasStableIds() {
         return false;
      }

      @Override
      public View getView(int position, View convertView,
                          ViewGroup parent) {
         ViewHolder holder;
         if (convertView == null) {
            convertView = mInflater.inflate(R.layout.tag_row, null);
            holder = new ViewHolder();
            holder.checkBox = (CheckBox)convertView
                    .findViewById(R.id.tag_checkbox);
            holder.tagName = (TextView)convertView
                    .findViewById(R.id.tag_name);
            holder.addImage = (ImageView)convertView
                    .findViewById(R.id.tag_add_image);
            convertView.setTag(holder);
         }
         final Tag tag = (Tag)getItem(position);
         holder = (ViewHolder) convertView.getTag();
         holder.tagName.setText(tag.toString());
         if (tag.mId == -1) {
            holder.addImage.setVisibility(View.VISIBLE);
            holder.checkBox.setVisibility(View.GONE);
         }
         else {
            holder.addImage.setVisibility(View.GONE);
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(tag.isChecked());
            holder.checkBox.setOnClickListener(new OnClickListener() {
               public void onClick(View v) {
                  tag.toggle();
               }
            });
         }
         return convertView;
      }
   }
   
   class RefreshTagsTask extends AsyncTask<Void, Void, Void> {
      @Override
      protected Void doInBackground(Void... params) {
          BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
          dba.open();
          final List<Tag> tags = dba.getTags();
          if (mBookmarkId < 0 && mPage > 0) {
             mBookmarkId = dba.getBookmarkId(mSura, mAyah, mPage);
          }
          final List<Long> bookmarkTags = mBookmarkId < 0 ?
                  null : dba.getBookmarkTagIds(mBookmarkId);
          dba.close();

          mTags = tags;
          mTags.add(new Tag(-1, getString(R.string.new_tag)));
          mBookmarkTags = bookmarkTags;
          return null;
      }
      
      @Override
      protected void onPostExecute(Void result) {
          for (Tag tag : mTags){
             if (mBookmarkTags != null && mBookmarkTags.contains(tag.mId)){
                tag.setChecked(true);
             }
          }
          mAdapter.notifyDataSetChanged();
      }
   }

   class ViewHolder {
      CheckBox checkBox;
      TextView tagName;
      ImageView addImage;
   }
   
   class AddTagTask extends AsyncTask<String, Void, Tag> {
       @Override
       protected Tag doInBackground(String... params) {
          BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
          dba.open();
          long id = dba.addTag(params[0]);
          dba.close();
          Tag t = new Tag(id, params[0]);
          return t;
       }
       @Override
       protected void onPostExecute(Tag result) {
          if (mTags != null && mAdapter != null) {
             result.setChecked(true);
             mTags.add(mTags.size() - 1, result);
             mAdapter.notifyDataSetChanged();
          }
       };
   }
   
   class UpdateBookmarkTagsTask extends AsyncTask<Void, Void, Long> {
      private OnBookmarkTagsUpdateListener mListener;
      public UpdateBookmarkTagsTask(OnBookmarkTagsUpdateListener listener) {
         mListener = listener;
      }
      @Override
      protected Long doInBackground(Void... params) {
          BookmarksDBAdapter dba = new BookmarksDBAdapter((Activity)mListener);
          dba.open();
          long bookmarkId = mBookmarkId;
          if (bookmarkId < 0) {
             bookmarkId = dba.addBookmarkIfNotExists(mSura, mAyah, mPage);
          }
          dba.tagBookmark(bookmarkId, mTags);
          dba.close();
          return bookmarkId;
      }
      @Override
      protected void onPostExecute(Long result) {
         mListener.onBookmarkTagsUpdated(result);
      }
   }

   public interface OnBookmarkTagsUpdateListener {
      public void onBookmarkTagsUpdated(long bookmarkId);
      public void onAddTagSelected();
   }
   
}
