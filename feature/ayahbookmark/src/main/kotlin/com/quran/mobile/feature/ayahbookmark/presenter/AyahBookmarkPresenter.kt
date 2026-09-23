package com.quran.mobile.feature.ayahbookmark.presenter

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.quran.data.core.QuranInfo
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.HighlightsDao
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.highlight.Highlight
import com.quran.mobile.feature.ayahbookmark.R
import com.quran.mobile.feature.ayahbookmark.readingbookmark.ReadingBookmarkAction
import com.quran.mobile.feature.ayahbookmark.readingbookmark.presenter.ReadingBookmarkSheetPresenter
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkCollectionCreationState
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkCollectionItem
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkEvent
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkState
import com.quran.page.common.data.QuranNaming
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

@AssistedInject
class AyahBookmarkPresenter(
  @Assisted private val currentAyah: SuraAyah,
  @Assisted private val onReadingBookmarkAction: (ReadingBookmarkAction) -> Unit,
  private val bookmarksDao: BookmarksDao,
  private val highlightsDao: HighlightsDao,
  private val readingBookmarksDao: ReadingBookmarksDao,
  private val quranNaming: QuranNaming,
  private val quranInfo: QuranInfo,
  private val readingBookmarkSheetPresenter: ReadingBookmarkSheetPresenter,
  private val appCoroutineScope: AppCoroutineScope
) {

  @AssistedFactory
  fun interface Factory {
    fun create(
      currentAyah: SuraAyah,
      onReadingBookmarkAction: (ReadingBookmarkAction) -> Unit
    ): AyahBookmarkPresenter
  }

  @Composable
  fun present(): AyahBookmarkState {
    val collectionState = bookmarksDao.collectionsWithBookmarksFlow().collectAsState(null)
    val readingBookmarks = readingBookmarksDao.readingBookmarksFlow().collectAsState(null)
    val highlight = highlightsDao.highlightsFlow(currentAyah)
      .collectAsState(null)

    val ayahReadingBookmarks = remember(readingBookmarks.value) {
      readingBookmarks.value
        .orEmpty()
        .filter { it.asSuraAyah() == currentAyah }
    }

    val suggestedReadingBookmark = remember(ayahReadingBookmarks, readingBookmarks.value) {
      if (ayahReadingBookmarks.isEmpty()) {
        // TODO: we need to have a data source for this - the priority should be:
        // sessionLaunchedReadingBookmark ?: readingBookmarksBeforeThisAyah.takeFurthestWithin25PagesOfCurrent()
        //    ?: readingBookmarksBeforeThisAyah.takeFirst() ?: takeLastUpdated
        readingBookmarks.value.orEmpty()
          .filterNot { it is EmptyReadingBookmark }
          .maxByOrNull { it.timestamp }
          ?: EmptyReadingBookmark(ReadingBookmarkType.TEAL, Clock.System.now())
      } else {
        ayahReadingBookmarks.first()
      }
    }

    val otherReadingBookmarks = remember(readingBookmarks.value, suggestedReadingBookmark.slot) {
      val placed = readingBookmarks.value
        .orEmpty()
        .filterNot { it is EmptyReadingBookmark }
        .associateBy { it.slot }
      ReadingBookmarkType.entries
        .filterNot { it == suggestedReadingBookmark.slot }
        .map { slot -> placed[slot] ?: EmptyReadingBookmark(slot, Instant.DISTANT_PAST) }
        .toImmutableList()
    }

    val currentHighlight = remember(highlight.value) { mutableStateOf(highlight.value) }

    val collectionCreationState = remember {
      mutableStateOf<AyahBookmarkCollectionCreationState>(AyahBookmarkCollectionCreationState.Inactive)
    }

    val didSeedCollectionIds = remember { mutableStateOf(false) }
    val checkedCollectionIds = remember { mutableStateOf<Set<String>>(emptySet()) }

    val isDismissed = remember { mutableStateOf(false) }
    val isSelectingReadingBookmark = remember { mutableStateOf(false) }

    val collections = remember(collectionState.value, checkedCollectionIds.value) {
      collectionState.value.orEmpty().map { collectionState ->
        AyahBookmarkCollectionItem(
          collectionState.readingCollection.id,
          collectionState.readingCollection.name,
          collectionState.readingCollection.isDefault,
          collectionState.bookmarks.size,
          checkedCollectionIds.value.contains(collectionState.readingCollection.id)
        )
      }.toImmutableList()
    }

    LaunchedEffect(collectionState.value) {
      val collections = collectionState.value
      if (collections != null && !didSeedCollectionIds.value) {
        val enabledCollections =
          collections
            .filter { collection ->
              collection.bookmarks.any { it.sura == currentAyah.sura && it.ayah == currentAyah.ayah }
            }
            .map { it.readingCollection.id }
            .toSet()
        checkedCollectionIds.value = enabledCollections
        didSeedCollectionIds.value = true
      }
    }

    val scope = rememberCoroutineScope()

    val commit: () -> Unit = {
      isDismissed.value = true
      appCoroutineScope.launch {
        bookmarksDao.replaceAyahBookmarkCollections(currentAyah, checkedCollectionIds.value)

        val pendingHighlight = currentHighlight.value
        if (pendingHighlight?.color != highlight.value?.color) {
          if (pendingHighlight == null) {
            highlightsDao.clearHighlight(currentAyah)
          } else {
            highlightsDao.setHighlight(currentAyah, pendingHighlight.color)
          }
        }
      }
    }

    val readingBookmarkSelection = if (isSelectingReadingBookmark.value) {
      readingBookmarkSheetPresenter.present(
        target = ReadingBookmarkTarget.Ayah(currentAyah),
        isNested = true,
        onAction = { action ->
          onReadingBookmarkAction(action)
          commit()
        }
      )
    } else {
      null
    }

    LaunchedEffect(readingBookmarkSelection?.isDismissed) {
      if (readingBookmarkSelection?.isDismissed == true && !isDismissed.value) {
        isSelectingReadingBookmark.value = false
      }
    }

    val eventSink: (AyahBookmarkEvent) -> Unit = { event ->
      when (event) {
        AyahBookmarkEvent.CancelCreatingCollection ->
          collectionCreationState.value = AyahBookmarkCollectionCreationState.Inactive

        is AyahBookmarkEvent.CollectionNameChanged -> {
          val current = collectionCreationState.value
          if (current is AyahBookmarkCollectionCreationState.Active) {
            collectionCreationState.value = current.copy(name = event.name, hasNameError = false)
          }
        }

        is AyahBookmarkEvent.CreateCollection -> {
          collectionCreationState.value =
            AyahBookmarkCollectionCreationState.Active(event.name, true)
          scope.launch {
            try {
              val collection = bookmarksDao.addCollection(event.name)
              checkedCollectionIds.value += collection.id
              collectionCreationState.value = AyahBookmarkCollectionCreationState.Inactive
            } catch (exception: CancellationException) {
              throw exception
            } catch (_: IllegalArgumentException) {
              collectionCreationState.value = AyahBookmarkCollectionCreationState.Active(
                name = event.name,
                hasNameError = true
              )
            }
          }
        }

        AyahBookmarkEvent.Done -> commit()

        AyahBookmarkEvent.StartCreatingCollection ->
          collectionCreationState.value = AyahBookmarkCollectionCreationState.Active(name = "")
        is AyahBookmarkEvent.ToggleCollection -> {
          val collectionIds = checkedCollectionIds.value
          checkedCollectionIds.value = if (event.id in collectionIds) {
            collectionIds - event.id
          } else {
            collectionIds + event.id
          }
        }

        AyahBookmarkEvent.PlaceSuggestedReadingBookmark -> {
          onReadingBookmarkAction(
            ReadingBookmarkAction.Place(
              slot = suggestedReadingBookmark.slot,
              target = ReadingBookmarkTarget.Ayah(currentAyah)
            )
          )
          commit()
        }

        AyahBookmarkEvent.ClearSuggestedReadingBookmark -> {
          onReadingBookmarkAction(ReadingBookmarkAction.Clear(suggestedReadingBookmark.slot))
          commit()
        }

        AyahBookmarkEvent.ShowReadingBookmarks -> isSelectingReadingBookmark.value = true

        is AyahBookmarkEvent.SetHighlight -> {
          currentHighlight.value = Highlight(currentAyah, event.color, Clock.System.now())
        }

        AyahBookmarkEvent.ClearHighlight -> {
          currentHighlight.value = null
        }
      }
    }

    return AyahBookmarkState(
      ayah = currentAyah,
      suggestedReadingBookmark = suggestedReadingBookmark,
      isSuggestedReadingBookmarkEnabled = suggestedReadingBookmark.asSuraAyah() == currentAyah,
      otherReadingBookmarks = otherReadingBookmarks,
      currentAyahReadingBookmarks = ayahReadingBookmarks,
      readingBookmarkSelection = readingBookmarkSelection,
      collections = collections,
      collectionCreation = collectionCreationState.value,
      highlight = currentHighlight.value,
      isDismissed = isDismissed.value,
      suraAyahNameResolver = { context, ayah -> quranNaming.getSuraAyahString(context, ayah.sura, ayah.ayah) },
      readingBookmarkLocationResolver = { context, bookmark -> locationName(context, bookmark) },
      eventSink = eventSink
    )
  }

  private fun locationName(context: Context, bookmark: ReadingBookmark): String {
    return when (bookmark) {
      is PageReadingBookmark -> quranNaming.getSuraPageString(context, bookmark.page)
      is AyahReadingBookmark -> context.getString(
        R.string.readingbookmark_ayah_location,
        quranNaming.getSuraAyahString(context, bookmark.sura, bookmark.ayah),
        quranInfo.getPageFromSuraAyah(bookmark.sura, bookmark.ayah)
      )
      is EmptyReadingBookmark -> ""
    }
  }

  private fun ReadingBookmark?.asSuraAyah(): SuraAyah? {
    return if (this is AyahReadingBookmark) {
      SuraAyah(this.sura, this.ayah)
    } else {
      null
    }
  }
}
