/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.app.assist.AssistStructure
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import androidx.annotation.RequiresApi
import com.github.ajalt.timberkt.e

enum class AutofillAction {
    Match, Search, Generate, FillOtpFromSms
}

/**
 * Represents a set of form fields with associated roles (e.g., username or new password) and
 * contains the logic that decides which fields should be filled or saved. The type [T] is one of
 * [FormField], [AssistStructure.ViewNode] or [AutofillId], depending on how much metadata about the
 * field is needed and available in the particular situation.
 */
@RequiresApi(Build.VERSION_CODES.O)
sealed class AutofillScenario<out T : Any> {

    companion object {

        const val BUNDLE_KEY_USERNAME_ID = "usernameId"
        const val BUNDLE_KEY_FILL_USERNAME = "fillUsername"
        const val BUNDLE_KEY_OTP_ID = "otpId"
        const val BUNDLE_KEY_CURRENT_PASSWORD_IDS = "currentPasswordIds"
        const val BUNDLE_KEY_NEW_PASSWORD_IDS = "newPasswordIds"
        const val BUNDLE_KEY_GENERIC_PASSWORD_IDS = "genericPasswordIds"

        fun fromBundle(clientState: Bundle): AutofillScenario<AutofillId>? {
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
            } catch (exception: IllegalArgumentException) {
                e(exception)
                null
            }
        }
    }

    class Builder<T : Any> {

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

    abstract val username: T?
    abstract val fillUsername: Boolean
    abstract val otp: T?
    abstract val allPasswordFields: List<T>
    abstract val passwordFieldsToFillOnMatch: List<T>
    abstract val passwordFieldsToFillOnSearch: List<T>
    abstract val passwordFieldsToFillOnGenerate: List<T>
    abstract val passwordFieldsToSave: List<T>

    val fieldsToSave
        get() = listOfNotNull(username) + passwordFieldsToSave

    val allFields
        get() = listOfNotNull(username, otp) + allPasswordFields

    fun fieldsToFillOn(action: AutofillAction): List<T> {
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
}

@RequiresApi(Build.VERSION_CODES.O)
data class ClassifiedAutofillScenario<T : Any>(
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
data class GenericAutofillScenario<T : Any>(
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

fun AutofillScenario<FormField>.passesOriginCheck(singleOriginMode: Boolean): Boolean {
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
fun Dataset.Builder.fillWith(
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

@RequiresApi(Build.VERSION_CODES.O)
@JvmName("fillWithFormField")
fun Dataset.Builder.fillWith(
    scenario: AutofillScenario<FormField>,
    action: AutofillAction,
    credentials: Credentials?
) {
    fillWith(scenario.map { it.autofillId }, action, credentials)
}

inline fun <T : Any, S : Any> AutofillScenario<T>.map(transform: (T) -> S): AutofillScenario<S> {
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
private fun AutofillScenario<AutofillId>.toBundle(): Bundle = when (this) {
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
@JvmName("toBundleFormField")
fun AutofillScenario<FormField>.toBundle(): Bundle = map { it.autofillId }.toBundle()

@RequiresApi(Build.VERSION_CODES.O)
fun AutofillScenario<AutofillId>.recoverNodes(structure: AssistStructure): AutofillScenario<AssistStructure.ViewNode>? {
    return map { autofillId ->
        structure.findNodeByAutofillId(autofillId) ?: return null
    }
}

val AutofillScenario<AssistStructure.ViewNode>.usernameValue: String?
    @RequiresApi(Build.VERSION_CODES.O) get() {
        val value = username?.autofillValue ?: return null
        return if (value.isText) value.textValue.toString() else null
    }
val AutofillScenario<AssistStructure.ViewNode>.passwordValue: String?
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
