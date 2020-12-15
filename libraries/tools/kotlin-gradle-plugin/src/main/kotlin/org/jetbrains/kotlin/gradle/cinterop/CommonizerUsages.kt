/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.cinterop

import org.gradle.api.Project
import org.gradle.api.attributes.Usage

fun Project.usage(name: String): Usage = objects.named(Usage::class.java, name)

object CommonizerUsages {
    val interopBundle = "commonizer-interop-bundle"
    val commonizerOutput = "commonizer-output"
}
