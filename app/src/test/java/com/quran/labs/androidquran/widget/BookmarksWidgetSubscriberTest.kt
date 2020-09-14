package com.quran.labs.androidquran.widget

import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness


class BookmarksWidgetSubscriberTest {

  @get:Rule
  val mockitoRule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  @Mock
  private lateinit var bookmarkModel: BookmarkModel

  @Mock
  private lateinit var bookmarksWidgetUpdater: BookmarksWidgetUpdater

  @InjectMocks
  private lateinit var bookmarksWidgetSubscriber: BookmarksWidgetSubscriber

  @Test
  fun testSubscribeBookmarksWidgetIfBookmarksWidgetsExist() {
    `when`(bookmarksWidgetUpdater.checkForAnyBookmarksWidgets()).thenReturn(true)
    val testSubject = PublishSubject.create<Boolean>().toSerialized()
    `when`(bookmarkModel.bookmarksObservable()).thenReturn(testSubject.hide())

    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()

    verify(bookmarksWidgetUpdater, never()).updateBookmarksWidget()
    testSubject.onNext(true)
    verify(bookmarksWidgetUpdater).updateBookmarksWidget()
  }

  @Test
  fun testDontSubscribeBookmarksWidgetIfNoBookmarksWidgetsExist() {
    `when`(bookmarksWidgetUpdater.checkForAnyBookmarksWidgets()).thenReturn(false)

    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()

    verify(bookmarkModel, never()).bookmarksObservable()
  }

  @Test
  fun testSubscribeBookmarksWidgetIfWidgetGetsAdded() {
    `when`(bookmarksWidgetUpdater.checkForAnyBookmarksWidgets()).thenReturn(false)
    val testSubject = PublishSubject.create<Boolean>().toSerialized()
    `when`(bookmarkModel.bookmarksObservable()).thenReturn(testSubject.hide())

    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()
    bookmarksWidgetSubscriber.onEnabledBookmarksWidget()

    testSubject.onNext(true)
    verify(bookmarksWidgetUpdater).updateBookmarksWidget()
  }

  @Test
  fun testUnsubscribeBookmarksWidgetIfAllBookmarksWidgetsGetRemoved() {
    `when`(bookmarksWidgetUpdater.checkForAnyBookmarksWidgets()).thenReturn(true)
    val testSubject = PublishSubject.create<Boolean>().toSerialized()
    `when`(bookmarkModel.bookmarksObservable()).thenReturn(testSubject.hide())

    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()
    bookmarksWidgetSubscriber.onDisabledBookmarksWidget()
    testSubject.onNext(true)
    verify(bookmarksWidgetUpdater, never()).updateBookmarksWidget()
  }

  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
    }
  }
}
