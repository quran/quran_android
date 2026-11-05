package com.quran.labs.androidquran.presenter.bookmark

import android.app.Dialog
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.common.truth.Truth.assertThat
import com.quran.data.dao.BookmarksDao
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.ui.fragment.AddTagDialog
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class AddTagDialogPresenterTest {

  private val bookmarksDao = FakeBookmarksDao()

  @Test
  fun `renaming a tag saves the new name and finishes`() = runTest {
    bookmarksDao.setTags(listOf(Tag("tag-1", "Review")))
    var finishedCount = 0

    boundPresenter().updateTag(Tag("tag-1", "Revision")) { finishedCount++ }
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(finishedCount).isEqualTo(1)
    assertThat(bookmarksDao.tags()).contains(Tag("tag-1", "Revision"))
  }

  @Test
  fun `renaming a tag that's gone keeps the dialog open with an error`() = runTest {
    val dialog = shownDialog()
    var finishedCount = 0

    dialog.addTagDialogPresenter.updateTag(Tag("deleted-tag", "Revision")) { finishedCount++ }
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(finishedCount).isEqualTo(0)
    assertThat(dialog.tagNameError()).isEqualTo(dialog.getString(R.string.tag_save_error))
    assertThat(dialog.isAdded).isTrue()
    assertThat(bookmarksDao.tags().map { it.id }).doesNotContain("deleted-tag")
  }

  @Test
  fun `rename write errors keep the dialog open`() = runTest {
    val failingDao = object : BookmarksDao by bookmarksDao {
      override suspend fun updateTag(tag: Tag): Boolean = error("Write failed")
    }
    val dialog = shownDialog(failingDao)
    var finishedCount = 0

    dialog.addTagDialogPresenter.updateTag(Tag("tag-1", "Revision")) { finishedCount++ }
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(finishedCount).isEqualTo(0)
    assertThat(dialog.tagNameError()).isEqualTo(dialog.getString(R.string.tag_save_error))
    assertThat(dialog.isAdded).isTrue()
  }

  @Test
  fun `adding a tag named Favorites says the name is reserved, not taken`() = runTest {
    val dialog = shownDialog()

    dialog.submit("Favorites")

    assertThat(dialog.tagNameError()).isEqualTo(dialog.getString(R.string.tag_reserved_tag_error))
    assertThat(bookmarksDao.addedTagNames()).isEmpty()
    assertThat(dialog.isAdded).isTrue()
  }

  private fun boundPresenter(): AddTagDialogPresenter {
    return AddTagDialogPresenter(bookmarksDao).apply { bind(AddTagDialog()) }
  }

  // shows the dialog in an activity, so it has a name field to show errors in, backed by the fake DAO
  private fun shownDialog(dao: BookmarksDao = bookmarksDao): AddTagDialog {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val dialog = AddTagDialog()
    dialog.show(activity.supportFragmentManager, AddTagDialog.TAG)
    shadowOf(Looper.getMainLooper()).idle()
    dialog.addTagDialogPresenter.unbind(dialog)
    dialog.addTagDialogPresenter = AddTagDialogPresenter(dao).apply { bind(dialog) }
    return dialog
  }

  private fun AddTagDialog.submit(name: String) {
    requireDialog().findViewById<TextInputEditText>(R.id.tag_name).setText(name)
    (requireDialog() as AlertDialog).getButton(Dialog.BUTTON_POSITIVE).performClick()
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun AddTagDialog.tagNameError(): CharSequence? {
    return requireDialog().findViewById<TextInputEditText>(R.id.tag_name).error
  }
}
