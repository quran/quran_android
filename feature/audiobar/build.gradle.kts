plugins {
  id("quran.android.library.compose")
  id("org.jetbrains.kotlin.plugin.parcelize")
  alias(libs.plugins.anvil)
  alias(libs.plugins.ksp)
}

android.namespace = "com.quran.mobile.feature.audiobar"

anvil { generateDaggerFactories = true }

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
  implementation(libs.compose.material.icons)
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  debugImplementation(libs.compose.ui.tooling)

  // circuit
  implementation(libs.circuit.foundation)
  api(libs.circuit.codegen.annotations)
  ksp(libs.circuit.codegen)

  // dagger
  implementation(libs.dagger.runtime)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
}
