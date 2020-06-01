/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.crypto

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.method.PasswordTransformationMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.github.ajalt.timberkt.Timber.e
import com.github.ajalt.timberkt.Timber.i
import com.github.ajalt.timberkt.Timber.tag
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.ClipboardService
import com.zeapo.pwdstore.PasswordEntry
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.autofill.oreo.AutofillPreferences
import com.zeapo.pwdstore.autofill.oreo.DirectoryStructure
import com.zeapo.pwdstore.ui.dialogs.PasswordGeneratorDialogFragment
import com.zeapo.pwdstore.ui.dialogs.XkPasswordGeneratorDialogFragment
import kotlinx.android.synthetic.main.decrypt_layout.extra_content
import kotlinx.android.synthetic.main.decrypt_layout.extra_content_container
import kotlinx.android.synthetic.main.decrypt_layout.password_category
import kotlinx.android.synthetic.main.decrypt_layout.password_file
import kotlinx.android.synthetic.main.decrypt_layout.password_last_changed
import kotlinx.android.synthetic.main.decrypt_layout.password_text
import kotlinx.android.synthetic.main.decrypt_layout.password_text_container
import kotlinx.android.synthetic.main.decrypt_layout.username_text
import kotlinx.android.synthetic.main.decrypt_layout.username_text_container
import kotlinx.android.synthetic.main.password_creation_activity.crypto_extra_edit
import kotlinx.android.synthetic.main.password_creation_activity.crypto_password_category
import kotlinx.android.synthetic.main.password_creation_activity.crypto_password_edit
import kotlinx.android.synthetic.main.password_creation_activity.encrypt_username
import kotlinx.android.synthetic.main.password_creation_activity.generate_password
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.ACTION_DECRYPT_VERIFY
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_CODE
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_CODE_ERROR
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_CODE_SUCCESS
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_CODE_USER_INTERACTION_REQUIRED
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_ERROR
import me.msfjarvis.openpgpktx.util.OpenPgpApi.Companion.RESULT_INTENT
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

class PgpActivity : AppCompatActivity(), OpenPgpServiceConnection.OnBound {
    private val clipboard by lazy { getSystemService<ClipboardManager>() }
    private var passwordEntry: PasswordEntry? = null
    private var api: OpenPgpApi? = null

    private var editName: String? = null
    private var editPass: String? = null
    private var editExtra: String? = null

    private val suggestedName by lazy { intent.getStringExtra("SUGGESTED_NAME") }
    private val suggestedPass by lazy { intent.getStringExtra("SUGGESTED_PASS") }
    private val suggestedExtra by lazy { intent.getStringExtra("SUGGESTED_EXTRA") }
    private val shouldGeneratePassword by lazy { intent.getBooleanExtra("GENERATE_PASSWORD", false) }

    private val operation: String by lazy { intent.getStringExtra("OPERATION") }
    private val repoPath: String by lazy { intent.getStringExtra("REPO_PATH") }

    private val fullPath: String by lazy { intent.getStringExtra("FILE_PATH") }
    private val name: String by lazy { getName(fullPath) }
    private val lastChangedString: CharSequence by lazy {
        getLastChangedString(
            intent.getLongExtra(
                "LAST_CHANGED_TIMESTAMP",
                -1L
            )
        )
    }
    private val relativeParentPath: String by lazy { getParentPath(fullPath, repoPath) }

    val settings: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val keyIDs get() = _keyIDs
    private var _keyIDs = emptySet<String>()
    private var serviceConnection: OpenPgpServiceConnection? = null
    private var delayTask: DelayShow? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            delayTask?.doOnPostExecute()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        tag(TAG)

        // some persistence
        _keyIDs = settings.getStringSet("openpgp_key_ids_set", null) ?: emptySet()
        val providerPackageName = settings.getString("openpgp_provider_list", "")

        if (TextUtils.isEmpty(providerPackageName)) {
            showSnackbar(resources.getString(R.string.provider_toast_text), Snackbar.LENGTH_LONG)
            val intent = Intent(this, UserPreference::class.java)
            startActivityForResult(intent, OPEN_PGP_BOUND)
        } else {
            // bind to service
            serviceConnection = OpenPgpServiceConnection(this, providerPackageName, this)
            serviceConnection?.bindToService()
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        when (operation) {
            "DECRYPT", "EDIT" -> {
                setContentView(R.layout.decrypt_layout)
                password_category.text = relativeParentPath
                password_file.text = name
                password_file.setOnLongClickListener {
                    val clipboard = clipboard ?: return@setOnLongClickListener false
                    val clip = ClipData.newPlainText("pgp_handler_result_pm", name)
                    clipboard.setPrimaryClip(clip)
                    showSnackbar(resources.getString(R.string.clipboard_copied_text))
                    true
                }

                password_last_changed.text = try {
                    resources.getString(R.string.last_changed, lastChangedString)
                } catch (e: RuntimeException) {
                    showSnackbar(getString(R.string.get_last_changed_failed))
                    ""
                }
            }
            "ENCRYPT" -> {
                setContentView(R.layout.password_creation_activity)

                generate_password?.setOnClickListener {
                    generatePassword()
                }

                title = getString(R.string.new_password_title)
                crypto_password_category.apply {
                    // If the activity has been provided with suggested info or is meant to generate
                    // a password, we allow the user to edit the path, otherwise we style the
                    // EditText like a TextView.
                    if (suggestedName != null || suggestedPass != null || shouldGeneratePassword) {
                        isEnabled = true
                    } else {
                        setBackgroundColor(getColor(android.R.color.transparent))
                    }
                    val path = getRelativePath(fullPath, repoPath)
                    // Keep empty path field visible if it is editable.
                    if (path.isEmpty() && !isEnabled)
                        visibility = View.GONE
                    else
                        setText(path)
                }
                suggestedName?.let { crypto_password_edit.setText(it) }
                // Allow the user to quickly switch between storing the username as the filename or
                // in the encrypted extras. This only makes sense if the directory structure is
                // FileBased.
                if (suggestedName != null &&
                    AutofillPreferences.directoryStructure(this) == DirectoryStructure.FileBased
                ) {
                    encrypt_username.apply {
                        visibility = View.VISIBLE
                        setOnClickListener {
                            if (isChecked) {
                                // User wants to enable username encryption, so we add it to the
                                // encrypted extras as the first line.
                                val username = crypto_password_edit.text!!.toString()
                                val extras = "username:$username\n${crypto_extra_edit.text!!}"

                                crypto_password_edit.setText("")
                                crypto_extra_edit.setText(extras)
                            } else {
                                // User wants to disable username encryption, so we extract the
                                // username from the encrypted extras and use it as the filename.
                                val entry = PasswordEntry("PASSWORD\n${crypto_extra_edit.text!!}")
                                val username = entry.username

                                // username should not be null here by the logic in
                                // updateEncryptUsernameState, but it could still happen due to
                                // input lag.
                                if (username != null) {
                                    crypto_password_edit.setText(username)
                                    crypto_extra_edit.setText(entry.extraContentWithoutUsername)
                                }
                            }
                            updateEncryptUsernameState()
                        }
                    }
                    crypto_password_edit.doOnTextChanged { _, _, _, _ -> updateEncryptUsernameState() }
                    crypto_extra_edit.doOnTextChanged { _, _, _, _ -> updateEncryptUsernameState() }
                    updateEncryptUsernameState()
                }
                suggestedPass?.let {
                    crypto_password_edit.setText(it)
                    crypto_password_edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                suggestedExtra?.let { crypto_extra_edit.setText(it) }
                if (shouldGeneratePassword) {
                    generatePassword()
                    crypto_password_edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }
        }
    }

    private fun updateEncryptUsernameState() {
        encrypt_username.apply {
            if (visibility != View.VISIBLE)
                return
            val hasUsernameInFileName = crypto_password_edit.text!!.toString().isNotBlank()
            // Use PasswordEntry to parse extras for username
            val entry = PasswordEntry("PLACEHOLDER\n${crypto_extra_edit.text!!}")
            val hasUsernameInExtras = entry.hasUsername()
            isEnabled = hasUsernameInFileName xor hasUsernameInExtras
            isChecked = hasUsernameInExtras
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(ACTION_CLEAR))
    }

    private fun generatePassword() {
        when (settings.getString("pref_key_pwgen_type", KEY_PWGEN_TYPE_CLASSIC)) {
            KEY_PWGEN_TYPE_CLASSIC -> PasswordGeneratorDialogFragment()
                .show(supportFragmentManager, "generator")
            KEY_PWGEN_TYPE_XKPASSWD -> XkPasswordGeneratorDialogFragment()
                .show(supportFragmentManager, "xkpwgenerator")
        }
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceConnection?.unbindFromService()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        // Do not use the value `operation` in this case as it is not valid when editing
        val menuId = when (intent.getStringExtra("OPERATION")) {
            "ENCRYPT", "EDIT" -> R.menu.pgp_handler_new_password
            "DECRYPT" -> R.menu.pgp_handler
            else -> R.menu.pgp_handler
        }

        menuInflater.inflate(menuId, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.crypto_cancel_add, android.R.id.home -> finish()
            R.id.copy_password -> copyPasswordToClipBoard()
            R.id.share_password_as_plaintext -> shareAsPlaintext()
            R.id.edit_password -> editPassword()
            R.id.crypto_confirm_add -> encrypt()
            R.id.crypto_confirm_add_and_copy -> encrypt(true)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * Shows a simple toast message
     */
    private fun showSnackbar(message: String, length: Int = Snackbar.LENGTH_SHORT) {
        runOnUiThread { Snackbar.make(findViewById(android.R.id.content), message, length).show() }
    }

    /**
     * Handle the case where OpenKeychain returns that it needs to interact with the user
     *
     * @param result The intent returned by OpenKeychain
     * @param requestCode The code we'd like to use to identify the behaviour
     */
    private fun handleUserInteractionRequest(result: Intent, requestCode: Int) {
        i { "RESULT_CODE_USER_INTERACTION_REQUIRED" }

        val pi: PendingIntent? = result.getParcelableExtra(RESULT_INTENT)
        try {
            this@PgpActivity.startIntentSenderFromChild(
                this@PgpActivity, pi?.intentSender, requestCode,
                null, 0, 0, 0
            )
        } catch (e: IntentSender.SendIntentException) {
            e(e) { "SendIntentException" }
        }
    }

    /**
     * Handle the error returned by OpenKeychain
     *
     * @param result The intent returned by OpenKeychain
     */
    private fun handleError(result: Intent) {
        // TODO show what kind of error it is
        /* For example:
         * No suitable key found -> no key in OpenKeyChain
         *
         * Check in open-pgp-lib how their definitions and error code
         */
        val error: OpenPgpError? = result.getParcelableExtra(RESULT_ERROR)
        if (error != null) {
            showSnackbar("Error from OpenKeyChain : " + error.message)
            e { "onError getErrorId: ${error.errorId}" }
            e { "onError getMessage: ${error.message}" }
        }
    }

    private fun initOpenPgpApi() {
        api = api ?: OpenPgpApi(this, serviceConnection!!.service!!)
    }

    private fun decryptAndVerify(receivedIntent: Intent? = null) {
        val data = receivedIntent ?: Intent()
        data.action = ACTION_DECRYPT_VERIFY

        val iStream = FileUtils.openInputStream(File(fullPath))
        val oStream = ByteArrayOutputStream()

        lifecycleScope.launch(IO) {
            api?.executeApiAsync(data, iStream, oStream) { result ->
                when (result?.getIntExtra(RESULT_CODE, RESULT_CODE_ERROR)) {
                    RESULT_CODE_SUCCESS -> {
                        try {
                            val showPassword = settings.getBoolean("show_password", true)
                            val showExtraContent = settings.getBoolean("show_extra_content", true)
                            val monoTypeface = Typeface.createFromAsset(assets, "fonts/sourcecodepro.ttf")
                            val entry = PasswordEntry(oStream)

                            passwordEntry = entry

                            if (intent.getStringExtra("OPERATION") == "EDIT") {
                                editPassword()
                                return@executeApiAsync
                            }

                            if (entry.password.isEmpty()) {
                                password_text_container.visibility = View.GONE
                            } else {
                                password_text_container.visibility = View.VISIBLE
                                password_text.setText(entry.password)
                                if (!showPassword) {
                                    password_text.transformationMethod = PasswordTransformationMethod.getInstance()
                                }
                                password_text_container.setOnClickListener { copyPasswordToClipBoard() }
                                password_text.setOnClickListener { copyPasswordToClipBoard() }
                            }

                            if (entry.hasExtraContent()) {
                                extra_content_container.visibility = View.VISIBLE
                                extra_content.typeface = monoTypeface
                                extra_content.setText(entry.extraContentWithoutUsername)
                                if (!showExtraContent) {
                                    extra_content.transformationMethod = PasswordTransformationMethod.getInstance()
                                }
                                extra_content_container.setOnClickListener { copyTextToClipboard(entry.extraContentWithoutUsername) }
                                extra_content.setOnClickListener { copyTextToClipboard(entry.extraContentWithoutUsername) }

                                if (entry.hasUsername()) {
                                    username_text.typeface = monoTypeface
                                    username_text.setText(entry.username)
                                    username_text_container.setEndIconOnClickListener { copyTextToClipboard(entry.username!!) }
                                    username_text_container.visibility = View.VISIBLE
                                } else {
                                    username_text_container.visibility = View.GONE
                                }
                            }

                            if (settings.getBoolean("copy_on_decrypt", true)) {
                                copyPasswordToClipBoard()
                            }
                        } catch (e: Exception) {
                            e(e) { "An Exception occurred" }
                        }
                    }
                    RESULT_CODE_USER_INTERACTION_REQUIRED -> handleUserInteractionRequest(result, REQUEST_DECRYPT)
                    RESULT_CODE_ERROR -> handleError(result)
                }
            }
        }
    }

    /**
     * Encrypts the password and the extra content
     */
    private fun encrypt(copy: Boolean = false) {
        editName = crypto_password_edit.text.toString().trim()
        editPass = crypto_password_edit.text.toString()
        editExtra = crypto_extra_edit.text.toString()

        if (editName?.isEmpty() == true) {
            showSnackbar(resources.getString(R.string.file_toast_text))
            return
        }

        if (editPass?.isEmpty() == true && editExtra?.isEmpty() == true) {
            showSnackbar(resources.getString(R.string.empty_toast_text))
            return
        }

        if (copy) {
            copyPasswordToClipBoard()
        }

        val data = Intent()
        data.action = OpenPgpApi.ACTION_ENCRYPT

        // EXTRA_KEY_IDS requires long[]
        val longKeys = keyIDs.map { it.toLong() }
        data.putExtra(OpenPgpApi.EXTRA_KEY_IDS, longKeys.toLongArray())
        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true)

        // TODO Check if we could use PasswordEntry to generate the file
        val content = "$editPass\n$editExtra"
        val iStream = ByteArrayInputStream(content.toByteArray(Charset.forName("UTF-8")))
        val oStream = ByteArrayOutputStream()

        val path = when {
            intent.getBooleanExtra("fromDecrypt", false) -> fullPath
            // If we allowed the user to edit the relative path, we have to consider it here instead
            // of fullPath.
            crypto_password_category.isEnabled -> {
                val editRelativePath = crypto_password_category.text!!.toString().trim()
                if (editRelativePath.isEmpty()) {
                    showSnackbar(resources.getString(R.string.path_toast_text))
                    return
                }
                "$repoPath/${editRelativePath.trim('/')}/$editName.gpg"
            }
            else -> "$fullPath/$editName.gpg"
        }

        lifecycleScope.launch(IO) {
            api?.executeApiAsync(data, iStream, oStream) { result ->
                when (result?.getIntExtra(RESULT_CODE, RESULT_CODE_ERROR)) {
                    RESULT_CODE_SUCCESS -> {
                        try {
                            // TODO This might fail, we should check that the write is successful
                            val file = File(path)
                            val outputStream = FileUtils.openOutputStream(file)
                            outputStream.write(oStream.toByteArray())
                            outputStream.close()

                            val returnIntent = Intent()
                            returnIntent.putExtra("CREATED_FILE", path)
                            returnIntent.putExtra("NAME", editName)
                            returnIntent.putExtra("LONG_NAME", getLongName(fullPath, repoPath, editName!!))

                            // if coming from decrypt screen->edit button
                            if (intent.getBooleanExtra("fromDecrypt", false)) {
                                returnIntent.putExtra("OPERATION", "EDIT")
                                returnIntent.putExtra("needCommit", true)
                            }

                            if (shouldGeneratePassword) {
                                val directoryStructure =
                                    AutofillPreferences.directoryStructure(applicationContext)
                                val entry = PasswordEntry(content)
                                returnIntent.putExtra("PASSWORD", entry.password)
                                val username = PasswordEntry(content).username
                                    ?: directoryStructure.getUsernameFor(file)
                                returnIntent.putExtra("USERNAME", username)
                            }

                            setResult(RESULT_OK, returnIntent)
                            finish()
                        } catch (e: Exception) {
                            e(e) { "An Exception occurred" }
                        }
                    }
                    RESULT_CODE_ERROR -> handleError(result)
                }
            }
        }
    }

    /**
     * Opens EncryptActivity with the information for this file to be edited
     */
    private fun editPassword() {
        setContentView(R.layout.password_creation_activity)
        generate_password?.setOnClickListener {
            when (settings.getString("pref_key_pwgen_type", KEY_PWGEN_TYPE_CLASSIC)) {
                KEY_PWGEN_TYPE_CLASSIC -> PasswordGeneratorDialogFragment()
                    .show(supportFragmentManager, "generator")
                KEY_PWGEN_TYPE_XKPASSWD -> XkPasswordGeneratorDialogFragment()
                    .show(supportFragmentManager, "xkpwgenerator")
            }
        }

        title = getString(R.string.edit_password_title)

        val monoTypeface = Typeface.createFromAsset(assets, "fonts/sourcecodepro.ttf")
        crypto_password_edit.setText(passwordEntry?.password)
        crypto_password_edit.typeface = monoTypeface
        crypto_extra_edit.setText(passwordEntry?.extraContent)
        crypto_extra_edit.typeface = monoTypeface

        crypto_password_category.setText(relativeParentPath)
        crypto_password_edit.setText(name)
        crypto_password_edit.isEnabled = false

        delayTask?.cancelAndSignal(true)

        val data = Intent(this, PgpActivity::class.java)
        data.putExtra("OPERATION", "EDIT")
        data.putExtra("fromDecrypt", true)
        intent = data
        invalidateOptionsMenu()
    }

    /**
     * Get the Key ids from OpenKeychain
     */
    private fun getKeyIds(receivedIntent: Intent? = null) {
        val data = receivedIntent ?: Intent()
        data.action = OpenPgpApi.ACTION_GET_KEY_IDS
        lifecycleScope.launch(IO) {
            api?.executeApiAsync(data, null, null) { result ->
                when (result?.getIntExtra(RESULT_CODE, RESULT_CODE_ERROR)) {
                    RESULT_CODE_SUCCESS -> {
                        try {
                            val ids = result.getLongArrayExtra(OpenPgpApi.RESULT_KEY_IDS)
                                ?: LongArray(0)
                            val keys = ids.map { it.toString() }.toSet()

                            // use Long
                            settings.edit { putStringSet("openpgp_key_ids_set", keys) }

                            showSnackbar("PGP keys selected")

                            setResult(RESULT_OK)
                            finish()
                        } catch (e: Exception) {
                            e(e) { "An Exception occurred" }
                        }
                    }
                    RESULT_CODE_USER_INTERACTION_REQUIRED -> handleUserInteractionRequest(result, REQUEST_KEY_ID)
                    RESULT_CODE_ERROR -> handleError(result)
                }
            }
        }
    }

    override fun onError(e: Exception) {}

    /**
     * The action to take when the PGP service is bound
     */
    override fun onBound(service: IOpenPgpService2) {
        initOpenPgpApi()
        when (operation) {
            "EDIT", "DECRYPT" -> decryptAndVerify()
            "GET_KEY_ID" -> getKeyIds()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (data == null) {
            setResult(RESULT_CANCELED, null)
            finish()
            return
        }

        // try again after user interaction
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_DECRYPT -> decryptAndVerify(data)
                REQUEST_KEY_ID -> getKeyIds(data)
                else -> {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        } else if (resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED, data)
            finish()
        }
    }

    private fun copyPasswordToClipBoard() {
        val clipboard = clipboard ?: return
        val pass = passwordEntry?.password
        val clip = ClipData.newPlainText("pgp_handler_result_pm", pass)
        clipboard.setPrimaryClip(clip)

        var clearAfter = 45
        try {
            clearAfter = Integer.parseInt(settings.getString("general_show_time", "45") as String)
        } catch (e: NumberFormatException) {
            // ignore and keep default
        }

        if (clearAfter != 0) {
            setTimer()
            showSnackbar(resources.getString(R.string.clipboard_password_toast_text, clearAfter))
        } else {
            showSnackbar(resources.getString(R.string.clipboard_password_no_clear_toast_text))
        }
    }

    private fun copyTextToClipboard(text: String) {
        val clipboard = clipboard ?: return
        val clip = ClipData.newPlainText("pgp_handler_result_pm", text)
        clipboard.setPrimaryClip(clip)
        showSnackbar(resources.getString(R.string.clipboard_copied_text))
    }

    private fun shareAsPlaintext() {
        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, passwordEntry?.password)
        sendIntent.type = "text/plain"
        startActivity(
            Intent.createChooser(
                sendIntent,
                resources.getText(R.string.send_plaintext_password_to)
            )
        ) // Always show a picker to give the user a chance to cancel
    }

    private fun setTimer() {

        // make sure to cancel any running tasks as soon as possible
        // if the previous task is still running, do not ask it to clear the password
        delayTask?.cancelAndSignal(true)

        // launch a new one
        delayTask = DelayShow()
        delayTask?.execute()
    }

    /**
     * Gets a relative string describing when this shape was last changed
     * (e.g. "one hour ago")
     */
    private fun getLastChangedString(timeStamp: Long): CharSequence {
        if (timeStamp < 0) {
            throw RuntimeException()
        }

        return DateUtils.getRelativeTimeSpanString(this, timeStamp, true)
    }

    @Suppress("StaticFieldLeak")
    inner class DelayShow {

        private var skip = false
        private var service: Intent? = null
        private var showTime: Int = 0

        // Custom cancellation that can be triggered from another thread.
        //
        // This signals the DelayShow task to stop and avoids it having
        // to poll the AsyncTask.isCancelled() excessively. If skipClearing
        // is true, the cancelled task won't clear the clipboard.
        fun cancelAndSignal(skipClearing: Boolean) {
            skip = skipClearing
            if (service != null) {
                stopService(service)
                service = null
            }
        }

        fun execute() {
            service = Intent(this@PgpActivity, ClipboardService::class.java).also {
                it.action = ACTION_START
            }
            doOnPreExecute()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service)
            } else {
                startService(service)
            }
        }

        private fun doOnPreExecute() {
            showTime = try {
                Integer.parseInt(settings.getString("general_show_time", "45") as String)
            } catch (e: NumberFormatException) {
                45
            }
            password_text_container?.visibility = View.VISIBLE
            if (extra_content?.text?.isNotEmpty() == true)
                extra_content_container?.visibility = View.VISIBLE
        }

        fun doOnPostExecute() {
            if (skip) return

            if (password_text != null) {
                passwordEntry = null
                extra_content_container.visibility = View.INVISIBLE
                password_text_container.visibility = View.INVISIBLE
                finish()
            }
        }
    }

    companion object {
        const val OPEN_PGP_BOUND = 101
        const val REQUEST_DECRYPT = 202
        const val REQUEST_KEY_ID = 203

        private const val ACTION_CLEAR = "ACTION_CLEAR_CLIPBOARD"
        private const val ACTION_START = "ACTION_START_CLIPBOARD_TIMER"

        const val TAG = "PgpActivity"

        const val KEY_PWGEN_TYPE_CLASSIC = "classic"
        const val KEY_PWGEN_TYPE_XKPASSWD = "xkpasswd"

        /**
         * Gets the relative path to the repository
         */
        fun getRelativePath(fullPath: String, repositoryPath: String): String =
            fullPath.replace(repositoryPath, "").replace("/+".toRegex(), "/")

        /**
         * Gets the Parent path, relative to the repository
         */
        fun getParentPath(fullPath: String, repositoryPath: String): String {
            val relativePath = getRelativePath(fullPath, repositoryPath)
            val index = relativePath.lastIndexOf("/")
            return "/${relativePath.substring(startIndex = 0, endIndex = index + 1)}/".replace("/+".toRegex(), "/")
        }

        /**
         * Gets the name of the password (excluding .gpg)
         */
        fun getName(fullPath: String): String {
            return FilenameUtils.getBaseName(fullPath)
        }

        /**
         * /path/to/store/social/facebook.gpg -> social/facebook
         */
        @JvmStatic
        fun getLongName(fullPath: String, repositoryPath: String, basename: String): String {
            var relativePath = getRelativePath(fullPath, repositoryPath)
            return if (relativePath.isNotEmpty() && relativePath != "/") {
                // remove preceding '/'
                relativePath = relativePath.substring(1)
                if (relativePath.endsWith('/')) {
                    relativePath + basename
                } else {
                    "$relativePath/$basename"
                }
            } else {
                basename
            }
        }
    }
}
