package io.mvdm.translationtools.client.placeholders

/**
 * A CLDR plural category, in CLDR's order. [keyword] is the selector that names it in a Plural value.
 */
internal enum class PluralCategory(val keyword: String)
{
   ZERO("zero"),
   ONE("one"),
   TWO("two"),
   FEW("few"),
   MANY("many"),
   OTHER("other"),
}
