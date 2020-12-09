/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.cli

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
        val targets = getMandatory<List<KonanTarget>, NativeTargetsOptionType>()
        val targetLibraries = getMandatory<List<LibrarySet>, LibrariesSetOptionType>()
        val statsType = getOptional<StatsType, StatsTypeOptionType> { it == "log-stats" } ?: StatsType.NONE


        NativeDistributionCommonizer(
            konanDistribution = distribution,
            repository = repository(targets, targetLibraries),
            dependencies = KonanDistributionRepository(distribution),
            targets = targets,
            destination = destination,
            statsType = statsType,
            logger = CliLoggerAdapter(2)

        ).run()

        println("DONE!!!")
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

        val repository = KonanDistributionRepository(distribution)

        val descriptionSuffix = estimateLibrariesCount(repository, targets).let { " ($it items)" }
        val description = "${logPrefix}Preparing commonized Kotlin/Native libraries for targets $targetNames$descriptionSuffix"
        println(description)

        NativeDistributionCommonizer(
            konanDistribution = distribution,
            repository = repository,
            dependencies = EmptyRepository,
            targets = targets,
            destination = destination,
            statsType = statsType,
            logger = CliLoggerAdapter(2)
        ).run()

        println("$description: Done")
    }

    companion object {
        private fun estimateLibrariesCount(repository: Repository, targets: List<KonanTarget>): Int {
            return targets.flatMap { repository.getLibraries(LeafTarget(it.name, it)) }.count()
        }
    }
}


private fun repository(targets: List<KonanTarget>, librarySets: List<LibrarySet>): Repository {
    require(targets.size == librarySets.size) { ":( Better error message? " }

    val setsByTarget = librarySets.withIndex().associate { (index, set) ->
        targets[index] to set
    }

    return object : Repository {
        override fun getLibraries(target: LeafTarget): List<File> {
            return setsByTarget[target.konanTarget]?.files?.toList().orEmpty()
        }
    }
}
