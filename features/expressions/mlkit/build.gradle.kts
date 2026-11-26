import extension.setupDependencyInjection

/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.features.expressions.mlkit"
}

setupDependencyInjection()

dependencies {
    implementation(projects.features.expressions.api)
    implementation(projects.features.expressions.impl)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(libs.coroutines.core)
    implementation(libs.google.mlkit.subject.segmentation)
}
