package io.mvdm.translationtools.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceholderSignatureTests
{
   @Test
   fun plainPlaceholderNames_should_read_the_runtime_grammar()
   {
      assertEquals(emptyList(), plainPlaceholderNames("'{'braces'}'"))
      assertEquals(emptyList(), plainPlaceholderNames("{0}"))
      assertEquals(emptyList(), plainPlaceholderNames("{Key}"))
      assertEquals(emptyList(), plainPlaceholderNames("{ name }"))
      assertEquals(listOf("name"), plainPlaceholderNames("It''s {name}"))
      assertEquals(listOf("a"), plainPlaceholderNames("{a}{a}"))
      assertEquals(listOf("in", "typeof"), plainPlaceholderNames("{in} {typeof}"))
      assertEquals(listOf("a", "b"), plainPlaceholderNames("{{a} and {b}"))
      assertEquals(listOf("a"), plainPlaceholderNames("'x {a} 'y"))
   }

   @Test
   fun plainPlaceholderNames_should_not_read_a_placeholder_in_a_quoted_run()
   {
      assertEquals(listOf("after"), plainPlaceholderNames("'{name}' {after}"))
      assertEquals(emptyList(), plainPlaceholderNames("'{name} still quoted"))
   }

   @Test
   fun placeholderSignatureOf_should_give_a_plain_value_its_placeholders_minus_the_globals()
   {
      assertEquals(PlaceholderSignature(null, listOf("company")), placeholderSignatureOf("{appName} by {company}", setOf("appName")))
      assertNull(placeholderSignatureOf("About {appName}", setOf("appName")))
      assertNull(placeholderSignatureOf("Home", emptySet()))
      assertNull(placeholderSignatureOf(null, emptySet()))
   }

   @Test
   fun placeholderSignatureOf_should_give_a_plural_its_number_even_without_other_placeholders()
   {
      assertEquals(
         PlaceholderSignature("count", listOf("cartName")),
         placeholderSignatureOf("{count, plural, one {# in {cartName}} other {{count} in {cartName}}}", emptySet()),
      )
      assertEquals(PlaceholderSignature("count", emptyList()), placeholderSignatureOf("{count, plural, other {#}}", emptySet()))
      assertEquals(PlaceholderSignature("count", emptyList()), placeholderSignatureOf("{count, plural, other {# {count}}}", setOf("count")))
   }

   @Test
   fun placeholderSignatureOf_should_collect_plural_placeholders_across_forms_in_first_seen_order()
   {
      assertEquals(
         PlaceholderSignature("n", listOf("b", "a")),
         placeholderSignatureOf("{n, plural, one {{b}} other {{a} {b} '{c}' {0}}}", emptySet()),
      )
   }

   @Test
   fun asKotlinIdentifier_should_backtick_hard_keywords_only()
   {
      assertEquals("`typeof`", asKotlinIdentifier("typeof"))
      assertEquals("`in`", asKotlinIdentifier("in"))
      assertEquals("value", asKotlinIdentifier("value"))
      assertEquals("data", asKotlinIdentifier("data"))
   }
}
