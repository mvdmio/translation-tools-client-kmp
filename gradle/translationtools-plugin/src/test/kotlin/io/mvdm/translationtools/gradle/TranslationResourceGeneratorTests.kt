package io.mvdm.translationtools.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationResourceGeneratorTests
{
   private val baseProject = AndroidTranslationProject(
      defaultLocale = "en",
      locales = listOf("en", "nl"),
      entries = emptyList(),
      warnings = emptyList(),
   )

   @Test
   fun sanitizeKey_should_handle_common_cases()
   {
      assertEquals("home_title", sanitizeResourcePropertyName("home.title"))
      assertEquals("checkout_primary_cta", sanitizeResourcePropertyName("checkout/primary-cta"))
      assertEquals("key_404_title", sanitizeResourcePropertyName("404.title"))
      assertEquals("object_", sanitizeResourcePropertyName("object"))
      assertEquals("key", sanitizeResourcePropertyName("---"))
   }

   @Test
   fun generate_should_add_stable_hash_suffix_for_collisions()
   {
      val project = baseProject.copy(
         locales = listOf("en"),
         entries = listOf(
            AndroidTranslationEntry(":app:/strings.xml", "home.title", "home.title", mapOf("en" to "Home"), "strings.xml", true),
            AndroidTranslationEntry(":app:/strings.xml", "home-title", "home-title", mapOf("en" to "Home dash"), "strings.xml", true),
         ),
      )

      val result = renderTranslationResources(
         project = project,
         packageName = "com.example.translations",
         placeholderGlobals = emptyList(),
      )

      assertTrue(result.contains("home_title__"))
      assertTrue(result.contains("key = \"home.title\""))
      assertTrue(result.contains("key = \"home-title\""))
      assertTrue(result.contains("TranslationRef("))
    }

   @Test
   fun generate_should_sort_entries_and_embed_default_locale_fallbacks()
   {
      val project = baseProject.copy(
         entries = listOf(
            AndroidTranslationEntry(":app:/strings.xml", "z.key", "z.key", mapOf("en" to "Zee", "nl" to "Zed"), "strings.xml", true),
            AndroidTranslationEntry(":app:/strings.xml", "a.key", "a.key", mapOf("en" to "Aye", "nl" to "A"), "strings.xml", true),
         ),
      )

      val result = renderTranslationResources(
         project = project,
         packageName = "com.example.translations",
         placeholderGlobals = emptyList(),
      )

      assertTrue(result.indexOf("val a_key") < result.indexOf("val z_key"))
      assertTrue(result.contains("fallback = \"Aye\""))
      assertTrue(result.contains("fallback = \"Zee\""))
   }

   @Test
   fun generate_should_emit_flat_translations_object_without_nested_string_wrapper()
   {
      val project = baseProject.copy(
         locales = listOf("en"),
         entries = listOf(
            AndroidTranslationEntry(":app:/strings.xml", "home.title", "home.title", mapOf("en" to "Home"), "strings.xml", true),
         ),
      )

      val result = renderTranslationResources(
         project = project,
         packageName = "com.example.translations",
         placeholderGlobals = emptyList(),
      )

      assertTrue(result.contains("public object Translations {"), "expected top-level Translations object, got:\n$result")
      assertTrue(!result.contains("object string"), "generated source should not contain nested 'string' object, got:\n$result")
      assertTrue(!result.contains("object Res"), "generated source should no longer use 'Res' object, got:\n$result")
   }

   @Test
   fun generate_should_carry_placeholder_tokens_through_unchanged()
   {
      val project = baseProject.copy(
         locales = listOf("en"),
         entries = listOf(
            AndroidTranslationEntry(
               ":app:/strings.xml",
               "greeting",
               "greeting",
               mapOf("en" to "Hello {userName}, '{'literal'}' costs \$5"),
               "strings.xml",
               true,
            ),
         ),
      )

      val result = renderTranslationResources(
         project = project,
         packageName = "com.example.translations",
         placeholderGlobals = emptyList(),
      )

      // Braces (token + escaped literal braces) ride through untouched; only the dollar sign is escaped.
      assertTrue(
         result.contains("""fallback = "Hello {userName}, '{'literal'}' costs \${'$'}5""""),
         "expected placeholder tokens carried through unchanged, got:\n$result",
      )
   }

   @Test
   fun bundled_snapshot_should_carry_placeholder_tokens_through_unchanged()
   {
      val project = baseProject.copy(
         locales = listOf("en"),
         entries = listOf(
            AndroidTranslationEntry(
               ":app:/strings.xml",
               "greeting",
               "greeting",
               mapOf("en" to "Hello {userName}, '{'literal'}' costs \$5"),
               "strings.xml",
               true,
            ),
         ),
      )

      val result = renderBundledSnapshot(project, "com.example.translations")

      assertTrue(
         result.contains("""value = "Hello {userName}, '{'literal'}' costs \${'$'}5""""),
         "expected placeholder tokens carried through unchanged in bundled snapshot, got:\n$result",
      )
   }

   @Test
   fun generate_should_give_a_plurals_key_a_reference_with_the_default_locale_plural_as_fallback_and_a_snapshot_entry_per_locale()
   {
      val enPlural = "{count, plural, one {# item in {cartName}} other {# items in {cartName}}}"
      val nlPlural = "{count, plural, one {# artikel in {cartName}} other {# artikelen in {cartName}}}"
      val project = baseProject.copy(
         entries = listOf(
            AndroidTranslationEntry(":app:/strings.xml", "cart_items", "cart_items", mapOf("en" to enPlural, "nl" to nlPlural), "strings.xml", true),
         ),
      )

      val resources = renderTranslationResources(project = project, packageName = "com.example.translations", placeholderGlobals = emptyList())
      val snapshot = renderBundledSnapshot(project, "com.example.translations")

      assertTrue(resources.contains("""fallback = "$enPlural""""), resources)
      assertTrue(snapshot.contains("""value = "$enPlural""""), snapshot)
      assertTrue(snapshot.contains("""value = "$nlPlural""""), snapshot)
   }

   private fun entry(key: String, en: String?, pl: String? = null, managedRemotely: Boolean = true) =
      AndroidTranslationEntry(
         ":app:/strings.xml",
         key,
         key,
         listOfNotNull(en?.let { "en" to it }, pl?.let { "pl" to it }).toMap(),
         "strings.xml",
         managedRemotely,
      )

   private fun render(vararg entries: AndroidTranslationEntry, globals: List<String> = emptyList()) =
      renderTranslationResources(
         baseProject.copy(locales = listOf("en", "pl"), entries = entries.toList()),
         "com.example.translations",
         globals,
      )

   @Test
   fun generate_should_give_a_key_with_placeholders_a_function_in_first_seen_order_and_a_private_resource()
   {
      val result = render(entry("greeting", "Hi {userName}, {cartName} and {userName}"))

      assertTrue(result.contains("public fun greeting(userName: String, cartName: String): BoundTranslationStringResource ="), result)
      assertTrue(result.contains("""BoundTranslationStringResource(_greeting, mapOf("userName" to userName, "cartName" to cartName))"""), result)
      assertTrue(result.contains("private val _greeting: TranslationStringResource ="), result)
      assertTrue(!result.contains("public val greeting"), result)
      assertTrue(result.contains("import io.mvdm.translationtools.client.BoundTranslationStringResource"), result)
   }

   @Test
   fun generate_should_keep_a_property_for_keys_without_placeholders_beyond_the_globals_or_without_a_default_value()
   {
      val result = render(
         entry("home_title", "Home", pl = "Start {userName}"),
         entry("about", "About {appName}"),
         entry("escaped", "'{'braces'}' {0} {Key}"),
         entry("only_pl", null, pl = "Tylko {userName}"),
         globals = listOf("appName"),
      )

      listOf("home_title", "about", "escaped", "only_pl").forEach {
         assertTrue(result.contains("public val $it: TranslationStringResource ="), "$it in:\n$result")
         assertTrue(!result.contains("fun $it("), "$it in:\n$result")
      }
   }

   @Test
   fun generate_should_drop_global_placeholders_from_the_parameters_and_keep_the_rest()
   {
      val result = render(entry("footer", "{appName} by {company}"), globals = listOf("appName"))

      assertTrue(result.contains("public fun footer(company: String): BoundTranslationStringResource ="), result)
      assertTrue(!result.contains("appName: String"), result)
   }

   @Test
   fun generate_should_give_a_plural_two_overloads_with_the_argument_first_and_placeholders_from_every_form()
   {
      val plural = "{count, plural, one {# item} other {# items in {cartName}, {count} total}}"
      val result = render(entry("cart_items", plural))

      assertTrue(result.contains("public fun cart_items(count: Long, cartName: String): BoundTranslationStringResource ="), result)
      assertTrue(result.contains("""BoundTranslationStringResource(_cart_items, mapOf("cartName" to cartName), pluralNumber = count)"""), result)
      assertTrue(result.contains("public fun cart_items(count: Int, cartName: String): BoundTranslationStringResource ="), result)
      assertTrue(result.contains("cart_items(count.toLong(), cartName)"), result)
      assertTrue(!result.contains("count: String"), result)
   }

   @Test
   fun generate_should_give_a_plural_without_placeholders_a_function_taking_only_the_number()
   {
      val result = render(entry("days", "{count, plural, one {# day} other {# days}}"))

      assertTrue(result.contains("public fun days(count: Long): BoundTranslationStringResource ="), result)
      assertTrue(result.contains("BoundTranslationStringResource(_days, pluralNumber = count)"), result)
      assertTrue(result.contains("public fun days(count: Int): BoundTranslationStringResource ="), result)
   }

   @Test
   fun generate_should_leave_the_number_of_a_plural_when_a_global_placeholder_has_the_argument_name()
   {
      val result = render(entry("days", "{count, plural, one {# day {x}} other {# days {x}}}"), globals = listOf("count", "x"))

      assertTrue(result.contains("public fun days(count: Long): BoundTranslationStringResource ="), result)
      assertTrue(!result.contains("x: String"), result)
   }

   @Test
   fun generate_should_give_a_non_translatable_key_the_same_function()
   {
      val result = render(entry("brand", "Brand {name}", managedRemotely = false))

      assertTrue(result.contains("public fun brand(name: String): BoundTranslationStringResource ="), result)
      assertTrue(result.contains("managedRemotely = false"), result)
   }

   @Test
   fun generate_should_read_the_required_placeholders_from_the_default_locale_only()
   {
      val result = render(entry("home_title", "Home", pl = "Start {userName}"))

      assertTrue(result.contains("public val home_title: TranslationStringResource ="), result)
   }

   @Test
   fun generate_should_put_the_collision_suffix_on_a_function_name()
   {
      val result = render(entry("home.title", "Hi {name}"), entry("home-title", "Hello {name}"))

      assertTrue(Regex("public fun home_title__[0-9a-f]{8}\\(name: String\\)").findAll(result).count() == 2, result)
   }

   @Test
   fun generate_should_wrap_a_keyword_placeholder_in_backticks_but_not_a_soft_keyword()
   {
      val result = render(entry("demo", "Type {typeof} in {in} of {value} and {data}"))

      assertTrue(
         result.contains("public fun demo(`typeof`: String, `in`: String, value: String, data: String): BoundTranslationStringResource ="),
         result,
      )
      assertTrue(result.contains("""mapOf("typeof" to `typeof`, "in" to `in`, "value" to value, "data" to data)"""), result)
   }
}
