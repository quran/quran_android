plugins {
  id("quran.android.library.android")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.labs.androidquran.pages.common.warsh"

metro {
  interop {
    includeDagger()
    includeAnvil()
  }
}

dependencies {
  implementation(project(":common:audio"))
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
