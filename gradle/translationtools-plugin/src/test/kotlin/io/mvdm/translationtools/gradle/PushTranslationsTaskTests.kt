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
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
   fun push_should_send_a_literal_hash_in_a_stringsdict_form_quoted_and_only_the_number_as_the_number()
   {
      val projectDir = createTempDirectory("translationtools-push-stringsdict-hash").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText("<resources></resources>")
      File(projectDir, "ios/en.lproj").mkdirs()
      File(projectDir, "ios/en.lproj/Localizable.stringsdict").writeText(
         stringsdictPlist(pluralEntry("order", "count", "d", "other" to "Order #%d"))
      )

      var capturedBody = ""
      val client = createHttpClient(
         MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
               content = """{"receivedKeyCount":1,"createdKeyCount":1,"updatedKeyCount":0,"removedKeyCount":0}""",
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
         .map { it.jsonObject.getValue("value").jsonPrimitive.content }
      assertEquals(listOf("{count, plural, other {Order '#'#}}"), sent)
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

      val server = FakeTranslationTools()
      val client = server.client

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

      val sent = Json.parseToJsonElement(server.pushBodies.single()).jsonObject.getValue("items").jsonArray
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

      val server = FakeTranslationTools()
      val client = server.client

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

      val sent = Json.parseToJsonElement(server.pushBodies.single()).jsonObject.getValue("items").jsonArray
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
   fun push_should_send_no_value_for_a_non_default_value_that_differs_from_translationtools()
   {
      val projectDir = createPushProject(
         "res/values/strings.xml" to androidStrings("home_title" to "Home"),
         "res/values-nl/strings.xml" to androidStrings("home_title" to "Start"),
      )
      val server = FakeTranslationTools(remoteByLocale = mapOf("nl" to remoteItems("home_title" to "Begin")))

      pushWith(projectDir, server)

      assertEquals(
         listOf(
            ":/strings.xml|en|home_title" to "Home",
            ":/strings.xml|nl|home_title" to null,
         ),
         sentValues(server.pushBodies.single()),
      )
   }

   @Test
   fun push_should_fill_a_non_default_value_translationtools_holds_empty_and_never_send_an_empty_string()
   {
      val projectDir = createPushProject(
         "res/values/strings.xml" to androidStrings("a" to "A", "b" to "B", "c" to "C", "d" to "D", "e" to "E"),
         "res/values-nl/strings.xml" to androidStrings("a" to "Aa", "b" to "Bb", "c" to "Cc", "d" to "", "e" to ""),
      )
      val server = FakeTranslationTools(
         remoteByLocale = mapOf(
            "nl" to """[
               {"origin":":/strings.xml","key":"a","value":null},
               {"origin":":/strings.xml","key":"b","value":""},
               {"origin":":/strings.xml","key":"d","value":"Dd remote"}
            ]""",
         ),
      )

      pushWith(projectDir, server)

      assertEquals(
         listOf(
            ":/strings.xml|nl|a" to "Aa",
            ":/strings.xml|nl|b" to "Bb",
            ":/strings.xml|nl|c" to "Cc",
            ":/strings.xml|nl|d" to null,
            ":/strings.xml|nl|e" to null,
         ),
         sentValues(server.pushBodies.single()).filter { it.first.contains("|nl|") },
      )
   }

   @Test
   fun push_should_send_no_value_for_a_non_default_value_equal_to_the_one_on_translationtools()
   {
      val projectDir = createPushProject(
         "res/values/strings.xml" to androidStrings("home_title" to "Home"),
         "res/values-nl/strings.xml" to androidStrings("home_title" to "Start"),
      )
      val server = FakeTranslationTools(remoteByLocale = mapOf("nl" to remoteItems("home_title" to "Start")))

      pushWith(projectDir, server)

      assertEquals(
         listOf(":/strings.xml|en|home_title" to "Home", ":/strings.xml|nl|home_title" to null),
         sentValues(server.pushBodies.single()),
      )
   }

   @Test
   fun push_should_still_send_a_default_locale_value_that_differs_from_translationtools_without_reading_that_locale()
   {
      val projectDir = createPushProject(
         "res/values/strings.xml" to androidStrings("home_title" to "Home"),
         "res/values-nl/strings.xml" to androidStrings("home_title" to "Start"),
      )
      val server = FakeTranslationTools(
         remoteByLocale = mapOf(
            "en" to remoteItems("home_title" to "Old home"),
            "nl" to remoteItems("home_title" to "Start"),
         ),
      )

      pushWith(projectDir, server)

      assertEquals(listOf("GET /api/v1/translations/nl", "POST /api/v1/translations/project"), server.requests)
      assertEquals("Home", sentValues(server.pushBodies.single()).toMap()[":/strings.xml|en|home_title"])
   }

   @Test
   fun push_should_never_send_a_key_only_translationtools_has_and_keep_the_sorted_order()
   {
      val projectDir = createPushProject(
         "res/values/strings.xml" to androidStrings("b_title" to "Bee", "a_title" to "Ay"),
         "res/values-nl/strings.xml" to androidStrings("b_title" to "Bee nl", "a_title" to "Ay nl"),
      )
      val server = FakeTranslationTools(remoteByLocale = mapOf("nl" to remoteItems("only_remote" to "Alleen", "a_title" to "Ay nl")))

      pushWith(projectDir, server)

      assertEquals(
         listOf(
            ":/strings.xml|en|a_title",
            ":/strings.xml|en|b_title",
            ":/strings.xml|nl|a_title",
            ":/strings.xml|nl|b_title",
         ),
         sentValues(server.pushBodies.single()).map { it.first },
      )
   }

   @Test
   fun push_should_make_only_the_upload_request_when_the_local_files_hold_only_the_default_locale()
   {
      val projectDir = createPushProject("res/values/strings.xml" to androidStrings("b_title" to "Bee", "a_title" to "Ay"))
      val server = FakeTranslationTools()

      pushWith(projectDir, server)

      assertEquals(listOf("POST /api/v1/translations/project"), server.requests)
      assertEquals(
         listOf(":/strings.xml|en|a_title" to "Ay", ":/strings.xml|en|b_title" to "Bee"),
         sentValues(server.pushBodies.single()),
      )
   }

   @Test
   fun push_should_fail_with_the_status_and_upload_nothing_when_the_read_gets_an_error_status()
   {
      val projectDir = createNlProject()
      val server = FakeTranslationTools(readStatus = HttpStatusCode.InternalServerError, readBody = "boom")

      val exception = assertFailsWith<GradleException> { pushWith(projectDir, server) }

      assertEquals("TranslationTools locale 'nl' request failed with status 500: boom", exception.message)
      assertEquals(listOf("GET /api/v1/translations/nl"), server.requests)
   }

   @Test
   fun push_should_fail_with_the_transport_error_when_the_read_cannot_connect()
   {
      val projectDir = createFormatSpecifierProject(
         defaultStrings = """<string name="home_title">Home</string>""",
         dutchStrings = """<string name="home_title">Start</string>""",
      )

      val result = GradleRunner.create()
         .withProjectDir(projectDir)
         .withPluginClasspath()
         .withArguments("pushTranslations", "-Ptranslationtools.baseUrl=http://127.0.0.1:1")
         .buildAndFail()

      assertEquals(TaskOutcome.FAILED, result.task(":pushTranslations")?.outcome)
      assertTrue(result.output.contains("TranslationTools locale 'nl' request failed: "), result.output)
      assertFalse(result.output.contains("Push complete."), result.output)
   }

   @Test
   fun push_should_fail_quoting_the_parse_error_and_upload_nothing_when_the_read_is_not_a_translation_list()
   {
      val projectDir = createNlProject()
      val server = FakeTranslationTools(readBody = "not json")

      val exception = assertFailsWith<GradleException> { pushWith(projectDir, server) }

      assertTrue(exception.message.orEmpty().startsWith("Failed to parse TranslationTools locale 'nl' response. "), exception.message)
      assertTrue(exception.message.orEmpty().contains("not json"), exception.message)
      assertEquals(listOf("GET /api/v1/translations/nl"), server.requests)
   }

   @Test
   fun push_should_treat_a_string_catalog_source_language_as_its_default_whatever_the_default_locale_setting_is()
   {
      val projectDir = createPushProject(
         "res/values/strings.xml" to androidStrings(),
         "ios/Localizable.xcstrings" to """
            {
              "sourceLanguage" : "de",
              "strings" : {
                "greeting" : {
                  "localizations" : {
                    "de" : { "stringUnit" : { "state" : "translated", "value" : "Hallo" } },
                    "en" : { "stringUnit" : { "state" : "translated", "value" : "Hello" } }
                  }
                }
              },
              "version" : "1.0"
            }
         """.trimIndent(),
      )
      val server = FakeTranslationTools(
         remoteByLocale = mapOf(
            "de" to """[{"origin":":/Localizable.xcstrings","key":"greeting","value":"Hallo alt"}]""",
            "en" to """[{"origin":":/Localizable.xcstrings","key":"greeting","value":"Hi"}]""",
         ),
      )

      pushWith(projectDir, server)

      assertEquals(listOf("GET /api/v1/translations/en", "POST /api/v1/translations/project"), server.requests)
      assertEquals(
         listOf(":/Localizable.xcstrings|de|greeting" to "Hallo", ":/Localizable.xcstrings|en|greeting" to null),
         sentValues(server.pushBodies.single()),
      )
   }

   @Test
   fun push_should_upload_this_exact_body_for_a_worked_example_of_equal_differing_and_new_values()
   {
      val projectDir = createPushProject(
         "res/values/strings.xml" to androidStrings("a_title" to "Ay", "b_title" to "Bee", "c_title" to "Cee"),
         "res/values-nl/strings.xml" to androidStrings("a_title" to "Aa", "b_title" to "Bee nl", "c_title" to "Cee nl"),
      )
      val server = FakeTranslationTools(
         remoteByLocale = mapOf(
            "nl" to remoteItems("a_title" to "Aa", "b_title" to "Bee vertaald", "z_title" to "Zed"),
         ),
      )

      pushWith(projectDir, server)

      assertEquals(
         """{"items":[""" +
            """{"origin":":/strings.xml","locale":"en","key":"a_title","value":"Ay"},""" +
            """{"origin":":/strings.xml","locale":"en","key":"b_title","value":"Bee"},""" +
            """{"origin":":/strings.xml","locale":"en","key":"c_title","value":"Cee"},""" +
            """{"origin":":/strings.xml","locale":"nl","key":"a_title","value":null},""" +
            """{"origin":":/strings.xml","locale":"nl","key":"b_title","value":null},""" +
            """{"origin":":/strings.xml","locale":"nl","key":"c_title","value":"Cee nl"}""" +
            """]}""",
         server.pushBodies.single(),
      )
   }

   @Test
   fun push_should_warn_with_the_exact_text_and_send_no_value_for_a_differing_non_default_value()
   {
      MockTranslationToolsServer(
         localeResponses = mapOf("nl" to remoteItems("home_title" to "Begin")),
      ).use { server ->
         val projectDir = createFormatSpecifierProject(
            defaultStrings = """<string name="home_title">Home</string>""",
            dutchStrings = """<string name="home_title">Start</string>""",
         )

         val result = runGradle(projectDir, "pushTranslations", server.baseUrl)

         assertEquals(TaskOutcome.SUCCESS, result.task(":pushTranslations")?.outcome)
         assertEquals(
            listOf(
               "Your local value of 'home_title' in ':/strings.xml' (locale nl) differs from the one on TranslationTools, so push did not send it. " +
                  "Push never replaces a value outside the default Locale. To change the value, edit it on TranslationTools. " +
                  "To copy the TranslationTools value into your file, run pullTranslations.",
            ),
            result.output.lines().filter { it.contains("differs from the one on TranslationTools") },
         )
         assertEquals(
            listOf(":/strings.xml|en|home_title" to "Home", ":/strings.xml|nl|home_title" to null),
            sentValues(server.pushBodies.single()),
         )
      }
   }

   @Test
   fun push_should_not_warn_for_equal_values_and_upload_a_new_key_in_every_locale()
   {
      MockTranslationToolsServer(
         localeResponses = mapOf("nl" to remoteItems("home_title" to "Start")),
      ).use { server ->
         val projectDir = createFormatSpecifierProject(
            defaultStrings = """<string name="home_title">Home</string><string name="promo_title">Promo</string>""",
            dutchStrings = """<string name="home_title">Start</string><string name="promo_title">Aanbieding</string>""",
         )

         val result = runGradle(projectDir, "pushTranslations", server.baseUrl)

         assertEquals(TaskOutcome.SUCCESS, result.task(":pushTranslations")?.outcome)
         assertFalse(result.output.contains("differs from the one on TranslationTools"), result.output)
         assertEquals(
            listOf(
               ":/strings.xml|en|home_title" to "Home",
               ":/strings.xml|en|promo_title" to "Promo",
               ":/strings.xml|nl|home_title" to null,
               ":/strings.xml|nl|promo_title" to "Aanbieding",
            ),
            sentValues(server.pushBodies.single()),
         )
      }
   }

   @Test
   fun push_should_warn_with_each_files_origin_for_differing_strings_stringsdict_and_catalog_values()
   {
      MockTranslationToolsServer(
         localeResponses = mapOf(
            "nl" to remoteItemsByOrigin(
               (":/Localizable.strings" to "Done") to "Klaar",
               (":/Localizable.stringsdict" to "cart_items") to "{items, plural, one {# artikel} other {# artikelen}}",
               (":/Catalog.xcstrings" to "greeting") to "Hoi",
            ),
         ),
      ).use { server ->
         val projectDir = createFormatSpecifierProject(defaultStrings = "", dutchStrings = "")
         File(projectDir, "translationtools.yaml").appendText("appleResources:\n  resourceDirectories:\n    - ios\n")
         File(projectDir, "ios/en.lproj").mkdirs()
         File(projectDir, "ios/nl.lproj").mkdirs()
         File(projectDir, "ios/en.lproj/Localizable.strings").writeText("\"Done\" = \"Done\";")
         File(projectDir, "ios/nl.lproj/Localizable.strings").writeText("\"Done\" = \"Gedaan\";")
         File(projectDir, "ios/en.lproj/Localizable.stringsdict").writeText(
            stringsdictPlist(pluralEntry("cart_items", "items", "d", "one" to "%d item", "other" to "%d items")),
         )
         File(projectDir, "ios/nl.lproj/Localizable.stringsdict").writeText(
            stringsdictPlist(pluralEntry("cart_items", "items", "d", "one" to "%d ding", "other" to "%d dingen")),
         )
         File(projectDir, "ios/Catalog.xcstrings").writeText(
            """
            {
              "sourceLanguage" : "en",
              "strings" : {
                "greeting" : {
                  "localizations" : {
                    "en" : { "stringUnit" : { "state" : "translated", "value" : "Hello" } },
                    "nl" : { "stringUnit" : { "state" : "translated", "value" : "Hallo" } }
                  }
                }
              },
              "version" : "1.0"
            }
            """.trimIndent(),
         )

         val result = runGradle(projectDir, "pushTranslations", server.baseUrl)

         assertEquals(TaskOutcome.SUCCESS, result.task(":pushTranslations")?.outcome)
         val warnings = result.output.lines().filter { it.contains("differs from the one on TranslationTools") }
         assertEquals(
            listOf(
               "'greeting' in ':/Catalog.xcstrings' (locale nl)",
               "'Done' in ':/Localizable.strings' (locale nl)",
               "'cart_items' in ':/Localizable.stringsdict' (locale nl)",
            ),
            warnings.map { it.removePrefix("Your local value of ").substringBefore(" differs") },
         )
         assertEquals(
            listOf(
               ":/Catalog.xcstrings|en|greeting" to "Hello",
               ":/Catalog.xcstrings|nl|greeting" to null,
               ":/Localizable.strings|en|Done" to "Done",
               ":/Localizable.strings|nl|Done" to null,
               ":/Localizable.stringsdict|en|cart_items" to "{items, plural, one {# item} other {# items}}",
               ":/Localizable.stringsdict|nl|cart_items" to null,
            ),
            sentValues(server.pushBodies.single()).filter { !it.first.startsWith(":/strings.xml") },
         )
      }
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

private fun createNlProject(): File =
   createPushProject(
      "res/values/strings.xml" to androidStrings("home_title" to "Home"),
      "res/values-nl/strings.xml" to androidStrings("home_title" to "Start"),
   )

private fun createPushProject(vararg files: Pair<String, String>): File
{
   val projectDir = createTempDirectory("translationtools-push-rule").toFile()
   files.forEach { (path, text) ->
      val file = File(projectDir, path)
      file.parentFile.mkdirs()
      file.writeText(text)
   }
   return projectDir
}

private fun androidStrings(vararg entries: Pair<String, String>): String =
   "<resources>" + entries.joinToString("") { (key, value) -> "<string name=\"$key\">$value</string>" } + "</resources>"

/** A per-Locale read response holding each `key to value` under `:/strings.xml`. */
private fun remoteItems(vararg entries: Pair<String, String>): String =
   remoteItemsByOrigin(*entries.map { (key, value) -> (":/strings.xml" to key) to value }.toTypedArray())

/** A per-Locale read response holding each `(origin to key) to value`. */
private fun remoteItemsByOrigin(vararg entries: Pair<Pair<String, String>, String>): String =
   entries.joinToString(prefix = "[", postfix = "]") { (originAndKey, value) ->
      """{"origin":"${originAndKey.first}","key":"${originAndKey.second}","value":"$value"}"""
   }

/** The pushed items as `origin|locale|key` to value, in upload order. */
private fun sentValues(body: String): List<Pair<String, String?>> =
   Json.parseToJsonElement(body).jsonObject.getValue("items").jsonArray
      .map { it.jsonObject }
      .map { item ->
         listOf("origin", "locale", "key").joinToString("|") { item.getValue(it).jsonPrimitive.content } to
            item.getValue("value").jsonPrimitive.contentOrNull
      }

private fun pushWith(projectDir: File, server: FakeTranslationTools)
{
   val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
   val task = project.tasks.create("pushTranslations", PushTranslationsTask::class.java)
   task.apiKey.set("test-key")
   task.defaultLocale.set("en")
   task.resourceDirectories.from(File(projectDir, "res"))
   task.appleResourceDirectories.from(File(projectDir, "ios"))
   task.keyOverrides.set(emptyMap())
   task.projectPathInput.set(project.path)
   task.baseUrl.set(BASE_URL)
   task.httpClientFactory = { server.client }

   task.push()
}

/** A TranslationTools stand-in: answers the per-Locale read from [remoteByLocale] (empty for any other Locale), or answers with [readStatus] and [readBody] instead. */
private class FakeTranslationTools(
   private val remoteByLocale: Map<String, String> = emptyMap(),
   private val readStatus: HttpStatusCode = HttpStatusCode.OK,
   private val readBody: String? = null,
)
{
   val requests = mutableListOf<String>()
   val pushBodies = mutableListOf<String>()
   val client: HttpClient = createHttpClient(
      MockEngine { request ->
         requests += "${request.method.value} ${request.url.encodedPath}"
         val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
         when {
            request.method == HttpMethod.Post -> {
               pushBodies += (request.body as TextContent).text
               respond(
                  content = """{"receivedKeyCount":1,"createdKeyCount":1,"updatedKeyCount":0,"removedKeyCount":0}""",
                  status = HttpStatusCode.OK,
                  headers = json,
               )
            }
            else -> respond(
               content = readBody ?: remoteByLocale[request.url.encodedPath.substringAfterLast('/')] ?: "[]",
               status = readStatus,
               headers = json,
            )
         }
      }
   )
}

private fun createHttpClient(engine: MockEngine): HttpClient
{
   return HttpClient(engine) {
      install(ContentNegotiation) {
         json(Json { ignoreUnknownKeys = true })
      }
   }
}
