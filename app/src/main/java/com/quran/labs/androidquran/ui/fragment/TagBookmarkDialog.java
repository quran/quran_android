package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import java.util.ArrayList;
import java.util.List;

import static com.quran.labs.androidquran.database.BookmarksDBAdapter.Tag;

public class TagBookmarkDialog extends SherlockDialogFragment {
   public static final String TAG = "TagBookmarkDialog";

   private long mBookmarkId = -1;
   private Integer mSura;
   private Integer mAyah;
   private int mPage = -1;
   private List<Tag> mTags;
   private List<Long> mBookmarkTags;
   private ArrayAdapter mAdapter;

   private ListView mListView;

   private static final String BOOKMARK_ID = "bookmarkid";
   private static final String PAGE = "page";
   private static final String SURA = "sura";
   private static final String AYAH = "ayah";

   public TagBookmarkDialog(long bookmarkId){
      mBookmarkId = bookmarkId;
   }
   
   public TagBookmarkDialog(Integer sura, Integer ayah, int page){
      mSura = sura;
      mAyah = ayah;
      mPage = page;
   }

   public TagBookmarkDialog(){
   }

   @Override
   public void onSaveInstanceState(Bundle outState) {
      outState.putLong(BOOKMARK_ID, mBookmarkId);
      outState.putInt(PAGE, mPage);
      outState.putInt(SURA, mSura == null? 0 : mSura);
      outState.putInt(AYAH, mAyah == null? 0 : mAyah);
      super.onSaveInstanceState(outState);
   }

   public void requestTagData(){
      new Thread(new Runnable() {
         @Override
         public void run() {
            Activity activity = getActivity();
            if (activity == null){ return; }

            BookmarksDBAdapter dba =
                    new BookmarksDBAdapter(activity);
            dba.open();
            final List<Tag> tags = dba.getTags();
            final List<Long> bookmarkTags = mBookmarkId >= 0 ? dba.getBookmarkTagIds(mBookmarkId) : null;
            dba.close();

//            tags.add(new Tag(0, getString(R.string.sample_bookmark_uncategorized)));
            mTags = tags;
            mTags.add(new Tag(-1, getString(R.string.add_tag)));
            mBookmarkTags = bookmarkTags;

            activity.runOnUiThread(new Runnable() {
               @Override
               public void run() {
                  mAdapter.clear();
                  for (Tag tag : mTags){
                     mAdapter.add(tag);
                     if (mBookmarkTags != null && mBookmarkTags.contains(tag.mId))
                        tag.setChecked(true);
                  }
                  mAdapter.notifyDataSetChanged();
               }
            });
         }
      }).start();
   }

   @Override
   public Dialog onCreateDialog(Bundle savedInstanceState) {
      final FragmentActivity activity = getActivity();

      if (savedInstanceState != null){
         mBookmarkId = savedInstanceState.getInt(BOOKMARK_ID);
         mSura = savedInstanceState.getInt(SURA);
         mAyah = savedInstanceState.getInt(AYAH);
         mPage = savedInstanceState.getInt(PAGE);

         if (mSura == 0){ mSura = null; }
         if (mAyah == 0){ mAyah = null; }
      }

      mTags = new ArrayList<Tag>();

      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      final LayoutInflater inflater = LayoutInflater.from(activity);
      mAdapter = new ArrayAdapter<Tag>(
              activity, R.layout.bookmark_row,
              R.id.bookmark_text, mTags) {
         @Override
         public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            
            if (convertView == null) {
               convertView = inflater.inflate(R.layout.tag_row, null);
               holder = new ViewHolder();
               holder.chk = (CheckBox) convertView.findViewById(R.id.tag_checkbox);
               holder.txt = (TextView) convertView.findViewById(R.id.tag_name);
               holder.img_del = (ImageView) convertView.findViewById(R.id.tag_remove_image);
               holder.img_new = (ImageView) convertView.findViewById(R.id.tag_add_image);
               convertView.setTag(holder);
            }
            final Tag tag = getItem(position);
            holder = (ViewHolder) convertView.getTag();
//            boolean checked = parent != null && parent instanceof ListView
//                  && ((ListView)parent).isItemChecked(position);
            holder.txt.setText(tag.toString());
            if (tag.mId == -1) {
               holder.img_new.setVisibility(View.VISIBLE);
               holder.img_del.setVisibility(View.GONE);
               holder.chk.setVisibility(View.GONE);
            } else {
               holder.img_new.setVisibility(View.GONE);
               holder.img_del.setVisibility(View.VISIBLE);
               holder.chk.setVisibility(View.VISIBLE);
               holder.chk.setChecked(tag.isChecked());
               holder.chk.setOnClickListener(new OnClickListener() {
                  public void onClick(View v) {
                     tag.toggle();
                  }
               });
               holder.img_del.setOnClickListener(new OnClickListener() {
                  public void onClick(View v) {
                     new Thread(new Runnable() {
                        @Override
                        public void run() {
                           // add the tag
                           BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
                           dba.open();
                           dba.removeTag(tag.mId, true);
                           dba.close();
                        }
                     }).start();
                  }
               });
            }
            return convertView;
         }
         class ViewHolder {
            CheckBox chk;
            TextView txt;
            ImageView img_del;
            ImageView img_new;
         }
      };

      mListView = new ListView(activity);
      mListView.setAdapter(mAdapter);
      mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
         public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Tag tag = (Tag)mAdapter.getItem(position);
            Activity currentActivity = getActivity();
            if (currentActivity != null && currentActivity instanceof OnTagSelectedListener){
               if (tag.mId == -1) {
                  SherlockDialogFragment addFragment = new AddTagAlertDialog();
                  addFragment.show(getFragmentManager(), AddTagAlertDialog.TAG);
               } else {
                  tag.toggle();
               }
            }
//               if (mBookmarkId < 0) {
//                  ((OnTagSelectedListener) currentActivity).onTagSelected(tag, mSura, mAyah, mPage);
//               } else {
//                  ((OnTagSelectedListener) currentActivity).onTagSelected(tag, mBookmarkId);
//               }
//            TagBookmarkDialog.this.dismiss();
         }
      });

      requestTagData();
      builder.setView(mListView);
      final Activity curAct = getActivity();
      builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int which) {
            new AsyncTask<Void, Void, Long>() {
               @Override
               protected Long doInBackground(Void... params) {
                  BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
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
                  if (curAct != null && curAct instanceof OnTagSelectedListener){
                     ((OnTagSelectedListener) curAct).onTagsUpdated(result);
                  }
               }
            }.execute();
         }
      });
      builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int which) {
            dismiss();
         }
      });
      return builder.create();
   }

   public interface OnTagSelectedListener {
      public void onTagsUpdated(long bookmarkId);
      public void onTagSelected(Tag tag, long bookmarkId);
      public void onTagSelected(Tag tag, Integer sura, Integer ayah, int page);
      public void onAddTagSelected();
   }
   
   public class AddTagAlertDialog extends SherlockDialogFragment {
      public static final String TAG = "AddTagAlertDialog";

      @Override
      public Dialog onCreateDialog(Bundle savedInstanceState) {
         final EditText nameText = new EditText(getActivity());

         return new AlertDialog.Builder(getActivity())
               .setTitle(getString(R.string.bookmark_tag_title))
               .setView(nameText)
               .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int whichButton) {
                     dismiss();
                  }
               })
               .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
                     new AsyncTask<Void, Void, Long>() {
                        @Override
                        protected Long doInBackground(Void... params) {
                           BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
                           dba.open();
                           long id = dba.addTag(nameText.getText().toString());
                           dba.close();
                           return id;
                        }
                        @Override
                        protected void onPostExecute(Long result) {
                           if (mTags != null && mAdapter != null) {
                              Tag t = new Tag(result, nameText.getText().toString());
                              t.setChecked(true);
                              mTags.add(mTags.size()-1, t);
                              // TODO Hack to get around android adapter storing copy instead of reference. Use custom adapter.
                              mAdapter.clear();
                              for (Tag tag : mTags) {
                                 mAdapter.add(tag);
                              }
                              mAdapter.notifyDataSetChanged();
                           }
                        };
                     }.execute();
                  }
               })
               .create();
      }
  }   
   
}
