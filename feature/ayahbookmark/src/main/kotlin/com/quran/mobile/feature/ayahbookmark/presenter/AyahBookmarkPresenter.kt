package com.quran.mobile.feature.ayahbookmark.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.HighlightsDao
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.highlight.Highlight
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

@AssistedInject
class AyahBookmarkPresenter(
  @Assisted private val currentAyah: SuraAyah,
  private val bookmarksDao: BookmarksDao,
  private val highlightsDao: HighlightsDao,
  private val readingBookmarksDao: ReadingBookmarksDao,
  private val quranNaming: QuranNaming,
  private val appCoroutineScope: AppCoroutineScope
) {

  @AssistedFactory
  fun interface Factory {
    fun create(currentAyah: SuraAyah): AyahBookmarkPresenter
  }

  @Composable
  fun present(): AyahBookmarkState {
    val collectionState = bookmarksDao.collectionsWithBookmarksFlow().collectAsState(null)
    val readingBookmarks = readingBookmarksDao.readingBookmarksFlow().collectAsState(null)
    val highlight = highlightsDao.highlightsFlow(currentAyah)
      .collectAsState(null)

    val enabledReadingBookmarksState = remember(readingBookmarks.value) {
      mutableStateOf(
        readingBookmarks.value
          .orEmpty()
          .filter { it.asSuraAyah() == currentAyah }
      )
    }

    val suggestedReadingBookmark = remember(enabledReadingBookmarksState.value) {
      val current = enabledReadingBookmarksState.value
      if (current.isEmpty()) {
        // TODO: we need to have a data source for this - the priority should be:
        // sessionLaunchedReadingBookmark ?: readingBookmarksBeforeThisAyah.takeFurthestWithin25PagesOfCurrent()
        //    ?: readingBookmarksBeforeThisAyah.takeFirst() ?: takeLastUpdated
        readingBookmarks.value.orEmpty()
          .filterNot { it is EmptyReadingBookmark }
          .maxByOrNull { it.timestamp }
          ?: EmptyReadingBookmark(ReadingBookmarkType.TEAL, Clock.System.now())
      } else {
        current.first()
      }
    }

    val currentHighlight = remember(highlight.value) { mutableStateOf(highlight.value) }

    val collectionCreationState = remember {
      mutableStateOf<AyahBookmarkCollectionCreationState>(AyahBookmarkCollectionCreationState.Inactive)
    }

    val didSeedCollectionIds = remember { mutableStateOf(false) }
    val checkedCollectionIds = remember { mutableStateOf<Set<String>>(emptySet()) }

    val isDismissed = remember { mutableStateOf(false) }

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

        AyahBookmarkEvent.Done ->
          appCoroutineScope.launch {
            isDismissed.value = true
            bookmarksDao.replaceAyahBookmarkCollections(currentAyah, checkedCollectionIds.value)

            val previousReadingBookmarks = readingBookmarks.value.orEmpty()
              .filter { it.asSuraAyah() == currentAyah }

            // TODO: remove after new ui for multiple bookmarks since those will just apply immediately
            if (previousReadingBookmarks != enabledReadingBookmarksState.value) {
              val removed = previousReadingBookmarks - enabledReadingBookmarksState.value.toSet()
              val added = enabledReadingBookmarksState.value - previousReadingBookmarks.toSet()
              readingBookmarksDao.updateReadingBookmarks(ayah = currentAyah, added = added, removed = removed)
            }

            val currentHighlight = currentHighlight.value
            if (currentHighlight?.color != highlight.value?.color) {
              if (currentHighlight == null) {
                highlightsDao.clearHighlight(currentAyah)
              } else {
                highlightsDao.setHighlight(currentAyah, currentHighlight.color)
              }
            }
          }

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

        is AyahBookmarkEvent.ToggleReadingBookmark -> {
          val current = enabledReadingBookmarksState.value
          val matching = current.firstOrNull { it.slot == event.type }
          enabledReadingBookmarksState.value = if (matching == null) {
            current + AyahReadingBookmark(
              event.type,
              currentAyah.sura,
              currentAyah.ayah,
              Clock.System.now()
            )
          } else {
            current - matching
          }
        }

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
      isReadingBookmarkEnabled = enabledReadingBookmarksState.value.firstOrNull().asSuraAyah() == currentAyah,
      currentReadingBookmark = enabledReadingBookmarksState.value.firstOrNull(),
      suggestedReadingBookmark = suggestedReadingBookmark,
      isSuggestedReadingBookmarkEnabled = suggestedReadingBookmark.asSuraAyah() == currentAyah,
      currentAyahReadingBookmarks = enabledReadingBookmarksState.value,
      collections = collections,
      collectionCreation = collectionCreationState.value,
      highlight = currentHighlight.value,
      isDismissed = isDismissed.value,
      suraAyahNameResolver = { context, ayah -> quranNaming.getSuraAyahString(context, ayah.sura, ayah.ayah) },
      suraPageNameResolver = { context, page -> quranNaming.getSuraPageString(context, page) },
      eventSink = eventSink
    )
  }

  private fun ReadingBookmark?.asSuraAyah(): SuraAyah? {
    return if (this is AyahReadingBookmark) {
      SuraAyah(this.sura, this.ayah)
    } else {
      null
    }
  }
}
