package com.zeapo.pwdstore.autofill.v2

import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.service.autofill.AutofillService
import androidx.annotation.RequiresApi
import android.service.autofill.FillResponse
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.autofill.v2.ui.AutofillFilterView
import android.util.Log
import com.zeapo.pwdstore.autofill.v2.AutofillUtils.Companion.getAutocompleteEntry


@RequiresApi(Build.VERSION_CODES.O)
class PasswordAutofillService : AutofillService() {

    private val TAG = PasswordAutofillService::class.java.name

    override fun onConnected() {
        Log.d(TAG, "onConnected")
    }

    override fun onDisconnected() {
        Log.d(TAG, "onDisconnected")
    }

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.last().structure
        val activityPackageName = structure.activityComponent.packageName
        if (this.packageName == activityPackageName) {
            callback.onSuccess(null)
            return
        }

        val parseResult = StructureParser(structure).parse()

        // Unreconnized field => Finish
        if (parseResult.password.isEmpty() && parseResult.username.isEmpty() && parseResult.email.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val responseBuilder = FillResponse.Builder()

        // Add only Unlock entry
        val stubEntry = getAutocompleteEntry(this, getString(R.string.app_name), R.mipmap.ic_launcher)
        responseBuilder.setAuthentication(parseResult.getAllAutoFillIds(), AutofillFilterView.getFilterWindow(this), stubEntry)

        // If there are no errors, call onSuccess() and pass the response
        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onFailure(getString(R.string.autofill_not_support_save))
    }
}