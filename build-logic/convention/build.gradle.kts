plugins {
   `kotlin-dsl`
}

group = "com.quran.labs.androidquran.buildlogic"

dependencies {
   compileOnly("com.android.tools.build:gradle:8.1.1")
   compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
}

gradlePlugin {
   plugins {
     register("androidApplication") {
       id = "quran.android.application"
       implementationClass = "AndroidApplicationConventionPlugin"
     }

     register("androidLibrary") {
       id = "quran.android.library.android"
       implementationClass = "AndroidLibraryConventionPlugin"
     }

     register("androidComposeLibrary") {
       id = "quran.android.library.compose"
       implementationClass = "AndroidLibraryComposeConventionPlugin"
     }

     register("kotlinLibrary") {
       id = "quran.android.library"
       implementationClass = "KotlinLibraryConventionPlugin"
     }
   }
}
