package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.PaintDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.*;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnTouchListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.TranslationManagerActivity;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.TranslationUtils;
import com.quran.labs.androidquran.widgets.HighlightingImageView;

import java.util.List;

@SuppressWarnings("deprecation")
public class QuranPageFragment extends SherlockFragment {
   private static final String TAG = "QuranPageFragment";
   private static final String PAGE_NUMBER_EXTRA = "pageNumber";

   private int mPageNumber;
   private HighlightingImageView mImageView;
   private ScrollView mScrollView;
   private PaintDrawable mLeftGradient, mRightGradient = null;

   private AlertDialog mTranslationDialog = null;

   public static QuranPageFragment newInstance(int page){
      final QuranPageFragment f = new QuranPageFragment();
      final Bundle args = new Bundle();
      args.putInt(PAGE_NUMBER_EXTRA, page);
      f.setArguments(args);
      return f;
   }

   @Override
   public void onCreate(Bundle savedInstanceState){
      super.onCreate(savedInstanceState);
      mPageNumber = getArguments() != null?
              getArguments().getInt(PAGE_NUMBER_EXTRA) : -1;
      int width = getActivity().getWindowManager()
            .getDefaultDisplay().getWidth();
      mLeftGradient = QuranDisplayHelper.getPaintDrawable(width, 0);
      mRightGradient = QuranDisplayHelper.getPaintDrawable(0, width);
      setHasOptionsMenu(true);
   }

   @Override
   public View onCreateView(LayoutInflater inflater,
                            ViewGroup container, Bundle savedInstanceState){
      final View view = inflater.inflate(R.layout.quran_page_layout,
              container, false);
      view.setBackgroundDrawable((mPageNumber % 2 == 0?
              mLeftGradient : mRightGradient));
      int lineImageId = R.drawable.dark_line;
      int leftBorderImageId = R.drawable.border_left;
      int rightBorderImageId = R.drawable.border_right;
      
      SharedPreferences prefs =
              PreferenceManager.getDefaultSharedPreferences(getActivity());

      Resources res = getResources();
      if (!prefs.getBoolean(Constants.PREF_USE_NEW_BACKGROUND, true)) {
    	  view.setBackgroundColor(res.getColor(R.color.page_background));
      }

      if (prefs.getBoolean(Constants.PREF_NIGHT_MODE, false)){
         leftBorderImageId = R.drawable.night_left_border;
         rightBorderImageId = R.drawable.night_right_border;
         lineImageId = R.drawable.light_line;
         view.setBackgroundColor(Color.BLACK);
      }

      ImageView leftBorder = (ImageView)view.findViewById(R.id.left_border);
      ImageView rightBorder = (ImageView)view.findViewById(R.id.right_border);
      if (mPageNumber % 2 == 0){
         rightBorder.setVisibility(View.GONE);
         leftBorder.setBackgroundResource(leftBorderImageId);
      }
      else {
         rightBorder.setVisibility(View.VISIBLE);
         rightBorder.setBackgroundResource(rightBorderImageId);
         leftBorder.setBackgroundResource(lineImageId);
      }

      mImageView = (HighlightingImageView)view.findViewById(R.id.page_image);
      mScrollView = (ScrollView)view.findViewById(R.id.page_scroller);
      
      final GestureDetector gestureDetector = new GestureDetector(
            new PageGestureDetector());
      OnTouchListener gestureListener = new OnTouchListener() {
         @Override
         public boolean onTouch(View v, MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
         }
      };
      mImageView.setOnTouchListener(gestureListener);
      mImageView.setClickable(true);
      mImageView.setLongClickable(true);
      if (prefs.getBoolean(Constants.PREF_OVERLAY_PAGE_INFO, true)) {
         try { mImageView.setOverlayText(mPageNumber, true); }
         catch (Exception e){ }
      }
      return view;
   }

   @Override
   public void onActivityCreated(Bundle savedInstanceState){
      super.onActivityCreated(savedInstanceState);
      if (PagerActivity.class.isInstance(getActivity())){
         QuranPageWorker worker = ((PagerActivity)getActivity()).getQuranPageWorker();
         worker.loadPage(mPageNumber, mImageView);
      }
   }

   @Override
   public void onPause(){
      if (mTranslationDialog != null){
         mTranslationDialog.dismiss();
         mTranslationDialog = null;
      }
      super.onPause();
   }

   public void cleanup(){
      android.util.Log.d(TAG, "cleaning up page " + mPageNumber);
      mImageView.setImageDrawable(null);
      mImageView = null;
      if (mTranslationDialog != null){
         mTranslationDialog.dismiss();
         mTranslationDialog = null;
      }
   }

   public void highlightAyah(int sura, int ayah){
      mImageView.highlightAyah(sura, ayah);
      if (mScrollView != null){
         AyahBounds yBounds = mImageView.getYBoundsForCurrentHighlight();
         if (yBounds != null){
            int screenHeight = QuranScreenInfo.getInstance().getHeight();
            int y = yBounds.getMinY() - (int)(0.05 * screenHeight);
            mScrollView.smoothScrollTo(mScrollView.getScrollX(), y);
         }
      }
      mImageView.invalidate();
   }

   public void unhighlightAyah(){
      mImageView.unhighlight();
   }

   private class PageGestureDetector extends SimpleOnGestureListener {
      @Override
      public boolean onSingleTapConfirmed(MotionEvent event) {
         PagerActivity pagerActivity = ((PagerActivity)getActivity());
         if (pagerActivity != null){
            pagerActivity.toggleActionBar();
            return true;
         }
         else { return false; }
      }

      @Override
      public boolean onDoubleTap(MotionEvent event) {
         unhighlightAyah();
         return true;
      }

      @Override
      public void onLongPress(MotionEvent event) {
         if (!QuranFileUtils.haveAyaPositionFile() ||
             !QuranFileUtils.hasArabicSearchDatabase()){
            Activity activity = getActivity();
            if (activity != null){
               PagerActivity pagerActivity = (PagerActivity)activity;
               pagerActivity.showGetRequiredFilesDialog();
               return;
            }
         }

         QuranAyah result = getAyahFromCoordinates(event.getX(), event.getY());
         if (result != null) {
            mImageView.highlightAyah(result.getSura(), result.getAyah());
            mImageView.invalidate();
            mImageView.performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS);
            
            // TODO Temporary UI until new UI is implemented
            new ShowAyahMenuTask().execute(
                    result.getSura(), result.getAyah(), mPageNumber);
         }
      }

      private QuranAyah getAyahFromCoordinates(float x, float y) {
         float[] pageXY = mImageView.getPageXY(x, y);
         QuranAyah result = null;
         if (pageXY != null) {
            String filename = QuranFileUtils.getAyaPositionFileName();
            try {
               AyahInfoDatabaseHandler handler =
                       new AyahInfoDatabaseHandler(filename);
               result = handler.getVerseAtPoint(mPageNumber,
                       pageXY[0], pageXY[1]);
               handler.closeDatabase();
            } catch (Exception e) {
               Log.e(TAG, e.getMessage(), e);
            }
         }
         return result;
      }
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
         BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
         dba.open();
         boolean bookmarked = dba.getBookmarkId(mSura, mAyah, mPage) >= 0;
         dba.close();
         return bookmarked;
      }
      
      @Override
      protected void onPostExecute(Boolean result) {
         showAyahMenu(mSura, mAyah, mPage, result);
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
                 R.string.copy_ayah, R.string.play_from_here
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
					} else if (selection == 1) {
                  if (activity != null && activity instanceof PagerActivity){
                     PagerActivity pagerActivity = (PagerActivity) activity;
                     FragmentManager fm =
                             pagerActivity.getSupportFragmentManager();
                     TagBookmarkDialog tagBookmarkDialog =
                             new TagBookmarkDialog(sura, ayah, page);
                     tagBookmarkDialog.show(fm, TagBookmarkDialog.TAG);
                  }
					} else if (selection == 2) {
					   new ShowTafsirTask(sura, ayah).execute();
					} else if (selection == 3) {
						new ShareAyahTask(sura, ayah, false).execute();
					} else if (selection == 4) {
						new ShareAyahTask(sura, ayah, true).execute();
					} else if (selection == 5) {
						if (activity instanceof PagerActivity) {
							PagerActivity pagerActivity = (PagerActivity) activity;
							pagerActivity.playFromAyah(mPageNumber, sura, ayah);
						}
               } else if (selection == 5) {
                  new ShowNotesTask(sura, ayah).execute();
					}
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
                    new DatabaseHandler(
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
                    ClipboardManager cm = (ClipboardManager) activity.
                            getSystemService(Activity.CLIPBOARD_SERVICE);
                    cm.setText(ayah);
                    Toast.makeText(activity, activity.getString(
                            R.string.ayah_copied_popup),
                            Toast.LENGTH_SHORT).show();
                } else {
                    final Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, ayah);
                    startActivity(Intent.createChooser(intent,
                            activity.getString(R.string.share_ayah)));
                }
            }
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
               DatabaseHandler tafsirHandler = new DatabaseHandler(db);
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
				   .setPositiveButton(getString(R.string.dialog_ok),
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
                                  startActivity(tm);
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
         }
		}
	}
	
   class ShowNotesTask extends AsyncTask<Void, Void, String> {
      private int ayahId;
      
      public ShowNotesTask(int sura, int ayah) {
         this.ayahId = QuranInfo.getAyahId(sura, ayah);
      }
      
      @Override
      protected String doInBackground(Void... params) {
         String result;
         BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
         dba.open();
         // TODO figure this out
         // result = dba.getAyahNote(ayahId);
         dba.close();
         // return result;
         return "";
      }
      
      @Override
      protected void onPostExecute(String result) {
         final Dialog dlg = new Dialog(getActivity());
         dlg.setTitle(getString(R.string.ayah_notes));
         dlg.setContentView(R.layout.notes_dialog);
         final EditText noteView = (EditText) dlg.findViewById(R.id.ayah_note);
         noteView.setText(result);
         dlg.setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {
               final String note = noteView.getText().toString();
               new SaveNotesTask(ayahId, note).execute();
            }
         });
         int background = getResources().getColor(
                 R.color.transparent_dialog_color);
         dlg.getWindow().setBackgroundDrawable(new ColorDrawable(background));
         dlg.show();
      }
   }
   
   class SaveNotesTask extends AsyncTask<Void, Void, Void> {
      private int ayahId;
      private String note;

      public SaveNotesTask(int ayahId, String note) {
         this.ayahId = ayahId;
         this.note = note;
      }
      
      @Override
      protected Void doInBackground(Void... params) {
         BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
         dba.open();
         // TODO figure this out
         if (note != null && !note.trim().equals("")) {
            //dba.saveAyahNote(ayahId, note);
         } else {
            //dba.deleteAyahNote(ayahId);
         }
         dba.close();
         return null;
      }
   }
   
}
