package com.quran.labs.androidquran

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QuranForwarderActivityReciterTest {

  private lateinit var context: Context

  @Before
  fun setup() {
    context = getApplicationContext()
  }

  private fun open(url: String) {
    val intent = Intent(context, QuranForwarderActivity::class.java).apply {
      action = Intent.ACTION_VIEW
      data = Uri.parse(url)
    }
    ActivityScenario.launch<QuranForwarderActivity>(intent).use {}
  }

  private fun currentQariId(): Int =
    PreferenceManager.getDefaultSharedPreferences(context)
      .getInt(CurrentQariManager.PREF_DEFAULT_QARI, UNSET_QARI)

  private fun setCurrentQari(qariId: Int) {
    PreferenceManager.getDefaultSharedPreferences(context)
      .edit()
      .putInt(CurrentQariManager.PREF_DEFAULT_QARI, qariId)
      .commit()
  }

  @Test
  fun testLinkChoosesTheReciterByQuranComRecitationId() {
    // 6 is Mahmoud Khalil Al-Husary on quran.com, qari 1 here.
    open("quran://2/255/?reciter=6")
    assertThat(currentQariId()).isEqualTo(1)
  }

  @Test
  fun testARecitationWeDoNotHaveLeavesTheQariAlone() {
    setCurrentQari(2)
    open("quran://2/255/?reciter=99999")
    assertThat(currentQariId()).isEqualTo(2)
  }

  @Test
  fun testALinkWithoutAReciterLeavesTheQariAlone() {
    setCurrentQari(2)
    open("quran://2/255")
    assertThat(currentQariId()).isEqualTo(2)
  }

  companion object {
    private const val UNSET_QARI = -1
  }
}
