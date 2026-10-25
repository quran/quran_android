plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android {
  namespace = "com.quran.mobile.feature.appicon"

  // compose runtime calls android.os.Trace when presenters run in unit tests
  testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
  implementation(project(":common:analytics"))
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:ui:core"))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.process)

  // compose
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)

  // implementation but removed for release builds
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.ui.tooling)

  // immutable collections
  api(libs.kotlinx.collections.immutable)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // molecule
  implementation(libs.molecule)

  implementation(libs.timber)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
}
