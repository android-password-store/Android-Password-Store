/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.autofill

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.Presentations
import android.service.autofill.SaveInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import app.passwordstore.autofill.oreo.ui.AutofillSmsActivity
import app.passwordstore.ui.autofill.AutofillDecryptActivity
import app.passwordstore.ui.autofill.AutofillFilterView
import app.passwordstore.ui.autofill.AutofillPublisherChangedActivity
import app.passwordstore.ui.autofill.AutofillSaveActivity
import com.github.androidpasswordstore.autofillparser.AutofillAction
import com.github.androidpasswordstore.autofillparser.FillableForm
import com.github.androidpasswordstore.autofillparser.fillWith
import com.github.michaelbull.result.fold
import java.io.File
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

/** Implements [AutofillResponseBuilder]'s methods for API 30 and above */
@RequiresApi(Build.VERSION_CODES.R)
class Api30AutofillResponseBuilder private constructor(form: FillableForm) :
  AutofillResponseBuilder {

  object Factory : AutofillResponseBuilder.Factory {
    override fun create(form: FillableForm): AutofillResponseBuilder =
      Api30AutofillResponseBuilder(form)
  }

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
    imeSpec: InlinePresentationSpec?,
  ): Dataset {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      makeIntentDataSetTiramisu(context, action, intentSender, metadata, imeSpec)
    } else {
      makeIntentDataSetR(context, action, intentSender, metadata, imeSpec)
    }
  }

  /**
   * Helper for creating an Autofill [Dataset]s for [Build.VERSION_CODES.R] <=
   * [Build.VERSION.SDK_INT] < [Build.VERSION_CODES.TIRAMISU].
   */
  @Suppress("DEPRECATION")
  private fun makeIntentDataSetR(
    context: Context,
    action: AutofillAction,
    intentSender: IntentSender,
    metadata: DatasetMetadata,
    imeSpec: InlinePresentationSpec?,
  ): Dataset {
    return Dataset.Builder(makeRemoteView(context, metadata)).run {
      fillWith(scenario, action, credentials = null)
      setAuthentication(intentSender)
      if (imeSpec != null) {
        val inlinePresentation = makeInlinePresentation(context, imeSpec, metadata)
        if (inlinePresentation != null) {
          setInlinePresentation(inlinePresentation)
        }
      }
      build()
    }
  }

  /**
   * Helper for creating Autofill [Dataset]s for
   * [Build.VERSION.SDK_INT] >= [Build.VERSION_CODES.TIRAMISU] and above.
   */
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun makeIntentDataSetTiramisu(
    context: Context,
    action: AutofillAction,
    intentSender: IntentSender,
    metadata: DatasetMetadata,
    imeSpec: InlinePresentationSpec?,
  ): Dataset {
    val presentationsBuilder = Presentations.Builder()
    if (imeSpec != null) {
      val inlinePresentation = makeInlinePresentation(context, imeSpec, metadata)
      if (inlinePresentation != null) {
        presentationsBuilder.setInlinePresentation(inlinePresentation)
      } else {
        presentationsBuilder.setMenuPresentation(makeRemoteView(context, metadata))
      }
    } else {
      presentationsBuilder.setMenuPresentation(makeRemoteView(context, metadata))
    }
    val presentations = presentationsBuilder.build()
    return Dataset.Builder(presentations).run {
      fillWith(scenario, action, credentials = null)
      setAuthentication(intentSender)
      build()
    }
  }

  private fun makeMatchDataset(
    context: Context,
    file: File,
    imeSpec: InlinePresentationSpec?,
  ): Dataset? {
    if (!scenario.hasFieldsToFillOn(AutofillAction.Match)) return null
    val metadata = makeFillMatchMetadata(context, file)
    val intentSender = AutofillDecryptActivity.makeDecryptFileIntentSender(file, context)
    return makeIntentDataset(context, AutofillAction.Match, intentSender, metadata, imeSpec)
  }

  private fun makeSearchDataset(context: Context, imeSpec: InlinePresentationSpec?): Dataset? {
    if (!scenario.hasFieldsToFillOn(AutofillAction.Search)) return null
    val metadata = makeSearchAndFillMetadata(context)
    val intentSender = AutofillFilterView.makeMatchAndDecryptFileIntentSender(context, formOrigin)
    return makeIntentDataset(context, AutofillAction.Search, intentSender, metadata, imeSpec)
  }

  private fun makeGenerateDataset(context: Context, imeSpec: InlinePresentationSpec?): Dataset? {
    if (!scenario.hasFieldsToFillOn(AutofillAction.Generate)) return null
    val metadata = makeGenerateAndFillMetadata(context)
    val intentSender = AutofillSaveActivity.makeSaveIntentSender(context, null, formOrigin)
    return makeIntentDataset(context, AutofillAction.Generate, intentSender, metadata, imeSpec)
  }

  private fun makeFillOtpFromSmsDataset(
    context: Context,
    imeSpec: InlinePresentationSpec?,
  ): Dataset? {
    if (!scenario.hasFieldsToFillOn(AutofillAction.FillOtpFromSms)) return null
    if (!AutofillSmsActivity.shouldOfferFillFromSms(context)) return null
    val metadata = makeFillOtpFromSmsMetadata(context)
    val intentSender = AutofillSmsActivity.makeFillOtpFromSmsIntentSender(context)
    return makeIntentDataset(
      context,
      AutofillAction.FillOtpFromSms,
      intentSender,
      metadata,
      imeSpec,
    )
  }

  private fun makePublisherChangedDataset(
    context: Context,
    publisherChangedException: AutofillPublisherChangedException,
    imeSpec: InlinePresentationSpec?,
  ): Dataset {
    val metadata = makeWarningMetadata(context)
    // If the user decides to trust the new publisher, they can choose reset the list of
    // matches. In this case we need to immediately show a new `FillResponse` as if the app were
    // autofilled for the first time. This `FillResponse` needs to be returned as a result from
    // `AutofillPublisherChangedActivity`, which is why we create and pass it on here.
    val fillResponseAfterReset = makeFillResponse(context, null, emptyList())
    val intentSender =
      AutofillPublisherChangedActivity.makePublisherChangedIntentSender(
        context,
        publisherChangedException,
        fillResponseAfterReset,
      )
    return makeIntentDataset(context, AutofillAction.Match, intentSender, metadata, imeSpec)
  }

  private fun makePublisherChangedResponse(
    context: Context,
    inlineSuggestionsRequest: InlineSuggestionsRequest?,
    publisherChangedException: AutofillPublisherChangedException,
  ): FillResponse {
    val imeSpec = inlineSuggestionsRequest?.inlinePresentationSpecs?.firstOrNull()
    return FillResponse.Builder().run {
      addDataset(makePublisherChangedDataset(context, publisherChangedException, imeSpec))
      setIgnoredIds(*ignoredIds.toTypedArray())
      build()
    }
  }

  private fun makeFillResponse(
    context: Context,
    inlineSuggestionsRequest: InlineSuggestionsRequest?,
    matchedFiles: List<File>,
  ): FillResponse? {
    var datasetCount = 0
    val imeSpecs = inlineSuggestionsRequest?.inlinePresentationSpecs ?: emptyList()
    return FillResponse.Builder().run {
      for (file in matchedFiles) {
        makeMatchDataset(context, file, imeSpecs.getOrNull(datasetCount))?.let {
          datasetCount++
          addDataset(it)
        }
      }
      makeGenerateDataset(context, imeSpecs.getOrNull(datasetCount))?.let {
        datasetCount++
        addDataset(it)
      }
      makeFillOtpFromSmsDataset(context, imeSpecs.getOrNull(datasetCount))?.let {
        datasetCount++
        addDataset(it)
      }
      makeSearchDataset(context, imeSpecs.getOrNull(datasetCount))?.let {
        datasetCount++
        addDataset(it)
      }
      if (datasetCount == 0) return null
      setHeader(
        makeRemoteView(
          context,
          makeHeaderMetadata(formOrigin.getPrettyIdentifier(context, untrusted = true)),
        )
      )
      makeSaveInfo()?.let { setSaveInfo(it) }
      setClientState(clientState)
      setIgnoredIds(*ignoredIds.toTypedArray())
      build()
    }
  }

  // TODO: Support multi-step authentication flows in apps via FLAG_DELAY_SAVE
  // See:
  // https://developer.android.com/reference/android/service/autofill/SaveInfo#FLAG_DELAY_SAVE
  private fun makeSaveInfo(): SaveInfo? {
    if (!canBeSaved) return null
    check(saveFlags != null) { "saveFlags must not be null" }
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

  /** Creates and returns a suitable [FillResponse] to the Autofill framework. */
  override fun fillCredentials(context: Context, fillRequest: FillRequest, callback: FillCallback) {
    AutofillMatcher.getMatchesFor(context, formOrigin)
      .fold(
        success = { matchedFiles ->
          callback.onSuccess(
            makeFillResponse(context, fillRequest.inlineSuggestionsRequest, matchedFiles)
          )
        },
        failure = { e ->
          logcat(ERROR) { e.asLog() }
          callback.onSuccess(
            makePublisherChangedResponse(context, fillRequest.inlineSuggestionsRequest, e)
          )
        },
      )
  }
}
