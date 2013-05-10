package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.PaintDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.*;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.TranslationManagerActivity;
import com.quran.labs.androidquran.ui.helpers.*;
import com.quran.labs.androidquran.util.*;
import com.quran.labs.androidquran.widgets.HighlightingImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("deprecation")
public class QuranPageFragment extends SherlockFragment
   implements AyahTracker {

   private static final String TAG = "QuranPageFragment";
   private static final String PAGE_NUMBER_EXTRA = "pageNumber";

   private int mPageNumber;
   private HighlightingImageView mImageView;
   private ScrollView mScrollView;
   private PaintDrawable mLeftGradient, mRightGradient = null;

   private AlertDialog mTranslationDialog = null;
   private ProgressDialog mProgressDialog;
   private AsyncTask mCurrentTask;

   private boolean mOverlayText;
   private Map<String, List<AyahBounds>> mCoordinateData;

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

      boolean nightMode = false;
      int nightModeTextBrightness = Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
      if (prefs.getBoolean(Constants.PREF_NIGHT_MODE, false)){
         leftBorderImageId = R.drawable.night_left_border;
         rightBorderImageId = R.drawable.night_right_border;
         lineImageId = R.drawable.light_line;
         view.setBackgroundColor(Color.BLACK);
         nightMode = true;
         nightModeTextBrightness = QuranSettings.getNightModeTextBrightness(getActivity());
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
      mImageView.setNightMode(nightMode);
      if (nightMode) {
         mImageView.setNightModeTextBrightness(nightModeTextBrightness);
      }

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

      mOverlayText = prefs.getBoolean(Constants.PREF_OVERLAY_PAGE_INFO, true);

      if (mCoordinateData != null){
         mImageView.setCoordinateData(mCoordinateData);
      }

      return view;
   }

   @Override
   public void onActivityCreated(Bundle savedInstanceState){
      super.onActivityCreated(savedInstanceState);
      Activity activity = getActivity();
      if (PagerActivity.class.isInstance(activity)){
         QuranPageWorker worker =
                 ((PagerActivity)activity).getQuranPageWorker();
         worker.loadPage(mPageNumber, mImageView);

         new QueryPageCoordinatesTask(activity).execute(mPageNumber);
      }
   }

   @Override
   public void onDestroyView() {
      if (mProgressDialog != null){
         mProgressDialog.hide();
         mProgressDialog = null;
      }

      if (mCurrentTask != null){ mCurrentTask.cancel(true); }
      mCurrentTask = null;
      super.onDestroyView();
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
      if (mImageView != null){
         mImageView.setImageDrawable(null);
         mImageView = null;
      }

      if (mTranslationDialog != null){
         mTranslationDialog.dismiss();
         mTranslationDialog = null;
      }
   }

   private class QueryPageCoordinatesTask extends QueryPageCoordsTask {
      public QueryPageCoordinatesTask(Context context){
         super(context);
      }

      @Override
      protected void onPostExecute(Rect[] rect) {
         if (rect != null && rect.length == 1){
            if (mImageView != null){
               mImageView.setPageBounds(rect[0]);
               if (mOverlayText){
                  mImageView.setOverlayText(mPageNumber, true);
               }
            }
         }
      }
   }

   private class GetAyahCoordsTask extends QueryAyahCoordsTask {

      public GetAyahCoordsTask(Context context, MotionEvent event){
         super(context, event);
      }

      public GetAyahCoordsTask(Context context, int sura, int ayah){
         super(context, sura, ayah);
      }

      @Override
      protected void onPostExecute(List<Map<String, List<AyahBounds>>> maps){
         if (maps != null && maps.size() > 0){
            mCoordinateData = maps.get(0);

            if (mImageView != null){
               mImageView.setCoordinateData(mCoordinateData);
            }
         }

         if (mHighlightAyah){
            handleHighlightAyah(mSura,  mAyah);
         }
         else { handleLongPress(mEvent); }
         mCurrentTask = null;
      }
   }

   @Override
   public void highlightAyah(int sura, int ayah){
      if (mCoordinateData == null){
         if (mCurrentTask != null &&
                 !(mCurrentTask instanceof QueryAyahCoordsTask)){
            mCurrentTask.cancel(true);
            mCurrentTask = null;
         }

         if (mCurrentTask == null){
            mCurrentTask = new GetAyahCoordsTask(
                    getActivity(), sura, ayah).execute(mPageNumber);
         }
      }
      else { handleHighlightAyah(sura, ayah); }
   }

   private void handleHighlightAyah(int sura, int ayah){
      if (mImageView == null){ return; }
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

   @Override
   public void unHighlightAyat(){
      mImageView.unhighlight();
   }

   private void handleLongPress(MotionEvent event){
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

   private QuranAyah getAyahFromKey(String key){
      String[] parts = key.split(":");
      QuranAyah result = null;
      if (parts.length == 2){
         try {
            int sura = Integer.parseInt(parts[0]);
            int ayah = Integer.parseInt(parts[1]);
            result = new QuranAyah(sura, ayah);
         }
         catch (Exception e){}
      }
      return result;
   }

   private QuranAyah getAyahFromCoordinates(float xc, float yc) {
      if (mCoordinateData == null){ return null; }

      float[] pageXY = mImageView.getPageXY(xc, yc);
      float x = pageXY[0];
      float y = pageXY[1];

      int closestLine = -1;
      int closestDelta = -1;

      SparseArray<List<String>> lineAyahs = new SparseArray<List<String>>();
      Set<String> keys = mCoordinateData.keySet();
      for (String key : keys){
         List<AyahBounds> bounds = mCoordinateData.get(key);
         if (bounds == null){ continue; }

         for (AyahBounds b : bounds){
            // only one AyahBound will exist for an ayah on a particular line
            int line = b.getLine();
            List<String> items = lineAyahs.get(line);
            if (items == null){
               items = new ArrayList<String>();
            }
            items.add(key);
            lineAyahs.put(line, items);

            if (b.getMaxX() >= x && b.getMinX() <= x &&
                b.getMaxY() >= y && b.getMinY() <= y){
               return getAyahFromKey(key);
            }

            int delta = Math.min((int)Math.abs(b.getMaxY() - y),
                                 (int)Math.abs(b.getMinY() - y));
            if (closestDelta == -1 || delta < closestDelta){
               closestLine = b.getLine();
               closestDelta = delta;
            }
         }
      }

      if (closestLine > -1){
         int leastDeltaX = -1;
         String closestAyah = null;
         List<String> ayat = lineAyahs.get(closestLine);
         if (ayat != null){
            Log.d(TAG, "no exact match, " + ayat.size() + " candidates.");
            for (String ayah : ayat){
               List<AyahBounds> bounds = mCoordinateData.get(ayah);
               if (bounds == null){ continue; }
               for (AyahBounds b : bounds){
                  if (b.getLine() > closestLine){
                     // this is the last ayah in ayat list
                     break;
                  }

                  if (b.getLine() == closestLine){
                     // if x is within the x of this ayah, that's our answer
                     if (b.getMaxX() >= x && b.getMinX() <= x){
                        return getAyahFromKey(ayah);
                     }

                     // otherwise, keep track of the least delta and return it
                     int delta = Math.min((int)Math.abs(b.getMaxX() - x),
                                          (int)Math.abs(b.getMinX() - x));
                     if (leastDeltaX == -1 || delta < leastDeltaX){
                        closestAyah = ayah;
                        leastDeltaX = delta;
                     }
                  }
               }
            }
         }

         if (closestAyah != null){
            Log.d(TAG, "fell back to closest ayah of " + closestAyah);
            return getAyahFromKey(closestAyah);
         }
      }
      return null;
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
         unHighlightAyat();
         return true;
      }

      @Override
      public void onLongPress(MotionEvent event) {
         if (!QuranFileUtils.haveAyaPositionFile(getActivity()) ||
             !QuranFileUtils.hasArabicSearchDatabase(getActivity())){
            Activity activity = getActivity();
            if (activity != null){
               PagerActivity pagerActivity = (PagerActivity)activity;
               pagerActivity.showGetRequiredFilesDialog();
               return;
            }
         }

         if (mCoordinateData == null){
            mCurrentTask = new GetAyahCoordsTask(getActivity(),
                    event).execute(mPageNumber);
         }
         else { handleLongPress(event); }
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
							pagerActivity.playFromAyah(mPageNumber, sura, ayah);
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
            startActivity(Intent.createChooser(intent,
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
            mCurrentTask = null;
         }
		}
	}

   /*
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
   */
   
}
