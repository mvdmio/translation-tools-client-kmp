package io.mvdm.translationtools.client.placeholders

/**
 * The CLDR cardinal plural rules that apply to one Locale, from the generated [cldrPluralRule] table.
 *
 * [matchedTag] is the Locale tag the table matched (the full tag, else its language subtag), or null
 * when the table knows neither; a null match picks [PluralCategory.OTHER] for every number.
 */
internal class PluralRules private constructor(
   val matchedTag: String?,
   private val categoryOfMagnitude: (ULong) -> PluralCategory,
)
{
   /** The category of [number]'s absolute value, so a negative number picks like its size. */
   fun categoryOf(number: Long): PluralCategory
   {
      val magnitude = if (number < 0) 0UL - number.toULong() else number.toULong()
      return categoryOfMagnitude(magnitude)
   }

   companion object
   {
      /** Looks up [localeTag] (lowercase, `-`-separated): the full tag, then the part before the first `-`. */
      fun forLocale(localeTag: String): PluralRules
      {
         for (tag in listOf(localeTag, localeTag.substringBefore('-')).distinct()) {
            val rule = cldrPluralRule(tag)
            if (rule != null)
               return PluralRules(tag, rule)
         }

         return PluralRules(null) { PluralCategory.OTHER }
      }
   }
}
