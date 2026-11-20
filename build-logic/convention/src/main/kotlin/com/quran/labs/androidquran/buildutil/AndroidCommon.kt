package com.quran.labs.androidquran.buildutil

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

fun CommonExtension.applyAndroidCommon(project: Project) {
  compileSdk {
    version = release(37) {
      minorApiLevel = 1
    }
  }
  defaultConfig.minSdk = 24

  compileOptions.apply {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  lint.apply {
    checkReleaseBuilds = true
    enable.add("Interoperability")
    @Suppress("UnstableApiUsage")
    lintConfig = project.isolated.rootProject.projectDirectory.file("lint.xml").asFile
  }
}
