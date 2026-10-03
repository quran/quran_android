pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "QuranKmp"
include(":core:model", ":core:domain", ":core:data", ":core:designsystem", ":feature:reader", ":feature:memorization", ":feature:qibla", ":feature:tutor", ":composeApp", ":androidApp")
