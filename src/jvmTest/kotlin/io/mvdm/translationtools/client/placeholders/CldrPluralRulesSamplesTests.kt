package io.mvdm.translationtools.client.placeholders

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CldrPluralRulesSamplesTests
{
   private val countPrefix = "pluralRule-count-"

   private val cardinalRules: JsonObject =
      Json.parseToJsonElement(File("gradle/cldr/plurals.json").readText())
         .jsonObject.getValue("supplemental")
         .jsonObject.getValue("plurals-type-cardinal")
         .jsonObject

   @Test
   fun table_should_map_every_cldr_integer_sample_to_its_category()
   {
      val mismatches = mutableListOf<String>()
      val languagesWithoutSamples = mutableListOf<String>()
      var checked = 0

      for ((cldrTag, categories) in cardinalRules) {
         val tag = cldrTag.lowercase().replace('_', '-')
         val rules = PluralRules.forLocale(tag)

         assertEquals(tag, rules.matchedTag, "table has no rules for '$tag'")
         val checkedBefore = checked

         for ((countKey, ruleText) in categories.jsonObject) {
            val category = PluralCategory.entries.single { it.keyword == countKey.removePrefix(countPrefix) }
            val samples = plainIntegerSamples(ruleText.jsonPrimitive.content)

            for (sample in samples) {
               checked++
               val actual = rules.categoryOf(sample)
               if (actual != category)
                  mismatches += "$tag: $sample should be ${category.keyword} but is ${actual.keyword}"
            }
         }

         if (checked == checkedBefore)
            languagesWithoutSamples += tag
      }

      assertEquals(emptyList(), mismatches)
      assertEquals(emptyList(), languagesWithoutSamples)
      assertTrue(checked > 5000, "only $checked samples were checked")
   }

   /** Every whole number in the `@integer` samples, skipping exponent notation and the trailing `…`. */
   private fun plainIntegerSamples(ruleText: String): List<Long>
   {
      val integerPart = ruleText.substringAfter("@integer", "").substringBefore("@decimal")

      return integerPart.split(',')
         .map { it.trim() }
         .filter { it.isNotEmpty() && it != "…" && 'c' !in it && 'e' !in it }
         .flatMap { sample ->
            if ('~' in sample)
               (sample.substringBefore('~').toLong()..sample.substringAfter('~').toLong()).toList()
            else
               listOf(sample.toLong())
         }
   }
}
