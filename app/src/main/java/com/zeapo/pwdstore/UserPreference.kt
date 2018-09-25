package com.zeapo.pwdstore

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.preference.CheckBoxPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import android.widget.Toast
import com.nononsenseapps.filepicker.FilePickerActivity
import com.zeapo.pwdstore.autofill.AutofillPreferenceActivity
import com.zeapo.pwdstore.crypto.PgpActivity
import com.zeapo.pwdstore.git.GitActivity
import com.zeapo.pwdstore.utils.PasswordRepository
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.openintents.openpgp.util.OpenPgpUtils
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class UserPreference : AppCompatActivity() {
    private lateinit var prefsFragment: PrefsFragment

    class PrefsFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val callingActivity = activity as UserPreference
            val sharedPreferences = preferenceManager.sharedPreferences

            addPreferencesFromResource(R.xml.preference)

            findPreference("app_version").summary = "Version: ${BuildConfig.VERSION_NAME}"

            findPreference("openpgp_key_id_pref").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val intent = Intent(callingActivity, PgpActivity::class.java)
                intent.putExtra("OPERATION", "GET_KEY_ID")
                startActivityForResult(intent, IMPORT_PGP_KEY)
                true
            }

            findPreference("ssh_key").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                callingActivity.getSshKeyWithPermissions(sharedPreferences.getBoolean("use_android_file_picker", false))
                true
            }

            findPreference("ssh_keygen").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                callingActivity.makeSshKey(true)
                true
            }

            findPreference("ssh_see_key").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val df = SshKeyGen.ShowSshKeyFragment()
                df.show(fragmentManager, "public_key")
                true
            }

            findPreference("ssh_key_clear_passphrase").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                sharedPreferences.edit().putString("ssh_key_passphrase", null).apply()
                it.isEnabled = false
                true
            }

            findPreference("hotp_remember_clear_choice").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                sharedPreferences.edit().putBoolean("hotp_remember_check", false).apply()
                it.isEnabled = false
                true
            }

            findPreference("git_server_info").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val intent = Intent(callingActivity, GitActivity::class.java)
                intent.putExtra("Operation", GitActivity.EDIT_SERVER)
                startActivityForResult(intent, EDIT_GIT_INFO)
                true
            }

            findPreference("git_config").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val intent = Intent(callingActivity, GitActivity::class.java)
                intent.putExtra("Operation", GitActivity.EDIT_GIT_CONFIG)
                startActivityForResult(intent, EDIT_GIT_CONFIG)
                true
            }

            findPreference("git_delete_repo").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val repoDir = PasswordRepository.getRepositoryDirectory(callingActivity.applicationContext)
                AlertDialog.Builder(callingActivity)
                        .setTitle(R.string.pref_dialog_delete_title)
                        .setMessage("${resources.getString(R.string.dialog_delete_msg)} \n $repoDir")
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_delete) { dialogInterface, _ ->
                            try {
                                FileUtils.cleanDirectory(PasswordRepository.getRepositoryDirectory(callingActivity.applicationContext))
                                PasswordRepository.closeRepository()
                            } catch (e: Exception) {
                                //TODO Handle the diffent cases of exceptions
                            }

                            sharedPreferences.edit().putBoolean("repository_initialized", false).apply()
                            dialogInterface.cancel()
                            callingActivity.finish()
                        }.setNegativeButton(R.string.dialog_do_not_delete) { dialogInterface, _ -> run { dialogInterface.cancel() } }
                        .show()

                true
            }

            val externalRepo = findPreference("pref_select_external")
            externalRepo.summary = sharedPreferences.getString("git_external_repo", callingActivity.getString(R.string.no_repo_selected))
            externalRepo.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                callingActivity.selectExternalGitRepository()
                true
            }

            val resetRepo = Preference.OnPreferenceChangeListener { _, o ->
                findPreference("git_delete_repo").isEnabled = !(o as Boolean)
                PasswordRepository.closeRepository()
                sharedPreferences.edit().putBoolean("repo_changed", true).apply()
                true
            }

            findPreference("pref_select_external").onPreferenceChangeListener = resetRepo
            findPreference("git_external").onPreferenceChangeListener = resetRepo

            findPreference("autofill_apps").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val intent = Intent(callingActivity, AutofillPreferenceActivity::class.java)
                startActivity(intent)
                true
            }

            findPreference("autofill_enable").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                AlertDialog.Builder(callingActivity).setTitle(R.string.pref_autofill_enable_title).setView(R.layout.autofill_instructions).setPositiveButton(R.string.dialog_ok) { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }.setNegativeButton(R.string.dialog_cancel, null).setOnDismissListener { (findPreference("autofill_enable") as CheckBoxPreference).isChecked = (activity as UserPreference).isServiceEnabled }.show()
                true
            }

            findPreference("export_passwords").onPreferenceClickListener = Preference.OnPreferenceClickListener {
                callingActivity.exportPasswordsWithPermissions()
                true
            }
        }

        override fun onStart() {
            super.onStart()
            val sharedPreferences = preferenceManager.sharedPreferences
            findPreference("pref_select_external").summary = preferenceManager.sharedPreferences.getString("git_external_repo", getString(R.string.no_repo_selected))
            findPreference("ssh_see_key").isEnabled = sharedPreferences.getBoolean("use_generated_key", false)
            findPreference("git_delete_repo").isEnabled = !sharedPreferences.getBoolean("git_external", false)
            findPreference("ssh_key_clear_passphrase").isEnabled = sharedPreferences.getString("ssh_key_passphrase", null)?.isNotEmpty() ?: false
            findPreference("hotp_remember_clear_choice").isEnabled = sharedPreferences.getBoolean("hotp_remember_check", false)
            val keyPref = findPreference("openpgp_key_id_pref")
            val selectedKeys: Array<String> = ArrayList<String>(sharedPreferences.getStringSet("openpgp_key_ids_set", HashSet<String>())).toTypedArray()
            if (selectedKeys.isEmpty()) {
                keyPref.summary = "No key selected"
            } else {
                keyPref.summary = selectedKeys.joinToString(separator = ";") {
                    s ->
                    OpenPgpUtils.convertKeyIdToHex(java.lang.Long.valueOf(s))
                }
            }

            // see if the autofill service is enabled and check the preference accordingly
            (findPreference("autofill_enable") as CheckBoxPreference).isChecked = (activity as UserPreference).isServiceEnabled
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.applicationContext)
        super.onCreate(savedInstanceState)
        when (intent?.getStringExtra("operation")) {
            "get_ssh_key" -> getSshKeyWithPermissions(sharedPreferences.getBoolean("use_android_file_picker", false))
            "make_ssh_key" -> makeSshKey(false)
            "git_external" -> selectExternalGitRepository()
        }
        prefsFragment = PrefsFragment()

        fragmentManager.beginTransaction().replace(android.R.id.content, prefsFragment).commit()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    fun selectExternalGitRepository() {
        val activity = this
        AlertDialog.Builder(this)
                .setTitle("Choose where to store the passwords")
                .setMessage("You must select a directory where to store your passwords. If you want " +
                        "to store your passwords within the hidden storage of the application, " +
                        "cancel this dialog and disable the \"External Repository\" option.")
                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                    // This always works
                    val i = Intent(activity.applicationContext, FilePickerActivity::class.java)
                    // This works if you defined the intent filter
                    // Intent i = new Intent(Intent.ACTION_GET_CONTENT);

                    // Set these depending on your use case. These are the defaults.
                    i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
                    i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
                    i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR)

                    i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().path)

                    startActivityForResult(i, SELECT_GIT_DIRECTORY)
                }.setNegativeButton(R.string.dialog_cancel, null).show()

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        when (id) {
            android.R.id.home -> {
                setResult(Activity.RESULT_OK)
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Opens a file explorer to import the private key
     */
    fun getSshKeyWithPermissions(useDefaultPicker: Boolean) = runWithPermissions(
            requestedPermission = Manifest.permission.READ_EXTERNAL_STORAGE,
            requestCode = REQUEST_EXTERNAL_STORAGE_SSH_KEY,
            reason = "We need access to the sd-card to import the ssh-key"
    ) {
        getSshKey(useDefaultPicker)
    }

    /**
     * Opens a file explorer to import the private key
     */
    fun getSshKey(useDefaultPicker: Boolean) {
        val intent = if (useDefaultPicker) {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.setType("*/*")
        } else {
            // This always works
            val intent = Intent(applicationContext, FilePickerActivity::class.java)

            // Set these depending on your use case. These are the defaults.
            intent.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
            intent.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false)
            intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE)

            intent.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().path)
        }
        startActivityForResult(intent, IMPORT_SSH_KEY)
    }

    /**
     * Run a function after checking that the permissions have been requested
     *
     * @param requestedPermission The permission to request
     * @param requestCode The code passed to onRequestPermissionsResult
     * @param reason The text to be shown to the user to explain why we're requesting this permission
     * @param body The function to run
     */
    private fun runWithPermissions(requestedPermission: String, requestCode: Int, reason: String, body: () -> Unit): Unit {
        if (ContextCompat.checkSelfPermission(this, requestedPermission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, requestedPermission)) {
                val snack = Snackbar.make(prefsFragment.view,
                        reason,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.dialog_ok) {
                            ActivityCompat.requestPermissions(this, arrayOf(requestedPermission), requestCode)
                        }
                snack.show()
                val view = snack.view
                val tv = view.findViewById<TextView>(android.support.design.R.id.snackbar_text)
                tv.setTextColor(Color.WHITE)
                tv.maxLines = 10
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, arrayOf(requestedPermission), requestCode)
            }
        } else {
            body()
        }

    }

    /**
     * Exports the passwords after requesting permissions
     */
    fun exportPasswordsWithPermissions() = runWithPermissions(
            requestedPermission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
            requestCode = REQUEST_EXTERNAL_STORAGE_SSH_KEY,
            reason = "We need access to the sd-card to export the passwords"
    ) {
        exportPasswords()
    }

    /**
     * Exports the passwords
     */
    private fun exportPasswords(): Unit {
        val i = Intent(applicationContext, FilePickerActivity::class.java)

        // Set these depending on your use case. These are the defaults.
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
        i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR)

        i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().path)

        startActivityForResult(i, EXPORT_PASSWORDS)
    }

    /**
     * Opens a key generator to generate a public/private key pair
     */
    fun makeSshKey(fromPreferences: Boolean) {
        val intent = Intent(applicationContext, SshKeyGen::class.java)
        startActivity(intent)
        if (!fromPreferences) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    @Throws(IOException::class)
    private fun copySshKey(uri: Uri) {
        val sshKey = this.contentResolver.openInputStream(uri)
        if (sshKey != null) {
            val privateKey = IOUtils.toByteArray(sshKey)
            FileUtils.writeByteArrayToFile(File(filesDir.toString() + "/.ssh_key"), privateKey)
            sshKey.close()
        } else {
            Toast.makeText(this, getString(R.string.ssh_key_does_not_exist), Toast.LENGTH_LONG).show()
        }
    }

    // Returns whether the autofill service is enabled
    private val isServiceEnabled: Boolean
        get() {
            val am = this
                    .getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val runningServices = am
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            return runningServices.any { "com.zeapo.pwdstore/.autofill.AutofillService" == it.id }
        }


    override fun onActivityResult(requestCode: Int, resultCode: Int,
                                  data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (data == null) {
                setResult(Activity.RESULT_CANCELED)
                return
            }

            when (requestCode) {
                IMPORT_SSH_KEY -> {
                    try {
                        val uri: Uri = data.data ?: throw IOException("Unable to open file")

                        copySshKey(uri)
                        Toast.makeText(this, this.resources.getString(R.string.ssh_key_success_dialog_title), Toast.LENGTH_LONG).show()
                        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

                        prefs.edit().putBoolean("use_generated_key", false).apply()

                        //delete the public key from generation
                        val file = File(filesDir.toString() + "/.ssh_key.pub")
                        file.delete()
                        setResult(Activity.RESULT_OK)

                        finish()
                    } catch (e: IOException) {
                        AlertDialog.Builder(this).setTitle(this.resources.getString(R.string.ssh_key_error_dialog_title)).setMessage(this.resources.getString(R.string.ssh_key_error_dialog_text) + e.message).setPositiveButton(this.resources.getString(R.string.dialog_ok)) { _, _ ->
                            // pass
                        }.show()
                    }

                }
                EDIT_GIT_INFO -> {

                }
                SELECT_GIT_DIRECTORY -> {
                    val uri = data.data

                    if (uri.path == Environment.getExternalStorageDirectory().path) {
                        // the user wants to use the root of the sdcard as a store...
                        AlertDialog.Builder(this)
                                .setTitle("SD-Card root selected")
                                .setMessage("You have selected the root of your sdcard for the store. " +
                                        "This is extremely dangerous and you will lose your data " +
                                        "as its content will, eventually, be deleted")
                                .setPositiveButton("Remove everything") { _, _ ->
                                    PreferenceManager.getDefaultSharedPreferences(applicationContext)
                                            .edit()
                                            .putString("git_external_repo", uri.path)
                                            .apply()
                                }.setNegativeButton(R.string.dialog_cancel, null).show()
                    } else {
                        PreferenceManager.getDefaultSharedPreferences(applicationContext)
                                .edit()
                                .putString("git_external_repo", uri.path)
                                .apply()
                    }
                }
                EXPORT_PASSWORDS -> {
                    val uri = data.data
                    val repositoryDirectory = PasswordRepository.getRepositoryDirectory(applicationContext)
                    val fmtOut = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
                    val date = Date()
                    val password_now = "/password_store_" + fmtOut.format(date)
                    val targetDirectory = File(uri.path + password_now)
                    if (repositoryDirectory != null) {
                        try {
                            FileUtils.copyDirectory(repositoryDirectory, targetDirectory, true)
                        } catch (e: IOException) {
                            Log.d("PWD_EXPORT", "Exception happened : " + e.message)
                        }

                    }
                }
                else -> {
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.applicationContext)
        when (requestCode) {
            REQUEST_EXTERNAL_STORAGE_SSH_KEY -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && PackageManager.PERMISSION_GRANTED in grantResults) {
                    getSshKey(sharedPreferences.getBoolean("use_android_file_picker", false))
                }
            }
            REQUEST_EXTERNAL_STORAGE_EXPORT_PWD -> {
                if (grantResults.isNotEmpty() && PackageManager.PERMISSION_GRANTED in grantResults) {
                    exportPasswords()
                }
            }
        }
    }

    companion object {
        private val IMPORT_SSH_KEY = 1
        private val IMPORT_PGP_KEY = 2
        private val EDIT_GIT_INFO = 3
        private val SELECT_GIT_DIRECTORY = 4
        private val EXPORT_PASSWORDS = 5
        private val EDIT_GIT_CONFIG = 6
        private val REQUEST_EXTERNAL_STORAGE_SSH_KEY = 50
        private val REQUEST_EXTERNAL_STORAGE_EXPORT_PWD = 51
    }
}
