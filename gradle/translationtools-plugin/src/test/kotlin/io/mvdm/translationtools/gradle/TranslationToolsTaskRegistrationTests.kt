package io.mvdm.translationtools.gradle

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class TranslationToolsTaskRegistrationTests
{
   @Test
   fun tasks_should_include_init_pull_and_push_translationtools_tasks()
   {
      val projectDir = createTempDirectory("translationtools-functional").toFile()
      writeFunctionalBuildFiles(projectDir)
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: test-key
         defaultLocale: en
         generated:
           packageName: com.example.translations
         """.trimIndent(),
      )

      val result = GradleRunner.create()
         .withProjectDir(projectDir)
         .withPluginClasspath()
         .withArguments("tasks", "--all")
         .build()

      assertTrue(result.output.contains("initTranslationTools"))
      assertTrue(result.output.contains("pullTranslations"))
      assertTrue(result.output.contains("pushTranslations"))
   }
}

private fun writeFunctionalBuildFiles(projectDir: File)
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
      """.trimIndent(),
   )

   File(projectDir, "build.gradle.kts").writeText(
      """
      plugins {
         id("org.jetbrains.kotlin.multiplatform") version "1.9.25"
         id("io.mvdm.translationtools.plugin")
      }

      kotlin {
         jvm()
      }

      """.trimIndent(),
    )
}
