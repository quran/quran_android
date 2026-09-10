plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.mobile.feature.ayahbookmark"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:pages"))
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

  // immutable collections
  implementation(libs.kotlinx.collections.immutable)

  // molecule
  implementation(libs.molecule)
}
