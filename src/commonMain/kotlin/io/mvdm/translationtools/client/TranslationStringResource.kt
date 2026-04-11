package io.mvdm.translationtools.client

public data class TranslationStringResource(
   val ref: TranslationRef,
   val fallback: String? = null,
   val managedRemotely: Boolean = true,
)
