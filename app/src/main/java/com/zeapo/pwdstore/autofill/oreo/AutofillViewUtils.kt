package com.zeapo.pwdstore.autofill.oreo

import android.content.Context
import android.widget.RemoteViews
import com.github.androidpasswordstore.autofillparser.FormOrigin
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.PasswordRepository
import java.io.File

private fun makeRemoteView(
    context: Context,
    title: String,
    summary: String,
    iconRes: Int
): RemoteViews {
    return RemoteViews(context.packageName, R.layout.oreo_autofill_dataset).apply {
        setTextViewText(R.id.title, title)
        setTextViewText(R.id.summary, summary)
        setImageViewResource(R.id.icon, iconRes)
    }
}

fun makeFillMatchRemoteView(context: Context, file: File, formOrigin: FormOrigin): RemoteViews {
    val title = formOrigin.getPrettyIdentifier(context, untrusted = false)
    val directoryStructure = AutofillPreferences.directoryStructure(context)
    val relativeFile = file.relativeTo(PasswordRepository.getRepositoryDirectory())
    val summary = directoryStructure.getUsernameFor(relativeFile)
        ?: directoryStructure.getPathToIdentifierFor(relativeFile) ?: ""
    val iconRes = R.drawable.ic_person_black_24dp
    return makeRemoteView(context, title, summary, iconRes)
}

fun makeSearchAndFillRemoteView(context: Context, formOrigin: FormOrigin): RemoteViews {
    val title = formOrigin.getPrettyIdentifier(context, untrusted = true)
    val summary = context.getString(R.string.oreo_autofill_search_in_store)
    val iconRes = R.drawable.ic_search_black_24dp
    return makeRemoteView(context, title, summary, iconRes)
}

fun makeGenerateAndFillRemoteView(context: Context, formOrigin: FormOrigin): RemoteViews {
    val title = formOrigin.getPrettyIdentifier(context, untrusted = true)
    val summary = context.getString(R.string.oreo_autofill_generate_password)
    val iconRes = R.drawable.ic_autofill_new_password
    return makeRemoteView(context, title, summary, iconRes)
}

fun makeFillOtpFromSmsRemoteView(context: Context, formOrigin: FormOrigin): RemoteViews {
    val title = formOrigin.getPrettyIdentifier(context, untrusted = true)
    val summary = context.getString(R.string.oreo_autofill_fill_otp_from_sms)
    val iconRes = R.drawable.ic_autofill_sms
    return makeRemoteView(context, title, summary, iconRes)
}

fun makePlaceholderRemoteView(context: Context): RemoteViews {
    return makeRemoteView(context, "PLACEHOLDER", "PLACEHOLDER", R.mipmap.ic_launcher)
}

fun makeWarningRemoteView(context: Context): RemoteViews {
    val title = context.getString(R.string.oreo_autofill_warning_publisher_dataset_title)
    val summary = context.getString(R.string.oreo_autofill_warning_publisher_dataset_summary)
    val iconRes = R.drawable.ic_warning_red_24dp
    return makeRemoteView(context, title, summary, iconRes)
}
