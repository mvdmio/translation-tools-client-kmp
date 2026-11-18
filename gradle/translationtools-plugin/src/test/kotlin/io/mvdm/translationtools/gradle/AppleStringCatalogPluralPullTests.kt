package io.mvdm.translationtools.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class AppleStringCatalogPluralPullTests
{
   @Test
   fun write_should_change_only_the_remote_form_that_differs_in_a_whole_value_plural()
   {
      val file = tempCatalog(xcodeLayoutFixture())

      val warnings = writeAppleCatalogFile(file, listOf(item("cart_items", "en" to "{count, plural, one {# item} other {# things}}")))

      assertEquals(emptyList(), warnings)
      assertEquals(xcodeLayoutFixture().replace("\"value\" : \"%lld items\"", "\"value\" : \"%lld things\""), file.readText())
   }

   @Test
   fun write_should_mark_a_changed_form_translated_and_keep_the_state_of_an_unchanged_one()
   {
      val file = tempCatalog(catalogOf("k", "en" to wholePlural("needs_review" to ("one" to "%lld item"), "needs_review" to ("other" to "%lld items"))))

      writeAppleCatalogFile(file, listOf(item("k", "en" to "{count, plural, one {# item} other {# things}}")))

      assertEquals(
         catalogOf("k", "en" to wholePlural("needs_review" to ("one" to "%lld item"), "translated" to ("other" to "%lld things"))),
         file.readText(),
      )
   }

   @Test
   fun write_should_keep_the_substitution_name_format_specifier_and_arg_num_remove_missing_categories_and_add_new_ones_in_place()
   {
      val file = tempCatalog(xcodeLayoutFixture())

      writeAppleCatalogFile(file, listOf(item("file_count", "en" to "{count, plural, few {# few files} other {# files}}")))

      val after = strings(file).obj("file_count", "localizations", "en")
      assertEquals(Json.parseToJsonElement("""{"state":"translated","value":"%#@files@"}"""), after.obj("stringUnit"))
      val substitution = after.obj("substitutions", "files")
      assertEquals(JsonPrimitive(1), substitution.getValue("argNum"))
      assertEquals(JsonPrimitive("lld"), substitution.getValue("formatSpecifier"))
      val forms = substitution.obj("variations", "plural")
      assertEquals(listOf("few", "other"), forms.keys.toList())
      assertEquals("%arg few files", formValue(forms, "few"))
      assertEquals("translated", formState(forms, "few"))
      assertEquals("%arg files", formValue(forms, "other"))
   }

   @Test
   fun write_should_turn_a_plain_value_and_a_missing_value_into_the_whole_value_shape_with_lld()
   {
      val file = tempCatalog(xcodeLayoutFixture())

      writeAppleCatalogFile(
         file,
         listOf(
            item("greeting", "en" to "{count, plural, one {# greeting} other {# greetings}}"),
            item("Done", "en" to "{count, plural, one {# done} other {# dones}}"),
         ),
      )

      val strings = strings(file)
      assertEquals(
         Json.parseToJsonElement(
            """{"variations":{"plural":{"one":{"stringUnit":{"state":"translated","value":"%lld greeting"}},"other":{"stringUnit":{"state":"translated","value":"%lld greetings"}}}}}""",
         ),
         strings.obj("greeting", "localizations").getValue("en"),
      )
      assertEquals(listOf("en", "pl"), strings.obj("Done", "localizations").keys.toList())
      assertEquals("%lld dones", formValue(strings.obj("Done", "localizations", "en", "variations", "plural"), "other"))
   }

   @Test
   fun write_should_turn_a_substitution_value_into_the_whole_value_shape_for_a_locale_that_has_none()
   {
      val file = tempCatalog(xcodeLayoutFixture())

      writeAppleCatalogFile(file, listOf(item("file_count", "pl" to "{count, plural, one {# plik} other {# plikow}}")))

      val locales = strings(file).obj("file_count", "localizations")
      assertEquals(listOf("en", "pl"), locales.keys.toList())
      assertEquals(
         Json.parseToJsonElement(
            """{"variations":{"plural":{"one":{"stringUnit":{"state":"translated","value":"%lld plik"}},"other":{"stringUnit":{"state":"translated","value":"%lld plikow"}}}}}""",
         ),
         locales.getValue("pl"),
      )
   }

   @Test
   fun write_should_write_the_number_token_the_locale_already_uses()
   {
      val file = tempCatalog(
         catalogOf(
            "k" to ("en" to wholePlural("translated" to ("one" to "%d item"), "translated" to ("other" to "%d items"))),
            "s" to ("en" to substitutionPlural("files", "lld", "translated" to ("other" to "%lld files"))),
            "t" to ("en" to substitutionPlural("files", "lld", "translated" to ("other" to "%arg files"))),
         ),
      )

      writeAppleCatalogFile(
         file,
         listOf(
            item("k", "en" to "{count, plural, one {# item} other {# things}}"),
            item("s", "en" to "{files, plural, one {# file} other {# things}}"),
            item("t", "en" to "{files, plural, one {# file} other {# things}}"),
         ),
      )

      val strings = strings(file)
      val k = strings.obj("k", "localizations", "en", "variations", "plural")
      assertEquals("%d item", formValue(k, "one"))
      assertEquals("%d things", formValue(k, "other"))
      val s = strings.obj("s", "localizations", "en", "substitutions", "files", "variations", "plural")
      assertEquals("%lld file", formValue(s, "one"))
      assertEquals("%lld things", formValue(s, "other"))
      assertEquals("%arg file", formValue(strings.obj("t", "localizations", "en", "substitutions", "files", "variations", "plural"), "one"))
   }

   @Test
   fun write_should_fall_back_to_the_source_language_token_then_lld_in_the_whole_value_shape()
   {
      val file = tempCatalog(
         catalogOf(
            "k" to ("en" to wholePlural("translated" to ("other" to "%i items"))),
            "k" to ("pl" to plainLocalization("Rzeczy")),
            "bare" to ("en" to wholePlural("translated" to ("other" to "items"))),
            "bare" to ("pl" to plainLocalization("Rzeczy")),
            "arg" to ("en" to substitutionPlural("files", "lld", "translated" to ("other" to "%arg files"))),
            "arg" to ("pl" to plainLocalization("Pliki")),
         ),
      )

      writeAppleCatalogFile(
         file,
         listOf(
            item("k", "pl" to "{count, plural, other {# rzeczy}}"),
            item("bare", "pl" to "{count, plural, other {# rzeczy}}"),
            item("arg", "pl" to "{count, plural, other {# plikow}}"),
         ),
      )

      val strings = strings(file)
      assertEquals("%i rzeczy", formValue(strings.obj("k", "localizations", "pl", "variations", "plural"), "other"))
      assertEquals("%lld rzeczy", formValue(strings.obj("bare", "localizations", "pl", "variations", "plural"), "other"))
      assertEquals("%lld plikow", formValue(strings.obj("arg", "localizations", "pl", "variations", "plural"), "other"))
   }

   @Test
   fun write_should_use_arg_in_the_substitution_shape_when_neither_the_locale_nor_the_source_language_holds_a_token()
   {
      val file = tempCatalog(catalogOf("s" to ("en" to substitutionPlural("files", "lld", "translated" to ("other" to "some files")))))

      writeAppleCatalogFile(file, listOf(item("s", "en" to "{files, plural, one {# file} other {# files}}")))

      val forms = strings(file).obj("s", "localizations", "en", "substitutions", "files", "variations", "plural")
      assertEquals("%arg file", formValue(forms, "one"))
      assertEquals("%arg files", formValue(forms, "other"))
   }

   @Test
   fun write_should_give_a_form_with_a_positional_specifier_the_number_token_with_position_one()
   {
      val file = tempCatalog(catalogOf("k" to ("en" to wholePlural("translated" to ("other" to "%lld items")))))

      writeAppleCatalogFile(file, listOf(item("k", "en" to "{count, plural, one {# of %2\$@} other {# in %2\$@ and #}}")))

      val forms = strings(file).obj("k", "localizations", "en", "variations", "plural")
      assertEquals("%1\$lld of %2\$@", formValue(forms, "one"))
      assertEquals("%1\$lld in %2\$@ and %1\$lld", formValue(forms, "other"))
   }

   @Test
   fun write_should_write_an_exact_zero_form_as_the_zero_category_after_the_existing_ones()
   {
      val file = tempCatalog(xcodeLayoutFixture())

      writeAppleCatalogFile(file, listOf(item("cart_items", "en" to "{count, plural, =0 {none} one {# item} other {# items}}")))

      val forms = strings(file).obj("cart_items", "localizations", "en", "variations", "plural")
      assertEquals(listOf("one", "other", "zero"), forms.keys.toList())
      assertEquals("none", formValue(forms, "zero"))
      assertEquals("translated", formState(forms, "zero"))
   }

   @Test
   fun write_should_keep_the_local_value_and_warn_for_an_exact_case_other_than_zero_and_still_write_the_other_locales()
   {
      val file = tempCatalog(xcodeLayoutFixture())

      val warnings = writeAppleCatalogFile(
         file,
         listOf(
            item(
               "cart_items",
               "en" to "{count, plural, =1 {one item} other {# items}}",
               "pl" to "{count, plural, one {# rzecz} few {# rzeczy} many {# rzeczy} other {# rzeczy!}}",
            ),
         ),
      )

      assertEquals(
         listOf("Kept local 'cart_items' in '${file.path}': the value for 'en' in TranslationTools is not a Plural that a String Catalog can hold."),
         warnings,
      )
      val before = (Json.parseToJsonElement(xcodeLayoutFixture()) as JsonObject).obj("strings", "cart_items", "localizations")
      val after = strings(file).obj("cart_items", "localizations")
      assertEquals(before.getValue("en"), after.getValue("en"))
      assertEquals("%lld rzeczy!", formValue(after.obj("pl", "variations", "plural"), "other"))
      assertEquals("%lld rzecz", formValue(after.obj("pl", "variations", "plural"), "one"))
   }

   @Test
   fun write_should_keep_the_file_bytes_when_the_only_remote_value_is_an_exact_case_plural()
   {
      val file = tempCatalog(xcodeLayoutFixture())

      writeAppleCatalogFile(file, listOf(item("cart_items", "en" to "{count, plural, =1 {one item} other {# items}}")))

      assertEquals(xcodeLayoutFixture(), file.readText())
   }

   @Test
   fun write_should_add_a_new_plural_key_in_the_whole_value_shape_marked_manual()
   {
      val file = tempCatalog(catalogText("a" to "\"extractionState\" : \"manual\""))

      val warnings = writeAppleCatalogFile(file, listOf(item("inbox_messages", "en" to "{count, plural, one {# message} other {# messages}}")))

      assertEquals(emptyList(), warnings)
      assertEquals(
         """
         {
           "sourceLanguage" : "en",
           "strings" : {
             "a" : {
               "extractionState" : "manual"
             },
             "inbox_messages" : {
               "extractionState" : "manual",
               "localizations" : {
                 "en" : {
                   "variations" : {
                     "plural" : {
                       "one" : {
                         "stringUnit" : {
                           "state" : "translated",
                           "value" : "%lld message"
                         }
                       },
                       "other" : {
                         "stringUnit" : {
                           "state" : "translated",
                           "value" : "%lld messages"
                         }
                       }
                     }
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
   fun write_should_not_add_a_value_to_a_new_key_for_a_plural_a_catalog_cannot_hold_but_write_its_other_locales()
   {
      val file = tempCatalog(catalogText("a" to "\"extractionState\" : \"manual\""))

      val warnings = writeAppleCatalogFile(file, listOf(item("b", "en" to "{count, plural, =1 {one} other {many}}", "pl" to "Dwa")))

      assertEquals(
         listOf(
            "Kept local 'b' in '${file.path}': the value for 'en' in TranslationTools is not a Plural that a String Catalog can hold.",
            newLocaleWarning("pl", file),
         ),
         warnings,
      )
      assertEquals(listOf("pl"), strings(file).obj("b", "localizations").keys.toList())
   }

   @Test
   fun write_should_leave_a_plural_equal_to_the_value_push_sends_unchanged_with_its_state_and_its_bytes()
   {
      val original = catalogOf("k", "en" to wholePlural("needs_review" to ("one" to "%d item"), "needs_review" to ("other" to "%d items")))
      val file = tempCatalog(original)
      file.setLastModified(1_000_000_000_000L)

      val warnings = writeAppleCatalogFile(file, listOf(item("k", "en" to "{count, plural, one {# item} other {# items}}")))

      assertEquals(emptyList(), warnings)
      assertEquals(original, file.readText())
      assertEquals(1_000_000_000_000L, file.lastModified())
   }

   @Test
   fun write_should_leave_the_fixture_unchanged_when_pull_returns_exactly_what_push_sent()
   {
      val file = tempCatalog(xcodeLayoutFixture())
      val catalog = (parseAppleStringCatalog(xcodeLayoutFixture(), file.path) as ParsedAppleStringCatalog.Readable).catalog
      val items = catalog.keys.filter { it.shouldTranslate }.map { key ->
         PulledTranslationItem(
            origin = ":/Localizable.xcstrings",
            key = key.key,
            valuesByLocale = catalog.locales.associateWith { locale -> (catalog.pushValue(key, locale) as? CatalogPushValue.Value)?.text },
         )
      }
      file.setLastModified(1_000_000_000_000L)

      val warnings = writeAppleCatalogFile(file, items)

      assertEquals(emptyList(), warnings)
      assertEquals(xcodeLayoutFixture(), file.readText())
      assertEquals(1_000_000_000_000L, file.lastModified())
   }

   @Test
   fun write_should_replace_a_local_plural_with_a_remote_plain_value_and_a_plain_value_with_a_remote_plural()
   {
      val file = tempCatalog(
         catalogOf(
            "a" to ("en" to wholePlural("translated" to ("other" to "%lld items"))),
            "b" to ("en" to plainLocalization("Hello")),
         ),
      )

      writeAppleCatalogFile(file, listOf(item("a", "en" to "Items"), item("b", "en" to "{count, plural, other {# hellos}}")))

      val strings = strings(file)
      assertEquals(Json.parseToJsonElement("""{"stringUnit":{"state":"translated","value":"Items"}}"""), strings.obj("a", "localizations").getValue("en"))
      assertEquals(
         Json.parseToJsonElement("""{"variations":{"plural":{"other":{"stringUnit":{"state":"translated","value":"%lld hellos"}}}}}"""),
         strings.obj("b", "localizations").getValue("en"),
      )
   }
}

private fun strings(file: java.io.File): JsonObject = (Json.parseToJsonElement(file.readText()) as JsonObject).obj("strings")

private fun JsonObject.obj(vararg path: String): JsonObject = path.fold(this) { node, name -> node.getValue(name) as JsonObject }

private fun formValue(forms: JsonObject, category: String): String =
   (forms.obj(category, "stringUnit").getValue("value") as JsonPrimitive).content

private fun formState(forms: JsonObject, category: String): String =
   (forms.obj(category, "stringUnit").getValue("state") as JsonPrimitive).content

/** A localization as JSON text with `state to (category to text)` forms, in the order given. */
private fun wholePlural(vararg forms: Pair<String, Pair<String, String>>): String =
   """{"variations":{"plural":${pluralObject(forms)}}}"""

private fun substitutionPlural(name: String, formatSpecifier: String, vararg forms: Pair<String, Pair<String, String>>): String =
   """{"stringUnit":{"state":"translated","value":"%#@$name@"},"substitutions":{"$name":{"argNum":1,"formatSpecifier":"$formatSpecifier","variations":{"plural":${pluralObject(forms)}}}}}"""

private fun plainLocalization(value: String): String = """{"stringUnit":{"state":"translated","value":"$value"}}"""

private fun pluralObject(forms: Array<out Pair<String, Pair<String, String>>>): String =
   forms.joinToString(",", "{", "}") { (state, form) -> """"${form.first}":{"stringUnit":{"state":"$state","value":"${form.second}"}}""" }

/** A catalog in Xcode's layout with one Key and the localization JSON of [locale]. */
private fun catalogOf(key: String, locale: Pair<String, String>): String = catalogOf(key to locale)

/** A catalog in Xcode's layout from `key to (locale to localization JSON)` entries, in the order given. */
private fun catalogOf(vararg localizations: Pair<String, Pair<String, String>>): String
{
   val keys = linkedMapOf<String, MutableMap<String, JsonElement>>()
   localizations.forEach { (key, locale) -> keys.getOrPut(key) { linkedMapOf() }[locale.first] = Json.parseToJsonElement(locale.second) }
   val strings = JsonObject(keys.mapValues { (_, locales) -> JsonObject(mapOf("localizations" to JsonObject(locales))) })
   return renderAppleCatalog(JsonObject(mapOf("sourceLanguage" to JsonPrimitive("en"), "strings" to strings, "version" to JsonPrimitive("1.0"))))
}
