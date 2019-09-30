package com.zeapo.pwdstore.autofill.v2

import android.content.Context
import android.os.Build
import android.service.autofill.Dataset
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.zeapo.pwdstore.R
import android.view.autofill.AutofillValue


@RequiresApi(api = Build.VERSION_CODES.O)
class AutofillUtils {
    companion object {
        fun getAutocompleteEntry(context: Context, text: String, icon: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.autofill_service_list_item)
            views.setTextViewText(R.id.textView, text)
            views.setImageViewResource(R.id.imageIcon, icon)
            return views
        }

        fun buildDataset(context: Context, title: String, entry: String, struct: StructureParser.Result): Dataset? {
            val views = getAutocompleteEntry(context, title, R.drawable.ic_action_secure)
            val builder = Dataset.Builder(views)

            // Build the Autofill response (only for password for now)
            if (entry.isNotEmpty()) {
                struct.password.forEach  { id -> builder.setValue(id, AutofillValue.forText(entry)) }
            }

            return try {
                builder.build()
            } catch (e: IllegalArgumentException) {
                // if not value be set
                null
            }

        }
    }

}