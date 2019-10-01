package com.zeapo.pwdstore.autofill.v2.ui

import android.annotation.SuppressLint
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
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.afollestad.recyclical.datasource.dataSourceOf
import com.afollestad.recyclical.setup
import com.afollestad.recyclical.withItem
import com.zeapo.pwdstore.autofill.v2.ui.holder.PasswordViewHolder
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.afterTextChanged
import java.io.File


@TargetApi(Build.VERSION_CODES.O)
class AutofillFilterView : AppCompatActivity() {

    companion object {
        fun getFilterWindow(context: Context): IntentSender {
            val intent = Intent(context, AutofillFilterView::class.java)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT).intentSender
        }
    }

    private val dataSource = dataSourceOf()
    private var settings: SharedPreferences? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_fill_filter_view)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        settings = PreferenceManager.getDefaultSharedPreferences(this)

        bindUI()
    }

    private fun bindUI() {
        // setup{} is an extension method on RecyclerView
        rvPassword.setup {
            withDataSource(dataSource)
            withItem<PasswordItem, PasswordViewHolder>(R.layout.password_row_layout) {
                onBind(::PasswordViewHolder) { _, item ->
                    this.label.text = item.longName
                    this.type.text = item.fullPathToParent
                    this.type_image.setImageResource(R.drawable.ic_action_secure)
                }
                onClick {
                    setResponse(item)
                    finish()
                }
            }
        }

        search.afterTextChanged { recursiveFilter(it, null) }

        close.setOnClickListener {
            setResponse(null)
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

    private fun setResponse(item: PasswordItem?) {

        if(item == null){
            setResult(RESULT_CANCELED)
            return
        }

        val structure = getStructure() ?: return
        val replyIntent = Intent()

        // TODO Replace Title and Entry by user choice
        val response = FillResponse
                .Builder()
                .addDataset(AutofillUtils.buildDataset(this, item.longName, "Password", structure))
                .build()

        replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)

        setResult(RESULT_OK, replyIntent)
    }

    @SuppressLint("DefaultLocale")
    private fun recursiveFilter(filter: String, dir: File?) {
        // on the root the pathStack is empty
        val passwordItems = if (dir == null) {
            PasswordRepository.getPasswords(PasswordRepository.getRepositoryDirectory(this), getSortOrder())
        } else{
            PasswordRepository.getPasswords(dir, PasswordRepository.getRepositoryDirectory(this), getSortOrder())
        }

        for (item in passwordItems) {
            if (item.type == PasswordItem.TYPE_CATEGORY) {
                recursiveFilter(filter, item.file)
            }

            val matches = item.toString().toLowerCase().contains(filter.toLowerCase())
            val inAdapter = dataSource.contains(item)
            if (matches && !inAdapter) {
                dataSource.add(item)
            } else if (!matches && inAdapter) {
                dataSource.remove(item)
            }
        }
    }

    private fun getSortOrder(): PasswordRepository.PasswordSortOrder {
        return PasswordRepository.PasswordSortOrder.getSortOrder(settings)
    }

}
