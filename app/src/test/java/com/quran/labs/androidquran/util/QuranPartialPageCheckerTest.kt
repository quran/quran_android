package com.quran.labs.androidquran.util

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QuranPartialPageCheckerTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val checker = QuranPartialPageChecker()

  @Test
  fun `empty directory returns empty list`() {
    val dir = tempFolder.newFolder("pages")
    assertThat(checker.checkPages(dir, 10, "_1024")).isEmpty()
  }

  @Test
  fun `pages 1 and 2 are skipped even when corrupt`() {
    val dir = tempFolder.newFolder("pages")
    File(dir, QuranFileUtils.getPageFileName(1)).writeBytes(byteArrayOf())
    File(dir, QuranFileUtils.getPageFileName(2)).writeBytes(byteArrayOf())

    assertThat(checker.checkPages(dir, 2, "_1024")).isEmpty()
  }

  @Test
  fun `corrupt or empty images are reported as partial without aborting subsequent pages`() {
    val dir = tempFolder.newFolder("pages")
    File(dir, QuranFileUtils.getPageFileName(3)).writeBytes(byteArrayOf())
    File(dir, QuranFileUtils.getPageFileName(4)).writeText("not a valid png")
    File(dir, QuranFileUtils.getPageFileName(5)).writeBytes(byteArrayOf())

    val partial = checker.checkPages(dir, 5, "_1024")
    assertThat(partial).containsExactly(3, 4, 5).inOrder()
  }
}
