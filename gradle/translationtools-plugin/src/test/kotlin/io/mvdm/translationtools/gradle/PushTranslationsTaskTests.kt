package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushTranslationsTaskTests
{
   @Test
   fun push_should_upload_both_android_and_apple_strings()
   {
      val projectDir = createTempDirectory("translationtools-push-apple").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText(
         "<resources><string name=\"home_title\">Home</string></resources>"
      )
      File(projectDir, "ios/en.lproj").mkdirs()
      File(projectDir, "ios/en.lproj/InfoPlist.strings").writeText(
         "\"NSCameraUsageDescription\" = \"Camera\";"
      )

      var capturedBody = ""
      val client = createHttpClient(
         MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
               content = """{"receivedKeyCount":2,"createdKeyCount":2,"updatedKeyCount":0,"removedKeyCount":0}""",
               status = HttpStatusCode.OK,
               headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
         }
      )

      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val task = project.tasks.create("pushTranslations", PushTranslationsTask::class.java)
      task.apiKey.set("test-key")
      task.defaultLocale.set("en")
      task.resourceDirectories.from(File(projectDir, "res"))
      task.appleResourceDirectories.from(File(projectDir, "ios"))
      task.keyOverrides.set(emptyMap())
      task.projectPathInput.set(project.path)
      task.baseUrl.set(BASE_URL)
      task.httpClientFactory = { client }

      task.push()

      assertTrue(capturedBody.contains(":/strings.xml"))
      assertTrue(capturedBody.contains("home_title"))
      assertTrue(capturedBody.contains(":/InfoPlist.strings"))
      assertTrue(capturedBody.contains("NSCameraUsageDescription"))
   }


   @Test
   fun push_should_send_stringsdict_plurals_next_to_strings_items_and_skip_what_cannot_sync()
   {
      val projectDir = createTempDirectory("translationtools-push-stringsdict").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText(
         "<resources><string name=\"home_title\">Home</string></resources>"
      )
      File(projectDir, "ios/en.lproj").mkdirs()
      File(projectDir, "ios/pl.lproj").mkdirs()
      File(projectDir, "ios/en.lproj/Localizable.strings").writeText("\"cart_items\" = \"Cart\";")
      File(projectDir, "ios/en.lproj/Localizable.stringsdict").writeText(
         stringsdictPlist(
            pluralEntry("cart_items", "items", "d", "one" to "%d item", "other" to "%d items"),
            pluralEntry("greeting", "items", "d", "other" to "%d items", format = "You have %#@items@"),
         )
      )
      File(projectDir, "ios/pl.lproj/Localizable.stringsdict").writeText("bplist00 not xml")

      var capturedBody = ""
      val client = createHttpClient(
         MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
               content = """{"receivedKeyCount":3,"createdKeyCount":3,"updatedKeyCount":0,"removedKeyCount":0}""",
               status = HttpStatusCode.OK,
               headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
         }
      )

      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val task = project.tasks.create("pushTranslations", PushTranslationsTask::class.java)
      task.apiKey.set("test-key")
      task.defaultLocale.set("en")
      task.resourceDirectories.from(File(projectDir, "res"))
      task.appleResourceDirectories.from(File(projectDir, "ios"))
      task.keyOverrides.set(emptyMap())
      task.projectPathInput.set(project.path)
      task.baseUrl.set(BASE_URL)
      task.httpClientFactory = { client }

      task.push()

      val sent = Json.parseToJsonElement(capturedBody).jsonObject.getValue("items").jsonArray
         .map { it.jsonObject }
         .map { Triple(it.getValue("origin").jsonPrimitive.content, it.getValue("key").jsonPrimitive.content, it.getValue("value").jsonPrimitive.content) }
      assertEquals(
         listOf(
            Triple(":/Localizable.strings", "cart_items", "Cart"),
            Triple(":/Localizable.stringsdict", "cart_items", "{items, plural, one {# item} other {# items}}"),
            Triple(":/strings.xml", "home_title", "Home"),
         ),
         sent,
      )
   }

   @Test
   fun push_should_send_string_catalog_values_as_their_own_origin_next_to_strings_and_android_items()
   {
      val projectDir = createTempDirectory("translationtools-push-xcstrings").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText(
         "<resources><string name=\"home_title\">Home</string></resources>"
      )
      File(projectDir, "ios/en.lproj").mkdirs()
      File(projectDir, "ios/en.lproj/Localizable.strings").writeText("\"Done\" = \"Finished\";")
      File(projectDir, "ios/Localizable.xcstrings").writeText(
         """
         {
           "sourceLanguage" : "en",
           "strings" : {
             "Done" : {
               "localizations" : {
                 "pl" : { "stringUnit" : { "state" : "translated", "value" : "Gotowe" } }
               }
             },
             "greeting" : {
               "comment" : "Shown on the home screen",
               "localizations" : {
                 "en" : { "stringUnit" : { "state" : "needs_review", "value" : "Hello" } }
               }
             },
             "brand" : {
               "shouldTranslate" : false,
               "localizations" : {
                 "en" : { "stringUnit" : { "state" : "translated", "value" : "Acme" } }
               }
             }
           },
           "version" : "1.0"
         }
         """.trimIndent()
      )

      var capturedBody = ""
      val client = createHttpClient(
         MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
               content = """{"receivedKeyCount":7,"createdKeyCount":7,"updatedKeyCount":0,"removedKeyCount":0}""",
               status = HttpStatusCode.OK,
               headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
         }
      )

      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val task = project.tasks.create("pushTranslations", PushTranslationsTask::class.java)
      task.apiKey.set("test-key")
      task.defaultLocale.set("en")
      task.resourceDirectories.from(File(projectDir, "res"))
      task.appleResourceDirectories.from(File(projectDir, "ios"))
      task.keyOverrides.set(emptyMap())
      task.projectPathInput.set(project.path)
      task.baseUrl.set(BASE_URL)
      task.httpClientFactory = { client }

      task.push()

      val sent = Json.parseToJsonElement(capturedBody).jsonObject.getValue("items").jsonArray
         .map { it.jsonObject }
         .map { item ->
            listOf("origin", "locale", "key").joinToString("|") { item.getValue(it).jsonPrimitive.content } to
               item.getValue("value").jsonPrimitive.contentOrNull
         }
      assertEquals(
         listOf(
            ":/Localizable.strings|en|Done" to "Finished",
            ":/Localizable.strings|pl|Done" to null,
            ":/Localizable.xcstrings|en|Done" to "Done",
            ":/Localizable.xcstrings|en|greeting" to "Hello",
            ":/Localizable.xcstrings|pl|Done" to "Gotowe",
            ":/Localizable.xcstrings|pl|greeting" to null,
            ":/strings.xml|en|home_title" to "Home",
         ),
         sent,
      )
   }

   @Test
   fun push_should_send_string_catalog_plurals_in_both_shapes_and_null_for_a_skipped_value_only()
   {
      val projectDir = createTempDirectory("translationtools-push-xcstrings-plurals").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText("<resources></resources>")
      File(projectDir, "ios").mkdirs()
      File(projectDir, "ios/Localizable.xcstrings").writeText(
         """
         {
           "sourceLanguage" : "en",
           "strings" : {
             "cart_items" : {
               "localizations" : {
                 "en" : { "variations" : { "plural" : {
                   "other" : { "stringUnit" : { "state" : "translated", "value" : "%lld items" } },
                   "one" : { "stringUnit" : { "state" : "translated", "value" : "%lld item" } }
                 } } },
                 "pl" : { "variations" : { "plural" : {
                   "other" : { "stringUnit" : { "state" : "translated", "value" : "%lld rzeczy" } },
                   "many" : { "stringUnit" : { "state" : "translated", "value" : "%lld rzeczy" } },
                   "few" : { "stringUnit" : { "state" : "translated", "value" : "%lld rzeczy" } },
                   "one" : { "stringUnit" : { "state" : "translated", "value" : "%lld rzecz" } }
                 } } }
               }
             },
             "migrated" : {
               "localizations" : {
                 "en" : {
                   "stringUnit" : { "state" : "translated", "value" : "%#@items@" },
                   "substitutions" : { "items" : { "formatSpecifier" : "d", "variations" : { "plural" : {
                     "one" : { "stringUnit" : { "state" : "translated", "value" : "%arg item" } },
                     "other" : { "stringUnit" : { "state" : "translated", "value" : "%d items" } }
                   } } } }
                 }
               }
             },
             "mixed" : {
               "localizations" : {
                 "en" : {
                   "stringUnit" : { "state" : "translated", "value" : "You have %#@items@" },
                   "substitutions" : { "items" : { "formatSpecifier" : "d", "variations" : { "plural" : {
                     "other" : { "stringUnit" : { "state" : "translated", "value" : "%d items" } }
                   } } } }
                 },
                 "pl" : { "stringUnit" : { "state" : "translated", "value" : "Mieszane" } }
               }
             }
           },
           "version" : "1.0"
         }
         """.trimIndent()
      )

      var capturedBody = ""
      val client = createHttpClient(
         MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
               content = """{"receivedKeyCount":6,"createdKeyCount":6,"updatedKeyCount":0,"removedKeyCount":0}""",
               status = HttpStatusCode.OK,
               headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
         }
      )

      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val task = project.tasks.create("pushTranslations", PushTranslationsTask::class.java)
      task.apiKey.set("test-key")
      task.defaultLocale.set("en")
      task.resourceDirectories.from(File(projectDir, "res"))
      task.appleResourceDirectories.from(File(projectDir, "ios"))
      task.keyOverrides.set(emptyMap())
      task.projectPathInput.set(project.path)
      task.baseUrl.set(BASE_URL)
      task.httpClientFactory = { client }

      task.push()

      val sent = Json.parseToJsonElement(capturedBody).jsonObject.getValue("items").jsonArray
         .map { it.jsonObject }
         .map { item ->
            listOf("origin", "locale", "key").joinToString("|") { item.getValue(it).jsonPrimitive.content } to
               item.getValue("value").jsonPrimitive.contentOrNull
         }
      assertEquals(
         listOf(
            ":/Localizable.xcstrings|en|cart_items" to "{count, plural, one {# item} other {# items}}",
            ":/Localizable.xcstrings|en|migrated" to "{items, plural, one {# item} other {# items}}",
            ":/Localizable.xcstrings|en|mixed" to null,
            ":/Localizable.xcstrings|pl|cart_items" to "{count, plural, one {# rzecz} few {# rzeczy} many {# rzeczy} other {# rzeczy}}",
            ":/Localizable.xcstrings|pl|migrated" to null,
            ":/Localizable.xcstrings|pl|mixed" to "Mieszane",
         ),
         sent,
      )
   }

   @Test
   fun push_should_send_only_the_sorted_local_items_in_one_request_without_fetching()
   {
      val projectDir = createTempDirectory("translationtools-push-local-only").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText(
         "<resources><string name=\"b_title\">Bee</string><string name=\"a_title\">Ay</string></resources>"
      )

      val requests = mutableListOf<Pair<HttpMethod, String>>()
      var capturedBody = ""
      val client = createHttpClient(
         MockEngine { request ->
            requests += request.method to request.url.encodedPath
            capturedBody = (request.body as TextContent).text
            respond(
               content = """{"receivedKeyCount":2,"createdKeyCount":0,"updatedKeyCount":0,"removedKeyCount":0}""",
               status = HttpStatusCode.OK,
               headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
         }
      )

      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val task = project.tasks.create("pushTranslations", PushTranslationsTask::class.java)
      task.apiKey.set("test-key")
      task.defaultLocale.set("en")
      task.resourceDirectories.from(File(projectDir, "res"))
      task.keyOverrides.set(emptyMap())
      task.projectPathInput.set(project.path)
      task.baseUrl.set(BASE_URL)
      task.httpClientFactory = { client }

      task.push()

      assertEquals(listOf(HttpMethod.Post to "/api/v1/translations/project"), requests)
      val sentKeys = Json.parseToJsonElement(capturedBody).jsonObject.getValue("items").jsonArray
         .map { it.jsonObject.getValue("key").jsonPrimitive.content }
      assertEquals(listOf("a_title", "b_title"), sentKeys)
   }

   @Test
   fun push_should_warn_once_per_key_about_android_format_specifiers_and_send_values_unchanged()
   {
      MockTranslationToolsServer().use { server ->
         val projectDir = createFormatSpecifierProject(
            defaultStrings = """
               <string name="greeting">Hello %1${'$'}s</string>
               <string name="count">%1${'$'}s has %2${'$'}d items</string>
            """.trimIndent(),
            dutchStrings = """<string name="greeting">Hallo %1${'$'}s</string>""",
         )

         val result = runGradle(projectDir, "pushTranslations", server.baseUrl)

         assertEquals(TaskOutcome.SUCCESS, result.task(":pushTranslations")?.outcome)
         assertEquals(
            listOf(
               "'count' in ':/strings.xml' contains Android format specifier '%1${'$'}s', '%2${'$'}d' (locales: en). $WARNING_ADVICE",
               "'greeting' in ':/strings.xml' contains Android format specifier '%1${'$'}s' (locales: en, nl). $WARNING_ADVICE",
            ),
            result.output.lines().filter { it.contains("contains Android format specifier") },
         )
         val sentValues = Json.parseToJsonElement(server.pushBodies.single()).jsonObject.getValue("items").jsonArray
            .associate {
               val item = it.jsonObject
               (item.getValue("key").jsonPrimitive.content + "/" + item.getValue("locale").jsonPrimitive.content) to
                  item.getValue("value").jsonPrimitive.content
            }
         assertEquals("Hello %1${'$'}s", sentValues["greeting/en"])
         assertEquals("Hallo %1${'$'}s", sentValues["greeting/nl"])
         assertEquals("%1${'$'}s has %2${'$'}d items", sentValues["count/en"])
      }
   }

   @Test
   fun push_should_send_each_plurals_resource_as_one_plural_per_locale_and_keep_translatable_false_local()
   {
      MockTranslationToolsServer().use { server ->
         val projectDir = createFormatSpecifierProject(
            defaultStrings = """
               <plurals name="cart_items">
                  <item quantity="other"># items in {cartName}</item>
                  <item quantity="one"># item in {cartName}</item>
               </plurals>
               <plurals name="empty_items"></plurals>
               <plurals name="unit_count" translatable="false"><item quantity="other">#</item></plurals>
            """.trimIndent(),
            dutchStrings = """<plurals name="cart_items"><item quantity="other"># artikelen</item><item quantity="one"># artikel</item></plurals>""",
         )

         val result = runGradle(projectDir, "pushTranslations", server.baseUrl)

         assertEquals(TaskOutcome.SUCCESS, result.task(":pushTranslations")?.outcome)
         val sentValues = Json.parseToJsonElement(server.pushBodies.single()).jsonObject.getValue("items").jsonArray
            .associate {
               val item = it.jsonObject
               (item.getValue("key").jsonPrimitive.content + "/" + item.getValue("locale").jsonPrimitive.content) to
                  item.getValue("value").let { value -> if (value is kotlinx.serialization.json.JsonNull) null else value.jsonPrimitive.content }
            }
         assertEquals(
            mapOf(
               "cart_items/en" to "{count, plural, one {# item in {cartName}} other {# items in {cartName}}}",
               "cart_items/nl" to "{count, plural, one {# artikel} other {# artikelen}}",
               "empty_items/en" to null,
               "empty_items/nl" to null,
            ),
            sentValues,
         )
      }
   }

   @Test
   fun push_should_warn_about_android_format_specifiers_in_a_plurals_item_naming_the_key()
   {
      MockTranslationToolsServer().use { server ->
         val projectDir = createFormatSpecifierProject(
            defaultStrings = """<plurals name="cart_items"><item quantity="one">%d item</item><item quantity="other">%d items</item></plurals>""",
            dutchStrings = "",
         )

         val result = runGradle(projectDir, "pushTranslations", server.baseUrl)

         assertEquals(TaskOutcome.SUCCESS, result.task(":pushTranslations")?.outcome)
         assertEquals(
            listOf("'cart_items' in ':/strings.xml' contains Android format specifier '%d' (locales: en). $WARNING_ADVICE"),
            result.output.lines().filter { it.contains("contains Android format specifier") },
         )
      }
   }

   @Test
   fun push_should_not_warn_about_placeholders_and_escaped_percent()
   {
      MockTranslationToolsServer().use { server ->
         val projectDir = createFormatSpecifierProject(
            defaultStrings = """
               <string name="greeting">Hello {userName}</string>
               <string name="sale">50%% off</string>
            """.trimIndent(),
            dutchStrings = """<string name="greeting">Hallo {userName}</string>""",
         )

         val result = runGradle(projectDir, "pushTranslations", server.baseUrl)

         assertEquals(TaskOutcome.SUCCESS, result.task(":pushTranslations")?.outcome)
         assertFalse(result.output.contains("Android format specifier"), result.output)
      }
   }

   @Test
   fun push_should_not_warn_about_translatable_false_keys()
   {
      MockTranslationToolsServer().use { server ->
         val projectDir = createFormatSpecifierProject(
            defaultStrings = """<string name="build_label" translatable="false">Build %1${'$'}s</string>""",
            dutchStrings = "",
         )

         val result = runGradle(projectDir, "pushTranslations", server.baseUrl)

         assertEquals(TaskOutcome.SUCCESS, result.task(":pushTranslations")?.outcome)
         assertFalse(result.output.contains("Android format specifier"), result.output)
      }
   }

   @Test
   fun generateTranslationResources_should_not_warn_about_android_format_specifiers()
   {
      val projectDir = createFormatSpecifierProject(
         defaultStrings = """<string name="greeting">Hello %1${'$'}s</string>""",
         dutchStrings = """<string name="greeting">Hallo %1${'$'}s</string>""",
      )

      val result = runGradle(projectDir, "generateTranslationResources", "http://127.0.0.1:1")

      assertEquals(TaskOutcome.SUCCESS, result.task(":generateTranslationResources")?.outcome)
      assertFalse(result.output.contains("Android format specifier"), result.output)
   }
}

private const val WARNING_ADVICE =
   "The runtime client fills only {named} placeholders, so these show as-is when read through " +
      "TranslationToolsClient. Replace them with a placeholder such as {name}."

private fun createFormatSpecifierProject(defaultStrings: String, dutchStrings: String): File
{
   val projectDir = createTempDirectory("translationtools-format-specifier").toFile()
   writeNonKmpBuildFiles(projectDir)
   writeStandardTestFixtures(projectDir, locales = listOf("en", "nl"))
   File(projectDir, "src/androidMain/res/values/strings.xml")
      .writeText("<resources>\n$defaultStrings\n</resources>")
   File(projectDir, "src/androidMain/res/values-nl").mkdirs()
   File(projectDir, "src/androidMain/res/values-nl/strings.xml")
      .writeText("<resources>\n$dutchStrings\n</resources>")
   return projectDir
}

private fun runGradle(projectDir: File, taskName: String, baseUrl: String) =
   GradleRunner.create()
      .withProjectDir(projectDir)
      .withPluginClasspath()
      .withArguments(taskName, "-Ptranslationtools.baseUrl=$baseUrl")
      .build()

private fun createHttpClient(engine: MockEngine): HttpClient
{
   return HttpClient(engine) {
      install(ContentNegotiation) {
         json(Json { ignoreUnknownKeys = true })
      }
   }
}
