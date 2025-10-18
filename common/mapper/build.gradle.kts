plugins {
  id("quran.android.library.android")
  alias(libs.plugins.metro)
}

metro {
  interop {
    includeDagger()
    includeAnvil()
  }
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
