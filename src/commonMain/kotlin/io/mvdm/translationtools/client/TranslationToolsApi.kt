package io.mvdm.translationtools.client

public interface TranslationToolsApi
{
   public suspend fun getProjectMetadata(): ProjectMetadata

   public suspend fun getLocale(locale: String): List<TranslationItem>

   public suspend fun getTranslation(locale: String, ref: TranslationRef, defaultValue: String? = null): TranslationItem
}
