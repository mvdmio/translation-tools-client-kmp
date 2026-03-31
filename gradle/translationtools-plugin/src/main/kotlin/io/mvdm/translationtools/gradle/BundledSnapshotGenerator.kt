package io.mvdm.translationtools.gradle

internal fun renderBundledSnapshot(snapshot: TranslationSnapshotFile, packageName: String, objectName: String): String
{
   val builder = StringBuilder()
   builder.appendLine("package $packageName")
   builder.appendLine()
   builder.appendLine("import io.mvdm.translationtools.client.ProjectMetadata")
   builder.appendLine("import io.mvdm.translationtools.client.StoredTranslations")
   builder.appendLine("import io.mvdm.translationtools.client.TranslationItem")
   builder.appendLine("import io.mvdm.translationtools.client.TranslationSnapshot")
   builder.appendLine()
   builder.appendLine("public object ${objectName}BundledSnapshot {")
   builder.appendLine("   public val value: StoredTranslations = StoredTranslations(")
   builder.appendLine("      projectMetadata = ProjectMetadata(")
   builder.appendLine("         locales = listOf(${snapshot.project.locales.joinToString(", ") { it.asKotlinStringLiteral() }}),")
   builder.appendLine("         defaultLocale = ${snapshot.project.defaultLocale.asKotlinStringLiteral()},")
   builder.appendLine("      ),")
   builder.appendLine("      snapshots = listOf(")

   val locales = snapshot.translations.keys.sorted()
   locales.forEachIndexed { localeIndex, locale ->
      val translations = snapshot.translations.getValue(locale).toSortedMap()
      builder.appendLine("         TranslationSnapshot(")
      builder.appendLine("            locale = ${locale.asKotlinStringLiteral()},")
      builder.appendLine("            items = listOf(")
      translations.entries.forEachIndexed { index, entry ->
         builder.appendLine("               TranslationItem(key = ${entry.key.asKotlinStringLiteral()}, value = ${entry.value.asKotlinStringLiteral()})${if (index == translations.size - 1) "" else ","}")
      }
      builder.appendLine("            ),")
      builder.appendLine("         )${if (localeIndex == locales.lastIndex) "" else ","}")
   }

   builder.appendLine("      ),")
   builder.appendLine("      lastSuccessfulRefreshAt = null,")
   builder.appendLine("   )")
   builder.appendLine("}")
   return builder.toString()
}
