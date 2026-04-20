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
            <plurals name="cart_items"><item quantity="one">1 item</item></plurals>
            <string-array name="menu_entries"><item>Home</item></string-array>
         </resources>
         """.trimIndent(),
      )

      assertEquals(listOf(ParsedAndroidResourceValue("action_save", "Save", true), ParsedAndroidResourceValue("hidden_label", "Hidden", false)), parsed.imported)
      assertEquals(
         listOf(
            "cart_items: plurals are not supported",
            "menu_entries: string-array is not supported",
         ),
         parsed.skipped.map { "${it.resourceName}: ${it.reason}" },
      )
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
