/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import com.github.ajalt.timberkt.e
import com.github.androidpasswordstore.autofillparser.AutofillAction
import com.github.androidpasswordstore.autofillparser.AutofillScenario
import com.github.androidpasswordstore.autofillparser.Credentials
import com.github.androidpasswordstore.autofillparser.FillableForm
import com.github.androidpasswordstore.autofillparser.fillWith
import com.github.michaelbull.result.fold
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillDecryptActivity
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillFilterView
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillPublisherChangedActivity
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillSaveActivity
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillSmsActivity
import java.io.File

@RequiresApi(Build.VERSION_CODES.O)
class AutofillResponseBuilder(form: FillableForm) {
    private val formOrigin = form.formOrigin
    private val scenario = form.scenario
    private val ignoredIds = form.ignoredIds
    private val saveFlags = form.saveFlags
    private val clientState = form.toClientState()

    // We do not offer save when the only relevant field is a username field or there is no field.
    private val scenarioSupportsSave =
        scenario.fieldsToSave.minus(listOfNotNull(scenario.username)).isNotEmpty()
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
            if (imeSpec != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val inlinePresentation = makeInlinePresentation(context, imeSpec, metadata)
                if (inlinePresentation != null) {
                    setInlinePresentation(inlinePresentation)
                }
            }
            build()
        }
    }

    private fun makeMatchDataset(context: Context, file: File, imeSpec: InlinePresentationSpec?): Dataset? {
        if (scenario.fieldsToFillOn(AutofillAction.Match).isEmpty()) return null
        val metadata = makeFillMatchMetadata(context, file)
        val intentSender = AutofillDecryptActivity.makeDecryptFileIntentSender(file, context)
        return makeIntentDataset(context, AutofillAction.Match, intentSender, metadata, imeSpec)
    }

    private fun makeSearchDataset(context: Context, imeSpec: InlinePresentationSpec?): Dataset? {
        if (scenario.fieldsToFillOn(AutofillAction.Search).isEmpty()) return null
        val metadata = makeSearchAndFillMetadata(context)
        val intentSender =
            AutofillFilterView.makeMatchAndDecryptFileIntentSender(context, formOrigin)
        return makeIntentDataset(context, AutofillAction.Search, intentSender, metadata, imeSpec)
    }

    private fun makeGenerateDataset(context: Context, imeSpec: InlinePresentationSpec?): Dataset? {
        if (scenario.fieldsToFillOn(AutofillAction.Generate).isEmpty()) return null
        val metadata = makeGenerateAndFillMetadata(context)
        val intentSender = AutofillSaveActivity.makeSaveIntentSender(context, null, formOrigin)
        return makeIntentDataset(context, AutofillAction.Generate, intentSender, metadata, imeSpec)
    }

    private fun makeFillOtpFromSmsDataset(context: Context, imeSpec: InlinePresentationSpec?): Dataset? {
        if (scenario.fieldsToFillOn(AutofillAction.FillOtpFromSms).isEmpty()) return null
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
        val imeSpec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineSuggestionsRequest?.inlinePresentationSpecs?.firstOrNull()
        } else {
            null
        }
        return FillResponse.Builder().run {
            addDataset(makePublisherChangedDataset(context, publisherChangedException, imeSpec))
            setIgnoredIds(*ignoredIds.toTypedArray())
            build()
        }
    }

    // TODO: Support multi-step authentication flows in apps via FLAG_DELAY_SAVE
    // See: https://developer.android.com/reference/android/service/autofill/SaveInfo#FLAG_DELAY_SAVE
    private fun makeSaveInfo(): SaveInfo? {
        if (!canBeSaved) return null
        check(saveFlags != null)
        val idsToSave = scenario.fieldsToSave.map { it.autofillId }.toTypedArray()
        if (idsToSave.isEmpty()) return null
        var saveDataTypes = SaveInfo.SAVE_DATA_TYPE_PASSWORD
        if (scenario.username != null) {
            saveDataTypes = saveDataTypes or SaveInfo.SAVE_DATA_TYPE_USERNAME
        }
        return SaveInfo.Builder(saveDataTypes, idsToSave).run {
            setFlags(saveFlags)
            build()
        }
    }

    private fun makeFillResponse(context: Context, inlineSuggestionsRequest: InlineSuggestionsRequest?, matchedFiles: List<File>): FillResponse? {
        var datasetCount = 0
        val imeSpecs: List<InlinePresentationSpec> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineSuggestionsRequest?.inlinePresentationSpecs
        } else {
            null
        } ?: emptyList()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setHeader(makeRemoteView(context, makeHeaderMetadata(formOrigin.getPrettyIdentifier(context, untrusted = true))))
            }
            makeSaveInfo()?.let { setSaveInfo(it) }
            setClientState(clientState)
            setIgnoredIds(*ignoredIds.toTypedArray())
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

    companion object {
        fun makeFillInDataset(
            context: Context,
            credentials: Credentials,
            clientState: Bundle,
            action: AutofillAction
        ): Dataset {
            val scenario = AutofillScenario.fromBundle(clientState)
            // Before Android P, Datasets used for fill-in had to come with a RemoteViews, even
            // though they are rarely shown.
            // FIXME: We should clone the original dataset here and add the credentials to be filled
            // in. Otherwise, the entry in the cached list of datasets will be overwritten by the
            // fill-in dataset without any visual representation. This causes it to be missing from
            // the Autofill suggestions shown after the user clears the filled out form fields.
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
