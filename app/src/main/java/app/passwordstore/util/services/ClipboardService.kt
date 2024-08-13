/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.util.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import app.passwordstore.R
import app.passwordstore.util.coroutines.DispatcherProvider
import app.passwordstore.util.extensions.clipboard
import app.passwordstore.util.extensions.sharedPrefs
import app.passwordstore.util.settings.PreferenceKeys
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

@AndroidEntryPoint
class ClipboardService : Service() {

  @Inject lateinit var dispatcherProvider: DispatcherProvider
  private val scope = CoroutineScope(Job())

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent != null) {
      when (intent.action) {
        ACTION_CLEAR -> {
          clearClipboard()
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
          return super.onStartCommand(intent, flags, startId)
        }
        ACTION_START -> {
          val time = intent.getIntExtra(EXTRA_NOTIFICATION_TIME, 45)

          if (time == 0) {
            stopSelf()
          }

          createNotification(time)
          scope.launch {
            withContext(dispatcherProvider.io()) { startTimer(time) }
            withContext(dispatcherProvider.main()) {
              clearClipboard()
              stopForeground(STOP_FOREGROUND_REMOVE)
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
    val deepClear = sharedPrefs.getBoolean(PreferenceKeys.CLEAR_CLIPBOARD_HISTORY, false)
    val clipboard = clipboard

    if (clipboard != null) {
      scope.launch {
        logcat { "Clearing the clipboard" }
        val clip = ClipData.newPlainText("pgp_handler_result_pm", "")
        clipboard.setPrimaryClip(clip)
        if (deepClear) {
          withContext(dispatcherProvider.io()) {
            repeat(CLIPBOARD_CLEAR_COUNT) {
              val count = (it * 500).toString()
              clipboard.setPrimaryClip(ClipData.newPlainText(count, count))
            }
          }
        }
      }
    } else {
      logcat { "Cannot get clipboard manager service" }
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

  @SuppressLint("UnspecifiedImmutableFlag") // The offending code path cannot be hit on S
  private fun createNotification(clearTime: Int) {
    val clearTimeMs = clearTime * 1000L
    val clearIntent = Intent(this, ClipboardService::class.java).apply { action = ACTION_CLEAR }
    val pendingIntent =
      PendingIntent.getForegroundService(
        this,
        0,
        clearIntent,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
          PendingIntent.FLAG_UPDATE_CURRENT
        },
      )
    val notification = createNotification(pendingIntent, clearTimeMs)

    createNotificationChannel()
    startForeground(1, notification)
  }

  private fun createNotification(pendingIntent: PendingIntent, clearTimeMs: Long): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(getString(R.string.app_name))
      .setContentText(getString(R.string.tap_clear_clipboard))
      .setSmallIcon(R.drawable.ic_action_secure_24dp)
      .setContentIntent(pendingIntent)
      .setUsesChronometer(true)
      .setChronometerCountDown(true)
      .setShowWhen(true)
      .setWhen(System.currentTimeMillis() + clearTimeMs)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun createNotificationChannel() {
    val serviceChannel =
      NotificationChannel(
        CHANNEL_ID,
        getString(R.string.app_name),
        NotificationManager.IMPORTANCE_LOW,
      )
    val manager = getSystemService<NotificationManager>()
    if (manager != null) {
      manager.createNotificationChannel(serviceChannel)
    } else {
      logcat { "Failed to create notification channel" }
    }
  }

  companion object {

    const val ACTION_START = "ACTION_START_CLIPBOARD_TIMER"
    const val EXTRA_NOTIFICATION_TIME = "EXTRA_NOTIFICATION_TIME"
    private const val ACTION_CLEAR = "ACTION_CLEAR_CLIPBOARD"
    private const val CHANNEL_ID = "NotificationService"
    // Newest Samsung phones now feature a history of up to 30 items. To err on the side of
    // caution,
    // push 35 fake ones.
    private const val CLIPBOARD_CLEAR_COUNT = 35
  }
}
