/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.commonizer.api

import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.Serializable

// N.B. TargetPlatform/SimplePlatform are non exhaustive enough to address both target platforms such as
// JVM, JS and concrete Kotlin/Native targets, e.g. macos_x64, ios_x64, linux_x64.
public sealed class CommonizerTarget : Serializable {
    final override fun toString(): String = identityString
}

public data class LeafCommonizerTarget public constructor(val konanTarget: KonanTarget) : CommonizerTarget() {
    public val name: String get() = konanTarget.name
}

public data class SharedCommonizerTarget(val targets: Set<CommonizerTarget>) : CommonizerTarget() {
    public constructor(vararg targets: CommonizerTarget) : this(targets.toSet())

    init {
        require(targets.isNotEmpty())
    }
}

public fun CommonizerTarget(konanTargets: Iterable<KonanTarget>): CommonizerTarget {
    val konanTargetsSet = konanTargets.toSet()
    require(konanTargetsSet.isNotEmpty()) { "Empty set of of konanTargets" }
    val leafTargets = konanTargetsSet.map(::LeafCommonizerTarget)
    return leafTargets.singleOrNull() ?: SharedCommonizerTarget(leafTargets.toSet())
}

public fun CommonizerTarget(vararg konanTargets: KonanTarget): CommonizerTarget {
    return CommonizerTarget(konanTargets.toList())
}

public val CommonizerTarget.identityString: String
    get() = when (this) {
        is LeafCommonizerTarget -> name
        is SharedCommonizerTarget -> identityString
    }

private val SharedCommonizerTarget.identityString: String
    get() {
        val segments = targets.map(CommonizerTarget::identityString).sorted()
        return segments.joinToString(
            separator = ", ", prefix = "[", postfix = "]"
        )
    }
