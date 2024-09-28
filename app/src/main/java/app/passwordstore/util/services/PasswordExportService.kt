/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import app.passwordstore.R
import app.passwordstore.data.repo.PasswordRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import logcat.logcat

class PasswordExportService : Service() {

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent != null) {
      when (intent.action) {
        ACTION_EXPORT_PASSWORD -> {
          val uri = IntentCompat.getParcelableExtra(intent, "uri", Uri::class.java)
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

    val repositoryDirectory =
      requireNotNull(PasswordRepository.getRepositoryDirectory()) {
        "Password directory must be set to export them"
      }
    val sourcePassDir = DocumentFile.fromFile(repositoryDirectory)

    logcat { "Copying ${repositoryDirectory.path} to $targetDirectory" }

    val dateString = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
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
        sourceInputStream.use { source -> destOutputStream.use { dest -> source.copyTo(dest) } }
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

    const val ACTION_EXPORT_PASSWORD = "ACTION_EXPORT_PASSWORD"
    private const val CHANNEL_ID = "NotificationService"
  }
}
