package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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

class PullTranslationsTaskTests
{
   @Test
   fun pullTranslations_should_build_origin_aware_state_from_metadata_and_locales() = runBlocking {
      val client = createHttpClient(
         MockEngine { request ->
            when (request.url.encodedPath) {
               "/api/v1/translations/project" -> respondJson(
                  """{"locales":["en","nl"],"defaultLocale":"en"}"""
               )
               "/api/v1/translations/en" -> respondJson(
                  """[{"origin":":app:/strings.xml","key":"home_title","value":"Home"}]"""
               )
               "/api/v1/translations/nl" -> respondJson(
                  """[{"origin":":app:/strings.xml","key":"home_title","value":"Start"}]"""
               )
               else -> error("Unexpected path: ${request.url.encodedPath}")
            }
         }
      )

      val pulled = pullTranslations(client, apiKey = "test-key", configuredLocales = listOf("nl"))

      assertEquals("en", pulled.project.defaultLocale)
      assertEquals(listOf("en", "nl"), pulled.project.locales)
      assertEquals(1, pulled.items.size)
      assertEquals(":app:/strings.xml", pulled.items.single().origin)
      assertEquals("home_title", pulled.items.single().key)
      assertEquals("Home", pulled.items.single().valuesByLocale["en"])
      assertEquals("Start", pulled.items.single().valuesByLocale["nl"])
   }

   @Test
   fun pullSnapshot_should_throw_clear_error_for_http_failure() {
      val client = createHttpClient(
         MockEngine {
            respond(
               content = "forbidden",
               status = HttpStatusCode.Forbidden,
               headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
            )
         }
      )

      val exception = assertFailsWith<PullTranslationsException> {
         runBlocking {
             pullTranslations(client, apiKey = "test-key", configuredLocales = emptyList())
          }
       }

      assertTrue(exception.message.orEmpty().contains("status 403"))
      assertTrue(exception.message.orEmpty().contains("forbidden"))
   }

   @Test
    fun pullSnapshot_should_throw_clear_error_for_invalid_payload() {
      val client = createHttpClient(
         MockEngine {
            respondJson("""{"bad":true}""")
         }
      )

      val exception = assertFailsWith<PullTranslationsException> {
         runBlocking {
            pullTranslations(client, apiKey = "test-key", configuredLocales = emptyList())
          }
       }

       assertTrue(exception.message.orEmpty().contains("Failed to parse"))
    }

    @Test
     fun pullTranslations_should_fall_back_to_first_locale_when_remote_default_locale_is_missing() = runBlocking {
       val client = createHttpClient(
          MockEngine { request ->
             when (request.url.encodedPath) {
                "/api/v1/translations/project" -> respondJson(
                   """{"locales":["nl","en"],"defaultLocale":null}"""
                )
                "/api/v1/translations/en" -> respondJson("[]")
                "/api/v1/translations/nl" -> respondJson("[]")
                else -> error("Unexpected path: ${request.url.encodedPath}")
             }
          }
       )

        val pulled = pullTranslations(client, apiKey = "test-key", configuredLocales = emptyList())

         assertEquals("en", pulled.project.defaultLocale)
         assertEquals(listOf("en", "nl"), pulled.project.locales)
      }

   @Test
   fun writeStringsFile_should_write_deterministic_xml_and_preserve_local_only_flag()
   {
      val directory = createTempDirectory("translationtools-pull-writer").toFile()
      val target = File(directory, "values/strings.xml")

      writeStringsFile(
         target,
         listOf(
            ParsedAndroidResourceValue(resourceName = "home_title", value = "Home", managedRemotely = true),
            ParsedAndroidResourceValue(resourceName = "device_name", value = "Phone", managedRemotely = false),
         ),
      )

      val rendered = target.readText()
      assertTrue(rendered.contains("<string name=\"home_title\">Home</string>"))
      assertTrue(rendered.contains("<string name=\"device_name\" translatable=\"false\">Phone</string>"))
   }

   @Test
   fun toAndroidValuesDirectory_should_render_region_locale_directories()
   {
      assertEquals("values", toAndroidValuesDirectory("en", "en"))
      assertEquals("values-nl", toAndroidValuesDirectory("nl", "en"))
      assertEquals("values-pt-rBR", toAndroidValuesDirectory("pt-br", "en"))
   }

   @Test
   fun writeStringsFile_should_skip_entries_with_null_values_when_not_already_present()
   {
      val directory = createTempDirectory("translationtools-pull-writer-null").toFile()
      val target = File(directory, "values-nl/strings.xml")

      writeStringsFile(
         target,
         listOf(
            ParsedAndroidResourceValue(resourceName = "home_title", value = "Start", managedRemotely = true),
            ParsedAndroidResourceValue(resourceName = "checkout_title", value = null, managedRemotely = true),
         ),
      )

      val rendered = target.readText()
      assertTrue(rendered.contains("home_title"))
      assertFalse(rendered.contains("checkout_title"))
   }

   @Test
   fun pull_should_write_apple_strings_preserve_on_merge_and_create_missing_locale()
   {
      val projectDir = createTempDirectory("translationtools-pull-apple").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText(
         "<resources><string name=\"home_title\">Home</string></resources>"
      )
      File(projectDir, "ios/en.lproj").mkdirs()
      File(projectDir, "ios/en.lproj/InfoPlist.strings").writeText(
         "/* App permissions */\n\"NSCameraUsageDescription\" = \"Old camera\";\n"
      )

      val client = createHttpClient(
         MockEngine { request ->
            when (request.url.encodedPath) {
               "/api/v1/translations/project" -> respondJson("""{"locales":["en","de"],"defaultLocale":"en"}""")
               "/api/v1/translations/en" -> respondJson(
                  """[{"origin":":/strings.xml","key":"home_title","value":"Home"},{"origin":":/InfoPlist.strings","key":"NSCameraUsageDescription","value":"New camera"}]"""
               )
               "/api/v1/translations/de" -> respondJson(
                  """[{"origin":":/InfoPlist.strings","key":"NSCameraUsageDescription","value":"Kamera"}]"""
               )
               else -> error("Unexpected path: ${request.url.encodedPath}")
            }
         }
      )

      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val task = project.tasks.create("pullTranslations", PullTranslationsTask::class.java)
      task.apiKey.set("test-key")
      task.defaultLocale.set("en")
      task.resourceDirectories.from(File(projectDir, "res"))
      task.appleResourceDirectories.from(File(projectDir, "ios"))
      task.keyOverrides.set(emptyMap())
      task.configuredLocales.set(listOf("en", "de"))
      task.projectPathInput.set(project.path)
      task.baseUrl.set(BASE_URL)
      task.httpClientFactory = { client }

      task.pull()

      val enContent = File(projectDir, "ios/en.lproj/InfoPlist.strings").readText()
      assertTrue(enContent.contains("/* App permissions */"))
      assertTrue(enContent.contains("\"NSCameraUsageDescription\" = \"New camera\";"))
      assertFalse(enContent.contains("Old camera"))

      val deFile = File(projectDir, "ios/de.lproj/InfoPlist.strings")
      assertTrue(deFile.exists())
      val deContent = deFile.readText()
      assertTrue(deContent.contains("Managed by TranslationTools"))
      assertTrue(deContent.contains("\"NSCameraUsageDescription\" = \"Kamera\";"))
   }

   @Test
   fun pull_should_merge_remote_plurals_into_stringsdict_files_create_a_new_locales_file_and_still_write_strings_files()
   {
      val cartItems = "{\"origin\":\":/Localizable.stringsdict\",\"key\":\"cart_items\",\"value\":\"%s\"}"
      MockTranslationToolsServer(
         projectResponse = """{"locales":["en","de"],"defaultLocale":"en"}""",
         localeResponses = mapOf(
            "en" to "[" + listOf(
               """{"origin":":/Localizable.strings","key":"title","value":"New title"}""",
               cartItems.format("{items, plural, one {# item} other {# things}}"),
               """{"origin":":/Localizable.stringsdict","key":"inbox_messages","value":"{count, plural, one {# message} other {# messages}}"}""",
               """{"origin":":/Localizable.stringsdict","key":"bag","value":"Items"}""",
               """{"origin":":/Other.stringsdict","key":"x","value":"{count, plural, other {# x}}"}""",
            ).joinToString(",") + "]",
            "de" to "[" + cartItems.format("{items, plural, one {# Artikel} other {# Artikel}}") + "]",
         ),
      ).use { server ->
         val projectDir = createTempDirectory("translationtools-pull-stringsdict").toFile()
         writeNonKmpBuildFiles(projectDir)
         writeStandardTestFixtures(projectDir, appleResourceDirectories = listOf("ios"))
         File(projectDir, "ios/en.lproj").mkdirs()
         File(projectDir, "ios/en.lproj/Localizable.strings").writeText("\"title\" = \"Old title\";\n")
         val stringsdict = File(projectDir, "ios/en.lproj/Localizable.stringsdict")
         stringsdict.writeText(
            stringsdictPlist(
               pluralEntry("cart_items", "items", "d", "one" to "%d item", "other" to "%d items"),
               pluralEntry("bag", "items", "d", "one" to "%d bag", "other" to "%d bags"),
            ),
         )

         val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("pullTranslations", "-Ptranslationtools.baseUrl=${server.baseUrl}")
            .build()

         assertEquals(TaskOutcome.SUCCESS, result.task(":pullTranslations")?.outcome)
         assertTrue(File(projectDir, "ios/en.lproj/Localizable.strings").readText().contains("\"title\" = \"New title\";"))

         val enForms = parseAppleStringsdict(stringsdict.readBytes(), stringsdict.path) as ParsedAppleStringsdict.Readable
         assertEquals(listOf("cart_items", "bag", "inbox_messages"), enForms.entries.map { it.key })
         assertEquals(
            listOf("{items, plural, one {# item} other {# things}}", "{items, plural, one {# bag} other {# bags}}", "{count, plural, one {# message} other {# messages}}"),
            enForms.entries.map { it.toPluralValue() },
         )
         assertEquals(listOf("d", "d", "lld"), enForms.entries.map { it.valueType })
         assertTrue(stringsdict.readText().contains("\t\t\t<string>%d things</string>\n"))
         assertTrue(result.output.contains("Kept local 'bag' in '${stringsdict.path}': the value for 'en' in TranslationTools is not a Plural that a .stringsdict entry can hold."), result.output)

         val deFile = File(projectDir, "ios/de.lproj/Localizable.stringsdict")
         assertTrue(deFile.readText().startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE plist PUBLIC"))
         assertEquals(
            "{items, plural, one {# Artikel} other {# Artikel}}",
            (parseAppleStringsdict(deFile.readBytes(), deFile.path) as ParsedAppleStringsdict.Readable).entries.single().toPluralValue(),
         )
         assertTrue(result.output.contains("Created '${deFile.path}' for locale 'de'. Add 'de' (or its region) to your Xcode project's knownRegions to bundle it."), result.output)

         assertFalse(File(projectDir, "ios/en.lproj/Other.stringsdict").exists())
         assertFalse(File(projectDir, "ios/de.lproj/Other.stringsdict").exists())
         assertFalse(File(projectDir, "ios/de.lproj/Localizable.strings").exists())
      }
   }

   @Test
   fun pull_should_leave_an_unreadable_stringsdict_file_unchanged_and_still_write_the_other_locales()
   {
      MockTranslationToolsServer(
         projectResponse = """{"locales":["en","pl"],"defaultLocale":"en"}""",
         localeResponses = mapOf(
            "en" to """[{"origin":":/Localizable.stringsdict","key":"cart_items","value":"{items, plural, one {# item} other {# things}}"}]""",
            "pl" to """[{"origin":":/Localizable.stringsdict","key":"cart_items","value":"{items, plural, other {# rzeczy}}"}]""",
         ),
      ).use { server ->
         val projectDir = createTempDirectory("translationtools-pull-stringsdict-binary").toFile()
         writeNonKmpBuildFiles(projectDir)
         writeStandardTestFixtures(projectDir, appleResourceDirectories = listOf("ios"))
         File(projectDir, "ios/en.lproj").mkdirs()
         File(projectDir, "ios/pl.lproj").mkdirs()
         val enFile = File(projectDir, "ios/en.lproj/Localizable.stringsdict")
         enFile.writeText(stringsdictPlist(pluralEntry("cart_items", "items", "d", "one" to "%d item", "other" to "%d items")))
         val binary = byteArrayOf('b'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), 0, 1, 0xFF.toByte())
         val plFile = File(projectDir, "ios/pl.lproj/Localizable.stringsdict")
         plFile.writeBytes(binary)

         val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("pullTranslations", "-Ptranslationtools.baseUrl=${server.baseUrl}")
            .build()

         assertEquals(TaskOutcome.SUCCESS, result.task(":pullTranslations")?.outcome)
         assertTrue(plFile.readBytes().contentEquals(binary))
         assertTrue(result.output.contains("Left '${plFile.path}' unchanged: it is not a readable XML property list."), result.output)
         assertTrue(enFile.readText().contains("\t\t\t<string>%d things</string>\n"))
         assertFalse(result.output.contains("Ignored pulled origin"), result.output)
      }
   }

   @Test
   fun pull_should_keep_plurals_resources_and_write_remote_forms_in_cldr_order()
   {
      val projectDir = createTempDirectory("translationtools-pull-plurals").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText(
         """
         <resources>
            <plurals name="cart_items">
               <item quantity="other"># items in {cartName}</item>
               <item quantity="one"># item in {cartName}</item>
            </plurals>
            <string name="greeting">Hello {userName}!</string>
         </resources>
         """.trimIndent(),
      )
      File(projectDir, "res/values-pl").mkdirs()
      File(projectDir, "res/values-pl/strings.xml").writeText(
         """
         <resources>
            <plurals name="cart_items">
               <item quantity="one"># produkt w {cartName}</item>
               <item quantity="few"># produkty w {cartName}</item>
               <item quantity="many"># produktów w {cartName}</item>
            </plurals>
         </resources>
         """.trimIndent(),
      )

      runPull(
         projectDir,
         locales = listOf("en", "pl"),
         remoteByLocale = mapOf(
            "en" to listOf(
               remoteItem("cart_items", "{count, plural, one {# item in {cartName}} other {# items in {cartName}}}"),
               remoteItem("greeting", "Hello {userName}!"),
            ),
            "pl" to listOf(
               remoteItem("cart_items", "{count, plural, one {# produkt w {cartName}} few {# produkty (w {cartName})} many {# produktów w {cartName}}}"),
            ),
         ),
      )

      assertEquals(
         """
         <?xml version="1.0" encoding="utf-8"?>
         <resources>
            <plurals name="cart_items">
               <item quantity="one"># item in {cartName}</item>
               <item quantity="other"># items in {cartName}</item>
            </plurals>
            <string name="greeting">Hello {userName}!</string>
         </resources>

         """.trimIndent(),
         File(projectDir, "res/values/strings.xml").readText(),
      )
      assertEquals(
         """
         <?xml version="1.0" encoding="utf-8"?>
         <resources>
            <plurals name="cart_items">
               <item quantity="one"># produkt w {cartName}</item>
               <item quantity="few"># produkty (w {cartName})</item>
               <item quantity="many"># produktów w {cartName}</item>
            </plurals>
         </resources>

         """.trimIndent(),
         File(projectDir, "res/values-pl/strings.xml").readText(),
      )
   }

   @Test
   fun pull_should_keep_translatable_false_and_escape_plurals_items()
   {
      val projectDir = createTempDirectory("translationtools-pull-plurals-local").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText(
         """<resources><plurals name="unit_count" translatable="false"><item quantity="other">old</item></plurals></resources>""",
      )

      runPull(
         projectDir,
         locales = listOf("en"),
         remoteByLocale = mapOf("en" to listOf(remoteItem("unit_count", "{n, plural, one {<b>#</b> unit} other {# units & more}}"))),
      )

      assertEquals(
         """
         <?xml version="1.0" encoding="utf-8"?>
         <resources>
            <plurals name="unit_count" translatable="false">
               <item quantity="one">&lt;b&gt;#&lt;/b&gt; unit</item>
               <item quantity="other"># units &amp; more</item>
            </plurals>
         </resources>

         """.trimIndent(),
         File(projectDir, "res/values/strings.xml").readText(),
      )
   }

   @Test
   fun pull_should_keep_a_string_that_holds_icu_text_as_a_string()
   {
      val projectDir = createTempDirectory("translationtools-pull-string-icu").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText("""<resources><string name="cart_items">old</string></resources>""")

      runPull(
         projectDir,
         locales = listOf("en"),
         remoteByLocale = mapOf("en" to listOf(remoteItem("cart_items", "{count, plural, one {# item} other {# items}}"))),
      )

      assertEquals(
         "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" +
            "   <string name=\"cart_items\">{count, plural, one {# item} other {# items}}</string>\n</resources>\n",
         File(projectDir, "res/values/strings.xml").readText(),
      )
   }

   @Test
   fun pull_should_write_a_remote_only_plural_as_plurals_and_one_with_an_exact_case_as_string()
   {
      val projectDir = createTempDirectory("translationtools-pull-remote-only").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText("""<resources><string name="home_title">Home</string></resources>""")

      runPull(
         projectDir,
         locales = listOf("en"),
         remoteByLocale = mapOf(
            "en" to listOf(
               remoteItem("home_title", "Home"),
               remoteItem("new_items", "{count, plural, one {# item} other {# items}}"),
               remoteItem("zero_items", "{count, plural, =0 {none} other {# items}}"),
            ),
         ),
      )

      assertEquals(
         """
         <?xml version="1.0" encoding="utf-8"?>
         <resources>
            <string name="home_title">Home</string>
            <plurals name="new_items">
               <item quantity="one"># item</item>
               <item quantity="other"># items</item>
            </plurals>
            <string name="zero_items">{count, plural, =0 {none} other {# items}}</string>
         </resources>

         """.trimIndent(),
         File(projectDir, "res/values/strings.xml").readText(),
      )
   }

   @Test
   fun pull_should_use_the_locale_own_element_when_the_default_locale_file_has_none()
   {
      val projectDir = createTempDirectory("translationtools-pull-own-kind").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText("""<resources><string name="home_title">Home</string></resources>""")
      File(projectDir, "res/values-pl").mkdirs()
      File(projectDir, "res/values-pl/strings.xml").writeText(
         """<resources><plurals name="only_pl"><item quantity="other">stare</item></plurals></resources>""",
      )
      File(projectDir, "res/values-nl").mkdirs()
      File(projectDir, "res/values-nl/strings.xml").writeText("""<resources><string name="only_pl">oud</string></resources>""")

      val plural = "{count, plural, one {# nowy} other {# nowych}}"
      runPull(
         projectDir,
         locales = listOf("en", "nl", "pl"),
         remoteByLocale = mapOf(
            "en" to listOf(remoteItem("home_title", "Home")),
            "nl" to listOf(remoteItem("only_pl", plural)),
            "pl" to listOf(remoteItem("only_pl", plural)),
         ),
      )

      assertEquals(
         "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" +
            "   <plurals name=\"only_pl\">\n" +
            "      <item quantity=\"one\"># nowy</item>\n" +
            "      <item quantity=\"other\"># nowych</item>\n" +
            "   </plurals>\n</resources>\n",
         File(projectDir, "res/values-pl/strings.xml").readText(),
      )
      assertEquals(
         "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" +
            "   <string name=\"only_pl\">$plural</string>\n</resources>\n",
         File(projectDir, "res/values-nl/strings.xml").readText(),
      )
   }

   @Test
   fun pull_should_leave_a_plurals_resource_alone_when_the_remote_value_does_not_fit()
   {
      val projectDir = createTempDirectory("translationtools-pull-no-fit").toFile()
      File(projectDir, "res/values").mkdirs()
      val original = "<resources><plurals name=\"cart_items\"><item quantity=\"other\"># items</item></plurals></resources>"
      File(projectDir, "res/values/strings.xml").writeText(original)

      runPull(
         projectDir,
         locales = listOf("en"),
         remoteByLocale = mapOf("en" to listOf(remoteItem("cart_items", "{count, plural, =0 {none} other {# items}}"))),
      )

      assertEquals(
         "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" +
            "   <plurals name=\"cart_items\">\n      <item quantity=\"other\"># items</item>\n   </plurals>\n</resources>\n",
         File(projectDir, "res/values/strings.xml").readText(),
      )
   }

   @Test
   fun pull_should_write_a_local_plurals_resource_back_with_every_item_intact_when_its_value_is_not_a_plural()
   {
      val projectDir = createTempDirectory("translationtools-pull-stray-brace").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText(
         """
         <resources>
            <string name="home_title">Home</string>
            <plurals name="odd"><item quantity="one">a } b</item><item quantity="other">it's # items</item></plurals>
         </resources>
         """.trimIndent(),
      )

      runPull(
         projectDir,
         locales = listOf("en"),
         remoteByLocale = mapOf("en" to listOf(remoteItem("home_title", "Start"))),
      )

      assertEquals(
         """
         <?xml version="1.0" encoding="utf-8"?>
         <resources>
            <string name="home_title">Start</string>
            <plurals name="odd">
               <item quantity="one">a } b</item>
               <item quantity="other">it&apos;s # items</item>
            </plurals>
         </resources>

         """.trimIndent(),
         File(projectDir, "res/values/strings.xml").readText(),
      )
   }

   @Test
   fun mergePulledLocale_should_warn_with_key_origin_and_locale_when_the_remote_value_does_not_fit_a_plurals_resource()
   {
      val existing = parseStringsFile(
         """<resources><plurals name="cart_items"><item quantity="other"># items</item></plurals></resources>""",
      ).imported.associateBy { it.resourceName }

      val exactCase = mergePulledLocale(
         origin = ":/strings.xml",
         locale = "en",
         existing = existing,
         defaultLocaleExisting = existing,
         remoteEntries = listOf(PulledTranslationItem(":/strings.xml", "cart_items", mapOf("en" to "{count, plural, =0 {none} other {# items}}"))),
      )
      val plainText = mergePulledLocale(
         origin = ":/strings.xml",
         locale = "en",
         existing = existing,
         defaultLocaleExisting = existing,
         remoteEntries = listOf(PulledTranslationItem(":/strings.xml", "cart_items", mapOf("en" to "plain text"))),
      )

      val expected = listOf("'cart_items' in ':/strings.xml' (locale en) is not a Plural that fits a <plurals> resource, so pull left it unchanged.")
      assertEquals(expected, exactCase.warnings)
      assertEquals(expected, plainText.warnings)
      assertEquals(existing.values.toList(), exactCase.entries)
   }

   @Test
   fun mergePulledLocale_should_write_nothing_for_a_plurals_key_without_a_local_element_when_the_remote_value_does_not_fit()
   {
      val existingDefault = parseStringsFile(
         """<resources><plurals name="cart_items"><item quantity="other"># items</item></plurals></resources>""",
      ).imported.associateBy { it.resourceName }

      val merged = mergePulledLocale(
         origin = ":/strings.xml",
         locale = "pl",
         existing = emptyMap(),
         defaultLocaleExisting = existingDefault,
         remoteEntries = listOf(PulledTranslationItem(":/strings.xml", "cart_items", mapOf("pl" to "plain text"))),
      )

      assertEquals(emptyList(), merged.entries)
      assertEquals(1, merged.warnings.size)
   }

   @Test
   fun pull_should_remove_a_plurals_resource_when_the_remote_value_is_null()
   {
      val projectDir = createTempDirectory("translationtools-pull-null-plurals").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText(
         """
         <resources>
            <string name="home_title">Home</string>
            <plurals name="cart_items"><item quantity="other"># items</item></plurals>
         </resources>
         """.trimIndent(),
      )

      runPull(
         projectDir,
         locales = listOf("en"),
         remoteByLocale = mapOf("en" to listOf(remoteItem("home_title", "Home"), remoteItem("cart_items", null))),
      )

      assertEquals(
         "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n   <string name=\"home_title\">Home</string>\n</resources>\n",
         File(projectDir, "res/values/strings.xml").readText(),
      )
   }

   @Test
   fun mergePulledLocale_should_remove_a_plurals_resource_without_a_warning_when_the_remote_value_is_null()
   {
      val existing = parseStringsFile(
         """<resources><plurals name="cart_items"><item quantity="other"># items</item></plurals></resources>""",
      ).imported.associateBy { it.resourceName }

      val merged = mergePulledLocale(
         origin = ":/strings.xml",
         locale = "en",
         existing = existing,
         defaultLocaleExisting = existing,
         remoteEntries = listOf(PulledTranslationItem(":/strings.xml", "cart_items", mapOf("en" to null))),
      )

      assertEquals(emptyList(), merged.warnings)
      assertEquals(emptyList(), merged.entries.filter { it.value != null })
   }

   @Test
   fun discoverLocaleFiles_should_keep_non_default_locale_only_variants_available_for_base_file_resolution()
   {
      val root = createTempDirectory("translationtools-localized-only-origin").toFile()
      File(root, "values-nl").mkdirs()
      File(root, "values-nl/feature.xml").writeText(
         """
         <resources>
            <string name="only_nl">Alleen nl</string>
         </resources>
         """.trimIndent(),
      )

      val discovery = discoverLocaleFiles(listOf(root), "en")

      assertEquals(1, discovery.files.size)
      assertEquals("feature.xml", discovery.files.single().relativeBaseFile)
      assertEquals(root, discovery.files.single().resourceDirectory)
   }
}

private fun createHttpClient(engine: MockEngine): HttpClient
{
   return HttpClient(engine) {
      install(ContentNegotiation) {
         json(Json { ignoreUnknownKeys = true })
      }
   }
}

private fun MockRequestHandleScope.respondJson(content: String) = respond(
   content = content,
   status = HttpStatusCode.OK,
   headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

private fun remoteItem(key: String, value: String?) = TranslationItemResponse(origin = ":/strings.xml", key = key, value = value)

private fun runPull(projectDir: File, locales: List<String>, remoteByLocale: Map<String, List<TranslationItemResponse>>)
{
   val json = Json { encodeDefaults = true }
   val client = createHttpClient(
      MockEngine { request ->
         val path = request.url.encodedPath
         val locale = path.substringAfter("/api/v1/translations/")
         when {
            path == "/api/v1/translations/project" ->
               respondJson("""{"locales":[${locales.joinToString(",") { "\"$it\"" }}],"defaultLocale":"en"}""")
            remoteByLocale.containsKey(locale) ->
               respondJson(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TranslationItemResponse.serializer()), remoteByLocale.getValue(locale)))
            else -> error("Unexpected path: $path")
         }
      }
   )

   val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
   val task = project.tasks.create("pullTranslations", PullTranslationsTask::class.java)
   task.apiKey.set("test-key")
   task.defaultLocale.set("en")
   task.resourceDirectories.from(File(projectDir, "res"))
   task.appleResourceDirectories.from()
   task.keyOverrides.set(emptyMap())
   task.configuredLocales.set(locales)
   task.projectPathInput.set(project.path)
   task.baseUrl.set(BASE_URL)
   task.httpClientFactory = { client }

   task.pull()
}
