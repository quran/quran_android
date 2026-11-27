package com.quran.labs.androidquran.ui.translation

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.ui.helpers.HighlightTypes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class TranslationAdapterDetachTest {

  @Test
  fun `setData and unhighlight while recyclerView is detached do not throw`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val recyclerView = RecyclerView(context).apply {
      layoutManager = LinearLayoutManager(context)
    }
    assertThat(recyclerView.handler).isNull()

    val adapter = TranslationAdapter(
      context = context,
      recyclerView = recyclerView,
      onClickListener = View.OnClickListener {},
      onVerseSelectedListener = object : TranslationAdapter.OnVerseSelectedListener {
        override fun onVerseSelected(ayahInfo: QuranAyahInfo) {}
      },
      onJumpToVerseListener = object : TranslationAdapter.OnJumpToAyahListener {
        override fun onJumpToAyah(target: SuraAyah, page: Int) {}
      }
    )

    val ayah1 = QuranAyahInfo(1, 1, "bismillah", emptyList(), 1)
    val ayah2 = QuranAyahInfo(1, 2, "alhamdu", emptyList(), 2)
    val rows = listOf(
      TranslationViewRow(TranslationViewRow.Type.VERSE_NUMBER, ayah1),
      TranslationViewRow(TranslationViewRow.Type.TRANSLATION_TEXT, ayah1, "In the name"),
      TranslationViewRow(TranslationViewRow.Type.VERSE_NUMBER, ayah2),
      TranslationViewRow(TranslationViewRow.Type.TRANSLATION_TEXT, ayah2, "All praise")
    )

    adapter.setData(rows)
    adapter.setHighlightedAyah(1, HighlightTypes.SELECTION)
    adapter.setHighlightedAyah(2, HighlightTypes.SELECTION)
    adapter.setData(rows)
    adapter.unhighlight()

    assertThat(adapter.itemCount).isEqualTo(4)
  }
}
