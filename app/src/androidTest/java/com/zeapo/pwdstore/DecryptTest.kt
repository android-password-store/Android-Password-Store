package com.zeapo.pwdstore

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.zeapo.pwdstore.crypto.PgpActivity
import kotlinx.android.synthetic.main.decrypt_layout.crypto_extra_show
import kotlinx.android.synthetic.main.decrypt_layout.crypto_password_category_decrypt
import kotlinx.android.synthetic.main.decrypt_layout.crypto_password_file
import kotlinx.android.synthetic.main.decrypt_layout.crypto_password_show
import kotlinx.android.synthetic.main.decrypt_layout.crypto_username_show
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets


@RunWith(AndroidJUnit4::class)
@LargeTest
class DecryptTest {
    private lateinit var targetContext: Context
    private lateinit var testContext: Context
    lateinit var activity: PgpActivity

    private val name = "sub"
    private val parentPath = "/category/"
    lateinit var path: String
    lateinit var repoPath: String

    @Rule @JvmField
    var mActivityRule: ActivityTestRule<PgpActivity> = ActivityTestRule<PgpActivity>(PgpActivity::class.java, true, false)

    private fun init() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        testContext = InstrumentationRegistry.getInstrumentation().context
        copyAssets("encrypted-store", File(targetContext.filesDir, "test-store").absolutePath)
        repoPath = File(targetContext.filesDir, "test-store").absolutePath
        path = "$repoPath/$parentPath/$name.gpg".replace("//", "/")

        val intent = Intent(targetContext, PgpActivity::class.java)
        intent.putExtra("OPERATION", "DECRYPT")
        intent.putExtra("FILE_PATH", path)
        intent.putExtra("REPO_PATH", repoPath)

        activity = mActivityRule.launchActivity(intent)
    }

    @Test
    fun pathShouldDecompose() {
        val pathOne = "/fake/path/cat1/n1.gpg"
        val pathTwo = "/fake/path/n2.gpg"

        assertEquals("/cat1/n1.gpg", PgpActivity.getRelativePath(pathOne, "/fake/path"))
        assertEquals("/cat1/", PgpActivity.getParentPath(pathOne, "/fake/path"))
        assertEquals("n1", PgpActivity.getName("$pathOne/fake/path"))
        // test that even if we append a `/` it still works
        assertEquals("n1", PgpActivity.getName("$pathOne/fake/path/"))

        assertEquals("/n2.gpg", PgpActivity.getRelativePath(pathTwo, "/fake/path"))
        assertEquals("/", PgpActivity.getParentPath(pathTwo, "/fake/path"))
        assertEquals("n2", PgpActivity.getName("$pathTwo/fake/path"))
        assertEquals("n2", PgpActivity.getName("$pathTwo/fake/path/"))
    }

    @Test
    fun activityShouldShowName() {
        init()

        val categoryView = activity.crypto_password_category_decrypt
        assertNotNull(categoryView)
        assertEquals(parentPath, categoryView.text)

        val nameView = activity.crypto_password_file
        assertNotNull(nameView)
        assertEquals(name, nameView.text)
    }

    @SuppressLint("ApplySharedPref") // we need the preferences right away
    @Test
    fun shouldDecrypt() {
        init()
        val clearPass = IOUtils.toString(testContext.assets.open("clear-store/category/sub"), StandardCharsets.UTF_8)
        val passEntry = PasswordEntry(clearPass)

        // Setup the timer to 1 second
        // first remember the previous timer to set it back later
        val showTime = try {
            Integer.parseInt(activity.settings.getString("general_show_time", "45") ?: "45")
        } catch (e: NumberFormatException) {
            45
        }
        // second set the new timer
        activity.settings.edit().putString("general_show_time", "2").commit()

        activity.onBound(null)

        // have we decrypted things correctly?
        assertEquals(passEntry.password, activity.crypto_password_show.text)
        assertEquals(passEntry.username, activity.crypto_username_show.text.toString())
        assertEquals(passEntry.extraContent, activity.crypto_extra_show.text.toString())

        // did we copy the password?
        val clipboard: ClipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(passEntry.password, clipboard.primaryClip.getItemAt(0).text)

        // wait until the clipboard is cleared
        SystemClock.sleep(4000)

        // The clipboard should be cleared!!
        for(i in 0..clipboard.primaryClip.itemCount) {
            assertEquals("", clipboard.primaryClip.getItemAt(i).text)
        }

        // set back the timer
        activity.settings.edit().putString("general_show_time", showTime.toString()).commit()
    }

    companion object {
        fun copyAssets(source: String, destination: String) {
            FileUtils.forceMkdir(File(destination))
            FileUtils.cleanDirectory(File(destination))

            val testContext = InstrumentationRegistry.getInstrumentation().context
            val assetManager = testContext.assets
            val files: Array<String>? = assetManager.list(source)

            files?.map { filename ->
                val destPath = "$destination/$filename"
                val sourcePath = "$source/$filename"

                if (assetManager.list(sourcePath).isNotEmpty()) {
                    FileUtils.forceMkdir(File(destination, filename))
                    copyAssets("$source/$filename", destPath)
                } else {
                    try {
                        val input = assetManager.open(sourcePath)
                        val outFile = File(destination, filename)
                        val output = FileOutputStream(outFile)
                        IOUtils.copy(input, output)
                        input.close()
                        output.flush()
                        output.close()
                    } catch (e: IOException) {
                        Log.e("tag", "Failed to copy asset file: $filename", e)
                    }
                }
            }
        }
    }

}



