plugins {
   alias(libs.plugins.android.library)
   alias(libs.plugins.maven.publish)
   id("io.mvdm.translationtools.plugin")
   alias(libs.plugins.kotlin.multiplatform)
   alias(libs.plugins.kotlin.serialization)
}

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform

group = "io.mvdm.translationtools"
version = "3.1.0"

val generatedVersionDir = layout.buildDirectory.dir("generated/translationtools")

val generateTranslationToolsBuildConfig by tasks.registering {
   val outputDir = generatedVersionDir
   val projectVersion = version.toString()
   inputs.property("version", projectVersion)
   outputs.dir(outputDir)

   doLast {
      val packageDir = outputDir.get().dir("io/mvdm/translationtools/client").asFile
      packageDir.mkdirs()
      packageDir.resolve("TranslationToolsBuildConfig.kt").writeText(
         """
         package io.mvdm.translationtools.client

         internal const val TRANSLATION_TOOLS_VERSION: String = "$projectVersion"
         """.trimIndent() + "\n"
      )
   }
}

kotlin {
   androidTarget {
      compilerOptions {
         jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
      }
   }

   jvm {
      compilerOptions {
         jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
      }
   }

   iosX64()
   iosArm64()
   iosSimulatorArm64()

   sourceSets {
      commonMain {
         // Pass the task provider (not just the output dir) so the build dependency
         // is carried into the source set for every consumer — the compile tasks and
         // the *SourcesJar tasks alike. Otherwise the sources jars (built during
         // publish) read this generated dir without a declared dependency and Gradle
         // fails task-input validation.
         kotlin.srcDir(generateTranslationToolsBuildConfig)
      }

      commonMain.dependencies {
         api(libs.kotlinx.coroutines.core)
         implementation(libs.okio)
         implementation(libs.ktor.client.core)
         implementation(libs.kotlinx.serialization.json)
      }

      commonTest.dependencies {
         implementation(kotlin("test"))
         implementation(libs.kotlinx.coroutines.test)
      }

      jvmTest.dependencies {
         implementation(libs.ktor.client.mock)
         implementation(libs.okio.fakefilesystem)
      }
   }
}

android {
   namespace = "io.mvdm.translationtools.client"
   compileSdk = 36

   defaultConfig {
      minSdk = 24
   }

   compileOptions {
      sourceCompatibility = JavaVersion.VERSION_17
      targetCompatibility = JavaVersion.VERSION_17
   }
}

mavenPublishing {
   configure(
      KotlinMultiplatform(
         javadocJar = JavadocJar.Empty(),
      )
   )

   coordinates(
      group.toString(),
      "translationtools-client-kmp",
      version.toString(),
   )

   publishToMavenCentral()
   val shouldSign = providers.gradleProperty("signingInMemoryKey").isPresent
      || providers.gradleProperty("signing.secretKeyRingFile").isPresent

   if (shouldSign)
      signAllPublications()

   pom {
      name.set("translationtools-client-kmp")
      description.set("Kotlin Multiplatform client library for the mvdm.io TranslationTools API.")
      url.set("https://github.com/mvdmio/translation-tools-client-kmp")
      inceptionYear.set("2026")

      licenses {
         license {
            name.set("Proprietary")
            url.set("https://github.com/mvdmio/translation-tools-client-kmp")
            distribution.set("repo")
         }
      }

      developers {
         developer {
            id.set("mvdmio")
            name.set("Michiel van der Meer")
            url.set("https://github.com/mvdmio")
         }
      }

      scm {
         url.set("https://github.com/mvdmio/translation-tools-client-kmp")
         connection.set("scm:git:git://github.com/mvdmio/translation-tools-client-kmp.git")
         developerConnection.set("scm:git:ssh://git@github.com/mvdmio/translation-tools-client-kmp.git")
      }
   }
}

val iosMavenCheckRepo = layout.buildDirectory.dir("ios-maven-check")
val iosMavenCheckRequiredArtifactIds = listOf(
   "translationtools-client-kmp-iosarm64",
   "translationtools-client-kmp-iosx64",
   "translationtools-client-kmp-iossimulatorarm64",
   "translationtools-client-compose-iosarm64",
   "translationtools-client-compose-iossimulatorarm64",
)

allprojects {
   pluginManager.withPlugin("maven-publish") {
      extensions.configure<org.gradle.api.publish.PublishingExtension> {
         repositories {
            maven {
               name = "IosMavenCheck"
               url = uri(iosMavenCheckRepo)
            }
         }
      }
   }
}

tasks.register("verifyIosMavenVariants") {
   group = "verification"
   description =
      "Publishes client + Compose to build/ios-maven-check and asserts iOS variant modules exist on disk."
   dependsOn(
      "publishAllPublicationsToIosMavenCheckRepository",
      ":translationtools-client-compose:publishAllPublicationsToIosMavenCheckRepository",
   )

   val repoDir = iosMavenCheckRepo
   val required = iosMavenCheckRequiredArtifactIds
   val projectVersion = provider { version.toString() }

   doLast {
      val root = repoDir.get().asFile
      val resolvedVersion = projectVersion.get()
      val missing = required.filter { artifactId ->
         !root.resolve("io/mvdm/translationtools/$artifactId/$resolvedVersion/$artifactId-$resolvedVersion.klib").isFile
      }
      if (missing.isNotEmpty())
      {
         throw GradleException(
            "Missing iOS Maven variant modules under ${root.absolutePath}: ${missing.joinToString()}. " +
               "Compile iOS klibs with -Pkotlin.native.ignoreDisabledTargets=false before publish " +
               "(kotlin.native.ignoreDisabledTargets must not hide a missing iOS output on the publish job). " +
               "If Linux cannot produce these klibs because of C interop, switch only the publish job to a macOS runner.",
         )
      }
   }
}

tasks.named("publishAndReleaseToMavenCentral").configure {
   dependsOn("verifyIosMavenVariants")
}
