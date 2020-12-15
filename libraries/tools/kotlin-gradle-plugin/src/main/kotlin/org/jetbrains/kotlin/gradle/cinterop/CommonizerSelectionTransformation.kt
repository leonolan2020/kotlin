/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.cinterop

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.Serializable

abstract class CommonizerSelectionTransformation : TransformAction<CommonizerSelectionTransformation.Parameters> {
    open class Parameters : TransformParameters, Serializable {
        @Input
        var targets: Set<KonanTarget> = emptySet()
    }

    @get:InputArtifact
    abstract val commonizerOutput: Provider<FileSystemLocation>

    override fun transform(output: TransformOutputs) {
        val targets = parameters.targets
        println("Selecting commonizer output for $targets")
        val commonizerOutputDirectory = commonizerOutput.get().asFile
        check(commonizerOutputDirectory.isDirectory)

        if (targets.size > 1) {
            val commonDiretory = commonizerOutputDirectory.resolve("common")
            check(commonDiretory.isDirectory) { "Missing common directory " }

            for (commonLibrary in commonDiretory.listFiles().orEmpty()) {
                val directory = output.dir("common/${commonLibrary.name}")
                commonLibrary.copyRecursively(directory)
                println("Selected common ${directory.path}")
            }
        }

        if (targets.size == 1) {
            val target = targets.single()
            val platformDirectory = commonizerOutputDirectory.resolve("platform")
            check(platformDirectory.isDirectory) { "Missing platform directory" }

            val targetDirectory = platformDirectory.resolve(target.name)
            check(targetDirectory.isDirectory) { "Unsupported platform ${target.name}" }

            for (targetLibrary in targetDirectory.listFiles().orEmpty()) {
                val directory = output.dir("${target.name}/${targetLibrary.name}")
                targetDirectory.listFiles().orEmpty().forEach { targetFile ->
                    targetFile.copyRecursively(directory)
                }
            }
        }
    }
}
