/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer

import org.jetbrains.kotlin.descriptors.commonizer.konan.NativeDistributionCommonizer
import org.jetbrains.kotlin.test.testFramework.KtUsefulTestCase
import org.junit.Test
import java.io.File

class LibcurlCommonizationTest : KtUsefulTestCase() {

    private val linuxLibraryFile = testDataDirectory.resolve("libcurl/libcurl-linux-x64.klib")
    private val macosLibraryFile = testDataDirectory.resolve("libcurl/libcurl-macos-x64.klib")

    fun testSuccessfulCommonization() {
        // TODO???
    }
}
