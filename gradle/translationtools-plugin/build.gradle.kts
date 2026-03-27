plugins {
   `java-gradle-plugin`
   kotlin("jvm") version "1.9.25"
   kotlin("plugin.serialization") version "1.9.25"
}

repositories {
   mavenCentral()
   gradlePluginPortal()
}

dependencies {
   implementation(gradleApi())
   implementation(kotlin("stdlib"))
   implementation("io.ktor:ktor-client-cio:2.3.12")
   implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
   implementation("io.ktor:ktor-client-mock:2.3.12")
   implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
   implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
   implementation("org.snakeyaml:snakeyaml-engine:2.9")
   implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")

   testImplementation(kotlin("test"))
   testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
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
