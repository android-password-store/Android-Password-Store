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
import com.zeapo.pwdstore.autofill.oreo.AutofillPreferences
import com.zeapo.pwdstore.autofill.oreo.DirectoryStructure
import com.zeapo.pwdstore.autofill.oreo.FormOrigin
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import java.io.File
import java.nio.file.Paths
import java.util.Locale
import kotlinx.android.synthetic.main.activity_oreo_autofill_filter.*

@TargetApi(Build.VERSION_CODES.O)
class AutofillFilterView : AppCompatActivity() {

    companion object {
        private const val HEIGHT_PERCENTAGE = 0.9
        private const val WIDTH_PERCENTAGE = 0.75
        private const val DECRYPT_FILL_REQUEST_CODE = 1

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
    private lateinit var repositoryRoot: File
    private lateinit var directoryStructure: DirectoryStructure

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
        repositoryRoot = PasswordRepository.getRepositoryDirectory(this)
        directoryStructure = AutofillPreferences.directoryStructure(this)

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
                    when (directoryStructure) {
                        DirectoryStructure.FileBased -> {
                            title.text = item.file.relativeTo(item.rootDir).parent
                            subtitle.text = item.file.nameWithoutExtension
                        }
                        DirectoryStructure.DirectoryBased -> {
                            title.text =
                                item.file.relativeTo(item.rootDir).parentFile?.parent ?: "/INVALID"
                            subtitle.text =
                                Paths.get(item.file.parentFile.name, item.file.nameWithoutExtension)
                                    .toString()
                        }
                    }
                }
                onClick { decryptAndFill(item) }
            }
        }
        rvPassword.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        search.addTextChangedListener { recursiveFilter(it.toString(), strict = false) }
        val initialFilter =
            formOrigin.getPrettyIdentifier(applicationContext, untrusted = false)
        search.setText(initialFilter, TextView.BufferType.EDITABLE)
        recursiveFilter(initialFilter, strict = formOrigin is FormOrigin.Web)

        shouldMatch.text = getString(
            R.string.oreo_autofill_match_with,
            formOrigin.getPrettyIdentifier(applicationContext)
        )
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
            ), DECRYPT_FILL_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DECRYPT_FILL_REQUEST_CODE) {
            if (resultCode == RESULT_OK) setResult(RESULT_OK, data)
            finish()
        }
    }

    private fun File.matches(filter: String, strict: Boolean): Boolean {
        return if (strict) {
            val toMatch = directoryStructure.getIdentifierFor(this) ?: return false
            // In strict mode, we match
            // * the search term exactly,
            // * subdomains of the search term,
            // * or the search term plus an arbitrary protocol.
            toMatch == filter || toMatch.endsWith(".$filter") || toMatch.endsWith("://$filter")
        } else {
            val toMatch =
                "${relativeTo(repositoryRoot).path}/$nameWithoutExtension".toLowerCase(Locale.getDefault())
            toMatch.contains(filter.toLowerCase(Locale.getDefault()))
        }
    }

    private fun recursiveFilter(filter: String, dir: File? = null, strict: Boolean = true) {
        // on the root the pathStack is empty
        val passwordItems = if (dir == null) {
            PasswordRepository.getPasswords(repositoryRoot, sortOrder)
        } else {
            PasswordRepository.getPasswords(dir, repositoryRoot, sortOrder)
        }

        for (item in passwordItems) {
            if (item.type == PasswordItem.TYPE_CATEGORY) {
                recursiveFilter(filter, item.file, strict = strict)
            } else {
                // TODO: Implement fuzzy search if strict == false?
                val matches = item.file.matches(filter, strict = strict)
                val inAdapter = dataSource.contains(item)
                if (matches && !inAdapter) {
                    dataSource.add(item)
                } else if (!matches && inAdapter) {
                    dataSource.remove(item)
                }
            }
        }
    }
}
