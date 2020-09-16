/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only OR (LGPL-3.0-only WITH LGPL-3.0-linking-exception)
 */
package com.github.androidpasswordstore.autofillparser

import android.os.Build
import androidx.annotation.RequiresApi
import com.github.androidpasswordstore.autofillparser.CertaintyLevel.Certain
import com.github.androidpasswordstore.autofillparser.CertaintyLevel.Likely

private inline fun <T> Pair<T, T>.all(predicate: T.() -> Boolean) =
    predicate(first) && predicate(second)

private inline fun <T> Pair<T, T>.any(predicate: T.() -> Boolean) =
    predicate(first) || predicate(second)

private inline fun <T> Pair<T, T>.none(predicate: T.() -> Boolean) =
    !predicate(first) && !predicate(second)

/**
 * The strategy used to detect [AutofillScenario]s; expressed using the DSL implemented in
 * [AutofillDsl].
 */
@RequiresApi(Build.VERSION_CODES.O)
val autofillStrategy = strategy {

    // Match two new password fields, an optional current password field right below or above, and
    // an optional username field with autocomplete hint.
    // TODO: Introduce a custom fill/generate/update flow for this scenario
    rule {
        newPassword {
            takePair { all { hasHintNewPassword } }
            breakTieOnPair { any { isFocused } }
        }
        currentPassword(optional = true) {
            takeSingle { alreadyMatched ->
                val adjacentToNewPasswords =
                    directlyPrecedes(alreadyMatched) || directlyFollows(alreadyMatched)
                // The Autofill framework has not hint that applies to current passwords only.
                // In this scenario, we have already matched fields a pair of fields with a specific
                // new password hint, so we take a generic Autofill password hint to mean a current
                // password.
                (hasAutocompleteHintCurrentPassword || hasAutofillHintPassword) &&
                    adjacentToNewPasswords
            }
        }
        username(optional = true) {
            takeSingle { hasHintUsername }
            breakTieOnSingle { alreadyMatched -> directlyPrecedes(alreadyMatched) }
            breakTieOnSingle { isFocused }
        }
    }

    // Match a single focused current password field and hidden username field with autocomplete
    // hint. This configuration is commonly used in two-step login flows to allow password managers
    // to save the username.
    // See: https://www.chromium.org/developers/design-documents/form-styles-that-chromium-understands
    // Note: The username is never filled in this scenario since usernames are generally only filled
    // in visible fields.
    rule {
        username(matchHidden = true) {
            takeSingle {
                couldBeTwoStepHiddenUsername
            }
        }
        currentPassword {
            takeSingle { _ ->
                hasAutocompleteHintCurrentPassword && isFocused
            }
        }
    }

    // Match a single current password field and optional username field with autocomplete hint.
    rule {
        currentPassword {
            takeSingle { hasAutocompleteHintCurrentPassword }
            breakTieOnSingle { isFocused }
        }
        username(optional = true) {
            takeSingle { hasHintUsername }
            breakTieOnSingle { alreadyMatched -> directlyPrecedes(alreadyMatched) }
            breakTieOnSingle { isFocused }
        }
    }

    // Match two adjacent password fields, implicitly understood as new passwords, and optional
    // username field.
    rule {
        newPassword {
            takePair { all { passwordCertainty >= Likely } }
            breakTieOnPair { all { passwordCertainty >= Certain } }
            breakTieOnPair { any { isFocused } }
        }
        username(optional = true) {
            takeSingle { usernameCertainty >= Likely }
            breakTieOnSingle { usernameCertainty >= Certain }
            breakTieOnSingle { alreadyMatched -> directlyPrecedes(alreadyMatched) }
            breakTieOnSingle { isFocused }
        }
    }

    // Match a single password field and optional username field.
    rule {
        genericPassword {
            takeSingle { passwordCertainty >= Likely }
            breakTieOnSingle { passwordCertainty >= Certain }
            breakTieOnSingle { isFocused }
        }
        username(optional = true) {
            takeSingle { usernameCertainty >= Likely }
            breakTieOnSingle { usernameCertainty >= Certain }
            breakTieOnSingle { alreadyMatched -> directlyPrecedes(alreadyMatched) }
            breakTieOnSingle { isFocused }
        }
    }

    // Match a single focused new password field and optional preceding username field.
    // This rule can apply in single origin mode since it only fills into a single focused password
    // field.
    rule(applyInSingleOriginMode = true) {
        newPassword {
            takeSingle { hasHintNewPassword && isFocused }
        }
        username(optional = true) {
            takeSingle { alreadyMatched ->
                usernameCertainty >= Likely && directlyPrecedes(alreadyMatched.singleOrNull())
            }
        }
    }

    // Match a single focused current password field and optional preceding username field.
    // This rule can apply in single origin mode since it only fills into a single focused password
    // field.
    rule(applyInSingleOriginMode = true) {
        currentPassword {
            takeSingle { hasAutocompleteHintCurrentPassword && isFocused }
        }
        username(optional = true) {
            takeSingle { alreadyMatched ->
                usernameCertainty >= Likely && directlyPrecedes(alreadyMatched.singleOrNull())
            }
        }
    }

    // Match a single focused password field and optional preceding username field.
    // This rule can apply in single origin mode since it only fills into a single focused password
    // field.
    rule(applyInSingleOriginMode = true) {
        genericPassword {
            takeSingle { passwordCertainty >= Likely && isFocused }
        }
        username(optional = true) {
            takeSingle { alreadyMatched ->
                usernameCertainty >= Likely && directlyPrecedes(alreadyMatched.singleOrNull())
            }
        }
    }

    // Match a focused username field with autocomplete hint directly followed by a hidden password
    // field, which is a common scenario in two-step login flows. No tie breakers are used to limit
    // filling of hidden password fields to scenarios where this is clearly warranted.
    rule {
        username {
            takeSingle { hasHintUsername && isFocused }
        }
        currentPassword(matchHidden = true) {
            takeSingle { alreadyMatched ->
                directlyFollows(alreadyMatched.singleOrNull()) && couldBeTwoStepHiddenPassword
            }
        }
    }

    // Match a single focused OTP field.
    rule(applyInSingleOriginMode = true) {
        otp {
            takeSingle { otpCertainty >= Likely && isFocused }
        }
    }

    // Match a single focused username field without a password field.
    rule(applyInSingleOriginMode = true) {
        username {
            takeSingle { usernameCertainty >= Likely && isFocused }
            breakTieOnSingle { usernameCertainty >= Certain }
            breakTieOnSingle { hasHintUsername }
        }
    }

    // Match any focused password field with optional username field on manual request.
    rule(applyInSingleOriginMode = true, applyOnManualRequestOnly = true) {
        genericPassword {
            takeSingle { isFocused }
        }
        username(optional = true) {
            takeSingle { alreadyMatched ->
                usernameCertainty >= Likely && directlyPrecedes(alreadyMatched.singleOrNull())
            }
        }
    }

    // Match any focused username field on manual request.
    rule(applyInSingleOriginMode = true, applyOnManualRequestOnly = true) {
        username {
            takeSingle { isFocused }
        }
    }
}
