package io.mvdm.translationtools.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class AppleStringCatalogWriterTests
{
   @Test
   fun render_should_write_the_checked_in_xcode_layout_back_to_the_same_bytes()
   {
      val fixture = xcodeLayoutFixture()

      val root = Json.parseToJsonElement(fixture) as JsonObject

      assertEquals(fixture, renderAppleCatalog(root))
   }

   @Test
   fun render_should_write_empty_containers_the_way_xcode_does_and_end_with_a_newline()
   {
      val root = Json.parseToJsonElement("""{"sourceLanguage":"en","strings":{},"list":[],"version":"1.0"}""") as JsonObject

      assertEquals(
         "{\n  \"sourceLanguage\" : \"en\",\n  \"strings\" : {\n\n  },\n  \"list\" : [\n\n  ],\n  \"version\" : \"1.0\"\n}\n",
         renderAppleCatalog(root),
      )
   }

   @Test
   fun render_should_escape_only_quotes_backslashes_and_control_characters()
   {
      val value = "a\"b\\c\nd\te\u0001f/gé–"
      val root = JsonObject(mapOf("value" to kotlinx.serialization.json.JsonPrimitive(value)))

      assertEquals("{\n  \"value\" : \"a\\\"b\\\\c\\nd\\te\\u0001f/gé–\"\n}\n", renderAppleCatalog(root))
   }

   @Test
   fun write_should_change_only_the_pulled_values_lines_of_the_fixture()
   {
      val file = tempCatalog(xcodeLayoutFixture())

      val warnings = writeAppleCatalogFile(file, listOf(item("Done", "pl" to "Zrobione")))

      assertEquals(emptyList(), warnings)
      val before = xcodeLayoutFixture().lines()
      val after = file.readText().lines()
      assertEquals(before.size, after.size)
      assertEquals(
         listOf(
            "            \"state\" : \"needs_review\"," to "            \"state\" : \"translated\",",
            "            \"value\" : \"Gotowe\"" to "            \"value\" : \"Zrobione\"",
         ),
         before.zip(after).filter { (old, new) -> old != new },
      )
   }

   @Test
   fun write_should_place_a_new_key_before_the_first_key_that_sorts_after_it_and_sort_its_new_members()
   {
      val file = tempCatalog(catalogText("a" to "\"extractionState\" : \"manual\"", "c" to "\"extractionState\" : \"manual\""))

      val warnings = writeAppleCatalogFile(file, listOf(item("b", "en" to "Bee", "pl" to "Pszczola"), item("z", "en" to "Zed")))

      assertEquals(listOf(newLocaleWarning("pl", file)), warnings)
      assertEquals(
         """
         {
           "sourceLanguage" : "en",
           "strings" : {
             "a" : {
               "extractionState" : "manual"
             },
             "b" : {
               "extractionState" : "manual",
               "localizations" : {
                 "en" : {
                   "stringUnit" : {
                     "state" : "translated",
                     "value" : "Bee"
                   }
                 },
                 "pl" : {
                   "stringUnit" : {
                     "state" : "translated",
                     "value" : "Pszczola"
                   }
                 }
               }
             },
             "c" : {
               "extractionState" : "manual"
             },
             "z" : {
               "extractionState" : "manual",
               "localizations" : {
                 "en" : {
                   "stringUnit" : {
                     "state" : "translated",
                     "value" : "Zed"
                   }
                 }
               }
             }
           },
           "version" : "1.0"
         }

         """.trimIndent(),
         file.readText(),
      )
   }

   @Test
   fun write_should_keep_the_member_name_of_an_existing_localization_and_write_a_new_locale_in_xcode_casing()
   {
      val file = tempCatalog(
         """
         {
           "sourceLanguage" : "en",
           "strings" : {
             "k" : {
               "localizations" : {
                 "pt_BR" : {
                   "stringUnit" : {
                     "state" : "translated",
                     "value" : "Antigo"
                   }
                 }
               }
             },
             "other" : {

             }
           },
           "version" : "1.0"
         }

         """.trimIndent(),
      )

      val warnings = writeAppleCatalogFile(file, listOf(item("k", "pt-br" to "Novo", "zh-hans" to "x"), item("other", "zh-hans" to "y")))

      assertEquals(listOf(newLocaleWarning("zh-hans", file)), warnings)
      assertEquals(
         """
         {
           "sourceLanguage" : "en",
           "strings" : {
             "k" : {
               "localizations" : {
                 "pt_BR" : {
                   "stringUnit" : {
                     "state" : "translated",
                     "value" : "Novo"
                   }
                 },
                 "zh-Hans" : {
                   "stringUnit" : {
                     "state" : "translated",
                     "value" : "x"
                   }
                 }
               }
             },
             "other" : {
               "localizations" : {
                 "zh-Hans" : {
                   "stringUnit" : {
                     "state" : "translated",
                     "value" : "y"
                   }
                 }
               }
             }
           },
           "version" : "1.0"
         }

         """.trimIndent(),
         file.readText(),
      )
   }

   @Test
   fun write_should_leave_a_file_in_another_layout_alone_when_nothing_changes_and_rewrite_it_in_xcode_layout_when_it_does()
   {
      val compact = """{"sourceLanguage":"en","strings":{"k":{"localizations":{"en":{"stringUnit":{"state":"needs_review","value":"Hi"}}}}},"version":"1.0"}"""
      val file = tempCatalog(compact)

      writeAppleCatalogFile(file, listOf(item("k", "en" to "Hi")))
      assertEquals(compact, file.readText())

      writeAppleCatalogFile(file, listOf(item("k", "en" to "Hello")))
      assertEquals(
         """
         {
           "sourceLanguage" : "en",
           "strings" : {
             "k" : {
               "localizations" : {
                 "en" : {
                   "stringUnit" : {
                     "state" : "translated",
                     "value" : "Hello"
                   }
                 }
               }
             }
           },
           "version" : "1.0"
         }

         """.trimIndent(),
         file.readText(),
      )
   }

   @Test
   fun write_should_leave_an_unreadable_file_unchanged_and_say_so()
   {
      val file = tempCatalog("not json")

      val warnings = writeAppleCatalogFile(file, listOf(item("k", "en" to "Hi")))

      assertEquals("not json", file.readText())
      assertEquals(listOf("Left '${file.path}' unchanged: it is not a readable String Catalog."), warnings)
   }

   @Test
   fun write_should_add_a_key_without_localizations_when_its_only_value_equals_the_key_and_skip_keys_with_only_null_values()
   {
      val file = tempCatalog(catalogText("a" to "\"extractionState\" : \"manual\""))

      val warnings = writeAppleCatalogFile(
         file,
         listOf(item("Same", "en" to "Same"), item("nulls", "en" to null)),
      )

      assertEquals(emptyList(), warnings)
      assertEquals(catalogText("Same" to "\"extractionState\" : \"manual\"", "a" to "\"extractionState\" : \"manual\""), file.readText())
   }
}

internal fun xcodeLayoutFixture(): String =
   AppleStringCatalogWriterTests::class.java.getResource("/xcode-layout.xcstrings")!!.readText()

internal fun newLocaleWarning(locale: String, file: File): String =
   "Added locale '$locale' to '${file.path}'. Add '$locale' (or its region) to your Xcode project's knownRegions to bundle it."

internal fun tempCatalog(content: String): File
{
   val file = File(createTempDirectory("translationtools-catalog-writer").toFile(), "Localizable.xcstrings")
   file.writeText(content)
   return file
}

internal fun item(key: String, vararg values: Pair<String, String?>) =
   PulledTranslationItem(origin = ":/Localizable.xcstrings", key = key, valuesByLocale = values.toMap())

internal fun catalogText(vararg keys: Pair<String, String>): String =
   buildString {
      append("{\n  \"sourceLanguage\" : \"en\",\n  \"strings\" : {\n")
      append(keys.joinToString(",\n") { (key, member) -> "    \"$key\" : {\n      $member\n    }" })
      append("\n  },\n  \"version\" : \"1.0\"\n}\n")
   }
