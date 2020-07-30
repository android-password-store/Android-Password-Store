/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi
import androidx.autofill.HintConstants
import java.util.Locale

enum class CertaintyLevel {
    Impossible, Possible, Likely, Certain
}

/**
 * Represents a single potentially fillable or saveable field together with all meta data
 * extracted from its [AssistStructure.ViewNode].
 */
@RequiresApi(Build.VERSION_CODES.O)
class FormField(
    node: AssistStructure.ViewNode,
    private val index: Int,
    passDownWebViewOrigins: Boolean,
    passedDownWebOrigin: String? = null
) {

    companion object {

        private val HINTS_USERNAME = listOf(
            HintConstants.AUTOFILL_HINT_USERNAME,
            HintConstants.AUTOFILL_HINT_NEW_USERNAME,
        )

        private val HINTS_NEW_PASSWORD = listOf(
            HintConstants.AUTOFILL_HINT_NEW_PASSWORD,
        )

        private val HINTS_PASSWORD = HINTS_NEW_PASSWORD + listOf(
            HintConstants.AUTOFILL_HINT_PASSWORD,
        )

        private val HINTS_OTP = listOf(
            HintConstants.AUTOFILL_HINT_SMS_OTP,
        )

        @Suppress("DEPRECATION")
        private val HINTS_FILLABLE = HINTS_USERNAME + HINTS_PASSWORD + HINTS_OTP + listOf(
            HintConstants.AUTOFILL_HINT_EMAIL_ADDRESS,
            HintConstants.AUTOFILL_HINT_NAME,
            HintConstants.AUTOFILL_HINT_PERSON_NAME,
            HintConstants.AUTOFILL_HINT_PHONE,
            HintConstants.AUTOFILL_HINT_PHONE_NUMBER,
        )

        private val ANDROID_TEXT_FIELD_CLASS_NAMES = listOf(
            "android.widget.EditText",
            "android.widget.AutoCompleteTextView",
            "androidx.appcompat.widget.AppCompatEditText",
            "android.support.v7.widget.AppCompatEditText",
            "com.google.android.material.textfield.TextInputEditText",
        )

        private const val ANDROID_WEB_VIEW_CLASS_NAME = "android.webkit.WebView"

        private fun isPasswordInputType(inputType: Int): Boolean {
            val typeClass = inputType and InputType.TYPE_MASK_CLASS
            val typeVariation = inputType and InputType.TYPE_MASK_VARIATION
            return when (typeClass) {
                InputType.TYPE_CLASS_NUMBER -> typeVariation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                InputType.TYPE_CLASS_TEXT -> typeVariation in listOf(
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                )
                else -> false
            }
        }

        private val HTML_INPUT_FIELD_TYPES_USERNAME = listOf(
            "email",
            "tel",
            "text",
        )
        private val HTML_INPUT_FIELD_TYPES_PASSWORD = listOf(
            "password",
        )
        private val HTML_INPUT_FIELD_TYPES_OTP = listOf(
            "tel",
            "text",
        )
        private val HTML_INPUT_FIELD_TYPES_FILLABLE =
            (HTML_INPUT_FIELD_TYPES_USERNAME + HTML_INPUT_FIELD_TYPES_PASSWORD + HTML_INPUT_FIELD_TYPES_OTP).toSet().toList()

        @RequiresApi(Build.VERSION_CODES.O)
        private fun isSupportedHint(hint: String) = hint in HINTS_FILLABLE

        private val EXCLUDED_TERMS = listOf(
            "url_bar", // Chrome/Edge/Firefox address bar
            "url_field", // Opera address bar
            "location_bar_edit_text", // Samsung address bar
            "search", "find", "captcha",
            "postal", // Prevent postal code fields from being mistaken for OTP fields

        )
        private val PASSWORD_HEURISTIC_TERMS = listOf(
            "pass",
            "pswd",
            "pwd",
        )
        private val USERNAME_HEURISTIC_TERMS = listOf(
            "alias",
            "e-mail",
            "email",
            "login",
            "user",
        )
        private val OTP_HEURISTIC_TERMS = listOf(
            "einmal",
            "otp",
            "challenge",
            "verification",
        )
        private val OTP_WEAK_HEURISTIC_TERMS = listOf(
            "code",
        )
    }

    private val List<String>.anyMatchesFieldInfo
        get() = any {
            fieldId.contains(it) || hint.contains(it) || htmlName.contains(it)
        }

    val autofillId: AutofillId = node.autofillId!!

    // Information for heuristics and exclusion rules based only on the current field
    private val htmlId = node.htmlInfo?.attributes?.firstOrNull { it.first == "id" }?.second
    private val resourceId = node.idEntry
    private val fieldId = (htmlId ?: resourceId ?: "").toLowerCase(Locale.US)
    private val hint = node.hint?.toLowerCase(Locale.US) ?: ""
    private val className: String? = node.className
    private val inputType = node.inputType

    // Information for advanced heuristics taking multiple fields and page context into account
    val isFocused = node.isFocused

    // The webOrigin of a WebView should be passed down to its children in certain browsers
    private val isWebView = node.className == ANDROID_WEB_VIEW_CLASS_NAME
    val webOrigin = node.webOrigin ?: if (passDownWebViewOrigins) passedDownWebOrigin else null
    val webOriginToPassDown = if (passDownWebViewOrigins) {
        if (isWebView) webOrigin else passedDownWebOrigin
    } else {
        null
    }

    // Basic type detection for HTML fields
    private val htmlTag = node.htmlInfo?.tag
    private val htmlAttributes: Map<String, String> =
        node.htmlInfo?.attributes?.filter { it.first != null && it.second != null }
            ?.associate { Pair(it.first.toLowerCase(Locale.US), it.second.toLowerCase(Locale.US)) }
            ?: emptyMap()

    private val htmlAttributesDebug =
        htmlAttributes.entries.joinToString { "${it.key}=${it.value}" }
    private val htmlInputType = htmlAttributes["type"]
    private val htmlName = htmlAttributes["name"] ?: ""
    private val htmlMaxLength = htmlAttributes["maxlength"]?.toIntOrNull()
    private val isHtmlField = htmlTag == "input"
    private val isHtmlPasswordField =
        isHtmlField && htmlInputType in HTML_INPUT_FIELD_TYPES_PASSWORD
    private val isHtmlTextField = isHtmlField && htmlInputType in HTML_INPUT_FIELD_TYPES_FILLABLE

    // Basic type detection for native fields
    private val hasPasswordInputType = isPasswordInputType(inputType)

    // HTML fields with non-fillable types (such as submit buttons) should be excluded here
    private val isAndroidTextField = !isHtmlField && className in ANDROID_TEXT_FIELD_CLASS_NAMES
    private val isAndroidPasswordField = isAndroidTextField && hasPasswordInputType

    private val isTextField = isAndroidTextField || isHtmlTextField

    // Autofill hint detection for native fields
    private val autofillHints = node.autofillHints?.filter { isSupportedHint(it) } ?: emptyList()
    private val excludedByAutofillHints =
        if (autofillHints.isEmpty()) false else autofillHints.intersect(HINTS_FILLABLE).isEmpty()
    val hasAutofillHintPassword = autofillHints.intersect(HINTS_PASSWORD).isNotEmpty()
    private val hasAutofillHintNewPassword = autofillHints.intersect(HINTS_NEW_PASSWORD).isNotEmpty()
    private val hasAutofillHintUsername = autofillHints.intersect(HINTS_USERNAME).isNotEmpty()
    private val hasAutofillHintOtp = autofillHints.intersect(HINTS_OTP).isNotEmpty()

    // W3C autocomplete hint detection for HTML fields
    private val htmlAutocomplete = htmlAttributes["autocomplete"]

    // Ignored for now, see excludedByHints
    private val excludedByAutocompleteHint = htmlAutocomplete == "off"
    private val hasAutocompleteHintUsername = htmlAutocomplete == "username"
    val hasAutocompleteHintCurrentPassword = htmlAutocomplete == "current-password"
    private val hasAutocompleteHintNewPassword = htmlAutocomplete == "new-password"
    private val hasAutocompleteHintPassword =
        hasAutocompleteHintCurrentPassword || hasAutocompleteHintNewPassword
    private val hasAutocompleteHintOtp = htmlAutocomplete == "one-time-code"

    // Results of hint-based field type detection
    val hasHintUsername = hasAutofillHintUsername || hasAutocompleteHintUsername
    val hasHintPassword = hasAutofillHintPassword || hasAutocompleteHintPassword
    val hasHintNewPassword = hasAutofillHintNewPassword || hasAutocompleteHintNewPassword
    val hasHintOtp = hasAutofillHintOtp || hasAutocompleteHintOtp

    // Basic autofill exclusion checks
    private val hasAutofillTypeText = node.autofillType == View.AUTOFILL_TYPE_TEXT
    val isVisible = node.visibility == View.VISIBLE && htmlAttributes["aria-hidden"] != "true"

    // Hidden username fields are used to help password managers save credentials in two-step login
    // flows.
    // See: https://www.chromium.org/developers/design-documents/form-styles-that-chromium-understands
    val couldBeTwoStepHiddenUsername = !isVisible && isHtmlTextField && hasAutocompleteHintUsername

    // Some websites with two-step login flows offer hidden password fields to fill the password
    // already in the first step. Thus, we delegate the decision about filling invisible password
    // fields to the fill rules and only exclude those fields that have incompatible autocomplete
    // hint.
    val couldBeTwoStepHiddenPassword =
        !isVisible && isHtmlPasswordField && (hasAutocompleteHintCurrentPassword || htmlAutocomplete == null)

    // Since many site put autocomplete=off on login forms for compliance reasons or since they are
    // worried of the user's browser automatically (i.e., without any user interaction) filling
    // them, which we never do, we choose to ignore the value of excludedByAutocompleteHint.
    // TODO: Revisit this decision in the future
    private val excludedByHints = excludedByAutofillHints

    // Only offer to fill into custom views if they explicitly opted into Autofill.
    val relevantField = hasAutofillTypeText && (isTextField || autofillHints.isNotEmpty()) && !excludedByHints

    // Exclude fields based on hint, resource ID or HTML name.
    // Note: We still report excluded fields as relevant since they count for adjacency heuristics,
    // but ensure that they are never detected as password or username fields.
    private val hasExcludedTerm = EXCLUDED_TERMS.anyMatchesFieldInfo
    private val notExcluded = relevantField && !hasExcludedTerm

    // Password field heuristics (based only on the current field)
    private val isPossiblePasswordField =
        notExcluded && (isAndroidPasswordField || isHtmlPasswordField)
    private val isCertainPasswordField = isPossiblePasswordField && hasHintPassword
    private val isLikelyPasswordField = isPossiblePasswordField &&
        (isCertainPasswordField || PASSWORD_HEURISTIC_TERMS.anyMatchesFieldInfo)
    val passwordCertainty =
        if (isCertainPasswordField) CertaintyLevel.Certain else if (isLikelyPasswordField) CertaintyLevel.Likely else if (isPossiblePasswordField) CertaintyLevel.Possible else CertaintyLevel.Impossible

    // OTP field heuristics (based only on the current field)
    private val isPossibleOtpField = notExcluded && !isPossiblePasswordField
    private val isCertainOtpField = isPossibleOtpField && hasHintOtp
    private val isLikelyOtpField = isPossibleOtpField && (
        isCertainOtpField || OTP_HEURISTIC_TERMS.anyMatchesFieldInfo ||
            ((htmlMaxLength == null || htmlMaxLength in 6..8) && OTP_WEAK_HEURISTIC_TERMS.anyMatchesFieldInfo))
    val otpCertainty =
        if (isCertainOtpField) CertaintyLevel.Certain else if (isLikelyOtpField) CertaintyLevel.Likely else if (isPossibleOtpField) CertaintyLevel.Possible else CertaintyLevel.Impossible

    // Username field heuristics (based only on the current field)
    private val isPossibleUsernameField = notExcluded && !isPossiblePasswordField && !isCertainOtpField
    private val isCertainUsernameField = isPossibleUsernameField && hasHintUsername
    private val isLikelyUsernameField = isPossibleUsernameField && (isCertainUsernameField || (USERNAME_HEURISTIC_TERMS.anyMatchesFieldInfo))
    val usernameCertainty =
        if (isCertainUsernameField) CertaintyLevel.Certain else if (isLikelyUsernameField) CertaintyLevel.Likely else if (isPossibleUsernameField) CertaintyLevel.Possible else CertaintyLevel.Impossible

    infix fun directlyPrecedes(that: FormField?): Boolean {
        return index == (that ?: return false).index - 1
    }

    infix fun directlyPrecedes(that: Iterable<FormField>): Boolean {
        val firstIndex = that.map { it.index }.minOrNull() ?: return false
        return index == firstIndex - 1
    }

    infix fun directlyFollows(that: FormField?): Boolean {
        return index == (that ?: return false).index + 1
    }

    infix fun directlyFollows(that: Iterable<FormField>): Boolean {
        val lastIndex = that.map { it.index }.maxOrNull() ?: return false
        return index == lastIndex + 1
    }

    override fun toString(): String {
        val field = if (isHtmlTextField) "$htmlTag[type=$htmlInputType]" else className
        val description =
            "\"$hint\", \"$fieldId\"${if (isFocused) ", focused" else ""}${if (isVisible) ", visible" else ""}, $webOrigin, $htmlAttributesDebug, $autofillHints"
        return "$field ($description): password=$passwordCertainty, username=$usernameCertainty, otp=$otpCertainty"
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false
        return autofillId == (other as FormField).autofillId
    }

    override fun hashCode(): Int {
        return autofillId.hashCode()
    }
}
