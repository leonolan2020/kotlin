/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer

import org.jetbrains.kotlin.konan.target.KonanTarget

// N.B. TargetPlatform/SimplePlatform are non exhaustive enough to address both target platforms such as
// JVM, JS and concrete Kotlin/Native targets, e.g. macos_x64, ios_x64, linux_x64.
sealed class Target

data class LeafTarget(val name: String, val konanTarget: KonanTarget? = null) : Target() {
    constructor(konanTarget: KonanTarget) : this(konanTarget.name, konanTarget)
}

data class SharedTarget(val targets: Set<Target>) : Target() {
    init {
        require(targets.isNotEmpty())
    }
}

fun Target(konanTargets: Iterable<KonanTarget>): Target {
    val konanTargetsSet = konanTargets.toSet()
    require(konanTargetsSet.isNotEmpty()) { "Empty set of of konanTargets" }
    val leafTargets = konanTargetsSet.map(::LeafTarget)
    return leafTargets.singleOrNull() ?: SharedTarget(leafTargets.toSet())
}
