/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.LibraryBuildType
import config.BuildTimeConfig
import config.FirebaseApp
import extension.setupDependencyInjection
import extension.testCommonDependencies
import org.gradle.kotlin.dsl.withType
import org.sonarqube.gradle.SonarResolverTask

plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.pushproviders.firebase"

    buildFeatures {
        resValues = true
    }

    buildTypes {
        getByName("release") {
            consumerProguardFiles("consumer-proguard-rules.pro")
            // GUA FORK: the release build type serves two apps. `-Pgua.deployment=dev`
            // builds the QA app (applicationId global.gua.dev), which is a separate app
            // record in a separate Firebase project, so it needs its own values here.
            // Without this branch QA would register under production's project and get no
            // push at all from a dev server, whose credential cannot address it.
            val useDevDeployment = (project.findProperty("gua.deployment") as? String) == "dev"
            firebase(if (useDevDeployment) BuildTimeConfig.FIREBASE_APP_DEV else BuildTimeConfig.FIREBASE_APP_RELEASE)
        }
        getByName("debug") {
            firebase(BuildTimeConfig.FIREBASE_APP_DEBUG)
        }
        register("nightly") {
            consumerProguardFiles("consumer-proguard-rules.pro")
            matchingFallbacks += listOf("release")
            firebase(BuildTimeConfig.FIREBASE_APP_NIGHTLY)
        }
    }
}

/**
 * Writes the string resources the Firebase SDK reads its options from.
 *
 * All of them, not just the app id: a variant that took its app id from one project and its
 * sender from another would mint a token no one can address, and the SDK would report success.
 * A variant with no Firebase app gets empty values, which is what makes the SDK skip
 * initialisation instead of registering as some other package.
 */
fun LibraryBuildType.firebase(app: FirebaseApp) {
    resValue(type = "string", name = "google_app_id", value = app.googleAppId)
    resValue(type = "string", name = "gcm_defaultSenderId", value = app.project?.senderId.orEmpty())
    resValue(type = "string", name = "google_api_key", value = app.project?.apiKey.orEmpty())
    resValue(type = "string", name = "google_storage_bucket", value = app.project?.storageBucket.orEmpty())
    resValue(type = "string", name = "project_id", value = app.project?.projectId.orEmpty())
}

// Configure the SonarQube plugin to wait for the resource generation tasks to complete before running the analysis.
tasks.withType<SonarResolverTask>().configureEach {
    dependsOn("generateDebugResValues", "generateDebugAndroidTestResValues")
}

setupDependencyInjection()

dependencies {
    implementation(libs.androidx.corektx)
    implementation(projects.features.enterprise.api)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.push.api)
    implementation(projects.libraries.sessionStorage.api)
    implementation(projects.libraries.uiStrings)
    implementation(projects.libraries.troubleshoot.api)
    implementation(projects.services.toolbox.api)

    implementation(projects.libraries.pushstore.api)
    implementation(projects.libraries.pushproviders.api)

    api(platform(libs.google.firebase.bom))
    api("com.google.firebase:firebase-messaging") {
        exclude(group = "com.google.firebase", module = "firebase-core")
        exclude(group = "com.google.firebase", module = "firebase-analytics")
        // GUA FORK: firebase-measurement-connector is NOT excluded, although upstream excludes it
        // alongside analytics. It carries one interface, AnalyticsConnector, and no analytics: the
        // implementation only exists if firebase-analytics is present, which it is not, so the
        // connector resolves to null and the SDK logs and moves on.
        //
        // Without it the app crashes on every FCM notification message. FCM attaches an analytics
        // label to a notification-type message, the SDK takes MessagingAnalytics.logNotificationReceived
        // on receipt, and that path hard-references AnalyticsConnector: NoClassDefFoundError on the
        // Firebase-Messaging-Intent-Handle thread, before the notification is ever displayed.
        // Upstream never meets this because every Element push is a data message from Sygnal.
        //
        // Gua's account-authority security alerts are notification messages on purpose, because they
        // have to be shown while the app is signed out and possibly not running, so the whole ADM-009
        // gate 2 channel landed on the one message type this app could not receive. Verified live on
        // a device: with the exclusion, a notification message crashed the process and nothing was
        // shown; a data message to the same token was handled normally.
    }

    testCommonDependencies(libs)
    testImplementation(libs.kotlinx.collections.immutable)
    testImplementation(projects.features.enterprise.test)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.libraries.push.test)
    testImplementation(projects.libraries.pushstore.test)
    testImplementation(projects.libraries.sessionStorage.test)
    testImplementation(projects.libraries.troubleshoot.test)
    testImplementation(projects.services.toolbox.test)
}
