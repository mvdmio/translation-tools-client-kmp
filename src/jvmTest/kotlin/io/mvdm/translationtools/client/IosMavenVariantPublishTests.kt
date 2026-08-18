package io.mvdm.translationtools.client

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class IosMavenVariantPublishTests
{
   @Test
   fun publish_workflow_should_compile_ios_klibs_on_ubuntu_before_maven_central()
   {
      val workflow = File(".github/workflows/publish.translationtools-client-maven-central.yml").readText()

      assertTrue(workflow.contains("ubuntu-22.04"))
      assertTrue(workflow.contains("compileKotlinIosArm64"))
      assertTrue(workflow.contains("compileKotlinIosX64"))
      assertTrue(workflow.contains("compileKotlinIosSimulatorArm64"))
      assertTrue(workflow.contains(":translationtools-client-compose:compileKotlinIosArm64"))
      assertTrue(workflow.contains(":translationtools-client-compose:compileKotlinIosSimulatorArm64"))
      assertTrue(workflow.contains("verifyIosMavenVariants"))
      assertTrue(workflow.contains("kotlin.native.ignoreDisabledTargets=false"))
      assertTrue(workflow.contains("publishAndReleaseToMavenCentral"))
   }

   @Test
   fun root_build_should_register_verify_ios_maven_variants_task()
   {
      val buildFile = File("build.gradle.kts").readText()

      assertTrue(buildFile.contains("verifyIosMavenVariants"))
      assertTrue(buildFile.contains("IosCheck"))
      assertTrue(buildFile.contains("publishAndReleaseToMavenCentral"))
   }

   @Test
   fun local_maven_check_repo_should_contain_ios_variant_klibs_when_present()
   {
      val repoPath = System.getProperty("iosMavenCheckRepo")
         ?: System.getenv("IOS_MAVEN_CHECK_REPO")
         ?: return

      val repo = File(repoPath)
      assertTrue(repo.isDirectory, "iosMavenCheckRepo is not a directory: ${repo.absolutePath}")

      val version = File("build.gradle.kts").readText()
         .lineSequence()
         .map { it.trim() }
         .firstOrNull { it.startsWith("version") }
         ?.substringAfter("=")
         ?.trim()
         ?.trim('"')
         ?: fail("Could not read version from build.gradle.kts")

      val required = listOf(
         "translationtools-client-kmp-iosarm64",
         "translationtools-client-kmp-iosx64",
         "translationtools-client-kmp-iossimulatorarm64",
         "translationtools-client-compose-iosarm64",
         "translationtools-client-compose-iossimulatorarm64",
      )

      val missing = required.filter { artifactId ->
         !File(repo, "io/mvdm/translationtools/$artifactId/$version/$artifactId-$version.klib").isFile
      }

      assertEquals(
         emptyList(),
         missing,
         "Missing iOS Maven variant modules under ${repo.absolutePath}. " +
            "Run verifyIosMavenVariants with -Pkotlin.native.ignoreDisabledTargets=false; " +
            "do not publish without these klibs.",
      )
   }
}
