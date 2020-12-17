/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer.api

import java.io.File
import java.net.URLClassLoader

private const val commonizerMainClass = "org.jetbrains.kotlin.descriptors.commonizer.cli.CommonizerCLI"
private const val commonizerMainFunction = "main"

public class CliCommonizer(private val classpath: Set<File>) : Commonizer {
    override fun invoke(
        konanHome: File,
        targetLibraries: Set<File>,
        dependencyLibraries: Set<File>,
        outputHierarchy: SharedCommonizerTarget,
        outputDirectory: File
    ) {
        val arguments = mutableListOf<String>().apply {
            add("commonize")
            add("-distribution-path"); add(konanHome.absolutePath)
            add("-target-libraries"); add(targetLibraries.joinToString(";") { it.absolutePath })
            add("-dependency-libraries"); add(dependencyLibraries.joinToString(";") { it.absolutePath })
            add("-output-hierarchy"); add(outputHierarchy.identityString)
            add("-output-path"); add(outputDirectory.absolutePath)
        }

        val commonizerClassLoader = URLClassLoader(classpath.map { it.absoluteFile.toURI().toURL() }.toTypedArray())
        val commonizerMainClass = commonizerClassLoader.loadClass(commonizerMainClass)
        val commonizerMainMethod = commonizerMainClass.methods.single { it.name == commonizerMainFunction }
        commonizerMainMethod.invoke(null, arguments.toTypedArray())
    }
}
