package com.quran.mobile.feature.ayahbookmark.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkCollectionCreationState
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkCollectionItem
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkEvent
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkState
import com.quran.page.common.data.QuranNaming
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class AyahBookmarkPresenter(
  @Assisted private val currentAyah: SuraAyah,
  private val bookmarksDao: BookmarksDao,
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
    val readingBookmark = readingBookmarksDao.readingBookmarkFlow().collectAsState(null)

    val isReadingBookmarkEnabledState = remember(readingBookmark.value) {
      mutableStateOf(readingBookmark.value.asSuraAyah() == currentAyah)
    }

    val collectionCreationState = remember {
      mutableStateOf<AyahBookmarkCollectionCreationState>(AyahBookmarkCollectionCreationState.Inactive)
    }
    val showLastPlaceWarningState = remember { mutableStateOf(false) }
    val isBookmarkRemovedState = remember { mutableStateOf(false) }

    val hasExistingCollections = remember { mutableStateOf(false) }
    val didSeedCollectionIds = remember { mutableStateOf(false) }
    val checkedCollectionIds = remember { mutableStateOf<Set<String>>(emptySet()) }

    val isDismissed = remember { mutableStateOf(false) }

    val removalJob = remember { mutableStateOf<Job?>(null) }

    val collections = remember(collectionState.value, checkedCollectionIds.value) {
      collectionState.value.orEmpty().map { collectionState ->
        AyahBookmarkCollectionItem(
          collectionState.readingCollection.id,
          collectionState.readingCollection.name,
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
        hasExistingCollections.value = enabledCollections.isNotEmpty()
      }
    }

    LaunchedEffect(showLastPlaceWarningState.value) {
      if (showLastPlaceWarningState.value) {
        delay(remainingItemWarningTimeout)
        showLastPlaceWarningState.value = false
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
            collectionCreationState.value = current.copy(name = event.name)
          }
        }
        is AyahBookmarkEvent.CreateCollection -> {
          collectionCreationState.value =
            AyahBookmarkCollectionCreationState.Active(event.name, true)
          scope.launch {
            val collection = bookmarksDao.addCollection(event.name)
            checkedCollectionIds.value += collection.id
            collectionCreationState.value = AyahBookmarkCollectionCreationState.Inactive
          }
        }
        AyahBookmarkEvent.Done ->
          appCoroutineScope.launch {
            isDismissed.value = true
            val collections = checkedCollectionIds.value
            if (collections.isNotEmpty()) {
              bookmarksDao.replaceAyahBookmarkCollections(currentAyah, collections)
            } else {
              bookmarksDao.deleteAyahBookmark(currentAyah)
            }

            val wasReadingBookmark = readingBookmark.value.asSuraAyah() == currentAyah
            if (wasReadingBookmark != isReadingBookmarkEnabledState.value) {
              if (isReadingBookmarkEnabledState.value) {
                readingBookmarksDao.setAyahReadingBookmark(currentAyah)
              } else {
                readingBookmarksDao.deleteReadingBookmark()
              }
            }
          }
        AyahBookmarkEvent.RemoveBookmark -> {
          isBookmarkRemovedState.value = true
          removalJob.value?.cancel()
          removalJob.value = appCoroutineScope.launch {
            delay(undoDefaultTimeout)
            isDismissed.value = true
            bookmarksDao.deleteAyahBookmark(currentAyah)
            if (currentAyah == readingBookmark.value.asSuraAyah()) {
              readingBookmarksDao.deleteReadingBookmark()
            }
          }
        }
        AyahBookmarkEvent.StartCreatingCollection ->
          collectionCreationState.value = AyahBookmarkCollectionCreationState.Active(name = "")
        is AyahBookmarkEvent.ToggleCollection -> {
          val collectionIds = checkedCollectionIds.value
          checkedCollectionIds.value = if (event.id in collectionIds) {
            if (collectionIds.size > 1 || isReadingBookmarkEnabledState.value) {
              collectionIds - event.id
            } else {
              showLastPlaceWarningState.value = true
              collectionIds
            }
          } else {
            collectionIds + event.id
          }
        }
        AyahBookmarkEvent.ToggleReadingBookmark -> {
          val isEnabled = isReadingBookmarkEnabledState.value
          if (isEnabled) {
            if (checkedCollectionIds.value.isNotEmpty()) {
              isReadingBookmarkEnabledState.value = false
            } else {
              showLastPlaceWarningState.value = true
            }
          } else {
            isReadingBookmarkEnabledState.value = true
          }
        }

        AyahBookmarkEvent.UndoRemoveBookmark -> {
          removalJob.value?.cancel()
          isBookmarkRemovedState.value = false
        }
      }
    }

    return AyahBookmarkState(
      ayah = currentAyah,
      isReadingBookmarkEnabled = isReadingBookmarkEnabledState.value,
      currentReadingBookmark = readingBookmark.value.asSuraAyah(),
      collections = collections,
      collectionCreation = collectionCreationState.value,
      showLastPlaceWarning = showLastPlaceWarningState.value,
      isBookmarkRemoved = isBookmarkRemovedState.value,
      showRemoveBookmarkButton = hasExistingCollections.value || readingBookmark.value.asSuraAyah() == currentAyah,
      suraAyahNameResolver = { context, ayah -> quranNaming.getSuraAyahString(context, ayah.sura, ayah.ayah) },
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

  companion object {
    val undoDefaultTimeout = 4.seconds
    val remainingItemWarningTimeout = 3.seconds
  }
}
