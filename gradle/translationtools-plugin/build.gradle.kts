plugins {
   `java-gradle-plugin`
   kotlin("jvm") version libs.versions.kotlin
   kotlin("plugin.serialization") version libs.versions.kotlin
}

repositories {
   mavenCentral()
   gradlePluginPortal()
}

dependencies {
   implementation(gradleApi())
   implementation(libs.ktor.client.cio)
   implementation(libs.ktor.client.content.negotiation)
   implementation(libs.ktor.client.mock)
   implementation(libs.ktor.serialization.kotlinx.json)
   implementation(libs.kotlinx.serialization.json)
   implementation(libs.snakeyaml.engine)
   implementation(libs.kotlin.gradle.plugin)

   testImplementation(kotlin("test"))
   testImplementation(libs.kotlinx.coroutines.test)
   testImplementation(gradleTestKit())
}

gradlePlugin {
   plugins {
      create("translationTools") {
         id = "io.mvdm.translationtools.plugin"
         implementationClass = "io.mvdm.translationtools.gradle.TranslationToolsPlugin"
      }
   }
}

tasks.test {
   useJUnitPlatform()
}
