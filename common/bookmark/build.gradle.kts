plugins {
  id("quran.android.library.android")
  alias(libs.plugins.sqldelight)
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.mobile.bookmark"

sqldelight {
  databases {
    create("BookmarksDatabase") {
      packageName.set("com.quran.labs.androidquran")
      schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
      verifyMigrations.set(true)
    }
  }
}

dependencies {
  implementation(project(":common:di"))
  implementation(project(":common:data"))

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // sqldelight
  implementation(libs.sqldelight.android.driver)
  implementation(libs.sqldelight.coroutines.extensions)
  implementation(libs.sqldelight.primitive.adapters)

  // testing
  testImplementation(project(":common:test-utils"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
  testImplementation(libs.sqldelight.sqlite.driver)
}
