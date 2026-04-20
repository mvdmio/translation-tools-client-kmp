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
version = "1.0.1"

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
      commonMain.dependencies {
         api(libs.kotlinx.coroutines.core)
         api(libs.kotlinx.datetime)
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
