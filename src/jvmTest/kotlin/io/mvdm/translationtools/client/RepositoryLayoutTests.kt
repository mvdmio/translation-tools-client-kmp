package io.mvdm.translationtools.client

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryLayoutTests
{
   @Test
   fun included_plugin_build_should_have_local_wrapper_entrypoints()
   {
      assertTrue(File("gradle/translationtools-plugin/gradlew.bat").isFile)
      assertTrue(File("gradle/translationtools-plugin/gradlew").isFile)
   }

   @Test
   fun compose_module_should_declare_ios_targets()
   {
      val buildFile = File("translationtools-client-compose/build.gradle.kts").readText()

      assertEquals(true, buildFile.contains("iosX64()"))
      assertEquals(true, buildFile.contains("iosArm64()"))
      assertEquals(true, buildFile.contains("iosSimulatorArm64()"))
   }
}
