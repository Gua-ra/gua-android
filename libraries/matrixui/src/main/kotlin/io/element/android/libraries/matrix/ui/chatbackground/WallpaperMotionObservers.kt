/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.libraries.designsystem.utils.LocalUiTestMode

/**
 * Whether [artwork] can animate here and now: a live artwork on Android 13 and up whose shader has not failed, outside previews and UI tests,
 * and nothing that asks for the still applies (Remove animations, Battery Saver, the thermal status or the frame watchdog). A paused screen
 * can still animate once it resumes.
 */
@Composable
fun rememberLiveWallpaperCanAnimate(artwork: ChatArtwork): Boolean {
    // Previews and screenshot tests never animate, and their render host cannot register the platform listeners below.
    if (LocalInspectionMode.current || LocalUiTestMode.current) return false
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        artwork.isLive &&
        LiveWallpaperSession.isAvailable(artwork) &&
        rememberWallpaperMotion(isCovered = false) != WallpaperMotion.Still
}

/**
 * Feeds [WallpaperMotionPolicy] from the platform: the screen's lifecycle and [LocalLiveWallpaperHidden], the animator scale,
 * Battery Saver and the thermal status.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun rememberWallpaperMotion(isCovered: Boolean): WallpaperMotion {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animationsEnabled by rememberAnimatorsEnabled()
    val isPowerSaveMode by rememberPowerSaveMode(context, powerManager)
    val thermalStatus by rememberThermalStatus(powerManager)
    val isHidden = LocalLiveWallpaperHidden.current
    return WallpaperMotionPolicy(
        isOnScreen = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && !isHidden,
        isCovered = isCovered,
        animationsEnabled = animationsEnabled,
        isPowerSaveMode = isPowerSaveMode,
        thermalStatus = thermalStatus,
        isOverFrameBudget = LiveWallpaperSession.isOverFrameBudget,
    ).motion
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun rememberAnimatorsEnabled(): State<Boolean> {
    val enabled = remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(Unit) {
        val listener = ValueAnimator.DurationScaleChangeListener { scale -> enabled.value = scale > 0f }
        ValueAnimator.registerDurationScaleChangeListener(listener)
        onDispose { ValueAnimator.unregisterDurationScaleChangeListener(listener) }
    }
    return enabled
}

@Composable
private fun rememberPowerSaveMode(context: Context, powerManager: PowerManager?): State<Boolean> {
    val isPowerSaveMode = remember(powerManager) { mutableStateOf(powerManager?.isPowerSaveMode == true) }
    DisposableEffect(context, powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                isPowerSaveMode.value = powerManager?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return isPowerSaveMode
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
private fun rememberThermalStatus(powerManager: PowerManager?): State<Int> {
    val thermalStatus = remember(powerManager) { mutableIntStateOf(powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE) }
    DisposableEffect(powerManager) {
        val listener = PowerManager.OnThermalStatusChangedListener { status -> thermalStatus.intValue = status }
        powerManager?.addThermalStatusListener(listener)
        onDispose { powerManager?.removeThermalStatusListener(listener) }
    }
    return thermalStatus
}
