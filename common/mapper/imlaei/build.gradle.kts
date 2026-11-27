plugins {
  id("quran.android.library.android")
  id("app.cash.sqldelight")
  alias(libs.plugins.metro)
}

sqldelight {
  databases {
    create("ImlaeiUthmaniMappingDatabase") {
      packageName.set("com.quran.mobile.mapper.imlaei.data")
      schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
      verifyMigrations.set(true)
    }
  }
}

android.namespace = "com.quran.mobile.mapper.imlaei"

dependencies {
  implementation(project(":common:di"))
  implementation(project(":common:data"))
  implementation(project(":pages:data:madani"))

  implementation(libs.sqldelight.android.driver)
  implementation(libs.sqldelight.coroutines.extensions)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.sqldelight.sqlite.driver)
}
