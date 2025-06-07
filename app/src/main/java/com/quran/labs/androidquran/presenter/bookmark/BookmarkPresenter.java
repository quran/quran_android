package com.quran.labs.androidquran.presenter.bookmark;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.snackbar.Snackbar;
import com.quran.data.core.QuranInfo;
import com.quran.data.model.bookmark.Bookmark;
import com.quran.data.model.bookmark.BookmarkData;
import com.quran.data.model.bookmark.RecentPage;
import com.quran.data.model.bookmark.Tag;
import com.quran.labs.androidquran.dao.bookmark.BookmarkRawResult;
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment;
import com.quran.labs.androidquran.ui.helpers.QuranRow;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.DisposableSingleObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class BookmarkPresenter implements Presenter<BookmarksFragment> {
  @Snackbar.Duration public static final int DELAY_DELETION_DURATION_IN_MS = 4 * 1000; // 4 seconds
  private static final long BOOKMARKS_WITHOUT_TAGS_ID = -1;

  private final BookmarkModel bookmarkModel;
  private final QuranSettings quranSettings;
  private final QuranInfo quranInfo;

  private int sortOrder;
  private boolean groupByTags;
  private boolean showRecents;
  private boolean showDate;
  private BookmarkRawResult cachedData;
  private BookmarksFragment fragment;
  private final ArabicDatabaseUtils arabicDatabaseUtils;

  private DisposableSingleObserver<BookmarkRawResult> pendingRemoval;
  private List<QuranRow> itemsToRemove;


  @Inject
  BookmarkPresenter(BookmarkModel bookmarkModel,
                    QuranSettings quranSettings,
                    ArabicDatabaseUtils arabicDatabaseUtils,
                    QuranInfo quranInfo) {
    this.quranSettings = quranSettings;
    this.bookmarkModel = bookmarkModel;
    this.arabicDatabaseUtils = arabicDatabaseUtils;
    this.quranInfo = quranInfo;

    sortOrder = quranSettings.getBookmarksSortOrder();
    groupByTags = quranSettings.getBookmarksGroupedByTags();
    showRecents = quranSettings.getShowRecents();
    showDate = quranSettings.getShowDate();
    subscribeToChanges();
  }

  @SuppressLint("CheckResult")
  @SuppressWarnings("CheckReturnValue")
  void subscribeToChanges() {
    Observable.merge(bookmarkModel.tagsObservable(),
        bookmarkModel.bookmarksObservable(), bookmarkModel.recentPagesUpdatedObservable())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(ignore -> {
          if (fragment != null) {
            requestData(false);
          } else {
            cachedData = null;
          }
        });
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
    quranSettings.setBookmarksSortOrder(this.sortOrder);
    requestData(false);
  }

  public void toggleGroupByTags() {
    groupByTags = !groupByTags;
    quranSettings.setBookmarksGroupedByTags(groupByTags);
    requestData(false);
  }

  public void toggleShowRecents() {
    showRecents = !showRecents;
    quranSettings.setShowRecents(showRecents);
    requestData(false);
  }

  public void toogleShowDate() {
    showDate = !showDate;
    quranSettings.setShowDate(showDate);
    requestData(false);
  }

  public boolean isShowingRecents() {
    return showRecents;
  }

  public boolean isDateShowing() {
    return showDate;
  }

  public boolean shouldShowInlineTags() {
    return !groupByTags;
  }

  public boolean isGroupedByTags() {
    return groupByTags;
  }

  public boolean[] getContextualOperationsForItems(List<QuranRow> rows) {
    boolean[] result = new boolean[3];

    int headers = 0;
    int bookmarks = 0;
    for (int i = 0, rowsSize = rows.size(); i < rowsSize; i++) {
      QuranRow row = rows.get(i);
      if (row.isBookmarkHeader()) {
        headers++;
      } else if (row.isBookmark()) {
        bookmarks++;
      }
    }

    result[0] = headers == 1 && bookmarks == 0;
    result[1] = (headers + bookmarks) > 0;
    result[2] = headers == 0 && bookmarks > 0;
    return result;
  }

  public void requestData(boolean canCache) {
    if (canCache && cachedData != null) {
      if (fragment != null) {
        Timber.d("sending cached bookmark data");
        fragment.onNewRawData(cachedData);
      }
    } else {
      Timber.d("requesting bookmark data from the database");
      getBookmarks(sortOrder, groupByTags);
    }
  }

  public void deleteAfterSomeTime(List<QuranRow> remove) {
    // the fragment just called this, so fragment should be valid
    fragment.onNewRawData(predictQuranListAfterDeletion(remove));

    if (pendingRemoval != null) {
      // handle a new delete request when one is already happening by adding those items to delete
      // now and un-subscribing from the old request.
      if (itemsToRemove != null) {
        remove.addAll(itemsToRemove);
      }
      cancelDeletion();
    }

    itemsToRemove = remove;
    pendingRemoval = Single.timer(DELAY_DELETION_DURATION_IN_MS, TimeUnit.MILLISECONDS)
        .flatMap(ignore -> removeItemsObservable())
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableSingleObserver<BookmarkRawResult>() {

          @Override
          public void onSuccess(@NonNull BookmarkRawResult result) {
            pendingRemoval = null;
            cachedData = result;
            if (fragment != null) {
              fragment.onNewRawData(result);
            }
          }

          @Override
          public void onError(@NonNull Throwable e) {
          }
        });
  }

  private BookmarkRawResult predictQuranListAfterDeletion(List<QuranRow> remove) {
    if (cachedData == null) {
      return null;
    }

    final List<BookmarkRowData> cachedRows = cachedData.getRows();
    // Extract the IDs and context of items to remove
    final Set<Long> bookmarkIdsToRemove = new HashSet<>();
    final Set<Long> tagIdsToUntag = new HashSet<>();
    final Map<Long, Set<Long>> bookmarkTagContext = new HashMap<>(); // bookmarkId -> set of tagId contexts
    
    for (QuranRow row : remove) {
      if (row.isBookmark() && row.bookmarkId > 0) {
        if (groupByTags && row.tagId > 0) {
          // When grouped by tags, removing a bookmark removes it from that specific tag
          // The same bookmark can appear multiple times in this case.
          final Set<Long> currentTags = bookmarkTagContext.get(row.bookmarkId);
          final Set<Long> updatedTags = currentTags != null ? currentTags : new HashSet<>();
          updatedTags.add(row.tagId);
          bookmarkTagContext.put(row.bookmarkId, updatedTags);
        } else {
          // When not grouped by tags, or untagged bookmark, remove the bookmark entirely
          bookmarkIdsToRemove.add(row.bookmarkId);
        }
      } else if (row.isBookmarkHeader() && row.tagId > 0) {
        // Removing a tag header untags all bookmarks with that tag (only when grouped by tags)
        tagIdsToUntag.add(row.tagId);
      }
    }

    // Filter the rows from cached data
    final List<BookmarkRowData> filteredRows = new ArrayList<>();
    final Set<Bookmark> removedBookmarks = new HashSet<>();

    boolean haveUntaggedSection = false;
    for (BookmarkRowData rowData : cachedRows) {
      boolean shouldKeep = true;
      
      if (rowData instanceof BookmarkRowData.BookmarkItem bookmarkItem) {
        final long bookmarkId = bookmarkItem.getBookmark().getId();
        final Long currentTagId = bookmarkItem.getTagId();
        
        if (bookmarkIdsToRemove.contains(bookmarkId)) {
          // Remove bookmark entirely
          shouldKeep = false;
        } else if (bookmarkTagContext.containsKey(bookmarkId)) {
          // Check if this bookmark should be removed from this specific tag context
          final Set<Long> contextTagIds = bookmarkTagContext.get(bookmarkId);
          if (contextTagIds != null && currentTagId != null && contextTagIds.contains(currentTagId)) {
            shouldKeep = false;
          } else if (tagIdsToUntag.contains(currentTagId)) {
            shouldKeep = false;
          }
        } else if (currentTagId != null && tagIdsToUntag.contains(currentTagId)) {
          // we're removing this tag from all bookmarks that contain it
          shouldKeep = false;
        }
        
        if (shouldKeep) {
          filteredRows.add(rowData);
        } else {
          removedBookmarks.add(bookmarkItem.getBookmark());
        }
      } else if (rowData instanceof BookmarkRowData.TagHeader tagHeader) {
        final long tagId = tagHeader.getTag().getId();
        if (!tagIdsToUntag.contains(tagId)) {
          filteredRows.add(rowData);
        }
      } else if (rowData instanceof BookmarkRowData.NotTaggedHeader) {
        haveUntaggedSection = true;
        filteredRows.add(rowData);
      } else {
        // Keep other types (recent pages, etc.)
        filteredRows.add(rowData);
      }
    }
    
    // Second pass: identify bookmarks that will become completely untagged
    final Set<Bookmark> newlyUntaggedBookmarks = new HashSet<>();

    for (Bookmark removedBookmark : removedBookmarks) {
      final Set<Long> tags = new HashSet<>(removedBookmark.getTags());
      if (!tags.isEmpty()) {
        final Set<Long> tagsExplicitlyRemovedFromBookmark = bookmarkTagContext.get(removedBookmark.getId());
        final Set<Long> tagHeaderDeletions = new HashSet<>(tagIdsToUntag);
        tagHeaderDeletions.retainAll(tags);
        final Set<Long> tagsDeletedFromBookmark = new HashSet<>(tagHeaderDeletions);
        if (tagsExplicitlyRemovedFromBookmark != null) {
          tagsDeletedFromBookmark.addAll(tagsExplicitlyRemovedFromBookmark);
        }

        if (tagsDeletedFromBookmark.equals(tags)) {
          newlyUntaggedBookmarks.add(removedBookmark);
        }
      }
    }

    final Set<BookmarkRowData.BookmarkItem> newlyUntaggedBookmarkItems = new HashSet<>();
    if (!newlyUntaggedBookmarks.isEmpty()) {
      for (Bookmark newlyUntaggedBookmark : newlyUntaggedBookmarks) {
        // Find first BookmarkItem that contains a bookmark from newlyUntaggedBookmarks
        BookmarkRowData.BookmarkItem item = null;
        for (BookmarkRowData rowData : cachedRows) {
          if (rowData instanceof BookmarkRowData.BookmarkItem bookmarkItem) {
            if (newlyUntaggedBookmark.equals(bookmarkItem.getBookmark())) {
              item = bookmarkItem;
              break;
            }
          }
        }

        if (item != null) {
          newlyUntaggedBookmarkItems.add(item);
        }
      }
    }

    if (!newlyUntaggedBookmarkItems.isEmpty()) {
      if (!haveUntaggedSection) {
        // If we don't have an untagged section, add it
        filteredRows.add(BookmarkRowData.NotTaggedHeader.INSTANCE);
      }

      for (BookmarkRowData.BookmarkItem untaggedItem : newlyUntaggedBookmarkItems) {
        // Create untagged version of this bookmark
        final BookmarkRowData.BookmarkItem untaggedBookmark = new BookmarkRowData.BookmarkItem(
            untaggedItem.getBookmark(), null);
        filteredRows.add(untaggedBookmark);
      }
    }
    
    // Update tag map: remove tags that were explicitly deleted via tag header deletion
    // Keep tags that just became empty due to bookmark removal
    final Map<Long, Tag> filteredTagMap = new HashMap<>(cachedData.getTagMap());
    for (Long tagId : tagIdsToUntag) {
      filteredTagMap.remove(tagId);
    }

    return new BookmarkRawResult(filteredRows, filteredTagMap);
  }

  private Single<BookmarkRawResult> removeItemsObservable() {
    return bookmarkModel.removeItemsObservable(new ArrayList<>(itemsToRemove))
        .andThen(getBookmarksListObservable(sortOrder, groupByTags));
  }

  public void cancelDeletion() {
    if (pendingRemoval != null) {
      pendingRemoval.dispose();
      pendingRemoval = null;
      itemsToRemove = null;
    }
  }

  private Single<BookmarkData> getBookmarksWithAyatObservable(int sortOrder) {
    return bookmarkModel.getBookmarkDataObservable(sortOrder)
        .map(bookmarkData -> {
          try {
            return new BookmarkData(bookmarkData.getTags(),
                arabicDatabaseUtils.hydrateAyahText(bookmarkData.getBookmarks()),
                bookmarkData.getRecentPages());
          } catch (Exception e) {
            return bookmarkData;
          }
        });
  }

  @VisibleForTesting
  Single<BookmarkRawResult> getBookmarksListObservable(
      int sortOrder, final boolean groupByTags) {
    return getBookmarksWithAyatObservable(sortOrder)
        .map(bookmarkData -> {
          List<BookmarkRowData> rows = getBookmarkRowData(bookmarkData, groupByTags);
          Map<Long, Tag> tagMap = generateTagMap(bookmarkData.getTags());
          return new BookmarkRawResult(rows, tagMap);
        })
        .subscribeOn(Schedulers.io());
  }

  @SuppressLint("CheckResult")
  @SuppressWarnings("CheckReturnValue")
  private void getBookmarks(final int sortOrder, final boolean groupByTags) {
    getBookmarksListObservable(sortOrder, groupByTags)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(result -> {
          // notify the ui if we're attached
          cachedData = result;
          if (fragment != null) {
            if (pendingRemoval != null && itemsToRemove != null) {
              fragment.onNewRawData(predictQuranListAfterDeletion(itemsToRemove));
            } else {
              fragment.onNewRawData(result);
            }
          }
        });
  }

  private List<BookmarkRowData> getBookmarkRowData(BookmarkData data, boolean groupByTags) {
    List<BookmarkRowData> rows;

    List<Tag> tags = data.getTags();
    List<Bookmark> bookmarks = data.getBookmarks();

    if (groupByTags) {
      rows = getRowDataSortedByTags(tags, bookmarks);
    } else {
      rows = getSortedRowData(bookmarks);
    }

    List<RecentPage> recentPages = data.getRecentPages();
    int size = recentPages.size();

    if (size > 0) {
      if (!showRecents) {
        // only show the last bookmark if show recents is off
        size = 1;
      }
      rows.add(0, new BookmarkRowData.RecentPageHeader(size));
      for (int i = 0; i < size; i++) {
        rows.add(i + 1, new BookmarkRowData.RecentPage(recentPages.get(i)));
      }
    }

    return rows;
  }

  private List<BookmarkRowData> getRowDataSortedByTags(List<Tag> tags, List<Bookmark> bookmarks) {
    List<BookmarkRowData> rows = new ArrayList<>();
    // sort by tags, alphabetical
    Map<Long, List<Bookmark>> tagsMapping = generateTagsMapping(tags, bookmarks);
    for (int i = 0, tagsSize = tags.size(); i < tagsSize; i++) {
      Tag tag = tags.get(i);
      rows.add(new BookmarkRowData.TagHeader(tag));
      List<Bookmark> tagBookmarks = tagsMapping.get(tag.getId());
      for (int j = 0, tagBookmarksSize = tagBookmarks == null ? 0 : tagBookmarks.size(); j < tagBookmarksSize; j++) {
        rows.add(new BookmarkRowData.BookmarkItem(tagBookmarks.get(j), tag.getId()));
      }
    }

    // add untagged bookmarks
    List<Bookmark> untagged = tagsMapping.get(BOOKMARKS_WITHOUT_TAGS_ID);
    if (untagged != null && !untagged.isEmpty()) {
      rows.add(BookmarkRowData.NotTaggedHeader.INSTANCE);
      for (int i = 0, untaggedSize = untagged.size(); i < untaggedSize; i++) {
        rows.add(new BookmarkRowData.BookmarkItem(untagged.get(i), null));
      }
    }
    return rows;
  }

  private List<BookmarkRowData> getSortedRowData(List<Bookmark> bookmarks) {
    List<BookmarkRowData> rows = new ArrayList<>(bookmarks.size());
    List<Bookmark> ayahBookmarks = new ArrayList<>();

    // add the page bookmarks directly, save ayah bookmarks for later
    for (int i = 0, bookmarksSize = bookmarks.size(); i < bookmarksSize; i++) {
      Bookmark bookmark = bookmarks.get(i);
      if (bookmark.isPageBookmark()) {
        rows.add(new BookmarkRowData.BookmarkItem(bookmark, null));
      } else {
        ayahBookmarks.add(bookmark);
      }
    }

    // add page bookmarks header if needed
    if (!rows.isEmpty()) {
      rows.add(0, BookmarkRowData.PageBookmarksHeader.INSTANCE);
    }

    // add ayah bookmarks if any
    if (!ayahBookmarks.isEmpty()) {
      rows.add(BookmarkRowData.AyahBookmarksHeader.INSTANCE);
      for (int i = 0, ayahBookmarksSize = ayahBookmarks.size(); i < ayahBookmarksSize; i++) {
        rows.add(new BookmarkRowData.BookmarkItem(ayahBookmarks.get(i), null));
      }
    }

    return rows;
  }

  private Map<Long, List<Bookmark>> generateTagsMapping(
      List<Tag> tags, List<Bookmark> bookmarks) {
    Set<Long> seenBookmarks = new HashSet<>();
    Map<Long, List<Bookmark>> tagMappings = new HashMap<>();
    for (int i = 0, tagSize = tags.size(); i < tagSize; i++) {
      long id = tags.get(i).getId();
      List<Bookmark> matchingBookmarks = new ArrayList<>();
      for (int j = 0, bookmarkSize = bookmarks.size(); j < bookmarkSize; j++) {
        Bookmark bookmark = bookmarks.get(j);
        if (bookmark.getTags().contains(id)) {
          matchingBookmarks.add(bookmark);
          seenBookmarks.add(bookmark.getId());
        }
      }
      tagMappings.put(id, matchingBookmarks);
    }

    List<Bookmark> untaggedBookmarks = new ArrayList<>();
    for (int i = 0, bookmarksSize = bookmarks.size(); i < bookmarksSize; i++) {
      Bookmark bookmark = bookmarks.get(i);
      if (!seenBookmarks.contains(bookmark.getId())) {
        untaggedBookmarks.add(bookmark);
      }
    }
    tagMappings.put(BOOKMARKS_WITHOUT_TAGS_ID, untaggedBookmarks);

    return tagMappings;
  }

  private Map<Long, Tag> generateTagMap(List<Tag> tags) {
    Map<Long, Tag> tagMap = new HashMap<>(tags.size());
    for (int i = 0, size = tags.size(); i < size; i++) {
      Tag tag = tags.get(i);
      tagMap.put(tag.getId(), tag);
    }
    return tagMap;
  }


  @Override
  public void bind(@NonNull BookmarksFragment fragment) {
    this.fragment = fragment;
    requestData(true);
  }

  @Override
  public void unbind(BookmarksFragment fragment) {
    if (fragment.equals(this.fragment)) {
      this.fragment = null;
    }
  }
}
