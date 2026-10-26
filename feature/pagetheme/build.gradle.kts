plugins {
  id("quran.android.library.compose")
}

android.namespace = "com.quran.mobile.feature.pagetheme"
android.testOptions.unitTests.isIncludeAndroidResources = true

dependencies {
  implementation(project(":common:ui:core"))

  // compose
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)

  // immutable collections
  implementation(libs.kotlinx.collections.immutable)

  // molecule
  implementation(libs.molecule)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.robolectric)
  testImplementation(libs.turbine)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.junit.ktx)
}
