/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.v2

import android.annotation.SuppressLint
import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi
import java.util.ArrayList

/**
 * Parse AssistStructure and guess username and password fields.
 */
@RequiresApi(Build.VERSION_CODES.O)
class StructureParser(private val structure: AssistStructure) {
    private lateinit var result: Result

    fun parse(): Result {
        result = Result()

        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            result.title.add(windowNode.title)
            parseViewNode(windowNode.rootViewNode)
        }

        return result
    }

    private fun parseViewNode(node: AssistStructure.ViewNode) {
        var hints = node.autofillHints

        if (hints == null) {
            // Could not find native autofill hints.
            // Try to infer any hints from the ID of the field (ie the #id of a webbased text input)
            val inferredHint = inferHint(node.idEntry)
            if (inferredHint != null) {
                hints = arrayOf(inferredHint)
            }
        }

        if (hints != null && hints.isNotEmpty()) {
            val hintsAsList = listOf(*hints)

            node.autofillId?.let {
                when {
                    hintsAsList.contains(View.AUTOFILL_HINT_USERNAME) -> result.username.add(it)
                    hintsAsList.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS) -> result.email.add(it)
                    hintsAsList.contains(View.AUTOFILL_HINT_PASSWORD) -> result.password.add(it)
                    !hintsAsList.contains("") && !hintsAsList.contains("off") -> {
                        // For now assume every input as fillable (except off and empty hint)
                        result.password.add(it)
                    }
                    else -> {
                        // Ignored
                    }
                }
            }
        } else if (node.autofillType == View.AUTOFILL_TYPE_TEXT) {
            // Attempt to match based on Field Type
            node.autofillId?.let {
                when (node.inputType) {
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> result.email.add(it)
                    InputType.TYPE_TEXT_VARIATION_PASSWORD -> result.password.add(it)
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> result.password.add(it)
                    else -> {
                        // Ignored
                    }
                }
            }
        }

        // Finally look for domain names
        val webDomain = node.webDomain
        if (webDomain != null) {
            result.webDomain.add(webDomain)
        }

        for (i in 0 until node.childCount) {
            parseViewNode(node.getChildAt(i))
        }
    }

    // Attempt to infer the AutoFill type from a string
    @SuppressLint("DefaultLocale")
    private fun inferHint(actualHint: String?): String? {
        if (actualHint == null) return null

        val hint = actualHint.toLowerCase()
        val isContain: (String) -> Boolean = { hint.contains(it) }

        val ignoredHints = listOf("label", "container")
        val passwordHints = listOf("password", "pass")
        val usernameHints = listOf(View.AUTOFILL_HINT_USERNAME, "login", "id", "user name", "identifier")
        val emailHints = listOf(View.AUTOFILL_HINT_EMAIL_ADDRESS, "email")
        val nameHints = listOf("name")
        val phoneHints = listOf("phone")

        if (ignoredHints.any(isContain)) {
            return null
        }

        if (passwordHints.any(isContain)) {
            return View.AUTOFILL_HINT_PASSWORD
        }

        if (usernameHints.any(isContain)) {
            return View.AUTOFILL_HINT_USERNAME
        }

        if (emailHints.any(isContain)) {
            return View.AUTOFILL_HINT_EMAIL_ADDRESS
        }

        if (nameHints.any(isContain)) {
            return View.AUTOFILL_HINT_NAME
        }

        if (phoneHints.any(isContain)) {
            return View.AUTOFILL_HINT_PHONE
        }

        return null
    }

    class Result {
        val title: MutableList<CharSequence> = ArrayList()
        val webDomain: MutableList<String> = ArrayList()
        val username: MutableList<AutofillId> = ArrayList()
        val email: MutableList<AutofillId> = ArrayList()
        val password: MutableList<AutofillId> = ArrayList()

        fun getAllAutoFillIds(): Array<AutofillId> {
            val autofillIds = ArrayList<AutofillId>()
            autofillIds.addAll(username)
            autofillIds.addAll(email)
            autofillIds.addAll(password)
            return autofillIds.toTypedArray()
        }
    }
}
