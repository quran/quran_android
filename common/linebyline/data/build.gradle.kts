plugins {
  id("quran.android.library.android")
  id("app.cash.sqldelight")
  alias(libs.plugins.anvil)
}

android.namespace = "com.quran.mobile.linebyline.data"

anvil {
  useKsp(contributesAndFactoryGeneration = true)
  generateDaggerFactories.set(true)
}

dependencies {
  implementation(project(":common:di"))
  implementation(project(":common:data"))

  implementation(libs.dagger.runtime)

  implementation(libs.kotlinx.collections.immutable)

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.sqldelight.android.driver)
  implementation(libs.sqldelight.coroutines.extensions)
}
