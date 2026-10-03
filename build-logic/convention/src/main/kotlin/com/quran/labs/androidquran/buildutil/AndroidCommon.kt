package com.quran.labs.androidquran.buildutil

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

fun CommonExtension.applyAndroidCommon(project: Project) {
  compileSdk = 37
  defaultConfig.minSdk = 24

  compileOptions.apply {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  project.tasks.withType<Test>().configureEach {
    // Robolectric's Android 37 FileDescriptor interceptor reflects into SharedSecrets.
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
  }

  lint.apply {
    checkReleaseBuilds = true
    enable.add("Interoperability")
    lintConfig = project.rootProject.file("lint.xml")
  }
}
