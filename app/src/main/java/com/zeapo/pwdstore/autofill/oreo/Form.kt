/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.app.assist.AssistStructure
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.e
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillDecryptActivity
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillFilterView
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillPublisherChangedActivity
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillSaveActivity
import java.io.File

/**
 * A unique identifier for either an Android app (package name) or a website (origin minus port).
 */
sealed class FormOrigin(open val identifier: String) {

    data class Web(override val identifier: String) : FormOrigin(identifier)
    data class App(override val identifier: String) : FormOrigin(identifier)

    companion object {
        private const val BUNDLE_KEY_WEB_IDENTIFIER = "webIdentifier"
        private const val BUNDLE_KEY_APP_IDENTIFIER = "appIdentifier"

        fun fromBundle(bundle: Bundle): FormOrigin? {
            val webIdentifier = bundle.getString(BUNDLE_KEY_WEB_IDENTIFIER)
            if (webIdentifier != null) {
                return Web(webIdentifier)
            } else {
                return App(bundle.getString(BUNDLE_KEY_APP_IDENTIFIER) ?: return null)
            }
        }
    }

    fun getPrettyIdentifier(context: Context, untrusted: Boolean = true) = when (this) {
        is Web -> identifier
        is App -> {
            val info = context.packageManager.getApplicationInfo(
                identifier, PackageManager.GET_META_DATA
            )
            val label = context.packageManager.getApplicationLabel(info)
            if (untrusted) "“$label”" else "$label"
        }
    }

    fun toBundle() = when (this) {
        is Web -> bundleOf(BUNDLE_KEY_WEB_IDENTIFIER to identifier)
        is App -> bundleOf(BUNDLE_KEY_APP_IDENTIFIER to identifier)
    }
}

/**
 * Manages the detection of fields to fill in an [AssistStructure] and determines the [FormOrigin].
 */
@RequiresApi(Build.VERSION_CODES.O)
private class Form(context: Context, structure: AssistStructure, isManualRequest: Boolean) {

    companion object {
        private val SUPPORTED_SCHEMES = listOf("http", "https")
    }

    private val relevantFields = mutableListOf<FormField>()
    val ignoredIds = mutableListOf<AutofillId>()
    private var fieldIndex = 0

    private var appPackage = structure.activityComponent.packageName

    private val trustedBrowserInfo =
        getBrowserAutofillSupportInfoIfTrusted(context, appPackage)
    val saveFlags = trustedBrowserInfo?.saveFlags

    private val webOrigins = mutableSetOf<String>()

    init {
        d { "Request from $appPackage (${computeCertificatesHash(context, appPackage)})" }
        parseStructure(structure)
    }

    val scenario = detectFieldsToFill(isManualRequest)
    val formOrigin = determineFormOrigin(context)

    init {
        d { "Origin: $formOrigin" }
    }

    private fun parseStructure(structure: AssistStructure) {
        for (i in 0 until structure.windowNodeCount) {
            visitFormNode(structure.getWindowNodeAt(i).rootViewNode)
        }
    }

    private fun visitFormNode(node: AssistStructure.ViewNode, inheritedWebOrigin: String? = null) {
        trackOrigin(node)
        val field =
            if (trustedBrowserInfo?.multiOriginMethod == BrowserMultiOriginMethod.WebView) {
                FormField(node, fieldIndex, true, inheritedWebOrigin)
            } else {
                check(inheritedWebOrigin == null)
                FormField(node, fieldIndex, false)
            }
        if (field.relevantField) {
            d { "Relevant: $field" }
            relevantFields.add(field)
            fieldIndex++
        } else {
            d { "Ignored : $field" }
            ignoredIds.add(field.autofillId)
        }
        for (i in 0 until node.childCount) {
            visitFormNode(node.getChildAt(i), field.webOriginToPassDown)
        }
    }

    private fun detectFieldsToFill(isManualRequest: Boolean) = autofillStrategy.match(
        relevantFields,
        singleOriginMode = trustedBrowserInfo?.multiOriginMethod == BrowserMultiOriginMethod.None,
        isManualRequest = isManualRequest
    )

    private fun trackOrigin(node: AssistStructure.ViewNode) {
        if (trustedBrowserInfo == null) return
        node.webOrigin?.let {
            if (it !in webOrigins) {
                d { "Origin encountered: $it" }
                webOrigins.add(it)
            }
        }
    }

    private fun webOriginToFormOrigin(context: Context, origin: String): FormOrigin? {
        val uri = Uri.parse(origin) ?: return null
        val scheme = uri.scheme ?: return null
        if (scheme !in SUPPORTED_SCHEMES) return null
        val host = uri.host ?: return null
        return FormOrigin.Web(getPublicSuffixPlusOne(context, host))
    }

    private fun determineFormOrigin(context: Context): FormOrigin? {
        if (scenario == null) return null
        if (trustedBrowserInfo == null || webOrigins.isEmpty()) {
            // Security assumption: If a trusted browser includes no web origin in the provided
            // AssistStructure, then the form is a native browser form (e.g. for a sync password).
            // TODO: Support WebViews in apps via Digital Asset Links
            // See: https://developer.android.com/reference/android/service/autofill/AutofillService#web-security
            return FormOrigin.App(appPackage)
        }
        return when (trustedBrowserInfo.multiOriginMethod) {
            BrowserMultiOriginMethod.None -> {
                // Security assumption: If a browser is trusted but does not support tracking
                // multiple origins, it is expected to annotate a single field, in most cases its
                // URL bar, with a webOrigin. We err on the side of caution and only trust the
                // reported web origin if no other web origin appears on the page.
                webOriginToFormOrigin(context, webOrigins.singleOrNull() ?: return null)
            }
            BrowserMultiOriginMethod.WebView,
            BrowserMultiOriginMethod.Field -> {
                // Security assumption: For browsers with full autofill support (the `Field` case),
                // every form field is annotated with its origin. For browsers based on WebView,
                // this is true after the web origins of WebViews are passed down to their children.
                //
                // For browsers with the WebView or Field method of multi origin support, we take
                // the single origin among the detected fillable or saveable fields. If this origin
                // is null, but we encountered web origins elsewhere in the AssistStructure, the
                // situation is uncertain and Autofill should not be offered.
                webOriginToFormOrigin(
                    context,
                    scenario.allFields.map { it.webOrigin }.toSet().singleOrNull() ?: return null
                )
            }
        }
    }
}

/**
 * Represents a collection of fields in a specific app that can be filled or saved. This is the
 * entry point to all fill and save features.
 */
@RequiresApi(Build.VERSION_CODES.O)
class FillableForm private constructor(
    private val formOrigin: FormOrigin,
    private val scenario: AutofillScenario<FormField>,
    private val ignoredIds: List<AutofillId>,
    private val saveFlags: Int?
) {

    companion object {
        fun makeFillInDataset(
            context: Context,
            credentials: Credentials,
            clientState: Bundle,
            action: AutofillAction
        ): Dataset {
            val remoteView = makePlaceholderRemoteView(context)
            val scenario = AutofillScenario.fromBundle(clientState)
            // Before Android P, Datasets used for fill-in had to come with a RemoteViews, even
            // though they are never shown.
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Dataset.Builder()
            } else {
                Dataset.Builder(remoteView)
            }
            return builder.run {
                if (scenario != null) fillWith(scenario, action, credentials)
                else e { "Failed to recover scenario from client state" }
                build()
            }
        }

        /**
         * Returns a [FillableForm] if a login form could be detected in [structure].
         */
        fun parseAssistStructure(
            context: Context,
            structure: AssistStructure,
            isManualRequest: Boolean
        ): FillableForm? {
            val form = Form(context, structure, isManualRequest)
            if (form.formOrigin == null || form.scenario == null) return null
            return FillableForm(
                form.formOrigin,
                form.scenario,
                form.ignoredIds,
                form.saveFlags
            )
        }
    }

    private val clientState = scenario.toBundle().apply {
        putAll(formOrigin.toBundle())
    }

    // We do not offer save when the only relevant field is a username field or there is no field.
    private val scenarioSupportsSave =
        scenario.fieldsToSave.minus(listOfNotNull(scenario.username)).isNotEmpty()
    private val canBeSaved = saveFlags != null && scenarioSupportsSave

    private fun makePlaceholderDataset(
        remoteView: RemoteViews,
        intentSender: IntentSender,
        action: AutofillAction
    ): Dataset {
        return Dataset.Builder(remoteView).run {
            fillWith(scenario, action, credentials = null)
            setAuthentication(intentSender)
            build()
        }
    }

    private fun makeMatchDataset(context: Context, file: File): Dataset? {
        if (scenario.fieldsToFillOn(AutofillAction.Match).isEmpty()) return null
        val remoteView = makeFillMatchRemoteView(context, file, formOrigin)
        val intentSender = AutofillDecryptActivity.makeDecryptFileIntentSender(file, context)
        return makePlaceholderDataset(remoteView, intentSender, AutofillAction.Match)
    }

    private fun makeSearchDataset(context: Context): Dataset? {
        if (scenario.fieldsToFillOn(AutofillAction.Search).isEmpty()) return null
        val remoteView = makeSearchAndFillRemoteView(context, formOrigin)
        val intentSender =
            AutofillFilterView.makeMatchAndDecryptFileIntentSender(context, formOrigin)
        return makePlaceholderDataset(remoteView, intentSender, AutofillAction.Search)
    }

    private fun makeGenerateDataset(context: Context): Dataset? {
        if (scenario.fieldsToFillOn(AutofillAction.Generate).isEmpty()) return null
        val remoteView = makeGenerateAndFillRemoteView(context, formOrigin)
        val intentSender = AutofillSaveActivity.makeSaveIntentSender(context, null, formOrigin)
        return makePlaceholderDataset(remoteView, intentSender, AutofillAction.Generate)
    }

    private fun makePublisherChangedDataset(
        context: Context,
        publisherChangedException: AutofillPublisherChangedException
    ): Dataset {
        val remoteView = makeWarningRemoteView(context)
        val intentSender = AutofillPublisherChangedActivity.makePublisherChangedIntentSender(
            context, publisherChangedException
        )
        return makePlaceholderDataset(remoteView, intentSender, AutofillAction.Match)
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

    private fun makeFillResponse(context: Context, matchedFiles: List<File>): FillResponse? {
        var hasDataset = false
        return FillResponse.Builder().run {
            for (file in matchedFiles) {
                makeMatchDataset(context, file)?.let {
                    hasDataset = true
                    addDataset(it)
                }
            }
            makeSearchDataset(context)?.let {
                hasDataset = true
                addDataset(it)
            }
            makeGenerateDataset(context)?.let {
                hasDataset = true
                addDataset(it)
            }
            if (!hasDataset) return null
            makeSaveInfo()?.let { setSaveInfo(it) }
            setClientState(clientState)
            setIgnoredIds(*ignoredIds.toTypedArray())
            build()
        }
    }

    /**
     * Creates and returns a suitable [FillResponse] to the Autofill framework.
     */
    fun fillCredentials(context: Context, callback: FillCallback) {
        val matchedFiles = try {
            AutofillMatcher.getMatchesFor(context, formOrigin)
        } catch (publisherChangedException: AutofillPublisherChangedException) {
            e(publisherChangedException)
            callback.onSuccess(makePublisherChangedResponse(context, publisherChangedException))
            return
        }
        callback.onSuccess(makeFillResponse(context, matchedFiles))
    }
}
