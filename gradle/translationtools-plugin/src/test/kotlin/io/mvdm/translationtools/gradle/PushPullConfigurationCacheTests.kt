package io.mvdm.translationtools.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushPullConfigurationCacheTests
{
   @Test
   fun pushTranslations_should_succeed_and_reuse_configuration_cache()
   {
      MockTranslationToolsServer().use { server ->
         val projectDir = createTempDirectory("translationtools-push-cc").toFile()
         writeNonKmpBuildFiles(projectDir)
         writeStandardTestFixtures(projectDir)

         val first = runWithConfigurationCache(projectDir, "pushTranslations", server.baseUrl)
         assertEquals(TaskOutcome.SUCCESS, first.task(":pushTranslations")?.outcome)
         assertTrue(
            first.output.contains("Configuration cache entry stored") ||
               first.output.contains("Calculating task graph as no cached configuration is available"),
            first.output,
         )
         assertTrue(server.pushBodies.isNotEmpty())
         assertTrue(server.pushBodies.last().contains("home_title"))

         val second = runWithConfigurationCache(projectDir, "pushTranslations", server.baseUrl)
         assertEquals(TaskOutcome.SUCCESS, second.task(":pushTranslations")?.outcome)
         assertTrue(second.output.contains("Reusing configuration cache."), second.output)
      }
   }

   @Test
   fun pullTranslations_should_succeed_and_reuse_configuration_cache()
   {
      MockTranslationToolsServer(
         projectResponse = """{"locales":["en","nl"],"defaultLocale":"en"}""",
         localeResponses = mapOf(
            "en" to """[{"origin":":/strings.xml","key":"home_title","value":"Home"}]""",
            "nl" to """[{"origin":":/strings.xml","key":"home_title","value":"Start"}]""",
         ),
      ).use { server ->
         val projectDir = createTempDirectory("translationtools-pull-cc").toFile()
         writeNonKmpBuildFiles(projectDir)
         writeStandardTestFixtures(projectDir, locales = listOf("en", "nl"))

         val first = runWithConfigurationCache(projectDir, "pullTranslations", server.baseUrl)
         assertEquals(TaskOutcome.SUCCESS, first.task(":pullTranslations")?.outcome)
         val nlFile = File(projectDir, "src/androidMain/res/values-nl/strings.xml")
         assertTrue(nlFile.exists())
         assertTrue(nlFile.readText().contains("home_title"))
         assertTrue(nlFile.readText().contains("Start"))

         val second = runWithConfigurationCache(projectDir, "pullTranslations", server.baseUrl)
         assertEquals(TaskOutcome.SUCCESS, second.task(":pullTranslations")?.outcome)
         assertTrue(second.output.contains("Reusing configuration cache."), second.output)
      }
   }

   @Test
   fun pushTranslations_with_appleResources_should_upload_strings_under_configuration_cache()
   {
      MockTranslationToolsServer(
         pushResponse = """{"receivedKeyCount":2,"createdKeyCount":2,"updatedKeyCount":0,"removedKeyCount":0}""",
      ).use { server ->
         val projectDir = createTempDirectory("translationtools-push-apple-cc").toFile()
         writeNonKmpBuildFiles(projectDir)
         writeStandardTestFixtures(projectDir, appleResourceDirectories = listOf("ios"))
         File(projectDir, "ios/en.lproj").mkdirs()
         File(projectDir, "ios/en.lproj/InfoPlist.strings").writeText(
            "\"NSCameraUsageDescription\" = \"Camera\";",
         )
         File(projectDir, "ios/en.lproj/Localizable.stringsdict").writeText(
            stringsdictPlist(pluralEntry("cart_items", "items", "d", "one" to "%d item", "other" to "%d items")),
         )

         val result = runWithConfigurationCache(projectDir, "pushTranslations", server.baseUrl)
         assertEquals(TaskOutcome.SUCCESS, result.task(":pushTranslations")?.outcome)
         assertTrue(server.pushBodies.isNotEmpty())
         assertTrue(server.pushBodies.last().contains("InfoPlist.strings"))
         assertTrue(server.pushBodies.last().contains("NSCameraUsageDescription"))
         assertTrue(server.pushBodies.last().contains(":/Localizable.stringsdict"))
         assertTrue(server.pushBodies.last().contains("{items, plural, one {# item} other {# items}}"))
      }
   }

   @Test
   fun pullTranslations_with_appleResources_should_write_stringsdict_plurals_under_configuration_cache()
   {
      MockTranslationToolsServer(
         localeResponses = mapOf(
            "en" to """[{"origin":":/Localizable.stringsdict","key":"cart_items","value":"{items, plural, one {# item} other {# things}}"}]""",
         ),
      ).use { server ->
         val projectDir = createTempDirectory("translationtools-pull-apple-cc").toFile()
         writeNonKmpBuildFiles(projectDir)
         writeStandardTestFixtures(projectDir, appleResourceDirectories = listOf("ios"))
         val stringsdict = File(projectDir, "ios/en.lproj/Localizable.stringsdict")
         stringsdict.parentFile.mkdirs()
         stringsdict.writeText(
            stringsdictPlist(pluralEntry("cart_items", "items", "d", "one" to "%d item", "other" to "%d items")),
         )

         val first = runWithConfigurationCache(projectDir, "pullTranslations", server.baseUrl)
         assertEquals(TaskOutcome.SUCCESS, first.task(":pullTranslations")?.outcome)
         assertTrue(stringsdict.readText().contains("\t\t\t<string>%d things</string>\n"))
         val written = stringsdict.readBytes()

         val second = runWithConfigurationCache(projectDir, "pullTranslations", server.baseUrl)
         assertEquals(TaskOutcome.SUCCESS, second.task(":pullTranslations")?.outcome)
         assertTrue(second.output.contains("Reusing configuration cache."), second.output)
         assertTrue(written.contentEquals(stringsdict.readBytes()))
      }
   }

   @Test
   fun changing_translationtools_yaml_invalidates_configuration_cache()
   {
      MockTranslationToolsServer().use { server ->
         val projectDir = createTempDirectory("translationtools-yaml-cc").toFile()
         writeNonKmpBuildFiles(projectDir)
         writeStandardTestFixtures(projectDir)

         val first = runWithConfigurationCache(projectDir, "pushTranslations", server.baseUrl)
         assertEquals(TaskOutcome.SUCCESS, first.task(":pushTranslations")?.outcome)

         File(projectDir, "translationtools.yaml").appendText("\n# cache-bust\n")

         val second = runWithConfigurationCache(projectDir, "pushTranslations", server.baseUrl)
         assertEquals(TaskOutcome.SUCCESS, second.task(":pushTranslations")?.outcome)
         assertTrue(second.output.contains("Calculating task graph"), second.output)
         assertTrue(!second.output.contains("Reusing configuration cache."), second.output)
      }
   }

   private fun runWithConfigurationCache(projectDir: File, taskName: String, baseUrl: String) =
      GradleRunner.create()
         .withProjectDir(projectDir)
         .withPluginClasspath()
         .withArguments(
            taskName,
            "--configuration-cache",
            "-Ptranslationtools.baseUrl=$baseUrl",
         )
         .build()
}
