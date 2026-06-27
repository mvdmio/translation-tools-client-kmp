package io.mvdm.translationtools.client

internal actual fun currentPlatform(): String = "kmp-jvm"

internal actual fun newClientId(): String = java.util.UUID.randomUUID().toString()
