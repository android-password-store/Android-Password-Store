package com.zeapo.pwdstore.git

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.git.config.SshApiSessionFactory
import com.zeapo.pwdstore.utils.PasswordRepository
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.lib.Constants
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

open class GitActivity : AppCompatActivity() {
    private lateinit var context: Context
    private lateinit var settings: SharedPreferences
    private lateinit var protocol: String
    private lateinit var connectionMode: String
    private lateinit var hostname: String
    private var identityBuilder: SshApiSessionFactory.IdentityBuilder? = null
    private var identity: SshApiSessionFactory.ApiIdentity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        context = requireNotNull(this)

        settings = PreferenceManager.getDefaultSharedPreferences(this)

        protocol = settings.getString("git_remote_protocol", null) ?: "ssh://"
        connectionMode = settings.getString("git_remote_auth", null) ?: "ssh-key"
        hostname = settings.getString("git_remote_location", null) ?: ""
        val operationCode = intent.extras!!.getInt("Operation")

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        when (operationCode) {
            REQUEST_CLONE, EDIT_SERVER -> {
                setContentView(R.layout.activity_git_clone)
                setTitle(R.string.title_activity_git_clone)

                val protcolSpinner = findViewById<Spinner>(R.id.clone_protocol)
                val connectionModeSpinner = findViewById<Spinner>(R.id.connection_mode)

                // init the spinner for connection modes
                val connectionModeAdapter = ArrayAdapter.createFromResource(this,
                        R.array.connection_modes, android.R.layout.simple_spinner_item)
                connectionModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                connectionModeSpinner.adapter = connectionModeAdapter
                connectionModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                        val selection = (findViewById<View>(R.id.connection_mode) as Spinner).selectedItem.toString()
                        connectionMode = selection
                        settings.edit().putString("git_remote_auth", selection).apply()
                    }

                    override fun onNothingSelected(adapterView: AdapterView<*>) {

                    }
                }

                // init the spinner for protocols
                val protocolAdapter = ArrayAdapter.createFromResource(this,
                        R.array.clone_protocols, android.R.layout.simple_spinner_item)
                protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                protcolSpinner.adapter = protocolAdapter
                protcolSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                        protocol = (findViewById<View>(R.id.clone_protocol) as Spinner).selectedItem.toString()
                        if (protocol == "ssh://") {

                            // select ssh-key auth mode as default and enable the spinner in case it was disabled
                            connectionModeSpinner.setSelection(0)
                            connectionModeSpinner.isEnabled = true

                            // however, if we have some saved that, that's more important!
                            when {
                                connectionMode.equals("ssh-key", ignoreCase = true) -> connectionModeSpinner.setSelection(0)
                                connectionMode.equals("OpenKeychain", ignoreCase = true) -> connectionModeSpinner.setSelection(2)
                                else -> connectionModeSpinner.setSelection(1)
                            }
                        } else {
                            // select user/pwd auth-mode and disable the spinner
                            connectionModeSpinner.setSelection(1)
                            connectionModeSpinner.isEnabled = false
                        }

                        updateURI()
                    }

                    override fun onNothingSelected(adapterView: AdapterView<*>) {

                    }
                }

                if (protocol == "ssh://") {
                    protcolSpinner.setSelection(0)
                } else {
                    protcolSpinner.setSelection(1)
                }

                // init the server information
                val serverUrl = findViewById<TextInputEditText>(R.id.server_url)
                val serverPort = findViewById<TextInputEditText>(R.id.server_port)
                val serverPath = findViewById<TextInputEditText>(R.id.server_path)
                val serverUser = findViewById<TextInputEditText>(R.id.server_user)
                val serverUri = findViewById<TextInputEditText>(R.id.clone_uri)

                serverUrl.setText(settings.getString("git_remote_server", ""))
                serverPort.setText(settings.getString("git_remote_port", ""))
                serverUser.setText(settings.getString("git_remote_username", ""))
                serverPath.setText(settings.getString("git_remote_location", ""))

                serverUrl.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {}

                    override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
                        if (serverUrl.isFocused)
                            updateURI()
                    }

                    override fun afterTextChanged(editable: Editable) {}
                })
                serverPort.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {}

                    override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
                        if (serverPort.isFocused)
                            updateURI()
                    }

                    override fun afterTextChanged(editable: Editable) {}
                })
                serverUser.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {}

                    override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
                        if (serverUser.isFocused)
                            updateURI()
                    }

                    override fun afterTextChanged(editable: Editable) {}
                })
                serverPath.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {}

                    override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
                        if (serverPath.isFocused)
                            updateURI()
                    }

                    override fun afterTextChanged(editable: Editable) {}
                })

                serverUri.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {}

                    override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
                        if (serverUri.isFocused)
                            splitURI()
                    }

                    override fun afterTextChanged(editable: Editable) {}
                })

                if (operationCode == EDIT_SERVER) {
                    findViewById<View>(R.id.clone_button).visibility = View.INVISIBLE
                    findViewById<View>(R.id.save_button).visibility = View.VISIBLE
                } else {
                    findViewById<View>(R.id.clone_button).visibility = View.VISIBLE
                    findViewById<View>(R.id.save_button).visibility = View.INVISIBLE
                }

                updateURI()
            }
            EDIT_GIT_CONFIG -> {
                setContentView(R.layout.activity_git_config)
                setTitle(R.string.title_activity_git_config)

                showGitConfig()
            }
            REQUEST_PULL -> syncRepository(REQUEST_PULL)

            REQUEST_PUSH -> syncRepository(REQUEST_PUSH)

            REQUEST_SYNC -> syncRepository(REQUEST_SYNC)
        }
    }

    /**
     * Fills in the server_uri field with the information coming from other fields
     */
    private fun updateURI() {
        val uri = findViewById<TextInputEditText>(R.id.clone_uri)
        val serverUrl = findViewById<TextInputEditText>(R.id.server_url)
        val serverPort = findViewById<TextInputEditText>(R.id.server_port)
        val serverPath = findViewById<TextInputEditText>(R.id.server_path)
        val serverUser = findViewById<TextInputEditText>(R.id.server_user)

        if (uri != null) {
            when (protocol) {
                "ssh://" -> {
                    var hostname = (serverUser.text.toString()
                            + "@" +
                            serverUrl.text.toString().trim { it <= ' ' }
                            + ":")
                    if (serverPort.text.toString() == "22") {
                        hostname += serverPath.text.toString()

                        findViewById<View>(R.id.warn_url).visibility = View.GONE
                    } else {
                        val warnUrl = findViewById<AppCompatTextView>(R.id.warn_url)
                        if (!serverPath.text.toString().matches("/.*".toRegex()) && serverPort.text.toString().isNotEmpty()) {
                            warnUrl.setText(R.string.warn_malformed_url_port)
                            warnUrl.visibility = View.VISIBLE
                        } else {
                            warnUrl.visibility = View.GONE
                        }
                        hostname += serverPort.text.toString() + serverPath.text.toString()
                    }

                    if (hostname != "@:") uri.setText(hostname)
                }
                "https://" -> {
                    val hostname = StringBuilder()
                    hostname.append(serverUrl.text.toString().trim { it <= ' ' })

                    if (serverPort.text.toString() == "443") {
                        hostname.append(serverPath.text.toString())

                        findViewById<View>(R.id.warn_url).visibility = View.GONE
                    } else {
                        hostname.append("/")
                        hostname.append(serverPort.text.toString())
                                .append(serverPath.text.toString())
                    }

                    if (hostname.toString() != "@/") uri.setText(hostname)
                }
                else -> {
                }
            }

        }
    }

    /**
     * Splits the information in server_uri into the other fields
     */
    private fun splitURI() {
        val serverUri = findViewById<TextInputEditText>(R.id.clone_uri)
        val serverUrl = findViewById<TextInputEditText>(R.id.server_url)
        val serverPort = findViewById<TextInputEditText>(R.id.server_port)
        val serverPath = findViewById<TextInputEditText>(R.id.server_path)
        val serverUser = findViewById<TextInputEditText>(R.id.server_user)

        val uri = serverUri.text.toString()
        val pattern = Pattern.compile("(.+)@([\\w\\d.]+):([\\d]+)*(.*)")
        val matcher = pattern.matcher(uri)
        if (matcher.find()) {
            val count = matcher.groupCount()
            if (count > 1) {
                serverUser.setText(matcher.group(1))
                serverUrl.setText(matcher.group(2))
            }
            if (count == 4) {
                serverPort.setText(matcher.group(3))
                serverPath.setText(matcher.group(4))

                val warnUrl = findViewById<AppCompatTextView>(R.id.warn_url)
                if (!serverPath.text.toString().matches("/.*".toRegex()) && serverPort.text.toString().isNotEmpty()) {
                    warnUrl.setText(R.string.warn_malformed_url_port)
                    warnUrl.visibility = View.VISIBLE
                } else {
                    warnUrl.visibility = View.GONE
                }
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        updateURI()
    }

    override fun onDestroy() {
        // Do not leak the service connection
        if (identityBuilder != null) {
            identityBuilder!!.close()
            identityBuilder = null
        }
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.git_clone, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.user_pref -> try {
                val intent = Intent(this, UserPreference::class.java)
                startActivity(intent)
                return true
            } catch (e: Exception) {
                println("Exception caught :(")
                e.printStackTrace()
            }

            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Saves the configuration found in the form
     */
    private fun saveConfiguration(): Boolean {
        // remember the settings
        val editor = settings.edit()

        editor.putString("git_remote_server", (findViewById<View>(R.id.server_url) as TextInputEditText).text.toString())
        editor.putString("git_remote_location", (findViewById<View>(R.id.server_path) as TextInputEditText).text.toString())
        editor.putString("git_remote_username", (findViewById<View>(R.id.server_user) as TextInputEditText).text.toString())
        editor.putString("git_remote_protocol", protocol)
        editor.putString("git_remote_auth", connectionMode)
        editor.putString("git_remote_port", (findViewById<View>(R.id.server_port) as TextInputEditText).text.toString())
        editor.putString("git_remote_uri", (findViewById<View>(R.id.clone_uri) as TextInputEditText).text.toString())

        // 'save' hostname variable for use by addRemote() either here or later
        // in syncRepository()
        hostname = (findViewById<View>(R.id.clone_uri) as TextInputEditText).text.toString()
        val port = (findViewById<View>(R.id.server_port) as TextInputEditText).text.toString()
        // don't ask the user, take off the protocol that he puts in
        hostname = hostname.replaceFirst("^.+://".toRegex(), "")
        (findViewById<View>(R.id.clone_uri) as TextInputEditText).setText(hostname)

        if (protocol != "ssh://") {
            hostname = protocol + hostname
        } else {
            // if the port is explicitly given, jgit requires the ssh://
            if (port.isNotEmpty() && port != "22")
                hostname = protocol + hostname

            // did he forget the username?
            if (!hostname.matches("^.+@.+".toRegex())) {
                MaterialAlertDialogBuilder(this)
                        .setMessage(context.getString(R.string.forget_username_dialog_text))
                        .setPositiveButton(context.getString(R.string.dialog_oops), null)
                        .show()
                return false
            }
        }
        if (PasswordRepository.isInitialized && settings.getBoolean("repository_initialized", false)) {
            // don't just use the clone_uri text, need to use hostname which has
            // had the proper protocol prepended
            PasswordRepository.addRemote("origin", hostname, true)
        }

        editor.apply()
        return true
    }

    /**
     * Save the repository information to the shared preferences settings
     */
    @Suppress("UNUSED_PARAMETER")
    fun saveConfiguration(view: View) {
        if (!saveConfiguration())
            return
        finish()
    }

    private fun showGitConfig() {
        // init the server information
        val username = findViewById<TextInputEditText>(R.id.git_user_name)
        val email = findViewById<TextInputEditText>(R.id.git_user_email)
        val abort = findViewById<MaterialButton>(R.id.git_abort_rebase)

        username.setText(settings.getString("git_config_user_name", ""))
        email.setText(settings.getString("git_config_user_email", ""))

        // git status
        val repo = PasswordRepository.getRepository(PasswordRepository.getRepositoryDirectory(context))
        if (repo != null) {
            val commitHash = findViewById<AppCompatTextView>(R.id.git_commit_hash)
            try {
                val objectId = repo.resolve(Constants.HEAD)
                val ref = repo.getRef("refs/heads/master")
                val head = if (ref.objectId.equals(objectId)) ref.name else "DETACHED"
                commitHash.text = String.format("%s (%s)", objectId.abbreviate(8).name(), head)

                // enable the abort button only if we're rebasing
                val isRebasing = repo.repositoryState.isRebasing
                abort.isEnabled = isRebasing
                abort.alpha = if (isRebasing) 1.0f else 0.5f
            } catch (e: Exception) {
                // ignore
            }

        }
    }

    private fun saveGitConfigs(): Boolean {
        // remember the settings
        val editor = settings.edit()

        val email = (findViewById<View>(R.id.git_user_email) as TextInputEditText).text!!.toString()
        editor.putString("git_config_user_email", email)
        editor.putString("git_config_user_name", (findViewById<View>(R.id.git_user_name) as TextInputEditText).text.toString())

        if (!email.matches(emailPattern.toRegex())) {
            MaterialAlertDialogBuilder(this)
                    .setMessage(context.getString(R.string.invalid_email_dialog_text))
                    .setPositiveButton(context.getString(R.string.dialog_oops), null)
                    .show()
            return false
        }

        editor.apply()
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun applyGitConfigs(view: View) {
        if (!saveGitConfigs())
            return
        PasswordRepository.setUserName(settings.getString("git_config_user_name", null) ?: "")
        PasswordRepository.setUserEmail(settings.getString("git_config_user_email", null) ?: "")
        finish()
    }

    @Suppress("UNUSED_PARAMETER")
    fun abortRebase(view: View) {
        launchGitOperation(BREAK_OUT_OF_DETACHED)
    }

    /**
     * Clones the repository, the directory exists, deletes it
     */
    @Suppress("UNUSED_PARAMETER")
    fun cloneRepository(view: View) {
        if (PasswordRepository.getRepository(null) == null) {
            PasswordRepository.initialize(this)
        }
        val localDir = PasswordRepository.getRepositoryDirectory(context)

        if (!saveConfiguration())
            return

        // Warn if non-empty folder unless it's a just-initialized store that has just a .git folder
        if (localDir!!.exists() && localDir.listFiles()!!.isNotEmpty()
                && !(localDir.listFiles()!!.size == 1 && localDir.listFiles()!![0].name == ".git")) {
            MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_delete_title)
                    .setMessage(resources.getString(R.string.dialog_delete_msg) + " " + localDir.toString())
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_delete
                    ) { dialog, _ ->
                        try {
                            FileUtils.deleteDirectory(localDir)
                            launchGitOperation(REQUEST_CLONE)
                        } catch (e: IOException) {
                            //TODO Handle the exception correctly if we are unable to delete the directory...
                            e.printStackTrace()
                            MaterialAlertDialogBuilder(this).setMessage(e.message).show()
                        }

                        dialog.cancel()
                    }
                    .setNegativeButton(R.string.dialog_do_not_delete
                    ) { dialog, _ -> dialog.cancel() }
                    .show()
        } else {
            try {
                // Silently delete & replace the lone .git folder if it exists
                if (localDir.exists() && localDir.listFiles()!!.size == 1 && localDir.listFiles()!![0].name == ".git") {
                    try {
                        FileUtils.deleteDirectory(localDir)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        MaterialAlertDialogBuilder(this).setMessage(e.message).show()
                    }

                }
            } catch (e: Exception) {
                //This is what happens when jgit fails :(
                //TODO Handle the diffent cases of exceptions
                e.printStackTrace()
                MaterialAlertDialogBuilder(this).setMessage(e.message).show()
            }

            launchGitOperation(REQUEST_CLONE)
        }
    }

    /**
     * Syncs the local repository with the remote one (either pull or push)
     *
     * @param operation the operation to execute can be REQUEST_PULL or REQUEST_PUSH
     */
    private fun syncRepository(operation: Int) {
        if (settings.getString("git_remote_username", "")!!.isEmpty() ||
                settings.getString("git_remote_server", "")!!.isEmpty() ||
                settings.getString("git_remote_location", "")!!.isEmpty())
            MaterialAlertDialogBuilder(this)
                    .setMessage(context.getString(R.string.set_information_dialog_text))
                    .setPositiveButton(context.getString(R.string.dialog_positive)) { _, _ ->
                        val intent = Intent(context, UserPreference::class.java)
                        startActivityForResult(intent, REQUEST_PULL)
                    }
                    .setNegativeButton(context.getString(R.string.dialog_negative)) { _, _ ->
                        // do nothing :(
                        setResult(AppCompatActivity.RESULT_OK)
                        finish()
                    }
                    .show()
        else {
            // check that the remote origin is here, else add it
            PasswordRepository.addRemote("origin", hostname, false)
            launchGitOperation(operation)
        }
    }

    /**
     * Attempt to launch the requested GIT operation. Depending on the configured auth, it may not
     * be possible to launch the operation immediately. In that case, this function may launch an
     * intermediate activity instead, which will gather necessary information and post it back via
     * onActivityResult, which will then re-call this function. This may happen multiple times,
     * until either an error is encountered or the operation is successfully launched.
     *
     * @param operation The type of GIT operation to launch
     */
    private fun launchGitOperation(operation: Int) {
        val op: GitOperation
        val localDir = PasswordRepository.getRepositoryDirectory(context)

        try {

            // Before launching the operation with OpenKeychain auth, we need to issue several requests
            // to the OpenKeychain API. IdentityBuild will take care of launching the relevant intents,
            // we just need to keep calling it until it returns a completed ApiIdentity.
            if (connectionMode.equals("OpenKeychain", ignoreCase = true) && identity == null) {
                // Lazy initialization of the IdentityBuilder
                if (identityBuilder == null) {
                    identityBuilder = SshApiSessionFactory.IdentityBuilder(this)
                }

                // Try to get an ApiIdentity and bail if one is not ready yet. The builder will ensure
                // that onActivityResult is called with operation again, which will re-invoke us here
                identity = identityBuilder!!.tryBuild(operation)
                if (identity == null)
                    return
            }

            when (operation) {
                REQUEST_CLONE, GitOperation.GET_SSH_KEY_FROM_CLONE -> op = CloneOperation(localDir!!, this).setCommand(hostname)

                REQUEST_PULL -> op = PullOperation(localDir!!, this).setCommand()

                REQUEST_PUSH -> op = PushOperation(localDir!!, this).setCommand()

                REQUEST_SYNC -> op = SyncOperation(localDir!!, this).setCommands()

                BREAK_OUT_OF_DETACHED -> op = BreakOutOfDetached(localDir!!, this).setCommands()

                SshApiSessionFactory.POST_SIGNATURE -> return

                else -> {
                    Log.e(TAG, "Operation not recognized : $operation")
                    setResult(AppCompatActivity.RESULT_CANCELED)
                    finish()
                    return
                }
            }

            op.executeAfterAuthentication(connectionMode,
                    settings.getString("git_remote_username", "git")!!,
                    File("$filesDir/.ssh_key"),
                    identity)
        } catch (e: Exception) {
            e.printStackTrace()
            MaterialAlertDialogBuilder(this).setMessage(e.message).show()
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int,
                                  data: Intent?) {

        // In addition to the pre-operation-launch series of intents for OpenKeychain auth
        // that will pass through here and back to launchGitOperation, there is one
        // synchronous operation that happens /after/ the operation has been launched in the
        // background thread - the actual signing of the SSH challenge. We pass through the
        // completed signature to the ApiIdentity, which will be blocked in the other thread
        // waiting for it.
        if (requestCode == SshApiSessionFactory.POST_SIGNATURE && identity != null)
            identity!!.postSignature(data)

        if (resultCode == AppCompatActivity.RESULT_CANCELED) {
            setResult(AppCompatActivity.RESULT_CANCELED)
            finish()
        } else if (resultCode == AppCompatActivity.RESULT_OK) {
            // If an operation has been re-queued via this mechanism, let the
            // IdentityBuilder attempt to extract some updated state from the intent before
            // trying to re-launch the operation.
            if (identityBuilder != null) {
                identityBuilder!!.consume(data)
            }
            launchGitOperation(requestCode)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        const val REQUEST_PULL = 101
        const val REQUEST_PUSH = 102
        const val REQUEST_CLONE = 103
        const val REQUEST_INIT = 104
        const val EDIT_SERVER = 105
        const val REQUEST_SYNC = 106
        @Suppress("Unused") const val REQUEST_CREATE = 107
        const val EDIT_GIT_CONFIG = 108
        const val BREAK_OUT_OF_DETACHED = 109
        private const val TAG = "GitAct"
        private const val emailPattern = "^[^@]+@[^@]+$"
    }
}
