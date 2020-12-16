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
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.jetbrains.kotlin.commonizer.api.LeafCommonizerTarget
import org.jetbrains.kotlin.commonizer.api.identityString
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File
import java.io.Serializable
import java.net.URLClassLoader


private const val commonizerMainClass = "org.jetbrains.kotlin.descriptors.commonizer.cli.CommonizerCLI"
private const val commonizerMainFunction = "main"

abstract class CommonizerTransformation : TransformAction<CommonizerTransformation.Parameters> {
    open class Parameters : TransformParameters, Serializable {

        @InputFile
        var konanHome: File? = null

        @Input
        var targets: Set<KonanTarget> = emptySet()

        @Classpath
        var commonizerClasspath: Set<File>? = null
    }

    @get:Classpath
    @get:InputArtifact
    abstract val interopBundle: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        println("Commonizing ${interopBundle.get().asFile} into ${parameters.targets}")
        val interopBundleFile = interopBundle.get().asFile
        val klibs = interopBundleFile.walkTopDown().filter { it.isFile && it.extension == "klib" }.toSet()
        println("Klibs: $klibs")
        val commandLineArguments = buildCommandLineArguments(outputs.dir("commonized"), klibs)
        println("Command line args=$commandLineArguments")

        val commonizerClasspath = parameters.commonizerClasspath ?: error("Missing commonizerClasspath")
        val commonizerClassLoader = URLClassLoader(commonizerClasspath.map { it.absoluteFile.toURI().toURL() }.toTypedArray())
        val commonizerMainClass = commonizerClassLoader.loadClass(commonizerMainClass)
        val commonizerMainMethod = commonizerMainClass.methods.single { it.name == commonizerMainFunction }
        commonizerMainMethod.invoke(null, commandLineArguments.toTypedArray())
    }

    private fun buildCommandLineArguments(output: File, klibs: Set<File>): List<String> {
        val konanHome = parameters.konanHome ?: error("Missing konanHome")
        return mutableListOf<String>().apply {
            add("commonize")
            add("-distribution-path"); add(konanHome.absolutePath)
            add("-targets"); add(parameters.targets.joinToString(","))
            add("-output-path"); add(output.absolutePath)
            add("-target-libraries"); add(klibs.joinToString(";") { it.absolutePath })
        }
    }
}


abstract class PlatformInteropKlibSelectionTransformation : TransformAction<PlatformInteropKlibSelectionTransformation.Parameters> {
    open class Parameters : TransformParameters, Serializable {
        @Input
        var target: LeafCommonizerTarget? = null
    }

    @get:Classpath
    @get:InputArtifact
    abstract val interopBundle: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val target = parameters.target ?: error("Missing target")
        val interopBundleFile = interopBundle.get().asFile
        val platformLibraryFiles = interopBundleFile.resolve(target.identityString)
        for (platformLibraryFile in platformLibraryFiles.listFiles().orEmpty()) {
            val outputFile = outputs.file(File(target.identityString).resolve(platformLibraryFile.name))
            platformLibraryFile.copyTo(outputFile)
        }
    }
}
