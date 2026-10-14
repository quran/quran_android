plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android {
  namespace = "com.quran.mobile.feature.sync"
}

dependencies {
  implementation(project(":common:bookmark"))
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:ui:core"))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.preference.ktx)

  implementation(libs.compose.foundation)
  implementation(libs.compose.material)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.ui.tooling)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.quran.mobile.sync)
}
