/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi

/*
    In order to add a new browser, do the following:

    1. Obtain the .apk from a trusted source. For example, download it from the Play Store on your
    phone and use adb pull to get it onto your computer. We will assume that it is called
    browser.apk.

    2. Run

    aapt dump badging browser.apk | grep package: | grep -Eo " name='[a-zA-Z0-9_\.]*" | cut -c8-

    to obtain the package name (actually, the application ID) of the app in the .apk.

    3. Run

    apksigner verify --print-certs browser.apk | grep "#1 certificate SHA-256" | grep -Eo "[a-f0-9]{64}" | tr -d '\n' | xxd -r -p | base64

    to calculate the hash of browser.apk's first signing certificate.
    Note: This will only work if the apk has a single signing certificate. Apps with multiple
    signers are very rare, so there is probably no need to add them.
    Refer to computeCertificatesHash to learn how the hash would be computed in this case.

    4. Verify the package name and the hash, for example by asking other people to repeat the steps
    above.

    5. Add an entry with the browser apps's package name and the hash to
    TRUSTED_BROWSER_CERTIFICATE_HASH.

    6. Optionally, try adding the browser's package name to BROWSERS_WITH_SAVE_SUPPORT and check
    whether a save request to Password Store is triggered when you submit a registration form.

    7. Optionally, try adding the browser's package name to BROWSERS_WITH_MULTI_ORIGIN_SUPPORT and
    check whether it correctly distinguishes web origins even if iframes are present on the page.
    You can use https://fabianhenneke.github.io/Android-Password-Store/ as a test form.
 */

/*
 * **Security assumption**: Browsers on this list correctly report the web origin of the top-level
 * window as part of their AssistStructure.
 *
 * Note: Browsers can be on this list even if they don't report the correct web origins of all
 * fields on the page, e.g. of those in iframes.
 */
private val TRUSTED_BROWSER_CERTIFICATE_HASH = mapOf(
    "com.android.chrome" to "8P1sW0EPJcslw7UzRsiXL64w+O50Ed+RBICtay1g24M=",
    "com.brave.browser" to "nC23BRNRX9v7vFhbPt89cSPU3GfJT/0wY2HB15u/GKw=",
    "com.chrome.beta" to "2mM9NLaeY64hA7SdU84FL8X388U6q5T9wqIIvf0UJJw=",
    "com.chrome.canary" to "IBnfofsj779wxbzRRDxb6rBPPy/0Nm6aweNFdjmiTPw=",
    "com.chrome.dev" to "kETuX+5LvF4h3URmVDHE6x8fcaMnFqC8knvLs5Izyr8=",
    "com.duckduckgo.mobile.android" to "u3uzHFc8RqHaf8XFKKas9DIQhFb+7FCBDH8zaU6z0tQ=",
    "com.microsoft.emmx" to "AeGZlxCoLCdJtNUMRF3IXWcLYTYInQp2anOCfIKh6sk=",
    "com.opera.mini.native" to "V6y8Ul8bLr0ZGWzW8BQ5fMkQ/RiEHgroUP68Ph5ZP/I=",
    "com.opera.mini.native.beta" to "V6y8Ul8bLr0ZGWzW8BQ5fMkQ/RiEHgroUP68Ph5ZP/I=",
    "com.opera.touch" to "qtjiBNJNF3k0yc0MY8xqo4779CxKaVcJfiIQ9X+qZ6o=",
    "org.gnu.icecat" to "wi2iuVvK/WYZUzd2g0Qzn9ef3kAisQURZ8U1WSMTkcM=",
    "org.mozilla.fenix" to "UAR3kIjn+YjVvFzF+HmP6/T4zQhKGypG79TI7krq8hE=",
    "org.mozilla.fenix.nightly" to "d+rEzu02r++6dheZMd1MwZWrDNVLrzVdIV57vdKOQCo=",
    "org.mozilla.fennec_aurora" to "vASIg40G9Mpr8yOG2qsN2OvPPncweHRZ9i+zzRShuqo=",
    "org.mozilla.fennec_fdroid" to "BmZTWO/YugW+I2pHoSywlY19dd2TnXfCsx9TmFN+vcU=",
    "org.mozilla.firefox" to "p4tipRZbRJSy/q2edqKA0i2Tf+5iUa7OWZRGsuoxmwQ=",
    "org.mozilla.firefox_beta" to "p4tipRZbRJSy/q2edqKA0i2Tf+5iUa7OWZRGsuoxmwQ=",
    "org.mozilla.focus" to "YgOkc7421k7jf4f6UA7bx56rkwYQq5ufpMp9XB8bT/w=",
    "org.mozilla.klar" to "YgOkc7421k7jf4f6UA7bx56rkwYQq5ufpMp9XB8bT/w=",
    "org.torproject.torbrowser" to "IAYfBF5zfGc3XBd5TP7bQ2oDzsa6y3y5+WZCIFyizsg="
)

private fun isTrustedBrowser(context: Context, appPackage: String): Boolean {
    val expectedCertificateHash = TRUSTED_BROWSER_CERTIFICATE_HASH[appPackage] ?: return false
    val certificateHash = computeCertificatesHash(context, appPackage)
    return certificateHash == expectedCertificateHash
}

enum class BrowserMultiOriginMethod {
    None, WebView, Field
}

/**
 * **Security assumption**: Browsers on this list correctly distinguish the web origins of form
 * fields, e.g. on a page which contains both a first-party login form and an iframe with a
 * (potentially malicious) third-party login form.
 *
 * There are two methods used by browsers:
 * - Browsers based on Android's WebView report web domains on each WebView view node, which then
 *   needs to be propagated to the child nodes ([BrowserMultiOriginMethod.WebView]).
 * - Browsers with custom Autofill implementations report web domains on each input field (
 *   [BrowserMultiOriginMethod.Field]).
 */
private val BROWSER_MULTI_ORIGIN_METHOD = mapOf(
    "com.duckduckgo.mobile.android" to BrowserMultiOriginMethod.WebView,
    "com.opera.mini.native" to BrowserMultiOriginMethod.WebView,
    "com.opera.mini.native.beta" to BrowserMultiOriginMethod.WebView,
    "com.opera.touch" to BrowserMultiOriginMethod.WebView,
    "org.gnu.icecat" to BrowserMultiOriginMethod.WebView,
    "org.mozilla.fenix" to BrowserMultiOriginMethod.Field,
    "org.mozilla.fenix.nightly" to BrowserMultiOriginMethod.Field,
    "org.mozilla.fennec_aurora" to BrowserMultiOriginMethod.Field,
    "org.mozilla.fennec_fdroid" to BrowserMultiOriginMethod.Field,
    "org.mozilla.firefox" to BrowserMultiOriginMethod.WebView,
    "org.mozilla.firefox_beta" to BrowserMultiOriginMethod.WebView,
    "org.mozilla.focus" to BrowserMultiOriginMethod.Field,
    "org.mozilla.klar" to BrowserMultiOriginMethod.Field,
    "org.torproject.torbrowser" to BrowserMultiOriginMethod.WebView
)

private fun getBrowserMultiOriginMethod(appPackage: String): BrowserMultiOriginMethod =
    BROWSER_MULTI_ORIGIN_METHOD[appPackage] ?: BrowserMultiOriginMethod.None

/**
 * Browsers on this list issue Autofill save requests and provide unmasked passwords as
 * `autofillValue`.
 *
 * Some browsers may not issue save requests automatically and thus need
 * `FLAG_SAVE_ON_ALL_VIEW_INVISIBLE` to be set.
 */
@RequiresApi(Build.VERSION_CODES.O)
private val BROWSER_SAVE_FLAG = mapOf(
    "com.duckduckgo.mobile.android" to 0,
    "org.mozilla.klar" to 0,
    "org.mozilla.focus" to 0,
    "org.mozilla.fenix" to 0,
    "org.mozilla.fenix.nightly" to 0,
    "org.mozilla.fennec_aurora" to 0,
    "com.opera.mini.native" to 0,
    "com.opera.mini.native.beta" to 0,
    "com.opera.touch" to 0
)

@RequiresApi(Build.VERSION_CODES.O)
private fun getBrowserSaveFlag(appPackage: String): Int? = BROWSER_SAVE_FLAG[appPackage]

data class BrowserAutofillSupportInfo(
    val multiOriginMethod: BrowserMultiOriginMethod,
    val saveFlags: Int?
)

@RequiresApi(Build.VERSION_CODES.O)
fun getBrowserAutofillSupportInfoIfTrusted(
    context: Context,
    appPackage: String
): BrowserAutofillSupportInfo? {
    if (!isTrustedBrowser(context, appPackage)) return null
    return BrowserAutofillSupportInfo(
        multiOriginMethod = getBrowserMultiOriginMethod(appPackage),
        saveFlags = getBrowserSaveFlag(appPackage)
    )
}

private val FLAKY_BROWSERS = listOf(
    "com.android.chrome",
    "com.chrome.beta",
    "com.chrome.canary",
    "com.chrome.dev"
)

enum class BrowserAutofillSupportLevel {
    None,
    FlakyFill,
    PasswordFill,
    GeneralFill,
    GeneralFillAndSave
}

@RequiresApi(Build.VERSION_CODES.O)
private fun getBrowserAutofillSupportLevel(
    context: Context,
    appPackage: String
): BrowserAutofillSupportLevel {
    val browserInfo = getBrowserAutofillSupportInfoIfTrusted(context, appPackage)
    return when {
        browserInfo == null -> BrowserAutofillSupportLevel.None
        appPackage in FLAKY_BROWSERS -> BrowserAutofillSupportLevel.FlakyFill
        browserInfo.multiOriginMethod == BrowserMultiOriginMethod.None -> BrowserAutofillSupportLevel.PasswordFill
        browserInfo.saveFlags == null -> BrowserAutofillSupportLevel.GeneralFill
        else -> BrowserAutofillSupportLevel.GeneralFillAndSave
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun getInstalledBrowsersWithAutofillSupportLevel(context: Context): List<Pair<String, BrowserAutofillSupportLevel>> {
    val testWebIntent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("http://example.org")
    }
    val installedBrowsers = context.packageManager.queryIntentActivities(
        testWebIntent,
        PackageManager.MATCH_ALL
    )
    return installedBrowsers.map {
        it to getBrowserAutofillSupportLevel(context, it.activityInfo.packageName)
    }.filter { it.first.isDefault || it.second != BrowserAutofillSupportLevel.None }.map {
        context.packageManager.getApplicationLabel(it.first.activityInfo.applicationInfo)
            .toString() to it.second
    }
}
