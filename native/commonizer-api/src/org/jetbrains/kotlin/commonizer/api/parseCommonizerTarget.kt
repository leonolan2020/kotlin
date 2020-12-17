/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer.api

import org.jetbrains.kotlin.konan.target.KonanTarget

public fun parseCommonizerTarget(identityString: String): CommonizerTarget {
    if (identityString.startsWith("[") && identityString.endsWith("]")) {
        return parseSharedCommonizerTarget(identityString)
    }
    val konanTarget = KonanTarget.predefinedTargets[identityString] ?: error("Unknown KonanTarget $identityString")
    return LeafCommonizerTarget(konanTarget)
}

private fun parseSharedCommonizerTarget(identityString: String): SharedCommonizerTarget {
    return SharedCommonizerTarget(
        identityString
            // TODO NOW
            .removePrefix("[").removeSuffix("]").split(", ")
            .map { childIdentityString -> parseCommonizerTarget(childIdentityString) }
            .toSet()
    )
}
