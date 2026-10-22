plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android {
  namespace = "com.quran.mobile.feature.memorize"
  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  // the session tests read the bundled lexicon
  testOptions.unitTests.isIncludeAndroidResources = true
  // library modules only build release; the on-device session test runs against it
  testBuildType = "release"
}

// the speech model and test audio for on-device tests, staged by
// tools/memorize/run_device_benchmark.sh (not in git); without the model the tests download it
androidComponents {
  onVariants { variant ->
    (variant as? com.android.build.api.variant.HasDeviceTests)?.deviceTests?.values?.forEach {
      it.sources.assets?.addStaticSourceDirectory(rootProject.file("build/memorize-testlab").path)
    }
  }
}

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:download"))
  implementation(project(":common:pages"))
  implementation(project(":common:recitation"))
  implementation(project(":common:ui:core"))
  implementation(project(":feature:memorize:tracker"))
  implementation(project(":feature:memorize:engine-sherpa"))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.preference.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)

  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.truth)
}
