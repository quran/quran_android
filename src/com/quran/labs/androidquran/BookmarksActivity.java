package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.BookmarksManager;

public class BookmarksActivity extends ListActivity {

	private static final int CONTEXT_MENU_REMOVE = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		BookmarksManager.load(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
		
		setContentView(R.layout.bookmarks_list);
		showBookmarks();
		registerForContextMenu(getListView());
	}

	private void showBookmarks() {
		ArrayList< Map<String, String> > bookmarkList = new ArrayList< Map<String, String> >();
		
		ArrayList<Integer> bookmarks = BookmarksManager.getInstance().getBookmarks();
		for (int i = 0; i < bookmarks.size(); i++) {
			int page = bookmarks.get(i);
			String title = (i+1) + ". " + QuranInfo.getPageTitleNoPrefix(page);
			Map<String, String> map = new HashMap<String, String>();
			map.put("suraname", title);
			bookmarkList.add(map);
		}
		
		String[] from = new String[]{ "suraname" };
		int[] to = new int[]{ R.id.sura_title };

		SimpleAdapter suraAdapter = new SimpleAdapter(this, bookmarkList, R.layout.quran_row, from, to);
		setListAdapter(suraAdapter);
		
		setTitle(getString(R.string.menu_bookmarks) + " (" + bookmarks.size() + ")");
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		int page = BookmarksManager.getInstance().getBookmarks().get((int)id);
		Intent data = new Intent();
		data.putExtra("page", page);
		setResult(RESULT_OK, data);
		finish();
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) { 
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		if (item.getItemId() == CONTEXT_MENU_REMOVE) {
			BookmarksManager.getInstance().removeAt(info.position,
					getSharedPreferences(ApplicationConstants.PREFERNCES, 0));
			showBookmarks();
		}
		return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		menu.add(0, CONTEXT_MENU_REMOVE, 0, R.string.menu_bookmarks_remove);
	}

}
