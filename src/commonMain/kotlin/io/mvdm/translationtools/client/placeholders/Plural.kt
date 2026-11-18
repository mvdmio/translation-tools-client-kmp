package io.mvdm.translationtools.client.placeholders

/**
 * What a Plural form is chosen by: an exact number, or a CLDR category.
 */
internal sealed interface PluralSelector
{
   /** `=N`. [digits] is the text after `=`; a value that overflows `Long` never matches and `01` reads as 1. */
   data class Exact(val digits: String) : PluralSelector
   {
      fun matches(number: Long): Boolean = digits.toLongOrNull() == number
   }

   data class Category(val category: PluralCategory) : PluralSelector
}

internal data class PluralCase(val selector: PluralSelector, val segments: List<PlaceholderSegment>)

/**
 * A whole-value Plural: its [argument] name and its [cases] in written order.
 */
internal data class ParsedPlural(val argument: String, val cases: List<PluralCase>)
{
   /** The form [number] picks in [rules]: an exact case, else its category, else `other`, else the last case. */
   fun pick(number: Long, rules: PluralRules): PluralCase
   {
      cases.firstOrNull { (it.selector as? PluralSelector.Exact)?.matches(number) == true }?.let { return it }

      val category = rules.categoryOf(number)
      return caseOf(category) ?: fallbackCase()
   }

   /** The form that renders when no number picks one: `other`, else the last case. */
   fun fallbackCase(): PluralCase = caseOf(PluralCategory.OTHER) ?: cases.last()

   private fun caseOf(category: PluralCategory): PluralCase? =
      cases.firstOrNull { (it.selector as? PluralSelector.Category)?.category == category }
}
