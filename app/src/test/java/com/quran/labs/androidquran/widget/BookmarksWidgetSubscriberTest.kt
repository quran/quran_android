package com.quran.labs.androidquran.widget

import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.schedulers.TestScheduler
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import org.mockito.Mockito.`when` as whenever


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
    whenever(bookmarksWidgetUpdater.checkForAnyBookmarksWidgets()).thenReturn(true)
    val testSubject = PublishSubject.create<Boolean>().toSerialized()
    whenever(bookmarkModel.bookmarksObservable()).thenReturn(testSubject.hide())

    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()
    testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

    verify(bookmarksWidgetUpdater, never()).updateBookmarksWidget()
    testSubject.onNext(true)
    testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)
    verify(bookmarksWidgetUpdater).updateBookmarksWidget()
  }

  @Test
  fun testDontSubscribeBookmarksWidgetIfNoBookmarksWidgetsExist() {
    whenever(bookmarksWidgetUpdater.checkForAnyBookmarksWidgets()).thenReturn(false)

    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()

    verify(bookmarkModel, never()).bookmarksObservable()
  }

  @Test
  fun testSubscribeBookmarksWidgetIfWidgetGetsAdded() {
    whenever(bookmarksWidgetUpdater.checkForAnyBookmarksWidgets()).thenReturn(false)
    val testSubject = PublishSubject.create<Boolean>().toSerialized()
    whenever(bookmarkModel.bookmarksObservable()).thenReturn(testSubject.hide())

    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()
    bookmarksWidgetSubscriber.onEnabledBookmarksWidget()

    testSubject.onNext(true)
    testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)
    verify(bookmarksWidgetUpdater).updateBookmarksWidget()
  }

  @Test
  fun testUnsubscribeBookmarksWidgetIfAllBookmarksWidgetsGetRemoved() {
    whenever(bookmarksWidgetUpdater.checkForAnyBookmarksWidgets()).thenReturn(true)
    val testSubject = PublishSubject.create<Boolean>().toSerialized()
    whenever(bookmarkModel.bookmarksObservable()).thenReturn(testSubject.hide())

    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()
    bookmarksWidgetSubscriber.onDisabledBookmarksWidget()
    testSubject.onNext(true)
    testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)
    verify(bookmarksWidgetUpdater, never()).updateBookmarksWidget()
  }

  companion object {

    private val testScheduler = TestScheduler()

    @BeforeClass
    @JvmStatic
    fun setup() {
      RxAndroidPlugins.setMainThreadSchedulerHandler { testScheduler }
    }

    @AfterClass
    @JvmStatic
    fun tearDown() {
      RxAndroidPlugins.reset()
    }
  }
}
