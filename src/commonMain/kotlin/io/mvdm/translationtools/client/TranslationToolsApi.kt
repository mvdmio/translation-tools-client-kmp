package io.mvdm.translationtools.client

public interface TranslationToolsApi
{
   public suspend fun getProjectMetadata(): ProjectMetadata

   public suspend fun getLocale(locale: String): List<TranslationItem>

   public suspend fun getTranslation(locale: String, key: String, defaultValue: String? = null): TranslationItem
}
