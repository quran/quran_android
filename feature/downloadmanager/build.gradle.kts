plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.anvil)
}

android.namespace = "com.quran.mobile.feature.downloadmanager"

anvil {
  useKsp(contributesAndFactoryGeneration = true, componentMerging = true)
  generateDaggerFactories.set(true)
}

dependencies {
  implementation(project(":common:audio"))
  implementation(project(":common:data"))
  implementation(project(":common:download"))
  implementation(project(":common:di"))
  implementation(project(":common:pages"))
  implementation(project(":common:search"))
  implementation(project(":common:ui:core"))

  implementation(libs.androidx.annotation)
  implementation(libs.androidx.activity.compose)

  // dagger
  implementation(libs.dagger.runtime)

  // compose
  implementation(libs.compose.animation)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material)
  implementation(libs.compose.material.icons)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  debugImplementation(libs.compose.ui.tooling)

  // immutable collections
  implementation(libs.kotlinx.collections.immutable)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
}
