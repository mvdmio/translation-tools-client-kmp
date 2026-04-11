package io.mvdm.translationtools.client

import kotlinx.serialization.Serializable

@Serializable
public data class TranslationRef(
   val origin: String,
   val key: String,
)
