/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.os.Build
import androidx.annotation.RequiresApi
import com.zeapo.pwdstore.autofill.oreo.CertaintyLevel.Certain
import com.zeapo.pwdstore.autofill.oreo.CertaintyLevel.Likely

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
            takePair { all { hasAutocompleteHintNewPassword } }
            breakTieOnPair { any { isFocused } }
        }
        currentPassword(optional = true) {
            takeSingle { alreadyMatched ->
                val adjacentToNewPasswords =
                    directlyPrecedes(alreadyMatched) || directlyFollows(alreadyMatched)
                hasAutocompleteHintCurrentPassword && adjacentToNewPasswords
            }
        }
        username(optional = true) {
            takeSingle { hasAutocompleteHintUsername }
            breakTieOnSingle { alreadyMatched -> directlyPrecedes(alreadyMatched) }
            breakTieOnSingle { isFocused }
        }
    }

    // Match a single current password field and optional username field with autocomplete hint.
    rule {
        currentPassword {
            takeSingle { hasAutocompleteHintCurrentPassword }
            breakTieOnSingle { isFocused }
        }
        username(optional = true) {
            takeSingle { hasAutocompleteHintUsername }
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
            takeSingle()
            breakTieOnSingle { usernameCertainty >= Likely }
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
            takeSingle()
            breakTieOnSingle { usernameCertainty >= Likely }
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
            takeSingle { hasAutocompleteHintNewPassword && isFocused }
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
}
