/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.utils

fun <K, V : Any> Map<K, V?>.filterValuesNotNull(): Map<K, V> {
    @Suppress("UNCHECKED_CAST")
    return filterValues { it != null } as Map<K, V>
}

inline fun <K, V, T : Any> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> T?): Map<K, T> {
    return mapValues(transform).filterValuesNotNull()
}

