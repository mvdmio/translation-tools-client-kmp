package io.mvdm.translationtools.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidStringResourceParserTests
{
   @Test
   fun parseStringsFile_should_parse_values_and_skip_unsupported_entries()
   {
      val parsed = parseStringsFile(
         """
         <resources>
            <string name="action_save">Save</string>
            <string name="hidden_label" translatable="false">Hidden</string>
            <string-array name="menu_entries"><item>Home</item></string-array>
         </resources>
         """.trimIndent(),
      )

      assertEquals(listOf(ParsedAndroidResourceValue("action_save", "Save", true), ParsedAndroidResourceValue("hidden_label", "Hidden", false)), parsed.imported)
      assertEquals(
         listOf("menu_entries: string-array is not supported"),
         parsed.skipped.map { "${it.resourceName}: ${it.reason}" },
      )
   }

   @Test
   fun parseStringsFile_should_compose_plurals_items_into_a_plural_in_cldr_order()
   {
      val parsed = parseStringsFile(
         """
         <resources>
            <plurals name="cart_items">
               <item quantity="other"># items</item>
               <item quantity="one"># item</item>
            </plurals>
            <plurals name="slow_items"><item quantity="many">m</item><item quantity="zero">z</item><item quantity="few">f</item><item quantity="two">t</item></plurals>
         </resources>
         """.trimIndent(),
      )

      assertEquals(
         listOf(
            "cart_items" to "{count, plural, one {# item} other {# items}}",
            "slow_items" to "{count, plural, zero {z} two {t} few {f} many {m}}",
         ),
         parsed.imported.map { it.resourceName to it.value },
      )
      assertEquals(
         listOf(AndroidPluralItem(PluralQuantity.ONE, "# item"), AndroidPluralItem(PluralQuantity.OTHER, "# items")),
         parsed.imported.first().pluralItems,
      )
   }

   @Test
   fun parseStringsFile_should_read_plurals_item_text_as_written()
   {
      val parsed = parseStringsFile(
         """<resources><plurals name="odd"><item quantity="other">it's a } &amp; #</item></plurals></resources>""",
      )

      assertEquals(listOf(AndroidPluralItem(PluralQuantity.OTHER, "it's a } & #")), parsed.imported.single().pluralItems)
      assertEquals("{count, plural, other {it's a } & #}}", parsed.imported.single().value)
   }

   @Test
   fun parseStringsFile_should_give_an_empty_plurals_a_null_value()
   {
      val parsed = parseStringsFile("""<resources><plurals name="cart_items"></plurals></resources>""")

      assertEquals(null, parsed.imported.single().value)
      assertEquals(emptyList(), parsed.imported.single().pluralItems)
   }

   @Test
   fun parseStringsFile_should_keep_translatable_false_on_plurals()
   {
      val parsed = parseStringsFile(
         """<resources><plurals name="unit_count" translatable="false"><item quantity="other">#</item></plurals></resources>""",
      )

      assertEquals(false, parsed.imported.single().managedRemotely)
   }

   @Test
   fun parseStringsFile_should_skip_a_plurals_with_an_unknown_or_repeated_quantity_and_keep_the_rest()
   {
      val parsed = parseStringsFile(
         """
         <resources>
            <string name="action_save">Save</string>
            <plurals name="unknown_quantity"><item quantity="several">#</item></plurals>
            <plurals name="repeated_quantity"><item quantity="one">a</item><item quantity="one">b</item></plurals>
            <plurals name="fine"><item quantity="other">#</item></plurals>
         </resources>
         """.trimIndent(),
      )

      assertEquals(listOf("action_save", "fine"), parsed.imported.map { it.resourceName })
      assertEquals(
         listOf(
            "unknown_quantity: unknown plurals quantity 'several'",
            "repeated_quantity: plurals quantity 'one' is repeated",
         ),
         parsed.skipped.map { "${it.resourceName}: ${it.reason}" },
      )
   }

   @Test
   fun parsed_resource_should_reject_plural_items_that_are_unordered_or_do_not_compose_to_its_value()
   {
      val items = listOf(AndroidPluralItem(PluralQuantity.ONE, "# item"), AndroidPluralItem(PluralQuantity.OTHER, "# items"))

      assertFailsWith<IllegalArgumentException> {
         ParsedAndroidResourceValue("cart_items", "{count, plural, other {# items}}", managedRemotely = true, pluralItems = items)
      }
      assertFailsWith<IllegalArgumentException> {
         ParsedAndroidResourceValue("cart_items", composePluralValue("count", items.reversed()), managedRemotely = true, pluralItems = items.reversed())
      }
   }

   @Test
   fun parseStringsFile_should_reject_a_duplicate_name_across_a_string_and_a_plurals()
   {
      val failure = assertFailsWith<IllegalStateException> {
         parseStringsFile(
            """<resources><string name="cart_items">x</string><plurals name="cart_items"><item quantity="other">#</item></plurals></resources>""",
         )
      }

      assertEquals("Duplicate Android resource name 'cart_items' in one locale file.", failure.message)
   }

   @Test
   fun parser_should_import_localized_only_android_keys_even_when_missing_from_default_locale_file()
   {
      val resourceDir = createTempDirectory("translationtools-android-resources").toFile()
      writeStringsFile(resourceDir, "values", "<resources><string name=\"action_save\">Save</string></resources>")
      writeStringsFile(resourceDir, "values-nl", "<resources><string name=\"only_nl\">Alleen nl</string></resources>")

      val state = AndroidStringResourceParser().parse(listOf(resourceDir), defaultLocale = "en", keyOverrides = emptyMap(), projectPath = ":app")

      assertEquals(listOf("en", "nl"), state.locales)
      val onlyNl = state.entries.single { it.key == "only_nl" }
      assertEquals(null, onlyNl.valuesByLocale["en"])
      assertEquals("Alleen nl", onlyNl.valuesByLocale["nl"])
   }

   @Test
   fun parser_should_keep_empty_android_locale_directories_in_locale_metadata_and_report_warning()
   {
      val resourceDir = createTempDirectory("translationtools-android-resources").toFile()
      writeStringsFile(resourceDir, "values", "<resources><string name=\"action_save\">Save</string></resources>")
      writeStringsFile(resourceDir, "values-nl", "<resources></resources>")

      val state = AndroidStringResourceParser().parse(listOf(resourceDir), defaultLocale = "en", keyOverrides = emptyMap(), projectPath = ":app")

      assertEquals(listOf("en", "nl"), state.locales)
      assertTrue(state.warnings.any { it.contains("values-nl", ignoreCase = true) })
   }

   @Test
   fun parser_should_allow_migration_when_values_file_is_empty()
   {
      val resourceDir = createTempDirectory("translationtools-android-resources").toFile()
      writeStringsFile(resourceDir, "values", "<resources></resources>")
      writeStringsFile(resourceDir, "values-nl", "<resources><string name=\"action_save\">Opslaan</string></resources>")

      val state = AndroidStringResourceParser().parse(listOf(resourceDir), defaultLocale = "en", keyOverrides = emptyMap(), projectPath = ":app")

      val actionSave = state.entries.single { it.key == "action_save" }
      assertEquals(null, actionSave.valuesByLocale["en"])
      assertEquals("Opslaan", actionSave.valuesByLocale["nl"])
   }

   @Test
   fun parser_should_continue_with_warning_when_all_android_locale_directories_are_empty()
   {
      val resourceDir = createTempDirectory("translationtools-android-resources").toFile()
      writeStringsFile(resourceDir, "values", "<resources></resources>")
      writeStringsFile(resourceDir, "values-nl", "<resources></resources>")

      val state = AndroidStringResourceParser().parse(listOf(resourceDir), defaultLocale = "en", keyOverrides = emptyMap(), projectPath = ":app")

      assertEquals(emptyList(), state.entries)
      assertTrue(state.warnings.any { it.contains("empty translation payload") })
   }

   @Test
   fun parser_should_fail_when_no_android_locale_directories_with_matching_strings_xml_are_found()
   {
      val resourceDir = createTempDirectory("translationtools-android-resources").toFile()

      val exception = assertFailsWith<IllegalStateException> {
         AndroidStringResourceParser().parse(listOf(resourceDir), defaultLocale = "en", keyOverrides = emptyMap(), projectPath = ":app")
      }

      assertTrue(exception.message.orEmpty().contains("No Android locale directories"))
   }

   @Test
   fun resolveAndroidLocale_should_map_locale_folders()
   {
      assertEquals("en", resolveAndroidLocale("values", "en"))
      assertEquals("nl", resolveAndroidLocale("values-nl", "en"))
      assertEquals("pt-br", resolveAndroidLocale("values-pt-rBR", "en"))
      assertEquals(null, resolveAndroidLocale("values-night", "en"))
   }

   @Test
   fun parser_should_fail_when_multiple_android_directories_normalize_to_same_locale()
   {
      val resourceDirA = createTempDirectory("translationtools-android-resources-a").toFile()
      val resourceDirB = createTempDirectory("translationtools-android-resources-b").toFile()
      writeStringsFile(resourceDirA, "values-nl", "<resources><string name=\"action_save\">Opslaan</string></resources>")
      writeStringsFile(resourceDirB, "values-nl", "<resources><string name=\"action_cancel\">Annuleren</string></resources>")

      val exception = assertFailsWith<IllegalStateException> {
          discoverLocaleFiles(listOf(resourceDirA, resourceDirB), "en")
       }

      assertTrue(exception.message.orEmpty().contains("same locale"))
   }

   @Test
   fun parser_should_keep_android_resource_names_as_is_by_default()
   {
      val resourceDir = createTempDirectory("translationtools-android-resources").toFile()
      writeStringsFile(resourceDir, "values", "<resources><string name=\"action_save\">Save</string></resources>")

      val state = AndroidStringResourceParser().parse(listOf(resourceDir), defaultLocale = "en", keyOverrides = emptyMap(), projectPath = ":app")

      assertTrue(state.entries.any { it.key == "action_save" && it.origin == ":app:/strings.xml" })
   }

   @Test
   fun parser_should_fail_for_duplicate_resource_names_in_one_locale()
   {
      val exception = assertFailsWith<IllegalStateException> {
         parseStringsFile(
            """
            <resources>
               <string name="action_save">Save</string>
               <string name="action_save">Again</string>
            </resources>
            """.trimIndent(),
         )
      }

      assertTrue(exception.message.orEmpty().contains("Duplicate Android resource name"))
   }
}

private fun writeStringsFile(resourceDir: File, valuesDirectory: String, content: String)
{
   val directory = File(resourceDir, valuesDirectory)
   directory.mkdirs()
   File(directory, "strings.xml").writeText(content)
}
