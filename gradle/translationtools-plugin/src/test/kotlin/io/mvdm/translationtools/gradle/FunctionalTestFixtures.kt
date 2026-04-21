package io.mvdm.translationtools.gradle

import java.io.File

internal fun writeBuildFiles(projectDir: File, kotlinVersion: String = "1.9.25")
{
   File(projectDir, "settings.gradle.kts").writeText(
      """
      pluginManagement {
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
         }
      }
      """.trimIndent()
   )

   File(projectDir, "build.gradle.kts").writeText(
      """
      plugins {
         id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
         id("io.mvdm.translationtools.plugin")
      }

      kotlin {
         jvm()
      }

      """.trimIndent()
   )
}

internal fun writeStandardTestFixtures(projectDir: File)
{
   File(projectDir, "translationtools.yaml").writeText(
      """
      apiKey: test-key
      defaultLocale: en
      locales:
        - en
      generated:
        packageName: com.example.translations
      androidResources:
        resourceDirectories:
          - src/androidMain/res
      """.trimIndent()
   )
   File(projectDir, "src/androidMain/res/values").mkdirs()
   File(projectDir, "src/androidMain/res/values/strings.xml").writeText(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
         <string name="home_title">Home</string>
      </resources>
      """.trimIndent()
   )
}
