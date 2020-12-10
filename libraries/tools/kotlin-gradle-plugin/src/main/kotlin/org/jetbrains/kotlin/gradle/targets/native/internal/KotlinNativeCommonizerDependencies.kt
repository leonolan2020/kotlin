/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.internal

import org.gradle.api.Named
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import javax.inject.Inject

class CommonizerRequest @Inject constructor(
    private val project: Project,
    private val name: String
) : Named {

    override fun getName(): String = name

    private val commonizerTask = project.tasks.register(lowerCamelCaseName("commonize", name), DependencyCommonizerTask::class.java)

}


