/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.ib

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.ib.InteropBundlePlugin.Companion.konanTargets
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.konan.target.KonanTarget

private const val INTEROP_BUNDLE_CONFIGURATION_NAME = "interopBundle"
private const val CREATE_INTEROP_BUNDLE_TASK_NAME = "createInteropBundle"
private const val CREATE_INTEROP_BUNDLE_KIB_TASK_NAME = "createInteropBundleKib"

internal val ARTIFACT_TYPE_ATTRIBUTE = Attribute.of("artifactType", String::class.java)
internal const val KLIB_ARTIFACT_TYPE = "org.jetbrains.kotlin.klib"
internal const val INTEROP_BUNDLE_ARTIFACT_TYPE = "org.jetbrains.kotlin.interopBundle"
internal const val ZIPPED_INTEROP_BUNDLE_ARTIFACT_TYPE = "org.jetbrains.kotlin.zippedInteropBundle"
internal const val COMMONIZED_INTEROP_BUNDLE_ARTIFACT_TYPE = "org.jetbrains.kotlin.commonizedInteropBundle"


open class InteropBundlePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.registerKonanTargetConfigurations()
        target.registerInteropBundleConfiguration()
        target.registerInteropBundleTasks()
        target.registerArtifacts()
        target.registerSoftwareComponent()
    }

    internal companion object {
        val konanTargets: Set<KonanTarget> get() = KonanTarget.predefinedTargets.values.toSet()
    }
}

private fun Project.registerKonanTargetConfigurations() {
    konanTargets.forEach { target ->
        configurations.register(target.name) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, KotlinUsages.KOTLIN_API))
            configuration.attributes.attribute(KotlinPlatformType.attribute, KotlinPlatformType.native)
            configuration.attributes.attribute(KotlinNativeTarget.konanTargetAttribute, target.name)
        }
    }
}

private fun Project.registerInteropBundleConfiguration() {
    configurations.register(INTEROP_BUNDLE_CONFIGURATION_NAME) { configuration ->
        configuration.isCanBeResolved = false
        configuration.isCanBeConsumed = true
        configuration.attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, KotlinUsages.KOTLIN_API))
        configuration.outgoing.attributes.attribute(COMMONIZER_TARGET_ATTRIBUTE, INTEROP_BUNDLE_COMMONIZIER_TARGET)
    }
}

private fun Project.registerInteropBundleTasks() {
    val createInteropBundleTask = tasks.register(CREATE_INTEROP_BUNDLE_TASK_NAME, CreateInteropBundleTask::class.java) { task ->
        task.group = "build"
    }

    tasks.register(CREATE_INTEROP_BUNDLE_KIB_TASK_NAME, Zip::class.java) { task ->
        task.group = "build"
        task.dependsOn(createInteropBundleTask)
        task.from(createInteropBundleTask.flatMap { it.outputDirectory })
        task.destinationDirectory.set(buildDir)
        task.archiveBaseName.set("interopBundle")
        task.archiveExtension.set("kib")
    }

    tasks.register("clean", Delete::class.java) { task ->
        task.group = "build"
        task.delete(buildDir)
    }

    tasks.register("assemble") { task ->
        task.group = "build"
        task.dependsOn(CREATE_INTEROP_BUNDLE_KIB_TASK_NAME)
    }

    tasks.register("build") { task ->
        task.group = "build"
        task.dependsOn("assemble")
    }
}

private fun Project.registerArtifacts() {
    artifacts.add(INTEROP_BUNDLE_CONFIGURATION_NAME, createInteropBundleKibTask.flatMap { it.archiveFile }) { artifact ->
        artifact.builtBy(createInteropBundleKibTask)
        artifact.type = ZIPPED_INTEROP_BUNDLE_ARTIFACT_TYPE
        artifact.extension = "kib"
    }
}

private fun Project.registerSoftwareComponent() {

}

private val Project.createInteropBundleKibTask: TaskProvider<Zip>
    get() = tasks.withType(Zip::class.java).named(CREATE_INTEROP_BUNDLE_KIB_TASK_NAME)
