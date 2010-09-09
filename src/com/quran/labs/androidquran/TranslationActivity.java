package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.text.Html;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.DatabaseHandler;
import com.quran.labs.androidquran.util.QuranUtils;

public class TranslationActivity extends Activity {

	private int page = 1;
    private AsyncTask<?, ?, ?> currentTask;
    private ProgressDialog pd = null;
    private GestureDetector gestureDetector;
    
	private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.quran_translation);
		loadPageState(savedInstanceState);
		gestureDetector = new GestureDetector(new QuranGestureDetector());
		renderTranslation();
	}
	
	// thanks to codeshogun's blog post for this
	// http://www.codeshogun.com/blog/2009/04/16/how-to-implement-swipe-action-in-android/
	class QuranGestureDetector extends SimpleOnGestureListener {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
			if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
				return false;
			// previous page swipe
			if ((e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE) && 
			    (Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY)){
				if (page > 1){
					page--;
					renderTranslation();
				}
			}
			else if ((e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE) &&
				(Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY)){
				if (page < 604){
					page++;
					renderTranslation();
				}
			}
			
			return false;
		}
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event){
		return gestureDetector.onTouchEvent(event);
	}
	
	// this function lets this activity handle the touch event before the ScrollView
	@Override
	public boolean dispatchTouchEvent(MotionEvent event){
		super.dispatchTouchEvent(event);
		return gestureDetector.onTouchEvent(event);
	}
	
	public void loadPageState(Bundle savedInstanceState){
		page = savedInstanceState != null ? savedInstanceState.getInt("page") : ApplicationConstants.PAGES_FIRST;
		if (page == ApplicationConstants.PAGES_FIRST){
			Bundle extras = getIntent().getExtras();
			page = extras != null? extras.getInt("page") : ApplicationConstants.PAGES_FIRST;
		}
		return;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT){
			if (page < 604){
				page++;
				renderTranslation();
			}
		}
		else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT){
			if (page > 1){
				page--;
				renderTranslation();
			}
		}
		else if (keyCode == KeyEvent.KEYCODE_BACK){
			goBack();
		}

		return super.onKeyDown(keyCode, event);
	}

	public void renderTranslation(){
		if ((page > 604) || (page < 1)) page = 1;
		setTitle(QuranInfo.getPageTitle(page));

		Integer[] bounds = QuranInfo.getPageBounds(page);
		
		String[] translationLists = new String[]{ "en_si" };
		List<String> unavailable = new ArrayList<String>();
		
		int available = 0;
		List<Map<String, String>> translations = new ArrayList<Map<String, String>>();
		for (String tl : translationLists){
			Map<String, String> currentTranslation = getVerses(tl, bounds);
			if (currentTranslation != null){
				translations.add(currentTranslation);
				available++;
			}
			else {
				unavailable.add(tl);
				translations.add(null);
			}
		}
		
		TextView translationArea = (TextView)findViewById(R.id.translationText);
		translationArea.setText("");
		
		if (available == 0){
			promptForTranslationDownload(unavailable);
			translationArea.setText(R.string.translationsNeeded);
			return;
		}
		
		
		int numTranslations = translationLists.length;
		
		int i = bounds[0];
		for (; i <= bounds[2]; i++){
			int j = (i == bounds[0])? bounds[1] : 1;
			
			for (;;){
				int numAdded = 0;
				String key = i + ":" + j++;
				for (int t = 0; t < numTranslations; t++){
					if (translations.get(t) == null) continue;
					String text = translations.get(t).get(key);
					if (text != null){
						numAdded++;
						String str = "<b>" + key + ":</b> " + text + "<br>";
						translationArea.append(Html.fromHtml(str));
					}
				}
				if (numAdded == 0) break;
			}
		}
	}
	
	public Map<String, String> getVerses(String translation, Integer[] bounds){
		DatabaseHandler handler = null;
		try {
			Map<String, String> ayahs = new HashMap<String, String>();
			handler = new DatabaseHandler(translation);
			for (int i = bounds[0]; i <= bounds[2]; i++){
				int max = (i == bounds[2])? bounds[3] : QuranInfo.getNumAyahs(i);
				int min = (i == bounds[0])? bounds[1] : 1;
				Cursor res = handler.getVerses(i, min, max);
				if ((res == null) || (!res.moveToFirst())) continue;
				do {
					int sura = res.getInt(0);
					int ayah = res.getInt(1);
					String text = res.getString(2);
					ayahs.put(sura + ":" + ayah, text);
				}
				while (res.moveToNext());
			}
			handler.closeDatabase();
			return ayahs;
		}
		catch (SQLException ex){
			ex.printStackTrace();
			if (handler != null) handler.closeDatabase();
			return null;
		}
	}
	
	public void goBack(){
		Intent i = new Intent();
		i.putExtra("page", page);
		setResult(RESULT_OK, i);
		finish();	
	}
	
	public void startDownload(List<String> whatToGet){
		pd = ProgressDialog.show(this, "Downloading..", "Please Wait...", true, true,
				new OnCancelListener(){

					@Override
					public void onCancel(DialogInterface dialog) {
						cancelDownload();
					}
			
		});
		currentTask = new DownloadTranslationsTask().execute(whatToGet.toArray());
	}
	
	public void cancelDownload(){
		pd.dismiss();
		currentTask.cancel(true);
		goBack();
	}
	
	public void doneDownloading(Integer downloaded){
		pd.dismiss();
		if (downloaded > 0) renderTranslation();
		else goBack();
	}
	
	private class DownloadTranslationsTask extends AsyncTask<Object[], Void, Integer> {
    	public Integer doInBackground(Object[]... params){
    		Integer numDownloads = 0;
    		
    		Object[] translations = (Object[]) params[0];
    		for (Object dbName : translations){
    			String tlFile = "quran." + (String)dbName + ".db";
    			if (QuranUtils.getTranslation(tlFile))
    				numDownloads++;
    		}
    		return numDownloads;
    	}
    	    	
    	@Override
    	public void onPostExecute(Integer downloaded){
    		currentTask = null;
    		doneDownloading(downloaded);
    	}
    }
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
		if ((currentTask != null) && (currentTask.getStatus() == Status.RUNNING))
			currentTask.cancel(true);
	}
	
	public void promptForTranslationDownload(final List<String> translationsToGet){
    	AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    	dialog.setMessage(R.string.downloadTranslationPrompt);
    	dialog.setCancelable(false);
    	dialog.setPositiveButton(R.string.downloadPrompt_ok,
			new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
					startDownload(translationsToGet);
				}
    	});
    	
    	dialog.setNegativeButton(R.string.downloadPrompt_no, 
    			new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int id) {
    					dialog.cancel();
    					goBack();
    				}
    	});
    	
    	AlertDialog alert = dialog.create();
    	alert.setTitle(R.string.downloadPrompt_title);
    	alert.show();
	}
}
