plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.labs.androidquran.extra.feature.linebyline"

// https://issuetracker.google.com/issues/372756067
android.lint {
   disable.add("SuspiciousModifierThen")
}

dependencies {
  implementation(project(":common:di"))
  implementation(project(":common:data"))
  implementation(project(":common:audio"))
  implementation(project(":common:bookmark"))
  implementation(project(":common:reading"))
  implementation(project(":common:analytics"))
  implementation(project(":common:drawing"))
  implementation(project(":common:linebyline:ui"))
  // has to be api, otherwise can't add classes to the correct components
  api(project(":common:linebyline:data"))

  implementation(libs.androidx.fragment.ktx)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)

  implementation(libs.compose.ui)
  implementation(libs.compose.material)

  // implementation but removed for release builds
  implementation(libs.compose.ui.tooling)
  implementation(libs.compose.ui.tooling.preview)

  implementation(libs.kotlinx.collections.immutable)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.compose.ui.tooling)
}
