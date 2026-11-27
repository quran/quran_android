package com.quran.labs.androidquran.ui.fragment

import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.ui.helpers.QuranRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class JuzListFragmentTest {

  private fun buildRows(count: Int = 270): Array<QuranRow> {
    return Array(count) { index ->
      val juz = (index / 9) + 1
      val isHeader = index % 9 == 0
      QuranRow.Builder()
        .withType(if (isHeader) QuranRow.HEADER else QuranRow.NONE)
        .withText(if (isHeader) "Juz $juz" else "Quarter $index")
        .withMetadata("Sura 1, Ayah 1")
        .withPage(index + 1)
        .build()
    }
  }

  private fun layoutRecycler(recyclerView: RecyclerView) {
    recyclerView.measure(
      View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
    )
    recyclerView.layout(0, 0, 720, 1200)
  }

  @Test
  fun `scrollToJuzPosition before rows arrive survives empty layout pass and scrolls once populated`() {
    Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      val containerId = View.generateViewId()
      activity.setContentView(FrameLayout(activity).apply { id = containerId })

      val fragment = JuzListFragment()
      activity.supportFragmentManager.beginTransaction()
        .add(containerId, fragment)
        .commitNow()

      val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
      val layoutManager = recyclerView.layoutManager as LinearLayoutManager

      // Empty layout pass, request scroll to Juz 21 (position 180), then another empty layout pass
      // (which previously caused LinearLayoutManager.onLayoutCompleted() to clear mPendingScrollPosition).
      layoutRecycler(recyclerView)
      assertThat(recyclerView.adapter?.itemCount).isEqualTo(0)

      val targetPosition = 180
      fragment.scrollToJuzPosition(targetPosition)
      layoutRecycler(recyclerView)

      // Now rows arrive from Dispatchers.Default and populate the adapter.
      fragment.applyJuzRows(buildRows(270))
      layoutRecycler(recyclerView)

      val firstVisible = layoutManager.findFirstVisibleItemPosition()
      val lastVisible = layoutManager.findLastVisibleItemPosition()
      assertThat(recyclerView.adapter?.itemCount).isEqualTo(270)
      assertThat(targetPosition in firstVisible..lastVisible).isTrue()
    }
  }

  @Test
  fun `scrollToJuzPosition after rows are already populated scrolls immediately`() {
    Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      val containerId = View.generateViewId()
      activity.setContentView(FrameLayout(activity).apply { id = containerId })

      val fragment = JuzListFragment()
      activity.supportFragmentManager.beginTransaction()
        .add(containerId, fragment)
        .commitNow()

      val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
      val layoutManager = recyclerView.layoutManager as LinearLayoutManager

      fragment.applyJuzRows(buildRows(270))
      layoutRecycler(recyclerView)
      assertThat(layoutManager.findFirstVisibleItemPosition()).isEqualTo(0)

      val targetPosition = 90
      fragment.scrollToJuzPosition(targetPosition)
      layoutRecycler(recyclerView)

      val firstVisible = layoutManager.findFirstVisibleItemPosition()
      val lastVisible = layoutManager.findLastVisibleItemPosition()
      assertThat(targetPosition in firstVisible..lastVisible).isTrue()
    }
  }
}
