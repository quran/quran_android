plugins {
  id("quran.android.library.android")
  alias(libs.plugins.anvil)
}

android.namespace = "com.quran.mobile.common.sync"

anvil {
  useKsp(contributesAndFactoryGeneration = true, componentMerging = true)
  generateDaggerFactories.set(true)
}

dependencies {
  implementation(project(":common:di"))
  implementation(project(":common:data"))

  // androidx
  api(libs.androidx.datastore.prefs)

  // app auth library
  implementation(libs.appauth)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // dagger
  implementation(libs.dagger.runtime)
}
