package io.mvdm.translationtools.gradle

import org.gradle.api.tasks.Internal
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertNotNull

class TaskPropertyAnnotationsTests
{
   @Test
   fun pullTranslationsTask_should_mark_http_client_factory_internal()
   {
      val property = PullTranslationsTask::class.memberProperties.firstOrNull { it.name == "httpClientFactory" }

      assertNotNull(property)
      assertNotNull(property.getter.annotations.filterIsInstance<Internal>().firstOrNull())
   }

   @Test
   fun pushTranslationsTask_should_mark_http_client_factory_internal()
   {
      val property = PushTranslationsTask::class.memberProperties.firstOrNull { it.name == "httpClientFactory" }

      assertNotNull(property)
      assertNotNull(property.getter.annotations.filterIsInstance<Internal>().firstOrNull())
   }
}
