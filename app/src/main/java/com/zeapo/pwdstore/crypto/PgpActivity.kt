package com.zeapo.pwdstore.crypto

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.AsyncTask
import android.os.Bundle
import android.os.ConditionVariable
import android.os.Handler
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.PasswordEntry
import com.zeapo.pwdstore.PasswordGeneratorDialogFragment
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.utils.Otp
import kotlinx.android.synthetic.main.decrypt_layout.*
import kotlinx.android.synthetic.main.encrypt_layout.crypto_extra_edit
import kotlinx.android.synthetic.main.encrypt_layout.crypto_password_category
import kotlinx.android.synthetic.main.encrypt_layout.crypto_password_edit
import kotlinx.android.synthetic.main.encrypt_layout.crypto_password_file_edit
import kotlinx.android.synthetic.main.encrypt_layout.generate_password
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpApi.ACTION_DECRYPT_VERIFY
import org.openintents.openpgp.util.OpenPgpApi.RESULT_CODE
import org.openintents.openpgp.util.OpenPgpApi.RESULT_CODE_ERROR
import org.openintents.openpgp.util.OpenPgpApi.RESULT_CODE_SUCCESS
import org.openintents.openpgp.util.OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED
import org.openintents.openpgp.util.OpenPgpApi.RESULT_ERROR
import org.openintents.openpgp.util.OpenPgpApi.RESULT_INTENT
import org.openintents.openpgp.util.OpenPgpServiceConnection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.Date

class PgpActivity : AppCompatActivity(), OpenPgpServiceConnection.OnBound {
    private val clipboard: ClipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    private var passwordEntry: PasswordEntry? = null
    private var api: OpenPgpApi? = null

    private var editName: String? = null
    private var editPass: String? = null
    private var editExtra: String? = null

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
    private val keyIDs: MutableSet<String> by lazy {
        settings.getStringSet("openpgp_key_ids_set", mutableSetOf()) ?: emptySet()
    }
    private var mServiceConnection: OpenPgpServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        // some persistence
        val providerPackageName = settings.getString("openpgp_provider_list", "")

        if (TextUtils.isEmpty(providerPackageName)) {
            Toast.makeText(this, this.resources.getString(R.string.provider_toast_text), Toast.LENGTH_LONG).show()
            val intent = Intent(this, UserPreference::class.java)
            startActivityForResult(intent, OPEN_PGP_BOUND)
        } else {
            // bind to service
            mServiceConnection = OpenPgpServiceConnection(this, providerPackageName, this)
            mServiceConnection?.bindToService()
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        when (operation) {
            "DECRYPT", "EDIT" -> {
                setContentView(R.layout.decrypt_layout)
                crypto_password_category_decrypt.text = relativeParentPath
                crypto_password_file.text = name

                crypto_password_last_changed.text = try {
                    this.resources.getString(R.string.last_changed, lastChangedString)
                } catch (e: RuntimeException) {
                    showToast(getString(R.string.get_last_changed_failed))
                    ""
                }
            }
            "ENCRYPT" -> {
                setContentView(R.layout.encrypt_layout)

                generate_password?.setOnClickListener {
                    PasswordGeneratorDialogFragment().show(supportFragmentManager, "generator")
                }

                title = getString(R.string.new_password_title)
                crypto_password_category.text = getRelativePath(fullPath, repoPath)
            }
        }
    }

    override fun onDestroy() {
        checkAndIncrementHotp()
        super.onDestroy()
        mServiceConnection?.unbindFromService()
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
            android.R.id.home -> {
                if (passwordEntry?.hotpIsIncremented() == false) {
                    setResult(RESULT_CANCELED)
                }
                finish()
            }
            R.id.copy_password -> copyPasswordToClipBoard()
            R.id.share_password_as_plaintext -> shareAsPlaintext()
            R.id.edit_password -> editPassword()
            R.id.crypto_confirm_add -> encrypt()
            R.id.crypto_confirm_add_and_copy -> encrypt(true)
            R.id.crypto_cancel_add -> {
                if (passwordEntry?.hotpIsIncremented() == false) {
                    setResult(RESULT_CANCELED)
                }
                finish()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * Shows a simple toast message
     */
    private fun showToast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    /**
     * Handle the case where OpenKeychain returns that it needs to interact with the user
     *
     * @param result The intent returned by OpenKeychain
     * @param requestCode The code we'd like to use to identify the behaviour
     */
    private fun handleUserInteractionRequest(result: Intent, requestCode: Int) {
        Log.i(TAG, "RESULT_CODE_USER_INTERACTION_REQUIRED")

        val pi: PendingIntent? = result.getParcelableExtra(RESULT_INTENT)
        try {
            this@PgpActivity.startIntentSenderFromChild(
                    this@PgpActivity, pi?.intentSender, requestCode,
                    null, 0, 0, 0
            )
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "SendIntentException", e)
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
            showToast("Error from OpenKeyChain : " + error.message)
            Log.e(TAG, "onError getErrorId:" + error.errorId)
            Log.e(TAG, "onError getMessage:" + error.message)
        }
    }

    private fun initOpenPgpApi() {
        api = api ?: OpenPgpApi(this, mServiceConnection?.service)
    }

    private fun decryptAndVerify(receivedIntent: Intent? = null) {
        val data = receivedIntent ?: Intent()
        data.action = ACTION_DECRYPT_VERIFY

        val iStream = FileUtils.openInputStream(File(fullPath))
        val oStream = ByteArrayOutputStream()

        api?.executeApiAsync(data, iStream, oStream) { result: Intent? ->
            when (result?.getIntExtra(RESULT_CODE, RESULT_CODE_ERROR)) {
                RESULT_CODE_SUCCESS -> {
                    try {
                        val showPassword = settings.getBoolean("show_password", true)
                        val showExtraContent = settings.getBoolean("show_extra_content", true)

                        crypto_container_decrypt.visibility = View.VISIBLE

                        val monoTypeface = Typeface.createFromAsset(assets, "fonts/sourcecodepro.ttf")
                        val entry = PasswordEntry(oStream)

                        passwordEntry = entry

                        if (intent.getStringExtra("OPERATION") == "EDIT") {
                            editPassword()
                            return@executeApiAsync
                        }

                        if (entry.password.isEmpty()) {
                            crypto_password_show.visibility = View.GONE
                            crypto_password_show_label.visibility = View.GONE
                        } else {
                            crypto_password_show.visibility = View.VISIBLE
                            crypto_password_show_label.visibility = View.VISIBLE
                            crypto_password_show.typeface = monoTypeface
                            crypto_password_show.text = entry.password
                        }
                        crypto_password_show.typeface = monoTypeface
                        crypto_password_show.text = entry.password

                        crypto_password_toggle_show.visibility = if (showPassword) View.GONE else View.VISIBLE
                        crypto_password_show.transformationMethod = if (showPassword) {
                            null
                        } else {
                            HoldToShowPasswordTransformation(
                                    crypto_password_toggle_show,
                                    Runnable { crypto_password_show.text = entry.password }
                            )
                        }

                        if (entry.hasExtraContent()) {
                            crypto_extra_show.typeface = monoTypeface
                            crypto_extra_show.text = entry.extraContent

                            if (showExtraContent) {
                                crypto_extra_show_layout.visibility = View.VISIBLE
                                crypto_extra_toggle_show.visibility = View.GONE
                                crypto_extra_show.transformationMethod = null
                            } else {
                                crypto_extra_show_layout.visibility = View.GONE
                                crypto_extra_toggle_show.visibility = View.VISIBLE
                                crypto_extra_toggle_show.setOnCheckedChangeListener { _, _ ->
                                    crypto_extra_show.text = entry.extraContent
                                }

                                crypto_extra_show.transformationMethod = object : PasswordTransformationMethod() {
                                    override fun getTransformation(source: CharSequence, view: View): CharSequence {
                                        return if (crypto_extra_toggle_show.isChecked) source else super.getTransformation(source, view)
                                    }
                                }
                            }

                            if (entry.hasUsername()) {
                                crypto_username_show.visibility = View.VISIBLE
                                crypto_username_show_label.visibility = View.VISIBLE
                                crypto_copy_username.visibility = View.VISIBLE

                                crypto_copy_username.setOnClickListener { copyUsernameToClipBoard(entry.username!!) }
                                crypto_username_show.typeface = monoTypeface
                                crypto_username_show.text = entry.username
                            } else {
                                crypto_username_show.visibility = View.GONE
                                crypto_username_show_label.visibility = View.GONE
                                crypto_copy_username.visibility = View.GONE
                            }
                        }

                        if (entry.hasTotp() || entry.hasHotp()) {
                            crypto_extra_show_layout.visibility = View.VISIBLE
                            crypto_extra_show.typeface = monoTypeface
                            crypto_extra_show.text = entry.extraContent

                            crypto_otp_show.visibility = View.VISIBLE
                            crypto_otp_show_label.visibility = View.VISIBLE
                            crypto_copy_otp.visibility = View.VISIBLE

                            if (entry.hasTotp()) {
                                crypto_copy_otp.setOnClickListener {
                                    copyOtpToClipBoard(
                                            Otp.calculateCode(
                                                    entry.totpSecret,
                                                    Date().time / (1000 * entry.totpPeriod),
                                                    entry.totpAlgorithm,
                                                    entry.digits)
                                    )
                                }
                                crypto_otp_show.text =
                                        Otp.calculateCode(
                                                entry.totpSecret,
                                                Date().time / (1000 * entry.totpPeriod),
                                                entry.totpAlgorithm,
                                                entry.digits)
                            } else {
                                // we only want to calculate and show HOTP if the user requests it
                                crypto_copy_otp.setOnClickListener {
                                    if (settings.getBoolean("hotp_remember_check", false)) {
                                        if (settings.getBoolean("hotp_remember_choice", false)) {
                                            calculateAndCommitHotp(entry)
                                        } else {
                                            calculateHotp(entry)
                                        }
                                    } else {
                                        // show a dialog asking permission to update the HOTP counter in the entry
                                        val checkInflater = LayoutInflater.from(this)
                                        val checkLayout = checkInflater.inflate(R.layout.otp_confirm_layout, null)
                                        val rememberCheck: CheckBox =
                                                checkLayout.findViewById(R.id.hotp_remember_checkbox)
                                        val dialogBuilder = MaterialAlertDialogBuilder(this)
                                        dialogBuilder.setView(checkLayout)
                                        dialogBuilder.setMessage(R.string.dialog_update_body)
                                                .setCancelable(false)
                                                .setPositiveButton(R.string.dialog_update_positive) { _, _ ->
                                                    run {
                                                        calculateAndCommitHotp(entry)
                                                        if (rememberCheck.isChecked) {
                                                            val editor = settings.edit()
                                                            editor.putBoolean("hotp_remember_check", true)
                                                            editor.putBoolean("hotp_remember_choice", true)
                                                            editor.apply()
                                                        }
                                                    }
                                                }
                                                .setNegativeButton(R.string.dialog_update_negative) { _, _ ->
                                                    run {
                                                        calculateHotp(entry)
                                                        val editor = settings.edit()
                                                        editor.putBoolean("hotp_remember_check", true)
                                                        editor.putBoolean("hotp_remember_choice", false)
                                                        editor.apply()
                                                    }
                                                }
                                        val updateDialog = dialogBuilder.create()
                                        updateDialog.setTitle(R.string.dialog_update_title)
                                        updateDialog.show()
                                    }
                                }
                                crypto_otp_show.setText(R.string.hotp_pending)
                            }
                            crypto_otp_show.typeface = monoTypeface
                        } else {
                            crypto_otp_show.visibility = View.GONE
                            crypto_otp_show_label.visibility = View.GONE
                            crypto_copy_otp.visibility = View.GONE
                        }

                        if (settings.getBoolean("copy_on_decrypt", true)) {
                            copyPasswordToClipBoard()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "An Exception occurred", e)
                    }
                }
                RESULT_CODE_USER_INTERACTION_REQUIRED -> handleUserInteractionRequest(result, REQUEST_DECRYPT)
                RESULT_CODE_ERROR -> handleError(result)
            }

        }
    }

    /**
     * Encrypts the password and the extra content
     */
    private fun encrypt(copy: Boolean = false) {
        // if HOTP was incremented, we leave fields as is; they have already been set
        if (intent.getStringExtra("OPERATION") != "INCREMENT") {
            editName = crypto_password_file_edit.text.toString().trim()
            editPass = crypto_password_edit.text.toString()
            editExtra = crypto_extra_edit.text.toString()
        }

        if (editName?.isEmpty() == true) {
            showToast(resources.getString(R.string.file_toast_text))
            return
        }

        if (editPass?.isEmpty() == true && editExtra?.isEmpty() == true) {
            showToast(resources.getString(R.string.empty_toast_text))
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
        val iStream = ByteArrayInputStream("$editPass\n$editExtra".toByteArray(Charset.forName("UTF-8")))
        val oStream = ByteArrayOutputStream()

        val path = if (intent.getBooleanExtra("fromDecrypt", false)) fullPath else "$fullPath/$editName.gpg"

        api?.executeApiAsync(data, iStream, oStream) { result: Intent? ->
            when (result?.getIntExtra(RESULT_CODE, RESULT_CODE_ERROR)) {
                RESULT_CODE_SUCCESS -> {
                    try {
                        // TODO This might fail, we should check that the write is successful
                        val outputStream = FileUtils.openOutputStream(File(path))
                        outputStream.write(oStream.toByteArray())
                        outputStream.close()

                        val returnIntent = Intent()
                        returnIntent.putExtra("CREATED_FILE", path)
                        returnIntent.putExtra("NAME", editName)
                        returnIntent.putExtra("LONG_NAME", getLongName(fullPath, repoPath, this.editName!!))

                        // if coming from decrypt screen->edit button
                        if (intent.getBooleanExtra("fromDecrypt", false)) {
                            returnIntent.putExtra("OPERATION", "EDIT")
                            returnIntent.putExtra("needCommit", true)
                        }
                        setResult(RESULT_OK, returnIntent)
                        finish()
                    } catch (e: Exception) {
                        Log.e(TAG, "An Exception occurred", e)
                    }
                }
                RESULT_CODE_ERROR -> handleError(result)
            }

        }
    }

    /**
     * Opens EncryptActivity with the information for this file to be edited
     */
    private fun editPassword() {
        setContentView(R.layout.encrypt_layout)
        generate_password?.setOnClickListener {
            PasswordGeneratorDialogFragment().show(supportFragmentManager, "generator")
        }

        title = getString(R.string.edit_password_title)

        val monoTypeface = Typeface.createFromAsset(assets, "fonts/sourcecodepro.ttf")
        crypto_password_edit.setText(passwordEntry?.password)
        crypto_password_edit.typeface = monoTypeface
        crypto_extra_edit.setText(passwordEntry?.extraContent)
        crypto_extra_edit.typeface = monoTypeface

        crypto_password_category.text = relativeParentPath
        crypto_password_file_edit.setText(name)
        crypto_password_file_edit.isEnabled = false

        delayTask?.cancelAndSignal(true)

        val data = Intent(this, PgpActivity::class.java)
        data.putExtra("OPERATION", "EDIT")
        data.putExtra("fromDecrypt", true)
        intent = data
        invalidateOptionsMenu()
    }

    /**
     * Writes updated HOTP counter to edit fields and encrypts
     */
    private fun checkAndIncrementHotp() {
        // we do not want to increment the HOTP counter if the user has edited the entry or has not
        // generated an HOTP code
        if (intent.getStringExtra("OPERATION") != "EDIT" && passwordEntry?.hotpIsIncremented() == true) {
            editName = name.trim()
            editPass = passwordEntry?.password
            editExtra = passwordEntry?.extraContent

            val data = Intent(this, PgpActivity::class.java)
            data.putExtra("OPERATION", "INCREMENT")
            data.putExtra("fromDecrypt", true)
            intent = data
            encrypt()
        }
    }

    private fun calculateHotp(entry: PasswordEntry) {
        copyOtpToClipBoard(Otp.calculateCode(entry.hotpSecret, entry.hotpCounter!! + 1, "sha1", entry.digits))
        crypto_otp_show.text = Otp.calculateCode(entry.hotpSecret, entry.hotpCounter + 1, "sha1", entry.digits)
        crypto_extra_show.text = entry.extraContent
    }

    private fun calculateAndCommitHotp(entry: PasswordEntry) {
        calculateHotp(entry)
        entry.incrementHotp()
        // we must set the result before encrypt() is called, since in
        // some cases it is called during the finish() sequence
        val returnIntent = Intent()
        returnIntent.putExtra("NAME", name.trim())
        returnIntent.putExtra("OPERATION", "INCREMENT")
        returnIntent.putExtra("needCommit", true)
        setResult(RESULT_OK, returnIntent)
    }

    /**
     * Get the Key ids from OpenKeychain
     */
    private fun getKeyIds(receivedIntent: Intent? = null) {
        val data = receivedIntent ?: Intent()
        data.action = OpenPgpApi.ACTION_GET_KEY_IDS
        api?.executeApiAsync(data, null, null) { result: Intent? ->
            when (result?.getIntExtra(RESULT_CODE, RESULT_CODE_ERROR)) {
                RESULT_CODE_SUCCESS -> {
                    try {
                        val ids = result.getLongArrayExtra(OpenPgpApi.RESULT_KEY_IDS)
                                ?: LongArray(0)
                        val keys = ids.map { it.toString() }.toSet()

                        // use Long
                        settings.edit().putStringSet("openpgp_key_ids_set", keys).apply()

                        showToast("PGP keys selected")

                        setResult(RESULT_OK)
                        finish()
                    } catch (e: Exception) {
                        Log.e(TAG, "An Exception occurred", e)
                    }
                }
                RESULT_CODE_USER_INTERACTION_REQUIRED -> handleUserInteractionRequest(result, REQUEST_KEY_ID)
                RESULT_CODE_ERROR -> handleError(result)
            }
        }
    }

    override fun onError(e: Exception?) {}

    /**
     * The action to take when the PGP service is bound
     */
    override fun onBound(service: IOpenPgpService2?) {
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

    private inner class HoldToShowPasswordTransformation constructor(button: Button, private val onToggle: Runnable) :
            PasswordTransformationMethod(), View.OnTouchListener {
        private var shown = false

        init {
            button.setOnTouchListener(this)
        }

        override fun getTransformation(charSequence: CharSequence, view: View): CharSequence {
            return if (shown) charSequence else super.getTransformation("12345", view)
        }

        @Suppress("ClickableViewAccessibility")
        override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    shown = true
                    onToggle.run()
                }
                MotionEvent.ACTION_UP -> {
                    shown = false
                    onToggle.run()
                }
            }
            return false
        }
    }

    private fun copyPasswordToClipBoard() {
        var pass = passwordEntry?.password

        if (findViewById<TextView>(R.id.crypto_password_show) == null) {
            if (editPass == null) {
                return
            } else {
                pass = editPass
            }
        }

        val clip = ClipData.newPlainText("pgp_handler_result_pm", pass)
        clipboard.setPrimaryClip(clip)

        var clearAfter = 45
        try {
            clearAfter = Integer.parseInt(settings.getString("general_show_time", "45") as String)
        } catch (e: NumberFormatException) {
            // ignore and keep default
        }

        if (settings.getBoolean("clear_after_copy", true) && clearAfter != 0) {
            setTimer()
            showToast(this.resources.getString(R.string.clipboard_password_toast_text, clearAfter))
        } else {
            showToast(this.resources.getString(R.string.clipboard_password_no_clear_toast_text))
        }

    }

    private fun copyUsernameToClipBoard(username: String) {
        val clip = ClipData.newPlainText("pgp_handler_result_pm", username)
        clipboard.setPrimaryClip(clip)
        showToast(resources.getString(R.string.clipboard_username_toast_text))
    }

    private fun copyOtpToClipBoard(code: String) {
        val clip = ClipData.newPlainText("pgp_handler_result_pm", code)
        clipboard.setPrimaryClip(clip)
        showToast(resources.getString(R.string.clipboard_otp_toast_text))
    }

    private fun shareAsPlaintext() {
        if (findViewById<View>(R.id.share_password_as_plaintext) == null)
            return

        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, passwordEntry?.password)
        sendIntent.type = "text/plain"
        startActivity(
                Intent.createChooser(
                        sendIntent,
                        resources.getText(R.string.send_plaintext_password_to)
                )
        )//Always show a picker to give the user a chance to cancel
    }

    private fun setTimer() {

        // make sure to cancel any running tasks as soon as possible
        // if the previous task is still running, do not ask it to clear the password
        delayTask?.cancelAndSignal(true)

        // launch a new one
        delayTask = DelayShow(this)
        delayTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
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
    inner class DelayShow(val activity: PgpActivity) : AsyncTask<Void, Int, Boolean>() {
        private val pb: ProgressBar? by lazy { pbLoading }
        private var skip = false
        private var cancelNotify = ConditionVariable()

        private var showTime: Int = 0

        // Custom cancellation that can be triggered from another thread.
        //
        // This signals the DelayShow task to stop and avoids it having
        // to poll the AsyncTask.isCancelled() excessively. If skipClearing
        // is true, the cancelled task won't clear the clipboard.
        fun cancelAndSignal(skipClearing: Boolean) {
            skip = skipClearing
            cancelNotify.open()
        }

        val settings: SharedPreferences by lazy {
            PreferenceManager.getDefaultSharedPreferences(activity)
        }

        override fun onPreExecute() {
            showTime = try {
                Integer.parseInt(settings.getString("general_show_time", "45") as String)
            } catch (e: NumberFormatException) {
                45
            }

            val container = findViewById<LinearLayout>(R.id.crypto_container_decrypt)
            container?.visibility = View.VISIBLE

            val extraText = findViewById<TextView>(R.id.crypto_extra_show)

            if (extraText?.text?.isNotEmpty() == true)
                findViewById<View>(R.id.crypto_extra_show_layout)?.visibility = View.VISIBLE

            if (showTime == 0) {
                // treat 0 as forever, and the user must exit and/or clear clipboard on their own
                cancel(true)
            } else {
                this.pb?.max = showTime
            }
        }

        override fun doInBackground(vararg params: Void): Boolean? {
            var current = 0
            while (current < showTime) {

                // Block for 1s or until cancel is signalled
                if (cancelNotify.block(1000)) {
                    return true
                }

                current++
                publishProgress(current)
            }
            return true
        }

        override fun onPostExecute(b: Boolean?) {
            if (skip) return
            checkAndIncrementHotp()

            // No need to validate clear_after_copy. It was validated in copyPasswordToClipBoard()
            Log.d("DELAY_SHOW", "Clearing the clipboard")
            val clip = ClipData.newPlainText("pgp_handler_result_pm", "")
            clipboard.setPrimaryClip(clip)
            if (settings.getBoolean("clear_clipboard_20x", false)) {
                val handler = Handler()
                for (i in 0..19) {
                    val count = i.toString()
                    handler.postDelayed(
                            { clipboard.setPrimaryClip(ClipData.newPlainText(count, count)) },
                            (i * 500).toLong()
                    )
                }
            }

            if (crypto_password_show != null) {
                // clear password; if decrypt changed to encrypt layout via edit button, no need
                if (passwordEntry?.hotpIsIncremented() == false) {
                    setResult(RESULT_CANCELED)
                }
                passwordEntry = null
                crypto_password_show.text = ""
                crypto_extra_show.text = ""
                crypto_extra_show_layout.visibility = View.INVISIBLE
                crypto_container_decrypt.visibility = View.INVISIBLE
                finish()
            }
        }

        override fun onProgressUpdate(vararg values: Int?) {
            this.pb?.progress = values[0] ?: 0
        }
    }

    companion object {
        const val OPEN_PGP_BOUND = 101
        const val REQUEST_DECRYPT = 202
        const val REQUEST_KEY_ID = 203

        const val TAG = "PgpActivity"

        private var delayTask: DelayShow? = null

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

