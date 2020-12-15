/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.internal

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.transform.*
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.artifacts.DependencyResolveContext
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.compilerRunner.konanHome
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.plugin.usageByName
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.library.ToolingSingleFileKlibResolveStrategy
import org.jetbrains.kotlin.library.resolveSingleFileKlib
import org.jetbrains.kotlin.library.uniqueName
import java.io.File
import java.io.Serializable

// NOTE: commonization could form some kind of

private typealias TargetName = String


abstract class CommonizedLibrarySelectorTransformation : TransformAction<CommonizedLibrarySelectorTransformation.Parameters> {
    open class Parameters : TransformParameters, Serializable {
        @get:Input
        var targets: Set<KonanTarget> = emptySet()
    }

    @get:InputArtifact
    abstract val commonizedRootDirectory: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {

        val library = resolveSingleFileKlib(
            libraryFile = org.jetbrains.kotlin.konan.file.File(""),
            strategy = ToolingSingleFileKlibResolveStrategy
        )

        library.uniqueName


        println("RUNNING COMMONIZED PICKER")
        outputs.dir(commonizedRootDirectory.get().asFile.resolve("someTarget")).mkdirs()
    }
}

abstract class DependencyCommonizerTask : DefaultTask() {

    @get:Internal
    internal val targetLibraries: MutableMap<TargetName, Configuration> = mutableMapOf()

    private val outputLibraries: MutableMap<Set<TargetName>, ConfigurableFileCollection> = mutableMapOf()

    @get:OutputDirectory
    internal val outputDirectory: File
        get() = project.buildDir.resolve("commonizer").resolve(name)

    fun addLibrary(target: KotlinNativeTarget, dependency: Any) {
        project.dependencies.add(maybeCreateTargetConfiguration(target).name, dependency)

    }

    private fun maybeCreateTargetConfiguration(target: KotlinNativeTarget): Configuration {
        return targetLibraries.getOrPut(target.konanTarget.name) {
            project.configurations.maybeCreate(lowerCamelCaseName("commonize", name, target.name)).also { configuration ->
                configuration.isCanBeConsumed = false
                configuration.isCanBeResolved = true
            }
        }
    }

    fun getOutput(vararg target: KotlinNativeTarget): FileCollection {
        return getOutput(*target.map { it.konanTarget.name }.toTypedArray())
    }

    fun getOutput(vararg targetNames: TargetName): FileCollection {
        return outputLibraries.getOrPut(targetNames.toSet()) { project.files().builtBy(this) }
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

        for ((targets, fileCollection) in outputLibraries) {
            val files = when {
                targets.size == 1 -> outputDirectory.resolve("platform/${targets.single()}").listFiles().orEmpty()
                targets.size > 1 -> outputDirectory.resolve("common").listFiles().orEmpty()
                else -> error("No target specified")
            }

            check(files.isNotEmpty()) { "No libraries found in ${files.toSet()}" }
            check(files.all { it.exists() })
            fileCollection.from(*files)
        }
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
