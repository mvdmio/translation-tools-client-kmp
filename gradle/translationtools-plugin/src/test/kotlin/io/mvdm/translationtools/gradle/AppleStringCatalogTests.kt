package io.mvdm.translationtools.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class AppleStringCatalogTests
{
   @Test
   fun parse_should_send_the_key_as_source_value_when_the_source_localization_is_missing()
   {
      val root = createTempDirectory("translationtools-catalog-fallback").toFile()
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog(
            "en",
            key("Done", "pl" to unit("Gotowe")),
            key("greeting", "en" to unit("Hello", state = "needs_review"), extra = "\"comment\" : \"Shown on the home screen\","),
         ),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(listOf("en", "pl"), project.locales)
      assertEquals(
         mapOf(
            (":/Localizable.xcstrings" to "Done") to mapOf("en" to "Done", "pl" to "Gotowe"),
            (":/Localizable.xcstrings" to "greeting") to mapOf("en" to "Hello", "pl" to null),
         ),
         valuesByRef(project),
      )
      assertEquals(emptyList(), project.warnings)
   }

   @Test
   fun parse_should_send_an_empty_source_value_as_empty_and_not_fall_back_to_the_key()
   {
      val root = createTempDirectory("translationtools-catalog-empty-source").toFile()
      writeCatalog(root, "Localizable.xcstrings", catalog("en", key("blank", "en" to unit(""), "pl" to unit("Pusty"))))

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(
         mapOf((":/Localizable.xcstrings" to "blank") to mapOf("en" to "", "pl" to "Pusty")),
         valuesByRef(project),
      )
   }

   @Test
   fun parse_should_treat_a_string_unit_without_a_value_as_no_localization()
   {
      val root = createTempDirectory("translationtools-catalog-no-value").toFile()
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog(
            "en",
            key(
               "Done",
               "en" to """{"stringUnit" : {"state" : "new"}}""",
               "pl" to """{"stringUnit" : {"state" : "new"}}""",
            ),
         ),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(
         mapOf((":/Localizable.xcstrings" to "Done") to mapOf("en" to "Done", "pl" to null)),
         valuesByRef(project),
      )
   }

   @Test
   fun parse_should_send_values_in_every_review_state()
   {
      val root = createTempDirectory("translationtools-catalog-states").toFile()
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog(
            "en",
            key("a", "en" to unit("Alpha", state = "translated")),
            key("b", "en" to unit("Bravo", state = "needs_review")),
            key("c", "en" to unit("Charlie", state = "new")),
            key("d", "en" to unit("Delta", state = "stale")),
            key("e", "en" to """{"stringUnit" : {"value" : "Echo"}}"""),
         ),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(
         mapOf("a" to "Alpha", "b" to "Bravo", "c" to "Charlie", "d" to "Delta", "e" to "Echo"),
         valuesByRef(project).entries.associate { (ref, values) -> ref.second to values["en"] },
      )
   }

   @Test
   fun parse_should_skip_a_dont_translate_key_but_still_count_its_locales()
   {
      val root = createTempDirectory("translationtools-catalog-dont-translate").toFile()
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog(
            "en",
            key("brand", "en" to unit("Acme"), "de" to unit("Acme"), extra = "\"shouldTranslate\" : false,"),
            key("title", "en" to unit("Title")),
            key("subtitle", "en" to unit("Subtitle"), extra = "\"shouldTranslate\" : true,"),
         ),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(listOf("de", "en"), project.locales)
      assertEquals(
         mapOf(
            (":/Localizable.xcstrings" to "subtitle") to mapOf("de" to null, "en" to "Subtitle"),
            (":/Localizable.xcstrings" to "title") to mapOf("de" to null, "en" to "Title"),
         ),
         valuesByRef(project),
      )
   }

   @Test
   fun parse_should_normalize_catalog_locale_codes_like_lproj_directory_names()
   {
      val root = createTempDirectory("translationtools-catalog-locales").toFile()
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog("en", key("title", "pt-BR" to unit("Titulo"), "zh-Hans" to unit("标题"), "en" to unit("Title"))),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(listOf("en", "pt-br", "zh-hans"), project.locales)
      assertEquals(
         mapOf("en" to "Title", "pt-br" to "Titulo", "zh-hans" to "标题"),
         valuesByRef(project).values.single(),
      )
   }

   @Test
   fun parse_should_use_the_catalogs_own_source_language_and_not_the_default_locale_for_the_key_fallback()
   {
      val root = createTempDirectory("translationtools-catalog-source-language").toFile()
      writeCatalog(root, "Localizable.xcstrings", catalog("pt-BR", key("Done", "en" to unit("Finished"))))

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(
         mapOf((":/Localizable.xcstrings" to "Done") to mapOf("en" to "Finished", "pt-br" to "Done")),
         valuesByRef(project),
      )
   }

   @Test
   fun parse_should_send_a_value_that_varies_by_plural_as_a_whole_as_a_count_plural_in_cldr_order()
   {
      val root = createTempDirectory("translationtools-catalog-plural-whole").toFile()
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog(
            "en",
            key(
               "cart_items",
               "en" to pluralVariation("other" to "%lld items", "one" to "%lld item"),
               "pl" to pluralVariation("other" to "%lld rzeczy", "many" to "%lld rzeczy", "few" to "%lld rzeczy", "one" to "%lld rzecz"),
            ),
         ),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(
         mapOf(
            (":/Localizable.xcstrings" to "cart_items") to mapOf(
               "en" to "{count, plural, one {# item} other {# items}}",
               "pl" to "{count, plural, one {# rzecz} few {# rzeczy} many {# rzeczy} other {# rzeczy}}",
            ),
         ),
         valuesByRef(project),
      )
      assertEquals(emptyList(), project.warnings)
   }

   @Test
   fun parse_should_turn_every_integer_specifier_with_or_without_a_position_into_a_hash_in_the_whole_value_shape()
   {
      val specifiers = listOf("d", "i", "u", "ld", "li", "lu", "lld", "lli", "llu")
      val keys = specifiers.flatMap { specifier ->
         listOf(
            key("plain_$specifier", "en" to pluralVariation("other" to "%$specifier items")),
            key("positional_$specifier", "en" to pluralVariation("other" to "%1\$$specifier items")),
         )
      }
      val root = createTempDirectory("translationtools-catalog-plural-specifiers").toFile()
      writeCatalog(root, "Localizable.xcstrings", catalog("en", *keys.toTypedArray()))

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(
         specifiers.flatMap { listOf("plain_$it", "positional_$it") }.associateWith { "{count, plural, other {# items}}" },
         valuesByRef(project).entries.associate { (ref, values) -> ref.second to values["en"] },
      )
   }

   @Test
   fun parse_should_send_other_specifiers_in_the_whole_value_shape_as_written()
   {
      val root = createTempDirectory("translationtools-catalog-plural-other-specifiers").toFile()
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog("en", key("files", "en" to pluralVariation("one" to "%lld file named %@", "other" to "%hd files %arg %f %%d"))),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(
         mapOf("en" to "{count, plural, one {# file named %@} other {%hd files %arg %f %%d}}"),
         valuesByRef(project).values.single(),
      )
   }

   @Test
   fun parse_should_send_a_value_that_is_one_plural_substitution_as_a_plural_named_after_the_substitution()
   {
      val root = createTempDirectory("translationtools-catalog-plural-substitution").toFile()
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog(
            "en",
            key(
               "cart_items",
               "en" to substitutionLocalization("items", "lld", "other" to "%arg items", "one" to "%lld item", "zero" to "no %@ items"),
               "pl" to substitutionLocalization("items", "lld", "one" to "%1\$lld rzecz", "few" to "%lld rzeczy"),
            ),
         ),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(
         mapOf(
            "en" to "{items, plural, zero {no %@ items} one {# item} other {# items}}",
            "pl" to "{items, plural, one {# rzecz} few {# rzeczy}}",
         ),
         valuesByRef(project).values.single(),
      )
      assertEquals(emptyList(), project.warnings)
   }

   @Test
   fun parse_should_turn_only_the_substitutions_own_format_specifier_into_a_hash()
   {
      val root = createTempDirectory("translationtools-catalog-plural-substitution-specifier").toFile()
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog(
            "en",
            key("days", "en" to substitutionLocalization("days", "d", "one" to "%d day, %lld x, %ld y", "other" to "%d days, %arg")),
         ),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(
         mapOf("en" to "{days, plural, one {# day, %lld x, %ld y} other {# days, #}}"),
         valuesByRef(project).values.single(),
      )
   }

   @Test
   fun parse_should_send_a_plural_without_a_readable_form_as_no_value_and_without_a_warning()
   {
      val root = createTempDirectory("translationtools-catalog-plural-no-forms").toFile()
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog(
            "en",
            key(
               "cart_items",
               "en" to """{"variations" : {"plural" : {"one" : {"stringUnit" : {"state" : "new"}}, "bogus" : {"stringUnit" : {"value" : "x"}}}}}""",
               "pl" to substitutionLocalization("items", "lld"),
            ),
         ),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(mapOf("en" to null, "pl" to null), valuesByRef(project).values.single())
      assertEquals(emptyList(), project.warnings)
   }

   @Test
   fun parse_should_treat_a_localization_with_substitutions_but_plain_text_as_plain_text()
   {
      val root = createTempDirectory("translationtools-catalog-plain-with-substitutions").toFile()
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog(
            "en",
            key("plain", "en" to """{"stringUnit" : {"state" : "translated", "value" : "Just text"}, "substitutions" : {}}"""),
         ),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(mapOf("en" to "Just text"), valuesByRef(project).values.single())
      assertEquals(emptyList(), project.warnings)
   }

   @Test
   fun parse_should_skip_a_value_that_is_not_exactly_one_plural_substitution_and_still_send_the_keys_other_locales()
   {
      val root = createTempDirectory("translationtools-catalog-plural-shape").toFile()
      val items = pluralSubstitution("items", "lld", "one" to "%lld item", "other" to "%lld items")
      val folders = pluralSubstitution("folders", "lld", "one" to "%lld folder", "other" to "%lld folders")
      val path = File(root, "Localizable.xcstrings").path
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog(
            "en",
            key("around", "en" to substitutionUnit("You have %#@items@", items), "pl" to unit("Masz")),
            key("two", "en" to substitutionUnit("%#@items@ in %#@folders@", items, folders), "pl" to unit("Dwa")),
            key("not_plural", "en" to substitutionUnit("%#@items@", """"items" : {"formatSpecifier" : "lld"}"""), "pl" to unit("Nie")),
            key("missing", "en" to substitutionUnit("%#@items@"), "pl" to unit("Brak")),
         ),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(
         mapOf(
            (":/Localizable.xcstrings" to "around") to mapOf("en" to null, "pl" to "Masz"),
            (":/Localizable.xcstrings" to "two") to mapOf("en" to null, "pl" to "Dwa"),
            (":/Localizable.xcstrings" to "not_plural") to mapOf("en" to null, "pl" to "Nie"),
            (":/Localizable.xcstrings" to "missing") to mapOf("en" to null, "pl" to "Brak"),
         ),
         valuesByRef(project),
      )
      assertEquals(
         listOf("around", "two", "not_plural", "missing").map {
            "Skipped '$it' for 'en' in '$path': only a value that varies by plural as a whole, or is exactly one plural " +
               "substitution such as '%#@items@', syncs as a Plural."
         },
         project.warnings,
      )
   }

   @Test
   fun parse_should_skip_a_value_that_varies_by_device_or_nests_a_variation_with_its_own_warning()
   {
      val root = createTempDirectory("translationtools-catalog-device").toFile()
      val localizations = linkedMapOf(
         "device" to
            """{"variations" : {"device" : {"iphone" : {"stringUnit" : {"state" : "translated", "value" : "Tap"}}}}}""",
         "nested" to
            """{"variations" : {"plural" : {"one" : {"variations" : {"device" : {"mac" : {"stringUnit" : {"value" : "x"}}}}}}}}""",
         "device_and_plural" to
            """{"variations" : {"plural" : {"one" : {"stringUnit" : {"value" : "x"}}}, "device" : {}}}""",
         "substitution_device" to substitutionUnit(
            "%#@items@",
            """"items" : {"formatSpecifier" : "lld", "variations" : {"device" : {"iphone" : {"stringUnit" : {"value" : "x"}}}}}""",
         ),
         "substitution_nested" to substitutionUnit(
            "%#@items@",
            """"items" : {"formatSpecifier" : "lld", "variations" : {"plural" : {"one" : {"variations" : {"device" : {}}}}}}""",
         ),
         "device_beats_shape" to substitutionUnit(
            "You have %#@items@",
            """"items" : {"formatSpecifier" : "lld", "variations" : {"device" : {}}}""",
         ),
      )
      val path = File(root, "Localizable.xcstrings").path
      writeCatalog(
         root,
         "Localizable.xcstrings",
         catalog("en", *localizations.map { (name, json) -> key(name, "en" to json, "pl" to unit("Ok")) }.toTypedArray()),
      )

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(
         localizations.keys.associate { (":/Localizable.xcstrings" to it) to mapOf("en" to null, "pl" to "Ok") },
         valuesByRef(project),
      )
      assertEquals(
         localizations.keys.map { "Skipped '$it' for 'en' in '$path': only plain text and values that vary by plural sync." },
         project.warnings,
      )
   }

   @Test
   fun parse_should_name_each_catalog_origin_after_its_file_and_keep_it_apart_from_a_strings_file_of_the_same_name()
   {
      val root = createTempDirectory("translationtools-catalog-origins").toFile()
      writeCatalog(root, "Localizable.xcstrings", catalog("en", key("title", "en" to unit("From catalog"))))
      writeCatalog(root, "InfoPlist.xcstrings", catalog("en", key("title", "en" to unit("Info"))))
      File(root, "en.lproj").mkdirs()
      File(root, "en.lproj/Localizable.strings").writeText("\"title\" = \"From strings\";")

      val root2 = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")
      val sub = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":app")

      assertEquals(
         mapOf(
            (":/InfoPlist.xcstrings" to "title") to mapOf("en" to "Info"),
            (":/Localizable.strings" to "title") to mapOf("en" to "From strings"),
            (":/Localizable.xcstrings" to "title") to mapOf("en" to "From catalog"),
         ),
         valuesByRef(root2),
      )
      assertEquals(
         listOf(":app:/InfoPlist.xcstrings", ":app:/Localizable.strings", ":app:/Localizable.xcstrings"),
         sub.entries.map { it.origin },
      )
   }

   @Test
   fun parse_should_read_only_catalogs_that_are_direct_children_of_a_resource_directory()
   {
      val root = createTempDirectory("translationtools-catalog-recursion").toFile()
      File(root, "en.lproj").mkdirs()
      File(root, "en.lproj/Localizable.strings").writeText("\"k\" = \"v\";")
      writeCatalog(File(root, "en.lproj"), "Inside.xcstrings", catalog("en", key("inside", "en" to unit("x"))))
      writeCatalog(File(root, "nested/deeper"), "Nested.xcstrings", catalog("en", key("nested", "en" to unit("x"))))
      writeCatalog(root, "Top.xcstrings", catalog("en", key("top", "en" to unit("x"))))

      val discovery = discoverAppleLocaleFiles(listOf(root), "en")
      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(listOf(File(root, "Top.xcstrings")), discovery.catalogs)
      assertEquals(listOf(":/Localizable.strings", ":/Top.xcstrings"), project.entries.map { it.origin })
   }

   @Test
   fun parse_should_accept_a_directory_that_holds_only_a_catalog_without_the_empty_result_warning()
   {
      val root = createTempDirectory("translationtools-catalog-only").toFile()
      writeCatalog(root, "Localizable.xcstrings", catalog("en", key("title", "en" to unit("Title"))))

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(listOf(":/Localizable.xcstrings"), project.entries.map { it.origin })
      assertEquals(emptyList(), project.warnings)
   }

   @Test
   fun parse_should_warn_that_nothing_was_found_when_there_are_neither_lproj_files_nor_catalogs()
   {
      val root = createTempDirectory("translationtools-catalog-nothing").toFile()
      File(root, "nested").mkdirs()
      writeCatalog(File(root, "nested"), "Nested.xcstrings", catalog("en", key("nested", "en" to unit("x"))))

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(emptyList(), project.entries)
      assertEquals(
         listOf(
            "No .lproj directories with .strings or .stringsdict files, and no .xcstrings files, " +
               "were found under the configured appleResources.resourceDirectories.",
         ),
         project.warnings,
      )
   }

   @Test
   fun parse_should_skip_an_unreadable_catalog_with_a_warning_and_still_read_the_other_apple_files()
   {
      val unreadable = mapOf(
         "not json" to "not json",
         "an array root" to "[]",
         "a missing strings object" to """{"sourceLanguage" : "en"}""",
         "a strings member that is not an object" to """{"sourceLanguage" : "en", "strings" : []}""",
         "a missing sourceLanguage" to """{"strings" : {}}""",
         "an empty sourceLanguage" to """{"sourceLanguage" : "", "strings" : {}}""",
         "a sourceLanguage that is not a string" to """{"sourceLanguage" : 5, "strings" : {}}""",
      )

      unreadable.forEach { (description, content) ->
         val root = createTempDirectory("translationtools-catalog-unreadable").toFile()
         File(root, "en.lproj").mkdirs()
         File(root, "en.lproj/Localizable.strings").writeText("\"k\" = \"v\";")
         writeCatalog(root, "Broken.xcstrings", content)
         writeCatalog(root, "Good.xcstrings", catalog("en", key("title", "en" to unit("Title"))))

         val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

         assertEquals(
            listOf("Skipped '${File(root, "Broken.xcstrings").path}': it is not a readable String Catalog."),
            project.warnings,
            description,
         )
         assertEquals(listOf(":/Good.xcstrings", ":/Localizable.strings"), project.entries.map { it.origin }, description)
      }
   }

   @Test
   fun parse_should_give_an_unreadable_catalog_no_locales()
   {
      val root = createTempDirectory("translationtools-catalog-unreadable-locales").toFile()
      writeCatalog(root, "Broken.xcstrings", "not json")
      writeCatalog(root, "Good.xcstrings", catalog("en", key("title", "en" to unit("Title"))))

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(listOf("en"), project.locales)
   }

   @Test
   fun parse_should_let_the_catalog_whose_path_sorts_last_win_for_a_shared_origin_and_keep_values_it_lacks()
   {
      val first = createTempDirectory("translationtools-catalog-dup-a").toFile()
      val second = createTempDirectory("translationtools-catalog-dup-b").toFile()
      val (early, late) = listOf(first, second).sortedBy { File(it, "Localizable.xcstrings").path }
      writeCatalog(
         early,
         "Localizable.xcstrings",
         catalog("en", key("title", "en" to unit("Early title"), "pl" to unit("Wczesny")), key("only_early", "en" to unit("Kept"))),
      )
      writeCatalog(late, "Localizable.xcstrings", catalog("en", key("title", "en" to unit("Late title")), key("only_late", "en" to unit("Late"))))

      val project = AppleStringResourceParser().parse(listOf(late, early), defaultLocale = "en", projectPath = ":")

      assertEquals(
         mapOf(
            (":/Localizable.xcstrings" to "only_early") to mapOf("en" to "Kept", "pl" to null),
            (":/Localizable.xcstrings" to "only_late") to mapOf("en" to "Late", "pl" to null),
            (":/Localizable.xcstrings" to "title") to mapOf("en" to "Late title", "pl" to "Wczesny"),
         ),
         valuesByRef(project),
      )
   }
}

private fun valuesByRef(project: AppleTranslationProject): Map<Pair<String, String>, Map<String, String?>> =
   project.entries.associate { (it.origin to it.key) to it.valuesByLocale }

private fun writeCatalog(directory: File, fileName: String, content: String)
{
   directory.mkdirs()
   File(directory, fileName).writeText(content)
}

private fun catalog(sourceLanguage: String, vararg keys: String): String =
   """{"sourceLanguage" : "$sourceLanguage", "strings" : {${keys.joinToString(",")}}, "version" : "1.0"}"""

private fun unit(value: String, state: String = "translated"): String =
   """{"stringUnit" : {"state" : "$state", "value" : "$value"}}"""

/** A catalog key named [name] with its [localizations] (locale to JSON); [extra] is raw members that end with a comma. */
private fun key(name: String, vararg localizations: Pair<String, String>, extra: String = ""): String =
   "\"$name\" : {$extra \"localizations\" : {${localizations.joinToString(",") { "\"${it.first}\" : ${it.second}" }}}}"

private fun pluralForms(forms: Array<out Pair<String, String>>): String =
   forms.joinToString(",") { """"${it.first}" : {"stringUnit" : {"state" : "translated", "value" : "${it.second}"}}""" }

/** A localization that varies by plural as a whole, with [forms] (category to text) in the given order. */
private fun pluralVariation(vararg forms: Pair<String, String>): String =
   """{"variations" : {"plural" : {${pluralForms(forms)}}}}"""

/** The `substitutions` entry named [name] that varies by plural; [forms] are category to text. */
private fun pluralSubstitution(name: String, formatSpecifier: String, vararg forms: Pair<String, String>): String =
   """"$name" : {"formatSpecifier" : "$formatSpecifier", "variations" : {"plural" : {${pluralForms(forms)}}}}"""

/** A localization whose value is [format] with the given raw `substitutions` entries. */
private fun substitutionUnit(format: String, vararg substitutions: String): String =
   """{"stringUnit" : {"state" : "translated", "value" : "$format"}, "substitutions" : {${substitutions.joinToString(",")}}}"""

/** A localization whose value is exactly `%#@name@` and whose [name] substitution varies by plural. */
private fun substitutionLocalization(name: String, formatSpecifier: String, vararg forms: Pair<String, String>): String =
   substitutionUnit("%#@$name@", pluralSubstitution(name, formatSpecifier, *forms))
