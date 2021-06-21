/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.util.autofill

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import androidx.annotation.RequiresApi
import com.github.ajalt.timberkt.e
import com.github.androidpasswordstore.autofillparser.AutofillAction
import com.github.androidpasswordstore.autofillparser.AutofillScenario
import com.github.androidpasswordstore.autofillparser.Credentials
import com.github.androidpasswordstore.autofillparser.FillableForm
import com.github.androidpasswordstore.autofillparser.fillWith
import com.github.michaelbull.result.fold
import dev.msfjarvis.aps.autofill.oreo.ui.AutofillSmsActivity
import dev.msfjarvis.aps.ui.autofill.AutofillFilterView
import dev.msfjarvis.aps.ui.autofill.AutofillPublisherChangedActivity
import dev.msfjarvis.aps.ui.autofill.AutofillSaveActivity
import dev.msfjarvis.aps.ui.autofill.GopenpgpAutofillDecryptActivity
import java.io.File

@RequiresApi(Build.VERSION_CODES.O)
class AutofillResponseBuilder(form: FillableForm) {

  private val formOrigin = form.formOrigin
  private val scenario = form.scenario
  private val ignoredIds = form.ignoredIds
  private val saveFlags = form.saveFlags
  private val clientState = form.toClientState()

  // We do not offer save when the only relevant field is a username field or there is no field.
  private val scenarioSupportsSave = scenario.hasPasswordFieldsToSave
  private val canBeSaved = saveFlags != null && scenarioSupportsSave

  private fun makeIntentDataset(
    context: Context,
    action: AutofillAction,
    intentSender: IntentSender,
    metadata: DatasetMetadata,
  ): Dataset {
    return Dataset.Builder(makeRemoteView(context, metadata)).run {
      fillWith(scenario, action, credentials = null)
      setAuthentication(intentSender)
      build()
    }
  }

  private fun makeMatchDataset(context: Context, file: File): Dataset? {
    if (!scenario.hasFieldsToFillOn(AutofillAction.Match)) return null
    val metadata = makeFillMatchMetadata(context, file)
    val intentSender = GopenpgpAutofillDecryptActivity.makeDecryptFileIntentSender(file, context)
    return makeIntentDataset(context, AutofillAction.Match, intentSender, metadata)
  }

  private fun makeSearchDataset(context: Context): Dataset? {
    if (!scenario.hasFieldsToFillOn(AutofillAction.Search)) return null
    val metadata = makeSearchAndFillMetadata(context)
    val intentSender = AutofillFilterView.makeMatchAndDecryptFileIntentSender(context, formOrigin)
    return makeIntentDataset(context, AutofillAction.Search, intentSender, metadata)
  }

  private fun makeGenerateDataset(context: Context): Dataset? {
    if (!scenario.hasFieldsToFillOn(AutofillAction.Generate)) return null
    val metadata = makeGenerateAndFillMetadata(context)
    val intentSender = AutofillSaveActivity.makeSaveIntentSender(context, null, formOrigin)
    return makeIntentDataset(context, AutofillAction.Generate, intentSender, metadata)
  }

  private fun makeFillOtpFromSmsDataset(context: Context): Dataset? {
    if (!scenario.hasFieldsToFillOn(AutofillAction.FillOtpFromSms)) return null
    if (!AutofillSmsActivity.shouldOfferFillFromSms(context)) return null
    val metadata = makeFillOtpFromSmsMetadata(context)
    val intentSender = AutofillSmsActivity.makeFillOtpFromSmsIntentSender(context)
    return makeIntentDataset(context, AutofillAction.FillOtpFromSms, intentSender, metadata)
  }

  private fun makePublisherChangedDataset(
    context: Context,
    publisherChangedException: AutofillPublisherChangedException,
  ): Dataset {
    val metadata = makeWarningMetadata(context)
    // If the user decides to trust the new publisher, they can choose reset the list of
    // matches. In this case we need to immediately show a new `FillResponse` as if the app were
    // autofilled for the first time. This `FillResponse` needs to be returned as a result from
    // `AutofillPublisherChangedActivity`, which is why we create and pass it on here.
    val fillResponseAfterReset = makeFillResponse(context, emptyList())
    val intentSender =
      AutofillPublisherChangedActivity.makePublisherChangedIntentSender(
        context,
        publisherChangedException,
        fillResponseAfterReset
      )
    return makeIntentDataset(context, AutofillAction.Match, intentSender, metadata)
  }

  private fun makePublisherChangedResponse(
    context: Context,
    publisherChangedException: AutofillPublisherChangedException
  ): FillResponse {
    return FillResponse.Builder().run {
      addDataset(makePublisherChangedDataset(context, publisherChangedException))
      setIgnoredIds(*ignoredIds.toTypedArray())
      build()
    }
  }

  // TODO: Support multi-step authentication flows in apps via FLAG_DELAY_SAVE
  // See:
  // https://developer.android.com/reference/android/service/autofill/SaveInfo#FLAG_DELAY_SAVE
  private fun makeSaveInfo(): SaveInfo? {
    if (!canBeSaved) return null
    check(saveFlags != null)
    val idsToSave = scenario.fieldsToSave.toTypedArray()
    if (idsToSave.isEmpty()) return null
    var saveDataTypes = SaveInfo.SAVE_DATA_TYPE_PASSWORD
    if (scenario.hasUsername) {
      saveDataTypes = saveDataTypes or SaveInfo.SAVE_DATA_TYPE_USERNAME
    }
    return SaveInfo.Builder(saveDataTypes, idsToSave).run {
      setFlags(saveFlags)
      build()
    }
  }

  private fun makeFillResponse(context: Context, matchedFiles: List<File>): FillResponse? {
    var datasetCount = 0
    return FillResponse.Builder().run {
      for (file in matchedFiles) {
        makeMatchDataset(context, file)?.let {
          datasetCount++
          addDataset(it)
        }
      }
      makeGenerateDataset(context)?.let {
        datasetCount++
        addDataset(it)
      }
      makeFillOtpFromSmsDataset(context)?.let {
        datasetCount++
        addDataset(it)
      }
      makeSearchDataset(context)?.let {
        datasetCount++
        addDataset(it)
      }
      if (datasetCount == 0) return null
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        setHeader(
          makeRemoteView(
            context,
            makeHeaderMetadata(formOrigin.getPrettyIdentifier(context, untrusted = true))
          )
        )
      }
      makeSaveInfo()?.let { setSaveInfo(it) }
      setClientState(clientState)
      setIgnoredIds(*ignoredIds.toTypedArray())
      build()
    }
  }

  /** Creates and returns a suitable [FillResponse] to the Autofill framework. */
  fun fillCredentials(context: Context, callback: FillCallback) {
    AutofillMatcher.getMatchesFor(context, formOrigin)
      .fold(
        success = { matchedFiles -> callback.onSuccess(makeFillResponse(context, matchedFiles)) },
        failure = { e ->
          e(e)
          callback.onSuccess(makePublisherChangedResponse(context, e))
        }
      )
  }

  companion object {

    fun makeFillInDataset(
      context: Context,
      credentials: Credentials,
      clientState: Bundle,
      action: AutofillAction
    ): Dataset {
      val scenario = AutofillScenario.fromClientState(clientState)
      // Before Android P, Datasets used for fill-in had to come with a RemoteViews, even
      // though they are rarely shown.
      // FIXME: We should clone the original dataset here and add the credentials to be filled
      // in. Otherwise, the entry in the cached list of datasets will be overwritten by the
      // fill-in dataset without any visual representation. This causes it to be missing from
      // the Autofill suggestions shown after the user clears the filled out form fields.
      val builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          Dataset.Builder()
        } else {
          Dataset.Builder(makeRemoteView(context, makeEmptyMetadata()))
        }
      return builder.run {
        if (scenario != null) fillWith(scenario, action, credentials)
        else e { "Failed to recover scenario from client state" }
        build()
      }
    }
  }
}
