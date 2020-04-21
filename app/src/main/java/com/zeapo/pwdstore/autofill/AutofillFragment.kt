/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.PasswordStore
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.resolveAttribute
import com.zeapo.pwdstore.utils.splitLines

class AutofillFragment : DialogFragment() {
    private var adapter: ArrayAdapter<String>? = null
    private var isWeb: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        // this fragment is only created from the settings page (AutofillPreferenceActivity)
        // need to interact with the recyclerAdapter which is a member of activity
        val callingActivity = requireActivity() as AutofillPreferenceActivity
        val inflater = callingActivity.layoutInflater
        val args = requireNotNull(arguments)

        @SuppressLint("InflateParams") val view = inflater.inflate(R.layout.fragment_autofill, null)

        builder.setView(view)

        val packageName = args.getString("packageName")
        val appName = args.getString("appName")
        isWeb = args.getBoolean("isWeb")

        // set the dialog icon and title or webURL editText
        val iconPackageName: String?
        if (!isWeb) {
            iconPackageName = packageName
            builder.setTitle(appName)
            view.findViewById<View>(R.id.webURL).visibility = View.GONE
        } else {
            val browserIntent = Intent("android.intent.action.VIEW", Uri.parse("http://"))
            val resolveInfo = requireContext().packageManager
                    .resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            iconPackageName = resolveInfo?.activityInfo?.packageName
            builder.setTitle("Website")
            (view.findViewById<View>(R.id.webURL) as EditText).setText(packageName
                    ?: "com.android.browser")
        }
        try {
            if (iconPackageName != null) {
                builder.setIcon(callingActivity.packageManager.getApplicationIcon(iconPackageName))
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        // set up the listview now for items added by button/from preferences
        adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, android.R.id.text1) {
            // set text color to black because default is white...
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val textView = super.getView(position, convertView, parent) as AppCompatTextView
                textView.setTextColor(requireContext().resolveAttribute(android.R.attr.textColor))
                return textView
            }
        }
        (view.findViewById<View>(R.id.matched) as ListView).adapter = adapter
        // delete items by clicking them
        (view.findViewById<View>(R.id.matched) as ListView).onItemClickListener =
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    adapter!!.remove(adapter!!.getItem(position))
                }

        // set the existing preference, if any
        val prefs: SharedPreferences = if (!isWeb) {
            callingActivity.applicationContext.getSharedPreferences("autofill", Context.MODE_PRIVATE)
        } else {
            callingActivity.applicationContext.getSharedPreferences("autofill_web", Context.MODE_PRIVATE)
        }
        when (val preference = prefs.getString(packageName, "")) {
            "" -> (view.findViewById<View>(R.id.use_default) as RadioButton).toggle()
            "/first" -> (view.findViewById<View>(R.id.first) as RadioButton).toggle()
            "/never" -> (view.findViewById<View>(R.id.never) as RadioButton).toggle()
            else -> {
                (view.findViewById<View>(R.id.match) as RadioButton).toggle()
                // trim to remove the last blank element
                adapter!!.addAll(*preference!!.trim { it <= ' ' }.splitLines())
            }
        }

        // add items with the + button
        val matchPassword = { _: View ->
            (view.findViewById<View>(R.id.match) as RadioButton).toggle()
            val intent = Intent(activity, PasswordStore::class.java)
            intent.putExtra("matchWith", true)
            startActivityForResult(intent, MATCH_WITH)
        }
        view.findViewById<View>(R.id.matchButton).setOnClickListener(matchPassword)

        // write to preferences when OK clicked
        builder.setPositiveButton(R.string.dialog_ok) { _, _ -> }
        builder.setNegativeButton(R.string.dialog_cancel, null)
        if (isWeb) {
            builder.setNeutralButton(R.string.autofill_apps_delete) { _, _ ->
                if (callingActivity.recyclerAdapter != null &&
                        packageName != null && packageName != "") {
                    prefs.edit {
                        remove(packageName)
                        callingActivity.recyclerAdapter?.removeWebsite(packageName)
                    }
                }
            }
        }
        return builder.create()
    }

    // need to the onClick here for buttons to dismiss dialog only when wanted
    override fun onStart() {
        super.onStart()
        val ad = dialog as? AlertDialog
        if (ad != null) {
            val positiveButton = ad.getButton(Dialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val callingActivity = requireActivity() as AutofillPreferenceActivity
                val dialog = requireDialog()
                val args = requireNotNull(arguments)

                val prefs: SharedPreferences = if (!isWeb) {
                    callingActivity.applicationContext.getSharedPreferences("autofill", Context.MODE_PRIVATE)
                } else {
                    callingActivity.applicationContext.getSharedPreferences("autofill_web", Context.MODE_PRIVATE)
                }

                var packageName = args.getString("packageName", "")
                if (isWeb) {
                    // handle some errors and don't dismiss the dialog
                    val webURL = dialog.findViewById<EditText>(R.id.webURL)

                    packageName = webURL.text.toString()

                    if (packageName == "") {
                        webURL.error = "URL cannot be blank"
                        return@setOnClickListener
                    }
                    val oldPackageName = args.getString("packageName", "")
                    if (oldPackageName != packageName && prefs.all.containsKey(packageName)) {
                        webURL.error = "URL already exists"
                        return@setOnClickListener
                    }
                }

                // write to preferences accordingly
                prefs.edit {
                    val radioGroup = dialog.findViewById<RadioGroup>(R.id.autofill_radiogroup)
                    when (radioGroup.checkedRadioButtonId) {
                        R.id.use_default -> if (!isWeb) {
                            remove(packageName)
                        } else {
                            putString(packageName, "")
                        }
                        R.id.first -> putString(packageName, "/first")
                        R.id.never -> putString(packageName, "/never")
                        else -> {
                            val paths = StringBuilder()
                            for (i in 0 until adapter!!.count) {
                                paths.append(adapter!!.getItem(i))
                                if (i != adapter!!.count) {
                                    paths.append("\n")
                                }
                            }
                            putString(packageName, paths.toString())
                        }
                    }
                }

                // notify the recycler adapter if it is loaded
                callingActivity.recyclerAdapter?.apply {
                    val position: Int
                    if (!isWeb) {
                        val appName = args.getString("appName", "")
                        position = getPosition(appName)
                        notifyItemChanged(position)
                    } else {
                        position = getPosition(packageName)
                        when (val oldPackageName = args.getString("packageName", "")) {
                            packageName -> notifyItemChanged(position)
                            "" -> addWebsite(packageName)
                            else -> {
                                prefs.edit { remove(oldPackageName) }
                                updateWebsite(oldPackageName, packageName)
                            }
                        }
                    }
                }
                dismiss()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == AppCompatActivity.RESULT_OK && data != null) {
            adapter!!.add(data.getStringExtra("path"))
        }
    }

    companion object {
        private const val MATCH_WITH = 777
    }
}
