/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.InlinePresentation
import android.view.View
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.DrawableRes
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.zeapo.pwdstore.PasswordStore
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.PasswordRepository
import java.io.File

data class DatasetMetadata(val title: String, val subtitle: String?, @DrawableRes val iconRes: Int)

fun makeRemoteView(context: Context, metadata: DatasetMetadata): RemoteViews {
    return RemoteViews(context.packageName, R.layout.oreo_autofill_dataset).apply {
        setTextViewText(R.id.title, metadata.title)
        if (metadata.subtitle != null) {
            setTextViewText(R.id.summary, metadata.subtitle)
        } else {
            setViewVisibility(R.id.summary, View.GONE)
        }
        if (metadata.iconRes != Resources.ID_NULL) {
            setImageViewResource(R.id.icon, metadata.iconRes)
        } else {
            setViewVisibility(R.id.icon, View.GONE)
        }
    }
}

@SuppressLint("RestrictedApi")
fun makeInlinePresentation(context: Context, imeSpec: InlinePresentationSpec, metadata: DatasetMetadata): InlinePresentation? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
        return null

    if (UiVersions.INLINE_UI_VERSION_1 !in UiVersions.getVersions(imeSpec.style))
        return null

    val launchIntent = PendingIntent.getActivity(context, 0, Intent(context, PasswordStore::class.java), 0)
    val slice = InlineSuggestionUi.newContentBuilder(launchIntent).run {
        setTitle(metadata.title)
        if (metadata.subtitle != null)
            setSubtitle(metadata.subtitle)
        setContentDescription(if (metadata.subtitle != null) "${metadata.title} - ${metadata.subtitle}" else metadata.title)
        setStartIcon(Icon.createWithResource(context, metadata.iconRes))
        build().slice
    }

    return InlinePresentation(slice, imeSpec, false)
}


fun makeFillMatchMetadata(context: Context, file: File): DatasetMetadata {
    val directoryStructure = AutofillPreferences.directoryStructure(context)
    val relativeFile = file.relativeTo(PasswordRepository.getRepositoryDirectory())
    val title = directoryStructure.getIdentifierFor(relativeFile)
        ?: directoryStructure.getAccountPartFor(relativeFile)!!
    val subtitle = directoryStructure.getAccountPartFor(relativeFile)
    return DatasetMetadata(
        title,
        subtitle,
        R.drawable.ic_person_black_24dp
    )
}

fun makeSearchAndFillMetadata(context: Context) = DatasetMetadata(
    context.getString(R.string.oreo_autofill_search_in_store),
    null,
    R.drawable.ic_search_black_24dp
)

fun makeGenerateAndFillMetadata(context: Context) = DatasetMetadata(
    context.getString(R.string.oreo_autofill_generate_password),
    null,
    R.drawable.ic_autofill_new_password
)

fun makeFillOtpFromSmsMetadata(context: Context) = DatasetMetadata(
    context.getString(R.string.oreo_autofill_fill_otp_from_sms),
    null,
    R.drawable.ic_autofill_sms
)

fun makeEmptyMetadata(context: Context) = DatasetMetadata(
    "PLACEHOLDER",
    "PLACEHOLDER",
    R.mipmap.ic_launcher
)

fun makeWarningMetadata(context: Context) = DatasetMetadata(
    context.getString(R.string.oreo_autofill_warning_publisher_dataset_title),
    context.getString(R.string.oreo_autofill_warning_publisher_dataset_summary),
    R.drawable.ic_warning_red_24dp
)

fun makeHeaderMetadata(title: String) = DatasetMetadata(
    title,
    null,
    0
)
