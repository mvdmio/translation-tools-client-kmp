package io.mvdm.translationtools.client.placeholders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlaceholderTests
{
   // --- Tokenizer / tokenNames (spec §8) ---

   @Test
   fun tokenNames_should_match_spec_vectors()
   {
      assertEquals(listOf("userName"), PlaceholderTokenParser.tokenNames("Hello {userName}!"))
      assertEquals(listOf("a", "b"), PlaceholderTokenParser.tokenNames("{a} {b} {a}"))
      assertEquals(emptyList(), PlaceholderTokenParser.tokenNames("No tokens here"))
      assertEquals(emptyList(), PlaceholderTokenParser.tokenNames("{0} {Key} {x_y}"))
      assertEquals(listOf("orderCount"), PlaceholderTokenParser.tokenNames("Order {orderCount} of {orderCount}"))
      assertEquals(listOf("n"), PlaceholderTokenParser.tokenNames("It's {n}"))
      assertEquals(emptyList(), PlaceholderTokenParser.tokenNames("'{'userName'}'"))
      assertEquals(emptyList(), PlaceholderTokenParser.tokenNames("'{userName}'"))
      assertEquals(listOf("amount"), PlaceholderTokenParser.tokenNames("Cost '{'total'}' is {amount}"))
   }

   @Test
   fun parse_should_resolve_apostrophe_escapes()
   {
      assertEquals("{", literalOf("'{'"))
      assertEquals("}", literalOf("'}'"))
      assertEquals("{userName}", literalOf("'{userName}'"))
      // It's {n} -> literal "It's " + token n
      val segments = PlaceholderTokenParser.parse("It's {n}")
      assertEquals(PlaceholderSegment.Literal("It's "), segments[0])
      assertEquals(PlaceholderSegment.Token("n"), segments[1])
      // 100'%' -> literal 100'%' (apostrophes not before a brace stay literal)
      assertEquals("100'%'", literalOf("100'%'"))
   }

   private fun literalOf(input: String): String =
      PlaceholderTokenParser.parse(input).filterIsInstance<PlaceholderSegment.Literal>().joinToString("") { it.text }

   // --- Render (substitute, knownSet=null, bindings only) (spec §8) ---

   @Test
   fun substitute_should_match_open_mode_render_vectors()
   {
      assertEquals("Hello Bob!", render("Hello {userName}!", mapOf("userName" to "Bob")))
      assertEquals("1+2=1", render("{a}+{b}={a}", mapOf("a" to "1", "b" to "2")))
      assertEquals("{literal}", render("'{'literal'}'", emptyMap()))
      assertEquals("It's 3 cats", render("It's {n} cats", mapOf("n" to "3")))
   }

   @Test
   fun substitute_should_degrade_unbound_token_to_raw_with_warning()
   {
      val warnings = mutableListOf<String>()
      val result = render("Hi {userName}", emptyMap(), warn = { warnings += it })

      assertEquals("Hi {userName}", result)
      assertEquals(1, warnings.size)
      assertTrue(warnings.single().contains("{userName}"))
   }

   @Test
   fun substitute_should_keep_escaped_brace_literal_without_warning()
   {
      val warnings = mutableListOf<String>()
      val result = render("'{'x'}'", emptyMap(), warn = { warnings += it })

      assertEquals("{x}", result)
      assertTrue(warnings.isEmpty())
   }

   @Test
   fun substitute_should_bind_null_as_empty_string()
   {
      assertEquals("Hi !", render("Hi {userName}!", mapOf("userName" to null)))
   }

   @Test
   fun substitute_should_warn_on_extra_supplied_binding()
   {
      val warnings = mutableListOf<String>()
      val result = render("Hello {userName}", mapOf("userName" to "Bob", "unused" to "x"), warn = { warnings += it })

      assertEquals("Hello Bob", result)
      assertEquals(1, warnings.size)
      assertTrue(warnings.single().contains("unused"))
   }

   // --- knownSet (generator path parity) (spec §8) ---

   @Test
   fun substitute_with_known_set_should_leave_unknown_token_inert_without_warning()
   {
      val warnings = mutableListOf<String>()
      val result = PlaceholderSubstitution.substitute(
         value = "Hi {userName}, {legacyWord}",
         bindings = mapOf("userName" to "Bob"),
         globals = GlobalPlaceholderRegistry.Empty,
         knownSet = setOf("userName"),
         throwOnError = false,
         warn = { warnings += it },
      )

      assertEquals("Hi Bob, {legacyWord}", result)
      assertTrue(warnings.isEmpty())
   }

   @Test
   fun substitute_with_known_set_should_warn_on_missing_managed_token()
   {
      val warnings = mutableListOf<String>()
      val result = PlaceholderSubstitution.substitute(
         value = "Hi {userName}",
         bindings = emptyMap(),
         globals = GlobalPlaceholderRegistry.Empty,
         knownSet = setOf("userName"),
         throwOnError = false,
         warn = { warnings += it },
      )

      assertEquals("Hi {userName}", result)
      assertEquals(1, warnings.size)
   }

   // --- Globals (ambient resolution) ---

   @Test
   fun substitute_should_resolve_registered_global_ambiently()
   {
      val registry = GlobalPlaceholderRegistry.from(mapOf("appName" to { "Acme" }))
      assertEquals("Welcome to Acme", render("Welcome to {appName}", emptyMap(), globals = registry))
   }

   @Test
   fun binding_should_shadow_global_of_same_name()
   {
      val registry = GlobalPlaceholderRegistry.from(mapOf("appName" to { "Acme" }))
      assertEquals("Welcome to Local", render("Welcome to {appName}", mapOf("appName" to "Local"), globals = registry))
   }

   @Test
   fun substitute_should_degrade_when_global_resolver_returns_null()
   {
      val warnings = mutableListOf<String>()
      val registry = GlobalPlaceholderRegistry.from(mapOf("appName" to { null }))
      val result = render("Welcome to {appName}", emptyMap(), globals = registry, warn = { warnings += it })

      assertEquals("Welcome to {appName}", result)
      assertEquals(1, warnings.size)
   }

   @Test
   fun substitute_should_degrade_when_global_resolver_throws()
   {
      val warnings = mutableListOf<String>()
      val registry = GlobalPlaceholderRegistry.from(mapOf("appName" to { throw RuntimeException("boom") }))
      val result = render("Welcome to {appName}", emptyMap(), globals = registry, warn = { warnings += it })

      assertEquals("Welcome to {appName}", result)
      assertEquals(1, warnings.size)
   }

   // --- Failure-behavior flag parity (warn-default vs throw) ---

   @Test
   fun substitute_should_throw_when_configured_for_unbound_token()
   {
      assertFailsWith<PlaceholderSubstitutionException> {
         render("Hi {userName}", emptyMap(), throwOnError = true)
      }
   }

   @Test
   fun substitute_should_throw_when_configured_for_extra_binding()
   {
      assertFailsWith<PlaceholderSubstitutionException> {
         render("Hello {userName}", mapOf("userName" to "Bob", "unused" to "x"), throwOnError = true)
      }
   }

   @Test
   fun registry_should_reject_invalid_global_name()
   {
      assertFailsWith<IllegalArgumentException> {
         GlobalPlaceholderRegistry.from(mapOf("User_Name" to { "x" }))
      }
   }

   private fun render(
      value: String,
      bindings: Map<String, String?>,
      globals: GlobalPlaceholderResolver = GlobalPlaceholderRegistry.Empty,
      throwOnError: Boolean = false,
      warn: ((String) -> Unit)? = null,
   ): String =
      PlaceholderSubstitution.substitute(
         value = value,
         bindings = bindings,
         globals = globals,
         knownSet = null,
         throwOnError = throwOnError,
         warn = warn,
      )
}
