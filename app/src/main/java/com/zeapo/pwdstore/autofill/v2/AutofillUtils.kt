/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.v2

import android.content.Context
import android.os.Build
import android.service.autofill.Dataset
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.zeapo.pwdstore.R
import android.view.autofill.AutofillValue

@RequiresApi(Build.VERSION_CODES.O)
object AutofillUtils {
    fun getAutocompleteEntry(context: Context, text: String, icon: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.autofill_service_list_item)
        views.setTextViewText(R.id.textView, text)
        views.setImageViewResource(R.id.imageIcon, icon)
        return views
    }

    fun buildDataset(context: Context, title: String, entry: String, struct: StructureParser.Result): Dataset? {
        val views = getAutocompleteEntry(context, title, R.drawable.autofill_icon_entry)
        val builder = Dataset.Builder(views)

        // Build the Autofill response (only for password for now)
        if (entry.isNotEmpty()) {
            struct.password.forEach { id -> builder.setValue(id, AutofillValue.forText(entry)) }
        }

        return try {
            builder.build()
        } catch (e: IllegalArgumentException) {
            // if not value be set
            null
        }
    }
}
