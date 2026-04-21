package io.mvdm.translationtools.gradle

internal fun renderBundledSnapshot(project: AndroidTranslationProject, packageName: String): String
{
   val builder = StringBuilder()
   builder.appendLine("package $packageName")
   builder.appendLine()
   builder.appendLine("import io.mvdm.translationtools.client.ProjectMetadata")
   builder.appendLine("import io.mvdm.translationtools.client.StoredTranslations")
   builder.appendLine("import io.mvdm.translationtools.client.TranslationItem")
    builder.appendLine("import io.mvdm.translationtools.client.TranslationRef")
   builder.appendLine("import io.mvdm.translationtools.client.TranslationSnapshot")
   builder.appendLine()
   builder.appendLine("public object ${GENERATED_OBJECT_NAME}BundledSnapshot {")
   builder.appendLine("   public val value: StoredTranslations = StoredTranslations(")
   builder.appendLine("      projectMetadata = ProjectMetadata(")
   builder.appendLine("         locales = listOf(${project.locales.joinToString(", ") { it.asKotlinStringLiteral() }}),")
   builder.appendLine("         defaultLocale = ${project.defaultLocale.asKotlinStringLiteral()},")
   builder.appendLine("      ),")
   builder.appendLine("      snapshots = listOf(")

   val locales = project.locales.sorted()
   locales.forEachIndexed { localeIndex, locale ->
      val translations = project.entries
         .mapNotNull { entry -> entry.valuesByLocale[locale]?.let { value -> entry to value } }
         .sortedBy { it.first.key }
      builder.appendLine("         TranslationSnapshot(")
      builder.appendLine("            locale = ${locale.asKotlinStringLiteral()},")
      builder.appendLine("            items = listOf(")
      translations.forEachIndexed { index, entry ->
         builder.appendLine("               TranslationItem(ref = TranslationRef(origin = ${entry.first.origin.asKotlinStringLiteral()}, key = ${entry.first.key.asKotlinStringLiteral()}), value = ${entry.second.asKotlinStringLiteral()})${if (index == translations.size - 1) "" else ","}")
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
