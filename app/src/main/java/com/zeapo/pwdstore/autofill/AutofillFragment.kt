package com.zeapo.pwdstore.autofill

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.DialogFragment
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zeapo.pwdstore.PasswordStore
import com.zeapo.pwdstore.R

class AutofillFragment : DialogFragment() {
    private var adapter: ArrayAdapter<String>? = null
    private var isWeb: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val builder = AlertDialog.Builder(activity)
        // this fragment is only created from the settings page (AutofillPreferenceActivity)
        // need to interact with the recyclerAdapter which is a member of activity
        val callingActivity = activity as AutofillPreferenceActivity
        val inflater = callingActivity.layoutInflater

        @SuppressLint("InflateParams") val view = inflater.inflate(R.layout.fragment_autofill, null)

        builder.setView(view)

        val packageName = arguments.getString("packageName")
        val appName = arguments.getString("appName")
        isWeb = arguments.getBoolean("isWeb")

        // set the dialog icon and title or webURL editText
        val iconPackageName: String?
        if (!isWeb) {
            iconPackageName = packageName
            builder.setTitle(appName)
            view.findViewById<View>(R.id.webURL).visibility = View.GONE
        } else {
            iconPackageName = "com.android.browser"
            builder.setTitle("Website")
            (view.findViewById<View>(R.id.webURL) as EditText).setText(packageName)
        }
        try {
            builder.setIcon(callingActivity.packageManager.getApplicationIcon(iconPackageName))
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        // set up the listview now for items added by button/from preferences
        adapter = object : ArrayAdapter<String>(activity.applicationContext, android.R.layout.simple_list_item_1, android.R.id.text1) {
            // set text color to black because default is white...
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val textView = super.getView(position, convertView, parent) as TextView
                textView.setTextColor(ContextCompat.getColor(context, R.color.grey_black_1000))
                return textView
            }
        }
        (view.findViewById<View>(R.id.matched) as ListView).adapter = adapter
        // delete items by clicking them
        (view.findViewById<View>(R.id.matched) as ListView).setOnItemClickListener { _, _, position, _ -> adapter!!.remove(adapter!!.getItem(position)) }

        // set the existing preference, if any
        val prefs: SharedPreferences = if (!isWeb) {
            activity.applicationContext.getSharedPreferences("autofill", Context.MODE_PRIVATE)
        } else {
            activity.applicationContext.getSharedPreferences("autofill_web", Context.MODE_PRIVATE)
        }
        when (val preference = prefs.getString(packageName, "")) {
            "" -> (view.findViewById<View>(R.id.use_default) as RadioButton).toggle()
            "/first" -> (view.findViewById<View>(R.id.first) as RadioButton).toggle()
            "/never" -> (view.findViewById<View>(R.id.never) as RadioButton).toggle()
            else -> {
                (view.findViewById<View>(R.id.match) as RadioButton).toggle()
                // trim to remove the last blank element
                adapter!!.addAll(*preference!!.trim { it <= ' ' }.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
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
        val editor = prefs.edit()
        if (isWeb) {
            builder.setNeutralButton(R.string.autofill_apps_delete) { _, _ ->
                if (callingActivity.recyclerAdapter != null
                        && packageName != null && packageName != "") {
                    editor.remove(packageName)
                    callingActivity.recyclerAdapter.removeWebsite(packageName)
                    editor.apply()
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
                val callingActivity = activity as AutofillPreferenceActivity
                val dialog = dialog

                val prefs: SharedPreferences = if (!isWeb) {
                    activity.applicationContext.getSharedPreferences("autofill", Context.MODE_PRIVATE)
                } else {
                    activity.applicationContext.getSharedPreferences("autofill_web", Context.MODE_PRIVATE)
                }
                val editor = prefs.edit()

                var packageName = arguments.getString("packageName", "")
                if (isWeb) {
                    // handle some errors and don't dismiss the dialog
                    val webURL = dialog.findViewById<EditText>(R.id.webURL)

                    packageName = webURL.text.toString()

                    if (packageName == "") {
                        webURL.error = "URL cannot be blank"
                        return@setOnClickListener
                    }
                    val oldPackageName = arguments.getString("packageName", "")
                    if (oldPackageName != packageName && prefs.all.containsKey(packageName)) {
                        webURL.error = "URL already exists"
                        return@setOnClickListener
                    }
                }

                // write to preferences accordingly
                val radioGroup = dialog.findViewById<RadioGroup>(R.id.autofill_radiogroup)
                when (radioGroup.checkedRadioButtonId) {
                    R.id.use_default -> if (!isWeb) {
                        editor.remove(packageName)
                    } else {
                        editor.putString(packageName, "")
                    }
                    R.id.first -> editor.putString(packageName, "/first")
                    R.id.never -> editor.putString(packageName, "/never")
                    else -> {
                        val paths = StringBuilder()
                        for (i in 0 until adapter!!.count) {
                            paths.append(adapter!!.getItem(i))
                            if (i != adapter!!.count) {
                                paths.append("\n")
                            }
                        }
                        editor.putString(packageName, paths.toString())
                    }
                }
                editor.apply()

                // notify the recycler adapter if it is loaded
                if (callingActivity.recyclerAdapter != null) {
                    val position: Int
                    if (!isWeb) {
                        val appName = arguments.getString("appName", "")
                        position = callingActivity.recyclerAdapter.getPosition(appName)
                        callingActivity.recyclerAdapter.notifyItemChanged(position)
                    } else {
                        position = callingActivity.recyclerAdapter.getPosition(packageName)
                        when (val oldPackageName = arguments.getString("packageName", "")) {
                            packageName -> callingActivity.recyclerAdapter.notifyItemChanged(position)
                            "" -> callingActivity.recyclerAdapter.addWebsite(packageName)
                            else -> {
                                editor.remove(oldPackageName)
                                callingActivity.recyclerAdapter.updateWebsite(oldPackageName, packageName)
                            }
                        }
                    }
                }

                dismiss()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (resultCode == AppCompatActivity.RESULT_OK) {
            adapter!!.add(data.getStringExtra("path"))
        }
    }

    companion object {
        private const val MATCH_WITH = 777
    }
}
