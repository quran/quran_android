plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android {
  namespace = "com.quran.mobile.feature.sync"
  testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
  implementation(project(":common:bookmark"))
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:ui:core"))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.preference.ktx)

  implementation(libs.compose.foundation)
  implementation(libs.compose.material)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.ui.tooling)

  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)

  // molecule
  implementation(libs.molecule)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.quran.mobile.sync)

  testImplementation(libs.junit)
  testImplementation(libs.turbine)
  testImplementation(libs.kotlinx.coroutines.test)
}
