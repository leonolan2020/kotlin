/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer

import org.jetbrains.kotlin.konan.library.*
import java.io.File


internal interface Repository {
    fun getLibraries(target: LeafTarget): List<File>
}

internal operator fun Repository.plus(other: Repository): Repository {
    if (this is CompositeRepository) {
        return CompositeRepository(this.repositories + other)
    }
    return CompositeRepository(listOf(this, other))
}

internal class KonanDistributionRepository(private val konanDistribution: KonanDistribution) : Repository {
    override fun getLibraries(target: LeafTarget): List<File> {
        return konanDistribution.platformLibsDir
            .resolve(target.name)
            .takeIf { it.isDirectory }
            ?.listFiles()
            .orEmpty().toList()
    }
}

private class CompositeRepository(val repositories: Iterable<Repository>) : Repository {
    override fun getLibraries(target: LeafTarget): List<File> {
        return repositories.flatMap { it.getLibraries(target) }.distinct()
    }
}

internal object EmptyRepository : Repository {
    override fun getLibraries(target: LeafTarget): List<File> {
        return emptyList()
    }
}

