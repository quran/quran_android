package com.quran.labs.androidquran.core

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.quran.labs.androidquran.base.TestApplication

class QuranTestRunner : AndroidJUnitRunner() {

  override fun newApplication(
    cl: ClassLoader?,
    className: String?,
    context: Context?
  ): Application {
    return super.newApplication(cl, TestApplication::class.qualifiedName, context)
  }
}
