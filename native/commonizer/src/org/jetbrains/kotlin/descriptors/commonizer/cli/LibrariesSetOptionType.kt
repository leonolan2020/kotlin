/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.cli

import java.io.File


internal object LibrariesSetOptionType : OptionType<List<File>>(
    mandatory = true,
    alias = "target-libraries",
    description = "; and , separated (TODO NOW)"
) {
    override fun parse(rawValue: String, onError: (reason: String) -> Nothing): Option<List<File>> {
        return Option(this, rawValue.split(";").map(::File))
    }
}
