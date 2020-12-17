/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.ib

import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.jetbrains.kotlin.commonizer.api.CommonizerTarget
import org.jetbrains.kotlin.commonizer.api.LeafCommonizerTarget
import org.jetbrains.kotlin.commonizer.api.SharedCommonizerTarget
import org.jetbrains.kotlin.commonizer.api.identityString
import org.jetbrains.kotlin.compilerRunner.konanHome
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.KLIB_COMMONIZER_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.plugin.mpp.CompilationSourceSetUtil.compilationsBySourceSets
import org.jetbrains.kotlin.gradle.targets.metadata.ALL_COMPILE_METADATA_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.utils.`is`
import org.jetbrains.kotlin.gradle.utils.filterValuesNotNull
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

val artifactTypeAttribute = Attribute.of("artifactType", String::class.java)
val klibArtifactType = "org.jetbrains.kotlin.klib"
val interopBundleArtifactType = "org.jetbrains.kotlin.kib"
val commonizedInteropBundleArtifactType = "org.jetbrains.kotlin.kib.commonized"

val commonizerTargetAttribute = Attribute.of("commonizer-target", String::class.java)
val interopBundleCommonizerTarget = "*interob-bundle*"
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

    dependencies.artifactTypes.register(interopBundleArtifactType) { definition ->
        definition.attributes.attribute(commonizerTargetAttribute, interopBundleCommonizerTarget)
    }
    dependencies.artifactTypes.register(commonizedInteropBundleArtifactType) { definition ->
        definition.attributes.attribute(commonizerTargetAttribute, wildcardCommonizerTarget)
    }

    kotlin.targets.all { target ->
        (target.compilations as DomainObjectCollection<*>).all { compilation ->
            if (compilation is KotlinCompilation<*>) {
                setupAttributeSchema(compilation)
            }
        }
    }

    kotlin.sourceSets.all { sourceSet ->
        setupAttributeSchema(sourceSet)
    }
}

private fun Project.setupAttributeSchema(compilation: KotlinCompilation<*>) {
    val commonizerTarget = getCommonizerTarget(compilation) ?: return
    val configurationsNames = compilation.relatedConfigurationNames.toSet()
    val configurations = configurationsNames.mapNotNull { name -> configurations.findByName(name) }
    configurations.forEach { configuration -> configuration.setCommonizerTargetAttributeIfAbsent(commonizerTarget) }
}


private fun Project.setupAttributeSchema(sourceSet: KotlinSourceSet) {
    val commonizerTarget = getCommonizerTarget(sourceSet) ?: return
    val configurationsNames = sourceSet.relatedConfigurationNames.toSet()
    val configurations = configurationsNames.mapNotNull { name -> configurations.findByName(name) }
    configurations.forEach { configuration -> configuration.setCommonizerTargetAttributeIfAbsent(commonizerTarget) }
}

private fun Project.registerInteropBundleCommonizerTransformation() = dependencies.run {
    val kotlin = multiplatformExtensionOrNull ?: return
    registerTransform(InteropBundleCommonizerTransformation::class.java) { spec ->


        spec.from.attribute(artifactTypeAttribute, interopBundleArtifactType)
        spec.to.attribute(artifactTypeAttribute, commonizedInteropBundleArtifactType)

        spec.from.attribute(commonizerTargetAttribute, interopBundleCommonizerTarget)
        spec.to.attribute(commonizerTargetAttribute, wildcardCommonizerTarget)

        spec.parameters { parameters ->
            parameters.konanHome = File(project.konanHome).absoluteFile
            parameters.commonizerClasspath = configurations.getByName(KLIB_COMMONIZER_CLASSPATH_CONFIGURATION_NAME).resolve()
            kotlin.targets.withType(KotlinNativeTarget::class.java).all { target ->
                parameters.targets = parameters.targets + target.konanTarget
            }
        }
    }
}


private fun Project.registerCommonizerOutputSelectionTransformation() = dependencies.run {
    for (sharedCommonizerTarget in getAllSharedCommonizerTargets()) {
        registerTransform(CommonizerOutputSelectionTransformation::class.java) { spec ->

            spec.from.attribute(artifactTypeAttribute, commonizedInteropBundleArtifactType)
            spec.to.attribute(artifactTypeAttribute, klibArtifactType)

            spec.from.attribute(commonizerTargetAttribute, wildcardCommonizerTarget)
            spec.to.attribute(commonizerTargetAttribute, sharedCommonizerTarget.identityString)

            spec.parameters { parameters ->
                parameters.target = sharedCommonizerTarget
            }
        }
    }
}


private fun Project.registerInteropBundlePlatformSelectionTransformation() = dependencies.run {
    for ((_, konanTarget) in KonanTarget.predefinedTargets) {
        registerTransform(InteropBundlePlatformSelectionTransformation::class.java) { spec ->

            spec.from.attribute(artifactTypeAttribute, interopBundleArtifactType)
            spec.to.attribute(artifactTypeAttribute, klibArtifactType)

            spec.from.attribute(commonizerTargetAttribute, interopBundleCommonizerTarget)
            spec.to.attribute(commonizerTargetAttribute, CommonizerTarget(konanTarget).identityString)

            spec.parameters { parameters ->
                parameters.target = LeafCommonizerTarget(konanTarget)
            }
        }
    }
}


private fun Project.getAllSharedCommonizerTargets(): Set<SharedCommonizerTarget> {
    val kotlin = multiplatformExtensionOrNull ?: return emptySet()
    return kotlin.sourceSets
        .map { sourceSet -> getCommonizerTarget(sourceSet) }
        .filterIsInstance<SharedCommonizerTarget>()
        .toSet()
        .onEach { sharedCommonizerTarget ->
            require(sharedCommonizerTarget.targets.all { it is LeafCommonizerTarget }) {
                "Hierarchical commonization is not yet supported: $sharedCommonizerTarget"
            }
        }
}

private fun Configuration.setCommonizerTargetAttributeIfAbsent(target: CommonizerTarget) {
    setCommonizerTargetAttributeIfAbsent(target.identityString)
}

private fun Configuration.setCommonizerTargetAttributeIfAbsent(value: String) {
    if (!attributes.contains(commonizerTargetAttribute)) {
        attributes.attribute(commonizerTargetAttribute, value)
    }
}

