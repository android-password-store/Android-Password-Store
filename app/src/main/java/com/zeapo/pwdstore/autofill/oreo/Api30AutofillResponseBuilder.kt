/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.autofill.oreo

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import com.github.ajalt.timberkt.e
import com.github.androidpasswordstore.autofillparser.AutofillAction
import com.github.androidpasswordstore.autofillparser.FillableForm
import com.github.androidpasswordstore.autofillparser.fillWith
import com.github.michaelbull.result.fold
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillDecryptActivity
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillFilterView
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillPublisherChangedActivity
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillSaveActivity
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillSmsActivity
import java.io.File

/**
 * Implements [AutofillResponseBuilder]'s methods for API 30 and above
 */
@RequiresApi(Build.VERSION_CODES.R)
class Api30AutofillResponseBuilder(form: FillableForm) {

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

    private fun makeMatchDataset(context: Context, file: File, imeSpec: InlinePresentationSpec?): Dataset? {
        if (!scenario.hasFieldsToFillOn(AutofillAction.Match)) return null
        val metadata = makeFillMatchMetadata(context, file)
        val intentSender = AutofillDecryptActivity.makeDecryptFileIntentSender(file, context)
        return makeIntentDataset(context, AutofillAction.Match, intentSender, metadata, imeSpec)
    }

    private fun makeSearchDataset(context: Context, imeSpec: InlinePresentationSpec?): Dataset? {
        if (!scenario.hasFieldsToFillOn(AutofillAction.Search)) return null
        val metadata = makeSearchAndFillMetadata(context)
        val intentSender =
            AutofillFilterView.makeMatchAndDecryptFileIntentSender(context, formOrigin)
        return makeIntentDataset(context, AutofillAction.Search, intentSender, metadata, imeSpec)
    }

    private fun makeGenerateDataset(context: Context, imeSpec: InlinePresentationSpec?): Dataset? {
        if (!scenario.hasFieldsToFillOn(AutofillAction.Generate)) return null
        val metadata = makeGenerateAndFillMetadata(context)
        val intentSender = AutofillSaveActivity.makeSaveIntentSender(context, null, formOrigin)
        return makeIntentDataset(context, AutofillAction.Generate, intentSender, metadata, imeSpec)
    }


    private fun makeFillOtpFromSmsDataset(context: Context, imeSpec: InlinePresentationSpec?): Dataset? {
        if (!scenario.hasFieldsToFillOn(AutofillAction.FillOtpFromSms)) return null
        if (!AutofillSmsActivity.shouldOfferFillFromSms(context)) return null
        val metadata = makeFillOtpFromSmsMetadata(context)
        val intentSender = AutofillSmsActivity.makeFillOtpFromSmsIntentSender(context)
        return makeIntentDataset(context, AutofillAction.FillOtpFromSms, intentSender, metadata, imeSpec)
    }

    private fun makePublisherChangedDataset(
        context: Context,
        publisherChangedException: AutofillPublisherChangedException,
        imeSpec: InlinePresentationSpec?
    ): Dataset {
        val metadata = makeWarningMetadata(context)
        // If the user decides to trust the new publisher, they can choose reset the list of
        // matches. In this case we need to immediately show a new `FillResponse` as if the app were
        // autofilled for the first time. This `FillResponse` needs to be returned as a result from
        // `AutofillPublisherChangedActivity`, which is why we create and pass it on here.
        val fillResponseAfterReset = makeFillResponse(context, null, emptyList())
        val intentSender = AutofillPublisherChangedActivity.makePublisherChangedIntentSender(
            context, publisherChangedException, fillResponseAfterReset
        )
        return makeIntentDataset(context, AutofillAction.Match, intentSender, metadata, imeSpec)
    }

    private fun makePublisherChangedResponse(
        context: Context,
        inlineSuggestionsRequest: InlineSuggestionsRequest?,
        publisherChangedException: AutofillPublisherChangedException
    ): FillResponse {
        val imeSpec = inlineSuggestionsRequest?.inlinePresentationSpecs?.firstOrNull()
        return FillResponse.Builder().run {
            addDataset(makePublisherChangedDataset(context, publisherChangedException, imeSpec))
            setIgnoredIds(*ignoredIds.toTypedArray())
            build()
        }
    }

    private fun makeFillResponse(context: Context, inlineSuggestionsRequest: InlineSuggestionsRequest?, matchedFiles: List<File>): FillResponse? {
        var datasetCount = 0
        val imeSpecs = inlineSuggestionsRequest?.inlinePresentationSpecs ?: emptyList()
        return FillResponse.Builder().run {
            for (file in matchedFiles) {
                makeMatchDataset(context, file, imeSpecs.getOrNull(datasetCount))?.let {
                    datasetCount++
                    addDataset(it)
                }
            }
            makeSearchDataset(context, imeSpecs.getOrNull(datasetCount))?.let {
                datasetCount++
                addDataset(it)
            }
            makeGenerateDataset(context, imeSpecs.getOrNull(datasetCount))?.let {
                datasetCount++
                addDataset(it)
            }
            makeFillOtpFromSmsDataset(context, imeSpecs.getOrNull(datasetCount))?.let {
                datasetCount++
                addDataset(it)
            }
            if (datasetCount == 0) return null
            setHeader(makeRemoteView(context, makeHeaderMetadata(formOrigin.getPrettyIdentifier(context, untrusted = true))))
            makeSaveInfo()?.let { setSaveInfo(it) }
            setClientState(clientState)
            setIgnoredIds(*ignoredIds.toTypedArray())
            build()
        }
    }

    // TODO: Support multi-step authentication flows in apps via FLAG_DELAY_SAVE
    // See: https://developer.android.com/reference/android/service/autofill/SaveInfo#FLAG_DELAY_SAVE
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

    /**
     * Creates and returns a suitable [FillResponse] to the Autofill framework.
     */
    fun fillCredentials(context: Context, inlineSuggestionsRequest: InlineSuggestionsRequest?, callback: FillCallback) {
        AutofillMatcher.getMatchesFor(context, formOrigin).fold(
            success = { matchedFiles ->
                callback.onSuccess(makeFillResponse(context, inlineSuggestionsRequest, matchedFiles))
            },
            failure = { e ->
                e(e)
                callback.onSuccess(makePublisherChangedResponse(context, inlineSuggestionsRequest, e))
            }
        )
    }
}
