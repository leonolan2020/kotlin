/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.cli

import org.jetbrains.kotlin.commonizer.api.*
import org.jetbrains.kotlin.descriptors.commonizer.*
import org.jetbrains.kotlin.descriptors.commonizer.EmptyRepository
import org.jetbrains.kotlin.descriptors.commonizer.KonanDistribution
import org.jetbrains.kotlin.descriptors.commonizer.KonanDistributionRepository
import org.jetbrains.kotlin.descriptors.commonizer.Repository
import org.jetbrains.kotlin.descriptors.commonizer.konan.NativeDistributionCommonizer
import org.jetbrains.kotlin.descriptors.commonizer.konan.NativeDistributionCommonizer.StatsType
import org.jetbrains.kotlin.konan.library.KONAN_DISTRIBUTION_KLIB_DIR
import org.jetbrains.kotlin.konan.library.KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

internal class NativeDistributionListTargets(options: Collection<Option<*>>) : Task(options) {
    override val category get() = Category.INFORMATIONAL

    override fun execute(logPrefix: String) {
        val distributionPath = getMandatory<File, NativeDistributionOptionType>()

        val targets = distributionPath.resolve(KONAN_DISTRIBUTION_KLIB_DIR)
            .resolve(KONAN_DISTRIBUTION_PLATFORM_LIBS_DIR)
            .list()
            ?.sorted()
            ?: emptyList()

        println()
        if (targets.isEmpty())
            println("No hardware targets found inside of the Kotlin/Native distribution \"$distributionPath\".")
        else {
            println("${targets.size} hardware targets found inside of the Kotlin/Native distribution \"$distributionPath\":")
            targets.forEach(::println)
        }
        println()
    }
}

internal class Commonize(options: Collection<Option<*>>) : Task(options) {
    override val category: Category = Category.COMMONIZATION

    override fun execute(logPrefix: String) {
        val distribution = KonanDistribution(getMandatory<File, NativeDistributionOptionType>())
        val destination = getMandatory<File, OutputOptionType>()
        val targetLibraries = getMandatory<List<File>, TargetLibrariesOptionType>()
        val dependencyLibraries = getMandatory<List<File>, DependencyLibrariesOptionType>()
        val outputHierarchy = getMandatory<SharedCommonizerTarget, OutputHierarchyOptionType>()

        val statsType = getOptional<StatsType, StatsTypeOptionType> { it == "log-stats" } ?: StatsType.NONE

        val logger = CliLoggerAdapter(2)
        val libraryLoader = DefaultNativeLibraryLoader(logger)

        NativeDistributionCommonizer(
            konanDistribution = distribution,
            repository = FilesRepository(targetLibraries.toSet(), libraryLoader),
            dependencies = KonanDistributionRepository(distribution, outputHierarchy.konanTargets, libraryLoader) +
                    FilesRepository(dependencyLibraries.toSet(), libraryLoader),
            targets = outputHierarchy.konanTargets.toList(),
            destination = destination,
            destinationLayout = HierarchicalCommonizerOutputLayout,
            statsType = statsType,
            logger = CliLoggerAdapter(2)

        ).run()
    }
}

internal class NativeDistributionCommonize(options: Collection<Option<*>>) : Task(options) {
    override val category get() = Category.COMMONIZATION

    override fun execute(logPrefix: String) {
        val distribution = KonanDistribution(getMandatory<File, NativeDistributionOptionType>())
        val destination = getMandatory<File, OutputOptionType>()
        val targets = getMandatory<List<KonanTarget>, NativeTargetsOptionType>()
        val statsType = getOptional<StatsType, StatsTypeOptionType> { it == "log-stats" } ?: StatsType.NONE
        val targetNames = targets.joinToString { "[${it.name}]" }

        val logger = CliLoggerAdapter(2)
        val repository = KonanDistributionRepository(distribution, targets.toSet(), DefaultNativeLibraryLoader(logger))

        val descriptionSuffix = estimateLibrariesCount(repository, targets).let { " ($it items)" }
        val description = "${logPrefix}Preparing commonized Kotlin/Native libraries for targets $targetNames$descriptionSuffix"
        println(description)

        NativeDistributionCommonizer(
            konanDistribution = distribution,
            repository = repository,
            dependencies = EmptyRepository,
            targets = targets,
            destination = destination,
            destinationLayout = NativeDistributionCommonizerOutputLayout,
            statsType = statsType,
            logger = logger,
        ).run()

        println("$description: Done")
    }

    companion object {
        private fun estimateLibrariesCount(repository: Repository, targets: List<KonanTarget>): Int {
            return targets.flatMap { repository.getLibraries(LeafCommonizerTarget(it)) }.count()
        }
    }
}
