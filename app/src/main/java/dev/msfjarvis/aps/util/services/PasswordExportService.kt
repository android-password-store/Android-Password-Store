/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.data.repo.PasswordRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.TimeZone
import logcat.logcat

class PasswordExportService : Service() {

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent != null) {
      when (intent.action) {
        ACTION_EXPORT_PASSWORD -> {
          val uri = intent.getParcelableExtra<Uri>("uri")
          if (uri != null) {
            val targetDirectory = DocumentFile.fromTreeUri(applicationContext, uri)

            if (targetDirectory != null) {
              createNotification()
              exportPasswords(targetDirectory)
              stopSelf()
              return START_NOT_STICKY
            }
          }
        }
      }
    }
    return super.onStartCommand(intent, flags, startId)
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }

  /**
   * Exports passwords to the given directory.
   *
   * Recursively copies the existing password store to an external directory.
   *
   * @param targetDirectory directory to copy password directory to.
   */
  private fun exportPasswords(targetDirectory: DocumentFile) {

    val repositoryDirectory = requireNotNull(PasswordRepository.getRepositoryDirectory())
    val sourcePassDir = DocumentFile.fromFile(repositoryDirectory)

    logcat { "Copying ${repositoryDirectory.path} to $targetDirectory" }

    val dateString =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
      } else {
        String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z")))
      }

    val passDir = targetDirectory.createDirectory("password_store_$dateString")

    if (passDir != null) {
      copyDirToDir(sourcePassDir, passDir)
    }
  }

  /**
   * Copies a password file to a given directory.
   *
   * Note: this does not preserve last modified time.
   *
   * @param passwordFile password file to copy.
   * @param targetDirectory target directory to copy password.
   */
  private fun copyFileToDir(passwordFile: DocumentFile, targetDirectory: DocumentFile) {
    val sourceInputStream = contentResolver.openInputStream(passwordFile.uri)
    val name = passwordFile.name
    val targetPasswordFile = targetDirectory.createFile("application/octet-stream", name!!)
    if (targetPasswordFile?.exists() == true) {
      val destOutputStream = contentResolver.openOutputStream(targetPasswordFile.uri)

      if (destOutputStream != null && sourceInputStream != null) {
        sourceInputStream.copyTo(destOutputStream, 1024)

        sourceInputStream.close()
        destOutputStream.close()
      }
    }
  }

  /**
   * Recursively copies a directory to a destination.
   *
   * @param sourceDirectory directory to copy from.
   * @param targetDirectory directory to copy to.
   */
  private fun copyDirToDir(sourceDirectory: DocumentFile, targetDirectory: DocumentFile) {
    sourceDirectory.listFiles().forEach { file ->
      if (file.isDirectory) {
        // Create new directory and recurse
        val newDir = targetDirectory.createDirectory(file.name!!)
        copyDirToDir(file, newDir!!)
      } else {
        copyFileToDir(file, targetDirectory)
      }
    }
  }

  private fun createNotification() {
    createNotificationChannel()

    val notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.exporting_passwords))
        .setSmallIcon(R.drawable.ic_round_import_export)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    startForeground(2, notification)
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val serviceChannel =
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.app_name),
          NotificationManager.IMPORTANCE_LOW
        )
      val manager = getSystemService<NotificationManager>()
      if (manager != null) {
        manager.createNotificationChannel(serviceChannel)
      } else {
        logcat { "Failed to create notification channel" }
      }
    }
  }

  companion object {

    const val ACTION_EXPORT_PASSWORD = "ACTION_EXPORT_PASSWORD"
    private const val CHANNEL_ID = "NotificationService"
  }
}
