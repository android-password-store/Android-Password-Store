package com.zeapo.pwdstore

import android.content.Context
import android.content.Intent
import android.support.test.InstrumentationRegistry
import android.support.test.filters.LargeTest
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import com.zeapo.pwdstore.crypto.PgpActivity
import kotlinx.android.synthetic.main.decrypt_layout.*
import org.apache.commons.io.IOUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


@RunWith(AndroidJUnit4::class)
@LargeTest
class WelcomeActivityTest {
    @Rule @JvmField
    var mActivityRule: ActivityTestRule<PgpActivity> = ActivityTestRule(PgpActivity::class.java, true, false)

    @Test
    fun pathShouldDecompose() {
        val path = "/data/my.app.com/files/store/cat1/name.gpg"
        val repoPath = "/data/my.app.com/files/store"
        assertEquals("/cat1/name.gpg", PgpActivity.getRelativePath(path, repoPath))
        assertEquals("/cat1/", PgpActivity.getParentPath(path, repoPath))
        assertEquals("name", PgpActivity.getName(path, repoPath))
        assertEquals("name", PgpActivity.getName(path, "$repoPath/"))
    }

    @Test
    fun activityShouldShowName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "name"
        val parentPath = "/cat1/"
        val repoPath = "${context.filesDir}/store/"
        val path = "$repoPath/cat1/name.gpg"


        val intent = Intent(context, PgpActivity::class.java)
        intent.putExtra("OPERATION", "DECRYPT")
        intent.putExtra("FILE_PATH", path)
        intent.putExtra("REPO_PATH", repoPath)

        copyAssets(context, "store", context.filesDir.absolutePath)

        val activity: PgpActivity = mActivityRule.launchActivity(intent)

        val categoryView = activity.crypto_password_category_decrypt
        assertNotNull(categoryView)
        assertEquals(parentPath, categoryView.text)

        val nameView = activity.crypto_password_file
        assertNotNull(nameView)
        assertEquals(name, nameView.text)
    }

    companion object {
        fun copyAssets(context: Context, source: String, destination: String) {
            val assetManager = context.assets
            val files: Array<String>? = assetManager.list(source)

            files?.map { filename ->
                val destPath = "$destination/$filename"
                val sourcePath = "$source/$filename"
                if (assetManager.list(filename).isNotEmpty()) {
                    File(destPath).mkdir()
                    copyAssets(context, "$source/$filename", destPath)
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
                        Log.e("tag", "Failed to copy asset file: " + filename, e)
                    }
                }
            }
        }
    }

}



