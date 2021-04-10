/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: LGPL-3.0-only WITH LGPL-3.0-linking-exception
 */
package com.github.androidpasswordstore.autofillparser

import android.app.assist.AssistStructure
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi
import com.github.ajalt.timberkt.d

/**
 * A unique identifier for either an Android app (package name) or a website (origin minus port).
 */
public sealed class FormOrigin(public open val identifier: String) {

  public data class Web(override val identifier: String) : FormOrigin(identifier)
  public data class App(override val identifier: String) : FormOrigin(identifier)

  public companion object {

    private const val BUNDLE_KEY_WEB_IDENTIFIER = "webIdentifier"
    private const val BUNDLE_KEY_APP_IDENTIFIER = "appIdentifier"

    public fun fromBundle(bundle: Bundle): FormOrigin? {
      val webIdentifier = bundle.getString(BUNDLE_KEY_WEB_IDENTIFIER)
      if (webIdentifier != null) {
        return Web(webIdentifier)
      } else {
        return App(bundle.getString(BUNDLE_KEY_APP_IDENTIFIER) ?: return null)
      }
    }
  }

  public fun getPrettyIdentifier(context: Context, untrusted: Boolean = true): String =
    when (this) {
      is Web -> identifier
      is App -> {
        val info = context.packageManager.getApplicationInfo(identifier, PackageManager.GET_META_DATA)
        val label = context.packageManager.getApplicationLabel(info)
        if (untrusted) "“$label”" else "$label"
      }
    }

  public fun toBundle(): Bundle =
    Bundle().apply {
      when (this@FormOrigin) {
        is Web -> putString(BUNDLE_KEY_WEB_IDENTIFIER, identifier)
        is App -> putString(BUNDLE_KEY_APP_IDENTIFIER, identifier)
      }
    }
}

/**
 * Manages the detection of fields to fill in an [AssistStructure] and determines the [FormOrigin].
 */
@RequiresApi(Build.VERSION_CODES.O)
private class AutofillFormParser(
  context: Context,
  structure: AssistStructure,
  isManualRequest: Boolean,
  private val customSuffixes: Sequence<String>
) {

  companion object {
    private val SUPPORTED_SCHEMES = listOf("http", "https")
  }

  private val relevantFields = mutableListOf<FormField>()
  val ignoredIds = mutableListOf<AutofillId>()
  private var fieldIndex = 0

  private var appPackage = structure.activityComponent.packageName

  private val trustedBrowserInfo = getBrowserAutofillSupportInfoIfTrusted(context, appPackage)
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

  private fun detectFieldsToFill(isManualRequest: Boolean) =
    autofillStrategy.match(
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
    return FormOrigin.Web(getPublicSuffixPlusOne(context, host, customSuffixes))
  }

  private fun determineFormOrigin(context: Context): FormOrigin? {
    if (scenario == null) return null
    if (trustedBrowserInfo == null || webOrigins.isEmpty()) {
      // Security assumption: If a trusted browser includes no web origin in the provided
      // AssistStructure, then the form is a native browser form (e.g. for a sync password).
      // TODO: Support WebViews in apps via Digital Asset Links
      // See:
      // https://developer.android.com/reference/android/service/autofill/AutofillService#web-security
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
      BrowserMultiOriginMethod.WebView, BrowserMultiOriginMethod.Field -> {
        // Security assumption: For browsers with full autofill support (the `Field` case),
        // every form field is annotated with its origin. For browsers based on WebView,
        // this is true after the web origins of WebViews are passed down to their children.
        //
        // For browsers with the WebView or Field method of multi origin support, we take
        // the single origin among the detected fillable or saveable fields. If this origin
        // is null, but we encountered web origins elsewhere in the AssistStructure, the
        // situation is uncertain and Autofill should not be offered.
        webOriginToFormOrigin(context, scenario.allFields.map { it.webOrigin }.toSet().singleOrNull() ?: return null)
      }
    }
  }
}

public data class Credentials(val username: String?, val password: String?, val otp: String?)

/**
 * Represents a collection of fields in a specific app that can be filled or saved. This is the
 * entry point to all fill and save features.
 */
@RequiresApi(Build.VERSION_CODES.O)
public class FillableForm
private constructor(
  public val formOrigin: FormOrigin,
  public val scenario: AutofillScenario<AutofillId>,
  public val ignoredIds: List<AutofillId>,
  public val saveFlags: Int?
) {
  public companion object {
    /** Returns a [FillableForm] if a login form could be detected in [structure]. */
    public fun parseAssistStructure(
      context: Context,
      structure: AssistStructure,
      isManualRequest: Boolean,
      customSuffixes: Sequence<String> = emptySequence(),
    ): FillableForm? {
      val form = AutofillFormParser(context, structure, isManualRequest, customSuffixes)
      if (form.formOrigin == null || form.scenario == null) return null
      return FillableForm(form.formOrigin, form.scenario.map { it.autofillId }, form.ignoredIds, form.saveFlags)
    }
  }

  public fun toClientState(): Bundle = scenario.toBundle().apply { putAll(formOrigin.toBundle()) }
}
