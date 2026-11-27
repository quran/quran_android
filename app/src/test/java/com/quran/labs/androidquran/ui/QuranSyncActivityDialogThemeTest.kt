package com.quran.labs.androidquran.ui

import android.os.Looper
import android.util.TypedValue
import androidx.appcompat.app.AlertDialog
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.ui.fragment.CollectionRenameDialogFactoryImpl
import com.quran.mobile.feature.sync.QuranSyncActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class QuranSyncActivityDialogThemeTest {

  @Test
  fun `collection rename dialog shows cleanly on QuranSyncActivity without accent resolution crash`() {
    val activity = Robolectric.buildActivity(QuranSyncActivity::class.java)
      .create()
      .start()
      .resume()
      .get()

    val dialogFragment = CollectionRenameDialogFactoryImpl().create("1", "Test")
    dialogFragment.show(activity.supportFragmentManager, "collectionRename")
    shadowOf(Looper.getMainLooper()).idle()

    val dialog = dialogFragment.dialog as? AlertDialog
    assertThat(dialog).isNotNull()
    assertThat(dialog!!.isShowing).isTrue()

    val accentValue = TypedValue()
    assertThat(
      dialog.context.theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, accentValue, true)
    ).isTrue()
    assertThat(
      dialog.context.theme.resolveAttribute(R.attr.quranAccent, accentValue, true)
    ).isTrue()
  }

  @Test
  fun `collection rename dialog also resolves accent cleanly on BookmarkListActivity`() {
    val activity = Robolectric.buildActivity(BookmarkListActivity::class.java)
      .create()
      .start()
      .resume()
      .get()

    val dialogFragment = CollectionRenameDialogFactoryImpl().create("1", "Test")
    dialogFragment.show(activity.supportFragmentManager, "collectionRename")
    shadowOf(Looper.getMainLooper()).idle()

    val dialog = dialogFragment.dialog as? AlertDialog
    assertThat(dialog).isNotNull()
    assertThat(dialog!!.isShowing).isTrue()

    val accentValue = TypedValue()
    assertThat(
      dialog.context.theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, accentValue, true)
    ).isTrue()
  }
}
