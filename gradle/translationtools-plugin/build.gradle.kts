plugins {
   `java-gradle-plugin`
   kotlin("jvm") version libs.versions.kotlin
   kotlin("plugin.serialization") version libs.versions.kotlin
}

repositories {
   mavenCentral()
   gradlePluginPortal()
}

val kotlinGradlePluginTestClasspath by configurations.creating {
   isCanBeConsumed = false
   isCanBeResolved = true
}

dependencies {
   implementation(gradleApi())
   implementation(libs.ktor.plugin.client.cio)
   implementation(libs.ktor.plugin.client.content.negotiation)
   implementation(libs.ktor.plugin.client.mock)
   implementation(libs.ktor.plugin.serialization.kotlinx.json)
   implementation(libs.kotlinx.serialization.json)
   implementation(libs.snakeyaml.engine)
   compileOnly(libs.kotlin.gradle.plugin)
   kotlinGradlePluginTestClasspath(libs.kotlin.gradle.plugin)

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

// TestKit withPluginClasspath isolates the plugin classpath; include KGP so codegen wiring
// can resolve KotlinMultiplatformExtension when tests apply KMP themselves.
tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
   pluginClasspath.from(kotlinGradlePluginTestClasspath)
}

tasks.test {
   useJUnitPlatform()
}
