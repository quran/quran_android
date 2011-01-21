package com.quran.labs.androidquran;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

import com.quran.labs.androidquran.common.BaseQuranActivity;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.BookmarksManager;

public class BookmarksActivity extends BaseQuranActivity {

	private static final int CONTEXT_MENU_REMOVE = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		setContentView(R.layout.bookmarks_list);
		BookmarksManager.load(prefs);
		showBookmarks();
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
		
		ListView list = (ListView)findViewById(R.id.lstBookmarks);

		SimpleAdapter suraAdapter = new SimpleAdapter(this, bookmarkList, R.layout.quran_row, from, to);
		list.setAdapter(suraAdapter);
		list.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				int page = BookmarksManager.getInstance().getBookmarks().get((int)id);
				Intent data = new Intent();
				data.putExtra("page", page);
				setResult(RESULT_OK, data);
				finish();
			}
		});
		
		list.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
			
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
				menu.add(0, CONTEXT_MENU_REMOVE, 0, R.string.menu_bookmarks_remove);
			}
		});

		setTitle(getString(R.string.menu_bookmarks) + " (" + bookmarks.size() + ")");
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) { 
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		if (item.getItemId() == CONTEXT_MENU_REMOVE) {
			BookmarksManager.getInstance().removeAt(info.position, prefs);
			showBookmarks();
		}
		return true;
	}

}
