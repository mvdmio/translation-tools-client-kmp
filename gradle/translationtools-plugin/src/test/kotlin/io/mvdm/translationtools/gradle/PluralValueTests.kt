package io.mvdm.translationtools.gradle

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluralValueTests
{
   @Test
   fun composePluralValue_should_name_the_argument_and_write_forms_in_cldr_order()
   {
      val items = listOf(AndroidPluralItem(PluralQuantity.OTHER, "# items"), AndroidPluralItem(PluralQuantity.ONE, "# item"))

      assertEquals("{items, plural, one {# item} other {# items}}", composePluralValue("items", items))
      assertEquals("{count, plural, one {# item} other {# items}}", composePluralValue("count", items))
   }

   @Test
   fun pluralValueToAndroidItems_should_return_raw_form_text_in_cldr_order_ignoring_whitespace_and_argument_name()
   {
      val items = pluralValueToAndroidItems("{ n ,plural,\n  other {it''s # of {cartName} '#' '{x}'}  one{# item}\t}")

      assertEquals(
         listOf(
            AndroidPluralItem(PluralQuantity.ONE, "# item"),
            AndroidPluralItem(PluralQuantity.OTHER, "it''s # of {cartName} '#' '{x}'"),
         ),
         items,
      )
   }

   @Test
   fun pluralValueToAndroidItems_should_keep_a_brace_literal_such_as_zero_in_braces_like_the_runtime_client()
   {
      assertEquals(
         listOf(AndroidPluralItem(PluralQuantity.OTHER, "{0} items")),
         pluralValueToAndroidItems("{count, plural, other {{0} items}}"),
      )
   }

   @Test
   fun pluralValueToAndroidItems_should_return_null_for_values_a_plurals_resource_cannot_hold()
   {
      val notFitting = listOf(
         "plain text",
         "{count, plural, =0 {none} other {# items}}",
         "{count, plural, one {a} one {b}}",
         "{count, plural, offset:1 one {a}}",
         "{count, select, one {a}}",
         "{count, selectordinal, one {a}}",
         "{count, plural, few {a {n, plural, other {b}}}}",
         "{count, plural, other {open}",
         "{count, plural, other {a}} trailing",
         "x {count, plural, other {a}}",
         "{count, plural, }",
         "{count, Plural, other {a}}",
         "{Count, plural, other {a}}",
         "{count, plural, lots {a}}",
      )

      notFitting.forEach { value -> assertNull(pluralValueToAndroidItems(value), value) }
   }

   @Test
   fun parsePluralValue_should_report_the_argument_and_where_the_unquoted_number_hash_stands()
   {
      val plural = parsePluralValue("{items, plural, one {# of '#' and {cartName}: #} other {''# items}}")

      assertEquals("items", plural?.argument)
      assertEquals(
         listOf(
            PluralCase(PluralSelector.Category(PluralQuantity.ONE), "# of '#' and {cartName}: #", listOf(0, 25), listOf("cartName")),
            PluralCase(PluralSelector.Category(PluralQuantity.OTHER), "''# items", listOf(2), emptyList()),
         ),
         plural?.cases,
      )
   }

   @Test
   fun parsePluralValue_should_agree_with_the_server_examples_the_runtime_client_is_pinned_to()
   {
      // The runtime client's PluralParseExamplesTests runs this same file, so both readers answer to one fixture.
      val examples = Json.parseToJsonElement(File(System.getProperty("pluralParseExamples")).readText()).jsonArray
      assertEquals(26, examples.size)

      for (example in examples) {
         val input = example.jsonObject.getValue("input").jsonPrimitive.content
         val expected = example.jsonObject.getValue("expected")
         val expectedSelectors = if (expected is JsonNull) null
         else expected.jsonObject.getValue("cases").jsonArray.map { it.jsonObject.getValue("selector").jsonPrimitive.content }

         val expectedForms = if (expected is JsonNull) null
         else expected.jsonObject.getValue("cases").jsonArray.map { case ->
            case.jsonObject.getValue("segments").jsonArray.mapNotNull { it.jsonObject["token"]?.jsonPrimitive?.content }.distinct()
         }
         val expectedArgument = if (expected is JsonNull) null else expected.jsonObject.getValue("argument").jsonPrimitive.content
         val parsed = parsePluralValue(input)
         assertEquals(expectedArgument, parsed?.argument, "argument of: $input")
         assertEquals(expectedForms, parsed?.cases?.map { it.placeholders }, "placeholders of: $input")

         val selectors = parsed?.cases?.map {
            when (val selector = it.selector) {
               is PluralSelector.Exact -> "=${selector.digits}"
               is PluralSelector.Category -> selector.quantity.keyword
            }
         }

         assertEquals(expectedSelectors, selectors, "input: $input")
      }
   }
}
