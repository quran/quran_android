package com.quran.labs.androidquran.ui.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.v4.app.FragmentManager;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.widget.Toast;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.TranslationManagerActivity;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;
import com.quran.labs.androidquran.ui.helpers.BookmarkHandler;
import com.quran.labs.androidquran.util.QuranAppUtils;
import com.quran.labs.androidquran.util.TranslationUtils;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: ahmedre
 * Date: 5/14/13
 * Time: 7:29 PM
 */
public class AyahMenuUtils {
   private AsyncTask mCurrentTask;
   private ProgressDialog mProgressDialog;
   private AlertDialog mTranslationDialog;
   private WeakReference<Activity> mActivityRef;

   public AyahMenuUtils(Activity activity){
      mActivityRef = new WeakReference<Activity>(activity);
   }

   public void cleanup(){
      if (mProgressDialog != null){
         mProgressDialog.hide();
         mProgressDialog = null;
      }

      if (mTranslationDialog != null){
         mTranslationDialog.dismiss();
         mTranslationDialog = null;
      }

      if (mCurrentTask != null){ mCurrentTask.cancel(true); }
      mCurrentTask = null;
   }

   private Activity getActivity(){
      if (mActivityRef != null){
         return mActivityRef.get();
      }
      return null;
   }

   public void showMenu(int sura, int ayah, int page){
      new ShowAyahMenuTask().execute(sura, ayah, page);
   }

   class ShowAyahMenuTask extends AsyncTask<Integer, Void, Boolean> {
      int mSura;
      int mAyah;
      int mPage;

      @Override
      protected Boolean doInBackground(Integer... params) {
         mSura = params[0];
         mAyah = params[1];
         mPage = params[2];

         BookmarksDBAdapter adapter = null;
         Activity activity = getActivity();
         if (activity != null && activity instanceof BookmarkHandler){
            adapter = ((BookmarkHandler) activity).getBookmarksAdapter();
         }

         if (adapter == null){ return null; }

         boolean bookmarked = adapter.getBookmarkId(mSura, mAyah, mPage) >= 0;
         return bookmarked;
      }

      @Override
      protected void onPostExecute(Boolean result) {
         if (result != null){
            showAyahMenu(mSura, mAyah, mPage, result);
         }
      }

   }

   private void showAyahMenu(final int sura, final int ayah,
                             final int page, boolean bookmarked) {
      final Activity activity = getActivity();
      if (activity == null){ return; }

      int[] optionIds = {
              bookmarked? R.string.unbookmark_ayah : R.string.bookmark_ayah,
              R.string.tag_ayah,
              R.string.translation_ayah, R.string.share_ayah,
              R.string.share_ayah_text, R.string.copy_ayah,
              R.string.play_from_here
                 /*, R.string.ayah_notes*/}; // TODO Enable notes
      CharSequence[] options = new CharSequence[optionIds.length];
      for (int i=0; i<optionIds.length; i++){
         options[i] = activity.getString(optionIds[i]);
      }

      AlertDialog.Builder builder = new AlertDialog.Builder(activity);
      builder.setTitle(QuranInfo.getAyahString(sura, ayah, activity));
      builder.setItems(options, new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int selection) {
            if (selection == 0) {
               if (activity != null && activity instanceof PagerActivity){
                  PagerActivity pagerActivity = (PagerActivity) activity;
                  pagerActivity.toggleBookmark(sura, ayah, page);
               }
            }
            else if (selection == 1) {
               if (activity != null && activity instanceof PagerActivity){
                  PagerActivity pagerActivity = (PagerActivity) activity;
                  FragmentManager fm =
                          pagerActivity.getSupportFragmentManager();
                  TagBookmarkDialog tagBookmarkDialog =
                          new TagBookmarkDialog(sura, ayah, page);
                  tagBookmarkDialog.show(fm, TagBookmarkDialog.TAG);
               }
            }
            else if (selection == 2) {
               mCurrentTask = new ShowTafsirTask(sura, ayah).execute();
            }
            else if (selection == 3){
               mCurrentTask = new ShareQuranApp().execute(sura, ayah);
            }
            else if (selection == 4) {
               mCurrentTask = new ShareAyahTask(sura, ayah, false).execute();
            }
            else if (selection == 5) {
               mCurrentTask = new ShareAyahTask(sura, ayah, true).execute();
            }
            else if (selection == 6) {
               if (activity instanceof PagerActivity) {
                  PagerActivity pagerActivity = (PagerActivity) activity;
                  pagerActivity.playFromAyah(page, sura, ayah);
               }
            }
               /* else if (selection == 5) {
                  new ShowNotesTask(sura, ayah).execute();
					} */
         }
      }).setOnCancelListener(new DialogInterface.OnCancelListener() {
         @Override
         public void onCancel(DialogInterface dialogInterface) {
            dialogInterface.dismiss();
            mTranslationDialog = null;
         }
      });

      mTranslationDialog = builder.create();
      mTranslationDialog.show();
   }

   class ShareQuranApp extends AsyncTask<Integer, Void, String> {

      @Override
      protected void onPreExecute() {
         Activity activity = getActivity();
         if (activity != null){
            mProgressDialog = new ProgressDialog(activity);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setMessage(
                    activity.getString(R.string.index_loading));
            mProgressDialog.show();
         }
      }

      @Override
      protected String doInBackground(Integer... params){
         String url = null;
         if (params.length > 0){
            Integer endAyah = null;
            Integer startAyah = null;
            int sura = params[0];
            if (params.length > 1){
               startAyah = params[1];
               if (params.length > 2){
                  endAyah = params[2];
               }
            }
            url = QuranAppUtils.getQuranAppUrl(sura,
                    startAyah, endAyah);
         }
         return url;
      }

      @Override
      protected void onPostExecute(String url) {
         if (mProgressDialog != null && mProgressDialog.isShowing()){
            mProgressDialog.dismiss();
            mProgressDialog = null;
         }

         Activity activity = getActivity();
         if (activity != null && !TextUtils.isEmpty(url)){
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, url);
            activity.startActivity(Intent.createChooser(intent,
                    activity.getString(R.string.share_ayah)));
         }

         mCurrentTask = null;
      }
   }

   class ShareAyahTask extends AsyncTask<Void, Void, String> {
      private int sura, ayah;
      private boolean copy;

      public ShareAyahTask(int sura, int ayah, boolean copy) {
         this.sura = sura;
         this.ayah = ayah;
         this.copy = copy;
      }

      @Override
      protected String doInBackground(Void... params) {
         String text = null;
         try {
            DatabaseHandler ayahHandler =
                    new DatabaseHandler(getActivity(),
                            QuranDataProvider.QURAN_ARABIC_DATABASE);
            Cursor cursor = ayahHandler.getVerses(sura, ayah, sura, ayah,
                    DatabaseHandler.ARABIC_TEXT_TABLE);
            if (cursor.moveToFirst()) {
               text = cursor.getString(2);
            }
            cursor.close();
            ayahHandler.closeDatabase();
         }
         catch (Exception e){
         }

         return text;
      }

      @Override
      protected void onPostExecute(String ayah) {
         Activity activity = getActivity();
         if (ayah != null && activity != null) {
            ayah = "(" + ayah + ")" + " " + "["
                    + QuranInfo.getSuraName(activity, this.sura, true)
                    + " : " + this.ayah + "]" + activity.getString(R.string.via_string);
            if (copy) {
               ClipboardManager cm = (ClipboardManager)activity.
                       getSystemService(Activity.CLIPBOARD_SERVICE);
               if (cm != null){
                  cm.setText(ayah);
                  Toast.makeText(activity, activity.getString(
                       R.string.ayah_copied_popup),
                       Toast.LENGTH_SHORT).show();
               }
            } else {
               final Intent intent = new Intent(Intent.ACTION_SEND);
               intent.setType("text/plain");
               intent.putExtra(Intent.EXTRA_TEXT, ayah);
               activity.startActivity(Intent.createChooser(intent,
                       activity.getString(R.string.share_ayah)));
            }
         }
         mCurrentTask = null;
      }
   }

   class ShowTafsirTask extends AsyncTask<Void, Void, String> {
      private int sura, ayah;

      public ShowTafsirTask(int sura, int ayah) {
         this.sura = sura;
         this.ayah = ayah;
      }

      @Override
      protected String doInBackground(Void... params) {
         Activity activity = getActivity();
         List<TranslationItem> translationItems = null;
         if (activity instanceof PagerActivity){
            translationItems = ((PagerActivity)activity).getTranslations();
         }

         String db = TranslationUtils.getDefaultTranslation(activity,
                 translationItems);

         if (db != null) {
            try {
               DatabaseHandler tafsirHandler = new DatabaseHandler(getActivity(), db);
               Cursor cursor = tafsirHandler.getVerse(sura, ayah);
               if (cursor.moveToFirst()) {
                  String text = cursor.getString(2);
                  cursor.close();
                  tafsirHandler.closeDatabase();
                  return text;
               }
            }
            catch (Exception e){
            }
         }
         return null;
      }

      @Override
      protected void onPostExecute(String text) {
         final Activity activity = getActivity();
         if (activity != null && text != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setMessage(text)
                    .setCancelable(true)
                    .setPositiveButton(activity.getString(R.string.dialog_ok),
                            new DialogInterface.OnClickListener() {
                               @Override
                               public void onClick(DialogInterface dialog, int which) {
                                  dialog.dismiss();
                                  mTranslationDialog = null;
                               }
                            }).
                            setNeutralButton(R.string.show_more,
                                    new DialogInterface.OnClickListener() {
                                       @Override
                                       public void onClick(DialogInterface dialog, int which) {
                                          dialog.dismiss();
                                          mTranslationDialog = null;
                                          if (activity instanceof PagerActivity){
                                             ((PagerActivity)activity).switchToTranslation();
                                          }
                                       }
                                    }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                       @Override
                       public void onCancel(DialogInterface dialogInterface) {
                          mTranslationDialog = null;
                       }
                    });
            mTranslationDialog = builder.create();
            mTranslationDialog.show();
         }
         else if (activity != null){
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setMessage(R.string.need_translation)
                    .setPositiveButton(R.string.get_translations,
                            new DialogInterface.OnClickListener() {
                               @Override
                               public void onClick(DialogInterface dialog,
                                                   int i) {
                                  dialog.dismiss();
                                  mTranslationDialog = null;
                                  Intent tm = new Intent(getActivity(),
                                          TranslationManagerActivity.class);
                                  activity.startActivity(tm);
                               }
                            })
                    .setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                               @Override
                               public void onClick(DialogInterface dialog,
                                                   int i) {
                                  dialog.dismiss();
                                  mTranslationDialog = null;
                               }
                            });
            mTranslationDialog = builder.create();
            mTranslationDialog.show();
            mCurrentTask = null;
         }
      }
   }
}
