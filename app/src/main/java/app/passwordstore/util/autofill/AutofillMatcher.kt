/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.util.autofill

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.core.content.edit
import app.passwordstore.R
import com.github.androidpasswordstore.autofillparser.FormOrigin
import com.github.androidpasswordstore.autofillparser.computeCertificatesHash
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import logcat.LogPriority.ERROR
import logcat.LogPriority.WARN
import logcat.logcat

private const val PREFERENCES_AUTOFILL_APP_MATCHES = "oreo_autofill_app_matches"
private val Context.autofillAppMatches
  get() = getSharedPreferences(PREFERENCES_AUTOFILL_APP_MATCHES, Context.MODE_PRIVATE)

private const val PREFERENCES_AUTOFILL_WEB_MATCHES = "oreo_autofill_web_matches"
private val Context.autofillWebMatches
  get() = getSharedPreferences(PREFERENCES_AUTOFILL_WEB_MATCHES, Context.MODE_PRIVATE)

private fun Context.matchPreferences(formOrigin: FormOrigin): SharedPreferences {
  return when (formOrigin) {
    is FormOrigin.App -> autofillAppMatches
    is FormOrigin.Web -> autofillWebMatches
  }
}

class AutofillPublisherChangedException(val formOrigin: FormOrigin) :
  Exception(
    "The publisher of '${formOrigin.identifier}' changed since an entry was first matched with this app"
  ) {

  init {
    require(formOrigin is FormOrigin.App) {
      "${this::class.java.simpleName} is only applicable for apps"
    }
  }
}

/** Manages "matches", i.e., associations between apps or websites and Password Store entries. */
class AutofillMatcher {

  companion object {

    private const val MAX_NUM_MATCHES = 10

    private const val PREFERENCE_PREFIX_TOKEN = "token;"

    private fun tokenKey(formOrigin: FormOrigin.App) =
      "$PREFERENCE_PREFIX_TOKEN${formOrigin.identifier}"

    private const val PREFERENCE_PREFIX_MATCHES = "matches;"

    private fun matchesKey(formOrigin: FormOrigin) =
      "$PREFERENCE_PREFIX_MATCHES${formOrigin.identifier}"

    private fun hasFormOriginHashChanged(context: Context, formOrigin: FormOrigin): Boolean {
      return when (formOrigin) {
        is FormOrigin.Web -> false
        is FormOrigin.App -> {
          val packageName = formOrigin.identifier
          val certificatesHash = computeCertificatesHash(context, packageName)
          val storedCertificatesHash =
            context.autofillAppMatches.getString(tokenKey(formOrigin), null) ?: return false
          val hashHasChanged = certificatesHash != storedCertificatesHash
          if (hashHasChanged) {
            logcat(ERROR) { "$packageName: stored=$storedCertificatesHash, new=$certificatesHash" }
            true
          } else {
            false
          }
        }
      }
    }

    private fun storeFormOriginHash(context: Context, formOrigin: FormOrigin) {
      if (formOrigin is FormOrigin.App) {
        val packageName = formOrigin.identifier
        val certificatesHash = computeCertificatesHash(context, packageName)
        context.autofillAppMatches.edit { putString(tokenKey(formOrigin), certificatesHash) }
      }
      // We don't need to store a hash for FormOrigin.Web since it can only originate from
      // browsers we trust to verify the origin.
    }

    /**
     * Get all Password Store entries that have already been associated with [formOrigin] by the
     * user.
     *
     * If [formOrigin] represents an app and that app's certificates have changed since the first
     * time the user associated an entry with it, an [AutofillPublisherChangedException] will be
     * thrown.
     */
    fun getMatchesFor(
      context: Context,
      formOrigin: FormOrigin
    ): Result<List<File>, AutofillPublisherChangedException> {
      if (hasFormOriginHashChanged(context, formOrigin)) {
        return Err(AutofillPublisherChangedException(formOrigin))
      }
      val matchPreferences = context.matchPreferences(formOrigin)
      val matchedFiles =
        matchPreferences.getStringSet(matchesKey(formOrigin), emptySet())!!.map { File(it) }
      return Ok(
        matchedFiles
          .filter { it.exists() }
          .also { validFiles ->
            matchPreferences.edit {
              putStringSet(matchesKey(formOrigin), validFiles.map { it.absolutePath }.toSet())
            }
          }
      )
    }

    fun clearMatchesFor(context: Context, formOrigin: FormOrigin) {
      context.matchPreferences(formOrigin).edit {
        remove(matchesKey(formOrigin))
        if (formOrigin is FormOrigin.App) remove(tokenKey(formOrigin))
      }
    }

    /**
     * Associates the store entry [file] with [formOrigin], such that future Autofill responses to
     * requests from this app or website offer this entry as a dataset.
     *
     * The maximum number of matches is limited by [MAX_NUM_MATCHES] since older versions of Android
     * may crash when too many datasets are offered.
     */
    fun addMatchFor(context: Context, formOrigin: FormOrigin, file: Path) {
      if (!file.exists()) return
      if (hasFormOriginHashChanged(context, formOrigin)) {
        // This should never happen since we already verified the publisher in
        // getMatchesFor.
        logcat(ERROR) { "App publisher changed between getMatchesFor and addMatchFor" }
        throw AutofillPublisherChangedException(formOrigin)
      }
      val matchPreferences = context.matchPreferences(formOrigin)
      val matchedFiles =
        matchPreferences.getStringSet(matchesKey(formOrigin), emptySet()).orEmpty().map(Paths::get)
      val newFiles = setOf(file.absolute()).union(matchedFiles)
      if (newFiles.size > MAX_NUM_MATCHES) {
        Toast.makeText(
            context,
            context.getString(R.string.oreo_autofill_max_matches_reached, MAX_NUM_MATCHES),
            Toast.LENGTH_LONG
          )
          .show()
        return
      }
      matchPreferences.edit {
        putStringSet(matchesKey(formOrigin), newFiles.map(Path::absolutePathString).toSet())
      }
      storeFormOriginHash(context, formOrigin)
      logcat { "Stored match for $formOrigin" }
    }

    /**
     * Goes through all existing matches and updates their associated entries by using [moveFromTo]
     * as a lookup table and deleting the matches for files in [delete].
     */
    fun updateMatches(
      context: Context,
      moveFromTo: Map<File, File> = emptyMap(),
      delete: Collection<File> = emptyList()
    ) {
      val deletePathList = delete.map { it.absolutePath }
      val oldNewPathMap =
        moveFromTo.mapValues { it.value.absolutePath }.mapKeys { it.key.absolutePath }
      for (prefs in listOf(context.autofillAppMatches, context.autofillWebMatches)) {
        for ((key, value) in prefs.all) {
          if (!key.startsWith(PREFERENCE_PREFIX_MATCHES)) continue
          // We know that preferences starting with `PREFERENCE_PREFIX_MATCHES` were
          // created with `putStringSet`.
          @Suppress("UNCHECKED_CAST") val oldMatches = value as? Set<String>
          if (oldMatches == null) {
            logcat(WARN) { "Failed to read matches for $key" }
            continue
          }
          // Delete all matches for file locations that are going to be overwritten, then
          // transfer matches over to the files at their new locations.
          val newMatches =
            oldMatches
              .asSequence()
              .minus(deletePathList)
              .minus(oldNewPathMap.values)
              .map { match ->
                val newPath = oldNewPathMap[match] ?: return@map match
                logcat { "Updating match for $key: $match --> $newPath" }
                newPath
              }
              .toSet()
          if (newMatches != oldMatches) prefs.edit { putStringSet(key, newMatches) }
        }
      }
    }
  }
}
