/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.internal

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.compilerRunner.konanHome
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import java.io.File

private typealias TargetName = String

abstract class ConfigurationCommonizerTask : DefaultTask() {

    @get:Internal
    internal val targetLibraries: MutableMap<TargetName, Configuration> = mutableMapOf()

    @get:OutputDirectory
    internal val outputDirectory: File
        get() = project.buildDir.resolve("commonizer").resolve(name)

    fun addLibrary(target: KotlinNativeTarget, dependency: Any) {
        project.dependencies.add(getOrCreateConfiguration(target).name, dependency)
    }

    private fun getOrCreateConfiguration(target: KotlinNativeTarget): Configuration {
        return targetLibraries.getOrPut(target.konanTarget.name) {
            project.configurations.maybeCreate(lowerCamelCaseName("commonizer", name, target.name))
        }
    }

    @TaskAction
    internal fun commonizeLibraries() {
        logger.info("Running commonizer...")
        val libraryFiles = targetLibraries.mapValues { (_, configuration) -> configuration.files }
        libraryFiles.forEach { (target, files) ->
            logger.info("target=${target} libraries=$files")
        }
        println("args=${buildCommandLineArguments()}")
        callCommonizerCLI(project, buildCommandLineArguments())
    }

    private fun buildCommandLineArguments(): List<String> {
        val orderedTargets = targetLibraries.keys.sorted()

        return mutableListOf<String>().apply {
            add("commonize")
            add("-distribution-path"); add(project.konanHome)
            add("-targets"); add(orderedTargets.joinToString(","))
            add("-output-path"); add(outputDirectory.absolutePath)
            add("-target-libraries"); add(targetLibrariesArgument(orderedTargets))
        }
    }

    private fun targetLibrariesArgument(targets: List<TargetName>): String {
        return targets.joinToString(";") { target ->
            targetLibraries.getValue(target).files.joinToString(",")
        }
    }
}
