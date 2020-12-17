/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.ib

import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.jetbrains.kotlin.commonizer.api.CommonizerTarget
import org.jetbrains.kotlin.commonizer.api.LeafCommonizerTarget
import org.jetbrains.kotlin.commonizer.api.SharedCommonizerTarget
import org.jetbrains.kotlin.commonizer.api.identityString
import org.jetbrains.kotlin.compilerRunner.konanHome
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.Companion.attribute
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.plugin.mpp.CompilationSourceSetUtil.compilationsBySourceSets
import org.jetbrains.kotlin.gradle.plugin.mpp.CompilationSourceSetUtil.sourceSetsInMultipleCompilations
import org.jetbrains.kotlin.gradle.utils.filterValuesNotNull
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

val artifactTypeAttribute = Attribute.of("artifactType", String::class.java)
val klibArtifactType = "org.jetbrains.kotlin.klib"
val interopBundleArtifactType = "org.jetbrains.kotlin.kib"
val commonizedInteropBundleArtifactType = "org.jetbrains.kotlin.kib.commonized"

val commonizerTargetAttribute = Attribute.of("commonizer-target", String::class.java)
val wildcardCommonizerTarget = "*"


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
    val kotlin = multiplatformExtensionOrNull ?: return
    dependencies.attributesSchema.attribute(commonizerTargetAttribute)
    dependencies.artifactTypes.register(interopBundleArtifactType)
    dependencies.artifactTypes.register(commonizedInteropBundleArtifactType) { definition ->
        definition.attributes.attribute(commonizerTargetAttribute, wildcardCommonizerTarget)
    }

    kotlin.targets.all { target ->
        (target.compilations as DomainObjectCollection<KotlinCompilation<*>>).all { compilation ->
            setupAttributeSchema(compilation)
        }
    }
}

private fun Project.setupAttributeSchema(compilation: KotlinCompilation<*>) {
    val commonizerTarget = getCommonizerTarget(compilation) ?: return
    val configurationsNames = compilation.relatedConfigurationNames.toSet()
    val configurations = configurationsNames.map { name -> configurations.getByName(name) }
    configurations.forEach { configuration ->
        configuration.attributes.attribute(commonizerTargetAttribute, commonizerTarget.identityString)
    }
}

private fun Project.getCommonizerTarget(compilation: KotlinCompilation<*>): CommonizerTarget? {
    if (compilation is KotlinNativeCompilation) {
        return LeafCommonizerTarget(compilation.konanTarget)
    }

    if (compilation is KotlinMetadataCompilation) {
        val sourceSets = compilation.allKotlinSourceSets
        val participatingKonanTargets = multiplatformExtension.targets
            .flatMap { target -> target.compilations }
            .filterIsInstance<KotlinNativeCompilation>()
            .filter { nativeCompilation -> nativeCompilation.allKotlinSourceSets.any { it in sourceSets } }
            .map { nativeCompilation -> nativeCompilation.konanTarget }
        if (participatingKonanTargets.isEmpty()) return null
        return CommonizerTarget(participatingKonanTargets)
    }


    return null
}

/*
private fun Project.setupAttributeSchema(sourceSet: KotlinSourceSet, commonizerTarget: CommonizerTarget) {
    val sourceSetConfigurationNames = setOf(
        sourceSet.apiMetadataConfigurationName,
        sourceSet.compileOnlyMetadataConfigurationName,
        sourceSet.implementationMetadataConfigurationName,
        sourceSet.runtimeOnlyMetadataConfigurationName
    )
    sourceSetConfigurationNames
        .map { name -> configurations.getByName(name) }
        .forEach { configuration -> configuration.attributes.attribute(commonizerTargetAttribute, commonizerTarget.identityString) }
}
 */

private fun Project.registerInteropBundleCommonizerTransformation() = dependencies.run {
    val kotlin = multiplatformExtensionOrNull ?: return
    registerTransform(InteropBundleCommonizerTransformation::class.java) { spec ->

        spec.from.attribute(artifactTypeAttribute, interopBundleArtifactType)
        spec.to.attribute(artifactTypeAttribute, commonizedInteropBundleArtifactType)

        spec.parameters { parameters ->
            parameters.konanHome = File(project.konanHome).absoluteFile
            afterEvaluate { parameters.commonizerClasspath = configurations.getByName("kotlinKlibCommonizerClasspath").resolve() }
            kotlin.targets.withType(KotlinNativeTarget::class.java).all { target ->
                parameters.targets = parameters.targets + target.konanTarget
            }
        }
    }
}

private fun Project.registerInteropBundlePlatformSelectionTransformation() = dependencies.run {
    for ((_, konanTarget) in KonanTarget.predefinedTargets) {
        registerTransform(InteropBundlePlatformSelectionTransformation::class.java) { spec ->

            spec.from.attribute(artifactTypeAttribute, interopBundleArtifactType)
            spec.to.attribute(artifactTypeAttribute, klibArtifactType)

            spec.from.attribute(commonizerTargetAttribute, wildcardCommonizerTarget)
            spec.to.attribute(commonizerTargetAttribute, CommonizerTarget(konanTarget).identityString)

            spec.parameters { parameters ->
                parameters.target = LeafCommonizerTarget(konanTarget)
            }
        }
    }
}

private fun Project.registerCommonizerOutputSelectionTransformation() = dependencies.run {
    project.afterEvaluate {
        for (sharedCommonizerTarget in getAllSharedCommonizerTargets()) {
            registerTransform(CommonizerOutputSelectionTransformation::class.java) { spec ->

                spec.from.attribute(artifactTypeAttribute, interopBundleArtifactType)
                spec.to.attribute(artifactTypeAttribute, klibArtifactType)

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
