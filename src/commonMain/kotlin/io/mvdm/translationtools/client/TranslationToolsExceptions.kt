package io.mvdm.translationtools.client

public open class TranslationToolsException(
   message: String,
   cause: Throwable? = null,
) : Exception(message, cause)

public class TranslationToolsNetworkException(
   message: String,
   cause: Throwable? = null,
) : TranslationToolsException(message, cause)

public class TranslationToolsHttpException(
   public val statusCode: Int,
   public val responseBody: String,
) : TranslationToolsException("TranslationTools request failed with status $statusCode. Body: $responseBody")

public class TranslationToolsSerializationException(
   message: String,
   cause: Throwable? = null,
) : TranslationToolsException(message, cause)

public class TranslationToolsValidationException(
   message: String,
) : TranslationToolsException(message)
