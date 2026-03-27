pluginManagement {
   includeBuild("gradle/translationtools-plugin")

   repositories {
      google()
      mavenCentral()
      gradlePluginPortal()
   }
}

dependencyResolutionManagement {
   repositories {
      google()
      mavenCentral()
      maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
   }
}

rootProject.name = "translationtools-client-kmp"

include(":translationtools-client-compose")
