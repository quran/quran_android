package com.quran.labs.androidquran.view

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.quran.labs.androidquran.TestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QuranPageLayoutTest {

@Test
fun fullScreenPortraitIsNotScrollable() {
val context = createContext(411, 915, Configuration.ORIENTATION_PORTRAIT)
val layout = QuranImagePageLayout(context)
assertFalse(layout.canScroll())
}

@Test
fun constrainedSplitScreenPortraitIsScrollable() {
val context = createContext(411, 420, Configuration.ORIENTATION_PORTRAIT)
val layout = QuranImagePageLayout(context)
assertTrue(layout.canScroll())
}

@Test
fun landscapeIsScrollable() {
val context = createContext(915, 411, Configuration.ORIENTATION_LANDSCAPE)
val layout = QuranImagePageLayout(context)
assertTrue(layout.canScroll())
}

@Test
fun tabletLayoutInConstrainedPortraitIsNotScrollable() {
val context = createContext(411, 420, Configuration.ORIENTATION_PORTRAIT)
val layout = QuranTabletImagePageLayout(context)
assertFalse(layout.canScroll())
}

private fun createContext(widthDp: Int, heightDp: Int, orientation: Int): Context {
val baseContext = ApplicationProvider.getApplicationContext<Context>()
val config = Configuration(baseContext.resources.configuration).apply {
screenWidthDp = widthDp
screenHeightDp = heightDp
this.orientation = orientation
}
return baseContext.createConfigurationContext(config)
}
}
