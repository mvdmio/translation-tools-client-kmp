package io.mvdm.translationtools.client

private const val LAST_RESORT_LOCALE = "en"

/**
 * The Declaration items for [snapshot]: one per Origin, Locale, and Key with a non-empty value,
 * plus one empty Default Locale item for each Key that has no non-empty value in any Locale.
 * Ordered by origin, locale, key.
 */
internal fun declarationItems(snapshot: StoredTranslations): List<TranslationDeclarationItem>
{
   val withValue = snapshot.snapshots
      .flatMap { translations ->
         translations.items
            .filter { !it.value.isNullOrEmpty() }
            .map { TranslationDeclarationItem(it.ref, translations.locale, it.value.orEmpty()) }
      }
      .distinctBy { Triple(it.ref.origin, it.locale, it.ref.key) }

   val declaredRefs = withValue.map { it.ref }.toSet()
   val defaultLocale = snapshot.projectMetadata?.defaultLocale ?: LAST_RESORT_LOCALE
   val withoutValue = snapshot.snapshots
      .flatMap { translations -> translations.items.map { it.ref } }
      .distinct()
      .filter { it !in declaredRefs }
      .map { TranslationDeclarationItem(it, defaultLocale, "") }

   return (withValue + withoutValue).sortedWith(
      compareBy({ it.ref.origin }, { it.locale }, { it.ref.key }),
   )
}
