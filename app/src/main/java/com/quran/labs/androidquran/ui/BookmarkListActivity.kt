package com.quran.labs.androidquran.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.dao.bookmark.BookmarkListMode
import com.quran.labs.androidquran.dao.bookmark.BookmarkListRowData
import com.quran.labs.androidquran.presenter.bookmark.BookmarkListPresenter
import com.quran.labs.androidquran.presenter.bookmark.BookmarkPresenter
import com.quran.labs.androidquran.ui.fragment.AddTagDialog
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog.OnBookmarkTagsUpdateListener
import com.quran.labs.androidquran.common.ui.core.HighlightColors
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.labs.androidquran.ui.helpers.QuranRowFactory
import com.quran.labs.androidquran.util.QuranSettings
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class BookmarkListActivity : AppCompatActivity(),
  QuranListAdapter.QuranTouchListener,
  OnBookmarkTagsUpdateListener {

  private lateinit var mode: BookmarkListMode
  private var adapter: QuranListAdapter? = null
  private var emptyStateView: TextView? = null
  private var recyclerView: RecyclerView? = null
  private var actionMode: ActionMode? = null
  private var rowData: List<BookmarkListRowData> = emptyList()
  private var collectionName: String? = null
  private var isPaused = false
  private val sortOrder = MutableStateFlow(BookmarkSortOrder.SORT_DATE_ADDED)

  @Inject
  lateinit var bookmarkListPresenter: BookmarkListPresenter

  @Inject
  lateinit var quranRowFactory: QuranRowFactory

  @Inject
  lateinit var quranSettings: QuranSettings

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)

    val mode = modeFromIntent(intent)
    if (mode == null) {
      finish()
      return
    }
    this.mode = mode

    setContentView(R.layout.bookmark_list)

    val root = findViewById<ViewGroup>(R.id.root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = insets.top
        leftMargin = insets.left
        rightMargin = insets.right
      }
      windowInsets
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
    recyclerView.layoutManager = LinearLayoutManager(this)
    recyclerView.itemAnimator = DefaultItemAnimator()
    ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      view.setPadding(0, 0, 0, insets.bottom)
      windowInsets
    }
    this.recyclerView = recyclerView

    val adapter = QuranListAdapter(this, recyclerView, emptyArray(), true)
    adapter.setQuranTouchListener(this)
    recyclerView.adapter = adapter
    this.adapter = adapter

    val emptyStateView = findViewById<TextView>(R.id.empty_state)
    emptyStateView.setText(
      when (mode) {
        is BookmarkListMode.Collection -> R.string.collection_empty
        is BookmarkListMode.Highlights -> R.string.highlights_empty
      }
    )
    this.emptyStateView = emptyStateView

    sortOrder.value = quranSettings.bookmarksSortOrder
    updateTitle()
    subscribeToData()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun subscribeToData() {
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        sortOrder
          .flatMapLatest { order -> bookmarkListPresenter.rows(mode, order) }
          .collect { rows -> onNewRowData(rows) }
      }
    }

    val mode = mode
    if (mode is BookmarkListMode.Collection) {
      lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
          bookmarkListPresenter.collectionName(mode.collectionId).collect { name ->
            // the collection can be renamed from this screen, or deleted from elsewhere
            if (name == null) {
              finish()
            } else {
              collectionName = name
              updateTitle()
            }
          }
        }
      }
    }
  }

  private fun updateTitle() {
    supportActionBar?.title = when (val mode = mode) {
      is BookmarkListMode.Collection ->
        collectionName ?: intent.getStringExtra(EXTRA_COLLECTION_NAME).orEmpty()

      is BookmarkListMode.Highlights -> getString(HighlightColors[mode.color].nameResourceId)
    }
  }

  private fun onNewRowData(rows: List<BookmarkListRowData>) {
    rowData = rows

    val quranRows = rows.map { row ->
      when (row) {
        is BookmarkListRowData.SuraHeader -> quranRowFactory.fromSuraHeader(this, row.sura)
        is BookmarkListRowData.BookmarkItem ->
          quranRowFactory.fromBookmark(this, row.bookmark, row.collectionId)

        is BookmarkListRowData.HighlightItem ->
          quranRowFactory.fromHighlight(this, row.highlight, row.ayahText)
      }
    }

    if (actionMode != null) {
      finishActionMode()
    }
    adapter?.setElements(quranRows.toTypedArray())
    emptyStateView?.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
  }

  override fun onStop() {
    isPaused = true
    // the undo window does not follow the user off the screen
    bookmarkListPresenter.flushPendingRemovals()
    super.onStop()
  }

  override fun onStart() {
    super.onStart()
    isPaused = false
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.bookmark_list_menu, menu)
    menu.findItem(R.id.rename_collection).isVisible = mode is BookmarkListMode.Collection

    if (sortOrder.value == BookmarkSortOrder.SORT_DATE_ADDED) {
      menu.findItem(R.id.sort_date).isChecked = true
    } else {
      menu.findItem(R.id.sort_location).isChecked = true
    }
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> {
        finish()
        return true
      }

      R.id.sort_date -> {
        setSortOrder(BookmarkSortOrder.SORT_DATE_ADDED)
        item.isChecked = true
        return true
      }

      R.id.sort_location -> {
        setSortOrder(BookmarkSortOrder.SORT_LOCATION)
        item.isChecked = true
        return true
      }

      R.id.rename_collection -> {
        val mode = mode
        if (mode is BookmarkListMode.Collection && !isPaused) {
          AddTagDialog.newInstance(mode.collectionId, collectionName.orEmpty())
            .show(supportFragmentManager, AddTagDialog.TAG)
        }
        return true
      }
    }
    return super.onOptionsItemSelected(item)
  }

  private fun setSortOrder(order: Int) {
    if (sortOrder.value == order) {
      return
    }
    // shared with the bookmarks tab, so the two screens agree on how the list is sorted
    quranSettings.bookmarksSortOrder = order
    sortOrder.value = order
  }

  override fun onClick(row: QuranRow, position: Int) {
    val adapter = adapter ?: return
    if (actionMode != null) {
      if (isValidSelection(row)) {
        adapter.setItemChecked(position, !adapter.isItemChecked(position))
        if (adapter.getCheckedItems().isEmpty()) {
          finishActionMode()
        } else {
          actionMode?.invalidate()
        }
      }
      return
    }

    adapter.setItemChecked(position, false)
    if (!row.isHeader) {
      jumpToAndHighlight(row.page, row.sura, row.ayah)
    }
  }

  override fun onLongClick(row: QuranRow, position: Int): Boolean {
    val adapter = adapter
    return if (adapter == null || !isValidSelection(row)) {
      false
    } else {

      adapter.setItemChecked(position, !adapter.isItemChecked(position))
      if (actionMode != null && adapter.getCheckedItems().isEmpty()) {
        finishActionMode()
      } else if (actionMode == null) {
        actionMode = startSupportActionMode(ModeCallback())
      } else {
        actionMode?.invalidate()
      }
      true
    }
  }

  private fun isValidSelection(row: QuranRow): Boolean =
    row.isBookmark || row.isHighlightedAyah

  private fun jumpToAndHighlight(page: Int, sura: Int, ayah: Int) {
    val intent = Intent(this, PagerActivity::class.java)
    intent.putExtra("page", page)
    intent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura)
    intent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah)
    intent.putExtra(PagerActivity.EXTRA_JUMP_TO_TRANSLATION, quranSettings.wasShowingTranslation)
    startActivity(intent)
  }

  private fun finishActionMode() {
    actionMode?.finish()
  }

  private fun removeSelectedRows() {
    val adapter = adapter ?: return
    val recyclerView = recyclerView ?: return
    val selected = adapter.getCheckedItems()

    val toRemove = selected.mapNotNull { row -> rowDataFor(row) }
    if (toRemove.isNotEmpty()) {
      bookmarkListPresenter.removeAfterSomeTime(
        toRemove,
        BookmarkPresenter.DELAY_DELETION_DURATION_IN_MS.toLong()
      )

      val size = toRemove.size
      val message = when (mode) {
        is BookmarkListMode.Collection ->
          resources.getQuantityString(R.plurals.bookmark_tag_deleted, size, size)

        is BookmarkListMode.Highlights ->
          resources.getQuantityString(R.plurals.removed_highlights, size, size)
      }

      val snackbar = Snackbar.make(
        recyclerView, message, BookmarkPresenter.DELAY_DELETION_DURATION_IN_MS
      )
      snackbar.setAction(R.string.undo) { bookmarkListPresenter.cancelRemoval() }
      snackbar.setTextColor(resources.getColor(R.color.default_text))
      snackbar.view.setBackgroundColor(resources.getColor(R.color.snackbar_background_color))
      snackbar.show()
    }
  }

  private fun rowDataFor(row: QuranRow): BookmarkListRowData? {
    return rowData.firstOrNull { data ->
      when (data) {
        is BookmarkListRowData.BookmarkItem -> data.bookmark.id == row.bookmarkId
        is BookmarkListRowData.HighlightItem ->
          data.highlight.suraAyah.sura == row.sura && data.highlight.suraAyah.ayah == row.ayah

        is BookmarkListRowData.SuraHeader -> false
      }
    }
  }

  private fun tagSelectedBookmarks() {
    val adapter = adapter ?: return
    val ids = adapter.getCheckedItems().mapNotNull { row -> row.bookmarkId }.toTypedArray()
    if (ids.isEmpty() || isPaused) {
      return
    }
    TagBookmarkDialog.newInstance(ids).show(supportFragmentManager, TagBookmarkDialog.TAG)
  }

  override fun onAddTagSelected() {
    if (!isPaused) {
      AddTagDialog().show(supportFragmentManager, AddTagDialog.TAG)
    }
  }

  private inner class ModeCallback : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
      menuInflater.inflate(R.menu.bookmark_list_contextual_menu, menu)
      return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
      // tagging only makes sense for bookmarks; a highlight is not in a collection
      menu.findItem(R.id.cab_tag_bookmark).isVisible =
        this@BookmarkListActivity.mode is BookmarkListMode.Collection
      menu.findItem(R.id.cab_delete).setTitle(
        when (this@BookmarkListActivity.mode) {
          is BookmarkListMode.Collection -> R.string.delete_tag
          is BookmarkListMode.Highlights -> R.string.remove_highlight
        }
      )
      return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
      when (item.itemId) {
        R.id.cab_delete -> removeSelectedRows()
        R.id.cab_tag_bookmark -> tagSelectedBookmarks()
        else -> return false
      }
      mode.finish()
      return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
      adapter?.uncheckAll()
      if (mode == actionMode) {
        actionMode = null
      }
    }
  }

  companion object {
    private const val EXTRA_COLLECTION_ID = "collectionId"
    private const val EXTRA_COLLECTION_NAME = "collectionName"
    private const val EXTRA_HIGHLIGHT_COLOR = "highlightColor"

    fun collectionIntent(context: Context, collectionId: String, name: String?): Intent {
      return Intent(context, BookmarkListActivity::class.java)
        .putExtra(EXTRA_COLLECTION_ID, collectionId)
        .putExtra(EXTRA_COLLECTION_NAME, name)
    }

    fun highlightsIntent(context: Context, color: HighlightColor): Intent {
      return Intent(context, BookmarkListActivity::class.java)
        .putExtra(EXTRA_HIGHLIGHT_COLOR, color.name)
    }

    private fun modeFromIntent(intent: Intent): BookmarkListMode? {
      val collectionId = intent.getStringExtra(EXTRA_COLLECTION_ID)
      if (collectionId != null) {
        return BookmarkListMode.Collection(collectionId)
      }

      val color = intent.getStringExtra(EXTRA_HIGHLIGHT_COLOR)
        ?.let { name -> HighlightColor.entries.firstOrNull { color -> color.name == name } }
      return color?.let { BookmarkListMode.Highlights(it) }
    }
  }
}
