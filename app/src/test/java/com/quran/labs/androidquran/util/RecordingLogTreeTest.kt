package com.quran.labs.androidquran.util

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class RecordingLogTreeTest {

  private var plantedTree: RecordingLogTree? = null

  @Before
  fun setUp() {
    Timber.uprootAll()
  }

  @After
  fun tearDown() {
    plantedTree?.let { Timber.uproot(it) }
    Timber.uprootAll()
  }

  @Test
  fun `non-debuggable mode drops verbose and debug logs`() {
    val tree = RecordingLogTree(isDebuggable = false).also { plantedTree = it }
    Timber.plant(tree)

    Timber.v("secret verbose query")
    Timber.d("secret debug query")
    Timber.i("info message")
    Timber.w("warn message")

    val logs = tree.getLogs()
    assertThat(logs).doesNotContain("secret verbose query")
    assertThat(logs).doesNotContain("secret debug query")
    assertThat(logs).contains("info message")
    assertThat(logs).contains("warn message")
  }

  @Test
  fun `debuggable mode records debug logs`() {
    val tree = RecordingLogTree(isDebuggable = true).also { plantedTree = it }
    Timber.plant(tree)

    Timber.d("debug message")

    assertThat(tree.getLogs()).contains("debug message")
  }
}
