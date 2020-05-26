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
import android.view.View
import android.view.autofill.AutofillManager
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.underline
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.ajalt.timberkt.e
import com.zeapo.pwdstore.FilterMode
import com.zeapo.pwdstore.ListMode
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.SearchMode
import com.zeapo.pwdstore.SearchableRepositoryAdapter
import com.zeapo.pwdstore.SearchableRepositoryViewModel
import com.zeapo.pwdstore.autofill.oreo.AutofillMatcher
import com.zeapo.pwdstore.autofill.oreo.AutofillPreferences
import com.zeapo.pwdstore.autofill.oreo.DirectoryStructure
import com.zeapo.pwdstore.autofill.oreo.FormOrigin
import com.zeapo.pwdstore.databinding.ActivityOreoAutofillFilterBinding
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.viewBinding

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
    private val binding by viewBinding(ActivityOreoAutofillFilterBinding::inflate)

    private val model: SearchableRepositoryViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
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
        updateSearch()
        setResult(RESULT_CANCELED)
    }

    private fun bindUI() {
        with(binding) {
            rvPassword.apply {
                adapter = SearchableRepositoryAdapter(
                    R.layout.oreo_autofill_filter_row,
                    ::PasswordViewHolder
                ) { item ->
                    val file = item.file.relativeTo(item.rootDir)
                    val pathToIdentifier = directoryStructure.getPathToIdentifierFor(file)
                    val identifier = directoryStructure.getIdentifierFor(file)
                    val accountPart = directoryStructure.getAccountPartFor(file)
                    check(identifier != null || accountPart != null) { "At least one of identifier and accountPart should always be non-null" }
                    title.text = if (identifier != null) {
                        buildSpannedString {
                            if (pathToIdentifier != null)
                                append("$pathToIdentifier/")
                            bold { underline { append(identifier) } }
                        }
                    } else {
                        accountPart
                    }
                    subtitle.apply {
                        if (identifier != null && accountPart != null) {
                            text = accountPart
                            visibility = View.VISIBLE
                        } else {
                            visibility = View.GONE
                        }
                    }
                }.onItemClicked { _, item ->
                    decryptAndFill(item)
                }
                layoutManager = LinearLayoutManager(context)
            }
            search.apply {
                val initialSearch =
                    formOrigin.getPrettyIdentifier(applicationContext, untrusted = false)
                setText(initialSearch, TextView.BufferType.EDITABLE)
                addTextChangedListener { updateSearch() }
            }
            strictDomainSearch.apply {
                visibility = if (formOrigin is FormOrigin.Web) View.VISIBLE else View.GONE
                isChecked = formOrigin is FormOrigin.Web
                setOnCheckedChangeListener { _, _ -> updateSearch() }
            }
            shouldMatch.text = getString(
                R.string.oreo_autofill_match_with,
                formOrigin.getPrettyIdentifier(applicationContext)
            )
            model.searchResult.observe(this@AutofillFilterView) { result ->
                val list = result.passwordItems
                (rvPassword.adapter as SearchableRepositoryAdapter).submitList(list) {
                    rvPassword.scrollToPosition(0)
                }
                // Switch RecyclerView out for a "no results" message if the new list is empty and
                // the message is not yet shown (and vice versa).
                if ((list.isEmpty() && rvPasswordSwitcher.nextView.id == rvPasswordEmpty.id) ||
                    (list.isNotEmpty() && rvPasswordSwitcher.nextView.id == rvPassword.id)
                ) {
                    rvPasswordSwitcher.showNext()
                }
            }
        }
    }

    private fun updateSearch() {
        model.search(
            binding.search.text.toString().trim(),
            filterMode = if (binding.strictDomainSearch.isChecked) FilterMode.StrictDomain else FilterMode.Fuzzy,
            searchMode = SearchMode.RecursivelyInSubdirectories,
            listMode = ListMode.FilesOnly
        )
    }

    private fun decryptAndFill(item: PasswordItem) {
        if (binding.shouldClear.isChecked) AutofillMatcher.clearMatchesFor(applicationContext, formOrigin)
        if (binding.shouldMatch.isChecked) AutofillMatcher.addMatchFor(
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
