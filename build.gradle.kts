plugins {
   id("com.android.library") version "8.5.2"
   id("com.vanniktech.maven.publish") version "0.35.0"
   kotlin("multiplatform") version "1.9.25"
   kotlin("plugin.serialization") version "1.9.25"
}

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform

group = "io.mvdm.translationtools"
version = "0.1.0"

kotlin {
   androidTarget {
      compilations.all {
         kotlinOptions {
            jvmTarget = "17"
         }
      }
   }

   jvm {
      compilations.all {
         kotlinOptions {
            jvmTarget = "17"
         }
      }
   }

   iosX64()
   iosArm64()
   iosSimulatorArm64()

   sourceSets {
      commonMain.dependencies {
         api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
         api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
         implementation("com.squareup.okio:okio:3.9.0")
         implementation("io.ktor:ktor-client-core:2.3.12")
         implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
      }

      commonTest.dependencies {
         implementation(kotlin("test"))
         implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
      }

      jvmTest.dependencies {
         implementation("io.ktor:ktor-client-mock:2.3.12")
         implementation("com.squareup.okio:okio-fakefilesystem:3.9.0")
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
         androidVariantsToPublish = listOf("release"),
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
