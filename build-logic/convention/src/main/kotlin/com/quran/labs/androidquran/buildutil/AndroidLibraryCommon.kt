package com.quran.labs.androidquran.buildutil

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasUnitTestBuilder
import org.gradle.api.Project

fun Project.configureAndroidLibraryVariants() {
  val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
  androidComponents.beforeVariants { variant ->
    if (variant.buildType == "debug") {
      variant.enable = false
    } else {
      (variant as? HasUnitTestBuilder)?.enableUnitTest = true
    }
  }
}
