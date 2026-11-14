plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android {
  namespace = "com.quran.mobile.feature.progress"
  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  testOptions.unitTests.isIncludeAndroidResources = true
  // library modules only build release; the screenshot test runs against it
  testBuildType = "release"
  // the sample data both the renders and the device screenshots draw
  sourceSets["test"].kotlin.srcDir("src/sharedTest/kotlin")
  sourceSets["androidTest"].kotlin.srcDir("src/sharedTest/kotlin")
}

// -Pprogress.render: ProgressScreenRenderTest draws the Progress screens to
// build/outputs/progress-renders (phone, MatePad 12 X landscape and portrait, a medium window;
// English and Arabic; day and night) to look at them
tasks.withType<Test>().configureEach {
  if (providers.gradleProperty("progress.render").isPresent) systemProperty("progress.render", "true")
}

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:pages"))
  implementation(project(":common:progress"))
  implementation(project(":common:ui:core"))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.preference.ktx)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.compose.animation)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.collections.immutable)

  testImplementation(project(":pages:data:madani"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  androidTestImplementation(project(":pages:data:madani"))
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.junit)
}
