/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.github.ajalt.timberkt.d
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.clipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClipboardService : Service() {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private val settings: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_CLEAR -> {
                    clearClipboard()
                    stopForeground(true)
                    stopSelf()
                    return super.onStartCommand(intent, flags, startId)
                }

                ACTION_START -> {
                    val time = try {
                        Integer.parseInt(settings.getString(PreferenceKeys.GENERAL_SHOW_TIME, "45") as String)
                    } catch (e: NumberFormatException) {
                        45
                    }

                    if (time == 0) {
                        stopSelf()
                    }

                    createNotification()
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            startTimer(time)
                        }
                        withContext(Dispatchers.Main) {
                            clearClipboard()
                            stopForeground(true)
                            stopSelf()
                        }
                    }
                    return START_NOT_STICKY
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun clearClipboard() {
        val deepClear = settings.getBoolean(PreferenceKeys.CLEAR_CLIPBOARD_20X, false)
        val clipboard = clipboard

        if (clipboard != null) {
            scope.launch {
                d { "Clearing the clipboard" }
                val clip = ClipData.newPlainText("pgp_handler_result_pm", "")
                clipboard.setPrimaryClip(clip)
                if (deepClear) {
                    withContext(Dispatchers.IO) {
                        repeat(20) {
                            val count = (it * 500).toString()
                            clipboard.setPrimaryClip(ClipData.newPlainText(count, count))
                        }
                    }
                }
            }
        } else {
            d { "Cannot get clipboard manager service" }
        }
    }

    private suspend fun startTimer(showTime: Int) {
        var current = 0
        while (scope.isActive && current < showTime) {
            // Block for 1s or until cancel is signalled
            current++
            delay(1000)
        }
    }

    private fun createNotification() {
        createNotificationChannel()
        val clearIntent = Intent(this, ClipboardService::class.java)
        clearIntent.action = ACTION_CLEAR
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, 0, clearIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(this, 0, clearIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.tap_clear_clipboard))
            .setSmallIcon(R.drawable.ic_action_secure_24dp)
            .setContentIntent(pendingIntent)
            .setUsesChronometer(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService<NotificationManager>()
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel)
            } else {
                d { "Failed to create notification channel" }
            }
        }
    }

    companion object {
        private const val ACTION_CLEAR = "ACTION_CLEAR_CLIPBOARD"
        const val ACTION_START = "ACTION_START_CLIPBOARD_TIMER"
        private const val CHANNEL_ID = "NotificationService"
    }
}
