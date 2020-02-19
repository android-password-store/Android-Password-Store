/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo.ui

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import com.afollestad.recyclical.datasource.dataSourceOf
import com.afollestad.recyclical.setup
import com.afollestad.recyclical.withItem
import com.github.ajalt.timberkt.e
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.autofill.oreo.AutofillMatcher
import com.zeapo.pwdstore.autofill.oreo.FormOrigin
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import java.io.File
import java.util.Locale
import kotlinx.android.synthetic.main.activity_oreo_autofill_filter.*

@TargetApi(Build.VERSION_CODES.O)
class AutofillFilterView : AppCompatActivity() {

    companion object {
        private const val HEIGHT_PERCENTAGE = 0.9
        private const val WIDTH_PERCENTAGE = 0.75

        private const val EXTRA_FORM_ORIGIN_WEB =
            "com.zeapo.pwdstore.autofill.oreo.ui.EXTRA_FORM_ORIGIN_WEB"
        private const val EXTRA_FORM_ORIGIN_APP =
            "com.zeapo.pwdstore.autofill.oreo.ui.EXTRA_FORM_ORIGIN_APP"
        private var matchAndDecryptFileRequestCode = 1

        fun makeMatchAndDecryptFileIntentSender(
            context: Context,
            formOrigin: FormOrigin
        ): IntentSender {
            val intent = Intent(context, AutofillFilterView::class.java).apply {
                when (formOrigin) {
                    is FormOrigin.Web -> putExtra(EXTRA_FORM_ORIGIN_WEB, formOrigin.identifier)
                    is FormOrigin.App -> putExtra(EXTRA_FORM_ORIGIN_APP, formOrigin.identifier)
                }
            }
            return PendingIntent.getActivity(
                context,
                matchAndDecryptFileRequestCode++,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT
            ).intentSender
        }
    }

    private val dataSource = dataSourceOf()
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val sortOrder
        get() = PasswordRepository.PasswordSortOrder.getSortOrder(preferences)

    private lateinit var formOrigin: FormOrigin

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oreo_autofill_filter)
        setFinishOnTouchOutside(true)

        val params = window.attributes
        params.height = (HEIGHT_PERCENTAGE * resources.displayMetrics.heightPixels).toInt()
        params.width = (WIDTH_PERCENTAGE * resources.displayMetrics.widthPixels).toInt()
        window.attributes = params

        if (intent?.hasExtra(AutofillManager.EXTRA_CLIENT_STATE) != true) {
            e { "AutofillFilterActivity started without EXTRA_CLIENT_STATE" }
            finish()
            return
        }
        formOrigin = when {
            intent?.hasExtra(EXTRA_FORM_ORIGIN_WEB) == true -> {
                FormOrigin.Web(intent!!.getStringExtra(EXTRA_FORM_ORIGIN_WEB)!!)
            }
            intent?.hasExtra(EXTRA_FORM_ORIGIN_APP) == true -> {
                FormOrigin.App(intent!!.getStringExtra(EXTRA_FORM_ORIGIN_APP)!!)
            }
            else -> {
                e { "AutofillFilterActivity started without EXTRA_FORM_ORIGIN_WEB or EXTRA_FORM_ORIGIN_APP" }
                finish()
                return
            }
        }

        supportActionBar?.hide()
        bindUI()
        setResult(RESULT_CANCELED)
    }

    private fun bindUI() {
        // setup is an extension method provided by recyclical
        rvPassword.setup {
            withDataSource(dataSource)
            withItem<PasswordItem, PasswordViewHolder>(R.layout.oreo_autofill_filter_row) {
                onBind(::PasswordViewHolder) { _, item ->
                    title.text = item.fullPathToParent
                    // drop the .gpg extension
                    subtitle.text = item.name.dropLast(4)
                }
                onClick { decryptAndFill(item) }
            }
        }
        rvPassword.apply {
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        search.addTextChangedListener { recursiveFilter(it.toString(), strict = false) }
        val initialFilter =
            formOrigin.getPrettyIdentifier(applicationContext, untrusted = false)
        search.setText(initialFilter, TextView.BufferType.EDITABLE)
        recursiveFilter(initialFilter, strict = formOrigin is FormOrigin.Web)

        shouldMatch.apply {
            text = getString(
                R.string.oreo_autofill_match_with,
                formOrigin.getPrettyIdentifier(applicationContext)
            )
        }
    }

    private fun decryptAndFill(item: PasswordItem) {
        if (shouldClear.isChecked) AutofillMatcher.clearMatchesFor(applicationContext, formOrigin)
        if (shouldMatch.isChecked) AutofillMatcher.addMatchFor(
            applicationContext,
            formOrigin,
            item.file
        )
        // intent?.extras? is checked to be non-null in onCreate
        startActivityForResult(
            AutofillDecryptActivity.makeDecryptFileIntent(
                item.file,
                intent!!.extras!!,
                this
            ), 1
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) setResult(RESULT_OK, data)
            finish()
        }
    }

    private fun recursiveFilter(filter: String, dir: File? = null, strict: Boolean = true) {
        val root = PasswordRepository.getRepositoryDirectory(this)
        // on the root the pathStack is empty
        val passwordItems = if (dir == null) {
            PasswordRepository.getPasswords(
                PasswordRepository.getRepositoryDirectory(this),
                sortOrder
            )
        } else {
            PasswordRepository.getPasswords(
                dir,
                PasswordRepository.getRepositoryDirectory(this),
                sortOrder
            )
        }

        for (item in passwordItems) {
            if (item.type == PasswordItem.TYPE_CATEGORY) {
                recursiveFilter(filter, item.file, strict = strict)
            }

            // TODO: Implement fuzzy search if strict == false?
            val matches = if (strict) item.file.parentFile.name.let {
                it == filter || it.endsWith(".$filter") || it.endsWith("://$filter")
            }
            else "${item.file.relativeTo(root).path}/${item.file.nameWithoutExtension}".toLowerCase(
                Locale.getDefault()
            ).contains(filter.toLowerCase(Locale.getDefault()))

            val inAdapter = dataSource.contains(item)
            if (item.type == PasswordItem.TYPE_PASSWORD && matches && !inAdapter) {
                dataSource.add(item)
            } else if (!matches && inAdapter) {
                dataSource.remove(item)
            }
        }
    }
}
