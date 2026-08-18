package io.mvdm.translationtools.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeneratedEnabledFunctionalTests
{
   @Test
   fun kmp_with_generated_enabled_false_can_sync_and_compile_without_translations()
   {
      MockTranslationToolsServer(
         projectResponse = """{"locales":["en"],"defaultLocale":"en"}""",
         localeResponses = mapOf(
            "en" to """[{"origin":":/strings.xml","key":"home_title","value":"Home"}]""",
         ),
      ).use { server ->
         val projectDir = createTempDirectory("translationtools-sync-only-kmp").toFile()
         writeBuildFiles(projectDir)
         writeSyncOnlyKmpFixtures(projectDir, enabled = false)
         writeCommonMainStub(projectDir)

         val push = runTask(projectDir, "pushTranslations", "-Ptranslationtools.baseUrl=${server.baseUrl}")
         assertEquals(TaskOutcome.SUCCESS, push.task(":pushTranslations")?.outcome)
         assertTrue(server.pushBodies.isNotEmpty())

         val pull = runTask(projectDir, "pullTranslations", "-Ptranslationtools.baseUrl=${server.baseUrl}")
         assertEquals(TaskOutcome.SUCCESS, pull.task(":pullTranslations")?.outcome)
         assertNull(pull.task(":generateTranslationResources"))

         val compile = runTask(projectDir, "compileKotlinJvm")
         assertEquals(TaskOutcome.SUCCESS, compile.task(":compileKotlinJvm")?.outcome)
         assertNull(compile.task(":generateTranslationResources"))
         assertTrue(!generatedTranslationsFile(projectDir).exists())
      }
   }

   @Test
   fun kmp_with_generated_enabled_omitted_still_wires_generate_into_compile()
   {
      val projectDir = createTempDirectory("translationtools-enabled-omitted").toFile()
      writeBuildFiles(projectDir)
      writeSyncOnlyKmpFixtures(projectDir, enabled = null)
      writeCommonMainStub(projectDir)

      val dryRun = runTask(projectDir, "compileKotlinJvm", "--dry-run")
      assertTrue(dryRun.output.contains(":generateTranslationResources"))
   }

   @Test
   fun kmp_with_generated_enabled_true_still_wires_generate_into_compile()
   {
      val projectDir = createTempDirectory("translationtools-enabled-true").toFile()
      writeBuildFiles(projectDir)
      writeSyncOnlyKmpFixtures(projectDir, enabled = true)
      writeCommonMainStub(projectDir)

      val dryRun = runTask(projectDir, "compileKotlinJvm", "--dry-run")
      assertTrue(dryRun.output.contains(":generateTranslationResources"))
   }

   @Test
   fun pull_with_generated_enabled_false_does_not_run_generate()
   {
      MockTranslationToolsServer().use { server ->
         val projectDir = createTempDirectory("translationtools-pull-no-generate").toFile()
         writeBuildFiles(projectDir)
         writeSyncOnlyKmpFixtures(projectDir, enabled = false)

         val result = runTask(projectDir, "pullTranslations", "-Ptranslationtools.baseUrl=${server.baseUrl}")
         assertEquals(TaskOutcome.SUCCESS, result.task(":pullTranslations")?.outcome)
         assertNull(result.task(":generateTranslationResources"))
         assertTrue(!generatedTranslationsFile(projectDir).exists())
      }
   }

   @Test
   fun generate_stays_registered_and_works_when_generated_enabled_is_false()
   {
      val projectDir = createTempDirectory("translationtools-generate-by-hand").toFile()
      writeBuildFiles(projectDir)
      writeSyncOnlyKmpFixtures(projectDir, enabled = false)

      val tasks = runTask(projectDir, "tasks", "--all")
      assertTrue(tasks.output.contains("generateTranslationResources"))

      val result = runTask(projectDir, "generateTranslationResources")
      assertEquals(TaskOutcome.SUCCESS, result.task(":generateTranslationResources")?.outcome)
      assertTrue(generatedTranslationsFile(projectDir).exists())
      assertTrue(generatedTranslationsFile(projectDir).readText().contains("package com.example.translations"))
   }

   @Test
   fun non_kmp_module_still_gets_push_and_pull_without_extra_flag()
   {
      val projectDir = createTempDirectory("translationtools-non-kmp-tasks").toFile()
      writeSyncOnlyBuildFiles(projectDir)
      writeStandardTestFixtures(projectDir)

      val result = runTask(projectDir, "tasks", "--all")
      assertTrue(result.output.contains("pushTranslations"))
      assertTrue(result.output.contains("pullTranslations"))
      assertTrue(result.output.contains("initTranslationTools"))
      assertTrue(result.output.contains("generateTranslationResources"))
   }

   @Test
   fun init_starter_yaml_does_not_set_generated_enabled()
   {
      val projectDir = createTempDirectory("translationtools-init-enabled").toFile()
      writeBuildFiles(projectDir)

      val result = runTask(projectDir, "initTranslationTools")
      assertEquals(TaskOutcome.SUCCESS, result.task(":initTranslationTools")?.outcome)
      val yaml = File(projectDir, "translationtools.yaml").readText()
      assertTrue(yaml.contains("packageName:"))
      assertTrue(!yaml.contains("enabled"))
   }

   private fun writeSyncOnlyKmpFixtures(projectDir: File, enabled: Boolean?)
   {
      File(projectDir, "translationtools.yaml").writeText(
         buildString {
            appendLine("apiKey: test-key")
            appendLine("defaultLocale: en")
            appendLine("locales:")
            appendLine("  - en")
            appendLine("generated:")
            if (enabled != null)
               appendLine("  enabled: $enabled")
            appendLine("  packageName: com.example.translations")
            appendLine("androidResources:")
            appendLine("  resourceDirectories:")
            appendLine("    - src/androidMain/res")
         },
      )
      File(projectDir, "src/androidMain/res/values").mkdirs()
      File(projectDir, "src/androidMain/res/values/strings.xml").writeText(
         """
         <?xml version="1.0" encoding="utf-8"?>
         <resources>
            <string name="home_title">Home</string>
         </resources>
         """.trimIndent(),
      )
   }

   private fun writeCommonMainStub(projectDir: File)
   {
      File(projectDir, "src/commonMain/kotlin").mkdirs()
      File(projectDir, "src/commonMain/kotlin/Hello.kt").writeText("fun hello() = \"hi\"\n")
   }

   private fun generatedTranslationsFile(projectDir: File) =
      File(projectDir, "build/generated/source/translationtools/commonMain/kotlin/com/example/translations/Translations.kt")

   private fun runTask(projectDir: File, vararg args: String) =
      GradleRunner.create()
         .withProjectDir(projectDir)
         .withPluginClasspath()
         .withArguments(*args)
         .build()
}
