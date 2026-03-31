package io.mvdm.translationtools.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationToolsPluginFunctionalTests
{
   @Test
    fun generateTranslationResources_should_generate_res_file_from_snapshot()
   {
      val projectDir = createTempDirectory("translationtools-functional").toFile()
      writeBuildFiles(projectDir)
       File(projectDir, "translationtools.yaml").writeText(
            """
            apiKey: test-key
            defaultLocale: en
            locales:
              - en
            generated:
              packageName: com.example.translations
              objectName: Res
           """.trimIndent()
       )
       File(projectDir, "snapshot.json").writeText(
          """
          {
           "schemaVersion": 1,
           "project": {
             "defaultLocale": "en",
             "locales": ["en"]
           },
           "translations": {
             "en": {
               "home.title": "Home"
             }
           }
         }
         """.trimIndent()
      )

      val result = GradleRunner.create()
         .withProjectDir(projectDir)
         .withPluginClasspath()
         .withArguments("generateTranslationResources")
         .build()

       assertEquals(TaskOutcome.SUCCESS, result.task(":generateTranslationResources")?.outcome)
       val generated = File(projectDir, "build/generated/source/translationtools/commonMain/kotlin/com/example/translations/Res.kt")
       val bundled = File(projectDir, "build/generated/source/translationtools/commonMain/kotlin/com/example/translations/ResBundledSnapshot.kt")
       assertTrue(generated.exists())
       assertTrue(bundled.exists())
       assertTrue(generated.readText().contains("val home_title"))
   }

   @Test
   fun generateTranslationResources_should_fail_when_snapshot_is_missing()
   {
      val projectDir = createTempDirectory("translationtools-functional").toFile()
      writeBuildFiles(projectDir)
      File(projectDir, "translationtools.yaml").writeText(
           """
           apiKey: test-key
           defaultLocale: en
           generated:
             packageName: com.example.translations
           """.trimIndent()
      )

      val result = GradleRunner.create()
         .withProjectDir(projectDir)
         .withPluginClasspath()
         .withArguments("generateTranslationResources")
         .buildAndFail()

      assertTrue(result.output.contains("BUILD FAILED"))
      assertTrue(
         !File(projectDir, "build/generated/source/translationtools/commonMain/kotlin/com/example/translations/Res.kt").exists()
      )
   }

   @Test
   fun jvmSourcesJar_should_depend_on_generateTranslationResources()
   {
      val projectDir = createTempDirectory("translationtools-functional").toFile()
      writeBuildFiles(projectDir)
       File(projectDir, "translationtools.yaml").writeText(
            """
            apiKey: test-key
            defaultLocale: en
            locales:
              - en
            generated:
              packageName: com.example.translations
              objectName: Res
           """.trimIndent()
       )
       File(projectDir, "snapshot.json").writeText(
          """
          {
           "schemaVersion": 1,
           "project": {
             "defaultLocale": "en",
             "locales": ["en"]
           },
           "translations": {
             "en": {
               "home.title": "Home"
              }
            }
          }
          """.trimIndent()
      )

      val result = GradleRunner.create()
         .withProjectDir(projectDir)
         .withPluginClasspath()
         .withArguments("jvmSourcesJar")
         .build()

      assertEquals(TaskOutcome.SUCCESS, result.task(":generateTranslationResources")?.outcome)
      assertEquals(TaskOutcome.SUCCESS, result.task(":jvmSourcesJar")?.outcome)
   }

   @Test
   fun initTranslationTools_should_create_starter_config()
   {
      val projectDir = createTempDirectory("translationtools-functional").toFile()
      writeBuildFiles(projectDir)

      val result = GradleRunner.create()
         .withProjectDir(projectDir)
         .withPluginClasspath()
         .withArguments("initTranslationTools")
         .build()

      assertEquals(TaskOutcome.SUCCESS, result.task(":initTranslationTools")?.outcome)
      assertTrue(File(projectDir, "translationtools.yaml").exists())
   }

   @Test
   fun migrateTranslations_should_fail_when_config_is_missing()
   {
      val projectDir = createTempDirectory("translationtools-functional").toFile()
      writeBuildFiles(projectDir)

      val result = GradleRunner.create()
         .withProjectDir(projectDir)
         .withPluginClasspath()
         .withArguments("migrateTranslations")
         .buildAndFail()

      assertTrue(result.output.contains("initTranslationTools"))
   }
}

private fun writeBuildFiles(projectDir: File)
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
         id("org.jetbrains.kotlin.multiplatform") version "1.9.25"
         id("io.mvdm.translationtools.plugin")
      }

      kotlin {
         jvm()
      }

      """.trimIndent()
    )
}
