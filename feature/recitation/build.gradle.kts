plugins {
  id("quran.android.library.android")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.mobile.recitation"

metro {
  interop {
    includeDagger()
    includeAnvil()
  }
}

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:recitation"))
  implementation(libs.androidx.annotation)

  // dagger
  implementation(libs.dagger.runtime)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
}
