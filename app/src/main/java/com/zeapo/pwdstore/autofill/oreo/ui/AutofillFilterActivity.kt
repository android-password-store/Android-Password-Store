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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.ajalt.timberkt.e
import com.zeapo.pwdstore.DelegatedSearchableRepositoryAdapter
import com.zeapo.pwdstore.FilterMode
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.SearchMode
import com.zeapo.pwdstore.SearchableRepositoryViewModel
import com.zeapo.pwdstore.autofill.oreo.AutofillMatcher
import com.zeapo.pwdstore.autofill.oreo.AutofillPreferences
import com.zeapo.pwdstore.autofill.oreo.DirectoryStructure
import com.zeapo.pwdstore.autofill.oreo.FormOrigin
import com.zeapo.pwdstore.utils.PasswordItem
import java.nio.file.Paths
import kotlinx.android.synthetic.main.activity_oreo_autofill_filter.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

@FlowPreview
@ExperimentalCoroutinesApi
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

    private lateinit var formOrigin: FormOrigin
    private lateinit var directoryStructure: DirectoryStructure

    private val model: SearchableRepositoryViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory(application)
    }

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
        directoryStructure = AutofillPreferences.directoryStructure(this)

        supportActionBar?.hide()
        bindUI()
        setResult(RESULT_CANCELED)
    }

    private fun bindUI() {
        val searchableAdapter = DelegatedSearchableRepositoryAdapter(
            R.layout.oreo_autofill_filter_row,
            ::PasswordViewHolder
        ) { item ->
            when (directoryStructure) {
                DirectoryStructure.FileBased -> {
                    title.text = item.file.relativeTo(item.rootDir).parent
                    subtitle.text = item.file.nameWithoutExtension
                }
                DirectoryStructure.DirectoryBased -> {
                    title.text = item.file.relativeTo(item.rootDir).parentFile?.parent ?: "/INVALID"
                    subtitle.text =
                        Paths.get(item.file.parentFile.name, item.file.nameWithoutExtension)
                            .toString()
                }
            }
            itemView.setOnClickListener { decryptAndFill(item) }
        }
        rvPassword.apply {
            adapter = searchableAdapter
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        val initialFilter = formOrigin.getPrettyIdentifier(applicationContext, untrusted = false)
        search.setText(initialFilter, TextView.BufferType.EDITABLE)
        val filterMode =
            if (formOrigin is FormOrigin.Web) FilterMode.StrictDomain else FilterMode.Fuzzy
        model.search(
            initialFilter,
            filterMode = filterMode,
            searchMode = SearchMode.Recursive,
            listFilesOnly = true
        )
        search.addTextChangedListener {
            model.search(
                it.toString(),
                filterMode = FilterMode.Fuzzy,
                searchMode = SearchMode.Recursive,
                listFilesOnly = true
            )
        }
        model.passwordItemsList.observe(
            this,
            Observer { list ->
                searchableAdapter.submitList(list)
                if ((list.isEmpty() && rvPasswordSwitcher.nextView.id == rvPasswordEmpty.id) ||
                    (list.isNotEmpty() && rvPasswordSwitcher.nextView.id == rvPassword.id))
                    rvPasswordSwitcher.showNext()
            })

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
}
