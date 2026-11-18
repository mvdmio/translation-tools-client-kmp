package io.mvdm.translationtools.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceholderGlobalsFunctionalTests
{
   private val generatedPath = "build/generated/source/translationtools/commonMain/kotlin/com/example/translations/Translations.kt"
   private val bundledPath = "build/generated/source/translationtools/commonMain/kotlin/com/example/translations/TranslationsBundledSnapshot.kt"

   @Test
   fun generateTranslationResources_should_succeed_with_valid_globals()
   {
      val projectDir = createTempDirectory("translationtools-globals").toFile()
      writeBuildFiles(projectDir)
      writeStandardTestFixtures(projectDir, placeholderGlobals = listOf("appName", "brand2"))

      val result = runner(projectDir, "generateTranslationResources").build()

      assertEquals(TaskOutcome.SUCCESS, result.task(":generateTranslationResources")?.outcome)
      assertTrue(File(projectDir, generatedPath).readText().contains("val home_title"))
   }

   @Test
   fun generateTranslationResources_should_fail_on_an_invalid_global_and_keep_the_previous_output()
   {
      val projectDir = createTempDirectory("translationtools-globals").toFile()
      writeBuildFiles(projectDir)
      writeStandardTestFixtures(projectDir, placeholderGlobals = listOf("appName"))
      runner(projectDir, "generateTranslationResources").build()
      val generated = File(projectDir, generatedPath)
      val bundled = File(projectDir, bundledPath)
      val generatedBefore = generated.readBytes()
      val bundledBefore = bundled.readBytes()

      writeStandardTestFixtures(projectDir, placeholderGlobals = listOf("appName", "AppName", "bad-name"))
      File(projectDir, "src/androidMain/res/values/strings.xml").writeText(
         """
         <?xml version="1.0" encoding="utf-8"?>
         <resources>
            <string name="home_title">Home</string>
            <string name="other_title">Other</string>
         </resources>
         """.trimIndent(),
      )

      val result = runner(projectDir, "generateTranslationResources").buildAndFail()

      assertTrue(
         result.output.contains(
            "'placeholders.globals' in translationtools.yaml holds 'AppName', which is not a valid Placeholder name. " +
               "A Placeholder name is a lowercase letter followed by letters and digits.",
         ),
         result.output,
      )
      assertTrue(generatedBefore.contentEquals(generated.readBytes()))
      assertTrue(bundledBefore.contentEquals(bundled.readBytes()))
   }

   @Test
   fun generateTranslationResources_should_name_the_first_invalid_entry_before_the_missing_resources_check()
   {
      val projectDir = createTempDirectory("translationtools-globals").toFile()
      writeBuildFiles(projectDir)
      File(projectDir, "translationtools.yaml").writeText(
         "apiKey: test-key\ndefaultLocale: en\ngenerated:\n  packageName: com.example.translations\nplaceholders:\n  globals:\n    - app_name\n",
      )

      val result = runner(projectDir, "generateTranslationResources").buildAndFail()

      assertTrue(result.output.contains("holds 'app_name', which is not a valid Placeholder name"), result.output)
      assertTrue(!result.output.contains("No Android XML string resources found"), result.output)
   }

   @Test
   fun pushTranslations_should_fail_when_placeholders_is_not_a_map()
   {
      val projectDir = createTempDirectory("translationtools-globals").toFile()
      writeNonKmpBuildFiles(projectDir)
      writeStandardTestFixtures(projectDir)
      File(projectDir, "translationtools.yaml").appendText("placeholders: appName\n")

      val result = runner(projectDir, "pushTranslations").buildAndFail()

      assertTrue(result.output.contains("'placeholders' in "), result.output)
      assertTrue(result.output.contains("must be a YAML map with a 'globals' list."), result.output)
   }

   @Test
   fun pushTranslations_should_fail_when_globals_is_a_string()
   {
      val projectDir = createTempDirectory("translationtools-globals").toFile()
      writeNonKmpBuildFiles(projectDir)
      writeStandardTestFixtures(projectDir)
      File(projectDir, "translationtools.yaml").appendText("placeholders:\n  globals: appName\n")

      val result = runner(projectDir, "pushTranslations").buildAndFail()

      assertTrue(result.output.contains("'placeholders.globals' in "), result.output)
      assertTrue(result.output.contains("must be a YAML list of strings."), result.output)
   }

   @Test
   fun pushTranslations_should_accept_an_invalid_placeholder_name_in_globals()
   {
      MockTranslationToolsServer().use { server ->
         val projectDir = createTempDirectory("translationtools-globals").toFile()
         writeNonKmpBuildFiles(projectDir)
         writeStandardTestFixtures(projectDir, placeholderGlobals = listOf("AppName"))

         val result = runner(projectDir, "pushTranslations", "-Ptranslationtools.baseUrl=${server.baseUrl}").build()

         assertEquals(TaskOutcome.SUCCESS, result.task(":pushTranslations")?.outcome)
         assertTrue(server.pushBodies.last().contains("home_title"))
      }
   }

   @Test
   fun pullTranslations_should_pull_with_an_invalid_placeholder_name_and_leave_the_name_check_to_generation()
   {
      MockTranslationToolsServer(
         projectResponse = """{"locales":["en","nl"],"defaultLocale":"en"}""",
         localeResponses = mapOf(
            "en" to """[{"origin":":/strings.xml","key":"home_title","value":"Home"}]""",
            "nl" to """[{"origin":":/strings.xml","key":"home_title","value":"Start"}]""",
         ),
      ).use { server ->
         val projectDir = createTempDirectory("translationtools-globals").toFile()
         writeNonKmpBuildFiles(projectDir)
         writeStandardTestFixtures(projectDir, locales = listOf("en", "nl"), placeholderGlobals = listOf("AppName"))

         // pullTranslations is finalized by generateTranslationResources, which is the one task that checks the names.
         val result = runner(projectDir, "pullTranslations", "-Ptranslationtools.baseUrl=${server.baseUrl}").buildAndFail()

         assertEquals(TaskOutcome.SUCCESS, result.task(":pullTranslations")?.outcome)
         assertEquals(TaskOutcome.FAILED, result.task(":generateTranslationResources")?.outcome)
         assertTrue(result.output.contains("holds 'AppName', which is not a valid Placeholder name"), result.output)
         assertTrue(File(projectDir, "src/androidMain/res/values-nl/strings.xml").readText().contains("Start"))
      }
   }

   @Test
   fun generateTranslationResources_should_reuse_configuration_cache_with_globals()
   {
      val projectDir = createTempDirectory("translationtools-globals-cc").toFile()
      writeBuildFiles(projectDir)
      writeStandardTestFixtures(projectDir, placeholderGlobals = listOf("appName", "brand"))

      val first = runner(projectDir, "generateTranslationResources", "--configuration-cache").build()
      assertEquals(TaskOutcome.SUCCESS, first.task(":generateTranslationResources")?.outcome)

      val second = runner(projectDir, "generateTranslationResources", "--configuration-cache").build()
      assertTrue(second.output.contains("Reusing configuration cache."), second.output)
   }

   private fun runner(projectDir: File, vararg arguments: String) =
      GradleRunner.create()
         .withProjectDir(projectDir)
         .withPluginClasspath()
         .withArguments(*arguments)
}
