/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer.api

import org.jetbrains.kotlin.konan.library.KONAN_DISTRIBUTION_COMMON_LIBS_DIR
import org.jetbrains.kotlin.konan.library.KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR
import java.io.File


public fun interface CommonizerOutputLayout {
    public fun getTargetDirectory(root: File, target: CommonizerTarget): File
}

public object NativeDistributionTargetDestinationLayout : CommonizerOutputLayout {
    override fun getTargetDirectory(root: File, target: CommonizerTarget): File {
        return when (target) {
            is LeafCommonizerTarget -> root.resolve(KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR).resolve(target.name)
            is SharedCommonizerTarget -> root.resolve(KONAN_DISTRIBUTION_COMMON_LIBS_DIR)
        }
    }
}

// TODO NOW: Test
public object HierarchicalDistributionTargetDestinationLayout : CommonizerOutputLayout {

    private fun stableDirectoryName(target: SharedCommonizerTarget): String {
        val segments = target.targets.map(::stableDirectoryName).sorted()
        return segments.joinToString(
            separator = ", ", prefix = "[", postfix = "]"
        )
    }

    public fun stableDirectoryName(target: CommonizerTarget): String {
        return when (target) {
            is LeafCommonizerTarget -> target.name
            is SharedCommonizerTarget -> stableDirectoryName(target)
        }
    }

    override fun getTargetDirectory(root: File, target: CommonizerTarget): File {
        return root.resolve(stableDirectoryName(target))
    }
}

