package io.mvdm.translationtools.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
   fun push_and_pull_with_a_string_catalog_should_send_its_values_and_write_pulled_plurals_under_configuration_cache()
   {
      MockTranslationToolsServer(
         pushResponse = """{"receivedKeyCount":2,"createdKeyCount":2,"updatedKeyCount":0,"removedKeyCount":0}""",
         localeResponses = mapOf(
            "en" to """[{"origin":":/Localizable.xcstrings","key":"cart_items","value":"{count, plural, one {# item} other {# things}}"}]""",
         ),
      ).use { server ->
         val projectDir = createTempDirectory("translationtools-catalog-cc").toFile()
         writeNonKmpBuildFiles(projectDir)
         writeStandardTestFixtures(projectDir, appleResourceDirectories = listOf("ios"))
         val catalog = File(projectDir, "ios/Localizable.xcstrings")
         catalog.parentFile.mkdirs()
         catalog.writeText(xcodeLayoutFixture())

         val push = runWithConfigurationCache(projectDir, "pushTranslations", server.baseUrl)
         assertEquals(TaskOutcome.SUCCESS, push.task(":pushTranslations")?.outcome)
         val sent = Json.parseToJsonElement(server.pushBodies.last()).jsonObject.getValue("items").jsonArray
            .filter { it.jsonObject.getValue("origin").jsonPrimitive.content == ":/Localizable.xcstrings" }
            .associate {
               val item = it.jsonObject
               (item.getValue("key").jsonPrimitive.content + "/" + item.getValue("locale").jsonPrimitive.content) to
                  item.getValue("value").let { value -> if (value is JsonNull) null else value.jsonPrimitive.content }
            }
         assertEquals(
            mapOf(
               "Done/en" to "Done",
               "cart_items/en" to "{count, plural, one {# item} other {# items}}",
               "device_hint/en" to null,
               "file_count/en" to "{files, plural, one {# file} other {# files}}",
               "greeting/en" to "Hello",
               "path/with slash/en" to "Open \"files\" in a/b \\ c",
               "Done/pl" to "Gotowe",
               "cart_items/pl" to "{count, plural, one {# rzecz} few {# rzeczy} many {# rzeczy} other {# rzeczy}}",
               "device_hint/pl" to null,
               "file_count/pl" to null,
               "greeting/pl" to null,
               "path/with slash/pl" to "Otwórz pliki – zażółć gęślą jaźń",
            ),
            sent,
         )

         val first = runWithConfigurationCache(projectDir, "pullTranslations", server.baseUrl)
         assertEquals(TaskOutcome.SUCCESS, first.task(":pullTranslations")?.outcome)
         assertEquals(xcodeLayoutFixture().replace("\"value\" : \"%lld items\"", "\"value\" : \"%lld things\""), catalog.readText())
         val written = catalog.readBytes()

         val second = runWithConfigurationCache(projectDir, "pullTranslations", server.baseUrl)
         assertEquals(TaskOutcome.SUCCESS, second.task(":pullTranslations")?.outcome)
         assertTrue(second.output.contains("Reusing configuration cache."), second.output)
         assertTrue(written.contentEquals(catalog.readBytes()))
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
