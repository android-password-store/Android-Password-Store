/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: LGPL-3.0-only WITH LGPL-3.0-linking-exception
 */
package com.github.androidpasswordstore.autofillparser

import android.app.assist.AssistStructure
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import androidx.annotation.RequiresApi
import com.github.ajalt.timberkt.e

public enum class AutofillAction {
    Match, Search, Generate, FillOtpFromSms
}

/**
 * Represents a set of form fields with associated roles (e.g., username or new password) and
 * contains the logic that decides which fields should be filled or saved. The type [T] is one of
 * [FormField], [AssistStructure.ViewNode] or [AutofillId], depending on how much metadata about the
 * field is needed and available in the particular situation.
 */
@RequiresApi(Build.VERSION_CODES.O)
public sealed class AutofillScenario<out T : Any> {

    public companion object {

        internal const val BUNDLE_KEY_USERNAME_ID = "usernameId"
        internal const val BUNDLE_KEY_FILL_USERNAME = "fillUsername"
        internal const val BUNDLE_KEY_OTP_ID = "otpId"
        internal const val BUNDLE_KEY_CURRENT_PASSWORD_IDS = "currentPasswordIds"
        internal const val BUNDLE_KEY_NEW_PASSWORD_IDS = "newPasswordIds"
        internal const val BUNDLE_KEY_GENERIC_PASSWORD_IDS = "genericPasswordIds"

        @Deprecated("Use `fromClientState` instead.", ReplaceWith("fromClientState(clientState)", "com.github.androidpasswordstore.autofillparser.AutofillScenario.Companion.fromClientState"))
        public fun fromBundle(clientState: Bundle): AutofillScenario<AutofillId>? {
            return fromClientState(clientState)
        }

        public fun fromClientState(clientState: Bundle): AutofillScenario<AutofillId>? {
            return try {
                Builder<AutofillId>().apply {
                    username = clientState.getParcelable(BUNDLE_KEY_USERNAME_ID)
                    fillUsername = clientState.getBoolean(BUNDLE_KEY_FILL_USERNAME)
                    otp = clientState.getParcelable(BUNDLE_KEY_OTP_ID)
                    currentPassword.addAll(
                        clientState.getParcelableArrayList(
                            BUNDLE_KEY_CURRENT_PASSWORD_IDS
                        ) ?: emptyList()
                    )
                    newPassword.addAll(
                        clientState.getParcelableArrayList(
                            BUNDLE_KEY_NEW_PASSWORD_IDS
                        ) ?: emptyList()
                    )
                    genericPassword.addAll(
                        clientState.getParcelableArrayList(
                            BUNDLE_KEY_GENERIC_PASSWORD_IDS
                        ) ?: emptyList()
                    )
                }.build()
            } catch(e: Throwable) {
                e(e)
                null
            }
        }
    }

    internal class Builder<T : Any> {

        var username: T? = null
        var fillUsername = false
        var otp: T? = null
        val currentPassword = mutableListOf<T>()
        val newPassword = mutableListOf<T>()
        val genericPassword = mutableListOf<T>()

        fun build(): AutofillScenario<T> {
            require(genericPassword.isEmpty() || (currentPassword.isEmpty() && newPassword.isEmpty()))
            return if (currentPassword.isNotEmpty() || newPassword.isNotEmpty()) {
                ClassifiedAutofillScenario(
                    username = username,
                    fillUsername = fillUsername,
                    otp = otp,
                    currentPassword = currentPassword,
                    newPassword = newPassword
                )
            } else {
                GenericAutofillScenario(
                    username = username,
                    fillUsername = fillUsername,
                    otp = otp,
                    genericPassword = genericPassword
                )
            }
        }
    }

    public abstract val username: T?
    public abstract val passwordFieldsToSave: List<T>

    internal abstract val otp: T?
    internal abstract val allPasswordFields: List<T>
    internal abstract val fillUsername: Boolean
    internal abstract val passwordFieldsToFillOnMatch: List<T>
    internal abstract val passwordFieldsToFillOnSearch: List<T>
    internal abstract val passwordFieldsToFillOnGenerate: List<T>

    public val fieldsToSave: List<T>
        get() = listOfNotNull(username) + passwordFieldsToSave

    internal val allFields: List<T>
        get() = listOfNotNull(username, otp) + allPasswordFields

    internal fun fieldsToFillOn(action: AutofillAction): List<T> {
        val credentialFieldsToFill = when (action) {
            AutofillAction.Match -> passwordFieldsToFillOnMatch + listOfNotNull(otp)
            AutofillAction.Search -> passwordFieldsToFillOnSearch + listOfNotNull(otp)
            AutofillAction.Generate -> passwordFieldsToFillOnGenerate
            AutofillAction.FillOtpFromSms -> listOfNotNull(otp)
        }
        return when {
            action == AutofillAction.FillOtpFromSms -> {
                // When filling from an SMS, we cannot get any data other than the OTP itself.
                credentialFieldsToFill
            }
            credentialFieldsToFill.isNotEmpty() -> {
                // If the current action would fill into any password field, we also fill into the
                // username field if possible.
                listOfNotNull(username.takeIf { fillUsername }) + credentialFieldsToFill
            }
            allPasswordFields.isEmpty() && action != AutofillAction.Generate -> {
                // If there no password fields at all, we still offer to fill the username, e.g. in
                // two-step login scenarios, but we do not offer to generate a password.
                listOfNotNull(username.takeIf { fillUsername })
            }
            else -> emptyList()
        }
    }

    public fun hasFieldsToFillOn(action: AutofillAction): Boolean {
        return fieldsToFillOn(action).isNotEmpty()
    }

    public val hasFieldsToSave: Boolean
        get() = fieldsToSave.isNotEmpty()

    public val hasPasswordFieldsToSave: Boolean
        get() = fieldsToSave.minus(listOfNotNull(username)).isNotEmpty()

    public val hasUsername: Boolean
        get() = username != null
}

@RequiresApi(Build.VERSION_CODES.O)
internal data class ClassifiedAutofillScenario<T : Any>(
    override val username: T?,
    override val fillUsername: Boolean,
    override val otp: T?,
    val currentPassword: List<T>,
    val newPassword: List<T>
) : AutofillScenario<T>() {

    override val allPasswordFields
        get() = currentPassword + newPassword
    override val passwordFieldsToFillOnMatch
        get() = currentPassword
    override val passwordFieldsToFillOnSearch
        get() = currentPassword
    override val passwordFieldsToFillOnGenerate
        get() = newPassword
    override val passwordFieldsToSave
        get() = if (newPassword.isNotEmpty()) newPassword else currentPassword
}

@RequiresApi(Build.VERSION_CODES.O)
internal data class GenericAutofillScenario<T : Any>(
    override val username: T?,
    override val fillUsername: Boolean,
    override val otp: T?,
    val genericPassword: List<T>
) : AutofillScenario<T>() {

    override val allPasswordFields
        get() = genericPassword
    override val passwordFieldsToFillOnMatch
        get() = if (genericPassword.size == 1) genericPassword else emptyList()
    override val passwordFieldsToFillOnSearch
        get() = if (genericPassword.size == 1) genericPassword else emptyList()
    override val passwordFieldsToFillOnGenerate
        get() = genericPassword
    override val passwordFieldsToSave
        get() = genericPassword
}

internal fun AutofillScenario<FormField>.passesOriginCheck(singleOriginMode: Boolean): Boolean {
    return if (singleOriginMode) {
        // In single origin mode, only the browsers URL bar (which is never filled) should have
        // a webOrigin.
        allFields.all { it.webOrigin == null }
    } else {
        // In apps or browsers in multi origin mode, every field in a dataset has to belong to
        // the same (possibly null) origin.
        allFields.map { it.webOrigin }.toSet().size == 1
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@JvmName("fillWithAutofillId")
public fun Dataset.Builder.fillWith(
    scenario: AutofillScenario<AutofillId>,
    action: AutofillAction,
    credentials: Credentials?
) {
    val credentialsToFill = credentials ?: Credentials(
        "USERNAME",
        "PASSWORD",
        "OTP"
    )
    for (field in scenario.fieldsToFillOn(action)) {
        val value = when (field) {
            scenario.username -> credentialsToFill.username
            scenario.otp -> credentialsToFill.otp
            else -> credentialsToFill.password
        }
        setValue(field, AutofillValue.forText(value))
    }
}

internal inline fun <T : Any, S : Any> AutofillScenario<T>.map(transform: (T) -> S): AutofillScenario<S> {
    val builder = AutofillScenario.Builder<S>()
    builder.username = username?.let(transform)
    builder.fillUsername = fillUsername
    builder.otp = otp?.let(transform)
    when (this) {
        is ClassifiedAutofillScenario -> {
            builder.currentPassword.addAll(currentPassword.map(transform))
            builder.newPassword.addAll(newPassword.map(transform))
        }
        is GenericAutofillScenario -> {
            builder.genericPassword.addAll(genericPassword.map(transform))
        }
    }
    return builder.build()
}

@RequiresApi(Build.VERSION_CODES.O)
@JvmName("toBundleAutofillId")
internal fun AutofillScenario<AutofillId>.toBundle(): Bundle = when (this) {
    is ClassifiedAutofillScenario<AutofillId> -> {
        Bundle(5).apply {
            putParcelable(AutofillScenario.BUNDLE_KEY_USERNAME_ID, username)
            putBoolean(AutofillScenario.BUNDLE_KEY_FILL_USERNAME, fillUsername)
            putParcelable(AutofillScenario.BUNDLE_KEY_OTP_ID, otp)
            putParcelableArrayList(
                AutofillScenario.BUNDLE_KEY_CURRENT_PASSWORD_IDS, ArrayList(currentPassword)
            )
            putParcelableArrayList(
                AutofillScenario.BUNDLE_KEY_NEW_PASSWORD_IDS, ArrayList(newPassword)
            )
        }
    }
    is GenericAutofillScenario<AutofillId> -> {
        Bundle(4).apply {
            putParcelable(AutofillScenario.BUNDLE_KEY_USERNAME_ID, username)
            putBoolean(AutofillScenario.BUNDLE_KEY_FILL_USERNAME, fillUsername)
            putParcelable(AutofillScenario.BUNDLE_KEY_OTP_ID, otp)
            putParcelableArrayList(
                AutofillScenario.BUNDLE_KEY_GENERIC_PASSWORD_IDS, ArrayList(genericPassword)
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
public fun AutofillScenario<AutofillId>.recoverNodes(structure: AssistStructure): AutofillScenario<AssistStructure.ViewNode>? {
    return map { autofillId ->
        structure.findNodeByAutofillId(autofillId) ?: return null
    }
}

public val AutofillScenario<AssistStructure.ViewNode>.usernameValue: String?
    @RequiresApi(Build.VERSION_CODES.O) get() {
        val value = username?.autofillValue ?: return null
        return if (value.isText) value.textValue.toString() else null
    }
public val AutofillScenario<AssistStructure.ViewNode>.passwordValue: String?
    @RequiresApi(Build.VERSION_CODES.O) get() {
        val distinctValues = passwordFieldsToSave.map {
            if (it.autofillValue?.isText == true) {
                it.autofillValue?.textValue?.toString()
            } else {
                null
            }
        }.toSet()
        // Only return a non-null password value when all password fields agree
        return distinctValues.singleOrNull()
    }
