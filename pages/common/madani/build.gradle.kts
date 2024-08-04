plugins {
  id("quran.android.library.android")
  alias(libs.plugins.anvil)
}

android.namespace = "com.quran.labs.androidquran.pages.common.madani"

anvil {
  useKsp(contributesAndFactoryGeneration = true)
  generateDaggerFactories.set(true)
}

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:upgrade"))

  // annotations
  implementation(libs.androidx.annotation)

  // dagger
  implementation(libs.dagger.runtime)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
}
