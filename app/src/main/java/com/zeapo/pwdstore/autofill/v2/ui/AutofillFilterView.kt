/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.v2.ui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import android.service.autofill.FillResponse
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.WindowManager
import android.view.autofill.AutofillManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.afollestad.recyclical.datasource.dataSourceOf
import com.afollestad.recyclical.setup
import com.afollestad.recyclical.withItem
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.autofill.v2.AutofillUtils
import com.zeapo.pwdstore.autofill.v2.StructureParser
import com.zeapo.pwdstore.autofill.v2.ui.holder.PasswordViewHolder
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.afterTextChanged
import java.io.File
import kotlinx.android.synthetic.main.activity_auto_fill_filter_view.*

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

        supportActionBar?.hide()
        settings = PreferenceManager.getDefaultSharedPreferences(this)

        bindUI()
    }

    private fun bindUI() {
        // setup{} is an extension method on RecyclerView
        rvPassword.setup {
            withDataSource(dataSource)
            withItem<PasswordItem, PasswordViewHolder>(R.layout.password_row_layout) {
                onBind(::PasswordViewHolder) { _, item ->
                    val source = "${item.fullPathToParent}\n${item.longName}"
                    val spannable = SpannableString(source).apply {
                        setSpan(RelativeSizeSpan(0.7f), 0, item.fullPathToParent.length, 0)
                    }
                    this.label.text = spannable
                    this.typeImage.setImageResource(R.drawable.ic_action_secure_24dp)
                }
                onClick {
                    setResponse(item)
                    finish()
                }
            }
        }

        dataSource.set(getLastPasswordsList())

        search.afterTextChanged {
            dataSource.clear()
            if (it.isEmpty()) {
                dataSource.set(getLastPasswordsList())
            } else {
                recursiveFilter(it, null)
            }
        }

        overlay.setOnClickListener {
            setResponse(null)
            finish()
        }
    }

    private fun getLastPasswordsList(): List<PasswordItem> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcutManager = getSystemService<ShortcutManager>()
            if (shortcutManager != null) {
                val shortcuts = shortcutManager.dynamicShortcuts
                shortcuts.map {
                    PasswordItem.newPassword(it.shortLabel.toString(), File(it.longLabel.toString()), File("/"))
                }
            } else {
                emptyList()
            }
        } else {
            emptyList()
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

        if (item == null) {
            setResult(RESULT_CANCELED)
            return
        }

        val structure = getStructure() ?: return
        val replyIntent = Intent()

        // TODO Replace the Entry by the password decoded from the user choice
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
            PasswordRepository.getPasswords(PasswordRepository.getRepositoryDirectory(this)!!, getSortOrder())
        } else {
            PasswordRepository.getPasswords(dir, PasswordRepository.getRepositoryDirectory(this)!!, getSortOrder())
        }

        for (item in passwordItems) {
            if (item.type == PasswordItem.TYPE_CATEGORY) {
                recursiveFilter(filter, item.file)
            }

            val matches = ("${item.file.absolutePath}/$item").toLowerCase().contains(filter.toLowerCase())
            val inAdapter = dataSource.contains(item)
            if (item.type == PasswordItem.TYPE_PASSWORD && matches && !inAdapter) {
                dataSource.add(item)
            } else if (!matches && inAdapter) {
                dataSource.remove(item)
            }
        }
    }

    private fun getSortOrder(): PasswordRepository.PasswordSortOrder {
        return settings?.let {
            PasswordRepository.PasswordSortOrder.getSortOrder(it)
        } ?: run {
            PasswordRepository.PasswordSortOrder.FOLDER_FIRST
        }
    }
}
