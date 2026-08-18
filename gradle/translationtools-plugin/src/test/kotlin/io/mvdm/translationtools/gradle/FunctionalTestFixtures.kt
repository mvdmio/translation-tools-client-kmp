package io.mvdm.translationtools.gradle

import java.io.File

internal fun writeBuildFiles(projectDir: File, kotlinVersion: String = "1.9.25")
{
   writeSettingsFiles(projectDir)

   File(projectDir, "build.gradle.kts").writeText(
      """
      plugins {
         id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
         id("io.mvdm.translationtools.plugin")
      }

      kotlin {
         jvm()
      }

      tasks.register("printCommonMainKotlinSrcDirs") {
         val srcDirs = project.extensions
            .getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
            .sourceSets.getByName("commonMain").kotlin.srcDirs
         doLast {
            srcDirs.forEach { src ->
               println("COMMON_MAIN_SRC=" + src.invariantSeparatorsPath)
            }
         }
      }

      """.trimIndent()
   )
}

internal fun writeNonKmpBuildFiles(projectDir: File)
{
   writeSettingsFiles(projectDir)

   File(projectDir, "build.gradle.kts").writeText(
      """
      plugins {
         id("io.mvdm.translationtools.plugin")
      }
      """.trimIndent()
   )
}

private fun writeSettingsFiles(projectDir: File)
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
}

internal fun writeStandardTestFixtures(
   projectDir: File,
   enabled: Boolean? = null,
   prune: Boolean? = null,
   locales: List<String> = listOf("en"),
   appleResourceDirectories: List<String> = emptyList(),
)
{
   File(projectDir, "translationtools.yaml").writeText(
      buildString {
         appendLine("apiKey: test-key")
         appendLine("defaultLocale: en")
         appendLine("locales:")
         locales.forEach { appendLine("  - $it") }
         appendLine("generated:")
         if (enabled != null)
            appendLine("  enabled: $enabled")
         appendLine("  packageName: com.example.translations")
         appendLine("androidResources:")
         appendLine("  resourceDirectories:")
         appendLine("    - src/androidMain/res")
         if (prune != null)
            appendLine("  prune: $prune")
         if (appleResourceDirectories.isNotEmpty())
         {
            appendLine("appleResources:")
            appendLine("  resourceDirectories:")
            appleResourceDirectories.forEach { appendLine("    - $it") }
         }
      },
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
