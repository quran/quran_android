plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
  alias(libs.plugins.ksp)
}

android.namespace = "com.quran.mobile.feature.audiobar"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:audio"))
  implementation(project(":common:download"))
  implementation(project(":common:recitation"))
  implementation(project(":common:ui:core"))

  // compose
  implementation(libs.compose.animation)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)

  // implementation but removed for release builds
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.ui.tooling)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // molecule
  implementation(libs.molecule)
}
