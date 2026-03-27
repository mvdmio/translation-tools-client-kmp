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
         locales:
           - en
         snapshotFile: translationtools/snapshot.json
         generated:
           packageName: com.example.translations
           objectName: Res
         """.trimIndent()
      )
      File(projectDir, "translationtools").mkdirs()
      File(projectDir, "translationtools/snapshot.json").writeText(
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
      assertTrue(generated.exists())
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
}

private fun writeBuildFiles(projectDir: File)
{
   File(projectDir, "settings.gradle.kts").writeText("")
   File(projectDir, "build.gradle.kts").writeText(
      """
      plugins {
         id("org.jetbrains.kotlin.multiplatform") version "1.9.25"
         id("io.mvdm.translationtools.plugin")
      }

      kotlin {
         jvm()
      }

      translationTools {
         configFile.set(layout.projectDirectory.file("translationtools.yaml"))
      }
      """.trimIndent()
   )
}
