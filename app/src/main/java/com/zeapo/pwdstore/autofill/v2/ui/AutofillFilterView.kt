package com.zeapo.pwdstore.autofill.v2.ui

import android.annotation.TargetApi
import android.app.PendingIntent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import com.zeapo.pwdstore.R
import kotlinx.android.synthetic.main.activity_auto_fill_filter_view.*
import android.service.autofill.FillResponse
import com.zeapo.pwdstore.autofill.v2.StructureParser
import android.view.autofill.AutofillManager
import android.app.assist.AssistStructure
import android.content.Context
import com.zeapo.pwdstore.autofill.v2.AutofillUtils
import android.content.Intent
import android.content.IntentSender


@TargetApi(Build.VERSION_CODES.O)
class AutofillFilterView : AppCompatActivity() {

    companion object {
        fun getFilterWindow(context: Context): IntentSender {
            val intent = Intent(context, AutofillFilterView::class.java)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT).intentSender
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_fill_filter_view)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        bindUI()
    }

    private fun bindUI() {
        // TODO RecyclerView

        // TODO Remove it and replace by a close action
        fill.setOnClickListener {
            setResponse()
            finish()
        }
    }

    private fun getStructure(): StructureParser.Result? {
        val structure = intent.getParcelableExtra<AssistStructure>(AutofillManager.EXTRA_ASSIST_STRUCTURE)
        return when {
            structure != null -> StructureParser(structure).parse()
            else -> null
        }
    }

    // TODO Trigger by the list item choice
    private fun setResponse() {
        val structure = getStructure() ?: return
        val replyIntent = Intent()

        // TODO Replace Title and Entry by user choice
        val response = FillResponse
                .Builder()
                .addDataset(AutofillUtils.buildDataset(this, "Test", "Password", structure))
                .build()

        replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)

        // TODO Replace by user choice or not
        if(true) {
            setResult(RESULT_OK, replyIntent)
        } else {
            setResult(RESULT_CANCELED)
        }
    }

}
