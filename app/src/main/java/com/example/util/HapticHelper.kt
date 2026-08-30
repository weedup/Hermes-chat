package com.example.util

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

class HapticHelper(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    enum class HapticType {
        KEYPRESS,
        LIGHT_TICK,
        CLICK,
        HEAVY_CLICK,
        SUCCESS,
        ERROR,
        SPEN_HOVER
    }

    fun trigger(type: HapticType, isEnabled: Boolean = true) {
        if (!isEnabled || vibrator == null || !vibrator!!.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (type) {
                    HapticType.KEYPRESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    HapticType.LIGHT_TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    HapticType.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    HapticType.HEAVY_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    HapticType.SUCCESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                    HapticType.ERROR -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val timings = longArrayOf(0, 40, 60, 40)
                            val amplitudes = intArrayOf(0, 200, 0, 255)
                            VibrationEffect.createWaveform(timings, amplitudes, -1)
                        } else {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                        }
                    }
                    HapticType.SPEN_HOVER -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                }
                vibrator?.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25)
            }
        } catch (_: Exception) {
            // Ignore if vibration fails
        }
    }

    fun performViewHaptic(view: View, type: HapticType) {
        val constant = when (type) {
            HapticType.KEYPRESS -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticType.CLICK -> HapticFeedbackConstants.VIRTUAL_KEY
            HapticType.HEAVY_CLICK -> HapticFeedbackConstants.LONG_PRESS
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }
}
