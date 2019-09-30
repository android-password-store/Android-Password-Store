package com.zeapo.pwdstore.autofill

import android.app.PendingIntent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.service.autofill.AutofillService
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import android.service.autofill.FillResponse
import android.widget.RemoteViews
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.autofill.ui.AutoFillFilterView


@RequiresApi(Build.VERSION_CODES.O)
class AutofillService2 : AutofillService() {

    private var dialog: AlertDialog? = null

    override fun onConnected() {
    }

    override fun onDisconnected() {
    }

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.last().structure
        val activityPackageName = structure.activityComponent.packageName
        if (this.packageName == activityPackageName) {
            callback.onSuccess(null)
            return
        }

        val parseResult = StructureParser(structure).parse()
        if (parseResult.password.isEmpty() && parseResult.username.isEmpty() && parseResult.email.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val responseBuilder = FillResponse.Builder()

        val presentation = getAutocompleteView(this, getString(R.string.app_name))
        responseBuilder.setAuthentication(parseResult.getAllAutoFillIds(), getAuthWindow(this), presentation)

        // If there are no errors, call onSuccess() and pass the response
        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onFailure("IMPLEMENT ME")
    }

    private fun getAuthWindow(context: Context): IntentSender {
        val intent = Intent(context, AutoFillFilterView::class.java)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT).intentSender
    }

    private fun getAutocompleteView(context: Context, text: String): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.autofill_service_list_item)
        views.setTextViewText(R.id.textView, text)
        views.setImageViewResource(R.id.imageIcon, R.mipmap.ic_launcher)
        return views
    }
}