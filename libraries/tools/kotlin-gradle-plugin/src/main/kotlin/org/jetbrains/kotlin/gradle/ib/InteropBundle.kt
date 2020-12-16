/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.ib

import org.gradle.api.Project
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.jetbrains.kotlin.commonizer.api.CommonizerTarget
import org.jetbrains.kotlin.commonizer.api.LeafCommonizerTarget
import org.jetbrains.kotlin.commonizer.api.SharedCommonizerTarget
import org.jetbrains.kotlin.commonizer.api.identityString
import org.jetbrains.kotlin.compilerRunner.konanHome
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.Companion.attribute
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.CompilationSourceSetUtil.compilationsBySourceSets
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.metadata.ALL_COMPILE_METADATA_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.utils.filterValuesNotNull
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

object CommonizerTargetAttributes {
    val attribute: Attribute<String> = Attribute.of("commonizerTarget", String::class.java)
    const val matchAll = "*"
    const val matchNone = "**none**"
}

internal val Project.isInteropBundleTransformationEnabled: Boolean
    get() = PropertiesProvider(this).enableInteropBundleTransformation == true

internal fun Project.setupInteropBundleTransformationIfEnabled() {
    if (!isInteropBundleTransformationEnabled) return
    setupTransformations()
}

private fun Project.setupTransformations() = dependencies.run {
    setupAttributeSchema()
    registerInteropBundleCommonizerTransformation()
    registerInteropBundlePlatformSelectionTransformation()
    registerCommonizerOutputSelectionTransformation()
}

private fun Project.setupAttributeSchema() {
    dependencies.attributesSchema.attribute(CommonizerTargetAttributes.attribute)
    getKotlinSourceSetsWithCommonizerTargets().forEach { (sourceSet, commonizerTarget) ->
        setupAttributeSchema(sourceSet, commonizerTarget)
    }
}

private fun Project.setupAttributeSchema(sourceSet: KotlinSourceSet, commonizerTarget: CommonizerTarget) {
    val sourceSetConfigurationNames = setOf(
        sourceSet.apiMetadataConfigurationName,
        sourceSet.compileOnlyMetadataConfigurationName,
        sourceSet.implementationMetadataConfigurationName,
        sourceSet.runtimeOnlyMetadataConfigurationName
    )
    sourceSetConfigurationNames
        .map { name -> configurations.getByName(name) }
        .forEach { configuration ->
            configuration.attributes.attribute(CommonizerTargetAttributes.attribute, commonizerTarget.identityString)
        }

    val allSourceSetsCompileDependenciesMetadataConfiguration = configurations.findByName(ALL_COMPILE_METADATA_CONFIGURATION_NAME)
    allSourceSetsCompileDependenciesMetadataConfiguration?.run {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, project.usage(KotlinUsages.KOTLIN_API))
        attributes.attribute(
            CommonizerTargetAttributes.attribute,
            CommonizerTarget(KonanTarget.LINUX_X64, KonanTarget.MACOS_X64).identityString
        )
    }
}

private fun Project.registerInteropBundleCommonizerTransformation() = dependencies.run {
    val kotlin = multiplatformExtensionOrNull ?: return
    registerTransform(InteropBundleCommonizerTransformation::class.java) { spec ->
        spec.from.attribute(Usage.USAGE_ATTRIBUTE, project.usage(CommonizerUsages.interopBundle))
        spec.to.attribute(Usage.USAGE_ATTRIBUTE, project.usage(CommonizerUsages.commonizerOutput))
        spec.parameters { parameters ->
            parameters.konanHome = File(project.konanHome).absoluteFile
            afterEvaluate {
                parameters.commonizerClasspath = configurations.getByName("kotlinKlibCommonizerClasspath").resolve()
            }
            kotlin.targets.withType(KotlinNativeTarget::class.java).all { target ->
                parameters.targets = parameters.targets + target.konanTarget
            }
        }
    }
}

private fun Project.registerInteropBundlePlatformSelectionTransformation() = dependencies.run {
    for ((_, konanTarget) in KonanTarget.predefinedTargets) {
        registerTransform(InteropBundlePlatformSelectionTransformation::class.java) { spec ->
            spec.from.attribute(Usage.USAGE_ATTRIBUTE, project.usage(CommonizerUsages.interopBundle))
            spec.from.attribute(KotlinNativeTarget.konanTargetAttribute, "commonizer")
            spec.from.attribute(CommonizerTargetAttributes.attribute, CommonizerTargetAttributes.matchAll)

            spec.to.attribute(Usage.USAGE_ATTRIBUTE, project.usage(KotlinUsages.KOTLIN_API))
            spec.to.attribute(KotlinNativeTarget.konanTargetAttribute, konanTarget.name)
            spec.from.attribute(CommonizerTargetAttributes.attribute, LeafCommonizerTarget(konanTarget).identityString)


            spec.parameters { parameters ->
                parameters.target = LeafCommonizerTarget(konanTarget)
            }
        }
    }
}

abstract class EmptyTransform : TransformAction<TransformParameters.None> {
    override fun transform(outputs: TransformOutputs) {
    }
}

private fun Project.registerCommonizerOutputSelectionTransformation() = dependencies.run {
    project.afterEvaluate {
        for (sharedCommonizerTarget in getAllSharedCommonizerTargets()) {
            registerTransform(CommonizerOutputSelectionTransformation::class.java) { spec ->
                spec.from.attribute(Usage.USAGE_ATTRIBUTE, project.usage(CommonizerUsages.commonizerOutput))
                spec.from.attribute(attribute, KotlinPlatformType.native)
                spec.from.attribute(CommonizerTargetAttributes.attribute, CommonizerTargetAttributes.matchAll)

                spec.to.attribute(Usage.USAGE_ATTRIBUTE, project.usage(KotlinUsages.KOTLIN_METADATA))
                spec.to.attribute(attribute, KotlinPlatformType.common)
                spec.to.attribute(CommonizerTargetAttributes.attribute, sharedCommonizerTarget.identityString)

                spec.parameters { parameters ->
                    parameters.target = sharedCommonizerTarget
                }
            }
        }
    }
}

private fun Project.getKotlinSourceSetsWithCommonizerTargets(): Map<KotlinSourceSet, CommonizerTarget> {
    return compilationsBySourceSets(project)
        .mapValues { (_, compilations) ->
            val leafCommonizerTargetsOfSourceSet = compilations
                .filterIsInstance<KotlinNativeCompilation>()
                .map { kotlinNativeCompilation -> kotlinNativeCompilation.konanTarget }
                .map { konanTarget -> LeafCommonizerTarget(konanTarget) }
                .toSet()

            when {
                leafCommonizerTargetsOfSourceSet.isEmpty() -> null
                leafCommonizerTargetsOfSourceSet.size == 1 -> leafCommonizerTargetsOfSourceSet.single()
                else -> SharedCommonizerTarget(leafCommonizerTargetsOfSourceSet)
            }
        }
        .filterValuesNotNull()
}

private fun Project.getAllSharedCommonizerTargets(): Set<SharedCommonizerTarget> {
    return getKotlinSourceSetsWithCommonizerTargets().values.filterIsInstance<SharedCommonizerTarget>().toSet()
}
