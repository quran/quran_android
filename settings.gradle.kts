pluginManagement {
  includeBuild("build-logic")
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    maven("https://androidx.dev/storage/compose-compiler/repository/")
  }
}

include(":app")
include(":common:analytics")
include(":common:audio")
include(":common:bookmark")
include(":common:data")
include(":common:drawing")
include(":common:di")
include(":common:download")
include(":common:mapper")
include(":common:networking")
include(":common:pages")
include(":common:reading")
include(":common:linebyline:data")
include(":common:linebyline:ui")
include(":common:recitation")
include(":common:preference")
include(":common:search")
include(":common:sync")
include(":common:toolbar")
include(":common:translation")
include(":common:upgrade")
include(":common:ui:core")
include(":feature:analytics-noop")
include(":feature:firebase-analytics")
include(":feature:audio")
include(":feature:autoquran")
include(":feature:audiobar")
include(":feature:downloadmanager")
include(":feature:linebyline")
include(":feature:qarilist")
include(":feature:recitation")
include(":feature:sync")
include(":pages:madani")
include(":pages:data:madani")
include(":pages:data:warsh")
include(":pages:common:madani")
include(":pages:common:warsh")
include(":autoquran")

if (File(rootDir, "extras/settings-extra.gradle").exists()) {
  apply(File(rootDir, "extras/settings-extra.gradle"))
}
