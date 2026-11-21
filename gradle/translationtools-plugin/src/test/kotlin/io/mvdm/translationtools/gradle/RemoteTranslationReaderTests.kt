package io.mvdm.translationtools.gradle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RemoteTranslationReaderTests
{
   @Test
   fun executeRemoteRequest_should_pass_cancellation_through_unwrapped()
   {
      val cancellation = CancellationException("cancelled")

      val thrown = assertFailsWith<CancellationException> {
         runBlocking {
            executeRemoteRequest("locale 'nl'", ::IllegalStateException) { throw cancellation }
         }
      }

      assertSame(cancellation, thrown)
   }
}
