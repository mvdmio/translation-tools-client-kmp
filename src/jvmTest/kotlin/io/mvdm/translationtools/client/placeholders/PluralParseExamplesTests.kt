package io.mvdm.translationtools.client.placeholders

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class PluralParseExamplesTests
{
   private val examples: JsonArray =
      Json.parseToJsonElement(javaClass.getResource("/plural-parse-examples.json")!!.readText()).jsonArray

   @Test
   fun parse_should_match_every_example_of_the_server()
   {
      assertEquals(26, examples.size)

      for (example in examples) {
         val input = example.jsonObject.getValue("input").jsonPrimitive.content
         val expected = example.jsonObject.getValue("expected")

         assertEquals(expectedPlural(expected), PlaceholderTokenParser.parsePlural(input)?.let(::describe), "input: $input")
      }
   }

   private fun expectedPlural(expected: JsonElement): List<String>?
   {
      if (expected is JsonNull)
         return null

      val plural = expected.jsonObject
      val cases = plural.getValue("cases").jsonArray.map { case ->
         val segments = case.jsonObject.getValue("segments").jsonArray.map(::expectedSegment)
         "${case.jsonObject.getValue("selector").jsonPrimitive.content} $segments"
      }

      return listOf(plural.getValue("argument").jsonPrimitive.content) + cases
   }

   private fun expectedSegment(segment: JsonElement): String
   {
      val fields: JsonObject = segment.jsonObject
      return when {
         "literal" in fields -> "literal:" + fields.getValue("literal").jsonPrimitive.content
         "token" in fields -> "token:" + fields.getValue("token").jsonPrimitive.content
         else -> "number"
      }
   }

   private fun describe(plural: ParsedPlural): List<String>
   {
      val cases = plural.cases.map { case ->
         val selector = when (val s = case.selector) {
            is PluralSelector.Exact -> "=${s.digits}"
            is PluralSelector.Category -> s.category.keyword
         }
         val segments = case.segments.map {
            when (it) {
               is PlaceholderSegment.Literal -> "literal:${it.text}"
               is PlaceholderSegment.Token -> "token:${it.name}"
               PlaceholderSegment.Number -> "number"
            }
         }
         "$selector $segments"
      }

      return listOf(plural.argument) + cases
   }
}
