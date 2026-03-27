plugins {
   id("com.android.library") version "8.5.2"
   id("org.jetbrains.compose") version "1.7.3"
   id("com.vanniktech.maven.publish") version "0.35.0"
   kotlin("multiplatform") version "1.9.25"
}

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform

group = rootProject.group
version = rootProject.version

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

   sourceSets {
      commonMain.dependencies {
         api(project(":"))
         implementation(compose.runtime)
       }

      commonTest.dependencies {
         implementation(kotlin("test"))
         implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
      }

      jvmTest.dependencies {
         implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
      }
   }
}

android {
   namespace = "io.mvdm.translationtools.client.compose"
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
      "translationtools-client-compose",
      version.toString(),
   )

   publishToMavenCentral()
   val shouldSign = providers.gradleProperty("signingInMemoryKey").isPresent
      || providers.gradleProperty("signing.secretKeyRingFile").isPresent

   if (shouldSign)
      signAllPublications()

   pom {
      name.set("translationtools-client-compose")
      description.set("Compose helpers for the mvdm.io TranslationTools Kotlin Multiplatform client.")
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
