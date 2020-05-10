/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill

import android.accessibilityservice.AccessibilityService
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.preference.PreferenceManager
import com.github.ajalt.timberkt.Timber.tag
import com.github.ajalt.timberkt.e
import com.github.ajalt.timberkt.i
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.PasswordEntry
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.splitLines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.apache.commons.io.FileUtils
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URL
import java.util.ArrayList
import java.util.Locale

class AutofillService : AccessibilityService(), CoroutineScope by CoroutineScope(Dispatchers.Default) {
    private var serviceConnection: OpenPgpServiceConnection? = null
    private var settings: SharedPreferences? = null
    private var info: AccessibilityNodeInfo? = null // the original source of the event (the edittext field)
    private var items: ArrayList<File> = arrayListOf() // password choices
    private var lastWhichItem: Int = 0
    private var dialog: AlertDialog? = null
    private var window: AccessibilityWindowInfo? = null
    private var resultData: Intent? = null // need the intent which contains results from user interaction
    private var packageName: CharSequence? = null
    private var ignoreActionFocus = false
    private var webViewTitle: String? = null
    private var webViewURL: String? = null
    private var lastPassword: PasswordEntry? = null
    private var lastPasswordMaxDate: Long = 0

    fun setResultData(data: Intent) {
        resultData = data
    }

    fun setPickedPassword(path: String) {
        items.add(File("${PasswordRepository.getRepositoryDirectory(applicationContext)}/$path.gpg"))
        bindDecryptAndVerify()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        cancel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceConnection = OpenPgpServiceConnection(this@AutofillService, "org.sufficientlysecure.keychain")
        serviceConnection!!.bindToService()
        settings = PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // remove stored password from cache
        if (lastPassword != null && System.currentTimeMillis() > lastPasswordMaxDate) {
            lastPassword = null
        }

        // if returning to the source app from a successful AutofillActivity
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.packageName != null && event.packageName == packageName &&
            resultData != null) {
            bindDecryptAndVerify()
        }

        // look for webView and trigger accessibility events if window changes
        // or if page changes in chrome
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                event.packageName != null &&
                (event.packageName == "com.android.chrome" || event.packageName == "com.android.browser"))) {
            // there is a chance for getRootInActiveWindow() to return null at any time. save it.
            try {
                val root = rootInActiveWindow
                webViewTitle = searchWebView(root)
                webViewURL = null
                if (webViewTitle != null) {
                    var nodes = root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
                    if (nodes.isEmpty()) {
                        nodes = root.findAccessibilityNodeInfosByViewId("com.android.browser:id/url")
                    }
                    for (node in nodes)
                        if (node.text != null) {
                            try {
                                webViewURL = URL(node.text.toString()).host
                            } catch (e: MalformedURLException) {
                                if (e.toString().contains("Protocol not found")) {
                                    try {
                                        webViewURL = URL("http://" + node.text.toString()).host
                                    } catch (ignored: MalformedURLException) {
                                    }
                                }
                            }
                        }
                }
            } catch (e: Exception) {
                // sadly we were unable to access the data we wanted
                return
            }
        }

        // nothing to do if field is keychain app or system ui
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.packageName != null && event.packageName == "org.sufficientlysecure.keychain" ||
            event.packageName != null && event.packageName == "com.android.systemui") {
            dismissDialog()
            return
        }

        if (!event.isPassword) {
            if (lastPassword != null && event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED && event.source.isEditable) {
                showPasteUsernameDialog(event.source, lastPassword!!)
                return
            } else {
                // nothing to do if not password field focus
                dismissDialog()
                return
            }
        }

        if (dialog != null && dialog!!.isShowing) {
            // the current dialog must belong to this window; ignore clicks on this password field
            // why handle clicks at all then? some cases e.g. Paypal there is no initial focus event
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                return
            }
            // if it was not a click, the field was refocused or another field was focused; recreate
            dialog!!.dismiss()
            dialog = null
        }

        // ignore the ACTION_FOCUS from decryptAndVerify otherwise dialog will appear after Fill
        if (ignoreActionFocus) {
            ignoreActionFocus = false
            return
        }

        // need to request permission before attempting to draw dialog
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return
        }

        // we are now going to attempt to fill, save AccessibilityNodeInfo for later in decryptAndVerify
        // (there should be a proper way to do this, although this seems to work 90% of the time)
        info = event.source
        if (info == null) return

        // save the dialog's corresponding window so we can use getWindows() in dismissDialog
        window = info!!.window

        val packageName: String
        val appName: String
        val isWeb: Boolean

        // Match with the app if a webview was not found or one was found but
        // there's no title or url to go by
        if (webViewTitle == null || webViewTitle == "" && webViewURL == null) {
            if (info!!.packageName == null) return
            packageName = info!!.packageName.toString()

            // get the app name and find a corresponding password
            val packageManager = packageManager
            val applicationInfo: ApplicationInfo? = try {
                packageManager.getApplicationInfo(event.packageName.toString(), 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            appName = (if (applicationInfo != null) packageManager.getApplicationLabel(applicationInfo) else "").toString()

            isWeb = false

            setAppMatchingPasswords(appName, packageName)
        } else {
            // now we may have found a title but webViewURL could be null
            // we set packagename so that we can find the website setting entry
            packageName = setWebMatchingPasswords(webViewTitle!!, webViewURL)
            appName = packageName
            isWeb = true
        }

        // if autofill_always checked, show dialog even if no matches (automatic
        // or otherwise)
        if (items.isEmpty() && !settings!!.getBoolean("autofill_always", false)) {
            return
        }
        showSelectPasswordDialog(packageName, appName, isWeb)
    }

    private fun searchWebView(source: AccessibilityNodeInfo?, depth: Int = 10): String? {
        if (source == null || depth == 0) {
            return null
        }
        for (i in 0 until source.childCount) {
            val u = source.getChild(i) ?: continue
            if (u.className != null && u.className == "android.webkit.WebView") {
                return if (u.contentDescription != null) {
                    u.contentDescription.toString()
                } else ""
            }
            val webView = searchWebView(u, depth - 1)
            if (webView != null) {
                return webView
            }
            u.recycle()
        }
        return null
    }

    // dismiss the dialog if the window has changed
    private fun dismissDialog() {
        val dismiss = !windows.contains(window)
        if (dismiss && dialog != null && dialog!!.isShowing) {
            dialog!!.dismiss()
            dialog = null
        }
    }

    private fun setWebMatchingPasswords(webViewTitle: String, webViewURL: String?): String {
        // Return the URL needed to open the corresponding Settings.
        var settingsURL = webViewURL

        // if autofill_default is checked and prefs.getString DNE, 'Automatically match with password'/"first" otherwise "never"
        val defValue = if (settings!!.getBoolean("autofill_default", true)) "/first" else "/never"
        val prefs: SharedPreferences = getSharedPreferences("autofill_web", Context.MODE_PRIVATE)
        var preference: String

        preference = defValue
        if (webViewURL != null) {
            val webViewUrlLowerCase = webViewURL.toLowerCase(Locale.ROOT)
            val prefsMap = prefs.all
            for (key in prefsMap.keys) {
                // for websites unlike apps there can be blank preference of "" which
                // means use default, so ignore it.
                val value = prefs.getString(key, null)
                val keyLowerCase = key.toLowerCase(Locale.ROOT)
                if (value != null && value != "" &&
                    (webViewUrlLowerCase.contains(keyLowerCase) || keyLowerCase.contains(webViewUrlLowerCase))) {
                    preference = value
                    settingsURL = key
                }
            }
        }

        when (preference) {
            "/first" -> {
                if (!PasswordRepository.isInitialized) {
                    PasswordRepository.initialize(this)
                }
                items = searchPasswords(PasswordRepository.getRepositoryDirectory(this), webViewTitle)
            }
            "/never" -> items = ArrayList()
            else -> getPreferredPasswords(preference)
        }

        return settingsURL!!
    }

    private fun setAppMatchingPasswords(appName: String, packageName: String) {
        // if autofill_default is checked and prefs.getString DNE, 'Automatically match with password'/"first" otherwise "never"
        val defValue = if (settings!!.getBoolean("autofill_default", true)) "/first" else "/never"
        val prefs: SharedPreferences = getSharedPreferences("autofill", Context.MODE_PRIVATE)
        val preference: String?

        preference = prefs.getString(packageName, defValue) ?: defValue

        when (preference) {
            "/first" -> {
                if (!PasswordRepository.isInitialized) {
                    PasswordRepository.initialize(this)
                }
                items = searchPasswords(PasswordRepository.getRepositoryDirectory(this), appName)
            }
            "/never" -> items = ArrayList()
            else -> getPreferredPasswords(preference)
        }
    }

    // Put the newline separated list of passwords from the SharedPreferences
    // file into the items list.
    private fun getPreferredPasswords(preference: String) {
        if (!PasswordRepository.isInitialized) {
            PasswordRepository.initialize(this)
        }
        val preferredPasswords = preference.splitLines()
        items = ArrayList()
        for (password in preferredPasswords) {
            val path = PasswordRepository.getRepositoryDirectory(applicationContext).toString() + "/" + password + ".gpg"
            if (File(path).exists()) {
                items.add(File(path))
            }
        }
    }

    private fun searchPasswords(path: File?, appName: String): ArrayList<File> {
        val passList = PasswordRepository.getFilesList(path)

        if (passList.size == 0) return ArrayList()

        val items = ArrayList<File>()

        for (file in passList) {
            if (file.isFile) {
                if (!file.isHidden && appName.toLowerCase(Locale.ROOT).contains(file.name.toLowerCase(Locale.ROOT).replace(".gpg", ""))) {
                    items.add(file)
                }
            } else {
                if (!file.isHidden) {
                    items.addAll(searchPasswords(file, appName))
                }
            }
        }
        return items
    }

    private fun showPasteUsernameDialog(node: AccessibilityNodeInfo, password: PasswordEntry) {
        if (dialog != null) {
            dialog!!.dismiss()
            dialog = null
        }

        val builder = MaterialAlertDialogBuilder(this, R.style.AppTheme_Dialog)
        builder.setNegativeButton(R.string.dialog_cancel) { _, _ ->
            dialog!!.dismiss()
            dialog = null
        }
        builder.setPositiveButton(R.string.autofill_paste) { _, _ ->
            pasteText(node, password.username)
            dialog!!.dismiss()
            dialog = null
        }
        builder.setMessage(getString(R.string.autofill_paste_username, password.username))

        dialog = builder.create()
        require(dialog != null) { "Dialog should not be null at this stage" }
        dialog!!.window!!.apply {
            setDialogType(this)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog!!.show()
    }

    private fun showSelectPasswordDialog(packageName: String, appName: String, isWeb: Boolean) {
        if (dialog != null) {
            dialog!!.dismiss()
            dialog = null
        }

        val builder = MaterialAlertDialogBuilder(this, R.style.AppTheme_Dialog)
        builder.setNegativeButton(R.string.dialog_cancel) { _, _ ->
            dialog!!.dismiss()
            dialog = null
        }
        builder.setNeutralButton("Settings") { _, _ ->
            // TODO make icon? gear?
            // the user will have to return to the app themselves.
            val intent = Intent(this@AutofillService, AutofillPreferenceActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra("packageName", packageName)
            intent.putExtra("appName", appName)
            intent.putExtra("isWeb", isWeb)
            startActivity(intent)
        }

        // populate the dialog items, always with pick + pick and match. Could
        // make it optional (or make height a setting for the same effect)
        val itemNames = arrayOfNulls<CharSequence>(items.size + 2)
        val passwordDirectory = PasswordRepository.getRepositoryDirectory(applicationContext).toString()
        val autofillFullPath = settings!!.getBoolean("autofill_full_path", false)
        for (i in items.indices) {
            if (autofillFullPath) {
                itemNames[i] = items[i].path.replace(".gpg", "")
                    .replace("$passwordDirectory/", "")
            } else {
                itemNames[i] = items[i].name.replace(".gpg", "")
            }
        }
        itemNames[items.size] = getString(R.string.autofill_pick)
        itemNames[items.size + 1] = getString(R.string.autofill_pick_and_match)
        builder.setItems(itemNames) { _, which ->
            lastWhichItem = which
            when {
                which < items.size -> bindDecryptAndVerify()
                which == items.size -> {
                    val intent = Intent(this@AutofillService, AutofillActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    intent.putExtra("pick", true)
                    startActivity(intent)
                }
                else -> {
                    lastWhichItem-- // will add one element to items, so lastWhichItem=items.size()+1
                    val intent = Intent(this@AutofillService, AutofillActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    intent.putExtra("pickMatchWith", true)
                    intent.putExtra("packageName", packageName)
                    intent.putExtra("isWeb", isWeb)
                    startActivity(intent)
                }
            }
        }

        dialog = builder.create()
        dialog?.window?.apply {
            setDialogType(this)
            val density = context.resources.displayMetrics.density
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // arbitrary non-annoying size
            setLayout((340 * density).toInt(), WRAP_CONTENT)
        }
        dialog?.show()
    }

    @Suppress("DEPRECATION")
    private fun setDialogType(window: Window) {
        window.setType(if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        else
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        )
    }

    override fun onInterrupt() {}

    private fun bindDecryptAndVerify() {
        if (serviceConnection!!.service == null) {
            // the service was disconnected, need to bind again
            // give it a listener and in the callback we will decryptAndVerify
            serviceConnection = OpenPgpServiceConnection(this@AutofillService, "org.sufficientlysecure.keychain", OnBoundListener())
            serviceConnection!!.bindToService()
        } else {
            decryptAndVerify()
        }
    }

    private fun decryptAndVerify() = launch {
        packageName = info!!.packageName
        val data: Intent
        if (resultData == null) {
            data = Intent()
            data.action = OpenPgpApi.ACTION_DECRYPT_VERIFY
        } else {
            data = resultData!!
            resultData = null
        }

        var inputStream: InputStream? = null
        withContext(Dispatchers.IO) {
            try {
                inputStream = FileUtils.openInputStream(items[lastWhichItem])
            } catch (e: IOException) {
                e.printStackTrace()
                cancel("", e)
            }
        }

        val os = ByteArrayOutputStream()

        val api = OpenPgpApi(this@AutofillService, serviceConnection!!.service!!)
        val result = api.executeApi(data, inputStream, os)
        when (result?.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            OpenPgpApi.RESULT_CODE_SUCCESS -> {
                try {
                    var entry: PasswordEntry? = null
                    withContext(Dispatchers.IO) {
                        entry = PasswordEntry(os)
                    }
                    withContext(Dispatchers.Main) { pasteText(info!!, entry?.password) }
                    // save password entry for pasting the username as well
                    if (entry?.hasUsername() == true) {
                        lastPassword = entry
                        val ttl = Integer.parseInt(settings!!.getString("general_show_time", "45")!!)
                        withContext(Dispatchers.Main) { Toast.makeText(applicationContext, getString(R.string.autofill_toast_username, ttl), Toast.LENGTH_LONG).show() }
                        lastPasswordMaxDate = System.currentTimeMillis() + ttl * 1000L
                    }
                } catch (e: UnsupportedEncodingException) {
                    tag(Constants.TAG).e(e)
                }
            }
            OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                tag("PgpHandler").i { "RESULT_CODE_USER_INTERACTION_REQUIRED" }
                val pi = result.getParcelableExtra<PendingIntent>(OpenPgpApi.RESULT_INTENT)
                // need to start a blank activity to call startIntentSenderForResult
                val intent = Intent(applicationContext, AutofillActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.putExtra("pending_intent", pi)
                startActivity(intent)
            }
            OpenPgpApi.RESULT_CODE_ERROR -> {
                val error = result.getParcelableExtra<OpenPgpError>(OpenPgpApi.RESULT_ERROR)
                if (error != null) {
                    withContext(Dispatchers.Main) { Toast.makeText(applicationContext, "Error from OpenKeyChain : ${error.message}", Toast.LENGTH_LONG).show() }
                    tag(Constants.TAG).e { "onError getErrorId: ${error.errorId}" }
                    tag(Constants.TAG).e { "onError getMessage: ${error.message}" }
                }
            }
        }
    }

    private fun pasteText(node: AccessibilityNodeInfo, text: String?) {
        // if the user focused on something else, take focus back
        // but this will open another dialog...hack to ignore this
        // & need to ensure performAction correct (i.e. what is info now?)
        ignoreActionFocus = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = bundleOf(Pair(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text))
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
    }

    internal object Constants {
        const val TAG = "Keychain"
    }

    private inner class OnBoundListener : OpenPgpServiceConnection.OnBound {
        override fun onBound(service: IOpenPgpService2) {
            decryptAndVerify()
        }

        override fun onError(e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        var instance: AutofillService? = null
            private set
    }
}
