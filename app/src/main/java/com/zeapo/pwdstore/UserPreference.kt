package com.zeapo.pwdstore

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.CheckBoxPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.zeapo.pwdstore.autofill.AutofillPreferenceActivity
import com.zeapo.pwdstore.crypto.PgpActivity
import com.zeapo.pwdstore.git.GitActivity
import com.zeapo.pwdstore.utils.PasswordRepository
import org.apache.commons.io.FileUtils
import org.openintents.openpgp.util.OpenPgpUtils
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class UserPreference : AppCompatActivity() {

    private val TAG = "UserPreference"
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
                callingActivity.getSshKey()
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

            findPreference("ssh_key_clear_passphrase").onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        sharedPreferences.edit().putString("ssh_key_passphrase", null).apply()
                        it.isEnabled = false
                        true
                    }

            findPreference("hotp_remember_clear_choice").onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
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
                        .setMessage(resources.getString(R.string.dialog_delete_msg, repoDir))
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_delete) { dialogInterface, _ ->
                            try {
                                FileUtils.cleanDirectory(PasswordRepository.getRepositoryDirectory(callingActivity.applicationContext))
                                PasswordRepository.closeRepository()
                            } catch (e: Exception) {
                                //TODO Handle the different cases of exceptions
                            }

                            sharedPreferences.edit().putBoolean("repository_initialized", false).apply()
                            dialogInterface.cancel()
                            callingActivity.finish()
                        }
                        .setNegativeButton(R.string.dialog_do_not_delete) { dialogInterface, _ -> run { dialogInterface.cancel() } }
                        .show()

                true
            }

            val externalRepo = findPreference("pref_select_external")
            externalRepo.summary =
                    sharedPreferences.getString("git_external_repo", callingActivity.getString(R.string.no_repo_selected))
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
                AlertDialog.Builder(callingActivity).setTitle(R.string.pref_autofill_enable_title)
                        .setView(R.layout.autofill_instructions).setPositiveButton(R.string.dialog_ok) { _, _ ->
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            startActivity(intent)
                        }.setNegativeButton(R.string.dialog_cancel, null).setOnDismissListener {
                            (findPreference("autofill_enable") as CheckBoxPreference).isChecked =
                                    (activity as UserPreference).isServiceEnabled
                        }.show()
                true
            }

            findPreference("export_passwords").apply {
                isEnabled = sharedPreferences.getBoolean("repository_initialized", false)
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    callingActivity.exportPasswords()
                    true
                }
            }

            findPreference("general_show_time").onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                try {
                    findPreference("clear_after_copy").isEnabled = newValue.toString().toInt() != 0
                    findPreference("clear_clipboard_20x").isEnabled = newValue.toString().toInt() != 0
                    true
                } catch (e: NumberFormatException) {
                    false
                }
            }
        }

        override fun onStart() {
            super.onStart()
            val sharedPreferences = preferenceManager.sharedPreferences
            findPreference("pref_select_external").summary =
                    preferenceManager.sharedPreferences.getString("git_external_repo", getString(R.string.no_repo_selected))
            findPreference("ssh_see_key").isEnabled = sharedPreferences.getBoolean("use_generated_key", false)
            findPreference("git_delete_repo").isEnabled = !sharedPreferences.getBoolean("git_external", false)
            findPreference("ssh_key_clear_passphrase").isEnabled = sharedPreferences.getString(
                    "ssh_key_passphrase",
                    null
            )?.isNotEmpty() ?: false
            findPreference("hotp_remember_clear_choice").isEnabled =
                    sharedPreferences.getBoolean("hotp_remember_check", false)
            findPreference("clear_after_copy").isEnabled = sharedPreferences.getString("general_show_time", "45")?.toInt() != 0
            findPreference("clear_clipboard_20x").isEnabled = sharedPreferences.getString("general_show_time", "45")?.toInt() != 0
            val keyPref = findPreference("openpgp_key_id_pref")
            val selectedKeys: Array<String> = ArrayList<String>(
                    sharedPreferences.getStringSet(
                            "openpgp_key_ids_set",
                            HashSet<String>()
                    )
            ).toTypedArray()
            if (selectedKeys.isEmpty()) {
                keyPref.summary = this.resources.getString(R.string.pref_no_key_selected)
            } else {
                keyPref.summary = selectedKeys.joinToString(separator = ";") { s ->
                    OpenPgpUtils.convertKeyIdToHex(java.lang.Long.valueOf(s))
                }
            }

            // see if the autofill service is enabled and check the preference accordingly
            (findPreference("autofill_enable") as CheckBoxPreference).isChecked =
                    (activity as UserPreference).isServiceEnabled
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.applicationContext)
        super.onCreate(savedInstanceState)
        when (intent?.getStringExtra("operation")) {
            "get_ssh_key" -> getSshKey()
            "make_ssh_key" -> makeSshKey(false)
            "git_external" -> selectExternalGitRepository()
        }
        prefsFragment = PrefsFragment()

        fragmentManager.beginTransaction().replace(android.R.id.content, prefsFragment).commit()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    fun selectExternalGitRepository() {
        AlertDialog.Builder(this)
                .setTitle(this.resources.getString(R.string.external_repository_dialog_title))
                .setMessage(this.resources.getString(R.string.external_repository_dialog_text))
                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    startActivityForResult(Intent.createChooser(i, "Choose Directory"), SELECT_GIT_DIRECTORY)
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
    private fun getSshKey() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, IMPORT_SSH_KEY)
    }

    /**
     * Exports the passwords
     */
    private fun exportPasswords() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(Intent.createChooser(i, "Choose Directory"), EXPORT_PASSWORDS)
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
        // TODO: Check if valid SSH Key before import
        val sshKeyInputStream = contentResolver.openInputStream(uri)
        if (sshKeyInputStream != null) {

            val internalKeyFile = File("""${filesDir.toString()}/.ssh_key""")

            if (internalKeyFile.exists()) {
                internalKeyFile.delete()
                internalKeyFile.createNewFile()
            }

            val sshKeyOutputSteam = internalKeyFile.outputStream();

            sshKeyInputStream.copyTo(sshKeyOutputSteam, 1024)

            sshKeyInputStream.close()
            sshKeyOutputSteam.close()


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
            return runningServices
                    .map { it.id.substringBefore("/") }
                    .any { it == BuildConfig.APPLICATION_ID }
        }

    override fun onActivityResult(
            requestCode: Int, resultCode: Int,
            data: Intent?
    ) {
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

                        Toast.makeText(
                                this,
                                this.resources.getString(R.string.ssh_key_success_dialog_title),
                                Toast.LENGTH_LONG
                        ).show()
                        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

                        prefs.edit().putBoolean("use_generated_key", false).apply()

                        // Delete the public key from generation
                        File("""${filesDir.toString()}/.ssh_key.pub""").delete()
                        setResult(Activity.RESULT_OK)

                        finish()
                    } catch (e: IOException) {
                        AlertDialog.Builder(this)
                                .setTitle(this.resources.getString(R.string.ssh_key_error_dialog_title))
                                .setMessage(this.resources.getString(R.string.ssh_key_error_dialog_text) + e.message)
                                .setPositiveButton(this.resources.getString(R.string.dialog_ok), null)
                                .show()
                    }
                }
                EDIT_GIT_INFO -> {

                }
                SELECT_GIT_DIRECTORY -> {
                    val uri = data.data

                    Log.d(TAG, "Selected repository URI is $uri")
                    // TODO: This is fragile. Workaround until PasswordItem is backed by DocumentFile
                    val docId = DocumentsContract.getTreeDocumentId(uri)
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val repoPath = "${Environment.getExternalStorageDirectory()}/${split[1]}"

                    Log.d(TAG, "Selected repository path is $repoPath")

                    if (Environment.getExternalStorageDirectory().path == repoPath) {
                        TODO("Assert that we haven't selected root directory")
                    }

                    PreferenceManager.getDefaultSharedPreferences(applicationContext)
                            .edit()
                            .putString("git_external_repo", repoPath)
                            .apply()
                }
                EXPORT_PASSWORDS -> {
                    var uri = data.data

                    val targetDirectory = DocumentFile.fromTreeUri(applicationContext, uri);

                    if (targetDirectory != null) {
                        exportPasswords(targetDirectory)
                    }
                }
                else -> {
                }
            }
        }
    }

    /**
     * Exports passwords to the given directory.
     *
     * Recursively copies the existing password store to an external directory.
     *
     * @param targetDirectory directory to copy password directory to.
     */
    private fun exportPasswords(targetDirectory: DocumentFile) {

        val repositoryDirectory = PasswordRepository.getRepositoryDirectory(applicationContext)
        val sourcePassDir = DocumentFile.fromFile(repositoryDirectory)

        Log.d(TAG, "Copying ${repositoryDirectory.path} to $targetDirectory")

        val dateString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDateTime
                    .now()
                    .format(DateTimeFormatter.ISO_DATE_TIME)
        } else {
            String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z")));
        }

        val passDir = targetDirectory.createDirectory("password_store_$dateString")

        if (passDir != null) {
            copyDirToDir(sourcePassDir, passDir)
        }

    }

    /**
     * Copies a password file to a given directory.
     *
     * Note: this does not preserve last modified time.
     *
     * @param passwordFile password file to copy.
     * @param targetDirectory target directory to copy password.
     */
    private fun copyFileToDir(passwordFile: DocumentFile, targetDirectory: DocumentFile) {
        val sourceInputStream = contentResolver.openInputStream(passwordFile.uri)
        val name = passwordFile.name
        val targetPasswordFile = targetDirectory.createFile("application/octet-stream", name!!)

        val destOutputStream = contentResolver.openOutputStream(targetPasswordFile?.uri)

        sourceInputStream.copyTo(destOutputStream, 1024)

        sourceInputStream.close()
        destOutputStream.close()
    }

    /**
     * Recursively copies a directory to a destination.
     *
     *  @param sourceDirectory directory to copy from.
     *  @param sourceDirectory directory to copy to.
     */
    private fun copyDirToDir(sourceDirectory: DocumentFile, targetDirectory: DocumentFile) {
        val files = sourceDirectory.listFiles();

        for (i in files.indices) {

            val f = files[i];

            if (f.isDirectory) {
                // Create new directory and recurse
                val newDir = targetDirectory.createDirectory(f.name!!);
                copyDirToDir(f, newDir!!)
            } else {
                copyFileToDir(f, targetDirectory);
            }

        }
    }

    companion object {
        private const val IMPORT_SSH_KEY = 1
        private const val IMPORT_PGP_KEY = 2
        private const val EDIT_GIT_INFO = 3
        private const val SELECT_GIT_DIRECTORY = 4
        private const val EXPORT_PASSWORDS = 5
        private const val EDIT_GIT_CONFIG = 6
    }
}
