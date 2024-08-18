plugins {
  id("quran.android.library.android")
  alias(libs.plugins.anvil)
}

anvil {
  useKsp(contributesAndFactoryGeneration = true)
  generateDaggerFactories.set(true)
}

android.namespace = "com.quran.labs.androidquran.common.mapper"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":pages:data:madani"))

  // dagger
  implementation(libs.dagger.runtime)

  // testing
  testImplementation(project(":pages:data:warsh"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
