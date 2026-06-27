package io.mvdm.translationtools.client

import platform.Foundation.NSUUID

internal actual fun currentPlatform(): String = "kmp-ios"

internal actual fun newClientId(): String = NSUUID().UUIDString()
